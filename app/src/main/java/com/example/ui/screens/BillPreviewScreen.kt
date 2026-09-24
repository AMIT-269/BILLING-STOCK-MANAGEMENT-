package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.ContextCompat
import com.example.data.model.Bill
import com.example.data.model.BillPayment
import com.example.ui.components.BluetoothPrinterDialog
import com.example.ui.components.JewelleryTopBar
import com.example.ui.components.SplitGoldCashDialog
import com.example.ui.locale.AppStrings
import com.example.ui.locale.LanguageManager
import com.example.ui.locale.isAppGujarati
import com.example.ui.locale.loc
import com.example.ui.theme.*
import com.example.ui.viewmodel.JewelleryViewModel
import com.example.util.BillShareHelper
import com.example.util.ImageHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillPreviewScreen(
    billId: String,
    viewModel: JewelleryViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToEditBill: (String) -> Unit,
    onBillDeleted: () -> Unit
) {
    val context = LocalContext.current
    val allBills by viewModel.allBills.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val isGu = isAppGujarati()

    val bill = remember(allBills, billId) {
        allBills.find { it.id == billId }
    }

    var showPrinterDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showAddPaymentDialog by remember { mutableStateOf(false) }
    var editingPayment by remember { mutableStateOf<BillPayment?>(null) }
    var showSplitGoldCashDialog by remember { mutableStateOf(false) }

    // Bluetooth permission launcher
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (granted) {
            showPrinterDialog = true
        } else {
            viewModel.showMessage(
                loc(
                    en = "Bluetooth permission is required to print receipt.",
                    gu = "રસીદ પ્રિન્ટ કરવા માટે બ્લૂટૂથ પરવાનગી જરૂરી છે."
                )
            )
        }
    }

    fun handlePrintClick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val connectGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
            val scanGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED

            if (connectGranted && scanGranted) {
                showPrinterDialog = true
            } else {
                bluetoothPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                    )
                )
            }
        } else {
            showPrinterDialog = true
        }
    }

    if (showPrinterDialog && bill != null) {
        BluetoothPrinterDialog(
            onDismissRequest = { showPrinterDialog = false },
            onSelectPrinter = { device ->
                showPrinterDialog = false
                viewModel.printBillViaBluetooth(device, bill) { _, _ -> }
            }
        )
    }

    if (showDeleteConfirmDialog && bill != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(AppStrings.delete(), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    loc(
                        en = "Are you sure you want to delete bill ${bill.billNumber}? Any affected stock will be restored automatically.",
                        gu = "શું તમે ખરેખર બિલ ${bill.billNumber} કાઢી નાખવા માંગો છો? આ બિલથી થયેલ સ્ટોક ફેરફાર પાછો આવી જશે."
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        viewModel.deleteBill(bill) {
                            onBillDeleted()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DebitRed),
                    modifier = Modifier.testTag("confirm_delete_bill_btn")
                ) {
                    Text(AppStrings.delete(), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(AppStrings.cancel())
                }
            }
        )
    }

    if (showAddPaymentDialog && bill != null) {
        PaymentEntryDialog(
            initialPayment = null,
            billType = bill.billType,
            defaultGoldRate = settings?.goldRate22k ?: 7200.0,
            defaultSilverRate = settings?.silverRate ?: 88.0,
            isGu = isGu,
            onDismiss = { showAddPaymentDialog = false },
            onSave = { newPayment ->
                viewModel.addAdditionalPayment(bill.id, newPayment)
                showAddPaymentDialog = false
            }
        )
    }

    if (editingPayment != null && bill != null) {
        PaymentEntryDialog(
            initialPayment = editingPayment,
            billType = bill.billType,
            defaultGoldRate = settings?.goldRate22k ?: 7200.0,
            defaultSilverRate = settings?.silverRate ?: 88.0,
            isGu = isGu,
            onDismiss = { editingPayment = null },
            onSave = { updatedPayment ->
                viewModel.updateAdditionalPayment(bill.id, updatedPayment)
                editingPayment = null
            }
        )
    }

    if (showSplitGoldCashDialog && bill != null) {
        val defaultRate = if (bill.itemsJson.isNotBlank()) {
            bill.parseItems().firstOrNull { it.metalType.equals("GOLD", ignoreCase = true) }?.ratePerGram
                ?: (settings?.goldRate22k ?: 10000.0)
        } else (settings?.goldRate22k ?: 10000.0)
        val defaultSilRate = if (bill.itemsJson.isNotBlank()) {
            bill.parseItems().firstOrNull { it.metalType.equals("SILVER", ignoreCase = true) }?.ratePerGram
                ?: (settings?.silverRate ?: 90.0)
        } else (settings?.silverRate ?: 90.0)

        SplitGoldCashDialog(
            grandTotal = bill.grandTotal,
            billType = bill.billType,
            defaultGoldRate = defaultRate,
            defaultSilverRate = defaultSilRate,
            isGu = isGu,
            onDismiss = { showSplitGoldCashDialog = false },
            onConfirm = { pMetal, pCash ->
                val list = mutableListOf<BillPayment>()
                if (pMetal.amount > 0.0 || pMetal.metalWeight > 0.0) list.add(pMetal)
                if (pCash.amount > 0.0) list.add(pCash)
                viewModel.setBillPayments(bill.id, list)
                showSplitGoldCashDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            JewelleryTopBar(
                title = AppStrings.previewTitle(),
                subtitle = bill?.billNumber ?: "",
                showBackButton = true,
                onBackClick = onNavigateBack,
                logoBase64 = settings?.logoBase64,
                syncStatus = syncStatus,
                actions = {
                    IconButton(
                        onClick = {
                            if (bill != null) {
                                BillShareHelper.shareBillText(context, bill, settings)
                            }
                        },
                        modifier = Modifier.testTag("share_bill_btn")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = AppStrings.share())
                    }
                    IconButton(
                        onClick = {
                            if (bill != null) {
                                onNavigateToEditBill(bill.id)
                            }
                        },
                        modifier = Modifier.testTag("edit_bill_btn")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = AppStrings.edit())
                    }
                    IconButton(
                        onClick = { showDeleteConfirmDialog = true },
                        modifier = Modifier.testTag("delete_bill_btn")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = AppStrings.delete(), tint = DebitRed)
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (bill != null) {
                                BillShareHelper.shareBillText(context, bill, settings)
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("share_digital_bill_btn")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(AppStrings.share(), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { handlePrintClick() },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldDark),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("print_bill_btn")
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(AppStrings.printThermal(), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (bill == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(loc(en = "Bill not found.", gu = "બિલ મળ્યું નથી."))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Receipt Container Card (Exact visual photo style)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFD0D7DE), RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Header Banner (Dark Navy)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0F172A))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                val logoBitmap = remember(settings?.logoBase64) {
                                    ImageHelper.base64ToBitmap(settings?.logoBase64)
                                }
                                if (logoBitmap != null) {
                                    Image(
                                        bitmap = logoBitmap.asImageBitmap(),
                                        contentDescription = "Shop Logo",
                                        modifier = Modifier
                                            .size(50.dp)
                                            .clip(CircleShape)
                                            .border(1.5.dp, GoldPrimary, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                }

                                Text(
                                    text = settings?.jewellerName?.ifBlank { "JEWELLERY SHOP" } ?: "JEWELLERY SHOP",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        fontSize = 19.sp,
                                        letterSpacing = 0.5.sp
                                    ),
                                    textAlign = TextAlign.Center
                                )

                                if (bill.isGstBill && !settings?.gstNumber.isNullOrBlank()) {
                                    Text(
                                        text = "${AppStrings.gstNumber()}: ${settings!!.gstNumber}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = GoldLight
                                    )
                                }

                                if (!settings?.address.isNullOrBlank()) {
                                    Text(
                                        text = settings!!.address,
                                        fontSize = 11.sp,
                                        color = Color(0xFFCBD5E1),
                                        textAlign = TextAlign.Center
                                    )
                                }

                                if (!settings?.contactNumber.isNullOrBlank()) {
                                    Text(
                                        text = "${loc(en = "Mobile", gu = "મોબાઈલ")}: ${settings!!.contactNumber}",
                                        fontSize = 11.sp,
                                        color = Color(0xFFE2E8F0)
                                    )
                                }
                            }
                        }

                        // Subheader / Invoice Type Ribbon
                        val invoiceTypeStr = if (bill.billType == "SALE") {
                            if (bill.isGstBill) AppStrings.taxInvoiceGst() else AppStrings.retailInvoiceNonGst()
                        } else {
                            if (bill.isGstBill) "${AppStrings.karigarPurchase()} (${AppStrings.taxInvoiceGst()})" else "${AppStrings.karigarPurchase()} (${AppStrings.retailInvoiceNonGst()})"
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1E293B))
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = invoiceTypeStr.uppercase(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = GoldLight,
                                letterSpacing = 1.sp
                            )
                        }

                        // Meta details: Customer Details & Bill Details Cards
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Customer / Karigar Details Card
                                Card(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Person,
                                                contentDescription = null,
                                                tint = Color(0xFF0F172A),
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = AppStrings.partyDetails(bill.billType == "SALE"),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = Color(0xFF0F172A)
                                            )
                                        }
                                        Divider(modifier = Modifier.padding(vertical = 2.dp), color = Color(0xFFE2E8F0))
                                        Text(
                                            text = bill.partyName.ifBlank { loc(en = "Walk-in Customer", gu = "ગ્રાહક") },
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            color = Color(0xFF1E293B)
                                        )
                                        if (bill.partyMobile.isNotBlank()) {
                                            Text(
                                                text = "${loc(en = "Ph", gu = "ફોન")}: ${bill.partyMobile}",
                                                fontSize = 11.sp,
                                                color = Color(0xFF64748B)
                                            )
                                        }
                                        if (bill.partyAddress.isNotBlank()) {
                                            Text(
                                                text = bill.partyAddress,
                                                fontSize = 10.sp,
                                                color = Color(0xFF64748B)
                                            )
                                        }
                                    }
                                }

                                // Bill Details Card
                                Card(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Receipt,
                                                contentDescription = null,
                                                tint = Color(0xFF0F172A),
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = loc(en = "Bill Details", gu = "બિલ વિગત"),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = Color(0xFF0F172A)
                                            )
                                        }
                                        Divider(modifier = Modifier.padding(vertical = 2.dp), color = Color(0xFFE2E8F0))
                                        val sdfDate = SimpleDateFormat("dd/MM/yyyy", Locale.US)
                                        val sdfTime = SimpleDateFormat("hh:mm a", Locale.US)
                                        val d = Date(bill.dateTimestamp)
                                        Text(
                                            text = "${AppStrings.billNumber()}: ${bill.billNumber}",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            color = Color(0xFF1E293B)
                                        )
                                        Text(
                                            text = "${AppStrings.billDate()}: ${sdfDate.format(d)}",
                                            fontSize = 11.sp,
                                            color = Color(0xFF64748B)
                                        )
                                        Text(
                                            text = "${AppStrings.billTime()}: ${sdfTime.format(d)}",
                                            fontSize = 11.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Section Title Bar: Jewellery Item Details
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF0F172A), RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Diamond,
                                        contentDescription = null,
                                        tint = GoldLight,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = AppStrings.itemDetails(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color.White
                                    )
                                }
                            }

                            // Items Table with Horizontal Scroll for pristine alignment
                            val items = bill.parseItems()
                            val hScrollState = rememberScrollState()

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, Color(0xFF94A3B8))
                                    .horizontalScroll(hScrollState)
                            ) {
                                Column(modifier = Modifier.width(820.dp)) {
                                    // Header Row
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFFE2E8F0))
                                            .padding(vertical = 8.dp, horizontal = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TableCell(AppStrings.colNo(), 35.dp, isHeader = true, align = TextAlign.Center)
                                        TableCell(AppStrings.colItem(), 130.dp, isHeader = true)
                                        TableCell(AppStrings.colMetal(), 70.dp, isHeader = true, align = TextAlign.Center)
                                        TableCell(AppStrings.colWeight(), 85.dp, isHeader = true, align = TextAlign.End)
                                        TableCell(AppStrings.colTouch(), 85.dp, isHeader = true, align = TextAlign.End)
                                        TableCell(AppStrings.colMaking(), 85.dp, isHeader = true, align = TextAlign.End)
                                        TableCell(AppStrings.colTotalTouch(), 75.dp, isHeader = true, align = TextAlign.End)
                                        TableCell(AppStrings.colTotalFine(), 85.dp, isHeader = true, align = TextAlign.End)
                                        TableCell(AppStrings.colPrice(), 90.dp, isHeader = true, align = TextAlign.End)
                                        TableCell(AppStrings.colAmount(), 95.dp, isHeader = true, align = TextAlign.End)
                                    }

                                    Divider(color = Color(0xFF94A3B8), thickness = 1.dp)

                                    // Item Rows
                                    items.forEachIndexed { index, item ->
                                        val metalName = if (item.metalType == "GOLD") AppStrings.gold() else AppStrings.silver()
                                        val metalDisplay = if (item.purity.isNotBlank()) "$metalName (${item.purity})" else metalName
                                        val wt = if (item.netWeight > 0) item.netWeight else item.grossWeight
                                        val wtFormatted = LanguageManager.formatDouble(wt, 3)
                                        val touchFormatted = "${LanguageManager.formatDouble(item.currentTouch, 1)}%"
                                        val makingFormatted = when {
                                            item.makingChargePercent > 0 && item.makingCharges > 0 -> "${LanguageManager.formatDouble(item.makingChargePercent, 1)}% + ₹${LanguageManager.formatDouble(item.makingCharges, 0)}"
                                            item.makingChargePercent > 0 -> "${LanguageManager.formatDouble(item.makingChargePercent, 1)}%"
                                            item.makingCharges > 0 -> "₹${LanguageManager.formatDouble(item.makingCharges, 0)}"
                                            else -> "0%"
                                        }
                                        val totalTouchFormatted = "${LanguageManager.formatDouble(item.totalTouch, 1)}%"
                                        val totalFineFormatted = LanguageManager.formatDouble(item.totalFine, 3)
                                        val rateUnit = if (item.metalType == "SILVER") "/kg" else "/g"
                                        val priceFormatted = "${LanguageManager.formatDouble(item.ratePerGram, 0)}$rateUnit"
                                        val amountFormatted = LanguageManager.formatDouble(item.itemTotal, 2)

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(if (index % 2 == 1) Color(0xFFF1F5F9) else Color.White)
                                                .padding(vertical = 8.dp, horizontal = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            TableCell("${index + 1}", 35.dp, isBold = true, align = TextAlign.Center)
                                            TableCell(item.description, 130.dp, isBold = true)
                                            TableCell(metalDisplay, 70.dp, isBold = true, align = TextAlign.Center)
                                            TableCell(wtFormatted, 85.dp, isBold = true, align = TextAlign.End)
                                            TableCell(touchFormatted, 85.dp, align = TextAlign.End)
                                            TableCell(makingFormatted, 85.dp, align = TextAlign.End)
                                            TableCell(totalTouchFormatted, 75.dp, align = TextAlign.End)
                                            TableCell(totalFineFormatted, 85.dp, isBold = true, align = TextAlign.End)
                                            TableCell(priceFormatted, 90.dp, align = TextAlign.End)
                                            TableCell(amountFormatted, 95.dp, isBold = true, align = TextAlign.End)
                                        }
                                        Divider(color = Color(0xFFCBD5E1), thickness = 0.8.dp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Additional Charges and Totals Cards
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Additional Charges Card
                                Card(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = AppStrings.additionalCharges(),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = Color(0xFF0F172A)
                                        )
                                        Divider(modifier = Modifier.padding(vertical = 2.dp), color = Color(0xFFE2E8F0))

                                        if (bill.isGstBill) {
                                            val halfGst = bill.gstAmount / 2.0
                                            val halfPercent = bill.gstPercent / 2.0
                                            MiniRow(
                                                title = "${AppStrings.cgst()} (${LanguageManager.formatDouble(halfPercent, 1)}%):",
                                                value = LanguageManager.formatCurrency(halfGst)
                                            )
                                            MiniRow(
                                                title = "${AppStrings.sgst()} (${LanguageManager.formatDouble(halfPercent, 1)}%):",
                                                value = LanguageManager.formatCurrency(halfGst)
                                            )
                                        } else {
                                            Text(
                                                text = loc(en = "Non-GST Retail Bill", gu = "જીએસટી વગરનું રિટેલ બિલ"),
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF1E293B)
                                            )
                                        }

                                        if (bill.discount > 0) {
                                            MiniRow(
                                                title = "${AppStrings.discount()}:",
                                                value = "- ${LanguageManager.formatCurrency(bill.discount)}",
                                                color = DebitRed
                                            )
                                        }

                                        if (bill.oldMetalExchangeAmount > 0) {
                                            MiniRow(
                                                title = "${AppStrings.oldMetalExchange()}:",
                                                value = "- ${LanguageManager.formatCurrency(bill.oldMetalExchangeAmount)}",
                                                color = Color(0xFFB45309)
                                            )
                                        }
                                    }
                                }

                                // Totals Card
                                Card(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = loc(en = "Total Amount", gu = "કુલ રકમ"),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = Color(0xFF0F172A)
                                        )
                                        Divider(modifier = Modifier.padding(vertical = 2.dp), color = Color(0xFFE2E8F0))

                                        MiniRow(
                                            title = "${AppStrings.subtotal()}:",
                                            value = LanguageManager.formatCurrency(bill.subtotal)
                                        )

                                        if (bill.isGstBill) {
                                            MiniRow(
                                                title = "${AppStrings.gst()} (${LanguageManager.formatDouble(bill.gstPercent, 1)}%):",
                                                value = LanguageManager.formatCurrency(bill.gstAmount)
                                            )
                                        }

                                        Divider(modifier = Modifier.padding(vertical = 2.dp), color = Color(0xFFE2E8F0))

                                        // Grand Total Pill
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFF0F172A),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = AppStrings.grandTotal(),
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp
                                                )
                                                Text(
                                                    text = LanguageManager.formatCurrency(bill.grandTotal),
                                                    color = GoldLight,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Payment Details & Remarks Cards
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Payment Details Card
                                Card(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(
                                            text = AppStrings.paymentDetails(),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = Color(0xFF0F172A)
                                        )
                                        Divider(modifier = Modifier.padding(vertical = 2.dp), color = Color(0xFFE2E8F0))

                                        val parsedPayments = bill.getEffectivePayments()
                                        val isSale = bill.billType == "SALE"
                                        if (parsedPayments.isNotEmpty()) {
                                            parsedPayments.forEach { p ->
                                                val label = p.getDisplayLabel(isSale = isSale, isGu = isGu)
                                                if (p.metalWeight > 0 && (p.paymentMode.equals("GOLD", ignoreCase = true) || p.paymentMode.equals("SILVER", ignoreCase = true))) {
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = GoldLight.copy(alpha = 0.25f),
                                                        border = androidx.compose.foundation.BorderStroke(0.8.dp, GoldPrimary.copy(alpha = 0.5f)),
                                                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                                    ) {
                                                        Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Text(
                                                                    text = label,
                                                                    fontSize = 11.5.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = Color(0xFF1E293B)
                                                                )
                                                                Text(
                                                                    text = LanguageManager.formatCurrency(p.amount),
                                                                    fontSize = 12.sp,
                                                                    fontWeight = FontWeight.ExtraBold,
                                                                    color = GoldDark
                                                                )
                                                            }
                                                            Text(
                                                                text = p.getFullPaymentFormula(isGu),
                                                                fontSize = 10.5.sp,
                                                                fontWeight = FontWeight.SemiBold,
                                                                color = GoldDark
                                                            )
                                                            if (p.note.isNotBlank()) {
                                                                Text(
                                                                    text = p.note,
                                                                    fontSize = 9.5.sp,
                                                                    color = Color(0xFF64748B)
                                                                )
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                                                        MiniRow(
                                                            title = label,
                                                            value = LanguageManager.formatCurrency(p.amount),
                                                            color = CashGreen,
                                                            isBold = true
                                                        )
                                                        if (p.note.isNotBlank()) {
                                                             Text(
                                                                 text = p.note,
                                                                 fontSize = 9.5.sp,
                                                                 color = Color(0xFF64748B),
                                                                 modifier = Modifier.padding(start = 4.dp)
                                                             )
                                                        }
                                                    }
                                                }
                                            }
                                            Divider(modifier = Modifier.padding(vertical = 2.dp), color = Color(0xFFE2E8F0))
                                            val totalPaid = parsedPayments.sumOf { it.amount }
                                            val totalLabel = if (isSale) {
                                                if (isGu) "કુલ મેળવેલ:" else "Total Received:"
                                            } else {
                                                if (isGu) "કુલ ચૂકવેલ:" else "Total Paid:"
                                            }
                                            MiniRow(
                                                title = totalLabel,
                                                value = LanguageManager.formatCurrency(totalPaid),
                                                color = CashGreen,
                                                isBold = true
                                            )
                                        } else {
                                            val totalLabel = if (isSale) {
                                                if (isGu) "કુલ મેળવેલ:" else "Total Received:"
                                            } else {
                                                if (isGu) "કુલ ચૂકવેલ:" else "Total Paid:"
                                            }
                                            MiniRow(
                                                title = totalLabel,
                                                value = LanguageManager.formatCurrency(0.0),
                                                color = CashGreen,
                                                isBold = true
                                            )
                                        }
                                        val balanceLabel = if (isGu) "બાકી રકમ (Balance):" else "Balance:"
                                        MiniRow(
                                            title = balanceLabel,
                                            value = LanguageManager.formatCurrency(bill.netBalanceDue),
                                            color = if (bill.netBalanceDue > 0) DebitRed else CashGreen,
                                            isBold = true
                                        )
                                    }
                                }

                                // Remarks & Blessings Card
                                Card(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = AppStrings.remarks(),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = Color(0xFF0F172A)
                                        )
                                        Divider(modifier = Modifier.padding(vertical = 2.dp), color = Color(0xFFE2E8F0))

                                        if (bill.notes.isNotBlank()) {
                                            Text(
                                                text = bill.notes,
                                                fontSize = 10.sp,
                                                color = Color(0xFF1E293B)
                                            )
                                        }
                                        Text(
                                            text = AppStrings.thankYouPurchase(),
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF1E293B)
                                        )
                                        Text(
                                            text = AppStrings.visitAgain(),
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF1E293B)
                                        )
                                        Text(
                                            text = AppStrings.shubhLabh(),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = GoldDark
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Payment History & Additional Entries (Installments & Metal Payments)
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = GoldDark, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = AppStrings.paymentHistory(),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = Color(0xFF0F172A)
                                            )
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Button(
                                                onClick = { showSplitGoldCashDialog = true },
                                                colors = ButtonDefaults.buttonColors(containerColor = GoldDark),
                                                shape = RoundedCornerShape(6.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(12.dp))
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text(loc(en = "+ Gold + Cash", gu = "+ સોનું + રોકડ"), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }

                                            Button(
                                                onClick = { showAddPaymentDialog = true },
                                                colors = ButtonDefaults.buttonColors(containerColor = CashGreen),
                                                shape = RoundedCornerShape(6.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp))
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text(AppStrings.addAdditionalPayment(), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    Divider(modifier = Modifier.padding(vertical = 2.dp), color = Color(0xFFE2E8F0))

                                    val paymentsList = bill.getEffectivePayments()
                                    if (paymentsList.isEmpty()) {
                                        Text(
                                            text = loc(en = "No payments recorded yet", gu = "હજુ સુધી કોઈ ચુકવણી નોંધાયેલ નથી"),
                                            fontSize = 11.sp,
                                            color = Color(0xFF475569)
                                        )
                                    } else {
                                        paymentsList.forEachIndexed { pIdx, p ->
                                            val pMode = when (p.paymentMode) {
                                                "GOLD" -> AppStrings.modeGold()
                                                "SILVER" -> AppStrings.modeSilver()
                                                "ONLINE" -> AppStrings.modeOnline()
                                                "CHEQUE" -> AppStrings.modeCheque()
                                                else -> AppStrings.modeCash()
                                            }
                                            val dateStr = SimpleDateFormat("dd/MM/yyyy hh:mm a", java.util.Locale.ENGLISH).format(Date(p.dateTimestamp))
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color.White,
                                                border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFFCBD5E1)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(6.dp).fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text(
                                                                text = "#${pIdx + 1} $pMode",
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 11.sp,
                                                                color = Color(0xFF0F172A)
                                                            )
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text(
                                                                text = dateStr,
                                                                fontSize = 9.sp,
                                                                color = Color(0xFF94A3B8)
                                                            )
                                                        }
                                                        if (p.metalWeight > 0) {
                                                            val rateUnit = if (p.paymentMode == "SILVER") "/kg" else "/g"
                                                            Text(
                                                                text = "${LanguageManager.formatWeight(p.metalWeight, isGu)} @ ₹${LanguageManager.formatDouble(p.metalRate, 0)}$rateUnit (${LanguageManager.formatDouble(p.metalTouch, 1)}%)",
                                                                fontSize = 10.sp,
                                                                color = Color(0xFF475569)
                                                            )
                                                        }
                                                        if (p.note.isNotBlank()) {
                                                            Text(
                                                                text = p.note,
                                                                fontSize = 10.sp,
                                                                color = Color(0xFF64748B)
                                                            )
                                                        }
                                                    }
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = LanguageManager.formatCurrency(p.amount),
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 12.sp,
                                                            color = CashGreen
                                                        )
                                                        IconButton(
                                                            onClick = {
                                                                editingPayment = p
                                                            },
                                                            modifier = Modifier.size(24.dp)
                                                        ) {
                                                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF2563EB), modifier = Modifier.size(14.dp))
                                                        }
                                                        IconButton(
                                                            onClick = {
                                                                viewModel.deleteAdditionalPayment(bill.id, p.id)
                                                            },
                                                            modifier = Modifier.size(24.dp)
                                                        ) {
                                                            Icon(Icons.Default.Close, contentDescription = "Delete", tint = DebitRed, modifier = Modifier.size(14.dp))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        // Bottom Thank You Banner (Navy)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0F172A))
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = AppStrings.thankYouBanner(),
                                color = GoldLight,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun TableCell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    isHeader: Boolean = false,
    isBold: Boolean = false,
    align: TextAlign = TextAlign.Start
) {
    Text(
        text = text,
        modifier = Modifier
            .width(width)
            .padding(horizontal = 4.dp),
        fontWeight = if (isHeader) FontWeight.ExtraBold else if (isBold) FontWeight.Bold else FontWeight.SemiBold,
        fontSize = if (isHeader) 11.sp else 12.sp,
        color = if (isHeader) Color(0xFF0F172A) else Color(0xFF000000),
        textAlign = align,
        maxLines = 2
    )
}

