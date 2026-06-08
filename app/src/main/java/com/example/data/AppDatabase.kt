package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        BusinessProfile::class,
        Party::class,
        Product::class,
        Voucher::class,
        VoucherItem::class,
        LedgerEntry::class,
        BankCashTransaction::class,
        ReceiptAllocation::class,
        LedgerAccount::class,
        BillReceivable::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun businessProfileDao(): BusinessProfileDao
    abstract fun partyDao(): PartyDao
    abstract fun productDao(): ProductDao
    abstract fun voucherDao(): VoucherDao
    abstract fun voucherItemDao(): VoucherItemDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun bankCashDao(): BankCashDao
    abstract fun receiptAllocationDao(): ReceiptAllocationDao
    abstract fun ledgerAccountDao(): LedgerAccountDao
    abstract fun billReceivableDao(): BillReceivableDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ZeroBook.db"
                )
                .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        ensureVoucherExtensionColumns(db)
                        ensureBusinessProfileExtensionColumns(db)
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        ensureVoucherExtensionColumns(db)
                        ensureBusinessProfileExtensionColumns(db)
                    }
                })
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }

    }
}
