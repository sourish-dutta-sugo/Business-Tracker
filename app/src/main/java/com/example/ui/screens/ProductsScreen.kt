package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Product
import com.example.data.Utils
import com.example.data.filterDecimalInput
import com.example.ui.AppViewModel
import com.example.ui.theme.AppColors
import com.example.ui.theme.Colors
import com.example.utils.HsnLookup
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
        products.filter { product ->
            product.name.contains(searchQuery, ignoreCase = true) ||
                product.hsnCode.contains(searchQuery, ignoreCase = true) ||
                product.barcodeValue.contains(searchQuery, ignoreCase = true)
        }
    }

    if (showAddForm) {
        AddProductForm(viewModel = viewModel, onDismiss = { showAddForm = false })
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = AppColors.screenBg,
        topBar = {
            TopAppBar(
                title = { Text("Manage Products", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.cardBg)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddForm = true },
                containerColor = AppColors.primary,
                contentColor = AppColors.textOnPrimary,
                modifier = Modifier.testTag("add_product_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Product")
            }
        }
    ) { innerPadding ->
        val lowStockCount = filteredProducts.count { product ->
            product.currentStock <= 0.0 || product.currentStock <= product.lowStockThreshold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.screenBg)
                .padding(innerPadding)
                .imePadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by product, HSN, or barcode") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = AppColors.inputBg,
                    unfocusedContainerColor = AppColors.inputBg
                )
            )

            if (lowStockCount > 0) {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))) {
                    Text(
                        text = "$lowStockCount product(s) need stock attention",
                        modifier = Modifier.padding(14.dp),
                        color = Color(0xFFB45309),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (filteredProducts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No products configured yet.", color = AppColors.textSecondary, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 120.dp)
                ) {
                    items(items = filteredProducts, key = { product -> product.id }) { product ->
                        ProductRow(product = product)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductRow(product: Product) {
    val statusColor = when {
        product.currentStock <= 0.0 -> Color(0xFFC62828)
        product.currentStock <= product.lowStockThreshold -> Color(0xFFEF6C00)
        else -> Color(0xFF2E7D32)
    }
    val statusLabel = when {
        product.currentStock <= 0.0 -> "Out of Stock"
        product.currentStock <= product.lowStockThreshold -> "Low Stock"
        else -> "In Stock"
    }

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
            Column(modifier = Modifier.weight(1f)) {
                Text(product.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1A1A1A))
                Spacer(modifier = Modifier.height(4.dp))
                Text("HSN: ${product.hsnCode.ifBlank { "N/A" }}", fontSize = 11.sp, color = AppColors.textSecondary)
                Text(
                    "Stock: ${product.currentStock} ${product.stockUnit.ifBlank { product.unit }} | Low at ${product.lowStockThreshold}",
                    fontSize = 11.sp,
                    color = AppColors.textSecondary
                )
                if (product.barcodeValue.isNotBlank()) {
                    Text("Barcode: ${product.barcodeValue}", fontSize = 11.sp, color = AppColors.textSecondary)
                }
            }

            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(statusLabel, color = statusColor, fontSize = 10.sp) },
                    colors = AssistChipDefaults.assistChipColors(
                        disabledContainerColor = statusColor.copy(alpha = 0.12f),
                        disabledLabelColor = statusColor
                    )
                )
                Text(Utils.formatIndianCurrency(product.saleRate), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AppColors.primary)
                Text("Sale Rate", fontSize = 10.sp, color = AppColors.textSecondary)
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
    var lowStockThresholdStr by remember { mutableStateOf("5") }
    var barcodeValue by remember { mutableStateOf("") }
    var secondaryUnit by remember { mutableStateOf("") }
    var conversionFactorStr by remember { mutableStateOf("1") }
    var batchEnabled by remember { mutableStateOf(false) }
    var batchNumber by remember { mutableStateOf("") }
    var expiryEnabled by remember { mutableStateOf(false) }
    var expiryDate by remember { mutableStateOf("") }
    var serialEnabled by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    var unitDropdownExpanded by remember { mutableStateOf(false) }
    var gstExpanded by remember { mutableStateOf(false) }
    var showBarcodeScanner by remember { mutableStateOf(false) }

    val units = listOf("PCS", "KG", "GM", "MG", "LTR", "ML", "BOX", "BAG", "NOS", "MTR")
    val gstRates = listOf(0.0, 5.0, 12.0, 18.0, 28.0)
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = AppColors.screenBg,
        topBar = {
            TopAppBar(
                title = { Text("Add New Product", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.cardBg)
            )
        }
        ) { innerPadding ->
        if (showBarcodeScanner) {
            BarcodeScannerDialog(
                onDismiss = { showBarcodeScanner = false },
                onScanned = {
                    barcodeValue = it
                    showBarcodeScanner = false
                }
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.screenBg)
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .imePadding()
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Product / Item Name *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            ProductOptionalFields(
                hsnCode = hsnCode,
                onHsnChange = { hsnCode = it },
                onAutoDetectHsn = {
                    val matches = HsnLookup.search(name.trim())
                    when {
                        name.isBlank() -> showError = true
                        matches.isEmpty() -> showError = true
                        else -> hsnCode = matches.first().code
                    }
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

            OutlinedTextField(
                value = barcodeValue,
                onValueChange = { barcodeValue = it.trim() },
                label = { Text("Barcode / QR Code") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { showBarcodeScanner = true }) {
                        Icon(Icons.Default.Search, contentDescription = "Scan barcode")
                    }
                }
            )

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = selectedUnit,
                    onValueChange = {},
                    label = { Text("Unit of Measurement *") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.clickable { unitDropdownExpanded = true }
                        )
                    }
                )
                DropdownMenu(
                    expanded = unitDropdownExpanded,
                    onDismissRequest = { unitDropdownExpanded = false }
                ) {
                    units.forEach { unit ->
                        DropdownMenuItem(
                            text = { Text(unit) },
                            onClick = {
                                selectedUnit = unit
                                unitDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                DecimalField(
                    value = saleRateStr,
                    onValueChange = { saleRateStr = it },
                    label = "Sale Rate (Rs) *",
                    modifier = Modifier.weight(1f)
                )
                DecimalField(
                    value = purchaseRateStr,
                    onValueChange = { purchaseRateStr = it },
                    label = "Cost Rate (Rs) *",
                    modifier = Modifier.weight(1f)
                )
            }

            ExposedDropdownMenuBox(expanded = gstExpanded, onExpandedChange = { gstExpanded = it }) {
                OutlinedTextField(
                    value = "${gstRate.toInt()}%",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("GST Rate") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = gstExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = gstExpanded, onDismissRequest = { gstExpanded = false }) {
                    gstRates.forEach { rate ->
                        DropdownMenuItem(
                            text = { Text("${rate.toInt()}%") },
                            onClick = {
                                gstRate = rate
                                gstExpanded = false
                            }
                        )
                    }
                }
            }

            DecimalField(
                value = openingStockStr,
                onValueChange = { openingStockStr = it },
                label = "Opening Stock Quantity",
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                DecimalField(
                    value = lowStockThresholdStr,
                    onValueChange = { lowStockThresholdStr = it },
                    label = "Low Stock Threshold",
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = secondaryUnit,
                    onValueChange = { secondaryUnit = it.uppercase() },
                    label = { Text("Secondary Unit") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            DecimalField(
                value = conversionFactorStr,
                onValueChange = { conversionFactorStr = it },
                label = "Conversion Factor",
                supportingText = if (secondaryUnit.isBlank()) {
                    "1 secondary unit = X $selectedUnit"
                } else {
                    "1 $secondaryUnit = ${conversionFactorStr.ifBlank { "1" }} $selectedUnit"
                },
                modifier = Modifier.fillMaxWidth()
            )

            if (showError) {
                Text(
                    "Please fill in the required fields. Enter a product name before using Auto-detect HSN.",
                    color = AppColors.error,
                    fontSize = 12.sp
                )
            }

            Button(
                onClick = {
                    val saleRate = saleRateStr.toDoubleOrNull()
                    val purchaseRate = purchaseRateStr.toDoubleOrNull()
                    val openingStock = openingStockStr.toDoubleOrNull() ?: 0.0
                    val lowStockThreshold = lowStockThresholdStr.toDoubleOrNull() ?: 5.0
                    val conversionFactor = conversionFactorStr.toDoubleOrNull() ?: 1.0
                    if (name.isBlank() || saleRate == null || purchaseRate == null) {
                        showError = true
                    } else {
                        viewModel.saveProduct(
                            Product(
                                id = UUID.randomUUID().toString(),
                                name = name.trim(),
                                hsnCode = hsnCode,
                                unit = selectedUnit,
                                saleRate = saleRate,
                                purchaseRate = purchaseRate,
                                gstRate = gstRate,
                                openingStock = openingStock,
                                currentStock = openingStock,
                                lowStockThreshold = lowStockThreshold,
                                stockUnit = selectedUnit,
                                barcodeValue = barcodeValue,
                                secondaryUnit = secondaryUnit,
                                conversionFactor = conversionFactor,
                                batchEnabled = batchEnabled,
                                batchNumber = if (batchEnabled) batchNumber.trim() else "",
                                expiryEnabled = expiryEnabled,
                                expiryDate = if (expiryEnabled) expiryDate.trim() else "",
                                serialEnabled = serialEnabled
                            )
                        ) {
                            onDismiss()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("save_product_button"),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.primary)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save Product", color = AppColors.textOnPrimary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DecimalField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(filterDecimalInput(it)) },
        label = { Text(label) },
        supportingText = supportingText?.let { { Text(it) } },
        modifier = modifier.onFocusChanged { state ->
            if (state.isFocused && (value == "0" || value == "0.0")) {
                onValueChange("")
            } else if (!state.isFocused && value.isBlank()) {
                onValueChange("0")
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}
