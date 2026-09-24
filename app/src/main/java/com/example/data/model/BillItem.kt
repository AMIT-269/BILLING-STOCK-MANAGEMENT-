package com.example.data.model

data class BillItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val description: String = "",
    val metalType: String = "GOLD", // "GOLD" or "SILVER"
    val purity: String = "22K",
    val grossWeight: Double = 0.0,
    val netWeight: Double = 0.0,
    val currentTouch: Double = 85.0, // Touch %
    val makingChargePercent: Double = 0.0, // Making Charge %
    val totalTouch: Double = 85.0, // Total Touch %
    val totalFine: Double = 0.0, // Total Fine (g)
    val ratePerGram: Double = 0.0,
    val makingCharges: Double = 0.0,
    val itemTotal: Double = 0.0,
    val stockClassification: String = "JEWELLERY" // "JEWELLERY" or "METAL"
)
