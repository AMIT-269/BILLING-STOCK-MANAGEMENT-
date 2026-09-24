package com.example.ui.locale

/**
 * Centralized localized strings for the entire Jeweller Billing application.
 * Ensures strict segregation: 100% English when English is selected, 100% Gujarati when Gujarati is selected.
 * No mixed labels, no parenthesized translations in UI elements.
 */
object AppStrings {

    // Common UI actions
    fun save(): String = loc(en = "Save", gu = "સાચવો")
    fun cancel(): String = loc(en = "Cancel", gu = "રદ કરો")
    fun delete(): String = loc(en = "Delete", gu = "કાઢી નાખો")
    fun edit(): String = loc(en = "Edit", gu = "સુધારો")
    fun back(): String = loc(en = "Back", gu = "પાછા જાઓ")
    fun search(): String = loc(en = "Search", gu = "શોધો")
    fun all(): String = loc(en = "All", gu = "બધા")
    fun close(): String = loc(en = "Close", gu = "બંધ કરો")
    fun ok(): String = loc(en = "OK", gu = "બરાબર")
    fun confirm(): String = loc(en = "Confirm", gu = "ખાતરી કરો")
    fun loading(): String = loc(en = "Loading...", gu = "લોડ થઈ રહ્યું છે...")

    // Cloud Sync
    fun cloudSynced(): String = loc(en = "Cloud ✓", gu = "ક્લાઉડ ✓")
    fun syncing(): String = loc(en = "Syncing...", gu = "સિંક...")
    fun offline(): String = loc(en = "Offline", gu = "ઑફલાઇન")
    fun syncNow(): String = loc(en = "Sync Now", gu = "હમણાં સિંક કરો")
    fun cloudSync(): String = loc(en = "Multi-Device Cloud Synchronization", gu = "ક્લાઉડ સિન્ક્રોનાઇઝેશન")
    fun cloudSyncTitle(): String = loc(en = "Multi-Device Cloud Synchronization", gu = "ક્લાઉડ સિન્ક્રોનાઇઝેશન")
    fun cloudSyncDesc(): String = loc(
        en = "Bills, stock, and settings sync automatically so your data is safe and restored upon device change.",
        gu = "બિલ, સ્ટોક અને સેટિંગ્સ ક્લાઉડ સાથે સ્વચાલિત સિંક થાય છે જેથી તમારો ડેટા સુરક્ષિત રહે છે."
    )

    // Navigation & Dashboard
    fun appTitle(): String = loc(en = "BILLING & STOCK MANAGEMENT", gu = "બિલિંગ અને સ્ટોક મેનેજમેન્ટ")
    fun customerSaleBill(): String = loc(en = "+ Customer Sale Bill", gu = "+ ગ્રાહક વેચાણ બિલ")
    fun karigarPurchaseBill(): String = loc(en = "+ Karigar Purchase", gu = "+ કારીગર ખરીદી")
    fun stockOverviewAll(): String = loc(en = "Stock Overview (All Metals & Cash)", gu = "સ્ટોક ઝાંખી (બધી ધાતુઓ અને રોકડ)")
    fun editOpening(): String = loc(en = "Edit Opening", gu = "ઓપનિંગ સુધારો")
    fun currentBalance(): String = loc(en = "Current Balance", gu = "હાલનો સ્ટોક")
    fun openingPlusCreditMinusDebit(): String = loc(en = "(Opening + Credit - Debit)", gu = "(ઓપનિંગ + જમા - ઉધાર)")
    fun openingStock(): String = loc(en = "Opening Stock", gu = "ઓપનિંગ સ્ટોક")
    fun history(): String = loc(en = "History", gu = "હિસ્ટ્રી")
    fun newBill(): String = loc(en = "New Bill", gu = "નવું બિલ")
    fun billHistory(): String = loc(en = "Bill History", gu = "બિલ હિસ્ટ્રી")
    fun settings(): String = loc(en = "Settings", gu = "સેટિંગ્સ")
    fun logout(): String = loc(en = "Logout", gu = "લોગઆઉટ")
    fun logoutConfirm(): String = loc(en = "Are you sure you want to logout?", gu = "શું તમે ખરેખર લોગઆઉટ કરવા માંગો છો?")

