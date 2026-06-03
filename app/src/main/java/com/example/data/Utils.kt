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
            val dir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            if (!dir.exists()) dir.mkdirs()
            val file = java.io.File(dir, fileName)
            
            val writer = java.io.FileWriter(file)
            writer.write("=============================================\n")
            val title = if (profile != null && !profile.gstin.isNullOrBlank()) "TAX INVOICE" else "INVOICE"
            writer.write("                 $title             \n")
            writer.write("=============================================\n")
            writer.write("BUSINESS NAME: ${profile?.businessName?.ifBlank { "ZeroBook Ltd" } ?: "ZeroBook Ltd"}\n")
            writer.write("ADDRESS: ${profile?.address ?: "Market Road"}, ${profile?.city ?: "New Delhi"}\n")
            writer.write("GSTIN: ${if (profile?.gstin.isNullOrBlank()) "NA" else profile?.gstin}\n")
            writer.write("PAN: ${if (profile?.pan.isNullOrBlank()) "NA" else profile?.pan}\n")
            writer.write("PHONE: ${profile?.phone ?: "NA"} | EMAIL: ${profile?.email ?: "NA"}\n")
            writer.write("=============================================\n")
            writer.write("INVOICE NO: $voucherNo\n")
            writer.write("DATE TIME:  $dateFormatted\n")
            writer.write("PARTY NAME: $partyName\n")
            writer.write("PAYMENT:    $paymentMode\n")
            writer.write("=============================================\n")
            writer.write(String.format("%-20s %5s %10s %10s\n", "ITEM NAME", "QTY", "RATE", "TOTAL"))
            writer.write("---------------------------------------------\n")
            for (item in lineItems) {
                val displayName = if (item.productName.length > 20) item.productName.substring(0, 17) + "..." else item.productName
                writer.write(String.format("%-20s %5.1f %10.2f %10.2f\n", 
                    displayName, item.qty, item.rate, item.totalAmount))
            }
            writer.write("=============================================\n")
            writer.write(String.format("%-30s %12.2f\n", "TAXABLE AMOUNT:", taxable))
            if (cgst > 0) writer.write(String.format("%-30s %12.2f\n", "CGST:", cgst))
            if (sgst > 0) writer.write(String.format("%-30s %12.2f\n", "SGST:", sgst))
            if (igst > 0) writer.write(String.format("%-30s %12.2f\n", "IGST:", igst))
            if (roundOff != 0.0) writer.write(String.format("%-30s %12.2f\n", "ROUND OFF:", roundOff))
            writer.write("---------------------------------------------\n")
            writer.write(String.format("%-30s %12.2f\n", "NET AMOUNT PAYABLE:", net))
            writer.write("=============================================\n")
            writer.write("Thank you for doing business with us!\n")
            if (profile?.ownerName != null) {
                writer.write("\n\nFor ${profile.businessName}\n\n\n\n[Digitally Signed by ${profile.ownerName}]\n")
                writer.write("Authorized Signatory\n")
            }
            writer.close()
            return file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
