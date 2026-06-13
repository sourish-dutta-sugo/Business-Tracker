package com.zerobook

import com.zerobook.data.BusinessProfile
import com.zerobook.data.Party
import com.zerobook.data.Voucher
import kotlin.math.absoluteValue
import kotlin.math.roundToLong

data class InvoiceLineItem(
    val description: String,
    val hsnSac: String = "",
    val qty: Double = 0.0,
    val unit: String = "",
    val rate: Double = 0.0,
    val taxableAmount: Double = 0.0,
)

fun buildInvoiceHtml(
    profile: BusinessProfile,
    party: Party?,
    voucher: Voucher,
    items: List<InvoiceLineItem> = emptyList(),
    sellerLogoDataUrl: String? = null,
    signatureDataUrl: String? = null,
    termsAndConditions: String = "",
): String {
    val gstTotal = voucher.netAmount - voucher.netAmount.coerceAtLeast(0.0) + 0.0
    val subtotal = items.sumOf { it.taxableAmount }
    val safeSubtotal = if (subtotal > 0.0) subtotal else voucher.netAmount
    val amountInWords = "INR ${amountToWords(voucher.netAmount)} Only"
    val spacerRows = (1..6).joinToString("") {
        """
        <tr>
          <td>&nbsp;</td><td></td><td></td><td></td><td></td><td></td><td></td>
        </tr>
        """.trimIndent()
    }
    val itemRows = if (items.isEmpty()) {
        """
        <tr>
          <td>1</td>
          <td>${escapeHtml(voucher.partyName.ifBlank { "Service Entry" })}</td>
          <td></td>
          <td>1.00</td>
          <td>Nos</td>
          <td>${formatAmount(voucher.netAmount)}</td>
          <td>${formatAmount(voucher.netAmount)}</td>
        </tr>
        """.trimIndent()
    } else {
        items.mapIndexed { index, item ->
            """
            <tr>
              <td>${index + 1}</td>
              <td>${escapeHtml(item.description)}</td>
              <td>${escapeHtml(item.hsnSac)}</td>
              <td>${formatQuantity(item.qty)}</td>
              <td>${escapeHtml(item.unit)}</td>
              <td>${formatAmount(item.rate)}</td>
              <td>${formatAmount(item.taxableAmount)}</td>
            </tr>
            """.trimIndent()
        }.joinToString("")
    }
    val gstRows = if (gstTotal > 0.0) {
        """
        <tr><td colspan="6" style="text-align:right;">OUTPUT CGST</td><td>${formatAmount(gstTotal / 2)}</td></tr>
        <tr><td colspan="6" style="text-align:right;">OUTPUT SGST</td><td>${formatAmount(gstTotal / 2)}</td></tr>
        """.trimIndent()
    } else {
        ""
    }
    val paymentSummary = if (voucher.status == "CREDIT" || voucher.status == "PART PAYMENT") {
        """
        <table class="box payment">
          <tr><th colspan="2">Payment Summary</th></tr>
          <tr><td>Paid</td><td>${formatAmount(0.0)}</td></tr>
          <tr><td>Balance</td><td>${formatAmount(voucher.netAmount)}</td></tr>
        </table>
        """.trimIndent()
    } else {
        ""
    }
    val gstBreakup = if (gstTotal > 0.0) {
        """
        <table class="box gst-breakup">
          <tr><th>GST Head</th><th>Amount</th></tr>
          <tr><td>CGST</td><td>${formatAmount(gstTotal / 2)}</td></tr>
          <tr><td>SGST</td><td>${formatAmount(gstTotal / 2)}</td></tr>
          <tr><td>Tax Amount (in words)</td><td>INR ${amountToWords(gstTotal)} Only</td></tr>
        </table>
        """.trimIndent()
    } else {
        ""
    }
    val logo = sellerLogoDataUrl?.takeIf { it.isNotBlank() }?.let {
        """<img src="$it" alt="Logo" style="max-height:72px;max-width:120px;display:block;margin-bottom:8px;" />"""
    }.orEmpty()
    val signature = signatureDataUrl?.takeIf { it.isNotBlank() }?.let {
        """<img src="$it" alt="Signature" style="max-height:72px;max-width:180px;display:block;margin-left:auto;" />"""
    }.orEmpty()

    return """
    <!DOCTYPE html>
    <html>
    <head>
      <meta charset="utf-8" />
      <title>TAX INVOICE</title>
      <style>
        body { font-family: Arial, sans-serif; margin: 16px; color: #000; }
        h1 { text-align: center; font-size: 24px; margin: 0 0 12px; }
        table { width: 100%; border-collapse: collapse; }
        .box, .box td, .box th { border: 1px solid #000; }
        .box td, .box th { padding: 6px; vertical-align: top; font-size: 12px; }
        .top td { width: 50%; }
        .items th { background: #efefef; text-align: center; }
        .items td:nth-child(1), .items td:nth-child(3), .items td:nth-child(4), .items td:nth-child(5) { text-align: center; }
        .items td:nth-child(6), .items td:nth-child(7), .right { text-align: right; }
        .total { background: #efefef; font-weight: 700; }
        .split { display: flex; gap: 16px; margin-top: 12px; }
        .split > div { flex: 1; }
        .footer { display: flex; justify-content: space-between; align-items: flex-end; margin-top: 20px; }
        .signature { text-align: right; min-height: 96px; }
        .city-footer { text-align: center; margin-top: 16px; font-size: 12px; }
      </style>
    </head>
    <body>
      <h1>TAX INVOICE</h1>
      <table class="box top">
        <tr>
          <td>
            $logo
            <strong>${escapeHtml(profile.businessName.ifBlank { "ZeroBook Business" })}</strong><br/>
            ${escapeHtml(profile.city)} ${escapeHtml(profile.state)}<br/>
            Phone: ${escapeHtml(profile.phone)}<br/>
            GSTIN: <br/>
            Email: ${escapeHtml(profile.email)}<br/>
            PAN:
          </td>
          <td>
            <table style="width:100%; border-collapse: collapse;">
              <tr><td><strong>Invoice No.</strong></td><td>${escapeHtml(voucher.voucherNo)}</td><td><strong>Dated</strong></td><td>${escapeHtml(voucher.dateLabel)}</td></tr>
              <tr><td><strong>Mode/Terms</strong></td><td>${escapeHtml(voucher.status)}</td><td><strong>Due Date</strong></td><td></td></tr>
              <tr><td><strong>Reference No.</strong></td><td colspan="3"></td></tr>
              <tr><td><strong>Other References</strong></td><td colspan="3"></td></tr>
              <tr><td><strong>Terms of Delivery</strong></td><td colspan="3">${escapeHtml(termsAndConditions)}</td></tr>
            </table>
          </td>
        </tr>
        <tr>
          <td colspan="2">
            <strong>Buyer</strong><br/>
            <strong>${escapeHtml(party?.name ?: voucher.partyName)}</strong><br/>
            ${escapeHtml(party?.city.orEmpty())}<br/>
            GSTIN: <br/>
            State: ${escapeHtml(party?.city.orEmpty())}
          </td>
        </tr>
      </table>

      <table class="box items" style="margin-top:12px;">
        <tr>
          <th>#</th>
          <th>Description of Goods</th>
          <th>HSN/SAC</th>
          <th>Qty</th>
          <th>Unit</th>
          <th>Rate</th>
          <th>Taxable Amount</th>
        </tr>
        $itemRows
        $gstRows
        $spacerRows
        <tr class="total">
          <td colspan="6">Total</td>
          <td>${formatAmount(safeSubtotal)}</td>
        </tr>
        <tr>
          <td colspan="7"><strong>Amount in words:</strong> $amountInWords</td>
        </tr>
      </table>

      <div class="split">
        <div>$paymentSummary</div>
        <div>$gstBreakup</div>
      </div>

      <div class="footer">
        <div>
          <strong>Bank Details</strong><br/>
          Bank Name:<br/>
          A/c No.:<br/>
          IFSC:<br/>
        </div>
        <div class="signature">
          $signature
          for ${escapeHtml(profile.businessName.ifBlank { "ZeroBook Business" })}<br/><br/>
          Authorised Signatory
        </div>
      </div>

      <div class="city-footer">${escapeHtml(profile.city)}</div>
    </body>
    </html>
    """.trimIndent()
}