    // Tabs & Metals
    fun tabGold(): String = loc(en = "GOLD", gu = "સોનું")
    fun tabSilver(): String = loc(en = "SILVER", gu = "ચાંદી")
    fun tabCash(): String = loc(en = "CASH", gu = "રોકડ")

    fun goldJewellery(): String = loc(en = "Jewellery Gold", gu = "સોનું ઘરેણાં")
    fun goldMetal(): String = loc(en = "GOLD", gu = "સોનું કાચું ધાતુ")
    fun silverJewellery(): String = loc(en = "Jewellery Silver", gu = "ચાંદી ઘરેણાં")
    fun silverMetal(): String = loc(en = "SILVER", gu = "ચાંદી કાચું ધાતુ")
    fun cash(): String = loc(en = "CASH", gu = "રોકડ")

    fun jewelleryStock(): String = loc(en = "Jewellery Stock", gu = "ઘરેણાં સ્ટોક")
    fun metalStock(): String = loc(en = "Metal Stock", gu = "ધાતુ સ્ટોક")
    fun jewellerySection(): String = loc(en = "Jewellery Section", gu = "ઘરેણાં વિભાગ")
    fun metalSection(): String = loc(en = "Metal Section", gu = "ધાતુ વિભાગ")

    // Stock & Balance Cards
    fun credit(): String = loc(en = "Credit", gu = "જમા")
    fun debit(): String = loc(en = "Debit", gu = "ઉધાર")
    fun totalCredit(): String = loc(en = "Total Credit", gu = "કુલ જમા")
    fun totalDebit(): String = loc(en = "Total Debit", gu = "કુલ ઉધાર")
    fun netBalance(): String = loc(en = "Net Balance", gu = "કુલ શિલક")
    fun cashBalance(): String = loc(en = "Cash Balance", gu = "રોકડ શિલક")
    fun addCredit(): String = loc(en = "+ Credit", gu = "+ જમા")
    fun addDebit(): String = loc(en = "+ Debit", gu = "+ ઉધાર")
    fun setOpeningStock(): String = loc(en = "+ Set Opening Stock", gu = "+ શરૂઆતનો સ્ટોક")
    fun cashToMetalPurchase(): String = loc(en = "Cash → Metal Purchase", gu = "રોકડથી ધાતુ ખરીદી")
    fun recentTransactions(): String = loc(en = "Recent Stock Transactions", gu = "તાજેતરના વ્યવહારો")
    fun noTransactions(): String = loc(en = "No stock transactions recorded yet.", gu = "હજુ સુધી કોઈ વ્યવહાર નોંધાયેલ નથી.")

    // Dialogs
    fun addEntryTitle(isCredit: Boolean, categoryName: String): String {
        val action = if (isCredit) loc(en = "Add Credit", gu = "જમા ઉમેરો") else loc(en = "Add Debit", gu = "ઉધાર ઉમેરો")
        return "$action - $categoryName"
    }
    fun enterQuantity(): String = loc(en = "Enter Quantity / Weight:", gu = "પ્રમાણ / વજન દાખલ કરો:")
    fun remarkNote(): String = loc(en = "Remark / Note (Optional)", gu = "નોંધ (મરજિયાત)")
    fun openingStockTitle(categoryName: String): String = loc(en = "Set Opening Stock - $categoryName", gu = "શરૂઆતનો સ્ટોક - $categoryName")
    fun openingStockDesc(): String = loc(
        en = "Enter existing opening stock before creating bills:",
        gu = "બિલ બનાવતાં પહેલાં તમારી પાસે રહેલો શરૂઆતનો સ્ટોક દાખલ કરો:"
    )

