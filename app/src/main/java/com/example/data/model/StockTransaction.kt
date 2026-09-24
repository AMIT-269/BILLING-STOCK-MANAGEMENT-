package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stock_transactions")
data class StockTransaction(
    @PrimaryKey
    val id: String = java.util.UUID.randomUUID().toString(),
    val accountId: String,
    val type: String, // "CREDIT" (જમા) or "DEBIT" (ઉધાર)
    val category: String, // "GOLD_JEWELLERY", "GOLD_METAL", "SILVER_JEWELLERY", "SILVER_METAL", "CASH"
    val quantityOrAmount: Double = 0.0,
    val unit: String = "g", // "g" or "₹"
    val dateTimestamp: Long = System.currentTimeMillis(),
    val remark: String = "",
    val isAutomaticFromBill: Boolean = false,
    val linkedBillId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
