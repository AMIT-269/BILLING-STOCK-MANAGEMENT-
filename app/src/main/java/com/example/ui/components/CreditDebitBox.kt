package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.StockTransaction
import com.example.ui.locale.AppStrings
import com.example.ui.locale.LanguageManager
import com.example.ui.locale.isAppGujarati
import com.example.ui.locale.loc
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CreditDebitCard(
    isCredit: Boolean,
    totalAmountOrWeight: Double,
    unit: String,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isGu = isAppGujarati()
    val themeColor = if (isCredit) CashGreen else DebitRed
    val bgLightColor = if (isCredit) CashGreenLight else DebitRedLight
    val title = if (isCredit) AppStrings.credit() else AppStrings.debit()
    val addLabel = if (isCredit) AppStrings.addCredit() else AppStrings.addDebit()
    val totalLabel = if (isCredit) AppStrings.totalCredit() else AppStrings.totalDebit()
    val icon = if (isCredit) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, themeColor.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(bgLightColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = themeColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = themeColor
                        )
                    )
                }

                Button(
                    onClick = onAddClick,
                    colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag(if (isCredit) "add_credit_btn" else "add_debit_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = addLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Total Display
            Text(
                text = "$totalLabel:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val displayValue = if (unit == "₹") {
                LanguageManager.formatCurrency(totalAmountOrWeight)
            } else {
                LanguageManager.formatWeight(totalAmountOrWeight, isGu)
            }
            Text(
                text = displayValue,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = themeColor
                )
            )
        }
    }
}

@Composable
fun AddStockEntryDialog(
    isCredit: Boolean,
    categoryName: String,
    defaultUnit: String,
    onDismiss: () -> Unit,
    onSave: (amount: Double, remark: String) -> Unit
) {
    val isGu = isAppGujarati()
    var amountText by remember { mutableStateOf("") }
    var remarkText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    val themeColor = if (isCredit) CashGreen else DebitRed
    val localizedUnit = LanguageManager.formatUnit(defaultUnit, isGu)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = AppStrings.addEntryTitle(isCredit, categoryName),
                fontWeight = FontWeight.Bold,
                color = themeColor,
                fontSize = 18.sp
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "${AppStrings.enterQuantity()} ($localizedUnit)",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        errorText = null
                    },
                    label = {
                        Text(
                            if (defaultUnit == "₹") loc(en = "Amount (₹)", gu = "રકમ (₹)")
                            else "${AppStrings.colWeight()} ($localizedUnit)"
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("entry_amount_input"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = remarkText,
                    onValueChange = { remarkText = it },
                    label = { Text(AppStrings.remarkNote()) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("entry_remark_input"),
                    maxLines = 3
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
                        errorText = loc(en = "Please enter a valid amount.", gu = "કૃપા કરીને માન્ય પ્રમાણ દાખલ કરો.")
                    } else {
                        onSave(amount, remarkText.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                modifier = Modifier.testTag("entry_submit_btn")
            ) {
                Text(AppStrings.save(), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(AppStrings.cancel())
            }
        }
    )
}

@Composable
fun StockTransactionRow(
    tx: StockTransaction,
    onDelete: () -> Unit
) {
    val isGu = isAppGujarati()
    val isCredit = tx.type == "CREDIT"
    val color = if (isCredit) CashGreen else DebitRed
    val sdf = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.US)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = if (isCredit) CashGreenLight else DebitRedLight,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (isCredit) "${AppStrings.credit()} (+)" else "${AppStrings.debit()} (-)",
                            color = color,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    val isOpening = tx.remark.contains("Opening Stock", ignoreCase = true) || tx.remark.contains("શરૂઆતનો સ્ટોક")
                    if (isOpening) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = GoldLight,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = loc(en = "Opening Stock", gu = "શરૂઆતનો સ્ટોક"),
                                color = GoldDark,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    } else if (tx.isAutomaticFromBill) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = GoldLight,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = loc(en = "Auto Bill", gu = "ઓટો બિલ"),
                                color = GoldDark,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = tx.remark.ifBlank { loc(en = "No remark", gu = "નોંધ વગર") },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = sdf.format(Date(tx.dateTimestamp)),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                val formattedValue = if (tx.unit == "₹") {
                    LanguageManager.formatCurrency(tx.quantityOrAmount)
                } else {
                    LanguageManager.formatWeight(tx.quantityOrAmount, isGu)
                }
                Text(
                    text = "${if (isCredit) "+" else "-"} $formattedValue",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = color
                )

                if (!tx.isAutomaticFromBill) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = AppStrings.delete(),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
