package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.GoogleSyncUiState
import com.example.ui.theme.AppColors

@Composable
fun StorageAccessScreen(
    currentFinancialYear: String,
    rootFolderName: String?,
    onChooseFolder: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.screenBg)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = AppColors.cardBg),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = AppColors.primary
                )
                Text(
                    text = "Choose ZeroBook Storage",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.textPrimary
                )
                Text(
                    text = "Select a folder location once. The app will create ZeroBook/$currentFinancialYear and keep each financial year in its own sub-folder.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                    color = AppColors.textSecondary
                )
                if (!rootFolderName.isNullOrBlank()) {
                    Text(
                        text = "Current folder: $rootFolderName",
                        fontSize = 12.sp,
                        color = AppColors.textSecondary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = onChooseFolder,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Choose Folder")
                }
            }
        }
    }
}

@Composable
fun GoogleSyncScreen(
    syncState: GoogleSyncUiState,
    onLogin: () -> Unit,
    onSkipConfirmed: () -> Unit
) {
    val showSkipDialog = remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.screenBg)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = AppColors.cardBg),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CloudSync,
                    contentDescription = null,
                    tint = AppColors.primary
                )
                Text(
                    text = "Google Sync",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.textPrimary
                )
                Text(
                    text = "Sign in with your Google account to keep progress synced across your logged-in devices in real time whenever internet is available.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                    color = AppColors.textSecondary
                )
                if (!syncState.statusMessage.isNullOrBlank()) {
                    Text(
                        text = syncState.statusMessage,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        color = if (syncState.syncEnabled) Color(0xFF2E7D32) else AppColors.textSecondary
                    )
                }
                if (!syncState.configAvailable) {
                    Text(
                        text = "This build can show the login flow, but live sync needs Firebase configuration before it can run.",
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Button(
                    onClick = onLogin,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Continue With Google")
                }
                OutlinedButton(
                    onClick = { showSkipDialog.value = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Skip For Now")
                }
            }
        }
    }

    if (showSkipDialog.value) {
        AlertDialog(
            onDismissRequest = { showSkipDialog.value = false },
            title = { Text("Skip Google Login") },
            text = { Text("This will help to sync the progress in other logged in devices") },
            confirmButton = {
                Button(
                    onClick = {
                        showSkipDialog.value = false
                        onSkipConfirmed()
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showSkipDialog.value = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
