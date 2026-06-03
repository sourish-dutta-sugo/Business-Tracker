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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
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
            topBar = {
                TopAppBar(
                    title = { Text("Manage Products", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by product name or HSN code...") },
                    leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().testTag("product_search_bar"),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                if (filteredProducts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No products configured yet.", color = Colors.textSecondary, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredProducts) { item ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(0.5.dp, Color(0xFFE8E8E8), RoundedCornerShape(8.dp)),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFAF9F9))
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
                                        Text(text = "HSN: ${item.hsnCode} | Unit: ${item.unit}", fontSize = 11.sp, color = Colors.textSecondary)
                                        Text(text = "GST Rate: ${item.gstRate}%", fontSize = 11.sp, color = Colors.textSecondary, fontWeight = FontWeight.Bold)
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
                                            color = Colors.textSecondary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(text = "Cost: ${Utils.formatIndianCurrency(item.purchaseRate)}", fontSize = 10.sp, color = Colors.textSecondary)
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
    var gstRate by remember { mutableStateOf(18.0) } // 18% standard default
    var openingStockStr by remember { mutableStateOf("0") }

    var unitDropdownExpanded by remember { mutableStateOf(false) }
    var gstDropdownExpanded by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add New Product", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .verticalScroll(scrollState)
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Product / Item Name *") },
                modifier = Modifier.fillMaxWidth().testTag("product_name_input"),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )

            OutlinedTextField(
                value = hsnCode,
                onValueChange = { hsnCode = it },
                label = { Text("HSN Code (8-digit) *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(8.dp)
            )

            // Unit Selector Dropdown
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
                            modifier = Modifier.clickable { unitDropdownExpanded = true }
                        )
                    }
                )
                DropdownMenu(
                    expanded = unitDropdownExpanded,
                    onDismissRequest = { unitDropdownExpanded = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val units = listOf("PCS", "KG", "GM", "MG", "LTR", "ML", "BOX", "BAG", "NOS", "MTR")
                    units.forEach { u ->
                        DropdownMenuItem(
                            text = { Text(u) },
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
                    onValueChange = { saleRateStr = it },
                    label = { Text("Sale Rate (₹) *") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = purchaseRateStr,
                    onValueChange = { purchaseRateStr = it },
                    label = { Text("Cost Rate (₹) *") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(8.dp)
                )
            }

            // GST Rate dropdown selector
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = "$gstRate%",
                    onValueChange = {},
                    label = { Text("GST Rate *") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.clickable { gstDropdownExpanded = true }
                        )
                    }
                )
                DropdownMenu(
                    expanded = gstDropdownExpanded,
                    onDismissRequest = { gstDropdownExpanded = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val rates = listOf(0.0, 5.0, 12.0, 18.0, 28.0)
                    rates.forEach { r ->
                        DropdownMenuItem(
                            text = { Text("$r%") },
                            onClick = {
                                gstRate = r
                                gstDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = openingStockStr,
                onValueChange = { openingStockStr = it },
                label = { Text("Opening Stock Quantity") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(8.dp)
            )

            if (showError) {
                Text("Please fill in all mandatory (*) fields with proper numbers.", color = Color.Red, fontSize = 12.sp)
            }

            Button(
                onClick = {
                    val sRate = saleRateStr.toDoubleOrNull()
                    val pRate = purchaseRateStr.toDoubleOrNull()
                    val oStock = openingStockStr.toDoubleOrNull() ?: 0.0

                    if (name.isBlank() || hsnCode.isBlank() || sRate == null || pRate == null) {
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
                            openingStock = oStock
                        )
                        viewModel.saveProduct(productObj) {
                            onDismiss()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_product_button"),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text("Save Product Raw Item", fontWeight = FontWeight.Bold)
            }
        }
    }
}
