package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Bill
import com.example.data.model.BillItem
import com.example.data.model.BillPayment
import com.example.ui.components.JewelleryTopBar
import com.example.ui.components.SplitGoldCashDialog
import com.example.ui.locale.AppStrings
import com.example.ui.locale.LanguageManager
import com.example.ui.locale.isAppGujarati
import com.example.ui.locale.loc
import com.example.ui.theme.*
import com.example.ui.viewmodel.JewelleryViewModel
import java.util.UUID
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateBillScreen(
    viewModel: JewelleryViewModel,
    editingBillId: String? = null,
    initialBillType: String? = null,
    onNavigateBack: () -> Unit,
    onBillSaved: (billId: String) -> Unit
) {
    val account by viewModel.currentAccount.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val isGu = isAppGujarati()
    val coroutineScope = rememberCoroutineScope()

    var billType by remember { mutableStateOf(initialBillType ?: "SALE") } // "SALE" or "KARIGAR_PURCHASE"
    var isGstBill by remember { mutableStateOf(false) }
    var isCstBill by remember { mutableStateOf(false) }
    var sgstPercentText by remember { mutableStateOf("1.5") }
    var cgstPercentText by remember { mutableStateOf("1.5") }
    var cstPercentText by remember { mutableStateOf("1.5") }

    var billNumber by remember { mutableStateOf("") }
    var partyName by remember { mutableStateOf("") }
    var partyMobile by remember { mutableStateOf("") }
    var partyAddress by remember { mutableStateOf("") }
    var paymentMode by remember { mutableStateOf("CASH") }
    var notes by remember { mutableStateOf("") }

    var discountText by remember { mutableStateOf("0") }
    var oldMetalExchangeText by remember { mutableStateOf("0") }
    var cashReceivedOrPaidText by remember { mutableStateOf("0") }

    val items = remember { mutableStateListOf<BillItem>() }
    val payments = remember { mutableStateListOf<BillPayment>() }

    // Inline Metal Payment fields (for Gold + Cash, Silver + Cash, or pure metal payment)
    var inlineMetalWeightText by remember { mutableStateOf("") }
    var inlineMetalTouchText by remember { mutableStateOf("80.0") }
    var inlineMetalRateText by remember { mutableStateOf("") }
    var inlineRemainingCashText by remember { mutableStateOf("") }

    var showAddItemDialog by remember { mutableStateOf(false) }
    var editingItemIndex by remember { mutableStateOf<Int?>(null) }

    var showAddPaymentDialog by remember { mutableStateOf(false) }
    var showSplitGoldCashDialog by remember { mutableStateOf(false) }
    var editingPaymentIndex by remember { mutableStateOf<Int?>(null) }

    val isEditMode = !editingBillId.isNullOrBlank()
    var existingBillCreatedAt by remember { mutableStateOf(System.currentTimeMillis()) }
    var existingBillDateTimestamp by remember { mutableStateOf(System.currentTimeMillis()) }
    var isSubmitting by remember { mutableStateOf(false) }

    // Fetch initial bill number or load existing bill if editing
    LaunchedEffect(editingBillId) {
        if (!editingBillId.isNullOrBlank()) {
            val b = viewModel.getBillById(editingBillId) ?: viewModel.allBills.value.find { it.id == editingBillId }
            if (b != null) {
                billType = b.billType
                isGstBill = b.isGstBill
                isCstBill = b.isCstBill
                val halfGst = if (b.gstPercent > 0) b.gstPercent / 2.0 else 1.5
                sgstPercentText = LanguageManager.formatDouble(halfGst, 1)
                cgstPercentText = LanguageManager.formatDouble(halfGst, 1)
                cstPercentText = if (b.cstPercent > 0) LanguageManager.formatDouble(b.cstPercent, 1) else "1.5"

                billNumber = b.billNumber
                partyName = b.partyName
                partyMobile = b.partyMobile
                partyAddress = b.partyAddress
                paymentMode = b.paymentMode
                notes = b.notes
                discountText = if (b.discount > 0) LanguageManager.formatDouble(b.discount, 0) else "0"
                oldMetalExchangeText = if (b.oldMetalExchangeAmount > 0) LanguageManager.formatDouble(b.oldMetalExchangeAmount, 0) else "0"
                cashReceivedOrPaidText = if (b.cashReceivedOrPaid > 0) LanguageManager.formatDouble(b.cashReceivedOrPaid, 0) else "0"
                existingBillCreatedAt = b.createdAt
                existingBillDateTimestamp = b.dateTimestamp

                items.clear()
                items.addAll(b.parseItems())

                payments.clear()
                val effectivePayments = b.getEffectivePayments()
                payments.addAll(effectivePayments)

                val goldPayment = effectivePayments.find { it.paymentMode == "GOLD" }
                val silverPayment = effectivePayments.find { it.paymentMode == "SILVER" }
                val cashPayment = effectivePayments.find { it.paymentMode in listOf("CASH", "ONLINE", "CHEQUE") }

                if (goldPayment != null && cashPayment != null) {
                    paymentMode = "GOLD_CASH"
                    inlineMetalWeightText = LanguageManager.formatDouble(goldPayment.metalWeight, 3)
                    inlineMetalTouchText = LanguageManager.formatDouble(goldPayment.metalTouch, 1)
                    inlineMetalRateText = if (goldPayment.metalRate > 0) LanguageManager.formatDouble(goldPayment.metalRate, 0) else ""
                    inlineRemainingCashText = LanguageManager.formatDouble(cashPayment.amount, 0)
                } else if (silverPayment != null && cashPayment != null) {
                    paymentMode = "SILVER_CASH"
                    inlineMetalWeightText = LanguageManager.formatDouble(silverPayment.metalWeight, 3)
                    inlineMetalTouchText = LanguageManager.formatDouble(silverPayment.metalTouch, 1)
                    inlineMetalRateText = if (silverPayment.metalRate > 0) LanguageManager.formatDouble(silverPayment.metalRate, 0) else ""
                    inlineRemainingCashText = LanguageManager.formatDouble(cashPayment.amount, 0)
                } else if (goldPayment != null && effectivePayments.size == 1) {
                    paymentMode = "GOLD"
                    inlineMetalWeightText = LanguageManager.formatDouble(goldPayment.metalWeight, 3)
                    inlineMetalTouchText = LanguageManager.formatDouble(goldPayment.metalTouch, 1)
                    inlineMetalRateText = if (goldPayment.metalRate > 0) LanguageManager.formatDouble(goldPayment.metalRate, 0) else ""
                } else if (silverPayment != null && effectivePayments.size == 1) {
                    paymentMode = "SILVER"
                    inlineMetalWeightText = LanguageManager.formatDouble(silverPayment.metalWeight, 3)
                    inlineMetalTouchText = LanguageManager.formatDouble(silverPayment.metalTouch, 1)
                    inlineMetalRateText = if (silverPayment.metalRate > 0) LanguageManager.formatDouble(silverPayment.metalRate, 0) else ""
                }
            }
        } else {
            billNumber = viewModel.getNextBillNumber(billType)
            // No example items added on new bill. Items list starts completely empty.
        }
    }

    // Calculations
    val subtotal = items.sumOf { it.itemTotal }

    val sgstRate = if (isGstBill) (sgstPercentText.toDoubleOrNull() ?: 1.5) else 0.0
    val cgstRate = if (isGstBill) (cgstPercentText.toDoubleOrNull() ?: 1.5) else 0.0
    val totalGstPercent = sgstRate + cgstRate
    val gstAmount = if (isGstBill) (subtotal * totalGstPercent / 100.0) else 0.0

    val cstRate = if (isCstBill) (cstPercentText.toDoubleOrNull() ?: 1.5) else 0.0
    val cstAmount = if (isCstBill) (subtotal * cstRate / 100.0) else 0.0

    val discount = discountText.toDoubleOrNull() ?: 0.0
    val oldMetal = oldMetalExchangeText.toDoubleOrNull() ?: 0.0
    val grandTotal = (subtotal + gstAmount + cstAmount - discount - oldMetal).coerceAtLeast(0.0)

    val totalPaidFromPayments = payments.sumOf { it.amount }
    val cashReceivedOrPaid = if (payments.isNotEmpty()) totalPaidFromPayments else (cashReceivedOrPaidText.toDoubleOrNull() ?: 0.0)
    val netBalanceDue = (grandTotal - cashReceivedOrPaid).coerceAtLeast(0.0)

    if (showAddItemDialog) {
        val currentItem = editingItemIndex?.let { items.getOrNull(it) }
        ItemEditDialog(
            initialItem = currentItem,
            billType = billType,
            defaultGoldRate = settings?.goldRate22k ?: 0.0,
            defaultSilverRate = settings?.silverRate ?: 0.0,
            isGu = isGu,
            onDismiss = {
                showAddItemDialog = false
                editingItemIndex = null
            },
            onSave = { newItem ->
                if (editingItemIndex != null && editingItemIndex!! < items.size) {
                    items[editingItemIndex!!] = newItem
                } else {
                    items.add(newItem)
                }
                showAddItemDialog = false
                editingItemIndex = null
            }
        )
    }

    if (showAddPaymentDialog) {
        val currentPayment = editingPaymentIndex?.let { payments.getOrNull(it) }
        PaymentEntryDialog(
            initialPayment = currentPayment,
            billType = billType,
            defaultGoldRate = settings?.goldRate22k ?: 0.0,
            defaultSilverRate = settings?.silverRate ?: 0.0,
            isGu = isGu,
            onDismiss = {
                showAddPaymentDialog = false
                editingPaymentIndex = null
            },
            onSave = { newPayment ->
                if (editingPaymentIndex != null && editingPaymentIndex!! < payments.size) {
                    payments[editingPaymentIndex!!] = newPayment
                } else {
                    payments.add(newPayment)
                }
                cashReceivedOrPaidText = LanguageManager.formatDouble(payments.sumOf { it.amount }, 0)
                showAddPaymentDialog = false
                editingPaymentIndex = null
            }
        )
    }

    if (showSplitGoldCashDialog) {
        val defaultRate = if (items.isNotEmpty()) {
            items.firstOrNull { it.metalType.equals("GOLD", ignoreCase = true) }?.ratePerGram
                ?: (settings?.goldRate22k ?: 0.0)
        } else (settings?.goldRate22k ?: 0.0)
        val defaultSilRate = if (items.isNotEmpty()) {
            items.firstOrNull { it.metalType.equals("SILVER", ignoreCase = true) }?.ratePerGram
                ?: (settings?.silverRate ?: 0.0)
        } else (settings?.silverRate ?: 0.0)

        SplitGoldCashDialog(
            grandTotal = grandTotal,
            billType = billType,
            defaultGoldRate = defaultRate,
            defaultSilverRate = defaultSilRate,
            isGu = isGu,
            onDismiss = { showSplitGoldCashDialog = false },
            onConfirm = { pMetal, pCash ->
                payments.clear()
                if (pMetal.amount > 0.0 || pMetal.metalWeight > 0.0) {
                    payments.add(pMetal)
                }
                if (pCash.amount > 0.0) {
                    payments.add(pCash)
                }
                cashReceivedOrPaidText = LanguageManager.formatDouble(payments.sumOf { it.amount }, 0)
                showSplitGoldCashDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            JewelleryTopBar(
                title = if (isEditMode) AppStrings.editBillTitle() else AppStrings.createBillTitle(),
                subtitle = "${AppStrings.billNumber()}: $billNumber",
                showBackButton = true,
                onBackClick = onNavigateBack,
                logoBase64 = settings?.logoBase64,
                syncStatus = syncStatus
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                // Bill Type Selection (Customer Sale vs Karigar Purchase)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(
                        selected = billType == "SALE",
                        onClick = {
                            billType = "SALE"
                            if (!isEditMode) {
                                coroutineScope.launch {
                                    billNumber = viewModel.getNextBillNumber("SALE")
                                }
                            }
                        },
                        label = { Text(AppStrings.customerSale()) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GoldDark,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.weight(1f).testTag("bill_type_sale")
                    )

                    FilterChip(
                        selected = billType == "KARIGAR_PURCHASE",
                        onClick = {
                            billType = "KARIGAR_PURCHASE"
                            if (!isEditMode) {
                                coroutineScope.launch {
                                    billNumber = viewModel.getNextBillNumber("KARIGAR_PURCHASE")
                                }
                            }
                        },
                        label = { Text(AppStrings.karigarPurchase()) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Charcoal,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.weight(1f).testTag("bill_type_karigar")
                    )
                }
            }

            item {
                // Per-Bill GST / CST Selection Card with manual change option
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isGstBill || isCstBill) GoldLight.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface
                    ),
                    border = if (isGstBill || isCstBill) androidx.compose.foundation.BorderStroke(1.5.dp, GoldDark) else null
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // GST Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isGstBill) AppStrings.taxInvoiceGst() else AppStrings.retailInvoiceNonGst(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = if (isGstBill) GoldDark else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isGstBill) loc(en = "GST applied (SGST 1.5% + CGST 1.5% or custom)", gu = "જીએસટી લાગુ થશે (SGST 1.5% + CGST 1.5% અથવા કસ્ટમ)")
                                    else loc(en = "Standard retail bill without GST", gu = "જીએસટી વગરનું નિયમિત રિટેલ બિલ"),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isGstBill,
                                onCheckedChange = {
                                    isGstBill = it
                                    if (it) isCstBill = false
                                },
                                modifier = Modifier.testTag("gst_toggle_switch")
                            )
                        }

                        // When GST enabled: Editable SGST and CGST rates (auto set 1.5% with manual change)
                        if (isGstBill) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = sgstPercentText,
                                    onValueChange = { sgstPercentText = it },
                                    label = { Text("${AppStrings.sgst()} %") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = cgstPercentText,
                                    onValueChange = { cgstPercentText = it },
                                    label = { Text("${AppStrings.cgst()} %") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }
                            Text(
                                text = "${loc(en = "Total GST", gu = "કુલ જીએસટી")}: ${LanguageManager.formatDouble(totalGstPercent, 1)}% (${LanguageManager.formatCurrency(gstAmount)})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = GoldDark
                            )
                        }

                        Divider(modifier = Modifier.padding(vertical = 2.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // CST Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = loc(en = "CST Bill", gu = "સીએસટી બિલ (CST)"),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (isCstBill) GoldDark else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = loc(en = "Interstate trade CST bill (auto 1.5% or custom)", gu = "અન્ય રાજ્ય વેપાર માટે સીએસટી બિલ (ઓટો 1.5% અથવા કસ્ટમ)"),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isCstBill,
                                onCheckedChange = {
                                    isCstBill = it
                                    if (it) isGstBill = false
                                },
                                modifier = Modifier.testTag("cst_toggle_switch")
                            )
                        }

                        // When CST enabled: Editable CST rate (auto set 1.5% with manual change option)
                        if (isCstBill) {
                            OutlinedTextField(
                                value = cstPercentText,
                                onValueChange = { cstPercentText = it },
                                label = { Text("${AppStrings.cst()} %") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Text(
                                text = "${loc(en = "CST Amount", gu = "સીએસટી રકમ")}: ${LanguageManager.formatCurrency(cstAmount)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = GoldDark
                            )
                        }
                    }
                }
            }

            item {
                // Party Details Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = AppStrings.partyDetails(billType == "SALE"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = GoldDark
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = partyName,
                            onValueChange = { partyName = it },
                            label = { Text(AppStrings.partyName(billType == "SALE")) },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = GoldDark) },
                            modifier = Modifier.fillMaxWidth().testTag("bill_party_name"),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = partyMobile,
                            onValueChange = {
                                if (it.length <= 10 && it.all { c -> c.isDigit() }) partyMobile = it
                            },
                            label = { Text(AppStrings.partyMobile()) },
                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = GoldDark) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth().testTag("bill_party_mobile"),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = partyAddress,
                            onValueChange = { partyAddress = it },
                            label = { Text(AppStrings.partyAddress()) },
                            leadingIcon = { Icon(Icons.Default.Place, contentDescription = null, tint = GoldDark) },
                            modifier = Modifier.fillMaxWidth().testTag("bill_party_address"),
                            singleLine = true
                        )
                    }
                }
            }

            item {
                // Items Header with Add Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${AppStrings.itemsInBill()} (${items.size}):",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Button(
                        onClick = {
                            editingItemIndex = null
                            showAddItemDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldDark),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("bill_add_item_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(AppStrings.addItem(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Items List
            if (items.isEmpty()) {
                item {
                    Text(
                        text = AppStrings.noItemsAdded(),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                itemsIndexed(items) { index, item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                                    Text(
                                        text = "${index + 1}. ${item.description}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (item.stockClassification == "METAL") Color(0xFFE0E7FF) else GoldLight.copy(alpha = 0.5f)
                                    ) {
                                        Text(
                                            text = if (item.stockClassification == "METAL") AppStrings.stockMetal() else AppStrings.stockJewellery(),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (item.stockClassification == "METAL") Color(0xFF3730A3) else GoldDark,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                val metalLabel = if (item.metalType == "GOLD") AppStrings.gold() else AppStrings.silver()
                                val purityLabel = if (item.purity.isNotBlank()) " (${item.purity})" else ""
                                val wtStr = LanguageManager.formatWeight(item.grossWeight, isGu)
                                val fineStr = LanguageManager.formatWeight(item.totalFine, isGu)
                                val makingInfo = when {
                                    item.makingChargePercent > 0 && item.makingCharges > 0 -> " • ${loc(en = "Making", gu = "મજૂરી")}: ${LanguageManager.formatDouble(item.makingChargePercent, 1)}% + ₹${LanguageManager.formatDouble(item.makingCharges, 0)}"
                                    item.makingChargePercent > 0 -> " • ${loc(en = "Making", gu = "મજૂરી")}: ${LanguageManager.formatDouble(item.makingChargePercent, 1)}%"
                                    item.makingCharges > 0 -> " • ${loc(en = "Making", gu = "મજૂરી")}: ₹${LanguageManager.formatDouble(item.makingCharges, 0)}"
                                    else -> ""
                                }
                                Text(
                                    text = "$metalLabel$purityLabel • $wtStr • ${AppStrings.colTouch()}: ${LanguageManager.formatDouble(item.currentTouch, 1)}%$makingInfo",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val rateUnit = if (item.metalType == "SILVER") "/kg" else "/g"
                                Text(
                                    text = "${AppStrings.colTotalFine()}: $fineStr @ ₹${LanguageManager.formatDouble(item.ratePerGram, 0)}$rateUnit",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = LanguageManager.formatCurrency(item.itemTotal),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = GoldDark
                                )
                            }

                            Row {
                                IconButton(
                                    onClick = {
                                        editingItemIndex = index
                                        showAddItemDialog = true
                                    }
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = GoldDark)
                                }
                                IconButton(
                                    onClick = {
                                        items.removeAt(index)
                                    }
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = DebitRed)
                                }
                            }
                        }
                    }
                }
            }

            // Calculation and Payment Breakdown Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = AppStrings.additionalCharges(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = GoldDark
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        BillCalcRow(AppStrings.subtotal(), LanguageManager.formatCurrency(subtotal))

                        if (isGstBill) {
                            val halfGst = gstAmount / 2.0
                            BillCalcRow("${AppStrings.cgst()} (${LanguageManager.formatDouble(cgstRate, 1)}%):", LanguageManager.formatCurrency(halfGst))
                            BillCalcRow("${AppStrings.sgst()} (${LanguageManager.formatDouble(sgstRate, 1)}%):", LanguageManager.formatCurrency(halfGst))
                        }

                        if (isCstBill) {
                            BillCalcRow("${AppStrings.cst()} (${LanguageManager.formatDouble(cstRate, 1)}%):", LanguageManager.formatCurrency(cstAmount))
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = discountText,
                                onValueChange = { discountText = it },
                                label = { Text(AppStrings.discount()) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f).testTag("bill_discount_input"),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = oldMetalExchangeText,
                                onValueChange = { oldMetalExchangeText = it },
                                label = { Text(AppStrings.oldMetalExchange()) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f).testTag("bill_old_exchange_input"),
                                singleLine = true
                            )
                        }

                        Divider(modifier = Modifier.padding(vertical = 10.dp))

                        // Grand Total
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(AppStrings.grandTotal(), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                text = LanguageManager.formatCurrency(grandTotal),
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = GoldDark
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Primary Payment Mode Selector (Cash, Gold, Silver, Online, Cheque)
                        Text(
                            text = AppStrings.paymentMode(),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(
                                selected = paymentMode == "CASH",
                                onClick = { paymentMode = "CASH" },
                                label = { Text(AppStrings.modeCash(), fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = paymentMode == "GOLD_CASH",
                                onClick = {
                                    paymentMode = "GOLD_CASH"
                                    if (inlineMetalWeightText.isBlank()) inlineMetalWeightText = "6.000"
                                    if (inlineMetalTouchText.isBlank()) inlineMetalTouchText = "80.0"
                                    if (inlineMetalRateText.isBlank()) {
                                        val defRate = settings?.goldRate22k ?: 7200.0
                                        if (defRate > 0) inlineMetalRateText = LanguageManager.formatDouble(defRate, 0)
                                    }
                                },
                                label = { Text(loc(en = "Gold + Cash", gu = "સોનું + રોકડ"), fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                modifier = Modifier.weight(1.3f)
                            )
                            FilterChip(
                                selected = paymentMode == "SILVER_CASH",
                                onClick = {
                                    paymentMode = "SILVER_CASH"
                                    if (inlineMetalWeightText.isBlank()) inlineMetalWeightText = "100.000"
                                    if (inlineMetalTouchText.isBlank()) inlineMetalTouchText = "70.0"
                                    if (inlineMetalRateText.isBlank()) {
                                        val defRate = settings?.silverRate ?: 88.0
                                        if (defRate > 0) inlineMetalRateText = LanguageManager.formatDouble(defRate, 0)
                                    }
                                },
                                label = { Text(loc(en = "Silver + Cash", gu = "ચાંદી + રોકડ"), fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                modifier = Modifier.weight(1.3f)
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(
                                selected = paymentMode == "GOLD",
                                onClick = {
                                    paymentMode = "GOLD"
                                    if (inlineMetalWeightText.isBlank()) inlineMetalWeightText = "6.000"
                                    if (inlineMetalTouchText.isBlank()) inlineMetalTouchText = "80.0"
                                    if (inlineMetalRateText.isBlank()) {
                                        val defRate = settings?.goldRate22k ?: 7200.0
                                        if (defRate > 0) inlineMetalRateText = LanguageManager.formatDouble(defRate, 0)
                                    }
                                },
                                label = { Text(AppStrings.modeGold(), fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = paymentMode == "SILVER",
                                onClick = {
                                    paymentMode = "SILVER"
                                    if (inlineMetalWeightText.isBlank()) inlineMetalWeightText = "100.000"
                                    if (inlineMetalTouchText.isBlank()) inlineMetalTouchText = "70.0"
                                    if (inlineMetalRateText.isBlank()) {
                                        val defRate = settings?.silverRate ?: 88.0
                                        if (defRate > 0) inlineMetalRateText = LanguageManager.formatDouble(defRate, 0)
                                    }
                                },
                                label = { Text(AppStrings.modeSilver(), fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = paymentMode == "ONLINE",
                                onClick = { paymentMode = "ONLINE" },
                                label = { Text(AppStrings.modeOnline(), fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = paymentMode == "CHEQUE",
                                onClick = { paymentMode = "CHEQUE" },
                                label = { Text(AppStrings.modeCheque(), fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Inline Metal/Multi-Payment Configuration Card
                        if (paymentMode in listOf("GOLD_CASH", "SILVER_CASH", "GOLD", "SILVER")) {
                            val isGold = paymentMode.startsWith("GOLD")
                            val isSplit = paymentMode.endsWith("_CASH")
                            val metalName = if (isGold) loc(en = "Gold", gu = "સોનું") else loc(en = "Silver", gu = "ચાંદી")
                            val defaultRate = if (isGold) (settings?.goldRate22k ?: 7200.0) else (settings?.silverRate ?: 88.0)

                            val syncInlinePayments: (String, String, String, String) -> Unit = { wtS, touchS, rateS, cashS ->
                                val wt = wtS.toDoubleOrNull() ?: 0.0
                                val touch = touchS.toDoubleOrNull() ?: (if (isGold) 80.0 else 70.0)
                                val rate = rateS.toDoubleOrNull() ?: defaultRate
                                val fine = if (wt > 0) wt * touch / 100.0 else 0.0
                                val metalVal = fine * rate

                                val isSale = billType == "SALE"
                                val metalPayment = BillPayment(
                                    id = payments.find { it.paymentMode == (if (isGold) "GOLD" else "SILVER") }?.id ?: UUID.randomUUID().toString(),
                                    entryNumber = 1,
                                    dateTimestamp = System.currentTimeMillis(),
                                    paymentMode = if (isGold) "GOLD" else "SILVER",
                                    amount = metalVal,
                                    metalWeight = wt,
                                    metalTouch = touch,
                                    metalRate = rate,
                                    fineWeight = fine,
                                    note = if (isSale) "$metalName received (મેળવેલ $metalName)" else "$metalName paid (ચૂકવેલ $metalName)"
                                )

                                if (isSplit) {
                                    val cashVal = cashS.toDoubleOrNull() ?: (grandTotal - metalVal).coerceAtLeast(0.0)
                                    val cashPayment = BillPayment(
                                        id = payments.find { it.paymentMode == "CASH" }?.id ?: UUID.randomUUID().toString(),
                                        entryNumber = 2,
                                        dateTimestamp = System.currentTimeMillis(),
                                        paymentMode = "CASH",
                                        amount = cashVal,
                                        note = if (isSale) "Cash received (રોકડ મેળવી)" else "Cash paid (રોકડ ચૂકવી)"
                                    )
                                    payments.clear()
                                    payments.add(metalPayment)
                                    payments.add(cashPayment)
                                    cashReceivedOrPaidText = LanguageManager.formatDouble(metalVal + cashVal, 0)
                                } else {
                                    payments.clear()
                                    payments.add(metalPayment)
                                    cashReceivedOrPaidText = LanguageManager.formatDouble(metalVal, 0)
                                }
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = if (isGold) Color(0xFFFFFBEB) else Color(0xFFF1F5F9)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isGold) GoldDark.copy(alpha = 0.5f) else Color(0xFFCBD5E1))
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = if (isSplit) loc(en = "🟡 $metalName + Cash Payment Details", gu = "🟡 $metalName + રોકડ ચુકવણી વિગત")
                                               else loc(en = "🟡 $metalName Payment Details", gu = "🟡 $metalName ચુકવણી વિગત"),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (isGold) GoldDark else Color(0xFF334155)
                                    )

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = inlineMetalWeightText,
                                            onValueChange = {
                                                inlineMetalWeightText = it
                                                syncInlinePayments(it, inlineMetalTouchText, inlineMetalRateText, inlineRemainingCashText)
                                            },
                                            label = { Text(loc(en = "$metalName Wt (g)", gu = "$metalName વજન (ગ્રા)")) },
                                            placeholder = { Text("6.000") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            modifier = Modifier.weight(1f),
                                            singleLine = true
                                        )
                                        OutlinedTextField(
                                            value = inlineMetalTouchText,
                                            onValueChange = {
                                                inlineMetalTouchText = it
                                                syncInlinePayments(inlineMetalWeightText, it, inlineMetalRateText, inlineRemainingCashText)
                                            },
                                            label = { Text(loc(en = "Touch %", gu = "ટચ %")) },
                                            placeholder = { Text("80.0%") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            modifier = Modifier.weight(1f),
                                            singleLine = true
                                        )
                                    }

                                    val rateUnit = if (isGold) "₹/g" else "₹/kg"
                                    OutlinedTextField(
                                        value = inlineMetalRateText,
                                        onValueChange = {
                                            inlineMetalRateText = it
                                            syncInlinePayments(inlineMetalWeightText, inlineMetalTouchText, it, inlineRemainingCashText)
                                        },
                                        label = { Text(loc(en = "$metalName Rate ($rateUnit)", gu = "$metalName ભાવ ($rateUnit)")) },
                                        placeholder = { Text(LanguageManager.formatDouble(defaultRate, 0)) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )

                                    val currWt = inlineMetalWeightText.toDoubleOrNull() ?: 0.0
                                    val currTouch = inlineMetalTouchText.toDoubleOrNull() ?: (if (isGold) 80.0 else 70.0)
                                    val currRate = inlineMetalRateText.toDoubleOrNull() ?: defaultRate
                                    val currFine = if (currWt > 0) currWt * currTouch / 100.0 else 0.0
                                    val currVal = currFine * currRate

                                    Surface(
                                        color = if (isGold) GoldLight.copy(alpha = 0.25f) else Color(0xFFE2E8F0),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp).fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${loc(en = "Fine:", gu = "ફાઇન:")} ${LanguageManager.formatDouble(currFine, 3)}g",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "${loc(en = "Metal Value:", gu = "ધાતુ મૂલ્ય:")} ${LanguageManager.formatCurrency(currVal)}",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isGold) GoldDark else Color(0xFF1E293B)
                                            )
                                        }
                                    }

                                    if (isSplit) {
                                        Divider(color = Color(0xFFE2E8F0))

                                        Text(
                                            text = loc(en = "💵 Cash Payment (રોકડ ચુકવણી):", gu = "💵 રોકડ ચુકવણી:"),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = CashGreen
                                        )

                                        OutlinedTextField(
                                            value = inlineRemainingCashText,
                                            onValueChange = {
                                                inlineRemainingCashText = it
                                                syncInlinePayments(inlineMetalWeightText, inlineMetalTouchText, inlineMetalRateText, it)
                                            },
                                            label = { Text(loc(en = "Cash Amount (₹)", gu = "રોકડ રકમ (₹)")) },
                                            placeholder = { Text(LanguageManager.formatDouble((grandTotal - currVal).coerceAtLeast(0.0), 0)) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Payment Entries Section (Supports multiple payments / installments & gold/silver payment)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = AppStrings.paymentHistory(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = GoldDark
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = { showSplitGoldCashDialog = true },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, GoldDark)
                                ) {
                                    Text("⚡ " + loc(en = "Gold + Cash", gu = "સોનું + રોકડ"), fontSize = 11.sp, color = GoldDark, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = {
                                        editingPaymentIndex = null
                                        showAddPaymentDialog = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = GoldDark),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(AppStrings.addPaymentEntry(), fontSize = 11.sp)
                                }
                            }
                        }

                        if (payments.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                payments.forEachIndexed { pIdx, p ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp).fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                val modeName = when (p.paymentMode) {
                                                    "GOLD" -> AppStrings.modeGold()
                                                    "SILVER" -> AppStrings.modeSilver()
                                                    "ONLINE" -> AppStrings.modeOnline()
                                                    "CHEQUE" -> AppStrings.modeCheque()
                                                    else -> AppStrings.modeCash()
                                                }
                                                Text(
                                                    text = "#${pIdx + 1} $modeName",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                                if (p.metalWeight > 0) {
                                                    Text(
                                                        text = p.getFullPaymentFormula(isGu),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = GoldDark
                                                    )
                                                }
                                                if (p.note.isNotBlank()) {
                                                    Text(
                                                        text = p.note,
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            Text(
                                                text = LanguageManager.formatCurrency(p.amount),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = CashGreen
                                            )
                                            IconButton(
                                                onClick = {
                                                    editingPaymentIndex = pIdx
                                                    showAddPaymentDialog = true
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF2563EB), modifier = Modifier.size(16.dp))
                                            }
                                            IconButton(
                                                onClick = {
                                                    payments.removeAt(pIdx)
                                                    cashReceivedOrPaidText = LanguageManager.formatDouble(payments.sumOf { it.amount }, 0)
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Delete", tint = DebitRed, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Cash Paid/Received & Balance Due
                        val hasMultiOrMetalPayments = payments.size > 1 || (payments.size == 1 && payments[0].metalWeight > 0.0)
                        val paymentInputLabel = if (hasMultiOrMetalPayments) {
                            if (billType == "SALE") loc(en = "Total Payment Received", gu = "કુલ ચુકવણી મેળવી")
                            else loc(en = "Total Payment Made", gu = "કુલ ચુકવણી ચૂકવી")
                        } else {
                            AppStrings.cashReceived(billType == "SALE")
                        }

                        OutlinedTextField(
                            value = cashReceivedOrPaidText,
                            onValueChange = {
                                if (!hasMultiOrMetalPayments) {
                                    cashReceivedOrPaidText = it
                                    // If user manually enters quick amount, keep single payment in sync
                                    val amt = it.toDoubleOrNull() ?: 0.0
                                    if (payments.isEmpty() && amt > 0) {
                                        payments.add(
                                            BillPayment(
                                                id = UUID.randomUUID().toString(),
                                                entryNumber = 1,
                                                paymentMode = paymentMode,
                                                amount = amt
                                            )
                                        )
                                    } else if (payments.size == 1 && payments[0].metalWeight == 0.0) {
                                        payments[0] = payments[0].copy(amount = amt, paymentMode = paymentMode)
                                    }
                                }
                            },
                            readOnly = hasMultiOrMetalPayments,
                            label = { Text(paymentInputLabel) },
                            supportingText = if (hasMultiOrMetalPayments) {
                                { Text(loc(en = "Calculated from ${payments.size} payment entries above", gu = "ઉપરની ${payments.size} ચૂકવણી એન્ટ્રીઓ મુજબ ગણતરી કરેલ"), fontSize = 10.sp, color = CashGreen) }
                            } else null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth().testTag("bill_cash_received_input"),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(6.dp))
                        BillCalcRow(
                            title = AppStrings.balanceDue(),
                            value = LanguageManager.formatCurrency(netBalanceDue),
                            color = if (netBalanceDue > 0) DebitRed else CashGreen,
                            bold = true
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text(AppStrings.remarks()) },
                            modifier = Modifier.fillMaxWidth().testTag("bill_notes_input")
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        if (isSubmitting) return@Button
                        val currentAccount = account ?: return@Button

                        val paymentsToSave = if (payments.isNotEmpty()) {
                            payments.toList()
                        } else if (cashReceivedOrPaid > 0) {
                            listOf(
                                BillPayment(
                                    id = UUID.randomUUID().toString(),
                                    entryNumber = 1,
                                    dateTimestamp = System.currentTimeMillis(),
                                    paymentMode = paymentMode,
                                    amount = cashReceivedOrPaid,
                                    note = if (billType == "SALE") "Initial Payment" else "Karigar Initial Payment"
                                )
                            )
                        } else {
                            emptyList()
                        }

                        val resolvedPaymentMode = when {
                            paymentsToSave.size > 1 -> "MULTI"
                            paymentsToSave.size == 1 -> paymentsToSave[0].paymentMode
                            else -> paymentMode
                        }

                        val finalBill = Bill(
                            id = if (isEditMode && !editingBillId.isNullOrBlank()) editingBillId else UUID.randomUUID().toString(),
                            accountId = currentAccount.accountId,
                            billNumber = billNumber.ifBlank { "BILL-0001" },
                            billType = billType,
                            isGstBill = isGstBill,
                            gstPercent = totalGstPercent,
                            gstAmount = gstAmount,
                            isCstBill = isCstBill,
                            cstPercent = cstRate,
                            cstAmount = cstAmount,
                            partyName = partyName.trim(),
                            partyMobile = partyMobile.trim(),
                            partyAddress = partyAddress.trim(),
                            paymentMode = resolvedPaymentMode,
                            dateTimestamp = if (isEditMode) existingBillDateTimestamp else System.currentTimeMillis(),
                            itemsJson = Bill.itemsToJson(items),
                            paymentsJson = Bill.paymentsToJson(paymentsToSave),
                            subtotal = subtotal,
                            discount = discount,
                            grandTotal = grandTotal,
                            cashReceivedOrPaid = cashReceivedOrPaid,
                            oldMetalExchangeAmount = oldMetal,
                            netBalanceDue = netBalanceDue,
                            notes = notes.trim(),
                            createdAt = if (isEditMode) existingBillCreatedAt else System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )

                        isSubmitting = true
                        viewModel.saveBill(
                            bill = finalBill,
                            isEdit = isEditMode,
                            onSuccess = { saved ->
                                isSubmitting = false
                                onBillSaved(saved.id)
                            },
                            onError = {
                                isSubmitting = false
                            }
                        )
                    },
                    enabled = !isSubmitting,
                    colors = ButtonDefaults.buttonColors(containerColor = GoldDark),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag(if (isEditMode) "save_changes_btn" else "save_bill_btn")
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isEditMode) loc("Save Changes", "ફેરફારો સાચવો (Save Changes)") else AppStrings.saveAndPreview(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun BillCalcRow(
    title: String,
    value: String,
    color: Color = Color.Unspecified,
    bold: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            color = if (color != Color.Unspecified) color else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun ItemEditDialog(
    initialItem: BillItem?,
    billType: String = "SALE",
    defaultGoldRate: Double = 0.0,
    defaultSilverRate: Double = 0.0,
    isGu: Boolean,
    onDismiss: () -> Unit,
    onSave: (BillItem) -> Unit
) {
    // Empty by default for new items as requested by user
    var desc by remember {
        mutableStateOf(initialItem?.description ?: "")
    }
    var metalType by remember { mutableStateOf(initialItem?.metalType ?: "GOLD") }
    var purityText by remember { mutableStateOf(initialItem?.purity ?: "") }
    var stockClassification by remember {
        mutableStateOf(initialItem?.stockClassification ?: "JEWELLERY")
    }

    var weightText by remember {
        mutableStateOf(if (initialItem != null && initialItem.grossWeight > 0) LanguageManager.formatDouble(initialItem.grossWeight, 3) else "")
    }
    var currentTouchText by remember {
        mutableStateOf(if (initialItem != null && initialItem.currentTouch > 0) LanguageManager.formatDouble(initialItem.currentTouch, 1) else "")
    }

    // Making Charges: Support BOTH % and ₹ together
    var makingPercentText by remember {
        mutableStateOf(if (initialItem != null && initialItem.makingChargePercent > 0) LanguageManager.formatDouble(initialItem.makingChargePercent, 1) else "")
    }
    var makingRupeesText by remember {
        mutableStateOf(if (initialItem != null && initialItem.makingCharges > 0) LanguageManager.formatDouble(initialItem.makingCharges, 0) else "")
    }
    var makingRupeeMode by remember {
        mutableStateOf("PER_GRAM") // "PER_GRAM" (₹/g) or "FLAT" (₹ total)
    }

    // Rate per gram: completely EMPTY by default as requested
    var rateText by remember {
        mutableStateOf(if (initialItem != null && initialItem.ratePerGram > 0) LanguageManager.formatDouble(initialItem.ratePerGram, 0) else "")
    }

    // Calculated base touch
    val effectiveTouch by remember {
        derivedStateOf {
            val userTouch = currentTouchText.toDoubleOrNull()
            if (userTouch != null && userTouch > 0) {
                userTouch
            } else if (metalType == "GOLD") {
                val cleanK = purityText.filter { it.isDigit() || it == '.' }.toDoubleOrNull()
                if (cleanK != null && cleanK > 0) {
                    (cleanK / 24.0) * 100.0
                } else {
                    0.0
                }
            } else {
                val cleanP = purityText.filter { it.isDigit() || it == '.' }.toDoubleOrNull()
                cleanP ?: 0.0
            }
        }
    }

    val calculatedTotalTouch by remember {
        derivedStateOf {
            val makingPct = makingPercentText.toDoubleOrNull() ?: 0.0
            effectiveTouch + makingPct
        }
    }

    val calculatedTotalFine by remember {
        derivedStateOf {
            val wt = weightText.toDoubleOrNull() ?: 0.0
            if (calculatedTotalTouch > 0) {
                wt * calculatedTotalTouch / 100.0
            } else {
                wt
            }
        }
    }

    val calculatedRupeeMaking by remember {
        derivedStateOf {
            val wt = weightText.toDoubleOrNull() ?: 0.0
            val rupeeVal = makingRupeesText.toDoubleOrNull() ?: 0.0
            if (makingRupeeMode == "PER_GRAM") {
                rupeeVal * wt
            } else {
                rupeeVal
            }
        }
    }

    val calculatedTotal by remember {
        derivedStateOf {
            val rate = rateText.toDoubleOrNull() ?: 0.0
            val effectiveRate = if (metalType == "SILVER") (rate / 1000.0) else rate
            val metalAmount = calculatedTotalFine * effectiveRate
            metalAmount + calculatedRupeeMaking
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialItem != null) AppStrings.edit() else AppStrings.addItem(),
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Metal Selector
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = metalType == "GOLD",
                        onClick = {
                            metalType = "GOLD"
                            purityText = ""
                            currentTouchText = ""
                        },
                        label = { Text(AppStrings.gold()) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = metalType == "SILVER",
                        onClick = {
                            metalType = "SILVER"
                            purityText = ""
                            currentTouchText = ""
                        },
                        label = { Text(AppStrings.silver()) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Manual Gold Karat or Manual Silver Touch/Purity Input
                if (metalType == "GOLD") {
                    Text(
                        text = loc(en = "Gold Karat (Manual Input)", gu = "સોનાનું કેરેટ (મેન્યુઅલ દાખલ કરો)"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = purityText,
                        onValueChange = { input ->
                            purityText = input
                            val cleanK = input.filter { it.isDigit() || it == '.' }.toDoubleOrNull()
                            if (cleanK != null && cleanK > 0) {
                                val autoTouch = (cleanK / 24.0) * 100.0
                                currentTouchText = LanguageManager.formatDouble(autoTouch, 1)
                            }
                        },
                        label = { Text(loc(en = "Gold Karat", gu = "સોનું કેરેટ")) },
                        placeholder = { Text(loc(en = "e.g. 20, 22, 18, 24", gu = "દા.ત. 20, 22, 18, 24")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().testTag("dialog_item_gold_karat"),
                        singleLine = true
                    )
                } else {
                    Text(
                        text = loc(en = "Silver Touch / Purity % (Manual Input)", gu = "ચાંદી ટચ / પ્યોરિટી % (મેન્યુઅલ દાખલ કરો)"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = purityText,
                        onValueChange = { input ->
                            purityText = input
                            val cleanP = input.filter { it.isDigit() || it == '.' }.toDoubleOrNull()
                            if (cleanP != null && cleanP > 0) {
                                currentTouchText = LanguageManager.formatDouble(cleanP, 1)
                            }
                        },
                        label = { Text(loc(en = "Silver Touch / Purity %", gu = "ચાંદી ટચ / પ્યોરિટી %")) },
                        placeholder = { Text(loc(en = "e.g. 65, 70, 92.5, 99.9", gu = "દા.ત. 65, 70, 92.5, 99.9")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().testTag("dialog_item_silver_touch"),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Stock Classification Selector (Jewellery vs Raw Metal)
                Text(
                    text = AppStrings.stockClassification(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = stockClassification == "JEWELLERY",
                        onClick = { stockClassification = "JEWELLERY" },
                        label = { Text(AppStrings.stockJewellery(), fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = stockClassification == "METAL",
                        onClick = { stockClassification = "METAL" },
                        label = { Text(AppStrings.stockMetal(), fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text(AppStrings.itemName()) },
                    placeholder = { Text(if (metalType == "GOLD") "e.g. Ring, Chain, Bar" else "e.g. Payal, Belt, Coin") },
                    modifier = Modifier.fillMaxWidth().testTag("dialog_item_desc"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = weightText,
                        onValueChange = { weightText = it },
                        label = { Text(AppStrings.weightGram()) },
                        placeholder = { Text("0.000") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).testTag("dialog_item_gross_wt"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = rateText,
                        onValueChange = { rateText = it },
                        label = {
                            Text(
                                if (metalType == "SILVER") loc(en = "Silver Price (₹/kg)", gu = "ચાંદી ભાવ (₹/કિલો)")
                                else loc(en = "Gold Price (₹/g)", gu = "સોના ભાવ (₹/ગ્રામ)")
                            )
                        },
                        placeholder = {
                            Text(
                                if (metalType == "SILVER") loc(en = "₹/kg (Enter Price)", gu = "₹/કિલો (ભાવ દાખલ કરો)")
                                else loc(en = "₹/g (Enter Price)", gu = "₹/ગ્રામ (ભાવ દાખલ કરો)")
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).testTag("dialog_item_rate"),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = currentTouchText,
                    onValueChange = { currentTouchText = it },
                    label = { Text("${AppStrings.currentTouch()} (%)") },
                    placeholder = { Text(if (metalType == "GOLD") "e.g. 83.3 or 91.6" else "e.g. 65.0 or 92.5") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("dialog_item_touch"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Making Charge Section: Both % and ₹ supported simultaneously
                Text(
                    text = loc(en = "Making Charges (% and/or ₹)", gu = "મજૂરી / ઘડામણ (% અને/અથવા ₹)"),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = makingPercentText,
                        onValueChange = { makingPercentText = it },
                        label = { Text(loc(en = "Making (%)", gu = "મજૂરી (%)")) },
                        placeholder = { Text("e.g. 8.0") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).testTag("dialog_item_making_pct"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = makingRupeesText,
                        onValueChange = { makingRupeesText = it },
                        label = { Text(if (makingRupeeMode == "PER_GRAM") loc(en = "Making (₹/g)", gu = "મજૂરી (₹/ગ્રા)") else loc(en = "Making (₹ Flat)", gu = "મજૂરી (₹ ફિક્સ)")) },
                        placeholder = { Text(if (makingRupeeMode == "PER_GRAM") "e.g. 100" else "e.g. 500") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).testTag("dialog_item_making_rs"),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = makingRupeeMode == "PER_GRAM",
                        onClick = { makingRupeeMode = "PER_GRAM" },
                        label = { Text(loc(en = "Per Gram (₹/g)", gu = "પ્રતિ ગ્રામ (₹/ગ્રા)"), fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = makingRupeeMode == "FLAT",
                        onClick = { makingRupeeMode = "FLAT" },
                        label = { Text(loc(en = "Flat (₹ Total)", gu = "ફિક્સ રકમ (₹)"), fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Real-time breakdown card
                Surface(
                    color = GoldLight.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(loc(en = "Touch %:", gu = "ટચ %:"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${LanguageManager.formatDouble(effectiveTouch, 1)}%", fontWeight = FontWeight.Medium, fontSize = 12.sp)
                        }
                        if ((makingPercentText.toDoubleOrNull() ?: 0.0) > 0.0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(loc(en = "Making %:", gu = "મજૂરી %:"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("+${makingPercentText}%", fontWeight = FontWeight.Medium, fontSize = 12.sp)
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${AppStrings.totalTouch()}:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${LanguageManager.formatDouble(calculatedTotalTouch, 1)}%", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${AppStrings.totalFine()}:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(LanguageManager.formatWeight(calculatedTotalFine, isGu), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        if (calculatedRupeeMaking > 0.0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(loc(en = "Making (₹):", gu = "મજૂરી (₹):"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("+₹${LanguageManager.formatDouble(calculatedRupeeMaking, 0)}", fontWeight = FontWeight.Medium, fontSize = 12.sp)
                            }
                        }
                        Divider(modifier = Modifier.padding(vertical = 2.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${AppStrings.itemAmount()}:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                text = LanguageManager.formatCurrency(calculatedTotal),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = GoldDark
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val wt = weightText.toDoubleOrNull() ?: 0.0
                    val currentTouch = effectiveTouch
                    val makingPct = makingPercentText.toDoubleOrNull() ?: 0.0
                    val rupeeMaking = calculatedRupeeMaking

                    val totalTouch = calculatedTotalTouch
                    val totalFine = calculatedTotalFine
                    val rate = rateText.toDoubleOrNull() ?: 0.0
                    val total = calculatedTotal

                    val fallbackDesc = if (stockClassification == "METAL") {
                        if (metalType == "GOLD") "Gold Metal" else "Silver Metal"
                    } else {
                        if (metalType == "GOLD") "Gold Jewellery" else "Silver Jewellery"
                    }

                    // Format Gold Karat as "20K", Silver Touch as "65%"
                    val finalPurity = if (metalType == "GOLD") {
                        val cleanK = purityText.trim().filter { it.isDigit() || it == '.' }
                        if (cleanK.isNotBlank()) {
                            if (purityText.trim().endsWith("K", ignoreCase = true)) purityText.trim().uppercase() else "${cleanK}K"
                        } else {
                            "22K"
                        }
                    } else {
                        val cleanP = purityText.trim().filter { it.isDigit() || it == '.' }
                        if (cleanP.isNotBlank()) {
                            if (purityText.trim().endsWith("%")) purityText.trim() else "${cleanP}%"
                        } else {
                            if (currentTouch > 0) "${LanguageManager.formatDouble(currentTouch, 1)}%" else "92.5%"
                        }
                    }

                    val item = BillItem(
                        id = initialItem?.id ?: UUID.randomUUID().toString(),
                        description = desc.ifBlank { fallbackDesc },
                        metalType = metalType,
                        purity = finalPurity,
                        grossWeight = wt,
                        netWeight = wt,
                        currentTouch = currentTouch,
                        makingChargePercent = makingPct,
                        totalTouch = totalTouch,
                        totalFine = totalFine,
                        ratePerGram = rate,
                        makingCharges = rupeeMaking,
                        itemTotal = total,
                        stockClassification = stockClassification
                    )
                    onSave(item)
                },
                colors = ButtonDefaults.buttonColors(containerColor = GoldDark),
                modifier = Modifier.testTag("dialog_item_save_btn")
            ) {
                Text(AppStrings.confirm(), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(AppStrings.cancel()) }
        }
    )
}

@Composable
fun PaymentEntryDialog(
    initialPayment: BillPayment?,
    billType: String = "SALE",
    defaultGoldRate: Double = 0.0,
    defaultSilverRate: Double = 0.0,
    isGu: Boolean,
    onDismiss: () -> Unit,
    onSave: (BillPayment) -> Unit
) {
    var mode by remember { mutableStateOf(initialPayment?.paymentMode ?: "CASH") }
    var amountText by remember {
        mutableStateOf(if (initialPayment != null && initialPayment.amount > 0) LanguageManager.formatDouble(initialPayment.amount, 0) else "")
    }
    var metalWeightText by remember {
        mutableStateOf(if (initialPayment != null && initialPayment.metalWeight > 0) LanguageManager.formatDouble(initialPayment.metalWeight, 3) else "")
    }
    var metalTouchText by remember {
        mutableStateOf(if (initialPayment != null && initialPayment.metalTouch > 0) LanguageManager.formatDouble(initialPayment.metalTouch, 1) else if (mode == "GOLD") "99.5" else "100.0")
    }
    // Rate starts completely empty by default
    var metalRateText by remember {
        mutableStateOf(if (initialPayment != null && initialPayment.metalRate > 0) LanguageManager.formatDouble(initialPayment.metalRate, 0) else "")
    }
    var note by remember { mutableStateOf(initialPayment?.note ?: "") }

    val calculatedAmount by remember {
        derivedStateOf {
            if (mode == "GOLD" || mode == "SILVER") {
                val wt = metalWeightText.toDoubleOrNull() ?: 0.0
                val touch = metalTouchText.toDoubleOrNull() ?: 100.0
                val rate = metalRateText.toDoubleOrNull() ?: 0.0
                val effectiveRate = if (mode == "SILVER") (rate / 1000.0) else rate
                (wt * touch / 100.0) * effectiveRate
            } else {
                amountText.toDoubleOrNull() ?: 0.0
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialPayment != null) AppStrings.edit() else AppStrings.addPaymentEntry(),
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = AppStrings.paymentMode(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
                // Payment Mode Selection Chips
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = mode == "CASH",
                        onClick = { mode = "CASH" },
                        label = { Text(AppStrings.modeCash(), fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = mode == "GOLD",
                        onClick = {
                            mode = "GOLD"
                            metalTouchText = "99.5"
                        },
                        label = { Text(AppStrings.modeGold(), fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = mode == "SILVER",
                        onClick = {
                            mode = "SILVER"
                            metalTouchText = "100.0"
                        },
                        label = { Text(AppStrings.modeSilver(), fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = mode == "ONLINE",
                        onClick = { mode = "ONLINE" },
                        label = { Text(AppStrings.modeOnline(), fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = mode == "CHEQUE",
                        onClick = { mode = "CHEQUE" },
                        label = { Text(AppStrings.modeCheque(), fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (mode == "GOLD" || mode == "SILVER") {
                    Text(
                        text = if (mode == "GOLD") loc(en = "Pay by Gold Metal", gu = "સોનાની ધાતુ આપી ચૂકવણી")
                        else loc(en = "Pay by Silver Metal", gu = "ચાંદીની ધાતુ આપી ચૂકવણી"),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = GoldDark
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = metalWeightText,
                            onValueChange = { metalWeightText = it },
                            label = { Text(AppStrings.weightGram()) },
                            placeholder = { Text("0.000") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = metalRateText,
                            onValueChange = { metalRateText = it },
                            label = {
                                Text(
                                    if (mode == "SILVER") loc(en = "Silver Price (₹/kg)", gu = "ચાંદી ભાવ (₹/કિલો)")
                                    else loc(en = "Gold Price (₹/g)", gu = "સોના ભાવ (₹/ગ્રામ)")
                                )
                            },
                            placeholder = {
                                Text(
                                    if (mode == "SILVER") loc(en = "₹/kg (Enter Price)", gu = "₹/કિલો (ભાવ દાખલ કરો)")
                                    else loc(en = "₹/g (Enter Price)", gu = "₹/ગ્રામ (ભાવ દાખલ કરો)")
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    OutlinedTextField(
                        value = metalTouchText,
                        onValueChange = { metalTouchText = it },
                        label = { Text(AppStrings.currentTouch()) },
                        placeholder = { Text("99.5%") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Live Mathematical Formula Box (Exact matching handwritten paper)
                    val wt = metalWeightText.toDoubleOrNull() ?: 0.0
                    val touch = metalTouchText.toDoubleOrNull() ?: (if (mode == "GOLD") 80.0 else 100.0)
                    val fine = if (wt > 0.0) (wt * touch / 100.0) else 0.0
                    val rate = metalRateText.toDoubleOrNull() ?: 0.0
                    val rateUnit = if (mode == "SILVER") (if (isGu) "/કિલો" else "/kg") else (if (isGu) "/ગ્રા" else "/g")
                    val fineLabel = if (isGu) "ફાઇન" else "FINE"
                    val priceLabel = if (isGu) "ભાવ" else "CURRENT PRICE"

                    Surface(
                        color = GoldLight.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = loc(en = "Calculation Formula (ગણતરી):", gu = "ગણતરી ફોર્મ્યુલા:"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = GoldDark
                            )
                            Text(
                                text = "${LanguageManager.formatDouble(wt, 3)}g . ${LanguageManager.formatDouble(touch, 1)}% = ${LanguageManager.formatDouble(fine, 3)}g $fineLabel",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1E293B)
                            )
                            Text(
                                text = "${LanguageManager.formatDouble(fine, 3)}g × $priceLabel (${LanguageManager.formatDouble(rate, 0)}$rateUnit) = ${LanguageManager.formatCurrency(calculatedAmount)}",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = GoldDark
                            )
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text(AppStrings.paidAmount()) },
                        placeholder = { Text("₹ 0") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(AppStrings.remarks()) },
                    placeholder = { Text("e.g. 1st installment, advance, cash counter") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Surface(
                    color = CashGreen.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(AppStrings.itemAmount(), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            text = LanguageManager.formatCurrency(calculatedAmount),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = CashGreen
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalAmount = calculatedAmount
                    val wt = if (mode == "GOLD" || mode == "SILVER") (metalWeightText.toDoubleOrNull() ?: 0.0) else 0.0
                    val touch = if (mode == "GOLD" || mode == "SILVER") (metalTouchText.toDoubleOrNull() ?: 100.0) else 0.0
                    val rate = if (mode == "GOLD" || mode == "SILVER") (metalRateText.toDoubleOrNull() ?: 0.0) else 0.0
                    val fine = if (wt > 0.0) (wt * touch / 100.0) else 0.0

                    val payment = BillPayment(
                        id = initialPayment?.id ?: UUID.randomUUID().toString(),
                        entryNumber = initialPayment?.entryNumber ?: 1,
                        dateTimestamp = initialPayment?.dateTimestamp ?: System.currentTimeMillis(),
                        paymentMode = mode,
                        amount = finalAmount,
                        metalWeight = wt,
                        metalTouch = touch,
                        metalRate = rate,
                        fineWeight = fine,
                        note = note.trim()
                    )
                    onSave(payment)
                },
                colors = ButtonDefaults.buttonColors(containerColor = GoldDark)
            ) {
                Text(AppStrings.confirm(), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(AppStrings.cancel()) }
        }
    )
}
