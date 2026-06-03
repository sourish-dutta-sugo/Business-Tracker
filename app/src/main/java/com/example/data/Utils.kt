package com.example.data

import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Utils {

    val INDIAN_STATES = listOf(
        "Jammu & Kashmir" to "01",
        "Himachal Pradesh" to "02",
        "Punjab" to "03",
        "Chandigarh" to "04",
        "Uttarakhand" to "05",
        "Haryana" to "06",
        "Delhi" to "07",
        "Rajasthan" to "08",
        "Uttar Pradesh" to "09",
        "Bihar" to "10",
        "Sikkim" to "11",
        "Arunachal Pradesh" to "12",
        "Nagaland" to "13",
        "Manipur" to "14",
        "Mizoram" to "15",
        "Tripura" to "16",
        "Meghalaya" to "17",
        "Assam" to "18",
        "West Bengal" to "19",
        "Jharkhand" to "20",
        "Odisha" to "21",
        "Chhattisgarh" to "22",
        "Madhya Pradesh" to "23",
        "Gujarat" to "24",
        "Daman & Diu" to "25",
        "Dadra & Nagar Haveli" to "26",
        "Maharashtra" to "27",
        "Andhra Pradesh" to "28",
        "Karnataka" to "29",
        "Goa" to "30",
        "Lakshadweep" to "31",
        "Kerala" to "32",
        "Tamil Nadu" to "33",
        "Puducherry" to "34",
        "Andaman & Nicobar Islands" to "35",
        "Telangana" to "36",
        "Andhra Pradesh (New)" to "37",
        "Ladakh" to "38"
    )

    fun formatIndianCurrency(amount: Double): String {
        val isNegative = amount < 0
        val absAmount = Math.abs(amount)
        val parts = DecimalFormat("0.00").format(absAmount).split(".")
        val wholePart = parts[0]
        val decimalPart = parts[1]

        val length = wholePart.length
        val formattedWhole = if (length <= 3) {
            wholePart
        } else {
            val lastThree = wholePart.substring(length - 3)
            val rest = wholePart.substring(0, length - 3)
            val revRest = rest.reversed()
            val chunked = mutableListOf<String>()
            var i = 0
            while (i < revRest.length) {
                val end = Math.min(i + 2, revRest.length)
                chunked.add(revRest.substring(i, end).reversed())
                i += 2
            }
            val restFormatted = chunked.reversed().joinToString(",")
            "$restFormatted,$lastThree"
        }

        return if (isNegative) "-₹$formattedWhole.$decimalPart" else "₹$formattedWhole.$decimalPart"
    }

    fun formatDate(timestamp: Long): String {
        return SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date(timestamp))
    }

    fun numberToWords(number: Double): String {
        val amount = Math.round(number).toInt()
        if (amount == 0) return "Zero Rupees Only"

        val units = arrayOf(
            "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
            "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"
        )
        val tens = arrayOf(
            "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"
        )

        fun convertLessThanOneThousand(n: Int): String {
            var temp = n
            var str = ""
            if (temp >= 100) {
                str += units[temp / 100] + " Hundred "
                temp %= 100
            }
            if (temp >= 20) {
                str += tens[temp / 10] + " "
                temp %= 10
            }
            if (temp > 0) {
                str += units[temp] + " "
            }
            return str.trim()
        }

        var tempAmt = amount
        var words = ""

        if (tempAmt >= 10000000) { // Crores (1,00,00,00)
            val cr = tempAmt / 10000000
            words += convertLessThanOneThousand(cr) + " Crore "
            tempAmt %= 10000000
        }
        if (tempAmt >= 100000) { // Lakhs (1,00,000)
            val lk = tempAmt / 100000
            words += convertLessThanOneThousand(lk) + " Lakh "
            tempAmt %= 100000
        }
        if (tempAmt >= 1000) { // Thousands (1000)
            val th = tempAmt / 1000
            words += convertLessThanOneThousand(th) + " Thousand "
            tempAmt %= 1000
        }
        if (tempAmt > 0) {
            words += convertLessThanOneThousand(tempAmt)
        }

        return "${words.trim()} Rupees Only"
    }

    fun saveInvoiceToDeviceDownloads(
        context: android.content.Context,
        profile: BusinessProfile?,
        voucherNo: String,
        dateFormatted: String,
        partyName: String,
        paymentMode: String,
        lineItems: List<VoucherItem>,
        taxable: Double,
        cgst: Double,
        sgst: Double,
        igst: Double,
        roundOff: Double,
        net: Double
    ): String? {
        try {
            val fileName = "ZeroBook_Invoice_${voucherNo.replace("/", "_")}.txt"
            val sb = StringBuilder()
            sb.append("=============================================\n")
            val title = if (profile != null && !profile.gstin.isNullOrBlank()) "TAX INVOICE" else "INVOICE"
            sb.append("                 $title             \n")
            sb.append("=============================================\n")
            sb.append("BUSINESS NAME: ${profile?.businessName?.ifBlank { "ZeroBook Ltd" } ?: "ZeroBook Ltd"}\n")
            sb.append("ADDRESS: ${profile?.address ?: "Market Road"}, ${profile?.city ?: "New Delhi"}\n")
            sb.append("GSTIN: ${if (profile?.gstin.isNullOrBlank()) "NA" else profile?.gstin}\n")
            sb.append("PAN: ${if (profile?.pan.isNullOrBlank()) "NA" else profile?.pan}\n")
            sb.append("PHONE: ${profile?.phone ?: "NA"} | EMAIL: ${profile?.email ?: "NA"}\n")
            sb.append("=============================================\n")
            sb.append("INVOICE NO: $voucherNo\n")
            sb.append("DATE TIME:  $dateFormatted\n")
            sb.append("PARTY NAME: $partyName\n")
            sb.append("PAYMENT:    $paymentMode\n")
            sb.append("=============================================\n")
            sb.append(String.format("%-18s %4s %8s %5s %9s\n", "ITEM NAME", "QTY", "RATE", "DISC", "TOTAL"))
            sb.append("---------------------------------------------\n")
            for (item in lineItems) {
                val displayName = if (item.productName.length > 18) item.productName.substring(0, 15) + "..." else item.productName
                val discStr = if (item.discount > 0.0) "${item.discount.toInt()}%" else "0%"
                sb.append(String.format("%-18s %4.1f %8.2f %5s %9.2f\n", 
                    displayName, item.qty, item.rate, discStr, item.totalAmount))
            }
            sb.append("=============================================\n")
            sb.append(String.format("%-30s %12.2f\n", "TAXABLE AMOUNT:", taxable))
            if (cgst > 0) sb.append(String.format("%-30s %12.2f\n", "CGST:", cgst))
            if (sgst > 0) sb.append(String.format("%-30s %12.2f\n", "SGST:", sgst))
            if (igst > 0) sb.append(String.format("%-30s %12.2f\n", "IGST:", igst))
            if (roundOff != 0.0) sb.append(String.format("%-30s %12.2f\n", "ROUND OFF:", roundOff))
            sb.append("---------------------------------------------\n")
            sb.append(String.format("%-30s %12.2f\n", "NET AMOUNT PAYABLE:", net))
            sb.append("=============================================\n")
            sb.append("Thank you for doing business with us!\n")
            if (profile?.ownerName != null) {
                sb.append("\n\nFor ${profile.businessName}\n\n\n\n[Digitally Signed by ${profile.ownerName}]\n")
                sb.append("Authorized Signatory\n")
            }
            val content = sb.toString()

            // 1) Solid fallback local write
            val dir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            if (!dir.exists()) dir.mkdirs()
            val backupFile = java.io.File(dir, fileName)
            val writer = java.io.FileWriter(backupFile)
            writer.write(content)
            writer.close()

            // 2) Write to the Public Downloads folder using MediaStore (Android 10+)
            var publicPath: String? = null
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val contentValues = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { os ->
                            os.write(content.toByteArray())
                        }
                        publicPath = "Downloads/$fileName"
                    }
                } else {
                    val publicDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                    if (!publicDir.exists()) publicDir.mkdirs()
                    val publicFile = java.io.File(publicDir, fileName)
                    val fw = java.io.FileWriter(publicFile)
                    fw.write(content)
                    fw.close()
                    publicPath = publicFile.absolutePath
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            return publicPath ?: backupFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
