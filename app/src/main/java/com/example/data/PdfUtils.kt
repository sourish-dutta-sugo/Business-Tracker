package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.example.data.AdditionalCharge
import java.io.File
import java.io.FileOutputStream

object PdfUtils {
    fun generatePdfInvoice(
        context: Context,
        profile: BusinessProfile?,
        voucherNo: String,
        dateFormatted: String,
        partyName: String,
        paymentMode: String,
        lineItems: List<VoucherItem>,
        additionalCharges: List<AdditionalCharge> = emptyList(),
        taxable: Double,
        cgst: Double,
        sgst: Double,
        igst: Double,
        roundOff: Double,
        net: Double,
        terms: String = "1. Goods once sold will not be taken back.\n2. Interest @ 18% p.a. will be charged if not paid within 30 days."
    ): File? {
        try {
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size
            var page = pdfDocument.startPage(pageInfo)
            var canvas = page.canvas
            val paint = Paint()

            paint.color = Color.BLACK
            paint.textSize = 20f
            paint.isFakeBoldText = true
            var yPos = 50f
            
            val title = if (profile != null && !profile.gstin.isNullOrBlank()) "TAX INVOICE" else "INVOICE"
            canvas.drawText(title, (595f - paint.measureText(title)) / 2f, yPos, paint)

            yPos += 40f
            paint.textSize = 14f
            paint.isFakeBoldText = true
            canvas.drawText(profile?.businessName?.ifBlank { "ZeroBook Ltd" } ?: "ZeroBook Ltd", 50f, yPos, paint)
            yPos += 20f
            paint.textSize = 12f
            paint.isFakeBoldText = false
            canvas.drawText("Address: ${profile?.address ?: ""}", 50f, yPos, paint)
            yPos += 15f
            canvas.drawText("GSTIN: ${profile?.gstin ?: "NA"}", 50f, yPos, paint)
            yPos += 15f
            canvas.drawText("PAN: ${profile?.pan ?: "NA"}", 50f, yPos, paint)
            yPos += 30f

            paint.strokeWidth = 1f
            canvas.drawLine(50f, yPos, 545f, yPos, paint)
            yPos += 15f

            canvas.drawText("Invoice No: $voucherNo", 50f, yPos, paint)
            canvas.drawText("Date: $dateFormatted", 350f, yPos, paint)
            yPos += 15f
            canvas.drawText("Party: $partyName", 50f, yPos, paint)
            canvas.drawText("Payment: $paymentMode", 350f, yPos, paint)

            yPos += 20f
            canvas.drawLine(50f, yPos, 545f, yPos, paint)
            yPos += 15f

            val hasDisc = lineItems.any { it.discount > 0.0 }
            
            paint.isFakeBoldText = true
            canvas.drawText("Item Name", 50f, yPos, paint)
            canvas.drawText("Qty", 280f, yPos, paint)
            canvas.drawText("Rate", 330f, yPos, paint)
            if (hasDisc) canvas.drawText("Disc", 400f, yPos, paint)
            canvas.drawText("Total", 480f, yPos, paint)
            paint.isFakeBoldText = false
            yPos += 10f
            canvas.drawLine(50f, yPos, 545f, yPos, paint)
            yPos += 20f

            for (item in lineItems) {
                val displayName = if (item.productName.length > 30) item.productName.substring(0, 27) + "..." else item.productName
                canvas.drawText(displayName, 50f, yPos, paint)
                canvas.drawText("${item.qty}", 280f, yPos, paint)
                canvas.drawText(String.format("%.2f", item.rate), 330f, yPos, paint)
                
                if (hasDisc) {
                    val discStr = if (item.discount > 0.0) "${item.discount.toInt()}%" else "0%"
                    canvas.drawText(discStr, 400f, yPos, paint)
                }
                canvas.drawText(String.format("%.2f", item.totalAmount), 480f, yPos, paint)
                yPos += 20f

                if (yPos > 750) {
                    pdfDocument.finishPage(page)
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    yPos = 50f
                }
            }

            additionalCharges.forEach { charge ->
                canvas.drawText(charge.label.ifBlank { "Other" }, 50f, yPos, paint)
                canvas.drawText(String.format("%.2f", charge.amount), 480f, yPos, paint)
                yPos += 20f
            }

            canvas.drawLine(50f, yPos, 545f, yPos, paint)
            yPos += 20f
            
            canvas.drawText("Taxable Amount:", 350f, yPos, paint)
            canvas.drawText(String.format("%.2f", taxable), 480f, yPos, paint)
            yPos += 20f
            
            if (igst > 0) {
                canvas.drawText("IGST:", 350f, yPos, paint)
                canvas.drawText(String.format("%.2f", igst), 480f, yPos, paint)
                yPos += 20f
            } else {
                canvas.drawText("CGST:", 350f, yPos, paint)
                canvas.drawText(String.format("%.2f", cgst), 480f, yPos, paint)
                yPos += 20f
                canvas.drawText("SGST:", 350f, yPos, paint)
                canvas.drawText(String.format("%.2f", sgst), 480f, yPos, paint)
                yPos += 20f
            }
            
            if (roundOff != 0.0) {
                canvas.drawText("Round Off:", 350f, yPos, paint)
                canvas.drawText(String.format("%.2f", roundOff), 480f, yPos, paint)
                yPos += 20f
            }

            canvas.drawLine(350f, yPos-10, 545f, yPos-10, paint)
            canvas.drawText("Net Amount:", 350f, yPos, paint)
            paint.isFakeBoldText = true
            canvas.drawText(String.format("%.2f", net), 480f, yPos, paint)
            paint.isFakeBoldText = false
            yPos += 25f

            canvas.drawText("Amount in words: " + Utils.numberToWords(net), 50f, yPos, paint)
            yPos += 30f
            
            paint.textSize = 10f
            canvas.drawText("Terms & Conditions:", 50f, yPos, paint)
            yPos += 15f
            terms.split("\n").forEach { term ->
                canvas.drawText(term, 50f, yPos, paint)
                yPos += 15f
            }
            
            yPos += 30f

            val signaturePath = profile?.signaturePath
            if (signaturePath != null && File(signaturePath).exists()) {
                val sigBitmap = BitmapFactory.decodeFile(signaturePath)
                if (sigBitmap != null) {
                    val scaled = Bitmap.createScaledBitmap(sigBitmap, 120, 50, true)
                    canvas.drawBitmap(scaled, 380f, yPos, paint)
                    yPos += 60f
                }
            } else {
                yPos += 60f
            }

            paint.textSize = 12f
            canvas.drawLine(380f, yPos - 15f, 530f, yPos - 15f, paint)
            canvas.drawText("Authorized Signatory", 380f, yPos, paint)

            pdfDocument.finishPage(page)

            val dir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
            if (!dir.exists()) dir.mkdirs()
            val fileName = "Invoice_" + voucherNo.replace("/", "_") + ".pdf"
            val file = File(dir, fileName)
            val outStream = FileOutputStream(file)
            pdfDocument.writeTo(outStream)
            pdfDocument.close()
            outStream.close()

            return file
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
