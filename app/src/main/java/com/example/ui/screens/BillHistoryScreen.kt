package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Bill
import com.example.ui.components.JewelleryTopBar
import com.example.ui.locale.AppStrings
import com.example.ui.locale.LanguageManager
import com.example.ui.locale.loc
import com.example.ui.theme.*
import com.example.ui.viewmodel.JewelleryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillHistoryScreen(
    viewModel: JewelleryViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToBillPreview: (String) -> Unit
) {
    val allBills by viewModel.allBills.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf("ALL") } // "ALL", "SALE", "KARIGAR"
    var filterGst by remember { mutableStateOf("ALL") } // "ALL", "GST", "NON_GST"

    val filteredBills = remember(allBills, searchQuery, filterType, filterGst) {
        allBills.filter { bill ->
            val matchQuery = searchQuery.isBlank() ||
                    bill.billNumber.contains(searchQuery, ignoreCase = true) ||
                    bill.partyName.contains(searchQuery, ignoreCase = true) ||
                    bill.partyMobile.contains(searchQuery)

            val matchType = when (filterType) {
                "SALE" -> bill.billType == "SALE"
                "KARIGAR" -> bill.billType == "KARIGAR_PURCHASE"
                else -> true
            }

            val matchGst = when (filterGst) {
                "GST" -> bill.isGstBill
                "NON_GST" -> !bill.isGstBill
                else -> true
            }

            matchQuery && matchType && matchGst
        }
    }

    Scaffold(
        topBar = {
            JewelleryTopBar(
                title = AppStrings.billHistory(),
                subtitle = "${AppStrings.totalBills()}: ${filteredBills.size}",
                showBackButton = true,
                onBackClick = onNavigateBack,
                logoBase64 = settings?.logoBase64,
                syncStatus = syncStatus
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text(AppStrings.searchBillsHint()) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = GoldDark) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("search_bills_input"),
                singleLine = true
            )

            // Filter Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filterType == "ALL",
                    onClick = { filterType = "ALL" },
                    label = { Text(AppStrings.filterAll()) }
                )
                FilterChip(
                    selected = filterType == "SALE",
                    onClick = { filterType = "SALE" },
                    label = { Text(AppStrings.customerSale()) }
                )
                FilterChip(
                    selected = filterType == "KARIGAR",
                    onClick = { filterType = "KARIGAR" },
                    label = { Text(AppStrings.karigarPurchase()) }
                )
                FilterChip(
                    selected = filterGst == "GST",
                    onClick = { filterGst = if (filterGst == "GST") "ALL" else "GST" },
                    label = { Text("GST") }
                )
            }

            if (filteredBills.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = AppStrings.noBillsFound(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredBills) { bill ->
                        BillHistoryCard(
                            bill = bill,
                            onClick = { onNavigateToBillPreview(bill.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BillHistoryCard(
    bill: Bill,
    onClick: () -> Unit
) {
    val sdf = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.US)
    val isSale = bill.billType == "SALE"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("bill_item_${bill.billNumber}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSale) GoldLight else CharcoalLight
                    ) {
                        Text(
                            text = if (isSale) AppStrings.customerSale() else AppStrings.karigarPurchase(),
                            color = if (isSale) GoldDark else Charcoal,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (bill.isGstBill) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = CashGreenLight
                        ) {
                            Text(
                                text = "GST ${LanguageManager.formatDouble(bill.gstPercent, 0)}%",
                                color = CashGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Text(
                    text = bill.billNumber,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = GoldDark
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = bill.partyName.ifBlank { loc(en = "Walk-in Customer", gu = "ગ્રાહક") },
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (bill.partyMobile.isNotBlank()) {
                        Text(
                            text = bill.partyMobile,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = LanguageManager.formatCurrency(bill.grandTotal),
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (bill.netBalanceDue > 0) {
                        Text(
                            text = "${AppStrings.balanceDue()}: ${LanguageManager.formatCurrency(bill.netBalanceDue)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = DebitRed
                        )
                    }
                }
            }

            // Actual Karat/Purity of items in this bill
            val billItems = bill.parseItems()
            if (billItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    billItems.take(3).forEach { item ->
                        val purityTag = if (item.purity.isNotBlank()) " [${item.purity}]" else ""
                        val wt = if (item.netWeight > 0) item.netWeight else item.grossWeight
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${item.description}$purityTag",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${LanguageManager.formatDouble(wt, 3)}g",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = GoldDark
                            )
                        }
                    }
                    if (billItems.size > 3) {
                        Text(
                            text = "+${billItems.size - 3} more items...",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Divider()
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = sdf.format(Date(bill.dateTimestamp)),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Print,
                        contentDescription = "Print",
                        modifier = Modifier.size(14.dp),
                        tint = GoldDark
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = AppStrings.previewAndPrint(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoldDark
                    )
                }
            }
        }
    }
}
