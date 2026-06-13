package com.zerobook.data

data class BusinessProfile(
    val businessName: String,
    val ownerName: String,
    val city: String,
    val state: String,
    val phone: String,
    val email: String,
    val fyLabel: String,
)

data class Party(
    val id: String,
    val name: String,
    val type: String,
    val phone: String,
    val city: String,
    val balance: Double,
    val balanceType: String,
)

data class Product(
    val id: String,
    val name: String,
    val unit: String,
    val saleRate: Double,
    val currentStock: Double,
    val gstRate: Double,
)

data class Voucher(
    val id: String,
    val voucherNo: String,
    val type: String,
    val partyName: String,
    val dateLabel: String,
    val netAmount: Double,
    val status: String,
)

data class Expense(
    val id: String,
    val category: String,
    val description: String,
    val amount: Double,
    val dateLabel: String,
)

data class DashboardMetric(
    val label: String,
    val value: String,
    val supportingText: String,
)

data class ZeroBookSnapshot(
    val profile: BusinessProfile,
    val parties: List<Party>,
    val products: List<Product>,
    val vouchers: List<Voucher>,
    val expenses: List<Expense>,
    val metrics: List<DashboardMetric>,
)

fun emptyZeroBookSnapshot(): ZeroBookSnapshot = ZeroBookSnapshot(
    profile = BusinessProfile(
        businessName = "",
        ownerName = "",
        city = "",
        state = "",
        phone = "",
        email = "",
        fyLabel = "",
    ),
    parties = emptyList(),
    products = emptyList(),
    vouchers = emptyList(),
    expenses = emptyList(),
    metrics = listOf(
        DashboardMetric("Sales Today", "₹0.00", "No entries yet"),
        DashboardMetric("Purchases", "₹0.00", "No entries yet"),
        DashboardMetric("Receivable", "₹0.00", "No entries yet"),
        DashboardMetric("Payable", "₹0.00", "No entries yet"),
        DashboardMetric("Cash", "₹0.00", "No entries yet"),
        DashboardMetric("Bank", "₹0.00", "No entries yet"),
        DashboardMetric("GST Liability", "₹0.00", "No entries yet"),
        DashboardMetric("Profit", "₹0.00", "No entries yet"),
    ),
)
