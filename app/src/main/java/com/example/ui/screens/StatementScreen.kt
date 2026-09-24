package com.example.ui.screens

import android.bluetooth.BluetoothDevice
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.components.BluetoothPrinterDialog
import com.example.ui.components.JewelleryTopBar
import com.example.ui.locale.AppStrings
import com.example.ui.locale.LanguageManager
import com.example.ui.locale.loc
import com.example.ui.theme.GoldDark
import com.example.ui.theme.GoldLight
import com.example.ui.viewmodel.JewelleryViewModel
import com.example.util.BluetoothPrinterHelper
import com.example.util.StatementCalculator
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class StatementTab(val index: Int) {
    GOLD(0),
    SILVER(1),
    CASH(2),
    MONTHLY(3),
    YEARLY(4)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatementScreen(
    viewModel: JewelleryViewModel,
    initialTab: StatementTab = StatementTab.GOLD,
    initialYear: Int = Calendar.getInstance(StatementCalculator.kolkataTimeZone).get(Calendar.YEAR),
    initialMonth: Int = Calendar.getInstance(StatementCalculator.kolkataTimeZone).get(Calendar.MONTH) + 1,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(initialTab) }
    var selectedYear by remember { mutableStateOf(initialYear) }
    var selectedMonth by remember { mutableStateOf(initialMonth) }

    val bills by viewModel.allBills.collectAsState()
    val transactions by viewModel.allStockTransactions.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()

    val isGu = LanguageManager.isGujarati()

    // Calculated Statement Data (Recalculates automatically whenever bills, transactions or settings change)
    val goldStatement = remember(bills, transactions, settings) {
        StatementCalculator.calculateGoldStatement(bills, transactions, settings)
    }

    val silverStatement = remember(bills, transactions, settings) {
        StatementCalculator.calculateSilverStatement(bills, transactions, settings)
    }

    val cashStatement = remember(bills, transactions) {
        StatementCalculator.calculateCashStatement(bills, transactions)
    }

    val monthlyStatement = remember(bills, transactions, settings, selectedYear, selectedMonth) {
        StatementCalculator.calculateMonthlyStatement(bills, transactions, settings, selectedYear, selectedMonth)
    }

    val yearlyStatement = remember(bills, transactions, settings, selectedYear) {
        StatementCalculator.calculateYearlyStatement(bills, transactions, settings, selectedYear)
    }

    // Bluetooth Printer State
    var showPrinterDialog by remember { mutableStateOf(false) }
    var pendingPrintAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var isPrinting by remember { mutableStateOf(false) }
    var savedPrinterName by remember {
        mutableStateOf(BluetoothPrinterHelper.getSavedPrinterName(context))
    }

    fun triggerPrint(printBlock: (BluetoothDevice) -> Unit) {
        val savedPrinter = BluetoothPrinterHelper.getPreferredPrinter(context)
        if (savedPrinter != null) {
            isPrinting = true
            coroutineScope.launch {
                printBlock(savedPrinter.device)
                isPrinting = false
            }
        } else {
            pendingPrintAction = {
                val printer = BluetoothPrinterHelper.getPreferredPrinter(context)
                if (printer != null) {
                    isPrinting = true
                    coroutineScope.launch {
                        printBlock(printer.device)
                        isPrinting = false
                    }
                }
            }
            showPrinterDialog = true
        }
    }

    if (showPrinterDialog) {
        BluetoothPrinterDialog(
            onDismissRequest = {
                showPrinterDialog = false
                pendingPrintAction = null
            },
            onSelectPrinter = { device ->
                showPrinterDialog = false
                savedPrinterName = BluetoothPrinterHelper.getSavedPrinterName(context)
                Toast.makeText(context, loc(en = "Printer selected and saved", gu = "પ્રિન્ટર સાચવાયું"), Toast.LENGTH_SHORT).show()
                pendingPrintAction?.invoke()
                pendingPrintAction = null
            }
        )
    }

    Scaffold(
        topBar = {
            JewelleryTopBar(
                title = loc(en = "STATEMENTS", gu = "ખાતાવહી અને સ્ટેટમેન્ટ"),
                subtitle = loc(en = "Gold • Silver • Cash • Monthly • Yearly", gu = "સોનું • ચાંદી • રોકડ • માસિક • વાર્ષિક"),
                showBackButton = true,
                onBackClick = onNavigateBack,
                logoBase64 = settings?.logoBase64,
                syncStatus = syncStatus
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Printer status bar & quick print bar
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { showPrinterDialog = true }
                            .padding(vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = null,
                            tint = GoldDark,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (savedPrinterName != null) {
                                "${loc(en = "Printer", gu = "પ્રિન્ટર")}: $savedPrinterName"
                            } else {
                                loc(en = "Select BT Printer", gu = "પ્રિન્ટર પસંદ કરો")
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "[${loc(en = "Change", gu = "બદલો")}]",
                            fontSize = 11.sp,
                            color = GoldDark,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = {
                            when (selectedTab) {
                                StatementTab.GOLD -> {
                                    triggerPrint { device ->
                                        coroutineScope.launch {
                                            val res = BluetoothPrinterHelper.printGoldStatement(device, goldStatement, settings)
                                            if (res.isSuccess) {
                                                Toast.makeText(context, loc(en = "Gold statement printed!", gu = "સોનું સ્ટેટમેન્ટ પ્રિન્ટ થયું!"), Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, loc(en = "Print failed. Check Bluetooth.", gu = "પ્રિન્ટ નિષ્ફળ. બ્લૂટૂથ તપાસો."), Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                                StatementTab.SILVER -> {
                                    triggerPrint { device ->
                                        coroutineScope.launch {
                                            val res = BluetoothPrinterHelper.printSilverStatement(device, silverStatement, settings)
                                            if (res.isSuccess) {
                                                Toast.makeText(context, loc(en = "Silver statement printed!", gu = "ચાંદી સ્ટેટમેન્ટ પ્રિન્ટ થયું!"), Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, loc(en = "Print failed. Check Bluetooth.", gu = "પ્રિન્ટ નિષ્ફળ. બ્લૂટૂથ તપાસો."), Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                                StatementTab.CASH -> {
                                    triggerPrint { device ->
                                        coroutineScope.launch {
                                            val res = BluetoothPrinterHelper.printCashStatement(device, cashStatement, settings)
                                            if (res.isSuccess) {
                                                Toast.makeText(context, loc(en = "Cash statement printed!", gu = "રોકડ સ્ટેટમેન્ટ પ્રિન્ટ થયું!"), Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, loc(en = "Print failed. Check Bluetooth.", gu = "પ્રિન્ટ નિષ્ફળ. બ્લૂટૂથ તપાસો."), Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                                StatementTab.MONTHLY -> {
                                    triggerPrint { device ->
                                        coroutineScope.launch {
                                            val res = BluetoothPrinterHelper.printMonthlyStatement(device, monthlyStatement, settings)
                                            if (res.isSuccess) {
                                                Toast.makeText(context, loc(en = "Monthly statement printed!", gu = "માસિક સ્ટેટમેન્ટ પ્રિન્ટ થયું!"), Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, loc(en = "Print failed. Check Bluetooth.", gu = "પ્રિન્ટ નિષ્ફળ. બ્લૂટૂથ તપાસો."), Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                                StatementTab.YEARLY -> {
                                    triggerPrint { device ->
                                        coroutineScope.launch {
                                            val res = BluetoothPrinterHelper.printYearlyStatement(device, yearlyStatement, settings)
                                            if (res.isSuccess) {
                                                Toast.makeText(context, loc(en = "Yearly statement printed!", gu = "વાર્ષિક સ્ટેટમેન્ટ પ્રિન્ટ થયું!"), Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, loc(en = "Print failed. Check Bluetooth.", gu = "પ્રિન્ટ નિષ્ફળ. બ્લૂટૂથ તપાસો."), Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldDark),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp).testTag("print_statement_btn")
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(loc(en = "Print", gu = "પ્રિન્ટ"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Scrollable Tab Row for the 5 Statements
            ScrollableTabRow(
                selectedTabIndex = selectedTab.index,
                edgePadding = 12.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = GoldDark,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab.index]),
                        color = GoldDark,
                        height = 3.dp
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == StatementTab.GOLD,
                    onClick = { selectedTab = StatementTab.GOLD },
                    text = { Text(loc(en = "Gold", gu = "સોનું"), fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == StatementTab.SILVER,
                    onClick = { selectedTab = StatementTab.SILVER },
                    text = { Text(loc(en = "Silver", gu = "ચાંદી"), fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == StatementTab.CASH,
                    onClick = { selectedTab = StatementTab.CASH },
                    text = { Text(loc(en = "Cash", gu = "રોકડ"), fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == StatementTab.MONTHLY,
                    onClick = { selectedTab = StatementTab.MONTHLY },
                    text = { Text(loc(en = "Monthly", gu = "માસિક"), fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == StatementTab.YEARLY,
                    onClick = { selectedTab = StatementTab.YEARLY },
                    text = { Text(loc(en = "Yearly", gu = "વાર્ષિક"), fontWeight = FontWeight.Bold) }
                )
            }

            // Body Content based on selected Tab
            when (selectedTab) {
                StatementTab.GOLD -> GoldStatementView(goldStatement, isGu)
                StatementTab.SILVER -> SilverStatementView(silverStatement, isGu)
                StatementTab.CASH -> CashStatementView(cashStatement, isGu)
                StatementTab.MONTHLY -> MonthlyStatementView(
                    data = monthlyStatement,
                    year = selectedYear,
                    month = selectedMonth,
                    isGu = isGu,
                    onPrevMonth = {
                        if (selectedMonth == 1) {
                            selectedMonth = 12
                            selectedYear -= 1
                        } else {
                            selectedMonth -= 1
                        }
                    },
                    onNextMonth = {
                        if (selectedMonth == 12) {
                            selectedMonth = 1
                            selectedYear += 1
                        } else {
                            selectedMonth += 1
                        }
                    }
                )
                StatementTab.YEARLY -> YearlyStatementView(
                    data = yearlyStatement,
                    year = selectedYear,
                    isGu = isGu,
                    onPrevYear = { selectedYear -= 1 },
                    onNextYear = { selectedYear += 1 },
                    onSelectMonth = { month ->
                        selectedMonth = month
                        selectedTab = StatementTab.MONTHLY
                    }
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// 1. GOLD STATEMENT VIEW
// -----------------------------------------------------------------------------------------
@Composable
private fun GoldStatementView(data: GoldStatementData, isGu: Boolean) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = loc(en = "Gold Summary (Jewellery + Metal Combined)", gu = "સોનું સમરી (ઘરેણાં + ધાતુ સંયુક્ત)"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = GoldDark
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loc(en = "Opening Balance:", gu = "શરૂઆતનો સ્ટોક:"), fontSize = 13.sp)
                        Text(LanguageManager.formatWeight(data.openingGrams, isGu), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loc(en = "Total Credit (+):", gu = "કુલ જમા (+):"), fontSize = 13.sp)
                        Text(LanguageManager.formatWeight(data.totalCreditGrams, isGu), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF15803D))
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loc(en = "Total Debit (-):", gu = "કુલ ઉધાર (-):"), fontSize = 13.sp)
                        Text(LanguageManager.formatWeight(data.totalDebitGrams, isGu), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFB91C1C))
                    }
                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loc(en = "Current Balance:", gu = "હાલનો સ્ટોક:"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            text = LanguageManager.formatWeight(data.currentBalanceGrams, isGu),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = GoldDark
                        )
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = loc(en = "Purchase & Sale Performance", gu = "ખરીદી અને વેચાણ પર્ફોમન્સ"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = GoldDark
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loc(en = "Purchased Gross Wt:", gu = "કુલ ખરીદ વજન:"), fontSize = 13.sp)
                        Text(LanguageManager.formatWeight(data.totalPurchaseGrams, isGu), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loc(en = "Purchased 100% Fine:", gu = "૧૦૦% ટચ ફાઈન ખરીદી:"), fontSize = 13.sp)
                        Text(LanguageManager.formatWeight(data.finePurchasedGrams, isGu), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = GoldDark)
                    }
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loc(en = "Total Purchase Cost:", gu = "કુલ ખરીદી રકમ:"), fontSize = 13.sp)
                        Text(LanguageManager.formatCurrency(data.totalPurchaseAmount), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loc(en = "Average Buy Price:", gu = "સરેરાશ ખરીદ ભાવ:"), fontSize = 13.sp)
                        Text("₹${LanguageManager.formatDouble(data.averageBuyPrice, 2)}/g", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loc(en = "Sold Gross Wt:", gu = "કુલ વેચાણ વજન:"), fontSize = 13.sp)
                        Text(LanguageManager.formatWeight(data.totalSaleGrams, isGu), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loc(en = "Sold 100% Fine:", gu = "૧૦૦% ટચ ફાઈન વેચાણ:"), fontSize = 13.sp)
                        Text(LanguageManager.formatWeight(data.fineSoldGrams, isGu), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = GoldDark)
                    }
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loc(en = "Total Sale Revenue:", gu = "કુલ વેચાણ આવક:"), fontSize = 13.sp)
                        Text(LanguageManager.formatCurrency(data.totalSaleAmount), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loc(en = "Average Sell Price:", gu = "સરેરાશ વેચાણ ભાવ:"), fontSize = 13.sp)
                        Text("₹${LanguageManager.formatDouble(data.averageSellPrice, 2)}/g", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loc(en = "Gross Profit / Loss:", gu = "નફો / નુકસાન:"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        val pColor = if (data.profitLoss >= 0) Color(0xFF15803D) else Color(0xFFB91C1C)
                        Text(
                            text = LanguageManager.formatCurrency(data.profitLoss),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = pColor
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = loc(en = "Stock Movements & Entries:", gu = "સ્ટોક આવક-જાવક હિસ્ટ્રી:"),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        if (data.transactions.isEmpty()) {
            item {
                Text(loc(en = "No gold transactions found.", gu = "સોનાના કોઈ વ્યવહાર મળ્યા નથી."), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            items(data.transactions) { tx ->
                StockTransactionCard(tx, isGu)
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// 2. SILVER STATEMENT VIEW
// -----------------------------------------------------------------------------------------
@Composable
private fun SilverStatementView(data: SilverStatementData, isGu: Boolean) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = loc(en = "Silver Summary (Jewellery + Metal Combined)", gu = "ચાંદી સમરી (ઘરેણાં + ધાતુ સંયુક્ત)"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = GoldDark
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loc(en = "Opening Balance:", gu = "શરૂઆતનો સ્ટોક:"), fontSize = 13.sp)
                        Text(LanguageManager.formatWeight(data.openingGrams, isGu), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loc(en = "Total Credit (+):", gu = "કુલ જમા (+):"), fontSize = 13.sp)
                        Text(LanguageManager.formatWeight(data.totalCreditGrams, isGu), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF15803D))
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loc(en = "Total Debit (-):", gu = "કુલ ઉધાર (-):"), fontSize = 13.sp)
                        Text(LanguageManager.formatWeight(data.totalDebitGrams, isGu), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFB91C1C))
                    }
                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loc(en = "Current Balance:", gu = "હાલનો સ્ટોક:"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            text = LanguageManager.formatWeight(data.currentBalanceGrams, isGu),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = GoldDark
                        )
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = loc(en = "Purchase & Sale Performance", gu = "ખરીદી અને વેચાણ પર્ફોમન્સ"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = GoldDark
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loc(en = "Purchased Gross Wt:", gu = "કુલ ખરીદ વજન:"), fontSize = 13.sp)
                        Text(LanguageManager.formatWeight(data.totalPurchaseGrams, isGu), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loc(en = "Purchased 100% Fine:", gu = "૧૦૦% ટચ ફાઈન ખરીદી:"), fontSize = 13.sp)
                        Text(LanguageManager.formatWeight(data.finePurchasedGrams, isGu), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = GoldDark)
                    }
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loc(en = "Total Purchase Cost:", gu = "કુલ ખરીદી રકમ:"), fontSize = 13.sp)
                        Text(LanguageManager.formatCurrency(data.totalPurchaseAmount), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loc(en = "Average Buy Price:", gu = "સરેરાશ ખરીદ ભાવ:"), fontSize = 13.sp)
                        Text("₹${LanguageManager.formatDouble(data.averageBuyPrice, 2)}/g", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loc(en = "Sold Gross Wt:", gu = "કુલ વેચાણ વજન:"), fontSize = 13.sp)
                        Text(LanguageManager.formatWeight(data.totalSaleGrams, isGu), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loc(en = "Sold 100% Fine:", gu = "૧૦૦% ટચ ફાઈન વેચાણ:"), fontSize = 13.sp)
                        Text(LanguageManager.formatWeight(data.fineSoldGrams, isGu), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = GoldDark)
                    }
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loc(en = "Total Sale Revenue:", gu = "કુલ વેચાણ આવક:"), fontSize = 13.sp)
                        Text(LanguageManager.formatCurrency(data.totalSaleAmount), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loc(en = "Average Sell Price:", gu = "સરેરાશ વેચાણ ભાવ:"), fontSize = 13.sp)
                        Text("₹${LanguageManager.formatDouble(data.averageSellPrice, 2)}/g", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loc(en = "Gross Profit / Loss:", gu = "નફો / નુકસાન:"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        val pColor = if (data.profitLoss >= 0) Color(0xFF15803D) else Color(0xFFB91C1C)
                        Text(
                            text = LanguageManager.formatCurrency(data.profitLoss),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = pColor
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = loc(en = "Stock Movements & Entries:", gu = "સ્ટોક આવક-જાવક હિસ્ટ્રી:"),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        if (data.transactions.isEmpty()) {
            item {
                Text(loc(en = "No silver transactions found.", gu = "ચાંદીના કોઈ વ્યવહાર મળ્યા નથી."), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            items(data.transactions) { tx ->
                StockTransactionCard(tx, isGu)
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// 3. CASH STATEMENT VIEW
// -----------------------------------------------------------------------------------------
@Composable
private fun CashStatementView(data: CashStatementData, isGu: Boolean) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = loc(en = "Cash Flow Overview", gu = "રોકડ ઝાંખી"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = GoldDark
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loc(en = "Opening Cash:", gu = "શરૂઆતની રોકડ:"), fontSize = 13.sp)
                        Text(LanguageManager.formatCurrency(data.openingCash), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loc(en = "Cash Credit (+ Receipts):", gu = "કુલ જમા (+ આવક):"), fontSize = 13.sp)
                        Text(LanguageManager.formatCurrency(data.cashCredit), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF15803D))
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loc(en = "Cash Debit (- Outflow):", gu = "કુલ ઉધાર (- ચૂકવણી):"), fontSize = 13.sp)
                        Text(LanguageManager.formatCurrency(data.cashDebit), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFB91C1C))
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loc(en = "Current Cash Balance:", gu = "હાલની રોકડ શિલક:"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            text = LanguageManager.formatCurrency(data.currentCash),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = GoldDark
                        )
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = loc(en = "Cash Outflow Breakdown", gu = "રોકડ વપરાશ વિગતો"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = GoldDark
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loc(en = "Cash for Gold Purchase:", gu = "સોનું ખરીદીમાં વપરાયેલ:"), fontSize = 13.sp)
                        Text(LanguageManager.formatCurrency(data.cashForGoldPurchase), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loc(en = "Cash for Silver Purchase:", gu = "ચાંદી ખરીદીમાં વપરાયેલ:"), fontSize = 13.sp)
                        Text(LanguageManager.formatCurrency(data.cashForSilverPurchase), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loc(en = "Total Metal/Jewellery Purchase:", gu = "કુલ ધાતુ ખરીદી ખર્ચ:"), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(LanguageManager.formatCurrency(data.cashForJewelleryMetalPurchase), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = GoldDark)
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loc(en = "Cash from Gold Sale:", gu = "સોનું વેચાણથી મળેલ રોકડ:"), fontSize = 13.sp)
                        Text(LanguageManager.formatCurrency(data.cashFromGoldSale), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF15803D))
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loc(en = "Cash from Silver Sale:", gu = "ચાંદી વેચાણથી મળેલ રોકડ:"), fontSize = 13.sp)
                        Text(LanguageManager.formatCurrency(data.cashFromSilverSale), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF15803D))
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loc(en = "Own Self Withdrawal (Personal):", gu = "અંગત ઉપાડ:"), fontSize = 13.sp)
                        Text(LanguageManager.formatCurrency(data.ownSelfWithdrawal), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFFD97706))
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loc(en = "Other Shop Expenses:", gu = "દુકાન અન્ય ખર્ચ:"), fontSize = 13.sp)
                        Text(LanguageManager.formatCurrency(data.otherExpenses), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loc(en = "Remaining Cash Balance:", gu = "બાકી રોકડ શિલક:"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            text = LanguageManager.formatCurrency(data.remainingCashBalance),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = GoldDark
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loc(en = "Overall Profit / Loss:", gu = "કુલ નફો / નુકસાન:"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        val oplColor = if (data.overallProfitLoss >= 0) Color(0xFF15803D) else Color(0xFFB91C1C)
                        Text(
                            text = LanguageManager.formatCurrency(data.overallProfitLoss),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = oplColor
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = loc(en = "Cash Entries & Movements:", gu = "રોકડ વ્યવહાર હિસ્ટ્રી:"),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        if (data.transactions.isEmpty()) {
            item {
                Text(loc(en = "No cash transactions recorded.", gu = "કોઈ રોકડ વ્યવહાર મળ્યા નથી."), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            items(data.transactions) { tx ->
                StockTransactionCard(tx, isGu)
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// 4. MONTHLY STATEMENT VIEW
// -----------------------------------------------------------------------------------------
@Composable
private fun MonthlyStatementView(
    data: MonthlyStatementData,
    year: Int,
    month: Int,
    isGu: Boolean,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Month Selector Bar (Previous Month / Next Month)
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onPrevMonth) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Month", tint = GoldDark)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = data.monthLabel,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = GoldDark
                        )
                        Text(
                            text = loc(en = "Tap arrows to browse any past month", gu = "ગત મહિનાઓ જોવા એરો ટેપ કરો"),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = onNextMonth) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Month", tint = GoldDark)
                    }
                }
            }
        }

        // Monthly Profit / Income Card
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = loc(en = "Monthly Income & Profit", gu = "માસિક આવક અને ચોખ્ખો નફો"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = GoldDark
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loc(en = "Total Sales Revenue:", gu = "કુલ વેચાણ આવક:"), fontSize = 13.sp)
                        Text(LanguageManager.formatCurrency(data.totalSales), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF15803D))
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loc(en = "Total Purchases Cost:", gu = "કુલ ખરીદી ખર્ચ:"), fontSize = 13.sp)
                        Text(LanguageManager.formatCurrency(data.totalPurchases), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loc(en = "Other Shop Expenses:", gu = "દુકાન અન્ય ખર્ચ:"), fontSize = 13.sp)
                        Text(LanguageManager.formatCurrency(data.totalExpenses), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loc(en = "Personal Drawings (Withdrawal):", gu = "અંગત ઉપાડ:"), fontSize = 13.sp)
                        Text(LanguageManager.formatCurrency(data.ownSelfWithdrawals), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFFD97706))
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loc(en = "Monthly Net Profit:", gu = "માસિક ચોખ્ખો નફો:"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        val pColor = if (data.monthlyIncomeProfit >= 0) Color(0xFF15803D) else Color(0xFFB91C1C)
                        Text(
                            text = LanguageManager.formatCurrency(data.monthlyIncomeProfit),
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = pColor
                        )
                    }
                }
            }
        }

        // Monthly Summary of Gold, Silver and Cash (Opening, Credit, Debit, Net Balance, Profit/Loss)
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = loc(en = "Monthly Summary of Gold, Silver & Cash", gu = "સોનું, ચાંદી અને રોકડનો માસિક સારાંશ"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = GoldDark
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Gold section
                    Text(loc(en = "Gold Summary:", gu = "સોનું સારાંશ:"), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = GoldDark)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("  ${loc(en = "Opening Balance", gu = "શરૂઆતનો સ્ટોક")}:", fontSize = 12.sp)
                        Text(LanguageManager.formatWeight(data.goldOpeningGrams, isGu), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("  ${loc(en = "Total Credit (+)", gu = "કુલ જમા (+)")}:", fontSize = 12.sp)
                        Text(LanguageManager.formatWeight(data.goldCreditGrams, isGu), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF15803D))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("  ${loc(en = "Total Debit (-)", gu = "કુલ ઉધાર (-)")}:", fontSize = 12.sp)
                        Text(LanguageManager.formatWeight(data.goldDebitGrams, isGu), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFB91C1C))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("  ${loc(en = "Net Balance", gu = "ચોખ્ખો સ્ટોક")}:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(LanguageManager.formatWeight(data.goldNetBalanceGrams, isGu), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GoldDark)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("  ${loc(en = "Gold Profit / Loss", gu = "સોનું નફો / નુકસાન")}:", fontSize = 12.sp)
                        val gpColor = if (data.goldProfitLoss >= 0) Color(0xFF15803D) else Color(0xFFB91C1C)
                        Text(LanguageManager.formatCurrency(data.goldProfitLoss), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = gpColor)
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    // Silver section
                    Text(loc(en = "Silver Summary:", gu = "ચાંદી સારાંશ:"), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = GoldDark)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("  ${loc(en = "Opening Balance", gu = "શરૂઆતનો સ્ટોક")}:", fontSize = 12.sp)
                        Text(LanguageManager.formatWeight(data.silverOpeningGrams, isGu), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("  ${loc(en = "Total Credit (+)", gu = "કુલ જમા (+)")}:", fontSize = 12.sp)
                        Text(LanguageManager.formatWeight(data.silverCreditGrams, isGu), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF15803D))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("  ${loc(en = "Total Debit (-)", gu = "કુલ ઉધાર (-)")}:", fontSize = 12.sp)
                        Text(LanguageManager.formatWeight(data.silverDebitGrams, isGu), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFB91C1C))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("  ${loc(en = "Net Balance", gu = "ચોખ્ખો સ્ટોક")}:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(LanguageManager.formatWeight(data.silverNetBalanceGrams, isGu), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GoldDark)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("  ${loc(en = "Silver Profit / Loss", gu = "ચાંદી નફો / નુકસાન")}:", fontSize = 12.sp)
                        val spColor = if (data.silverProfitLoss >= 0) Color(0xFF15803D) else Color(0xFFB91C1C)
                        Text(LanguageManager.formatCurrency(data.silverProfitLoss), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = spColor)
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    // Cash section
                    Text(loc(en = "Cash Summary:", gu = "રોકડ સારાંશ:"), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = GoldDark)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("  ${loc(en = "Opening Cash", gu = "શરૂઆતની રોકડ")}:", fontSize = 12.sp)
                        Text(LanguageManager.formatCurrency(data.cashOpening), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("  ${loc(en = "Total Cash Credit (+)", gu = "કુલ જમા આવક (+)")}:", fontSize = 12.sp)
                        Text(LanguageManager.formatCurrency(data.cashCredit), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF15803D))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("  ${loc(en = "Total Cash Debit (-)", gu = "કુલ ઉધાર ચૂકવણી (-)")}:", fontSize = 12.sp)
                        Text(LanguageManager.formatCurrency(data.cashDebit), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFB91C1C))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("  ${loc(en = "Net Cash Balance", gu = "ચોખ્ખી રોકડ શિલક")}:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(LanguageManager.formatCurrency(data.cashNetBalance), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GoldDark)
                    }
                }
            }
        }

        // Gold & Silver Metal Activity Cards
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = loc(en = "Metal Activity (${data.monthLabel})", gu = "ધાતુ ટર્નઓવર (${data.monthLabel})"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = GoldDark
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(loc(en = "Gold Turnover:", gu = "સોનું વિગત:"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("  ${loc(en = "Purchased (Fine)", gu = "ખરીદી (ફાઈન)")}:", fontSize = 12.sp)
                        Text("${LanguageManager.formatWeight(data.goldPurchasedGrams, isGu)} (${LanguageManager.formatWeight(data.goldPurchasedFineGrams, isGu)})", fontSize = 12.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("  ${loc(en = "Sold (Fine)", gu = "વેચાણ (ફાઈન)")}:", fontSize = 12.sp)
                        Text("${LanguageManager.formatWeight(data.goldSoldGrams, isGu)} (${LanguageManager.formatWeight(data.goldSoldFineGrams, isGu)})", fontSize = 12.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("  ${loc(en = "Avg Buy / Sell Rate", gu = "સરેરાશ ખરીદ / વેચાણ ભાવ")}:", fontSize = 12.sp)
                        Text("₹${LanguageManager.formatDouble(data.avgGoldBuyRate, 0)} / ₹${LanguageManager.formatDouble(data.avgGoldSellRate, 0)}", fontSize = 12.sp)
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    Text(loc(en = "Silver Turnover:", gu = "ચાંદી વિગત:"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("  ${loc(en = "Purchased (Fine)", gu = "ખરીદી (ફાઈન)")}:", fontSize = 12.sp)
                        Text("${LanguageManager.formatWeight(data.silverPurchasedGrams, isGu)} (${LanguageManager.formatWeight(data.silverPurchasedFineGrams, isGu)})", fontSize = 12.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("  ${loc(en = "Sold (Fine)", gu = "વેચાણ (ફાઈન)")}:", fontSize = 12.sp)
                        Text("${LanguageManager.formatWeight(data.silverSoldGrams, isGu)} (${LanguageManager.formatWeight(data.silverSoldFineGrams, isGu)})", fontSize = 12.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("  ${loc(en = "Avg Buy / Sell Rate", gu = "સરેરાશ ખરીદ / વેચાણ ભાવ")}:", fontSize = 12.sp)
                        Text("₹${LanguageManager.formatDouble(data.avgSilverBuyRate, 1)} / ₹${LanguageManager.formatDouble(data.avgSilverSellRate, 1)}", fontSize = 12.sp)
                    }
                }
            }
        }

        // Cash Flow Card
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = loc(en = "Cash Flow (${data.monthLabel})", gu = "રોકડ પ્રવાહ (${data.monthLabel})"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = GoldDark
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loc(en = "Cash Received:", gu = "રોકડ આવક:"), fontSize = 13.sp)
                        Text(LanguageManager.formatCurrency(data.cashReceived), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF15803D))
                    }
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loc(en = "Cash Paid:", gu = "રોકડ ચૂકવણી:"), fontSize = 13.sp)
                        Text(LanguageManager.formatCurrency(data.cashPaid), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFFB91C1C))
                    }
                    Divider(modifier = Modifier.padding(vertical = 6.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loc(en = "Net Cash Flow:", gu = "ચોખ્ખો રોકડ પ્રવાહ:"), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        val cfColor = if (data.netCashFlow >= 0) Color(0xFF15803D) else Color(0xFFB91C1C)
                        Text(LanguageManager.formatCurrency(data.netCashFlow), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = cfColor)
                    }
                }
            }
        }

        item {
            Text(
                text = "${loc(en = "Bills Created in", gu = "આ મહિનામાં બનાવેલ બિલ")} ${data.monthLabel} (${data.bills.size}):",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        if (data.bills.isEmpty()) {
            item {
                Text(loc(en = "No bills found in this month.", gu = "આ મહિનામાં કોઈ બિલ મળ્યા નથી."), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            items(data.bills) { bill ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${bill.billNumber} • ${if (bill.billType == "SALE") AppStrings.customerSale() else AppStrings.karigarPurchase()}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            Text(
                                text = bill.partyName.ifBlank { loc(en = "General Customer", gu = "સામાન્ય ગ્રાહક") },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.US).apply {
                                timeZone = StatementCalculator.kolkataTimeZone
                            }
                            Text(
                                text = sdf.format(Date(bill.dateTimestamp)),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = LanguageManager.formatCurrency(bill.grandTotal),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = GoldDark
                            )
                            val items = bill.parseItems()
                            val purities = items.map { it.purity }.filter { it.isNotBlank() }.distinct()
                            if (purities.isNotEmpty()) {
                                Text(
                                    text = purities.joinToString(", "),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// 5. YEARLY STATEMENT VIEW
// -----------------------------------------------------------------------------------------
@Composable
private fun YearlyStatementView(
    data: YearlyStatementData,
    year: Int,
    isGu: Boolean,
    onPrevYear: () -> Unit,
    onNextYear: () -> Unit,
    onSelectMonth: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Year Selector
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onPrevYear) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Year", tint = GoldDark)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${loc(en = "Financial Year", gu = "નાણાકીય વર્ષ")} ${data.year}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = GoldDark
                        )
                        Text(
                            text = loc(en = "Showing monthly totals only", gu = "માત્ર માસિક સરવાળા દર્શાવે છે"),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = onNextYear) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Year", tint = GoldDark)
                    }
                }
            }
        }

        // Yearly Highlights
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = loc(en = "Annual Financial Highlights", gu = "વાર્ષિક નાણાકીય સારાંશ"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = GoldDark
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loc(en = "Total Annual Sales:", gu = "વાર્ષિક કુલ વેચાણ:"), fontSize = 13.sp)
                        Text(LanguageManager.formatCurrency(data.yearlyTotalSales), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF15803D))
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loc(en = "Total Annual Purchases:", gu = "વાર્ષિક કુલ ખરીદી:"), fontSize = 13.sp)
                        Text(LanguageManager.formatCurrency(data.yearlyTotalPurchases), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loc(en = "Closing Cash Balance:", gu = "આખર રોકડ શિલક:"), fontSize = 13.sp)
                        Text(LanguageManager.formatCurrency(data.closingCashBalance), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loc(en = "Yearly Total Profit:", gu = "વાર્ષિક ચોખ્ખો નફો:"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        val pColor = if (data.yearlyTotalProfit >= 0) Color(0xFF15803D) else Color(0xFFB91C1C)
                        Text(
                            text = LanguageManager.formatCurrency(data.yearlyTotalProfit),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = pColor
                        )
                    }
                }
            }
        }

        // Yearly Summary of Gold, Silver and Cash
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = loc(en = "Yearly Summary of Gold, Silver & Cash", gu = "સોનું, ચાંદી અને રોકડનો વાર્ષિક સારાંશ"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = GoldDark
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Gold
                    Text(loc(en = "Gold Annual Balance:", gu = "સોનું વાર્ષિક હિસાબ:"), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = GoldDark)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("  ${loc(en = "Opening Balance", gu = "શરૂઆતનો સ્ટોક")}:", fontSize = 12.sp)
                        Text(LanguageManager.formatWeight(data.goldOpeningGrams, isGu), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("  ${loc(en = "Total Credit (+)", gu = "કુલ જમા (+)")}:", fontSize = 12.sp)
                        Text(LanguageManager.formatWeight(data.goldCreditGrams, isGu), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF15803D))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("  ${loc(en = "Total Debit (-)", gu = "કુલ ઉધાર (-)")}:", fontSize = 12.sp)
                        Text(LanguageManager.formatWeight(data.goldDebitGrams, isGu), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFB91C1C))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("  ${loc(en = "Net Balance", gu = "ચોખ્ખો સ્ટોક")}:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(LanguageManager.formatWeight(data.goldNetBalanceGrams, isGu), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GoldDark)
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    // Silver
                    Text(loc(en = "Silver Annual Balance:", gu = "ચાંદી વાર્ષિક હિસાબ:"), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = GoldDark)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("  ${loc(en = "Opening Balance", gu = "શરૂઆતનો સ્ટોક")}:", fontSize = 12.sp)
                        Text(LanguageManager.formatWeight(data.silverOpeningGrams, isGu), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("  ${loc(en = "Total Credit (+)", gu = "કુલ જમા (+)")}:", fontSize = 12.sp)
                        Text(LanguageManager.formatWeight(data.silverCreditGrams, isGu), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF15803D))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("  ${loc(en = "Total Debit (-)", gu = "કુલ ઉધાર (-)")}:", fontSize = 12.sp)
                        Text(LanguageManager.formatWeight(data.silverDebitGrams, isGu), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFB91C1C))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("  ${loc(en = "Net Balance", gu = "ચોખ્ખો સ્ટોક")}:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(LanguageManager.formatWeight(data.silverNetBalanceGrams, isGu), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GoldDark)
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    // Cash
                    Text(loc(en = "Cash Annual Balance:", gu = "રોકડ વાર્ષિક હિસાબ:"), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = GoldDark)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("  ${loc(en = "Opening Cash", gu = "શરૂઆતની રોકડ")}:", fontSize = 12.sp)
                        Text(LanguageManager.formatCurrency(data.cashOpening), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("  ${loc(en = "Total Cash Credit (+)", gu = "કુલ જમા આવક (+)")}:", fontSize = 12.sp)
                        Text(LanguageManager.formatCurrency(data.cashCredit), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF15803D))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("  ${loc(en = "Total Cash Debit (-)", gu = "કુલ ઉધાર ચૂકવણી (-)")}:", fontSize = 12.sp)
                        Text(LanguageManager.formatCurrency(data.cashDebit), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFB91C1C))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("  ${loc(en = "Net Cash Balance", gu = "ચોખ્ખી રોકડ શિલક")}:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(LanguageManager.formatCurrency(data.cashNetBalance), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GoldDark)
                    }
                }
            }
        }

        item {
            Text(
                text = loc(en = "Monthly Breakdown (Tap any month to view details):", gu = "માસિક વિગતો (વિગતવાર જોવા ટેપ કરો):"),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        // Monthly Summary rows
        items(data.monthlySummaries) { row ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectMonth(row.month) },
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = row.monthName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = GoldDark
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = GoldLight.copy(alpha = 0.3f)
                        ) {
                            Text(
                                text = loc(en = "View Month Details →", gu = "વિગતો જુઓ →"),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = GoldDark,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = "${loc(en = "Sales", gu = "વેચાણ")}: ${LanguageManager.formatCurrency(row.salesTotal)}",
                            fontSize = 12.sp
                        )
                        Text(
                            text = "${loc(en = "Purchases", gu = "ખરીદી")}: ${LanguageManager.formatCurrency(row.purchasesTotal)}",
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = "${loc(en = "Expenses", gu = "ખર્ચ")}: ${LanguageManager.formatCurrency(row.expensesTotal)}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val pColor = if (row.profitTotal >= 0) Color(0xFF15803D) else Color(0xFFB91C1C)
                        Text(
                            text = "${loc(en = "Profit", gu = "નફો")}: ${LanguageManager.formatCurrency(row.profitTotal)}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = pColor
                        )
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// REUSABLE STOCK TRANSACTION CARD
// -----------------------------------------------------------------------------------------
@Composable
private fun StockTransactionCard(tx: StockTransaction, isGu: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tx.remark.ifBlank {
                        if (tx.type == "CREDIT") loc(en = "Credit Entry", gu = "જમા વ્યવહાર") else loc(en = "Debit Entry", gu = "ઉધાર વ્યવહાર")
                    },
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )
                val sdf = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.US).apply {
                    timeZone = StatementCalculator.kolkataTimeZone
                }
                Text(
                    text = sdf.format(Date(tx.dateTimestamp)),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val isCredit = tx.type == "CREDIT"
            val qtyStr = if (tx.unit == "₹") {
                LanguageManager.formatCurrency(tx.quantityOrAmount)
            } else {
                LanguageManager.formatWeight(tx.quantityOrAmount, isGu)
            }
            val sign = if (isCredit) "+" else "-"
            val color = if (isCredit) Color(0xFF15803D) else Color(0xFFB91C1C)

            Text(
                text = "$sign$qtyStr",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = color
            )
        }
    }
}
