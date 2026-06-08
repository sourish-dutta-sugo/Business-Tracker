package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.example.services.InvoiceGenerator
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class AppViewModel(application: Application) : AndroidViewModel(application) {
    data class InvoicePreviewData(
        val voucher: Voucher,
        val items: List<VoucherItem>,
        val charges: List<AdditionalCharge>,
        val party: Party?,
        val profile: BusinessProfile,
        val html: String
    )

    data class VoucherPrefillRequest(
        val voucherType: String,
        val partyId: String?,
        val invoiceId: String?,
        val amount: Double?
    )

    sealed class DbInitState {
        object Loading : DbInitState()
        object Success : DbInitState()
        data class Error(val message: String) : DbInitState()
    }

    val dbInitState = MutableStateFlow<DbInitState>(DbInitState.Loading)

    private val repository: AppRepository

    val profile: StateFlow<BusinessProfile?>
    val parties: StateFlow<List<Party>>
    val products: StateFlow<List<Product>>
    val vouchers: StateFlow<List<Voucher>>
    val voucherItems: StateFlow<List<VoucherItem>>
    val ledgerEntries: StateFlow<List<LedgerEntry>>
    val transactions: StateFlow<List<BankCashTransaction>>
    val receiptAllocations: StateFlow<List<ReceiptAllocation>>
    
    // New flows for ledgers & outstanding bills
    val ledgerAccounts: StateFlow<List<LedgerAccount>>
    val billsReceivable: StateFlow<List<BillReceivable>>

    // Current setup status
    val isSetupCompleted = MutableStateFlow(false)
    val financialYear = MutableStateFlow(YearStorageManager.getActiveFinancialYear(application))
    val isStorageConfigured = MutableStateFlow(YearStorageManager.isStorageConfigured(application))
    val googleSyncState = MutableStateFlow(GoogleSyncManager.buildInitialUiState(application))
    val voucherPrefillRequest = MutableStateFlow<VoucherPrefillRequest?>(null)
    private var syncUploadJob: Job? = null

    init {
        var tempRepo: AppRepository? = null
        try {
            val db = AppDatabase.getDatabase(application)
            // Force write/read query to ensure SQLite tables are successfully built/open
            db.openHelper.writableDatabase
            tempRepo = AppRepository(db)
            dbInitState.value = DbInitState.Success
        } catch (e: Exception) {
            e.printStackTrace()
            dbInitState.value = DbInitState.Error(e.localizedMessage ?: "Failed to initialize SQLite Database.")
            val db = AppDatabase.getDatabase(application)
            tempRepo = AppRepository(db)
        }

        repository = tempRepo

        profile = repository.profile.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

        parties = repository.parties.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        products = repository.products.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        vouchers = repository.vouchers.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        voucherItems = repository.voucherItems.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        ledgerEntries = repository.ledgerEntries.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        transactions = repository.allTransactions.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        receiptAllocations = repository.receiptAllocations.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        ledgerAccounts = repository.ledgerAccounts.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        billsReceivable = repository.billsReceivable.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Seed standard Tally-prime ledgers if empty
        viewModelScope.launch {
            repository.seedLedgersIfEmpty()
        }

        // Monitor if profile exists to lock/unlock Setup screen
        viewModelScope.launch {
            repository.profile.collect { prof ->
                isSetupCompleted.value = prof != null
                prof?.fyLabel?.takeIf { it.isNotBlank() }?.let { savedFy ->
                    financialYear.value = savedFy
                    YearStorageManager.setActiveFinancialYear(getApplication(), savedFy)
                }
            }
        }

        if (googleSyncState.value.isSignedIn) {
            startRealtimeSync(financialYear.value)
        }
    }

    fun insertAllocation(allocation: ReceiptAllocation) {
        viewModelScope.launch {
            repository.insertAllocation(allocation)
            scheduleSyncUpload()
        }
    }

    fun insertLedgerAccount(account: LedgerAccount) {
        viewModelScope.launch {
            repository.insertLedgerAccount(account)
            scheduleSyncUpload()
        }
    }

    fun deleteLedgerAccount(id: String) {
        viewModelScope.launch {
            repository.deleteLedgerAccount(id)
            scheduleSyncUpload()
        }
    }

    // Business Profile Operations
    fun saveProfile(profile: BusinessProfile, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.insertProfile(profile)
            isSetupCompleted.value = true
            financialYear.value = profile.fyLabel
            YearStorageManager.setActiveFinancialYear(getApplication(), profile.fyLabel)
            scheduleSyncUpload()
            onSuccess()
        }
    }

    fun updateProfile(profile: BusinessProfile, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.insertProfile(profile)
            financialYear.value = profile.fyLabel
            YearStorageManager.setActiveFinancialYear(getApplication(), profile.fyLabel)
            scheduleSyncUpload()
            onSuccess()
        }
    }

    // Parties Operations
    fun saveParty(party: Party, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.insertParty(party)
            scheduleSyncUpload()
            onSuccess()
        }
    }

    fun getPartyById(partyId: String): Party? {
        return parties.value.find { it.id == partyId }
    }

    fun deleteParty(partyId: String) {
        viewModelScope.launch {
            repository.deleteParty(partyId)
            scheduleSyncUpload()
        }
    }

    // Products Operations
    fun saveProduct(product: Product, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.insertProduct(product)
            scheduleSyncUpload()
            onSuccess()
        }
    }

    fun deleteProduct(productId: String) {
        viewModelScope.launch {
            repository.deleteProduct(productId)
            scheduleSyncUpload()
        }
    }

    // Dynamic voucher numbering generator
    suspend fun generateNextVoucherNo(type: String, timestamp: Long): String {
        return repository.generateNextVoucherNo(type, timestamp)
    }

    suspend fun getVoucherSaveExtras(voucherId: String): VoucherSaveExtras {
        return repository.getVoucherSaveExtras(voucherId)
    }

    // Voucher Operations
    fun saveVoucher(
        voucher: Voucher,
        items: List<VoucherItem>,
        partyName: String?,
        extras: VoucherSaveExtras = VoucherSaveExtras(),
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            repository.saveAndPostVoucher(voucher, items, partyName, extras)
            scheduleSyncUpload()
            onSuccess()
        }
    }

    fun deleteVoucher(voucherId: String) {
        viewModelScope.launch {
            repository.deleteVoucher(voucherId)
            scheduleSyncUpload()
        }
    }

    fun getVoucherById(voucherId: String): Voucher? {
        return vouchers.value.find { it.id == voucherId }
    }

    fun getItemsForVoucher(voucherId: String): Flow<List<VoucherItem>> {
        return repository.getItemsForVoucher(voucherId)
    }

    suspend fun getInvoicePreviewData(voucherId: String): InvoicePreviewData? {
        val voucher = repository.getVoucherById(voucherId) ?: return null
        val profile = repository.getProfileSync() ?: return null
        val items = repository.getItemsForVoucherSync(voucherId)
        val party = voucher.partyId?.let { repository.getPartyById(it) }
        val charges = InvoiceGenerator.parseAdditionalCharges(voucher.additionalChargesJson)
        InvoiceGenerator.primeVoucherRenderExtras(getApplication(), voucherId)
        val html = InvoiceGenerator.buildInvoiceHtml(voucher, items, profile, party, charges)
        return InvoicePreviewData(
            voucher = voucher,
            items = items,
            charges = charges,
            party = party,
            profile = profile,
            html = html
        )
    }

    // Manual Transactions (Bank / Cash)
    fun saveTransaction(tx: BankCashTransaction, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.saveBankCashTransaction(tx)
            scheduleSyncUpload()
            onSuccess()
        }
    }

    fun deleteTransaction(txId: String) {
        viewModelScope.launch {
            repository.deleteTransaction(txId)
            scheduleSyncUpload()
        }
    }

    fun setVoucherPrefillRequest(request: VoucherPrefillRequest?) {
        voucherPrefillRequest.value = request
    }

    // Populate Sample Data
    fun loadSampleData() {
        viewModelScope.launch {
            repository.insertSampleData()
            scheduleSyncUpload()
        }
    }

    // Backup & Restore Database File
    fun backupDatabase(context: Context): File? {
        return try {
            val dbFile = context.getDatabasePath("ZeroBook.db")
            if (dbFile.exists()) {
                val backupFile = File(context.cacheDir, "ZeroBook_Backup.db")
                FileInputStream(dbFile).use { input ->
                    FileOutputStream(backupFile).use { output ->
                        input.copyTo(output)
                    }
                }
                backupFile
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun restoreDatabase(context: Context, backupFile: File): Boolean {
        return try {
            val dbFile = context.getDatabasePath("ZeroBook.db")
            dbFile.delete()
            FileInputStream(backupFile).use { input ->
                FileOutputStream(dbFile).use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun saveZeroBookFolder(uri: Uri): Boolean {
        val saved = YearStorageManager.saveZeroBookRoot(getApplication(), uri)
        isStorageConfigured.value = YearStorageManager.isStorageConfigured(getApplication())
        if (saved) {
            viewModelScope.launch {
                YearStorageManager.exportActiveDatabaseToFinancialYearFolder(
                    getApplication(),
                    financialYear.value
                )
            }
        }
        return saved
    }

    fun switchFinancialYear(
        targetFinancialYear: String,
        onComplete: (FinancialYearSwitchResult) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val result = YearStorageManager.switchFinancialYear(
                    context = getApplication(),
                    targetFinancialYear = targetFinancialYear,
                    currentProfile = repository.getProfileSync()
                )
                financialYear.value = result.financialYear
                if (googleSyncState.value.isSignedIn) {
                    startRealtimeSync(result.financialYear)
                }
                onComplete(result)
            } catch (error: Exception) {
                onError(error.localizedMessage ?: "Unable to switch financial year.")
            }
        }
    }

    fun exportActiveFinancialYearCsv(onComplete: (String?) -> Unit) {
        viewModelScope.launch {
            val header = "VoucherNo,Date,Type,Party,PaymentMode,TaxableAmount,CGST,SGST,IGST,RoundOff,NetAmount"
            val sdf = java.text.SimpleDateFormat("dd-MM-yyyy HH:mm", java.util.Locale.US)
            val body = vouchers.value.joinToString("\n") { voucher ->
                listOf(
                    voucher.voucherNo,
                    sdf.format(java.util.Date(voucher.createdAt)),
                    voucher.type,
                    voucher.partyId ?: "Cash",
                    voucher.paymentMode,
                    voucher.taxableAmount,
                    voucher.cgst,
                    voucher.sgst,
                    voucher.igst,
                    voucher.roundOff,
                    voucher.netAmount
                ).joinToString(",")
            }
            val path = YearStorageManager.exportCsvToFinancialYearFolder(
                context = getApplication(),
                financialYear = financialYear.value,
                csvContent = if (body.isBlank()) header else "$header\n$body"
            )
            onComplete(path)
        }
    }

    fun restoreActiveFinancialYearFromFolder(
        onComplete: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val restored = YearStorageManager.restoreActiveDatabaseFromFinancialYearFolder(
                context = getApplication(),
                financialYear = financialYear.value
            )
            onComplete(restored)
        }
    }

    fun markGoogleSyncPromptHandled() {
        GoogleSyncManager.markPromptHandled(getApplication())
        googleSyncState.value = GoogleSyncManager.buildInitialUiState(getApplication())
    }

    fun reopenGoogleSyncPrompt() {
        GoogleSyncManager.resetPromptState(getApplication())
        googleSyncState.value = GoogleSyncManager.buildInitialUiState(getApplication())
    }

    fun buildGoogleSignInIntent() = GoogleSyncManager.buildSignInIntent(getApplication())

    fun completeGoogleSignIn(
        account: GoogleSignInAccount,
        onComplete: (Result<GoogleSyncUiState>) -> Unit
    ) {
        viewModelScope.launch {
            val result = GoogleSyncManager.completeGoogleSignIn(getApplication(), account)
            result.onSuccess { state ->
                googleSyncState.value = state
                if (state.syncEnabled) {
                    startRealtimeSync(financialYear.value)
                    scheduleSyncUpload(immediate = true)
                }
            }
            onComplete(result)
        }
    }

    fun logoutGoogleAccount(onComplete: (Result<GoogleSyncUiState>) -> Unit) {
        viewModelScope.launch {
            val result = GoogleSyncManager.signOut(getApplication())
            result.onSuccess { googleSyncState.value = it }
            onComplete(result)
        }
    }

    fun clearGoogleSyncStatus() {
        googleSyncState.value = googleSyncState.value.copy(statusMessage = null)
    }

    fun refreshGoogleSyncState() {
        googleSyncState.value = GoogleSyncManager.buildInitialUiState(getApplication())
    }

    private fun startRealtimeSync(financialYearLabel: String) {
        GoogleSyncManager.startRealtimeSync(getApplication(), financialYearLabel) { snapshot ->
            repository.importSnapshot(snapshot)
        }
    }

    private fun scheduleSyncUpload(immediate: Boolean = false) {
        syncUploadJob?.cancel()
        syncUploadJob = viewModelScope.launch {
            if (!immediate) {
                kotlinx.coroutines.delay(1200)
            }
            val snapshot = repository.exportSnapshot()
            GoogleSyncManager.pushSnapshot(
                context = getApplication(),
                financialYear = financialYear.value,
                snapshot = snapshot
            )
        }
    }

    // Smart lookups for PIN, IFSC, and GSTIN
    fun fetchPinCodeDetails(pincode: String, onResult: (city: String?, state: String?) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val url = java.net.URL("https://api.postalpincode.in/pincode/$pincode")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val districtRegex = """"District"\s*:\s*"([^"]+)"""".toRegex()
                val stateRegex = """"State"\s*:\s*"([^"]+)"""".toRegex()
                
                val district = districtRegex.find(responseText)?.groupValues?.get(1)
                val state = stateRegex.find(responseText)?.groupValues?.get(1)
                
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(district, state)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(null, null)
                }
            }
        }
    }

    fun fetchIfscDetails(ifsc: String, onResult: (bankName: String?, branchName: String?) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val url = java.net.URL("https://ifsc.razorpay.com/$ifsc")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val bankRegex = """"BANK"\s*:\s*"([^"]+)"""".toRegex()
                val branchRegex = """"BRANCH"\s*:\s*"([^"]+)"""".toRegex()
                val bank = bankRegex.find(responseText)?.groupValues?.get(1)
                val branch = branchRegex.find(responseText)?.groupValues?.get(1)
                
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(bank, branch)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(null, null)
                }
            }
        }
    }

    fun fetchGstinDetails(gstin: String, onResult: (tradeName: String?, legalName: String?) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val url = java.net.URL("https://api.copreco.com/gstin/$gstin")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val tradeNameRegex = """"tradeNam"\s*:\s*"([^"]+)"""".toRegex()
                val legalNameRegex = """"lgnm"\s*:\s*"([^"]+)"""".toRegex()
                val legalNameAltRegex = """"legalName"\s*:\s*"([^"]+)"""".toRegex()
                
                val trade = tradeNameRegex.find(responseText)?.groupValues?.get(1)
                val legal = legalNameRegex.find(responseText)?.groupValues?.get(1) ?: legalNameAltRegex.find(responseText)?.groupValues?.get(1)
                
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(trade, legal)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(null, null)
                }
            }
        }
    }
}
