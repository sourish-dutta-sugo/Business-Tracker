package com.example.services

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream

enum class ExportTarget(val relativeDir: String) {
    Invoices("Documents/ZeroBook/Invoices"),
    Statements("Documents/ZeroBook/Statements"),
    Exports("Documents/ZeroBook/Exports"),
    Backups("Documents/ZeroBook/Backups"),
    Reports("Documents/ZeroBook/Reports")
}

object ExportStorageManager {
    data class ExportResult(
        val fileName: String,
        val locationLabel: String,
        val uri: Uri,
        val mimeType: String
    )

    fun exportBytes(
        context: Context,
        bytes: ByteArray,
        displayName: String,
        mimeType: String,
        target: ExportTarget
    ): ExportResult {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, target.relativeDir)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = requireNotNull(
                resolver.insert(MediaStore.Files.getContentUri("external"), values)
            ) { "Unable to create export file." }
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            ExportResult(displayName, "${target.relativeDir}/$displayName", uri, mimeType)
        } else {
            val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val exportDir = File(root, target.relativeDir.removePrefix("Documents/"))
            if (!exportDir.exists()) exportDir.mkdirs()
            val exportedFile = File(exportDir, displayName)
            exportedFile.writeBytes(bytes)
            ExportResult(
                fileName = displayName,
                locationLabel = exportedFile.absolutePath,
                uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", exportedFile),
                mimeType = mimeType
            )
        }
    }

    fun exportFile(
        context: Context,
        sourceFile: File,
        displayName: String,
        mimeType: String,
        target: ExportTarget
    ): ExportResult = FileInputStream(sourceFile).use {
        exportBytes(context, it.readBytes(), displayName, mimeType, target)
    }

    fun openFile(context: Context, exportResult: ExportResult) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(exportResult.uri, exportResult.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Open File"))
    }

    fun shareFile(context: Context, exportResult: ExportResult) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = exportResult.mimeType
            putExtra(Intent.EXTRA_STREAM, exportResult.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share File"))
    }
}
