package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Calendar

class AppViewModel(application: Application) : AndroidViewModel(application) {

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
    val ledgerEntries: StateFlow<List<LedgerEntry>>
    val transactions: StateFlow<List<BankCashTransaction>>

    // Current setup status
    val isSetupCompleted = MutableStateFlow(false)

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

        // Monitor if profile exists to lock/unlock Setup screen
        viewModelScope.launch {
            repository.profile.collect { prof ->
                isSetupCompleted.value = prof != null
            }
        }
    }

    // Business Profile Operations
    fun saveProfile(profile: BusinessProfile, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.insertProfile(profile)
            isSetupCompleted.value = true
            onSuccess()
        }
    }

    fun updateProfile(profile: BusinessProfile, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.insertProfile(profile)
            onSuccess()
        }
    }

    // Parties Operations
    fun saveParty(party: Party, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.insertParty(party)
            onSuccess()
        }
    }

    fun getPartyById(partyId: String): Party? {
        return parties.value.find { it.id == partyId }
    }

    fun deleteParty(partyId: String) {
        viewModelScope.launch {
            repository.deleteParty(partyId)
        }
    }

    // Products Operations
    fun saveProduct(product: Product, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.insertProduct(product)
            onSuccess()
        }
    }

    fun deleteProduct(productId: String) {
        viewModelScope.launch {
            repository.deleteProduct(productId)
        }
    }

    // Dynamic voucher numbering generator
    suspend fun generateNextVoucherNo(type: String, timestamp: Long): String {
        return repository.generateNextVoucherNo(type, timestamp)
    }

    // Voucher Operations
    fun saveVoucher(
        voucher: Voucher,
        items: List<VoucherItem>,
        partyName: String?,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            repository.saveAndPostVoucher(voucher, items, partyName)
            onSuccess()
        }
    }

    fun deleteVoucher(voucherId: String) {
        viewModelScope.launch {
            repository.deleteVoucher(voucherId)
        }
    }

    fun getVoucherById(voucherId: String): Voucher? {
        return vouchers.value.find { it.id == voucherId }
    }

    fun getItemsForVoucher(voucherId: String): Flow<List<VoucherItem>> {
        return repository.getItemsForVoucher(voucherId)
    }

    // Manual Transactions (Bank / Cash)
    fun saveTransaction(tx: BankCashTransaction, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.saveBankCashTransaction(tx)
            onSuccess()
        }
    }

    fun deleteTransaction(txId: String) {
        viewModelScope.launch {
            repository.deleteTransaction(txId)
        }
    }

    // Populate Sample Data
    fun loadSampleData() {
        viewModelScope.launch {
            repository.insertSampleData()
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

    fun fetchIfscDetails(ifsc: String, onResult: (bankName: String?) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val url = java.net.URL("https://ifsc.razorpay.com/$ifsc")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val bankRegex = """"BANK"\s*:\s*"([^"]+)"""".toRegex()
                val bank = bankRegex.find(responseText)?.groupValues?.get(1)
                
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(bank)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(null)
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
