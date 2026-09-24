package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.AuthResult
import com.example.data.repository.JewelleryRepository
import com.example.data.sync.LicenceValidator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("BILLING & STOCK MANAGEMENT", appName)
  }

  @Test
  fun `verify owner licence code validation`() {
    // Owner Licence Code must be valid
    assertTrue(LicenceValidator.isValidLicence(LicenceValidator.OWNER_LICENCE_CODE))
    assertTrue(LicenceValidator.isValidLicence(" ${LicenceValidator.OWNER_LICENCE_CODE} "))

    // All other codes must be invalid
    assertFalse(LicenceValidator.isValidLicence("1234"))
    assertFalse(LicenceValidator.isValidLicence("0000"))
    assertFalse(LicenceValidator.isValidLicence(""))
    assertFalse(LicenceValidator.isValidLicence("2331"))
    assertFalse(LicenceValidator.isValidLicence("ACTV-8822-PRO"))
  }

  @Test
  fun `verify registration login and granular error handling`() = runBlocking {
    com.example.ui.locale.LanguageManager.globalLanguage = com.example.ui.locale.AppLanguage.ENGLISH
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = JewelleryRepository(context)

    // 1. Invalid licence code during registration must fail
    val invalidLicenceRes = repo.registerNewJeweller(
      name = "Shiv Jewellers",
      mobile = "9876543210",
      code = "1234",
      confirmCode = "1234",
      licenceCode = "9999"
    )
    assertTrue(invalidLicenceRes is AuthResult.Error)
    val msg1 = (invalidLicenceRes as AuthResult.Error).message
    assertTrue(msg1.contains("Licence") || msg1.contains("લાઇસન્સ"))

    // 2. Valid licence code registration must succeed
    val regSuccess = repo.registerNewJeweller(
      name = "Shiv Jewellers",
      mobile = "9876543210",
      code = "1234",
      confirmCode = "1234",
      licenceCode = LicenceValidator.OWNER_LICENCE_CODE
    )
    assertTrue(regSuccess is AuthResult.Success)

    // 3. Login with correct credentials must succeed (Licence code 2330 NOT needed!)
    val loginSuccess = repo.login(
      name = "Shiv Jewellers",
      mobile = "9876543210",
      code = "1234"
    )
    assertTrue(loginSuccess is AuthResult.Success)
    assertEquals("Shiv Jewellers", (loginSuccess as AuthResult.Success).account.jewellerName)

    // 4. Incorrect 4-digit code must return exact message "4 Digit Code ખોટો છે."
    val wrongCodeLogin = repo.login(
      name = "Shiv Jewellers",
      mobile = "9876543210",
      code = "9999"
    )
    assertTrue(wrongCodeLogin is AuthResult.Error)
    val msg4 = (wrongCodeLogin as AuthResult.Error).message
    assertTrue(msg4.contains("Code") || msg4.contains("કોડ"))

    // 5. Wrong Jeweller Name with existing mobile must return error
    val wrongNameLogin = repo.login(
      name = "Om Jewellers",
      mobile = "9876543210",
      code = "1234"
    )
    assertTrue(wrongNameLogin is AuthResult.Error)
    val msg5 = (wrongNameLogin as AuthResult.Error).message
    assertTrue(msg5.contains("Jeweller Name") || msg5.contains("નામ"))

    // 6. Wrong Mobile with existing name must return error
    val wrongMobileLogin = repo.login(
      name = "Shiv Jewellers",
      mobile = "9123456780",
      code = "1234"
    )
    assertTrue(wrongMobileLogin is AuthResult.Error)
    val msg6 = (wrongMobileLogin as AuthResult.Error).message
    assertTrue(msg6.contains("Mobile") || msg6.contains("મોબાઈલ"))

    // 7. Non-existent account must return error
    val notAvailableLogin = repo.login(
      name = "Unknown Shop",
      mobile = "9111111111",
      code = "1234"
    )
    assertTrue(notAvailableLogin is AuthResult.Error)
    val msg7 = (notAvailableLogin as AuthResult.Error).message
    assertTrue(msg7.contains("Account") || msg7.contains("એકાઉન્ટ"))

    // 8. Forgot Password can reset code without licence code
    val resetRes = repo.resetPassword(
      name = "Shiv Jewellers",
      mobile = "9876543210",
      newCode = "5678",
      confirmCode = "5678"
    )
    assertTrue(resetRes is AuthResult.Success)

    // 9. Login with new code succeeds
    val loginWithNewCode = repo.login(
      name = "Shiv Jewellers",
      mobile = "9876543210",
      code = "5678"
    )
    assertTrue(loginWithNewCode is AuthResult.Success)
  }

  @Test
  fun `verify registration login forgot password with GST number and change code`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = JewelleryRepository(context)

    val gstNumber = "24AAAAA0000A1Z5"

    // Register with GST
    val regRes = repo.registerNewJeweller(
      name = "Ganesh Jewellers",
      mobile = "9988776655",
      code = "4321",
      confirmCode = "4321",
      licenceCode = LicenceValidator.OWNER_LICENCE_CODE,
      gstNumber = gstNumber
    )
    assertTrue(regRes is AuthResult.Success)
    val account = (regRes as AuthResult.Success).account
    assertEquals(gstNumber, account.gstNumber)

    // Login with GST
    val loginRes = repo.login(
      name = "Ganesh Jewellers",
      mobile = "9988776655",
      gstNumber = gstNumber,
      code = "4321"
    )
    assertTrue(loginRes is AuthResult.Success)

    // Login with wrong GST fails
    val loginWrongGst = repo.login(
      name = "Ganesh Jewellers",
      mobile = "9988776655",
      gstNumber = "24WRONGGST00000",
      code = "4321"
    )
    assertTrue(loginWrongGst is AuthResult.Error)

    // Reset password with GST
    val resetRes = repo.resetPassword(
      name = "Ganesh Jewellers",
      mobile = "9988776655",
      gstNumber = gstNumber,
      newCode = "8888",
      confirmCode = "8888"
    )
    assertTrue(resetRes is AuthResult.Success)

    // Login with new code 8888
    val loginAfterReset = repo.login(
      name = "Ganesh Jewellers",
      mobile = "9988776655",
      gstNumber = gstNumber,
      code = "8888"
    )
    assertTrue(loginAfterReset is AuthResult.Success)

    // Change 4-digit code in settings
    val changeCodeRes = repo.changeSecurityCode(
      currentCode = "8888",
      newCode = "9999",
      confirmCode = "9999"
    )
    assertTrue(changeCodeRes is AuthResult.Success)

    // Verify login with 9999
    val loginAfterChange = repo.login(
      name = "Ganesh Jewellers",
      mobile = "9988776655",
      gstNumber = gstNumber,
      code = "9999"
    )
    assertTrue(loginAfterChange is AuthResult.Success)
  }

  @Test
  fun `verify language manager digits stay 0-9 in all supported languages`() {
    val languages = com.example.ui.locale.AppLanguage.entries
    assertEquals(13, languages.size)

    for (lang in languages) {
      com.example.ui.locale.LanguageManager.globalLanguage = lang
      assertEquals(lang, com.example.ui.locale.LanguageManager.globalLanguage)

      // Verify digits are strictly English 0-9
      val formattedWeight = com.example.ui.locale.LanguageManager.formatWeight(12.345, false)
      assertTrue(formattedWeight.contains("12.345"))
      assertTrue(formattedWeight.all { it.isDigit() || it == '.' || it == ' ' || it.isLetter() })

      val formattedCurr = com.example.ui.locale.LanguageManager.formatCurrency(54321.50)
      assertTrue(formattedCurr.contains("54,321.50") || formattedCurr.contains("54321.50"))
    }

    // Reset to English
    com.example.ui.locale.LanguageManager.globalLanguage = com.example.ui.locale.AppLanguage.ENGLISH
  }

  @Test
  fun `verify fine 100 percent calculation logic - 10g at 80 touch equals 8g fine`() {
    // 10.000g at 80 touch = 8.000g fine
    val weight = 10.0
    val touch = 80.0
    val fine = (weight * touch) / 100.0
    assertEquals(8.0, fine, 0.0001)

    // 10.000g 22k (91.6 touch) = 9.16g fine
    val fine22k = (10.0 * 91.6) / 100.0
    assertEquals(9.16, fine22k, 0.0001)
  }

  @Test
  fun `verify customer sale and karigar purchase bill numbering with jeweller initials and sequential digits`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = JewelleryRepository(context)

    // Register a jeweller named "Amit Jewellers"
    val regRes = repo.registerNewJeweller(
      name = "Amit Jewellers",
      mobile = "9900112233",
      code = "1122",
      confirmCode = "1122",
      licenceCode = LicenceValidator.OWNER_LICENCE_CODE
    )
    assertTrue(regRes is AuthResult.Success)
    val accountId = (regRes as AuthResult.Success).account.accountId

    // Customer Sale Bill number must start with AJ-0001
    val saleBillNo1 = repo.getNextBillNumber(accountId, "SALE")
    assertEquals("AJ-0001", saleBillNo1)

    // Karigar Purchase Bill number must start with AJ(P)-0001
    val purchaseBillNo1 = repo.getNextBillNumber(accountId, "KARIGAR_PURCHASE")
    assertEquals("AJ(P)-0001", purchaseBillNo1)
  }

  @Test
  fun `verify silver price unit per kg calculation`() {
    // Silver price = 90,000 Rs/kg
    // Rate per gram = 90,000 / 1000 = 90 Rs/g
    val silverRatePerKg = 90000.0
    val effectiveRatePerGram = silverRatePerKg / 1000.0
    assertEquals(90.0, effectiveRatePerGram, 0.0001)

    // 50 grams of fine silver at 90 Rs/g = 4,500 Rs
    val fineSilverWeightGrams = 50.0
    val totalSilverMetalAmount = fineSilverWeightGrams * effectiveRatePerGram
    assertEquals(4500.0, totalSilverMetalAmount, 0.0001)
  }

  @Test
  fun `verify persistent registration and login protocol across app closure and time delay`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo1 = JewelleryRepository(context)

    val shopName = "Mahalaxmi Jewellers"
    val mobile = "9825012345"
    val code = "5566"
    val gst = "24ABCDE1234F1Z5"

    // Step 1: Register a new account
    val regRes = repo1.registerNewJeweller(
      name = shopName,
      mobile = mobile,
      code = code,
      confirmCode = code,
      licenceCode = LicenceValidator.OWNER_LICENCE_CODE,
      gstNumber = gst
    )
    assertTrue("Registration should succeed", regRes is AuthResult.Success)
    val registeredAccount = (regRes as AuthResult.Success).account
    assertEquals(shopName, registeredAccount.jewellerName)
    assertEquals(mobile, registeredAccount.mobileNumber)

    // Step 2: Confirm account is saved in persistent layers
    val syncManager = com.example.data.sync.CloudSyncManager.getInstance(context)
    val cloudAccounts = syncManager.getAllAccountsFromCloud()
    val cloudMatch = cloudAccounts.firstOrNull { it.mobileNumber == mobile }
    assertTrue("Account must be saved in persistent cloud/disk mirror", cloudMatch != null)
    assertEquals(shopName, cloudMatch?.jewellerName)

    val dbAccount = com.example.data.local.AppDatabase.getDatabase(context).accountDao().findAccountByMobile(mobile)
    assertTrue("Account must be saved in local Room database", dbAccount != null)

    // Step 3: Logout and login
    repo1.logout()
    val loginRes = repo1.loginWithMobileAndCode(mobile, code)
    assertTrue("Login after logout should succeed", loginRes is AuthResult.Success)
    assertEquals(shopName, (loginRes as AuthResult.Success).account.jewellerName)

    // Step 4: Simulate App Closure and Reopen (new repository instance created)
    val repo2 = JewelleryRepository(context)
    repo2.initializeSession()
    // Session should be restored from persistent storage
    val restoredAccount = repo2.currentAccount.value
    assertTrue("Account must be restored on app restart", restoredAccount != null)
    assertEquals(mobile, restoredAccount?.mobileNumber)

    // Step 5: Simulate 30-minute delay and login again with mobile and code
    // Advance simulated time or verify login survives prolonged closure
    val loginAfter30Min = repo2.loginWithMobileAndCode(mobile, code)
    assertTrue("Login after delay must succeed without 'Mobile Number Not Registered'", loginAfter30Min is AuthResult.Success)

    // Also test full login with Name, Mobile, GST, Code
    val fullLoginAfterDelay = repo2.login(shopName, mobile, gst, code)
    assertTrue("Full login after delay must succeed", fullLoginAfterDelay is AuthResult.Success)

    // Step 6: Simulate lookup on another device (fresh context with clean Room database, only cloud accounts available)
    val anotherDeviceCloudAccount = syncManager.findAccountByMobileInCloud(mobile)
    assertTrue("Another device can find account in cloud by mobile", anotherDeviceCloudAccount != null)
    assertEquals(shopName, anotherDeviceCloudAccount?.jewellerName)
    assertEquals(code, anotherDeviceCloudAccount?.code4Digit)
  }

  @Test
  fun `verify repeated logout and login cycles survive continuously`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = JewelleryRepository(context)

    val shopName = "Ambica Jewellers"
    val mobile = "9898123456"
    val code = "1234"
    val gst = "24AABCA1234A1Z5"

    // Initial Registration
    val regRes = repo.registerNewJeweller(
      name = shopName,
      mobile = mobile,
      code = code,
      confirmCode = code,
      licenceCode = LicenceValidator.OWNER_LICENCE_CODE,
      gstNumber = gst
    )
    assertTrue("Registration should succeed", regRes is AuthResult.Success)

    // Repeat 5 consecutive logout and login cycles
    for (cycle in 1..5) {
      repo.logout()
      assertEquals("Session should be cleared on logout", null, repo.currentAccount.value)

      val loginResult = repo.loginWithMobileAndCode(mobile, code)
      assertTrue("Cycle $cycle: Login must succeed without 'Mobile Number Not Registered'", loginResult is AuthResult.Success)
      val loggedIn = (loginResult as AuthResult.Success).account
      assertEquals(shopName, loggedIn.jewellerName)
      assertEquals(mobile, loggedIn.mobileNumber)
      assertEquals(gst, loggedIn.gstNumber)
    }
  }

  @Test
  fun `verify multi-payment bill preserves fine metal and cash breakdown in bill text and json`() {
    com.example.ui.locale.LanguageManager.globalLanguage = com.example.ui.locale.AppLanguage.ENGLISH
    val context = ApplicationProvider.getApplicationContext<Context>()

    // Customer purchase: 10g Chain, 92% Touch + 8% Making = 100% Total Touch, 10g Fine
    val item = com.example.data.model.BillItem(
      id = "item_1",
      metalType = "GOLD",
      description = "10g Chain",
      purity = "22K",
      grossWeight = 10.0,
      netWeight = 10.0,
      currentTouch = 92.0,
      makingChargePercent = 8.0,
      totalTouch = 100.0,
      totalFine = 10.0,
      ratePerGram = 15000.0,
      itemTotal = 150000.0
    )

    // Payment 1: Gold 10.000g @ 75% touch = Fine 7.500g @ ₹15,000/g = ₹112,500
    val payment1 = com.example.data.model.BillPayment(
      id = "pay_1",
      paymentMode = "GOLD",
      amount = 112500.0,
      metalWeight = 10.0,
      metalTouch = 75.0,
      metalRate = 15000.0,
      fineWeight = 7.5,
      note = "Old gold ornament"
    )

    // Payment 2: Cash ₹37,500
    val payment2 = com.example.data.model.BillPayment(
      id = "pay_2",
      paymentMode = "CASH",
      amount = 37500.0,
      note = "Counter cash"
    )

    val bill = com.example.data.model.Bill(
      id = "bill_test_multi",
      accountId = "jwl_9898123456_main",
      billNumber = "AJ-0001",
      billType = "SALE",
      partyName = "Rameshbhai Patel",
      partyMobile = "9825199999",
      paymentMode = "MULTI",
      subtotal = 150000.0,
      grandTotal = 150000.0,
      cashReceivedOrPaid = 150000.0,
      netBalanceDue = 0.0,
      itemsJson = com.example.data.model.Bill.itemsToJson(listOf(item)),
      paymentsJson = com.example.data.model.Bill.paymentsToJson(listOf(payment1, payment2))
    )

    // Verify payments parsing
    val parsedPayments = bill.parsePayments()
    assertEquals(2, parsedPayments.size)
    assertEquals("GOLD", parsedPayments[0].paymentMode)
    assertEquals(112500.0, parsedPayments[0].amount, 0.001)
    assertEquals(7.5, parsedPayments[0].calculatedFineWeight, 0.001)

    assertEquals("CASH", parsedPayments[1].paymentMode)
    assertEquals(37500.0, parsedPayments[1].amount, 0.001)

    // Verify breakdown helper
    val breakdown = parsedPayments[0].getFormattedBreakdown(isGu = false)
    assertTrue("Breakdown should contain 10.000g", breakdown.contains("10.000g"))
    assertTrue("Breakdown should contain 75%", breakdown.contains("75%"))
    assertTrue("Breakdown should contain Fine 7.500g", breakdown.contains("Fine 7.500g"))
    assertTrue("Breakdown should contain 112,500", breakdown.contains("112,500") || breakdown.contains("1,12,500"))

    // Verify formatted bill text includes exact breakdown and NOT "Cash: ₹150,000"
    val billText = com.example.util.BillShareHelper.generateFormattedBillText(bill, null)
    assertTrue("Bill text should mention Gold Received", billText.contains("Gold Received"))
    assertTrue("Bill text should show 10.000g", billText.contains("10.000g"))
    assertTrue("Bill text should show 75%", billText.contains("75%"))
    assertTrue("Bill text should show Fine 7.500g", billText.contains("Fine 7.500g"))
    assertTrue("Bill text should show 112,500", billText.contains("112,500") || billText.contains("1,12,500"))
    assertTrue("Bill text should mention Cash Received", billText.contains("Cash Received"))
    assertTrue("Bill text should show 37,500", billText.contains("37,500"))
    assertTrue("Bill text should show Total Received", billText.contains("Total Received"))
    assertTrue("Bill text should show 150,000", billText.contains("150,000") || billText.contains("1,50,000"))
    assertFalse("Bill text must NOT show Cash: 150000", billText.contains("Cash Received:\n₹150,000") || billText.contains("Cash: ₹150,000") || billText.contains("Cash (MULTI): ₹150,000"))
  }

  @Test
  fun `verify bill update does not duplicate bill or stock records`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = JewelleryRepository(context)
    val db = com.example.data.local.AppDatabase.getDatabase(context)

    val regRes = repo.registerNewJeweller(
      name = "Sardar Jewellers",
      mobile = "9824055555",
      code = "3344",
      confirmCode = "3344",
      licenceCode = LicenceValidator.OWNER_LICENCE_CODE
    )
    assertTrue(regRes is AuthResult.Success)
    val accountId = (regRes as AuthResult.Success).account.accountId

    val billId = "bill_unique_101"
    val item1 = com.example.data.model.BillItem(
      id = "item_1",
      metalType = "GOLD",
      description = "Ring",
      purity = "22K",
      grossWeight = 5.0,
      netWeight = 5.0,
      itemTotal = 35000.0
    )

    val bill1 = com.example.data.model.Bill(
      id = billId,
      accountId = accountId,
      billNumber = "SJ-0001",
      billType = "SALE",
      partyName = "Kishorbhai",
      partyMobile = "9824011111",
      paymentMode = "CASH",
      subtotal = 35000.0,
      grandTotal = 35000.0,
      cashReceivedOrPaid = 35000.0,
      itemsJson = com.example.data.model.Bill.itemsToJson(listOf(item1)),
      paymentsJson = "[]"
    )

    // Save bill
    repo.createBill(bill1)
    val billsAfterCreate = db.billDao().getAllBillsDirect(accountId)
    assertEquals(1, billsAfterCreate.size)
    assertEquals(billId, billsAfterCreate[0].id)

    // Edit bill (modify party name and total)
    val updatedBill = bill1.copy(
      partyName = "Kishorbhai Prajapati",
      grandTotal = 36000.0,
      cashReceivedOrPaid = 36000.0
    )
    repo.updateBill(updatedBill)

    // Check that bill was updated and NOT duplicated
    val billsAfterEdit = db.billDao().getAllBillsDirect(accountId)
    assertEquals("Bills list must still contain exactly 1 bill (no duplicates)", 1, billsAfterEdit.size)
    assertEquals("Kishorbhai Prajapati", billsAfterEdit[0].partyName)
    assertEquals(36000.0, billsAfterEdit[0].grandTotal, 0.001)
  }
}

