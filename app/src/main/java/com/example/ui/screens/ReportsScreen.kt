package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.AppViewModel
import com.example.ui.theme.Colors
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: AppViewModel,
    onNavigateBack: () -> Unit
) {
    val ledgerEntries by viewModel.ledgerEntries.collectAsState()
    val parties by viewModel.parties.collectAsState()
    val products by viewModel.products.collectAsState()

    var activeReport by remember { mutableStateOf("MENU") } // MENU, TRIAL, PL, BALANCE, GST, RECEIVABLES, PAYABLES

    if (activeReport == "MENU") {
        // Main Reports Navigation Index
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Financial Reports (GST)", fontWeight = FontWeight.Bold, color = Colors.textPrimary) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Colors.textPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Colors.cardBackground)
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Colors.background)
                    .verticalScroll(rememberScrollState())
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Select Financial Report to View",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Colors.textSecondary
                )

                // Menu list items (6 Cards as requested)
                ReportMenuCard(
                    title = "Trial Balance",
                    description = "Double entry verification of net debits and credits",
                    icon = Icons.Default.AccountBalance,
                    onClick = { activeReport = "TRIAL" }
                )

                ReportMenuCard(
                    title = "Profit & Loss Account",
                    description = "View statement of trading, total sales, purchases, and net margin",
                    icon = Icons.Default.Assignment,
                    onClick = { activeReport = "PL" }
                )

                ReportMenuCard(
                    title = "Balance Sheet",
                    description = "Statement of financial position showing Total Assets & Liabilities",
                    icon = Icons.Default.Receipt,
                    onClick = { activeReport = "BALANCE" }
                )

                ReportMenuCard(
                    title = "GST Summary Status",
                    description = "Calculated Output Tax collected vs Input Tax Credit offsets",
                    icon = Icons.Default.Percent,
                    onClick = { activeReport = "GST" }
                )

                ReportMenuCard(
                    title = "Outstanding Receivables",
                    description = "Dynamic list of client accounts with positive debit (DR) outstanding balances",
                    icon = Icons.Default.TrendingUp,
                    onClick = { activeReport = "RECEIVABLES" }
                )

                ReportMenuCard(
                    title = "Outstanding Payables",
                    description = "Dynamic list of suppliers with negative credit (CR) balances to pay",
                    icon = Icons.Default.TrendingDown,
                    onClick = { activeReport = "PAYABLES" }
                )
            }
        }
    } else {
        // Shared dynamic computation variables
        val (trialRows, plBlock, bsBlock, gstSummary) = remember(ledgerEntries, parties, products) {
            // Aggregate debit / credit for each unique account head
            val heads = ledgerEntries.map { it.accountHead }.distinct().toMutableList()
            if (!heads.contains("Cash")) heads.add("Cash")
            if (!heads.contains("Bank")) heads.add("Bank")
            if (!heads.contains("Sales Account")) heads.add("Sales Account")
            if (!heads.contains("Purchases Account")) heads.add("Purchases Account")

            // Trial Balance Calculation
            val rawTrial = heads.map { head ->
                var dr = 0.0
                var cr = 0.0
                ledgerEntries.filter { it.accountHead == head }.forEach { et ->
                    dr += et.debit
                    cr += et.credit
                }
                // include opening party balances directly as adjustments to ledger rows
                if (head.startsWith("Party: ")) {
                    val pName = head.removePrefix("Party: ")
                    val p = parties.find { it.name == pName }
                    if (p != null) {
                        if (p.balanceType == "DR") dr += p.openingBalance else cr += p.openingBalance
                    }
                }
                
                // net off rows for display
                val net = dr - cr
                val finalDr = if (net >= 0) net else 0.0
                val finalCr = if (net < 0) -net else 0.0

                TrialRow(head, finalDr, finalCr)
            }.filter { it.debit > 0 || it.credit > 0 }

            val totalDr = rawTrial.sumOf { it.debit }
            val totalCr = rawTrial.sumOf { it.credit }

            // P&L calculation
            val salesTotal = rawTrial.find { it.head == "Sales Account" }?.credit ?: 0.0
            val purchaseTotal = rawTrial.find { it.head == "Purchases Account" }?.debit ?: 0.0
            // Estimate standard closing stock based on remaining items setup or standard percentage
            val closingStock = products.sumOf { it.openingStock * it.purchaseRate }
            val grossProfit = salesTotal - purchaseTotal + closingStock
            val netProfit = grossProfit // for simplicity in standard retail accounting

            // Assets vs Liabilities
            val cashValue = rawTrial.find { it.head == "Cash" }?.debit ?: 0.0
            val bankValue = rawTrial.find { it.head == "Bank" }?.debit ?: 0.0
            val partyReceivables = rawTrial.filter { it.head.startsWith("Party: ") }.sumOf { it.debit }
            val totalAssets = cashValue + bankValue + partyReceivables + closingStock

            val partyPayables = rawTrial.filter { it.head.startsWith("Party: ") }.sumOf { it.credit }
            val cgstPayable = rawTrial.find { it.head == "CGST Payable" }?.credit ?: 0.0
            val sgstPayable = rawTrial.find { it.head == "SGST Payable" }?.credit ?: 0.0
            val igstPayable = rawTrial.find { it.head == "IGST Payable" }?.credit ?: 0.0
            val gstPaidInput = rawTrial.find { it.head == "CGST Payable" }?.debit ?: 0.0 +
                    (rawTrial.find { it.head == "SGST Payable" }?.debit ?: 0.0) +
                    (rawTrial.find { it.head == "IGST Payable" }?.debit ?: 0.0)

            val netGstLiability = (cgstPayable + sgstPayable + igstPayable) - gstPaidInput
            val totalLiabilities = partyPayables + (if (netGstLiability > 0) netGstLiability else 0.0)

            // GST logic
            val gstBlock = GstLiabilitySummary(
                outputTax = cgstPayable + sgstPayable + igstPayable,
                inputTaxCredit = gstPaidInput,
                netPayable = netGstLiability
            )

            TupleReport(
                TrialReportBlock(rawTrial, totalDr, totalCr),
                PLReportBlock(salesTotal, purchaseTotal, closingStock, grossProfit, netProfit),
                BSReportBlock(cashValue, bankValue, partyReceivables, closingStock, totalAssets, partyPayables, netGstLiability, totalLiabilities),
                gstBlock
            )
        }

        // Sub Reports display
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(activeReport, fontWeight = FontWeight.Bold, color = Colors.textPrimary) },
                    navigationIcon = {
                        IconButton(onClick = { activeReport = "MENU" }) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Colors.textPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Colors.cardBackground)
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Colors.background)
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                when (activeReport) {
                    "TRIAL" -> {
                        // Trial Balance layout
                        Text("Ledger balances as of today", color = Color.Gray, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(12.dp))

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .border(0.5.dp, Color.LightGray, RoundedCornerShape(4.dp))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFFAF9F9))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Account Node Name", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
                                Text("Debit DR (₹)", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text("Credit CR (₹)", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            }

                            LazyColumn(modifier = Modifier.weight(1f)) {
                                items(trialRows.rows) { row ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(row.head, fontSize = 11.sp, modifier = Modifier.weight(1.5f))
                                        Text(if (row.debit > 0) String.format("%.2f", row.debit) else "-", fontSize = 11.sp, modifier = Modifier.weight(1f))
                                        Text(if (row.credit > 0) String.format("%.2f", row.credit) else "-", fontSize = 11.sp, modifier = Modifier.weight(1f))
                                    }
                                    HorizontalDivider(color = Color(0xFFEAEAEA))
                                }
                            }

                            // Net Aggregate Total
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF1F1F1))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Aggregate Ledger Total:", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
                                Text(String.format("%.2f", trialRows.totalDebit), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text(String.format("%.2f", trialRows.totalCredit), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    "PL" -> {
                        val plState = rememberScrollState()

                        // Profit or Loss Statement
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(plState),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text("P&L Trading Account & net Margin", color = Color.Gray, fontSize = 12.sp)

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(0.5.dp, Color.LightGray, RoundedCornerShape(4.dp))
                                    .padding(12.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ReportLineItemRow("Gross Sales value (Credit):", Utils.formatIndianCurrency(plBlock.salesRevenue))
                                    ReportLineItemRow("Inward Purchases expense (Debit):", Utils.formatIndianCurrency(plBlock.purchasesCost))
                                    ReportLineItemRow("Estimated inventory closing stock value:", Utils.formatIndianCurrency(plBlock.closingStockValue))
                                    HorizontalDivider()

                                    val color = if (plBlock.grossProfit >= 0) SuccessGreen else DangerRed
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Gross Margin / Profit:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(Utils.formatIndianCurrency(plBlock.grossProfit), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = color)
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("NET BUSINESS PROFIT", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                                        Text("Profit transferrable to proprietor capital node", fontSize = 10.sp, color = Color.Gray)
                                    }
                                    Text(
                                        text = Utils.formatIndianCurrency(plBlock.netProfit),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = if (plBlock.netProfit >= 0) SuccessGreen else DangerRed
                                    )
                                }
                            }
                        }
                    }

                    "BALANCE" -> {
                        // Asset vs Liability
                        val bsScroll = rememberScrollState()

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(bsScroll),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text("Proprietorship Statement of Financial position", color = Color.Gray, fontSize = 12.sp)

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Assets Column
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .border(0.5.dp, Color.LightGray, RoundedCornerShape(4.dp))
                                        .padding(10.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("ASSETS", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                                        HorizontalDivider()
                                        ReportSummaryCell("Cash in hand:", Utils.formatIndianCurrency(bsBlock.cash))
                                        ReportSummaryCell("Bank Accounts:", Utils.formatIndianCurrency(bsBlock.bank))
                                        ReportSummaryCell("Sundry Debtors:", Utils.formatIndianCurrency(bsBlock.receivables))
                                        ReportSummaryCell("Closing stock value:", Utils.formatIndianCurrency(bsBlock.closingStock))
                                        HorizontalDivider()
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Total Assets:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text(Utils.formatIndianCurrency(bsBlock.totalAssets), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                }

                                // Liabilities Column
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .border(0.5.dp, Color.LightGray, RoundedCornerShape(4.dp))
                                        .padding(10.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("LIABILITIES", fontWeight = FontWeight.Bold, color = DangerRed, fontSize = 12.sp)
                                        HorizontalDivider()
                                        ReportSummaryCell("Sundry Creditors:", Utils.formatIndianCurrency(bsBlock.payables))
                                        ReportSummaryCell("Net GST Payable:", Utils.formatIndianCurrency(if (bsBlock.netGstPayable > 0) bsBlock.netGstPayable else 0.0))
                                        ReportSummaryCell("Owner's Equity:", Utils.formatIndianCurrency(bsBlock.totalAssets - bsBlock.payables - (if (bsBlock.netGstPayable > 0) bsBlock.netGstPayable else 0.0)))
                                        HorizontalDivider()
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Total Liabilities:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text(Utils.formatIndianCurrency(bsBlock.totalLiabilities + (bsBlock.totalAssets - bsBlock.payables - (if (bsBlock.netGstPayable > 0) bsBlock.netGstPayable else 0.0))), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "GST" -> {
                        // GST summary layout
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(0.5.dp, Color.LightGray, RoundedCornerShape(4.dp))
                                .padding(16.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("GST TRANSFERS SUMMARY", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                                HorizontalDivider()
                                ReportLineItemRow("Total Outward Tax Collected (Output Liability):", Utils.formatIndianCurrency(gstSummary.outputTax))
                                ReportLineItemRow("Total Inward Tax Paid (Input Tax Credit):", Utils.formatIndianCurrency(gstSummary.inputTaxCredit))
                                HorizontalDivider()

                                val color = if (gstSummary.netPayable >= 0) DangerRed else SuccessGreen
                                val label = if (gstSummary.netPayable >= 0) "Net GST Payable to Government:" else "ITC Carryover Balance (Credit Asset):"

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(Utils.formatIndianCurrency(Math.abs(gstSummary.netPayable)), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = color)
                                }
                            }
                        }
                    }

                    "RECEIVABLES" -> {
                        // Outstanding Receivables layout
                        Text("Client accounts with positive DR outstanding balances", color = Colors.textSecondary, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(12.dp))

                        val bMap = remember(parties, ledgerEntries) {
                            val balances = mutableMapOf<String, Double>()
                            parties.forEach { p ->
                                var bal = if (p.balanceType == "DR") p.openingBalance else -p.openingBalance
                                ledgerEntries.filter { it.accountHead == "Party: ${p.name}" }.forEach { entry ->
                                    bal += (entry.debit - entry.credit)
                                }
                                balances[p.id] = bal
                            }
                            balances
                        }
                        
                        val receivablesList = remember(parties, bMap) {
                            parties.filter { (bMap[it.id] ?: 0.0) > 0.0 }.map { it to (bMap[it.id] ?: 0.0) }.sortedByDescending { it.second }
                        }

                        if (receivablesList.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No outstanding receivables.", color = Colors.textSecondary, fontSize = 14.sp)
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                                items(receivablesList) { (party, balance) ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, Colors.border, RoundedCornerShape(12.dp)),
                                        colors = CardDefaults.cardColors(containerColor = Colors.cardBackground)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp).fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(party.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Colors.textPrimary)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text("Phone: ${party.phone}", fontSize = 11.sp, color = Colors.textSecondary)
                                            }
                                            Text(Utils.formatIndianCurrency(balance) + " DR", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DangerRed)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "PAYABLES" -> {
                        // Outstanding Payables layout
                        Text("Supplier accounts with credit (CR) outstanding balances to pay", color = Colors.textSecondary, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(12.dp))

                        val bMap = remember(parties, ledgerEntries) {
                            val balances = mutableMapOf<String, Double>()
                            parties.forEach { p ->
                                var bal = if (p.balanceType == "DR") p.openingBalance else -p.openingBalance
                                ledgerEntries.filter { it.accountHead == "Party: ${p.name}" }.forEach { entry ->
                                    bal += (entry.debit - entry.credit)
                                }
                                balances[p.id] = bal
                            }
                            balances
                        }

                        val payablesList = remember(parties, bMap) {
                            parties.filter { (bMap[it.id] ?: 0.0) < 0.0 }.map { it to Math.abs(bMap[it.id] ?: 0.0) }.sortedByDescending { it.second }
                        }

                        if (payablesList.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No outstanding payables.", color = Colors.textSecondary, fontSize = 14.sp)
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                                items(payablesList) { (party, balance) ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, Colors.border, RoundedCornerShape(12.dp)),
                                        colors = CardDefaults.cardColors(containerColor = Colors.cardBackground)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp).fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(party.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Colors.textPrimary)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text("Phone: ${party.phone}", fontSize = 11.sp, color = Colors.textSecondary)
                                            }
                                            Text(Utils.formatIndianCurrency(balance) + " CR", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = SuccessGreen)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReportMenuCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .border(1.dp, Colors.border, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Colors.cardBackground)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Colors.primary.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Colors.primary,
                        modifier = Modifier.padding(10.dp).size(24.dp)
                    )
                }
                Column {
                    Text(text = title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Colors.textPrimary)
                    Text(text = description, color = Colors.textSecondary, fontSize = 11.sp)
                }
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = Colors.textTertiary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
fun ReportLineItemRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.sp, color = Color.Gray)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF161616))
    }
}

@Composable
fun ReportSummaryCell(label: String, value: String) {
    Column {
        Text(label, fontSize = 10.sp, color = Color.Gray)
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
    }
}

data class TrialRow(val head: String, val debit: Double, val credit: Double)
data class TrialReportBlock(val rows: List<TrialRow>, val totalDebit: Double, val totalCredit: Double)
data class PLReportBlock(val salesRevenue: Double, val purchasesCost: Double, val closingStockValue: Double, val grossProfit: Double, val netProfit: Double)
data class BSReportBlock(val cash: Double, val bank: Double, val receivables: Double, val closingStock: Double, val totalAssets: Double, val payables: Double, val netGstPayable: Double, val totalLiabilities: Double)
data class GstLiabilitySummary(val outputTax: Double, val inputTaxCredit: Double, val netPayable: Double)
data class TupleReport(val trialRows: TrialReportBlock, val plBlock: PLReportBlock, val bsBlock: BSReportBlock, val gstSummary: GstLiabilitySummary)
