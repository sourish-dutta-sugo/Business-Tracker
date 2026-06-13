package com.zerobook.platform

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.zerobook.database.applicationContextOrNull

actual fun getPlatformName(): String = "Android"

actual fun openUrl(url: String) {
    applicationContextOrNull()?.startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

actual class InvoiceWebView {
    @Composable
    actual fun Display(html: String, modifier: Modifier) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                WebView(context).apply {
                    settings.allowFileAccess = true
                    webViewClient = WebViewClient()
                }
            },
            update = { webView ->
                webView.loadDataWithBaseURL("file:///", html, "text/html", "utf-8", null)
            },
        )
    }
}

actual class PlatformFileSaver {
    actual fun saveToDownloads(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
    ): String {
        val context = checkNotNull(applicationContextOrNull()) { "Android context is not available." }
        val resolver = context.contentResolver
        val values = android.content.ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Unable to create download entry.")
        resolver.openOutputStream(uri)?.use { it.write(bytes) }
            ?: error("Unable to open download output stream.")
        return uri.toString()
    }
}

actual class PlatformFilePicker {
    actual fun pickFile(
        accept: String,
        onSelected: (ByteArray, String) -> Unit,
    ) = Unit
}
