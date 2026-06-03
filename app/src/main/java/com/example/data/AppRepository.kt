package com.example.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import java.util.Calendar
import java.util.UUID

class AppRepository(private val db: AppDatabase) {

    val profile = db.businessProfileDao().getProfile()
    val parties = db.partyDao().getAllParties()
    val products = db.productDao().getAllProducts()
    val vouchers = db.voucherDao().getAllVouchers()
    val ledgerEntries = db.ledgerDao().getAllLedgerEntries()
    val cashTransactions = db.bankCashDao().getCashTransactions()
    val bankTransactions = db.bankCashDao().getBankTransactions()
    val allTransactions = db.bankCashDao().getAllTransactions()

    // Retrieve specific items
    fun getItemsForVoucher(voucherId: String): Flow<List<VoucherItem>> =
        db.voucherItemDao().getItemsForVoucher(voucherId)

    suspend fun getVoucherById(id: String) = db.voucherDao().getVoucherById(id)

    suspend fun getPartyById(id: String) = db.partyDao().getPartyById(id)

    suspend fun getProductById(id: String) = db.productDao().getProductById(id)

    // Profile Operations
    suspend fun insertProfile(profile: BusinessProfile) {
        db.businessProfileDao().insertProfile(profile)
    }

    suspend fun getProfileSync(): BusinessProfile? {
        return db.businessProfileDao().getProfileSync()
    }

    // Party Operations
    suspend fun insertParty(party: Party) {
        db.partyDao().insertParty(party)
    }

    suspend fun deleteParty(id: String) {
        db.partyDao().deleteParty(id)
    }

    // Product Operations
    suspend fun insertProduct(product: Product) {
        db.productDao().insertProduct(product)
    }

    suspend fun deleteProduct(id: String) {
        db.productDao().deleteProduct(id)
    }

