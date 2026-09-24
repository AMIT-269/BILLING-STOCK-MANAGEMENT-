package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "jeweller_settings")
data class JewellerSettings(
    @PrimaryKey
    val accountId: String,
    val jewellerName: String = "",
    val address: String = "",
    val gstNumber: String = "",
    val logoBase64: String? = null,
    val contactNumber: String = "",
    val goldRate22k: Double = 0.0,
    val silverRate: Double = 0.0,
    val language: String = "en",
    val updatedAt: Long = System.currentTimeMillis()
)
