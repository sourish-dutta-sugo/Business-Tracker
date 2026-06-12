package com.zerobook.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class WindowType { PHONE, TABLET, DESKTOP }

data class AppWindowInfo(
    val windowType: WindowType,
    val widthDp: Dp,
    val heightDp: Dp,
) {
    val isDesktopLike: Boolean = windowType == WindowType.DESKTOP
}

@Composable
fun WithWindowInfo(
    content: @Composable (AppWindowInfo) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val windowType = when {
            maxWidth >= 840.dp -> WindowType.DESKTOP
            maxWidth >= 600.dp -> WindowType.TABLET
            else -> WindowType.PHONE
        }
        content(
            AppWindowInfo(
                windowType = windowType,
                widthDp = maxWidth,
                heightDp = maxHeight,
            ),
        )
    }
}
