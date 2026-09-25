package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.model.Bill
import com.example.data.model.BillPayment
import com.example.data.model.JewellerAccount
import com.example.data.model.JewellerSettings
import com.example.data.model.StockTransaction
import com.example.data.sync.CloudSyncManager
import com.example.data.sync.LicenceValidator
import com.example.data.sync.SyncStatus
import com.example.util.PhoneUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import com.example.ui.locale.loc
import com.example.ui.locale.LanguageManager

sealed class AuthResult {
    data class Success(val account: JewellerAccount) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

class JewelleryRepository(private val context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val accountDao = db.accountDao()
    private val settingsDao = db.settingsDao()
    private val billDao = db.billDao()
    private val stockDao = db.stockTransactionDao()
    private val cloudSync = CloudSyncManager.getInstance(context)

    private val sessionPrefs: SharedPreferences =
        context.getSharedPreferences("jewellery_active_session", Context.MODE_PRIVATE)

    // Permanent SharedPreferences registry: NEVER CLEARED ON LOGOUT
    private val permanentPrefs: SharedPreferences =
        context.getSharedPreferences("jeweller_permanent_registry", Context.MODE_PRIVATE)

    // High-speed thread-safe in-memory cache of accounts keyed by 10-digit mobile number
    private val accountMemoryCache = ConcurrentHashMap<String, JewellerAccount>()

    private val _currentAccount = MutableStateFlow<JewellerAccount?>(null)
    val currentAccount: StateFlow<JewellerAccount?> = _currentAccount.asStateFlow()

    val syncStatus: StateFlow<SyncStatus> = cloudSync.syncStatus

    private fun normalizePhone(raw: String): String {
        return PhoneUtil.normalizePhone(raw)
    }

    private fun normalizeCode(raw: String): String {
        return PhoneUtil.normalizeCode(raw)
    }

    private fun normalizeText(raw: String): String {
        return raw.trim().replace(Regex("\\s+"), " ")
    }

    private fun parseSingleAccountJson(json: String?): JewellerAccount? {
        if (json.isNullOrBlank() || json == "{}") return null
        return try {
            val obj = JSONObject(json)
            val id = obj.optString("accountId")
            val name = obj.optString("jewellerName")
            val mob = obj.optString("mobileNumber")
            if (id.isNotEmpty() && name.isNotEmpty() && mob.isNotEmpty()) {
                JewellerAccount(
                    accountId = id,
                    jewellerName = name,
                    mobileNumber = mob,
                    code4Digit = obj.optString("code4Digit"),
                    gstNumber = obj.optString("gstNumber", ""),
                    isLicensed = obj.optBoolean("isLicensed", true),
                    status = obj.optString("status", "ACTIVE"),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                )
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun parseAccountsArrayJson(json: String?): List<JewellerAccount> {
        val list = mutableListOf<JewellerAccount>()
        if (json.isNullOrBlank() || json == "[]") return list
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.optString("accountId")
                val name = obj.optString("jewellerName")
                val mob = obj.optString("mobileNumber")
                if (id.isNotEmpty() && name.isNotEmpty() && mob.isNotEmpty()) {
                    list.add(
                        JewellerAccount(
                            accountId = id,
                            jewellerName = name,
                            mobileNumber = mob,
                            code4Digit = obj.optString("code4Digit"),
                            gstNumber = obj.optString("gstNumber", ""),
                            isLicensed = obj.optBoolean("isLicensed", true),
                            status = obj.optString("status", "ACTIVE"),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return list
    }

    private fun getPermanentAccountsList(): List<JewellerAccount> {
        val raw = permanentPrefs.getString("registered_accounts_list", null)
        return parseAccountsArrayJson(raw)
    }

    private fun writeVaultFiles(data: String) {
        val files = listOf(
            File(context.filesDir, "jeweller_accounts_vault.json"),
            File(context.cacheDir, "jeweller_accounts_vault.json"),
            context.getDatabasePath("jewellery_billing_database").parentFile?.let { File(it, "jeweller_accounts_vault.json") }
        )
        for (f in files) {
            if (f == null) continue
            try {
                f.parentFile?.mkdirs()
                FileOutputStream(f).use { fos ->
                    fos.write(data.toByteArray())
                    fos.flush()
                    try { fos.fd.sync() } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
    }

    suspend fun saveAccountPermanently(account: JewellerAccount) {
        val cleanMob = PhoneUtil.normalizePhone(account.mobileNumber)
        val cleanGst = PhoneUtil.normalizeGst(account.gstNumber)
        if (cleanMob.isNotEmpty()) {
            accountMemoryCache[cleanMob] = account
        }

        // 1. Room DB
        try {
            accountDao.insertAccount(account)
        } catch (e: Exception) {
            Log.w("JewelleryRepository", "Room insertAccount error: ${e.message}")
        }

        // 2. Permanent SharedPreferences registry
        try {
            val accountJson = JSONObject().apply {
                put("accountId", account.accountId)
                put("jewellerName", account.jewellerName)
                put("mobileNumber", account.mobileNumber)
                put("code4Digit", account.code4Digit)
                put("gstNumber", account.gstNumber)
                put("isLicensed", account.isLicensed)
                put("status", account.status)
                put("createdAt", account.createdAt)
            }.toString()

            val editor = permanentPrefs.edit()
            if (cleanMob.isNotEmpty()) {
                editor.putString("account_$cleanMob", accountJson)
                editor.putString("code_$cleanMob", account.code4Digit)
            }
            if (cleanGst.isNotEmpty()) {
                editor.putString("account_gst_$cleanGst", accountJson)
                editor.putString("code_gst_$cleanGst", account.code4Digit)
            }
            editor.putString("last_registered_account", accountJson)
            editor.putString("last_registered_mobile", account.mobileNumber)
            editor.putString("last_registered_name", account.jewellerName)
            editor.putString("last_registered_gst", account.gstNumber)
            editor.putString("last_registered_code", account.code4Digit)

            val existing = getPermanentAccountsList().toMutableList()
            val idx = existing.indexOfFirst { it.accountId == account.accountId || PhoneUtil.normalizePhone(it.mobileNumber) == cleanMob }
            if (idx >= 0) {
                existing[idx] = account
            } else {
                existing.add(account)
            }

            val jsonArray = JSONArray()
            for (acc in existing) {
                jsonArray.put(JSONObject().apply {
                    put("accountId", acc.accountId)
                    put("jewellerName", acc.jewellerName)
                    put("mobileNumber", acc.mobileNumber)
                    put("code4Digit", acc.code4Digit)
                    put("gstNumber", acc.gstNumber)
                    put("isLicensed", acc.isLicensed)
                    put("status", acc.status)
                    put("createdAt", acc.createdAt)
                })
            }
            val listJson = jsonArray.toString()
            editor.putString("registered_accounts_list", listJson)
            editor.commit()

            // 3. Vault files
            writeVaultFiles(listJson)
        } catch (e: Exception) {
            Log.w("JewelleryRepository", "Permanent registry write error: ${e.message}")
        }

        // 4. CloudSync local mirror
        try {
            cloudSync.saveAccountToPersistentMirror(account)
        } catch (_: Exception) {}

        // 5. Session credentials
        try {
            sessionPrefs.edit()
                .putString("last_jeweller_name", account.jewellerName)
                .putString("last_mobile_number", account.mobileNumber)
                .putString("last_gst_number", account.gstNumber)
                .putString("last_code4digit", account.code4Digit)
                .commit()
        } catch (_: Exception) {}
    }

    private suspend fun loadAllKnownAccounts() {
        try {
            val list = mutableListOf<JewellerAccount>()
            // From Room
            try { list.addAll(accountDao.getAllAccounts()) } catch (_: Exception) {}
            // From permanentPrefs registered list
            list.addAll(getPermanentAccountsList())
            // From permanentPrefs direct keys
            for ((key, value) in permanentPrefs.all) {
                if (key.startsWith("account_") && value is String) {
                    parseSingleAccountJson(value)?.let { list.add(it) }
                }
            }
            parseSingleAccountJson(permanentPrefs.getString("last_registered_account", null))?.let { list.add(it) }
            // From cloudSync mirror
            list.addAll(cloudSync.getLocalMirroredAccounts())
            // From vault files
            val vaultFiles = listOf(
                File(context.filesDir, "jeweller_accounts_vault.json"),
                File(context.cacheDir, "jeweller_accounts_vault.json"),
                context.getDatabasePath("jewellery_billing_database").parentFile?.let { File(it, "jeweller_accounts_vault.json") }
            )
            for (f in vaultFiles) {
                if (f != null && f.exists()) {
                    try { list.addAll(parseAccountsArrayJson(f.readText())) } catch (_: Exception) {}
                }
            }
            for (acc in list) {
                val clean = PhoneUtil.normalizePhone(acc.mobileNumber)
                if (clean.isNotEmpty()) {
                    val existing = accountMemoryCache[clean]
                    if (existing == null || acc.createdAt >= existing.createdAt) {
                        accountMemoryCache[clean] = acc
                        try { accountDao.insertAccount(acc) } catch (_: Exception) {}
                    }
                }
            }
        } catch (_: Exception) {}
    }

    suspend fun getAllCandidateAccounts(targetMobile: String = "", targetGst: String = ""): List<JewellerAccount> = withContext(Dispatchers.IO) {
        loadAllKnownAccounts()
        val list = mutableListOf<JewellerAccount>()

        // 1. In-memory cache
        list.addAll(accountMemoryCache.values)

        // 2. Room DB
        try { list.addAll(accountDao.getAllAccounts()) } catch (_: Exception) {}
        if (targetMobile.isNotBlank()) {
            try { accountDao.findAccountByMobile(targetMobile)?.let { list.add(it) } } catch (_: Exception) {}
        }
        if (targetGst.isNotBlank()) {
            try { accountDao.findAccountByGst(targetGst)?.let { list.add(it) } } catch (_: Exception) {}
        }
        if (targetMobile.isNotBlank() && targetGst.isNotBlank()) {
            try { accountDao.findAccountByMobileAndGst(targetMobile, targetGst)?.let { list.add(it) } } catch (_: Exception) {}
        }

        // 3. Permanent SharedPreferences
        if (targetMobile.isNotBlank()) {
            parseSingleAccountJson(permanentPrefs.getString("account_$targetMobile", null))?.let { list.add(it) }
        }
        if (targetGst.isNotBlank()) {
            parseSingleAccountJson(permanentPrefs.getString("account_gst_$targetGst", null))?.let { list.add(it) }
        }
        parseSingleAccountJson(permanentPrefs.getString("last_registered_account", null))?.let { list.add(it) }
        list.addAll(getPermanentAccountsList())

        for ((key, value) in permanentPrefs.all) {
            if (key.startsWith("account_") && value is String) {
                parseSingleAccountJson(value)?.let { list.add(it) }
            }
        }

        // 4. Disk Vault files
        val vaultFiles = listOf(
            File(context.filesDir, "jeweller_accounts_vault.json"),
            File(context.cacheDir, "jeweller_accounts_vault.json"),
            context.getDatabasePath("jewellery_billing_database").parentFile?.let { File(it, "jeweller_accounts_vault.json") }
        )
        for (f in vaultFiles) {
            if (f != null && f.exists()) {
                try { list.addAll(parseAccountsArrayJson(f.readText())) } catch (_: Exception) {}
            }
        }

        // 5. CloudSync local persistent mirror
        list.addAll(cloudSync.getLocalMirroredAccounts())

        // Check if any candidate account already matches targetMobile or targetGst locally
        val cleanTargetMob = PhoneUtil.normalizeMobile(targetMobile)
        val cleanTargetGst = PhoneUtil.normalizeGst(targetGst)
        val hasLocalMatch = list.any { acc ->
            val accMob = PhoneUtil.normalizeMobile(acc.mobileNumber)
            val accGst = PhoneUtil.normalizeGst(acc.gstNumber)
            (cleanTargetMob.isNotEmpty() && accMob == cleanTargetMob) ||
            (cleanTargetGst.isNotEmpty() && accGst == cleanTargetGst)
        }

        // 6. Only if NO local match exists and network is available, query Cloud Firestore with short timeout
        if (!hasLocalMatch && cloudSync.isNetworkAvailable()) {
            if (cleanTargetMob.isNotEmpty() && cleanTargetGst.isNotEmpty()) {
                try {
                    val cloudRes = cloudSync.findAccountByMobileAndGstInCloud(cleanTargetMob, cleanTargetGst)
                    if (cloudRes is CloudSyncManager.CloudLookupResult.Found) {
                        list.add(cloudRes.account)
                    }
                } catch (_: Exception) {}
            } else if (cleanTargetMob.isNotEmpty()) {
                try { cloudSync.findAccountByMobileInCloud(cleanTargetMob)?.let { list.add(it) } } catch (_: Exception) {}
            } else if (cleanTargetGst.isNotEmpty()) {
                try { cloudSync.findAccountByGstInCloud(cleanTargetGst)?.let { list.add(it) } } catch (_: Exception) {}
            }
        }

        // Deduplicate by 10-digit mobile number, newest created first
        val distinctByMobile = list.sortedByDescending { it.createdAt }
            .distinctBy { PhoneUtil.normalizeMobile(it.mobileNumber) }

        // If a specific mobile is targeted, ensure it's prioritized at the top
        val prioritized = if (cleanTargetMob.isNotEmpty()) {
            distinctByMobile.sortedByDescending { PhoneUtil.normalizeMobile(it.mobileNumber) == cleanTargetMob }
        } else {
            distinctByMobile
        }

        return@withContext prioritized
    }

    suspend fun findAccountEverywhereByMobile(mobile: String): JewellerAccount? = withContext(Dispatchers.IO) {
        val cleanMobile = PhoneUtil.normalizeMobile(mobile)
        if (cleanMobile.length != 10) return@withContext null

        val isOnline = cloudSync.isNetworkAvailable()

        if (isOnline) {
            // ONLINE ORDER:
            // 1. Firestore/current cloud account FIRST
            try {
                val cloudMatch = cloudSync.findAccountByMobileInCloud(cleanMobile)
                if (cloudMatch != null && PhoneUtil.normalizeMobile(cloudMatch.mobileNumber) == cleanMobile) {
                    // Refresh and update all local persistence layers with the cloud account
                    accountMemoryCache[cleanMobile] = cloudMatch
                    try { accountDao.insertAccount(cloudMatch) } catch (_: Exception) {}
                    saveAccountPermanently(cloudMatch)
                    return@withContext cloudMatch
                }
            } catch (e: Exception) {
                Log.w("JewelleryRepository", "Cloud mobile lookup exception: ${e.message}")
            }

            // 2. Room database
            try {
                val direct = accountDao.findAccountByMobile(cleanMobile)
                if (direct != null && PhoneUtil.normalizeMobile(direct.mobileNumber) == cleanMobile) {
                    accountMemoryCache[cleanMobile] = direct
                    saveAccountPermanently(direct)
                    return@withContext direct
                }
                val localAccounts = accountDao.getAllAccounts()
                val matched = localAccounts.firstOrNull { PhoneUtil.normalizeMobile(it.mobileNumber) == cleanMobile }
                if (matched != null) {
                    accountMemoryCache[cleanMobile] = matched
                    saveAccountPermanently(matched)
                    return@withContext matched
                }
            } catch (_: Exception) {}

            // 3. SharedPreferences
            try {
                val directJson = permanentPrefs.getString("account_$cleanMobile", null)
                if (!directJson.isNullOrBlank()) {
                    parseSingleAccountJson(directJson)?.let { acc ->
                        if (PhoneUtil.normalizeMobile(acc.mobileNumber) == cleanMobile) {
                            accountMemoryCache[cleanMobile] = acc
                            try { accountDao.insertAccount(acc) } catch (_: Exception) {}
                            return@withContext acc
                        }
                    }
                }
                val lastAccJson = permanentPrefs.getString("last_registered_account", null)
                if (!lastAccJson.isNullOrBlank()) {
                    parseSingleAccountJson(lastAccJson)?.let { acc ->
                        if (PhoneUtil.normalizeMobile(acc.mobileNumber) == cleanMobile) {
                            accountMemoryCache[cleanMobile] = acc
                            try { accountDao.insertAccount(acc) } catch (_: Exception) {}
                            return@withContext acc
                        }
                    }
                }
                val permMatch = getPermanentAccountsList().firstOrNull { PhoneUtil.normalizeMobile(it.mobileNumber) == cleanMobile }
                if (permMatch != null) {
                    accountMemoryCache[cleanMobile] = permMatch
                    try { accountDao.insertAccount(permMatch) } catch (_: Exception) {}
                    return@withContext permMatch
                }
            } catch (_: Exception) {}

            // 4. local cloud mirror
            try {
                val mirrored = cloudSync.getLocalMirroredAccounts()
                val matched = mirrored.firstOrNull { PhoneUtil.normalizeMobile(it.mobileNumber) == cleanMobile }
                if (matched != null) {
                    accountMemoryCache[cleanMobile] = matched
                    try { accountDao.insertAccount(matched) } catch (_: Exception) {}
                    saveAccountPermanently(matched)
                    return@withContext matched
                }
            } catch (_: Exception) {}

            // 5. persistent files
            try {
                val vaultFiles = listOf(
                    File(context.filesDir, "jeweller_accounts_vault.json"),
                    File(context.cacheDir, "jeweller_accounts_vault.json"),
                    context.getDatabasePath("jewellery_billing_database").parentFile?.let { File(it, "jeweller_accounts_vault.json") }
                )
                for (f in vaultFiles) {
                    if (f != null && f.exists()) {
                        val raw = f.readText()
                        val parsed = parseAccountsArrayJson(raw)
                        val match = parsed.firstOrNull { PhoneUtil.normalizeMobile(it.mobileNumber) == cleanMobile }
                        if (match != null) {
                            accountMemoryCache[cleanMobile] = match
                            try { accountDao.insertAccount(match) } catch (_: Exception) {}
                            saveAccountPermanently(match)
                            return@withContext match
                        }
                    }
                }
            } catch (_: Exception) {}

            // Fallback: memory cache
            accountMemoryCache[cleanMobile]?.let { return@withContext it }
        } else {
            // OFFLINE ORDER:
            // 1. Room
            try {
                val direct = accountDao.findAccountByMobile(cleanMobile)
                if (direct != null && PhoneUtil.normalizeMobile(direct.mobileNumber) == cleanMobile) {
                    accountMemoryCache[cleanMobile] = direct
                    return@withContext direct
                }
                val localAccounts = accountDao.getAllAccounts()
                val matched = localAccounts.firstOrNull { PhoneUtil.normalizeMobile(it.mobileNumber) == cleanMobile }
                if (matched != null) {
                    accountMemoryCache[cleanMobile] = matched
                    return@withContext matched
                }
            } catch (_: Exception) {}

            // 2. SharedPreferences
            try {
                val directJson = permanentPrefs.getString("account_$cleanMobile", null)
                if (!directJson.isNullOrBlank()) {
                    parseSingleAccountJson(directJson)?.let { acc ->
                        if (PhoneUtil.normalizeMobile(acc.mobileNumber) == cleanMobile) {
                            accountMemoryCache[cleanMobile] = acc
                            try { accountDao.insertAccount(acc) } catch (_: Exception) {}
                            return@withContext acc
                        }
                    }
                }
                val lastAccJson = permanentPrefs.getString("last_registered_account", null)
                if (!lastAccJson.isNullOrBlank()) {
                    parseSingleAccountJson(lastAccJson)?.let { acc ->
                        if (PhoneUtil.normalizeMobile(acc.mobileNumber) == cleanMobile) {
                            accountMemoryCache[cleanMobile] = acc
                            try { accountDao.insertAccount(acc) } catch (_: Exception) {}
                            return@withContext acc
                        }
                    }
                }
                val permMatch = getPermanentAccountsList().firstOrNull { PhoneUtil.normalizeMobile(it.mobileNumber) == cleanMobile }
                if (permMatch != null) {
                    accountMemoryCache[cleanMobile] = permMatch
                    try { accountDao.insertAccount(permMatch) } catch (_: Exception) {}
                    return@withContext permMatch
                }
            } catch (_: Exception) {}

            // 3. local cloud mirror
            try {
                val mirrored = cloudSync.getLocalMirroredAccounts()
                val matched = mirrored.firstOrNull { PhoneUtil.normalizeMobile(it.mobileNumber) == cleanMobile }
                if (matched != null) {
                    accountMemoryCache[cleanMobile] = matched
                    try { accountDao.insertAccount(matched) } catch (_: Exception) {}
                    saveAccountPermanently(matched)
                    return@withContext matched
                }
            } catch (_: Exception) {}

            // 4. persistent files
            try {
                val vaultFiles = listOf(
                    File(context.filesDir, "jeweller_accounts_vault.json"),
                    File(context.cacheDir, "jeweller_accounts_vault.json"),
                    context.getDatabasePath("jewellery_billing_database").parentFile?.let { File(it, "jeweller_accounts_vault.json") }
                )
                for (f in vaultFiles) {
                    if (f != null && f.exists()) {
                        val raw = f.readText()
                        val parsed = parseAccountsArrayJson(raw)
                        val match = parsed.firstOrNull { PhoneUtil.normalizeMobile(it.mobileNumber) == cleanMobile }
                        if (match != null) {
                            accountMemoryCache[cleanMobile] = match
                            try { accountDao.insertAccount(match) } catch (_: Exception) {}
                            saveAccountPermanently(match)
                            return@withContext match
                        }
                    }
                }
            } catch (_: Exception) {}

            // 5. memory cache
            accountMemoryCache[cleanMobile]?.let { return@withContext it }
        }

        return@withContext null
    }

    suspend fun adoptOrphanData(accountId: String) = withContext(Dispatchers.IO) {
        try {
            val orphanTxCount = stockDao.getOrphanTransactionCount()
            if (orphanTxCount > 0) {
                stockDao.adoptOrphanTransactions(accountId)
                Log.d("JewelleryRepository", "Adopted $orphanTxCount orphan stock transactions to account $accountId")
            }

            val orphanBillCount = billDao.getOrphanBillCount()
            if (orphanBillCount > 0) {
                billDao.adoptOrphanBills(accountId)
                Log.d("JewelleryRepository", "Adopted $orphanBillCount orphan bills to account $accountId")
            }

            val currentSettings = settingsDao.getSettingsDirect(accountId)
            if (currentSettings == null) {
                settingsDao.adoptOrphanSettings(accountId)
            }
        } catch (e: Exception) {
            Log.w("JewelleryRepository", "adoptOrphanData error: ${e.message}")
        }
    }

    suspend fun initializeSession() = withContext(Dispatchers.IO) {
        loadAllKnownAccounts()

        val savedAccountId = sessionPrefs.getString("logged_in_account_id", null)
        var account: JewellerAccount? = null

        if (!savedAccountId.isNullOrBlank()) {
            account = accountDao.getAccountById(savedAccountId)
        }

        if (account == null) {
            val savedMobile = sessionPrefs.getString("logged_in_mobile", null)
                ?: sessionPrefs.getString("last_mobile_number", null)
                ?: permanentPrefs.getString("last_registered_mobile", null)
            if (!savedMobile.isNullOrBlank()) {
                account = findAccountEverywhereByMobile(savedMobile)
            }
        }

        if (account == null) {
            val savedName = sessionPrefs.getString("logged_in_name", null)
                ?: sessionPrefs.getString("last_jeweller_name", null)
                ?: permanentPrefs.getString("last_registered_name", null)
            val savedMobile = sessionPrefs.getString("logged_in_mobile", null)
                ?: sessionPrefs.getString("last_mobile_number", null)
                ?: permanentPrefs.getString("last_registered_mobile", null)
            if (!savedMobile.isNullOrBlank() || !savedName.isNullOrBlank()) {
                account = cloudSync.findAccountInCloud(savedName ?: "", savedMobile ?: "")
            }
        }

        // Auto-recover account on single-shop device if session was cleared
        if (account == null) {
            val allLocal = try { accountDao.getAllAccounts() } catch (_: Exception) { emptyList() }
            val permAccounts = getPermanentAccountsList() + accountMemoryCache.values
            account = (allLocal + permAccounts).firstOrNull()
        }

        if (account != null) {
            saveAccountPermanently(account)
            adoptOrphanData(account.accountId)
            _currentAccount.value = account
            sessionPrefs.edit()
                .putString("logged_in_account_id", account.accountId)
                .putString("logged_in_name", account.jewellerName)
                .putString("logged_in_mobile", account.mobileNumber)
                .putString("logged_in_gst", account.gstNumber)
                .putString("last_jeweller_name", account.jewellerName)
                .putString("last_mobile_number", account.mobileNumber)
                .putString("last_gst_number", account.gstNumber)
                .commit()

            cloudSync.startPeriodicAutoSync(account.accountId)
        }
    }

    suspend fun registerNewJeweller(
        name: String,
        mobile: String,
        code: String,
        confirmCode: String,
        licenceCode: String,
        gstNumber: String
    ): AuthResult = withContext(Dispatchers.IO) {
        val cleanName = normalizeText(name)
        val cleanMobile = PhoneUtil.normalizeMobile(mobile)
        val cleanGst = PhoneUtil.normalizeGst(gstNumber)
        val cleanCode = PhoneUtil.normalizeCode(code)
        val cleanConfirm = PhoneUtil.normalizeCode(confirmCode)
        val cleanLicence = licenceCode.trim()

        if (cleanName.isEmpty()) return@withContext AuthResult.Error(loc("Please enter Jeweller/Shop Name.", "કૃપા કરીને ઝવેરી/દુકાનનું નામ દાખલ કરો."))
        if (cleanMobile.length != 10) return@withContext AuthResult.Error(loc("Please enter a valid 10-digit mobile number.", "કૃપા કરીને માન્ય 10 અંકનો મોબાઈલ નંબર દાખલ કરો."))
        if (cleanGst.isEmpty()) return@withContext AuthResult.Error(loc("GST Number is mandatory.", "જીએસટી નંબર આવશ્યક છે."))
        if (cleanCode.length != 4) return@withContext AuthResult.Error(loc("Please enter a 4-digit code.", "4 અંકનો કોડ દાખલ કરો."))
        if (cleanCode != cleanConfirm) return@withContext AuthResult.Error(loc("Codes do not match. Both codes must be identical.", "કોડ મેળ ખાતો નથી. બંને કોડ સમાન હોવા જોઈએ."))
        if (cleanLicence.isEmpty()) return@withContext AuthResult.Error(loc("Licence code is required.", "લાઇસન્સ કોડ આવશ્યક છે."))

        // Validate Licence Code using owner validation system (strictly 2330)
        if (!LicenceValidator.isValidLicence(cleanLicence)) {
            return@withContext AuthResult.Error(loc("Invalid Licence Code", "ખોટો લાઇસન્સ કોડ"))
        }

        // Check if account already exists locally or in cloud
        val localAccounts = try { accountDao.getAllAccounts() } catch (e: Exception) { emptyList() }
        val permAccounts = getPermanentAccountsList() + accountMemoryCache.values
        val cloudAccounts = cloudSync.getAllAccountsFromCloud()
        val allAccounts = (localAccounts + permAccounts + cloudAccounts)

        // Find existing account ONLY by this exact mobile number
        val existingAccount = allAccounts.firstOrNull {
            PhoneUtil.normalizeMobile(it.mobileNumber) == cleanMobile
        } ?: cloudSync.findAccountByMobileInCloud(cleanMobile)

        // Reuse existing accountId if re-registering same mobile, otherwise unique ID
        val cleanGstSafe = cleanGst.filter { it.isLetterOrDigit() }.take(15)
        val targetAccountId = existingAccount?.accountId ?: "jwl_${cleanMobile}_${cleanGstSafe.ifEmpty { "reg" }}"

        val finalAccount = JewellerAccount(
            accountId = targetAccountId,
            jewellerName = cleanName,
            mobileNumber = cleanMobile,
            code4Digit = cleanCode,
            gstNumber = cleanGst,
            isLicensed = true,
            status = "ACTIVE",
            createdAt = existingAccount?.createdAt ?: System.currentTimeMillis()
        )

        try {
            saveAccountPermanently(finalAccount)

            var settings = settingsDao.getSettingsDirect(targetAccountId)
            if (settings == null) {
                settings = JewellerSettings(
                    accountId = targetAccountId,
                    jewellerName = cleanName,
                    contactNumber = cleanMobile,
                    address = "",
                    gstNumber = cleanGst,
                    goldRate22k = 0.0,
                    silverRate = 0.0,
                    updatedAt = System.currentTimeMillis()
                )
                try { settingsDao.insertOrUpdate(settings) } catch (_: Exception) {}
            } else {
                settings = settings.copy(
                    jewellerName = cleanName,
                    contactNumber = cleanMobile,
                    gstNumber = cleanGst,
                    updatedAt = System.currentTimeMillis()
                )
                try { settingsDao.insertOrUpdate(settings) } catch (_: Exception) {}
            }

            val cloudSuccess = try {
                cloudSync.registerAccountToCloud(finalAccount)
            } catch (e: Exception) {
                Log.w("JewelleryRepository", "registerAccountToCloud exception: ${e.message}")
                false
            }
            if (!cloudSuccess) {
                cloudSync.enqueueAccount(finalAccount)
            }

            val settingsSuccess = try {
                cloudSync.syncSettingsToCloud(settings)
            } catch (_: Exception) {
                false
            }
            if (!settingsSuccess) {
                cloudSync.enqueueSettings(settings)
            }

            adoptOrphanData(targetAccountId)

            try { cloudSync.restoreFullAccountFromCloud(targetAccountId) } catch (_: Exception) {}

            sessionPrefs.edit()
                .putString("logged_in_account_id", finalAccount.accountId)
                .putString("logged_in_name", finalAccount.jewellerName)
                .putString("logged_in_mobile", finalAccount.mobileNumber)
                .putString("logged_in_gst", finalAccount.gstNumber)
                .putString("last_jeweller_name", finalAccount.jewellerName)
                .putString("last_mobile_number", finalAccount.mobileNumber)
                .putString("last_gst_number", finalAccount.gstNumber)
                .commit()

            _currentAccount.value = finalAccount
            cloudSync.startPeriodicAutoSync(finalAccount.accountId)

            AuthResult.Success(finalAccount)
        } catch (e: Exception) {
            Log.e("JewelleryRepository", "Registration save failed", e)
            AuthResult.Error(loc("Registration failed: ${e.message}", "રજીસ્ટ્રેશન નિષ્ફળ ગયું: ${e.message}"))
        }
    }

    /**
     * Login using Mobile Number (+ optional GST No.) + 4-Digit Code.
     * Enforces Single Account Identity Rule:
     * - Mobile Number is normalized to 10 digits
     * - 4-Digit Code is verified against the registered code
     * - Stock transactions and bills are adopted to prevent any data loss
     */
    suspend fun login(mobile: String, gstNumber: String = "", code: String): AuthResult = withContext(Dispatchers.IO) {
        val cleanMobile = PhoneUtil.normalizeMobile(mobile)
        val cleanGst = PhoneUtil.normalizeGst(gstNumber)
        val cleanCode = PhoneUtil.normalizeCode(code)

        if (cleanMobile.length != 10) {
            return@withContext AuthResult.Error(loc("Please enter a valid 10-digit mobile number.", "કૃપા કરીને માન્ય 10 અંકનો મોબાઈલ નંબર દાખલ કરો."))
        }
        if (cleanCode.length != 4) {
            return@withContext AuthResult.Error(loc("Please enter 4-digit code.", "૪ અંકનો કોડ દાખલ કરો."))
        }

        // 1. Search account by normalized mobile FIRST (online or offline priority via findAccountEverywhereByMobile)
        var matchedAccount: JewellerAccount? = findAccountEverywhereByMobile(cleanMobile)

        // 2. If not found and GST was provided, try candidate accounts matching mobile & GST
        if (matchedAccount == null && cleanGst.isNotBlank()) {
            val candidates = getAllCandidateAccounts(cleanMobile, cleanGst)
            matchedAccount = candidates.firstOrNull {
                PhoneUtil.normalizeMobile(it.mobileNumber) == cleanMobile &&
                PhoneUtil.normalizeGst(it.gstNumber) == cleanGst
            } ?: candidates.firstOrNull {
                PhoneUtil.normalizeMobile(it.mobileNumber) == cleanMobile
            }
        }

        // 3. Fallback: search candidate accounts by mobile alone
        if (matchedAccount == null) {
            val candidates = getAllCandidateAccounts(cleanMobile, cleanGst)
            matchedAccount = candidates.firstOrNull {
                PhoneUtil.normalizeMobile(it.mobileNumber) == cleanMobile
            }
        }

        // 4. Try matching with last registered account in permanentPrefs or sessionPrefs
        if (matchedAccount == null) {
            val lastRegMob = PhoneUtil.normalizeMobile(
                permanentPrefs.getString("last_registered_mobile", null)
                    ?: sessionPrefs.getString("last_mobile_number", null)
            )
            if (lastRegMob == cleanMobile) {
                matchedAccount = parseSingleAccountJson(permanentPrefs.getString("last_registered_account", null))
            }
        }

        if (matchedAccount != null) {
            // Verify mobile consistency
            if (PhoneUtil.normalizeMobile(matchedAccount.mobileNumber) != cleanMobile) {
                return@withContext AuthResult.Error(
                    loc("Mobile Number Not Registered.", "મોબાઈલ નંબર રજીસ્ટર્ડ નથી.")
                )
            }

            // If user explicitly entered a GST that differs from registered non-blank GST
            val currentAccGst = PhoneUtil.normalizeGst(matchedAccount.gstNumber)
            if (cleanGst.isNotBlank() && currentAccGst.isNotBlank() && currentAccGst != cleanGst) {
                return@withContext AuthResult.Error(
                    loc("Mobile Number Not Registered.", "મોબાઈલ નંબર રજીસ્ટર્ડ નથી.")
                )
            }

            // Verify 4-digit code ONLY (strictly remove 2330 / M30P23 owner bypass)
            val storedCode = PhoneUtil.normalizeCode(matchedAccount.code4Digit)
            if (storedCode.isNotEmpty() && cleanCode != storedCode) {
                return@withContext AuthResult.Error(loc("Incorrect 4-Digit Code.", "૪ અંકનો સિક્યુરિટી કોડ ખોટો છે."))
            }

            // Login succeeds!
            val updatedGst = if (cleanGst.isNotBlank()) cleanGst else matchedAccount.gstNumber
            val normalizedAcc = matchedAccount.copy(
                mobileNumber = cleanMobile,
                gstNumber = updatedGst,
                code4Digit = cleanCode,
                isLicensed = true,
                status = "ACTIVE"
            )

            saveAccountPermanently(normalizedAcc)
            val cloudReg = cloudSync.registerAccountToCloud(normalizedAcc)
            if (!cloudReg) {
                cloudSync.enqueueAccount(normalizedAcc)
            }
            adoptOrphanData(normalizedAcc.accountId)

            try {
                cloudSync.restoreFullAccountFromCloud(normalizedAcc.accountId)
            } catch (e: Exception) {
                Log.w("JewelleryRepository", "restoreFullAccountFromCloud skipped: ${e.message}")
            }

            sessionPrefs.edit()
                .putString("logged_in_account_id", normalizedAcc.accountId)
                .putString("logged_in_name", normalizedAcc.jewellerName)
                .putString("logged_in_mobile", normalizedAcc.mobileNumber)
                .putString("logged_in_gst", normalizedAcc.gstNumber)
                .putString("last_jeweller_name", normalizedAcc.jewellerName)
                .putString("last_mobile_number", normalizedAcc.mobileNumber)
                .putString("last_gst_number", normalizedAcc.gstNumber)
                .commit()

            _currentAccount.value = normalizedAcc
            cloudSync.startPeriodicAutoSync(normalizedAcc.accountId)
            return@withContext AuthResult.Success(normalizedAcc)
        }

        return@withContext AuthResult.Error(
            loc("Mobile Number Not Registered.", "મોબાઈલ નંબર રજીસ્ટર્ડ નથી.")
        )
    }

    suspend fun login(name: String, mobile: String, gstNumber: String, code: String): AuthResult {
        return login(mobile, gstNumber, code)
    }

    suspend fun loginWithMobileAndCode(mobile: String, code: String): AuthResult {
        return login(mobile, "", code)
    }

    suspend fun resetPassword(
        mobile: String,
        gstNumber: String,
        newCode: String,
        confirmCode: String
    ): AuthResult = withContext(Dispatchers.IO) {
        val cleanMobile = PhoneUtil.normalizeMobile(mobile)
        val cleanGst = PhoneUtil.normalizeGst(gstNumber)
        val cleanNewCode = PhoneUtil.normalizeCode(newCode)
        val cleanConfirm = PhoneUtil.normalizeCode(confirmCode)

        if (cleanMobile.length != 10) return@withContext AuthResult.Error(loc("Please enter valid 10-digit mobile number.", "માન્ય 10 અંકનો મોબાઈલ નંબર દાખલ કરો."))
        if (cleanGst.isEmpty()) return@withContext AuthResult.Error(loc("Please enter GST Number.", "કૃપા કરીને જીએસટી નંબર દાખલ કરો."))
        if (cleanNewCode.length != 4) return@withContext AuthResult.Error(loc("Please enter 4-digit new code.", "4 અંકનો નવો કોડ દાખલ કરો."))
        if (cleanNewCode != cleanConfirm) return@withContext AuthResult.Error(loc("Codes do not match.", "બંને કોડ મેળ ખાતા નથી."))

        // Look up account using all candidates
        val candidates = getAllCandidateAccounts(cleanMobile, cleanGst)
        var account = candidates.firstOrNull {
            PhoneUtil.normalizeMobile(it.mobileNumber) == cleanMobile &&
            PhoneUtil.normalizeGst(it.gstNumber) == cleanGst
        }

        if (account == null) {
            val result = cloudSync.findAccountByMobileAndGstInCloud(cleanMobile, cleanGst)
            if (result is CloudSyncManager.CloudLookupResult.Found) {
                account = result.account
            }
        }

        if (account == null) {
            return@withContext AuthResult.Error(
                loc("Account not found. Please check your Mobile Number and GST No.", "એકાઉન્ટ મળ્યું નથી. કૃપા કરીને તમારો મોબાઈલ નંબર અને GST નંબર ચકાસો.")
            )
        }

        val updatedAccount = account.copy(code4Digit = cleanNewCode, gstNumber = cleanGst)
        saveAccountPermanently(updatedAccount)
        cloudSync.updateCodeInCloud(account.accountId, cleanNewCode, cleanMobile, cleanGst)

        if (_currentAccount.value?.accountId == account.accountId) {
            _currentAccount.value = updatedAccount
        }

        AuthResult.Success(updatedAccount)
    }

    suspend fun resetPassword(name: String, mobile: String, gstNumber: String, newCode: String, confirmCode: String): AuthResult {
        return resetPassword(mobile, gstNumber, newCode, confirmCode)
    }

    suspend fun changeSecurityCode(
        currentCode: String,
        newCode: String,
        confirmCode: String
    ): AuthResult = withContext(Dispatchers.IO) {
        val currentAccount = _currentAccount.value
            ?: return@withContext AuthResult.Error(loc("No user currently logged in.", "કોઈ વપરાશકર્તા લૉગિન નથી."))

        val cleanCurrent = PhoneUtil.normalizeCode(currentCode)
        val cleanNew = PhoneUtil.normalizeCode(newCode)
        val cleanConfirm = PhoneUtil.normalizeCode(confirmCode)

        if (cleanCurrent != PhoneUtil.normalizeCode(currentAccount.code4Digit)) {
            return@withContext AuthResult.Error(loc("Current 4-Digit Code is incorrect.", "હાલનો ૪-અંકનો કોડ ખોટો છે."))
        }
        if (cleanNew.length != 4) {
            return@withContext AuthResult.Error(loc("New code must be exactly 4 digits.", "નવો કોડ બરાબર ૪ અંકનો હોવો જોઈએ."))
        }
        if (cleanNew != cleanConfirm) {
            return@withContext AuthResult.Error(loc("New codes do not match.", "નવા કોડ મેળ ખાતા નથી."))
        }

        val updatedAccount = currentAccount.copy(code4Digit = cleanNew)
        saveAccountPermanently(updatedAccount)
        cloudSync.updateCodeInCloud(currentAccount.accountId, cleanNew)
        _currentAccount.value = updatedAccount

        AuthResult.Success(updatedAccount)
    }

    fun logout() {
        cloudSync.stopPeriodicAutoSync()
        cloudSync.stopRealtimeCloudSync()
        clearSession()
    }

    suspend fun deleteCurrentAccount(confirmCode: String): Result<Unit> = withContext(Dispatchers.IO) {
        val current = _currentAccount.value ?: return@withContext Result.failure(
            Exception(loc("No account is currently logged in.", "હાલમાં કોઈ એકાઉન્ટ લૉગિન નથી."))
        )
        if (PhoneUtil.normalizeCode(confirmCode) != PhoneUtil.normalizeCode(current.code4Digit)) {
            return@withContext Result.failure(
                Exception(loc("Incorrect 4-digit code. Please enter your correct code to confirm deletion.", "ખોટો 4-અંકનો સિક્યુરિટી કોડ. ડિલીટ કરવા માટે સાચો કોડ દાખલ કરો."))
            )
        }

        try {
            // 1. Stop realtime sync and periodic auto-sync
            cloudSync.stopPeriodicAutoSync()
            cloudSync.stopRealtimeCloudSync()

            val accountId = current.accountId
            val cleanMob = PhoneUtil.normalizeMobile(current.mobileNumber)
            val cleanGst = PhoneUtil.normalizeGst(current.gstNumber)

            // 2. Delete local Room records for this account
            try { accountDao.deleteAccountById(accountId) } catch (e: Exception) { Log.w("JewelleryRepository", "deleteAccountById error: ${e.message}") }
            try { settingsDao.deleteSettingsByAccountId(accountId) } catch (e: Exception) { Log.w("JewelleryRepository", "deleteSettings error: ${e.message}") }
            try { billDao.deleteAllBillsForAccount(accountId) } catch (e: Exception) { Log.w("JewelleryRepository", "deleteAllBills error: ${e.message}") }
            try { stockDao.deleteAllTransactionsForAccount(accountId) } catch (e: Exception) { Log.w("JewelleryRepository", "deleteAllTransactions error: ${e.message}") }

            // 3. Clear from memory & permanent registry
            if (cleanMob.isNotEmpty()) {
                accountMemoryCache.remove(cleanMob)
                permanentPrefs.edit()
                    .remove("account_$cleanMob")
                    .remove("code_$cleanMob")
                    .commit()
            }

            // 4. Delete from Cloud and persistent mirrors (including indexes by_mobile and by_gst)
            cloudSync.deleteAccountFromCloud(accountId, cleanMob, cleanGst)

            // 5. Clear active session
            clearSession()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("JewelleryRepository", "deleteCurrentAccount failed", e)
            Result.failure(e)
        }
    }

    private fun clearSession() {
        // ONLY clear active login session keys!
        // NEVER clear last_jeweller_name, last_mobile_number, last_gst_number, or permanent registry!
        sessionPrefs.edit()
            .remove("logged_in_account_id")
            .remove("logged_in_name")
            .remove("logged_in_mobile")
            .remove("logged_in_gst")
            .commit()
        _currentAccount.value = null
    }

    suspend fun manualSyncNow() = withContext(Dispatchers.IO) {
        val current = _currentAccount.value ?: return@withContext
        cloudSync.performFullTwoWayAutoSync(current.accountId)
    }

    // Settings
    fun getSettingsFlow(accountId: String): Flow<JewellerSettings?> = settingsDao.getSettingsFlow(accountId)

    suspend fun getSettingsDirect(accountId: String): JewellerSettings? = withContext(Dispatchers.IO) {
        settingsDao.getSettingsDirect(accountId)
    }

    suspend fun updateSettings(settings: JewellerSettings) = withContext(Dispatchers.IO) {
        settingsDao.insertOrUpdate(settings)
        cloudSync.syncSettingsToCloud(settings)
        cloudSync.triggerAutoSync(settings.accountId)
    }

    // Bills
    fun getAllBillsFlow(accountId: String): Flow<List<Bill>> = billDao.getAllBillsFlow(accountId)

    fun getBillsByTypeFlow(accountId: String, type: String): Flow<List<Bill>> = billDao.getBillsByTypeFlow(accountId, type)

    suspend fun getBillById(id: String): Bill? = withContext(Dispatchers.IO) {
        billDao.getBillById(id)
    }

    suspend fun getNextBillNumber(accountId: String, billType: String): String = withContext(Dispatchers.IO) {
        // Fetch current jeweller name for this account to compute initials
        val account = accountDao.getAccountById(accountId) ?: _currentAccount.value
        val jewellerName = account?.jewellerName?.ifBlank {
            try { settingsDao.getSettingsDirect(accountId)?.jewellerName ?: "" } catch (_: Exception) { "" }
        } ?: ""

        // Compute Initials prefix from Jeweller Name
        // Example: "Amit Jewellers" -> "AJ", "Radhe Krishna Jewellers" -> "RKJ"
        val words = jewellerName.trim().split(Regex("[^a-zA-Z0-9]+")).filter { it.isNotBlank() }
        val initials = when {
            words.isEmpty() -> "AJ"
            words.size == 1 -> {
                val w = words[0]
                if (w.length >= 2) w.take(2).uppercase() else "${w.uppercase()}J"
            }
            else -> words.map { it.first().uppercaseChar() }.joinToString("")
        }

        val isPurchase = billType.equals("KARIGAR_PURCHASE", ignoreCase = true) ||
                         billType.equals("PURCHASE", ignoreCase = true)
        val prefix = if (isPurchase) "$initials(P)-" else "$initials-"

        // Gather all existing bill numbers for this account from local Room DB and Cloud Store
        val allLocalBills: List<Bill> = try { billDao.getAllBillsDirect(accountId) } catch (_: Exception) { emptyList() }
        val cloudBillsJson = try {
            val pref = context.getSharedPreferences("jewellery_cloud_remote_store", Context.MODE_PRIVATE)
            pref.getString("bills_$accountId", "[]") ?: "[]"
        } catch (_: Exception) { "[]" }

        val cloudBillNumbers = mutableListOf<String>()
        try {
            val arr = org.json.JSONArray(cloudBillsJson)
            for (i in 0 until arr.length()) {
                val bNo = arr.getJSONObject(i).optString("billNumber")
                if (bNo.isNotBlank()) cloudBillNumbers.add(bNo.trim())
            }
        } catch (_: Exception) {}

        val existingBillNumbers = (allLocalBills.map { it.billNumber.trim() } + cloudBillNumbers).distinct()

        // Scan existing bills to determine the highest sequence for this prefix
        var maxSeq = 0
        val targetRegex = if (isPurchase) {
            Regex("^${Regex.escape(initials)}\\(P\\)[\\s\\-_]?(\\d+)", RegexOption.IGNORE_CASE)
        } else {
            Regex("^${Regex.escape(initials)}(?!\\(P\\))[\\s\\-_]?(\\d+)", RegexOption.IGNORE_CASE)
        }

        for (bNo in existingBillNumbers) {
            val match = targetRegex.find(bNo)
            if (match != null) {
                val num = match.groupValues[1].toIntOrNull() ?: 0
                if (num > maxSeq) maxSeq = num
            }
        }

        // Also check any existing local bills with matching billType to preserve sequences
        for (b in allLocalBills) {
            val sameType = if (isPurchase) {
                b.billType.contains("PURCHASE", ignoreCase = true)
            } else {
                b.billType.equals("SALE", ignoreCase = true)
            }
            if (sameType) {
                val bTrim = b.billNumber.trim()
                if (bTrim.startsWith(prefix, ignoreCase = true) || bTrim.matches(Regex("^[#\\s]?\\d+$"))) {
                    val digitMatch = Regex("(\\d+)$").find(bTrim)
                    if (digitMatch != null) {
                        val num = digitMatch.groupValues[1].toIntOrNull() ?: 0
                        if (num > maxSeq) maxSeq = num
                    }
                }
            }
        }

        fun formatSeq(seq: Int): String {
            return if (seq < 10000) String.format(Locale.US, "%04d", seq) else seq.toString()
        }

        var nextSeq = maxSeq + 1
        var candidate = "$prefix${formatSeq(nextSeq)}"

        // Ensure strictly NO duplicate bill number across local & cloud
        while (existingBillNumbers.any { it.equals(candidate, ignoreCase = true) }) {
            nextSeq++
            candidate = "$prefix${formatSeq(nextSeq)}"
        }

        candidate
    }

    /**
     * Creates a new Sale Bill or Karigar Purchase Bill with automatic stock Credit/Debit
     */
    suspend fun createBill(bill: Bill) = withContext(Dispatchers.IO) {
        billDao.insertBill(bill)
        cloudSync.enqueueBill(bill, "UPSERT")
        if (cloudSync.isNetworkAvailable()) {
            cloudSync.syncBillToCloud(bill)
        }

        // Generate automatic stock transactions
        generateAutomaticStockFromBill(bill)
        cloudSync.triggerAutoSync(bill.accountId)
    }

    /**
     * Updates an existing bill. Corrects/reverses old stock movements and applies new ones!
     */
    suspend fun updateBill(bill: Bill) = withContext(Dispatchers.IO) {
        // 1. Remove old automatic stock movements for this bill locally and in cloud
        stockDao.deleteByLinkedBillId(bill.id, bill.accountId)
        cloudSync.deleteLinkedStockTransactionsFromCloud(bill.accountId, bill.id)

        // 2. Update bill locally & in cloud
        billDao.updateBill(bill)
        cloudSync.enqueueBill(bill, "UPSERT")
        if (cloudSync.isNetworkAvailable()) {
            cloudSync.syncBillToCloud(bill)
        }

        // 3. Re-generate new automatic stock movements with deterministic IDs
        generateAutomaticStockFromBill(bill)
        cloudSync.triggerAutoSync(bill.accountId)
    }

    /**
     * Deletes a bill and cleanly reverses/removes related automatic stock movements
     */
    suspend fun deleteBill(bill: Bill) = withContext(Dispatchers.IO) {
        // 1. Delete linked automatic stock transactions locally and in cloud
        stockDao.deleteByLinkedBillId(bill.id, bill.accountId)
        cloudSync.deleteLinkedStockTransactionsFromCloud(bill.accountId, bill.id)

        // 2. Delete bill locally and enqueue/sync deletion
        billDao.deleteBill(bill)
        cloudSync.enqueueDeleteBill(bill.accountId, bill.id)
        if (cloudSync.isNetworkAvailable()) {
            cloudSync.deleteBillFromCloud(bill.accountId, bill.id)
        }
        cloudSync.triggerAutoSync(bill.accountId)
    }

    /**
     * Adds an additional payment entry to an existing Customer Sale or Karigar Purchase Bill
     */
    suspend fun addAdditionalPayment(billId: String, payment: BillPayment) = withContext(Dispatchers.IO) {
        val currentBill = billDao.getBillById(billId) ?: return@withContext
        val existingPayments = currentBill.getEffectivePayments().toMutableList()
        val entryNo = existingPayments.size + 1
        val newPayment = payment.copy(entryNumber = entryNo)
        existingPayments.add(newPayment)

        val totalPaid = existingPayments.sumOf { it.amount }
        val newBalance = (currentBill.grandTotal - totalPaid).coerceAtLeast(0.0)
        val resolvedMode = if (existingPayments.size > 1) "MULTI" else (existingPayments.firstOrNull()?.paymentMode ?: currentBill.paymentMode)

        val updatedBill = currentBill.copy(
            paymentMode = resolvedMode,
            cashReceivedOrPaid = totalPaid,
            netBalanceDue = newBalance,
            paymentsJson = Bill.paymentsToJson(existingPayments),
            updatedAt = System.currentTimeMillis()
        )
        updateBill(updatedBill)
    }

    /**
     * Updates an additional payment entry in an existing Bill
     */
    suspend fun updateAdditionalPayment(billId: String, updatedPayment: BillPayment) = withContext(Dispatchers.IO) {
        val currentBill = billDao.getBillById(billId) ?: return@withContext
        val existingPayments = currentBill.getEffectivePayments().toMutableList()
        val idx = existingPayments.indexOfFirst { it.id == updatedPayment.id }
        if (idx >= 0) {
            existingPayments[idx] = updatedPayment
            val totalPaid = existingPayments.sumOf { it.amount }
            val newBalance = (currentBill.grandTotal - totalPaid).coerceAtLeast(0.0)
            val resolvedMode = if (existingPayments.size > 1) "MULTI" else (existingPayments.firstOrNull()?.paymentMode ?: currentBill.paymentMode)
            val updatedBill = currentBill.copy(
                paymentMode = resolvedMode,
                cashReceivedOrPaid = totalPaid,
                netBalanceDue = newBalance,
                paymentsJson = Bill.paymentsToJson(existingPayments),
                updatedAt = System.currentTimeMillis()
            )
            updateBill(updatedBill)
        }
    }

    /**
     * Deletes an additional payment entry from an existing Bill
     */
    suspend fun deleteAdditionalPayment(billId: String, paymentId: String) = withContext(Dispatchers.IO) {
        val currentBill = billDao.getBillById(billId) ?: return@withContext
        val existingPayments = currentBill.getEffectivePayments().toMutableList()
        existingPayments.removeAll { it.id == paymentId }
        val reindexed = existingPayments.mapIndexed { index, p -> p.copy(entryNumber = index + 1) }
        val totalPaid = reindexed.sumOf { it.amount }
        val newBalance = (currentBill.grandTotal - totalPaid).coerceAtLeast(0.0)
        val resolvedMode = if (reindexed.size > 1) "MULTI" else (reindexed.firstOrNull()?.paymentMode ?: currentBill.paymentMode)
        val updatedBill = currentBill.copy(
            paymentMode = resolvedMode,
            cashReceivedOrPaid = totalPaid,
            netBalanceDue = newBalance,
            paymentsJson = Bill.paymentsToJson(reindexed),
            updatedAt = System.currentTimeMillis()
        )
        updateBill(updatedBill)
    }

    /**
     * Replaces/sets all payments on an existing bill (e.g. converting to Gold + Cash)
     */
    suspend fun setBillPayments(billId: String, newPayments: List<BillPayment>) = withContext(Dispatchers.IO) {
        val currentBill = billDao.getBillById(billId) ?: return@withContext
        val reindexed = newPayments.mapIndexed { index, p -> p.copy(entryNumber = index + 1) }
        val totalPaid = reindexed.sumOf { it.amount }
        val newBalance = (currentBill.grandTotal - totalPaid).coerceAtLeast(0.0)
        val resolvedMode = if (reindexed.size > 1) "MULTI" else (reindexed.firstOrNull()?.paymentMode ?: currentBill.paymentMode)
        val updatedBill = currentBill.copy(
            paymentMode = resolvedMode,
            cashReceivedOrPaid = totalPaid,
            netBalanceDue = newBalance,
            paymentsJson = Bill.paymentsToJson(reindexed),
            updatedAt = System.currentTimeMillis()
        )
        updateBill(updatedBill)
    }

    fun getLastCredentials(): Pair<String, String> {
        val t = getLastCredentialsTriple()
        return Pair(t.first, t.second)
    }

    fun getLastCredentialsTriple(): Triple<String, String, String> {
        var name = sessionPrefs.getString("last_jeweller_name", "") ?: ""
        var mobile = sessionPrefs.getString("last_mobile_number", "") ?: ""
        var gst = sessionPrefs.getString("last_gst_number", "") ?: ""

        if (mobile.isBlank()) {
            mobile = permanentPrefs.getString("last_registered_mobile", "") ?: ""
        }
        if (name.isBlank()) {
            name = permanentPrefs.getString("last_registered_name", "") ?: ""
        }
        if (gst.isBlank()) {
            gst = permanentPrefs.getString("last_registered_gst", "") ?: ""
        }

        if (mobile.isBlank()) {
            val anyAcc = accountMemoryCache.values.firstOrNull()
                ?: getPermanentAccountsList().firstOrNull()
            if (anyAcc != null) {
                name = anyAcc.jewellerName
                mobile = anyAcc.mobileNumber
                gst = anyAcc.gstNumber
            }
        }

        return Triple(name, mobile, gst)
    }

    /**
     * Generates automatic stock movements according to strict requirements:
     * - Customer Sale: Relevant Jewellery stock = DEBIT (ઉધાર)
     * - Karigar Purchase:
     *     - Jewellery item: Jewellery stock = CREDIT (જમા)
     *     - Raw Gold/Silver Metal item: Metal stock = CREDIT (જમા)
     * - Payments (Sale):
     *     - Cash/Online/Cheque: CASH = CREDIT (જમા)
     *     - Gold: GOLD_METAL = CREDIT (જમા) (credited at Fine 100% weight = physicalWt * touch / 100)
     *     - Silver: SILVER_METAL = CREDIT (જમા) (credited at Fine 100% weight = physicalWt * touch / 100)
     * - Payments (Karigar Purchase):
     *     - Cash/Online/Cheque: CASH = DEBIT (ઉધાર)
     *     - Gold: GOLD_METAL = DEBIT (ઉધાર) (debited at Fine 100% weight)
     *     - Silver: SILVER_METAL = DEBIT (ઉધાર) (debited at Fine 100% weight)
     * - Old metal exchange in Sale: Metal = CREDIT (જમા)
     */
    private suspend fun generateAutomaticStockFromBill(bill: Bill) {
        val items = bill.parseItems()

        // 1. Items stock movement (Jewellery vs Raw Metal)
        items.forEachIndexed { index, item ->
            val weight = if (item.netWeight > 0) item.netWeight else item.grossWeight
            if (weight > 0) {
                val isMetal = item.stockClassification.equals("METAL", ignoreCase = true)
                val category = if (item.metalType.equals("SILVER", ignoreCase = true)) {
                    if (isMetal) "SILVER_METAL" else "SILVER_JEWELLERY"
                } else {
                    if (isMetal) "GOLD_METAL" else "GOLD_JEWELLERY"
                }

                val type = if (bill.billType == "SALE") {
                    "DEBIT" // Customer sale reduces stock
                } else {
                    "CREDIT" // Karigar purchase adds stock
                }

                val desc = if (item.description.isNotBlank()) item.description else loc("Jewellery", "દાગીના")
                val itemTypeLabel = if (isMetal) loc("Raw Metal", "ધાતુ") else loc("Jewellery", "દાગીના")
                val remark = if (bill.billType == "SALE") {
                    loc("Bill #${bill.billNumber} Sale: $desc ($itemTypeLabel, ${item.purity})", "બિલ નં. ${bill.billNumber} વેચાણ: $desc ($itemTypeLabel, ${item.purity})")
                } else {
                    loc("Bill #${bill.billNumber} Karigar Purchase: $desc ($itemTypeLabel, ${item.purity})", "બિલ નં. ${bill.billNumber} કારીગર ખરીદી: $desc ($itemTypeLabel, ${item.purity})")
                }

                val tx = StockTransaction(
                    id = "stk_item_${bill.id}_$index",
                    accountId = bill.accountId,
                    type = type,
                    category = category,
                    quantityOrAmount = weight,
                    unit = "g",
                    dateTimestamp = bill.dateTimestamp,
                    remark = remark,
                    isAutomaticFromBill = true,
                    linkedBillId = bill.id,
                    createdAt = System.currentTimeMillis()
                )
                stockDao.insertTransaction(tx)
                cloudSync.enqueueStockTransaction(tx, "UPSERT")
                if (cloudSync.isNetworkAvailable()) {
                    cloudSync.syncStockTransactionToCloud(tx)
                }
            }
        }

        // 2. Payments stock movement (Cash, Gold, Silver, Online, Cheque)
        val payments = bill.parsePayments()
        if (payments.isNotEmpty()) {
            payments.forEachIndexed { pIndex, p ->
                when (p.paymentMode.uppercase()) {
                    "GOLD" -> {
                        val physicalWt = if (p.metalWeight > 0) p.metalWeight else (if (p.metalRate > 0) p.amount / p.metalRate else 0.0)
                        val touch = if (p.metalTouch > 0) p.metalTouch else 100.0
                        val fineWt = if (p.calculatedFineWeight > 0) p.calculatedFineWeight else (physicalWt * touch / 100.0)
                        val stockQty = if (fineWt > 0) fineWt else physicalWt

                        if (stockQty > 0) {
                            val type = if (bill.billType == "SALE") "CREDIT" else "DEBIT"
                            val formattedPhysical = LanguageManager.formatDouble(physicalWt, 3)
                            val formattedTouch = LanguageManager.formatDouble(touch, 1)
                            val formattedFine = LanguageManager.formatDouble(stockQty, 3)
                            val rem = if (bill.billType == "SALE") {
                                loc(
                                    "bill no. ${bill.billNumber} - ${formattedPhysical}g ${formattedTouch}% = ${formattedFine}g fine (${bill.partyName})",
                                    "બિલ નં. ${bill.billNumber} - ${formattedPhysical}g ${formattedTouch}% = ${formattedFine}g ફાઇન જમા (${bill.partyName})"
                                )
                            } else {
                                loc(
                                    "bill no. ${bill.billNumber} - ${formattedPhysical}g ${formattedTouch}% = ${formattedFine}g fine (${bill.partyName})",
                                    "બિલ નં. ${bill.billNumber} - ${formattedPhysical}g ${formattedTouch}% = ${formattedFine}g ફાઇન ચૂકવ્યું (${bill.partyName})"
                                )
                            }
                            val tx = StockTransaction(
                                id = "stk_pay_gold_${bill.id}_${p.entryNumber}_$pIndex",
                                accountId = bill.accountId,
                                type = type,
                                category = "GOLD_METAL",
                                quantityOrAmount = stockQty,
                                unit = "g",
                                dateTimestamp = p.dateTimestamp,
                                remark = rem,
                                isAutomaticFromBill = true,
                                linkedBillId = bill.id,
                                createdAt = System.currentTimeMillis()
                            )
                            stockDao.insertTransaction(tx)
                            cloudSync.enqueueStockTransaction(tx, "UPSERT")
                            if (cloudSync.isNetworkAvailable()) {
                                cloudSync.syncStockTransactionToCloud(tx)
                            }
                        }
                    }
                    "SILVER" -> {
                        val physicalWt = if (p.metalWeight > 0) p.metalWeight else (if (p.metalRate > 0) p.amount / p.metalRate else 0.0)
                        val touch = if (p.metalTouch > 0) p.metalTouch else 100.0
                        val fineWt = if (p.calculatedFineWeight > 0) p.calculatedFineWeight else (physicalWt * touch / 100.0)
                        val stockQty = if (fineWt > 0) fineWt else physicalWt

                        if (stockQty > 0) {
                            val type = if (bill.billType == "SALE") "CREDIT" else "DEBIT"
                            val formattedPhysical = LanguageManager.formatDouble(physicalWt, 3)
                            val formattedTouch = LanguageManager.formatDouble(touch, 1)
                            val formattedFine = LanguageManager.formatDouble(stockQty, 3)
                            val rem = if (bill.billType == "SALE") {
                                loc(
                                    "bill no. ${bill.billNumber} - ${formattedPhysical}g ${formattedTouch}% = ${formattedFine}g fine (${bill.partyName})",
                                    "બિલ નં. ${bill.billNumber} - ${formattedPhysical}g ${formattedTouch}% = ${formattedFine}g ફાઇન જમા (${bill.partyName})"
                                )
                            } else {
                                loc(
                                    "bill no. ${bill.billNumber} - ${formattedPhysical}g ${formattedTouch}% = ${formattedFine}g fine (${bill.partyName})",
                                    "બિલ નં. ${bill.billNumber} - ${formattedPhysical}g ${formattedTouch}% = ${formattedFine}g ફાઇન ચૂકવ્યું (${bill.partyName})"
                                )
                            }
                            val tx = StockTransaction(
                                id = "stk_pay_silver_${bill.id}_${p.entryNumber}_$pIndex",
                                accountId = bill.accountId,
                                type = type,
                                category = "SILVER_METAL",
                                quantityOrAmount = stockQty,
                                unit = "g",
                                dateTimestamp = p.dateTimestamp,
                                remark = rem,
                                isAutomaticFromBill = true,
                                linkedBillId = bill.id,
                                createdAt = System.currentTimeMillis()
                            )
                            stockDao.insertTransaction(tx)
                            cloudSync.enqueueStockTransaction(tx, "UPSERT")
                            if (cloudSync.isNetworkAvailable()) {
                                cloudSync.syncStockTransactionToCloud(tx)
                            }
                        }
                    }
                    else -> { // CASH, ONLINE, CHEQUE
                        if (p.amount > 0) {
                            val type = if (bill.billType == "SALE") "CREDIT" else "DEBIT"
                            val rem = if (bill.billType == "SALE") {
                                loc("Bill #${bill.billNumber} Payment received [${p.paymentMode}] (${bill.partyName})", "બિલ નં. ${bill.billNumber} ચુકવણી પ્રાપ્ત [${p.paymentMode}] (${bill.partyName})")
                            } else {
                                loc("Bill #${bill.billNumber} Paid to Karigar [${p.paymentMode}] (${bill.partyName})", "બિલ નં. ${bill.billNumber} કારીગરને ચૂકવણી [${p.paymentMode}] (${bill.partyName})")
                            }
                            val cashTx = StockTransaction(
                                id = "stk_pay_cash_${bill.id}_${p.entryNumber}_$pIndex",
                                accountId = bill.accountId,
                                type = type,
                                category = "CASH",
                                quantityOrAmount = p.amount,
                                unit = "₹",
                                dateTimestamp = p.dateTimestamp,
                                remark = rem,
                                isAutomaticFromBill = true,
                                linkedBillId = bill.id,
                                createdAt = System.currentTimeMillis()
                            )
                            stockDao.insertTransaction(cashTx)
                            cloudSync.enqueueStockTransaction(cashTx, "UPSERT")
                            if (cloudSync.isNetworkAvailable()) {
                                cloudSync.syncStockTransactionToCloud(cashTx)
                            }
                        }
                    }
                }
            }
        } else {
            // Backward compatibility fallback
            if (bill.billType == "SALE" && bill.cashReceivedOrPaid > 0) {
                val cashTx = StockTransaction(
                    id = "stk_cash_fb_${bill.id}",
                    accountId = bill.accountId,
                    type = "CREDIT",
                    category = "CASH",
                    quantityOrAmount = bill.cashReceivedOrPaid,
                    unit = "₹",
                    dateTimestamp = bill.dateTimestamp,
                    remark = loc(
                        "Bill #${bill.billNumber} Cash Received (${bill.partyName})",
                        "બિલ નં. ${bill.billNumber} ગ્રાહક પાસેથી રોકડ પ્રાપ્ત (${bill.partyName})"
                    ),
                    isAutomaticFromBill = true,
                    linkedBillId = bill.id,
                    createdAt = System.currentTimeMillis()
                )
                stockDao.insertTransaction(cashTx)
                cloudSync.enqueueStockTransaction(cashTx, "UPSERT")
                if (cloudSync.isNetworkAvailable()) {
                    cloudSync.syncStockTransactionToCloud(cashTx)
                }
            } else if (bill.billType == "KARIGAR_PURCHASE" && bill.cashReceivedOrPaid > 0) {
                val cashTx = StockTransaction(
                    id = "stk_cash_fb_${bill.id}",
                    accountId = bill.accountId,
                    type = "DEBIT",
                    category = "CASH",
                    quantityOrAmount = bill.cashReceivedOrPaid,
                    unit = "₹",
                    dateTimestamp = bill.dateTimestamp,
                    remark = loc(
                        "Bill #${bill.billNumber} Cash Paid (${bill.partyName})",
                        "બિલ નં. ${bill.billNumber} કારીગરને રોકડ ચૂકવણી (${bill.partyName})"
                    ),
                    isAutomaticFromBill = true,
                    linkedBillId = bill.id,
                    createdAt = System.currentTimeMillis()
                )
                stockDao.insertTransaction(cashTx)
                cloudSync.enqueueStockTransaction(cashTx, "UPSERT")
                if (cloudSync.isNetworkAvailable()) {
                    cloudSync.syncStockTransactionToCloud(cashTx)
                }
            }
        }

        // 3. Old Gold/Silver Metal Exchange in Sale
        if (bill.oldMetalExchangeAmount > 0) {
            val metalTx = StockTransaction(
                id = "stk_oldmetal_${bill.id}",
                accountId = bill.accountId,
                type = "CREDIT",
                category = "GOLD_METAL", // Old metal received added to raw metal stock
                quantityOrAmount = bill.oldMetalExchangeAmount,
                unit = "₹",
                dateTimestamp = bill.dateTimestamp,
                remark = loc(
                    "Bill #${bill.billNumber} Old Metal Exchange (${bill.partyName})",
                    "બિલ નં. ${bill.billNumber} જૂનું સોનું/ચાંદી જમા (${bill.partyName})"
                ),
                isAutomaticFromBill = true,
                linkedBillId = bill.id,
                createdAt = System.currentTimeMillis()
            )
            stockDao.insertTransaction(metalTx)
            cloudSync.enqueueStockTransaction(metalTx, "UPSERT")
            if (cloudSync.isNetworkAvailable()) {
                cloudSync.syncStockTransactionToCloud(metalTx)
            }
        }
    }

    // Stock Transactions
    fun getTransactionsByCategoryFlow(accountId: String, category: String): Flow<List<StockTransaction>> =
        stockDao.getTransactionsByCategoryFlow(accountId, category)

    fun getAllTransactionsFlow(accountId: String): Flow<List<StockTransaction>> =
        stockDao.getAllTransactionsFlow(accountId)

    suspend fun getNetStock(accountId: String, category: String): Double = withContext(Dispatchers.IO) {
        val credit = stockDao.getTotalCredit(accountId, category)
        val debit = stockDao.getTotalDebit(accountId, category)
        credit - debit
    }

    suspend fun addManualStockTransaction(
        accountId: String,
        type: String, // "CREDIT" or "DEBIT"
        category: String,
        amount: Double,
        unit: String,
        remark: String,
        timestamp: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        val tx = StockTransaction(
            id = UUID.randomUUID().toString(),
            accountId = accountId,
            type = type,
            category = category,
            quantityOrAmount = amount,
            unit = unit,
            dateTimestamp = timestamp,
            remark = remark,
            isAutomaticFromBill = false,
            linkedBillId = null,
            createdAt = System.currentTimeMillis()
        )
        stockDao.insertTransaction(tx)
        cloudSync.syncStockTransactionToCloud(tx)
        cloudSync.triggerAutoSync(accountId)
    }

    suspend fun updateStockTransaction(tx: StockTransaction) = withContext(Dispatchers.IO) {
        stockDao.updateTransaction(tx)
        cloudSync.syncStockTransactionToCloud(tx)
        cloudSync.triggerAutoSync(tx.accountId)
    }

    suspend fun deleteStockTransaction(tx: StockTransaction) = withContext(Dispatchers.IO) {
        stockDao.deleteTransaction(tx)
        cloudSync.deleteStockTransactionFromCloud(tx.accountId, tx.id)
        cloudSync.triggerAutoSync(tx.accountId)
    }

    /**
     * Requirement 14: ₹ Cash -> Gold / Silver Purchase without a bill
     * Automatically debits Cash and credits raw Metal stock!
     */
    suspend fun recordCashMetalPurchase(
        accountId: String,
        metalCategory: String, // "GOLD_METAL" or "SILVER_METAL"
        grams: Double,
        cashAmount: Double,
        remark: String
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        // 1. Cash DEBIT
        val cashTx = StockTransaction(
            id = UUID.randomUUID().toString(),
            accountId = accountId,
            type = "DEBIT",
            category = "CASH",
            quantityOrAmount = cashAmount,
            unit = "₹",
            dateTimestamp = now,
            remark = loc("Metal Cash Purchase: $remark", "સોનું/ચાંદી રોકડથી ખરીદી: $remark"),
            isAutomaticFromBill = false,
            createdAt = now
        )
        stockDao.insertTransaction(cashTx)
        cloudSync.syncStockTransactionToCloud(cashTx)

        // 2. Metal CREDIT
        val metalTx = StockTransaction(
            id = UUID.randomUUID().toString(),
            accountId = accountId,
            type = "CREDIT",
            category = metalCategory,
            quantityOrAmount = grams,
            unit = "g",
            dateTimestamp = now,
            remark = loc("Metal purchase for cash ₹$cashAmount: $remark", "રોકડ ₹$cashAmount થી ખરીદેલ વજન: $remark"),
            isAutomaticFromBill = false,
            createdAt = now
        )
        stockDao.insertTransaction(metalTx)
        cloudSync.syncStockTransactionToCloud(metalTx)
        cloudSync.triggerAutoSync(accountId)
    }
}
