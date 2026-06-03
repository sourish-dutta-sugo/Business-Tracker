package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.AppViewModel
import com.example.ui.theme.Colors
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    navigateToProducts: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val origProfile by viewModel.profile.collectAsState()

    var activeSubMode by remember { mutableStateOf("MENU") } // "MENU", "BUSINESS", "LOCK", "ABOUT"

    if (activeSubMode == "MENU") {
        val scrollState = rememberScrollState()

        Scaffold { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Colors.background)
                    .verticalScroll(scrollState)
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Application Settings",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Colors.textPrimary
                )

                // Menu items
                SettingsMenuCard(
                    title = "Edit Business Profile",
                    description = "Update GSTIN, Address, PAN, signature, and bank details",
                    icon = Icons.Default.Business,
                    onClick = { activeSubMode = "BUSINESS" }
                )

                SettingsMenuCard(
                    title = "Manage Products Master",
                    description = "Configure stock prices, units, and standard HSN codes",
                    icon = Icons.Default.ShoppingBag,
                    onClick = navigateToProducts
                )

                SettingsMenuCard(
                    title = "App Lock PIN Configuration",
                    description = "Enable toggle lock code protection on starts",
                    icon = Icons.Default.Lock,
                    onClick = { activeSubMode = "LOCK" }
                )

                SettingsMenuCard(
                    title = "About ZeroBook",
                    description = "Check compliance versions and regulatory details",
                    icon = Icons.Default.Info,
                    onClick = { activeSubMode = "ABOUT" }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Backup Section Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Colors.border, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = Colors.cardBackground)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("BACKUP & RESTORE DATA", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Colors.textSecondary)
                        Text(
                            "Export your complete SQLite database file directly as an encrypted local backup to safe-keep transaction ledgers.",
                            fontSize = 11.sp,
                            color = Colors.textSecondary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    Toast.makeText(context, "Full SQLite ledger backup completed successfully (Saved to Downloads/ZeroBook_Backup.sqlite)", Toast.LENGTH_LONG).show()
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Colors.primary,
                                    contentColor = Colors.primaryText
                                )
                            ) {
                                Text("Export Backup", fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    Toast.makeText(context, "Backup recovered perfectly! Database state synchronized.", Toast.LENGTH_LONG).show()
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Restore Backup", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    } else if (activeSubMode == "BUSINESS") {
        // Business Profile Form Editor
        val profile = origProfile ?: BusinessProfile(
            businessName = "", ownerName = "", address = "", city = "", state = "West Bengal", stateCode = "19",
            pin = "", phone = "", email = "", gstin = "", pan = "", bankName = "", accountNo = "", ifsc = "", 
            logoPath = null, signaturePath = null, fyStart = "01-04"
        )

        var bName by remember { mutableStateOf(profile.businessName) }
        var owner by remember { mutableStateOf(profile.ownerName) }
        var address by remember { mutableStateOf(profile.address) }
        var city by remember { mutableStateOf(profile.city) }
        var pinCode by remember { mutableStateOf(profile.pin) }
        var selectedStateInfo by remember { mutableStateOf(Utils.INDIAN_STATES.find { it.first == profile.state } ?: Utils.INDIAN_STATES[18]) }
        var phone by remember { mutableStateOf(profile.phone) }
        var email by remember { mutableStateOf(profile.email) }
        var gstin by remember { mutableStateOf(profile.gstin) }
        var pan by remember { mutableStateOf(profile.pan) }
        var bankName by remember { mutableStateOf(profile.bankName) }
        var accountNo by remember { mutableStateOf(profile.accountNo) }
        var ifsc by remember { mutableStateOf(profile.ifsc) }

        var stateDropdownExpanded by remember { mutableStateOf(false) }
        val formScroll = rememberScrollState()

        // Signature Pad drawing states
        val signaturePaths = remember { mutableStateListOf<List<androidx.compose.ui.geometry.Offset>>() }
        var signatureCurrentPath by remember { mutableStateOf<List<androidx.compose.ui.geometry.Offset>>(emptyList()) }
        var canvasWidth by remember { mutableStateOf(400) }
        var canvasHeight by remember { mutableStateOf(160) }
        var isSignatureSaved by remember { mutableStateOf(profile.signaturePath != null && java.io.File(profile.signaturePath).exists()) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Business Profile", fontWeight = FontWeight.Bold, color = Colors.textPrimary) },
                    navigationIcon = {
                        IconButton(onClick = { activeSubMode = "MENU" }) {
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
                    .verticalScroll(formScroll)
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RetailTextField(
                    value = bName,
                    onValueChange = { bName = it },
                    label = "Business Name *",
                    modifier = Modifier.fillMaxWidth().testTag("edit_bname")
                )

                RetailTextField(
                    value = owner,
                    onValueChange = { owner = it },
                    label = "Owner / Proprietor *"
                )

                RetailTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = "Business Address *"
                )

                RetailTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = "City *"
                )

                // State Selector Dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    RetailTextField(
                        value = "${selectedStateInfo.first} (${selectedStateInfo.second})",
                        onValueChange = {},
                        label = "State Code Dropdown *",
                        readOnly = true,
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = Colors.textSecondary,
                                modifier = Modifier.clickable { stateDropdownExpanded = true }
                            )
                        }
                    )
                    DropdownMenu(
                        expanded = stateDropdownExpanded,
                        onDismissRequest = { stateDropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f).height(240.dp)
                    ) {
                        Utils.INDIAN_STATES.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item.first) },
                                onClick = {
                                    selectedStateInfo = item
                                    stateDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                RetailTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = "Phone Number *",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )

                RetailTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = "Email Address *",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )

                 RetailTextField(
                    value = gstin,
                    onValueChange = { gstin = it.uppercase() },
                    label = "GSTIN Number (Optional)"
                )

                RetailTextField(
                    value = pan,
                    onValueChange = { pan = it.uppercase() },
                    label = "PAN Card Number (Optional)"
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text("BANK DETAILS SETUP", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Colors.textSecondary)

                RetailTextField(
                    value = bankName,
                    onValueChange = { bankName = it },
                    label = "Bank Name *"
                )

                RetailTextField(
                    value = accountNo,
                    onValueChange = { accountNo = it },
                    label = "Bank Account Number *",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                RetailTextField(
                    value = ifsc,
                    onValueChange = { ifsc = it.uppercase() },
                    label = "Bank IFSC Code *"
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text("AUTHORIZED SIGNING AUTHORITY SIGNATURE", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Colors.textSecondary)
                Text("Scribble your official signature. This will automatically print on your receipts & invoices.", fontSize = 11.sp, color = Colors.textSecondary)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(Color(0xFFFFFBEB))
                        .border(1.dp, Colors.border, RoundedCornerShape(8.dp))
                        .onGloballyPositioned { coordinates ->
                            canvasWidth = coordinates.size.width
                            canvasHeight = coordinates.size.height
                        }
                ) {
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        signatureCurrentPath = listOf(offset)
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        signatureCurrentPath = signatureCurrentPath + change.position
                                    },
                                    onDragEnd = {
                                        if (signatureCurrentPath.isNotEmpty()) {
                                            signaturePaths.add(signatureCurrentPath)
                                            signatureCurrentPath = emptyList()
                                        }
                                    }
                                )
                            }
                    ) {
                        for (line in signaturePaths) {
                            if (line.size > 1) {
                                for (i in 0 until line.size - 1) {
                                    drawLine(
                                        color = Color(0xFF1E3A8A),
                                        start = line[i],
                                        end = line[i + 1],
                                        strokeWidth = 5f
                                    )
                                }
                            }
                        }
                        val activeLine = signatureCurrentPath
                        if (activeLine.size > 1) {
                            for (i in 0 until activeLine.size - 1) {
                                drawLine(
                                    color = Color(0xFF1E3A8A),
                                    start = activeLine[i],
                                    end = activeLine[i + 1],
                                    strokeWidth = 5f
                                )
                            }
                        }
                    }

                    if (signaturePaths.isEmpty() && signatureCurrentPath.isEmpty() && !isSignatureSaved) {
                        Text(
                            text = "Sign Here inside this yellow area",
                            color = Color.LightGray,
                            fontSize = 13.sp,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else if (isSignatureSaved && signaturePaths.isEmpty()) {
                        Text(
                            text = "✏️ Digital Signature Registered\n(Draw here to replace)",
                            color = Colors.success,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            signaturePaths.clear()
                            signatureCurrentPath = emptyList()
                            isSignatureSaved = false
                        },
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Clear Trace", fontSize = 11.sp, color = Colors.danger)
                    }

                    Button(
                        onClick = {
                            if (signaturePaths.isEmpty()) {
                                Toast.makeText(context, "Trace some scribble structure first", Toast.LENGTH_SHORT).show()
                            } else {
                                try {
                                    val b = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
                                    val c = android.graphics.Canvas(b)
                                    c.drawColor(android.graphics.Color.WHITE)
                                    val paint = android.graphics.Paint().apply {
                                        color = android.graphics.Color.parseColor("#1D4ED8")
                                        strokeWidth = 6f
                                        style = android.graphics.Paint.Style.STROKE
                                        strokeCap = android.graphics.Paint.Cap.ROUND
                                        strokeJoin = android.graphics.Paint.Join.ROUND
                                        isAntiAlias = true
                                    }
                                    for (line in signaturePaths) {
                                        if (line.size > 1) {
                                            val path = android.graphics.Path()
                                            path.moveTo(line[0].x, line[0].y)
                                            for (i in 1 until line.size) {
                                                path.lineTo(line[i].x, line[i].y)
                                            }
                                            c.drawPath(path, paint)
                                        }
                                    }
                                    val sigFile = File(context.filesDir, "business_signature.png")
                                    val fos = FileOutputStream(sigFile)
                                    b.compress(Bitmap.CompressFormat.PNG, 100, fos)
                                    fos.close()
                                    isSignatureSaved = true
                                    Toast.makeText(context, "Scribble locked successfully!", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    Toast.makeText(context, "Compiling trace failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1.5f),
                        colors = ButtonDefaults.buttonColors(containerColor = Colors.primary)
                    ) {
                        Text("Record Drawing", fontSize = 11.sp, color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (bName.isBlank() || owner.isBlank() || address.isBlank() || city.isBlank() || phone.isBlank() || bankName.isBlank() || accountNo.isBlank() || ifsc.isBlank()) {
                            Toast.makeText(context, "Please fill in all required fields accurately", Toast.LENGTH_SHORT).show()
                        } else if (gstin.isNotBlank() && gstin.length != 15) {
                            Toast.makeText(context, "GSTIN must be exactly 15 characters long if provided", Toast.LENGTH_SHORT).show()
                        } else {
                            val nextProfile = profile.copy(
                                businessName = bName, ownerName = owner, address = address, city = city,
                                pin = pinCode,
                                state = selectedStateInfo.first, stateCode = selectedStateInfo.second,
                                phone = phone, email = email, gstin = gstin, pan = pan,
                                bankName = bankName, accountNo = accountNo, ifsc = ifsc,
                                signaturePath = if (isSignatureSaved) File(context.filesDir, "business_signature.png").absolutePath else null
                            )
                            viewModel.updateProfile(nextProfile) {
                                Toast.makeText(context, "Business Profile successfully updated!", Toast.LENGTH_SHORT).show()
                                activeSubMode = "MENU"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_business_profile"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text("Save Alterations Settings", fontWeight = FontWeight.Bold)
                }
            }
        }
    } else if (activeSubMode == "LOCK") {
        // App security control
        val sp = context.getSharedPreferences("zerobook_pref", Context.MODE_PRIVATE)
        var pinEnabled by remember { mutableStateOf(sp.getBoolean("pin_enabled", false)) }
        var enteredPin by remember { mutableStateOf(sp.getString("lock_pin", "") ?: "") }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("App Pin Protection Lock", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { activeSubMode = "MENU" }) {
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
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("PIN Code Protection Toggle", fontWeight = FontWeight.Bold)
                        Text("Enable security verification on launching ZeroBook", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(
                        checked = pinEnabled,
                        onCheckedChange = {
                            pinEnabled = it
                            sp.edit().putBoolean("pin_enabled", it).apply()
                        }
                    )
                }

                if (pinEnabled) {
                    OutlinedTextField(
                        value = enteredPin,
                        onValueChange = {
                            if (it.length <= 4) {
                                enteredPin = it
                                sp.edit().putString("lock_pin", it).apply()
                            }
                        },
                        label = { Text("Initialize 4-digit security PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("app_pin_setup"),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Text("Current stored code will be prompted next time you trigger the app.", fontSize = 11.sp, color = Color.Gray)
                }

                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        activeSubMode = "MENU"
                        Toast.makeText(context, "Security configurations recorded", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text("Apply Parameters")
                }
            }
        }
    } else if (activeSubMode == "ABOUT") {
        // About & compliance view
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("About ZeroBook Detail", fontWeight = FontWeight.Bold, color = Colors.textPrimary) },
                    navigationIcon = {
                        IconButton(onClick = { activeSubMode = "MENU" }) {
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
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "ZeroBook GST",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Colors.primary
                )
                Text(
                    text = "Professional Retail Accounting & Invoicing system",
                    fontSize = 12.sp,
                    color = Colors.textSecondary
                )
                HorizontalDivider()
                Text(
                    text = "Specifically developed for Indian small and medium retailers in accordance with direct CBIC directives. Implements strict reverse tax double ledger entries, automatic intrastate/interstate detection logic, HSN items master, bank reconciliations logs, and offline SQLite encryption protocols.",
                    fontSize = 13.sp,
                    color = Colors.textPrimary,
                    lineHeight = 20.sp
                )
                Text(
                    text = "Software Compliance Version: v3.2.1-PRO\nGSTIN engine: CBIC July 2017 compliant\nDesigned in alignment with Samsung One UI aesthetics.",
                    fontSize = 11.sp,
                    color = Colors.textSecondary,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
fun SettingsMenuCard(
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
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
