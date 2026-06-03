package com.example.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object GlobalStyles {
    val inputModifier = Modifier
        .background(Colors.inputBackground, RoundedCornerShape(8.dp))
        .border(1.dp, Colors.inputBorder, RoundedCornerShape(8.dp))
        .padding(horizontal = 12.dp, vertical = 12.dp)

    val labelTextStyle = TextStyle(
        color = Colors.labelText,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium
    )

    val cardModifier = Modifier
        .clip(RoundedCornerShape(12.dp))
        .background(Colors.cardBackground)
        .border(1.dp, Colors.cardBorder, RoundedCornerShape(12.dp))
        .padding(16.dp)

    val screenBackgroundModifier = Modifier
        .background(Colors.background)

    val sectionTitleTextStyle = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = Colors.textPrimary
    )

    val buttonModifier = Modifier
        .clip(RoundedCornerShape(8.dp))
        .background(Colors.primary)
        .padding(vertical = 14.dp)

    val buttonTextStyle = TextStyle(
        color = Colors.textOnPrimary,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold
    )

    val emptyStateTextStyle = TextStyle(
        color = Colors.textTertiary,
        fontSize = 15.sp
    )
}