    // Create Bill
    fun createBillTitle(): String = loc(en = "Create Bill", gu = "નવું બિલ")
    fun editBillTitle(): String = loc(en = "Edit Bill", gu = "બિલમાં ફેરફાર કરો")
    fun billType(): String = loc(en = "Bill Type", gu = "બિલનો પ્રકાર")
    fun customerSale(): String = loc(en = "Customer Sale", gu = "ગ્રાહક વેચાણ")
    fun karigarPurchase(): String = loc(en = "Karigar Purchase", gu = "કારીગર ખરીદી")
    fun invoiceMode(): String = loc(en = "Invoice Mode", gu = "ઇન્વોઇસ મોડ")
    fun taxInvoiceGst(): String = loc(en = "Tax Invoice (GST)", gu = "ટેક્સ ઇન્વોઇસ (GST)")
    fun retailInvoiceNonGst(): String = loc(en = "Retail Invoice (Non-GST)", gu = "રિટેલ ઇન્વોઇસ (Non-GST)")
    fun partyDetails(isSale: Boolean): String = if (isSale) loc(en = "Customer Details", gu = "ગ્રાહકની વિગત") else loc(en = "Karigar Details", gu = "કારીગરની વિગત")
    fun partyName(isSale: Boolean): String = if (isSale) loc(en = "Customer Name *", gu = "ગ્રાહકનું નામ *") else loc(en = "Karigar Name *", gu = "કારીગરનું નામ *")
    fun partyMobile(): String = loc(en = "Mobile Number", gu = "મોબાઈલ નંબર")
    fun partyAddress(): String = loc(en = "Address", gu = "સરનામું")
    fun billNumber(): String = loc(en = "Bill Number", gu = "બિલ નંબર")
    fun billDate(): String = loc(en = "Date", gu = "તારીખ")
    fun billTime(): String = loc(en = "Time", gu = "સમય")

    // Bill Item Fields
    fun addItem(): String = loc(en = "+ Add Item", gu = "+ દાગીનો ઉમેરો")
    fun itemDetails(): String = loc(en = "Jewellery Item Details", gu = "દાગીનાની વિગત")
    fun itemName(): String = loc(en = "Jewellery Item Name (e.g. Ring, Chain)", gu = "દાગીનાનું નામ (દા.ત. વીંટી, ચેઇન)")
    fun metal(): String = loc(en = "Metal", gu = "ધાતુ")
    fun gold(): String = loc(en = "Gold", gu = "સોનું")
    fun silver(): String = loc(en = "Silver", gu = "ચાંદી")
    fun weightGram(): String = loc(en = "Weight (g)", gu = "વજન (ગ્રામ)")
    fun currentTouch(): String = loc(en = "Current Touch (%)", gu = "હાલનો ટચ (%)")
    fun makingChargePercent(): String = loc(en = "Making Charge (%)", gu = "મેકિંગ ચાર્જ (%)")
    fun totalTouch(): String = loc(en = "Total Touch (%)", gu = "કુલ ટચ (%)")
    fun totalFine(): String = loc(en = "Total Fine (g)", gu = "કુલ શુદ્ધ વજન (ગ્રામ)")
    fun currentPrice(): String = loc(en = "Current Price (₹/g)", gu = "હાલનો ભાવ (₹/ગ્રામ)")
    fun itemAmount(): String = loc(en = "Amount (₹)", gu = "રકમ (₹)")
    fun itemsInBill(): String = loc(en = "Items in Bill", gu = "બિલમાં સામેલ દાગીના")
    fun noItemsAdded(): String = loc(en = "No items added yet. Click + Add Item.", gu = "હજુ સુધી કોઈ દાગીનો ઉમેરેલ નથી. + દાગીનો ઉમેરો પર ક્લિક કરો.")

