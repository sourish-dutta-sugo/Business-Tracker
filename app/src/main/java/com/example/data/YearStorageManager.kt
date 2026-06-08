package com.example.data

import android.content.Context
import java.io.File

data class FinancialYearSwitchResult(
    val financialYear: String,
    val restoredExistingData: Boolean,
    val storagePath: String?
)

object YearStorageManager {
    private const val PREFS_NAME = "zerobook_storage"
    private const val KEY_ACTIVE_FINANCIAL_YEAR = "active_financial_year"
    private const val KEY_STORAGE_ACCESS_GRANTED = "storage_access_granted"

    private const val ZEROBOOK_FOLDER_NAME = "ZeroBook"
    private const val DATABASE_FILE_NAME = "ZeroBook.db"
    private const val CSV_FILE_PREFIX = "ZeroBook_Ledger_"

    fun isStorageConfigured(context: Context): Boolean =
        prefs(context).getBoolean(KEY_STORAGE_ACCESS_GRANTED, false) && ensureRootDirectory(context) != null

    fun grantStorageAccess(context: Context): Boolean {
        val root = ensureRootDirectory(context) ?: return false
        prefs(context).edit()
            .putBoolean(KEY_STORAGE_ACCESS_GRANTED, true)
            .apply()
        ensureFinancialYearDirectory(context, getActiveFinancialYear(context))
        return root.exists()
    }

    fun getActiveFinancialYear(context: Context): String =
        prefs(context).getString(KEY_ACTIVE_FINANCIAL_YEAR, "2025-26") ?: "2025-26"

    fun setActiveFinancialYear(context: Context, financialYear: String) {
        prefs(context).edit().putString(KEY_ACTIVE_FINANCIAL_YEAR, financialYear).apply()
    }

    fun getStoragePathLabel(context: Context): String {
        val root = getRootDirectory(context)
        return root?.absolutePath ?: "Storage path will be created automatically"
    }

    fun ensureFinancialYearDirectory(context: Context, financialYear: String): File? {
        val root = ensureRootDirectory(context) ?: return null
        val yearDir = File(root, financialYear)
        if (!yearDir.exists()) {
            yearDir.mkdirs()
        }
        return yearDir.takeIf { it.exists() }
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

        val targetFile = File(yearDir, DATABASE_FILE_NAME)
        dbFile.copyTo(targetFile, overwrite = true)
        return targetFile.absolutePath
    }

    fun exportCsvToFinancialYearFolder(
        context: Context,
        financialYear: String,
        csvContent: String
    ): String? {
        val yearDir = ensureFinancialYearDirectory(context, financialYear) ?: return null
        val fileName = "$CSV_FILE_PREFIX${financialYear.replace("/", "-")}.csv"
        val csvFile = File(yearDir, fileName)
        csvFile.writeText(csvContent)
        return csvFile.absolutePath
    }

    fun restoreActiveDatabaseFromFinancialYearFolder(
        context: Context,
        financialYear: String
    ): Boolean {
        val yearDir = ensureFinancialYearDirectory(context, financialYear) ?: return false
        val sourceFile = File(yearDir, DATABASE_FILE_NAME)
        if (!sourceFile.exists()) {
            return false
        }

        checkpointAndCloseDatabase(context)
        deleteInternalDatabaseFiles(context)

        val internalDb = context.getDatabasePath(DATABASE_FILE_NAME)
        internalDb.parentFile?.mkdirs()
        sourceFile.copyTo(internalDb, overwrite = true)
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
            storagePath = ensureFinancialYearDirectory(context, targetFinancialYear)?.absolutePath
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

    private fun getRootDirectory(context: Context): File? {
        val externalRoot = context.getExternalFilesDir(null)
        return externalRoot?.let { File(it, ZEROBOOK_FOLDER_NAME) }
    }

    private fun ensureRootDirectory(context: Context): File? {
        val root = getRootDirectory(context) ?: return null
        if (!root.exists()) {
            root.mkdirs()
        }
        return root.takeIf { it.exists() }
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
