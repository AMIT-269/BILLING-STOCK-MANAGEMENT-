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
import androidx.compose.ui.text.input.KeyboardCapitalization
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
                    en = "Enter your registered Mobile Number and GST No. to set a new security code.",
                    gu = "તમારો રજીસ્ટર્ડ મોબાઈલ નંબર અને જીએસટી નંબર દાખલ કરી નવો કોડ સેટ કરો."
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
                    // 1. Registered Mobile Number (Exactly 10 digits)
                    OutlinedTextField(
                        value = mobileNumber,
                        onValueChange = {
                            mobileNumber = PhoneUtil.sanitizePhoneInput(it)
                            errorMessage = null
                        },
                        label = { Text("${loc(en = "Registered Mobile Number", gu = "રજીસ્ટર્ડ મોબાઈલ નંબર")} *") },
                        placeholder = { Text(loc("10-digit mobile number", "૧૦-અંકનો મોબાઈલ નંબર")) },
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

                    // 2. GST Number — REQUIRED
                    OutlinedTextField(
                        value = gstNumber,
                        onValueChange = {
                            gstNumber = PhoneUtil.normalizeGst(it)
                            errorMessage = null
                        },
                        label = { Text("${AppStrings.gstNumber()} *") },
                        placeholder = { Text(loc(en = "Enter GST Number", gu = "જીએસટી નંબર દાખલ કરો")) },
                        leadingIcon = {
                            Icon(Icons.Default.Receipt, contentDescription = null, tint = GoldDark)
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            capitalization = KeyboardCapitalization.Characters
                        ),
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
                        placeholder = { Text(loc("4-digit security code", "૪-અંકનો સુરક્ષા કોડ")) },
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
                        placeholder = { Text(loc("Re-enter 4-digit code", "૪-અંકનો કોડ ફરીથી દાખલ કરો")) },
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
                            val cleanMobile = PhoneUtil.normalizeMobile(mobileNumber)
                            val cleanGst = PhoneUtil.normalizeGst(gstNumber)
                            val cleanNew = PhoneUtil.normalizeCode(newCode4Digit)
                            val cleanConfirm = PhoneUtil.normalizeCode(confirmNewCode)

                            if (cleanMobile.length != 10) {
                                errorMessage = loc(en = "Please enter a valid 10-digit mobile number.", gu = "કૃપા કરીને માન્ય 10 અંકનો મોબાઈલ નંબર દાખલ કરો.")
                                return@Button
                            }
                            if (cleanGst.isEmpty()) {
                                errorMessage = loc(en = "Please enter GST number.", gu = "જીએસટી નંબર દાખલ કરો.")
                                return@Button
                            }
                            if (cleanNew.length != 4) {
                                errorMessage = loc(en = "Please enter 4-digit code.", gu = "4 અંકનો નવો કોડ દાખલ કરો.")
                                return@Button
                            }
                            if (cleanNew != cleanConfirm) {
                                errorMessage = loc(en = "Codes do not match.", gu = "બંને કોડ મેળ ખાતા નથી.")
                                return@Button
                            }

                            viewModel.forgotPassword(
                                mobile = cleanMobile,
                                gstNumber = cleanGst,
                                newCode = cleanNew,
                                confirmCode = cleanConfirm,
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
