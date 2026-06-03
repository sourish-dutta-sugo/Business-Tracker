package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceScreen(
    viewModel: AppViewModel,
    voucherId: String,
    onNavigateBack: () -> Unit,
    onEditVoucher: (String) -> Unit
) {
    val context = LocalContext.current
    val profile by viewModel.profile.collectAsState()
    val parties by viewModel.parties.collectAsState()

    var voucher by remember { mutableStateOf<Voucher?>(null) }
    var voucherItems by remember { mutableStateOf<List<VoucherItem>>(emptyList()) }

    val createPdfLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null && voucher != null) {
            try {
                val v = voucher!!
                val pref = profile
                val party = parties.find { it.name == v.partyId }
                val javaSdf = java.text.SimpleDateFormat("dd-MM-yyyy HH:mm", java.util.Locale.US)
                val dateStr = javaSdf.format(java.util.Date(v.createdAt))
                val pdfFile = com.example.data.PdfUtils.generatePdfInvoice(
                    context = context,
                    profile = pref,
                    voucherNo = v.voucherNo,
                    dateFormatted = dateStr,
                    partyName = party?.name ?: "Cash / Walk-in Customer",
                    paymentMode = v.paymentMode,
                    lineItems = voucherItems,
                    taxable = v.taxableAmount,
                    cgst = v.cgst,
                    sgst = v.sgst,
                    igst = v.igst,
                    roundOff = v.roundOff,
                    net = v.netAmount
                )
                if (pdfFile != null) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        pdfFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                    android.widget.Toast.makeText(context, "PDF saved successfully", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                android.widget.Toast.makeText(context, "Failed to save PDF", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(voucherId) {
        voucher = viewModel.getVoucherById(voucherId)
        viewModel.getItemsForVoucher(voucherId).collect { items ->
            voucherItems = items
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (profile?.gstin.isNullOrBlank()) "Invoice" else "Tax Invoice", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onEditVoucher(voucherId) }) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Voucher")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { innerPadding ->
        if (voucher == null || profile == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val v = voucher!!
            val prof = profile!!
            val party = parties.find { it.id == v.partyId }
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .padding(innerPadding)
            ) {
                // Printable View Area
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Buyer and Seller details block
                    Row(
                        modifier = Modifier.fillMaxWidth().border(0.5.dp, Color.Gray, RoundedCornerShape(4.dp)).padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Seller Details Box (Left)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("SELLER:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = prof.businessName, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(text = "${prof.address}, ${prof.city}, ${prof.state} - ${prof.pin}", fontSize = 11.sp)
                            val profGst = if (prof.gstin.isBlank()) "NA" else prof.gstin
                            Text(text = "GSTIN: $profGst", fontSize = 11.sp)
                            Text(text = "Phone: ${prof.phone}", fontSize = 11.sp)
                        }

                        // Bill To Box (Right)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("BUYER:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                            Spacer(modifier = Modifier.height(4.dp))
                            if (party != null) {
                                Text(text = party.name, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(text = party.address, fontSize = 11.sp)
                                Text(text = "${party.city}, ${party.state}", fontSize = 11.sp)
                                val partyGst = if (party.gstin.isNullOrBlank()) "NA" else party.gstin
                                Text(text = "GSTIN: $partyGst", fontSize = 11.sp)
                                Text(text = "Phone: ${party.phone}", fontSize = 11.sp)
                            } else {
                                Text(text = "Cash / Walk-in Customer", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Invoice Metadata
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Invoice No: ${v.voucherNo}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Date: ${Utils.formatDate(v.date)}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Mode: ${v.paymentMode}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // Item Table Block
                    Text("LINE ITEMS", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.5.dp, Color.LightGray, RoundedCornerShape(4.dp))
                    ) {
                        Column {
                            // Table Header Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF9F9F9))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("#", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp))
                                Text("Description", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.4f))
                                Text("HSN", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.7f))
                                Text("Qty", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.5f))
                                Text("Rate", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.8f))
                                Text("Disc", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.6f))
                                Text("Taxable", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.9f))
                                Text("Total", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.0f))
                            }

                            // Items List inside the table
                            if (voucherItems.isEmpty()) {
                                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                    Text("No items on invoice", fontSize = 13.sp, color = Color.Gray)
                                }
                            } else {
                                voucherItems.forEachIndexed { idx, item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("${idx + 1}", fontSize = 11.sp, modifier = Modifier.width(20.dp))
                                        Text(item.productName, fontSize = 11.sp, modifier = Modifier.weight(1.4f))
                                        Text(item.hsnCode, fontSize = 11.sp, modifier = Modifier.weight(0.7f))
                                        Text("${item.qty} ${item.unit}", fontSize = 11.sp, modifier = Modifier.weight(0.5f))
                                        Text(String.format("%.2f", item.rate), fontSize = 11.sp, modifier = Modifier.weight(0.8f))
                                        val discText = if (item.discount > 0.0) {
                                            if (item.discountType == "PERCENT") "${item.discount.toInt()}%" else "₹${item.discount.toInt()}"
                                        } else {
                                            "0%"
                                        }
                                        Text(discText, fontSize = 11.sp, modifier = Modifier.weight(0.6f))
                                        Text(Utils.formatIndianCurrency(item.taxableAmount), fontSize = 11.sp, modifier = Modifier.weight(0.9f))
                                        Text(Utils.formatIndianCurrency(item.totalAmount), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.0f))
                                    }
                                }
                            }
                        }
                    }

                    // Rate-wise GST Breakup Summary Table
                    Text("GST RATE-WISE ANALYSIS", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.5.dp, Color.LightGray, RoundedCornerShape(4.dp))
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF9F9F9))
                                    .padding(6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("HSN", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text("Taxable", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.1f))
                                Text("CGST Am", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.1f))
                                Text("SGST Am", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.1f))
                                Text("IGST Am", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.1f))
                            }
                            // Form aggregates
                            val hsnGstGroup = voucherItems.groupBy { it.hsnCode }
                            hsnGstGroup.forEach { (hsn, itemsInGroup) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(hsn, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                    Text(Utils.formatIndianCurrency(itemsInGroup.sumOf { it.taxableAmount }), fontSize = 11.sp, modifier = Modifier.weight(1.1f))
                                    Text(Utils.formatIndianCurrency(itemsInGroup.sumOf { it.cgstAmount }), fontSize = 11.sp, modifier = Modifier.weight(1.1f))
                                    Text(Utils.formatIndianCurrency(itemsInGroup.sumOf { it.sgstAmount }), fontSize = 11.sp, modifier = Modifier.weight(1.1f))
                                    Text(Utils.formatIndianCurrency(itemsInGroup.sumOf { it.igstAmount }), fontSize = 11.sp, modifier = Modifier.weight(1.1f))
                                }
                            }
                        }
                    }

                    // Multi Column Details at bottom: Totals & Bank
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Bank Details Block
                        Box(
                            modifier = Modifier
                                .weight(0.9f)
                                .border(0.5.dp, Color.LightGray, RoundedCornerShape(4.dp))
                                .padding(8.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("BANK DETAILS:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                Text(prof.bankName, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("A/C: ${prof.accountNo}", fontSize = 11.sp)
                                Text("IFSC: ${prof.ifsc}", fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("TERMS & CONDITIONS:", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                Text("E & OE. Goods once sold will not be taken back.", fontSize = 9.sp, color = Color.Gray)
                            }
                        }

                        // Totals Block
                        Box(
                            modifier = Modifier
                                .weight(1.1f)
                                .border(0.5.dp, Color.LightGray, RoundedCornerShape(4.dp))
                                .padding(8.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                InvoiceSummaryRow("Sub-taxable:", Utils.formatIndianCurrency(v.taxableAmount))
                                if (v.isIgst) {
                                    InvoiceSummaryRow("Total IGST:", Utils.formatIndianCurrency(v.igst))
                                } else {
                                    InvoiceSummaryRow("Total CGST:", Utils.formatIndianCurrency(v.cgst))
                                    InvoiceSummaryRow("Total SGST:", Utils.formatIndianCurrency(v.sgst))
                                }
                                InvoiceSummaryRow("Round Off:", Utils.formatIndianCurrency(v.roundOff))
                                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Net Total:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = Utils.formatIndianCurrency(v.netAmount),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    // Word Translation block
                    Text(
                        text = "Amount in Words: ${Utils.numberToWords(v.netAmount)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.DarkGray
                    )

                    // Signature block
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.5.dp, Color.LightGray, RoundedCornerShape(4.dp))
                            .padding(12.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.fillMaxWidth()) {
                            Text(text = "For ${prof.businessName}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            
                            val signaturePath = prof.signaturePath
                            if (signaturePath != null && java.io.File(signaturePath).exists()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                val sigBitmap = android.graphics.BitmapFactory.decodeFile(signaturePath)
                                if (sigBitmap != null) {
                                    Image(
                                        bitmap = sigBitmap.asImageBitmap(),
                                        contentDescription = "Authorized Signature Image",
                                        modifier = Modifier
                                            .height(55.dp)
                                            .width(130.dp)
                                            .background(Color.White)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                            } else {
                                Spacer(modifier = Modifier.height(26.dp))
                            }
                            
                            Text(text = "Authorized Signatory", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Action buttons at the bottom of screen
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFAF9F9))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (v != null) {
                                createPdfLauncher.launch("Invoice_${v.voucherNo.replace("/", "_")}.pdf")
                            }
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp).padding(end = 4.dp))
                        Text("Save PDF", fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            try {
                                val javaSdf = java.text.SimpleDateFormat("dd-MM-yyyy HH:mm", java.util.Locale.US)
                                val dateStr = javaSdf.format(java.util.Date(v.createdAt))
                                val pdfFile = PdfUtils.generatePdfInvoice(
                                    context = context,
                                    profile = prof,
                                    voucherNo = v.voucherNo,
                                    dateFormatted = dateStr,
                                    partyName = party?.name ?: "Cash / Walk-in Customer",
                                    paymentMode = v.paymentMode,
                                    lineItems = voucherItems,
                                    taxable = v.taxableAmount,
                                    cgst = v.cgst,
                                    sgst = v.sgst,
                                    igst = v.igst,
                                    roundOff = v.roundOff,
                                    net = v.netAmount
                                )
                                if (pdfFile != null) {
                                    val printManager = context.getSystemService(android.content.Context.PRINT_SERVICE) as android.print.PrintManager
                                    val printAdapter = object : android.print.PrintDocumentAdapter() {
                                        override fun onWrite(pages: Array<out android.print.PageRange>?, destination: android.os.ParcelFileDescriptor?, cancellationSignal: android.os.CancellationSignal?, callback: WriteResultCallback?) {
                                            java.io.FileInputStream(pdfFile).use { input ->
                                                java.io.FileOutputStream(destination?.fileDescriptor).use { output ->
                                                    input.copyTo(output)
                                                }
                                            }
                                            callback?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                                        }
                                        override fun onLayout(oldAttributes: android.print.PrintAttributes?, newAttributes: android.print.PrintAttributes?, cancellationSignal: android.os.CancellationSignal?, callback: LayoutResultCallback?, extras: android.os.Bundle?) {
                                            if (cancellationSignal?.isCanceled == true) {
                                                callback?.onLayoutCancelled()
                                                return
                                            }
                                            val info = android.print.PrintDocumentInfo.Builder("invoice.pdf")
                                                .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                                                .build()
                                            callback?.onLayoutFinished(info, true)
                                        }
                                    }
                                    printManager.print("Invoice_${v.voucherNo}", printAdapter, android.print.PrintAttributes.Builder().build())
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C757D)),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp).padding(end = 4.dp))
                        Text("Print", fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            try {
                                val javaSdf = java.text.SimpleDateFormat("dd-MM-yyyy HH:mm", java.util.Locale.US)
                                val dateStr = javaSdf.format(java.util.Date(v.createdAt))
                                val pdfFile = PdfUtils.generatePdfInvoice(
                                    context = context,
                                    profile = prof,
                                    voucherNo = v.voucherNo,
                                    dateFormatted = dateStr,
                                    partyName = party?.name ?: "Cash / Walk-in Customer",
                                    paymentMode = v.paymentMode,
                                    lineItems = voucherItems,
                                    taxable = v.taxableAmount,
                                    cgst = v.cgst,
                                    sgst = v.sgst,
                                    igst = v.igst,
                                    roundOff = v.roundOff,
                                    net = v.netAmount
                                )
                                if (pdfFile != null) {
                                    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", pdfFile)
                                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "application/pdf"
                                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(android.content.Intent.createChooser(intent, "Share Invoice PDF"))
                                } else {
                                    Toast.makeText(context, "Failed to generate PDF", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF28A745)),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp).padding(end = 4.dp))
                        Text("Share", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun InvoiceSummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 11.sp, color = Color.Gray)
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
    }
}
