package com.zerobook.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

actual fun getPlatformName(): String = "iOS"

actual fun openUrl(url: String) {
    UIApplication.sharedApplication.openURL(NSURL.URLWithString(url)!!)
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
    ): String = fileName
}

actual class PlatformFilePicker {
    actual fun pickFile(
        accept: String,
        onSelected: (ByteArray, String) -> Unit,
    ) = Unit
}
