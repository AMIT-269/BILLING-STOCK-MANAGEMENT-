package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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

@Composable
fun LoginScreen(
    viewModel: JewelleryViewModel,
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToForgotPassword: () -> Unit
) {
    val lastCreds = remember { viewModel.getLastCredentialsTriple() }
    var mobileNumber by remember { mutableStateOf(PhoneUtil.normalizePhone(lastCreds.second)) }
    var code4Digit by remember { mutableStateOf("") }
    var confirmCode4Digit by remember { mutableStateOf("") }
    var codeVisible by remember { mutableStateOf(false) }
    var confirmCodeVisible by remember { mutableStateOf(false) }

    // If initial lastCreds was empty, refresh from triple once on launch
    LaunchedEffect(Unit) {
        if (mobileNumber.isBlank()) {
            val creds = viewModel.getLastCredentialsTriple()
            val clean = PhoneUtil.normalizePhone(creds.second)
            if (clean.isNotBlank()) {
                mobileNumber = clean
            }
        }
    }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    val isLoading by viewModel.isLoading.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
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
            Spacer(modifier = Modifier.height(28.dp))

            // Logo Icon matching new App Icon
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF0F0F14),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, GoldDark),
                modifier = Modifier.size(76.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(6.dp)) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.ic_billing_stock_logo),
                        contentDescription = "Billing & Stock Logo",
                        modifier = Modifier.size(58.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = AppStrings.appTitle(),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = GoldDark
            )

            Text(
                text = AppStrings.login(),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    // Mobile Number — REQUIRED
                    OutlinedTextField(
                        value = mobileNumber,
                        onValueChange = { input ->
                            mobileNumber = PhoneUtil.sanitizePhoneInput(input)
                            errorMessage = null
                        },
                        label = { Text(AppStrings.partyMobile() + " *") },
                        placeholder = { Text(loc("10-digit mobile number", "૧૦-અંકનો મોબાઈલ નંબર")) },
                        leadingIcon = {
                            Icon(Icons.Default.Phone, contentDescription = null, tint = GoldDark)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("login_mobile_number"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 4 Digit Code — REQUIRED
                    OutlinedTextField(
                        value = code4Digit,
                        onValueChange = { input ->
                            code4Digit = PhoneUtil.sanitizeCodeInput(input)
                            errorMessage = null
                        },
                        label = { Text("${AppStrings.securityCode()} *") },
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
                            .testTag("login_4digit_code"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // CONFORM CODE (4 digit) — REQUIRED
                    OutlinedTextField(
                        value = confirmCode4Digit,
                        onValueChange = { input ->
                            confirmCode4Digit = PhoneUtil.sanitizeCodeInput(input)
                            errorMessage = null
                        },
                        label = { Text("${loc(en = "CONFORM CODE (4-digit)", gu = "કન્ફર્મ કોડ (૪ અંક)")} *") },
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
                            .testTag("login_confirm_code"),
                        singleLine = true
                    )

                    // Visible Forgot Password link
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = onNavigateToForgotPassword,
                            modifier = Modifier.testTag("forgot_password_btn")
                        ) {
                            Text(
                                text = AppStrings.forgotPassword(),
                                color = GoldDark,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            if (mobileNumber.trim().isEmpty()) {
                                errorMessage = loc("Please enter Mobile Number", "મોબાઈલ નંબર દાખલ કરો")
                                return@Button
                            }
                            if (code4Digit.length != 4) {
                                errorMessage = loc("Please enter 4-digit code", "૪ અંકનો કોડ દાખલ કરો")
                                return@Button
                            }
                            if (confirmCode4Digit.length != 4) {
                                errorMessage = loc("Please enter conform code", "કન્ફર્મ કોડ દાખલ કરો")
                                return@Button
                            }
                            if (code4Digit != confirmCode4Digit) {
                                errorMessage = loc("Codes do not match", "કોડ મેળ ખાતો નથી. બંને કોડ સમાન હોવા જોઈએ.")
                                return@Button
                            }

                            viewModel.loginWithMobileAndCode(
                                mobile = mobileNumber.trim(),
                                code = code4Digit.trim(),
                                onSuccess = onLoginSuccess,
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
                            .testTag("login_submit_btn")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        } else {
                            Text(
                                text = AppStrings.login(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = loc("Don't have an account? ", "એકાઉન્ટ નથી? "),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                TextButton(
                    onClick = onNavigateToRegister,
                    modifier = Modifier.testTag("login_to_register_btn")
                ) {
                    Text(
                        text = AppStrings.newRegistration(),
                        fontWeight = FontWeight.Bold,
                        color = GoldDark,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
