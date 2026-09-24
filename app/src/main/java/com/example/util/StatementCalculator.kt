package com.example.util

import com.example.data.model.*
import java.text.SimpleDateFormat
import java.util.*

object StatementCalculator {

    val kolkataTimeZone: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")

    /**
     * Gold Statement:
     * - Combine gold jewellery and gold metal into one total.
     * - Show total gold credit, debit, purchase, sale, fine 100%, average buy price, average sell price,
     *   profit/loss, opening balance and current balance.
     * - Calculate different purity/touch values correctly using fine 100%.
     */
    fun calculateGoldStatement(
        bills: List<Bill>,
        transactions: List<StockTransaction>,
        settings: JewellerSettings?
    ): GoldStatementData {
        val goldTx = transactions.filter {
            it.category == "GOLD_JEWELLERY" || it.category == "GOLD_METAL"
        }

        val openingTxList = goldTx.filter {
            it.remark.contains("Opening Stock", ignoreCase = true) || it.remark.contains("શરૂઆત")
        }
        val openingGrams = openingTxList.sumOf { it.quantityOrAmount }

        val creditGrams = goldTx.filter {
            it.type == "CREDIT" && it !in openingTxList
        }.sumOf { it.quantityOrAmount }

        val debitGrams = goldTx.filter {
            it.type == "DEBIT"
        }.sumOf { it.quantityOrAmount }

        val currentBalance = openingGrams + creditGrams - debitGrams

        var totalPurchaseGrams = 0.0
        var totalPurchaseAmount = 0.0
        var finePurchasedGrams = 0.0

        var totalSaleGrams = 0.0
        var totalSaleAmount = 0.0
        var fineSoldGrams = 0.0

        for (bill in bills) {
            val items = bill.parseItems()
            for (item in items) {
                if (item.metalType.equals("GOLD", ignoreCase = true)) {
                    val wt = if (item.netWeight > 0) item.netWeight else item.grossWeight
                    val touch = if (item.currentTouch > 0) item.currentTouch else 85.0
                    val fine = if (item.totalFine > 0) item.totalFine else (wt * touch / 100.0)

                    if (bill.billType == "KARIGAR_PURCHASE") {
                        totalPurchaseGrams += wt
                        finePurchasedGrams += fine
                        totalPurchaseAmount += item.itemTotal
                    } else if (bill.billType == "SALE") {
                        totalSaleGrams += wt
                        fineSoldGrams += fine
                        totalSaleAmount += item.itemTotal
                    }
                }
            }
        }

        // Also add manual Cash -> Metal Purchases recorded in StockTransactions
        for (tx in transactions) {
            if (tx.category == "GOLD_METAL" && tx.type == "CREDIT" && !tx.isAutomaticFromBill && tx !in openingTxList) {
                if (tx.remark.contains("Cash → Metal", ignoreCase = true) || tx.remark.contains("ખરીદી")) {
                    totalPurchaseGrams += tx.quantityOrAmount
                    val estFine = tx.quantityOrAmount * 0.999 // 24K metal purchase
                    finePurchasedGrams += estFine
                }
            }
        }

        val fallbackBuyRate = settings?.goldRate22k ?: 7200.0
        val avgBuyPrice = if (totalPurchaseGrams > 0) {
            totalPurchaseAmount / totalPurchaseGrams
        } else {
            fallbackBuyRate
        }

        val avgSellPrice = if (totalSaleGrams > 0) {
            totalSaleAmount / totalSaleGrams
        } else {
            0.0
        }

        // Profit/Loss: Revenue - Cost of Goods Sold (using fine 100%)
        val costOfGoldSold = if (finePurchasedGrams > 0 && totalPurchaseAmount > 0) {
            val costPerFineGram = totalPurchaseAmount / finePurchasedGrams
            fineSoldGrams * costPerFineGram
        } else {
            fineSoldGrams * (fallbackBuyRate / 0.916)
        }
        val profitLoss = if (totalSaleAmount > 0) (totalSaleAmount - costOfGoldSold) else 0.0

        return GoldStatementData(
            openingGrams = openingGrams,
            totalCreditGrams = creditGrams,
            totalDebitGrams = debitGrams,
            currentBalanceGrams = currentBalance,
            totalPurchaseGrams = totalPurchaseGrams,
            totalPurchaseAmount = totalPurchaseAmount,
            totalSaleGrams = totalSaleGrams,
            totalSaleAmount = totalSaleAmount,
            finePurchasedGrams = finePurchasedGrams,
            fineSoldGrams = fineSoldGrams,
            netFineGrams = finePurchasedGrams - fineSoldGrams,
            averageBuyPrice = avgBuyPrice,
            averageSellPrice = avgSellPrice,
            profitLoss = profitLoss,
            transactions = goldTx.sortedByDescending { it.dateTimestamp }
        )
    }

