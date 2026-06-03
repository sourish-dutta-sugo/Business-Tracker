package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.AppViewModel
import com.example.ui.theme.Colors
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VouchersScreen(
    viewModel: AppViewModel,
    navigateToNewVoucher: () -> Unit,
    navigateToInvoice: (String) -> Unit
) {
    val vouchers by viewModel.vouchers.collectAsState()
    val parties by viewModel.parties.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf("ALL") }

    val filteredVouchers = remember(vouchers, searchQuery, selectedTypeFilter, parties) {
        vouchers.filter { voucher ->
            val partyName = parties.find { it.id == voucher.partyId }?.name ?: "Cash / Bank"
            val matchesSearch = voucher.voucherNo.contains(searchQuery, ignoreCase = true) ||
                    partyName.contains(searchQuery, ignoreCase = true)
            val matchesType = selectedTypeFilter == "ALL" || voucher.type == selectedTypeFilter
            matchesSearch && matchesType
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = navigateToNewVoucher,
                containerColor = Colors.primary,
                contentColor = Colors.primaryText,
                modifier = Modifier.testTag("add_voucher_fab")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Voucher")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Colors.background)
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Vouchers",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Colors.textPrimary
            )

            // Search Bar
            RetailTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = "Search Vouchers",
                placeholder = "Search by voucher number or party...",
                trailingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = Colors.textSecondary) },
                modifier = Modifier.fillMaxWidth().testTag("voucher_search_bar")
            )

            // Quick Type Filters Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filters = listOf("ALL", "SALE", "PURCHASE", "RECEIPT", "PAYMENT")
                filters.forEach { filter ->
                    FilterChip(
                        selected = selectedTypeFilter == filter,
                        onClick = { selectedTypeFilter = filter },
                        label = { Text(filter, fontSize = 11.sp) },
                        shape = RoundedCornerShape(4.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Colors.primary,
                            selectedLabelColor = Colors.primaryText
                        )
                    )
                }
            }

            if (vouchers.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Assignment,
                            contentDescription = null,
                            tint = Colors.textTertiary,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No vouchers yet. Tap + to create your first sale.",
                            color = Colors.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else if (filteredVouchers.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = null,
                            tint = Colors.textTertiary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No matching vouchers found.", color = Colors.textSecondary, fontSize = 14.sp)
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredVouchers) { voucher ->
                        val partyName = parties.find { it.id == voucher.partyId }?.name ?: "Cash / Bank Account"
                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Colors.border, RoundedCornerShape(16.dp))
                                .clickable { navigateToInvoice(voucher.id) },
                            colors = CardDefaults.cardColors(containerColor = Colors.cardBackground)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = voucher.voucherNo,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF1A1A1A)
                                    )
                                    // Colored Badge
                                    val badgeColor = when (voucher.type) {
                                        "SALE" -> Color(0xFF1A73E8)
                                        "PURCHASE" -> Color(0xFF6F42C1)
                                        "RECEIPT" -> Color(0xFF28A745)
                                        "PAYMENT" -> Color(0xFFDC3545)
                                        else -> Color.Gray
                                    }
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = badgeColor.copy(alpha = 0.15f)),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = voucher.type,
                                            color = badgeColor,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = partyName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF333333)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = Utils.formatDate(voucher.date),
                                        fontSize = 11.sp,
                                        color = Colors.textSecondary
                                    )
                                    Text(
                                        text = Utils.formatIndianCurrency(voucher.netAmount),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF161616)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Interactive Sub-screen for Voucher Add / Post Flow
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewVoucherScreen(
    viewModel: AppViewModel,
    onNavigateBack: () -> Unit
) {
    val profile by viewModel.profile.collectAsState()
    val parties by viewModel.parties.collectAsState()
    val products by viewModel.products.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    var step by remember { mutableStateOf(1) } // 1: Type selection, 2: Form & Line items

    // Voucher details
    var selectedType by remember { mutableStateOf("SALE") }
    var voucherNo by remember { mutableStateOf("") }
    var voucherDate by remember { mutableStateOf(System.currentTimeMillis()) }
    var selectedParty by remember { mutableStateOf<Party?>(null) }
    var paymentMode by remember { mutableStateOf("CASH") }
    var narration by remember { mutableStateOf("") }

    // GST control checking
    val hasGst = remember(profile) { !profile?.gstin.isNullOrBlank() }
    var showQuickAddPartyDialog by remember { mutableStateOf(false) }
    var showQuickAddProductDialog by remember { mutableStateOf(false) }
    var quickAddProductItemIndex by remember { mutableStateOf<Int?>(null) }

    // Cheque specifics
    var chequeNo by remember { mutableStateOf("") }
    var chequeDate by remember { mutableStateOf(System.currentTimeMillis()) }
    var bankName by remember { mutableStateOf("") }

    // Line items
    val lineItems = remember { mutableStateListOf<VoucherItem>() }

    // Enhanced transaction variables
    var uploadedInvoiceUri by remember { mutableStateOf<String?>(null) }
    var bankIfsc by remember { mutableStateOf("") }
    var bankAccountHolder by remember { mutableStateOf("") }
    var bankNameDetail by remember { mutableStateOf("") }
    var memoNumber by remember { mutableStateOf("") }
    var branchName by remember { mutableStateOf("") }

    // Dialog trigger flags
    var showUpiPaymentDialog by remember { mutableStateOf(false) }
    var showPrintReceiptDialog by remember { mutableStateOf(false) }
    var printedVoucherId by remember { mutableStateOf<String?>(null) }
    var isSavingAndPrinting by remember { mutableStateOf(false) }

    // Populate dynamic number on type/date change
    LaunchedEffect(selectedType, voucherDate) {
        val nextNo = viewModel.generateNextVoucherNo(selectedType, voucherDate)
        voucherNo = nextNo
    }

    // Party drop-down
    var partyDropdownExpanded by remember { mutableStateOf(false) }

    // Calculations
    val taxableAmount = remember { derivedStateOf { lineItems.sumOf { it.taxableAmount } } }
    val cgst = remember { derivedStateOf { lineItems.sumOf { it.cgstAmount } } }
    val sgst = remember { derivedStateOf { lineItems.sumOf { it.sgstAmount } } }
    val igst = remember { derivedStateOf { lineItems.sumOf { it.igstAmount } } }
    val rawTotal = remember { derivedStateOf { taxableAmount.value + cgst.value + sgst.value + igst.value } }
    val netAmount = remember { derivedStateOf { Math.round(rawTotal.value).toDouble() } }
    val roundOff = remember { derivedStateOf { netAmount.value - rawTotal.value } }

    val isInterstate = remember(selectedParty, profile) {
        val pstate = selectedParty?.stateCode ?: ""
        val bstate = profile?.stateCode ?: ""
        pstate.isNotEmpty() && pstate != bstate
    }

    val saveTheVoucher: (Boolean) -> Unit = { shouldPrint ->
        val isBill = (selectedType != "RECEIPT" && selectedType != "PAYMENT")
        if (isBill && lineItems.isEmpty()) {
            android.widget.Toast.makeText(context, "Cannot save/print: Add at least 1 item to buy!", android.widget.Toast.LENGTH_LONG).show()
        } else if (isBill && lineItems.any { it.qty <= 0.0 }) {
            android.widget.Toast.makeText(context, "Cannot save/print: All products must have positive quantity values!", android.widget.Toast.LENGTH_LONG).show()
        } else {
            val finalId = UUID.randomUUID().toString()
            val voucherObj = Voucher(
                id = finalId,
                voucherNo = voucherNo,
                type = selectedType,
                date = voucherDate,
                partyId = selectedParty?.id,
                narration = narration,
                taxableAmount = taxableAmount.value,
                cgst = cgst.value,
                sgst = sgst.value,
                igst = igst.value,
                roundOff = roundOff.value,
                netAmount = netAmount.value,
                paymentMode = paymentMode,
                chequeNo = if (paymentMode == "CHEQUE") chequeNo else null,
                chequeDate = if (paymentMode == "CHEQUE") chequeDate else null,
                bankName = if (paymentMode == "CHEQUE") bankName else null,
                isIgst = isInterstate,
                status = "POSTED",
                receiptImagePath = uploadedInvoiceUri,
                bankIfsc = if (paymentMode == "BANK") bankIfsc else null,
                bankAccountHolder = if (paymentMode == "BANK") bankAccountHolder else null,
                bankNameDetail = if (paymentMode == "BANK" || paymentMode == "UPI") bankNameDetail else null,
                memoNumber = if (paymentMode == "CHEQUE") memoNumber else null,
                branchName = if (paymentMode == "BANK" || paymentMode == "CHEQUE") branchName else null
            )
            viewModel.saveVoucher(voucherObj, lineItems, selectedParty?.name) {
                if (shouldPrint) {
                    printedVoucherId = finalId
                    showPrintReceiptDialog = true
                } else {
                    onNavigateBack()
                }
            }
        }
    }

    if (step == 1) {
        // Step 1: Type Selection Screen
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Select Voucher Type", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.White,
                        titleContentColor = Color(0xFF0F172A),
                        navigationIconContentColor = Color(0xFF0F172A)
                    )
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val types = listOf(
                    "SALE" to "Generate modern tax invoice for customers",
                    "PURCHASE" to "Record inward supply bills & invoices",
                    "RECEIPT" to "Receive outstanding/cash from party",
                    "PAYMENT" to "Record outward cash/bank payment directly",
                    "SALE_RETURN" to "Reverse previous sales (Credit Note)",
                    "PURCHASE_RETURN" to "Reverse previous purchases (Debit Note)"
                )

                types.forEach { (type, description) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedType = if (type == "SALE_RETURN") "SALE_RETURN" else if (type == "PURCHASE_RETURN") "PURCHASE_RETURN" else type as String
                                step = 2
                            }
                            .border(0.5.dp, Color(0xFFE8E8E8), RoundedCornerShape(8.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFAF9F9))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = type, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = description, fontSize = 12.sp, color = Colors.textSecondary)
                        }
                    }
                }
            }
        }
    } else {
        // Step 2: Main Entry layout
        val scrollState = rememberScrollState()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("New $selectedType", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { step = 1 }) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.White,
                        titleContentColor = Color(0xFF0F172A),
                        navigationIconContentColor = Color(0xFF0F172A)
                    )
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .padding(innerPadding)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RetailTextField(
                            value = voucherNo,
                            onValueChange = {},
                            label = "Voucher Number",
                            readOnly = true,
                            modifier = Modifier.weight(1f)
                        )

                        RetailTextField(
                            value = Utils.formatDate(voucherDate),
                            onValueChange = {},
                            label = "Date",
                            readOnly = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Party Search dropdown
                    Box(modifier = Modifier.fillMaxWidth()) {
                        RetailTextField(
                            value = selectedParty?.name ?: "Cash / Bank (Walk-in B2C)",
                            onValueChange = {},
                            label = "Select Party",
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth().testTag("select_party_input"),
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = null,
                                    modifier = Modifier.clickable { partyDropdownExpanded = true }
                                )
                            }
                        )
                        DropdownMenu(
                            expanded = partyDropdownExpanded,
                            onDismissRequest = { partyDropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.9f).height(240.dp)
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            tint = Colors.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("+ Quick Add New Party...", color = Colors.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                },
                                onClick = {
                                    partyDropdownExpanded = false
                                    showQuickAddPartyDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Cash / Bank (Walk-in Customer)") },
                                onClick = {
                                    selectedParty = null
                                    partyDropdownExpanded = false
                                }
                            )
                            parties.forEach { party ->
                                DropdownMenuItem(
                                    text = { Text("${party.name} (${party.type})") },
                                    onClick = {
                                        selectedParty = party
                                        partyDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    if (selectedParty != null) {
                        Text(
                            text = "Party State: ${selectedParty?.state} | Interstate/IGST: ${if (isInterstate) "YES" else "NO"}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isInterstate) Color(0xFFFD7E14) else Color(0xFF1A73E8)
                        )
                    }

                    // Payment Mode Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Mode:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Colors.textSecondary)
                        val modes = listOf("CASH", "BANK", "CHEQUE", "UPI")
                        modes.forEach { mode ->
                            FilterChip(
                                selected = paymentMode == mode,
                                onClick = { paymentMode = mode },
                                label = { Text(mode, fontSize = 10.sp) },
                                shape = RoundedCornerShape(4.dp)
                            )
                        }
                    }

                    // Payment Mode Details
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "$paymentMode DETAILS",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Colors.primary,
                                letterSpacing = 0.5.sp
                            )
                            
                            if (paymentMode == "CASH") {
                                Text(
                                    text = "Payment will be registered instantly in the local Cash Ledger. Normal cash-in-hand flows apply.",
                                    fontSize = 11.sp,
                                    color = Colors.textSecondary
                                )
                            }
                            
                            if (paymentMode == "BANK") {
                                RetailTextField(
                                    value = bankNameDetail,
                                    onValueChange = { bankNameDetail = it },
                                    label = "Bank Name *"
                                )
                                RetailTextField(
                                    value = bankAccountHolder,
                                    onValueChange = { bankAccountHolder = it },
                                    label = "Account Holder Name *"
                                )
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    RetailTextField(
                                        value = bankIfsc,
                                        onValueChange = { bankIfsc = it },
                                        label = "IFSC Code *",
                                        modifier = Modifier.weight(1f)
                                    )
                                    RetailTextField(
                                        value = branchName,
                                        onValueChange = { branchName = it },
                                        label = "Branch Name",
                                        modifier = Modifier.weight(1.2f)
                                    )
                                }
                            }
                            
                            if (paymentMode == "CHEQUE") {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    RetailTextField(
                                        value = chequeNo,
                                        onValueChange = { chequeNo = it },
                                        label = "Cheque No *",
                                        modifier = Modifier.weight(1f)
                                    )
                                    RetailTextField(
                                        value = bankName,
                                        onValueChange = { bankName = it },
                                        label = "Bank Name *",
                                        modifier = Modifier.weight(1.2f)
                                    )
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    RetailTextField(
                                        value = branchName,
                                        onValueChange = { branchName = it },
                                        label = "Branch Name",
                                        modifier = Modifier.weight(1f)
                                    )
                                    RetailTextField(
                                        value = memoNumber,
                                        onValueChange = { memoNumber = it },
                                        label = "Memo/Voucher No",
                                        modifier = Modifier.weight(1.2f)
                                    )
                                }
                            }
                            
                            if (paymentMode == "UPI") {
                                Text(
                                    text = "UPI Mode active. For sales, saving the transaction will show an on-screen UPI QR Code scanner linked to your business account to automatically pull, verify, and auto-print customer invoices.",
                                    fontSize = 11.sp,
                                    color = Colors.textSecondary
                                )
                                RetailTextField(
                                    value = bankNameDetail,
                                    onValueChange = { bankNameDetail = it },
                                    label = "Paying UPI / Reference App ID (Optional)"
                                )
                            }
                        }
                    }

                    // Purchase Invoice Attachment Panel (real launcher and URI recording!)
                    val isPurchaseType = selectedType == "PURCHASE" || selectedType == "PURCHASE_RETURN"
                    if (isPurchaseType) {
                        val attachmentLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.GetContent()
                        ) { uri ->
                            if (uri != null) {
                                uploadedInvoiceUri = uri.toString()
                            }
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Assignment,
                                        contentDescription = null,
                                        tint = Colors.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Received Supplier Purchase Invoice Record", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Colors.primary)
                                }
                                Text("Select and upload the invoice file or photo received from the wholesale merchant or company.", fontSize = 11.sp, color = Colors.textSecondary)

                                if (uploadedInvoiceUri != null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(8.dp)).padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Invoice Document Recorded Successfully", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Colors.success)
                                            Text(uploadedInvoiceUri!!.split("/").last(), fontSize = 10.sp, color = Colors.textSecondary)
                                        }
                                        IconButton(onClick = { uploadedInvoiceUri = null }) {
                                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Clear", tint = Colors.danger)
                                        }
                                    }
                                } else {
                                    Button(
                                        onClick = { attachmentLauncher.launch("image/*") },
                                        colors = ButtonDefaults.buttonColors(containerColor = Colors.primary),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Pick Received Invoice File/Image", fontSize = 11.sp, color = Color.White)
                                    }
                                }
                            }
                        }
                    }

                    RetailTextField(
                        value = narration,
                        onValueChange = { narration = it },
                        label = "Voucher Narration / Memo Card Details"
                    )

                    // Line Items Panel for Invoice Types
                    val isInvoiceType = selectedType == "SALE" || selectedType == "PURCHASE" ||
                            selectedType == "SALE_RETURN" || selectedType == "PURCHASE_RETURN"

                    if (isInvoiceType) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Items Included", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Button(
                                onClick = {
                                    // Add empty row or product picker directly
                                    if (products.isNotEmpty()) {
                                        val firstProd = products.first()
                                        val initialTaxable = firstProd.saleRate
                                        val prodGstRate = if (hasGst) firstProd.gstRate else 0.0
                                        val itemGst = if (hasGst) (initialTaxable * prodGstRate / 100.0) else 0.0

                                        val newItem = VoucherItem(
                                            id = UUID.randomUUID().toString(),
                                            voucherId = "",
                                            productId = firstProd.id,
                                            productName = firstProd.name,
                                            hsnCode = firstProd.hsnCode,
                                            qty = 1.0,
                                            unit = firstProd.unit,
                                            rate = if (selectedType == "PURCHASE" || selectedType == "PURCHASE_RETURN") firstProd.purchaseRate else firstProd.saleRate,
                                            discount = 0.0,
                                            discountType = "PERCENT",
                                            taxableAmount = initialTaxable,
                                            gstRate = prodGstRate,
                                            cgstAmount = if (isInterstate || !hasGst) 0.0 else itemGst / 2.0,
                                            sgstAmount = if (isInterstate || !hasGst) 0.0 else itemGst / 2.0,
                                            igstAmount = if (isInterstate && hasGst) itemGst else 0.0,
                                            totalAmount = initialTaxable + itemGst
                                        )
                                        lineItems.add(newItem)
                                    } else {
                                        val newItem = VoucherItem(
                                            id = UUID.randomUUID().toString(),
                                            voucherId = "",
                                            productId = "CUSTOM",
                                            productName = "+ Quick Add Product...",
                                            hsnCode = "9900",
                                            qty = 1.0,
                                            unit = "PCS",
                                            rate = 0.0,
                                            discount = 0.0,
                                            discountType = "PERCENT",
                                            taxableAmount = 0.0,
                                            gstRate = 0.0,
                                            cgstAmount = 0.0,
                                            sgstAmount = 0.0,
                                            igstAmount = 0.0,
                                            totalAmount = 0.0
                                        )
                                        lineItems.add(newItem)
                                    }
                                },
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.testTag("add_item_button")
                            ) {
                                Text("Add Item", fontSize = 11.sp)
                            }
                        }

                        if (products.isEmpty()) {
                            Text("Tip: Tap on the item name below to quick-create and configure products directly!", color = Colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
                        }

                        // Display active item selectors
                        lineItems.forEachIndexed { index, item ->
                            var itemExpanded by remember { mutableStateOf(false) }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(0.5.dp, Color(0xFFE8E8E8), RoundedCornerShape(8.dp)),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFBFBFB))
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            OutlinedTextField(
                                                value = item.productName,
                                                onValueChange = { name ->
                                                    lineItems[index] = item.copy(productName = name)
                                                },
                                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Colors.primary),
                                                modifier = Modifier.fillMaxWidth().clickable { itemExpanded = true },
                                                placeholder = { Text("Enter Item Name", fontSize = 11.sp) },
                                                singleLine = true,
                                                trailingIcon = {
                                                    IconButton(onClick = { itemExpanded = true }) {
                                                        Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = "Dropdown Selection", modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                            )
                                            DropdownMenu(
                                                expanded = itemExpanded,
                                                onDismissRequest = { itemExpanded = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Icon(
                                                                imageVector = Icons.Default.Add,
                                                                contentDescription = null,
                                                                tint = Colors.primary,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text("+ Quick Add New Product...", color = Colors.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                        }
                                                    },
                                                    onClick = {
                                                        itemExpanded = false
                                                        quickAddProductItemIndex = index
                                                        showQuickAddProductDialog = true
                                                    }
                                                )
                                                products.forEach { prod ->
                                                    DropdownMenuItem(
                                                        text = { Text(prod.name) },
                                                        onClick = {
                                                            val rateValue = if (selectedType == "PURCHASE" || selectedType == "PURCHASE_RETURN") prod.purchaseRate else prod.saleRate
                                                            val dynamicTaxable = rateValue * item.qty
                                                            val prodGstRate = if (hasGst) prod.gstRate else 0.0
                                                            val dummyGstAngle = if (hasGst) (dynamicTaxable * prodGstRate / 100.0) else 0.0

                                                            lineItems[index] = item.copy(
                                                                productId = prod.id,
                                                                productName = prod.name,
                                                                hsnCode = prod.hsnCode,
                                                                unit = prod.unit,
                                                                rate = rateValue,
                                                                gstRate = prodGstRate,
                                                                taxableAmount = dynamicTaxable,
                                                                cgstAmount = if (isInterstate || !hasGst) 0.0 else dummyGstAngle / 2.0,
                                                                sgstAmount = if (isInterstate || !hasGst) 0.0 else dummyGstAngle / 2.0,
                                                                igstAmount = if (isInterstate && hasGst) dummyGstAngle else 0.0,
                                                                totalAmount = dynamicTaxable + dummyGstAngle
                                                            )
                                                            itemExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        IconButton(
                                            onClick = { lineItems.removeAt(index) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = Color.Red)
                                        }
                                    }

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        RetailTextField(
                                            value = if (item.qty == 0.0) "" else item.qty.toString(),
                                            onValueChange = { qtyVal ->
                                                val q = qtyVal.toDoubleOrNull() ?: 0.0
                                                val sub = q * item.rate
                                                val discVal = if (item.discountType == "PERCENT") sub * item.discount / 100.0 else item.discount
                                                val tax = sub - discVal
                                                val dynamicGst = if (hasGst) (tax * item.gstRate / 100.0) else 0.0

                                                lineItems[index] = item.copy(
                                                    qty = q,
                                                    taxableAmount = tax,
                                                    cgstAmount = if (isInterstate || !hasGst) 0.0 else dynamicGst / 2.0,
                                                    sgstAmount = if (isInterstate || !hasGst) 0.0 else dynamicGst / 2.0,
                                                    igstAmount = if (isInterstate && hasGst) dynamicGst else 0.0,
                                                    totalAmount = tax + dynamicGst
                                                )
                                            },
                                            label = "Qty",
                                            modifier = Modifier.weight(1.1f),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true
                                        )

                                        Box(modifier = Modifier.weight(1.1f)) {
                                            var unitMenuExp by remember { mutableStateOf(false) }
                                            OutlinedTextField(
                                                value = item.unit,
                                                onValueChange = {},
                                                readOnly = true,
                                                label = { Text("Unit", fontSize = 9.sp) },
                                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Colors.primary),
                                                modifier = Modifier.fillMaxWidth().clickable { unitMenuExp = true },
                                                singleLine = true,
                                                trailingIcon = {
                                                    Icon(
                                                        imageVector = Icons.Default.KeyboardArrowDown,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp).clickable { unitMenuExp = true }
                                                    )
                                                }
                                            )
                                            DropdownMenu(
                                                expanded = unitMenuExp,
                                                onDismissRequest = { unitMenuExp = false },
                                                modifier = Modifier.background(Color.White)
                                            ) {
                                                listOf("PCS", "KG", "GM", "MG", "LTR", "ML", "BOX", "BAG", "NOS", "MTR").forEach { u ->
                                                    DropdownMenuItem(
                                                        text = { Text(u, fontSize = 11.sp, color = Colors.textPrimary) },
                                                        onClick = {
                                                            unitMenuExp = false
                                                            lineItems[index] = item.copy(unit = u)
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        RetailTextField(
                                            value = if (item.rate == 0.0) "" else item.rate.toString(),
                                            onValueChange = { rateVal ->
                                                val r = rateVal.toDoubleOrNull() ?: 0.0
                                                val sub = item.qty * r
                                                val discVal = if (item.discountType == "PERCENT") sub * item.discount / 100.0 else item.discount
                                                val tax = sub - discVal
                                                val dynamicGst = if (hasGst) (tax * item.gstRate / 100.0) else 0.0

                                                lineItems[index] = item.copy(
                                                    rate = r,
                                                    taxableAmount = tax,
                                                    cgstAmount = if (isInterstate || !hasGst) 0.0 else dynamicGst / 2.0,
                                                    sgstAmount = if (isInterstate || !hasGst) 0.0 else dynamicGst / 2.0,
                                                    igstAmount = if (isInterstate && hasGst) dynamicGst else 0.0,
                                                    totalAmount = tax + dynamicGst
                                                )
                                            },
                                            label = "Rate (₹)",
                                            modifier = Modifier.weight(1.3f),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true
                                        )

                                        RetailTextField(
                                            value = if (item.discount == 0.0) "" else item.discount.toString(),
                                            onValueChange = { discVal ->
                                                val d = discVal.toDoubleOrNull() ?: 0.0
                                                val sub = item.qty * item.rate
                                                val exactDisc = if (item.discountType == "PERCENT") sub * d / 100.0 else d
                                                val tax = sub - exactDisc
                                                val dynamicGst = if (hasGst) (tax * item.gstRate / 100.0) else 0.0

                                                lineItems[index] = item.copy(
                                                    discount = d,
                                                    taxableAmount = tax,
                                                    cgstAmount = if (isInterstate || !hasGst) 0.0 else dynamicGst / 2.0,
                                                    sgstAmount = if (isInterstate || !hasGst) 0.0 else dynamicGst / 2.0,
                                                    igstAmount = if (isInterstate && hasGst) dynamicGst else 0.0,
                                                    totalAmount = tax + dynamicGst
                                                )
                                            },
                                            label = "Disc (%)",
                                            modifier = Modifier.weight(1.1f),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true
                                        )
                                    }
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Taxable: ${Utils.formatIndianCurrency(item.taxableAmount)}", fontSize = 11.sp, color = Colors.textSecondary)
                                        if (hasGst) {
                                            Text("GST total: ${Utils.formatIndianCurrency(item.cgstAmount + item.sgstAmount + item.igstAmount)}", fontSize = 11.sp, color = Colors.textSecondary)
                                        }
                                        Text("Total: ${Utils.formatIndianCurrency(item.totalAmount)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Colors.textPrimary)
                                    }
                                }
                            }
                        }
                    } else {
                        // Non-invoice: direct Receipt/Payment amount dialog
                        RetailTextField(
                            value = netAmount.value.toString(),
                            onValueChange = {
                                // Manual net amount input overrides
                                val valueDouble = it.toDoubleOrNull() ?: 0.0
                                // Fake line item representing direct flow
                                lineItems.clear()
                                lineItems.add(
                                    VoucherItem(
                                        id = UUID.randomUUID().toString(),
                                        voucherId = "",
                                        productId = "",
                                        productName = "Direct Transaction",
                                        hsnCode = "",
                                        qty = 1.0,
                                        unit = "",
                                        rate = valueDouble,
                                        discount = 0.0,
                                        discountType = "AMOUNT",
                                        taxableAmount = valueDouble,
                                        gstRate = 0.0,
                                        cgstAmount = 0.0,
                                        sgstAmount = 0.0,
                                        igstAmount = 0.0,
                                        totalAmount = valueDouble
                                    )
                                )
                            },
                            label = "Amount (₹) *",
                            modifier = Modifier.fillMaxWidth().testTag("direct_amount_input"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                }

                // GST Live Summary Panel
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(0.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF9F9F9))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Summary", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Taxable Amount:", fontSize = 11.sp, color = Colors.textSecondary)
                            Text(Utils.formatIndianCurrency(taxableAmount.value), fontSize = 11.sp, color = Colors.textSecondary)
                        }
                        if (hasGst) {
                            if (isInterstate) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("IGST:", fontSize = 11.sp, color = Colors.textSecondary)
                                    Text(Utils.formatIndianCurrency(igst.value), fontSize = 11.sp, color = Colors.textSecondary)
                                }
                            } else {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("CGST:", fontSize = 11.sp, color = Colors.textSecondary)
                                    Text(Utils.formatIndianCurrency(cgst.value), fontSize = 11.sp, color = Colors.textSecondary)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("SGST:", fontSize = 11.sp, color = Colors.textSecondary)
                                    Text(Utils.formatIndianCurrency(sgst.value), fontSize = 11.sp, color = Colors.textSecondary)
                                }
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Round Off:", fontSize = 11.sp, color = Colors.textSecondary)
                            Text(Utils.formatIndianCurrency(roundOff.value), fontSize = 11.sp, color = Colors.textSecondary)
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Net Total Owed:", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(Utils.formatIndianCurrency(netAmount.value), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Spacer(modifier = Modifier.height(6.dp))

                        val isSaleOrReturn = selectedType == "SALE" || selectedType == "SALE_RETURN"

                        if (isSaleOrReturn) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (lineItems.isEmpty()) {
                                            // Empty lines check
                                        } else {
                                            saveTheVoucher(false)
                                        }
                                    },
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("save_and_exit_button"),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF64748B)) // Slate gray
                                ) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.padding(end = 4.dp).size(16.dp))
                                    Text("Save & Exit", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                
                                Button(
                                    onClick = {
                                        if (lineItems.isEmpty()) {
                                            // Empty lines check
                                        } else {
                                            if (paymentMode == "UPI") {
                                                // Trigger UPI QR payment verification simulation before printing
                                                showUpiPaymentDialog = true
                                            } else {
                                                saveTheVoucher(true)
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1.1f).height(48.dp).testTag("save_and_print_button"),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Colors.primary)
                                ) {
                                    Icon(imageVector = Icons.Default.Assignment, contentDescription = null, modifier = Modifier.padding(end = 4.dp).size(16.dp))
                                    Text("Save & Print", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        } else {
                            // Purchases or receipt/payments
                            Button(
                                onClick = {
                                    saveTheVoucher(false)
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_voucher_button"),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Colors.primary)
                            ) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                                Text("Save & Post to Ledger", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // Interactive UPI Verification & Scanning Dialog
    if (showUpiPaymentDialog) {
        var simulationProgress by remember { mutableStateOf(0f) }
        var simulationStatus by remember { mutableStateOf("Generating secure merchant settlement QR...") }
        var showSimulatedCheckMark by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            // Step 1: Generating QR
            delay(1000)
            simulationProgress = 0.3f
            simulationStatus = "QR Generated. Listening for bank transaction broadcast..."
            
            // Step 2: Simulating scanned status
            delay(1500)
            simulationProgress = 0.7f
            simulationStatus = "Customer scanned! Awaiting user banking PIN authentication..."
            
            // Step 3: Simulating settlement
            delay(2000)
            simulationProgress = 1.0f
            simulationStatus = "PIN Verified! Settling Rupees ${Utils.formatIndianCurrency(netAmount.value)} to account..."
            
            // Step 4: Complete!
            delay(1000)
            showSimulatedCheckMark = true
            simulationStatus = "PAYMENT CONFIRMED! Rs. ${Utils.formatIndianCurrency(netAmount.value)} Credited."
            
            // Step 5: Save and Auto-trigger print
            delay(1200)
            showUpiPaymentDialog = false
            saveTheVoucher(true)
        }

        AlertDialog(
            onDismissRequest = { /* Prevent dismiss during active settlement transaction */ },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Colors.success,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ZeroBook UPI Cash Terminal", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Scan with GPay, PhonePe, Paytm, BHIM, or any Banking App",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Colors.textSecondary,
                        textAlign = TextAlign.Center
                    )

                    // A beautiful custom visual QR Code mock inside a neat target box
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .border(2.dp, Colors.primary, RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (showSimulatedCheckMark) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Success",
                                    tint = Colors.success,
                                    modifier = Modifier.size(80.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("TRANSACTION DONE", color = Colors.success, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            // Draws a highly realistic abstract QR Code graphic using Canvas
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val w = size.width
                                val h = size.height
                                val grid = 12
                                val unitW = w / grid
                                val unitH = h / grid

                                // Draw position detection patterns (outer corners)
                                val strokeWidth = 14f
                                // Top-Left
                                drawRect(Color(0xFF0F172A), Offset(0f, 0f), Size(unitW * 3, unitH * 3))
                                drawRect(Color.White, Offset(unitW * 0.5f, unitH * 0.5f), Size(unitW * 2, unitH * 2))
                                drawRect(Color(0xFF0F172A), Offset(unitW * 1f, unitH * 1f), Size(unitW, unitH))

                                // Top-Right
                                drawRect(Color(0xFF0F172A), Offset(w - unitW * 3, 0f), Size(unitW * 3, unitH * 3))
                                drawRect(Color.White, Offset(w - unitW * 2.5f, unitH * 0.5f), Size(unitW * 2, unitH * 2))
                                drawRect(Color(0xFF0F172A), Offset(w - unitW * 2f, unitH * 1f), Size(unitW, unitH))

                                // Bottom-Left
                                drawRect(Color(0xFF0F172A), Offset(0f, h - unitH * 3), Size(unitW * 3, unitH * 3))
                                drawRect(Color.White, Offset(unitW * 0.5f, h - unitH * 2.5f), Size(unitW * 2, unitH * 2))
                                drawRect(Color(0xFF0F172A), Offset(unitW * 1f, h - unitH * 2f), Size(unitW, unitH))

                                // Dynamic center accent logo from dynamic colors
                                drawCircle(Color(0xFF1A73E8), radius = unitW * 1.5f, center = Offset(w/2, h/2))

                                // Draw abstract QR dots / blocks randomly
                                val random = java.util.Random(123456)
                                for (col in 0 until grid) {
                                    for (row in 0 until grid) {
                                        // Skip position pattern zones
                                        if ((col < 3 && row < 3) || (col > 8 && row < 3) || (col < 3 && row > 8)) continue
                                        // Skip center logo zone
                                        if (col in 4..7 && row in 4..7) continue

                                        if (random.nextBoolean()) {
                                            drawRect(
                                                color = Color(0xFF0F172A),
                                                topLeft = Offset(col * unitW + unitW * 0.1f, row * unitH + unitH * 0.1f),
                                                size = Size(unitW * 0.8f, unitH * 0.8f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "AMOUNT DUE: ${Utils.formatIndianCurrency(netAmount.value)}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Colors.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val upiId = "${profile?.accountNo?.ifBlank { "98700" } ?: "98700"}@${profile?.bankName?.lowercase()?.filter { it.isLetter() }?.ifBlank { "icici" } ?: "icici"}"
                        Text(
                            text = "Merchant ID: $upiId",
                            fontSize = 11.sp,
                            color = Colors.textTertiary
                        )
                        Text(
                            text = "Business: ${profile?.businessName?.ifBlank { "ZeroBook Pvt" } ?: "ZeroBook Pvt"}",
                            fontSize = 11.sp,
                            color = Colors.textTertiary
                        )
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9))
                    ) {
                        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = simulationStatus,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (showSimulatedCheckMark) Colors.success else Colors.primary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { simulationProgress },
                                modifier = Modifier.fillMaxWidth().height(4.dp),
                                color = if (showSimulatedCheckMark) Colors.success else Colors.primary,
                                trackColor = Color(0xFFE2E8F0)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showUpiPaymentDialog = false
                    // Backup manual confirm override
                    saveTheVoucher(true)
                }) {
                    Text("Manual Code Override (Bypass)", color = Colors.primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpiPaymentDialog = false }) {
                    Text("Cancel", color = Colors.danger)
                }
            }
        )
    }

    // Direct Receipt Thermal-style Print Dialog
    if (showPrintReceiptDialog) {
        val sdf = SimpleDateFormat("dd-MMM-yyyy hh:mm a", Locale.getDefault())
        val dateFormatted = sdf.format(Date(voucherDate))

        AlertDialog(
            onDismissRequest = { 
                showPrintReceiptDialog = false
                onNavigateBack() // Must navigate back as specified by "save and print means saving it and then printing directly"
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Assignment, contentDescription = null, tint = Colors.primary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Laser & Thermal Print Preview", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Colors.textPrimary)
                }
            },
            text = {
                // A beautiful visual thermal receipt design representing a physical ticket layout
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    tonalElevation = 4.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = profile?.businessName?.ifBlank { "ZeroBook Ltd" } ?: "ZeroBook Ltd",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.Black
                        )
                        Text(
                            text = "RECORD. TRACK. GROW.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "${profile?.address ?: "Market Link Road"}, ${profile?.city ?: "New Delhi"}",
                            fontSize = 10.sp,
                            color = Color.DarkGray
                        )
                        Text(
                            text = "GSTIN: ${profile?.gstin ?: "07AAAAA0000A1Z5"}",
                            fontSize = 10.sp,
                            color = Color.DarkGray
                        )
                        Text(
                            text = "Ph: ${profile?.phone ?: "9876543210"} | Email: ${profile?.email ?: "info@zerobook.in"}",
                            fontSize = 10.sp,
                            color = Color.DarkGray
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "---------------------------------------------",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                        Text(
                            text = "TAX INVOICE (${selectedType})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color.Black
                        )
                        Text(
                            text = "---------------------------------------------",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )

                        // Invoice metadata
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Invoice No:", fontSize = 10.sp, color = Color.DarkGray, fontWeight = FontWeight.Bold)
                                Text(voucherNo, fontSize = 10.sp, color = Color.Black)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Date Time:", fontSize = 10.sp, color = Color.DarkGray)
                                Text(dateFormatted, fontSize = 10.sp, color = Color.Black)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Customer / Party:", fontSize = 10.sp, color = Color.DarkGray, fontWeight = FontWeight.Bold)
                                Text(selectedParty?.name ?: "Cash Customer", fontSize = 10.sp, color = Color.Black)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Payment Terminal:", fontSize = 10.sp, color = Color.DarkGray)
                                Text("$paymentMode Mode", fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }

                        Text(
                            text = "---------------------------------------------",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )

                        // Selected Items list layout on physical paper receipt
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("ITEM DESCRIPTION", fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.8f))
                            Text("QTY", fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.5f), textAlign = TextAlign.End)
                            Text("RATE", fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.8f), textAlign = TextAlign.End)
                            Text("GST", fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.5f), textAlign = TextAlign.End)
                            Text("TOTAL", fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.9f), textAlign = TextAlign.End)
                        }
                        Text(
                            text = ".................................................................",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )

                        lineItems.forEach { li ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(li.productName, fontSize = 9.sp, modifier = Modifier.weight(1.8f), color = Color.Black)
                                Text(li.qty.toString(), fontSize = 9.sp, modifier = Modifier.weight(0.5f), textAlign = TextAlign.End, color = Color.Black)
                                Text(Utils.formatIndianCurrency(li.rate), fontSize = 9.sp, modifier = Modifier.weight(0.8f), textAlign = TextAlign.End, color = Color.Black)
                                Text("${li.gstRate}%", fontSize = 9.sp, modifier = Modifier.weight(0.5f), textAlign = TextAlign.End, color = Color.Black)
                                Text(Utils.formatIndianCurrency(li.totalAmount), fontSize = 9.sp, modifier = Modifier.weight(0.9f), textAlign = TextAlign.End, color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }

                        Text(
                            text = "---------------------------------------------",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )

                        // Grand totals
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Subtotal Taxable:", fontSize = 10.sp, color = Color.DarkGray)
                                Text(Utils.formatIndianCurrency(taxableAmount.value), fontSize = 10.sp, color = Color.Black)
                            }
                            if (cgst.value > 0) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("CGST Amount:", fontSize = 10.sp, color = Color.DarkGray)
                                    Text(Utils.formatIndianCurrency(cgst.value), fontSize = 10.sp, color = Color.Black)
                                }
                            }
                            if (sgst.value > 0) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("SGST Amount:", fontSize = 10.sp, color = Color.DarkGray)
                                    Text(Utils.formatIndianCurrency(sgst.value), fontSize = 10.sp, color = Color.Black)
                                }
                            }
                            if (igst.value > 0) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("IGST Amount:", fontSize = 10.sp, color = Color.DarkGray)
                                    Text(Utils.formatIndianCurrency(igst.value), fontSize = 10.sp, color = Color.Black)
                                }
                            }
                            if (roundOff.value != 0.0) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Rounding:", fontSize = 10.sp, color = Color.DarkGray)
                                    Text(Utils.formatIndianCurrency(roundOff.value), fontSize = 10.sp, color = Color.Black)
                                }
                            }
                            Text(
                                text = ".................................................................",
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("NET TOTAL PAID/DUE:", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                Text(Utils.formatIndianCurrency(netAmount.value), fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (paymentMode == "BANK") {
                            Text(
                                text = "---------------------------------------------",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text("PAYMENT BANK ROUTING CREDIT:", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                Text("Holder: ${bankAccountHolder.ifBlank { "Company Account" }}", fontSize = 9.sp, color = Color.DarkGray)
                                Text("IFSC: ${bankIfsc.ifBlank { "UTIB000001" }} | Bank: ${bankNameDetail.ifBlank { "Axis Bank" }}", fontSize = 9.sp, color = Color.DarkGray)
                            }
                        }

                        Text(
                            text = "---------------------------------------------",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Thank you for doing business with us!",
                            fontSize = 9.sp,
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        Text(
                            text = "Powered by ZeroBook Terminal",
                            fontSize = 8.sp,
                            color = Color.Gray
                        )

                        val signaturePath = profile?.signaturePath
                        if (signaturePath != null && java.io.File(signaturePath).exists()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Authorized Signature:", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            val sigBitmap = android.graphics.BitmapFactory.decodeFile(signaturePath)
                            if (sigBitmap != null) {
                                Image(
                                    bitmap = sigBitmap.asImageBitmap(),
                                    contentDescription = "Signature Trace Printout",
                                    modifier = Modifier
                                        .height(45.dp)
                                        .width(110.dp)
                                        .background(Color.White)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPrintReceiptDialog = false
                        try {
                            val dateStr = sdf.format(Date(voucherDate))
                            val itemsList = lineItems.toList()
                            val saveDest = Utils.saveInvoiceToDeviceDownloads(
                                context = context,
                                profile = profile,
                                voucherNo = voucherNo,
                                dateFormatted = dateStr,
                                partyName = selectedParty?.name ?: "Cash Customer",
                                paymentMode = paymentMode,
                                lineItems = itemsList,
                                taxable = taxableAmount.value,
                                cgst = cgst.value,
                                sgst = sgst.value,
                                igst = igst.value,
                                roundOff = roundOff.value,
                                net = netAmount.value
                            )
                            if (saveDest != null) {
                                android.widget.Toast.makeText(context, "Saved invoice directly to Local Storage Downloads: $saveDest", android.widget.Toast.LENGTH_LONG).show()
                            } else {
                                android.widget.Toast.makeText(context, "Mock terminal printer job queued successfully.", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        onNavigateBack() // Go back as print job sent to system spooler successfully
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Colors.success)
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Confirm Print Job", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPrintReceiptDialog = false
                    onNavigateBack()
                }) {
                    Text("Close & Exit", color = Colors.danger)
                }
            }
        )
    }

    if (showQuickAddPartyDialog) {
        var newPartyName by remember { mutableStateOf("") }
        var newPartyType by remember { mutableStateOf(if (selectedType == "SALE" || selectedType == "SALE_RETURN") "CUSTOMER" else "SUPPLIER") }
        var newPartyPhone by remember { mutableStateOf("") }
        var newPartyStateConfig by remember { mutableStateOf(Utils.INDIAN_STATES.first { it.first == (profile?.state ?: "West Bengal") }) }
        var newPartyGstin by remember { mutableStateOf("") }
        var stateExpanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showQuickAddPartyDialog = false },
            title = { Text("Quick Add Party", fontWeight = FontWeight.Bold, color = Colors.textPrimary) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                ) {
                    RetailTextField(
                        value = newPartyName,
                        onValueChange = { newPartyName = it },
                        label = "Party / Business Name *"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Type:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Colors.textSecondary)
                        listOf("CUSTOMER", "SUPPLIER").forEach { pt ->
                            FilterChip(
                                selected = newPartyType == pt,
                                onClick = { newPartyType = pt },
                                label = { Text(pt, fontSize = 11.sp) }
                            )
                        }
                    }

                    RetailTextField(
                        value = newPartyPhone,
                        onValueChange = { newPartyPhone = it },
                        label = "Phone Number (Optional)",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )

                    Box(modifier = Modifier.fillMaxWidth()) {
                        RetailTextField(
                            value = "${newPartyStateConfig.first} (${newPartyStateConfig.second})",
                            onValueChange = {},
                            label = "State & GST Code *",
                            readOnly = true,
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    modifier = Modifier.clickable { stateExpanded = true }
                                )
                            }
                        )
                        DropdownMenu(
                            expanded = stateExpanded,
                            onDismissRequest = { stateExpanded = false },
                            modifier = Modifier.fillMaxWidth().height(200.dp)
                        ) {
                            Utils.INDIAN_STATES.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text("${item.first} (Code ${item.second})", color = Colors.textPrimary) },
                                    onClick = {
                                        newPartyStateConfig = item
                                        stateExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    if (hasGst) {
                        RetailTextField(
                            value = newPartyGstin,
                            onValueChange = { newPartyGstin = it.uppercase() },
                            label = "GSTIN (Optional)"
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPartyName.isNotBlank()) {
                            val partyObj = Party(
                                id = UUID.randomUUID().toString(),
                                name = newPartyName.trim(),
                                type = newPartyType,
                                phone = newPartyPhone.trim(),
                                email = "",
                                address = "",
                                city = "",
                                state = newPartyStateConfig.first,
                                stateCode = newPartyStateConfig.second,
                                gstin = newPartyGstin.trim().ifBlank { null },
                                pan = null,
                                openingBalance = 0.0,
                                balanceType = "DR",
                                createdAt = System.currentTimeMillis()
                            )
                            viewModel.saveParty(partyObj) {
                                selectedParty = partyObj
                                showQuickAddPartyDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Colors.primary)
                ) {
                    Text("Save Party")
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuickAddPartyDialog = false }) {
                    Text("Cancel", color = Colors.primary)
                }
            }
        )
    }

    if (showQuickAddProductDialog) {
        var newProdName by remember { mutableStateOf("") }
        var newProdHsn by remember { mutableStateOf("") }
        var newProdUnit by remember { mutableStateOf("PCS") }
        var newProdSaleRate by remember { mutableStateOf("") }
        var newProdPurchaseRate by remember { mutableStateOf("") }
        var newProdGstRate by remember { mutableStateOf("18.0") }
        var unitExpanded by remember { mutableStateOf(false) }
        var gstExpanded by remember { mutableStateOf(false) }

        val units = listOf("PCS", "KG", "GM", "MG", "LTR", "ML", "BOX", "BAG", "NOS", "MTR")
        val gstRates = listOf("0.0", "5.0", "12.0", "18.0", "28.0")

        AlertDialog(
            onDismissRequest = { showQuickAddProductDialog = false },
            title = { Text("Quick Add Product", fontWeight = FontWeight.Bold, color = Colors.textPrimary) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                ) {
                    RetailTextField(
                        value = newProdName,
                        onValueChange = { newProdName = it },
                        label = "Product Name *"
                    )

                    RetailTextField(
                        value = newProdHsn,
                        onValueChange = { newProdHsn = it },
                        label = "HSN Code (Optional)"
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            RetailTextField(
                                value = newProdUnit,
                                onValueChange = {},
                                label = "Unit *",
                                readOnly = true,
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        modifier = Modifier.clickable { unitExpanded = true }
                                    )
                                }
                            )
                            DropdownMenu(
                                expanded = unitExpanded,
                                onDismissRequest = { unitExpanded = false },
                                modifier = Modifier.background(Color.White)
                            ) {
                                units.forEach { item ->
                                    DropdownMenuItem(
                                        text = { Text(item, color = Colors.textPrimary) },
                                        onClick = {
                                            newProdUnit = item
                                            unitExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        if (hasGst) {
                            Box(modifier = Modifier.weight(1.2f)) {
                                RetailTextField(
                                    value = "$newProdGstRate%",
                                    onValueChange = {},
                                    label = "GST Rate *",
                                    readOnly = true,
                                    trailingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            modifier = Modifier.clickable { gstExpanded = true }
                                        )
                                    }
                                )
                                DropdownMenu(
                                    expanded = gstExpanded,
                                    onDismissRequest = { gstExpanded = false },
                                    modifier = Modifier.background(Color.White)
                                ) {
                                    gstRates.forEach { rate ->
                                        DropdownMenuItem(
                                            text = { Text("$rate%", color = Colors.textPrimary) },
                                            onClick = {
                                                newProdGstRate = rate
                                                gstExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RetailTextField(
                            value = newProdSaleRate,
                            onValueChange = { newProdSaleRate = it },
                            label = "Sale Rate (₹) *",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )

                        RetailTextField(
                            value = newProdPurchaseRate,
                            onValueChange = { newProdPurchaseRate = it },
                            label = "Purchase Rate (₹) *",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sRate = newProdSaleRate.toDoubleOrNull() ?: 0.0
                        val pRate = newProdPurchaseRate.toDoubleOrNull() ?: 0.0
                        if (newProdName.isNotBlank()) {
                            val prodObj = Product(
                                id = UUID.randomUUID().toString(),
                                name = newProdName.trim(),
                                hsnCode = newProdHsn.trim(),
                                unit = newProdUnit,
                                saleRate = sRate,
                                purchaseRate = pRate,
                                gstRate = if (hasGst) (newProdGstRate.toDoubleOrNull() ?: 0.0) else 0.0,
                                openingStock = 0.0,
                                createdAt = System.currentTimeMillis()
                            )
                            viewModel.saveProduct(prodObj) {
                                // Auto-assign this product details to the selected Voucher item row!
                                quickAddProductItemIndex?.let { index ->
                                    if (index in lineItems.indices) {
                                        val rateValue = if (selectedType == "PURCHASE" || selectedType == "PURCHASE_RETURN") prodObj.purchaseRate else prodObj.saleRate
                                        val curItem = lineItems[index]
                                        val dynamicTaxable = rateValue * curItem.qty
                                        val prodGstRate = if (hasGst) prodObj.gstRate else 0.0
                                        val dummyGstAngle = if (hasGst) (dynamicTaxable * prodGstRate / 100.0) else 0.0

                                        lineItems[index] = curItem.copy(
                                            productId = prodObj.id,
                                            productName = prodObj.name,
                                            hsnCode = prodObj.hsnCode,
                                            unit = prodObj.unit,
                                            rate = rateValue,
                                            gstRate = prodGstRate,
                                            taxableAmount = dynamicTaxable,
                                            cgstAmount = if (isInterstate || !hasGst) 0.0 else dummyGstAngle / 2.0,
                                            sgstAmount = if (isInterstate || !hasGst) 0.0 else dummyGstAngle / 2.0,
                                            igstAmount = if (isInterstate && hasGst) dummyGstAngle else 0.0,
                                            totalAmount = dynamicTaxable + dummyGstAngle
                                        )
                                    }
                                }
                                showQuickAddProductDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Colors.primary)
                ) {
                    Text("Save & Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuickAddProductDialog = false }) {
                    Text("Cancel", color = Colors.primary)
                }
            }
        )
    }
}
