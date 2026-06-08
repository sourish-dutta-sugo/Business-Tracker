package com.example.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Send
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.clip
import coil.compose.AsyncImage
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.*
import com.example.ui.AppViewModel
import com.example.ui.theme.AppColors
import com.example.ui.theme.Colors
import com.example.ui.theme.GstinValidationFeedback
import com.example.ui.theme.LocalAppTheme
import com.example.ui.theme.ThemeViewModel
import com.example.ui.theme.parseGstinInput
import com.example.ui.theme.*
import com.example.utils.copyUriToInternalStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    themeViewModel: ThemeViewModel,
    googleSyncState: GoogleSyncUiState,
    isDesktop: Boolean = false,
    navigateToProducts: () -> Unit,
    navigateToLedgerBooks: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val origProfile by viewModel.profile.collectAsState()
    val currentTheme by themeViewModel.currentTheme.collectAsState()

    var activeSubMode by remember { mutableStateOf(if (isDesktop) "BUSINESS" else "MENU") }

    @Composable
    fun DetailContent() {
        if (activeSubMode == "BUSINESS") {
            // Business Profile Form Editor
            val profile = origProfile ?: BusinessProfile(
                businessName = "", ownerName = "", address = "", city = "", state = "West Bengal", stateCode = "19",
                pin = "", phone = "", email = "", gstin = "", pan = "", bankName = "", accountNo = "", ifsc = "",
                logoPath = null, signaturePath = null
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
            var gstinValid by remember { mutableStateOf<Boolean?>(null) }
            var pan by remember { mutableStateOf(profile.pan) }
            var bankName by remember { mutableStateOf(profile.bankName) }
            var accountNo by remember { mutableStateOf(profile.accountNo) }
            var ifsc by remember { mutableStateOf(profile.ifsc) }
            var bankBranch by remember { mutableStateOf(profile.branchName) }
            var isIfscLoading by remember { mutableStateOf(false) }
            var ifscVerifiedMessage by remember { mutableStateOf("") }
            var isPinLoading by remember { mutableStateOf(false) }
            var pinLookupMessage by remember { mutableStateOf("") }
            var pinLookupSuccess by remember { mutableStateOf(false) }
            var stateDropdownExpanded by remember { mutableStateOf(false) }
            var logoPath by remember { mutableStateOf(profile.logoPath) }
            var uploadedSignaturePath by remember { mutableStateOf(profile.signaturePath) }
            val profileScope = rememberCoroutineScope()
            val formScroll = rememberScrollState()

            LaunchedEffect(pinCode) {
                if (pinCode.length == 6 && pinCode.all { it.isDigit() }) {
                    delay(300)
                    isPinLoading = true
                    val result = fetchPinLookup(pinCode)
                    isPinLoading = false
                    if (result != null) {
                        city = result.city
                        pinLookupMessage = "City: ${result.city}, ${result.state}"
                        pinLookupSuccess = true
                        Utils.INDIAN_STATES.find {
                            it.second == result.stateCode || it.first.equals(result.state, ignoreCase = true)
                        }?.let { selectedStateInfo = it }
                    } else {
                        pinLookupMessage = "City not found — enter manually"
                        pinLookupSuccess = false
                    }
                } else {
                    isPinLoading = false
                    pinLookupMessage = ""
                    pinLookupSuccess = false
                }
            }

            val signatureUploadLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    profileScope.launch {
                        val ext = when {
                            context.contentResolver.getType(uri)?.contains("pdf") == true -> ".pdf"
                            else -> ".jpg"
                        }
                        val path = withContext(kotlinx.coroutines.Dispatchers.IO) {
                            com.example.utils.copyUriToInternalStorage(
                                context, uri,
                                "business_signature_${System.currentTimeMillis()}$ext"
                            )
                        }
                        path?.let {
                            uploadedSignaturePath = it
                            android.widget.Toast.makeText(context, "Signature uploaded!", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

        Scaffold(
            containerColor = AppColors.screenBg,
            topBar = {
                TopAppBar(
                    title = { Text("Business Profile", fontWeight = FontWeight.Bold, color = AppColors.textPrimary) },
                    navigationIcon = {
                        if (!isDesktop) {
                            IconButton(onClick = { activeSubMode = "MENU" }) {
                                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = AppColors.textPrimary)
                            }
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
                    .verticalScroll(formScroll)
                    .imePadding()
                    .padding(innerPadding)
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 80.dp),
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RetailTextField(
                        value = pinCode,
                        onValueChange = { pinCode = it.filter(Char::isDigit).take(6) },
                        label = "PIN Code *",
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        trailingIcon = {
                            if (isPinLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            }
                        }
                    )

                    RetailTextField(
                        value = city,
                        onValueChange = { city = it },
                        label = "City *",
                        modifier = Modifier.weight(1f)
                    )
                }

                if (pinLookupMessage.isNotBlank()) {
                    Text(
                        text = pinLookupMessage,
                        color = if (pinLookupSuccess) Color(0xFF2E7D32) else Color.Gray,
                        fontSize = 11.sp
                    )
                }

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
                                tint = AppColors.textSecondary,
                                modifier = Modifier.clickable { stateDropdownExpanded = true }
                            )
                        }
                    )
                    DropdownMenu(
                        expanded = stateDropdownExpanded,
                        onDismissRequest = { stateDropdownExpanded = false },
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .height(240.dp)
                            .background(AppColors.cardBg)
                    ) {
                        Utils.INDIAN_STATES.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item.first, color = AppColors.textPrimary) },
                                onClick = {
                                    selectedStateInfo = item
                                    stateDropdownExpanded = false
                                },
                                colors = MenuDefaults.itemColors(textColor = AppColors.textPrimary)
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

                Text(
                    "GSTIN Number (Optional)",
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
                    colors = com.example.ui.theme.zeroBookInputColors(),
                    singleLine = true
                )
                GstinValidationFeedback(
                    gstin, gstinValid, pan, selectedStateInfo.first, selectedStateInfo.second
                )

                RetailTextField(
                    value = pan,
                    onValueChange = { pan = it.uppercase() },
                    label = "PAN Card Number (Optional)"
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text("BANK DETAILS SETUP", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = AppColors.textSecondary)

                RetailTextField(
                    value = accountNo,
                    onValueChange = { accountNo = it.filter(Char::isDigit) },
                    label = "Account Number *",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                RetailTextField(
                    value = ifsc,
                    onValueChange = { input ->
                        val uppercaseIn = input.uppercase().replace("\\s".toRegex(), "")
                        if (uppercaseIn.length <= 11) {
                            ifsc = uppercaseIn
                            ifscVerifiedMessage = ""
                            if (uppercaseIn.length == 11) {
                                isIfscLoading = true
                                viewModel.fetchIfscDetails(uppercaseIn) { resolvedBank, resolvedBranch ->
                                    isIfscLoading = false
                                    if (!resolvedBank.isNullOrBlank()) {
                                        bankName = resolvedBank
                                        bankBranch = resolvedBranch.orEmpty()
                                        ifscVerifiedMessage = buildString {
                                            append("Bank verified: ")
                                            append(resolvedBank)
                                            if (!resolvedBranch.isNullOrBlank()) {
                                                append(", ")
                                                append(resolvedBranch)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    label = "IFSC Code *",
                    trailingIcon = {
                        if (isIfscLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        }
                    }
                )

                if (ifscVerifiedMessage.isNotBlank()) {
                    Text(ifscVerifiedMessage, color = Color(0xFF2E7D32), fontSize = 12.sp)
                }

                RetailTextField(
                    value = bankName,
                    onValueChange = { bankName = it },
                    label = "Bank Name *"
                )

                RetailTextField(
                    value = bankBranch,
                    onValueChange = { bankBranch = it },
                    label = "Bank Branch"
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text("BRANDING & INVOICE IMAGES", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = AppColors.textSecondary)

                BusinessImageUploadSection(
                    title = "Business Logo",
                    subtitle = "Appears on invoice header. PNG or JPG recommended.",
                    currentPath = logoPath,
                    onImageSelected = { uri ->
                        profileScope.launch {
                            val path = withContext(Dispatchers.IO) {
                                copyUriToInternalStorage(
                                    context, uri,
                                    "business_logo_${System.currentTimeMillis()}.jpg"
                                )
                            }
                            path?.let { logoPath = it }
                        }
                    }
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AppColors.cardBg),
                    border = BorderStroke(1.dp, AppColors.border),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Authorized Signature",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = com.example.ui.theme.TextDark
                        )
                        Text(
                            "Upload a photo of your signature",
                            fontSize = 11.sp,
                            color = AppColors.textSecondary,
                            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                        )
                        val sigFile = uploadedSignaturePath?.let { java.io.File(it) }
                        if (sigFile != null && sigFile.exists() && !sigFile.extension.equals("pdf", ignoreCase = true)) {
                            coil.compose.AsyncImage(
                                model = sigFile,
                                contentDescription = "Signature preview",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(80.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, AppColors.border, RoundedCornerShape(8.dp)),
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        signatureUploadLauncher.launch(arrayOf("image/*", "application/pdf"))
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Change", color = com.example.ui.theme.TextDark, fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = { uploadedSignaturePath = null },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Remove", color = AppColors.error, fontSize = 12.sp)
                                }
                            }
                        } else if (sigFile != null && sigFile.exists()) {
                            Text("PDF signature uploaded: ${sigFile.name}", color = com.example.ui.theme.TextDark, fontSize = 12.sp)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    signatureUploadLauncher.launch(arrayOf("image/*", "application/pdf"))
                                }, modifier = Modifier.weight(1f)) {
                                    Text("Change", color = com.example.ui.theme.TextDark, fontSize = 12.sp)
                                }
                                OutlinedButton(onClick = { uploadedSignaturePath = null }, modifier = Modifier.weight(1f)) {
                                    Text("Remove", color = AppColors.error, fontSize = 12.sp)
                                }
                            }
                        } else {
                            Button(
                                onClick = {
                                    signatureUploadLauncher.launch(arrayOf("image/*", "application/pdf"))
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = AppColors.primary)
                            ) {
                                Text("Upload Signature", color = AppColors.textOnPrimary)
                            }
                        }
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
                                bankName = bankName, accountNo = accountNo, ifsc = ifsc, branchName = bankBranch,
                                logoPath = logoPath,
                                signaturePath = uploadedSignaturePath
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
                        if (!isDesktop) {
                            IconButton(onClick = { activeSubMode = "MENU" }) {
                                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                            }
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
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
                    title = { Text("About ZeroBook Detail", fontWeight = FontWeight.Bold, color = AppColors.textPrimary) },
                    navigationIcon = {
                        if (!isDesktop) {
                            IconButton(onClick = { activeSubMode = "MENU" }) {
                                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = AppColors.textPrimary)
                            }
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
                    .padding(innerPadding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_symbol),
                    contentDescription = "ZeroBook",
                    modifier = Modifier.size(80.dp),
                    contentScale = ContentScale.Fit
                )
                Text(
                    text = "ZeroBook",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.textPrimary
                )
                Text(
                    text = "Record. Transact. Grow.",
                    fontSize = 13.sp,
                    color = AppColors.textSecondary
                )
                Text(
                    text = "Version 1.0.0",
                    fontSize = 12.sp,
                    color = AppColors.textSecondary
                )
                Text(
                    text = "Built for Indian small retailers",
                    fontSize = 11.sp,
                    color = AppColors.textSecondary
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    } else if (activeSubMode == "THEME") {
            Scaffold(
                containerColor = AppColors.screenBg,
                topBar = {
                    TopAppBar(
                        title = { Text("Theme & Colors", fontWeight = FontWeight.Bold, color = AppColors.textPrimary) },
                        navigationIcon = {
                            if (!isDesktop) {
                                IconButton(onClick = { activeSubMode = "MENU" }) {
                                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = AppColors.textPrimary)
                                }
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
                        .padding(innerPadding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = AppColors.cardBg),
                        border = BorderStroke(1.dp, AppColors.border),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "App Appearance",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = AppColors.textPrimary
                            )
                            Text(
                                text = "Pick a theme and the whole app updates immediately.",
                                fontSize = 12.sp,
                                color = AppColors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            ThemePickerRow(
                                selectedThemeName = currentTheme.name,
                                onThemeSelected = themeViewModel::setTheme
                            )
                        }
                    }
                }
            }
        } else if (activeSubMode == "FY") {
        val context = LocalContext.current
        val profile = origProfile ?: BusinessProfile(
            businessName = "", ownerName = "", address = "", city = "", state = "West Bengal", stateCode = "19",
            pin = "", phone = "", email = "", gstin = "", pan = "", bankName = "", accountNo = "", ifsc = "",
            logoPath = null, signaturePath = null
        )
        val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
        val currentFyLabel = (origProfile?.fyLabel?.takeIf { it.isNotBlank() }
            ?: viewModel.financialYear.collectAsState().value)
        var startYearText by remember(currentFyLabel) {
            val yearPart = currentFyLabel.substringBefore("-").toIntOrNull() ?: 2025
            mutableStateOf(yearPart.toString())
        }
        var isSaved by remember { mutableStateOf(false) }

        val startYearVal = startYearText.toIntOrNull()
        val isValid = startYearVal != null && startYearVal in 2000..2099
        val calculatedEndYearText = if (isValid) (startYearVal!! + 1).toString() else ""
        val calculatedLabel = if (isValid) {
            val endYrMod = (startYearVal!! + 1) % 100
            val formattedEndYr = String.format("%02d", endYrMod)
            "$startYearVal-$formattedEndYr"
        } else ""

        val saveAction = {
            if (isValid) {
                viewModel.switchFinancialYear(
                    targetFinancialYear = calculatedLabel,
                    onComplete = { result ->
                        isSaved = true
                        Toast.makeText(
                            context,
                            if (result.restoredExistingData) {
                                "FY ${result.financialYear} loaded from ${result.storagePath}"
                            } else {
                                "FY ${result.financialYear} opened with clean entries."
                            },
                            Toast.LENGTH_SHORT
                        ).show()
                        activity?.recreate()
                    },
                    onError = { message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Financial Year Control", fontWeight = FontWeight.Bold, color = AppColors.textPrimary) },
                    navigationIcon = {
                        if (!isDesktop) {
                            IconButton(onClick = { activeSubMode = "MENU" }) {
                                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = AppColors.textPrimary)
                            }
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
                    .padding(innerPadding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, AppColors.border, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = AppColors.cardBg),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "FINANCIAL YEAR CONFIGURATION",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.primary,
                            letterSpacing = 1.sp
                        )

                        Text(
                            text = "Enter Start Year",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.textPrimary
                        )

                        OutlinedTextField(
                            value = startYearText,
                            onValueChange = { text ->
                                val cleaned = text.replace(Regex("[^0-9]"), "")
                                if (cleaned.length <= 4) {
                                    startYearText = cleaned
                                    isSaved = false
                                }
                            },
                            placeholder = { Text("2025", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = AppColors.textTertiary) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    saveAction()
                                    focusManager.clearFocus()
                                }
                            ),
                            textStyle = TextStyle(
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppColors.primary,
                                textAlign = TextAlign.Center
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = AppColors.screenBg,
                                unfocusedContainerColor = AppColors.screenBg,
                                focusedBorderColor = AppColors.primary,
                                unfocusedBorderColor = AppColors.border
                            ),
                            modifier = Modifier
                                .width(180.dp)
                                .onFocusChanged { focusState ->
                                    if (!focusState.isFocused) {
                                        saveAction()
                                    }
                                }
                        )

                        if (!isValid && startYearText.isNotEmpty()) {
                            Text(
                                text = "Enter a valid year between 2000 and 2099",
                                color = AppColors.error,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        if (isValid) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = AppColors.primary.copy(alpha = 0.05f)),
                                border = BorderStroke(1.dp, AppColors.primary.copy(alpha = 0.2f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Financial Year: $startYearText - $calculatedEndYearText",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                        color = AppColors.textPrimary
                                    )
                                    Text(
                                        text = "Label: $calculatedLabel",
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 13.sp,
                                        color = AppColors.textSecondary
                                    )
                                }
                            }
                        }

                        if (isSaved && isValid) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Saved successfully",
                                    tint = AppColors.credit,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Saved: FY $calculatedLabel",
                                    color = AppColors.credit,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        if (isValid) {
                            Text(
                                text = "Each financial year is stored in its own ZeroBook/$calculatedLabel folder. New FY selections start clean, and earlier FY folders reload their original records.",
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                color = AppColors.textSecondary
                            )
                        }
                    }
                }
                
                Button(
                    onClick = {
                        if (isValid) {
                            saveAction()
                            focusManager.clearFocus()
                        } else {
                            Toast.makeText(context, "Enter a valid year between 2000 and 2099", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.primary)
                ) {
                    Text("Save Alterations Settings", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    } else if (activeSubMode == "EMAIL") {
        val sp = context.getSharedPreferences("zerobook_pref", Context.MODE_PRIVATE)
        var automationEnabled by remember { mutableStateOf(sp.getBoolean("email_automation_enabled", false)) }
        var triggerTimeText by remember { mutableStateOf(sp.getString("email_trigger_time", "09:00 AM") ?: "09:00 AM") }
        var scheduledAt by remember { mutableStateOf(EmailReminderScheduler.loadScheduledAt(context)) }
        var isRunningSim by remember { mutableStateOf(false) }
        
        var logString by remember { mutableStateOf(sp.getString("email_logs_list", "") ?: "") }
        val logsList = remember(logString) {
            if (logString.isBlank()) emptyList<String>() else logString.split("\n")
        }

        val parties by viewModel.parties.collectAsState()
        val bills by viewModel.billsReceivable.collectAsState()
        val scope = rememberCoroutineScope()
        val senderEmail = origProfile?.email?.ifBlank { "yourcompany@example.com" } ?: "yourcompany@example.com"
        val eligibleRecipients = remember(parties, bills) {
            bills
                .filter { it.outstandingAmount > 0.0 }
                .groupBy { it.partyId }
                .mapNotNull { (partyId, groupedBills) ->
                    val party = parties.find { it.id == partyId } ?: return@mapNotNull null
                    if (party.email.isBlank()) return@mapNotNull null
                    ReminderRecipientUi(
                        partyId = party.id,
                        partyName = party.name,
                        email = party.email,
                        dueAmount = groupedBills.sumOf { it.outstandingAmount }
                    )
                }
                .sortedBy { it.partyName.lowercase() }
        }
        val selectedRecipientIds = remember { mutableStateMapOf<String, Boolean>() }
        val scheduleSummary = scheduledAt?.let {
            java.text.SimpleDateFormat("dd-MMM-yyyy hh:mm a", java.util.Locale.ENGLISH).format(java.util.Date(it))
        }.orEmpty()

        LaunchedEffect(eligibleRecipients) {
            val persistedIds = EmailReminderScheduler.loadSelectedRecipients(context)
            selectedRecipientIds.clear()
            eligibleRecipients.forEach { recipient ->
                selectedRecipientIds[recipient.partyId] = if (persistedIds.isEmpty()) true else persistedIds.contains(recipient.partyId)
            }
        }

        val saveSettings = {
            sp.edit()
                .putBoolean("email_automation_enabled", automationEnabled)
                .putString("email_trigger_time", triggerTimeText)
                .apply()
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Email Automation System", fontWeight = FontWeight.Bold, color = AppColors.textPrimary) },
                    navigationIcon = {
                        if (!isDesktop) {
                            IconButton(onClick = { activeSubMode = "MENU" }) {
                                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = AppColors.textPrimary)
                            }
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
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, AppColors.border, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = AppColors.cardBg)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("AUTOMATIC REMINDERS", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AppColors.primary)
                                val subText = if (automationEnabled) "SERVICE STATUS: ACTIVE (Running)" else "SERVICE STATUS: SHUT (Disabled)"
                                Text(subText, fontSize = 11.sp, color = if (automationEnabled) AppColors.credit else AppColors.error)
                            }
                            Switch(
                                checked = automationEnabled,
                                onCheckedChange = {
                                    automationEnabled = it
                                    saveSettings()
                                    if (!it) {
                                        scheduledAt = null
                                        EmailReminderScheduler.schedule(context, null)
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = AppColors.primary,
                                    checkedTrackColor = AppColors.primary.copy(alpha = 0.38f)
                                )
                            )
                        }

                        HorizontalDivider()

                        Text("Sender", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = AppColors.textSecondary)
                        OutlinedTextField(
                            value = senderEmail,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Authenticated company email") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AppColors.primary,
                                unfocusedBorderColor = AppColors.border
                            )
                        )

                        HorizontalDivider()

                        Text("Reminder Recipients", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = AppColors.textSecondary)
                        if (eligibleRecipients.isEmpty()) {
                            Text(
                                "Only customers with email ID and outstanding due amount appear here.",
                                fontSize = 11.sp,
                                color = AppColors.textSecondary
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${eligibleRecipients.count { selectedRecipientIds[it.partyId] == true }} selected",
                                    fontSize = 11.sp,
                                    color = AppColors.textSecondary
                                )
                                TextButton(
                                    onClick = {
                                        val allSelected = eligibleRecipients.all { selectedRecipientIds[it.partyId] == true }
                                        eligibleRecipients.forEach { selectedRecipientIds[it.partyId] = !allSelected }
                                        EmailReminderScheduler.saveSelectedRecipients(
                                            context,
                                            selectedRecipientIds.filterValues { it }.keys
                                        )
                                    }
                                ) {
                                    Text("Select all", color = AppColors.primary)
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                eligibleRecipients.forEach { recipient ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, AppColors.border, RoundedCornerShape(10.dp))
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = selectedRecipientIds[recipient.partyId] == true,
                                            onCheckedChange = { checked ->
                                                selectedRecipientIds[recipient.partyId] = checked
                                                EmailReminderScheduler.saveSelectedRecipients(
                                                    context,
                                                    selectedRecipientIds.filterValues { it }.keys
                                                )
                                            }
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(recipient.partyName, fontWeight = FontWeight.SemiBold, color = AppColors.textPrimary)
                                            Text(recipient.email, fontSize = 11.sp, color = AppColors.textSecondary)
                                        }
                                        Text(
                                            Utils.formatIndianCurrency(recipient.dueAmount),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AppColors.primary
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider()

                        Text("Reminder Scheduling", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = AppColors.textSecondary)
                        Text("Quick Options", fontSize = 11.sp, color = AppColors.textSecondary)
                        val quickOptions = listOf(
                            "Today" to 0,
                            "Tomorrow" to 1,
                            "After 2 Days" to 2,
                            "After 3 Days" to 3,
                            "After 7 Days" to 7
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            quickOptions.forEach { (label, daysToAdd) ->
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        val calendar = Calendar.getInstance()
                                        calendar.add(Calendar.DAY_OF_YEAR, daysToAdd)
                                        calendar.set(Calendar.SECOND, 0)
                                        scheduledAt = calendar.timeInMillis
                                        triggerTimeText = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.ENGLISH)
                                            .format(java.util.Date(calendar.timeInMillis))
                                        saveSettings()
                                        EmailReminderScheduler.schedule(context, scheduledAt)
                                    },
                                    label = { Text(label) }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = triggerTimeText,
                            onValueChange = {
                                triggerTimeText = it
                                saveSettings()
                            },
                            placeholder = { Text("09:00 AM") },
                            singleLine = true,
                            label = { Text("Preferred send time") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AppColors.primary,
                                unfocusedBorderColor = AppColors.border
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Custom Option", fontSize = 11.sp, color = AppColors.textSecondary)
                                Text(
                                    text = if (scheduleSummary.isBlank()) "No reminder scheduled yet" else scheduleSummary,
                                    fontWeight = FontWeight.Medium,
                                    color = AppColors.textPrimary
                                )
                            }
                            TextButton(
                                onClick = {
                                    val calendar = Calendar.getInstance()
                                    DatePickerDialog(
                                        context,
                                        { _, year, month, dayOfMonth ->
                                            val pickedDate = Calendar.getInstance().apply {
                                                set(Calendar.YEAR, year)
                                                set(Calendar.MONTH, month)
                                                set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                            }
                                            TimePickerDialog(
                                                context,
                                                { _, hourOfDay, minute ->
                                                    pickedDate.set(Calendar.HOUR_OF_DAY, hourOfDay)
                                                    pickedDate.set(Calendar.MINUTE, minute)
                                                    pickedDate.set(Calendar.SECOND, 0)
                                                    scheduledAt = pickedDate.timeInMillis
                                                    triggerTimeText = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.ENGLISH)
                                                        .format(java.util.Date(pickedDate.timeInMillis))
                                                    saveSettings()
                                                    EmailReminderScheduler.schedule(context, scheduledAt)
                                                },
                                                calendar.get(Calendar.HOUR_OF_DAY),
                                                calendar.get(Calendar.MINUTE),
                                                false
                                            ).show()
                                        },
                                        calendar.get(Calendar.YEAR),
                                        calendar.get(Calendar.MONTH),
                                        calendar.get(Calendar.DAY_OF_MONTH)
                                    ).show()
                                }
                            ) {
                                Text("Pick date & time", color = AppColors.primary)
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        scope.launch {
                            isRunningSim = true
                            EmailReminderScheduler.saveSelectedRecipients(
                                context,
                                selectedRecipientIds.filterValues { it }.keys
                            )
                            kotlinx.coroutines.delay(600)
                            val sentCount = withContext(Dispatchers.IO) {
                                EmailReminderScheduler.processReminders(context)
                            }
                            logString = sp.getString("email_logs_list", "") ?: ""
                            Toast.makeText(
                                context,
                                if (sentCount > 0) "Reminder run completed for $sentCount recipients" else "No eligible reminders were sent",
                                Toast.LENGTH_SHORT
                            ).show()
                            isRunningSim = false
                            return@launch
                            kotlinx.coroutines.delay(1200) // simulated shoot reminder delay
                            val activeBills = bills.filter { it.outstandingAmount > 0.0 }
                            if (activeBills.isEmpty()) {
                                Toast.makeText(context, "No outstanding bills found to notify", Toast.LENGTH_SHORT).show()
                                val noDuesLog = "INFO: Scanned database | No outstanding bills with pending dues found."
                                val updatedLogs = if (logString.isEmpty()) noDuesLog else "$noDuesLog\n$logString"
                                sp.edit().putString("email_logs_list", updatedLogs).apply()
                                logString = updatedLogs
                            } else {
                                val addedLogs = mutableListOf<String>()
                                for (bill in activeBills) {
                                    val partyObj = parties.find { it.id == bill.partyId }
                                    val emailStr = if (partyObj?.email.isNullOrBlank()) "agarwal@test.com" else partyObj!!.email
                                    val amtFormatted = String.format("₹%,.0f", bill.outstandingAmount)
                                    val formattedTime = triggerTimeText.ifBlank { "09:00 AM" }
                                    val row = "SENT to $emailStr | Amt: $amtFormatted | Status: SUCCESS | Time: $formattedTime"
                                    addedLogs.add(row)
                                }
                                val joinedNewLog = addedLogs.joinToString("\n")
                                val finalLogs = if (logString.isEmpty()) joinedNewLog else "$joinedNewLog\n$logString"
                                sp.edit().putString("email_logs_list", finalLogs).apply()
                                logString = finalLogs
                                Toast.makeText(context, "Notifier process triggered: ${addedLogs.size} reminders sent!", Toast.LENGTH_SHORT).show()
                            }
                            isRunningSim = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.primary),
                    enabled = !isRunningSim
                ) {
                    if (isRunningSim) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Executing simulated emailing...", color = Color.White)
                    } else {
                        Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text("RUN TRIGGER NOW", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, AppColors.border, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = AppColors.cardBg)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("DETECTOR RUN LOGS", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = AppColors.textSecondary)
                            TextButton(onClick = {
                                sp.edit().putString("email_logs_list", "").apply()
                                logString = ""
                            }) {
                                Text("Clear", color = AppColors.error, fontSize = 11.sp)
                            }
                        }

                        HorizontalDivider()

                        if (logsList.isEmpty()) {
                            Text("No emails dispatched yet. Tap RUN TRIGGER NOW to scan active debtors.", fontSize = 11.sp, color = AppColors.textTertiary)
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                logsList.forEach { logLine ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = AppColors.screenBg),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = logLine,
                                            fontSize = 11.sp,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            color = AppColors.textPrimary,
                                            modifier = Modifier.padding(8.dp)
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

    if (isDesktop) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.width(360.dp).fillMaxHeight()) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Application Settings", fontWeight = FontWeight.Bold, color = AppColors.textPrimary) },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.cardBg)
                        )
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        SettingsMenuSection(
                            activeSubMode = activeSubMode,
                            onSelect = { activeSubMode = it },
                            context = context,
                            viewModel = viewModel,
                            googleSyncState = googleSyncState,
                            navigateToProducts = navigateToProducts,
                            navigateToLedgerBooks = navigateToLedgerBooks
                        )
                    }
                }
            }
            Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(AppColors.border))
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                DetailContent()
            }
        }
    } else {
        if (activeSubMode == "MENU") {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Application Settings", fontWeight = FontWeight.Bold, color = AppColors.textPrimary) },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = AppColors.textPrimary)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.cardBg)
                    )
                }
            ) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding)) {
                    SettingsMenuSection(
                        activeSubMode = activeSubMode,
                        onSelect = { activeSubMode = it },
                        context = context,
                        viewModel = viewModel,
                        googleSyncState = googleSyncState,
                        navigateToProducts = navigateToProducts,
                        navigateToLedgerBooks = navigateToLedgerBooks
                    )
                }
            }
        } else {
            DetailContent()
        }
    }
}

