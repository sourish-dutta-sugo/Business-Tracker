package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BusinessProfileDao {
    @Query("SELECT * FROM business_profile WHERE id = 1 LIMIT 1")
    fun getProfile(): Flow<BusinessProfile?>

    @Query("SELECT * FROM business_profile WHERE id = 1 LIMIT 1")
    suspend fun getProfileSync(): BusinessProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: BusinessProfile)

    @Query("DELETE FROM business_profile")
    suspend fun deleteProfile()
}

@Dao
interface PartyDao {
    @Query("SELECT * FROM parties ORDER BY name ASC")
    fun getAllParties(): Flow<List<Party>>

    @Query("SELECT * FROM parties ORDER BY name ASC")
    suspend fun getAllPartiesSync(): List<Party>

    @Query("SELECT * FROM parties WHERE id = :id LIMIT 1")
    suspend fun getPartyById(id: String): Party?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParty(party: Party)

    @Query("DELETE FROM parties WHERE id = :id")
    suspend fun deleteParty(id: String)

    @Query("DELETE FROM parties")
    suspend fun deleteAllParties()
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllProducts(): Flow<List<Product>>

    @Query("SELECT * FROM products ORDER BY name ASC")
    suspend fun getAllProductsSync(): List<Product>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProductById(id: String): Product?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: Product)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteProduct(id: String)

    @Query("DELETE FROM products")
    suspend fun deleteAllProducts()
}

@Dao
interface VoucherDao {
    @Query("SELECT * FROM vouchers ORDER BY date DESC, createdAt DESC")
    fun getAllVouchers(): Flow<List<Voucher>>

    @Query("SELECT * FROM vouchers ORDER BY date DESC, createdAt DESC")
    suspend fun getAllVouchersSync(): List<Voucher>

    @Query("SELECT * FROM vouchers WHERE id = :id LIMIT 1")
    suspend fun getVoucherById(id: String): Voucher?

    @Query("SELECT * FROM vouchers WHERE type = :type ORDER BY date DESC, createdAt DESC")
    fun getVouchersByType(type: String): Flow<List<Voucher>>

    @Query("SELECT voucherNo FROM vouchers WHERE type = :type AND voucherNo LIKE :pattern ORDER BY voucherNo DESC LIMIT 1")
    suspend fun getLatestVoucherNo(type: String, pattern: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVoucher(voucher: Voucher)

    @Query("DELETE FROM vouchers WHERE id = :id")
    suspend fun deleteVoucher(id: String)

    @Query("DELETE FROM vouchers")
    suspend fun deleteAllVouchers()
}

@Dao
interface VoucherItemDao {
    @Query("SELECT * FROM voucher_items")
    fun getAllItems(): Flow<List<VoucherItem>>

    @Query("SELECT * FROM voucher_items")
    suspend fun getAllItemsSync(): List<VoucherItem>

    @Query("SELECT * FROM voucher_items WHERE voucherId = :voucherId")
    fun getItemsForVoucher(voucherId: String): Flow<List<VoucherItem>>

    @Query("SELECT * FROM voucher_items WHERE voucherId = :voucherId")
    suspend fun getItemsForVoucherSync(voucherId: String): List<VoucherItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<VoucherItem>)

    @Query("DELETE FROM voucher_items WHERE voucherId = :voucherId")
    suspend fun deleteItemsForVoucher(voucherId: String)

    @Query("DELETE FROM voucher_items")
    suspend fun deleteAllItems()
}

@Dao
interface LedgerDao {
    @Query("SELECT * FROM ledger_entries ORDER BY date DESC, createdAt DESC")
    fun getAllLedgerEntries(): Flow<List<LedgerEntry>>

    @Query("SELECT * FROM ledger_entries ORDER BY date DESC, createdAt DESC")
    suspend fun getAllLedgerEntriesSync(): List<LedgerEntry>

    @Query("SELECT * FROM ledger_entries WHERE accountHead = :accountHead ORDER BY date DESC, createdAt DESC")
    fun getLedgerEntriesByAccount(accountHead: String): Flow<List<LedgerEntry>>

    @Query("SELECT * FROM ledger_entries WHERE voucherId = :voucherId")
    suspend fun getLedgerEntriesByVoucherId(voucherId: String): List<LedgerEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLedgerEntries(entries: List<LedgerEntry>)

    @Query("DELETE FROM ledger_entries WHERE voucherId = :voucherId")
    suspend fun deleteLedgerEntriesForVoucher(voucherId: String)

    @Query("DELETE FROM ledger_entries")
    suspend fun deleteAllLedgerEntries()
}

@Dao
interface BankCashDao {
    @Query("SELECT * FROM bank_cash_transactions ORDER BY date DESC, createdAt DESC")
    fun getAllTransactions(): Flow<List<BankCashTransaction>>