    // Summary & Totals
    fun additionalCharges(): String = loc(en = "Additional Charges", gu = "અન્ય ચાર્જ")
    fun subtotal(): String = loc(en = "Subtotal", gu = "પેટા કુલ રકમ")
    fun gst(): String = loc(en = "GST", gu = "જીએસટી")
    fun cgst(): String = loc(en = "CGST", gu = "સીજીએસટી")
    fun sgst(): String = loc(en = "SGST", gu = "એસજીએસટી")
    fun discount(): String = loc(en = "Discount", gu = "વળતર")
    fun oldMetalExchange(): String = loc(en = "Old Metal Exchange", gu = "જૂનું સોનું/ચાંદી જમા")
    fun grandTotal(): String = loc(en = "Grand Total", gu = "કુલ રકમ")
    fun paymentDetails(): String = loc(en = "Payment Details", gu = "ચૂકવણીની વિગત")
    fun paymentHistory(): String = loc(en = "Payment History & Installments", gu = "ચુકવણી ઇતિહાસ અને હપ્તા")
    fun addPaymentEntry(): String = loc(en = "+ Add Payment", gu = "+ ચુકવણી ઉમેરો")
    fun addAdditionalPayment(): String = loc(en = "+ Add Payment Entry", gu = "+ વધારાની ચુકવણી ઉમેરો")
    fun paymentMode(): String = loc(en = "Payment Mode", gu = "ચૂકવણીનો પ્રકાર")
    fun modeCash(): String = loc(en = "Cash", gu = "રોકડ")
    fun modeGold(): String = loc(en = "Gold Metal", gu = "સોનું")
    fun modeSilver(): String = loc(en = "Silver Metal", gu = "ચાંદી")
    fun modeOnline(): String = loc(en = "Online / UPI", gu = "ઓનલાઇન / UPI")
    fun modeCheque(): String = loc(en = "Cheque", gu = "ચેક")
    fun stockClassification(): String = loc(en = "Stock Type", gu = "સ્ટોક પ્રકાર")
    fun stockJewellery(): String = loc(en = "Jewellery (Ornaments)", gu = "દાગીના (ઘરેણાં)")
    fun stockMetal(): String = loc(en = "Raw Metal (Bullion)", gu = "કાચું ધાતુ")
    fun cst(): String = loc(en = "CST", gu = "સીએસટી")
    fun cashReceived(isSale: Boolean): String = if (isSale) loc(en = "Cash Received", gu = "મેળવેલ રોકડ") else loc(en = "Cash Paid", gu = "ચૂકવેલ રોકડ")
    fun balanceDue(): String = loc(en = "Balance Due", gu = "બાકી રકમ")
    fun paidAmount(): String = loc(en = "Paid Amount", gu = "ચૂકવેલ રકમ")
    fun remarks(): String = loc(en = "Remarks", gu = "નોંધ")
    fun saveAndPreview(): String = loc(en = "Save & Preview Bill", gu = "બિલ સાચવો અને પ્રિવ્યૂ જુઓ")

    // Bill History Screen
    fun totalBills(): String = loc(en = "Total Bills", gu = "કુલ બિલ")
    fun searchBillsHint(): String = loc(en = "Search by bill number or party name...", gu = "બિલ નંબર અથવા નામ દ્વારા શોધો...")
    fun filterAll(): String = loc(en = "All", gu = "બધા")
    fun noBillsFound(): String = loc(en = "No bills found.", gu = "કોઈ બિલ મળ્યું નથી.")
    fun previewAndPrint(): String = loc(en = "Preview & Print", gu = "પ્રિવ્યૂ અને પ્રિન્ટ")

    // Bill Preview
    fun previewTitle(): String = loc(en = "Bill Preview", gu = "બિલ પ્રિવ્યૂ")
    fun printThermal(): String = loc(en = "Thermal Print", gu = "થર્મલ પ્રિન્ટ")
    fun share(): String = loc(en = "Share", gu = "શેર કરો")
    fun thankYouPurchase(): String = loc(en = "Thank you for your valuable purchase.", gu = "આપની અમૂલ્ય ખરીદી બદલ ખૂબ ખૂબ આભાર.")
    fun visitAgain(): String = loc(en = "Visit again...", gu = "ફરી પધારશો...")
    fun shubhLabh(): String = loc(en = "|| Shubh Labh ||", gu = "|| શુભ લાભ ||")
    fun thankYouBanner(): String = loc(en = "Thank You! For Shopping With Us", gu = "આપનો આભાર! ફરી પધારશો")

