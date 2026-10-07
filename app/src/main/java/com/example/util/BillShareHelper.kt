package com.example.util

import android.content.Context
import android.content.Intent
import com.example.data.model.Bill
import com.example.data.model.JewellerSettings
import com.example.ui.locale.LanguageManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object BillShareHelper {
    fun generateFormattedBillText(bill: Bill, settings: JewellerSettings?): String {
        val sb = StringBuilder()
        fun line(value: String = "") { sb.append(value).append("\n") }
        val shopName = settings?.jewellerName?.ifBlank { "JEWELLERY SHOP" } ?: "JEWELLERY SHOP"
        line(shopName)
        if (!settings?.address.isNullOrBlank()) line(settings!!.address)
        if (!settings?.contactNumber.isNullOrBlank()) line("Ph: ${settings!!.contactNumber}")
        if (bill.isGstBill && !settings?.gstNumber.isNullOrBlank()) line("GSTIN: ${settings!!.gstNumber}")
        line("--------------------------------")
        val title = if (bill.billType == "SALE") {
            if (bill.isGstBill) "TAX INVOICE (GST SALE)" else if (bill.isCstBill) "TAX INVOICE (CST SALE)" else "RETAIL INVOICE (NON-GST)"
        } else {
            if (bill.isGstBill) "KARIGAR PURCHASE (GST)" else if (bill.isCstBill) "KARIGAR PURCHASE (CST)" else "KARIGAR PURCHASE (NON-GST)"
        }
        line(title)
        line("Bill No: ${bill.billNumber}")
        val sdf = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.US).apply { timeZone = TimeZone.getTimeZone("Asia/Kolkata") }
        line("Date   : ${sdf.format(Date(bill.dateTimestamp))}")
        val partyLabel = if (bill.billType == "SALE") "Customer" else "Karigar"
        if (bill.partyName.isNotBlank()) line("$partyLabel: ${bill.partyName}")
        if (bill.partyMobile.isNotBlank()) line("Mobile  : ${bill.partyMobile}")
        if (bill.partyAddress.isNotBlank()) line("Address : ${bill.partyAddress}")
        if (bill.partyAadharNumber.isNotBlank()) line("Aadhaar : ${bill.partyAadharNumber}")
        if (bill.partyPanNumber.isNotBlank()) line("PAN     : ${bill.partyPanNumber}")
        if (bill.partyGstNumber.isNotBlank()) line("GST No. : ${bill.partyGstNumber}")
        if (bill.billType == "SALE") {
            val saleItems = bill.parseItems()
            if (saleItems.any { it.metalType.equals("GOLD", true) } && !settings?.goldHsnCode.isNullOrBlank()) line("Gold HSN : ${settings!!.goldHsnCode}")
            if (saleItems.any { it.metalType.equals("SILVER", true) } && !settings?.silverHsnCode.isNullOrBlank()) line("Silver HSN: ${settings!!.silverHsnCode}")
        }
        line("--------------------------------")
        line(String.format(Locale.US, "%-13s|%6s|%11s", "Item/Purity", "Wt(g)", "Total(Rs)"))
        line("--------------------------------")
        val items = bill.parseItems()
        val goldOnly = items.isNotEmpty() && items.all { it.metalType.equals("GOLD", true) }
        val silverOnly = items.isNotEmpty() && items.all { it.metalType.equals("SILVER", true) }
        val karigarGold = bill.billType == "KARIGAR_PURCHASE" && goldOnly
        items.forEachIndexed { index, item ->
            if (karigarGold) {
                val purity = if (item.purity.isNotBlank()) "Gold / ${item.purity}" else "Gold"
                line(String.format(Locale.US, "%d | %s | %s", index + 1, item.description.take(14), purity.take(18)))
                line(String.format(Locale.US, "  Labour: %.1f%% | Gross: %.3fg | Net: %.3fg", item.makingChargePercent, item.grossWeight, item.netWeight))
                line(String.format(Locale.US, "  Fine Gold: %.3fg | Gold Rate: Rs. %.0f/g", item.totalFine, item.ratePerGram))
                line(String.format(Locale.US, "  Amount: Rs. %.2f", item.itemTotal))
            } else if (silverOnly) {
                val silverPrice = item.netWeight * (item.ratePerGram / 1000.0)
                val labour = silverPrice * item.makingChargePercent / 100.0 + item.makingCharges
                line(String.format(Locale.US, "%d | %s | Silver", index + 1, item.description.take(14)))
                line(String.format(Locale.US, "  Gross: %.3fg | Net: %.3fg | Rate: Rs. %.0f/kg", item.grossWeight, item.netWeight, item.ratePerGram))
                line(String.format(Locale.US, "  Silver Price: Rs. %.2f | Labour (AUTO): Rs. %.2f", silverPrice, labour))
                line(String.format(Locale.US, "  Total Amount: Rs. %.2f", item.itemTotal))
            } else if (goldOnly) {
                val purity = if (item.purity.isNotBlank()) "Gold / ${item.purity}" else "Gold"
                val goldAmount = item.netWeight * item.ratePerGram
                val labour = if (item.makingCharges > 0) item.makingCharges else goldAmount * item.makingChargePercent / 100.0
                line(String.format(Locale.US, "%d | %s | %s", index + 1, item.description.take(14), purity.take(18)))
                line(String.format(Locale.US, "  Gross: %.3fg | Net: %.3fg | Rate: Rs. %.0f/g", item.grossWeight, item.netWeight, item.ratePerGram))
                line(String.format(Locale.US, "  Gold Amount: Rs. %.2f | Labour: Rs. %.2f", goldAmount, labour))
                line(String.format(Locale.US, "  Total Amount: Rs. %.2f", item.itemTotal))
            } else {
                val purityTag = if (item.purity.isNotBlank()) " ${item.purity}" else ""
                val namePurity = "${item.description}${purityTag}".take(14)
                val wt = String.format(Locale.US, "%.3f", if (item.netWeight > 0) item.netWeight else item.grossWeight)
                val total = String.format(Locale.US, "%.2f", item.itemTotal)
                line(String.format(Locale.US, "%-13s|%6s|%11s", namePurity, wt, total))
            }
        }
        line("--------------------------------")
        line(String.format(Locale.US, "Subtotal: Rs. %.2f", bill.subtotal))
        if (bill.isGstBill) {
            val halfGst = bill.gstAmount / 2.0
            val halfPercent = bill.gstPercent / 2.0
            line(String.format(Locale.US, "CGST (%.1f%%): Rs. %.2f", halfPercent, halfGst))
            line(String.format(Locale.US, "SGST (%.1f%%): Rs. %.2f", halfPercent, halfGst))
        } else if (bill.isCstBill) line(String.format(Locale.US, "CST (%.1f%%): Rs. %.2f", bill.cstPercent, bill.cstAmount))
        if (bill.discount > 0) line(String.format(Locale.US, "Discount: Rs. -%.2f", bill.discount))
        if (bill.oldMetalExchangeAmount > 0) line(String.format(Locale.US, "Old Gold Exch: Rs. -%.2f", bill.oldMetalExchangeAmount))
        if (bill.otherCharges > 0 || bill.otherChargesRemark.isNotBlank()) {
            val otherLines = bill.otherChargesRemark.lines().mapNotNull { entry ->
                val parts = entry.split("|", limit = 2)
                if (parts.size == 2 && parts[0].isNotBlank()) parts[0].trim() to (parts[1].trim().toDoubleOrNull() ?: 0.0) else null
            }
            if (otherLines.isNotEmpty()) {
                otherLines.forEach { (name, amount) -> line(String.format(Locale.US, "Other: %-14s Rs. +%.2f", name.take(14), amount)) }
                line(String.format(Locale.US, "Other Charges Total: Rs. +%.2f", bill.otherCharges))
            } else line(String.format(Locale.US, "Other Charges%s: Rs. +%.2f", if (bill.otherChargesRemark.isNotBlank()) " (${bill.otherChargesRemark})" else "", bill.otherCharges))
        }
        line(String.format(Locale.US, "Grand Total: Rs. %.2f", bill.grandTotal))
        val payments = bill.getEffectivePayments()
        if (payments.isNotEmpty()) {
            line("--------------------------------")
            line("PAYMENT DETAILS:")
            val isSale = bill.billType == "SALE"
            payments.forEach { p ->
                when (p.paymentMode.uppercase()) {
                    "GOLD" -> {
                        line(if (isSale) "Gold Received:" else "Gold Paid:")
                        val touch = if (p.metalTouch > 0) String.format(Locale.US, "%.1f%%", p.metalTouch) else "100%"
                        if (!isSale) line(String.format(Locale.US, "  %.3fg . %s = %.3fg Fine", p.metalWeight, touch, p.calculatedFineWeight)) else line(String.format(Locale.US, "  %.3fg", p.metalWeight))
                        if (p.metalRate > 0) line(String.format(Locale.US, "  x Price: Rs. %.0f/g", p.metalRate))
                        line(String.format(Locale.US, "  = Rs. %.2f", p.amount))
                        if (p.note.isNotBlank()) line("  (${p.note})")
                    }
                    "SILVER" -> {
                        line(if (isSale) "Silver Received:" else "Silver Paid:")
                        val touch = if (p.metalTouch > 0) String.format(Locale.US, "%.1f%%", p.metalTouch) else "100%"
                        line(String.format(Locale.US, "  %.3fg . %s = %.3fg Fine", p.metalWeight, touch, p.calculatedFineWeight))
                        if (p.metalRate > 0) line(String.format(Locale.US, "  x Price: Rs. %.0f/kg", p.metalRate))
                        line(String.format(Locale.US, "  = Rs. %.2f", p.amount))
                        if (p.note.isNotBlank()) line("  (${p.note})")
                    }
                    "ONLINE" -> line(String.format(Locale.US, "%-17s Rs. %.2f", if (isSale) "Online Received:" else "Online Paid:", p.amount))
                    "CHEQUE" -> line(String.format(Locale.US, "%-17s Rs. %.2f", if (isSale) "Cheque Received:" else "Cheque Paid:", p.amount))
                    else -> line(String.format(Locale.US, "%-17s Rs. %.2f", if (isSale) "Cash Received:" else "Cash Paid:", p.amount))
                }
            }
            line("--------------------------------")
            line(String.format(Locale.US, "%-17s Rs. %.2f", if (isSale) "TOTAL RECEIVED:" else "TOTAL PAID:", payments.sumOf { it.amount }))
        }
        line(String.format(Locale.US, "%-17s Rs. %.2f", "BALANCE DUE:", bill.netBalanceDue))
        line("--------------------------------")
        line("Generated by Billing & Stock")
        line("Shubh Muhurat")
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