    @Query("SELECT * FROM bank_cash_transactions ORDER BY date DESC, createdAt DESC")
    suspend fun getAllTransactionsSync(): List<BankCashTransaction>

    @Query("SELECT * FROM bank_cash_transactions WHERE mode = 'CASH' ORDER BY date DESC, createdAt DESC")
    fun getCashTransactions(): Flow<List<BankCashTransaction>>

    @Query("SELECT * FROM bank_cash_transactions WHERE mode != 'CASH' ORDER BY date DESC, createdAt DESC")
    fun getBankTransactions(): Flow<List<BankCashTransaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: BankCashTransaction)

    @Query("DELETE FROM bank_cash_transactions WHERE id = :id")
    suspend fun deleteTransaction(id: String)

    @Query("DELETE FROM bank_cash_transactions WHERE narration LIKE '%' || :voucherId || '%'")
    suspend fun deleteTransactionsByVoucher(voucherId: String)

    @Query("DELETE FROM bank_cash_transactions")
    suspend fun deleteAllTransactions()
}

@Dao
interface ReceiptAllocationDao {
    @Query("SELECT * FROM receipt_allocations WHERE receiptId = :receiptId")
    fun getAllocationsForReceipt(receiptId: String): Flow<List<ReceiptAllocation>>

    @Query("SELECT * FROM receipt_allocations")
    fun getAllReceiptAllocations(): Flow<List<ReceiptAllocation>>

    @Query("SELECT * FROM receipt_allocations")
    suspend fun getAllReceiptAllocationsSync(): List<ReceiptAllocation>

    @Query("SELECT * FROM receipt_allocations WHERE invoiceId = :invoiceId")
    suspend fun getAllocationsForInvoiceSync(invoiceId: String): List<ReceiptAllocation>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllocation(allocation: ReceiptAllocation)

    @Query("DELETE FROM receipt_allocations WHERE receiptId = :receiptId")
    suspend fun deleteAllocationsByReceipt(receiptId: String)

    @Query("DELETE FROM receipt_allocations WHERE invoiceId = :invoiceId")
    suspend fun deleteAllocationsByInvoice(invoiceId: String)

    @Query("DELETE FROM receipt_allocations")
    suspend fun deleteAllAllocations()
}

@Dao
interface LedgerAccountDao {
    @Query("SELECT * FROM ledger_accounts ORDER BY name ASC")
    fun getAllLedgerAccounts(): Flow<List<LedgerAccount>>

    @Query("SELECT * FROM ledger_accounts ORDER BY name ASC")
    suspend fun getAllLedgerAccountsSync(): List<LedgerAccount>

    @Query("SELECT * FROM ledger_accounts WHERE id = :id LIMIT 1")
    suspend fun getLedgerAccountById(id: String): LedgerAccount?

    @Query("SELECT * FROM ledger_accounts WHERE name = :name LIMIT 1")
    suspend fun getLedgerAccountByName(name: String): LedgerAccount?

    @Query("SELECT * FROM ledger_accounts WHERE partyId = :partyId LIMIT 1")
    suspend fun getLedgerAccountByPartyId(partyId: String): LedgerAccount?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLedgerAccount(account: LedgerAccount)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLedgerAccounts(accounts: List<LedgerAccount>)

    @Query("UPDATE ledger_accounts SET openingBalance = :balance WHERE id = :id")
    suspend fun updateOpeningBalance(id: String, balance: Double)

    @Query("DELETE FROM ledger_accounts WHERE id = :id")
    suspend fun deleteLedgerAccount(id: String)
    
    @Query("DELETE FROM ledger_accounts WHERE partyId = :partyId")
    suspend fun deleteLedgerAccountByParty(partyId: String)

    @Query("DELETE FROM ledger_accounts")
    suspend fun deleteAllLedgerAccounts()
}

@Dao
interface BillReceivableDao {
    @Query("SELECT * FROM bills_receivable ORDER BY billDate DESC")
    fun getAllBills(): Flow<List<BillReceivable>>

    @Query("SELECT * FROM bills_receivable ORDER BY billDate DESC")
    suspend fun getAllBillsSync(): List<BillReceivable>

    @Query("SELECT * FROM bills_receivable WHERE voucherId = :voucherId LIMIT 1")
    suspend fun getBillByVoucherId(voucherId: String): BillReceivable?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBill(bill: BillReceivable)

    @Query("DELETE FROM bills_receivable WHERE voucherId = :voucherId")
    suspend fun deleteBillByVoucherId(voucherId: String)

    @Query("DELETE FROM bills_receivable")
    suspend fun deleteAllBills()
}
