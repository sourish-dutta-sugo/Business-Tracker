package com.example.ui.screens
import com.example.ui.theme.AppColors

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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
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
import com.example.utils.HsnEntry
import com.example.utils.HsnLookup
import kotlinx.coroutines.delay
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(
    viewModel: AppViewModel,
    onNavigateBack: () -> Unit
) {
    val products by viewModel.products.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var showAddForm by remember { mutableStateOf(false) }

    val filteredProducts = remember(products, searchQuery) {
        products.filter { p ->
            p.name.contains(searchQuery, ignoreCase = true) || p.hsnCode.contains(searchQuery)
        }
    }

    if (showAddForm) {
        AddProductForm(
            viewModel = viewModel,
            onDismiss = { showAddForm = false }
        )
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = AppColors.screenBg,
            topBar = {
                TopAppBar(
                    title = { Text("Manage Products", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.cardBg)
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showAddForm = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("add_product_fab")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Product")
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppColors.screenBg)
                    .padding(innerPadding)
                    .imePadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 16.dp, end = 16.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search by product name or HSN code...") },
                        leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = AppColors.textSecondary) },
                        modifier = Modifier.fillMaxWidth().testTag("product_search_bar"),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AppColors.inputText,
                            unfocusedTextColor = AppColors.inputText,
                            disabledTextColor = AppColors.inputText,
                            focusedBorderColor = Colors.inputBorder,
                            unfocusedBorderColor = Colors.inputBorder,
                            disabledBorderColor = Colors.inputBorder,
                            focusedContainerColor = AppColors.inputBg,
                            unfocusedContainerColor = AppColors.inputBg,
                            disabledContainerColor = AppColors.inputBg,
                            focusedPlaceholderColor = AppColors.inputPlaceholder,
                            unfocusedPlaceholderColor = AppColors.inputPlaceholder
                        )
                    )

                    if (filteredProducts.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No products configured yet.", color = AppColors.textSecondary, fontSize = 14.sp)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .imePadding(),
                            contentPadding = PaddingValues(start = 0.dp, end = 0.dp, bottom = 120.dp)
                        ) {
                            items(filteredProducts) { item ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(0.5.dp, Color(0xFFE8E8E8), RoundedCornerShape(8.dp)),
                                    colors = CardDefaults.cardColors(containerColor = AppColors.cardBg)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = item.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = Color(0xFF1A1A1A)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(text = "HSN: ${item.hsnCode} | Unit: ${item.unit}", fontSize = 11.sp, color = AppColors.textSecondary)
                                            Text(text = "GST Rate: ${item.gstRate}%", fontSize = 11.sp, color = AppColors.textSecondary, fontWeight = FontWeight.Bold)
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = Utils.formatIndianCurrency(item.saleRate),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "Sale Rate",
                                                fontSize = 10.sp,
                                                color = AppColors.textSecondary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(text = "Cost: ${Utils.formatIndianCurrency(item.purchaseRate)}", fontSize = 10.sp, color = AppColors.textSecondary)
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
fun AddProductForm(
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var hsnCode by remember { mutableStateOf("") }
    var selectedUnit by remember { mutableStateOf("PCS") }
    var saleRateStr by remember { mutableStateOf("") }
    var purchaseRateStr by remember { mutableStateOf("") }
    var gstRate by remember { mutableStateOf(18.0) }
    var openingStockStr by remember { mutableStateOf("0") }

    var unitDropdownExpanded by remember { mutableStateOf(false) }
    var gstExpanded by remember { mutableStateOf(false) }
    val gstRates = listOf(0.0, 5.0, 12.0, 18.0, 28.0)
    var batchEnabled by remember { mutableStateOf(false) }
    var batchNumber by remember { mutableStateOf("") }
    var expiryEnabled by remember { mutableStateOf(false) }
    var expiryDate by remember { mutableStateOf("") }
    var serialEnabled by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    var hsnSuggestions by remember { mutableStateOf<List<HsnEntry>>(emptyList()) }
    var wasHsnAutoFilled by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    LaunchedEffect(name) {
        if (name.length >= 3) {
            delay(400)
            hsnSuggestions = HsnLookup.search(name)
        } else {
            hsnSuggestions = emptyList()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = AppColors.screenBg,
        topBar = {
            TopAppBar(
                title = { Text("Add New Product", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.cardBg)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.screenBg)
                .padding(innerPadding)
                .imePadding()
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .imePadding()
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    if (wasHsnAutoFilled) {
                        hsnCode = ""
                        wasHsnAutoFilled = false
                    }
                },
                label = { Text("Product / Item Name *") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("product_name_input"),
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = AppColors.inputText,
                    unfocusedTextColor = AppColors.inputText,
                    disabledTextColor = AppColors.inputText,
                    focusedBorderColor = Colors.inputBorder,
                    unfocusedBorderColor = Colors.inputBorder,
                    disabledBorderColor = Colors.inputBorder,
                    focusedContainerColor = AppColors.inputBg,
                    unfocusedContainerColor = AppColors.inputBg,
                    disabledContainerColor = AppColors.inputBg,
                    focusedPlaceholderColor = AppColors.inputPlaceholder,
                    unfocusedPlaceholderColor = AppColors.inputPlaceholder
                )
            )

            ProductOptionalFields(
                hsnCode = hsnCode,
                onHsnChange = { hsnCode = it },
                hsnSuggestions = hsnSuggestions,
                onHsnSelected = {
                    hsnCode = it
                    wasHsnAutoFilled = true
                    hsnSuggestions = emptyList()
                },
                batchEnabled = batchEnabled,
                onBatchEnabledChange = { batchEnabled = it },
                batchNumber = batchNumber,
                onBatchNumberChange = { batchNumber = it },
                expiryEnabled = expiryEnabled,
                onExpiryEnabledChange = { expiryEnabled = it },
                expiryDate = expiryDate,
                onExpiryDateChange = { expiryDate = it },
                serialEnabled = serialEnabled,
                onSerialEnabledChange = { serialEnabled = it }
            )

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = selectedUnit,
                    onValueChange = {},
                    label = { Text("Unit of Measurement *") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.clickable { unitDropdownExpanded = true },
                            tint = AppColors.textSecondary
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppColors.inputText,
                        unfocusedTextColor = AppColors.inputText,
                        disabledTextColor = AppColors.inputText,
                        focusedBorderColor = Colors.inputBorder,
                        unfocusedBorderColor = Colors.inputBorder,
                        disabledBorderColor = Colors.inputBorder,
                        focusedContainerColor = AppColors.inputBg,
                        unfocusedContainerColor = AppColors.inputBg,
                        disabledContainerColor = AppColors.inputBg,
                        focusedPlaceholderColor = AppColors.inputPlaceholder,
                        unfocusedPlaceholderColor = AppColors.inputPlaceholder
                    )
                )
                DropdownMenu(
                    expanded = unitDropdownExpanded,
                    onDismissRequest = { unitDropdownExpanded = false },
                    modifier = Modifier.fillMaxWidth().background(AppColors.cardBg)
                ) {
                    val units = listOf("PCS", "KG", "GM", "MG", "LTR", "ML", "BOX", "BAG", "NOS", "MTR")
                    units.forEach { u ->
                        DropdownMenuItem(
                            text = { Text(u, color = AppColors.textPrimary) },
                            onClick = {
                                selectedUnit = u
                                unitDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = saleRateStr,
                    onValueChange = { saleRateStr = filterDecimalInput(it) },
                    label = { Text("Sale Rate (₹) *") },
                    modifier = Modifier.weight(1f).onFocusChanged { focusState ->
                        if (focusState.isFocused && (saleRateStr == "0" || saleRateStr == "0.0" || saleRateStr == "0.00")) {
                            saleRateStr = ""
                        } else if (!focusState.isFocused && saleRateStr.isBlank()) {
                            saleRateStr = "0"
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppColors.inputText,
                        unfocusedTextColor = AppColors.inputText,
                        disabledTextColor = AppColors.inputText,
                        focusedBorderColor = Colors.inputBorder,
                        unfocusedBorderColor = Colors.inputBorder,
                        disabledBorderColor = Colors.inputBorder,
                        focusedContainerColor = AppColors.inputBg,
                        unfocusedContainerColor = AppColors.inputBg,
                        disabledContainerColor = AppColors.inputBg,
                        focusedPlaceholderColor = AppColors.inputPlaceholder,
                        unfocusedPlaceholderColor = AppColors.inputPlaceholder
                    )
                )

                OutlinedTextField(
                    value = purchaseRateStr,
                    onValueChange = { purchaseRateStr = filterDecimalInput(it) },
                    label = { Text("Cost Rate (₹) *") },
                    modifier = Modifier.weight(1f).onFocusChanged { focusState ->
                        if (focusState.isFocused && (purchaseRateStr == "0" || purchaseRateStr == "0.0" || purchaseRateStr == "0.00")) {
                            purchaseRateStr = ""
                        } else if (!focusState.isFocused && purchaseRateStr.isBlank()) {
                            purchaseRateStr = "0"
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppColors.inputText,
                        unfocusedTextColor = AppColors.inputText,
                        disabledTextColor = AppColors.inputText,
                        focusedBorderColor = Colors.inputBorder,
                        unfocusedBorderColor = Colors.inputBorder,
                        disabledBorderColor = Colors.inputBorder,
                        focusedContainerColor = AppColors.inputBg,
                        unfocusedContainerColor = AppColors.inputBg,
                        disabledContainerColor = AppColors.inputBg,
                        focusedPlaceholderColor = AppColors.inputPlaceholder,
                        unfocusedPlaceholderColor = AppColors.inputPlaceholder
                    )
                )
            }

            ExposedDropdownMenuBox(
                expanded = gstExpanded,
                onExpandedChange = { gstExpanded = it }
            ) {
                OutlinedTextField(
                    value = "${gstRate.toInt()}%",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("GST Rate") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults
                            .TrailingIcon(expanded = gstExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppColors.inputText,
                        unfocusedTextColor = AppColors.inputText,
                        disabledTextColor = AppColors.inputText,
                        focusedBorderColor = Colors.inputBorder,
                        unfocusedBorderColor = Colors.inputBorder,
                        disabledBorderColor = Colors.inputBorder,
                        focusedContainerColor = AppColors.inputBg,
                        unfocusedContainerColor = AppColors.inputBg,
                        disabledContainerColor = AppColors.inputBg,
                        focusedPlaceholderColor = AppColors.inputPlaceholder,
                        unfocusedPlaceholderColor = AppColors.inputPlaceholder
                    )
                )
                ExposedDropdownMenu(
                    expanded = gstExpanded,
                    onDismissRequest = { gstExpanded = false }
                ) {
                    gstRates.forEach { rate ->
                        DropdownMenuItem(
                            text = {
                                Text("${rate.toInt()}%")
                            },
                            onClick = {
                                gstRate = rate
                                gstExpanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = openingStockStr,
                onValueChange = { openingStockStr = filterDecimalInput(it) },
                label = { Text("Opening Stock Quantity") },
                modifier = Modifier.fillMaxWidth().onFocusChanged { focusState ->
                    if (focusState.isFocused && (openingStockStr == "0" || openingStockStr == "0.0" || openingStockStr == "0.00")) {
                        openingStockStr = ""
                    } else if (!focusState.isFocused && openingStockStr.isBlank()) {
                        openingStockStr = "0"
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = AppColors.inputText,
                    unfocusedTextColor = AppColors.inputText,
                    disabledTextColor = AppColors.inputText,
                    focusedBorderColor = Colors.inputBorder,
                    unfocusedBorderColor = Colors.inputBorder,
                    disabledBorderColor = Colors.inputBorder,
                    focusedContainerColor = AppColors.inputBg,
                    unfocusedContainerColor = AppColors.inputBg,
                    disabledContainerColor = AppColors.inputBg,
                    focusedPlaceholderColor = AppColors.inputPlaceholder,
                    unfocusedPlaceholderColor = AppColors.inputPlaceholder
                )
            )

            if (showError) {
                Text("Please fill in all mandatory (*) fields with proper numbers.", color = AppColors.error, fontSize = 12.sp)
            }

            Button(
                onClick = {
                    val sRate = saleRateStr.toDoubleOrNull()
                    val pRate = purchaseRateStr.toDoubleOrNull()
                    val oStock = openingStockStr.toDoubleOrNull() ?: 0.0

                    if (name.isBlank() || sRate == null || pRate == null) {
                        showError = true
                    } else {
                        val productObj = Product(
                            id = UUID.randomUUID().toString(),
                            name = name,
                            hsnCode = hsnCode,
                            unit = selectedUnit,
                            saleRate = sRate,
                            purchaseRate = pRate,
                            gstRate = gstRate,
                            openingStock = oStock,
                            batchEnabled = batchEnabled,
                            batchNumber = if (batchEnabled) batchNumber.trim() else "",
                            expiryEnabled = expiryEnabled,
                            expiryDate = if (expiryEnabled) expiryDate.trim() else "",
                            serialEnabled = serialEnabled
                        )
                        viewModel.saveProduct(productObj) {
                            onDismiss()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_product_button"),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.primary)
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text("Save Product Raw Item", fontWeight = FontWeight.Bold, color = AppColors.textOnPrimary)
            }
        }
        }
    }
}
