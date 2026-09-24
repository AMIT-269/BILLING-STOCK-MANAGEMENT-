package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.sync.LicenceValidator
import com.example.ui.locale.AppStrings
import com.example.ui.locale.loc
import com.example.ui.theme.*
import com.example.ui.viewmodel.JewelleryViewModel
import com.example.util.PhoneUtil

@Composable
fun RegistrationScreen(
    viewModel: JewelleryViewModel,
    onNavigateToLogin: () -> Unit,
    onRegistrationSuccess: () -> Unit = onNavigateToLogin
) {
    var jewellerName by remember { mutableStateOf("") }
    var mobileNumber by remember { mutableStateOf("") }
    var gstNumber by remember { mutableStateOf("") }
    var code4Digit by remember { mutableStateOf("") }
    var confirmCode4Digit by remember { mutableStateOf("") }
    var licenceCode by remember { mutableStateOf("") }
    var codeVisible by remember { mutableStateOf(false) }
    var confirmCodeVisible by remember { mutableStateOf(false) }
    var licenceCodeVisible by remember { mutableStateOf(false) }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showSuccessDialog by remember { mutableStateOf(false) }

    val isLoading by viewModel.isLoading.collectAsState()

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = {
                showSuccessDialog = false
                onRegistrationSuccess()
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = CashGreen,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    text = loc(en = "Registration Successful", gu = "રજીસ્ટ્રેશન સફળ"),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = loc(
                        en = "Your Jeweller account is activated! You can now start billing and stock management.",
                        gu = "તમારું એકાઉન્ટ સફળતાપૂર્વક સક્રિય થઈ ગયું છે! હવે તમે બિલિંગ અને સ્ટોક મેનેજમેન્ટ શરૂ કરી શકો છો."
                    ),
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSuccessDialog = false
                        onRegistrationSuccess()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldDark),
                    modifier = Modifier.fillMaxWidth().testTag("reg_success_ok_btn")
                ) {
                    Text(loc(en = "Open Dashboard", gu = "ડેશબોર્ડ ખોલો"), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSuccessDialog = false
                        onNavigateToLogin()
                    },
                    modifier = Modifier.fillMaxWidth().testTag("reg_success_go_login_btn")
                ) {
                    Text(loc(en = "Go to Login", gu = "લોગિન સ્ક્રીન પર જાઓ"), color = GoldDark)
                }
            }
        )
    }

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
            Spacer(modifier = Modifier.height(16.dp))

            // Brand Header
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF0F0F14),
                border = androidx.compose.foundation.BorderStroke(1.2.dp, GoldDark),
                modifier = Modifier.size(68.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(4.dp)) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.ic_billing_stock_logo),
                        contentDescription = "Logo",
                        modifier = Modifier.size(50.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = loc(en = "New Registration", gu = "નવું રજીસ્ટ્રેશન"),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "BILLING & STOCK MANAGEMENT",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                ),
                color = GoldDark
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
                    // 1. Jewellers Name — REQUIRED
                    OutlinedTextField(
                        value = jewellerName,
                        onValueChange = {
                            jewellerName = it
                            errorMessage = null
                        },
                        label = { Text("${AppStrings.shopName()} *") },
                        placeholder = { Text(loc(en = "Enter Jewellers Name", gu = "ઝવેરીનું નામ દાખલ કરો")) },
                        leadingIcon = {
                            Icon(Icons.Default.Storefront, contentDescription = null, tint = GoldDark)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reg_jeweller_name"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 2. Mobile Number — REQUIRED (Exactly 10 digits, no +91)
                    OutlinedTextField(
                        value = mobileNumber,
                        onValueChange = {
                            mobileNumber = PhoneUtil.sanitizePhoneInput(it)
                            errorMessage = null
                        },
                        label = { Text("${loc(en = "Mobile Number", gu = "મોબાઈલ નંબર")} *") },
                        placeholder = { Text(loc("10-digit mobile number", "૧૦-અંકનો મોબાઈલ નંબર")) },
                        leadingIcon = {
                            Icon(Icons.Default.Phone, contentDescription = null, tint = GoldDark)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reg_mobile_number"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 3. GST No. — REQUIRED
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
                            .testTag("reg_gst_number"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 4. 4 Digit Code — REQUIRED
                    OutlinedTextField(
                        value = code4Digit,
                        onValueChange = {
                            code4Digit = PhoneUtil.sanitizeCodeInput(it)
                            errorMessage = null
                        },
                        label = { Text("${loc(en = "4 Digit Code", gu = "૪ અંકનો કોડ")} *") },
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
                            .testTag("reg_4digit_code"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 5. Confirm Code — REQUIRED
                    OutlinedTextField(
                        value = confirmCode4Digit,
                        onValueChange = {
                            confirmCode4Digit = PhoneUtil.sanitizeCodeInput(it)
                            errorMessage = null
                        },
                        label = { Text("${loc(en = "Confirm Code", gu = "કન્ફર્મ કોડ")} *") },
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
                            .testTag("reg_confirm_code"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 6. Licence Code — REQUIRED (only during registration, never displayed afterward)
                    OutlinedTextField(
                        value = licenceCode,
                        onValueChange = {
                            licenceCode = it.uppercase()
                            errorMessage = null
                        },
                        label = { Text("${loc(en = "Licence Code", gu = "લાઇસન્સ કોડ")} *") },
                        placeholder = { Text(loc(en = "Enter Licence Code", gu = "લાઇસન્સ કોડ દાખલ કરો")) },
                        leadingIcon = {
                            Icon(Icons.Default.VpnKey, contentDescription = null, tint = GoldDark)
                        },
                        trailingIcon = {
                            IconButton(onClick = { licenceCodeVisible = !licenceCodeVisible }) {
                                Icon(
                                    imageVector = if (licenceCodeVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (licenceCodeVisible) "Hide licence code" else "Show licence code",
                                    tint = GoldDark
                                )
                            }
                        },
                        visualTransformation = if (licenceCodeVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            capitalization = KeyboardCapitalization.Characters
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reg_licence_code"),
                        singleLine = true
                    )

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth().testTag("reg_error_banner")
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Error",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = errorMessage!!,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (errorMessage!!.contains("already registered", ignoreCase = true) ||
                                    errorMessage!!.contains("પહેલેથી જ રજીસ્ટર", ignoreCase = true)) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    TextButton(
                                        onClick = onNavigateToLogin,
                                        modifier = Modifier.align(Alignment.End).testTag("reg_err_goto_login_btn")
                                    ) {
                                        Text(
                                            text = loc(en = "Go to Login Screen →", gu = "લોગઇન સ્ક્રીન પર જાઓ →"),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            val cleanName = jewellerName.trim()
                            val cleanMobile = PhoneUtil.normalizeMobile(mobileNumber)
                            val cleanGst = PhoneUtil.normalizeGst(gstNumber)
                            val cleanCode = PhoneUtil.normalizeCode(code4Digit)
                            val cleanConfirm = PhoneUtil.normalizeCode(confirmCode4Digit)
                            val cleanLicence = licenceCode.trim()

                            if (cleanName.isEmpty()) {
                                errorMessage = loc(en = "Please enter Jewellers Name.", gu = "કૃપા કરીને ઝવેરીનું નામ દાખલ કરો.")
                                return@Button
                            }
                            if (cleanMobile.length != 10) {
                                errorMessage = loc(en = "Please enter a valid 10-digit mobile number.", gu = "કૃપા કરીને 10 અંકનો મોબાઈલ નંબર દાખલ કરો.")
                                return@Button
                            }
                            if (cleanGst.isEmpty()) {
                                errorMessage = loc(en = "Please enter GST number.", gu = "કૃપા કરીને જીએસટી નંબર દાખલ કરો.")
                                return@Button
                            }
                            if (cleanCode.length != 4) {
                                errorMessage = loc(en = "Please enter 4-digit code.", gu = "4 અંકનો સિક્યુરિટી કોડ દાખલ કરો.")
                                return@Button
                            }
                            if (cleanCode != cleanConfirm) {
                                errorMessage = loc(en = "Codes do not match.", gu = "કોડ મેળ ખાતો નથી. બંને કોડ સમાન હોવા જોઈએ.")
                                return@Button
                            }
                            if (cleanLicence.isEmpty()) {
                                errorMessage = loc(en = "Licence Code is required.", gu = "લાઇસન્સ કોડ આવશ્યક છે.")
                                return@Button
                            }
                            if (!LicenceValidator.isValidLicence(cleanLicence)) {
                                errorMessage = loc(en = "Invalid Licence Code.", gu = "અમાન્ય લાયસન્સ કોડ.")
                                return@Button
                            }

                            viewModel.register(
                                name = cleanName,
                                mobile = cleanMobile,
                                code = cleanCode,
                                confirmCode = cleanConfirm,
                                licenceCode = cleanLicence,
                                gstNumber = cleanGst,
                                onSuccess = {
                                    showSuccessDialog = true
                                },
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
                            .testTag("reg_submit_btn")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        } else {
                            Text(
                                text = loc(en = "Activate Account", gu = "એકાઉન્ટ સક્રિય કરો"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = loc(en = "Already have an account? ", gu = "પહેલેથી જ એકાઉન્ટ છે? "),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                TextButton(
                    onClick = onNavigateToLogin,
                    modifier = Modifier.testTag("reg_to_login_btn")
                ) {
                    Text(
                        text = loc(en = "Login", gu = "લોગિન કરો"),
                        fontWeight = FontWeight.Bold,
                        color = GoldDark,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