    // Helpers
    fun getFinancialYear(timestamp: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) // 0-based
        return if (month >= Calendar.APRIL) {
            "$year-${(year + 1) % 100}"
        } else {
            "${year - 1}-${year % 100}"
        }
    }

    suspend fun generateNextVoucherNo(type: String, timestamp: Long): String {
        val fy = getFinancialYear(timestamp)
        val prefix = when (type) {
            "SALE" -> "SAL"
            "PURCHASE" -> "PUR"
            "SALE_RETURN" -> "SRN"
            "PURCHASE_RETURN" -> "PRN"
            "RECEIPT" -> "RCP"
            "PAYMENT" -> "PMT"
            "DEBIT_NOTE" -> "DBN"
            "CREDIT_NOTE" -> "CRN"
            else -> "VCH"
        }
        val pattern = "$prefix/$fy/%"
        val latestNo = db.voucherDao().getLatestVoucherNo(type, pattern)
        val sequenceNum = if (latestNo != null) {
            val parts = latestNo.split("/")
            if (parts.size >= 3) {
                val lastPart = parts.last().toIntOrNull() ?: 0
                lastPart + 1
            } else {
                1
            }
        } else {
            1
        }
        val formattedSeq = String.format("%04d", sequenceNum)
        return "$prefix/$fy/$formattedSeq"
    }

    // Comprehensive Voucher Post Logic
    suspend fun saveAndPostVoucher(
        voucher: Voucher,
        items: List<VoucherItem>,
        partyName: String?
    ) {
        db.withTransaction {
            // 1. Delete if existing
            db.voucherDao().deleteVoucher(voucher.id)
            db.voucherItemDao().deleteItemsForVoucher(voucher.id)
            db.ledgerDao().deleteLedgerEntriesForVoucher(voucher.id)
            db.bankCashDao().deleteTransactionsByVoucher(voucher.id)

            // 2. Insert Voucher & Items
            db.voucherDao().insertVoucher(voucher)
            db.voucherItemDao().insertItems(items.map { it.copy(voucherId = voucher.id) })

            // 3. Auto-generate Ledger entries
            val ledgerList = mutableListOf<LedgerEntry>()
            val partyDesc = partyName ?: "Cash/Bank Account"

            when (voucher.type) {
                "SALE" -> {
                    // DR: Party or Cash/Bank
                    val drHead = if (voucher.paymentMode == "CASH") "Cash" else if (voucher.paymentMode == "BANK" || voucher.paymentMode == "UPI") "Bank" else "Party: $partyDesc"
                    ledgerList.add(
                        LedgerEntry(
                            id = UUID.randomUUID().toString(),
                            accountHead = drHead,
                            voucherId = voucher.id,
                            date = voucher.date,
                            debit = voucher.netAmount,
                            credit = 0.0,
                            narration = "Sales entry for voucher ${voucher.voucherNo}",
                            createdAt = System.currentTimeMillis()
                        )
                    )
                    // CR: Sales
                    ledgerList.add(
                        LedgerEntry(
                            id = UUID.randomUUID().toString(),
                            accountHead = "Sales Account",
                            voucherId = voucher.id,
                            date = voucher.date,
                            debit = 0.0,
                            credit = voucher.taxableAmount,
                            narration = "Sales credit",
                            createdAt = System.currentTimeMillis()
                        )
                    )
                    // CR: CGST / SGST / IGST
                    if (voucher.isIgst) {
                        if (voucher.igst > 0) {
                            ledgerList.add(
                                LedgerEntry(
                                    id = UUID.randomUUID().toString(),
                                    accountHead = "IGST Payable",
                                    voucherId = voucher.id,
                                    date = voucher.date,
                                    debit = 0.0,
                                    credit = voucher.igst,
                                    narration = "IGST liability",
                                    createdAt = System.currentTimeMillis()
                                )
                            )
                        }
                    } else {
                        if (voucher.cgst > 0) {
                            ledgerList.add(
                                LedgerEntry(
                                    id = UUID.randomUUID().toString(),
                                    accountHead = "CGST Payable",
                                    voucherId = voucher.id,
                                    date = voucher.date,
                                    debit = 0.0,
                                    credit = voucher.cgst,
                                    narration = "CGST liability",
                                    createdAt = System.currentTimeMillis()
                                )
                            )
                        }
                        if (voucher.sgst > 0) {
                            ledgerList.add(
                                LedgerEntry(
                                    id = UUID.randomUUID().toString(),
                                    accountHead = "SGST Payable",
                                    voucherId = voucher.id,
                                    date = voucher.date,
                                    debit = 0.0,
                                    credit = voucher.sgst,
                                    narration = "SGST liability",
                                    createdAt = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                    // DR/CR: Round Off
                    if (voucher.roundOff != 0.0) {
                        ledgerList.add(
                            LedgerEntry(
                                id = UUID.randomUUID().toString(),
                                accountHead = "Round Off Account",
                                voucherId = voucher.id,
                                date = voucher.date,
                                debit = if (voucher.roundOff > 0) voucher.roundOff else 0.0,
                                credit = if (voucher.roundOff < 0) -voucher.roundOff else 0.0,
                                narration = "Round Off adjust",
                                createdAt = System.currentTimeMillis()
                            )
                        )
                    }
                }

                "PURCHASE" -> {
                    // DR: Purchase
                    ledgerList.add(
                        LedgerEntry(
                            id = UUID.randomUUID().toString(),
                            accountHead = "Purchase Account",
                            voucherId = voucher.id,
                            date = voucher.date,
                            debit = voucher.taxableAmount,
                            credit = 0.0,
                            narration = "Purchase debit",
                            createdAt = System.currentTimeMillis()
                        )
                    )
                    // DR: CGST / SGST / IGST Receivable
                    if (voucher.isIgst) {
                        if (voucher.igst > 0) {
                            ledgerList.add(
                                LedgerEntry(
                                    id = UUID.randomUUID().toString(),
                                    accountHead = "IGST Receivable",
                                    voucherId = voucher.id,
                                    date = voucher.date,
                                    debit = voucher.igst,
                                    credit = 0.0,
                                    narration = "IGST receivable",
                                    createdAt = System.currentTimeMillis()
                                )
                            )
                        }
                    } else {
                        if (voucher.cgst > 0) {
                            ledgerList.add(
                                LedgerEntry(
                                    id = UUID.randomUUID().toString(),
                                    accountHead = "CGST Receivable",
                                    voucherId = voucher.id,
                                    date = voucher.date,
                                    debit = voucher.cgst,
                                    credit = 0.0,
                                    narration = "CGST receivable",
                                    createdAt = System.currentTimeMillis()
                                )
                            )
                        }
                        if (voucher.sgst > 0) {
                            ledgerList.add(
                                LedgerEntry(
                                    id = UUID.randomUUID().toString(),
                                    accountHead = "SGST Receivable",
                                    voucherId = voucher.id,
                                    date = voucher.date,
                                    debit = voucher.sgst,
                                    credit = 0.0,
                                    narration = "SGST receivable",
                                    createdAt = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                    // DR/CR: Round off
                    if (voucher.roundOff != 0.0) {
                        ledgerList.add(
                            LedgerEntry(
                                id = UUID.randomUUID().toString(),
                                accountHead = "Round Off Account",
                                voucherId = voucher.id,
                                date = voucher.date,
                                debit = if (voucher.roundOff > 0) voucher.roundOff else 0.0,
                                credit = if (voucher.roundOff < 0) -voucher.roundOff else 0.0,
                                narration = "Round Off adjust",
                                createdAt = System.currentTimeMillis()
                            )
                        )
                    }
                    // CR: Party or Cash/Bank
                    val crHead = if (voucher.paymentMode == "CASH") "Cash" else if (voucher.paymentMode == "BANK" || voucher.paymentMode == "UPI") "Bank" else "Party: $partyDesc"
                    ledgerList.add(
                        LedgerEntry(
                            id = UUID.randomUUID().toString(),
                            accountHead = crHead,
                            voucherId = voucher.id,
                            date = voucher.date,
                            debit = 0.0,
                            credit = voucher.netAmount,
                            narration = "Purchase entry for voucher ${voucher.voucherNo}",
                            createdAt = System.currentTimeMillis()
                        )
                    )
                }

                "RECEIPT" -> {
                    // DR: Cash/Bank
                    val drHead = if (voucher.paymentMode == "CASH") "Cash" else "Bank"
                    ledgerList.add(
                        LedgerEntry(
                            id = UUID.randomUUID().toString(),
                            accountHead = drHead,
                            voucherId = voucher.id,
                            date = voucher.date,
                            debit = voucher.netAmount,
                            credit = 0.0,
                            narration = "Receipt entry",
                            createdAt = System.currentTimeMillis()
                        )
                    )
                    // CR: Party
                    ledgerList.add(
                        LedgerEntry(
                            id = UUID.randomUUID().toString(),
                            accountHead = "Party: $partyDesc",
                            voucherId = voucher.id,
                            date = voucher.date,
                            debit = 0.0,
                            credit = voucher.netAmount,
                            narration = "Receipt from $partyDesc",
                            createdAt = System.currentTimeMillis()
                        )
                    )
                }

                "PAYMENT" -> {
                    // DR: Party
                    ledgerList.add(
                        LedgerEntry(
                            id = UUID.randomUUID().toString(),
                            accountHead = "Party: $partyDesc",
                            voucherId = voucher.id,
                            date = voucher.date,
                            debit = voucher.netAmount,
                            credit = 0.0,
                            narration = "Payment to $partyDesc",
                            createdAt = System.currentTimeMillis()
                        )
                    )
                    // CR: Cash/Bank
                    val crHead = if (voucher.paymentMode == "CASH") "Cash" else "Bank"
                    ledgerList.add(
                        LedgerEntry(
                            id = UUID.randomUUID().toString(),
                            accountHead = crHead,
                            voucherId = voucher.id,
                            date = voucher.date,
                            debit = 0.0,
                            credit = voucher.netAmount,
                            narration = "Payment entry",
                            createdAt = System.currentTimeMillis()
                        )
                    )
                }

                "SALE_RETURN" -> {
                    // Reverse of SALE
                    // DR: Sales Return Account
                    ledgerList.add(
                        LedgerEntry(
                            id = UUID.randomUUID().toString(),
                            accountHead = "Sales Return Account",
                            voucherId = voucher.id,
                            date = voucher.date,
                            debit = voucher.taxableAmount,
                            credit = 0.0,
                            narration = "Sales Return debit",
                            createdAt = System.currentTimeMillis()
                        )
                    )
                    // DR: CGST / SGST / IGST Payable
                    if (voucher.isIgst) {
                        if (voucher.igst > 0) {
                            ledgerList.add(
                                LedgerEntry(
                                    id = UUID.randomUUID().toString(),
                                    accountHead = "IGST Payable",
                                    voucherId = voucher.id,
                                    date = voucher.date,
                                    debit = voucher.igst,
                                    credit = 0.0,
                                    narration = "IGST reversal",
                                    createdAt = System.currentTimeMillis()
                                )
                            )
                        }
                    } else {
                        if (voucher.cgst > 0) {
                            ledgerList.add(
                                LedgerEntry(
                                    id = UUID.randomUUID().toString(),
                                    accountHead = "CGST Payable",
                                    voucherId = voucher.id,
                                    date = voucher.date,
                                    debit = voucher.cgst,
                                    credit = 0.0,
                                    narration = "CGST reversal",
                                    createdAt = System.currentTimeMillis()
                                )
                            )
                        }
                        if (voucher.sgst > 0) {
                            ledgerList.add(
                                LedgerEntry(
                                    id = UUID.randomUUID().toString(),
                                    accountHead = "SGST Payable",
                                    voucherId = voucher.id,
                                    date = voucher.date,
                                    debit = voucher.sgst,
                                    credit = 0.0,
                                    narration = "SGST reversal",
                                    createdAt = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                    // CR: Party / Cash / Bank
                    val crHead = if (voucher.paymentMode == "CASH") "Cash" else if (voucher.paymentMode == "BANK" || voucher.paymentMode == "UPI") "Bank" else "Party: $partyDesc"
                    ledgerList.add(
                        LedgerEntry(
                            id = UUID.randomUUID().toString(),
                            accountHead = crHead,
                            voucherId = voucher.id,
                            date = voucher.date,
                            debit = 0.0,
                            credit = voucher.netAmount,
                            narration = "Sales Return entry for voucher ${voucher.voucherNo}",
                            createdAt = System.currentTimeMillis()
                        )
                    )
                }

                "PURCHASE_RETURN" -> {
                    // Reverse of PURCHASE
                    // DR: Party or Cash/Bank
                    val drHead = if (voucher.paymentMode == "CASH") "Cash" else if (voucher.paymentMode == "BANK" || voucher.paymentMode == "UPI") "Bank" else "Party: $partyDesc"
                    ledgerList.add(
                        LedgerEntry(
                            id = UUID.randomUUID().toString(),
                            accountHead = drHead,
                            voucherId = voucher.id,
                            date = voucher.date,
                            debit = voucher.netAmount,
                            credit = 0.0,
                            narration = "Purchase Return entry for ${voucher.voucherNo}",
                            createdAt = System.currentTimeMillis()
                        )
                    )
                    // CR: Purchase Return Account
                    ledgerList.add(
                        LedgerEntry(
                            id = UUID.randomUUID().toString(),
                            accountHead = "Purchase Return Account",
                            voucherId = voucher.id,
                            date = voucher.date,
                            debit = 0.0,
                            credit = voucher.taxableAmount,
                            narration = "Purchase Return credit",
                            createdAt = System.currentTimeMillis()
                        )
                    )
                    // CR: CGST / SGST / IGST Receivable
                    if (voucher.isIgst) {
                        if (voucher.igst > 0) {
                            ledgerList.add(
                                LedgerEntry(
                                    id = UUID.randomUUID().toString(),
                                    accountHead = "IGST Receivable",
                                    voucherId = voucher.id,
                                    date = voucher.date,
                                    debit = 0.0,
                                    credit = voucher.igst,
                                    narration = "IGST Receivable reversal",
                                    createdAt = System.currentTimeMillis()
                                )
                            )
                        }
                    } else {
                        if (voucher.cgst > 0) {
                            ledgerList.add(
                                LedgerEntry(
                                    id = UUID.randomUUID().toString(),
                                    accountHead = "CGST Receivable",
                                    voucherId = voucher.id,
                                    date = voucher.date,
                                    debit = 0.0,
                                    credit = voucher.cgst,
                                    narration = "CGST Receivable reversal",
                                    createdAt = System.currentTimeMillis()
                                )
                            )
                        }
                        if (voucher.sgst > 0) {
                            ledgerList.add(
                                LedgerEntry(
                                    id = UUID.randomUUID().toString(),
                                    accountHead = "SGST Receivable",
                                    voucherId = voucher.id,
                                    date = voucher.date,
                                    debit = 0.0,
                                    credit = voucher.sgst,
                                    narration = "SGST Receivable reversal",
                                    createdAt = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                }

                "BILLS_RECEIVABLE" -> {
                    ledgerList.add(
                        LedgerEntry(
                            id = UUID.randomUUID().toString(),
                            accountHead = "Bills Receivable Account",
                            voucherId = voucher.id,
                            date = voucher.date,
                            debit = voucher.netAmount,
                            credit = 0.0,
                            narration = "Bills Receivable entry",
                            createdAt = System.currentTimeMillis()
                        )
                    )
                    ledgerList.add(
                        LedgerEntry(
                            id = UUID.randomUUID().toString(),
                            accountHead = "Party: $partyDesc",
                            voucherId = voucher.id,
                            date = voucher.date,
                            debit = 0.0,
                            credit = voucher.netAmount,
                            narration = "Bills Receivable from $partyDesc",
                            createdAt = System.currentTimeMillis()
                        )
                    )
                }

                "BILLS_PAYABLE" -> {
                    ledgerList.add(
                        LedgerEntry(
                            id = UUID.randomUUID().toString(),
                            accountHead = "Party: $partyDesc",
                            voucherId = voucher.id,
                            date = voucher.date,
                            debit = voucher.netAmount,
                            credit = 0.0,
                            narration = "Bills Payable to $partyDesc",
                            createdAt = System.currentTimeMillis()
                        )
                    )
                    ledgerList.add(
                        LedgerEntry(
                            id = UUID.randomUUID().toString(),
                            accountHead = "Bills Payable Account",
                            voucherId = voucher.id,
                            date = voucher.date,
                            debit = 0.0,
                            credit = voucher.netAmount,
                            narration = "Bills Payable entry",
                            createdAt = System.currentTimeMillis()
                        )
                    )
                }
            }

            if (ledgerList.isNotEmpty()) {
                db.ledgerDao().insertLedgerEntries(ledgerList)
            }

            // 4. Auto-register Cash or Bank trans
            val isReceipt = (voucher.type == "RECEIPT" || voucher.type == "SALE" || voucher.type == "PURCHASE_RETURN")
            val isPayment = (voucher.type == "PAYMENT" || voucher.type == "PURCHASE" || voucher.type == "SALE_RETURN")
            
            if (isReceipt || isPayment) {
                val txType = if (isReceipt) "RECEIPT" else "PAYMENT"
                db.bankCashDao().insertTransaction(
                    BankCashTransaction(
                        id = UUID.randomUUID().toString(),
                        type = txType,
                        mode = voucher.paymentMode,
                        amount = voucher.netAmount,
                        date = voucher.date,
                        partyId = voucher.partyId,
                        partyName = partyDesc,
                        narration = "Auto-posted for voucher ${voucher.voucherNo}. ${voucher.narration}",
                        chequeNo = voucher.chequeNo,
                        chequeDate = voucher.chequeDate,
                        bankName = voucher.bankName,
                        receiptImagePath = null,
                        createdAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    suspend fun deleteVoucher(id: String) {
        db.withTransaction {
            db.voucherDao().deleteVoucher(id)
            db.voucherItemDao().deleteItemsForVoucher(id)
            db.ledgerDao().deleteLedgerEntriesForVoucher(id)
            db.bankCashDao().deleteTransactionsByVoucher(id)
        }
    }

    // Direct Cash/Bank manual transaction
    suspend fun saveBankCashTransaction(transaction: BankCashTransaction) {
        db.withTransaction {
            db.bankCashDao().insertTransaction(transaction)
            
            // Post manual bank/cash to ledger entries
            val drHead = if (transaction.type == "RECEIPT") {
                if (transaction.mode == "CASH") "Cash" else "Bank"
            } else {
                "Party: ${transaction.partyName ?: "General"}"
            }
            val crHead = if (transaction.type == "RECEIPT") {
                "Party: ${transaction.partyName ?: "General"}"
            } else {
                if (transaction.mode == "CASH") "Cash" else "Bank"
            }

            val ledgerEntries = listOf(
                LedgerEntry(
                    id = UUID.randomUUID().toString(),
                    accountHead = drHead,
                    voucherId = transaction.id,
                    date = transaction.date,
                    debit = transaction.amount,
                    credit = 0.0,
                    narration = transaction.narration,
                    createdAt = System.currentTimeMillis()
                ),
                LedgerEntry(
                    id = UUID.randomUUID().toString(),
                    accountHead = crHead,
                    voucherId = transaction.id,
                    date = transaction.date,
                    debit = 0.0,
                    credit = transaction.amount,
                    narration = transaction.narration,
                    createdAt = System.currentTimeMillis()
                )
            )
            db.ledgerDao().insertLedgerEntries(ledgerEntries)
        }
    }

    suspend fun deleteTransaction(id: String) {
        db.withTransaction {
            db.bankCashDao().deleteTransaction(id)
            db.ledgerDao().deleteLedgerEntriesForVoucher(id)
        }
    }

    suspend fun insertSampleData() {
        db.withTransaction {
            // Drop tables state (can be destructive or append)
            // Customers
            val p1 = Party(
                id = UUID.randomUUID().toString(),
                name = "Agarwal Distributors",
                type = "CUSTOMER",
                phone = "9876543210",
                email = "agarwal@test.com",
                address = "12/A MG Road",
                city = "Kolkata",
                state = "West Bengal",
                stateCode = "19",
                gstin = "19AAACA1234A1Z1",
                pan = "AAACA1234A",
                openingBalance = 5000.0,
                balanceType = "DR"
            )
            val p2 = Party(
                id = UUID.randomUUID().toString(),
                name = "Sharma Electronics",
                type = "CUSTOMER",
                phone = "9870011223",
                email = "sharma@test.com",
                address = "56 Sector 18",
                city = "Gurugram",
                state = "Haryana",
                stateCode = "06",
                gstin = "06AAACT9182K2Z0",
                pan = "AAACT9182K",
                openingBalance = 0.0,
                balanceType = "DR"
            )
            // Supplier
            val p3 = Party(
                id = UUID.randomUUID().toString(),
                name = "Bharat Wholesalers",
                type = "SUPPLIER",
                phone = "9001122334",
                email = "contact@bharat.com",
                address = "Flat 4, Link Road",
                city = "Mumbai",
                state = "Maharashtra",
                stateCode = "27",
                gstin = "27AAACB3412B1Z8",
                pan = "AAACB3412B",
                openingBalance = 15000.0,
                balanceType = "CR"
            )

            db.partyDao().insertParty(p1)
            db.partyDao().insertParty(p2)
            db.partyDao().insertParty(p3)

            // Products
            val pr1 = Product(
                id = UUID.randomUUID().toString(),
                name = "Steel Basin 18-inch",
                hsnCode = "73241000",
                unit = "PCS",
                saleRate = 1200.0,
                purchaseRate = 850.0,
                gstRate = 18.0,
                openingStock = 50.0
            )
            val pr2 = Product(
                id = UUID.randomUUID().toString(),
                name = "LED Tube Light 20W",
                hsnCode = "85395000",
                unit = "PCS",
                saleRate = 250.0,
                purchaseRate = 170.0,
                gstRate = 12.0,
                openingStock = 120.0
            )
            val pr3 = Product(
                id = UUID.randomUUID().toString(),
                name = "Organic Detergent Powder",
                hsnCode = "34022010",
                unit = "KG",
                saleRate = 180.0,
                purchaseRate = 120.0,
                gstRate = 5.0,
                openingStock = 200.0
            )

            db.productDao().insertProduct(pr1)
            db.productDao().insertProduct(pr2)
            db.productDao().insertProduct(pr3)

            // Dynamic setup can create some vouchers using helper post logic
            // Add a sale (Intrastate - same state, let's assume business is West Bengal (19))
            // We'll code sample vouchers assuming business state is West Bengal
            val v1Id = UUID.randomUUID().toString()
            val v1 = Voucher(
                id = v1Id,
                voucherNo = "SAL/2026-27/0001",
                type = "SALE",
                date = System.currentTimeMillis() - 5 * 24 * 3600 * 1000, // 5 days ago
                partyId = p1.id,
                narration = "Sales to Agarwal Distributors",
                taxableAmount = 2400.0,
                cgst = 216.0,
                sgst = 216.0,
                igst = 0.0,
                roundOff = 0.0,
                netAmount = 2832.0,
                paymentMode = "BANK",
                chequeNo = null,
                chequeDate = null,
                bankName = null,
                isIgst = false,
                status = "POSTED"
            )
            val v1Items = listOf(
                VoucherItem(
                    id = UUID.randomUUID().toString(),
                    voucherId = v1Id,
                    productId = pr1.id,
                    productName = pr1.name,
                    hsnCode = pr1.hsnCode,
                    qty = 2.0,
                    unit = pr1.unit,
                    rate = 1200.0,
                    discount = 0.0,
                    discountType = "PERCENT",
                    taxableAmount = 2400.0,
                    gstRate = 18.0,
                    cgstAmount = 216.0,
                    sgstAmount = 216.0,
                    igstAmount = 0.0,
                    totalAmount = 2832.0
                )
            )
            saveAndPostVoucher(v1, v1Items, p1.name)

            // Add an Interstate Sale (Haryana (06) from West Bengal (19))
            val v2Id = UUID.randomUUID().toString()
            val v2 = Voucher(
                id = v2Id,
                voucherNo = "SAL/2026-27/0002",
                type = "SALE",
                date = System.currentTimeMillis() - 3 * 24 * 3600 * 1000,
                partyId = p2.id,
                narration = "Interstate sales to Sharma Electronics",
                taxableAmount = 500.0,
                cgst = 0.0,
                sgst = 0.0,
                igst = 60.0,
                roundOff = 0.0,
                netAmount = 560.0,
                paymentMode = "UPI",
                chequeNo = null,
                chequeDate = null,
                bankName = null,
                isIgst = true,
                status = "POSTED"
            )
            val v2Items = listOf(
                VoucherItem(
                    id = UUID.randomUUID().toString(),
                    voucherId = v2Id,
                    productId = pr2.id,
                    productName = pr2.name,
                    hsnCode = pr2.hsnCode,
                    qty = 2.0,
                    unit = pr2.unit,
                    rate = 250.0,
                    discount = 0.0,
                    discountType = "PERCENT",
                    taxableAmount = 500.0,
                    gstRate = 12.0,
                    cgstAmount = 0.0,
                    sgstAmount = 0.0,
                    igstAmount = 60.0,
                    totalAmount = 560.0
                )
            )
            saveAndPostVoucher(v2, v2Items, p2.name)

            // Add a purchase
            val v3Id = UUID.randomUUID().toString()
            val v3 = Voucher(
                id = v3Id,
                voucherNo = "PUR/2026-27/0001",
                type = "PURCHASE",
                date = System.currentTimeMillis() - 1 * 24 * 3600 * 1000,
                partyId = p3.id,
                narration = "Stock purchase from Bharat Wholesalers",
                taxableAmount = 8500.0,
                cgst = 0.0,
                sgst = 0.0,
                igst = 1530.0,
                roundOff = 0.0,
                netAmount = 10030.0,
                paymentMode = "CASH",
                chequeNo = null,
                chequeDate = null,
                bankName = null,
                isIgst = true,
                status = "POSTED"
            )
            val v3Items = listOf(
                VoucherItem(
                    id = UUID.randomUUID().toString(),
                    voucherId = v3Id,
                    productId = pr1.id,
                    productName = pr1.name,
                    hsnCode = pr1.hsnCode,
                    qty = 10.0,
                    unit = pr1.unit,
                    rate = 850.0,
                    discount = 0.0,
                    discountType = "PERCENT",
                    taxableAmount = 8500.0,
                    gstRate = 18.0,
                    cgstAmount = 0.0,
                    sgstAmount = 0.0,
                    igstAmount = 1530.0,
                    totalAmount = 10030.0
                )
            )
            saveAndPostVoucher(v3, v3Items, p3.name)

            // Add a Receipt
            val v4Id = UUID.randomUUID().toString()
            val v4 = Voucher(
                id = v4Id,
                voucherNo = "RCP/2026-27/0001",
                type = "RECEIPT",
                date = System.currentTimeMillis(),
                partyId = p1.id,
                narration = "On account payment from Agarwal Distributors",
                taxableAmount = 0.0,
                cgst = 0.0,
                sgst = 0.0,
                igst = 0.0,
                roundOff = 0.0,
                netAmount = 2000.0,
                paymentMode = "UPI",
                chequeNo = null,
                chequeDate = null,
                bankName = null,
                isIgst = false,
                status = "POSTED"
            )
            saveAndPostVoucher(v4, emptyList(), p1.name)
        }
    }
}
