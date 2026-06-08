package com.example.data

data class AppSyncSnapshot(
    val profile: BusinessProfile?,
    val parties: List<Party>,
    val products: List<Product>,
    val vouchers: List<Voucher>,
    val voucherItems: List<VoucherItem>,
    val ledgerEntries: List<LedgerEntry>,
    val transactions: List<BankCashTransaction>,
    val receiptAllocations: List<ReceiptAllocation>,
    val ledgerAccounts: List<LedgerAccount>,
    val billsReceivable: List<BillReceivable>,
    val updatedAt: Long = System.currentTimeMillis()
)
