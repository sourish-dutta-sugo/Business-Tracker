package com.example.services

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfDocument
import android.os.Handler
import android.os.Looper
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.View
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
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
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
        val party: Party?
    )

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

    private val invoiceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val voucherExtrasCache = mutableMapOf<String, VoucherRenderExtras>()
    private val invoiceDateFormat = SimpleDateFormat("dd-MMM-yy", Locale.ENGLISH)

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
        val db = AppDatabase.getDatabase(context)
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
        primeVoucherRenderExtras(context, voucherId)
        return InvoiceDocumentData(
            voucher = voucher,
            items = items,
            charges = charges,
            profile = profile,
            party = party
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
        val resolvedParty = party ?: Party(
            id = "",
            name = "Walk-in Customer",
            type = "CUSTOMER",
            phone = "",
            email = "",
            address = "",
            city = "",
            state = business.state,
            stateCode = business.stateCode,
            pin = "",
            gstin = null,
            pan = null,
            openingBalance = 0.0,
            balanceType = "DR"
        )
        val extras = voucherExtrasCache[voucher.id] ?: VoucherRenderExtras(paymentModeValue = voucher.paymentMode)
        return buildInvoiceHtmlInternal(
            voucher = voucher,
            items = items,
            business = business,
            party = resolvedParty,
            additionalCharges = additionalCharges,
            extras = extras
        )
    }

    private fun buildInvoiceHtmlInternal(
        voucher: Voucher,
        items: List<VoucherItem>,
        business: BusinessProfile,
        party: Party,
        additionalCharges: List<AdditionalCharge>,
        extras: VoucherRenderExtras
    ): String {
        val totalQty = items.sumOf { it.qty }
        val qtyUnit = items.firstOrNull()?.unit.orEmpty()
        val totalGst = voucher.cgst + voucher.sgst + voucher.igst
        val showGst = business.gstin.isNotBlank() && totalGst > 0.0
        val isIntrastate = !voucher.isIgst && party.stateCode.isNotBlank() && party.stateCode == business.stateCode
        val isProforma = voucher.documentType.equals("PROFORMA", ignoreCase = true) ||
            voucher.type.equals("PROFORMA", ignoreCase = true)
        val invoiceHeading = when {
            isProforma -> "PROFORMA INVOICE"
            business.gstin.isNotBlank() -> "TAX INVOICE"
            else -> "INVOICE"
        }

        val normalizedPaymentMode = when {
            extras.isAdvance -> "ADVANCE"
            else -> (extras.paymentModeValue.ifBlank { voucher.paymentMode })
                .replace("_", " ")
                .trim()
                .uppercase(Locale.ENGLISH)
        }

        val narrationLines = buildList {
            if (business.termsAndConditions.isNotBlank()) {
                add(escapeHtml(business.termsAndConditions).replace("\n", "<br/>"))
            }
            if (voucher.narration.isNotBlank() && voucher.narration != business.termsAndConditions) {
                add(escapeHtml(voucher.narration).replace("\n", "<br/>"))
            }
        }
        val declarationHtml = if (narrationLines.isEmpty()) {
            ""
        } else {
            """
            <div style='margin-top:8px;'>
              <div style='font-weight:bold;font-size:9px;'>Declaration</div>
              <div style='font-size:9px;line-height:1.5;color:#333333;margin-top:2px;'>
                TERMS &amp; CONDITIONS<br/>
                ${narrationLines.joinToString("<br/>")}
              </div>
            </div>
            """.trimIndent()
        }

        val logoHtml = if (business.logoPath?.isNotBlank() == true && File(business.logoPath).exists()) {
            "<img src='${toFileUrl(business.logoPath)}' style='max-height:65px;max-width:120px;object-fit:contain;display:block;'/>"
        } else {
            ""
        }
        val signatureHtml = if (business.signaturePath?.isNotBlank() == true && File(business.signaturePath).exists()) {
            """
            <div style='margin:8px 0 4px 0;'>
              <img src='${toFileUrl(business.signaturePath)}'
                   style='max-height:55px;max-width:160px;object-fit:contain;display:block;margin-left:auto;'/>
            </div>
            """.trimIndent()
        } else {
            "<div style='height:55px;'></div>"
        }

        val sellerLines = buildList {
            if (business.address.isNotBlank()) add(escapeHtml(business.address).replace("\n", "<br/>"))
            if (business.phone.isNotBlank()) add("Ph : ${escapeHtml(business.phone)}")
            if (business.gstin.isNotBlank()) add("GSTIN/UIN: ${escapeHtml(business.gstin)}")
            if (business.state.isNotBlank()) add("State Name : ${escapeHtml(business.state)}, Code : ${escapeHtml(business.stateCode)}")
            if (business.email.isNotBlank()) add("E-Mail : ${escapeHtml(business.email)}")
            if (business.pan.isNotBlank()) add("PAN: ${escapeHtml(business.pan)}")
        }.joinToString("<br/>")

        val buyerCityPin = listOfNotNull(
            party.city.takeIf { it.isNotBlank() }?.let(::escapeHtml),
            party.pin.takeIf { it.isNotBlank() }?.let(::escapeHtml)
        ).joinToString(" - ")
        val buyerLines = buildList {
            if (party.address.isNotBlank()) add(escapeHtml(party.address).replace("\n", "<br/>"))
            if (buyerCityPin.isNotBlank()) add(buyerCityPin)
            if (!party.pan.isNullOrBlank()) add("PAN: ${escapeHtml(party.pan!!)}")
            if (!party.gstin.isNullOrBlank()) add("GSTIN: ${escapeHtml(party.gstin!!)}")
            if (party.state.isNotBlank()) add("State Name : ${escapeHtml(party.state)}, Code : ${escapeHtml(party.stateCode)}")
            if (party.state.isNotBlank()) add("Place of Supply   : ${escapeHtml(party.state)}")
        }.joinToString("<br/>")

        val paymentTermsHtml = buildPaymentTermsHtml(
            voucher = voucher,
            extras = extras,
            normalizedPaymentMode = normalizedPaymentMode
        )

        val transportRows = buildList<Pair<String, String>> {
            if (voucher.transporterName.isNotBlank()) add("Transporter" to voucher.transporterName)
            if (voucher.vehicleNo.isNotBlank()) add("Vehicle No." to voucher.vehicleNo)
            if (voucher.lrNo.isNotBlank()) add("LR/GR No." to voucher.lrNo)
            if (voucher.destination.isNotBlank()) add("Destination" to voucher.destination)
        }.joinToString("") { (label, value) ->
            """
            <tr>
              <td style='border:none;border-bottom:1px solid #cccccc;padding:4px;'>
                <div style='font-size:9px;color:#555;'>$label</div>
                <div style='font-weight:bold;'>${escapeHtml(value)}</div>
              </td>
            </tr>
            """.trimIndent()
        }

        val itemRows = items.mapIndexed { index, item ->
            val rowBg = if (index % 2 == 0) "#ffffff" else "#fafafa"
            """
            <tr style='background:$rowBg;'>
              <td align='center'>${index + 1}</td>
              <td><strong>${escapeHtml(item.productName)}</strong></td>
              <td align='center'>${if (item.hsnCode.isBlank()) "" else escapeHtml(item.hsnCode)}</td>
              <td align='center'>${formatQty(item.qty)} ${escapeHtml(item.unit)}</td>
              <td align='right'>${formatMoney(item.rate)}</td>
              <td align='center'>${escapeHtml(item.unit)}</td>
              <td align='right'>${formatMoney(item.taxableAmount)}</td>
            </tr>
            """.trimIndent()
        }.joinToString("")

        val additionalChargeRows = additionalCharges.joinToString("") { charge ->
            """
            <tr>
              <td></td>
              <td colspan='5' align='right'><em><strong>${escapeHtml(charge.label)}</strong></em></td>
              <td align='right'>${formatMoney(charge.amount)}</td>
            </tr>
            """.trimIndent()
        }

        val taxSummaryRows = buildString {
            if (showGst && voucher.cgst > 0.0 && isIntrastate) {
                append(
                    """
                    <tr>
                      <td></td>
                      <td colspan='5' align='right'><em><strong>OUTPUT CGST</strong></em></td>
                      <td align='right'>${formatMoney(voucher.cgst)}</td>
                    </tr>
                    """.trimIndent()
                )
            }
            if (showGst && voucher.sgst > 0.0 && isIntrastate) {
                append(
                    """
                    <tr>
                      <td></td>
                      <td colspan='5' align='right'><em><strong>OUTPUT SGST</strong></em></td>
                      <td align='right'>${formatMoney(voucher.sgst)}</td>
                    </tr>
                    """.trimIndent()
                )
            }
            if (showGst && voucher.igst > 0.0 && !isIntrastate) {
                append(
                    """
                    <tr>
                      <td></td>
                      <td colspan='5' align='right'><em><strong>OUTPUT IGST</strong></em></td>
                      <td align='right'>${formatMoney(voucher.igst)}</td>
                    </tr>
                    """.trimIndent()
                )
            }
            if (abs(voucher.roundOff) > 0.0) {
                append(
                    """
                    <tr>
                      <td></td>
                      <td colspan='5' align='right'><em><strong>ROUND OFF (S)</strong></em></td>
                      <td align='right'>${formatSignedMoney(voucher.roundOff)}</td>
                    </tr>
                    """.trimIndent()
                )
            }
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

        val paymentSummaryBox = buildPaymentSummarySection(
            voucher = voucher,
            extras = extras,
            normalizedPaymentMode = normalizedPaymentMode
        )

        val gstBreakupSection = if (showGst) {
            buildGstBreakupSection(items, voucher, isIntrastate)
        } else {
            ""
        }

        val bankRows = buildList<String> {
            if (business.bankName.isNotBlank()) add(bankDetailRow("Bank Name", business.bankName))
            if (business.accountNo.isNotBlank()) add(bankDetailRow("A/c No.", business.accountNo))
            if (business.ifsc.isNotBlank()) add(bankDetailRow("IFS Code", business.ifsc))
            if (business.branchName.isNotBlank()) add(bankDetailRow("Branch", business.branchName))
        }.joinToString("")

        val bankSection = if (bankRows.isBlank()) {
            ""
        } else {
            """
            <div style='font-weight:bold;font-size:10px;margin-bottom:4px;'>Company's Bank Details</div>
            <table style='border:none;font-size:10px;border-collapse:collapse;width:auto;'>
              $bankRows
            </table>
            """.trimIndent()
        }

        val amountWords = amountInWords(voucher.netAmount)
            .removePrefix("Rupees ")
            .removeSuffix(" Only")
            .trim()

        return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset='UTF-8'/>
          <meta name='viewport' content='width=device-width, initial-scale=1.0'/>
          <style>
            body { font-family: Arial, Helvetica, sans-serif; font-size: 11px; color: #000000; background: #FFFFFF; margin: 0; }
            .page { width: 100%; padding: 20px 24px; background: #FFFFFF; }
            table { width: 100%; border-collapse: collapse; table-layout: fixed; }
            td, th { border: 1px solid #000000; padding: 3px 5px; vertical-align: top; }
            .header-gray { background: #f0f0f0; font-weight: bold; font-size: 10px; }
          </style>
        </head>
        <body>
          <div class='page'>
            <div style='text-align:center;font-weight:bold;font-size:14px;letter-spacing:2px;margin-bottom:8px;'>$invoiceHeading</div>

            <table>
              <tr>
                <td style='width:60%;border-right:2px solid #000000;'>
                  <table style='border:none;width:100%;'>
                    <tr>
                      <td style='width:95px;border:none;vertical-align:middle;'>$logoHtml</td>
                      <td style='border:none;padding-left:8px;vertical-align:middle;'>
                        <div style='font-size:14px;font-weight:bold;'>${escapeHtml(business.businessName)}</div>
                      </td>
                    </tr>
                  </table>
                  <div style='line-height:1.8;font-size:11px;'>$sellerLines</div>
                  <div style='border-top:1px solid #000000;margin-top:8px;margin-bottom:6px;'></div>
                  <div style='font-size:9px;color:#555555;'>Buyer (Bill to)</div>
                  <div style='font-weight:bold;font-size:12px;margin-top:3px;margin-bottom:4px;'>${escapeHtml(party.name)}</div>
                  <div style='line-height:1.8;font-size:11px;'>$buyerLines</div>
                </td>
                <td style='width:40%;padding:0;'>
                  <table style='border:none;width:100%;'>
                    <tr>
                      <td style='border:none;border-bottom:1px solid #cccccc;padding:4px;'>
                        <div style='display:flex;'>
                          <div style='width:50%;'>
                            <div style='font-size:9px;color:#555;'>Invoice No.</div>
                            <div style='font-weight:bold;'>${escapeHtml(voucher.voucherNo)}</div>
                          </div>
                          <div style='width:50%;border-left:1px solid #cccccc;padding-left:6px;'>
                            <div style='font-size:9px;color:#555;'>Dated</div>
                            <div style='font-weight:bold;'>${invoiceDateFormat.format(Date(voucher.date))}</div>
                          </div>
                        </div>
                      </td>
                    </tr>
                    <tr>
                      <td style='border:none;border-bottom:1px solid #cccccc;padding:4px;'>
                        <div style='font-size:9px;color:#555;'>Mode/Terms of Payment</div>
                        $paymentTermsHtml
                      </td>
                    </tr>
                    <tr>
                      <td style='border:none;border-bottom:1px solid #cccccc;padding:4px;'>
                        <div style='font-size:9px;color:#555;'>Reference No. &amp; Date</div>
                        <div>${escapeHtml(voucher.referenceNo)}</div>
                      </td>
                    </tr>
                    <tr>
                      <td style='border:none;border-bottom:1px solid #cccccc;padding:4px;'>
                        <div style='font-size:9px;color:#555;'>Buyer's Order No.</div>
                        <div>${escapeHtml(voucher.buyerOrderNo)}</div>
                      </td>
                    </tr>
                    <tr>
                      <td style='border:none;border-bottom:${if (transportRows.isBlank()) "none" else "1px solid #cccccc"};padding:4px;'>
                        <div style='font-size:9px;color:#555;'>Terms of Delivery</div>
                        <div>${escapeHtml(voucher.termsOfDelivery)}</div>
                      </td>
                    </tr>
                    $transportRows
                  </table>
                </td>
              </tr>
            </table>

            <table>
              <tr class='header-gray'>
                <th width='4%' align='center'>Sl<br/>No.</th>
                <th width='35%' align='center'>Description of Goods</th>
                <th width='10%' align='center'>HSN/SAC</th>
                <th width='13%' align='center'>Quantity</th>
                <th width='9%' align='right'>Rate</th>
                <th width='6%' align='center'>per</th>
                <th width='13%' align='right'>Amount</th>
              </tr>
              $itemRows
              $additionalChargeRows
              $taxSummaryRows
              $spacerRows
              <tr style='font-weight:bold;background:#f5f5f5;'>
                <td></td>
                <td><strong>Total</strong></td>
                <td></td>
                <td align='center'><strong>${formatQty(totalQty)} ${escapeHtml(qtyUnit)}</strong></td>
                <td></td>
                <td></td>
                <td align='right'><strong>${formatMoney(voucher.netAmount)}</strong></td>
              </tr>
            </table>

            <table>
              <tr>
                <td style='width:72%;border:1px solid #000000;padding:5px 6px;'>
                  <div style='font-size:9px;color:#555;'>Amount Chargeable (in words)</div>
                  <div style='font-weight:bold;font-size:11px;'>Indian Rupees $amountWords Only</div>
                </td>
                <td style='width:28%;border:1px solid #000000;padding:5px 6px;text-align:right;vertical-align:bottom;'>
                  <em style='font-size:9px;'>E. &amp; O.E</em>
                </td>
              </tr>
            </table>

            $paymentSummaryBox

            $gstBreakupSection

            <table style='margin-top:0;'>
              <tr>
                <td style='width:55%;border:1px solid #000000;vertical-align:top;padding:8px;'>
                  $bankSection
                  $declarationHtml
                </td>
                <td style='width:5%;border:none;'></td>
                <td style='width:40%;border:1px solid #000000;vertical-align:bottom;text-align:right;padding:8px;'>
                  <div style='font-weight:bold;font-size:10px;'>for ${escapeHtml(business.businessName)}</div>
                  $signatureHtml
                  <hr style='border:none;border-top:1px solid #000000;margin:4px 0;'/>
                  <div style='font-size:9px;'>Authorised Signatory</div>
                </td>
              </tr>
            </table>

            <p style='text-align:center;font-size:10px;color:#666666;margin-top:10px;'>${escapeHtml(business.city)}</p>
          </div>
        </body>
        </html>
        """.trimIndent()
    }

    private fun buildPaymentTermsHtml(
        voucher: Voucher,
        extras: VoucherRenderExtras,
        normalizedPaymentMode: String
    ): String {
        val chequeDateText = voucher.chequeDate?.let { invoiceDateFormat.format(Date(it)) }.orEmpty()
        val dueDateText = extras.creditDueDate.takeIf { it.isNotBlank() }?.let(::formatDueDateText).orEmpty()
        return when (normalizedPaymentMode) {
            "CASH" -> "<div style='font-weight:bold;'>Cash</div>"
            "BANK" -> "<div style='font-weight:bold;'>Bank Transfer</div>"
            "UPI" -> "<div style='font-weight:bold;'>UPI</div>"
            "CHEQUE" -> buildString {
                append("<div style='font-weight:bold;'>Cheque</div>")
                if (!voucher.chequeNo.isNullOrBlank()) {
                    append("<div style='font-size:9px;'>Cheque No: ${escapeHtml(voucher.chequeNo!!)}")
                    if (chequeDateText.isNotBlank()) {
                        append(" dated $chequeDateText")
                    }
                    append("</div>")
                }
            }
            "PART PAYMENT" -> buildString {
                append("<div style='font-weight:bold;'>Part Payment</div>")
                append("<div style='font-size:9px;color:#333;'>Paid: &#8377;${formatMoney(extras.partialAmountPaid)} via ${escapeHtml(extras.partialPaymentSubmode.ifBlank { "CASH" })}</div>")
                append("<div style='font-size:9px;color:#333;'>Balance Due: &#8377;${formatMoney(extras.remainingCreditAmount)}")
                if (dueDateText.isNotBlank()) {
                    append(" $dueDateText")
                }
                append("</div>")
            }
            "CREDIT" -> buildString {
                append("<div style='font-weight:bold;'>Credit</div>")
                append("<div style='font-size:9px;color:#333;'>Full amount on credit</div>")
                append("<div style='font-size:9px;color:#333;'>Due: ${if (dueDateText.isBlank()) "No due date" else dueDateText}</div>")
            }
            "ADVANCE" -> buildString {
                append("<div style='font-weight:bold;'>Advance Receipt</div>")
                append("<div style='font-size:9px;color:#333;'>Advance amount: &#8377;${formatMoney(voucher.netAmount)}</div>")
            }
            else -> "<div style='font-weight:bold;'>${escapeHtml(normalizedPaymentMode)}</div>"
        }
    }

    private fun buildPaymentSummarySection(
        voucher: Voucher,
        extras: VoucherRenderExtras,
        normalizedPaymentMode: String
    ): String {
        return when (normalizedPaymentMode) {
            "PART PAYMENT" -> {
                val dueDateText = extras.creditDueDate.takeIf { it.isNotBlank() }?.let(::formatDueDateText).orEmpty()
                """
                <table style='width:100%;border-collapse:collapse;margin-top:0;'>
                  <tr>
                    <td colspan='2' style='background:#f8f8f8;font-weight:bold;font-size:10px;border:1px solid #000000;padding:4px 6px;'>Payment Summary</td>
                  </tr>
                  <tr>
                    <td style='border:1px solid #000000;padding:3px 6px;width:60%;'>Invoice Total</td>
                    <td style='border:1px solid #000000;padding:3px 6px;text-align:right;font-weight:bold;'>&#8377;${formatMoney(voucher.netAmount)}</td>
                  </tr>
                  <tr>
                    <td style='border:1px solid #000000;padding:3px 6px;'>Amount Paid (via ${escapeHtml(extras.partialPaymentSubmode.ifBlank { "CASH" })})</td>
                    <td style='border:1px solid #000000;padding:3px 6px;text-align:right;'>&#8377;${formatMoney(extras.partialAmountPaid)}</td>
                  </tr>
                  <tr>
                    <td style='border:1px solid #000000;padding:3px 6px;font-weight:bold;'>Balance Due</td>
                    <td style='border:1px solid #000000;padding:3px 6px;text-align:right;font-weight:bold;'>&#8377;${formatMoney(extras.remainingCreditAmount)}</td>
                  </tr>
                  ${
                    if (dueDateText.isBlank()) ""
                    else """
                    <tr>
                      <td style='border:1px solid #000000;padding:3px 6px;font-size:9px;color:#555;'>Due Date</td>
                      <td style='border:1px solid #000000;padding:3px 6px;font-size:9px;text-align:right;'>$dueDateText</td>
                    </tr>
                    """.trimIndent()
                  }
                </table>
                """.trimIndent()
            }
            "ADVANCE" -> {
                val advanceMode = extras.partialPaymentSubmode.ifBlank { voucher.paymentMode.ifBlank { "Advance" } }
                """
                <table style='width:100%;border-collapse:collapse;margin-top:0;'>
                  <tr>
                    <td colspan='2' style='background:#f8f8f8;font-weight:bold;font-size:10px;border:1px solid #000000;padding:4px 6px;'>Advance Receipt Summary</td>
                  </tr>
                  <tr>
                    <td style='border:1px solid #000000;padding:3px 6px;'>Advance Received</td>
                    <td style='border:1px solid #000000;padding:3px 6px;text-align:right;font-weight:bold;'>&#8377;${formatMoney(voucher.netAmount)}</td>
                  </tr>
                  <tr>
                    <td style='border:1px solid #000000;padding:3px 6px;'>Payment Mode</td>
                    <td style='border:1px solid #000000;padding:3px 6px;text-align:right;'>${escapeHtml(advanceMode)}</td>
                  </tr>
                  <tr>
                    <td style='border:1px solid #000000;padding:3px 6px;font-size:9px;color:#555;'>This is an advance receipt for future goods or services.</td>
                    <td style='border:1px solid #000000;padding:3px 6px;'></td>
                  </tr>
                </table>
                """.trimIndent()
            }
            else -> ""
        }
    }

    private fun buildGstBreakupSection(
        items: List<VoucherItem>,
        voucher: Voucher,
        isIntrastate: Boolean
    ): String {
        val groupedRows = items
            .groupBy { "${it.hsnCode}|${it.gstRate}" }
            .entries
            .sortedBy { it.value.firstOrNull()?.gstRate ?: 0.0 }
            .joinToString("") { (_, groupedItems) ->
                val first = groupedItems.first()
                val taxable = groupedItems.sumOf { it.taxableAmount }
                val cgst = groupedItems.sumOf { it.cgstAmount }
                val sgst = groupedItems.sumOf { it.sgstAmount }
                val igst = groupedItems.sumOf { it.igstAmount }
                val halfRate = first.gstRate / 2.0
                """
                <tr>
                  <td style='border:1px solid #000000;padding:4px;'>${escapeHtml(first.hsnCode)}</td>
                  <td style='border:1px solid #000000;padding:4px;text-align:right;'>${formatMoney(taxable)}</td>
                  <td style='border:1px solid #000000;padding:4px;text-align:center;'>${if (isIntrastate) "${formatTaxRate(halfRate)}%" else ""}</td>
                  <td style='border:1px solid #000000;padding:4px;text-align:right;'>${if (isIntrastate) formatMoney(cgst) else ""}</td>
                  <td style='border:1px solid #000000;padding:4px;text-align:center;'>${if (isIntrastate) "${formatTaxRate(halfRate)}%" else ""}</td>
                  <td style='border:1px solid #000000;padding:4px;text-align:right;'>${if (isIntrastate) formatMoney(sgst) else ""}</td>
                  <td style='border:1px solid #000000;padding:4px;text-align:center;'>${if (!isIntrastate) "${formatTaxRate(first.gstRate)}%" else ""}</td>
                  <td style='border:1px solid #000000;padding:4px;text-align:right;'>${if (!isIntrastate) formatMoney(igst) else ""}</td>
                  <td style='border:1px solid #000000;padding:4px;text-align:right;'>${formatMoney(cgst + sgst + igst)}</td>
                </tr>
                """.trimIndent()
            }

        val taxWords = amountInWords(voucher.cgst + voucher.sgst + voucher.igst)
            .removePrefix("Rupees ")
            .removeSuffix(" Only")
            .trim()

        return """
        <table style='width:100%;border-collapse:collapse;'>
          <tr>
            <th rowspan='2' style='background:#e8e8e8;font-weight:bold;font-size:10px;border:1px solid #000000;padding:4px;'>HSN/SAC</th>
            <th rowspan='2' style='background:#e8e8e8;font-weight:bold;font-size:10px;border:1px solid #000000;padding:4px;'>Taxable Value</th>
            <th colspan='2' style='background:#e8e8e8;font-weight:bold;font-size:10px;border:1px solid #000000;padding:4px;'>CGST</th>
            <th colspan='2' style='background:#e8e8e8;font-weight:bold;font-size:10px;border:1px solid #000000;padding:4px;'>SGST/UTGST</th>
            <th colspan='2' style='background:#e8e8e8;font-weight:bold;font-size:10px;border:1px solid #000000;padding:4px;'>IGST</th>
            <th rowspan='2' style='background:#e8e8e8;font-weight:bold;font-size:10px;border:1px solid #000000;padding:4px;'>Total Tax Amount</th>
          </tr>
          <tr>
            <th style='background:#e8e8e8;font-weight:bold;font-size:10px;border:1px solid #000000;padding:4px;'>Rate</th>
            <th style='background:#e8e8e8;font-weight:bold;font-size:10px;border:1px solid #000000;padding:4px;'>Amount</th>
            <th style='background:#e8e8e8;font-weight:bold;font-size:10px;border:1px solid #000000;padding:4px;'>Rate</th>
            <th style='background:#e8e8e8;font-weight:bold;font-size:10px;border:1px solid #000000;padding:4px;'>Amount</th>
            <th style='background:#e8e8e8;font-weight:bold;font-size:10px;border:1px solid #000000;padding:4px;'>Rate</th>
            <th style='background:#e8e8e8;font-weight:bold;font-size:10px;border:1px solid #000000;padding:4px;'>Amount</th>
          </tr>
          $groupedRows
          <tr style='font-weight:bold;background:#f0f0f0;'>
            <td style='border:1px solid #000000;padding:4px;text-align:center;'>Total</td>
            <td style='border:1px solid #000000;padding:4px;text-align:right;'>${formatMoney(voucher.taxableAmount)}</td>
            <td style='border:1px solid #000000;padding:4px;'></td>
            <td style='border:1px solid #000000;padding:4px;text-align:right;'>${formatMoney(voucher.cgst)}</td>
            <td style='border:1px solid #000000;padding:4px;'></td>
            <td style='border:1px solid #000000;padding:4px;text-align:right;'>${formatMoney(voucher.sgst)}</td>
            <td style='border:1px solid #000000;padding:4px;'></td>
            <td style='border:1px solid #000000;padding:4px;text-align:right;'>${formatMoney(voucher.igst)}</td>
            <td style='border:1px solid #000000;padding:4px;text-align:right;'>${formatMoney(voucher.cgst + voucher.sgst + voucher.igst)}</td>
          </tr>
        </table>
        <table>
          <tr>
            <td style='font-size:10px;padding:5px 6px;border:1px solid #000000;'>
              <strong>Tax Amount (in words) : </strong>
              Indian Rupees $taxWords Only
            </td>
          </tr>
        </table>
        """.trimIndent()
    }

    fun numToWords(n: Long): String {
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

    fun amountInWords(amount: Double): String {
        val safeAmount = amount.coerceAtLeast(0.0)
        val rupees = safeAmount.toLong()
        val paise = ((safeAmount - rupees) * 100).roundToInt().coerceIn(0, 99)
        var result = "Rupees " + (numToWords(rupees).trim().ifBlank { "Zero" })
        if (paise > 0) {
            result += " and " + numToWords(paise.toLong()).trim() + " Paise"
        }
        return result + " Only"
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
        val encodedName = java.net.URLEncoder.encode(businessName, "UTF-8")
        val encodedNote = java.net.URLEncoder.encode("Invoice $invoiceNo", "UTF-8")
        return "upi://pay?pa=$upiId&pn=$encodedName&am=${"%.2f".format(Locale.ENGLISH, amount)}&cu=INR&tn=$encodedNote"
    }

    fun getQrHtml(upiLink: String, upiId: String): String {
        return if (upiId.isBlank()) "" else {
            """
            <div style='text-align:center; padding:8px;'>
              <div style='font-size:9px; color:#444;'>Scan &amp; Pay via UPI</div>
              <div style='font-size:8px; margin-top:4px; color:#000; word-break:break-all;'>${escapeHtml(upiId)}</div>
            </div>
            """.trimIndent()
        }
    }

    fun generateQrBase64(value: String, size: Int = 200): String? = null

    fun generatePdfFromVoucherId(
        context: Context,
        voucherId: String,
        onComplete: (File?, Voucher?) -> Unit
    ) {
        invoiceScope.launch {
            val documentData = loadInvoiceDocumentData(context, voucherId)
            if (documentData == null) {
                withContext(Dispatchers.Main) { onComplete(null, null) }
                return@launch
            }
            val html = buildInvoiceHtml(
                voucher = documentData.voucher,
                items = documentData.items,
                business = documentData.profile,
                party = documentData.party,
                additionalCharges = documentData.charges
            )
            withContext(Dispatchers.Main) {
                val webView = WebView(context)
                configureInvoiceWebView(webView)
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        val targetView = view ?: run {
                            onComplete(null, documentData.voucher)
                            return
                        }
                        Handler(Looper.getMainLooper()).postDelayed({
                            writePdfFromWebView(
                                context = context,
                                webView = targetView,
                                voucherNo = documentData.voucher.voucherNo
                            ) { file ->
                                onComplete(file, documentData.voucher)
                                targetView.destroy()
                            }
                        }, 500)
                    }
                }
                webView.loadDataWithBaseURL(
                    "file:///",
                    html,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        }
    }

    private fun writePdfFromWebView(
        context: Context,
        webView: WebView,
        voucherNo: String,
        onComplete: (File?) -> Unit
    ) {
        try {
            val dir = File(context.getExternalFilesDir(null), "ZeroBook/Invoices")
            dir.mkdirs()
            val pdfFile = File(dir, "${voucherNo.replace("/", "-")}.pdf")
            val pageWidth = 1240
            val pageHeight = 1754
            val widthSpec = View.MeasureSpec.makeMeasureSpec(pageWidth, View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            webView.measure(widthSpec, heightSpec)

            val contentHeight = max(
                webView.measuredHeight,
                (webView.contentHeight * context.resources.displayMetrics.density).roundToInt()
            ).coerceAtLeast(pageHeight)

            webView.layout(0, 0, pageWidth, contentHeight)

            val pdfDocument = PdfDocument()
            val totalPages = max(1, ceil(contentHeight / pageHeight.toDouble()).toInt())
            for (pageIndex in 0 until totalPages) {
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas
                canvas.save()
                canvas.translate(0f, -(pageIndex * pageHeight).toFloat())
                webView.draw(canvas)
                canvas.restore()
                pdfDocument.finishPage(page)
            }

            FileOutputStream(pdfFile).use { output ->
                pdfDocument.writeTo(output)
            }
            pdfDocument.close()
            onComplete(pdfFile)
        } catch (_: Exception) {
            onComplete(null)
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

fun printInvoice(
    context: Context,
    htmlContent: String,
    invoiceNo: String
) {
    val webView = WebView(context)
    configureInvoiceWebView(webView)
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            Handler(Looper.getMainLooper()).postDelayed({
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val printAdapter = view?.createPrintDocumentAdapter(invoiceNo) ?: return@postDelayed
                printManager.print(
                    invoiceNo,
                    printAdapter,
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
) {
    val uri = FileProvider.getUriForFile(
        context,
        context.packageName + ".provider",
        pdfFile
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share Invoice"))
}

fun shareInvoicePdfToWhatsApp(
    context: Context,
    pdfFile: File
) {
    val uri = FileProvider.getUriForFile(
        context,
        context.packageName + ".provider",
        pdfFile
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        setPackage("com.whatsapp")
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(
            Intent.createChooser(
                intent.apply { setPackage(null) },
                "Share Invoice"
            )
        )
    }
}

private fun escapeHtml(value: String): String =
    value.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

private fun toFileUrl(path: String): String =
    if (path.startsWith("/")) "file://$path" else "file:///$path"

private fun formatMoney(value: Double): String =
    DecimalFormat("#,##0.00").format(value)

private fun formatSignedMoney(value: Double): String =
    if (value < 0) "-${formatMoney(abs(value))}" else formatMoney(value)

private fun formatQty(value: Double): String =
    String.format(Locale.ENGLISH, "%.3f", value)

private fun formatTaxRate(value: Double): String =
    if (value % 1.0 == 0.0) String.format(Locale.ENGLISH, "%.0f", value) else String.format(Locale.ENGLISH, "%.2f", value)

private fun formatDueDateText(raw: String): String {
    val millis = raw.toLongOrNull()
    return if (millis != null) {
        SimpleDateFormat("dd-MMM-yy", Locale.ENGLISH).format(Date(millis))
    } else {
        raw
    }
}

private fun bankDetailRow(label: String, value: String): String {
    return """
    <tr>
      <td style='color:#444;padding:2px 8px 2px 0;border:none;white-space:nowrap;'>$label</td>
      <td style='font-weight:bold;border:none;padding:2px 0;'>${escapeHtml(value)}</td>
    </tr>
    """.trimIndent()
}
