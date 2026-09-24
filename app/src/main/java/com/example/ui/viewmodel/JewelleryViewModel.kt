package com.example.ui.viewmodel

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Bill
import com.example.data.model.BillPayment
import com.example.data.model.JewellerAccount
import com.example.data.model.JewellerSettings
import com.example.data.model.StockTransaction
import com.example.data.repository.AuthResult
import com.example.data.repository.JewelleryRepository
import com.example.data.sync.SyncStatus
import com.example.ui.locale.AppLanguage
import com.example.ui.locale.LanguageManager
import com.example.util.BluetoothPrinterHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class JewelleryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = JewelleryRepository(application)

    val currentAccount: StateFlow<JewellerAccount?> = repository.currentAccount
    val syncStatus: StateFlow<SyncStatus> = repository.syncStatus

    // Loading & message states
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    init {
        viewModelScope.launch {
            repository.initializeSession()
        }
    }

    fun clearMessage() {
        _userMessage.value = null
    }

    fun showMessage(msg: String) {
        _userMessage.value = msg
    }

    // Settings
    val settings: StateFlow<JewellerSettings?> = currentAccount.flatMapLatest { account ->
        if (account != null) {
            repository.getSettingsFlow(account.accountId)
        } else {
            flowOf(null)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Bills
    val allBills: StateFlow<List<Bill>> = currentAccount.flatMapLatest { account ->
        if (account != null) {
            repository.getAllBillsFlow(account.accountId)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val saleBills: StateFlow<List<Bill>> = allBills.map { bills ->
        bills.filter { it.billType == "SALE" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val karigarBills: StateFlow<List<Bill>> = allBills.map { bills ->
        bills.filter { it.billType == "KARIGAR_PURCHASE" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Stock Transactions for each category
    private fun getCategoryFlow(category: String): StateFlow<List<StockTransaction>> {
        return currentAccount.flatMapLatest { account ->
            if (account != null) {
                repository.getTransactionsByCategoryFlow(account.accountId, category)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    val goldJewelleryTransactions = getCategoryFlow("GOLD_JEWELLERY")
    val goldMetalTransactions = getCategoryFlow("GOLD_METAL")
    val silverJewelleryTransactions = getCategoryFlow("SILVER_JEWELLERY")
    val silverMetalTransactions = getCategoryFlow("SILVER_METAL")
    val cashTransactions = getCategoryFlow("CASH")

    val allStockTransactions: StateFlow<List<StockTransaction>> = currentAccount.flatMapLatest { account ->
        if (account != null) {
            repository.getAllTransactionsFlow(account.accountId)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Net Stock calculations
    fun calculateNetStock(transactions: List<StockTransaction>): Double {
        var net = 0.0
        for (tx in transactions) {
            if (tx.type == "CREDIT") {
                net += tx.quantityOrAmount
            } else if (tx.type == "DEBIT") {
                net -= tx.quantityOrAmount
            }
        }
        return net
    }

    val goldJewelleryNet = goldJewelleryTransactions.map { calculateNetStock(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val goldMetalNet = goldMetalTransactions.map { calculateNetStock(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val silverJewelleryNet = silverJewelleryTransactions.map { calculateNetStock(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val silverMetalNet = silverMetalTransactions.map { calculateNetStock(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val cashNet = cashTransactions.map { calculateNetStock(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Auth actions
    fun register(
        name: String,
        mobile: String,
        code: String,
        confirmCode: String,
        licenceCode: String,
        gstNumber: String = "",
        onSuccess: () -> Unit,
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            when (val res = repository.registerNewJeweller(name, mobile, code, confirmCode, licenceCode, gstNumber)) {
                is AuthResult.Success -> {
                    _isLoading.value = false
                    _userMessage.value = "રજીસ્ટ્રેશન સફળ રહ્યું! હવે લોગિન કરો. (Registration Successful)"
                    onSuccess()
                }
                is AuthResult.Error -> {
                    _isLoading.value = false
                    _userMessage.value = res.message
                    onError(res.message)
                }
            }
        }
    }

    fun login(
        name: String,
        mobile: String,
        gstNumber: String,
        code: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            when (val res = repository.login(name, mobile, gstNumber, code)) {
                is AuthResult.Success -> {
                    _isLoading.value = false
                    _userMessage.value = "Welcome ${res.account.jewellerName}"
                    onSuccess()
                }
                is AuthResult.Error -> {
                    _isLoading.value = false
                    _userMessage.value = res.message
                    onError(res.message)
                }
            }
        }
    }

    fun login(
        name: String,
        mobile: String,
        code: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit = {}
    ) {
        login(name, mobile, "", code, onSuccess, onError)
    }

    private val loginMutex = kotlinx.coroutines.sync.Mutex()

    fun loginWithMobileAndCode(
        mobile: String,
        code: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit = {}
    ) {
        if (_isLoading.value) return
        viewModelScope.launch {
            if (!loginMutex.tryLock()) return@launch
            try {
                _isLoading.value = true
                when (val res = repository.loginWithMobileAndCode(mobile, code)) {
                    is AuthResult.Success -> {
                        _isLoading.value = false
                        _userMessage.value = "Welcome ${res.account.jewellerName}"
                        onSuccess()
                    }
                    is AuthResult.Error -> {
                        _isLoading.value = false
                        _userMessage.value = res.message
                        onError(res.message)
                    }
                }
            } finally {
                loginMutex.unlock()
            }
        }
    }

    fun forgotPassword(
        name: String,
        mobile: String,
        gstNumber: String,
        newCode: String,
        confirmCode: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            when (val res = repository.resetPassword(name, mobile, gstNumber, newCode, confirmCode)) {
                is AuthResult.Success -> {
                    _isLoading.value = false
                    _userMessage.value = "4-Digit Code Updated Successfully!"
                    onSuccess()
                }
                is AuthResult.Error -> {
                    _isLoading.value = false
                    _userMessage.value = res.message
                    onError(res.message)
                }
            }
        }
    }

    fun forgotPassword(
        name: String,
        mobile: String,
        newCode: String,
        confirmCode: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit = {}
    ) {
        forgotPassword(name, mobile, "", newCode, confirmCode, onSuccess, onError)
    }

    fun changeSecurityCode(
        currentCode: String,
        newCode: String,
        confirmCode: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            when (val res = repository.changeSecurityCode(currentCode, newCode, confirmCode)) {
                is AuthResult.Success -> {
                    _isLoading.value = false
                    _userMessage.value = "Security Code changed successfully"
                    onSuccess()
                }
                is AuthResult.Error -> {
                    _isLoading.value = false
                    _userMessage.value = res.message
                    onError(res.message)
                }
            }
        }
    }

    fun getLastCredentials(): Pair<String, String> {
        return repository.getLastCredentials()
    }

    fun getLastCredentialsTriple(): Triple<String, String, String> {
        return repository.getLastCredentialsTriple()
    }

    fun logout(onLoggedOut: () -> Unit) {
        repository.logout()
        onLoggedOut()
    }

    fun deleteAccount(confirmCode: String, onDeleted: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.deleteCurrentAccount(confirmCode)
            _isLoading.value = false
            result.onSuccess {
                _userMessage.value = "એકાઉન્ટ સફળતાપૂર્વક ડિલીટ થયું (Account Deleted)"
                onDeleted()
            }.onFailure { err ->
                onError(err.message ?: "ડિલીટ કરવામાં ભૂલ આવી (Delete Failed)")
            }
        }
    }

    fun manualSync() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.manualSyncNow()
            _isLoading.value = false
            _userMessage.value = "ક્લાઉડ ડેટા સફળતાપૂર્વક સિન્ક થયો (Cloud Data Synced)"
        }
    }

    // Bill Operations
    suspend fun getBillById(id: String): Bill? = repository.getBillById(id)

    suspend fun getNextBillNumber(billType: String): String {
        val account = currentAccount.value
        if (account == null) {
            val isPurchase = billType.contains("PURCHASE", ignoreCase = true)
            return if (isPurchase) "AJ(P)-0001" else "AJ-0001"
        }
        return repository.getNextBillNumber(account.accountId, billType)
    }

    fun saveBill(
        bill: Bill,
        isEdit: Boolean,
        onSuccess: (Bill) -> Unit,
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                if (isEdit) {
                    repository.updateBill(bill)
                    _userMessage.value = "બિલ અપડેટ થયું (Bill Updated)"
                } else {
                    repository.createBill(bill)
                    _userMessage.value = "બિલ સફળતાપૂર્વક બન્યું (Bill Created)"
                }
                _isLoading.value = false
                onSuccess(bill)
            } catch (e: Exception) {
                _isLoading.value = false
                val msg = "ભૂલ આવી: ${e.message}"
                _userMessage.value = msg
                onError(msg)
            }
        }
    }

    fun deleteBill(bill: Bill, onDeleted: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.deleteBill(bill)
                _isLoading.value = false
                _userMessage.value = "બિલ રદ કર્યું અને સ્ટોક અપડેટ થયો (Bill Deleted & Stock Restored)"
                onDeleted()
            } catch (e: Exception) {
                _isLoading.value = false
                _userMessage.value = "ભૂલ: ${e.message}"
            }
        }
    }

    fun addAdditionalPayment(billId: String, payment: BillPayment, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.addAdditionalPayment(billId, payment)
                _isLoading.value = false
                _userMessage.value = "પેમેન્ટ એન્ટ્રી ઉમેરાઈ ગઈ (Payment entry added)"
                onComplete()
            } catch (e: Exception) {
                _isLoading.value = false
                _userMessage.value = "ભૂલ: ${e.message}"
            }
        }
    }

    fun updateAdditionalPayment(billId: String, updatedPayment: BillPayment, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.updateAdditionalPayment(billId, updatedPayment)
                _isLoading.value = false
                _userMessage.value = "પેમેન્ટ એન્ટ્રી અપડેટ થઈ (Payment entry updated)"
                onComplete()
            } catch (e: Exception) {
                _isLoading.value = false
                _userMessage.value = "ભૂલ: ${e.message}"
            }
        }
    }

    fun deleteAdditionalPayment(billId: String, paymentId: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.deleteAdditionalPayment(billId, paymentId)
                _isLoading.value = false
                _userMessage.value = "પેમેન્ટ એન્ટ્રી ડિલીટ થઈ (Payment entry deleted)"
                onComplete()
            } catch (e: Exception) {
                _isLoading.value = false
                _userMessage.value = "ભૂલ: ${e.message}"
            }
        }
    }

    fun setBillPayments(billId: String, newPayments: List<BillPayment>, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.setBillPayments(billId, newPayments)
                _isLoading.value = false
                _userMessage.value = "ચુકવણી વિગતો અપડેટ થઈ (Payment details updated)"
                onComplete()
            } catch (e: Exception) {
                _isLoading.value = false
                _userMessage.value = "ભૂલ: ${e.message}"
            }
        }
    }

    // Stock Manual Operations
    fun addManualStockEntry(
        type: String,
        category: String,
        amount: Double,
        unit: String,
        remark: String,
        onSuccess: () -> Unit
    ) {
        val account = currentAccount.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.addManualStockTransaction(
                    accountId = account.accountId,
                    type = type,
                    category = category,
                    amount = amount,
                    unit = unit,
                    remark = remark
                )
                _isLoading.value = false
                _userMessage.value = "${if (type == "CREDIT") "જમા (Credit)" else "ઉધાર (Debit)"} નોંધ ઉમેરાઈ"
                onSuccess()
            } catch (e: Exception) {
                _isLoading.value = false
                _userMessage.value = "ભૂલ: ${e.message}"
            }
        }
    }

    fun deleteStockEntry(tx: StockTransaction) {
        viewModelScope.launch {
            try {
                repository.deleteStockTransaction(tx)
                _userMessage.value = "નોંધ ડિલીટ કરી (Entry Deleted)"
            } catch (e: Exception) {
                _userMessage.value = "ભૂલ: ${e.message}"
            }
        }
    }

    fun recordCashToMetalPurchase(
        metalCategory: String,
        grams: Double,
        cashAmount: Double,
        remark: String,
        onSuccess: () -> Unit
    ) {
        val account = currentAccount.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.recordCashMetalPurchase(
                    accountId = account.accountId,
                    metalCategory = metalCategory,
                    grams = grams,
                    cashAmount = cashAmount,
                    remark = remark
                )
                _isLoading.value = false
                _userMessage.value = "રોકડ ઉધાર અને સોનું/ચાંદી જમા થયું (Cash Debited & Metal Credited)"
                onSuccess()
            } catch (e: Exception) {
                _isLoading.value = false
                _userMessage.value = "ભૂલ: ${e.message}"
            }
        }
    }

    // Opening Stock Operation
    fun setOpeningStock(
        category: String,
        amount: Double,
        unit: String,
        remark: String,
        onSuccess: () -> Unit
    ) {
        val account = currentAccount.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val finalRemark = if (remark.isBlank()) "Opening Stock (શરૂઆતનો સ્ટોક)" else "$remark (Opening Stock)"
                repository.addManualStockTransaction(
                    accountId = account.accountId,
                    type = "CREDIT",
                    category = category,
                    amount = amount,
                    unit = unit,
                    remark = finalRemark
                )
                _isLoading.value = false
                _userMessage.value = "શરૂઆતનો સ્ટોક સફળતાપૂર્વક સાચવ્યો (Opening Stock Saved)"
                onSuccess()
            } catch (e: Exception) {
                _isLoading.value = false
                _userMessage.value = "ભૂલ: ${e.message}"
            }
        }
    }

    // Language switch
    fun updateLanguage(langCode: String) {
        val appLang = AppLanguage.fromCode(langCode)
        LanguageManager.globalLanguage = appLang
        val current = settings.value ?: return
        viewModelScope.launch {
            try {
                repository.updateSettings(current.copy(language = langCode, updatedAt = System.currentTimeMillis()))
            } catch (_: Exception) {}
        }
    }

    // Settings Operation
    fun saveSettings(
        name: String,
        address: String,
        gstNumber: String,
        logoBase64: String?,
        contactNumber: String,
        goldRate22k: Double,
        silverRate: Double,
        language: String = "en",
        onSuccess: () -> Unit
    ) {
        val account = currentAccount.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val updated = JewellerSettings(
                    accountId = account.accountId,
                    jewellerName = name.trim().ifBlank { account.jewellerName },
                    address = address.trim(),
                    gstNumber = gstNumber.trim().uppercase(),
                    logoBase64 = logoBase64,
                    contactNumber = contactNumber.trim().ifBlank { account.mobileNumber },
                    goldRate22k = goldRate22k,
                    silverRate = silverRate,
                    language = language,
                    updatedAt = System.currentTimeMillis()
                )
                repository.updateSettings(updated)
                _isLoading.value = false
                _userMessage.value = "સેટિંગ્સ સફળતાપૂર્વક સાચવ્યાં (Settings Saved)"
                onSuccess()
            } catch (e: Exception) {
                _isLoading.value = false
                _userMessage.value = "સેટિંગ્સ સેવ કરવામાં ભૂલ: ${e.message}"
            }
        }
    }

    // Bluetooth Printing
    fun getPairedBluetoothPrinters(): List<BluetoothPrinterHelper.BluetoothPrinterDevice> {
        return BluetoothPrinterHelper.getPairedPrinters()
    }

    fun printBillViaBluetooth(
        device: BluetoothDevice,
        bill: Bill,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            val currentSettings = settings.value
            val res = BluetoothPrinterHelper.printBill(device, bill, currentSettings)
            _isLoading.value = false
            if (res.isSuccess) {
                _userMessage.value = "પ્રિન્ટ આદેશ મોકલાઈ ગયો (Printed Successfully)"
                onResult(true, "પ્રિન્ટ સફળ")
            } else {
                val errorMsg = res.exceptionOrNull()?.message ?: "કનેક્ટ કરવામાં અસમર્થ"
                _userMessage.value = "પ્રિન્ટિંગ નિષ્ફળ: $errorMsg"
                onResult(false, errorMsg)
            }
        }
    }
}
