package com.example.services

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.FileProvider
import com.example.data.AdditionalCharge
import com.example.data.AppDatabase
import com.example.data.BusinessProfile
import com.example.data.DEFAULT_TERMS_AND_CONDITIONS
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
        val advanceFor: String = "",
        val referenceNo: String = "",
        val otherReferences: String = ""
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
        val partPaymentSubmode: String,
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
        val referenceNo: String,
        val otherReferences: String,
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
            partPaymentSubmode = "",
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
            referenceNo = voucher.referenceNo,
            otherReferences = voucher.dispatchDocNo,
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
            suspendCancellableCoroutine<Unit> { continuation ->
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        val target = view ?: return
                        target.postDelayed({
                            runCatching {
                                val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                                val printAdapter = target.createPrintDocumentAdapter(bundle.document.invoiceNumber)
                                val printAttributes = PrintAttributes.Builder()
                                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                                    .setResolution(PrintAttributes.Resolution("pdf", "pdf", 600, 600))
                                    .setMinMargins(PrintAttributes.Margins(0, 0, 0, 0))
                                    .build()
                                printManager.print("Invoice_${bundle.document.invoiceNumber}", printAdapter, printAttributes)
                            }.onSuccess {
                                if (continuation.isActive) continuation.resume(Unit)
                            }.onFailure {
                                if (continuation.isActive) continuation.resumeWithException(it)
                            }
                        }, 500L)
                    }
                }
                webView.loadDataWithBaseURL("file:///", bundle.html, "text/html", "UTF-8", null)
                continuation.invokeOnCancellation { webView.destroy() }
            }
            webView.destroy()
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
            partPaymentSubmode = source.extras.partialPaymentSubmode,
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
            referenceNo = source.extras.referenceNo.ifBlank { source.voucher.referenceNo },
            otherReferences = source.extras.otherReferences,
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
        val heading = when {
            voucher.documentType.contains("PROFORMA", ignoreCase = true) -> "PROFORMA INVOICE"
            business.gstin.isNotBlank() -> "TAX INVOICE"
            else -> "INVOICE"
        }
        val modeTermsHtml = buildModeTermsHtml(document)
        val dueDateValue = document.dueDateLabel
        val referenceValue = document.referenceNo
        val otherReferencesValue = document.otherReferences
        val sellerDetailsHtml = buildString {
            if (business.address.isNotBlank()) append("<div>${escapeHtml(business.address).replace("\n", "<br/>")}</div>")
            if (business.phone.isNotBlank()) append("<div>Ph : ${escapeHtml(business.phone)}</div>")
            if (business.gstin.isNotBlank()) append("<div>GSTIN/UIN: ${escapeHtml(business.gstin)}</div>")
            if (business.state.isNotBlank()) append("<div>State Name : ${escapeHtml(business.state)}, Code : ${escapeHtml(business.stateCode)}</div>")
            if (business.email.isNotBlank()) append("<div>E-Mail : ${escapeHtml(business.email)}</div>")
            if (business.pan.isNotBlank()) append("<div>PAN: ${escapeHtml(business.pan)}</div>")
            if (business.accountNo.isNotBlank()) append("<div>A/c: ${escapeHtml(business.accountNo)}</div>")
        }
        val buyerDetailsHtml = buildString {
            append("<div class='sublabel'>Buyer (Bill to)</div>")
            append("<div style='font-weight:bold;font-size:12px;'>${escapeHtml(buyer?.name ?: "Cash / Walk-in Customer")}</div>")
            if (!buyer?.address.isNullOrBlank()) append("<div>${escapeHtml(buyer!!.address).replace("\n", "<br/>")}</div>")
            if (!buyer?.city.isNullOrBlank() || !buyer?.pin.isNullOrBlank()) {
                append("<div>${escapeHtml(buyer?.city.orEmpty())}${if (!buyer?.pin.isNullOrBlank()) " - ${escapeHtml(buyer!!.pin)}" else ""}</div>")
            }
            if (!buyer?.pan.isNullOrBlank()) append("<div>PAN: ${escapeHtml(buyer!!.pan!!)}</div>")
            if (!buyer?.gstin.isNullOrBlank()) append("<div>GSTIN: ${escapeHtml(buyer!!.gstin!!)}</div>")
            if (!buyer?.state.isNullOrBlank()) append("<div>State Name : ${escapeHtml(buyer!!.state)}, Code : ${escapeHtml(buyer.stateCode)}</div>")
            if (!buyer?.state.isNullOrBlank()) append("<div>Place of Supply   : ${escapeHtml(buyer!!.state)}</div>")
        }
        val transportRowsHtml = buildTransportRowsHtml(voucher)
        val itemRows = document.items.mapIndexed { index, item ->
            val bg = if (index % 2 == 0) "#ffffff" else "#fafafa"
            """
            <tr style='background:$bg;'>
              <td class='c'>${index + 1}</td>
              <td><span class='b'>${escapeHtml(item.productName)}</span></td>
              <td class='c'>${escapeHtml(item.hsnCode)}</td>
              <td class='c'>${formatQty(item.qty)}</td>
              <td class='r'>${formatMoney(item.rate)}</td>
              <td class='c'>${escapeHtml(item.unit)}</td>
              <td class='r'>${formatMoney(item.taxableAmount)}</td>
            </tr>
            """.trimIndent()
        }.joinToString("\n")
        val additionalChargeRows = document.additionalCharges.joinToString("\n") { charge ->
            """
            <tr>
              <td></td>
              <td colspan='5' class='r' style='font-style:italic;font-weight:bold;'>${escapeHtml(charge.label.ifBlank { "Additional Charge" })}</td>
              <td class='r'>${formatMoney(charge.amount)}</td>
            </tr>
            """.trimIndent()
        }
        val gstRows = buildItemGstRows(document, isIntrastate)
        val totalUnit = document.items.firstOrNull()?.unit.orEmpty()
        val spacerRows = (1..8).joinToString("") {
            "<tr class='spacer'><td></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>"
        }
        val paymentSummaryHtml = buildPaymentSummaryHtml(document)
        val gstBreakupHtml = buildGstBreakupHtml(document, business.gstin.isNotBlank(), isIntrastate)
        val signatureHtml = if (showSignature) {
            "<img src='${toFileUrl(business.signaturePath!!)}' style='max-height:55px;max-width:160px;object-fit:contain;display:block;margin-left:auto;'/>"
        } else {
            "<div style='height:55px;'></div>"
        }
        val termsHtml = escapeHtml(business.termsAndConditions.ifBlank { DEFAULT_TERMS_AND_CONDITIONS }).replace("\n", "<br/>")

        return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset='UTF-8'/>
          <meta name='viewport' content='width=device-width, initial-scale=1.0'/>
          <style>
            * { margin:0; padding:0; box-sizing:border-box; }
            body { font-family:Arial,Helvetica,sans-serif; font-size:11px; color:#000000; background:#ffffff; }
            .page { width:100%; padding:18px 22px; background:#ffffff; }
            table { width:100%; border-collapse:collapse; table-layout:fixed; }
            td, th { border:1px solid #000000; padding:3px 5px; vertical-align:top; }
            .nb td, .nb th { border:none; }
            .b { font-weight:bold; }
            .c { text-align:center; }
            .r { text-align:right; }
            .heading { font-size:14px; font-weight:bold; text-align:center; letter-spacing:2px; padding:5px 0 8px 0; }
            .sublabel { font-size:9px; color:#555555; }
            .aw { font-weight:bold; font-size:11px; padding:5px 6px; }
            .thdr { background:#f0f0f0; font-weight:bold; font-size:10px; }
            .gthdr { background:#e8e8e8; font-weight:bold; font-size:10px; text-align:center; }
            .tot { font-weight:bold; background:#f5f5f5; }
            .spacer td { border-top:none; border-bottom:none; height:20px; }
            .psummary td { font-size:10px; }
            .wrap { word-wrap:break-word; overflow-wrap:break-word; white-space:normal; max-width:0; }
            .terms { word-wrap:break-word; overflow-wrap:break-word; white-space:normal; line-height:1.45; }
            @media print {
              body { -webkit-print-color-adjust:exact; }
            }
          </style>
        </head>
        <body>
          <div class='page'>
            <div class='heading'>$heading</div>
            <table>
              <tr>
                <td style='width:60%;border-right:2px solid #000000;'>
                  <table class='nb'>
                    <tr>
                      <td style='width:95px;'>
                        ${if (showLogo) "<img src='${toFileUrl(business.logoPath!!)}' style='max-height:65px;max-width:120px;object-fit:contain;display:block;'/>" else ""}
                      </td>
                      <td>
                        <div style='font-weight:bold;font-size:14px;'>${escapeHtml(business.businessName)}</div>
                      </td>
                    </tr>
                  </table>
                  <div style='line-height:1.8;'>$sellerDetailsHtml</div>
                  <div style='border-top:1px solid #000000;margin:8px 0;'></div>
                  <div style='line-height:1.7;'>$buyerDetailsHtml</div>
                </td>
                <td style='width:40%;padding:0;'>
                  <table class='nb' style='height:100%;'>
                    <tr>
                      <td style='border-bottom:1px solid #cccccc;'>
                        <div class='sublabel'>Invoice No.</div>
                        <div class='b'>${escapeHtml(document.invoiceNumber)}</div>
                      </td>
                      <td style='border-left:1px solid #cccccc;border-bottom:1px solid #cccccc;padding-left:6px;'>
                        <div class='sublabel'>Dated</div>
                        <div class='b'>${escapeHtml(document.issuedAtLabel)}</div>
                      </td>
                    </tr>
                    <tr><td colspan='2' style='border-bottom:1px solid #cccccc;'>$modeTermsHtml</td></tr>
                    <tr>
                      <td colspan='2' class='wrap' style='border-bottom:1px solid #cccccc;'>
                        <div class='sublabel'>Due Date</div>
                        <div class='b'>${escapeHtml(dueDateValue)}</div>
                      </td>
                    </tr>
                    <tr>
                      <td colspan='2' class='wrap' style='border-bottom:1px solid #cccccc;'>
                        <div class='sublabel'>Reference No. & Date</div>
                        <div>${escapeHtml(referenceValue)}</div>
                        ${if (otherReferencesValue.isNotBlank()) "<div class='sublabel' style='margin-top:4px;'>${escapeHtml(otherReferencesValue)}</div>" else ""}
                      </td>
                    </tr>
                    <tr>
                      <td colspan='2' class='wrap' style='border-bottom:1px solid #cccccc;'>
                        <div class='sublabel'>Other References</div>
                        <div>${escapeHtml(otherReferencesValue)}</div>
                      </td>
                    </tr>
                    <tr>
                      <td colspan='2' style='border-bottom:1px solid #cccccc;'>
                        <div class='sublabel'>Buyer's Order No.</div>
                        <div>${escapeHtml(voucher.buyerOrderNo)}</div>
                      </td>
                    </tr>
                    <tr>
                      <td colspan='2' style='border-bottom:${if (transportRowsHtml.isBlank()) "none" else "1px solid #cccccc"};'>
                        <div class='sublabel'>Terms of Delivery</div>
                        <div>${escapeHtml(voucher.termsOfDelivery)}</div>
                      </td>
                    </tr>
                    $transportRowsHtml
                  </table>
                </td>
              </tr>
            </table>

            <table>
              <tr class='thdr'>
                <th style='width:4%;' class='c'>Sl No</th>
                <th style='width:35%;' class='c'>Description</th>
                <th style='width:10%;' class='c'>HSN/SAC</th>
                <th style='width:13%;' class='c'>Quantity</th>
                <th style='width:9%;' class='r'>Rate</th>
                <th style='width:6%;' class='c'>per</th>
                <th style='width:13%;' class='r'>Amount</th>
              </tr>
              $itemRows
              $additionalChargeRows
              $gstRows
              $spacerRows
              <tr class='tot'>
                <td></td>
                <td class='b'>Total</td>
                <td></td>
                <td class='c b'>${formatQty(totals.totalQuantity)} ${escapeHtml(totalUnit)}</td>
                <td></td>
                <td></td>
                <td class='r b'>${formatMoney(totals.netAmount)}</td>
              </tr>
            </table>

            <table>
              <tr>
                <td style='width:72%;'>
                  <div class='sublabel'>Amount Chargeable (in words)</div>
                  <div class='aw'>${escapeHtml(document.amountInWords)}</div>
                </td>
                <td style='width:28%;'><div style='text-align:right;font-style:italic;'>E. &amp; O.E</div></td>
              </tr>
            </table>

            $paymentSummaryHtml
            $gstBreakupHtml

            <table>
              <tr>
                <td style='width:55%;' class='terms'>
                  ${if (business.bankName.isNotBlank()) """
                  <div class='b'>Company's Bank Details</div>
                  <div>Bank Name : <span class='b'>${escapeHtml(business.bankName)}</span></div>
                  <div>A/c No.   : <span class='b'>${escapeHtml(business.accountNo)}</span></div>
                  <div>IFS Code  : <span class='b'>${escapeHtml(business.ifsc)}</span></div>
                  <div>Branch    : <span class='b'>${escapeHtml(business.branchName)}</span></div>
                  """.trimIndent() else ""}
                  <div style='height:10px;'></div>
                  <div class='b'>Declaration</div>
                  <div class='b'>TERMS &amp; CONDITIONS</div>
                  <div>$termsHtml</div>
                </td>
                <td style='width:5px;border:none;'></td>
                <td style='width:40%;text-align:right;vertical-align:bottom;'>
                  <div class='b'>for ${escapeHtml(business.businessName)}</div>
                  $signatureHtml
                  <div style='border-top:1px solid #000000;'></div>
                  <div style='font-size:9px;'>Authorised Signatory</div>
                </td>
              </tr>
            </table>

            <div style='text-align:center;color:#666666;font-size:10px;padding-top:8px;'>${escapeHtml(business.city)}</div>
          </div>
        </body>
        </html>
        """.trimIndent()
    }

    private fun buildModeTermsHtml(document: InvoiceDocument): String {
        val voucher = document.voucher
        val snapshot = document.paymentSnapshot
        val dueDateLabel = snapshot.dueDateLabel.ifBlank { "Not specified" }
        val lines = mutableListOf<String>()
        when {
            voucher.paymentMode.equals("CASH", ignoreCase = true) -> lines += "<div class='b'>Cash</div>"
            voucher.paymentMode.equals("BANK", ignoreCase = true) -> lines += "<div class='b'>Bank Transfer</div>"
            voucher.paymentMode.equals("UPI", ignoreCase = true) -> lines += "<div class='b'>UPI</div>"
            voucher.paymentMode.equals("CHEQUE", ignoreCase = true) -> {
                lines += "<div class='b'>Cheque</div>"
                val chequeBits = listOfNotNull(
                    voucher.chequeNo?.takeIf { it.isNotBlank() }?.let { "No: ${escapeHtml(it)}" },
                    voucher.chequeDate?.takeIf { it > 0L }?.let { "Date: ${escapeHtml(invoiceDateFormat.format(Date(it)))}" }
                )
                if (chequeBits.isNotEmpty()) lines += "<div>${chequeBits.joinToString(" | ")}</div>"
            }
            voucher.paymentMode.equals("PART_PAYMENT", ignoreCase = true) || voucher.paymentMode.equals("PART PAYMENT", ignoreCase = true) -> {
                lines += "<div class='b'>Part Payment</div>"
                lines += "<div>Paid Now: Rs. ${formatMoney(snapshot.partPaymentReceived)}${snapshot.partPaymentSubmode.takeIf { it.isNotBlank() }?.let { " via ${escapeHtml(it)}" } ?: ""}</div>"
                lines += "<div>Balance Due: Rs. ${formatMoney(snapshot.balanceDue)}</div>"
                lines += "<div>Due by: ${escapeHtml(dueDateLabel)}</div>"
            }
            voucher.paymentMode.equals("CREDIT", ignoreCase = true) -> {
                lines += "<div class='b'>Credit</div>"
                lines += "<div>Paid Now: Rs. ${formatMoney(snapshot.partPaymentReceived)}${snapshot.partPaymentSubmode.takeIf { it.isNotBlank() }?.let { " via ${escapeHtml(it)}" } ?: ""}</div>"
                lines += "<div>Balance Due: Rs. ${formatMoney(snapshot.balanceDue)}</div>"
                lines += "<div>Due by: ${escapeHtml(dueDateLabel)}</div>"
            }
            snapshot.advanceReceived > 0.0 -> {
                lines += "<div class='b'>Advance Receipt</div>"
                lines += "<div>Amount: Rs. ${formatMoney(snapshot.advanceReceived)}</div>"
            }
            else -> lines += "<div class='b'>${escapeHtml(snapshot.modeLabel)}</div>"
        }
        return "<div class='sublabel'>Mode/Terms of Payment</div>${lines.joinToString("")}"
    }

    private fun buildTransportRowsHtml(voucher: Voucher): String {
        val rows = buildList {
            if (voucher.transporterName.isNotBlank()) add("Transporter" to voucher.transporterName)
            if (voucher.vehicleNo.isNotBlank()) add("Vehicle No." to voucher.vehicleNo)
            if (voucher.lrNo.isNotBlank()) add("LR/GR No." to voucher.lrNo)
            if (voucher.destination.isNotBlank()) add("Destination" to voucher.destination)
        }
        return rows.joinToString("") { (label, value) ->
            """
            <tr>
              <td colspan='2' style='border-top:1px solid #cccccc;'>
                <div class='sublabel'>$label</div>
                <div>${escapeHtml(value)}</div>
              </td>
            </tr>
            """.trimIndent()
        }
    }

    private fun buildItemGstRows(document: InvoiceDocument, isIntrastate: Boolean): String {
        val rows = mutableListOf<String>()
        if (document.voucher.cgst > 0.0 && isIntrastate) {
            rows += """
            <tr>
              <td></td>
              <td colspan='5' class='r' style='font-style:italic;font-weight:bold;'>OUTPUT CGST</td>
              <td class='r'>${formatMoney(document.voucher.cgst)}</td>
            </tr>
            """.trimIndent()
        }
        if (document.voucher.sgst > 0.0 && isIntrastate) {
            rows += """
            <tr>
              <td></td>
              <td colspan='5' class='r' style='font-style:italic;font-weight:bold;'>OUTPUT SGST</td>
              <td class='r'>${formatMoney(document.voucher.sgst)}</td>
            </tr>
            """.trimIndent()
        }
        if (document.voucher.igst > 0.0 && !isIntrastate) {
            rows += """
            <tr>
              <td></td>
              <td colspan='5' class='r' style='font-style:italic;font-weight:bold;'>OUTPUT IGST</td>
              <td class='r'>${formatMoney(document.voucher.igst)}</td>
            </tr>
            """.trimIndent()
        }
        if (abs(document.voucher.roundOff) > 0.0) {
            rows += """
            <tr>
              <td></td>
              <td colspan='5' class='r' style='font-style:italic;font-weight:bold;'>ROUND OFF (S)</td>
              <td class='r'>${formatSignedMoney(document.voucher.roundOff)}</td>
            </tr>
            """.trimIndent()
        }
        return rows.joinToString("\n")
    }

    private fun buildPaymentSummaryHtml(document: InvoiceDocument): String {
        val snapshot = document.paymentSnapshot
        val mode = document.voucher.paymentMode.uppercase(Locale.ENGLISH)
        if (mode != "PART_PAYMENT" && mode != "PART PAYMENT" && mode != "CREDIT") return ""
        val header = if (mode == "CREDIT") "Credit Sale Summary" else "Payment Summary"
        val paidLabel = snapshot.partPaymentSubmode.takeIf { it.isNotBlank() }?.let { "Amount Paid (via ${escapeHtml(it)})" } ?: "Amount Paid"
        val dueDateLabel = snapshot.dueDateLabel.ifBlank { "Not specified" }
        val bodyRows = if (mode == "CREDIT") {
            buildList {
                add("<tr><td>Total Amount on Credit</td><td class='r'>Rs. ${formatMoney(snapshot.currentInvoiceAmount)}</td></tr>")
                add("<tr><td>Amount Received So Far</td><td class='r'>Rs. ${formatMoney(snapshot.partPaymentReceived)}</td></tr>")
                add("<tr><td class='b'>Outstanding Balance</td><td class='r b'>Rs. ${formatMoney(snapshot.balanceDue)}</td></tr>")
                add("<tr><td>Due Date</td><td class='r'>${escapeHtml(dueDateLabel)}</td></tr>")
            }
        } else {
            buildList {
                add("<tr><td>Invoice Total</td><td class='r'>Rs. ${formatMoney(snapshot.currentInvoiceAmount)}</td></tr>")
                add("<tr><td>$paidLabel</td><td class='r'>Rs. ${formatMoney(snapshot.partPaymentReceived)}</td></tr>")
                add("<tr><td class='b'>Balance Due</td><td class='r b'>Rs. ${formatMoney(snapshot.balanceDue)}</td></tr>")
                add("<tr><td>Due Date</td><td class='r'>${escapeHtml(dueDateLabel)}</td></tr>")
            }
        }
        return """
        <table class='psummary'>
          <tr><td colspan='2' style='font-weight:bold;background:#f8f8f8;'>$header</td></tr>
          ${bodyRows.joinToString("")}
        </table>
        """.trimIndent()
    }

    private fun buildGstBreakupHtml(
        document: InvoiceDocument,
        isGst: Boolean,
        isIntrastate: Boolean
    ): String {
        if (!isGst || document.totals.totalTaxAmount <= 0.0) return ""
        val taxRows = document.taxSummary.joinToString("") { summary ->
            val rightRate = if (isIntrastate) rateLabel(summary.sgstRate) else rateLabel(summary.igstRate)
            val rightAmount = if (isIntrastate) formatMoney(summary.sgstAmount) else formatMoney(summary.igstAmount)
            """
            <tr>
              <td>${escapeHtml(summary.hsnCode)}</td>
              <td class='r'>${formatMoney(summary.taxableValue)}</td>
              <td class='c'>${rateLabel(summary.cgstRate)}</td>
              <td class='r'>${formatMoney(summary.cgstAmount)}</td>
              <td class='c'>$rightRate</td>
              <td class='r'>$rightAmount</td>
              <td class='r'>${formatMoney(summary.totalTaxAmount)}</td>
            </tr>
            """.trimIndent()
        }
        val totalRightAmount = if (isIntrastate) formatMoney(document.totals.sgst) else formatMoney(document.totals.igst)
        return """
        <table>
          <tr class='gthdr'>
            <th rowspan='2' style='width:20%;'>HSN/SAC</th>
            <th rowspan='2' style='width:15%;'>Taxable Value</th>
            <th colspan='2' style='width:25%;'>CGST</th>
            <th colspan='2' style='width:25%;'>${if (isIntrastate) "SGST/UTGST" else "IGST"}</th>
            <th rowspan='2' style='width:15%;'>Total Tax Amount</th>
          </tr>
          <tr class='gthdr'>
            <th>Rate</th>
            <th>Amount</th>
            <th>Rate</th>
            <th>Amount</th>
          </tr>
          $taxRows
          <tr class='tot'>
            <td class='r b'>Total</td>
            <td class='r b'>${formatMoney(document.totals.taxableAmount)}</td>
            <td class='c b'>${rateLabel(document.taxSummary.firstOrNull()?.cgstRate ?: 0.0)}</td>
            <td class='r b'>${formatMoney(document.totals.cgst)}</td>
            <td class='c b'>${if (isIntrastate) rateLabel(document.taxSummary.firstOrNull()?.sgstRate ?: 0.0) else rateLabel(document.taxSummary.firstOrNull()?.igstRate ?: 0.0)}</td>
            <td class='r b'>$totalRightAmount</td>
            <td class='r b'>${formatMoney(document.totals.totalTaxAmount)}</td>
          </tr>
        </table>
        <div style='padding:6px 0 8px 2px;'>Tax Amount (in words) : <span class='b'>${escapeHtml(document.taxAmountInWords)}</span></div>
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
        var result = "INR ${numToWords(rupees).trim().ifBlank { "Zero" }}"
        if (paise > 0) {
            result += " and ${numToWords(paise.toLong()).trim()} Paise"
        }
        return "$result Only"
    }

    private fun loadVoucherExtras(db: AppDatabase, voucherId: String): VoucherRenderExtras {
        val cursor = db.openHelper.readableDatabase.query(
            """
            SELECT payment_mode, partial_amount_paid, partial_payment_submode, credit_due_date,
                   remaining_credit_amount, is_advance, advance_for,
                   COALESCE(NULLIF(reference_no, ''), NULLIF(referenceNo, ''), ''),
                   COALESCE(NULLIF(other_references, ''), '')
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
                    advanceFor = it.getString(6).orEmpty(),
                    referenceNo = it.getString(7).orEmpty(),
                    otherReferences = it.getString(8).orEmpty()
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

fun printInvoice(
    context: Context,
    htmlContent: String,
    voucherNo: String
) {
    val webView = WebView(context)
    configureInvoiceWebView(webView)
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            Handler(Looper.getMainLooper()).postDelayed({
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val safeName = voucherNo.replace("/", "-")
                val adapter = view!!.createPrintDocumentAdapter(safeName)
                printManager.print(
                    safeName,
                    adapter,
                    PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .build()
                )
            }, 500)
        }
    }
    webView.loadDataWithBaseURL(
        "file:///",
        htmlContent,
        "text/html",
        "UTF-8",
        null
    )
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
