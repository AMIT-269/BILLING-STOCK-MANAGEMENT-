package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.locale.AppStrings
import com.example.ui.locale.LanguageManager
import com.example.ui.locale.isAppGujarati
import com.example.ui.locale.loc
import com.example.ui.theme.GoldDark

@Composable
fun AddOpeningStockDialog(
    categoryName: String,
    unit: String,
    onDismiss: () -> Unit,
    onSave: (amount: Double, remark: String) -> Unit
) {
    val isGu = isAppGujarati()
    var amountText by remember { mutableStateOf("") }
    var remarkText by remember { mutableStateOf(loc(en = "Opening Stock", gu = "શરૂઆતનો સ્ટોક")) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val localizedUnit = LanguageManager.formatUnit(unit, isGu)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AccountBalance, contentDescription = null, tint = GoldDark)
                Text(
                    text = AppStrings.openingStockTitle(categoryName),
                    fontWeight = FontWeight.Bold,
                    color = GoldDark,
                    fontSize = 17.sp
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = AppStrings.openingStockDesc(),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        errorText = null
                    },
                    label = {
                        Text(
                            if (unit == "₹") loc(en = "Opening Cash (₹) *", gu = "શરૂઆતની રોકડ શિલક (₹) *")
                            else "${loc(en = "Opening Weight", gu = "શરૂઆતનું વજન")} ($localizedUnit) *"
                        )
                    },
                    placeholder = { Text("0.000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("opening_stock_amount_input"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = remarkText,
                    onValueChange = { remarkText = it },
                    label = {
                        Text(AppStrings.remarkNote())
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("opening_stock_remark_input"),
                    singleLine = true
                )

                if (errorText != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorText!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull()
                    if (amount == null || amount <= 0.0) {
                        errorText = loc(
                            en = "Please enter a valid amount / weight.",
                            gu = "કૃપા કરીને માન્ય રકમ અથવા વજન દાખલ કરો."
                        )
                    } else {
                        onSave(amount, remarkText.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = GoldDark),
                modifier = Modifier.testTag("opening_stock_submit_btn")
            ) {
                Text(
                    AppStrings.save(),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(AppStrings.cancel())
            }
        }
    )
}
