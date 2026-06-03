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
}

@Dao
interface PartyDao {
    @Query("SELECT * FROM parties ORDER BY name ASC")
    fun getAllParties(): Flow<List<Party>>

    @Query("SELECT * FROM parties WHERE id = :id LIMIT 1")
    suspend fun getPartyById(id: String): Party?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParty(party: Party)

    @Query("DELETE FROM parties WHERE id = :id")
    suspend fun deleteParty(id: String)
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllProducts(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProductById(id: String): Product?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: Product)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteProduct(id: String)
}

@Dao
interface VoucherDao {
    @Query("SELECT * FROM vouchers ORDER BY date DESC, createdAt DESC")
    fun getAllVouchers(): Flow<List<Voucher>>

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
}

@Dao
interface VoucherItemDao {
    @Query("SELECT * FROM voucher_items WHERE voucherId = :voucherId")
    fun getItemsForVoucher(voucherId: String): Flow<List<VoucherItem>>

    @Query("SELECT * FROM voucher_items WHERE voucherId = :voucherId")
    suspend fun getItemsForVoucherSync(voucherId: String): List<VoucherItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<VoucherItem>)

    @Query("DELETE FROM voucher_items WHERE voucherId = :voucherId")
    suspend fun deleteItemsForVoucher(voucherId: String)
}

@Dao
interface LedgerDao {
    @Query("SELECT * FROM ledger_entries ORDER BY date DESC, createdAt DESC")
    fun getAllLedgerEntries(): Flow<List<LedgerEntry>>

    @Query("SELECT * FROM ledger_entries WHERE accountHead = :accountHead ORDER BY date DESC, createdAt DESC")
    fun getLedgerEntriesByAccount(accountHead: String): Flow<List<LedgerEntry>>

    @Query("SELECT * FROM ledger_entries WHERE voucherId = :voucherId")
    suspend fun getLedgerEntriesByVoucherId(voucherId: String): List<LedgerEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLedgerEntries(entries: List<LedgerEntry>)

    @Query("DELETE FROM ledger_entries WHERE voucherId = :voucherId")
    suspend fun deleteLedgerEntriesForVoucher(voucherId: String)
}

@Dao
interface BankCashDao {
    @Query("SELECT * FROM bank_cash_transactions ORDER BY date DESC, createdAt DESC")
    fun getAllTransactions(): Flow<List<BankCashTransaction>>

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
}
