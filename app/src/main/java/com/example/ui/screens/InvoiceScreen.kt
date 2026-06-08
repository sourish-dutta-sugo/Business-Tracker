package com.example.ui.screens

import android.content.Intent
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.example.data.Voucher
import com.example.services.InvoiceGenerator
import com.example.services.configureInvoiceWebView
import com.example.services.printInvoice
import com.example.services.shareInvoicePdf
import com.example.services.shareInvoicePdfToWhatsApp
import com.example.ui.AppViewModel
import com.example.ui.theme.AppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceScreen(
    viewModel: AppViewModel,
    voucherId: String,
    onNavigateBack: () -> Unit,
    onEditVoucher: (String) -> Unit
) {
    val context = LocalContext.current

    var voucher by remember { mutableStateOf<Voucher?>(null) }
    var htmlContent by remember { mutableStateOf("") }
    var isGeneratingPdf by remember { mutableStateOf(false) }
    var lastGeneratedPdf by remember { mutableStateOf<java.io.File?>(null) }

    LaunchedEffect(voucherId) {
        viewModel.getInvoicePreviewData(voucherId)?.let { previewData ->
            voucher = previewData.voucher
            htmlContent = InvoiceGenerator.buildInvoiceHtml(
                voucher = previewData.voucher,
                items = previewData.items,
                business = previewData.profile,
                party = previewData.party,
                additionalCharges = previewData.charges
            )
        }
    }

    fun openPdf(file: java.io.File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "No PDF viewer found", Toast.LENGTH_SHORT).show()
        }
    }

    fun runPdfAction(onFile: (java.io.File) -> Unit) {
        if (voucher == null) return
        isGeneratingPdf = true
        InvoiceGenerator.generatePdfFromVoucherId(context, voucherId) { file, freshVoucher ->
            isGeneratingPdf = false
            if (file != null && freshVoucher != null) {
                voucher = freshVoucher
                lastGeneratedPdf = file
                Toast.makeText(context, "Saved: ${file.absolutePath}", Toast.LENGTH_LONG).show()
                onFile(file)
            } else {
                Toast.makeText(context, "Failed to generate PDF", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        containerColor = AppColors.screenBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Invoice Preview",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0D0D0D)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFF444444))
                    }
                },
                actions = {
                    IconButton(onClick = { onEditVoucher(voucherId) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF444444))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFFFFFFF))
            )
        }
    ) { innerPadding ->
        if (voucher == null || htmlContent.isBlank()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AppColors.primary)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppColors.screenBg)
                    .padding(innerPadding)
            ) {
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                configureInvoiceWebView(this)
                                webViewClient = WebViewClient()
                            }
                        },
                        update = { webView ->
                            webView.loadDataWithBaseURL(
                                "file:///",
                                htmlContent,
                                "text/html",
                                "UTF-8",
                                null
                            )
                        }
                    )
                }

                if (isGeneratingPdf) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = AppColors.primary
                    )
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    color = MaterialTheme.colorScheme.background,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(width = 1.dp, color = Color(0x1A000000), shape = RoundedCornerShape(0.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BottomActionItem(
                            icon = { Icon(Icons.Outlined.Share, contentDescription = "Share", modifier = Modifier.size(22.dp), tint = Color(0xFF555555)) },
                            label = "Share",
                            enabled = !isGeneratingPdf,
                            onClick = { runPdfAction { shareInvoicePdf(context, it) } },
                            modifier = Modifier.weight(1f)
                        )
                        BottomActionItem(
                            icon = { Icon(Icons.Outlined.OpenInNew, contentDescription = "Open", modifier = Modifier.size(22.dp), tint = Color(0xFF555555)) },
                            label = "Open",
                            enabled = !isGeneratingPdf && lastGeneratedPdf != null,
                            onClick = { lastGeneratedPdf?.let(::openPdf) },
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { runPdfAction { } },
                            modifier = Modifier
                                .height(48.dp)
                                .weight(1.4f),
                            enabled = !isGeneratingPdf,
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A73E8)),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Download,
                                contentDescription = "Download PDF",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Download PDF", fontSize = 12.sp, color = Color.White, maxLines = 1)
                        }
                        BottomActionItem(
                            icon = { Icon(Icons.Outlined.Print, contentDescription = "Print", modifier = Modifier.size(22.dp), tint = Color(0xFF555555)) },
                            label = "Print",
                            enabled = !isGeneratingPdf,
                            onClick = { printInvoice(context, htmlContent, voucher!!.voucherNo) },
                            modifier = Modifier.weight(1f)
                        )
                        BottomActionItem(
                            icon = { Icon(Icons.Outlined.Send, contentDescription = "WhatsApp", modifier = Modifier.size(22.dp), tint = Color(0xFF555555)) },
                            label = "WhatsApp",
                            enabled = !isGeneratingPdf,
                            fontSize = 10.sp,
                            onClick = { runPdfAction { shareInvoicePdfToWhatsApp(context, it) } },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomActionItem(
    icon: @Composable () -> Unit,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 11.sp,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .widthIn(min = 60.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        icon()
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = fontSize,
            color = if (enabled) Color(0xFF555555) else Color(0xFFAAAAAA),
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
