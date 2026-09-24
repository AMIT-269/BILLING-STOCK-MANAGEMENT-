package com.example.data.model

data class GoldStatementData(
    val openingGrams: Double = 0.0,
    val totalCreditGrams: Double = 0.0,
    val totalDebitGrams: Double = 0.0,
    val currentBalanceGrams: Double = 0.0,
    val totalPurchaseGrams: Double = 0.0,
    val totalPurchaseAmount: Double = 0.0,
    val totalSaleGrams: Double = 0.0,
    val totalSaleAmount: Double = 0.0,
    val finePurchasedGrams: Double = 0.0,
    val fineSoldGrams: Double = 0.0,
    val netFineGrams: Double = 0.0,
    val averageBuyPrice: Double = 0.0,
    val averageSellPrice: Double = 0.0,
    val profitLoss: Double = 0.0,
    val transactions: List<StockTransaction> = emptyList()
)

data class SilverStatementData(
    val openingGrams: Double = 0.0,
    val totalCreditGrams: Double = 0.0,
    val totalDebitGrams: Double = 0.0,
    val currentBalanceGrams: Double = 0.0,
    val totalPurchaseGrams: Double = 0.0,
    val totalPurchaseAmount: Double = 0.0,
    val totalSaleGrams: Double = 0.0,
    val totalSaleAmount: Double = 0.0,
    val finePurchasedGrams: Double = 0.0,
    val fineSoldGrams: Double = 0.0,
    val netFineGrams: Double = 0.0,
    val averageBuyPrice: Double = 0.0,
    val averageSellPrice: Double = 0.0,
    val profitLoss: Double = 0.0,
    val transactions: List<StockTransaction> = emptyList()
)

data class CashStatementData(
    val openingCash: Double = 0.0,
    val cashCredit: Double = 0.0,
    val cashDebit: Double = 0.0,
    val currentCash: Double = 0.0,
    val cashForGoldPurchase: Double = 0.0,
    val cashForSilverPurchase: Double = 0.0,
    val cashForJewelleryMetalPurchase: Double = 0.0,
    val cashFromGoldSale: Double = 0.0,
    val cashFromSilverSale: Double = 0.0,
    val ownSelfWithdrawal: Double = 0.0,
    val otherExpenses: Double = 0.0,
    val remainingCashBalance: Double = 0.0,
    val overallProfitLoss: Double = 0.0,
    val transactions: List<StockTransaction> = emptyList()
)

data class MonthlyStatementData(
    val year: Int,
    val month: Int, // 1 - 12
    val monthLabel: String,
    val goldOpeningGrams: Double = 0.0,
    val goldCreditGrams: Double = 0.0,
    val goldDebitGrams: Double = 0.0,
    val goldNetBalanceGrams: Double = 0.0,
    val goldPurchasedGrams: Double = 0.0,
    val goldPurchasedFineGrams: Double = 0.0,
    val goldSoldGrams: Double = 0.0,
    val goldSoldFineGrams: Double = 0.0,
    val goldNetGrams: Double = 0.0,
    val goldProfitLoss: Double = 0.0,
    val silverOpeningGrams: Double = 0.0,
    val silverCreditGrams: Double = 0.0,
    val silverDebitGrams: Double = 0.0,
    val silverNetBalanceGrams: Double = 0.0,
    val silverPurchasedGrams: Double = 0.0,
    val silverPurchasedFineGrams: Double = 0.0,
    val silverSoldGrams: Double = 0.0,
    val silverSoldFineGrams: Double = 0.0,
    val silverNetGrams: Double = 0.0,
    val silverProfitLoss: Double = 0.0,
    val cashOpening: Double = 0.0,
    val cashCredit: Double = 0.0,
    val cashDebit: Double = 0.0,
    val cashNetBalance: Double = 0.0,
    val totalPurchases: Double = 0.0,
    val totalSales: Double = 0.0,
    val cashReceived: Double = 0.0,
    val cashPaid: Double = 0.0,
    val netCashFlow: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val ownSelfWithdrawals: Double = 0.0,
    val avgGoldBuyRate: Double = 0.0,
    val avgGoldSellRate: Double = 0.0,
    val avgSilverBuyRate: Double = 0.0,
    val avgSilverSellRate: Double = 0.0,
    val monthlyIncomeProfit: Double = 0.0,
    val bills: List<Bill> = emptyList()
)

data class MonthSummaryRow(
    val month: Int,
    val monthName: String,
    val goldTotalGrams: Double = 0.0,
    val silverTotalGrams: Double = 0.0,
    val cashTotalAmount: Double = 0.0,
    val expensesTotal: Double = 0.0,
    val profitTotal: Double = 0.0,
    val purchasesTotal: Double = 0.0,
    val salesTotal: Double = 0.0
)

data class YearlyStatementData(
    val year: Int,
    val monthlySummaries: List<MonthSummaryRow> = emptyList(),
    val yearlyTotalProfit: Double = 0.0,
    val yearlyTotalPurchases: Double = 0.0,
    val yearlyTotalSales: Double = 0.0,
    val closingCashBalance: Double = 0.0,
    val goldOpeningGrams: Double = 0.0,
    val goldCreditGrams: Double = 0.0,
    val goldDebitGrams: Double = 0.0,
    val goldNetBalanceGrams: Double = 0.0,
    val silverOpeningGrams: Double = 0.0,
    val silverCreditGrams: Double = 0.0,
    val silverDebitGrams: Double = 0.0,
    val silverNetBalanceGrams: Double = 0.0,
    val cashOpening: Double = 0.0,
    val cashCredit: Double = 0.0,
    val cashDebit: Double = 0.0,
    val cashNetBalance: Double = 0.0
)
