package com.example.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import java.io.File

data class FinancialYearSwitchResult(
    val financialYear: String,
    val restoredExistingData: Boolean,
    val storagePath: String?
)

object YearStorageManager {
    private const val PREFS_NAME = "zerobook_storage"
    private const val KEY_ZEROBOOK_TREE_URI = "zerobook_tree_uri"
    private const val KEY_ACTIVE_FINANCIAL_YEAR = "active_financial_year"

    private const val ZEROBOOK_FOLDER_NAME = "ZeroBook"
    private const val DATABASE_FILE_NAME = "ZeroBook.db"
    private const val CSV_FILE_PREFIX = "ZeroBook_Ledger_"

    fun isStorageConfigured(context: Context): Boolean = getRootUri(context) != null

    fun getActiveFinancialYear(context: Context): String =
        prefs(context).getString(KEY_ACTIVE_FINANCIAL_YEAR, "2025-26") ?: "2025-26"

    fun setActiveFinancialYear(context: Context, financialYear: String) {
        prefs(context).edit().putString(KEY_ACTIVE_FINANCIAL_YEAR, financialYear).apply()
    }

    fun saveZeroBookRoot(context: Context, treeUri: Uri): Boolean {
        val resolver = context.contentResolver
        return runCatching {
            val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            resolver.takePersistableUriPermission(treeUri, takeFlags)
            val root = DocumentFile.fromTreeUri(context, treeUri) ?: return false
            val zeroBookDir = root.findFile(ZEROBOOK_FOLDER_NAME) ?: root.createDirectory(ZEROBOOK_FOLDER_NAME)
            val zeroBookUri = zeroBookDir?.uri ?: return false
            prefs(context).edit()
                .putString(KEY_ZEROBOOK_TREE_URI, zeroBookUri.toString())
                .apply()
            true
        }.getOrDefault(false)
    }

    fun getZeroBookRootName(context: Context): String? =
        getZeroBookRoot(context)?.name

    fun ensureFinancialYearDirectory(context: Context, financialYear: String): DocumentFile? {
        val root = getZeroBookRoot(context) ?: return null
        return root.findFile(financialYear) ?: root.createDirectory(financialYear)
    }

    fun exportActiveDatabaseToFinancialYearFolder(
        context: Context,
        financialYear: String
    ): String? {
        val yearDir = ensureFinancialYearDirectory(context, financialYear) ?: return null
        checkpointDatabase(context)
        val dbFile = context.getDatabasePath(DATABASE_FILE_NAME)
        if (!dbFile.exists()) {
            return null
        }

        val dbDocument = yearDir.findFile(DATABASE_FILE_NAME)
            ?: yearDir.createFile("application/octet-stream", DATABASE_FILE_NAME)
            ?: return null

        context.contentResolver.openOutputStream(dbDocument.uri, "wt")?.use { output ->
            dbFile.inputStream().use { input -> input.copyTo(output) }
        } ?: return null

        return "${yearDir.name}/$DATABASE_FILE_NAME"
    }

    fun exportCsvToFinancialYearFolder(
        context: Context,
        financialYear: String,
        csvContent: String
    ): String? {
        val yearDir = ensureFinancialYearDirectory(context, financialYear) ?: return null
        val fileName = "$CSV_FILE_PREFIX${financialYear.replace("/", "-")}.csv"
        val csvDocument = yearDir.findFile(fileName)
            ?: yearDir.createFile("text/csv", fileName)
            ?: return null

        context.contentResolver.openOutputStream(csvDocument.uri, "wt")?.use { output ->
            output.write(csvContent.toByteArray())
        } ?: return null

        return "${yearDir.name}/$fileName"
    }

    fun restoreActiveDatabaseFromFinancialYearFolder(
        context: Context,
        financialYear: String
    ): Boolean {
        val yearDir = ensureFinancialYearDirectory(context, financialYear) ?: return false
        val dbDocument = yearDir.findFile(DATABASE_FILE_NAME) ?: return false

        checkpointAndCloseDatabase(context)
        deleteInternalDatabaseFiles(context)

        context.contentResolver.openInputStream(dbDocument.uri)?.use { input ->
            val internalDb = context.getDatabasePath(DATABASE_FILE_NAME)
            internalDb.parentFile?.mkdirs()
            internalDb.outputStream().use { output -> input.copyTo(output) }
        } ?: return false

        reopenDatabase(context)
        return true
    }

    suspend fun switchFinancialYear(
        context: Context,
        targetFinancialYear: String,
        currentProfile: BusinessProfile?
    ): FinancialYearSwitchResult {
        val previousFinancialYear = getActiveFinancialYear(context)
        exportActiveDatabaseToFinancialYearFolder(context, previousFinancialYear)

        val restoredExistingData = restoreActiveDatabaseFromFinancialYearFolder(context, targetFinancialYear)
        if (!restoredExistingData) {
            createFreshFinancialYearDatabase(context, targetFinancialYear, currentProfile)
            exportActiveDatabaseToFinancialYearFolder(context, targetFinancialYear)
        }

        setActiveFinancialYear(context, targetFinancialYear)
        return FinancialYearSwitchResult(
            financialYear = targetFinancialYear,
            restoredExistingData = restoredExistingData,
            storagePath = "${ZEROBOOK_FOLDER_NAME}/$targetFinancialYear"
        )
    }

    suspend fun createFreshFinancialYearDatabase(
        context: Context,
        financialYear: String,
        currentProfile: BusinessProfile?
    ) {
        checkpointAndCloseDatabase(context)
        deleteInternalDatabaseFiles(context)

        val db = reopenDatabase(context)
        currentProfile?.let {
            db.businessProfileDao().insertProfile(it.copy(fyLabel = financialYear))
        }
        AppRepository(db).seedLedgersIfEmpty()
    }

    private fun checkpointAndCloseDatabase(context: Context) {
        runCatching {
            checkpointDatabase(context)
            AppDatabase.closeDatabase()
        }
    }

    private fun checkpointDatabase(context: Context) {
        runCatching {
            val db = AppDatabase.getDatabase(context)
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
        }
    }

    private fun reopenDatabase(context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    private fun getZeroBookRoot(context: Context): DocumentFile? {
        val rootUri = getRootUri(context) ?: return null
        return DocumentFile.fromTreeUri(context, rootUri)
    }

    private fun getRootUri(context: Context): Uri? {
        val raw = prefs(context).getString(KEY_ZEROBOOK_TREE_URI, null) ?: return null
        return raw.toUri()
    }

    private fun deleteInternalDatabaseFiles(context: Context) {
        listOf(
            context.getDatabasePath(DATABASE_FILE_NAME),
            File(context.getDatabasePath(DATABASE_FILE_NAME).absolutePath + "-wal"),
            File(context.getDatabasePath(DATABASE_FILE_NAME).absolutePath + "-shm")
        ).forEach { file ->
            if (file.exists()) {
                file.delete()
            }
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
