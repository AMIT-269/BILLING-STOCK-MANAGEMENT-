package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "bills")
data class Bill(
    @PrimaryKey
    val id: String = java.util.UUID.randomUUID().toString(),
    val accountId: String,
    val billNumber: String,
    val billType: String = "SALE", // "SALE" or "KARIGAR_PURCHASE"
    val isGstBill: Boolean = false,
    val gstPercent: Double = 1.5,
    val gstAmount: Double = 0.0,
    val isCstBill: Boolean = false,
    val cstPercent: Double = 1.5,
    val cstAmount: Double = 0.0,
    val partyName: String = "",
    val partyMobile: String = "",
    val partyAddress: String = "",
    val paymentMode: String = "CASH", // "CASH", "GOLD", "SILVER", "ONLINE", "CHEQUE"
    val dateTimestamp: Long = System.currentTimeMillis(),
    val itemsJson: String = "[]",
    val subtotal: Double = 0.0,
    val discount: Double = 0.0,
    val grandTotal: Double = 0.0,
    val cashReceivedOrPaid: Double = 0.0,
    val oldMetalExchangeAmount: Double = 0.0,
    val netBalanceDue: Double = 0.0,
    val notes: String = "",
    val paymentsJson: String = "[]",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun parseItems(): List<BillItem> {
        val list = mutableListOf<BillItem>()
        try {
            val arr = JSONArray(itemsJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val gross = obj.optDouble("grossWeight", 0.0)
                val net = obj.optDouble("netWeight", gross)
                val weight = if (net > 0) net else gross
                val currentTouch = obj.optDouble("currentTouch", 85.0)
                val makingPct = obj.optDouble("makingChargePercent", 0.0)
                val totalTouch = obj.optDouble("totalTouch", currentTouch + makingPct)
                val totalFine = obj.optDouble("totalFine", weight * totalTouch / 100.0)
                val stockClass = obj.optString("stockClassification", "JEWELLERY")
                list.add(
                    BillItem(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        description = obj.optString("description", ""),
                        metalType = obj.optString("metalType", "GOLD"),
                        purity = obj.optString("purity", "22K"),
                        grossWeight = gross,
                        netWeight = net,
                        currentTouch = currentTouch,
                        makingChargePercent = makingPct,
                        totalTouch = totalTouch,
                        totalFine = totalFine,
                        ratePerGram = obj.optDouble("ratePerGram", 0.0),
                        makingCharges = obj.optDouble("makingCharges", 0.0),
                        itemTotal = obj.optDouble("itemTotal", 0.0),
                        stockClassification = stockClass
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    fun parsePayments(): List<BillPayment> {
        val list = mutableListOf<BillPayment>()
        try {
            val arr = JSONArray(paymentsJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    BillPayment(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        entryNumber = obj.optInt("entryNumber", i + 1),
                        dateTimestamp = obj.optLong("dateTimestamp", System.currentTimeMillis()),
                        paymentMode = obj.optString("paymentMode", "CASH"),
                        amount = obj.optDouble("amount", 0.0),
                        metalWeight = obj.optDouble("metalWeight", 0.0),
                        metalTouch = obj.optDouble("metalTouch", 0.0),
                        metalRate = obj.optDouble("metalRate", 0.0),
                        note = obj.optString("note", ""),
                        fineWeight = obj.optDouble("fineWeight", 0.0)
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    fun getEffectivePayments(): List<BillPayment> {
        val parsed = parsePayments()
        if (parsed.isNotEmpty()) return parsed
        if (cashReceivedOrPaid > 0) {
            return listOf(
                BillPayment(
                    id = "legacy_${id}",
                    entryNumber = 1,
                    dateTimestamp = dateTimestamp,
                    paymentMode = paymentMode,
                    amount = cashReceivedOrPaid,
                    note = if (billType == "SALE") "Payment Received" else "Payment Made"
                )
            )
        }
        return emptyList()
    }

    companion object {
        fun itemsToJson(items: List<BillItem>): String {
            val arr = JSONArray()
            for (item in items) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("description", item.description)
                    put("metalType", item.metalType)
                    put("purity", item.purity)
                    put("grossWeight", item.grossWeight)
                    put("netWeight", item.netWeight)
                    put("currentTouch", item.currentTouch)
                    put("makingChargePercent", item.makingChargePercent)
                    put("totalTouch", item.totalTouch)
                    put("totalFine", item.totalFine)
                    put("ratePerGram", item.ratePerGram)
                    put("makingCharges", item.makingCharges)
                    put("itemTotal", item.itemTotal)
                    put("stockClassification", item.stockClassification)
                }
                arr.put(obj)
            }
            return arr.toString()
        }

        fun paymentsToJson(payments: List<BillPayment>): String {
            val arr = JSONArray()
            for (payment in payments) {
                val obj = JSONObject().apply {
                    put("id", payment.id)
                    put("entryNumber", payment.entryNumber)
                    put("dateTimestamp", payment.dateTimestamp)
                    put("paymentMode", payment.paymentMode)
                    put("amount", payment.amount)
                    put("metalWeight", payment.metalWeight)
                    put("metalTouch", payment.metalTouch)
                    put("metalRate", payment.metalRate)
                    put("note", payment.note)
                    put("fineWeight", payment.calculatedFineWeight)
                }
                arr.put(obj)
            }
            return arr.toString()
        }
    }
}