    // Bill Preview Table Column Headers
    fun colNo(): String = loc(en = "No.", gu = "ક્રમ")
    fun colItem(): String = loc(en = "Jewellery Item", gu = "દાગીનાનું નામ")
    fun colMetal(): String = loc(en = "Metal", gu = "ધાતુ")
    fun colWeight(): String = loc(en = "Weight (g.)", gu = "વજન (ગ્રામ)")
    fun colTouch(): String = loc(en = "Current Touch", gu = "હાલનો ટચ")
    fun colMaking(): String = loc(en = "Making Charge (%)", gu = "મેકિંગ ચાર્જ (%)")
    fun colTotalTouch(): String = loc(en = "Total Touch", gu = "કુલ ટચ")
    fun colTotalFine(): String = loc(en = "Total Fine (g.)", gu = "કુલ શુદ્ધ વજન (ગ્રામ)")
    fun colPrice(): String = loc(en = "Current Price (₹/g)", gu = "હાલનો ભાવ (₹/ગ્રામ)")
    fun colAmount(): String = loc(en = "Amount (₹)", gu = "રકમ (₹)")

    // Settings Screen
    fun settingsTitle(): String = loc(en = "Settings & Profile", gu = "સેટિંગ્સ અને પ્રોફાઇલ")
    fun chooseLanguage(): String = loc(en = "Choose Language", gu = "ભાષા પસંદ કરો")
    fun englishLang(): String = "English"
    fun gujaratiLang(): String = "ગુજરાતી"
    fun shopLogo(): String = loc(en = "Shop Logo", gu = "દુકાનનો લોગો")
    fun changeLogo(): String = loc(en = "Change Logo", gu = "લોગો બદલો")
    fun shopProfile(): String = loc(en = "Jeweller Profile", gu = "દુકાનની વિગત")
    fun shopName(): String = loc(en = "Workshop / Jewellers Name", gu = "દુકાન અથવા ઝવેરીનું નામ")
    fun shopAddress(): String = loc(en = "Shop Address", gu = "દુકાનનું સરનામું")
    fun contactNumber(): String = loc(en = "Contact / Mobile Number", gu = "સંપર્ક મોબાઈલ નંબર")
    fun shopMobile(): String = loc(en = "Contact Mobile Number", gu = "સંપર્ક મોબાઈલ નંબર")
    fun gstNumber(): String = loc(en = "GST Number (GSTIN)", gu = "જીએસટી નંબર (GSTIN)")
    fun dailyRates(): String = loc(en = "Daily Rates", gu = "દૈનિક સોના-ચાંદી ભાવ")
    fun gold22kRate(): String = loc(en = "Gold 22K", gu = "૨૨ કેરેટ સોનું")
    fun silverRate(): String = loc(en = "Silver Price (₹/kg)", gu = "ચાંદી ભાવ (₹/કિલો)")
    fun goldRate22k(): String = loc(en = "Gold Rate 22K (₹/g)", gu = "૨૨ કેરેટ સોનાનો ભાવ (₹/ગ્રામ)")
    fun appLanguage(): String = loc(en = "Choose Language", gu = "ભાષા પસંદ કરો")
    fun appLanguageDesc(): String = loc(en = "Select application language", gu = "એપ્લિકેશનની ભાષા પસંદ કરો")
    fun english(): String = "English"
    fun gujarati(): String = "ગુજરાતી"
    fun saveSettings(): String = loc(en = "Save Settings", gu = "સેટિંગ્સ સાચવો")