    /**
     * Silver Statement:
     * - Combine silver jewellery and silver metal into one total.
     * - Show total silver credit, debit, purchase, sale, fine 100%, average buy price, average sell price,
     *   profit/loss, opening balance and current balance.
     */
    fun calculateSilverStatement(
        bills: List<Bill>,
        transactions: List<StockTransaction>,
        settings: JewellerSettings?
    ): SilverStatementData {
        val silverTx = transactions.filter {
            it.category == "SILVER_JEWELLERY" || it.category == "SILVER_METAL"
        }

        val openingTxList = silverTx.filter {
            it.remark.contains("Opening Stock", ignoreCase = true) || it.remark.contains("શરૂઆત")
        }
        val openingGrams = openingTxList.sumOf { it.quantityOrAmount }

        val creditGrams = silverTx.filter {
            it.type == "CREDIT" && it !in openingTxList
        }.sumOf { it.quantityOrAmount }

        val debitGrams = silverTx.filter {
            it.type == "DEBIT"
        }.sumOf { it.quantityOrAmount }

        val currentBalance = openingGrams + creditGrams - debitGrams

        var totalPurchaseGrams = 0.0
        var totalPurchaseAmount = 0.0
        var finePurchasedGrams = 0.0

        var totalSaleGrams = 0.0
        var totalSaleAmount = 0.0
        var fineSoldGrams = 0.0

        for (bill in bills) {
            val items = bill.parseItems()
            for (item in items) {
                if (item.metalType.equals("SILVER", ignoreCase = true)) {
                    val wt = if (item.netWeight > 0) item.netWeight else item.grossWeight
                    val touch = if (item.currentTouch > 0) item.currentTouch else 70.0
                    val fine = if (item.totalFine > 0) item.totalFine else (wt * touch / 100.0)

                    if (bill.billType == "KARIGAR_PURCHASE") {
                        totalPurchaseGrams += wt
                        finePurchasedGrams += fine
                        totalPurchaseAmount += item.itemTotal
                    } else if (bill.billType == "SALE") {
                        totalSaleGrams += wt
                        fineSoldGrams += fine
                        totalSaleAmount += item.itemTotal
                    }
                }
            }
        }

        for (tx in transactions) {
            if (tx.category == "SILVER_METAL" && tx.type == "CREDIT" && !tx.isAutomaticFromBill && tx !in openingTxList) {
                if (tx.remark.contains("Cash → Metal", ignoreCase = true) || tx.remark.contains("ખરીદી")) {
                    totalPurchaseGrams += tx.quantityOrAmount
                    val estFine = tx.quantityOrAmount * 0.999
                    finePurchasedGrams += estFine
                }
            }
        }

        val fallbackBuyRate = settings?.silverRate ?: 88.0
        val avgBuyPrice = if (totalPurchaseGrams > 0) {
            totalPurchaseAmount / totalPurchaseGrams
        } else {
            fallbackBuyRate
        }

        val avgSellPrice = if (totalSaleGrams > 0) {
            totalSaleAmount / totalSaleGrams
        } else {
            0.0
        }

        val costOfSilverSold = if (finePurchasedGrams > 0 && totalPurchaseAmount > 0) {
            val costPerFineGram = totalPurchaseAmount / finePurchasedGrams
            fineSoldGrams * costPerFineGram
        } else {
            fineSoldGrams * (fallbackBuyRate / 0.70)
        }
        val profitLoss = if (totalSaleAmount > 0) (totalSaleAmount - costOfSilverSold) else 0.0

        return SilverStatementData(
            openingGrams = openingGrams,
            totalCreditGrams = creditGrams,
            totalDebitGrams = debitGrams,
            currentBalanceGrams = currentBalance,
            totalPurchaseGrams = totalPurchaseGrams,
            totalPurchaseAmount = totalPurchaseAmount,
            totalSaleGrams = totalSaleGrams,
            totalSaleAmount = totalSaleAmount,
            finePurchasedGrams = finePurchasedGrams,
            fineSoldGrams = fineSoldGrams,
            netFineGrams = finePurchasedGrams - fineSoldGrams,
            averageBuyPrice = avgBuyPrice,
            averageSellPrice = avgSellPrice,
            profitLoss = profitLoss,
            transactions = silverTx.sortedByDescending { it.dateTimestamp }
        )
    }

