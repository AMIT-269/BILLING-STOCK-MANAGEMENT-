package com.example.util

import android.content.Context
import android.content.Intent
import com.example.data.model.Bill
import com.example.data.model.JewellerSettings
import com.example.ui.locale.LanguageManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BillShareHelper {

    fun generateFormattedBillText(bill: Bill, settings: JewellerSettings?): String {
        val isGu = LanguageManager.isGujarati()
        val sb = StringBuilder()
        val shopName = settings?.jewellerName?.ifBlank {
            if (isGu) "ઝવેરી શોપ" else "JEWELLERY SHOP"
        } ?: if (isGu) "ઝવેરી શોપ" else "JEWELLERY SHOP"

        sb.append("✨ *$shopName* ✨\n")
        if (!settings?.address.isNullOrBlank()) {
            sb.append("${settings!!.address}\n")
        }
        if (!settings?.contactNumber.isNullOrBlank()) {
            sb.append("${if (isGu) "ફોન" else "Ph"}: ${settings!!.contactNumber}\n")
        }

        // GST number only if GST Bill
        if (bill.isGstBill && !settings?.gstNumber.isNullOrBlank()) {
            sb.append("GSTIN: *${settings!!.gstNumber}*\n")
        }

        sb.append("━━━━━━━━━━━━━━━━━━━━\n")
        val invoiceTitle = if (bill.billType == "SALE") {
            if (bill.isGstBill) {
                if (isGu) "ટેક્સ ઇન્વોઇસ (GST વેચાણ)" else "TAX INVOICE (GST SALE)"
            } else {
                if (isGu) "રિટેલ ઇન્વોઇસ" else "RETAIL INVOICE"
            }
        } else {
            if (bill.isGstBill) {
                if (isGu) "કારીગર ખરીદી (GST)" else "KARIGAR PURCHASE (GST)"
            } else {
                if (isGu) "કારીગર ખરીદી" else "KARIGAR PURCHASE"
            }
        }
        sb.append("📄 *$invoiceTitle*\n")
        val sdf = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.US)
        sb.append("${if (isGu) "બિલ નં." else "Bill No"}: *${bill.billNumber}*\n")
        sb.append("${if (isGu) "તારીખ" else "Date"}: ${sdf.format(Date(bill.dateTimestamp))}\n")

        val partyLabel = if (bill.billType == "SALE") {
            if (isGu) "ગ્રાહક" else "Customer"
        } else {
            if (isGu) "કારીગર" else "Karigar"
        }
        if (bill.partyName.isNotBlank()) {
            sb.append("$partyLabel: *${bill.partyName}*\n")
        }
        if (bill.partyMobile.isNotBlank()) {
            sb.append("${if (isGu) "મોબાઈલ" else "Mobile"}: ${bill.partyMobile}\n")
        }

        sb.append("━━━━━━━━━━━━━━━━━━━━\n")
        sb.append(if (isGu) "*દાગીનાની વિગત:*\n" else "*ITEMS:*\n")

        val items = bill.parseItems()
        for ((idx, item) in items.withIndex()) {
            val wt = if (item.netWeight > 0) item.netWeight else item.grossWeight
            val wtUnit = if (isGu) "ગ્રામ" else "g"
            val metalLabel = if (item.metalType == "GOLD") {
                if (isGu) "સોનું" else "Gold"
            } else {
                if (isGu) "ચાંદી" else "Silver"
            }

            sb.append("${idx + 1}. *${item.description}* ($metalLabel - ${item.purity})\n")
            val rateUnit = if (item.metalType == "SILVER") (if (isGu) "કિલો" else "kg") else wtUnit
            sb.append("   ${if (isGu) "વજન" else "Wt"}: ${LanguageManager.formatDouble(wt, 3)}$wtUnit  |  ${if (isGu) "ભાવ" else "Rate"}: ₹${LanguageManager.formatDouble(item.ratePerGram, 0)}/$rateUnit\n")
            if (item.currentTouch > 0 || item.makingChargePercent > 0) {
                sb.append("   ${if (isGu) "ટચ" else "Touch"}: ${LanguageManager.formatDouble(item.currentTouch, 1)}% + ${LanguageManager.formatDouble(item.makingChargePercent, 1)}% = ${LanguageManager.formatDouble(item.totalTouch, 1)}%  |  ${if (isGu) "શુદ્ધ" else "Fine"}: ${LanguageManager.formatDouble(item.totalFine, 3)}$wtUnit\n")
            }
            if (item.makingCharges > 0) {
                sb.append("   ${if (isGu) "મેકિંગ ચાર્જ" else "Making"}: ${LanguageManager.formatCurrency(item.makingCharges)}\n")
            }
            sb.append("   ${if (isGu) "કુલ" else "Total"}: *${LanguageManager.formatCurrency(item.itemTotal)}*\n")
        }

        sb.append("━━━━━━━━━━━━━━━━━━━━\n")
        sb.append("${if (isGu) "પેટા કુલ" else "Subtotal"}: ${LanguageManager.formatCurrency(bill.subtotal)}\n")

        if (bill.isGstBill) {
            val halfGst = bill.gstAmount / 2.0
            val halfPercent = bill.gstPercent / 2.0
            sb.append("CGST (${LanguageManager.formatDouble(halfPercent, 1)}%): ${LanguageManager.formatCurrency(halfGst)}\n")
            sb.append("SGST (${LanguageManager.formatDouble(halfPercent, 1)}%): ${LanguageManager.formatCurrency(halfGst)}\n")
        } else if (bill.isCstBill) {
            sb.append("CST (${LanguageManager.formatDouble(bill.cstPercent, 1)}%): ${LanguageManager.formatCurrency(bill.cstAmount)}\n")
        }

        if (bill.discount > 0) {
            sb.append("${if (isGu) "વળતર" else "Discount"}: -${LanguageManager.formatCurrency(bill.discount)}\n")
        }

        if (bill.oldMetalExchangeAmount > 0) {
            sb.append("${if (isGu) "જૂનું સોનું/ચાંદી જમા" else "Old Metal Exchange"}: -${LanguageManager.formatCurrency(bill.oldMetalExchangeAmount)}\n")
        }

        sb.append("━━━━━━━━━━━━━━━━━━━━\n")
        sb.append("⭐ *${if (isGu) "કુલ રકમ" else "GRAND TOTAL"}: ${LanguageManager.formatCurrency(bill.grandTotal)}* ⭐\n")

        val payments = bill.getEffectivePayments()
        if (payments.isNotEmpty()) {
            sb.append("━━━━━━━━━━━━━━━━━━━━\n")
            sb.append(if (isGu) "*ચુકવણી વિગતો (PAYMENT DETAILS):*\n\n" else "*PAYMENT DETAILS:*\n\n")
            val isSale = bill.billType == "SALE"
            for (p in payments) {
                when (p.paymentMode.uppercase()) {
                    "GOLD" -> {
                        val label = if (isSale) (if (isGu) "🟡 સોનું મેળવ્યું (Gold Received):" else "🟡 Gold Received:") else (if (isGu) "🟡 સોનું ચૂકવ્યું (Gold Paid):" else "🟡 Gold Paid:")
                        val formula = p.getFullPaymentFormula(isGu)
                        sb.append("$label\n   $formula\n")
                        if (p.note.isNotBlank()) sb.append("   _${p.note}_\n")
                        sb.append("\n")
                    }
                    "SILVER" -> {
                        val label = if (isSale) (if (isGu) "⚪ ચાંદી મેળવ્યું (Silver Received):" else "⚪ Silver Received:") else (if (isGu) "⚪ ચાંદી ચૂકવ્યું (Silver Paid):" else "⚪ Silver Paid:")
                        val formula = p.getFullPaymentFormula(isGu)
                        sb.append("$label\n   $formula\n")
                        if (p.note.isNotBlank()) sb.append("   _${p.note}_\n")
                        sb.append("\n")
                    }
                    "ONLINE" -> {
                        val label = if (isSale) (if (isGu) "💳 ઓનલાઇન મેળવ્યા (Online Received):" else "💳 Online Received:") else (if (isGu) "💳 ઓનલાઇન ચૂકવ્યા (Online Paid):" else "💳 Online Paid:")
                        sb.append("$label\n   ${LanguageManager.formatCurrency(p.amount)}\n")
                        if (p.note.isNotBlank()) sb.append("   _${p.note}_\n")
                        sb.append("\n")
                    }
                    "CHEQUE" -> {
                        val label = if (isSale) (if (isGu) "📝 ચેક મેળવ્યો (Cheque Received):" else "📝 Cheque Received:") else (if (isGu) "📝 ચેક ચૂકવ્યો (Cheque Paid):" else "📝 Cheque Paid:")
                        sb.append("$label\n   ${LanguageManager.formatCurrency(p.amount)}\n")
                        if (p.note.isNotBlank()) sb.append("   _${p.note}_\n")
                        sb.append("\n")
                    }
                    else -> { // CASH
                        val label = if (isSale) (if (isGu) "💵 રોકડ મેળવી (Cash Received):" else "💵 Cash Received:") else (if (isGu) "💵 રોકડ ચૂકવી (Cash Paid):" else "💵 Cash Paid:")
                        sb.append("$label\n   ${LanguageManager.formatCurrency(p.amount)}\n")
                        if (p.note.isNotBlank()) sb.append("   _${p.note}_\n")
                        sb.append("\n")
                    }
                }
            }
            val totalPaid = payments.sumOf { it.amount }
            val totalLabel = if (isSale) (if (isGu) "કુલ મેળવેલ (Total Received)" else "Total Received") else (if (isGu) "કુલ ચૂકવેલ (Total Paid)" else "Total Paid")
            sb.append("$totalLabel:\n*${LanguageManager.formatCurrency(totalPaid)}*\n\n")
            val balanceLabel = if (isGu) "બાકી રકમ (Balance)" else "Balance"
            sb.append("$balanceLabel:\n*${LanguageManager.formatCurrency(bill.netBalanceDue)}*\n")
        } else if (bill.netBalanceDue > 0) {
            val balanceLabel = if (isGu) "બાકી રકમ (Balance)" else "Balance"
            sb.append("$balanceLabel:\n*${LanguageManager.formatCurrency(bill.netBalanceDue)}*\n")
        }

        sb.append(if (isGu) "\n_આપની ખરીદી બદલ આભાર! ફરી પધારશો._\n" else "\n_Thank you for your business! Visit again._\n")
        return sb.toString()
    }

    fun shareBillText(context: Context, bill: Bill, settings: JewellerSettings?) {
        val text = generateFormattedBillText(bill, settings)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Jewellery Bill ${bill.billNumber}")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, if (LanguageManager.isGujarati()) "બિલ શેર કરો" else "Share Bill"))
    }
}