    // Login & Register
    fun login(): String = loc(en = "Login", gu = "લોગઇન")
    fun newRegistration(): String = loc(en = "New Registration", gu = "નવું રજીસ્ટ્રેશન")
    fun registrationSuccess(): String = loc(en = "Registration Successful", gu = "રજીસ્ટ્રેશન સફળ")
    fun registrationSuccessMsg(): String = loc(
        en = "Your Jeweller account is activated. Please login now.",
        gu = "તમારું ઝવેરી એકાઉન્ટ સક્રિય થઈ ગયું છે. હવે લોગઇન કરો."
    )
    fun goToLogin(): String = loc(en = "Go to Login", gu = "લોગઇન કરો")
    fun forgotPassword(): String = loc(en = "Forgot Password?", gu = "પાસવર્ડ ભૂલી ગયા?")
    fun resetCode(): String = loc(en = "Reset 4-Digit Code", gu = "૪-અંકનો કોડ રીસેટ કરો")
    fun resetCodeDesc(): String = loc(
        en = "Enter your Jeweller Name and registered mobile number to set a new code.",
        gu = "નવો કોડ સેટ કરવા માટે તમારું ઝવેરી નામ અને નોંધાયેલ મોબાઈલ નંબર દાખલ કરો."
    )
    fun securityCode(): String = loc(en = "4-Digit Security Code *", gu = "૪-અંકનો સુરક્ષા કોડ *")
    fun confirmSecurityCode(): String = loc(en = "Confirm 4-Digit Code *", gu = "સુરક્ષા કોડ ફરી દાખલ કરો *")
    fun licenceKey(): String = loc(en = "Owner Licence Key *", gu = "માલિકી લાઇસન્સ કી *")
    fun newCode(): String = loc(en = "New 4-Digit Code *", gu = "નવો ૪-અંકનો કોડ *")
    fun confirmNewCode(): String = loc(en = "Confirm New Code *", gu = "નવો કોડ ફરી દાખલ કરો *")
    fun resetCodeBtn(): String = loc(en = "Reset Code", gu = "કોડ રીસેટ કરો")
    fun dontHaveAccount(): String = loc(en = "Don't have an account? Register here", gu = "એકાઉન્ટ નથી? અહીં રજીસ્ટ્રેશન કરો")
    fun alreadyHaveAccount(): String = loc(en = "Already have an account? Login here", gu = "પહેલેથી એકાઉન્ટ છે? અહીં લોગઇન કરો")

    // Bluetooth Printer Dialog
    fun bluetoothPrinterTitle(): String = loc(en = "Connect Bluetooth Printer", gu = "બ્લૂટૂથ પ્રિન્ટર કનેક્ટ કરો")
    fun printerDialogTitle(): String = loc(en = "Connect Bluetooth Printer", gu = "બ્લૂટૂથ પ્રિન્ટર કનેક્ટ કરો")
    fun printerDialogDesc(): String = loc(en = "Select Paired Thermal Bill Printer:", gu = "જોડાયેલ થર્મલ પ્રિન્ટર પસંદ કરો:")
    fun searchPairedPrinters(): String = loc(en = "Search Paired Printers", gu = "જોડાયેલા પ્રિન્ટર શોધો")
    fun selectPrinterDesc(): String = loc(
        en = "Make sure your 58mm or 80mm Bluetooth printer is ON and paired in phone Bluetooth settings.",
        gu = "તમારું ૫૮mm અથવા ૮૦mm બ્લૂટૂથ પ્રિન્ટર ચાલુ છે અને ફોનના બ્લૂટૂથ સાથે જોડાયેલું છે તેની ખાતરી કરો."
    )
    fun noPrintersFound(): String = loc(en = "No paired Bluetooth printers found.", gu = "કોઈ જોડાયેલ બ્લૂટૂથ પ્રિન્ટર મળ્યું નથી.")
    fun noPairedPrinters(): String = loc(
        en = "No paired Bluetooth printers found. Pair in phone settings first.",
        gu = "કોઈ જોડાયેલ બ્લૂટૂથ પ્રિન્ટર મળ્યું નથી. પહેલા ફોનના સેટિંગ્સમાં જોડાવો."
    )
    fun printReceipt(): String = loc(en = "Print Receipt", gu = "રસીદ પ્રિન્ટ કરો")
}
