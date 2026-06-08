package com.example.services

import android.content.Context
import android.graphics.pdf.PdfDocument
import android.os.Handler
import android.os.Looper
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Base64
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.data.AdditionalCharge
import com.example.data.BusinessProfile
import com.example.data.Party
import com.example.data.Utils
import com.example.data.Voucher
import com.example.data.VoucherItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.net.URLEncoder
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

object InvoiceGenerator {
    data class VoucherRenderExtras(
        val paymentModeValue: String = "",
        val partialAmountPaid: Double = 0.0,
        val partialPaymentSubmode: String = "",
        val creditDueDate: String = "",
        val remainingCreditAmount: Double = 0.0,
        val isAdvance: Boolean = false,
        val advanceFor: String = ""
    )

    private val voucherExtrasCache = mutableMapOf<String, VoucherRenderExtras>()

    fun primeVoucherRenderExtras(context: Context, voucherId: String) {
        val db = com.example.data.AppDatabase.getDatabase(context)
        val cursor = db.openHelper.readableDatabase.query(
            """
            SELECT payment_mode, partial_amount_paid, partial_payment_submode, credit_due_date,
                   remaining_credit_amount, is_advance, advance_for
            FROM vouchers WHERE id = ?
            """.trimIndent(),
            arrayOf(voucherId)
        )
        cursor.use {
            if (it.moveToFirst()) {
                voucherExtrasCache[voucherId] = VoucherRenderExtras(
                    paymentModeValue = it.getString(0).orEmpty(),
                    partialAmountPaid = it.getDouble(1),
                    partialPaymentSubmode = it.getString(2).orEmpty(),
                    creditDueDate = it.getString(3).orEmpty(),
                    remainingCreditAmount = it.getDouble(4),
                    isAdvance = it.getInt(5) == 1,
                    advanceFor = it.getString(6).orEmpty()
                )
            }
        }
    }

    private val standardCss = """
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { font-family: Arial, sans-serif; font-size: 11px; color: #000000; background: #ffffff; }
        .page { width: 794px; min-height: 1123px; padding: 30px 35px; margin: 0 auto; background: white; }
        table { width: 100%; border-collapse: collapse; }
        td, th { border: 1px solid #000; padding: 4px 6px; vertical-align: top; }
        .no-border td { border: none; }
        .bold { font-weight: bold; }
        .center { text-align: center; }
        .right { text-align: right; }
        .heading { font-size: 14px; font-weight: bold; text-align: center; letter-spacing: 1px; margin-bottom: 8px; }
        .sub-label { font-size: 9px; color: #444; font-weight: normal; }
        .amount-words { font-weight: bold; font-size: 11px; padding: 6px; }
        .footer-note { font-size: 9px; color: #333; }
        .gst-table th { background-color: #f0f0f0; font-weight: bold; text-align: center; font-size: 10px; }
        .total-row td { font-weight: bold; background-color: #f5f5f5; }
        .signature-box { text-align: right; padding: 8px; min-height: 60px; }
        .logo-area { width: 80px; height: 70px; object-fit: contain; }
        @media print { body { -webkit-print-color-adjust: exact; } }
    """.trimIndent()