@Composable
fun SettingsMenuSection(
    activeSubMode: String,
    onSelect: (String) -> Unit,
    context: android.content.Context,
    viewModel: AppViewModel,
    googleSyncState: GoogleSyncUiState,
    navigateToProducts: () -> Unit,
    navigateToLedgerBooks: () -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.screenBg)
            .verticalScroll(scrollState)
            .imePadding()
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Menu items
        SettingsMenuCard(
            title = "Edit Business Profile",
            description = "Update GSTIN, Address, PAN, signature, and bank details",
            icon = Icons.Default.Business,
            onClick = { onSelect("BUSINESS") }
        )

        SettingsMenuCard(
            title = "Manage Products Master",
            description = "Configure stock prices, units, and standard HSN codes",
            icon = Icons.Default.ShoppingBag,
            onClick = navigateToProducts
        )

        SettingsMenuCard(
                            title = "Ledger Books & Account Heads",
                            description = "View full ledger account list with balances and groups",
                            icon = Icons.Default.AccountBalance,
                            onClick = navigateToLedgerBooks
                        )

        SettingsMenuCard(
            title = "Theme & Colors",
            description = "Switch between Beach, Blue, Green, Purple, and Dark",
            icon = Icons.Default.CheckCircle,
            onClick = { onSelect("THEME") }
        )

        SettingsMenuCard(
            title = "Financial Year Control",
            description = "Configure custom financial year with auto-save and validations",
            icon = Icons.Default.DateRange,
            onClick = { onSelect("FY") }
        )

        SettingsMenuCard(
            title = "Email Automation Control",
            description = "Automate bills outstanding reminders to debtors",
            icon = Icons.Default.Email,
            onClick = { onSelect("EMAIL") }
        )

        SettingsMenuCard(
            title = "About ZeroBook",
            description = "Check compliance versions and regulatory details",
            icon = Icons.Default.Info,
            onClick = { onSelect("ABOUT") }
        )

        SettingsMenuCard(
            title = if (googleSyncState.isSignedIn) "Logout Google Account" else "Connect Google Sync",
            description = if (googleSyncState.isSignedIn) {
                googleSyncState.accountEmail ?: "Logout from the connected Google account"
            } else {
                "Login later to sync progress across your devices"
            },
            icon = Icons.Default.Lock,
            onClick = {
                if (googleSyncState.isSignedIn) {
                    viewModel.logoutGoogleAccount { result ->
                        result.onSuccess {
                            Toast.makeText(context, it.statusMessage ?: "Google account logged out.", Toast.LENGTH_SHORT).show()
                        }.onFailure { error ->
                            Toast.makeText(context, error.localizedMessage ?: "Logout failed.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    viewModel.reopenGoogleSyncPrompt()
                    (context as? android.app.Activity)?.recreate()
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Backup Section Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, AppColors.border, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = AppColors.cardBg)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("BACKUP & RESTORE DATA", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = AppColors.textSecondary)
                Text(
                    "Export your complete SQLite database file directly as an encrypted local backup to safe-keep transaction ledgers.",
                    fontSize = 11.sp,
                    color = AppColors.textSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.exportActiveFinancialYearCsv { path ->
                                Toast.makeText(
                                    context,
                                    if (path != null) "CSV exported to $path" else "Export failed. Choose the ZeroBook folder again if needed.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppColors.primary,
                            contentColor = AppColors.textOnPrimary
                        )
                    ) {
                        Text("Export to CSV", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.restoreActiveFinancialYearFromFolder { restored ->
                                Toast.makeText(
                                    context,
                                    if (restored) "Active FY restored from the ZeroBook folder." else "No saved data file was found for this FY folder.",
                                    Toast.LENGTH_LONG
                                ).show()
                                if (restored) {
                                    (context as? android.app.Activity)?.recreate()
                                }
                            }
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
            .border(1.dp, AppColors.border, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = AppColors.cardBg)
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
                    colors = CardDefaults.cardColors(containerColor = AppColors.primary.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = AppColors.primary,
                        modifier = Modifier.padding(10.dp).size(24.dp)
                    )
                }
                Column {
                    Text(text = title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = AppColors.textPrimary)
                    Text(text = description, color = AppColors.textSecondary, fontSize = 11.sp)
                }
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = AppColors.textTertiary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ThemePickerRow(
    selectedThemeName: String,
    onThemeSelected: (String) -> Unit
) {
    val themeOptions = listOf(
        Triple("BEACH", "Beach", Color(0xFFFDF6EC)),
        Triple("BLUE", "Blue", Color(0xFF1A73E8)),
        Triple("GREEN", "Green", Color(0xFF1E8A3C)),
        Triple("PURPLE", "Purple", Color(0xFF6200EA)),
        Triple("TEAL", "Teal", Color(0xFF0F9D8A))
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        themeOptions.forEach { (name, label, swatch) ->
            val isSelected = selectedThemeName == name
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .weight(1f)
                    .clickable { onThemeSelected(name) }
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(21.dp))
                        .background(swatch)
                        .border(
                            width = if (name == "BEACH") 2.dp else 1.dp,
                            color = when {
                                isSelected -> AppColors.primary
                                name == "BEACH" -> Color(0xFF8D6E63)
                                else -> AppColors.border
                            },
                            shape = RoundedCornerShape(21.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = label,
                            tint = if (name == "BEACH") AppColors.textPrimary else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = AppColors.textSecondary
                )
            }
        }
    }
}