    /**
     * Cash Statement:
     * - show opening cash, cash credit, cash debit and current cash.
     * - show cash used for gold purchase, silver purchase, jewellery/metal purchase,
     *   own self withdrawal and other expenses.
     * - show remaining cash balance.
     */
    fun calculateCashStatement(
        bills: List<Bill>,
        transactions: List<StockTransaction>
    ): CashStatementData {
        val cashTx = transactions.filter { it.category == "CASH" }

        val openingTxList = cashTx.filter {
            it.remark.contains("Opening", ignoreCase = true) || it.remark.contains("શરૂઆત")
        }
        val openingCash = openingTxList.sumOf { it.quantityOrAmount }

        val creditCash = cashTx.filter {
            it.type == "CREDIT" && it !in openingTxList
        }.sumOf { it.quantityOrAmount }

        val debitCash = cashTx.filter {
            it.type == "DEBIT"
        }.sumOf { it.quantityOrAmount }

        val currentCash = openingCash + creditCash - debitCash

        var cashForGold = 0.0
        var cashForSilver = 0.0
        var cashFromGold = 0.0
        var cashFromSilver = 0.0
        var selfWithdrawal = 0.0
        var otherExpenses = 0.0

        // Analyze bills for cash used in purchases and cash received from sales
        for (bill in bills) {
            val items = bill.parseItems()
            val payments = bill.getEffectivePayments()
            val goldItemTotal = items.filter { it.metalType.equals("GOLD", ignoreCase = true) }.sumOf { it.itemTotal }
            val silverItemTotal = items.filter { it.metalType.equals("SILVER", ignoreCase = true) }.sumOf { it.itemTotal }
            val total = goldItemTotal + silverItemTotal

            if (bill.billType == "KARIGAR_PURCHASE") {
                val cashPaidOnBill = if (payments.isNotEmpty()) {
                    payments.filter { it.paymentMode in listOf("CASH", "ONLINE", "CHEQUE") }.sumOf { it.amount }
                } else if (bill.paymentMode in listOf("CASH", "ONLINE", "CHEQUE") || bill.paymentMode.isBlank()) {
                    bill.cashReceivedOrPaid
                } else {
                    0.0
                }

                if (cashPaidOnBill > 0) {
                    if (total > 0) {
                        cashForGold += cashPaidOnBill * (goldItemTotal / total)
                        cashForSilver += cashPaidOnBill * (silverItemTotal / total)
                    } else {
                        cashForGold += cashPaidOnBill
                    }
                }
            } else if (bill.billType == "SALE") {
                val cashReceivedOnBill = if (payments.isNotEmpty()) {
                    payments.filter { it.paymentMode in listOf("CASH", "ONLINE", "CHEQUE") }.sumOf { it.amount }
                } else if (bill.paymentMode in listOf("CASH", "ONLINE", "CHEQUE") || bill.paymentMode.isBlank()) {
                    bill.cashReceivedOrPaid
                } else {
                    0.0
                }

                if (cashReceivedOnBill > 0) {
                    if (total > 0) {
                        cashFromGold += cashReceivedOnBill * (goldItemTotal / total)
                        cashFromSilver += cashReceivedOnBill * (silverItemTotal / total)
                    } else {
                        cashFromGold += cashReceivedOnBill
                    }
                }
            }
        }

        // Analyze cash transactions for self withdrawal, manual purchases, expenses
        for (tx in cashTx) {
            if (tx.type == "DEBIT") {
                val rem = tx.remark.lowercase()
                val isWithdrawal = rem.contains("withdrawal") || rem.contains("personal") ||
                        rem.contains("self") || rem.contains("drawing") || rem.contains("ઉપાડ") || rem.contains("અંગત")
                val isGoldPurchase = rem.contains("gold") || rem.contains("સોનું") || rem.contains("સોના")
                val isSilverPurchase = rem.contains("silver") || rem.contains("ચાંદી")

                if (isWithdrawal) {
                    selfWithdrawal += tx.quantityOrAmount
                } else if (isGoldPurchase && !tx.isAutomaticFromBill) {
                    cashForGold += tx.quantityOrAmount
                } else if (isSilverPurchase && !tx.isAutomaticFromBill) {
                    cashForSilver += tx.quantityOrAmount
                } else if (!tx.isAutomaticFromBill) {
                    // General expenses like rent, tea, electricity, salary, etc.
                    otherExpenses += tx.quantityOrAmount
                }
            } else if (tx.type == "CREDIT" && !tx.isAutomaticFromBill && tx !in openingTxList) {
                val rem = tx.remark.lowercase()
                if (rem.contains("gold") || rem.contains("સોનું")) {
                    cashFromGold += tx.quantityOrAmount
                } else if (rem.contains("silver") || rem.contains("ચાંદી")) {
                    cashFromSilver += tx.quantityOrAmount
                }
            }
        }

        val overallProfitLoss = (cashFromGold + cashFromSilver) - (cashForGold + cashForSilver) - otherExpenses

        return CashStatementData(
            openingCash = openingCash,
            cashCredit = creditCash,
            cashDebit = debitCash,
            currentCash = currentCash,
            cashForGoldPurchase = cashForGold,
            cashForSilverPurchase = cashForSilver,
            cashForJewelleryMetalPurchase = cashForGold + cashForSilver,
            cashFromGoldSale = cashFromGold,
            cashFromSilverSale = cashFromSilver,
            ownSelfWithdrawal = selfWithdrawal,
            otherExpenses = otherExpenses,
            remainingCashBalance = currentCash,
            overallProfitLoss = overallProfitLoss,
            transactions = cashTx.sortedByDescending { it.dateTimestamp }
        )
    }

