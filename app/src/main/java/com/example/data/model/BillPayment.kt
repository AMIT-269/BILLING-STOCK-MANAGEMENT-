package com.example.data.model

import com.example.ui.locale.LanguageManager

data class BillPayment(
    val id: String = java.util.UUID.randomUUID().toString(),
    val entryNumber: Int = 1,
    val dateTimestamp: Long = System.currentTimeMillis(),
    val paymentMode: String = "CASH", // "CASH", "GOLD", "SILVER", "ONLINE", "CHEQUE"
    val amount: Double = 0.0,
    val metalWeight: Double = 0.0,
    val metalTouch: Double = 0.0,
    val metalRate: Double = 0.0,
    val note: String = "",
    val fineWeight: Double = 0.0
) {
    val calculatedFineWeight: Double
        get() = if (fineWeight > 0.0) {
            fineWeight
        } else if (metalTouch > 0.0) {
            (metalWeight * metalTouch / 100.0)
        } else {
            metalWeight
        }

    fun getDisplayLabel(isSale: Boolean = true, isGu: Boolean = false): String {
        return when (paymentMode.uppercase()) {
            "GOLD" -> if (isSale) (if (isGu) "સોનું મેળવ્યું:" else "Gold Received:") else (if (isGu) "સોનું ચૂકવ્યું:" else "Gold Paid:")
            "SILVER" -> if (isSale) (if (isGu) "ચાંદી મેળવ્યું:" else "Silver Received:") else (if (isGu) "ચાંદી ચૂકવ્યું:" else "Silver Paid:")
            "ONLINE" -> if (isSale) (if (isGu) "ઓનલાઇન મેળવ્યા:" else "Online Received:") else (if (isGu) "ઓનલાઇન ચૂકવ્યા:" else "Online Paid:")
            "CHEQUE" -> if (isSale) (if (isGu) "ચેક મેળવ્યો:" else "Cheque Received:") else (if (isGu) "ચેક ચૂકવ્યો:" else "Cheque Paid:")
            else -> if (isSale) (if (isGu) "રોકડ મેળવી:" else "Cash Received:") else (if (isGu) "રોકડ ચૂકવી:" else "Cash Paid:")
        }
    }

    /**
     * Formats full mathematical breakdown exactly matching jeweler manual receipt:
     * e.g., "6.000g . 80% = 4.800 Fine × Current Price (10000) = ₹48,000"
     */
    fun getFullPaymentFormula(isGu: Boolean = false): String {
        if (!paymentMode.equals("GOLD", ignoreCase = true) && !paymentMode.equals("SILVER", ignoreCase = true)) {
            return LanguageManager.formatCurrency(amount)
        }
        val wtUnit = if (isGu) "ગ્રા" else "g"
        val rateUnit = if (paymentMode.equals("SILVER", ignoreCase = true)) (if (isGu) "/કિલો" else "/kg") else (if (isGu) "/ગ્રા" else "/g")
        val fine = calculatedFineWeight
        val touchStr = if (metalTouch > 0) {
            if (metalTouch % 1.0 == 0.0) "${metalTouch.toInt()}%" else "${LanguageManager.formatDouble(metalTouch, 1)}%"
        } else "100%"
        val fineLabel = if (isGu) "ફાઇન" else "FINE"
        val priceLabel = if (isGu) "ભાવ" else "PRICE"

        val formula = StringBuilder()
        formula.append("${LanguageManager.formatDouble(metalWeight, 3)}$wtUnit . $touchStr = ${LanguageManager.formatDouble(fine, 3)}$wtUnit $fineLabel")
        if (metalRate > 0) {
            formula.append(" × $priceLabel (${LanguageManager.formatDouble(metalRate, 0)}$rateUnit)")
        }
        formula.append(" = ${LanguageManager.formatCurrency(amount)}")
        return formula.toString()
    }

    fun getFormattedBreakdown(isGu: Boolean = false): String {
        if (!paymentMode.equals("GOLD", ignoreCase = true) && !paymentMode.equals("SILVER", ignoreCase = true)) {
            return ""
        }
        val wtUnit = if (isGu) "ગ્રા" else "g"
        val rateUnit = if (paymentMode.equals("SILVER", ignoreCase = true)) (if (isGu) "/કિલો" else "/kg") else (if (isGu) "/ગ્રા" else "/g")
        val fine = calculatedFineWeight
        val touchStr = if (metalTouch > 0) {
            if (metalTouch % 1.0 == 0.0) "${metalTouch.toInt()}%" else "${LanguageManager.formatDouble(metalTouch, 1)}%"
        } else ""
        val fineStr = "${if (isGu) "ફાઇન" else "Fine"} ${LanguageManager.formatDouble(fine, 3)}$wtUnit"
        val valStr = LanguageManager.formatCurrency(amount)

        return buildList {
            if (metalWeight > 0) add("${LanguageManager.formatDouble(metalWeight, 3)}$wtUnit")
            if (touchStr.isNotEmpty()) add(touchStr)
            add(fineStr)
            if (metalRate > 0) add("₹${LanguageManager.formatDouble(metalRate, 0)}$rateUnit")
            add(valStr)
        }.joinToString(" | ")
    }
}

