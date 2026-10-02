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
    var partyAadharNumber by remember { mutableStateOf("") }
    var partyPanNumber by remember { mutableStateOf("") }
    var partyGstNumber by remember { mutableStateOf("") }
    var paymentMode by remember { mutableStateOf("CASH") }
    var notes by remember { mutableStateOf("") }

    var discountText by remember { mutableStateOf("0") }
    var oldMetalExchangeText by remember { mutableStateOf("0") }
    var otherChargesText by remember { mutableStateOf("") }
    var otherChargesRemarkText by remember { mutableStateOf("") }
    var cashReceivedOrPaidText by remember { mutableStateOf("0") }

    val items = remember { mutableStateListOf<BillItem>() }
    val payments = remember { mutableStateListOf<BillPayment>() }

    // Inline Metal Payment fields (for Gold + Cash, Silver + Cash, or pure metal payment)
    var inlineMetalWeightText by remember { mutableStateOf("") }
    var inlineMetalTouchText by remember { mutableStateOf("") }
    var inlineMetalRateText by remember { mutableStateOf("") }
    var inlineMetalMakingText by remember { mutableStateOf("") }
    var inlineGoldPaidFineText by remember { mutableStateOf("") }
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
                partyAadharNumber = b.partyAadharNumber
                partyPanNumber = b.partyPanNumber
                partyGstNumber = b.partyGstNumber
                paymentMode = b.paymentMode
                notes = b.notes
                discountText = if (b.discount > 0) LanguageManager.formatDouble(b.discount, 0) else "0"
                oldMetalExchangeText = if (b.oldMetalExchangeAmount > 0) LanguageManager.formatDouble(b.oldMetalExchangeAmount, 0) else "0"
                otherChargesText = if (b.otherCharges > 0) LanguageManager.formatDouble(b.otherCharges, 0) else ""
                otherChargesRemarkText = b.otherChargesRemark
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
    val otherCharges = otherChargesText.toDoubleOrNull() ?: 0.0
    val grandTotal = (subtotal + gstAmount + cstAmount + otherCharges - discount - oldMetal).coerceAtLeast(0.0)

    val totalPaidFromPayments = payments.sumOf { it.amount }
    val cashReceivedOrPaid = if (payments.isNotEmpty()) totalPaidFromPayments else (cashReceivedOrPaidText.toDoubleOrNull() ?: 0.0)
    val netBalanceDue = (grandTotal - cashReceivedOrPaid).coerceAtLeast(0.0)

    if (showAddItemDialog) {
        val currentItem = editingItemIndex?.let { items.getOrNull(it) }
        ItemEditDialog(
            initialItem = currentItem,
            billType = billType,
            defaultGoldRate = settings?.goldRate22k ?: 0.0,
            // Silver payment rate is always entered as ₹/kg. Do not inject the legacy
            // settings silver rate here because older settings may be stored as ₹/g.
            defaultSilverRate = 0.0,
            initialOtherCharges = otherChargesText,
            initialOtherChargesRemark = otherChargesRemarkText,
            onOtherChargesChange = { amount, remark ->
                otherChargesText = amount
                otherChargesRemarkText = remark
            },
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
            defaultSilverRate = 0.0,
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
        // BillItem.ratePerGram for Silver is ₹/g; the payment dialog requires ₹/kg.
        // Convert the item rate once when using it as the payment default.
        val defaultSilRate = items.firstOrNull { it.metalType.equals("SILVER", ignoreCase = true) }
            ?.ratePerGram
            ?.takeIf { it > 0.0 }
            ?.times(1000.0)
            ?: 0.0

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

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = partyAadharNumber,
                            onValueChange = { if (it.length <= 12 && it.all { c -> c.isDigit() }) partyAadharNumber = it },
                            label = { Text("Customer Aadhaar Card No.") },
                            leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, tint = GoldDark) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().testTag("bill_party_aadhaar"),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = partyPanNumber,
                            onValueChange = { partyPanNumber = it.uppercase().take(10) },
                            label = { Text("Customer PAN Card No.") },
                            leadingIcon = { Icon(Icons.Default.CreditCard, contentDescription = null, tint = GoldDark) },
                            modifier = Modifier.fillMaxWidth().testTag("bill_party_pan"),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = partyGstNumber,
                            onValueChange = { partyGstNumber = it.uppercase() },
                            label = { Text("Customer GST No.") },
                            leadingIcon = { Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = GoldDark) },
                            modifier = Modifier.fillMaxWidth().testTag("bill_party_gst"),
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

                        // Other Charges stays at bill level so it does not disappear when Add Item closes.
                        Card(
                            modifier = Modifier.fillMaxWidth().testTag("bill_other_charges_box"),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Other Charges",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = GoldDark
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = otherChargesRemarkText,
                                        onValueChange = { otherChargesRemarkText = it },
                                        label = { Text("Remark") },
                                        placeholder = { Text("Rhodium / HUID / AD / Moti etc.") },
                                        modifier = Modifier.weight(1.4f).testTag("bill_other_charges_remark"),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = otherChargesText,
                                        onValueChange = { otherChargesText = it },
                                        label = { Text("Amount") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.weight(0.8f).testTag("bill_other_charges_amount"),
                                        singleLine = true
                                    )
                                }
                            }
                        }

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
                                    inlineMetalWeightText = ""
                                    inlineMetalTouchText = ""
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
                                    inlineMetalWeightText = ""
                                    inlineMetalTouchText = ""
                                    if (inlineMetalRateText.isBlank()) {
                                        val defRate = 0.0
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
                                    // Payment fields start blank; no automatic 6g or rate.
                                },
                                label = { Text(AppStrings.modeGold(), fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = paymentMode == "SILVER",
                                onClick = {
                                    paymentMode = "SILVER"
                                    // Payment fields start blank; no automatic weight or rate.
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
                            val isSale = billType == "SALE"
                            val metalName = if (isGold) loc(en = "Gold", gu = "સોનું") else loc(en = "Silver", gu = "ચાંદી")
                            val defaultRate = if (isGold) (settings?.goldRate22k ?: 7200.0) else 0.0

                            val syncInlinePayments: (String, String, String, String, String) -> Unit = { wtS, touchS, rateS, cashS, paidFineS ->
                                val isSale = billType == "SALE"
                                val wt = wtS.toDoubleOrNull() ?: 0.0
                                val touch = touchS.toDoubleOrNull() ?: 0.0
                                val totalTouch = touch
                                val rate = rateS.toDoubleOrNull() ?: 0.0
                                val totalFine = if (wt > 0) wt * totalTouch / 100.0 else 0.0
                                val ratePerGram = if (isGold) rate else rate / 1000.0
                                val paidFine = if (isGold && !isSale) paidFineS.toDoubleOrNull()?.coerceIn(0.0, totalFine) ?: totalFine else totalFine
                                val metalVal = if (isGold) paidFine * ratePerGram else totalFine * ratePerGram
                                val metalPayment = BillPayment(
                                    id = payments.find { it.paymentMode == (if (isGold) "GOLD" else "SILVER") }?.id ?: UUID.randomUUID().toString(),
                                    entryNumber = 1, dateTimestamp = System.currentTimeMillis(),
                                    paymentMode = if (isGold) "GOLD" else "SILVER", amount = metalVal,
                                    metalWeight = if (isGold && isSplit && !isSale) paidFine else wt,
                                    metalTouch = if (isGold && isSplit && !isSale) 100.0 else totalTouch,
                                    metalRate = rate, fineWeight = if (isGold) paidFine else totalFine,
                                    note = if (isSale) "$metalName received (મેળવેલ $metalName)" else "$metalName paid (ચૂકવેલ $metalName)"
                                )
                                if (isSplit) {
                                    val autoCash = if (isGold && !isSale) ((totalFine - paidFine).coerceAtLeast(0.0) * ratePerGram) else (grandTotal - metalVal).coerceAtLeast(0.0)
                                    val cashVal = cashS.toDoubleOrNull() ?: autoCash
                                    val cashPayment = BillPayment(
                                        id = payments.find { it.paymentMode == "CASH" }?.id ?: UUID.randomUUID().toString(),
                                        entryNumber = 2, dateTimestamp = System.currentTimeMillis(), paymentMode = "CASH",
                                        amount = cashVal, note = if (isSale) "Cash received (રોકડ મેળવી)" else "Cash paid (રોકડ ચૂકવી)"
                                    )
                                    payments.clear(); payments.add(metalPayment); payments.add(cashPayment)
                                    cashReceivedOrPaidText = LanguageManager.formatDouble(metalVal + cashVal, 0)
                                } else {
                                    payments.clear(); payments.add(metalPayment)
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
                                                val wtAuto = it.toDoubleOrNull() ?: 0.0
                                                val touchAuto = inlineMetalTouchText.toDoubleOrNull() ?: 0.0
                                                inlineGoldPaidFineText = if (isGold && wtAuto > 0.0 && touchAuto > 0.0) LanguageManager.formatDouble(wtAuto * touchAuto / 100.0, 3) else ""
                                                syncInlinePayments(it, inlineMetalTouchText, inlineMetalRateText, inlineRemainingCashText, inlineGoldPaidFineText)
                                            },
                                            label = { Text(loc(en = "$metalName Wt (g)", gu = "$metalName વજન (ગ્રા)")) },
                                            placeholder = { Text("0.000") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            modifier = Modifier.weight(1f),
                                            singleLine = true
                                        )
                                        OutlinedTextField(
                                            value = inlineMetalTouchText,
                                            onValueChange = {
                                                inlineMetalTouchText = it
                                                val wtAuto = inlineMetalWeightText.toDoubleOrNull() ?: 0.0
                                                val touchAuto = it.toDoubleOrNull() ?: 0.0
                                                inlineGoldPaidFineText = if (isGold && wtAuto > 0.0 && touchAuto > 0.0) LanguageManager.formatDouble(wtAuto * touchAuto / 100.0, 3) else ""
                                                syncInlinePayments(inlineMetalWeightText, it, inlineMetalRateText, inlineRemainingCashText, inlineGoldPaidFineText)
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
                                            syncInlinePayments(inlineMetalWeightText, inlineMetalTouchText, it, inlineRemainingCashText, inlineGoldPaidFineText)
                                        },
                                        label = { Text(loc(en = "$metalName Rate ($rateUnit)", gu = "$metalName ભાવ ($rateUnit)")) },
                                        placeholder = { Text(LanguageManager.formatDouble(defaultRate, 0)) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )

                                    val currWt = inlineMetalWeightText.toDoubleOrNull() ?: 0.0
                                    val currTouch = inlineMetalTouchText.toDoubleOrNull() ?: 0.0
                                    val currTotalTouch = currTouch
                                    val currRate = inlineMetalRateText.toDoubleOrNull() ?: 0.0
                                    val currFine = if (currWt > 0) currWt * currTotalTouch / 100.0 else 0.0
                                    val currRatePerGram = if (isGold) currRate else currRate / 1000.0
                                    val enteredPaidFine = if (isGold && isSplit && !isSale) inlineGoldPaidFineText.toDoubleOrNull()?.coerceIn(0.0, currFine) ?: 0.0 else currFine
                                    val currVal = if (isGold) enteredPaidFine * currRatePerGram else currFine * currRatePerGram

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
                                                text = if (isGold && isSplit && !isSale) {
                                                    "${loc(en = "Total Fine:", gu = "કુલ ફાઇન:")} ${LanguageManager.formatDouble(currFine, 3)}g"
                                                } else {
                                                    "${loc(en = "Fine:", gu = "ફાઇન:")} ${LanguageManager.formatDouble(currFine, 3)}g (${LanguageManager.formatDouble(currTotalTouch, 1)}%)"
                                                },
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

                                    if (isGold && !isSale) {
                                        OutlinedTextField(
                                            value = inlineGoldPaidFineText,
                                            onValueChange = { inlineGoldPaidFineText = it; syncInlinePayments(inlineMetalWeightText, inlineMetalTouchText, inlineMetalRateText, inlineRemainingCashText, it) },
                                            label = { Text(loc(en = "Gold Fine Given (g)", gu = "સોનામાં આપેલ ફાઇન (ગ્રા)")) },
                                            placeholder = { Text("0.000") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            modifier = Modifier.fillMaxWidth(), singleLine = true
                                        )
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
                                                syncInlinePayments(inlineMetalWeightText, inlineMetalTouchText, inlineMetalRateText, it, inlineGoldPaidFineText)
                                            },
                                            label = { Text(loc(en = "Cash Amount (₹)", gu = "રોકડ રકમ (₹)")) },
                                            placeholder = { Text(
                                                LanguageManager.formatDouble(
                                                    if (isGold && !isSale) {
                                                        val paidFine = inlineGoldPaidFineText.toDoubleOrNull() ?: 0.0
                                                        ((currFine - paidFine).coerceAtLeast(0.0) * currRatePerGram)
                                                    } else (grandTotal - currVal).coerceAtLeast(0.0),
                                                    0
                                                )
                                            ) },
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
                                                        text = p.getFormattedBreakdown(isGu, billType == "SALE"),
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
                            partyAadharNumber = partyAadharNumber.trim(),
                            partyPanNumber = partyPanNumber.trim().uppercase(),
                            partyGstNumber = partyGstNumber.trim().uppercase(),
                            paymentMode = resolvedPaymentMode,
                            dateTimestamp = if (isEditMode) existingBillDateTimestamp else System.currentTimeMillis(),
                            itemsJson = Bill.itemsToJson(items),
                            paymentsJson = Bill.paymentsToJson(paymentsToSave),
                            subtotal = subtotal,
                            discount = discount,
                            grandTotal = grandTotal,
                            cashReceivedOrPaid = cashReceivedOrPaid,
                            oldMetalExchangeAmount = oldMetal,
                            otherCharges = otherCharges,
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
    initialOtherCharges: String = "",
    initialOtherChargesRemark: String = "",
    onOtherChargesChange: (String, String) -> Unit = { _, _ -> },
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
    // Gold can be entered as Karat, Touch/Purity, or both (for example 22K / 916).
    var goldKaratText by remember {
        mutableStateOf(
            initialItem?.purity
                ?.substringBefore("/")
                ?.trim()
                ?.removeSuffix("K")
                ?.removeSuffix("k")
                ?.takeIf { it.isNotBlank() } ?: ""
        )
    }
    var goldTouchText by remember {
        mutableStateOf(
            initialItem?.currentTouch
                ?.takeIf { it > 0 }
                ?.let { LanguageManager.formatDouble(it, 1) } ?: ""
        )
    }
    var stockClassification by remember {
        mutableStateOf(initialItem?.stockClassification ?: "JEWELLERY")
    }

    var weightText by remember {
        mutableStateOf(if (initialItem != null && initialItem.grossWeight > 0) LanguageManager.formatDouble(initialItem.grossWeight, 3) else "")
    }
    var netWeightText by remember { mutableStateOf(if (initialItem != null && initialItem.netWeight > 0) LanguageManager.formatDouble(initialItem.netWeight, 3) else "") }
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
                val explicitTouch = goldTouchText.filter { it.isDigit() || it == '.' }.toDoubleOrNull()
                if (explicitTouch != null && explicitTouch > 0) {
                    if (explicitTouch > 100.0 && explicitTouch <= 1000.0) explicitTouch / 10.0 else explicitTouch
                } else {
                    val cleanK = goldKaratText.filter { it.isDigit() || it == '.' }.toDoubleOrNull()
                    if (cleanK != null && cleanK > 0) (cleanK / 24.0) * 100.0 else 0.0
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
            if (metalType == "SILVER") effectiveTouch else effectiveTouch + makingPct
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
            val wt = if (metalType == "SILVER") (netWeightText.toDoubleOrNull() ?: 0.0) else (weightText.toDoubleOrNull() ?: 0.0)
            val rupeeVal = makingRupeesText.toDoubleOrNull() ?: 0.0
            if (makingRupeeMode == "PER_GRAM") rupeeVal * wt else rupeeVal
        }
    }

    val calculatedSilverPrice by remember {
        derivedStateOf {
            val netWt = netWeightText.toDoubleOrNull() ?: 0.0
            val ratePerKg = rateText.toDoubleOrNull() ?: 0.0
            netWt * (ratePerKg / 1000.0)
        }
    }

    val calculatedSilverPercentMaking by remember {
        derivedStateOf {
            calculatedSilverPrice * (makingPercentText.toDoubleOrNull() ?: 0.0) / 100.0
        }
    }

    val calculatedSilverLabour by remember {
        derivedStateOf {
            calculatedSilverPercentMaking + calculatedRupeeMaking
        }
    }

    // Customer Sale Gold is valued on net weight + labour.
    // Karigar Purchase Gold is valued on the final fine (weight × (touch + making)%).
    // Example: 2g × (87% + 3%) = 1.800g fine; at ₹15,000/g = ₹27,000.
    val calculatedGoldAmount by remember {
        derivedStateOf {
            val rate = rateText.toDoubleOrNull() ?: 0.0
            if (billType == "SALE") {
                (netWeightText.toDoubleOrNull() ?: 0.0) * rate
            } else {
                calculatedTotalFine * rate
            }
        }
    }
    val calculatedGoldLabour by remember {
        derivedStateOf {
            if (billType == "SALE") {
                calculatedGoldAmount * (makingPercentText.toDoubleOrNull() ?: 0.0) / 100.0
            } else {
                0.0
            }
        }
    }
    val calculatedTotal by remember {
        derivedStateOf {
            if (metalType == "GOLD") {
                if (billType == "SALE") calculatedGoldAmount + calculatedGoldLabour
                else calculatedGoldAmount
            } else {
                calculatedSilverPrice + calculatedSilverLabour
            }
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
                            goldKaratText = ""
                            goldTouchText = ""
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
                        text = loc(en = "Gold Karat + Touch / Purity (Optional)", gu = "સોનું કેરેટ + ટચ / પ્યોરિટી (વૈકલ્પિક)"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = goldKaratText,
                            onValueChange = { goldKaratText = it },
                            label = { Text(loc(en = "Gold Karat", gu = "સોનું કેરેટ")) },
                            placeholder = { Text("22") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f).testTag("dialog_item_gold_karat"),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = goldTouchText,
                            onValueChange = { goldTouchText = it },
                            label = { Text(loc(en = "Touch / Purity", gu = "ટચ / પ્યોરિટી")) },
                            placeholder = { Text("916 or 91.6") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f).testTag("dialog_item_gold_touch"),
                            singleLine = true
                        )
                    }
                    Text(
                        text = loc(
                            en = "Enter either one, or both (example: 22K / 916).",
                            gu = "એક, અથવા બંને લખી શકો (ઉદાહરણ: 22K / 916)."
                        ),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        label = { Text(if (metalType == "GOLD") loc(en = "Gross Weight (g)", gu = "ગ્રોસ વજન (ગ્રા)") else AppStrings.weightGram()) },
                        placeholder = { Text("0.000") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).testTag("dialog_item_gross_wt"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = netWeightText,
                            onValueChange = { netWeightText = it },
                            label = { Text(loc(en = "Net Weight (g)", gu = "નેટ વજન (ગ્રા)")) },
                            placeholder = { Text("0.000") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f).testTag("dialog_item_net_wt"),
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

                if (metalType == "SILVER") {
                    OutlinedTextField(
                        value = currentTouchText,
                        onValueChange = { currentTouchText = it },
                        label = { Text("${AppStrings.currentTouch()} (%)") },
                        placeholder = { Text("e.g. 65.0 or 92.5") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().testTag("dialog_item_touch"),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Making Charge Section: Both % and ₹ supported simultaneously
                Text(
                    text = if (metalType == "GOLD") loc(en = "Labour Charge", gu = "લેબર ચાર્જ") else loc(en = "Making Charges (% and/or ₹)", gu = "મજૂરી / ઘડામણ (% અને/અથવા ₹)"),
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

                    if (metalType == "SILVER") OutlinedTextField(
                        value = makingRupeesText,
                        onValueChange = { makingRupeesText = it },
                        label = { Text(loc(en = "Making (₹/g)", gu = "મજૂરી (₹/ગ્રા)")) },
                        placeholder = { Text("e.g. 100") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).testTag("dialog_item_making_rs"),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (metalType == "SILVER") {
                    Text(
                        text = loc(en = "Making ₹ is calculated per gram", gu = "મેકિંગ ₹ પ્રતિ ગ્રામ પ્રમાણે ગણાશે"),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        if (metalType == "GOLD") {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Gold Amount:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(LanguageManager.formatCurrency(calculatedGoldAmount), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            if (billType == "KARIGAR_PURCHASE") {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Fine Gold:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${LanguageManager.formatDouble(calculatedTotalFine, 3)}g", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            } else {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Labour:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${LanguageManager.formatDouble(makingPercentText.toDoubleOrNull() ?: 0.0, 1)}% = ${LanguageManager.formatCurrency(calculatedGoldLabour)}", fontWeight = FontWeight.Medium, fontSize = 12.sp)
                                }
                            }
                        } else {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Silver Price:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(LanguageManager.formatCurrency(calculatedSilverPrice), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            if (calculatedSilverPercentMaking > 0.0) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Labour (${LanguageManager.formatDouble(makingPercentText.toDoubleOrNull() ?: 0.0, 1)}%):", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(LanguageManager.formatCurrency(calculatedSilverPercentMaking), fontWeight = FontWeight.Medium, fontSize = 12.sp)
                                }
                            }
                            if (calculatedRupeeMaking > 0.0) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Making (₹):", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("+${LanguageManager.formatCurrency(calculatedRupeeMaking)}", fontWeight = FontWeight.Medium, fontSize = 12.sp)
                                }
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
                    val nw = netWeightText.toDoubleOrNull() ?: wt
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

                    // Gold purity may contain Karat, Touch/Purity, or both.
                    // Examples: "22K", "916", or "22K / 916".
                    val finalPurity = if (metalType == "GOLD") {
                        val cleanK = goldKaratText.trim().filter { it.isDigit() || it == '.' }
                        val cleanTouch = goldTouchText.trim().filter { it.isDigit() || it == '.' }
                        val karatLabel = if (cleanK.isNotBlank()) "${cleanK}K" else ""
                        val touchLabel = if (cleanTouch.isNotBlank()) cleanTouch else ""
                        when {
                            karatLabel.isNotBlank() && touchLabel.isNotBlank() -> "$karatLabel / $touchLabel"
                            karatLabel.isNotBlank() -> karatLabel
                            touchLabel.isNotBlank() -> touchLabel
                            else -> ""
                        }
                    } else {
                        val cleanP = purityText.trim().filter { it.isDigit() || it == '.' }
                        if (cleanP.isNotBlank()) {
                            if (purityText.trim().endsWith("%")) purityText.trim() else "${cleanP}%"
                        } else {
                            ""
                        }
                    }

                    val item = BillItem(
                        id = initialItem?.id ?: UUID.randomUUID().toString(),
                        description = desc.ifBlank { fallbackDesc },
                        metalType = metalType,
                        purity = finalPurity,
                        grossWeight = wt,
                        netWeight = nw,
                        currentTouch = currentTouch,
                        makingChargePercent = makingPct,
                        totalTouch = totalTouch,
                        totalFine = totalFine,
                        ratePerGram = rate,
                        makingCharges = if (metalType == "GOLD" && billType == "SALE") calculatedGoldLabour else rupeeMaking,
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
        mutableStateOf(if (initialPayment != null && initialPayment.metalTouch > 0) LanguageManager.formatDouble(initialPayment.metalTouch, 1) else "")
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
                val touch = metalTouchText.toDoubleOrNull() ?: 0.0
                val enteredRate = metalRateText.toDoubleOrNull() ?: 0.0
                // Silver payment rate is entered/stored/displayed as ₹/kg.
                // Convert the kg rate to ₹/g exactly once for gram-based calculation.
                // Example: 1,000g × 100% × ₹234,000/kg ÷ 1,000 = ₹234,000.
                val ratePerGram = if (mode == "SILVER") enteredRate / 1000.0 else enteredRate
                (wt * touch / 100.0) * ratePerGram
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
                            metalTouchText = ""
                        },
                        label = { Text(AppStrings.modeGold(), fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = mode == "SILVER",
                        onClick = {
                            mode = "SILVER"
                            metalTouchText = ""
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
                    val touch = metalTouchText.toDoubleOrNull() ?: 0.0
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
                    val touch = if (mode == "GOLD" || mode == "SILVER") (metalTouchText.toDoubleOrNull() ?: 0.0) else 0.0
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
