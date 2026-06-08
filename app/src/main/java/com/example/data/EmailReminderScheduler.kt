package com.example.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val REMINDER_PREFS = "zerobook_pref"
private const val KEY_ENABLED = "email_automation_enabled"
private const val KEY_TRIGGER_TIME = "email_trigger_time"
private const val KEY_LOGS = "email_logs_list"
private const val KEY_SELECTED_RECIPIENTS = "email_selected_recipients"
private const val KEY_SCHEDULED_AT = "email_scheduled_at"

data class ReminderRecipientUi(
    val partyId: String,
    val partyName: String,
    val email: String,
    val dueAmount: Double
)

object EmailReminderScheduler {
    private const val REQUEST_CODE = 4041
    private const val ACTION_SEND_REMINDERS = "com.example.action.SEND_EMAIL_REMINDERS"

    fun loadSelectedRecipients(context: Context): Set<String> {
        val raw = context.getSharedPreferences(REMINDER_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SELECTED_RECIPIENTS, "")
            .orEmpty()
        return raw.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
    }

    fun saveSelectedRecipients(context: Context, selectedIds: Set<String>) {
        context.getSharedPreferences(REMINDER_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SELECTED_RECIPIENTS, selectedIds.sorted().joinToString(","))
            .apply()
    }

    fun loadScheduledAt(context: Context): Long? {
        val value = context.getSharedPreferences(REMINDER_PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_SCHEDULED_AT, -1L)
        return value.takeIf { it > 0L }
    }

    fun saveSchedule(context: Context, scheduledAt: Long?) {
        context.getSharedPreferences(REMINDER_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_SCHEDULED_AT, scheduledAt ?: -1L)
            .apply()
    }

    fun schedule(context: Context, scheduledAt: Long?) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = pendingIntent(context)
        alarmManager.cancel(pendingIntent)
        saveSchedule(context, scheduledAt)
        if (scheduledAt != null) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                scheduledAt,
                pendingIntent
            )
        }
    }

    suspend fun processReminders(context: Context, triggeredAt: Long = System.currentTimeMillis()): Int {
        val prefs = context.getSharedPreferences(REMINDER_PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ENABLED, false)) return 0

        val selectedRecipientIds = loadSelectedRecipients(context)
        if (selectedRecipientIds.isEmpty()) return 0

        val db = AppDatabase.getDatabase(context)
        val profile = db.businessProfileDao().getProfileSync()
        val bills = db.billReceivableDao().getAllBillsSync()
            .filter { it.outstandingAmount > 0.0 && selectedRecipientIds.contains(it.partyId) }

        if (bills.isEmpty()) {
            appendLogs(
                prefs = prefs,
                rows = listOf(
                    "AUTO RUN | ${formatTriggerTime(triggeredAt)} | No eligible customer dues found"
                )
            )
            saveSchedule(context, null)
            return 0
        }

        val logRows = bills.mapNotNull { bill ->
            val party = db.partyDao().getPartyById(bill.partyId) ?: return@mapNotNull null
            if (party.email.isBlank()) return@mapNotNull null

            val voucher = db.voucherDao().getVoucherById(bill.voucherId)
            val paymentLink = buildPaymentLink(profile, bill.outstandingAmount)
            val subject = "Payment reminder from ${profile?.businessName ?: "ZeroBook"}"
            val body = buildReminderEmailBody(
                businessName = profile?.businessName ?: "ZeroBook",
                invoiceNo = voucher?.voucherNo ?: bill.voucherNo.orEmpty(),
                invoiceDate = bill.billDate,
                outstandingAmount = bill.outstandingAmount,
                dueDate = bill.dueDate,
                paymentLink = paymentLink
            )

            "AUTO SENT | ${party.name} <${party.email}> | ${subject} | ${body.replace('\n', ' ').take(180)}"
        }

        appendLogs(prefs, logRows.ifEmpty {
            listOf("AUTO RUN | ${formatTriggerTime(triggeredAt)} | No selected recipients with valid email found")
        })
        saveSchedule(context, null)
        return logRows.size
    }

    private fun appendLogs(
        prefs: android.content.SharedPreferences,
        rows: List<String>
    ) {
        val existing = prefs.getString(KEY_LOGS, "").orEmpty()
        val joined = rows.joinToString("\n")
        val finalLogs = if (existing.isBlank()) joined else "$joined\n$existing"
        prefs.edit().putString(KEY_LOGS, finalLogs).apply()
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, EmailReminderReceiver::class.java).apply {
            action = ACTION_SEND_REMINDERS
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildPaymentLink(profile: BusinessProfile?, amount: Double): String? {
        val upiId = profile?.upiId?.trim().orEmpty()
        if (upiId.isBlank()) return null
        val businessName = profile?.businessName?.trim().orEmpty()
        return "upi://pay?pa=$upiId&pn=${businessName.ifBlank { "ZeroBook" }}&am=${"%.2f".format(Locale.ENGLISH, amount)}&cu=INR"
    }

    private fun buildReminderEmailBody(
        businessName: String,
        invoiceNo: String,
        invoiceDate: Long,
        outstandingAmount: Double,
        dueDate: Long?,
        paymentLink: String?
    ): String {
        val dateFormat = SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH)
        val dueDateLine = dueDate?.let { "Due Date: ${dateFormat.format(Date(it))}\n" }.orEmpty()
        val paymentLine = paymentLink?.let { "Payment Link: $it\n" }.orEmpty()
        return buildString {
            append("Business Name: $businessName\n")
            append("Invoice Number: $invoiceNo\n")
            append("Invoice Date: ${dateFormat.format(Date(invoiceDate))}\n")
            append("Outstanding Amount: ${Utils.formatIndianCurrency(outstandingAmount)}\n")
            append(dueDateLine)
            append(paymentLine)
            append("\nPlease clear the outstanding amount at the earliest.")
        }.trim()
    }

    private fun formatTriggerTime(triggeredAt: Long): String =
        SimpleDateFormat("dd-MMM-yyyy hh:mm a", Locale.ENGLISH).format(Date(triggeredAt))
}

class EmailReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != "com.example.action.SEND_EMAIL_REMINDERS") return
        CoroutineScope(Dispatchers.IO).launch {
            EmailReminderScheduler.processReminders(context.applicationContext)
        }
    }
}
