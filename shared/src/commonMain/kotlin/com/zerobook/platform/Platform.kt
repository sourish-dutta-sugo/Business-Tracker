package com.zerobook.platform

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

expect fun getPlatformName(): String

expect fun openUrl(url: String)

expect class InvoiceWebView() {
    @Composable
    fun Display(html: String, modifier: Modifier = Modifier)
}

expect class PlatformFileSaver() {
    fun saveToDownloads(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
    ): String
}

expect class PlatformFilePicker() {
    fun pickFile(
        accept: String,
        onSelected: (ByteArray, String) -> Unit,
    )
}

@Composable
fun FallbackHtmlSource(
    html: String,
    modifier: Modifier = Modifier,
) {
    SelectionContainer(modifier = modifier) {
        Text(html)
    }
}
