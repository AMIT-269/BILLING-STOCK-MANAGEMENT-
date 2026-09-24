package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BillPayment
import com.example.ui.locale.AppStrings
import com.example.ui.locale.LanguageManager
import com.example.ui.locale.loc
import com.example.ui.theme.*
import java.util.UUID

@Composable
fun SplitGoldCashDialog(
    grandTotal: Double,
    billType: String = "SALE",
    defaultGoldRate: Double = 0.0,
    defaultSilverRate: Double = 0.0,
    isGu: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (metalPayment: BillPayment, cashPayment: BillPayment) -> Unit
) {
    var metalMode by remember { mutableStateOf("GOLD") } // "GOLD" or "SILVER"
    var metalWeightText by remember { mutableStateOf("") }
    var metalTouchText by remember { mutableStateOf(if (metalMode == "GOLD") "80.0" else "100.0") }
    var metalRateText by remember {
        val initialRate = if (metalMode == "GOLD") {
            if (defaultGoldRate > 0) defaultGoldRate else 10000.0
        } else {
            if (defaultSilverRate > 0) defaultSilverRate else 90.0
        }
        mutableStateOf(if (initialRate > 0) LanguageManager.formatDouble(initialRate, 0) else "")
    }

    val metalWeight = metalWeightText.toDoubleOrNull() ?: 0.0
    val metalTouch = metalTouchText.toDoubleOrNull() ?: (if (metalMode == "GOLD") 80.0 else 100.0)
    val metalRate = metalRateText.toDoubleOrNull() ?: 0.0
    val fineWeight = if (metalWeight > 0.0) (metalWeight * metalTouch / 100.0) else 0.0
    val effectiveRate = if (metalMode == "SILVER" && metalRate > 1000) (metalRate / 1000.0) else metalRate
    val metalAmount = fineWeight * effectiveRate

    val suggestedCash = (grandTotal - metalAmount).coerceAtLeast(0.0)
    var cashText by remember { mutableStateOf(if (suggestedCash > 0) LanguageManager.formatDouble(suggestedCash, 0) else "") }

    LaunchedEffect(metalAmount, grandTotal) {
        val diff = (grandTotal - metalAmount).coerceAtLeast(0.0)
        cashText = if (diff > 0) LanguageManager.formatDouble(diff, 0) else "0"
    }

    val cashAmount = cashText.toDoubleOrNull() ?: 0.0
    val totalPaid = metalAmount + cashAmount
    val balance = (grandTotal - totalPaid).coerceAtLeast(0.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Payments, contentDescription = null, tint = GoldDark, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = loc(en = "Multi-Payment (Metal + Cash)", gu = "મલ્ટી ચુકવણી (સોનું/ચાંદી + રોકડ)"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Bill Grand Total Banner
                Surface(
                    color = Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = loc(en = "Bill Grand Total:", gu = "બિલ કુલ રકમ:"),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = LanguageManager.formatCurrency(grandTotal),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = GoldDark
                        )
                    }
                }

                // Metal Selection Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = metalMode == "GOLD",
                        onClick = {
                            metalMode = "GOLD"
                            if (metalTouchText == "100.0" || metalTouchText.isBlank()) metalTouchText = "80.0"
                            if (defaultGoldRate > 0) metalRateText = LanguageManager.formatDouble(defaultGoldRate, 0)
                        },
                        label = { Text("🟡 " + AppStrings.modeGold(), fontSize = 12.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = metalMode == "SILVER",
                        onClick = {
                            metalMode = "SILVER"
                            if (metalTouchText == "80.0" || metalTouchText.isBlank()) metalTouchText = "100.0"
                            if (defaultSilverRate > 0) metalRateText = LanguageManager.formatDouble(defaultSilverRate, 0)
                        },
                        label = { Text("⚪ " + AppStrings.modeSilver(), fontSize = 12.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    text = if (metalMode == "GOLD") loc(en = "1. Gold Payment Details:", gu = "૧. સોનામાં ચુકવણી વિગત:")
                    else loc(en = "1. Silver Payment Details:", gu = "૧. ચાંદીમાં ચુકવણી વિગત:"),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = GoldDark
                )

                // Weight & Touch
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = metalWeightText,
                        onValueChange = { metalWeightText = it },
                        label = { Text(loc(en = "Weight (g)", gu = "વજન (ગ્રા)")) },
                        placeholder = { Text("10.000") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = metalTouchText,
                        onValueChange = { metalTouchText = it },
                        label = { Text(loc(en = "Touch %", gu = "ટચ %")) },
                        placeholder = { Text("80.0") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                // Current Price / Rate
                OutlinedTextField(
                    value = metalRateText,
                    onValueChange = { metalRateText = it },
                    label = {
                        Text(
                            if (metalMode == "SILVER") loc(en = "Silver Current Price (₹/kg or ₹/g)", gu = "ચાંદી વર્તમાન ભાવ (₹/કિલો અથવા ₹/ગ્રા)")
                            else loc(en = "Gold Current Price (₹/g)", gu = "સોનાનો વર્તમાન ભાવ (₹/ગ્રામ)")
                        )
                    },
                    placeholder = { Text(if (metalMode == "SILVER") "90" else "10000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Exact Mathematical Formula Card (Matches handwritten receipt)
                Surface(
                    color = GoldLight.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val wtStr = LanguageManager.formatDouble(metalWeight, 3)
                        val touchStr = LanguageManager.formatDouble(metalTouch, 1)
                        val fineStr = LanguageManager.formatDouble(fineWeight, 3)
                        val rateUnit = if (metalMode == "SILVER") (if (isGu) "/કિલો" else "/kg") else (if (isGu) "/ગ્રા" else "/g")
                        val rateStr = LanguageManager.formatDouble(metalRate, 0)
                        val fineLabel = if (isGu) "ફાઇન" else "FINE"
                        val priceLabel = if (isGu) "ભાવ" else "CURRENT PRICE"

                        Text(
                            text = loc(en = "📐 Metal Calculation Formula:", gu = "📐 ધાતુ ગણતરી ફોર્મ્યુલા:"),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldDark
                        )
                        Text(
                            text = "$wtStr g . $touchStr% = $fineStr g $fineLabel",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1E293B)
                        )
                        Text(
                            text = "$fineStr g × $priceLabel ($rateStr$rateUnit) = ${LanguageManager.formatCurrency(metalAmount)}",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldDark
                        )
                    }
                }

                Divider(color = Color(0xFFE2E8F0))

                // Cash Payment Section
                Text(
                    text = loc(en = "💵 2. Additional Cash Payment (વધારાની રોકડ ચુકવણી):", gu = "💵 ૨. વધારાની રોકડ ચુકવણી:"),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = CashGreen
                )

                OutlinedTextField(
                    value = cashText,
                    onValueChange = { cashText = it },
                    label = { Text(loc(en = "Cash Amount (₹)", gu = "રોકડ રકમ (₹)")) },
                    placeholder = { Text("20000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Payment Summary Pill
                Surface(
                    color = CashGreen.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, CashGreen.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                text = if (metalMode == "GOLD") loc(en = "Gold Value:", gu = "સોનાનું મૂલ્ય:") else loc(en = "Silver Value:", gu = "ચાંદીનું મૂલ્ય:"),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(LanguageManager.formatCurrency(metalAmount), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = GoldDark)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(loc(en = "Cash Value:", gu = "રોકડ રકમ:"), fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                            Text(LanguageManager.formatCurrency(cashAmount), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = CashGreen)
                        }
                        Divider(color = CashGreen.copy(alpha = 0.2f), thickness = 0.5.dp)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(loc(en = "Total Payment Paid:", gu = "કુલ ચુકવેલ રકમ:"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(LanguageManager.formatCurrency(totalPaid), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = CashGreen)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(loc(en = "Remaining Balance:", gu = "બાકી રકમ:"), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            Text(LanguageManager.formatCurrency(balance), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (balance > 0) DebitRed else CashGreen)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val isSale = billType == "SALE"
                    val pMetal = BillPayment(
                        id = UUID.randomUUID().toString(),
                        entryNumber = 1,
                        dateTimestamp = System.currentTimeMillis(),
                        paymentMode = metalMode,
                        amount = metalAmount,
                        metalWeight = metalWeight,
                        metalTouch = metalTouch,
                        metalRate = metalRate,
                        fineWeight = fineWeight,
                        note = if (metalMode == "GOLD") {
                            if (isSale) "Gold received (સોનું મેળવ્યું)" else "Gold paid (સોનું ચૂકવ્યું)"
                        } else {
                            if (isSale) "Silver received (ચાંદી મેળવ્યું)" else "Silver paid (ચાંદી ચૂકવ્યું)"
                        }
                    )
                    val pCash = BillPayment(
                        id = UUID.randomUUID().toString(),
                        entryNumber = 2,
                        dateTimestamp = System.currentTimeMillis(),
                        paymentMode = "CASH",
                        amount = cashAmount,
                        note = if (isSale) "Cash received (રોકડ મેળવી)" else "Cash paid (રોકડ ચૂકવી)"
                    )
                    onConfirm(pMetal, pCash)
                },
                colors = ButtonDefaults.buttonColors(containerColor = GoldDark)
            ) {
                Text(loc(en = "Apply Multi-Payment", gu = "ચુકવણી લાગુ કરો"), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(AppStrings.cancel())
            }
        }
    )
}