@Composable
private fun MiniRow(
    title: String,
    value: String,
    subtitle: String? = null,
    badge: String? = null,
    color: Color = Color.Unspecified,
    isBold: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E293B)
                )
                if (!badge.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(
                        color = Color(0xFFFEF3C7),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = badge,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF92400E),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = Color(0xFF64748B)
                )
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = if (isBold) FontWeight.ExtraBold else FontWeight.Bold,
            color = if (color != Color.Unspecified) color else Color(0xFF000000)
        )
    }
}

@Composable
fun SplitGoldCashDialog(
    grandTotal: Double,
    billType: String,
    defaultGoldRate: Double,
    isGu: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (goldPayment: BillPayment, cashPayment: BillPayment) -> Unit
) {
    var goldWeightText by remember { mutableStateOf("6.000") }
    var goldTouchText by remember { mutableStateOf("80.0") }
    var goldRateText by remember {
        mutableStateOf(if (defaultGoldRate > 0) LanguageManager.formatDouble(defaultGoldRate, 0) else "")
    }

    val goldWeight = goldWeightText.toDoubleOrNull() ?: 0.0
    val goldTouch = goldTouchText.toDoubleOrNull() ?: 80.0
    val goldRate = goldRateText.toDoubleOrNull() ?: 0.0
    val fineWeight = (goldWeight * goldTouch / 100.0)
    val goldAmount = fineWeight * goldRate

    val suggestedCash = (grandTotal - goldAmount).coerceAtLeast(0.0)
    var cashText by remember { mutableStateOf(LanguageManager.formatDouble(suggestedCash, 0)) }

    LaunchedEffect(goldAmount, grandTotal) {
        cashText = LanguageManager.formatDouble((grandTotal - goldAmount).coerceAtLeast(0.0), 0)
    }

    val cashAmount = cashText.toDoubleOrNull() ?: 0.0
    val totalPaid = goldAmount + cashAmount
    val balance = (grandTotal - totalPaid).coerceAtLeast(0.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Payments, contentDescription = null, tint = GoldDark, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = loc(en = "Gold + Cash Payment", gu = "સોનું + રોકડ ચુકવણી"),
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
                Surface(
                    color = Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(loc(en = "Bill Grand Total:", gu = "બિલ કુલ રકમ:"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(LanguageManager.formatCurrency(grandTotal), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = GoldDark)
                    }
                }

                Text(
                    text = loc(en = "🟡 1. Gold Payment (સોનામાં ચુકવણી):", gu = "🟡 ૧. સોનામાં ચુકવણી:"),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = GoldDark
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = goldWeightText,
                        onValueChange = { goldWeightText = it },
                        label = { Text(loc(en = "Gold Wt (g)", gu = "વજન (ગ્રા)")) },
                        placeholder = { Text("6.000") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = goldTouchText,
                        onValueChange = { goldTouchText = it },
                        label = { Text(loc(en = "Touch %", gu = "ટચ %")) },
                        placeholder = { Text("80.0%") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = goldRateText,
                    onValueChange = { goldRateText = it },
                    label = { Text(loc(en = "Gold Price (₹/g)", gu = "સોના ભાવ (₹/ગ્રામ)")) },
                    placeholder = { Text("₹/g") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Surface(
                    color = GoldLight.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${loc(en = "Fine:", gu = "ફાઇન:")} ${LanguageManager.formatDouble(fineWeight, 3)}g",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${loc(en = "Value:", gu = "મૂલ્ય:")} ${LanguageManager.formatCurrency(goldAmount)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldDark
                        )
                    }
                }

                Divider(color = Color(0xFFE2E8F0))

                Text(
                    text = loc(en = "💵 2. Cash Payment (રોકડ ચુકવણી):", gu = "💵 ૨. રોકડ ચુકવણી:"),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = CashGreen
                )

                OutlinedTextField(
                    value = cashText,
                    onValueChange = { cashText = it },
                    label = { Text(loc(en = "Remaining Cash (₹)", gu = "બાકી રોકડ (₹)")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Surface(
                    color = CashGreen.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(loc(en = "Total Payment:", gu = "કુલ ચુકવણી:"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(LanguageManager.formatCurrency(totalPaid), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CashGreen)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(loc(en = "Remaining Balance:", gu = "બાકી રકમ:"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                    val p1 = BillPayment(
                        id = java.util.UUID.randomUUID().toString(),
                        entryNumber = 1,
                        dateTimestamp = System.currentTimeMillis(),
                        paymentMode = "GOLD",
                        amount = goldAmount,
                        metalWeight = goldWeight,
                        metalTouch = goldTouch,
                        metalRate = goldRate,
                        fineWeight = fineWeight,
                        note = if (isSale) "Gold received (વેચાણ બિલ સોનું)" else "Gold paid (ખરીદી બિલ સોનું)"
                    )
                    val p2 = BillPayment(
                        id = java.util.UUID.randomUUID().toString(),
                        entryNumber = 2,
                        dateTimestamp = System.currentTimeMillis(),
                        paymentMode = "CASH",
                        amount = cashAmount,
                        note = if (isSale) "Cash received (રોકડ મેળવી)" else "Cash paid (રોકડ ચૂકવી)"
                    )
                    onConfirm(p1, p2)
                },
                colors = ButtonDefaults.buttonColors(containerColor = GoldDark)
            ) {
                Text(loc(en = "Apply Gold + Cash", gu = "સોનું + રોકડ લાગુ કરો"), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(AppStrings.cancel())
            }
        }
    )
}
