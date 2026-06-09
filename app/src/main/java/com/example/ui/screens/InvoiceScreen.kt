package com.example.ui.screens

import android.content.Intent
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.example.services.ExportStorageManager
import com.example.services.InvoiceGenerator
import com.example.services.configureInvoiceWebView
import com.example.services.emailInvoicePdf
import com.example.services.exportInvoicePdf
import com.example.services.printInvoice
import com.example.services.shareInvoicePdfToWhatsApp
import com.example.ui.AppViewModel
import com.example.ui.theme.AppColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceScreen(
    viewModel: AppViewModel,
    voucherId: String,
    onNavigateBack: () -> Unit,
    onEditVoucher: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var renderBundle by remember { mutableStateOf<InvoiceGenerator.InvoiceRenderBundle?>(null) }
    var isWorking by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var lastGeneratedPdf by remember { mutableStateOf<java.io.File?>(null) }
    var lastExportResult by remember { mutableStateOf<ExportStorageManager.ExportResult?>(null) }

    fun refreshBundle() {
        scope.launch {
            isWorking = true
            renderBundle = viewModel.getInvoiceRenderBundle(voucherId)
            isWorking = false
        }
    }

    LaunchedEffect(voucherId) {
        refreshBundle()
    }

    fun openPdf(file: java.io.File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(intent)
        }.onFailure {
            Toast.makeText(context, "No PDF viewer found", Toast.LENGTH_SHORT).show()
        }
    }

    fun runPdfAction(action: suspend (InvoiceGenerator.InvoiceRenderBundle, java.io.File) -> Unit) {
        scope.launch {
            isWorking = true
            val freshBundle = viewModel.getInvoiceRenderBundle(voucherId)
            if (freshBundle == null) {
                isWorking = false
                Toast.makeText(context, "Failed to load latest invoice data", Toast.LENGTH_SHORT).show()
                return@launch
            }
            renderBundle = freshBundle
            runCatching {
                val pdfFile = InvoiceGenerator.renderBundleToPdf(context, freshBundle)
                lastGeneratedPdf = pdfFile
                action(freshBundle, pdfFile)
            }.onFailure {
                Toast.makeText(context, it.message ?: "Invoice action failed", Toast.LENGTH_LONG).show()
            }
            isWorking = false
        }
    }

    Scaffold(
        containerColor = AppColors.screenBg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Invoice Viewer", fontWeight = FontWeight.Bold, color = Color(0xFF0D0D0D))
                        Text(
                            renderBundle?.document?.invoiceNumber ?: "",
                            fontSize = 11.sp,
                            color = Color(0xFF6B7280)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFF444444))
                    }
                },
                actions = {
                    IconButton(onClick = { webViewRef?.zoomOut() }) {
                        Icon(Icons.Outlined.Remove, contentDescription = "Zoom Out", tint = Color(0xFF444444))
                    }
                    IconButton(onClick = { webViewRef?.zoomIn() }) {
                        Icon(Icons.Outlined.Add, contentDescription = "Zoom In", tint = Color(0xFF444444))
                    }
                    IconButton(onClick = { refreshBundle() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Regenerate", tint = Color(0xFF444444))
                    }
                    IconButton(onClick = { onEditVoucher(voucherId) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF444444))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { innerPadding ->
        val bundle = renderBundle
        if (bundle == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
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
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                configureInvoiceWebView(this)
                                webViewClient = WebViewClient()
                                webViewRef = this
                            }
                        },
                        update = { webView ->
                            webViewRef = webView
                            webView.loadDataWithBaseURL("file:///", bundle.html, "text/html", "UTF-8", null)
                        }
                    )
                }

                if (isWorking) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = AppColors.primary)
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
                            .border(1.dp, Color(0x14000000))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BottomActionItem(
                            icon = { Icon(Icons.Outlined.Send, contentDescription = "Email", modifier = Modifier.size(20.dp), tint = Color(0xFF555555)) },
                            label = "Email",
                            enabled = !isWorking,
                            onClick = {
                                runPdfAction { freshBundle, pdfFile ->
                                    emailInvoicePdf(
                                        context = context,
                                        pdfFile = pdfFile,
                                        recipient = freshBundle.document.buyer?.email,
                                        subject = "Invoice ${freshBundle.document.invoiceNumber}",
                                        body = "Please find attached invoice ${freshBundle.document.invoiceNumber}."
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        BottomActionItem(
                            icon = { Icon(Icons.Outlined.OpenInNew, contentDescription = "Open", modifier = Modifier.size(20.dp), tint = Color(0xFF555555)) },
                            label = "Open",
                            enabled = !isWorking && lastGeneratedPdf != null,
                            onClick = { lastGeneratedPdf?.let(::openPdf) },
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                runPdfAction { freshBundle, pdfFile ->
                                    lastExportResult = exportInvoicePdf(context, pdfFile)
                                    Toast.makeText(
                                        context,
                                        "Saved ${lastExportResult?.fileName} to ${lastExportResult?.locationLabel}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            modifier = Modifier
                                .height(46.dp)
                                .weight(1.4f),
                            enabled = !isWorking,
                            shape = RoundedCornerShape(22.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.primary),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Outlined.Download, contentDescription = "Download", tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Download", color = Color.White, fontSize = 12.sp, maxLines = 1)
                        }
                        BottomActionItem(
                            icon = { Icon(Icons.Outlined.Print, contentDescription = "Print", modifier = Modifier.size(20.dp), tint = Color(0xFF555555)) },
                            label = "Print",
                            enabled = !isWorking,
                            onClick = {
                                scope.launch {
                                    isWorking = true
                                    val freshBundle = viewModel.getInvoiceRenderBundle(voucherId)
                                    if (freshBundle != null) {
                                        renderBundle = freshBundle
                                        runCatching { printInvoice(context, freshBundle) }
                                            .onFailure { Toast.makeText(context, it.message ?: "Print failed", Toast.LENGTH_LONG).show() }
                                    }
                                    isWorking = false
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        BottomActionItem(
                            icon = { Icon(Icons.Outlined.Refresh, contentDescription = "Refresh", modifier = Modifier.size(20.dp), tint = Color(0xFF555555)) },
                            label = "Refresh",
                            enabled = !isWorking,
                            onClick = { refreshBundle() },
                            modifier = Modifier.weight(1f)
                        )
                        BottomActionItem(
                            icon = { Icon(Icons.Outlined.Send, contentDescription = "WhatsApp", modifier = Modifier.size(20.dp), tint = Color(0xFF555555)) },
                            label = "WhatsApp",
                            enabled = !isWorking,
                            fontSize = 10.sp,
                            onClick = {
                                runPdfAction { _, pdfFile ->
                                    shareInvoicePdfToWhatsApp(context, pdfFile)
                                }
                            },
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
    fontSize: TextUnit = 11.sp,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        icon()
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = fontSize,
            color = if (enabled) Color(0xFF555555) else Color(0xFFAAAAAA),
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
