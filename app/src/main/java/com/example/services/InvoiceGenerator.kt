package com.example.services

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.CancellationSignal
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.FileProvider
import com.example.data.AdditionalCharge
import com.example.data.AppDatabase
import com.example.data.BusinessProfile
import com.example.data.Party
import com.example.data.Voucher
import com.example.data.VoucherItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
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

    data class InvoiceDocumentData(
        val voucher: Voucher,
        val items: List<VoucherItem>,
        val charges: List<AdditionalCharge>,
        val profile: BusinessProfile,
        val party: Party?,
        val extras: VoucherRenderExtras
    )

    data class InvoiceTotals(
        val totalQuantity: Double,
        val taxableAmount: Double,
        val cgst: Double,
        val sgst: Double,
        val igst: Double,
        val roundOff: Double,
        val netAmount: Double,
        val totalTaxAmount: Double
    )

    data class InvoiceTaxSummary(
        val hsnCode: String,
        val taxableValue: Double,
        val cgstRate: Double,
        val cgstAmount: Double,
        val sgstRate: Double,
        val sgstAmount: Double,
        val igstRate: Double,
        val igstAmount: Double,
        val totalTaxAmount: Double
    )

    data class InvoicePaymentSnapshot(
        val modeLabel: String,
        val previousDue: Double,
        val currentInvoiceAmount: Double,
        val advanceReceived: Double,
        val partPaymentReceived: Double,
        val balanceDue: Double,
        val outstandingAmount: Double,
        val dueDateLabel: String,
        val summaryLabel: String
    )

    data class InvoiceDocument(
        val voucherId: String,
        val financialYearCode: String,
        val invoiceNumber: String,
        val displayTitle: String,
        val issuedAtLabel: String,
        val dueDateLabel: String,
        val business: BusinessProfile,
        val buyer: Party?,
        val voucher: Voucher,
        val items: List<VoucherItem>,
        val additionalCharges: List<AdditionalCharge>,
        val paymentSnapshot: InvoicePaymentSnapshot,
        val totals: InvoiceTotals,
        val taxSummary: List<InvoiceTaxSummary>,
        val amountInWords: String,
        val taxAmountInWords: String
    )

    data class InvoiceRenderBundle(
        val document: InvoiceDocument,
        val html: String,
        val exportFileName: String,
        val cacheKey: String
    )

    private val invoiceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val invoiceDateFormat = SimpleDateFormat("dd-MMM-yy", Locale.ENGLISH)
    private val pdfResolution = PrintAttributes.Resolution("zerobook", "zerobook", 300, 300)
    private val decimalFormat = DecimalFormat("#,##0.00")
    private val ones = arrayOf(
        "", "One", "Two", "Three", "Four", "Five",
        "Six", "Seven", "Eight", "Nine", "Ten",
        "Eleven", "Twelve", "Thirteen", "Fourteen",
        "Fifteen", "Sixteen", "Seventeen", "Eighteen",
        "Nineteen"
    )
    private val tens = arrayOf(
        "", "", "Twenty", "Thirty", "Forty", "Fifty",
        "Sixty", "Seventy", "Eighty", "Ninety"
    )

    fun parseAdditionalCharges(json: String?): List<AdditionalCharge> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                AdditionalCharge(
                    label = obj.optString("label", "Other Charge"),
                    amount = obj.optDouble("amount", 0.0),
                    isTaxable = obj.optBoolean("is_taxable", false),
                    gstRate = obj.optDouble("gst_rate", 0.0),
                    gstAmount = obj.optDouble("gst_amount", 0.0)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun additionalChargesToJson(charges: List<AdditionalCharge>): String {
        val array = JSONArray()
        charges.forEach { charge ->
            array.put(
                JSONObject().apply {
                    put("label", charge.label)
                    put("amount", charge.amount)
                    put("is_taxable", charge.isTaxable)
                    put("gst_rate", charge.gstRate)
                    put("gst_amount", charge.gstAmount)
                }
            )
        }
        return array.toString()
    }

    fun primeVoucherRenderExtras(context: Context, voucherId: String) {
        // Intentionally retained as a no-op compatibility method.
    }

    suspend fun loadInvoiceDocumentData(
        context: Context,
        voucherId: String
    ): InvoiceDocumentData? {
        val db = AppDatabase.getDatabase(context)
        val voucher = db.voucherDao().getVoucherById(voucherId) ?: return null
        val profile = db.businessProfileDao().getProfileSync() ?: return null
        val items = db.voucherItemDao().getItemsForVoucherSync(voucherId)
        val party = voucher.partyId?.let { db.partyDao().getPartyById(it) }
        val charges = parseAdditionalCharges(voucher.additionalChargesJson)
        val extras = loadVoucherExtras(db, voucherId)
        return InvoiceDocumentData(
            voucher = voucher,
            items = items,
            charges = charges,
            profile = profile,
            party = party,
            extras = extras
        )
    }

    suspend fun buildRenderBundle(
        context: Context,
        voucherId: String
    ): InvoiceRenderBundle? {
        val documentData = loadInvoiceDocumentData(context, voucherId) ?: return null
        val document = buildInvoiceDocument(documentData)
        validateInvoiceDocument(document)
        val html = buildInvoiceHtml(document)
        val fileName = "${buildInvoiceFileStem(document)}.pdf"
        val cacheKey = sha256("${document.voucherId}|${document.invoiceNumber}|$html")
        return InvoiceRenderBundle(
            document = document,
            html = html,
            exportFileName = fileName,
            cacheKey = cacheKey
        )
    }

    fun buildInvoiceHtml(
        voucher: Voucher,
        items: List<VoucherItem>,
        business: BusinessProfile,
        party: Party?,
        additionalCharges: List<AdditionalCharge> = emptyList(),
        qrBase64: String? = null
    ): String {
        val totals = InvoiceTotals(
            totalQuantity = items.sumOf { it.qty },
            taxableAmount = voucher.taxableAmount,
            cgst = voucher.cgst,
            sgst = voucher.sgst,
            igst = voucher.igst,
            roundOff = voucher.roundOff,
            netAmount = voucher.netAmount,
            totalTaxAmount = voucher.cgst + voucher.sgst + voucher.igst
        )
        val paymentSnapshot = InvoicePaymentSnapshot(
            modeLabel = voucher.paymentMode,
            previousDue = 0.0,
            currentInvoiceAmount = voucher.netAmount,
            advanceReceived = 0.0,
            partPaymentReceived = 0.0,
            balanceDue = voucher.outstandingAmount,
            outstandingAmount = voucher.outstandingAmount,
            dueDateLabel = "",
            summaryLabel = voucher.paymentMode
        )
        val document = InvoiceDocument(
            voucherId = voucher.id,
            financialYearCode = voucher.financialYearCode,
            invoiceNumber = voucher.voucherNo,
            displayTitle = if (voucher.documentType.equals("PROFORMA", ignoreCase = true)) "PROFORMA INVOICE" else "TAX INVOICE",
            issuedAtLabel = invoiceDateFormat.format(Date(voucher.date)),
            dueDateLabel = "",
            business = business,
            buyer = party,
            voucher = voucher,
            items = items,
            additionalCharges = additionalCharges,
            paymentSnapshot = paymentSnapshot,
            totals = totals,
            taxSummary = buildTaxSummary(items, voucher),
            amountInWords = amountInWords(voucher.netAmount),
            taxAmountInWords = amountInWords(totals.totalTaxAmount)
        )
        return buildInvoiceHtml(document)
    }

    fun generatePdfFromVoucherId(
        context: Context,
        voucherId: String,
        onComplete: (File?, Voucher?) -> Unit
    ) {
        invoiceScope.launch {
            val bundle = buildRenderBundle(context, voucherId)
            if (bundle == null) {
                withContext(Dispatchers.Main) { onComplete(null, null) }
                return@launch
            }
            val pdfFile = runCatching {
                renderBundleToPdf(context, bundle)
            }.getOrNull()
            withContext(Dispatchers.Main) {
                onComplete(pdfFile, bundle.document.voucher)
            }
        }
    }

    suspend fun renderBundleToPdf(
        context: Context,
        bundle: InvoiceRenderBundle
    ): File {
        val cacheDir = File(context.cacheDir, "zerobook-invoice-render")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val pdfFile = File(cacheDir, "${bundle.cacheKey}.pdf")
        if (pdfFile.exists()) {
            pdfFile.delete()
        }
        renderHtmlToPdf(context, bundle.html, bundle.document.invoiceNumber, pdfFile)
        validatePdfFile(pdfFile, bundle)
        return pdfFile
    }

    suspend fun exportLatestPdf(
        context: Context,
        bundle: InvoiceRenderBundle
    ): ExportStorageManager.ExportResult {
        val pdfFile = renderBundleToPdf(context, bundle)
        return ExportStorageManager.exportFile(
            context = context,
            sourceFile = pdfFile,
            displayName = bundle.exportFileName,
            mimeType = "application/pdf",
            target = ExportTarget.Invoices
        )
    }

    suspend fun printBundle(
        context: Context,
        bundle: InvoiceRenderBundle
    ) {
        withContext(Dispatchers.Main) {
            val webView = createConfiguredWebView(context)
            try {
                loadHtmlIntoWebView(webView, bundle.html)
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val printAdapter = webView.createPrintDocumentAdapter(bundle.document.invoiceNumber)
                val attributes = buildPrintAttributes()
                printManager.print(bundle.document.invoiceNumber, printAdapter, attributes)
            } finally {
                webView.destroy()
            }
        }
    }

    fun buildUpiDeepLink(upiId: String, businessName: String, amount: Double, voucherNo: String): String =
        generateUpiLink(upiId, businessName, amount, voucherNo)

    fun generateUpiLink(
        upiId: String,
        businessName: String,
        amount: Double,
        invoiceNo: String
    ): String {
        val encodedName = java.net.URLEncoder.encode(businessName, "UTF-8")
        val encodedNote = java.net.URLEncoder.encode("Invoice $invoiceNo", "UTF-8")
        return "upi://pay?pa=$upiId&pn=$encodedName&am=${"%.2f".format(Locale.ENGLISH, amount)}&cu=INR&tn=$encodedNote"
    }

    fun getQrHtml(upiLink: String, upiId: String): String {
        return if (upiId.isBlank()) "" else {
            """
            <div style='display:flex;align-items:center;justify-content:center;width:96px;height:96px;border:1px dashed #777;color:#444;font-size:9px;text-align:center;'>
              QR
            </div>
            <div style='font-size:9px;color:#444;margin-top:4px;'>${escapeHtml(upiId)}</div>
            """.trimIndent()
        }
    }

    fun generateQrBase64(value: String, size: Int = 200): String? = null

    private fun buildInvoiceDocument(source: InvoiceDocumentData): InvoiceDocument {
        val taxSummary = buildTaxSummary(source.items, source.voucher)
        val totals = InvoiceTotals(
            totalQuantity = source.items.sumOf { it.qty },
            taxableAmount = source.voucher.taxableAmount,
            cgst = source.voucher.cgst,
            sgst = source.voucher.sgst,
            igst = source.voucher.igst,
            roundOff = source.voucher.roundOff,
            netAmount = source.voucher.netAmount,
            totalTaxAmount = source.voucher.cgst + source.voucher.sgst + source.voucher.igst
        )
        val dueDateLabel = source.extras.creditDueDate.takeIf { it.isNotBlank() }?.let(::formatDueDateText).orEmpty()
        val partPaymentReceived = source.extras.partialAmountPaid.coerceAtLeast(0.0)
        val advanceReceived = if (source.extras.isAdvance) source.voucher.netAmount else 0.0
        val paymentSnapshot = InvoicePaymentSnapshot(
            modeLabel = resolvePaymentLabel(source.voucher, source.extras),
            previousDue = 0.0,
            currentInvoiceAmount = source.voucher.netAmount,
            advanceReceived = advanceReceived,
            partPaymentReceived = partPaymentReceived,
            balanceDue = source.extras.remainingCreditAmount.coerceAtLeast(source.voucher.outstandingAmount).coerceAtLeast(0.0),
            outstandingAmount = source.voucher.outstandingAmount.coerceAtLeast(0.0),
            dueDateLabel = dueDateLabel,
            summaryLabel = buildPaymentSummaryLabel(source.voucher, source.extras)
        )
        val title = when {
            source.voucher.documentType.equals("PROFORMA", ignoreCase = true) -> "PROFORMA INVOICE"
            source.extras.isAdvance && source.voucher.type == "RECEIPT" -> "ADVANCE RECEIPT"
            source.voucher.type == "PURCHASE" -> "PURCHASE INVOICE"
            source.voucher.type == "SALE_RETURN" -> "SALES RETURN"
            source.voucher.type == "PURCHASE_RETURN" -> "PURCHASE RETURN"
            source.voucher.type == "PAYMENT" && source.extras.isAdvance -> "ADVANCE PAYMENT"
            else -> if (source.profile.gstin.isNotBlank()) "TAX INVOICE" else "INVOICE"
        }
        return InvoiceDocument(
            voucherId = source.voucher.id,
            financialYearCode = source.voucher.financialYearCode,
            invoiceNumber = source.voucher.voucherNo,
            displayTitle = title,
            issuedAtLabel = invoiceDateFormat.format(Date(source.voucher.date)),
            dueDateLabel = dueDateLabel,
            business = source.profile,
            buyer = source.party,
            voucher = source.voucher,
            items = source.items,
            additionalCharges = source.charges,
            paymentSnapshot = paymentSnapshot,
            totals = totals,
            taxSummary = taxSummary,
            amountInWords = amountInWords(source.voucher.netAmount),
            taxAmountInWords = amountInWords(totals.totalTaxAmount)
        )
    }

    private suspend fun validateInvoiceDocument(document: InvoiceDocument) {
        check(document.invoiceNumber.isNotBlank()) { "Invoice number is missing." }
        check(document.business.businessName.isNotBlank()) { "Business name is missing." }
        check(document.items.isNotEmpty() || document.voucher.type in setOf("RECEIPT", "PAYMENT")) { "Invoice line items are missing." }
        val computedTaxable = document.items.sumOf { it.taxableAmount } + document.additionalCharges.sumOf { if (it.isTaxable) it.amount else 0.0 }
        check(abs(computedTaxable - document.voucher.taxableAmount) < 1.0) { "Taxable amount validation failed." }
        val computedNet = document.voucher.taxableAmount +
            document.voucher.cgst +
            document.voucher.sgst +
            document.voucher.igst +
            document.additionalCharges.sumOf { it.amount } +
            document.voucher.roundOff
        check(abs(computedNet - document.voucher.netAmount) < 1.0) { "Net amount validation failed." }
        document.business.logoPath?.takeIf { it.isNotBlank() }?.let {
            check(File(it).exists()) { "Business logo file not found." }
        }
    }

    private fun buildInvoiceHtml(document: InvoiceDocument): String {
        val business = document.business
        val buyer = document.buyer
        val voucher = document.voucher
        val totals = document.totals
        val showLogo = business.showLogo && !business.logoPath.isNullOrBlank() && File(business.logoPath!!).exists()
        val showSignature = business.showSignature && !business.signaturePath.isNullOrBlank() && File(business.signaturePath!!).exists()
        val isIntrastate = !voucher.isIgst && buyer?.stateCode == business.stateCode

        val itemRows = document.items.mapIndexed { index, item ->
            val description = buildString {
                append(escapeHtml(item.productName))
                if (item.discount > 0.0) {
                    append("<div class='subtext'>Discount: ${escapeHtml(item.discountType)} ${formatMoney(item.discount)}</div>")
                }
            }
            """
            <tr>
              <td class='center'>${index + 1}</td>
              <td>$description</td>
              <td class='center'>${escapeHtml(item.hsnCode)}</td>
              <td class='right'>${formatQty(item.qty)}</td>
              <td class='center'>${escapeHtml(item.unit)}</td>
              <td class='right'>${formatMoney(item.rate)}</td>
              <td class='right'>${formatMoney(item.taxableAmount)}</td>
            </tr>
            """.trimIndent()
        }.joinToString("\n")

        val adjustmentRows = buildList {
            if (voucher.cgst > 0.0) add("CGST" to voucher.cgst)
            if (voucher.sgst > 0.0) add("SGST" to voucher.sgst)
            if (voucher.igst > 0.0) add("IGST" to voucher.igst)
            document.additionalCharges.forEach { add(it.label.ifBlank { "Additional Charge" } to it.amount) }
            if (abs(voucher.roundOff) > 0.0) add("Round Off" to voucher.roundOff)
        }.joinToString("\n") { (label, amount) ->
            """
            <tr>
              <td colspan='6' class='label-cell'>${escapeHtml(label)}</td>
              <td class='right'>${formatSignedMoney(amount)}</td>
            </tr>
            """.trimIndent()
        }

        val taxRows = document.taxSummary.joinToString("\n") { summary ->
            """
            <tr>
              <td>${escapeHtml(summary.hsnCode)}</td>
              <td class='right'>${formatMoney(summary.taxableValue)}</td>
              <td class='center'>${rateLabel(summary.cgstRate)}</td>
              <td class='right'>${formatMoney(summary.cgstAmount)}</td>
              <td class='center'>${rateLabel(summary.sgstRate)}</td>
              <td class='right'>${formatMoney(summary.sgstAmount)}</td>
              <td class='center'>${rateLabel(summary.igstRate)}</td>
              <td class='right'>${formatMoney(summary.igstAmount)}</td>
              <td class='right'>${formatMoney(summary.totalTaxAmount)}</td>
            </tr>
            """.trimIndent()
        }

        val transportRows = buildList {
            if (voucher.transporterName.isNotBlank()) add("Transport" to voucher.transporterName)
            if (voucher.vehicleNo.isNotBlank()) add("Vehicle No." to voucher.vehicleNo)
            if (voucher.lrNo.isNotBlank()) add("LR / GR No." to voucher.lrNo)
            if (voucher.destination.isNotBlank()) add("Destination" to voucher.destination)
            if (voucher.referenceNo.isNotBlank()) add("Reference No." to voucher.referenceNo)
            if (voucher.dispatchDocNo.isNotBlank()) add("Dispatch Doc" to voucher.dispatchDocNo)
        }.joinToString("<br/>") { "${escapeHtml(it.first)}: <strong>${escapeHtml(it.second)}</strong>" }

        val bankDetails = buildList {
            if (business.bankName.isNotBlank()) add("Bank: ${escapeHtml(business.bankName)}")
            if (business.accountNo.isNotBlank()) add("A/C No: ${escapeHtml(business.accountNo)}")
            if (business.ifsc.isNotBlank()) add("IFSC: ${escapeHtml(business.ifsc)}")
            if (business.branchName.isNotBlank()) add("Branch: ${escapeHtml(business.branchName)}")
        }.joinToString("<br/>")

        val qrSection = if (business.showUpiQr && business.upiId.isNotBlank()) {
            val upiLink = generateUpiLink(business.upiId, business.businessName, totals.netAmount, document.invoiceNumber)
            """
            <div class='payment-box'>
              <div class='section-title'>QR / UPI</div>
              ${getQrHtml(upiLink, business.upiId)}
            </div>
            """.trimIndent()
        } else {
            ""
        }

        return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset='UTF-8'/>
          <meta name='viewport' content='width=device-width, initial-scale=1.0'/>
          <style>
            @page { size: A4; margin: 10mm; }
            * { box-sizing: border-box; }
            body {
              margin: 0;
              background: #f3f4f7;
              color: #111827;
              font-family: Arial, Helvetica, sans-serif;
              -webkit-print-color-adjust: exact;
              print-color-adjust: exact;
            }
            .sheet {
              width: 210mm;
              min-height: 297mm;
              margin: 0 auto;
              padding: 9mm;
              background: #ffffff;
            }
            .title {
              text-align: center;
              font-size: 17px;
              font-weight: 700;
              letter-spacing: 0.05em;
              margin-bottom: 8px;
            }
            .frame {
              border: 1px solid #111827;
            }
            .top-grid {
              display: grid;
              grid-template-columns: 58% 42%;
              border-bottom: 1px solid #111827;
            }
            .seller, .meta-box {
              padding: 8px 10px;
              min-height: 165px;
            }
            .seller {
              border-right: 1px solid #111827;
            }
            .brand {
              display: flex;
              gap: 12px;
              align-items: flex-start;
            }
            .logo {
              width: 92px;
              max-height: 74px;
              object-fit: contain;
            }
            .business-name {
              font-size: 23px;
              font-weight: 700;
              text-transform: uppercase;
              line-height: 1.05;
              margin-bottom: 4px;
            }
            .meta-grid {
              display: grid;
              grid-template-columns: 1fr 1fr;
              border-left: 1px solid #111827;
              height: 100%;
            }
            .meta-cell {
              border-bottom: 1px solid #111827;
              border-right: 1px solid #111827;
              padding: 7px 8px;
              min-height: 56px;
            }
            .meta-cell:nth-child(2n) {
              border-right: none;
            }
            .meta-label, .section-title, .subtext {
              color: #4b5563;
              font-size: 10px;
            }
            .meta-value {
              font-size: 14px;
              font-weight: 700;
              margin-top: 3px;
            }
            .party-grid {
              display: grid;
              grid-template-columns: 58% 42%;
              border-bottom: 1px solid #111827;
            }
            .party-box, .transport-box {
              padding: 8px 10px;
              min-height: 106px;
            }
            .party-box {
              border-right: 1px solid #111827;
            }
            .body-table, .tax-table {
              width: 100%;
              border-collapse: collapse;
              table-layout: fixed;
            }
            .body-table th, .body-table td, .tax-table th, .tax-table td {
              border: 1px solid #111827;
              padding: 6px 7px;
              vertical-align: top;
              font-size: 11px;
            }
            .body-table thead th, .tax-table thead th {
              background: #f1f5f9;
              font-size: 10px;
              font-weight: 700;
            }
            .body-table { border-left: none; border-right: none; }
            .body-table th:first-child, .body-table td:first-child { border-left: none; width: 5%; }
            .body-table th:nth-child(2), .body-table td:nth-child(2) { width: 41%; }
            .body-table th:nth-child(3), .body-table td:nth-child(3) { width: 12%; }
            .body-table th:nth-child(4), .body-table td:nth-child(4) { width: 10%; }
            .body-table th:nth-child(5), .body-table td:nth-child(5) { width: 10%; }
            .body-table th:nth-child(6), .body-table td:nth-child(6) { width: 10%; }
            .body-table th:nth-child(7), .body-table td:nth-child(7) { width: 12%; border-right: none; }
            .center { text-align: center; }
            .right { text-align: right; }
            .totals-wrap {
              display: grid;
              grid-template-columns: 58% 42%;
              border-bottom: 1px solid #111827;
            }
            .totals-left {
              border-right: 1px solid #111827;
              padding: 8px 10px;
            }
            .totals-right {
              padding: 0;
            }
            .totals-table {
              width: 100%;
              border-collapse: collapse;
            }
            .totals-table td {
              border-bottom: 1px solid #111827;
              padding: 7px 10px;
              font-size: 11px;
            }
            .totals-table tr:last-child td {
              border-bottom: none;
            }
            .label-cell {
              text-align: right;
              color: #111827;
              font-weight: 600;
            }
            .grand-total td {
              background: #eef2ff;
              font-size: 14px;
              font-weight: 700;
            }
            .summary-grid {
              display: grid;
              grid-template-columns: 58% 42%;
              border-bottom: 1px solid #111827;
            }
            .words-box, .payment-box {
              padding: 8px 10px;
            }
            .words-box {
              border-right: 1px solid #111827;
            }
            .footer-grid {
              display: grid;
              grid-template-columns: 58% 42%;
            }
            .declaration-box, .signature-box {
              padding: 10px;
              min-height: 118px;
            }
            .declaration-box {
              border-right: 1px solid #111827;
            }
            .signature-box {
              display: flex;
              flex-direction: column;
              justify-content: space-between;
              align-items: flex-end;
            }
            .signature {
              max-width: 160px;
              max-height: 70px;
              object-fit: contain;
            }
            .muted { color: #6b7280; }
            .divider-line { margin: 5px 0; border-top: 1px solid #d1d5db; }
            .terms { white-space: pre-line; line-height: 1.45; font-size: 11px; }
            @media print {
              body { background: #ffffff; }
              .sheet {
                width: 100%;
                min-height: auto;
                margin: 0;
                padding: 0;
              }
            }
          </style>
        </head>
        <body>
          <div class='sheet'>
            <div class='title'>${escapeHtml(document.displayTitle)}</div>
            <div class='frame'>
              <div class='top-grid'>
                <div class='seller'>
                  <div class='brand'>
                    ${if (showLogo) "<img class='logo' src='${toFileUrl(business.logoPath!!)}' alt='Logo' />" else ""}
                    <div>
                      <div class='business-name'>${escapeHtml(business.businessName)}</div>
                      <div>${escapeHtml(business.address).replace("\n", "<br/>")}</div>
                      ${if (business.city.isNotBlank()) "<div>${escapeHtml(business.city)} - ${escapeHtml(business.pin)}</div>" else ""}
                      ${if (business.pan.isNotBlank()) "<div>PAN: ${escapeHtml(business.pan)}</div>" else ""}
                      ${if (business.phone.isNotBlank()) "<div>Ph: ${escapeHtml(business.phone)}</div>" else ""}
                      ${if (business.gstin.isNotBlank()) "<div>GSTIN/UIN: ${escapeHtml(business.gstin)}</div>" else ""}
                      ${if (business.state.isNotBlank()) "<div>State Name: ${escapeHtml(business.state)}, Code: ${escapeHtml(business.stateCode)}</div>" else ""}
                      ${if (business.email.isNotBlank()) "<div>E-Mail: ${escapeHtml(business.email)}</div>" else ""}
                    </div>
                  </div>
                </div>
                <div class='meta-grid'>
                  <div class='meta-cell'>
                    <div class='meta-label'>Invoice No.</div>
                    <div class='meta-value'>${escapeHtml(document.invoiceNumber)}</div>
                  </div>
                  <div class='meta-cell'>
                    <div class='meta-label'>Dated</div>
                    <div class='meta-value'>${escapeHtml(document.issuedAtLabel)}</div>
                  </div>
                  <div class='meta-cell'>
                    <div class='meta-label'>Mode / Terms of Payment</div>
                    <div class='meta-value'>${escapeHtml(document.paymentSnapshot.modeLabel)}</div>
                  </div>
                  <div class='meta-cell'>
                    <div class='meta-label'>Due Date</div>
                    <div class='meta-value'>${escapeHtml(document.dueDateLabel.ifBlank { "-" })}</div>
                  </div>
                  <div class='meta-cell'>
                    <div class='meta-label'>Reference No. & Date</div>
                    <div class='meta-value'>${escapeHtml(voucher.referenceNo.ifBlank { "-" })}</div>
                  </div>
                  <div class='meta-cell'>
                    <div class='meta-label'>Other References</div>
                    <div class='meta-value'>${escapeHtml(voucher.dispatchDocNo.ifBlank { "-" })}</div>
                  </div>
                </div>
              </div>

              <div class='party-grid'>
                <div class='party-box'>
                  <div class='section-title'>Buyer (Bill to)</div>
                  <div style='font-size:15px;font-weight:700;margin-top:2px;'>${escapeHtml(buyer?.name ?: "Walk-in Customer")}</div>
                  ${if (!buyer?.address.isNullOrBlank()) "<div>${escapeHtml(buyer!!.address).replace("\n", "<br/>")}</div>" else ""}
                  ${if (!buyer?.city.isNullOrBlank()) "<div>${escapeHtml(buyer!!.city)} - ${escapeHtml(buyer.pin)}</div>" else ""}
                  ${if (!buyer?.phone.isNullOrBlank()) "<div>Phone: ${escapeHtml(buyer!!.phone)}</div>" else ""}
                  ${if (!buyer?.email.isNullOrBlank()) "<div>Email: ${escapeHtml(buyer!!.email)}</div>" else ""}
                  ${if (!buyer?.gstin.isNullOrBlank()) "<div>GSTIN: ${escapeHtml(buyer!!.gstin!!)}</div>" else ""}
                  ${if (!buyer?.pan.isNullOrBlank()) "<div>PAN: ${escapeHtml(buyer!!.pan!!)}</div>" else ""}
                  ${if (!buyer?.state.isNullOrBlank()) "<div>Place of Supply: ${escapeHtml(buyer!!.state)}</div>" else ""}
                  ${if (!buyer?.stateCode.isNullOrBlank()) "<div>State Code: ${escapeHtml(buyer!!.stateCode)}</div>" else ""}
                </div>
                <div class='transport-box'>
                  <div class='section-title'>Transport / Delivery / Payment</div>
                  <div style='margin-top:2px;'>${transportRows.ifBlank { "<span class='muted'>No transport details</span>" }}</div>
                  <div class='divider-line'></div>
                  <div>${document.paymentSnapshot.summaryLabel}</div>
                  ${if (bankDetails.isNotBlank()) "<div class='divider-line'></div><div>$bankDetails</div>" else ""}
                </div>
              </div>

              <table class='body-table'>
                <thead>
                  <tr>
                    <th>Sl No.</th>
                    <th>Description of Goods</th>
                    <th>HSN/SAC</th>
                    <th>Quantity</th>
                    <th>Unit</th>
                    <th>Rate</th>
                    <th>Taxable Amount</th>
                  </tr>
                </thead>
                <tbody>
                  $itemRows
                  $adjustmentRows
                </tbody>
              </table>

              <div class='totals-wrap'>
                <div class='totals-left'>
                  <div class='section-title'>Amount Chargeable (in words)</div>
                  <div style='font-size:15px;font-weight:700;margin-top:4px;'>${escapeHtml(document.amountInWords)}</div>
                </div>
                <div class='totals-right'>
                  <table class='totals-table'>
                    <tr><td>Items Quantity</td><td class='right'>${formatQty(totals.totalQuantity)}</td></tr>
                    <tr><td>Taxable Amount</td><td class='right'>${formatMoney(totals.taxableAmount)}</td></tr>
                    ${if (voucher.cgst > 0.0) "<tr><td>CGST</td><td class='right'>${formatMoney(voucher.cgst)}</td></tr>" else ""}
                    ${if (voucher.sgst > 0.0) "<tr><td>SGST / UTGST</td><td class='right'>${formatMoney(voucher.sgst)}</td></tr>" else ""}
                    ${if (voucher.igst > 0.0) "<tr><td>IGST</td><td class='right'>${formatMoney(voucher.igst)}</td></tr>" else ""}
                    ${document.additionalCharges.joinToString("") { "<tr><td>${escapeHtml(it.label)}</td><td class='right'>${formatMoney(it.amount)}</td></tr>" }}
                    ${if (abs(voucher.roundOff) > 0.0) "<tr><td>Round Off</td><td class='right'>${formatSignedMoney(voucher.roundOff)}</td></tr>" else ""}
                    <tr class='grand-total'><td>Grand Total</td><td class='right'>${formatMoney(totals.netAmount)}</td></tr>
                  </table>
                </div>
              </div>

              <table class='tax-table'>
                <thead>
                  <tr>
                    <th>HSN/SAC</th>
                    <th>Taxable Value</th>
                    <th>CGST Rate</th>
                    <th>CGST Amount</th>
                    <th>SGST Rate</th>
                    <th>SGST Amount</th>
                    <th>IGST Rate</th>
                    <th>IGST Amount</th>
                    <th>Total Tax Amount</th>
                  </tr>
                </thead>
                <tbody>
                  $taxRows
                  <tr>
                    <td class='right'><strong>Total</strong></td>
                    <td class='right'><strong>${formatMoney(totals.taxableAmount)}</strong></td>
                    <td class='center'>${if (isIntrastate && totals.cgst > 0.0) rateLabel(document.taxSummary.firstOrNull()?.cgstRate ?: 0.0) else ""}</td>
                    <td class='right'><strong>${formatMoney(totals.cgst)}</strong></td>
                    <td class='center'>${if (isIntrastate && totals.sgst > 0.0) rateLabel(document.taxSummary.firstOrNull()?.sgstRate ?: 0.0) else ""}</td>
                    <td class='right'><strong>${formatMoney(totals.sgst)}</strong></td>
                    <td class='center'>${if (!isIntrastate && totals.igst > 0.0) rateLabel(document.taxSummary.firstOrNull()?.igstRate ?: 0.0) else ""}</td>
                    <td class='right'><strong>${formatMoney(totals.igst)}</strong></td>
                    <td class='right'><strong>${formatMoney(totals.totalTaxAmount)}</strong></td>
                  </tr>
                </tbody>
              </table>

              <div class='summary-grid'>
                <div class='words-box'>
                  <div class='section-title'>Tax Amount (in words)</div>
                  <div style='font-weight:700;margin-top:4px;'>${escapeHtml(document.taxAmountInWords)}</div>
                </div>
                <div class='payment-box'>
                  <div class='section-title'>Balance Snapshot</div>
                  <div>Previous Due: ${formatMoney(document.paymentSnapshot.previousDue)}</div>
                  <div>Current Invoice: ${formatMoney(document.paymentSnapshot.currentInvoiceAmount)}</div>
                  <div>Advance Received: ${formatMoney(document.paymentSnapshot.advanceReceived)}</div>
                  <div>Part Payment: ${formatMoney(document.paymentSnapshot.partPaymentReceived)}</div>
                  <div><strong>Outstanding: ${formatMoney(document.paymentSnapshot.outstandingAmount)}</strong></div>
                  ${qrSection}
                </div>
              </div>

              <div class='footer-grid'>
                <div class='declaration-box'>
                  <div class='section-title'>Declaration / Terms & Conditions</div>
                  <div class='terms'>${escapeHtml(business.termsAndConditions.ifBlank { business.invoiceFooterText }).replace("\n", "<br/>")}</div>
                </div>
                <div class='signature-box'>
                  <div style='text-align:right; width:100%; font-weight:700;'>for ${escapeHtml(business.businessName)}</div>
                  ${if (showSignature) "<img class='signature' src='${toFileUrl(business.signaturePath!!)}' alt='Signature' />" else "<div style='height:72px;'></div>"}
                  <div>Authorised Signatory</div>
                </div>
              </div>
            </div>
          </div>
        </body>
        </html>
        """.trimIndent()
    }

    private suspend fun renderHtmlToPdf(
        context: Context,
        html: String,
        jobName: String,
        outputFile: File
    ) {
        withContext(Dispatchers.Main) {
            val webView = createConfiguredWebView(context)
            try {
                loadHtmlIntoWebView(webView, html)
                writePrintAdapterToFile(webView, jobName, outputFile)
            } finally {
                webView.destroy()
            }
        }
    }

    private fun createConfiguredWebView(context: Context): WebView =
        WebView(context).apply {
            configureInvoiceWebView(this)
        }

    private suspend fun loadHtmlIntoWebView(webView: WebView, html: String) {
        suspendCancellableCoroutine<Unit> { continuation ->
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    val target = view ?: return
                    target.postVisualStateCallback(
                        System.currentTimeMillis(),
                        object : WebView.VisualStateCallback() {
                            override fun onComplete(requestId: Long) {
                                if (continuation.isActive) {
                                    continuation.resume(Unit)
                                }
                            }
                        }
                    )
                }
            }
            webView.loadDataWithBaseURL("file:///", html, "text/html", "UTF-8", null)
        }
    }

    private suspend fun writePrintAdapterToFile(
        webView: WebView,
        jobName: String,
        outputFile: File
    ) {
        suspendCancellableCoroutine<Unit> { continuation ->
            WebViewPdfWriter.writePdf(
                webView,
                jobName,
                buildPrintAttributes(),
                outputFile,
                object : WebViewPdfWriter.Callback {
                    override fun onSuccess() {
                        if (continuation.isActive) continuation.resume(Unit)
                    }

                    override fun onError(error: Throwable) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }
            )
        }
    }

    private fun buildPrintAttributes(): PrintAttributes =
        PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setResolution(pdfResolution)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()

    private suspend fun validatePdfFile(file: File, bundle: InvoiceRenderBundle) {
        check(file.exists()) { "PDF file was not created." }
        check(file.length() > 128L) { "PDF file is blank." }
        val header = file.inputStream().use { input ->
            ByteArray(4).also { input.read(it) }.toString(Charsets.US_ASCII)
        }
        check(header.startsWith("%PDF")) { "Generated file is not a valid PDF." }
        check(bundle.document.invoiceNumber.isNotBlank()) { "Invoice identity missing." }
    }

    private fun buildTaxSummary(items: List<VoucherItem>, voucher: Voucher): List<InvoiceTaxSummary> {
        return items.groupBy { "${it.hsnCode}|${it.gstRate}" }
            .entries
            .sortedBy { it.key }
            .map { (_, groupedItems) ->
                val first = groupedItems.first()
                val taxableValue = groupedItems.sumOf { it.taxableAmount }
                val cgstAmount = groupedItems.sumOf { it.cgstAmount }
                val sgstAmount = groupedItems.sumOf { it.sgstAmount }
                val igstAmount = groupedItems.sumOf { it.igstAmount }
                InvoiceTaxSummary(
                    hsnCode = first.hsnCode,
                    taxableValue = taxableValue,
                    cgstRate = if (!voucher.isIgst) first.gstRate / 2.0 else 0.0,
                    cgstAmount = cgstAmount,
                    sgstRate = if (!voucher.isIgst) first.gstRate / 2.0 else 0.0,
                    sgstAmount = sgstAmount,
                    igstRate = if (voucher.isIgst) first.gstRate else 0.0,
                    igstAmount = igstAmount,
                    totalTaxAmount = cgstAmount + sgstAmount + igstAmount
                )
            }
    }

    private fun resolvePaymentLabel(voucher: Voucher, extras: VoucherRenderExtras): String {
        return when {
            extras.isAdvance && voucher.type == "RECEIPT" -> "ADVANCE RECEIVED"
            extras.isAdvance && voucher.type == "PAYMENT" -> "ADVANCE PAID"
            voucher.paymentMode.equals("PART PAYMENT", ignoreCase = true) -> "PART PAYMENT"
            voucher.paymentMode.equals("CREDIT", ignoreCase = true) -> "CREDIT"
            else -> voucher.paymentMode.replace("_", " ")
        }
    }

    private fun buildPaymentSummaryLabel(voucher: Voucher, extras: VoucherRenderExtras): String {
        return when {
            extras.isAdvance -> "Advance amount recorded for future adjustment."
            voucher.paymentMode.equals("PART PAYMENT", ignoreCase = true) -> {
                val dueText = extras.creditDueDate.takeIf { it.isNotBlank() }?.let(::formatDueDateText)
                "Part payment received: ${formatMoney(extras.partialAmountPaid)}. Balance due: ${formatMoney(extras.remainingCreditAmount)}${dueText?.let { " by $it" } ?: ""}."
            }
            voucher.paymentMode.equals("CREDIT", ignoreCase = true) -> {
                val dueText = extras.creditDueDate.takeIf { it.isNotBlank() }?.let(::formatDueDateText)
                "Full amount on credit${dueText?.let { " due by $it" } ?: ""}."
            }
            else -> "Payment captured through ${resolvePaymentLabel(voucher, extras)}."
        }
    }

    private fun buildInvoiceFileStem(document: InvoiceDocument): String {
        val sequence = document.invoiceNumber.substringAfterLast('/').padStart(5, '0')
        val yearCode = document.financialYearCode.replace("/", "-")
        val prefix = when (document.voucher.type) {
            "PURCHASE" -> "PUR"
            "SALE_RETURN" -> "SRN"
            "PURCHASE_RETURN" -> "PRN"
            "PAYMENT" -> "PMT"
            "RECEIPT" -> "RCP"
            else -> "INV"
        }
        return "$prefix-$yearCode-$sequence"
    }

    fun shareInvoicePdf(
        context: Context,
        pdfFile: File
    ) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", pdfFile)
        ExportStorageManager.shareFile(
            context,
            ExportStorageManager.ExportResult(
                fileName = pdfFile.name,
                locationLabel = pdfFile.absolutePath,
                uri = uri,
                mimeType = "application/pdf"
            )
        )
    }

    fun shareInvoicePdfToWhatsApp(
        context: Context,
        pdfFile: File
    ) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", pdfFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            setPackage("com.whatsapp")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            context.startActivity(Intent.createChooser(intent.apply { setPackage(null) }, "Share Invoice"))
        }
    }

    fun exportInvoicePdf(
        context: Context,
        pdfFile: File
    ): ExportStorageManager.ExportResult =
        ExportStorageManager.exportFile(
            context = context,
            sourceFile = pdfFile,
            displayName = pdfFile.name,
            mimeType = "application/pdf",
            target = ExportTarget.Invoices
        )

    fun emailInvoicePdf(
        context: Context,
        pdfFile: File,
        recipient: String?,
        subject: String,
        body: String
    ) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", pdfFile)
        EmailComposer.compose(
            context = context,
            draft = EmailComposer.Draft(
                recipients = recipient?.takeIf { it.isNotBlank() }?.let(::listOf).orEmpty(),
                subject = subject,
                body = body,
                attachments = listOf(uri)
            ),
            chooserTitle = "Send Invoice Email"
        )
    }

    fun amountInWords(amount: Double): String {
        val safeAmount = amount.coerceAtLeast(0.0)
        val rupees = safeAmount.toLong()
        val paise = ((safeAmount - rupees) * 100).roundToInt().coerceIn(0, 99)
        var result = "Indian Rupees ${numToWords(rupees).trim().ifBlank { "Zero" }}"
        if (paise > 0) {
            result += " and ${numToWords(paise.toLong()).trim()} Paise"
        }
        return "$result Only"
    }

    private fun loadVoucherExtras(db: AppDatabase, voucherId: String): VoucherRenderExtras {
        val cursor = db.openHelper.readableDatabase.query(
            """
            SELECT payment_mode, partial_amount_paid, partial_payment_submode, credit_due_date,
                   remaining_credit_amount, is_advance, advance_for
            FROM vouchers WHERE id = ?
            """.trimIndent(),
            arrayOf(voucherId)
        )
        cursor.use {
            return if (it.moveToFirst()) {
                VoucherRenderExtras(
                    paymentModeValue = it.getString(0).orEmpty(),
                    partialAmountPaid = it.getDouble(1),
                    partialPaymentSubmode = it.getString(2).orEmpty(),
                    creditDueDate = it.getString(3).orEmpty(),
                    remainingCreditAmount = it.getDouble(4),
                    isAdvance = it.getInt(5) == 1,
                    advanceFor = it.getString(6).orEmpty()
                )
            } else {
                VoucherRenderExtras()
            }
        }
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun numToWords(n: Long): String {
        if (n == 0L) return ""
        return when {
            n < 20 -> ones[n.toInt()] + " "
            n < 100 -> tens[(n / 10).toInt()] + " " + numToWords(n % 10)
            n < 1000 -> ones[(n / 100).toInt()] + " Hundred " + numToWords(n % 100)
            n < 100000 -> numToWords(n / 1000) + "Thousand " + numToWords(n % 1000)
            n < 10000000 -> numToWords(n / 100000) + "Lakh " + numToWords(n % 100000)
            else -> numToWords(n / 10000000) + "Crore " + numToWords(n % 10000000)
        }
    }
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

suspend fun printInvoice(
    context: Context,
    bundle: InvoiceGenerator.InvoiceRenderBundle
) {
    InvoiceGenerator.printBundle(context, bundle)
}

fun shareInvoicePdf(
    context: Context,
    pdfFile: File
) = InvoiceGenerator.shareInvoicePdf(context, pdfFile)

fun shareInvoicePdfToWhatsApp(
    context: Context,
    pdfFile: File
) = InvoiceGenerator.shareInvoicePdfToWhatsApp(context, pdfFile)

fun exportInvoicePdf(
    context: Context,
    pdfFile: File
): ExportStorageManager.ExportResult = InvoiceGenerator.exportInvoicePdf(context, pdfFile)

fun emailInvoicePdf(
    context: Context,
    pdfFile: File,
    recipient: String?,
    subject: String,
    body: String
) = InvoiceGenerator.emailInvoicePdf(context, pdfFile, recipient, subject, body)

private fun escapeHtml(value: String): String =
    value.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

private fun toFileUrl(path: String): String =
    if (path.startsWith("/")) "file://$path" else "file:///$path"

private fun formatMoney(value: Double): String = DecimalFormat("#,##0.00").format(value)

private fun formatSignedMoney(value: Double): String =
    if (value < 0) "-${formatMoney(abs(value))}" else formatMoney(value)

private fun formatQty(value: Double): String =
    String.format(Locale.ENGLISH, "%.3f", value)

private fun rateLabel(value: Double): String =
    if (value <= 0.0) "" else if (value % 1.0 == 0.0) String.format(Locale.ENGLISH, "%.0f%%", value) else String.format(Locale.ENGLISH, "%.2f%%", value)

private fun formatDueDateText(raw: String): String {
    val millis = raw.toLongOrNull()
    return if (millis != null) {
        SimpleDateFormat("dd-MMM-yy", Locale.ENGLISH).format(Date(millis))
    } else {
        raw
    }
}
