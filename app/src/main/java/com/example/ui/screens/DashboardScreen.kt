package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.StockTransaction
import com.example.ui.components.*
import com.example.ui.locale.AppStrings
import com.example.ui.locale.LanguageManager
import com.example.ui.locale.isAppGujarati
import com.example.ui.locale.loc
import com.example.ui.theme.*
import com.example.ui.viewmodel.JewelleryViewModel
import com.example.util.ImageHelper

enum class DashboardTab {
    GOLD,
    SILVER,
    CASH
}

data class SubcategoryStockData(
    val categoryKey: String,
    val displayName: String,
    val unit: String,
    val openingStock: Double,
    val totalCredit: Double,
    val totalDebit: Double,
    val currentBalance: Double,
    val transactions: List<StockTransaction>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: JewelleryViewModel,
    onNavigateToCreateBill: (billType: String) -> Unit,
    onNavigateToBillHistory: () -> Unit,
    onNavigateToStatement: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onLogout: () -> Unit
) {
    val account by viewModel.currentAccount.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val isGu = isAppGujarati()

    var selectedTab by remember { mutableStateOf(DashboardTab.GOLD) }
    var goldSubIndex by remember { mutableStateOf(0) } // 0: Jewellery Gold, 1: GOLD
    var silverSubIndex by remember { mutableStateOf(0) } // 0: Jewellery Silver, 1: SILVER
    var cashSubIndex by remember { mutableStateOf(0) } // 0: CASH

    // Net stock values
    val goldJewelleryNet by viewModel.goldJewelleryNet.collectAsState()
    val goldMetalNet by viewModel.goldMetalNet.collectAsState()
    val silverJewelleryNet by viewModel.silverJewelleryNet.collectAsState()
    val silverMetalNet by viewModel.silverMetalNet.collectAsState()
    val cashNet by viewModel.cashNet.collectAsState()

    // Transactions
    val goldJewelleryTx by viewModel.goldJewelleryTransactions.collectAsState()
    val goldMetalTx by viewModel.goldMetalTransactions.collectAsState()
    val silverJewelleryTx by viewModel.silverJewelleryTransactions.collectAsState()
    val silverMetalTx by viewModel.silverMetalTransactions.collectAsState()
    val cashTx by viewModel.cashTransactions.collectAsState()

    // State for Adding Credit / Debit Dialog
    var showAddDialog by remember { mutableStateOf(false) }
    var isAddingCredit by remember { mutableStateOf(true) }
    var targetCategory by remember { mutableStateOf("GOLD_JEWELLERY") }
    var targetCategoryName by remember { mutableStateOf("") }
    var targetUnit by remember { mutableStateOf("g") }

    // State for Opening Stock Dialog
    var showOpeningStockDialog by remember { mutableStateOf(false) }
    var openingStockCategory by remember { mutableStateOf("GOLD_JEWELLERY") }
    var openingStockCategoryName by remember { mutableStateOf("") }
    var openingStockUnit by remember { mutableStateOf("g") }

    // State for Category Transaction History Dialog
    var showCategoryHistoryDialog by remember { mutableStateOf(false) }

    // State for Cash -> Metal Purchase Dialog
    var showCashMetalPurchaseDialog by remember { mutableStateOf(false) }

    // State for Logout confirmation
    var showLogoutDialog by remember { mutableStateOf(false) }

    // Helper to compute subcategory stock data
    fun computeData(
        catKey: String,
        displayName: String,
        unit: String,
        txList: List<StockTransaction>
    ): SubcategoryStockData {
        val openingTx = txList.find {
            it.remark.contains("Opening Stock", ignoreCase = true) || it.remark.contains("શરૂઆત")
        }
        val opening = openingTx?.quantityOrAmount ?: 0.0
        val creditEx = txList.filter { it.type == "CREDIT" && it != openingTx }.sumOf { it.quantityOrAmount }
        val debit = txList.filter { it.type == "DEBIT" }.sumOf { it.quantityOrAmount }
        val balance = (opening + creditEx) - debit

        return SubcategoryStockData(
            categoryKey = catKey,
            displayName = displayName,
            unit = unit,
            openingStock = opening,
            totalCredit = creditEx,
            totalDebit = debit,
            currentBalance = balance,
            transactions = txList
        )
    }

    val goldJewelleryData = computeData("GOLD_JEWELLERY", AppStrings.goldJewellery(), "g", goldJewelleryTx)
    val goldMetalData = computeData("GOLD_METAL", AppStrings.goldMetal(), "g", goldMetalTx)
    val silverJewelleryData = computeData("SILVER_JEWELLERY", AppStrings.silverJewellery(), "g", silverJewelleryTx)
    val silverMetalData = computeData("SILVER_METAL", AppStrings.silverMetal(), "g", silverMetalTx)
    val cashData = computeData("CASH", AppStrings.cash(), "₹", cashTx)

    // Current active stock card data
    val activeData: SubcategoryStockData = when (selectedTab) {
        DashboardTab.GOLD -> if (goldSubIndex == 0) goldJewelleryData else goldMetalData
        DashboardTab.SILVER -> if (silverSubIndex == 0) silverJewelleryData else silverMetalData
        DashboardTab.CASH -> cashData
    }

    if (showOpeningStockDialog) {
        AddOpeningStockDialog(
            categoryName = openingStockCategoryName,
            unit = openingStockUnit,
            onDismiss = { showOpeningStockDialog = false },
            onSave = { amount, remark ->
                viewModel.setOpeningStock(
                    category = openingStockCategory,
                    amount = amount,
                    unit = openingStockUnit,
                    remark = remark,
                    onSuccess = { showOpeningStockDialog = false }
                )
            }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(AppStrings.logout(), fontWeight = FontWeight.Bold) },
            text = { Text(AppStrings.logoutConfirm()) },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.logout(onLogout)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DebitRed),
                    modifier = Modifier.testTag("confirm_logout_btn")
                ) {
                    Text(AppStrings.logout(), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(AppStrings.cancel())
                }
            }
        )
    }

    if (showAddDialog) {
        AddStockEntryDialog(
            isCredit = isAddingCredit,
            categoryName = targetCategoryName,
            defaultUnit = targetUnit,
            onDismiss = { showAddDialog = false },
            onSave = { amount, remark ->
                viewModel.addManualStockEntry(
                    type = if (isAddingCredit) "CREDIT" else "DEBIT",
                    category = targetCategory,
                    amount = amount,
                    unit = targetUnit,
                    remark = remark,
                    onSuccess = { showAddDialog = false }
                )
            }
        )
    }

    if (showCashMetalPurchaseDialog) {
        CashToMetalPurchaseDialog(
            isGu = isGu,
            onDismiss = { showCashMetalPurchaseDialog = false },
            onConfirm = { category, grams, cashAmount, remark ->
                viewModel.recordCashToMetalPurchase(
                    metalCategory = category,
                    grams = grams,
                    cashAmount = cashAmount,
                    remark = remark,
                    onSuccess = { showCashMetalPurchaseDialog = false }
                )
            }
        )
    }

    if (showCategoryHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showCategoryHistoryDialog = false },
            title = {
                Text(
                    text = "${activeData.displayName} - ${AppStrings.history()}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Box(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                    if (activeData.transactions.isEmpty()) {
                        Text(
                            text = AppStrings.noTransactions(),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(activeData.transactions) { tx ->
                                StockTransactionRow(
                                    tx = tx,
                                    onDelete = { viewModel.deleteStockEntry(tx) }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showCategoryHistoryDialog = false },
                    modifier = Modifier.testTag("close_category_history_btn")
                ) {
                    Text(AppStrings.close(), fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Color definitions matching the screenshot
    val topBarBg = Color(0xFF083375)
    val goldTextYellow = Color(0xFFFBBF24)
    val gstTextBlue = Color(0xFFBFDBFE)
    val navyBtnBg = Color(0xFF0A233F)
    val amberBtnBg = Color(0xFFD97706)
    val activeBalanceCardBg = Color(0xFF082F6A)
    val greenCreditColor = Color(0xFF16A34A)
    val redDebitColor = Color(0xFFDC2626)

    val logoBitmap = remember(settings?.logoBase64) {
        ImageHelper.base64ToBitmap(settings?.logoBase64)
    }

    val jewellerName = settings?.jewellerName?.ifBlank { account?.jewellerName ?: "amitgold" }
        ?: (account?.jewellerName?.ifBlank { "amitgold" } ?: "amitgold")

    val gstNumber = settings?.gstNumber?.ifBlank { "12ASDFG3456H7J" } ?: "12ASDFG3456H7J"

    Scaffold(
        topBar = {
            // Dark Royal Blue Top Bar matching the photo
            Surface(
                color = topBarBg,
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Circular Avatar with Gold Ring
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0D254C))
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (logoBitmap != null) {
                            Image(
                                bitmap = logoBitmap.asImageBitmap(),
                                contentDescription = "Shop Logo",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Diamond,
                                contentDescription = "Jewellery Logo",
                                tint = goldTextYellow,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Shop Name & GST in Column
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = jewellerName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = goldTextYellow
                        )
                        Text(
                            text = "GST: $gstNumber",
                            fontSize = 11.sp,
                            color = gstTextBlue
                        )
                    }

                    // Sync Refresh Icon
                    IconButton(
                        onClick = { viewModel.manualSync() },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("dashboard_sync_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sync",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Language Toggle Badge
                    val currentLang = settings?.language ?: "en"
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF1E4988),
                        modifier = Modifier
                            .clickable {
                                val newLang = if (currentLang == "gu") "en" else "gu"
                                viewModel.updateLanguage(newLang)
                            }
                            .testTag("dashboard_lang_toggle_btn")
                    ) {
                        Text(
                            text = if (currentLang == "gu") "ગુ" else "En",
                            color = goldTextYellow,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Settings Gear Icon
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("dashboard_settings_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = AppStrings.settings(),
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Logout Icon
                    IconButton(
                        onClick = { showLogoutDialog = true },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("dashboard_logout_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = AppStrings.logout(),
                            tint = Color(0xFFFCA5A5),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        },
        bottomBar = {
            // Bottom Bar: [ 📄 History ] ------- [ ⚙ Settings ] matching photo
            Surface(
                color = Color.White,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: History Navigation Button
                    Row(
                        modifier = Modifier
                            .clickable { onNavigateToBillHistory() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("bottom_nav_history_btn"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = null,
                            tint = Color(0xFF0F172A),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = AppStrings.history(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = Color(0xFF0F172A)
                        )
                    }

                    // Right: Settings Navigation Button
                    Row(
                        modifier = Modifier
                            .clickable { onNavigateToSettings() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("bottom_nav_settings_btn"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = Color(0xFF0F172A),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = AppStrings.settings(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = Color(0xFF0F172A)
                        )
                    }
                }
            }
        },
        containerColor = Color(0xFFF8FAFC)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            item {
                // Two Top Action Buttons Row: + Customer Sale Bill  |  + Karigar Purchase
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Navy Blue Button: + Customer Sale Bill
                    Button(
                        onClick = { onNavigateToCreateBill("SALE") },
                        colors = ButtonDefaults.buttonColors(containerColor = navyBtnBg),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("customer_sale_bill_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Scale,
                            contentDescription = null,
                            tint = goldTextYellow,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = AppStrings.customerSaleBill(),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // Amber Gold Button: + Karigar Purchase
                    Button(
                        onClick = { onNavigateToCreateBill("KARIGAR_PURCHASE") },
                        colors = ButtonDefaults.buttonColors(containerColor = amberBtnBg),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("karigar_purchase_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = AppStrings.karigarPurchaseBill(),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            item {
                // Three Main Metal Tabs: GOLD | SILVER | CASH
                Surface(
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(0.5.dp, Color(0xFFE2E8F0))
                ) {
                    TabRow(
                        selectedTabIndex = selectedTab.ordinal,
                        containerColor = Color.White,
                        contentColor = amberBtnBg,
                        indicator = { tabPositions ->
                            if (selectedTab.ordinal < tabPositions.size) {
                                TabRowDefaults.Indicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                                    color = amberBtnBg,
                                    height = 3.dp
                                )
                            }
                        },
                        divider = {}
                    ) {
                        DashboardTab.values().forEach { tab ->
                            val isSelected = selectedTab == tab
                            val title = when (tab) {
                                DashboardTab.GOLD -> AppStrings.tabGold()
                                DashboardTab.SILVER -> AppStrings.tabSilver()
                                DashboardTab.CASH -> AppStrings.tabCash()
                            }

                            Tab(
                                selected = isSelected,
                                onClick = { selectedTab = tab },
                                text = {
                                    Text(
                                        text = title,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.5.sp,
                                        color = if (isSelected) amberBtnBg else Color(0xFF64748B)
                                    )
                                },
                                modifier = Modifier.testTag("tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            }

            item {
                // Sub-category Pills (Under selected tab)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (selectedTab) {
                        DashboardTab.GOLD -> {
                            // Pill 0: Jewellery Gold
                            SubcategoryPill(
                                title = AppStrings.goldJewellery(),
                                isSelected = goldSubIndex == 0,
                                onClick = { goldSubIndex = 0 },
                                tag = "pill_gold_jewellery"
                            )
                            // Pill 1: GOLD
                            SubcategoryPill(
                                title = AppStrings.goldMetal(),
                                isSelected = goldSubIndex == 1,
                                onClick = { goldSubIndex = 1 },
                                tag = "pill_gold_metal"
                            )
                        }
                        DashboardTab.SILVER -> {
                            // Pill 0: Jewellery Silver
                            SubcategoryPill(
                                title = AppStrings.silverJewellery(),
                                isSelected = silverSubIndex == 0,
                                onClick = { silverSubIndex = 0 },
                                tag = "pill_silver_jewellery"
                            )
                            // Pill 1: SILVER
                            SubcategoryPill(
                                title = AppStrings.silverMetal(),
                                isSelected = silverSubIndex == 1,
                                onClick = { silverSubIndex = 1 },
                                tag = "pill_silver_metal"
                            )
                        }
                        DashboardTab.CASH -> {
                            // Pill 0: CASH
                            SubcategoryPill(
                                title = AppStrings.cash(),
                                isSelected = cashSubIndex == 0,
                                onClick = { cashSubIndex = 0 },
                                tag = "pill_cash"
                            )
                        }
                    }
                }
            }

            item {
                // The Active Stock Card matching photo layout
                val openingFormatted = if (activeData.unit == "₹") {
                    "₹ " + LanguageManager.formatDouble(activeData.openingStock, 2)
                } else {
                    LanguageManager.formatWeight(activeData.openingStock, isGu)
                }

                val creditFormatted = if (activeData.unit == "₹") {
                    "₹ " + LanguageManager.formatDouble(activeData.totalCredit, 2)
                } else {
                    LanguageManager.formatWeight(activeData.totalCredit, isGu)
                }

                val debitFormatted = if (activeData.unit == "₹") {
                    "₹ " + LanguageManager.formatDouble(activeData.totalDebit, 2)
                } else {
                    LanguageManager.formatWeight(activeData.totalDebit, isGu)
                }

                val balanceFormatted = if (activeData.unit == "₹") {
                    "₹ " + LanguageManager.formatDouble(activeData.currentBalance, 2)
                } else {
                    LanguageManager.formatWeight(activeData.currentBalance, isGu)
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        // Header Row: Active Name (Left)  |  ✏ Edit Opening (Right)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = activeData.displayName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )

                            Row(
                                modifier = Modifier
                                    .clickable {
                                        openingStockCategory = activeData.categoryKey
                                        openingStockCategoryName = activeData.displayName
                                        openingStockUnit = activeData.unit
                                        showOpeningStockDialog = true
                                    }
                                    .testTag("edit_opening_btn"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = amberBtnBg,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = AppStrings.editOpening(),
                                    color = amberBtnBg,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 3 Metrics Columns: Opening Stock | Total Credit | Total Debit
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Column 1: Opening Stock
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = AppStrings.openingStock(),
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B),
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = openingFormatted,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A),
                                    textAlign = TextAlign.Center
                                )
                            }

                            // Column 2: Total Credit
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = AppStrings.totalCredit(),
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B),
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = creditFormatted,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = greenCreditColor,
                                    textAlign = TextAlign.Center
                                )
                            }

                            // Column 3: Total Debit
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = AppStrings.totalDebit(),
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B),
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = debitFormatted,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = redDebitColor,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Middle Banner: Deep Dark Blue Rounded Box for Current Balance
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = activeBalanceCardBg
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
                                        text = AppStrings.currentBalance(),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp
                                    )
                                    Text(
                                        text = AppStrings.openingPlusCreditMinusDebit(),
                                        color = Color(0xFF93C5FD),
                                        fontSize = 10.sp
                                    )
                                }

                                Text(
                                    text = balanceFormatted,
                                    color = goldTextYellow,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Action Buttons Row: [ + Credit ]  |  [ - Debit ]  |  [ 🕒 History ]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Green Button: + Credit
                            Button(
                                onClick = {
                                    isAddingCredit = true
                                    targetCategory = activeData.categoryKey
                                    targetCategoryName = activeData.displayName
                                    targetUnit = activeData.unit
                                    showAddDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = greenCreditColor),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .testTag("active_add_credit_btn")
                            ) {
                                Text(
                                    text = AppStrings.addCredit(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp
                                )
                            }

                            // Red Button: - Debit
                            Button(
                                onClick = {
                                    isAddingCredit = false
                                    targetCategory = activeData.categoryKey
                                    targetCategoryName = activeData.displayName
                                    targetUnit = activeData.unit
                                    showAddDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = redDebitColor),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .testTag("active_add_debit_btn")
                            ) {
                                Text(
                                    text = AppStrings.addDebit(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp
                                )
                            }

                            // Outlined Button: 🕒 History
                            OutlinedButton(
                                onClick = { showCategoryHistoryDialog = true },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .testTag("active_view_history_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = Color(0xFF475569),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = AppStrings.history(),
                                    color = Color(0xFF334155),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                    }
                }
            }

            // If in Cash tab, show optional Cash -> Metal purchase action
            if (selectedTab == DashboardTab.CASH) {
                item {
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                            .clickable { showCashMetalPurchaseDialog = true }
                            .testTag("cash_buy_metal_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = GoldLight,
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.ShoppingBag,
                                            contentDescription = null,
                                            tint = GoldDark,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = AppStrings.cashToMetalPurchase(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp
                                    )
                                    Text(
                                        text = loc(
                                            en = "Deducts cash and adds to gold/silver stock",
                                            gu = "રોકડ ઘટશે અને સોનું/ચાંદી સ્ટોક વધશે"
                                        ),
                                        fontSize = 10.5.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = GoldDark)
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                // Stock Overview (All Metals & Cash) Section
                Text(
                    text = AppStrings.stockOverviewAll(),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            item {
                // White Overview Card with 5 rows matching screenshot
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Row 1: 🟡 Jewellery Gold
                        StockOverviewRow(
                            dotColor = Color(0xFFF59E0B),
                            title = AppStrings.goldJewellery(),
                            valueFormatted = LanguageManager.formatWeight(goldJewelleryNet, isGu),
                            onClick = {
                                selectedTab = DashboardTab.GOLD
                                goldSubIndex = 0
                            }
                        )
                        HorizontalDivider(color = Color(0xFFF1F5F9))

                        // Row 2: 🟡 GOLD
                        StockOverviewRow(
                            dotColor = Color(0xFFF59E0B),
                            title = AppStrings.goldMetal(),
                            valueFormatted = LanguageManager.formatWeight(goldMetalNet, isGu),
                            onClick = {
                                selectedTab = DashboardTab.GOLD
                                goldSubIndex = 1
                            }
                        )
                        HorizontalDivider(color = Color(0xFFF1F5F9))

                        // Row 3: ⚪ Jewellery Silver
                        StockOverviewRow(
                            dotColor = Color(0xFF94A3B8),
                            title = AppStrings.silverJewellery(),
                            valueFormatted = LanguageManager.formatWeight(silverJewelleryNet, isGu),
                            onClick = {
                                selectedTab = DashboardTab.SILVER
                                silverSubIndex = 0
                            }
                        )
                        HorizontalDivider(color = Color(0xFFF1F5F9))

                        // Row 4: ⚪ SILVER
                        StockOverviewRow(
                            dotColor = Color(0xFF94A3B8),
                            title = AppStrings.silverMetal(),
                            valueFormatted = LanguageManager.formatWeight(silverMetalNet, isGu),
                            onClick = {
                                selectedTab = DashboardTab.SILVER
                                silverSubIndex = 1
                            }
                        )
                        HorizontalDivider(color = Color(0xFFF1F5F9))

                        // Row 5: 🟢 CASH
                        StockOverviewRow(
                            dotColor = Color(0xFF16A34A),
                            title = AppStrings.cash(),
                            valueFormatted = LanguageManager.formatCurrency(cashNet),
                            onClick = {
                                selectedTab = DashboardTab.CASH
                                cashSubIndex = 0
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
fun SubcategoryPill(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) Color(0xFF0A2540) else Color.Transparent,
        modifier = Modifier
            .clickable { onClick() }
            .testTag(tag)
    ) {
        Text(
            text = title,
            color = if (isSelected) Color.White else Color(0xFF334155),
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun StockOverviewRow(
    dotColor: Color,
    title: String,
    valueFormatted: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1E293B)
            )
        }

        Text(
            text = valueFormatted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A)
        )
    }
}

@Composable
fun CashToMetalPurchaseDialog(
    isGu: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (category: String, grams: Double, cashAmount: Double, remark: String) -> Unit
) {
    var metalType by remember { mutableStateOf("GOLD_METAL") }
    var gramsText by remember { mutableStateOf("") }
    var cashAmountText by remember { mutableStateOf("") }
    var remarkText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    val wtUnit = if (isGu) "ગ્રામ" else "g"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = AppStrings.cashToMetalPurchase(),
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = loc(
                        en = "This entry deducts from Cash and credits Gold/Silver metal stock directly.",
                        gu = "આ એન્ટ્રીથી રોકડ શિલકમાંથી રકમ બાદ થશે અને સોનું/ચાંદી સ્ટોકમાં વજન જમા થશે."
                    ),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = metalType == "GOLD_METAL",
                        onClick = { metalType = "GOLD_METAL" },
                        label = { Text(AppStrings.gold()) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = metalType == "SILVER_METAL",
                        onClick = { metalType = "SILVER_METAL" },
                        label = { Text(AppStrings.silver()) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = gramsText,
                    onValueChange = { gramsText = it },
                    label = { Text("${loc(en = "Purchased Weight", gu = "ખરીદેલ વજન")} ($wtUnit) *") },
                    modifier = Modifier.fillMaxWidth().testTag("cash_purchase_grams"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = cashAmountText,
                    onValueChange = { cashAmountText = it },
                    label = { Text("${loc(en = "Cash Paid", gu = "ચૂકવેલ રોકડ")} (₹) *") },
                    modifier = Modifier.fillMaxWidth().testTag("cash_purchase_amount"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = remarkText,
                    onValueChange = { remarkText = it },
                    label = { Text(AppStrings.remarkNote()) },
                    modifier = Modifier.fillMaxWidth().testTag("cash_purchase_remark")
                )

                if (errorText != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = errorText!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val g = gramsText.toDoubleOrNull()
                    val c = cashAmountText.toDoubleOrNull()
                    if (g == null || g <= 0) {
                        errorText = loc(en = "Please enter valid weight.", gu = "માન્ય વજન દાખલ કરો.")
                        return@Button
                    }
                    if (c == null || c <= 0) {
                        errorText = loc(en = "Please enter valid cash amount.", gu = "માન્ય રોકડ રકમ દાખલ કરો.")
                        return@Button
                    }
                    onConfirm(metalType, g, c, remarkText.trim())
                },
                colors = ButtonDefaults.buttonColors(containerColor = GoldDark),
                modifier = Modifier.testTag("cash_purchase_submit_btn")
            ) {
                Text(AppStrings.save(), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(AppStrings.cancel()) }
        }
    )
}
