package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "business_profile")
data class BusinessProfile(
    @PrimaryKey val id: Int = 1,
    val businessName: String,
    val ownerName: String,
    val address: String,
    val city: String,
    val state: String,
    val pin: String,
    val phone: String,
    val email: String,
    val gstin: String,
    val pan: String,
    val stateCode: String,
    val bankName: String,
    val accountNo: String,
    val ifsc: String,
    val logoPath: String? = null,
    val signaturePath: String? = null,
    val fyStartMonth: Int = 4,
    val fyStartYear: Int = 2025,
    val fyEndYear: Int = 2026,
    val fyLabel: String = "2025-26",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "parties")
data class Party(
    @PrimaryKey val id: String, // UUID
    val name: String,
    val type: String, // "CUSTOMER", "SUPPLIER", "BOTH"
    val phone: String,
    val email: String,
    val address: String,
    val city: String,
    val state: String,
    val stateCode: String,
    val gstin: String?,
    val pan: String?,
    val openingBalance: Double,
    val balanceType: String, // "DR" or "CR"
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "products")
data class Product(
    @PrimaryKey val id: String, // UUID
    val name: String,
    val hsnCode: String,
    val unit: String, // "PCS", "KG", "LTR", "MTR", "BOX", "BAG", "NOS"
    val saleRate: Double,
    val purchaseRate: Double,
    val gstRate: Double, // 0.0, 5.0, 12.0, 18.0, 28.0
    val openingStock: Double,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "vouchers")
data class Voucher(
    @PrimaryKey val id: String, // UUID
    val voucherNo: String,
    val type: String, // "SALE", "PURCHASE", "SALE_RETURN", "PURCHASE_RETURN", "RECEIPT", "PAYMENT", "DEBIT_NOTE", "CREDIT_NOTE"
    val date: Long,
    val partyId: String?,
    val narration: String,
    val taxableAmount: Double,
    val cgst: Double,
    val sgst: Double,
    val igst: Double,
    val roundOff: Double,
    val netAmount: Double,
    val paymentMode: String, // "CASH", "BANK", "CHEQUE", "UPI"
    val chequeNo: String?,
    val chequeDate: Long?,
    val bankName: String?,
    val isIgst: Boolean,
    val status: String, // "DRAFT", "POSTED"
    val receiptImagePath: String? = null,
    val bankIfsc: String? = null,
    val bankAccountHolder: String? = null,
    val bankNameDetail: String? = null,
    val memoNumber: String? = null,
    val branchName: String? = null,
    val outstandingAmount: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "voucher_items")
data class VoucherItem(
    @PrimaryKey val id: String, // UUID
    val voucherId: String,
    val productId: String,
    val productName: String,
    val hsnCode: String,
    val qty: Double,
    val unit: String,
    val rate: Double,
    val discount: Double,
    val discountType: String, // "PERCENT", "AMOUNT"
    val taxableAmount: Double,
    val gstRate: Double,
    val cgstAmount: Double,
    val sgstAmount: Double,
    val igstAmount: Double,
    val totalAmount: Double
)

@Entity(tableName = "ledger_entries")
data class LedgerEntry(
    @PrimaryKey val id: String, // UUID
    val accountHead: String, // e.g. "Sales Account", "Party Name", "CGST Payable", "Round Off Account", "Cash", "Bank"
    val voucherId: String,
    val date: Long,
    val debit: Double,
    val credit: Double,
    val narration: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "bank_cash_transactions")
data class BankCashTransaction(
    @PrimaryKey val id: String, // UUID
    val type: String, // "RECEIPT", "PAYMENT"
    val mode: String, // "CASH", "BANK", "CHEQUE", "UPI", "NEFT", "RTGS", "IMPS"
    val amount: Double,
    val date: Long,
    val partyId: String?,
    val partyName: String?,
    val narration: String,
    val chequeNo: String?,
    val chequeDate: Long?,
    val bankName: String?,
    val receiptImagePath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "receipt_allocations")
data class ReceiptAllocation(
    @PrimaryKey val id: String, // UUID
    val receiptId: String, // Voucher ID of RECEIPT or PAYMENT voucher
    val invoiceId: String, // Voucher ID of SALE or PURCHASE voucher
    val allocatedAmount: Double,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "ledger_accounts")
data class LedgerAccount(
    @PrimaryKey val id: String, // UUID
    val name: String,
    val groupName: String,
    val openingBalance: Double = 0.0,
    val balanceType: String = "DR", // "DR" or "CR"
    val isSystem: Int = 0, // 0=no, 1=yes
    val isParty: Int = 0, // 0=no, 1=yes
    val partyId: String? = null,
    val gstin: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "bills_receivable")
data class BillReceivable(
    @PrimaryKey val id: String, // UUID
    val voucherId: String,
    val voucherNo: String?,
    val partyId: String,
    val partyName: String?,
    val billDate: Long,
    val dueDate: Long?,
    val originalAmount: Double = 0.0,
    val paidAmount: Double = 0.0,
    val outstandingAmount: Double = 0.0,
    val status: String = "UNPAID", // UNPAID / PARTIAL / PAID / OVERDUE
    val daysOverdue: Int = 0,
    val lastReminderDate: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

