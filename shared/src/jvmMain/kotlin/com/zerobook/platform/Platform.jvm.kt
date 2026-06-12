package com.zerobook.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import java.awt.Desktop
import java.io.File
import java.net.URI

actual fun getPlatformName(): String = "Desktop"

actual fun openUrl(url: String) {
    if (Desktop.isDesktopSupported()) {
        Desktop.getDesktop().browse(URI(url))
    }
}

actual class InvoiceWebView {
    @Composable
    actual fun Display(html: String, modifier: Modifier) {
        FallbackHtmlSource(html = html, modifier = modifier)
    }
}

actual class PlatformFileSaver {
    actual fun saveToDownloads(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
    ): String {
        val downloads = File(System.getProperty("user.home"), "Downloads")
        if (!downloads.exists()) {
            downloads.mkdirs()
        }
        val target = File(downloads, fileName)
        target.writeBytes(bytes)
        return target.absolutePath
    }
}