    /**
     * Monthly Statement:
     * - allow selecting any month, including previous months.
     * - show total gold, silver and cash activity, purchases, sales, expenses, personal withdrawals,
     *   average prices and monthly income/profit.
     * - calculate profit using fine 100% and actual transaction rates.
     * - include making-charge and non-making-charge transactions correctly.
     */
    fun calculateMonthlyStatement(
        bills: List<Bill>,
        transactions: List<StockTransaction>,
        settings: JewellerSettings?,
        year: Int,
        month: Int // 1 - 12
    ): MonthlyStatementData {
        val calStart = Calendar.getInstance(kolkataTimeZone).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val calEnd = Calendar.getInstance(kolkataTimeZone).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }

        val startTs = calStart.timeInMillis
        val endTs = calEnd.timeInMillis

        val monthBills = bills.filter { it.dateTimestamp in startTs..endTs }
        val monthTx = transactions.filter { it.dateTimestamp in startTs..endTs }

        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.US)
        monthFormat.timeZone = kolkataTimeZone
        val monthLabel = monthFormat.format(calStart.time)

        var goldPurchasedGrams = 0.0
        var goldPurchasedFine = 0.0
        var goldPurchasedAmt = 0.0

        var goldSoldGrams = 0.0
        var goldSoldFine = 0.0
        var goldSoldAmt = 0.0

        var silverPurchasedGrams = 0.0
        var silverPurchasedFine = 0.0
        var silverPurchasedAmt = 0.0

        var silverSoldGrams = 0.0
        var silverSoldFine = 0.0
        var silverSoldAmt = 0.0

        var totalSales = 0.0
        var totalPurchases = 0.0
        var makingChargesTotal = 0.0

        for (bill in monthBills) {
            val items = bill.parseItems()
            if (bill.billType == "SALE") {
                totalSales += bill.grandTotal
                for (item in items) {
                    val wt = if (item.netWeight > 0) item.netWeight else item.grossWeight
                    val touch = if (item.currentTouch > 0) item.currentTouch else (if (item.metalType == "GOLD") 85.0 else 70.0)
                    val fine = if (item.totalFine > 0) item.totalFine else (wt * touch / 100.0)
                    makingChargesTotal += item.makingCharges

                    if (item.metalType.equals("GOLD", ignoreCase = true)) {
                        goldSoldGrams += wt
                        goldSoldFine += fine
                        goldSoldAmt += item.itemTotal
                    } else {
                        silverSoldGrams += wt
                        silverSoldFine += fine
                        silverSoldAmt += item.itemTotal
                    }
                }
            } else if (bill.billType == "KARIGAR_PURCHASE") {
                totalPurchases += bill.grandTotal
                for (item in items) {
                    val wt = if (item.netWeight > 0) item.netWeight else item.grossWeight
                    val touch = if (item.currentTouch > 0) item.currentTouch else (if (item.metalType == "GOLD") 85.0 else 70.0)
                    val fine = if (item.totalFine > 0) item.totalFine else (wt * touch / 100.0)

                    if (item.metalType.equals("GOLD", ignoreCase = true)) {
                        goldPurchasedGrams += wt
                        goldPurchasedFine += fine
                        goldPurchasedAmt += item.itemTotal
                    } else {
                        silverPurchasedGrams += wt
                        silverPurchasedFine += fine
                        silverPurchasedAmt += item.itemTotal
                    }
                }
            }
        }

        // Cash activity in this month
        var cashReceived = 0.0
        var cashPaid = 0.0
        var totalExpenses = 0.0
        var ownWithdrawals = 0.0

        val cashTx = monthTx.filter { it.category == "CASH" }
        for (tx in cashTx) {
            if (tx.type == "CREDIT") {
                cashReceived += tx.quantityOrAmount
            } else if (tx.type == "DEBIT") {
                cashPaid += tx.quantityOrAmount
                val rem = tx.remark.lowercase()
                val isWithdrawal = rem.contains("withdrawal") || rem.contains("personal") ||
                        rem.contains("self") || rem.contains("drawing") || rem.contains("ઉપાડ") || rem.contains("અંગત")
                if (isWithdrawal) {
                    ownWithdrawals += tx.quantityOrAmount
                } else if (!tx.isAutomaticFromBill && !rem.contains("gold") && !rem.contains("silver") && !rem.contains("ધાતુ")) {
                    totalExpenses += tx.quantityOrAmount
                }
            }
        }

        val fallbackGoldBuy = settings?.goldRate22k ?: 7200.0
        val fallbackSilverBuy = settings?.silverRate ?: 88.0

        val avgGoldBuyRate = if (goldPurchasedGrams > 0) (goldPurchasedAmt / goldPurchasedGrams) else fallbackGoldBuy
        val avgGoldSellRate = if (goldSoldGrams > 0) (goldSoldAmt / goldSoldGrams) else 0.0

        val avgSilverBuyRate = if (silverPurchasedGrams > 0) (silverPurchasedAmt / silverPurchasedGrams) else fallbackSilverBuy
        val avgSilverSellRate = if (silverSoldGrams > 0) (silverSoldAmt / silverSoldGrams) else 0.0

        // Cost of Goods Sold = Fine gold sold * fine buy rate + Fine silver sold * fine buy rate
        val goldCost = goldSoldFine * (if (goldPurchasedFine > 0 && goldPurchasedAmt > 0) (goldPurchasedAmt / goldPurchasedFine) else (avgGoldBuyRate / 0.916))
        val silverCost = silverSoldFine * (if (silverPurchasedFine > 0 && silverPurchasedAmt > 0) (silverPurchasedAmt / silverPurchasedFine) else (avgSilverBuyRate / 0.70))
        val costOfGoods = goldCost + silverCost

        val monthlyProfit = if (totalSales > 0) (totalSales - costOfGoods - totalExpenses) else 0.0

        // Gold monthly stock balance
        val goldTxAll = transactions.filter { it.category == "GOLD_JEWELLERY" || it.category == "GOLD_METAL" }
        val goldOpeningTx = goldTxAll.filter { it.remark.contains("Opening", ignoreCase = true) || it.remark.contains("શરૂઆત") }
        val goldInitialOpening = goldOpeningTx.sumOf { it.quantityOrAmount }
        val goldPriorCredits = goldTxAll.filter { it.type == "CREDIT" && it !in goldOpeningTx && it.dateTimestamp < startTs }.sumOf { it.quantityOrAmount }
        val goldPriorDebits = goldTxAll.filter { it.type == "DEBIT" && it.dateTimestamp < startTs }.sumOf { it.quantityOrAmount }
        val goldOpeningMonth = goldInitialOpening + goldPriorCredits - goldPriorDebits
        val goldCreditMonth = goldTxAll.filter { it.type == "CREDIT" && it !in goldOpeningTx && it.dateTimestamp in startTs..endTs }.sumOf { it.quantityOrAmount }
        val goldDebitMonth = goldTxAll.filter { it.type == "DEBIT" && it.dateTimestamp in startTs..endTs }.sumOf { it.quantityOrAmount }
        val goldNetBalanceMonth = goldOpeningMonth + goldCreditMonth - goldDebitMonth
        val goldProfitMonth = if (goldSoldAmt > 0) (goldSoldAmt - goldCost) else 0.0

        // Silver monthly stock balance
        val silverTxAll = transactions.filter { it.category == "SILVER_JEWELLERY" || it.category == "SILVER_METAL" }
        val silverOpeningTx = silverTxAll.filter { it.remark.contains("Opening", ignoreCase = true) || it.remark.contains("શરૂઆત") }
        val silverInitialOpening = silverOpeningTx.sumOf { it.quantityOrAmount }
        val silverPriorCredits = silverTxAll.filter { it.type == "CREDIT" && it !in silverOpeningTx && it.dateTimestamp < startTs }.sumOf { it.quantityOrAmount }
        val silverPriorDebits = silverTxAll.filter { it.type == "DEBIT" && it.dateTimestamp < startTs }.sumOf { it.quantityOrAmount }
        val silverOpeningMonth = silverInitialOpening + silverPriorCredits - silverPriorDebits
        val silverCreditMonth = silverTxAll.filter { it.type == "CREDIT" && it !in silverOpeningTx && it.dateTimestamp in startTs..endTs }.sumOf { it.quantityOrAmount }
        val silverDebitMonth = silverTxAll.filter { it.type == "DEBIT" && it.dateTimestamp in startTs..endTs }.sumOf { it.quantityOrAmount }
        val silverNetBalanceMonth = silverOpeningMonth + silverCreditMonth - silverDebitMonth
        val silverProfitMonth = if (silverSoldAmt > 0) (silverSoldAmt - silverCost) else 0.0

        // Cash monthly balance
        val cashTxAll = transactions.filter { it.category == "CASH" }
        val cashOpeningTx = cashTxAll.filter { it.remark.contains("Opening", ignoreCase = true) || it.remark.contains("શરૂઆત") }
        val cashInitialOpening = cashOpeningTx.sumOf { it.quantityOrAmount }
        val cashPriorCredits = cashTxAll.filter { it.type == "CREDIT" && it !in cashOpeningTx && it.dateTimestamp < startTs }.sumOf { it.quantityOrAmount }
        val cashPriorDebits = cashTxAll.filter { it.type == "DEBIT" && it.dateTimestamp < startTs }.sumOf { it.quantityOrAmount }
        val cashOpeningMonth = cashInitialOpening + cashPriorCredits - cashPriorDebits
        val cashCreditMonth = cashReceived
        val cashDebitMonth = cashPaid
        val cashNetBalanceMonth = cashOpeningMonth + cashCreditMonth - cashDebitMonth

        return MonthlyStatementData(
            year = year,
            month = month,
            monthLabel = monthLabel,
            goldOpeningGrams = goldOpeningMonth,
            goldCreditGrams = goldCreditMonth,
            goldDebitGrams = goldDebitMonth,
            goldNetBalanceGrams = goldNetBalanceMonth,
            goldPurchasedGrams = goldPurchasedGrams,
            goldPurchasedFineGrams = goldPurchasedFine,
            goldSoldGrams = goldSoldGrams,
            goldSoldFineGrams = goldSoldFine,
            goldNetGrams = goldPurchasedGrams - goldSoldGrams,
            goldProfitLoss = goldProfitMonth,
            silverOpeningGrams = silverOpeningMonth,
            silverCreditGrams = silverCreditMonth,
            silverDebitGrams = silverDebitMonth,
            silverNetBalanceGrams = silverNetBalanceMonth,
            silverPurchasedGrams = silverPurchasedGrams,
            silverPurchasedFineGrams = silverPurchasedFine,
            silverSoldGrams = silverSoldGrams,
            silverSoldFineGrams = silverSoldFine,
            silverNetGrams = silverPurchasedGrams - silverSoldGrams,
            silverProfitLoss = silverProfitMonth,
            cashOpening = cashOpeningMonth,
            cashCredit = cashCreditMonth,
            cashDebit = cashDebitMonth,
            cashNetBalance = cashNetBalanceMonth,
            totalPurchases = totalPurchases,
            totalSales = totalSales,
            cashReceived = cashReceived,
            cashPaid = cashPaid,
            netCashFlow = cashReceived - cashPaid,
            totalExpenses = totalExpenses,
            ownSelfWithdrawals = ownWithdrawals,
            avgGoldBuyRate = avgGoldBuyRate,
            avgGoldSellRate = avgGoldSellRate,
            avgSilverBuyRate = avgSilverBuyRate,
            avgSilverSellRate = avgSilverSellRate,
            monthlyIncomeProfit = monthlyProfit,
            bills = monthBills.sortedByDescending { it.dateTimestamp }
        )
    }

    /**
     * Yearly Statement:
     * - show only monthly totals, not every individual transaction.
     * - show each month's gold, silver, cash, expenses and profit totals.
     * - show yearly total profit, purchases, sales and closing cash balance.
     * - allow opening a selected month for detailed information.
     */
    fun calculateYearlyStatement(
        bills: List<Bill>,
        transactions: List<StockTransaction>,
        settings: JewellerSettings?,
        year: Int
    ): YearlyStatementData {
        val rows = mutableListOf<MonthSummaryRow>()
        var yearlyProfit = 0.0
        var yearlyPurchases = 0.0
        var yearlySales = 0.0

        val monthCal = Calendar.getInstance(kolkataTimeZone).apply {
            set(Calendar.YEAR, year)
        }

        val monthNameFormat = SimpleDateFormat("MMMM", Locale.US).apply {
            timeZone = kolkataTimeZone
        }

        for (m in 1..12) {
            val monthData = calculateMonthlyStatement(bills, transactions, settings, year, m)
            monthCal.set(Calendar.MONTH, m - 1)
            val monthName = monthNameFormat.format(monthCal.time)

            yearlyProfit += monthData.monthlyIncomeProfit
            yearlyPurchases += monthData.totalPurchases
            yearlySales += monthData.totalSales

            rows.add(
                MonthSummaryRow(
                    month = m,
                    monthName = monthName,
                    goldTotalGrams = monthData.goldSoldGrams + monthData.goldPurchasedGrams,
                    silverTotalGrams = monthData.silverSoldGrams + monthData.silverPurchasedGrams,
                    cashTotalAmount = monthData.cashReceived,
                    expensesTotal = monthData.totalExpenses,
                    profitTotal = monthData.monthlyIncomeProfit,
                    purchasesTotal = monthData.totalPurchases,
                    salesTotal = monthData.totalSales
                )
            )
        }

        // Closing cash balance for the year
        val yearStart = Calendar.getInstance(kolkataTimeZone).apply {
            set(Calendar.YEAR, year)
            set(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val yearEnd = Calendar.getInstance(kolkataTimeZone).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, 11)
            set(Calendar.DAY_OF_MONTH, 31)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        val goldTxAll = transactions.filter { it.category == "GOLD_JEWELLERY" || it.category == "GOLD_METAL" }
        val goldOpeningTx = goldTxAll.filter { it.remark.contains("Opening", ignoreCase = true) || it.remark.contains("શરૂઆત") }
        val goldInitialOpening = goldOpeningTx.sumOf { it.quantityOrAmount }
        val goldPriorCreditsYear = goldTxAll.filter { it.type == "CREDIT" && it !in goldOpeningTx && it.dateTimestamp < yearStart }.sumOf { it.quantityOrAmount }
        val goldPriorDebitsYear = goldTxAll.filter { it.type == "DEBIT" && it.dateTimestamp < yearStart }.sumOf { it.quantityOrAmount }
        val goldOpeningYear = goldInitialOpening + goldPriorCreditsYear - goldPriorDebitsYear
        val goldCreditYear = goldTxAll.filter { it.type == "CREDIT" && it !in goldOpeningTx && it.dateTimestamp in yearStart..yearEnd }.sumOf { it.quantityOrAmount }
        val goldDebitYear = goldTxAll.filter { it.type == "DEBIT" && it.dateTimestamp in yearStart..yearEnd }.sumOf { it.quantityOrAmount }
        val goldNetBalanceYear = goldOpeningYear + goldCreditYear - goldDebitYear

        val silverTxAll = transactions.filter { it.category == "SILVER_JEWELLERY" || it.category == "SILVER_METAL" }
        val silverOpeningTx = silverTxAll.filter { it.remark.contains("Opening", ignoreCase = true) || it.remark.contains("શરૂઆત") }
        val silverInitialOpening = silverOpeningTx.sumOf { it.quantityOrAmount }
        val silverPriorCreditsYear = silverTxAll.filter { it.type == "CREDIT" && it !in silverOpeningTx && it.dateTimestamp < yearStart }.sumOf { it.quantityOrAmount }
        val silverPriorDebitsYear = silverTxAll.filter { it.type == "DEBIT" && it.dateTimestamp < yearStart }.sumOf { it.quantityOrAmount }
        val silverOpeningYear = silverInitialOpening + silverPriorCreditsYear - silverPriorDebitsYear
        val silverCreditYear = silverTxAll.filter { it.type == "CREDIT" && it !in silverOpeningTx && it.dateTimestamp in yearStart..yearEnd }.sumOf { it.quantityOrAmount }
        val silverDebitYear = silverTxAll.filter { it.type == "DEBIT" && it.dateTimestamp in yearStart..yearEnd }.sumOf { it.quantityOrAmount }
        val silverNetBalanceYear = silverOpeningYear + silverCreditYear - silverDebitYear

        val cashTxAll = transactions.filter { it.category == "CASH" }
        val cashOpeningTx = cashTxAll.filter { it.remark.contains("Opening", ignoreCase = true) || it.remark.contains("શરૂઆત") }
        val cashInitialOpening = cashOpeningTx.sumOf { it.quantityOrAmount }
        val cashPriorCreditsYear = cashTxAll.filter { it.type == "CREDIT" && it !in cashOpeningTx && it.dateTimestamp < yearStart }.sumOf { it.quantityOrAmount }
        val cashPriorDebitsYear = cashTxAll.filter { it.type == "DEBIT" && it.dateTimestamp < yearStart }.sumOf { it.quantityOrAmount }
        val cashOpeningYear = cashInitialOpening + cashPriorCreditsYear - cashPriorDebitsYear
        val cashCreditYear = cashTxAll.filter { it.type == "CREDIT" && it !in cashOpeningTx && it.dateTimestamp in yearStart..yearEnd }.sumOf { it.quantityOrAmount }
        val cashDebitYear = cashTxAll.filter { it.type == "DEBIT" && it.dateTimestamp in yearStart..yearEnd }.sumOf { it.quantityOrAmount }
        val cashNetBalanceYear = cashOpeningYear + cashCreditYear - cashDebitYear

        return YearlyStatementData(
            year = year,
            monthlySummaries = rows,
            yearlyTotalProfit = yearlyProfit,
            yearlyTotalPurchases = yearlyPurchases,
            yearlyTotalSales = yearlySales,
            closingCashBalance = cashNetBalanceYear,
            goldOpeningGrams = goldOpeningYear,
            goldCreditGrams = goldCreditYear,
            goldDebitGrams = goldDebitYear,
            goldNetBalanceGrams = goldNetBalanceYear,
            silverOpeningGrams = silverOpeningYear,
            silverCreditGrams = silverCreditYear,
            silverDebitGrams = silverDebitYear,
            silverNetBalanceGrams = silverNetBalanceYear,
            cashOpening = cashOpeningYear,
            cashCredit = cashCreditYear,
            cashDebit = cashDebitYear,
            cashNetBalance = cashNetBalanceYear
        )
    }
}
