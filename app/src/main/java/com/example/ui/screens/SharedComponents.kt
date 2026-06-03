package com.example.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Colors

@Composable
fun RetailTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    trailingIcon: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    isError: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label, fontSize = 14.sp) },
        placeholder = placeholder?.let { { Text(text = it, fontSize = 15.sp) } },
        modifier = modifier.fillMaxWidth(),
        readOnly = readOnly,
        singleLine = singleLine,
        textStyle = TextStyle(color = Colors.inputText, fontSize = 15.sp),
        trailingIcon = trailingIcon,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = RoundedCornerShape(8.dp),
        isError = isError,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Colors.inputText,
            unfocusedTextColor = Colors.inputText,
            disabledTextColor = Colors.inputText,
            errorTextColor = Colors.inputText,
            focusedBorderColor = Colors.primary,
            unfocusedBorderColor = Colors.inputBorder,
            disabledBorderColor = Colors.inputBorder,
            errorBorderColor = Colors.danger,
            focusedContainerColor = Colors.inputBackground,
            unfocusedContainerColor = Colors.inputBackground,
            disabledContainerColor = Colors.inputBackground,
            errorContainerColor = Colors.inputBackground,
            focusedLabelColor = Colors.labelText,
            unfocusedLabelColor = Colors.labelText,
            focusedPlaceholderColor = Colors.inputPlaceholder,
            unfocusedPlaceholderColor = Colors.inputPlaceholder
        )
    )
}
