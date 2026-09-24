package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.locale.AppStrings
import com.example.ui.locale.loc
import com.example.ui.theme.GoldDark
import com.example.ui.theme.GoldPrimary
import com.example.ui.viewmodel.JewelleryViewModel
import com.example.util.PhoneUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(
    viewModel: JewelleryViewModel,
    onNavigateBack: () -> Unit,
    onResetSuccess: () -> Unit
) {
    var jewellerName by remember { mutableStateOf("") }
    var mobileNumber by remember { mutableStateOf("") }
    var gstNumber by remember { mutableStateOf("") }
    var newCode4Digit by remember { mutableStateOf("") }
    var confirmNewCode by remember { mutableStateOf("") }
    var codeVisible by remember { mutableStateOf(false) }
    var confirmCodeVisible by remember { mutableStateOf(false) }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    val isLoading by viewModel.isLoading.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(loc(en = "Reset Code", gu = "પાસવર્ડ રીસેટ")) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("forgot_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = AppStrings.back())
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = GoldPrimary.copy(alpha = 0.15f),
                modifier = Modifier.size(60.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.LockReset,
                        contentDescription = "Reset",
                        tint = GoldDark,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = loc(en = "Reset 4-Digit Security Code", gu = "4-અંકનો સિક્યુરિટી કોડ રીસેટ કરો"),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = loc(
                    en = "Enter your workshop name and registered mobile to set a new security code.",
                    gu = "તમારું ઝવેરી નામ અને રજીસ્ટર્ડ મોબાઈલ દાખલ કરી નવો કોડ સેટ કરો."
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // 1. Workshop / Jewellers Name
                    OutlinedTextField(
                        value = jewellerName,
                        onValueChange = {
                            jewellerName = it
                            errorMessage = null
                        },
                        label = { Text("${AppStrings.shopName()} *") },
                        placeholder = { Text(loc(en = "Enter Workshop or Jeweller Name", gu = "ઝવેરીનું નામ દાખલ કરો")) },
                        leadingIcon = {
                            Icon(Icons.Default.Storefront, contentDescription = null, tint = GoldDark)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("forgot_jeweller_name"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 2. Registered Mobile Number
                    OutlinedTextField(
                        value = mobileNumber,
                        onValueChange = {
                            mobileNumber = PhoneUtil.sanitizePhoneInput(it)
                            errorMessage = null
                        },
                        label = { Text("${loc(en = "Registered Mobile Number", gu = "રજીસ્ટર્ડ મોબાઈલ નંબર")} *") },
                        leadingIcon = {
                            Icon(Icons.Default.Phone, contentDescription = null, tint = GoldDark)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("forgot_mobile_number"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // GST Number — REQUIRED
                    OutlinedTextField(
                        value = gstNumber,
                        onValueChange = {
                            gstNumber = it.uppercase()
                            errorMessage = null
                        },
                        label = { Text("${AppStrings.gstNumber()} *") },
                        placeholder = { Text(loc(en = "Enter GST Number", gu = "જીએસટી નંબર દાખલ કરો")) },
                        leadingIcon = {
                            Icon(Icons.Default.Receipt, contentDescription = null, tint = GoldDark)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("forgot_gst_number"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 3. New 4 Digit Code — REQUIRED
                    OutlinedTextField(
                        value = newCode4Digit,
                        onValueChange = {
                            newCode4Digit = PhoneUtil.sanitizeCodeInput(it)
                            errorMessage = null
                        },
                        label = { Text("${loc(en = "Set New 4 Digit Code", gu = "નવો 4 અંકનો કોડ")} *") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = GoldDark)
                        },
                        trailingIcon = {
                            IconButton(onClick = { codeVisible = !codeVisible }) {
                                Icon(
                                    imageVector = if (codeVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (codeVisible) "Hide code" else "Show code",
                                    tint = GoldDark
                                )
                            }
                        },
                        visualTransformation = if (codeVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("forgot_new_code"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 4. Confirm New 4 Digit Code — REQUIRED
                    OutlinedTextField(
                        value = confirmNewCode,
                        onValueChange = {
                            confirmNewCode = PhoneUtil.sanitizeCodeInput(it)
                            errorMessage = null
                        },
                        label = { Text("${loc(en = "Confirm New 4 Digit Code", gu = "નવો કોડ કન્ફર્મ કરો")} *") },
                        leadingIcon = {
                            Icon(Icons.Default.LockOpen, contentDescription = null, tint = GoldDark)
                        },
                        trailingIcon = {
                            IconButton(onClick = { confirmCodeVisible = !confirmCodeVisible }) {
                                Icon(
                                    imageVector = if (confirmCodeVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (confirmCodeVisible) "Hide code" else "Show code",
                                    tint = GoldDark
                                )
                            }
                        },
                        visualTransformation = if (confirmCodeVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("forgot_confirm_code"),
                        singleLine = true
                    )

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            if (jewellerName.trim().isEmpty()) {
                                errorMessage = loc(en = "Please enter jeweller name.", gu = "ઝવેરીનું નામ દાખલ કરો.")
                                return@Button
                            }
                            if (mobileNumber.trim().isEmpty()) {
                                errorMessage = loc(en = "Please enter mobile number.", gu = "મોબાઈલ નંબર દાખલ કરો.")
                                return@Button
                            }
                            if (gstNumber.trim().isEmpty()) {
                                errorMessage = loc(en = "Please enter GST number.", gu = "જીએસટી નંબર દાખલ કરો.")
                                return@Button
                            }
                            if (newCode4Digit.length != 4) {
                                errorMessage = loc(en = "Please enter 4-digit code.", gu = "4 અંકનો નવો કોડ દાખલ કરો.")
                                return@Button
                            }
                            if (newCode4Digit != confirmNewCode) {
                                errorMessage = loc(en = "Codes do not match.", gu = "બંને કોડ મેળ ખાતા નથી.")
                                return@Button
                            }

                            viewModel.forgotPassword(
                                name = jewellerName,
                                mobile = mobileNumber,
                                gstNumber = gstNumber,
                                newCode = newCode4Digit,
                                confirmCode = confirmNewCode,
                                onSuccess = onResetSuccess,
                                onError = { err ->
                                    errorMessage = err
                                }
                            )
                        },
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = GoldDark),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("forgot_submit_btn")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
                        } else {
                            Text(
                                text = loc(en = "Update Code", gu = "કોડ અપડેટ કરો"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
