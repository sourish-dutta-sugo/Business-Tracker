package com.zerobook.database

private data class SystemLedgerSeed(
    val id: String,
    val name: String,
    val groupName: String,
    val balanceType: String,
)

private val systemLedgers = listOf(
    SystemLedgerSeed("system-cash", "Cash", "Current Assets", "DR"),
    SystemLedgerSeed("system-bank", "Bank", "Bank Accounts", "DR"),
    SystemLedgerSeed("system-sales", "Sales", "Sales Accounts", "CR"),
    SystemLedgerSeed("system-purchase", "Purchase", "Purchase Accounts", "DR"),
    SystemLedgerSeed("system-cgst", "CGST Payable", "Duties & Taxes", "CR"),
    SystemLedgerSeed("system-sgst", "SGST Payable", "Duties & Taxes", "CR"),
    SystemLedgerSeed("system-igst", "IGST Payable", "Duties & Taxes", "CR"),
    SystemLedgerSeed("system-capital", "Capital Account", "Capital Account", "CR"),
)

fun bootstrapDatabase(database: ZeroBookDatabase) {
    if (database.zeroBookDatabaseQueries.getLedgerAccountCount().executeAsOne() > 0L) {
        return
    }

    systemLedgers.forEach { ledger ->
        database.zeroBookDatabaseQueries.insertLedgerAccount(
            id = ledger.id,
            name = ledger.name,
            group_name = ledger.groupName,
            opening_balance = 0.0,
            balance_type = ledger.balanceType,
            is_system = 1L,
            is_party = 0L,
            party_id = "",
            created_at = 0L,
        )
    }
}
