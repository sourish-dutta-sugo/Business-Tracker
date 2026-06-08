package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.AppViewModel
import com.example.ui.theme.*
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartiesScreen(
    viewModel: AppViewModel,
    isDesktop: Boolean = false,
    onPartySelected: (String) -> Unit
) {
    val parties by viewModel.parties.collectAsState()
    val ledgerEntries by viewModel.ledgerEntries.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf("ALL") } // "ALL", "CUSTOMER", "SUPPLIER"
    var showAddPartyForm by remember { mutableStateOf(false) }

    // Computes dynamic balances per party
    val partyBalances = remember(parties, ledgerEntries) {
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

    val filteredParties = remember(parties, searchQuery, selectedTypeFilter) {
        parties.filter { p ->
            val matchesSearch = p.name.contains(searchQuery, ignoreCase = true) ||
                    (p.phone.contains(searchQuery)) ||
                    (p.gstin?.contains(searchQuery, ignoreCase = true) ?: false)
            val matchesType = selectedTypeFilter == "ALL" || p.type == selectedTypeFilter || p.type == "BOTH"
            matchesSearch && matchesType
        }
    }

    var selectedPartyId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(filteredParties, isDesktop) {
        if (isDesktop && selectedPartyId == null && filteredParties.isNotEmpty()) {
            selectedPartyId = filteredParties.first().id
        }
    }

    if (isDesktop) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Master Pane (Parties List)
            Box(modifier = Modifier.width(360.dp).fillMaxHeight()) {
                Scaffold(
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = { showAddPartyForm = true },
                            containerColor = AppColors.primary,
                            contentColor = AppColors.textOnPrimary,
                            modifier = Modifier.testTag("add_party_fab")
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Add Party")
                        }
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(AppColors.screenBg)
                            .padding(innerPadding)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Parties",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.textPrimary
                        )

                        // Search Box using RetailTextField
                        RetailTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = "Search Parties",
                            placeholder = "Search by name...",
                            trailingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = AppColors.textSecondary) },
                            modifier = Modifier.fillMaxWidth().testTag("party_search_bar")
                        )

                        // Filter Buttons Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val filters = listOf("ALL", "CUSTOMER", "SUPPLIER")
                            filters.forEach { filter ->
                                FilterChip(
                                    selected = selectedTypeFilter == filter,
                                    onClick = { selectedTypeFilter = filter },
                                    label = { Text(filter, fontSize = 9.sp) },
                                    shape = RoundedCornerShape(4.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AppColors.primary,
                                        selectedLabelColor = AppColors.textOnPrimary
                                    )
                                )
                            }
                        }

                        if (filteredParties.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No matching parties found.", color = AppColors.textSecondary, fontSize = 14.sp)
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(filteredParties) { party ->
                                    val currentBal = partyBalances[party.id] ?: 0.0
                                    val isSelected = selectedPartyId == party.id

                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(
                                                1.dp,
                                                if (isSelected) AppColors.primary else AppColors.border,
                                                RoundedCornerShape(16.dp)
                                            )
                                            .clickable { selectedPartyId = party.id; showAddPartyForm = false },
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) AppColors.primary.copy(alpha = 0.08f) else AppColors.cardBg
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1.2f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = party.name,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp,
                                                        color = AppColors.textPrimary
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Card(
                                                        colors = CardDefaults.cardColors(containerColor = AppColors.cardBg),
                                                        shape = RoundedCornerShape(2.dp)
                                                    ) {
                                                        Text(
                                                            text = party.type,
                                                            fontSize = 8.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = AppColors.primary,
                                                            modifier = Modifier.padding(horizontal = 2.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(text = "Phone: ${party.phone}", fontSize = 10.sp, color = AppColors.textSecondary)
                                            }

                                            Column(
                                                horizontalAlignment = Alignment.End,
                                                modifier = Modifier.weight(0.8f)
                                            ) {
                                                val displayBal = Utils.formatIndianCurrency(Math.abs(currentBal))
                                                val balLabel = if (currentBal >= 0) "DR" else "CR"
                                                val balColor = if (currentBal >= 0) DangerRed else SuccessGreen

                                                Text(
                                                    text = displayBal,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = balColor
                                                )
                                                Text(
                                                    text = balLabel,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = AppColors.textSecondary
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

            // Detail Pane
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(8.dp)
            ) {
                if (showAddPartyForm) {
                    AddPartyForm(
                        viewModel = viewModel,
                        onDismiss = { showAddPartyForm = false }
                    )
                } else {
                    selectedPartyId?.let { pId ->
                        PartyDetailScreen(
                            viewModel = viewModel,
                            partyId = pId,
                            onNavigateBack = { selectedPartyId = null }
                        )
                    } ?: Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Select a party to see detailed layout.", color = AppColors.textSecondary)
                    }
                }
            }
        }
    } else {
        if (showAddPartyForm) {
            AddPartyForm(
                viewModel = viewModel,
                onDismiss = { showAddPartyForm = false }
            )
        } else {
            Scaffold(
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = { showAddPartyForm = true },
                        containerColor = AppColors.primary,
                        contentColor = AppColors.textOnPrimary,
                        modifier = Modifier.testTag("add_party_fab")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add Party")
                    }
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AppColors.screenBg)
                        .padding(innerPadding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Parties",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.textPrimary
                    )

                    // Search Box using RetailTextField
                    RetailTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = "Search Parties",
                        placeholder = "Search by name, phone or GSTIN...",
                        trailingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = AppColors.textSecondary) },
                        modifier = Modifier.fillMaxWidth().testTag("party_search_bar")
                    )

                    // Filter Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val filters = listOf("ALL", "CUSTOMER", "SUPPLIER")
                        filters.forEach { filter ->
                            FilterChip(
                                selected = selectedTypeFilter == filter,
                                onClick = { selectedTypeFilter = filter },
                                label = { Text(filter, fontSize = 11.sp) },
                                shape = RoundedCornerShape(4.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AppColors.primary,
                                    selectedLabelColor = AppColors.textOnPrimary
                                )
                            )
                        }
                    }

                    if (parties.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = AppColors.textTertiary,
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No parties added yet. Tap + to add.",
                                    color = AppColors.textPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    } else if (filteredParties.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No matching parties found.", color = AppColors.textSecondary, fontSize = 14.sp)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredParties) { party ->
                                val currentBal = partyBalances[party.id] ?: 0.0

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, AppColors.border, RoundedCornerShape(16.dp))
                                        .clickable { onPartySelected(party.id) },
                                    colors = CardDefaults.cardColors(containerColor = AppColors.cardBg)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1.2f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = party.name,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = AppColors.textPrimary
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Card(
                                                    colors = CardDefaults.cardColors(containerColor = AppColors.cardBg),
                                                    shape = RoundedCornerShape(2.dp)
                                                ) {
                                                    Text(
                                                        text = party.type,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = AppColors.primary,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(text = "Phone: ${party.phone}", fontSize = 12.sp, color = AppColors.textSecondary)
                                            Text(text = "GSTIN: ${party.gstin ?: "Unregistered (B2C)"}", fontSize = 11.sp, color = AppColors.textSecondary)
                                        }

                                        Column(
                                            horizontalAlignment = Alignment.End,
                                            modifier = Modifier.weight(0.8f)
                                        ) {
                                            val displayBal = Utils.formatIndianCurrency(Math.abs(currentBal))
                                            val balLabel = if (currentBal >= 0) "DR (Receivable)" else "CR (Payable)"
                                            val balColor = if (currentBal >= 0) DangerRed else SuccessGreen

                                            Text(
                                                text = displayBal,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = balColor
                                            )
                                            Text(
                                                text = balLabel,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = AppColors.textSecondary
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPartyForm(
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("CUSTOMER") } // "CUSTOMER", "SUPPLIER", "BOTH"
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var selectedStateInfo by remember { mutableStateOf(Utils.INDIAN_STATES[18]) } // WB fallback
    var gstin by remember { mutableStateOf("") }
    var gstinValid by remember { mutableStateOf<Boolean?>(null) }
    var pan by remember { mutableStateOf("") }
    var openingBalStr by remember { mutableStateOf("0") }
    var balanceType by remember { mutableStateOf("DR") } // "DR", "CR"

    var dropdownExpanded by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Scaffold(
        containerColor = AppColors.screenBg,
        topBar = {
            TopAppBar(
                title = { Text("Add New Party", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.cardBg)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.screenBg)
                .verticalScroll(scrollState)
                .imePadding()
                .padding(innerPadding)
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Party Name *") },
                modifier = Modifier.fillMaxWidth().testTag("party_name_input"),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )

            // Select Type Segmented Chips
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Type:", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(12.dp))
                val typeOptions = listOf("CUSTOMER", "SUPPLIER", "BOTH")
                typeOptions.forEach { opt ->
                    FilterChip(
                        selected = type == opt,
                        onClick = { type = opt },
                        label = { Text(opt, fontSize = 11.sp) },
                        shape = RoundedCornerShape(4.dp)
                    )
                }
            }

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Phone Number *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                shape = RoundedCornerShape(8.dp)
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email Address") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                shape = RoundedCornerShape(8.dp)
            )

            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("Street Address") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            )

            OutlinedTextField(
                value = city,
                onValueChange = { city = it },
                label = { Text("City") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )

            // State selection dropdown
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = "${selectedStateInfo.first} (${selectedStateInfo.second})",
                    onValueChange = {},
                    label = { Text("State") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.clickable { dropdownExpanded = true }
                        )
                    }
                )
                StateDropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                    onStateSelected = {
                        selectedStateInfo = it
                        dropdownExpanded = false
                    }
                )
            }

            Text(
                "GSTIN (Optional)",
                color = AppColors.labelText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            OutlinedTextField(
                value = gstin,
                onValueChange = { value ->
                    val result = parseGstinInput(
                        value, pan, selectedStateInfo.first, selectedStateInfo.second
                    )
                    gstin = result.gstin
                    gstinValid = result.valid
                    if (result.valid == true) {
                        pan = result.pan
                        result.stateName?.let { name ->
                            Utils.INDIAN_STATES.find { it.first == name }
                                ?.let { selectedStateInfo = it }
                        }
                    }
                },
                placeholder = { Text("15-digit GSTIN", color = AppColors.inputPlaceholder) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                colors = zeroBookInputColors()
            )
            GstinValidationFeedback(
                gstin, gstinValid, pan, selectedStateInfo.first, selectedStateInfo.second
            )

            OutlinedTextField(
                value = pan,
                onValueChange = { pan = it.uppercase() },
                label = { Text("PAN Number (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = openingBalStr,
                    onValueChange = { openingBalStr = filterDecimalInput(it) },
                    label = { Text("Opening Balance (₹)") },
                    modifier = Modifier.weight(1.2f).onFocusChanged { focusState ->
                        if (focusState.isFocused && (openingBalStr == "0" || openingBalStr == "0.0" || openingBalStr == "0.00")) {
                            openingBalStr = ""
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(8.dp)
                )
                
                Row(modifier = Modifier.weight(0.8f).padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("DR", "CR").forEach { bType ->
                        FilterChip(
                            selected = balanceType == bType,
                            onClick = { balanceType = bType },
                            label = { Text(bType) },
                            shape = RoundedCornerShape(4.dp)
                        )
                    }
                }
            }

            if (showError) {
                Text("Please fill in all mandatory (*) fields correctly.", color = Color.Red, fontSize = 12.sp)
            }

            Button(
                onClick = {
                    if (name.isBlank() || phone.isBlank()) {
                        showError = true
                    } else {
                        val partyObj = Party(
                            id = UUID.randomUUID().toString(),
                            name = name,
                            type = type,
                            phone = phone,
                            email = email,
                            address = address,
                            city = city,
                            state = selectedStateInfo.first,
                            stateCode = selectedStateInfo.second,
                            gstin = if (gstin.isBlank()) null else gstin,
                            pan = if (pan.isBlank()) null else pan,
                            openingBalance = openingBalStr.toDoubleOrNull() ?: 0.0,
                            balanceType = balanceType
                        )
                        viewModel.saveParty(partyObj) {
                            onDismiss()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_party_button"),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text("Save Party", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// Party Detailed General Ledger Statement/Reminder screen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartyDetailScreen(
    viewModel: AppViewModel,
    partyId: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var party by remember { mutableStateOf<Party?>(null) }
    val profile by viewModel.profile.collectAsState()
    val ledgerEntries by viewModel.ledgerEntries.collectAsState()

    LaunchedEffect(partyId) {
        party = viewModel.getPartyById(partyId)
    }

    Scaffold(
        containerColor = AppColors.screenBg,
        topBar = {
            TopAppBar(
                title = { Text(party?.name ?: "Party Ledger", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.cardBg)
            )
        }
    ) { innerPadding ->
        if (party == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val p = party!!
            val pName = p.name

            // Computes dynamic ledger flow statements
            val matchedEntries = remember(ledgerEntries, pName) {
                ledgerEntries.filter { it.accountHead == "Party: $pName" }.sortedBy { it.date }
            }

            var runningBalance = p.openingBalance * (if (p.balanceType == "DR") 1 else -1)
            val statementRows = remember(matchedEntries, runningBalance) {
                var current = runningBalance
                matchedEntries.map { entry ->
                    current += (entry.debit - entry.credit)
                    LedgerStatementRow(entry, current)
                }.reversed() // descending date view
            }

            val finalOutstanding = statementRows.firstOrNull()?.runningBalance ?: runningBalance

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppColors.screenBg)
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Party Details Box
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(0.5.dp, Color(0xFFEAEAEA), RoundedCornerShape(8.dp)),
                    colors = CardDefaults.cardColors(containerColor = AppColors.cardBg)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = AppColors.textSecondary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Phone: ${p.phone} | Email: ${p.email}", fontSize = 12.sp, color = AppColors.textSecondary)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Map,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = AppColors.textSecondary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("GSTIN: ${p.gstin ?: "Unregistered"} | State: ${p.state}", fontSize = 12.sp, color = AppColors.textSecondary)
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Outstanding Balance:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            val color = if (finalOutstanding >= 0) DangerRed else SuccessGreen
                            Text(
                                text = Utils.formatIndianCurrency(Math.abs(finalOutstanding)) + if (finalOutstanding >= 0) " DR" else " CR",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = color
                            )
                        }
                    }
                }

                // Payment Reminder Trigger email button
                if (finalOutstanding > 0) {
                    Button(
                        onClick = {
                            val emailSubject = Uri.encode("Payment Reminder — Outstanding Balance from ${profile?.businessName ?: "ZeroBook"}")
                            val emailBody = Uri.encode(
                                """
                                Dear $pName,

                                This is a friendly reminder that you have an outstanding balance of ${Utils.formatIndianCurrency(finalOutstanding)} payable to ${profile?.businessName ?: "us"}.

                                Please arrange a clearance transfer of the outstanding amount.

                                Bank account details for your transfer:
                                Bank Name: ${profile?.bankName ?: "N/A"}
                                Account No: ${profile?.accountNo ?: "N/A"}
                                IFSC: ${profile?.ifsc ?: "N/A"}

                                Thank you for your continued partnership.
                                
                                Best Regards,
                                ${profile?.businessName ?: "ZeroBook Dealer"}
                                ${profile?.phone ?: ""}
                                """.trimIndent()
                            )
                            val mailtoUri = "mailto:${p.email}?subject=$emailSubject&body=$emailBody"
                            val mailToIntent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse(mailtoUri)
                            }
                            try {
                                context.startActivity(Intent.createChooser(mailToIntent, "Send Reminder Email"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "No email client configured on device.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(imageVector = Icons.Default.Email, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                        Text("Send Email Payment Reminder")
                    }
                }

                // Statement Header
                Text("LEDGER STATEMENT", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Gray)

                // Sub-statement Table Grid
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .border(0.5.dp, Color.LightGray, RoundedCornerShape(4.dp))
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFAF9F9))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Date", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.9f))
                            Text("Debit (₹)", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text("Credit (₹)", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text("Balance (₹)", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f))
                        }

                        if (statementRows.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                                Text("No ledger activities recorded", fontSize = 13.sp, color = Color.Gray)
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(statementRows) { row ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(Utils.formatDate(row.entry.date), fontSize = 11.sp, modifier = Modifier.weight(0.9f))
                                        Text(if (row.entry.debit > 0) String.format("%.2f", row.entry.debit) else "-", fontSize = 11.sp, modifier = Modifier.weight(1f))
                                        Text(if (row.entry.credit > 0) String.format("%.2f", row.entry.credit) else "-", fontSize = 11.sp, modifier = Modifier.weight(1f))
                                        
                                        val rowBal = row.runningBalance
                                        val displayStr = Utils.formatIndianCurrency(Math.abs(rowBal)) + if (rowBal >= 0) " DR" else " CR"
                                        val color = if (rowBal >= 0) DangerRed else SuccessGreen

                                        Text(displayStr, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.weight(1.2f))
                                    }
                                    HorizontalDivider(color = Color(0xFFF1F1F1))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

data class LedgerStatementRow(val entry: LedgerEntry, val runningBalance: Double)
