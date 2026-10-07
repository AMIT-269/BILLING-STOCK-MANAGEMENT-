package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.model.Bill
import com.example.data.model.JewellerSettings
import com.example.ui.locale.LanguageManager
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object BillShareHelper {
    /**
     * Builds the same bill line structure used by the thermal printer.
     * Share formatting is intentionally kept here so only the Share output changes.
     */
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
            if (bill.isGstBill) "TAX INVOICE (GST SALE)"
            else if (bill.isCstBill) "TAX INVOICE (CST SALE)"
            else "RETAIL INVOICE (NON-GST)"
        } else {
            if (bill.isGstBill) "KARIGAR PURCHASE (GST)"
            else if (bill.isCstBill) "KARIGAR PURCHASE (CST)"
            else "KARIGAR PURCHASE (NON-GST)"
        }
        line(title)

        line("Bill No: ${bill.billNumber}")
        val sdf = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }
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
            val hasGold = saleItems.any { it.metalType.equals("GOLD", ignoreCase = true) }
            val hasSilver = saleItems.any { it.metalType.equals("SILVER", ignoreCase = true) }
            if (hasGold && !settings?.goldHsnCode.isNullOrBlank()) {
                line("Gold HSN : ${settings!!.goldHsnCode}")
            }
            if (hasSilver && !settings?.silverHsnCode.isNullOrBlank()) {
                line("Silver HSN: ${settings!!.silverHsnCode}")
            }
        }

        line("--------------------------------")
        line(String.format(Locale.US, "%-13s|%6s|%11s", "Item/Purity", "Wt(g)", "Total(Rs)"))
        line("--------------------------------")

        val items = bill.parseItems()
        val goldOnly = items.isNotEmpty() && items.all { it.metalType.equals("GOLD", ignoreCase = true) }
        val karigarGold = bill.billType == "KARIGAR_PURCHASE" && goldOnly
        val silverOnly = items.isNotEmpty() && items.all { it.metalType.equals("SILVER", ignoreCase = true) }

        for (item in items) {
            if (karigarGold) {
                val metalPurity = if (item.purity.isNotBlank()) "Gold / ${item.purity}" else "Gold"
                line(String.format(Locale.US, "%d | %s | %s", items.indexOf(item) + 1, item.description.take(14), metalPurity.take(18)))
                line(String.format(Locale.US, "  Labour: %.1f%% | Gross: %.3fg | Net: %.3fg", item.makingChargePercent, item.grossWeight, item.netWeight))
                line(String.format(Locale.US, "  Fine Gold: %.3fg | Gold Rate: Rs. %.0f/g", item.totalFine, item.ratePerGram))
                line(String.format(Locale.US, "  Amount: Rs. %.2f", item.itemTotal))
            } else if (silverOnly) {
                val silverPrice = item.netWeight * (item.ratePerGram / 1000.0)
                val labour = silverPrice * item.makingChargePercent / 100.0 + item.makingCharges
                line(String.format(Locale.US, "%d | %s | Silver", items.indexOf(item) + 1, item.description.take(14)))
                line(String.format(Locale.US, "  Gross: %.3fg | Net: %.3fg | Rate: Rs. %.0f/kg", item.grossWeight, item.netWeight, item.ratePerGram))
                line(String.format(Locale.US, "  Silver Price: Rs. %.2f | Labour (AUTO): Rs. %.2f", silverPrice, labour))
                line(String.format(Locale.US, "  Total Amount: Rs. %.2f", item.itemTotal))
            } else if (goldOnly) {
                val metalPurity = if (item.purity.isNotBlank()) "Gold / ${item.purity}" else "Gold"
                val goldAmount = item.netWeight * item.ratePerGram
                val labour = if (item.makingCharges > 0) item.makingCharges else goldAmount * item.makingChargePercent / 100.0
                line(String.format(Locale.US, "%d | %s | %s", items.indexOf(item) + 1, item.description.take(14), metalPurity.take(18)))
                line(String.format(Locale.US, "  Gross: %.3fg | Net: %.3fg | Rate: Rs. %.0f/g", item.grossWeight, item.netWeight, item.ratePerGram))
                line(String.format(Locale.US, "  Gold Amount: Rs. %.2f | Labour: Rs. %.2f", goldAmount, labour))
                line(String.format(Locale.US, "  Total Amount: Rs. %.2f", item.itemTotal))
            } else {
                val purityTag = if (item.purity.isNotBlank()) " ${item.purity}" else ""
                val namePurity = "${item.description}$purityTag".take(14)
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
        } else if (bill.isCstBill) {
            line(String.format(Locale.US, "CST (%.1f%%): Rs. %.2f", bill.cstPercent, bill.cstAmount))
        }

        if (bill.discount > 0) {
            line(String.format(Locale.US, "Discount: Rs. -%.2f", bill.discount))
        }
        if (bill.oldMetalExchangeAmount > 0) {
            line(String.format(Locale.US, "Old Gold Exch: Rs. -%.2f", bill.oldMetalExchangeAmount))
        }

        if (bill.otherCharges > 0 || bill.otherChargesRemark.isNotBlank()) {
            val otherLines = bill.otherChargesRemark.lines().mapNotNull { entry ->
                val parts = entry.split("|", limit = 2)
                if (parts.size == 2 && parts[0].isNotBlank()) {
                    parts[0].trim() to (parts[1].trim().toDoubleOrNull() ?: 0.0)
                } else null
            }
            if (otherLines.isNotEmpty()) {
                for ((name, amount) in otherLines) {
                    line(String.format(Locale.US, "Other: %-14s Rs. +%.2f", name.take(14), amount))
                }
                line(String.format(Locale.US, "Other Charges Total: Rs. +%.2f", bill.otherCharges))
            } else {
                line(
                    if (bill.otherChargesRemark.isNotBlank())
                        String.format(Locale.US, "Other Charges (%s): Rs. +%.2f", bill.otherChargesRemark, bill.otherCharges)
                    else
                        String.format(Locale.US, "Other Charges: Rs. +%.2f", bill.otherCharges)
                )
            }
        }

        line(String.format(Locale.US, "Grand Total: Rs. %.2f", bill.grandTotal))

        val payments = bill.getEffectivePayments()
        if (payments.isNotEmpty()) {
            line("--------------------------------")
            line("PAYMENT DETAILS:")
            val isSale = bill.billType == "SALE"

            for (p in payments) {
                when (p.paymentMode.uppercase()) {
                    "GOLD" -> {
                        val fine = p.calculatedFineWeight
                        val label = if (isSale) "Gold Received:" else "Gold Paid:"
                        line(label)
                        val touchStr = if (p.metalTouch > 0) String.format(Locale.US, "%.1f%%", p.metalTouch) else "100%"
                        if (!isSale) {
                            line(String.format(Locale.US, "  %.3fg . %s = %.3fg Fine", p.metalWeight, touchStr, fine))
                        } else {
                            line(String.format(Locale.US, "  %.3fg", p.metalWeight))
                        }
                        if (p.metalRate > 0) {
                            line(String.format(Locale.US, "  x Price: Rs. %.0f/g", p.metalRate))
                        }
                        line(String.format(Locale.US, "  = Rs. %.2f", p.amount))
                        if (p.note.isNotBlank()) line("  (${p.note})")
                    }
                    "SILVER" -> {
                        val fine = p.calculatedFineWeight
                        val label = if (isSale) "Silver Received:" else "Silver Paid:"
                        line(label)
                        val touchStr = if (p.metalTouch > 0) String.format(Locale.US, "%.1f%%", p.metalTouch) else "100%"
                        line(String.format(Locale.US, "  %.3fg . %s = %.3fg Fine", p.metalWeight, touchStr, fine))
                        if (p.metalRate > 0) {
                            line(String.format(Locale.US, "  x Price: Rs. %.0f/kg", p.metalRate))
                        }
                        line(String.format(Locale.US, "  = Rs. %.2f", p.amount))
                        if (p.note.isNotBlank()) line("  (${p.note})")
                    }
                    "ONLINE" -> {
                        val label = if (isSale) "Online Received:" else "Online Paid:"
                        line(String.format(Locale.US, "%-17s Rs. %.2f", label, p.amount))
                        if (p.note.isNotBlank()) line("  (${p.note})")
                    }
                    "CHEQUE" -> {
                        val label = if (isSale) "Cheque Received:" else "Cheque Paid:"
                        line(String.format(Locale.US, "%-17s Rs. %.2f", label, p.amount))
                        if (p.note.isNotBlank()) line("  (${p.note})")
                    }
                    else -> {
                        val label = if (isSale) "Cash Received:" else "Cash Paid:"
                        line(String.format(Locale.US, "%-17s Rs. %.2f", label, p.amount))
                        if (p.note.isNotBlank()) line("  (${p.note})")
                    }
                }
            }

            val totalPaid = payments.sumOf { it.amount }
            val totalLabel = if (isSale) "TOTAL RECEIVED:" else "TOTAL PAID:"
            line("--------------------------------")
            line(String.format(Locale.US, "%-17s Rs. %.2f", totalLabel, totalPaid))
        }

        line(String.format(Locale.US, "%-17s Rs. %.2f", "BALANCE DUE:", bill.netBalanceDue))
        line("--------------------------------")
        line("Generated by Billing & Stock")
        line("Shubh Muhurat")
        line("")
        line("")
        line("")
        return sb.toString()
    }

    fun shareBillText(context: Context, bill: Bill, settings: JewellerSettings?) {
        val text = generateFormattedBillText(bill, settings)
        val lines = text.trimEnd().split("\n").flatMap { raw ->
            if (raw.isEmpty()) listOf("") else wrapForShareImage(raw, 32)
        }

        val width = 480
        val horizontalPadding = 24f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            textSize = 20f
            typeface = Typeface.MONOSPACE
        }
        val lineHeight = 29f
        val topBottom = 28f

        fun isSeparator(line: String): Boolean =
            line.isNotEmpty() && line.all { it == '-' }

        fun isCenteredLine(line: String): Boolean {
            val upper = line.uppercase(Locale.US)
            return line.length <= 30 && (
                upper.contains("TAX INVOICE") ||
                upper.contains("RETAIL INVOICE") ||
                upper.contains("KARIGAR PURCHASE") ||
                upper == "PAYMENT DETAILS:"
            )
        }

        fun isRightAlignedLine(line: String): Boolean {
            val upper = line.uppercase(Locale.US)
            return upper.startsWith("SUBTOTAL:") ||
                upper.startsWith("CGST ") ||
                upper.startsWith("SGST ") ||
                upper.startsWith("CST ") ||
                upper.startsWith("DISCOUNT:") ||
                upper.startsWith("OLD GOLD EXCH:") ||
                upper.startsWith("OTHER CHARGES") ||
                upper.startsWith("OTHER:") ||
                upper.startsWith("GRAND TOTAL:") ||
                upper.startsWith("TOTAL RECEIVED:") ||
                upper.startsWith("TOTAL PAID:") ||
                upper.startsWith("BALANCE DUE:")
        }

        val height = (topBottom * 2 + lineHeight * lines.size + 8f).toInt().coerceAtLeast(140)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)

        var y = topBottom + 20f
        for (line in lines) {
            val drawLine = line.take(32)
            if (drawLine.isBlank()) {
                y += lineHeight
                continue
            }

            when {
                isSeparator(drawLine) -> {
                    paint.textSize = 18f
                    paint.typeface = Typeface.MONOSPACE
                    canvas.drawText("--------------------------------", horizontalPadding, y, paint)
                }
                isCenteredLine(drawLine) -> {
                    paint.textSize = 20f
                    paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    val measured = paint.measureText(drawLine)
                    canvas.drawText(drawLine, (width - measured) / 2f, y, paint)
                }
                drawLine.equals(shopNameForShare(settings), ignoreCase = false) -> {
                    paint.textSize = 24f
                    paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    val measured = paint.measureText(drawLine)
                    canvas.drawText(drawLine, (width - measured) / 2f, y, paint)
                }
                isRightAlignedLine(drawLine) -> {
                    paint.textSize = 19f
                    paint.typeface = if (
                        drawLine.uppercase(Locale.US).contains("GRAND TOTAL") ||
                        drawLine.uppercase(Locale.US).contains("BALANCE DUE") ||
                        drawLine.uppercase(Locale.US).contains("TOTAL RECEIVED") ||
                        drawLine.uppercase(Locale.US).contains("TOTAL PAID")
                    ) Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) else Typeface.MONOSPACE
                    val measured = paint.measureText(drawLine)
                    canvas.drawText(drawLine, (width - horizontalPadding - measured).coerceAtLeast(horizontalPadding), y, paint)
                }
                else -> {
                    paint.textSize = 19f
                    paint.typeface = Typeface.MONOSPACE
                    canvas.drawText(drawLine, horizontalPadding, y, paint)
                }
            }
            y += lineHeight
        }

        val shareDir = File(context.cacheDir, "shared_bills")
        if (!shareDir.exists()) shareDir.mkdirs()
        val safeBillNumber = bill.billNumber.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = File(shareDir, "bill_$safeBillNumber.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()

        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Jewellery Bill ${bill.billNumber}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(
                intent,
                if (LanguageManager.isGujarati()) "બિલ શેર કરો" else "Share Bill"
            )
        )
    }

    private fun shopNameForShare(settings: JewellerSettings?): String =
        settings?.jewellerName?.ifBlank { "JEWELLERY SHOP" } ?: "JEWELLERY SHOP"

    private fun wrapForShareImage(text: String, maxChars: Int): List<String> {
        if (text.length <= maxChars) return listOf(text)
        val result = mutableListOf<String>()
        var remaining = text
        while (remaining.length > maxChars) {
            var cut = remaining.lastIndexOf(' ', maxChars)
            if (cut <= 0) cut = maxChars
            result.add(remaining.substring(0, cut))
            remaining = remaining.substring(cut).trimStart()
        }
        result.add(remaining)
        return result
    }
}