private fun escapeHtml(value: String): String = buildString {
    value.forEach { character ->
        append(
            when (character) {
                '&' -> "&amp;"
                '<' -> "&lt;"
                '>' -> "&gt;"
                '"' -> "&quot;"
                '\'' -> "&#39;"
                else -> character
            },
        )
    }
}

private fun formatQuantity(value: Double): String {
    val rounded = ((value * 100).roundToLong() / 100.0)
    return rounded.toString()
}

private fun formatAmount(value: Double): String {
    val rounded = ((value * 100).roundToLong() / 100.0)
    val sign = if (rounded < 0) "-" else ""
    val text = rounded.absoluteValue.toString().split(".")
    val integer = text.firstOrNull().orEmpty()
    val fraction = text.getOrNull(1).orEmpty().padEnd(2, '0').take(2)
    return "$sign₹$integer.$fraction"
}

private fun amountToWords(value: Double): String {
    val whole = value.roundToLong()
    if (whole == 0L) return "Zero"
    return buildIndianNumberWords(whole)
}

private fun buildIndianNumberWords(value: Long): String {
    val ones = listOf(
        "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
        "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen",
        "Seventeen", "Eighteen", "Nineteen",
    )
    val tens = listOf("", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety")
    fun belowHundred(number: Long): String = when {
        number < 20 -> ones[number.toInt()]
        else -> listOf(tens[(number / 10).toInt()], ones[(number % 10).toInt()]).filter { it.isNotBlank() }.joinToString(" ")
    }
    fun belowThousand(number: Long): String = when {
        number < 100 -> belowHundred(number)
        else -> listOf(
            ones[(number / 100).toInt()],
            "Hundred",
            belowHundred(number % 100),
        ).filter { it.isNotBlank() }.joinToString(" ")
    }
    val parts = listOf(
        value / 10000000 to "Crore",
        (value / 100000) % 100 to "Lakh",
        (value / 1000) % 100 to "Thousand",
        value % 1000 to "",
    )
    return parts
        .filter { it.first > 0 }
        .joinToString(" ") { (number, label) ->
            listOf(belowThousand(number), label).filter { it.isNotBlank() }.joinToString(" ")
        }
}
