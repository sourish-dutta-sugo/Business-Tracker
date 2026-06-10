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
        renderExtras: VoucherRenderExtras = VoucherRenderExtras()
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
            modeLabel = resolvePaymentLabel(voucher, renderExtras),
            previousDue = 0.0,
            currentInvoiceAmount = voucher.netAmount,
            advanceReceived = if (renderExtras.isAdvance) voucher.netAmount else 0.0,
            partPaymentReceived = renderExtras.partialAmountPaid.coerceAtLeast(0.0),
            partPaymentSubmode = renderExtras.partialPaymentSubmode,
            balanceDue = when (voucher.paymentMode.normalizedPaymentMode()) {
                "PART PAYMENT" -> renderExtras.remainingCreditAmount.coerceAtLeast(0.0)
                "CREDIT" -> voucher.outstandingAmount.takeIf { it > 0.0 } ?: voucher.netAmount
                else -> voucher.outstandingAmount.coerceAtLeast(0.0)
            },
            outstandingAmount = when (voucher.paymentMode.normalizedPaymentMode()) {
                "PART PAYMENT" -> renderExtras.remainingCreditAmount.coerceAtLeast(0.0)
                "CREDIT" -> voucher.outstandingAmount.takeIf { it > 0.0 } ?: voucher.netAmount
                else -> voucher.outstandingAmount.coerceAtLeast(0.0)
            },
            dueDateLabel = if (
                voucher.type == "SALE" &&
                voucher.paymentMode.normalizedPaymentMode() in setOf("CREDIT", "PART PAYMENT") &&
                renderExtras.creditDueDate.isNotBlank()
            ) formatDueDateText(renderExtras.creditDueDate) else "",
            summaryLabel = buildPaymentSummaryLabel(voucher, renderExtras)
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
            referenceNo = renderExtras.referenceNo.ifBlank { voucher.referenceNo },
            otherReferences = renderExtras.otherReferences.ifBlank { voucher.dispatchDocNo },
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
        val normalizedPaymentMode = source.voucher.paymentMode.normalizedPaymentMode()
        val dueDateLabel = if (
            source.voucher.type == "SALE" &&
            normalizedPaymentMode in setOf("CREDIT", "PART PAYMENT") &&
            source.extras.creditDueDate.isNotBlank()
        ) {
            formatDueDateText(source.extras.creditDueDate)
        } else {
            ""
        }
        val partPaymentReceived = source.extras.partialAmountPaid.coerceAtLeast(0.0)
        val advanceReceived = if (source.extras.isAdvance) source.voucher.netAmount else 0.0
        val balanceDue = when {
            normalizedPaymentMode == "PART PAYMENT" -> source.extras.remainingCreditAmount.coerceAtLeast(0.0)
            normalizedPaymentMode == "CREDIT" -> source.voucher.outstandingAmount
                .takeIf { it > 0.0 }
                ?.coerceAtLeast(0.0)
                ?: source.voucher.netAmount.coerceAtLeast(0.0)
            else -> source.voucher.outstandingAmount.coerceAtLeast(0.0)
        }
        val paymentSnapshot = InvoicePaymentSnapshot(
            modeLabel = resolvePaymentLabel(source.voucher, source.extras),
            previousDue = 0.0,
            currentInvoiceAmount = source.voucher.netAmount,
            advanceReceived = advanceReceived,
            partPaymentReceived = partPaymentReceived,
            partPaymentSubmode = source.extras.partialPaymentSubmode,
            balanceDue = balanceDue,
            outstandingAmount = balanceDue,
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
            referenceNo = source.extras.referenceNo.trim(),
            otherReferences = source.extras.otherReferences.trim(),
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
        val showSignature = business.showSignature && !business.signaturePath.isNullOrBlank() && File(business.signaturePath!!).exists()
        val isIntrastate = !voucher.isIgst && buyer?.stateCode == business.stateCode
        val showLogo = business.showLogo && !business.logoPath.isNullOrBlank() && File(business.logoPath!!).exists()
        val dueDateValue = document.dueDateLabel.ifBlank { "-" }
        val referenceValue = document.referenceNo.ifBlank { "-" }
        val otherReferencesValue = document.otherReferences.ifBlank { "-" }
        val heading = if (voucher.documentType.contains("PROFORMA", ignoreCase = true)) "PROFORMA INVOICE" else "TAX INVOICE"
        val signatureHtml = if (showSignature) {
            "<img src='${toFileUrl(business.signaturePath!!)}' style='max-height:62px;max-width:180px;object-fit:contain;display:block;margin-left:auto;'/>"
        } else {
            "<div style='height:62px;'></div>"
        }
        val sellerDetailsHtml = buildSellerBlock(business, showLogo)
        val buyerDetailsHtml = buildBuyerBlock(buyer, business)
        val transportDetailsHtml = buildTransportDeliveryPaymentHtml(voucher, document, business)
        val itemRows = document.items.mapIndexed { index, item ->
            """
            <tr>
              <td class='center num'>${index + 1}</td>
              <td>${escapeHtml(item.productName)}</td>
              <td class='center num'>${escapeHtml(item.hsnCode)}</td>
              <td class='right num'>${formatQty(item.qty)}</td>
              <td class='center'>${escapeHtml(item.unit)}</td>
              <td class='right num'>${formatMoney(item.rate)}</td>
              <td class='right num'>${formatMoney(item.taxableAmount)}</td>
            </tr>
            """.trimIndent()
        }.joinToString("\n")
        val additionalChargeRows = document.additionalCharges.joinToString("\n") { charge ->
            """
            <tr>
              <td></td>
              <td colspan='5' class='right bold'>${escapeHtml(charge.label.ifBlank { "Additional Charge" })}</td>
              <td class='right num'>${formatMoney(charge.amount)}</td>
            </tr>
            """.trimIndent()
        }
        val gstRows = buildItemGstRows(document, isIntrastate)
        val usedRows = document.items.size + document.additionalCharges.size + listOf(document.voucher.cgst, document.voucher.sgst, document.voucher.igst).count { it > 0.0 }
        val spacerRows = (1..maxOf(0, 4 - usedRows)).joinToString("") {
            "<tr class='blank-row'><td></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>"
        }
        val declarationHtml = buildDeclarationHtml(business.termsAndConditions.ifBlank { DEFAULT_TERMS_AND_CONDITIONS })

        return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset='UTF-8'/>
          <meta name='viewport' content='width=device-width, initial-scale=1.0'/>
          <style>
            * { margin:0; padding:0; box-sizing:border-box; }
            body { font-family:Arial,Helvetica,sans-serif; font-size:11px; color:#111111; background:#ffffff; min-width:920px; }
            .page { width:920px; margin:0 auto; padding:14px 18px 18px 18px; background:#ffffff; }
            table { width:100%; border-collapse:collapse; table-layout:fixed; }
            td, th { border:1px solid #1d2430; padding:5px 6px; vertical-align:top; overflow-wrap:anywhere; word-break:break-word; }
            .title { text-align:center; font-size:15px; font-weight:700; letter-spacing:0.5px; padding:4px 0 12px 0; }
            .center { text-align:center; }
            .right { text-align:right; }
            .bold { font-weight:700; }
            .num { white-space:nowrap; overflow-wrap:normal; word-break:normal; font-size:9.4px; }
            .small-label { font-size:9px; color:#666666; margin-bottom:4px; }
            .meta-value { font-size:11px; font-weight:700; margin-top:2px; }
            .company-name { font-size:15px; font-weight:700; margin-bottom:6px; }
            .buyer-name { font-size:12px; font-weight:700; margin-bottom:3px; }
            .section-body { line-height:1.45; font-size:11px; }
            .seller-head { font-size:9px; color:#666666; margin-bottom:6px; }
            .seller-grid { width:100%; border-collapse:collapse; table-layout:fixed; }
            .seller-grid td { border:none; padding:0; vertical-align:top; }
            .seller-logo { width:86px; padding-right:8px; }
            .seller-logo img { max-width:80px; max-height:80px; object-fit:contain; display:block; }
            .items-header th { background:#eef2fa; font-size:9.5px; font-weight:700; text-align:center; }
            .amount-words .value { display:block; font-size:11px; font-weight:700; margin-top:4px; line-height:1.45; }
            .snapshot-table td, .gst-table td, .gst-table th { font-size:10px; line-height:1.25; }
            .snapshot-table .grand { background:#eef2fa; font-weight:700; }
            .gst-table th { background:#eef2fa; font-size:9.5px; font-weight:700; text-align:center; }
            .inner-table { width:100%; border-collapse:collapse; table-layout:fixed; }
            .inner-table td, .inner-table th { border:1px solid #1d2430; padding:5px 6px; vertical-align:top; }
            .inner-table tr:first-child td, .inner-table tr:first-child th { border-top:none; }
            .inner-table tr:last-child td, .inner-table tr:last-child th { border-bottom:none; }
            .inner-table td:first-child, .inner-table th:first-child { border-left:none; }
            .inner-table td:last-child, .inner-table th:last-child { border-right:none; }
            .blank-row td { height:30px; }
            .terms-line { display:block; line-height:1.5; }
            .signatory { vertical-align:bottom; text-align:right; min-height:120px; }
            .signatory .for-line { font-size:11px; font-weight:700; margin-bottom:22px; }
            .summary-wrap { padding:0; }
            .summary-wrap > table { width:100%; border-collapse:collapse; table-layout:fixed; }
            @media print { body { -webkit-print-color-adjust:exact; } }
          </style>
        </head>
        <body>
          <div class='page'>
            <div class='title'>$heading</div>
            <table>
              <tr>
                <td style='width:58%;'><div class='section-body'>$sellerDetailsHtml</div></td>
                <td style='width:42%; padding:0;'>
                  <table class='inner-table'>
                    <tr>
                      <td><div class='small-label'>Invoice No.</div><div class='meta-value'>${escapeHtml(document.invoiceNumber)}</div></td>
                      <td><div class='small-label'>Dated</div><div class='meta-value'>${escapeHtml(document.issuedAtLabel)}</div></td>
                    </tr>
                    <tr>
                      <td><div class='small-label'>Mode / Terms of Payment</div><div class='meta-value'>${buildModeTermsValue(document)}</div></td>
                      <td><div class='small-label'>Due Date</div><div class='meta-value'>${escapeHtml(dueDateValue)}</div></td>
                    </tr>
                    <tr>
                      <td><div class='small-label'>Reference No. &amp; Date</div><div class='meta-value'>${escapeHtml(referenceValue)}</div></td>
                      <td><div class='small-label'>Other References</div><div class='meta-value'>${escapeHtml(otherReferencesValue)}</div></td>
                    </tr>
                  </table>
                </td>
              </tr>
              <tr>
                <td style='width:58%;'><div class='section-body'>$buyerDetailsHtml</div></td>
                <td style='width:42%;'><div class='section-body'>$transportDetailsHtml</div></td>
              </tr>
            </table>
            <table>
              <tr class='items-header'>
                <th style='width:6%;'>Sl No.</th>
                <th style='width:40%;'>Description of Goods</th>
                <th style='width:12%;'>HSN/SAC</th>
                <th style='width:11%;'>Quantity</th>
                <th style='width:10%;'>Unit</th>
                <th style='width:10%;'>Rate</th>
                <th style='width:11%;'>Taxable Amount</th>
              </tr>
              $itemRows
              $additionalChargeRows
              $gstRows
              $spacerRows
            </table>
            <table>
              <tr>
                <td style='width:58%;' class='amount-words'>
                  <div class='small-label'>Amount Chargeable (in words)</div>
                  <span class='value'>${escapeHtml(document.amountInWords)}</span>
                </td>
                <td style='width:42%; padding:0;' class='summary-wrap'>${buildChargeSummaryHtml(document)}</td>
              </tr>
            </table>
            ${buildGstBreakupHtml(document, business.gstin.isNotBlank(), isIntrastate)}
            <table>
              <tr>
                <td style='width:58%;' class='amount-words'>
                  <div class='small-label'>Tax Amount (in words)</div>
                  <span class='value'>${escapeHtml(document.taxAmountInWords)}</span>
                </td>
                <td style='width:42%; padding:0;' class='summary-wrap'>${buildBalanceSnapshotHtml(document)}</td>
              </tr>
            </table>
            <table>
              <tr>
                <td style='width:58%;'>
                  <div class='small-label'>Declaration / Terms &amp; Conditions</div>
                  $declarationHtml
                </td>
                <td style='width:42%;' class='signatory'>
                  <div class='for-line'>for ${escapeHtml(business.businessName)}</div>
                  $signatureHtml
                  <div>Authorised Signatory</div>
                </td>
              </tr>
            </table>
          </div>
        </body>
        </html>
        """.trimIndent()
    }

    private fun buildSellerBlock(business: BusinessProfile, showLogo: Boolean): String {
        val detailsHtml = buildString {
            append("<div class='seller-head'>Seller</div>")
            append("<div class='company-name'>${escapeHtml(business.businessName)}</div>")
            if (business.address.isNotBlank()) append("<div>${escapeHtml(business.address).replace("\n", "<br/>")}</div>")
            append("<div>${escapeHtml(listOfNotNull(business.city.takeIf { it.isNotBlank() }, business.pin.takeIf { it.isNotBlank() }).joinToString(" - "))}</div>")
            if (business.pan.isNotBlank()) append("<div>PAN: ${escapeHtml(business.pan)}</div>")
            if (business.phone.isNotBlank()) append("<div>Ph: ${escapeHtml(business.phone)}</div>")
            if (business.gstin.isNotBlank()) append("<div>GSTIN/UIN: ${escapeHtml(business.gstin)}</div>")
            if (business.state.isNotBlank()) append("<div>State Name: ${escapeHtml(business.state)}, Code: ${escapeHtml(business.stateCode)}</div>")
            if (business.email.isNotBlank()) append("<div>E-Mail: ${escapeHtml(business.email)}</div>")
        }
        if (!showLogo) return detailsHtml
        return """
        <table class='seller-grid'>
          <tr>
            <td class='seller-logo'><img src='${toFileUrl(business.logoPath!!)}'/></td>
            <td>$detailsHtml</td>
          </tr>
        </table>
        """.trimIndent()
    }

    private fun buildBuyerBlock(buyer: Party?, business: BusinessProfile): String = buildString {
        append("<div class='small-label'>Buyer (Bill to)</div>")
        append("<div class='buyer-name'>${escapeHtml(buyer?.name ?: "Cash / Walk-in Customer")}</div>")
        if (!buyer?.address.isNullOrBlank()) append("<div>${escapeHtml(buyer!!.address).replace("\n", "<br/>")}</div>")
        if (!buyer?.city.isNullOrBlank() || !buyer?.pin.isNullOrBlank()) {
            append("<div>${escapeHtml(buyer?.city.orEmpty())}${if (!buyer?.pin.isNullOrBlank()) " - ${escapeHtml(buyer!!.pin)}" else ""}</div>")
        }
        if (!buyer?.phone.isNullOrBlank()) append("<div>Phone: ${escapeHtml(buyer!!.phone)}</div>")
        append("<div>Place of Supply: ${escapeHtml(buyer?.state?.ifBlank { business.state } ?: business.state)}</div>")
        append("<div>State Code: ${escapeHtml(buyer?.stateCode?.ifBlank { business.stateCode } ?: business.stateCode)}</div>")
    }

    private fun buildModeTermsValue(document: InvoiceDocument): String = when (document.voucher.paymentMode.normalizedPaymentMode()) {
        "PART PAYMENT" -> "PART PAYMENT"
        "CREDIT" -> "CREDIT"
        "BANK" -> "BANK"
        "UPI" -> "UPI"
        "CHEQUE" -> "CHEQUE"
        "CASH" -> "CASH"
        else -> document.paymentSnapshot.modeLabel.ifBlank { "-" }
    }

    private fun buildTransportDeliveryPaymentHtml(voucher: Voucher, document: InvoiceDocument, business: BusinessProfile): String {
        val transportLines = buildList {
            if (voucher.transporterName.isNotBlank()) add("Transport: ${escapeHtml(voucher.transporterName)}")
            if (voucher.vehicleNo.isNotBlank()) add("Vehicle No.: ${escapeHtml(voucher.vehicleNo)}")
            if (voucher.lrNo.isNotBlank()) add("LR / GR No.: ${escapeHtml(voucher.lrNo)}")
            if (voucher.destination.isNotBlank()) add("Destination: ${escapeHtml(voucher.destination)}")
        }
        val paymentLine = when (voucher.paymentMode.normalizedPaymentMode()) {
            "PART PAYMENT" -> "Part payment received: ${formatMoney(document.paymentSnapshot.partPaymentReceived)}<br/>Outstanding due: ${formatMoney(document.paymentSnapshot.balanceDue)}"
            "CREDIT" -> "Full amount on credit."
            "BANK" -> "Payment through bank transfer."
            "UPI" -> "Payment through UPI."
            "CHEQUE" -> "Payment through cheque."
            "CASH" -> "Payment received in cash."
            else -> escapeHtml(document.paymentSnapshot.summaryLabel.ifBlank { "-" })
        }
        val bankLines = buildList {
            if (business.bankName.isNotBlank()) add("Seller's bank details")
            if (business.bankName.isNotBlank()) add("Bank: ${escapeHtml(business.bankName)}")
            if (business.accountNo.isNotBlank()) add("A/C No: ${escapeHtml(business.accountNo)}")
            if (business.ifsc.isNotBlank()) add("IFSC: ${escapeHtml(business.ifsc)}")
            if (business.branchName.isNotBlank()) add("Branch: ${escapeHtml(business.branchName)}")
        }
        return """
        <div class='small-label'>Transport / Delivery / Payment</div>
        <div>${transportLines.joinToString("<br/>").ifBlank { "-" }}</div>
        <div style='border-top:1px solid #cfd5df; margin:8px 0 6px 0;'></div>
        <div>$paymentLine</div>
        ${if (bankLines.isNotEmpty()) "<div style='border-top:1px solid #cfd5df; margin:8px 0 6px 0;'></div><div><span class='bold'>${bankLines.first()}</span>${bankLines.drop(1).joinToString(prefix = "<br/>", separator = "<br/>")}</div>" else ""}
        """.trimIndent()
    }

    private fun buildItemGstRows(document: InvoiceDocument, isIntrastate: Boolean): String {
        val rows = mutableListOf<String>()
        if (document.voucher.cgst > 0.0 && isIntrastate) rows += "<tr><td></td><td colspan='5' class='right bold'>CGST</td><td class='right'>${formatMoney(document.voucher.cgst)}</td></tr>"
        if (document.voucher.sgst > 0.0 && isIntrastate) rows += "<tr><td></td><td colspan='5' class='right bold'>SGST</td><td class='right'>${formatMoney(document.voucher.sgst)}</td></tr>"
        if (document.voucher.igst > 0.0 && !isIntrastate) rows += "<tr><td></td><td colspan='5' class='right bold'>IGST</td><td class='right'>${formatMoney(document.voucher.igst)}</td></tr>"
        return rows.joinToString("\n")
    }

    private fun buildChargeSummaryHtml(document: InvoiceDocument): String {
        val totals = document.totals
        return """
        <table class='inner-table snapshot-table'>
          <tr><td>Items Quantity</td><td class='right num'>${formatQty(totals.totalQuantity)}</td></tr>
          <tr><td>Taxable Amount</td><td class='right num'>${formatMoney(totals.taxableAmount)}</td></tr>
          <tr><td>CGST</td><td class='right num'>${formatMoney(totals.cgst)}</td></tr>
          <tr><td>${if (document.voucher.isIgst) "IGST" else "SGST / UTGST"}</td><td class='right num'>${formatMoney(if (document.voucher.isIgst) totals.igst else totals.sgst)}</td></tr>
          ${document.additionalCharges.joinToString("") { "<tr><td>${escapeHtml(it.label.ifBlank { "Other" })}</td><td class='right num'>${formatMoney(it.amount)}</td></tr>" }}
          <tr class='grand'><td>Grand Total</td><td class='right num'>${formatMoney(totals.netAmount)}</td></tr>
        </table>
        """.trimIndent()
    }

    private fun buildBalanceSnapshotHtml(document: InvoiceDocument): String {
        val snapshot = document.paymentSnapshot
        return """
        <table class='inner-table snapshot-table'>
          <tr><td>Previous Due</td><td class='right num'>${formatMoney(snapshot.previousDue)}</td></tr>
          <tr><td>Current Invoice</td><td class='right num'>${formatMoney(snapshot.currentInvoiceAmount)}</td></tr>
          <tr><td>Part Payment</td><td class='right num'>${formatMoney(snapshot.partPaymentReceived)}</td></tr>
          <tr><td>Outstanding / Due</td><td class='right num'>${formatMoney(snapshot.balanceDue)}</td></tr>
          <tr class='grand'><td>Total Invoice</td><td class='right num'>${formatMoney(snapshot.currentInvoiceAmount)}</td></tr>
        </table>
        """.trimIndent()
    }

    private fun buildDeclarationHtml(rawTerms: String): String =
        rawTerms.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapIndexed { index, line ->
                val prefix = if (line.first().isDigit()) "" else "${index + 1}. "
                "<span class='terms-line'>$prefix${escapeHtml(line)}</span>"
            }
            .joinToString("")

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
              <td class='center num'>${escapeHtml(summary.hsnCode)}</td>
              <td class='right num'>${formatMoney(summary.taxableValue)}</td>
              <td class='center num'>${rateLabel(summary.cgstRate)}</td>
              <td class='right num'>${formatMoney(summary.cgstAmount)}</td>
              <td class='center num'>$rightRate</td>
              <td class='right num'>$rightAmount</td>
              <td class='right num'>${formatMoney(summary.totalTaxAmount)}</td>
            </tr>
            """.trimIndent()
        }
        val totalRate = if (isIntrastate) rateLabel(document.taxSummary.firstOrNull()?.sgstRate ?: 0.0) else rateLabel(document.taxSummary.firstOrNull()?.igstRate ?: 0.0)
        val totalAmount = if (isIntrastate) formatMoney(document.totals.sgst) else formatMoney(document.totals.igst)
        return """
        <table class='inner-table gst-table'>
          <tr>
            <th>HSN/SAC</th>
            <th>Taxable Value</th>
            <th>CGST Rate</th>
            <th>CGST Amount</th>
            <th>${if (isIntrastate) "SGST Rate" else "IGST Rate"}</th>
            <th>${if (isIntrastate) "SGST Amount" else "IGST Amount"}</th>
            <th>Total Tax Amount</th>
          </tr>
          $taxRows
          <tr>
            <td class='right bold'>Total</td>
            <td class='right bold num'>${formatMoney(document.totals.taxableAmount)}</td>
            <td class='center bold num'>${rateLabel(document.taxSummary.firstOrNull()?.cgstRate ?: 0.0)}</td>
            <td class='right bold num'>${formatMoney(document.totals.cgst)}</td>
            <td class='center bold num'>$totalRate</td>
            <td class='right bold num'>$totalAmount</td>
            <td class='right bold num'>${formatMoney(document.totals.totalTaxAmount)}</td>
          </tr>
        </table>
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
            voucher.paymentMode.normalizedPaymentMode() == "PART PAYMENT" -> "PART PAYMENT"
            voucher.paymentMode.normalizedPaymentMode() == "CREDIT" -> "CREDIT"
            else -> voucher.paymentMode.normalizedPaymentMode()
        }
    }

    private fun buildPaymentSummaryLabel(voucher: Voucher, extras: VoucherRenderExtras): String {
        return when {
            extras.isAdvance -> "Advance amount recorded for future adjustment."
            voucher.paymentMode.normalizedPaymentMode() == "PART PAYMENT" -> {
                val dueText = extras.creditDueDate.takeIf { it.isNotBlank() }?.let(::formatDueDateText)
                "Part payment received: ${formatMoney(extras.partialAmountPaid)}. Balance due: ${formatMoney(extras.remainingCreditAmount)}${dueText?.let { " by $it" } ?: ""}."
            }
            voucher.paymentMode.normalizedPaymentMode() == "CREDIT" -> {
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

private fun String.normalizedPaymentMode(): String =
    trim().replace('_', ' ').uppercase(Locale.ENGLISH)

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
