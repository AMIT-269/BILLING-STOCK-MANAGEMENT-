package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.JewelleryTopBar
import com.example.ui.locale.AppLanguage
import com.example.ui.locale.AppStrings
import com.example.ui.locale.LanguageManager
import com.example.ui.locale.isAppGujarati
import com.example.ui.locale.loc
import com.example.ui.theme.*
import com.example.ui.viewmodel.JewelleryViewModel
import com.example.util.ImageHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: JewelleryViewModel,
    onNavigateBack: () -> Unit,
    onAccountDeleted: () -> Unit = onNavigateBack
) {
    val context = LocalContext.current
    val account by viewModel.currentAccount.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isGu = isAppGujarati()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteConfirmCode by remember { mutableStateOf("") }
    var deleteError by remember { mutableStateOf<String?>(null) }

    var jewellerName by remember(settings) { mutableStateOf(settings?.jewellerName ?: account?.jewellerName ?: "") }
    var address by remember(settings) { mutableStateOf(settings?.address ?: "") }
    var gstNumber by remember(settings) { mutableStateOf(settings?.gstNumber ?: "") }
    var contactNumber by remember(settings) { mutableStateOf(settings?.contactNumber ?: account?.mobileNumber ?: "") }
    var silverRateText by remember(settings) {
        mutableStateOf(
            if (settings != null && settings!!.silverRate > 0) LanguageManager.formatDouble(settings!!.silverRate, 0) else ""
        )
    }
    var currentLogoBase64 by remember(settings) { mutableStateOf(settings?.logoBase64) }
    var selectedLanguage by remember(settings) { mutableStateOf(settings?.language ?: "en") }

    // Google Play Policy compliant zero-permission Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val base64 = ImageHelper.uriToBase64(context, uri)
            if (base64 != null) {
                currentLogoBase64 = base64
            }
        }
    }

    val logoBitmap = remember(currentLogoBase64) {
        ImageHelper.base64ToBitmap(currentLogoBase64)
    }

    val wtUnit = if (isGu) "ગ્રામ" else "g"

    Scaffold(
        topBar = {
            JewelleryTopBar(
                title = AppStrings.settings(),
                showBackButton = true,
                onBackClick = onNavigateBack,
                logoBase64 = currentLogoBase64,
                syncStatus = syncStatus,
                onSyncClick = { viewModel.manualSync() }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Choose Language Card (Supports all 13 languages)
            var languageExpanded by remember { mutableStateOf(false) }
            val currentAppLang = remember(selectedLanguage) { AppLanguage.fromCode(selectedLanguage) }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Language, contentDescription = null, tint = GoldDark)
                        Text(
                            text = AppStrings.chooseLanguage(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    // Language Selector Dropdown Box
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedCard(
                            onClick = { languageExpanded = !languageExpanded },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_language_selector"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = GoldPrimary.copy(alpha = 0.06f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = currentAppLang.label,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = currentAppLang.englishName,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = if (languageExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                    contentDescription = "Select Language",
                                    tint = GoldDark
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = languageExpanded,
                            onDismissRequest = { languageExpanded = false },
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .heightIn(max = 350.dp)
                        ) {
                            AppLanguage.entries.forEach { lang ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${lang.label} (${lang.englishName})",
                                                fontWeight = if (selectedLanguage == lang.code) FontWeight.Bold else FontWeight.Normal,
                                                color = if (selectedLanguage == lang.code) GoldDark else MaterialTheme.colorScheme.onSurface
                                            )
                                            if (selectedLanguage == lang.code) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = GoldDark, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    },
                                    onClick = {
                                        selectedLanguage = lang.code
                                        viewModel.updateLanguage(lang.code)
                                        languageExpanded = false
                                    },
                                    modifier = Modifier.testTag("lang_item_${lang.code}")
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Shop Logo Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = AppStrings.shopLogo(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .border(2.dp, GoldPrimary, CircleShape)
                            .clickable {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (logoBitmap != null) {
                            Image(
                                bitmap = logoBitmap.asImageBitmap(),
                                contentDescription = "Logo",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = "Add Logo",
                                tint = GoldDark,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.testTag("upload_logo_btn")
                    ) {
                        Text(AppStrings.changeLogo(), color = GoldDark, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Shop Profile Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = AppStrings.shopProfile(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = jewellerName,
                        onValueChange = { jewellerName = it },
                        label = { Text("${AppStrings.shopName()} *") },
                        modifier = Modifier.fillMaxWidth().testTag("settings_name"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text(AppStrings.shopAddress()) },
                        modifier = Modifier.fillMaxWidth().testTag("settings_address"),
                        maxLines = 3
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = contactNumber,
                        onValueChange = { contactNumber = it },
                        label = { Text(AppStrings.contactNumber()) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth().testTag("settings_contact"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = gstNumber,
                        onValueChange = { gstNumber = it.uppercase() },
                        label = { Text(AppStrings.gstNumber()) },
                        supportingText = {
                            Text(
                                loc(
                                    en = "Note: GST number only prints on GST bills.",
                                    gu = "નોંધ: GST નંબર ફક્ત GST બિલમાં જ પ્રિન્ટ થશે."
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth().testTag("settings_gst"),
                        singleLine = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Daily Silver Price Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PriceChange, contentDescription = null, tint = GoldDark)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = AppStrings.dailyRates(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = silverRateText,
                        onValueChange = { silverRateText = it },
                        label = { Text(loc(en = "Silver Price (₹/kg)", gu = "ચાંદી ભાવ (₹/કિલો)")) },
                        placeholder = { Text(loc(en = "Enter Silver Price in ₹/kg (e.g. 90000)", gu = "ચાંદી ભાવ ₹/કિલો દાખલ કરો (દા.ત. 90000)")) },
                        leadingIcon = {
                            Icon(Icons.Default.CurrencyRupee, contentDescription = null, tint = GoldDark)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        supportingText = {
                            Text(
                                loc(
                                    en = "Default silver rate for bill calculations and auto-conversions (₹/kg).",
                                    gu = "બિલ ગણતરી અને સ્વચાલિત કન્વર્ઝન માટે ચાંદીનો ડિફોલ્ટ ભાવ (₹/કિલો)."
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth().testTag("settings_silver_rate"),
                        singleLine = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Cloud Data Sync Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = AppStrings.cloudSync(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = AppStrings.cloudSyncDesc(),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { viewModel.manualSync() },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("settings_manual_sync_btn")
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(AppStrings.syncNow(), fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Change 4-Digit Code Card
            var currentCodeInput by remember { mutableStateOf("") }
            var newCodeInput by remember { mutableStateOf("") }
            var confirmNewCodeInput by remember { mutableStateOf("") }
            var currentCodeVisible by remember { mutableStateOf(false) }
            var newCodeVisible by remember { mutableStateOf(false) }
            var confirmNewCodeVisible by remember { mutableStateOf(false) }
            var codeChangeError by remember { mutableStateOf<String?>(null) }
            var codeChangeSuccess by remember { mutableStateOf<String?>(null) }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.LockReset, contentDescription = null, tint = GoldDark)
                        Text(
                            text = loc(en = "Change 4-Digit Code", gu = "૪-અંકનો સિક્યુરિટી કોડ બદલો"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // 1. Enter Current 4 Digit Code
                    OutlinedTextField(
                        value = currentCodeInput,
                        onValueChange = {
                            if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                                currentCodeInput = it
                                codeChangeError = null
                                codeChangeSuccess = null
                            }
                        },
                        label = { Text(loc(en = "Enter Current 4 Digit Code", gu = "હાલનો ૪ અંકનો કોડ દાખલ કરો") + " *") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = GoldDark)
                        },
                        trailingIcon = {
                            IconButton(onClick = { currentCodeVisible = !currentCodeVisible }) {
                                Icon(
                                    imageVector = if (currentCodeVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (currentCodeVisible) "Hide code" else "Show code",
                                    tint = GoldDark
                                )
                            }
                        },
                        visualTransformation = if (currentCodeVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_current_code"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // 2. Enter New 4 Digit Code
                    OutlinedTextField(
                        value = newCodeInput,
                        onValueChange = {
                            if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                                newCodeInput = it
                                codeChangeError = null
                                codeChangeSuccess = null
                            }
                        },
                        label = { Text(loc(en = "Enter New 4 Digit Code", gu = "નવો ૪ અંકનો કોડ દાખલ કરો") + " *") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = GoldDark)
                        },
                        trailingIcon = {
                            IconButton(onClick = { newCodeVisible = !newCodeVisible }) {
                                Icon(
                                    imageVector = if (newCodeVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (newCodeVisible) "Hide code" else "Show code",
                                    tint = GoldDark
                                )
                            }
                        },
                        visualTransformation = if (newCodeVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_new_code"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // 3. Confirm New 4 Digit Code
                    OutlinedTextField(
                        value = confirmNewCodeInput,
                        onValueChange = {
                            if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                                confirmNewCodeInput = it
                                codeChangeError = null
                                codeChangeSuccess = null
                            }
                        },
                        label = { Text(loc(en = "Confirm New 4 Digit Code", gu = "નવો ૪ અંકનો કોડ કન્ફર્મ કરો") + " *") },
                        leadingIcon = {
                            Icon(Icons.Default.LockOpen, contentDescription = null, tint = GoldDark)
                        },
                        trailingIcon = {
                            IconButton(onClick = { confirmNewCodeVisible = !confirmNewCodeVisible }) {
                                Icon(
                                    imageVector = if (confirmNewCodeVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (confirmNewCodeVisible) "Hide code" else "Show code",
                                    tint = GoldDark
                                )
                            }
                        },
                        visualTransformation = if (confirmNewCodeVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_confirm_new_code"),
                        singleLine = true
                    )

                    if (codeChangeError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = codeChangeError!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (codeChangeSuccess != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = codeChangeSuccess!!,
                            color = Color(0xFF15803D),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Save Button
                    Button(
                        onClick = {
                            if (currentCodeInput.length != 4) {
                                codeChangeError = loc(en = "Current code must be 4 digits", gu = "હાલનો કોડ ૪ અંકનો હોવો જોઈએ")
                                return@Button
                            }
                            if (newCodeInput.length != 4) {
                                codeChangeError = loc(en = "New code must be 4 digits", gu = "નવો કોડ ૪ અંકનો હોવો જોઈએ")
                                return@Button
                            }
                            if (newCodeInput != confirmNewCodeInput) {
                                codeChangeError = loc(en = "New codes do not match", gu = "નવા બંને કોડ મેળ ખાતા નથી")
                                return@Button
                            }

                            viewModel.changeSecurityCode(
                                currentCode = currentCodeInput,
                                newCode = newCodeInput,
                                confirmCode = confirmNewCodeInput,
                                onSuccess = {
                                    currentCodeInput = ""
                                    newCodeInput = ""
                                    confirmNewCodeInput = ""
                                    codeChangeError = null
                                    codeChangeSuccess = loc(en = "Code updated successfully!", gu = "સિક્યુરિટી કોડ સફળતાપૂર્વક બદલાઈ ગયો છે!")
                                },
                                onError = { err ->
                                    codeChangeSuccess = null
                                    codeChangeError = err
                                }
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldDark),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("settings_change_code_btn")
                    ) {
                        Text(
                            text = loc(en = "Save Code", gu = "કોડ સાચવો"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    viewModel.saveSettings(
                        name = jewellerName,
                        address = address,
                        gstNumber = gstNumber,
                        logoBase64 = currentLogoBase64,
                        contactNumber = contactNumber,
                        goldRate22k = settings?.goldRate22k ?: 0.0,
                        silverRate = silverRateText.toDoubleOrNull() ?: (settings?.silverRate ?: 0.0),
                        language = selectedLanguage,
                        onSuccess = onNavigateBack
                    )
                },
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = GoldDark),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_settings_btn")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
                } else {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(AppStrings.saveSettings(), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ==========================================
            // ACCOUNT MANAGEMENT & DANGER ZONE
            // ==========================================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_account_danger_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCA5A5))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = loc(en = "Account Danger Zone", gu = "એકાઉન્ટ સેટિંગ્સ & ડિલીટ"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF991B1B)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = loc(
                            en = "Permanently delete your account registration, security code, and local/cloud business records from this device and server. This action cannot be undone.",
                            gu = "તમારું રજીસ્ટ્રેશન, સિક્યુરિટી કોડ અને હિસાબી રેકોર્ડ્સ આ ફોન અને સર્વર પરથી કાયમ માટે ડિલીટ કરો. આ પ્રક્રિયા પૂર્વવત કરી શકાતી નથી."
                        ),
                        fontSize = 12.sp,
                        color = Color(0xFF7F1D1D),
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            deleteConfirmCode = ""
                            deleteError = null
                            showDeleteDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFDC2626),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("settings_delete_account_btn")
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = loc(en = "Delete Account", gu = "એકાઉન્ટ ડિલીટ કરો"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }

    // ==========================================
    // CONFIRM ACCOUNT DELETION DIALOG
    // ==========================================
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isLoading) showDeleteDialog = false
            },
            icon = {
                Icon(
                    Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = Color(0xFFDC2626),
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = loc(en = "Delete Account Permanently?", gu = "એકાઉન્ટ કાયમ માટે ડિલીટ કરવું છે?"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF991B1B)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = loc(
                            en = "Are you sure you want to delete account '${account?.jewellerName}' (${account?.mobileNumber})? Enter your 4-digit security code to confirm:",
                            gu = "શું તમે ખરેખર '${account?.jewellerName}' (${account?.mobileNumber}) નું એકાઉન્ટ ડિલીટ કરવા માંગો છો? પુષ્ટિ કરવા માટે તમારો 4-અંકનો સિક્યુરિટી કોડ દાખલ કરો:"
                        ),
                        fontSize = 13.sp,
                        color = Color(0xFF334155)
                    )

                    OutlinedTextField(
                        value = deleteConfirmCode,
                        onValueChange = {
                            if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                                deleteConfirmCode = it
                                deleteError = null
                            }
                        },
                        label = { Text(loc(en = "4-Digit Security Code", gu = "4-અંકનો સિક્યુરિટી કોડ")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("delete_account_code_input")
                    )

                    if (deleteError != null) {
                        Text(
                            text = deleteError!!,
                            color = Color(0xFFDC2626),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (deleteConfirmCode.length != 4) {
                            deleteError = loc(en = "Please enter 4-digit code", gu = "કૃપા કરીને 4-અંકનો કોડ દાખલ કરો")
                            return@Button
                        }
                        viewModel.deleteAccount(
                            confirmCode = deleteConfirmCode,
                            onDeleted = {
                                showDeleteDialog = false
                                onAccountDeleted()
                            },
                            onError = { err ->
                                deleteError = err
                            }
                        )
                    },
                    enabled = !isLoading && deleteConfirmCode.length == 4,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    modifier = Modifier.testTag("confirm_delete_account_btn")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                    } else {
                        Text(loc(en = "Yes, Delete", gu = "હા, ડિલીટ કરો"), fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false },
                    enabled = !isLoading
                ) {
                    Text(loc(en = "Cancel", gu = "રદ કરો"), color = Color(0xFF64748B))
                }
            }
        )
    }
}
