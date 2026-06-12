package com.zerobook.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLAnchorElement
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

actual fun getPlatformName(): String = "Web"

actual fun openUrl(url: String) {
    window.open(url, "_blank")
}

actual class InvoiceWebView {
    @Composable
    actual fun Display(html: String, modifier: Modifier) {
        FallbackHtmlSource(html = html, modifier = modifier)
    }
}

actual class PlatformFileSaver {
    @OptIn(ExperimentalEncodingApi::class)
    actual fun saveToDownloads(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
    ): String {
        val href = "data:$mimeType;base64,${Base64.encode(bytes)}"
        val anchor = document.createElement("a") as HTMLAnchorElement
        anchor.href = href
        anchor.download = fileName
        document.body?.appendChild(anchor)
        anchor.click()
        document.body?.removeChild(anchor)
        return fileName
    }
}
