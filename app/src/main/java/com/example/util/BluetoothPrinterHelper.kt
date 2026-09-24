package com.example.util

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

object BluetoothPrinterHelper {
    private const val TAG = "BluetoothPrinterHelper"
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private const val PREFS_NAME = "printer_prefs"
    private const val KEY_PRINTER_ADDRESS = "pref_printer_address"
    private const val KEY_PRINTER_NAME = "pref_printer_name"

    val kolkataTimeZone: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")

    // ESC/POS Command constants
    private val ESC_INIT = byteArrayOf(0x1B, 0x40)
    private val ESC_ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
    private val ESC_ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)
    private val ESC_ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02)
    private val ESC_BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01)
    private val ESC_BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00)
    private val ESC_DOUBLE_HEIGHT = byteArrayOf(0x1B, 0x21, 0x10)
    private val ESC_DOUBLE_WIDTH_HEIGHT = byteArrayOf(0x1D, 0x21, 0x11)
    private val ESC_NORMAL = byteArrayOf(0x1D, 0x21, 0x00)
    private val ESC_FEED_AND_CUT = byteArrayOf(0x1D, 0x56, 0x41, 0x03)

    data class BluetoothPrinterDevice(
        val name: String,
        val address: String,
        val device: BluetoothDevice
    )

    fun getBluetoothAdapter(): BluetoothAdapter? {
        return BluetoothAdapter.getDefaultAdapter()
    }

    fun isBluetoothSupported(): Boolean {
        return getBluetoothAdapter() != null
    }

    fun isBluetoothEnabled(): Boolean {
        return getBluetoothAdapter()?.isEnabled == true
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun savePreferredPrinter(context: Context, address: String, name: String) {
        getPrefs(context).edit()
            .putString(KEY_PRINTER_ADDRESS, address)
            .putString(KEY_PRINTER_NAME, name)
            .apply()
    }

    fun getSavedPrinterName(context: Context): String? {
        return getPrefs(context).getString(KEY_PRINTER_NAME, null)
    }

    fun getSavedPrinterAddress(context: Context): String? {
        return getPrefs(context).getString(KEY_PRINTER_ADDRESS, null)
    }

    @SuppressLint("MissingPermission")
    fun getPreferredPrinter(context: Context): BluetoothPrinterDevice? {
        val address = getSavedPrinterAddress(context) ?: return null
        val adapter = getBluetoothAdapter() ?: return null
        return try {
            val device = adapter.getRemoteDevice(address)
            val name = getSavedPrinterName(context) ?: device.name ?: "Saved Printer"
            BluetoothPrinterDevice(name, address, device)
        } catch (e: Exception) {
            null
        }
    }

    fun clearPreferredPrinter(context: Context) {
        getPrefs(context).edit().clear().apply()
    }

    @SuppressLint("MissingPermission")
    fun getPairedPrinters(): List<BluetoothPrinterDevice> {
        val adapter = getBluetoothAdapter() ?: return emptyList()
        val paired = mutableListOf<BluetoothPrinterDevice>()
        try {
            val bondedDevices = adapter.bondedDevices
            for (device in bondedDevices) {
                val name = device.name ?: "Unknown Printer"
                paired.add(BluetoothPrinterDevice(name, device.address, device))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching paired devices", e)
        }
        return paired
    }

    /**
     * Connects to a Bluetooth Thermal Printer and sends raw ESC/POS bytes
     */
    @SuppressLint("MissingPermission")
    suspend fun printReceiptBytes(
        device: BluetoothDevice,
        receiptBytes: ByteArray
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        var socket: BluetoothSocket? = null
        var outputStream: OutputStream? = null

        try {
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            try {
                BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery()
            } catch (_: Exception) {}

            socket.connect()
            outputStream = socket.outputStream

            outputStream.write(receiptBytes)
            outputStream.flush()

            kotlinx.coroutines.delay(1000)
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to print via Bluetooth", e)
            Result.failure(e)
        } finally {
            try { outputStream?.close() } catch (_: Exception) {}
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    suspend fun printBill(
        device: BluetoothDevice,
        bill: Bill,
        settings: JewellerSettings?
    ): Result<Boolean> {
        val bytes = generateEscPosBill(bill, settings)
        return printReceiptBytes(device, bytes)
    }

    suspend fun printGoldStatement(
        device: BluetoothDevice,
        data: GoldStatementData,
        settings: JewellerSettings?
    ): Result<Boolean> {
        val bytes = generateEscPosGoldStatement(data, settings)
        return printReceiptBytes(device, bytes)
    }

    suspend fun printSilverStatement(
        device: BluetoothDevice,
        data: SilverStatementData,
        settings: JewellerSettings?
    ): Result<Boolean> {
        val bytes = generateEscPosSilverStatement(data, settings)
        return printReceiptBytes(device, bytes)
    }

    suspend fun printCashStatement(
        device: BluetoothDevice,
        data: CashStatementData,
        settings: JewellerSettings?
    ): Result<Boolean> {
        val bytes = generateEscPosCashStatement(data, settings)
        return printReceiptBytes(device, bytes)
    }

    suspend fun printMonthlyStatement(
        device: BluetoothDevice,
        data: MonthlyStatementData,
        settings: JewellerSettings?
    ): Result<Boolean> {
        val bytes = generateEscPosMonthlyStatement(data, settings)
        return printReceiptBytes(device, bytes)
    }

    suspend fun printYearlyStatement(
        device: BluetoothDevice,
        data: YearlyStatementData,
        settings: JewellerSettings?
    ): Result<Boolean> {
        val bytes = generateEscPosYearlyStatement(data, settings)
        return printReceiptBytes(device, bytes)
    }

    // Helper to format timestamps using India Timezone Asia/Kolkata
    private fun getKolkataDateTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.US)
        sdf.timeZone = kolkataTimeZone
        return sdf.format(Date(timestamp))
    }

    private fun writeHeader(
        output: ByteArrayOutputStream,
        settings: JewellerSettings?,
        title: String
    ) {
        fun write(bytes: ByteArray) = output.write(bytes)
        fun writeText(text: String) = output.write(text.toByteArray(charset("ISO-8859-1")))
        fun writeLine(text: String = "") {
            writeText(text)
            output.write(0x0A)
        }

        write(ESC_INIT)
        write(ESC_ALIGN_CENTER)
        write(ESC_BOLD_ON)
        write(ESC_DOUBLE_WIDTH_HEIGHT)
        val shopName = settings?.jewellerName?.ifBlank { "JEWELLERY SHOP" } ?: "JEWELLERY SHOP"
        writeLine(shopName)

        write(ESC_NORMAL)
        write(ESC_BOLD_OFF)
        if (!settings?.address.isNullOrBlank()) {
            writeLine(settings!!.address)
        }
        if (!settings?.contactNumber.isNullOrBlank()) {
            writeLine("Ph: ${settings!!.contactNumber}")
        }
        if (!settings?.gstNumber.isNullOrBlank()) {
            writeLine("GSTIN: ${settings!!.gstNumber}")
        }

        writeLine("--------------------------------")
        write(ESC_BOLD_ON)
        writeLine(title)
        write(ESC_BOLD_OFF)
        write(ESC_ALIGN_LEFT)
        writeLine("Date: ${getKolkataDateTime(System.currentTimeMillis())}")
        writeLine("--------------------------------")
    }

    private fun writeFooter(output: ByteArrayOutputStream) {
        fun write(bytes: ByteArray) = output.write(bytes)
        fun writeText(text: String) = output.write(text.toByteArray(charset("ISO-8859-1")))
        fun writeLine(text: String = "") {
            writeText(text)
            output.write(0x0A)
        }

        write(ESC_ALIGN_CENTER)
        writeLine("--------------------------------")
        writeLine("Generated by Billing & Stock")
        writeLine("Shubh Muhurat")
        writeLine("")
        writeLine("")
        writeLine("")
        write(ESC_FEED_AND_CUT)
    }

    private fun generateEscPosBill(bill: Bill, settings: JewellerSettings?): ByteArray {
        val output = ByteArrayOutputStream()

        fun write(bytes: ByteArray) = output.write(bytes)
        fun writeText(text: String) = output.write(text.toByteArray(charset("ISO-8859-1")))
        fun writeLine(text: String = "") {
            writeText(text)
            output.write(0x0A)
        }

        write(ESC_INIT)
        write(ESC_ALIGN_CENTER)
        write(ESC_BOLD_ON)
        write(ESC_DOUBLE_WIDTH_HEIGHT)
        val shopName = settings?.jewellerName?.ifBlank { "JEWELLERY SHOP" } ?: "JEWELLERY SHOP"
        writeLine(shopName)

        write(ESC_NORMAL)
        write(ESC_BOLD_OFF)
        if (!settings?.address.isNullOrBlank()) {
            writeLine(settings!!.address)
        }
        if (!settings?.contactNumber.isNullOrBlank()) {
            writeLine("Ph: ${settings!!.contactNumber}")
        }

        if (bill.isGstBill && !settings?.gstNumber.isNullOrBlank()) {
            write(ESC_BOLD_ON)
            writeLine("GSTIN: ${settings!!.gstNumber}")
            write(ESC_BOLD_OFF)
        }

        writeLine("--------------------------------")
        val title = if (bill.billType == "SALE") {
            if (bill.isGstBill) "TAX INVOICE (GST SALE)" else if (bill.isCstBill) "TAX INVOICE (CST SALE)" else "RETAIL INVOICE (NON-GST)"
        } else {
            if (bill.isGstBill) "KARIGAR PURCHASE (GST)" else if (bill.isCstBill) "KARIGAR PURCHASE (CST)" else "KARIGAR PURCHASE (NON-GST)"
        }
        write(ESC_BOLD_ON)
        writeLine(title)
        write(ESC_BOLD_OFF)

        write(ESC_ALIGN_LEFT)
        writeLine("Bill No: ${bill.billNumber}")
        writeLine("Date   : ${getKolkataDateTime(bill.dateTimestamp)}")

        val partyLabel = if (bill.billType == "SALE") "Customer" else "Karigar"
        if (bill.partyName.isNotBlank()) {
            writeLine("$partyLabel: ${bill.partyName}")
        }
        if (bill.partyMobile.isNotBlank()) {
            writeLine("Mobile  : ${bill.partyMobile}")
        }

        writeLine("--------------------------------")
        write(ESC_BOLD_ON)
        writeLine(String.format(Locale.US, "%-14s %6s %10s", "Item/Purity", "Wt(g)", "Total(Rs)"))
        write(ESC_BOLD_OFF)
        writeLine("--------------------------------")

        val items = bill.parseItems()
        for (item in items) {
            val purityTag = if (item.purity.isNotBlank()) " ${item.purity}" else ""
            val namePurity = "${item.description}$purityTag".take(14)
            val wt = String.format(Locale.US, "%.3f", if (item.netWeight > 0) item.netWeight else item.grossWeight)
            val total = String.format(Locale.US, "%.2f", item.itemTotal)
            writeLine(String.format(Locale.US, "%-14s %6s %10s", namePurity, wt, total))
        }

        writeLine("--------------------------------")
        write(ESC_ALIGN_RIGHT)
        writeLine(String.format(Locale.US, "Subtotal: Rs. %.2f", bill.subtotal))

        if (bill.isGstBill) {
            val halfGst = bill.gstAmount / 2.0
            val halfPercent = bill.gstPercent / 2.0
            writeLine(String.format(Locale.US, "CGST (%.1f%%): Rs. %.2f", halfPercent, halfGst))
            writeLine(String.format(Locale.US, "SGST (%.1f%%): Rs. %.2f", halfPercent, halfGst))
        } else if (bill.isCstBill) {
            writeLine(String.format(Locale.US, "CST (%.1f%%): Rs. %.2f", bill.cstPercent, bill.cstAmount))
        }

        if (bill.discount > 0) {
            writeLine(String.format(Locale.US, "Discount: Rs. -%.2f", bill.discount))
        }

        if (bill.oldMetalExchangeAmount > 0) {
            writeLine(String.format(Locale.US, "Old Gold Exch: Rs. -%.2f", bill.oldMetalExchangeAmount))
        }

        write(ESC_BOLD_ON)
        write(ESC_DOUBLE_HEIGHT)
        writeLine(String.format(Locale.US, "Grand Total: Rs. %.2f", bill.grandTotal))
        write(ESC_NORMAL)

        val payments = bill.getEffectivePayments()
        if (payments.isNotEmpty()) {
            writeLine("--------------------------------")
            write(ESC_BOLD_ON)
            writeLine("PAYMENT DETAILS:")
            write(ESC_BOLD_OFF)
            val isSale = bill.billType == "SALE"
            for (p in payments) {
                when (p.paymentMode.uppercase()) {
                    "GOLD" -> {
                        val fine = p.calculatedFineWeight
                        val label = if (isSale) "Gold Received:" else "Gold Paid:"
                        write(ESC_BOLD_ON)
                        writeLine(label)
                        write(ESC_BOLD_OFF)
                        val touchStr = if (p.metalTouch > 0) String.format(Locale.US, "%.1f%%", p.metalTouch) else "100%"
                        writeLine(String.format(Locale.US, "  %.3fg . %s = %.3fg Fine", p.metalWeight, touchStr, fine))
                        if (p.metalRate > 0) {
                            writeLine(String.format(Locale.US, "  x Price: Rs. %.0f/g", p.metalRate))
                        }
                        writeLine(String.format(Locale.US, "  = Rs. %.2f", p.amount))
                        if (p.note.isNotBlank()) {
                            writeLine("  (${p.note})")
                        }
                    }
                    "SILVER" -> {
                        val fine = p.calculatedFineWeight
                        val label = if (isSale) "Silver Received:" else "Silver Paid:"
                        write(ESC_BOLD_ON)
                        writeLine(label)
                        write(ESC_BOLD_OFF)
                        val touchStr = if (p.metalTouch > 0) String.format(Locale.US, "%.1f%%", p.metalTouch) else "100%"
                        writeLine(String.format(Locale.US, "  %.3fg . %s = %.3fg Fine", p.metalWeight, touchStr, fine))
                        if (p.metalRate > 0) {
                            writeLine(String.format(Locale.US, "  x Price: Rs. %.0f/kg", p.metalRate))
                        }
                        writeLine(String.format(Locale.US, "  = Rs. %.2f", p.amount))
                        if (p.note.isNotBlank()) {
                            writeLine("  (${p.note})")
                        }
                    }
                    "ONLINE" -> {
                        val label = if (isSale) "Online Received:" else "Online Paid:"
                        writeLine(String.format(Locale.US, "%-17s Rs. %.2f", label, p.amount))
                        if (p.note.isNotBlank()) {
                            writeLine("  (${p.note})")
                        }
                    }
                    "CHEQUE" -> {
                        val label = if (isSale) "Cheque Received:" else "Cheque Paid:"
                        writeLine(String.format(Locale.US, "%-17s Rs. %.2f", label, p.amount))
                        if (p.note.isNotBlank()) {
                            writeLine("  (${p.note})")
                        }
                    }
                    else -> {
                        val label = if (isSale) "Cash Received:" else "Cash Paid:"
                        writeLine(String.format(Locale.US, "%-17s Rs. %.2f", label, p.amount))
                        if (p.note.isNotBlank()) {
                            writeLine("  (${p.note})")
                        }
                    }
                }
            }
            val totalPaid = payments.sumOf { it.amount }
            val totalLabel = if (isSale) "TOTAL RECEIVED:" else "TOTAL PAID:"
            writeLine("--------------------------------")
            write(ESC_BOLD_ON)
            writeLine(String.format(Locale.US, "%-17s Rs. %.2f", totalLabel, totalPaid))
            write(ESC_BOLD_OFF)
        }

        write(ESC_BOLD_ON)
        writeLine(String.format(Locale.US, "%-17s Rs. %.2f", "BALANCE DUE:", bill.netBalanceDue))
        write(ESC_BOLD_OFF)

        writeFooter(output)
        return output.toByteArray()
    }

    private fun generateEscPosGoldStatement(data: GoldStatementData, settings: JewellerSettings?): ByteArray {
        val output = ByteArrayOutputStream()
        writeHeader(output, settings, "GOLD STATEMENT (JEWELLERY + METAL)")

        fun write(bytes: ByteArray) = output.write(bytes)
        fun writeText(text: String) = output.write(text.toByteArray(charset("ISO-8859-1")))
        fun writeLine(text: String = "") {
            writeText(text)
            output.write(0x0A)
        }

        write(ESC_ALIGN_LEFT)
        writeLine(String.format(Locale.US, "Opening Balance  : %.3f g", data.openingGrams))
        writeLine(String.format(Locale.US, "Total Credit (+) : %.3f g", data.totalCreditGrams))
        writeLine(String.format(Locale.US, "Total Debit  (-) : %.3f g", data.totalDebitGrams))
        write(ESC_BOLD_ON)
        writeLine(String.format(Locale.US, "Current Balance  : %.3f g", data.currentBalanceGrams))
        write(ESC_BOLD_OFF)
        writeLine("--------------------------------")

        writeLine(String.format(Locale.US, "Purchased Weight : %.3f g", data.totalPurchaseGrams))
        writeLine(String.format(Locale.US, "Purchase Amount  : Rs. %.2f", data.totalPurchaseAmount))
        writeLine(String.format(Locale.US, "Fine Purchased   : %.3f g", data.finePurchasedGrams))
        writeLine(String.format(Locale.US, "Average Buy Rate : Rs. %.2f/g", data.averageBuyPrice))
        writeLine("--------------------------------")

        writeLine(String.format(Locale.US, "Sold Weight      : %.3f g", data.totalSaleGrams))
        writeLine(String.format(Locale.US, "Sale Amount      : Rs. %.2f", data.totalSaleAmount))
        writeLine(String.format(Locale.US, "Fine Sold (100%%) : %.3f g", data.fineSoldGrams))
        writeLine(String.format(Locale.US, "Average Sell Rate: Rs. %.2f/g", data.averageSellPrice))
        writeLine("--------------------------------")

        write(ESC_BOLD_ON)
        write(ESC_DOUBLE_HEIGHT)
        val pOrL = if (data.profitLoss >= 0) "Profit: Rs. %.2f" else "Loss: Rs. %.2f"
        writeLine(String.format(Locale.US, pOrL, data.profitLoss))
        write(ESC_NORMAL)
        write(ESC_BOLD_OFF)

        writeFooter(output)
        return output.toByteArray()
    }

    private fun generateEscPosSilverStatement(data: SilverStatementData, settings: JewellerSettings?): ByteArray {
        val output = ByteArrayOutputStream()
        writeHeader(output, settings, "SILVER STATEMENT (JEWELLERY + METAL)")

        fun write(bytes: ByteArray) = output.write(bytes)
        fun writeText(text: String) = output.write(text.toByteArray(charset("ISO-8859-1")))
        fun writeLine(text: String = "") {
            writeText(text)
            output.write(0x0A)
        }

        write(ESC_ALIGN_LEFT)
        writeLine(String.format(Locale.US, "Opening Balance  : %.3f g", data.openingGrams))
        writeLine(String.format(Locale.US, "Total Credit (+) : %.3f g", data.totalCreditGrams))
        writeLine(String.format(Locale.US, "Total Debit  (-) : %.3f g", data.totalDebitGrams))
        write(ESC_BOLD_ON)
        writeLine(String.format(Locale.US, "Current Balance  : %.3f g", data.currentBalanceGrams))
        write(ESC_BOLD_OFF)
        writeLine("--------------------------------")

        writeLine(String.format(Locale.US, "Purchased Weight : %.3f g", data.totalPurchaseGrams))
        writeLine(String.format(Locale.US, "Purchase Amount  : Rs. %.2f", data.totalPurchaseAmount))
        writeLine(String.format(Locale.US, "Fine Purchased   : %.3f g", data.finePurchasedGrams))
        writeLine(String.format(Locale.US, "Average Buy Rate : Rs. %.2f/g", data.averageBuyPrice))
        writeLine("--------------------------------")

        writeLine(String.format(Locale.US, "Sold Weight      : %.3f g", data.totalSaleGrams))
        writeLine(String.format(Locale.US, "Sale Amount      : Rs. %.2f", data.totalSaleAmount))
        writeLine(String.format(Locale.US, "Fine Sold (100%%) : %.3f g", data.fineSoldGrams))
        writeLine(String.format(Locale.US, "Average Sell Rate: Rs. %.2f/g", data.averageSellPrice))
        writeLine("--------------------------------")

        write(ESC_BOLD_ON)
        write(ESC_DOUBLE_HEIGHT)
        val pOrL = if (data.profitLoss >= 0) "Profit: Rs. %.2f" else "Loss: Rs. %.2f"
        writeLine(String.format(Locale.US, pOrL, data.profitLoss))
        write(ESC_NORMAL)
        write(ESC_BOLD_OFF)

        writeFooter(output)
        return output.toByteArray()
    }

    private fun generateEscPosCashStatement(data: CashStatementData, settings: JewellerSettings?): ByteArray {
        val output = ByteArrayOutputStream()
        writeHeader(output, settings, "CASH STATEMENT")

        fun write(bytes: ByteArray) = output.write(bytes)
        fun writeText(text: String) = output.write(text.toByteArray(charset("ISO-8859-1")))
        fun writeLine(text: String = "") {
            writeText(text)
            output.write(0x0A)
        }

        write(ESC_ALIGN_LEFT)
        writeLine(String.format(Locale.US, "Opening Cash     : Rs. %.2f", data.openingCash))
        writeLine(String.format(Locale.US, "Cash Credit (+)  : Rs. %.2f", data.cashCredit))
        writeLine(String.format(Locale.US, "Cash Debit  (-)  : Rs. %.2f", data.cashDebit))
        write(ESC_BOLD_ON)
        writeLine(String.format(Locale.US, "Current Cash     : Rs. %.2f", data.currentCash))
        write(ESC_BOLD_OFF)
        writeLine("--------------------------------")

        writeLine("CASH UTILIZATION:")
        writeLine(String.format(Locale.US, "- Gold Purchase  : Rs. %.2f", data.cashForGoldPurchase))
        writeLine(String.format(Locale.US, "- Silver Purchase: Rs. %.2f", data.cashForSilverPurchase))
        writeLine(String.format(Locale.US, "- Metal/Jewelry  : Rs. %.2f", data.cashForJewelleryMetalPurchase))
        writeLine(String.format(Locale.US, "- Own Withdrawal : Rs. %.2f", data.ownSelfWithdrawal))
        writeLine(String.format(Locale.US, "- Other Expenses : Rs. %.2f", data.otherExpenses))
        writeLine("--------------------------------")

        write(ESC_BOLD_ON)
        write(ESC_DOUBLE_HEIGHT)
        writeLine(String.format(Locale.US, "Remaining Balance: Rs. %.2f", data.remainingCashBalance))
        write(ESC_NORMAL)
        write(ESC_BOLD_OFF)

        writeFooter(output)
        return output.toByteArray()
    }

    private fun generateEscPosMonthlyStatement(data: MonthlyStatementData, settings: JewellerSettings?): ByteArray {
        val output = ByteArrayOutputStream()
        writeHeader(output, settings, "MONTHLY STATEMENT - ${data.monthLabel.uppercase()}")

        fun write(bytes: ByteArray) = output.write(bytes)
        fun writeText(text: String) = output.write(text.toByteArray(charset("ISO-8859-1")))
        fun writeLine(text: String = "") {
            writeText(text)
            output.write(0x0A)
        }

        write(ESC_ALIGN_LEFT)
        write(ESC_BOLD_ON)
        writeLine("GOLD SUMMARY:")
        write(ESC_BOLD_OFF)
        writeLine(String.format(Locale.US, "Purchased : %.3f g (Fine: %.3fg)", data.goldPurchasedGrams, data.goldPurchasedFineGrams))
        writeLine(String.format(Locale.US, "Sold      : %.3f g (Fine: %.3fg)", data.goldSoldGrams, data.goldSoldFineGrams))
        writeLine(String.format(Locale.US, "Avg Rates : Buy Rs.%.0f | Sell Rs.%.0f", data.avgGoldBuyRate, data.avgGoldSellRate))
        writeLine("--------------------------------")

        write(ESC_BOLD_ON)
        writeLine("SILVER SUMMARY:")
        write(ESC_BOLD_OFF)
        writeLine(String.format(Locale.US, "Purchased : %.3f g (Fine: %.3fg)", data.silverPurchasedGrams, data.silverPurchasedFineGrams))
        writeLine(String.format(Locale.US, "Sold      : %.3f g (Fine: %.3fg)", data.silverSoldGrams, data.silverSoldFineGrams))
        writeLine(String.format(Locale.US, "Avg Rates : Buy Rs.%.1f | Sell Rs.%.1f", data.avgSilverBuyRate, data.avgSilverSellRate))
        writeLine("--------------------------------")

        write(ESC_BOLD_ON)
        writeLine("FINANCIAL SUMMARY:")
        write(ESC_BOLD_OFF)
        writeLine(String.format(Locale.US, "Total Sales Revenue: Rs. %.2f", data.totalSales))
        writeLine(String.format(Locale.US, "Total Purchases    : Rs. %.2f", data.totalPurchases))
        writeLine(String.format(Locale.US, "Other Expenses     : Rs. %.2f", data.totalExpenses))
        writeLine(String.format(Locale.US, "Personal Drawings  : Rs. %.2f", data.ownSelfWithdrawals))
        writeLine(String.format(Locale.US, "Net Cash Flow      : Rs. %.2f", data.netCashFlow))
        writeLine("--------------------------------")

        write(ESC_BOLD_ON)
        write(ESC_DOUBLE_HEIGHT)
        writeLine(String.format(Locale.US, "Net Profit: Rs. %.2f", data.monthlyIncomeProfit))
        write(ESC_NORMAL)
        write(ESC_BOLD_OFF)

        writeFooter(output)
        return output.toByteArray()
    }

    private fun generateEscPosYearlyStatement(data: YearlyStatementData, settings: JewellerSettings?): ByteArray {
        val output = ByteArrayOutputStream()
        writeHeader(output, settings, "YEARLY STATEMENT - ${data.year}")

        fun write(bytes: ByteArray) = output.write(bytes)
        fun writeText(text: String) = output.write(text.toByteArray(charset("ISO-8859-1")))
        fun writeLine(text: String = "") {
            writeText(text)
            output.write(0x0A)
        }

        write(ESC_ALIGN_LEFT)
        write(ESC_BOLD_ON)
        writeLine(String.format(Locale.US, "%-9s %7s %7s %7s", "Month", "Sales", "Exp", "Profit"))
        write(ESC_BOLD_OFF)
        writeLine("--------------------------------")

        for (row in data.monthlySummaries) {
            val mShort = row.monthName.take(8)
            val salesK = String.format(Locale.US, "%.0f", row.salesTotal)
            val expK = String.format(Locale.US, "%.0f", row.expensesTotal)
            val profK = String.format(Locale.US, "%.0f", row.profitTotal)
            writeLine(String.format(Locale.US, "%-9s %7s %7s %7s", mShort, salesK, expK, profK))
        }

        writeLine("--------------------------------")
        write(ESC_BOLD_ON)
        writeLine(String.format(Locale.US, "Total Purchases: Rs. %.2f", data.yearlyTotalPurchases))
        writeLine(String.format(Locale.US, "Total Sales    : Rs. %.2f", data.yearlyTotalSales))
        writeLine(String.format(Locale.US, "Closing Cash   : Rs. %.2f", data.closingCashBalance))
        write(ESC_DOUBLE_HEIGHT)
        writeLine(String.format(Locale.US, "Yearly Profit  : Rs. %.2f", data.yearlyTotalProfit))
        write(ESC_NORMAL)
        write(ESC_BOLD_OFF)

        writeFooter(output)
        return output.toByteArray()
    }
}
