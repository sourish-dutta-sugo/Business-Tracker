package com.zerobook.data

object SampleData {
    fun snapshot(): ZeroBookSnapshot {
        val profile = BusinessProfile(
            businessName = "ZeroBook Traders",
            ownerName = "Sourish Dutta",
            city = "Kolkata",
            state = "West Bengal",
            phone = "+91 98765 43210",
            email = "accounts@zerobook.in",
            fyLabel = "2026-27",
        )

        val parties = listOf(
            Party("P1", "Agarwal Distributors", "CUSTOMER", "9876543210", "Kolkata", 8320.0, "DR"),
            Party("P2", "Sharma Electronics", "CUSTOMER", "9870011223", "Gurugram", 560.0, "DR"),
            Party("P3", "Bharat Wholesalers", "SUPPLIER", "9001122334", "Mumbai", 10030.0, "CR"),
        )

        val products = listOf(
            Product("PR1", "Steel Basin 18-inch", "PCS", 1200.0, 58.0, 18.0),
            Product("PR2", "LED Tube Light 20W", "PCS", 250.0, 118.0, 12.0),
            Product("PR3", "Organic Detergent Powder", "KG", 180.0, 200.0, 5.0),
        )

        val vouchers = listOf(
            Voucher("V1", "SAL/2026-27/0002", "SALE", "Sharma Electronics", "09 Jun 2026", 560.0, "POSTED"),
            Voucher("V2", "RCP/2026-27/0001", "RECEIPT", "Agarwal Distributors", "11 Jun 2026", 2000.0, "POSTED"),
            Voucher("V3", "PUR/2026-27/0001", "PURCHASE", "Bharat Wholesalers", "10 Jun 2026", 10030.0, "POSTED"),
        )

        val expenses = listOf(
            Expense("E1", "Rent", "June warehouse rent", 18000.0, "01 Jun 2026"),
            Expense("E2", "Transport", "Delivery van diesel", 3200.0, "08 Jun 2026"),
            Expense("E3", "Utilities", "Office internet and power", 2450.0, "09 Jun 2026"),
        )

        val metrics = listOf(
            DashboardMetric("Sales This Week", "Rs 3,392", "2 posted invoices"),
            DashboardMetric("Payables", "Rs 10,030", "1 supplier outstanding"),
            DashboardMetric("Receivables", "Rs 8,880", "2 customer balances"),
            DashboardMetric("Low Stock Alerts", "0", "All items above threshold"),
        )

        return ZeroBookSnapshot(
            profile = profile,
            parties = parties,
            products = products,
            vouchers = vouchers,
            expenses = expenses,
            metrics = metrics,
        )
    }
}