    fun parseAdditionalCharges(json: String?): List<AdditionalCharge> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                AdditionalCharge(
                    label = obj.optString("label", "Other Charge"),
                    amount = obj.optDouble("amount", 0.0),
                    gstRate = obj.optDouble("gst_rate", 0.0),
                    gstAmount = obj.optDouble("gst_amount", 0.0),
                    isTaxable = obj.optBoolean("is_taxable", false)
                )
            }
        } catch (ex: Exception) {
            emptyList()
        }
    }

    private fun Voucher.hasTransportInfo(): Boolean {
        return transporterName.isNotBlank() || lrNo.isNotBlank() || vehicleNo.isNotBlank() || dispatchDocNo.isNotBlank() || destination.isNotBlank() || termsOfDelivery.isNotBlank() || buyerOrderNo.isNotBlank() || referenceNo.isNotBlank()
    }

    fun additionalChargesToJson(charges: List<AdditionalCharge>): String {
        val array = JSONArray()
        charges.forEach { charge ->
            val obj = JSONObject().apply {
                put("label", charge.label)
                put("amount", charge.amount)
                put("gst_rate", charge.gstRate)
                put("gst_amount", charge.gstAmount)
                put("is_taxable", charge.isTaxable)
            }
            array.put(obj)
        }
        return array.toString()
    }

    fun buildInvoiceHtml(
        voucher: Voucher,
        items: List<VoucherItem>,
        business: BusinessProfile,
        party: Party?,
        additionalCharges: List<AdditionalCharge> = emptyList(),
        qrBase64: String? = null
    ): String {
        val resolvedParty = party ?: Party(
            id = "", name = "Walk-in Customer", type = "CUSTOMER",
            phone = "", email = "", address = "", city = "", state = business.state,
            stateCode = business.stateCode, pin = "", gstin = null, pan = null,
            openingBalance = 0.0, balanceType = "DR"
        )
        return buildInvoiceHtmlResolved(voucher, items, additionalCharges, business, resolvedParty)
    }

    fun buildInvoiceHtml(
        voucher: Voucher,
        items: List<VoucherItem>,
        additionalCharges: List<AdditionalCharge>,
        business: BusinessProfile,
        party: Party
    ): String = buildInvoiceHtmlResolved(voucher, items, additionalCharges, business, party)

    private fun buildInvoiceHtmlResolved(
        voucher: Voucher,
        items: List<VoucherItem>,
        additionalCharges: List<AdditionalCharge>,
        business: BusinessProfile,
        party: Party
    ): String {
        val isGst = business.gstin.isNotBlank()
        val isIntrastate = party.stateCode.isNotBlank() && party.stateCode == business.stateCode
        val renderExtras = voucherExtrasCache[voucher.id] ?: VoucherRenderExtras(paymentModeValue = voucher.paymentMode)
        val resolvedInvoiceTitleDefault = when {
            voucher.type.contains("PROFORMA", ignoreCase = true) -> "PROFORMA INVOICE"
            voucher.documentType.contains("PROFORMA", ignoreCase = true) -> "PROFORMA INVOICE"
            business.invoiceTitleDefault.contains("PROFORMA", ignoreCase = true) -> "PROFORMA INVOICE"
            else -> business.invoiceTitleDefault
        }
        return buildInvoiceHtmlReference(
            voucherNo = voucher.voucherNo,
            voucherDate = SimpleDateFormat("dd-MMM-yy", Locale.ENGLISH).format(Date(voucher.date)),
            paymentMode = renderExtras.paymentModeValue.ifBlank { voucher.paymentMode },
            referenceNo = voucher.referenceNo,
            buyerOrderNo = voucher.buyerOrderNo,
            termsOfDelivery = voucher.termsOfDelivery,
            transportName = voucher.transporterName,
            transportVehicle = voucher.vehicleNo,
            transportLrNo = voucher.lrNo,
            transportDestination = voucher.destination,
            narration = voucher.narration.ifBlank { business.termsAndConditions },
            invoiceTitleDefault = resolvedInvoiceTitleDefault,
            businessName = business.businessName,
            businessAddress = business.address,
            businessCity = business.city,
            businessPin = business.pin,
            businessState = business.state,
            businessStateCode = business.stateCode,
            businessPhone = business.phone,
            businessEmail = business.email,
            businessGstin = business.gstin,
            businessPan = business.pan,
            businessBankName = business.bankName,
            businessAccountNo = business.accountNo,
            businessIfsc = business.ifsc,
            businessBankBranch = business.branchName,
            businessLogoPath = business.logoPath ?: "",
            businessSignaturePath = business.signaturePath ?: "",
            partyName = party.name,
            partyAddress = party.address,
            partyCity = party.city,
            partyPin = party.pin,
            partyState = party.state,
            partyStateCode = party.stateCode,
            partyGstin = party.gstin ?: "",
            partyPan = party.pan ?: "",
            items = items,
            additionalCharges = additionalCharges,
            isGst = isGst,
            isIntrastate = isIntrastate,
            voucherRenderExtras = renderExtras
        )
    }

    private val ones = arrayOf(
        "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
        "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen",
        "Seventeen", "Eighteen", "Nineteen"
    )
    private val tens = arrayOf(
        "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"
    )

    private fun numToWords(n: Long): String {
        if (n == 0L) return ""
        if (n < 20) return ones[n.toInt()] + " "
        if (n < 100) return tens[(n / 10).toInt()] + " " + numToWords(n % 10)
        if (n < 1000) return ones[(n / 100).toInt()] + " Hundred " + numToWords(n % 100)
        if (n < 100000) return numToWords(n / 1000) + "Thousand " + numToWords(n % 1000)
        if (n < 10000000) return numToWords(n / 100000) + "Lakh " + numToWords(n % 100000)
        return numToWords(n / 10000000) + "Crore " + numToWords(n % 10000000)
    }

    private fun convertToWords(amount: Double): String {
        val safeAmount = if (amount < 0) 0.0 else amount
        val rupees = safeAmount.toLong()
        val paiseVal = ((safeAmount - rupees) * 100).roundToInt()
        var result = "Rupees " + (if (rupees == 0L) "Zero" else numToWords(rupees).trim())
        if (paiseVal > 0) {
            result += " and " + numToWords(paiseVal.toLong()).trim() + " Paise"
        }
        return result + " Only"
    }

    private fun fileUri(path: String): String {
        if (path.isBlank()) return ""
        return if (path.startsWith("/")) "file://$path" else "file:///$path"
    }

    private fun buildInvoiceHtml(
        voucherNo: String,
        voucherDate: String,
        paymentMode: String,
        referenceNo: String,
        buyerOrderNo: String,
        termsOfDelivery: String,
        narration: String,
        invoiceTitleDefault: String = "TAX INVOICE",
        businessName: String,
        businessAddress: String,
        businessCity: String,
        businessPin: String,
        businessState: String,
        businessStateCode: String,
        businessPhone: String,
        businessEmail: String,
        businessGstin: String,
        businessPan: String,
        businessBankName: String,
        businessAccountNo: String,
        businessIfsc: String,
        businessBankBranch: String,
        businessLogoPath: String,
        businessSignaturePath: String,
        partyName: String,
        partyAddress: String,
        partyCity: String,
        partyPin: String,
        partyState: String,
        partyStateCode: String,
        partyGstin: String,
        partyPan: String,
        items: List<VoucherItem>,
        additionalCharges: List<AdditionalCharge>,
        isGst: Boolean,
        isIntrastate: Boolean,
        voucherRenderExtras: VoucherRenderExtras
    ): String {
        var taxableTotal = 0.0
        var cgstTotal = 0.0
        var sgstTotal = 0.0
        var igstTotal = 0.0
        var totalQty = 0.0
        val qtyUnit = items.firstOrNull()?.unit ?: ""

        items.forEach { item ->
            taxableTotal += item.taxableAmount
            cgstTotal += item.cgstAmount
            sgstTotal += item.sgstAmount
            igstTotal += item.igstAmount
            totalQty += item.qty
        }
        val chargesTotal = additionalCharges.sumOf { it.amount }
        val rawTotal = taxableTotal + cgstTotal + sgstTotal + igstTotal + chargesTotal
        val netAmount = kotlin.math.round(rawTotal).toDouble()
        val roundOff = netAmount - rawTotal

        val totalGst = cgstTotal + sgstTotal + igstTotal
        val showGst = isGst && totalGst > 0.001

        fun f(n: Double) = "%,.2f".format(n)
        fun e(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        fun formatDueDate(raw: String): String {
            val millis = raw.toLongOrNull() ?: return raw
            return SimpleDateFormat("dd-MMM-yy", Locale.ENGLISH).format(Date(millis))
        }

        val logoHtml = if (businessLogoPath.isNotBlank() && File(businessLogoPath).exists())
            "<img src='${fileUri(businessLogoPath)}' style='max-height:65px;max-width:130px;object-fit:contain;display:block;'/>"
        else ""

        val sigHtml = if (businessSignaturePath.isNotBlank() && File(businessSignaturePath).exists())
            "<img src='${fileUri(businessSignaturePath)}' style='max-height:55px;max-width:160px;object-fit:contain;display:block;margin-bottom:4px;'/>"
        else "<div style='height:55px;'></div>"

        val invoiceTitle = when {
            invoiceTitleDefault.contains("PROFORMA", ignoreCase = true) -> "PROFORMA INVOICE"
            isGst -> "TAX INVOICE"
            else -> "INVOICE"
        }

        val sellerBlock = buildString {
            if (businessAddress.isNotBlank()) append("${e(businessAddress)}<br/>")
            val cityPin = listOfNotNull(
                businessCity.takeIf { it.isNotBlank() },
                businessPin.takeIf { it.isNotBlank() }
            ).joinToString("-")
            if (cityPin.isNotBlank()) append("$cityPin<br/>")
            if (businessPhone.isNotBlank()) append("Ph : ${e(businessPhone)}<br/>")
            if (businessGstin.isNotBlank()) append("GSTIN/UIN: ${e(businessGstin)}<br/>")
            if (businessState.isNotBlank()) {
                append("State Name : ${e(businessState)}, Code : ${e(businessStateCode)}<br/>")
            }
            if (businessEmail.isNotBlank()) append("E-Mail : ${e(businessEmail)}<br/>")
            if (businessPan.isNotBlank()) append("PAN: ${e(businessPan)}")
        }

        val buyerBlock = buildString {
            if (partyAddress.isNotBlank()) append("${e(partyAddress)}<br/>")
            val cp = listOfNotNull(
                partyCity.takeIf { it.isNotBlank() },
                partyPin.takeIf { it.isNotBlank() }
            ).joinToString(" - ")
            if (cp.isNotBlank()) append("$cp<br/>")
            if (partyPan.isNotBlank()) append("PAN: ${e(partyPan)}<br/>")
            if (partyGstin.isNotBlank()) append("GSTIN: ${e(partyGstin)}<br/>")
            if (partyState.isNotBlank()) {
                append("State Name : ${e(partyState)}, Code : ${e(partyStateCode)}<br/>")
            }
            if (partyState.isNotBlank()) append("Place of Supply : ${e(partyState)}")
        }

        val paymentDetailsHtml = when (paymentMode.uppercase()) {
            "PART PAYMENT" -> {
                val dueText = if (voucherRenderExtras.creditDueDate.isNotBlank()) {
                    " due ${e(formatDueDate(voucherRenderExtras.creditDueDate))}"
                } else ""
                """
                <div style='font-weight:bold;'>Part Payment</div>
                <div style='font-size:10px;font-weight:bold;'>₹${f(voucherRenderExtras.partialAmountPaid)} paid via ${e(voucherRenderExtras.partialPaymentSubmode)}</div>
                <div style='font-size:10px;font-weight:bold;'>₹${f(voucherRenderExtras.remainingCreditAmount)} due$dueText</div>
                """.trimIndent()
            }
            "CREDIT" -> {
                val dueText = if (voucherRenderExtras.creditDueDate.isNotBlank()) {
                    " due ${e(formatDueDate(voucherRenderExtras.creditDueDate))}"
                } else ""
                """
                <div style='font-weight:bold;'>Credit</div>
                <div style='font-size:10px;font-weight:bold;'>₹${f(voucherRenderExtras.remainingCreditAmount.coerceAtLeast(rawTotal))} due$dueText</div>
                """.trimIndent()
            }
            else -> "<div style='font-weight:bold;'>${e(paymentMode)}</div>"
        }

        val itemRows = items.mapIndexed { idx, item ->
            val altBg = if (idx % 2 == 0) "#ffffff" else "#fafafa"
            """<tr style='background:$altBg;'>
          <td class='c'>${idx + 1}</td>
          <td><b>${e(item.productName)}</b></td>
          <td class='c'>${e(item.hsnCode)}</td>
          <td class='c'>${f(item.qty)} ${e(item.unit)}<br/>
            <span style='font-size:8px;'></span>
          </td>
          <td class='r'>${f(item.rate)}</td>
          <td class='c'>${e(item.unit)}</td>
          <td class='r'>${f(item.taxableAmount)}</td>
        </tr>"""
        }.joinToString("\n")

        val chargeRows = additionalCharges.joinToString("\n") { ch ->
            """<tr>
          <td></td>
          <td class='r'><i>${e(ch.label)}</i></td>
          <td></td><td></td><td></td><td></td>
          <td class='r'>${f(ch.amount)}</td>
        </tr>"""
        }

        val gstRows = buildString {
            if (showGst) {
                if (isIntrastate) {
                    if (cgstTotal > 0.001) append("""
                <tr>
                  <td></td>
                  <td colspan='4' class='r'><b><i>OUTPUT CGST</i></b></td>
                  <td></td>
                  <td class='r'><b>${f(cgstTotal)}</b></td>
                </tr>""")
                    if (sgstTotal > 0.001) append("""
                <tr>
                  <td></td>
                  <td colspan='4' class='r'><b><i>OUTPUT SGST</i></b></td>
                  <td></td>
                  <td class='r'><b>${f(sgstTotal)}</b></td>
                </tr>""")
                } else {
                    if (igstTotal > 0.001) append("""
                <tr>
                  <td></td>
                  <td colspan='4' class='r'><b><i>OUTPUT IGST</i></b></td>
                  <td></td>
                  <td class='r'><b>${f(igstTotal)}</b></td>
                </tr>""")
                }
                if (kotlin.math.abs(roundOff) > 0.001) append("""
            <tr>
              <td></td>
              <td colspan='4' class='r'><b><i>ROUND OFF (S)</i></b></td>
              <td></td>
              <td class='r'><b>${f(roundOff)}</b></td>
            </tr>""")
            }
        }

        val emptyRows = (1..8).joinToString("\n") {
            "<tr class='spacer' style='height:18px;'><td>&nbsp;</td><td></td><td></td><td></td><td></td><td></td><td></td></tr>"
        }

        val gstBreakupRows = items.groupBy { it.gstRate }.entries.joinToString("\n") { (rate, grpItems) ->
            val t = grpItems.sumOf { it.taxableAmount }
            val c = grpItems.sumOf { it.cgstAmount }
            val s = grpItems.sumOf { it.sgstAmount }
            val ig = grpItems.sumOf { it.igstAmount }
            val hsn = grpItems.firstOrNull()?.hsnCode ?: ""
            val halfRate = rate / 2.0
            """<tr>
          <td class='c'>${e(hsn)}</td>
          <td class='r'>${f(t)}</td>
          <td class='c'>${if (isIntrastate) "$halfRate%" else ""}</td>
          <td class='r'>${if (isIntrastate) f(c) else ""}</td>
          <td class='c'>${if (isIntrastate) "$halfRate%" else ""}</td>
          <td class='r'>${if (isIntrastate) f(s) else ""}</td>
          <td class='c'>${if (!isIntrastate) "$rate%" else ""}</td>
          <td class='r'>${if (!isIntrastate) f(ig) else ""}</td>
          <td class='r b'>${f(c + s + ig)}</td>
        </tr>"""
        }

        val bankSection = if (businessBankName.isNotBlank()) """
        <div style='font-size:10px;'>
          <div style='font-weight:bold;margin-bottom:3px;'>Company's Bank Details</div>
          <table style='border:none;font-size:10px;'>
            <tr><td style='border:none;padding:1px 6px 1px 0;color:#444;'>Bank Name:</td>
                <td style='border:none;padding:1px 0;font-weight:bold;'>${e(businessBankName)}</td></tr>
            <tr><td style='border:none;padding:1px 6px 1px 0;color:#444;'>A/c No.:</td>
                <td style='border:none;padding:1px 0;font-weight:bold;'>${e(businessAccountNo)}</td></tr>
            <tr><td style='border:none;padding:1px 6px 1px 0;color:#444;'>IFS Code:</td>
                <td style='border:none;padding:1px 0;font-weight:bold;'>${e(businessIfsc)}</td></tr>
            <tr><td style='border:none;padding:1px 6px 1px 0;color:#444;'>Branch:</td>
                <td style='border:none;padding:1px 0;font-weight:bold;'>${e(businessBankBranch)}</td></tr>
          </table>
        </div>""" else ""

        val declarationSection = if (narration.isNotBlank()) """
        <div style='margin-top:8px;font-size:9px;'>
          <div style='font-weight:bold;'>Declaration</div>
          <div style='margin-top:2px;line-height:1.5;color:#333;'>
            TERMS &amp; CONDITIONS<br/>${e(narration)}
          </div>
        </div>""" else ""

        val amountWords = "Indian Rupees ${convertToWords(netAmount)} Only"
        val taxAmountWords = "Indian Rupees ${convertToWords(totalGst)} Only"

        return """<!DOCTYPE html>
<html>
<head>
<meta charset='UTF-8'/>
<meta name='viewport' content='width=device-width'/>
<style>
  * { margin:0; padding:0; box-sizing:border-box; }
  body { font-family:Arial,Helvetica,sans-serif; font-size:11px; color:#000000; background:#ffffff; }
  .page { width:100%; padding:16px 20px; background:white; }
  table { width:100%; border-collapse:collapse; }
  td, th { border:1px solid #000000; padding:3px 5px; vertical-align:top; }
  .nb td { border:none; }
  .b { font-weight:bold; }
  .c { text-align:center; }
  .r { text-align:right; }
  .heading { font-size:18px; font-weight:bold; text-align:center; letter-spacing:1.4px; padding:2px 0 6px 0; margin-bottom:2px; }
  .sublabel { font-size:9px; color:#555555; }
  .aw { font-weight:bold; font-size:11px; padding:5px 6px; }
  .th { background-color:#f0f0f0; font-weight:bold; font-size:10px; }
  .gth { background-color:#e8e8e8; font-weight:bold; font-size:10px; text-align:center; }
  .tot { font-weight:bold; background-color:#f5f5f5; }
  .spacer td { border-left:1px solid #000; border-right:1px solid #000; border-top:none; border-bottom:none; }
</style>
</head>
<body>
<div class='page'>
<div class='heading'>$invoiceTitle</div>
<table>
<tr>
  <td style='width:60%;border-right:2px solid #000000;vertical-align:top;'>
    <table class='nb' style='margin-bottom:6px;'>
    <tr>
      <td style='border:none;width:100px;padding:0;vertical-align:middle;'>$logoHtml</td>
      <td style='border:none;padding:0 0 0 8px;vertical-align:middle;'>
        <div style='font-size:14px;font-weight:bold;'>${e(businessName)}</div>
      </td>
    </tr>
    </table>
    <div style='line-height:1.75;font-size:10.5px;padding-bottom:8px;'>$sellerBlock</div>
    <div style='border-top:1px solid #000000;padding-top:6px;margin-top:2px;'>
      <div class='sublabel'>Buyer (Bill to)</div>
      <div style='font-size:12px;font-weight:bold;margin-top:3px;margin-bottom:3px;'>${e(partyName)}</div>
      <div style='line-height:1.75;font-size:10.5px;'>$buyerBlock</div>
    </div>
  </td>
  <td style='width:40%;vertical-align:top;'>
    <table style='width:100%;border:none;'>
      <tr>
        <td style='border:none;border-bottom:1px solid #cccccc;padding:4px;'>
          <div class='sublabel'>Invoice No.</div>
          <div style='font-weight:bold;font-size:11px;'>${e(voucherNo)}</div>
        </td>
        <td style='border:none;border-bottom:1px solid #cccccc;border-left:1px solid #cccccc;padding:4px;'>
          <div class='sublabel'>Dated</div>
          <div style='font-weight:bold;font-size:11px;'>${e(voucherDate)}</div>
        </td>
      </tr>
      <tr><td colspan='2' style='border:none;border-bottom:1px solid #cccccc;padding:4px;'>
        <div class='sublabel'>Mode/Terms of Payment</div>
        <div style='font-weight:bold;'>$paymentDetailsHtml</div>
      </td></tr>
      <tr><td colspan='2' style='border:none;border-bottom:1px solid #cccccc;padding:4px;'>
        <div class='sublabel'>Reference No. &amp; Date</div>
        <div>${e(referenceNo)}</div>
      </td></tr>
      <tr><td colspan='2' style='border:none;border-bottom:1px solid #cccccc;padding:4px;'>
        <div class='sublabel'>Buyer's Order No.</div>
        <div>${e(buyerOrderNo)}</div>
      </td></tr>
      <tr><td colspan='2' style='border:none;padding:4px;'>
        <div class='sublabel'>Terms of Delivery</div>
        <div>${e(termsOfDelivery)}</div>
      </td></tr>
    </table>
  </td>
</tr>
</table>
<table>
  <tr class='th'>
    <th style='width:4%;'>Sl<br/>No.</th>
    <th style='width:36%;'>Description of Goods</th>
    <th style='width:10%;'>HSN/SAC</th>
    <th style='width:13%;'>Quantity</th>
    <th style='width:9%;'>Rate</th>
    <th style='width:6%;'>per</th>
    <th style='width:12%;text-align:right;'>Amount</th>
  </tr>
  $itemRows
  $chargeRows
  $gstRows
  $emptyRows
  <tr class='tot'>
    <td></td>
    <td style='font-weight:bold;'>Total</td>
    <td></td>
    <td class='c' style='font-weight:bold;'>${f(totalQty)} ${e(qtyUnit)}</td>
    <td></td>
    <td></td>
    <td class='r' style='font-weight:bold;'>${f(netAmount)}</td>
  </tr>
</table>
<table>
  <tr>
    <td style='width:72%;'>
      <div class='sublabel'>Amount Chargeable (in words)</div>
      <div class='aw'>Indian Rupees ${convertToWords(netAmount)} Only</div>
    </td>
    <td style='width:28%;text-align:right;vertical-align:bottom;font-size:9px;font-style:italic;color:#000000;'>E. &amp; O.E</td>
  </tr>
</table>
${if (showGst) """
<table>
  <tr>
    <th class='gth' rowspan='2'>HSN/SAC</th>
    <th class='gth' rowspan='2'>Taxable<br/>Value</th>
    <th class='gth' colspan='2'>CGST</th>
    <th class='gth' colspan='2'>SGST/UTGST</th>
    <th class='gth' colspan='2'>IGST</th>
    <th class='gth' rowspan='2'>Total<br/>Tax Amount</th>
  </tr>
  <tr>
    <th class='gth'>Rate</th><th class='gth'>Amount</th>
    <th class='gth'>Rate</th><th class='gth'>Amount</th>
    <th class='gth'>Rate</th><th class='gth'>Amount</th>
  </tr>
  $gstBreakupRows
  <tr class='tot'>
    <td style='font-weight:bold;'>Total</td>
    <td class='r b'>${f(taxableTotal)}</td>
    <td></td><td class='r b'>${f(cgstTotal)}</td>
    <td></td><td class='r b'>${f(sgstTotal)}</td>
    <td></td><td class='r b'>${f(igstTotal)}</td>
    <td class='r b'>${f(cgstTotal + sgstTotal + igstTotal)}</td>
  </tr>
</table>
<div style='padding:5px 6px;border:1px solid #000000;border-top:none;font-size:10px;'>
  <b>Tax Amount (in words) :</b>
  $taxAmountWords
</div>
""" else ""}
<table style='margin-top:8px;'>
  <tr>
    <td style='width:55%;vertical-align:top;border-right:1px solid #000000;'>$bankSection$declarationSection</td>
    <td style='width:10%;border:none;'></td>
    <td style='width:35%;text-align:right;vertical-align:bottom;border:1px solid #000000;padding:8px;'>
      <div style='font-weight:bold;font-size:10px;'>for ${e(businessName)}</div>
      <div style='margin:10px 0 4px 0;'>$sigHtml</div>
      <div style='border-top:1px solid #000000;margin:4px 0;'></div>
      <div style='font-size:9px;'>Authorised Signatory</div>
    </td>
  </tr>
</table>
<div style='text-align:center;margin-top:10px;font-size:10px;color:#666666;'>${e(businessCity.uppercase())}</div>
</div>
</body>
</html>"""
    }

    private fun buildInvoiceHtmlReference(
        voucherNo: String,
        voucherDate: String,
        paymentMode: String,
        referenceNo: String,
        buyerOrderNo: String,
        termsOfDelivery: String,
        transportName: String,
        transportVehicle: String,
        transportLrNo: String,
        transportDestination: String,
        narration: String,
        invoiceTitleDefault: String = "TAX INVOICE",
        businessName: String,
        businessAddress: String,
        businessCity: String,
        businessPin: String,
        businessState: String,
        businessStateCode: String,
        businessPhone: String,
        businessEmail: String,
        businessGstin: String,
        businessPan: String,
        businessBankName: String,
        businessAccountNo: String,
        businessIfsc: String,
        businessBankBranch: String,
        businessLogoPath: String,
        businessSignaturePath: String,
        partyName: String,
        partyAddress: String,
        partyCity: String,
        partyPin: String,
        partyState: String,
        partyStateCode: String,
        partyGstin: String,
        partyPan: String,
        items: List<VoucherItem>,
        additionalCharges: List<AdditionalCharge>,
        isGst: Boolean,
        isIntrastate: Boolean,
        voucherRenderExtras: VoucherRenderExtras
    ): String {
        val taxableTotal = items.sumOf { it.taxableAmount }
        val cgstTotal = items.sumOf { it.cgstAmount }
        val sgstTotal = items.sumOf { it.sgstAmount }
        val igstTotal = items.sumOf { it.igstAmount }
        val totalQty = items.sumOf { it.qty }
        val qtyUnit = items.firstOrNull()?.unit.orEmpty()
        val chargesTotal = additionalCharges.sumOf { it.amount }
        val rawTotal = taxableTotal + cgstTotal + sgstTotal + igstTotal + chargesTotal
        val netAmount = kotlin.math.round(rawTotal).toDouble()
        val roundOff = netAmount - rawTotal
        val totalGst = cgstTotal + sgstTotal + igstTotal
        val showGst = isGst && totalGst > 0.001

        fun f(n: Double) = "%,.2f".format(Locale.ENGLISH, n)
        fun fq(n: Double) = "%.3f".format(Locale.ENGLISH, n)
        fun e(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        fun formatDueDate(raw: String): String {
            val millis = raw.toLongOrNull() ?: return raw
            return SimpleDateFormat("dd-MMM-yy", Locale.ENGLISH).format(Date(millis))
        }
        fun wordsOnly(amount: Double): String = convertToWords(amount)
            .removePrefix("Rupees ")
            .removeSuffix(" Only")
            .trim()
        fun detailRow(label: String, value: String, bold: Boolean = false, last: Boolean = false): String {
            val borderStyle = if (last) "border-bottom:none;" else "border-bottom:1px solid #cccccc;"
            val renderedValue = if (bold) {
                "<div style='font-weight:bold;font-size:11px;'>${e(value)}</div>"
            } else {
                "<div>${e(value)}</div>"
            }
            return """
                <tr>
                  <td style='border:none;$borderStyle padding:4px;'>
                    <div style='font-size:9px;color:#555;'>$label</div>
                    $renderedValue
                  </td>
                </tr>
            """.trimIndent()
        }

        val invoiceTitle = when {
            invoiceTitleDefault.contains("PROFORMA", ignoreCase = true) -> "PROFORMA INVOICE"
            businessGstin.isNotBlank() -> "TAX INVOICE"
            else -> "INVOICE"
        }
        val logoHtml = if (businessLogoPath.isNotBlank() && File(businessLogoPath).exists()) {
            "<img src='${fileUri(businessLogoPath)}' style='max-height:65px;max-width:120px;object-fit:contain;display:block;'/>"
        } else ""
        val signatureHtml = if (businessSignaturePath.isNotBlank() && File(businessSignaturePath).exists()) {
            "<div style='margin:8px 0 4px 0;'><img src='${fileUri(businessSignaturePath)}' style='max-height:55px;max-width:160px;object-fit:contain;display:block;margin-left:auto;'/></div>"
        } else {
            "<div style='height:55px;'></div>"
        }

        val sellerLines = buildList {
            if (businessAddress.isNotBlank()) add(e(businessAddress).replace("\n", "<br/>"))
            if (businessPhone.isNotBlank()) add("Ph : ${e(businessPhone)}")
            if (businessGstin.isNotBlank()) add("GSTIN/UIN: ${e(businessGstin)}")
            if (businessState.isNotBlank()) add("State Name : ${e(businessState)}, Code : ${e(businessStateCode)}")
            if (businessEmail.isNotBlank()) add("E-Mail : ${e(businessEmail)}")
            if (businessPan.isNotBlank()) add("PAN: ${e(businessPan)}")
        }.joinToString("<br/>")
        val buyerCityPin = listOfNotNull(
            partyCity.takeIf { it.isNotBlank() }?.let(::e),
            partyPin.takeIf { it.isNotBlank() }?.let(::e)
        ).joinToString(" - ")
        val buyerLines = buildList {
            if (partyAddress.isNotBlank()) add(e(partyAddress).replace("\n", "<br/>"))
            if (buyerCityPin.isNotBlank()) add(buyerCityPin)
            if (partyPan.isNotBlank()) add("PAN: ${e(partyPan)}")
            if (partyGstin.isNotBlank()) add("GSTIN: ${e(partyGstin)}")
            if (partyState.isNotBlank()) add("State Name : ${e(partyState)}, Code : ${e(partyStateCode)}")
            if (partyState.isNotBlank()) add("Place of Supply   : ${e(partyState)}")
        }.joinToString("<br/>")

        val normalizedPaymentMode = paymentMode.uppercase(Locale.ENGLISH).replace("_", " ").trim()
        val paymentDetailsHtml = when {
            normalizedPaymentMode == "PART PAYMENT" -> """
                <div style='font-weight:bold;'>Part Payment</div>
                <div style='font-size:9px;'>&#8377;${f(voucherRenderExtras.partialAmountPaid)} paid via ${e(voucherRenderExtras.partialPaymentSubmode.ifBlank { "CASH" })}</div>
                <div style='font-size:9px;'>&#8377;${f(voucherRenderExtras.remainingCreditAmount)} due ${e(formatDueDate(voucherRenderExtras.creditDueDate))}</div>
            """.trimIndent()
            normalizedPaymentMode == "CREDIT" -> """
                <div style='font-weight:bold;'>Credit</div>
                <div style='font-size:9px;'>Full amount due ${e(formatDueDate(voucherRenderExtras.creditDueDate))}</div>
            """.trimIndent()
            normalizedPaymentMode in setOf("CASH", "BANK", "UPI", "CHEQUE") -> "<div style='font-weight:bold;'>${e(normalizedPaymentMode)}</div>"
            else -> "<div style='font-weight:bold;'>${e(paymentMode)}</div>"
        }

        val transportRows = listOfNotNull(
            transportName.takeIf { it.isNotBlank() }?.let { "Transporter" to it },
            transportVehicle.takeIf { it.isNotBlank() }?.let { "Vehicle No." to it },
            transportLrNo.takeIf { it.isNotBlank() }?.let { "LR/GR No." to it },
            transportDestination.takeIf { it.isNotBlank() }?.let { "Destination" to it }
        )
        val rightRows = buildList {
            add("""
                <tr>
                  <td style='border:none;border-bottom:1px solid #cccccc;padding:4px;'>
                    <div style='display:flex;'>
                      <div style='width:50%;'>
                        <div style='font-size:9px;color:#555;'>Invoice No.</div>
                        <div style='font-weight:bold;font-size:11px;'>${e(voucherNo)}</div>
                      </div>
                      <div style='width:50%;border-left:1px solid #cccccc;padding-left:6px;'>
                        <div style='font-size:9px;color:#555;'>Dated</div>
                        <div style='font-weight:bold;font-size:11px;'>${e(voucherDate)}</div>
                      </div>
                    </div>
                  </td>
                </tr>
            """.trimIndent())
            add("<tr><td style='border:none;border-bottom:1px solid #cccccc;padding:4px;'><div style='font-size:9px;color:#555;'>Mode/Terms of Payment</div>$paymentDetailsHtml</td></tr>")
            add(detailRow("Reference No. & Date", referenceNo))
            add(detailRow("Buyer's Order No.", buyerOrderNo))
            add(detailRow("Terms of Delivery", termsOfDelivery, last = transportRows.isEmpty()))
            transportRows.forEachIndexed { index, pair ->
                add(detailRow(pair.first, pair.second, bold = true, last = index == transportRows.lastIndex))
            }
        }.joinToString("")

        val itemRows = items.mapIndexed { index, item ->
            val altBg = if (index % 2 == 0) "#ffffff" else "#fafafa"
            """
                <tr style='background:$altBg;'>
                  <td style='text-align:center;'>${index + 1}</td>
                  <td style='font-weight:bold;'>${e(item.productName)}</td>
                  <td style='text-align:center;'>${e(item.hsnCode)}</td>
                  <td style='text-align:center;'>${fq(item.qty)} ${e(item.unit)}<br/><span style='font-size:8px;'></span></td>
                  <td style='text-align:right;'>${f(item.rate)}</td>
                  <td style='text-align:center;'>${e(item.unit)}</td>
                  <td style='text-align:right;'>${f(item.taxableAmount)}</td>
                </tr>
            """.trimIndent()
        }.joinToString("")
        val chargeRows = additionalCharges.joinToString("") { charge ->
            "<tr><td></td><td colspan='5' style='text-align:right;font-style:italic;font-weight:bold;'>${e(charge.label)}</td><td style='text-align:right;'>${f(charge.amount)}</td></tr>"
        }
        val gstRows = buildString {
            if (showGst) {
                if (isIntrastate) {
                    if (cgstTotal > 0.001) append("<tr><td></td><td colspan='5' style='text-align:right;font-style:italic;font-weight:bold;'>OUTPUT CGST</td><td style='text-align:right;'>${f(cgstTotal)}</td></tr>")
                    if (sgstTotal > 0.001) append("<tr><td></td><td colspan='5' style='text-align:right;font-style:italic;font-weight:bold;'>OUTPUT SGST</td><td style='text-align:right;'>${f(sgstTotal)}</td></tr>")
                } else if (igstTotal > 0.001) {
                    append("<tr><td></td><td colspan='5' style='text-align:right;font-style:italic;font-weight:bold;'>OUTPUT IGST</td><td style='text-align:right;'>${f(igstTotal)}</td></tr>")
                }
            }
            if (kotlin.math.abs(roundOff) > 0.001) append("<tr><td></td><td colspan='5' style='text-align:right;font-style:italic;font-weight:bold;'>ROUND OFF (S)</td><td style='text-align:right;'>${f(roundOff)}</td></tr>")
        }
        val spacerRows = (1..8).joinToString("") {
            """
                <tr style='height:22px;'>
                  <td style='border-left:1px solid #000;border-right:1px solid #000;border-top:none;border-bottom:none;'></td>
                  <td style='border-left:1px solid #000;border-right:1px solid #000;border-top:none;border-bottom:none;'></td>
                  <td style='border-left:1px solid #000;border-right:1px solid #000;border-top:none;border-bottom:none;'></td>
                  <td style='border-left:1px solid #000;border-right:1px solid #000;border-top:none;border-bottom:none;'></td>
                  <td style='border-left:1px solid #000;border-right:1px solid #000;border-top:none;border-bottom:none;'></td>
                  <td style='border-left:1px solid #000;border-right:1px solid #000;border-top:none;border-bottom:none;'></td>
                  <td style='border-left:1px solid #000;border-right:1px solid #000;border-top:none;border-bottom:none;'></td>
                </tr>
            """.trimIndent()
        }
        val gstBreakupRows = items.groupBy { it.hsnCode to it.gstRate }.entries.sortedBy { it.key.second }.joinToString("") { (key, groupedItems) ->
            val hsn = key.first
            val rate = key.second
            val taxable = groupedItems.sumOf { it.taxableAmount }
            val groupedCgst = groupedItems.sumOf { it.cgstAmount }
            val groupedSgst = groupedItems.sumOf { it.sgstAmount }
            val groupedIgst = groupedItems.sumOf { it.igstAmount }
            val halfRate = rate / 2.0
            """
                <tr>
                  <td style='border:1px solid #000;padding:4px;text-align:center;'>${e(hsn)}</td>
                  <td style='border:1px solid #000;padding:4px;text-align:right;'>${f(taxable)}</td>
                  <td style='border:1px solid #000;padding:4px;text-align:center;'>${if (isIntrastate) "${f(halfRate)}%" else ""}</td>
                  <td style='border:1px solid #000;padding:4px;text-align:right;'>${if (isIntrastate) f(groupedCgst) else ""}</td>
                  <td style='border:1px solid #000;padding:4px;text-align:center;'>${if (isIntrastate) "${f(halfRate)}%" else ""}</td>
                  <td style='border:1px solid #000;padding:4px;text-align:right;'>${if (isIntrastate) f(groupedSgst) else ""}</td>
                  <td style='border:1px solid #000;padding:4px;text-align:center;'>${if (!isIntrastate) "${f(rate)}%" else ""}</td>
                  <td style='border:1px solid #000;padding:4px;text-align:right;'>${if (!isIntrastate) f(groupedIgst) else ""}</td>
                  <td style='border:1px solid #000;padding:4px;text-align:right;'>${f(groupedCgst + groupedSgst + groupedIgst)}</td>
                </tr>
            """.trimIndent()
        }
        val bankSection = if (businessBankName.isBlank()) "" else buildString {
            append("<div style='font-weight:bold;font-size:10px;margin-bottom:4px;'>Company's Bank Details</div><table style='border:none;font-size:10px;width:auto;'>")
            if (businessBankName.isNotBlank()) append("<tr><td style='border:none;padding:1px 8px 1px 0;color:#444;'>Bank Name :</td><td style='border:none;padding:1px 0;font-weight:bold;'>${e(businessBankName)}</td></tr>")
            if (businessAccountNo.isNotBlank()) append("<tr><td style='border:none;padding:1px 8px 1px 0;color:#444;'>A/c No. :</td><td style='border:none;padding:1px 0;font-weight:bold;'>${e(businessAccountNo)}</td></tr>")
            if (businessIfsc.isNotBlank()) append("<tr><td style='border:none;padding:1px 8px 1px 0;color:#444;'>IFS Code :</td><td style='border:none;padding:1px 0;font-weight:bold;'>${e(businessIfsc)}</td></tr>")
            if (businessBankBranch.isNotBlank()) append("<tr><td style='border:none;padding:1px 8px 1px 0;color:#444;'>Branch :</td><td style='border:none;padding:1px 0;font-weight:bold;'>${e(businessBankBranch)}</td></tr>")
            append("</table>")
        }
        val declarationSection = if (narration.isBlank()) {
            ""
        } else {
            "<div style='margin-top:8px;'><div style='font-weight:bold;font-size:9px;'>Declaration</div><div style='font-size:9px;line-height:1.5;color:#333;margin-top:2px;'>TERMS &amp; CONDITIONS<br/>${e(narration).replace("\n", "<br/>")}</div></div>"
        }
        val amountWords = wordsOnly(netAmount)
        val taxWords = wordsOnly(totalGst)
        val gstBreakupSection = if (!showGst) {
            ""
        } else {
            """
            <table style='width:100%;border-collapse:collapse;'>
              <tr>
                <th rowspan='2' style='background:#e8e8e8;font-weight:bold;font-size:10px;text-align:center;border:1px solid #000;padding:4px;'>HSN/SAC</th>
                <th rowspan='2' style='background:#e8e8e8;font-weight:bold;font-size:10px;text-align:center;border:1px solid #000;padding:4px;'>Taxable<br/>Value</th>
                <th colspan='2' style='background:#e8e8e8;font-weight:bold;font-size:10px;text-align:center;border:1px solid #000;padding:4px;'>CGST</th>
                <th colspan='2' style='background:#e8e8e8;font-weight:bold;font-size:10px;text-align:center;border:1px solid #000;padding:4px;'>SGST/UTGST</th>
                <th colspan='2' style='background:#e8e8e8;font-weight:bold;font-size:10px;text-align:center;border:1px solid #000;padding:4px;'>IGST</th>
                <th rowspan='2' style='background:#e8e8e8;font-weight:bold;font-size:10px;text-align:center;border:1px solid #000;padding:4px;'>Total<br/>Tax Amount</th>
              </tr>
              <tr>
                <th style='background:#e8e8e8;font-weight:bold;font-size:10px;text-align:center;border:1px solid #000;padding:4px;'>Rate</th>
                <th style='background:#e8e8e8;font-weight:bold;font-size:10px;text-align:center;border:1px solid #000;padding:4px;'>Amount</th>
                <th style='background:#e8e8e8;font-weight:bold;font-size:10px;text-align:center;border:1px solid #000;padding:4px;'>Rate</th>
                <th style='background:#e8e8e8;font-weight:bold;font-size:10px;text-align:center;border:1px solid #000;padding:4px;'>Amount</th>
                <th style='background:#e8e8e8;font-weight:bold;font-size:10px;text-align:center;border:1px solid #000;padding:4px;'>Rate</th>
                <th style='background:#e8e8e8;font-weight:bold;font-size:10px;text-align:center;border:1px solid #000;padding:4px;'>Amount</th>
              </tr>
              $gstBreakupRows
              <tr style='font-weight:bold;background:#f0f0f0;'>
                <td style='border:1px solid #000;padding:4px;text-align:center;'>Total</td>
                <td style='border:1px solid #000;padding:4px;text-align:right;'>${f(taxableTotal)}</td>
                <td style='border:1px solid #000;padding:4px;'></td>
                <td style='border:1px solid #000;padding:4px;text-align:right;'>${f(cgstTotal)}</td>
                <td style='border:1px solid #000;padding:4px;'></td>
                <td style='border:1px solid #000;padding:4px;text-align:right;'>${f(sgstTotal)}</td>
                <td style='border:1px solid #000;padding:4px;'></td>
                <td style='border:1px solid #000;padding:4px;text-align:right;'>${f(igstTotal)}</td>
                <td style='border:1px solid #000;padding:4px;text-align:right;'>${f(totalGst)}</td>
              </tr>
              <tr>
                <td colspan='9' style='border:1px solid #000;padding:5px;font-size:10px;'>
                  <b>Tax Amount (in words) : </b>
                  Indian Rupees $taxWords Only
                </td>
              </tr>
            </table>
            """.trimIndent()
        }

        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset='UTF-8'/>
              <meta name='viewport' content='width=device-width, initial-scale=1.0'/>
              <style>
                body { margin:0; background:#FFFFFF; color:#000000; font-family:Arial,Helvetica,sans-serif; font-size:11px; }
                .page { width:794px; margin:0 auto; padding:18px 22px; }
                table { width:100%; border-collapse:collapse; table-layout:fixed; }
                td, th { border:1px solid #000000; padding:4px; vertical-align:top; }
              </style>
            </head>
            <body>
              <div class='page'>
                <div style='text-align:center;font-weight:bold;font-size:14px;letter-spacing:2px;padding-bottom:6px;'>$invoiceTitle</div>

                <table>
                  <tr>
                    <td style='width:60%;border-right:2px solid #000000;'>
                      <table style='border:none;width:100%;'>
                        <tr>
                          <td style='width:90px;border:none;vertical-align:middle;'>$logoHtml</td>
                          <td style='border:none;padding-left:8px;vertical-align:middle;'>
                            <div style='font-size:14px;font-weight:bold;color:#000000;'>${e(businessName)}</div>
                          </td>
                        </tr>
                      </table>
                      <div style='line-height:1.75;font-size:11px;'>$sellerLines</div>
                      <hr style='border:none;border-top:1px solid #000000;margin-top:8px;margin-bottom:6px;'/>
                      <div style='font-size:9px;color:#555555;'>Buyer (Bill to)</div>
                      <div style='font-size:12px;font-weight:bold;margin-top:3px;margin-bottom:3px;'>${e(partyName)}</div>
                      <div style='line-height:1.75;font-size:11px;'>$buyerLines</div>
                    </td>
                    <td style='width:40%;padding:0;'>
                      <table style='border:none;width:100%;'>
                        $rightRows
                      </table>
                    </td>
                  </tr>
                </table>

                <table style='width:100%;border-collapse:collapse;'>
                  <tr style='background:#f0f0f0;font-weight:bold;font-size:10px;'>
                    <th style='width:4%;text-align:center;'>Sl<br/>No.</th>
                    <th style='width:35%;text-align:center;'>Description of Goods</th>
                    <th style='width:10%;text-align:center;'>HSN/SAC</th>
                    <th style='width:13%;text-align:center;'>Quantity</th>
                    <th style='width:9%;text-align:right;'>Rate</th>
                    <th style='width:6%;text-align:center;'>per</th>
                    <th style='width:13%;text-align:right;'>Amount</th>
                  </tr>
                  $itemRows
                  $chargeRows
                  $gstRows
                  $spacerRows
                  <tr style='font-weight:bold;background:#f5f5f5;'>
                    <td></td>
                    <td style='font-weight:bold;'>Total</td>
                    <td></td>
                    <td style='text-align:center;font-weight:bold;'>${fq(totalQty)} ${e(qtyUnit)}</td>
                    <td></td>
                    <td></td>
                    <td style='text-align:right;font-weight:bold;'>${f(netAmount)}</td>
                  </tr>
                </table>

                <table style='width:100%;border-collapse:collapse;'>
                  <tr>
                    <td style='width:70%;border:1px solid #000;padding:5px 6px;'>
                      <div style='font-size:9px;color:#555555;'>Amount Chargeable (in words)</div>
                      <div style='font-weight:bold;font-size:11px;'>Indian Rupees $amountWords Only</div>
                    </td>
                    <td style='width:30%;border:1px solid #000;text-align:right;vertical-align:bottom;padding:5px 6px;'>
                      <span style='font-size:9px;font-style:italic;'>E. &amp; O.E</span>
                    </td>
                  </tr>
                </table>

                $gstBreakupSection

                <table style='width:100%;border-collapse:collapse;margin-top:8px;'>
                  <tr>
                    <td style='width:55%;border:1px solid #000000;vertical-align:top;padding:6px;'>
                      $bankSection
                      $declarationSection
                    </td>
                    <td style='width:5%;border:none;'></td>
                    <td style='width:40%;border:1px solid #000000;vertical-align:bottom;text-align:right;padding:8px;'>
                      <div style='font-weight:bold;font-size:10px;'>for ${e(businessName)}</div>
                      $signatureHtml
                      <hr style='border:none;border-top:1px solid #000000;margin:4px 0;'/>
                      <div style='font-size:9px;'>Authorised Signatory</div>
                    </td>
                  </tr>
                </table>

                <p style='text-align:center;font-size:10px;color:#666666;margin-top:8px;'>${e(businessCity)}</p>
              </div>
            </body>
            </html>
        """.trimIndent()
    }
    private fun formatNumber(value: Double): String {
        return DecimalFormat("0.00").format(value)
    }

    fun buildUpiDeepLink(upiId: String, businessName: String, amount: Double, voucherNo: String): String {
        return generateUpiLink(upiId, businessName, amount, voucherNo)
    }

    fun generateUpiLink(
        upiId: String,
        businessName: String,
        amount: Double,
        invoiceNo: String
    ): String {
        val encodedName = URLEncoder.encode(businessName, "UTF-8")
        val encodedNote = URLEncoder.encode("Invoice $invoiceNo", "UTF-8")
        return "upi://pay?pa=$upiId" +
            "&pn=$encodedName" +
            "&am=${"%.2f".format(amount)}" +
            "&cu=INR" +
            "&tn=$encodedNote"
    }

    fun getQrHtml(upiLink: String, upiId: String): String {
        return if (upiId.isBlank()) "" else """
        <div style='text-align:center; padding:8px;'>
          <div style='font-size:9px; color:#444;'>
            Scan &amp; Pay via UPI
          </div>
          <div style='font-size:8px; margin-top:4px;
                      color:#1A73E8; word-break:break-all;'>
            $upiId
          </div>
        </div>
    """.trimIndent()
    }

    fun generateQrBase64(value: String, size: Int = 200): String? = null
}

fun printInvoice(
    context: Context,
    htmlContent: String,
    invoiceNo: String
) {
    val webView = WebView(context)
    configureInvoiceWebView(webView)
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(
            view: WebView?, url: String?) {
            Handler(Looper.getMainLooper()).postDelayed({
                val printManager = context
                    .getSystemService(Context.PRINT_SERVICE)
                    as PrintManager
                val printAdapter = view!!
                    .createPrintDocumentAdapter(invoiceNo)
                printManager.print(
                    invoiceNo,
                    printAdapter,
                    PrintAttributes.Builder()
                        .setMediaSize(
                            PrintAttributes.MediaSize.ISO_A4)
                        .build()
                )
            }, 300)
        }
    }
    webView.loadDataWithBaseURL(
        "file:///", htmlContent,
        "text/html", "UTF-8", null)
}

fun generatePdfFromHtml(
    context: Context,
    htmlContent: String,
    invoiceNo: String,
    onComplete: (File?) -> Unit
) {
    val webView = WebView(context)
    configureInvoiceWebView(webView)
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            if (view == null) {
                onComplete(null)
                return
            }
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    val safeName = invoiceNo.replace("/", "-").replace("\\", "-")
                    val dir = File(context.getExternalFilesDir(null), "ZeroBook/Invoices")
                    dir.mkdirs()
                    val pdfFile = File(dir, "$safeName.pdf")
                    val pageWidth = 595
                    val pageHeight = 842
                    view.measure(
                        View.MeasureSpec.makeMeasureSpec(pageWidth, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                    )
                    val contentHeight = view.measuredHeight.coerceAtLeast(pageHeight)
                    view.layout(0, 0, pageWidth, contentHeight)

                    val document = PdfDocument()
                    val totalPages = kotlin.math.ceil(contentHeight / pageHeight.toDouble()).toInt().coerceAtLeast(1)
                    for (pageIndex in 0 until totalPages) {
                        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
                        val page = document.startPage(pageInfo)
                        page.canvas.save()
                        page.canvas.translate(0f, -(pageIndex * pageHeight).toFloat())
                        view.draw(page.canvas)
                        page.canvas.restore()
                        document.finishPage(page)
                    }

                    pdfFile.outputStream().use { document.writeTo(it) }
                    document.close()
                    onComplete(pdfFile)
                    webView.destroy()
                } catch (e: Exception) {
                    onComplete(null)
                    webView.destroy()
                }
            }, 500)
        }
    }
    webView.loadDataWithBaseURL(
        "file:///", htmlContent,
        "text/html", "UTF-8", null)
}

fun configureInvoiceWebView(webView: WebView) {
    webView.settings.apply {
        allowFileAccess = true
        allowContentAccess = true
        @Suppress("DEPRECATION")
        allowFileAccessFromFileURLs = true
        @Suppress("DEPRECATION")
        allowUniversalAccessFromFileURLs = true
        javaScriptEnabled = false
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
        loadWithOverviewMode = true
        useWideViewPort = true
    }
}

fun saveInvoiceToFile(
    context: Context,
    htmlContent: String,
    invoiceNo: String
): File {
    val dir = File(
        context.getExternalFilesDir(null),
        "ZeroBook/Invoices"
    )
    dir.mkdirs()
    val safeName = invoiceNo.replace("/", "-")
    val file = File(dir, "$safeName.html")
    file.writeText(htmlContent)
    return file
}

fun shareInvoice(context: Context, file: File) {
    val uri = androidx.core.content.FileProvider
        .getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
    val intent = android.content.Intent(
        android.content.Intent.ACTION_SEND
    ).apply {
        type = "text/html"
        putExtra(
            android.content.Intent.EXTRA_STREAM, uri)
        addFlags(
            android.content.Intent
                .FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        android.content.Intent.createChooser(
            intent, "Share Invoice"))
}

