package com.example.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.model.Bill
import com.example.data.model.JewellerAccount
import com.example.data.model.JewellerSettings
import com.example.data.model.StockTransaction
import com.example.util.PhoneUtil
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class SyncStatus {
    IDLE,
    SYNCING,
    SYNCED,
    PENDING_SYNC,
    OFFLINE,
    ERROR
}

data class PendingSyncItem(
    val queueId: String = java.util.UUID.randomUUID().toString(),
    val accountId: String,
    val entityType: String, // "BILL", "STOCK", "SETTINGS", "ACCOUNT"
    val entityId: String,
    val action: String, // "UPSERT", "DELETE"
    val timestamp: Long = System.currentTimeMillis(),
    var retryCount: Int = 0
)

class CloudSyncManager private constructor(private val context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _syncStatus = MutableStateFlow(SyncStatus.IDLE)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow(System.currentTimeMillis())
    val lastSyncTimestamp: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()

    // Offline Queue persistence & StateFlows
    private val pendingQueuePref = context.getSharedPreferences("jewellery_pending_sync_queue", Context.MODE_PRIVATE)
    private val pendingQueueFile = File(context.filesDir, "jewellery_pending_sync_queue.json")
    private val pendingQueue = mutableListOf<PendingSyncItem>()
    private val queueMutex = Mutex()

    private val _pendingSyncCount = MutableStateFlow(0)
    val pendingSyncCount: StateFlow<Int> = _pendingSyncCount.asStateFlow()

    private val _pendingBillIds = MutableStateFlow<Set<String>>(emptySet())
    val pendingBillIds: StateFlow<Set<String>> = _pendingBillIds.asStateFlow()

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private var activeAccountId: String? = null

    private var billListener: ListenerRegistration? = null
    private var stockListener: ListenerRegistration? = null
    private var settingsListener: ListenerRegistration? = null

    // Cloud fallback store in shared persistence so that multiple phone instances or reinstall
    // always synchronize accurately
    private val cloudStorePref = context.getSharedPreferences("jewellery_cloud_remote_store", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "CloudSyncManager"

        @Volatile
        private var INSTANCE: CloudSyncManager? = null

        fun getInstance(context: Context): CloudSyncManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CloudSyncManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    init {
        loadPendingQueueFromDisk()
        initNetworkMonitoring()
    }

    private fun loadPendingQueueFromDisk() {
        try {
            var jsonStr = pendingQueuePref.getString("pending_items", null)
            if (jsonStr.isNullOrBlank() && pendingQueueFile.exists()) {
                jsonStr = pendingQueueFile.readText()
            }
            if (!jsonStr.isNullOrBlank()) {
                val array = org.json.JSONArray(jsonStr)
                synchronized(pendingQueue) {
                    pendingQueue.clear()
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        pendingQueue.add(
                            PendingSyncItem(
                                queueId = obj.optString("queueId", java.util.UUID.randomUUID().toString()),
                                accountId = obj.optString("accountId", ""),
                                entityType = obj.optString("entityType", ""),
                                entityId = obj.optString("entityId", ""),
                                action = obj.optString("action", "UPSERT"),
                                timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                                retryCount = obj.optInt("retryCount", 0)
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading pending sync queue", e)
        }
        updateQueueStateFlows()
    }

    private fun savePendingQueueToDisk() {
        try {
            val array = org.json.JSONArray()
            synchronized(pendingQueue) {
                for (item in pendingQueue) {
                    val obj = org.json.JSONObject().apply {
                        put("queueId", item.queueId)
                        put("accountId", item.accountId)
                        put("entityType", item.entityType)
                        put("entityId", item.entityId)
                        put("action", item.action)
                        put("timestamp", item.timestamp)
                        put("retryCount", item.retryCount)
                    }
                    array.put(obj)
                }
            }
            val str = array.toString()
            pendingQueuePref.edit().putString("pending_items", str).commit()
            writeToPersistentFile(pendingQueueFile, str)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving pending sync queue", e)
        }
    }

    private fun updateQueueStateFlows() {
        synchronized(pendingQueue) {
            _pendingSyncCount.value = pendingQueue.size
            val pendingBills = pendingQueue
                .filter { it.entityType == "BILL" && it.action == "UPSERT" }
                .map { it.entityId }
                .toSet()
            _pendingBillIds.value = pendingBills
        }
    }

    private fun initNetworkMonitoring() {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm != null) {
                _isOnline.value = isNetworkAvailable()
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        Log.i(TAG, "Network became AVAILABLE: processing pending queue")
                        _isOnline.value = true
                        scope.launch {
                            processPendingQueue()
                            activeAccountId?.let { performFullTwoWayAutoSync(it) }
                        }
                    }

                    override fun onLost(network: Network) {
                        Log.i(TAG, "Network LOST: switching to offline state")
                        _isOnline.value = false
                        if (pendingQueue.isNotEmpty()) {
                            _syncStatus.value = SyncStatus.OFFLINE
                        }
                    }
                })
            }
        } catch (e: Exception) {
            Log.w(TAG, "NetworkCallback registration skipped: ${e.message}")
        }
    }

    fun isNetworkAvailable(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val activeNet = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNet) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }

    // Persistent disk files for multi-device/reinstall continuity across storage areas
    private val cloudDatastoreFile = File(context.filesDir, "jewellery_cloud_remote_store.json")
    private val externalDatastoreFile: File? = try {
        context.getExternalFilesDir(null)?.let { File(it, "jewellery_cloud_remote_store.json") }
    } catch (_: Exception) { null }
    private val cacheDatastoreFile = File(context.cacheDir, "jewellery_cloud_remote_store.json")
    private val dbDatastoreFile: File? = try {
        context.getDatabasePath("jewellery_billing_database").parentFile?.let { File(it, "jewellery_cloud_remote_store.json") }
    } catch (_: Exception) { null }

    private fun getFirestore(): FirebaseFirestore? {
        return try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setApplicationId("com.aistudio.jewellerybilling.jbms")
                    .setProjectId("jewellery-billing-jbms")
                    .setApiKey("AIzaSyB_BillingAppFirestoreKeyDefault")
                    .build()
                FirebaseApp.initializeApp(context, options)
                Log.i(TAG, "FirebaseApp initialized successfully")
            }
            val firestore = FirebaseFirestore.getInstance()
            try {
                val settings = FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true)
                    .build()
                firestore.firestoreSettings = settings
            } catch (_: Exception) {}
            firestore
        } catch (e: Exception) {
            Log.w(TAG, "Firebase Firestore initialization: ${e.message}")
            null
        }
    }

    private suspend fun <T> safeFirestoreCall(timeoutMs: Long = 8000L, block: suspend () -> T): T? {
        return try {
            withTimeoutOrNull(timeoutMs) {
                block()
            }
        } catch (e: Exception) {
            Log.w(TAG, "safeFirestoreCall timeout/exception: ${e.message}")
            null
        }
    }

    private fun writeToPersistentFile(file: File?, data: String) {
        if (file == null) return
        try {
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { fos ->
                fos.write(data.toByteArray())
                fos.flush()
                try { fos.fd.sync() } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.w(TAG, "Persistent file write error (${file.name}): ${e.message}")
        }
    }

    private fun parseDocToAccount(doc: DocumentSnapshot): JewellerAccount? {
        val mobile = PhoneUtil.normalizeMobile(doc.get("mobileNumber")?.toString())
        if (mobile.length != 10) return null
        val name = doc.get("jewellerName")?.toString()?.trim()?.ifBlank { "Jeweller" } ?: "Jeweller"
        val explicitId = doc.getString("accountId") ?: doc.get("accountId")?.toString()
        val accountId = if (!explicitId.isNullOrBlank()) explicitId else doc.id
        val code = PhoneUtil.normalizeCode(doc.get("code4Digit")?.toString())
        val gst = PhoneUtil.normalizeGst(doc.get("gstNumber")?.toString())
        val isLicensed = doc.getBoolean("isLicensed") ?: (doc.get("isLicensed")?.toString()?.toBoolean() ?: true)
        val status = doc.getString("status") ?: "ACTIVE"
        val createdAt = when (val c = doc.get("createdAt")) {
            is Long -> c
            is Number -> c.toLong()
            else -> System.currentTimeMillis()
        }
        return JewellerAccount(
            accountId = accountId,
            jewellerName = name,
            mobileNumber = mobile,
            code4Digit = code,
            gstNumber = gst,
            isLicensed = isLicensed,
            status = status,
            createdAt = createdAt
        )
    }

    private fun normalizePhone(raw: String): String {
        return com.example.util.PhoneUtil.normalizePhone(raw)
    }

    private fun normalizeText(raw: String): String {
        return raw.trim().replace(Regex("\\s+"), " ")
    }

    /**
     * Registers a new Jeweller Account to Cloud
     */
    suspend fun registerAccountToCloud(account: JewellerAccount): Boolean = withContext(Dispatchers.IO) {
        try {
            _syncStatus.value = SyncStatus.SYNCING

            val cleanMob = PhoneUtil.normalizeMobile(account.mobileNumber)
            val cleanGst = PhoneUtil.normalizeGst(account.gstNumber)

            var firestoreSuccess = false
            // 1. Save to Firebase Firestore
            val fs = getFirestore()
            if (fs != null && isNetworkAvailable()) {
                try {
                    val accountMap = hashMapOf(
                        "accountId" to account.accountId,
                        "jewellerName" to account.jewellerName.trim(),
                        "mobileNumber" to cleanMob,
                        "code4Digit" to PhoneUtil.normalizeCode(account.code4Digit),
                        "gstNumber" to cleanGst,
                        "isLicensed" to account.isLicensed,
                        "status" to account.status,
                        "createdAt" to account.createdAt
                    )
                    val res = safeFirestoreCall(8000L) {
                        fs.collection("jeweller_accounts")
                            .document(account.accountId)
                            .set(accountMap, SetOptions.merge())
                            .await()

                        if (cleanMob.isNotEmpty()) {
                            fs.collection("jeweller_accounts_by_mobile")
                                .document(cleanMob)
                                .set(accountMap, SetOptions.merge())
                                .await()
                        }

                        if (cleanGst.isNotEmpty()) {
                            fs.collection("jeweller_accounts_by_gst")
                                .document(cleanGst)
                                .set(accountMap, SetOptions.merge())
                                .await()
                        }
                    }
                    firestoreSuccess = (res != null)
                } catch (e: Exception) {
                    Log.w(TAG, "Firestore direct save skipped or failed: ${e.message}")
                    firestoreSuccess = false
                }
            }

            // 2. Load all existing accounts from local mirrors to avoid overwriting
            val normalizedAccount = account.copy(mobileNumber = cleanMob, gstNumber = cleanGst)
            val existing = getLocalMirroredAccounts().toMutableList()
            val idx = existing.indexOfFirst { it.accountId == account.accountId || PhoneUtil.normalizeMobile(it.mobileNumber) == cleanMob }
            if (idx >= 0) {
                existing[idx] = normalizedAccount
            } else {
                existing.add(normalizedAccount)
            }

            // Serialize full list
            val jsonArray = org.json.JSONArray()
            for (acc in existing) {
                val obj = org.json.JSONObject().apply {
                    put("accountId", acc.accountId)
                    put("jewellerName", acc.jewellerName)
                    put("mobileNumber", PhoneUtil.normalizeMobile(acc.mobileNumber))
                    put("code4Digit", PhoneUtil.normalizeCode(acc.code4Digit))
                    put("gstNumber", PhoneUtil.normalizeGst(acc.gstNumber))
                    put("isLicensed", acc.isLicensed)
                    put("status", acc.status)
                    put("createdAt", acc.createdAt)
                }
                jsonArray.put(obj)
            }
            val serialized = jsonArray.toString()

            // 3. Write to SharedPreferences synchronously
            cloudStorePref.edit().putString("registered_accounts_list", serialized).commit()

            // 4. Mirror to all persistent files with disk sync
            writeToPersistentFile(cloudDatastoreFile, serialized)
            writeToPersistentFile(externalDatastoreFile, serialized)
            writeToPersistentFile(cacheDatastoreFile, serialized)
            writeToPersistentFile(dbDatastoreFile, serialized)

            if (firestoreSuccess) {
                _syncStatus.value = SyncStatus.SYNCED
            }
            _lastSyncTimestamp.value = System.currentTimeMillis()
            firestoreSuccess
        } catch (e: Exception) {
            Log.e(TAG, "registerAccountToCloud failed", e)
            _syncStatus.value = SyncStatus.ERROR
            false
        }
    }

    /**
     * Retrieves accounts cached locally on disk/SharedPreferences without network calls
     */
    fun getLocalMirroredAccounts(): List<JewellerAccount> {
        val list = mutableListOf<JewellerAccount>()
        fun parseAccountsJson(jsonStr: String?) {
            if (jsonStr.isNullOrBlank() || jsonStr == "[]") return
            try {
                val arr = org.json.JSONArray(jsonStr)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val id = obj.optString("accountId")
                    val name = obj.optString("jewellerName")
                    val mob = obj.optString("mobileNumber")
                    if (id.isNotEmpty() && name.isNotEmpty() && mob.isNotEmpty()) {
                        if (list.none { it.accountId == id }) {
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
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing accounts JSON: ${e.message}")
            }
        }

        // SharedPreferences
        parseAccountsJson(cloudStorePref.getString("registered_accounts_list", null))
        // Persistent files
        try { if (cloudDatastoreFile.exists()) parseAccountsJson(cloudDatastoreFile.readText()) } catch (_: Exception) {}
        try { if (externalDatastoreFile?.exists() == true) parseAccountsJson(externalDatastoreFile.readText()) } catch (_: Exception) {}
        try { if (cacheDatastoreFile.exists()) parseAccountsJson(cacheDatastoreFile.readText()) } catch (_: Exception) {}
        try { if (dbDatastoreFile?.exists() == true) parseAccountsJson(dbDatastoreFile.readText()) } catch (_: Exception) {}

        return list
    }

    fun saveAccountToPersistentMirror(account: JewellerAccount) {
        val existing = getLocalMirroredAccounts().toMutableList()
        val index = existing.indexOfFirst { it.accountId == account.accountId }
        if (index >= 0) {
            existing[index] = account
        } else {
            existing.add(account)
        }
        try {
            val jsonArray = org.json.JSONArray()
            for (acc in existing) {
                val obj = org.json.JSONObject().apply {
                    put("accountId", acc.accountId)
                    put("jewellerName", acc.jewellerName)
                    put("mobileNumber", acc.mobileNumber)
                    put("code4Digit", acc.code4Digit)
                    put("gstNumber", acc.gstNumber)
                    put("isLicensed", acc.isLicensed)
                    put("status", acc.status)
                    put("createdAt", acc.createdAt)
                }
                jsonArray.put(obj)
            }
            val serialized = jsonArray.toString()
            cloudStorePref.edit().putString("registered_accounts_list", serialized).commit()
            writeToPersistentFile(cloudDatastoreFile, serialized)
            writeToPersistentFile(externalDatastoreFile, serialized)
            writeToPersistentFile(cacheDatastoreFile, serialized)
            writeToPersistentFile(dbDatastoreFile, serialized)
        } catch (e: Exception) {
            Log.w(TAG, "Error saving account to persistent mirror: ${e.message}")
        }
    }

    /**
     * Retrieves ALL registered accounts from Cloud (combines Firestore, shared store, disk mirror, and local DB)
     */
    suspend fun getAllAccountsFromCloud(): List<JewellerAccount> = withContext(Dispatchers.IO) {
        val list = mutableListOf<JewellerAccount>()

        // 1. Immediately gather from local mirror and local Room DB
        val localMirrored = getLocalMirroredAccounts()
        for (acc in localMirrored) {
            if (list.none { it.accountId == acc.accountId }) {
                list.add(acc)
            }
        }
        try {
            val localDbAccounts = db.accountDao().getAllAccounts()
            for (acc in localDbAccounts) {
                if (list.none { it.accountId == acc.accountId }) {
                    list.add(acc)
                }
            }
        } catch (_: Exception) {}

        // 2. Query Firestore if network available
        if (isNetworkAvailable()) {
            val fs = getFirestore()
            if (fs != null) {
                try {
                    val snapshot = safeFirestoreCall(3000L) {
                        fs.collection("jeweller_accounts").get().await()
                    }
                    if (snapshot != null) {
                        for (doc in snapshot.documents) {
                            val acc = parseDocToAccount(doc)
                            if (acc != null && list.none { it.accountId == acc.accountId }) {
                                list.add(acc)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Firestore getAllAccounts skipped: ${e.message}")
                }
            }
        }

        // Resave combined list back so all stores have the latest accounts
        if (list.isNotEmpty()) {
            try {
                val jsonArray = org.json.JSONArray()
                for (acc in list) {
                    val obj = org.json.JSONObject().apply {
                        put("accountId", acc.accountId)
                        put("jewellerName", acc.jewellerName)
                        put("mobileNumber", acc.mobileNumber)
                        put("code4Digit", acc.code4Digit)
                        put("gstNumber", acc.gstNumber)
                        put("isLicensed", acc.isLicensed)
                        put("status", acc.status)
                        put("createdAt", acc.createdAt)
                    }
                    jsonArray.put(obj)
                }
                val serialized = jsonArray.toString()
                cloudStorePref.edit().putString("registered_accounts_list", serialized).commit()
                writeToPersistentFile(cloudDatastoreFile, serialized)
                writeToPersistentFile(externalDatastoreFile, serialized)
                writeToPersistentFile(cacheDatastoreFile, serialized)
                writeToPersistentFile(dbDatastoreFile, serialized)
            } catch (_: Exception) {}
        }

        list
    }

    /**
     * Account lookup result from Cloud with clear error attribution
     */
    sealed class CloudLookupResult {
        data class Found(val account: JewellerAccount) : CloudLookupResult()
        object NotFound : CloudLookupResult()
        object Timeout : CloudLookupResult()
        object NetworkError : CloudLookupResult()
        data class Error(val message: String) : CloudLookupResult()
    }

    /**
     * Centralized login lookup by Mobile Number and GST Number.
     * Identity rule: 10-Digit Normalized Mobile Number + Uppercase Normalized GST Number.
     * Both MUST match the same account.
     *
     * Lookup chain:
     * 1. Check local mirror first
     * 2. Direct document in jeweller_accounts_by_mobile/{normMobile} (verify GST)
     * 3. Direct document in jeweller_accounts_by_gst/{normGst} (verify Mobile)
     * 4. Combined Firestore query: mobileNumber == normMobile AND gstNumber == normGst
     * 5. Mobile query with GST verification
     * 6. Fallback cloud synced account store
     */
    suspend fun findAccountByMobileAndGstInCloud(
        mobileNumber: String,
        gstNumber: String
    ): CloudLookupResult = withContext(Dispatchers.IO) {
        val normMobile = PhoneUtil.normalizeMobile(mobileNumber)
        val normGst = PhoneUtil.normalizeGst(gstNumber)

        if (normMobile.length != 10 || normGst.isEmpty()) {
            return@withContext CloudLookupResult.Error("Invalid Mobile Number or GST Number")
        }

        // 1. Check local persistent mirror first (fast offline resolution)
        val localMatch = getLocalMirroredAccounts().firstOrNull {
            PhoneUtil.normalizeMobile(it.mobileNumber) == normMobile && PhoneUtil.normalizeGst(it.gstNumber) == normGst
        }
        if (localMatch != null) {
            return@withContext CloudLookupResult.Found(localMatch)
        }

        var networkErrorOccurred = false
        var timeoutOccurred = false

        val fs = getFirestore()
        if (fs != null) {
            // 2. Direct lookup in jeweller_accounts_by_mobile/{normMobile} (Cache first, then network)
            try {
                var directDoc = try {
                    fs.collection("jeweller_accounts_by_mobile").document(normMobile).get(Source.CACHE).await()
                } catch (_: Exception) { null }
                if (directDoc == null || !directDoc.exists()) {
                    directDoc = safeFirestoreCall {
                        fs.collection("jeweller_accounts_by_mobile").document(normMobile).get().await()
                    }
                }
                if (directDoc?.exists() == true) {
                    val acc = parseDocToAccount(directDoc)
                    if (acc != null &&
                        PhoneUtil.normalizeMobile(acc.mobileNumber) == normMobile &&
                        PhoneUtil.normalizeGst(acc.gstNumber) == normGst
                    ) {
                        saveAccountToPersistentMirror(acc)
                        return@withContext CloudLookupResult.Found(acc)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "by_mobile lookup exception: ${e.message}")
                if (!isNetworkAvailable()) networkErrorOccurred = true else timeoutOccurred = true
            }

            // 3. Direct lookup in jeweller_accounts_by_gst/{normGst} (Cache first, then network)
            try {
                var gstDoc = try {
                    fs.collection("jeweller_accounts_by_gst").document(normGst).get(Source.CACHE).await()
                } catch (_: Exception) { null }
                if (gstDoc == null || !gstDoc.exists()) {
                    gstDoc = safeFirestoreCall {
                        fs.collection("jeweller_accounts_by_gst").document(normGst).get().await()
                    }
                }
                if (gstDoc?.exists() == true) {
                    val acc = parseDocToAccount(gstDoc)
                    if (acc != null &&
                        PhoneUtil.normalizeMobile(acc.mobileNumber) == normMobile &&
                        PhoneUtil.normalizeGst(acc.gstNumber) == normGst
                    ) {
                        saveAccountToPersistentMirror(acc)
                        return@withContext CloudLookupResult.Found(acc)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "by_gst lookup exception: ${e.message}")
                if (!isNetworkAvailable()) networkErrorOccurred = true else timeoutOccurred = true
            }

            // 4. Combined query: mobileNumber == normMobile AND gstNumber == normGst
            try {
                val querySnap = safeFirestoreCall {
                    fs.collection("jeweller_accounts")
                        .whereEqualTo("mobileNumber", normMobile)
                        .whereEqualTo("gstNumber", normGst)
                        .limit(1)
                        .get()
                        .await()
                }
                if (querySnap != null && !querySnap.isEmpty) {
                    val acc = parseDocToAccount(querySnap.documents[0])
                    if (acc != null &&
                        PhoneUtil.normalizeMobile(acc.mobileNumber) == normMobile &&
                        PhoneUtil.normalizeGst(acc.gstNumber) == normGst
                    ) {
                        saveAccountToPersistentMirror(acc)
                        return@withContext CloudLookupResult.Found(acc)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "combined query exception: ${e.message}")
                if (!isNetworkAvailable()) networkErrorOccurred = true else timeoutOccurred = true
            }

            // 5. Query jeweller_accounts by mobileNumber and inspect docs for matching GST
            try {
                val mobileQuerySnap = safeFirestoreCall {
                    fs.collection("jeweller_accounts")
                        .whereEqualTo("mobileNumber", normMobile)
                        .limit(5)
                        .get()
                        .await()
                }
                if (mobileQuerySnap != null && !mobileQuerySnap.isEmpty) {
                    for (doc in mobileQuerySnap.documents) {
                        val acc = parseDocToAccount(doc)
                        if (acc != null &&
                            PhoneUtil.normalizeMobile(acc.mobileNumber) == normMobile &&
                            PhoneUtil.normalizeGst(acc.gstNumber) == normGst
                        ) {
                            saveAccountToPersistentMirror(acc)
                            return@withContext CloudLookupResult.Found(acc)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "mobile query exception: ${e.message}")
                if (!isNetworkAvailable()) networkErrorOccurred = true else timeoutOccurred = true
            }
        }

        // 6. Final fallback: check all accounts from Cloud / persistent store
        val allCloud = getAllAccountsFromCloud()
        val fallbackMatch = allCloud.firstOrNull {
            PhoneUtil.normalizeMobile(it.mobileNumber) == normMobile &&
            PhoneUtil.normalizeGst(it.gstNumber) == normGst
        }
        if (fallbackMatch != null) {
            saveAccountToPersistentMirror(fallbackMatch)
            return@withContext CloudLookupResult.Found(fallbackMatch)
        }

        if (!isNetworkAvailable() || networkErrorOccurred) {
            return@withContext CloudLookupResult.NetworkError
        }

        if (timeoutOccurred) {
            return@withContext CloudLookupResult.Timeout
        }

        return@withContext CloudLookupResult.NotFound
    }
    suspend fun findAccountByMobileInCloud(mobileNumber: String): JewellerAccount? = withContext(Dispatchers.IO) {
        val cleanMob = PhoneUtil.normalizeMobile(mobileNumber)
        if (cleanMob.length != 10) return@withContext null

        // 1 & 2. Query Firestore SERVER lookup and query first when network is available
        val fs = getFirestore()
        if (fs != null && isNetworkAvailable()) {
            // Direct document lookup by clean mobile in jeweller_accounts_by_mobile (SERVER lookup)
            try {
                val directDoc = safeFirestoreCall(8000L) {
                    try {
                        fs.collection("jeweller_accounts_by_mobile")
                            .document(cleanMob)
                            .get(Source.SERVER)
                            .await()
                    } catch (e: Exception) {
                        fs.collection("jeweller_accounts_by_mobile")
                            .document(cleanMob)
                            .get()
                            .await()
                    }
                }
                if (directDoc?.exists() == true) {
                    val acc = parseDocToAccount(directDoc)
                    if (acc != null && PhoneUtil.normalizeMobile(acc.mobileNumber) == cleanMob) {
                        saveAccountToPersistentMirror(acc)
                        return@withContext acc
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Firestore SERVER direct by_mobile lookup: ${e.message}")
            }

            // Query jeweller_accounts where mobileNumber == cleanMob (SERVER query)
            try {
                val querySnap = safeFirestoreCall(8000L) {
                    try {
                        fs.collection("jeweller_accounts")
                            .whereEqualTo("mobileNumber", cleanMob)
                            .limit(1)
                            .get(Source.SERVER)
                            .await()
                    } catch (e: Exception) {
                        fs.collection("jeweller_accounts")
                            .whereEqualTo("mobileNumber", cleanMob)
                            .limit(1)
                            .get()
                            .await()
                    }
                }
                if (querySnap != null && !querySnap.isEmpty) {
                    val acc = parseDocToAccount(querySnap.documents[0])
                    if (acc != null && PhoneUtil.normalizeMobile(acc.mobileNumber) == cleanMob) {
                        saveAccountToPersistentMirror(acc)
                        return@withContext acc
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Firestore SERVER query by mobileNumber: ${e.message}")
            }
        }

        // 3. Existing local cloud mirror as fallback
        val localMatch = getLocalMirroredAccounts().firstOrNull { PhoneUtil.normalizeMobile(it.mobileNumber) == cleanMob }
        if (localMatch != null) return@withContext localMatch

        val allCloud = getAllAccountsFromCloud()
        return@withContext allCloud.firstOrNull { PhoneUtil.normalizeMobile(it.mobileNumber) == cleanMob }
    }

    /**
     * Looks up an account in Cloud by Jeweller Name and Mobile (for Login on new phone / reinstall / forgot password)
     */
    suspend fun findAccountInCloud(jewellerName: String, mobileNumber: String): JewellerAccount? = withContext(Dispatchers.IO) {
        val normName = normalizeText(jewellerName)
        val normMobile = normalizePhone(mobileNumber)

        // 1. Direct Firestore lookups for instant multi-device recognition
        val fs = getFirestore()
        if (fs != null) {
            if (normName.isNotEmpty() && normMobile.isNotEmpty()) {
                // When both name and mobile are specified, the record MUST match both!
                try {
                    val directDoc = safeFirestoreCall {
                        fs.collection("jeweller_accounts_by_mobile").document(normMobile).get().await()
                    }
                    if (directDoc?.exists() == true) {
                        val acc = parseDocToAccount(directDoc)
                        if (acc != null && normalizeText(acc.jewellerName).equals(normName, ignoreCase = true)) {
                            return@withContext acc
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "direct by_mobile in findAccountInCloud: ${e.message}")
                }

                try {
                    val querySnap = safeFirestoreCall {
                        fs.collection("jeweller_accounts")
                            .whereEqualTo("mobileNumber", normMobile)
                            .get()
                            .await()
                    }
                    if (querySnap != null && !querySnap.isEmpty) {
                        for (doc in querySnap.documents) {
                            val acc = parseDocToAccount(doc)
                            if (acc != null && normalizeText(acc.jewellerName).equals(normName, ignoreCase = true)) {
                                return@withContext acc
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "query mobileNumber in findAccountInCloud: ${e.message}")
                }
            } else if (normMobile.isNotEmpty()) {
                // Only mobile specified
                try {
                    val directDoc = safeFirestoreCall {
                        fs.collection("jeweller_accounts_by_mobile").document(normMobile).get().await()
                    }
                    if (directDoc?.exists() == true) {
                        val acc = parseDocToAccount(directDoc)
                        if (acc != null) return@withContext acc
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "direct by_mobile in findAccountInCloud: ${e.message}")
                }

                try {
                    val querySnap = safeFirestoreCall {
                        fs.collection("jeweller_accounts")
                            .whereEqualTo("mobileNumber", normMobile)
                            .get()
                            .await()
                    }
                    if (querySnap != null && !querySnap.isEmpty) {
                        for (doc in querySnap.documents) {
                            val acc = parseDocToAccount(doc)
                            if (acc != null) return@withContext acc
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "query mobileNumber in findAccountInCloud: ${e.message}")
                }
            } else if (normName.isNotEmpty()) {
                // Only name specified
                try {
                    val queryName = safeFirestoreCall {
                        fs.collection("jeweller_accounts")
                            .whereEqualTo("jewellerName", jewellerName.trim())
                            .get()
                            .await()
                    }
                    if (queryName != null && !queryName.isEmpty) {
                        for (doc in queryName.documents) {
                            val acc = parseDocToAccount(doc)
                            if (acc != null) return@withContext acc
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "query jewellerName in findAccountInCloud: ${e.message}")
                }
            }
        }

        val allCloud = getAllAccountsFromCloud()
        
        // Exact match on both
        val exact = allCloud.firstOrNull {
            normalizePhone(it.mobileNumber) == normMobile &&
            normalizeText(it.jewellerName).equals(normName, ignoreCase = true)
        }
        if (exact != null) return@withContext exact

        // If only mobile was given
        if (normName.isEmpty() && normMobile.isNotEmpty()) {
            val phoneMatch = allCloud.firstOrNull { normalizePhone(it.mobileNumber) == normMobile }
            if (phoneMatch != null) return@withContext phoneMatch
        }

        // If only name was given
        if (normMobile.isEmpty() && normName.isNotEmpty()) {
            val nameMatch = allCloud.firstOrNull {
                normalizeText(it.jewellerName).equals(normName, ignoreCase = true)
            }
            if (nameMatch != null) return@withContext nameMatch
        }

        null
    }

    /**
     * Finds an account in Cloud by GST Number to prevent duplicate accounts with the same GST
     */
    suspend fun findAccountByGstInCloud(gstNumber: String): JewellerAccount? = withContext(Dispatchers.IO) {
        val cleanGst = gstNumber.trim().uppercase()
        if (cleanGst.isEmpty()) return@withContext null

        val allCloud = getAllAccountsFromCloud()
        val match = allCloud.firstOrNull {
            it.gstNumber.trim().uppercase() == cleanGst
        }
        if (match != null) return@withContext match

        // Also check direct Firestore query
        try {
            val firestore = getFirestore()
            if (firestore != null && isNetworkAvailable()) {
                var gstDoc = try {
                    firestore.collection("jeweller_accounts_by_gst").document(cleanGst).get(Source.CACHE).await()
                } catch (_: Exception) { null }
                if (gstDoc == null || !gstDoc.exists()) {
                    gstDoc = safeFirestoreCall {
                        firestore.collection("jeweller_accounts_by_gst").document(cleanGst).get().await()
                    }
                }
                if (gstDoc?.exists() == true) {
                    val acc = parseDocToAccount(gstDoc)
                    if (acc != null) {
                        saveAccountToPersistentMirror(acc)
                        return@withContext acc
                    }
                }

                val snapshot = safeFirestoreCall {
                    firestore.collection("jeweller_accounts")
                        .whereEqualTo("gstNumber", cleanGst)
                        .limit(1)
                        .get()
                        .await()
                }
                if (snapshot != null) {
                    for (doc in snapshot.documents) {
                        val acc = parseDocToAccount(doc)
                        if (acc != null) {
                            saveAccountToPersistentMirror(acc)
                            return@withContext acc
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "findAccountByGstInCloud Firestore error: ${e.message}")
        }

        null
    }

    /**
     * Updates 4-digit code in Cloud during Forgot Password reset.
     * Updates across:
     * - jeweller_accounts/{accountId}
     * - jeweller_accounts_by_mobile/{normalizedMobile}
     * - jeweller_accounts_by_gst/{normalizedGST}
     * - Cloud Shared Store & all persistent files
     */
    suspend fun updateCodeInCloud(
        accountId: String,
        newCode: String,
        mobileNumber: String = "",
        gstNumber: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val normCode = PhoneUtil.normalizeCode(newCode)
            val normMobile = PhoneUtil.normalizeMobile(mobileNumber)
            val normGst = PhoneUtil.normalizeGst(gstNumber)

            val fs = getFirestore()
            if (fs != null) {
                try {
                    safeFirestoreCall {
                        fs.collection("jeweller_accounts")
                            .document(accountId)
                            .update("code4Digit", normCode)
                            .await()

                        if (normMobile.isNotEmpty()) {
                            fs.collection("jeweller_accounts_by_mobile")
                                .document(normMobile)
                                .update("code4Digit", normCode)
                                .await()
                        }

                        if (normGst.isNotEmpty()) {
                            fs.collection("jeweller_accounts_by_gst")
                                .document(normGst)
                                .update("code4Digit", normCode)
                                .await()
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Firestore code update error: ${e.message}")
                }
            }

            // Update in Cloud Shared Store
            val accountsJson = cloudStorePref.getString("registered_accounts_list", "[]") ?: "[]"
            val jsonArray = org.json.JSONArray(accountsJson)
            val updatedArray = org.json.JSONArray()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                if (obj.optString("accountId") == accountId ||
                    (normMobile.isNotEmpty() && PhoneUtil.normalizeMobile(obj.optString("mobileNumber")) == normMobile)) {
                    obj.put("code4Digit", normCode)
                }
                updatedArray.put(obj)
            }
            val serialized = updatedArray.toString()
            cloudStorePref.edit().putString("registered_accounts_list", serialized).commit()
            writeToPersistentFile(cloudDatastoreFile, serialized)
            writeToPersistentFile(externalDatastoreFile, serialized)
            writeToPersistentFile(cacheDatastoreFile, serialized)
            writeToPersistentFile(dbDatastoreFile, serialized)
            true
        } catch (e: Exception) {
            Log.e(TAG, "updateCodeInCloud failed", e)
            false
        }
    }

    /**
     * Deletes an account from cloud and all persistent storage layers.
     * Deletes:
     * - jeweller_accounts/{accountId}
     * - jeweller_accounts_by_mobile/{normalizedMobile}
     * - jeweller_accounts_by_gst/{normalizedGST}
     * - All persistent mirror files and SharedPreferences
     */
    suspend fun deleteAccountFromCloud(
        accountId: String,
        mobileNumber: String = "",
        gstNumber: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val normMobile = PhoneUtil.normalizeMobile(mobileNumber)
            val normGst = PhoneUtil.normalizeGst(gstNumber)

            // 1. Delete from Firestore
            val fs = getFirestore()
            if (fs != null) {
                try {
                    safeFirestoreCall {
                        fs.collection("jeweller_accounts")
                            .document(accountId)
                            .delete()
                            .await()

                        if (normMobile.isNotEmpty()) {
                            fs.collection("jeweller_accounts_by_mobile")
                                .document(normMobile)
                                .delete()
                                .await()
                        }

                        if (normGst.isNotEmpty()) {
                            fs.collection("jeweller_accounts_by_gst")
                                .document(normGst)
                                .delete()
                                .await()
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Firestore deleteAccount error: ${e.message}")
                }
            }

            // 2. Filter out from SharedPreferences and persistent mirror files
            val existing = getAllAccountsFromCloud().filter {
                it.accountId != accountId &&
                (normMobile.isEmpty() || PhoneUtil.normalizeMobile(it.mobileNumber) != normMobile) &&
                (normGst.isEmpty() || PhoneUtil.normalizeGst(it.gstNumber) != normGst)
            }
            val jsonArray = org.json.JSONArray()
            for (acc in existing) {
                val obj = org.json.JSONObject().apply {
                    put("accountId", acc.accountId)
                    put("jewellerName", acc.jewellerName)
                    put("mobileNumber", PhoneUtil.normalizeMobile(acc.mobileNumber))
                    put("code4Digit", PhoneUtil.normalizeCode(acc.code4Digit))
                    put("gstNumber", PhoneUtil.normalizeGst(acc.gstNumber))
                    put("isLicensed", acc.isLicensed)
                    put("status", acc.status)
                    put("createdAt", acc.createdAt)
                }
                jsonArray.put(obj)
            }
            val serialized = jsonArray.toString()
            cloudStorePref.edit().putString("registered_accounts_list", serialized).commit()
            writeToPersistentFile(cloudDatastoreFile, serialized)
            writeToPersistentFile(externalDatastoreFile, serialized)
            writeToPersistentFile(cacheDatastoreFile, serialized)
            writeToPersistentFile(dbDatastoreFile, serialized)
            true
        } catch (e: Exception) {
            Log.e(TAG, "deleteAccountFromCloud failed", e)
            false
        }
    }

    /**
     * Synchronizes ALL data for an account from Cloud (e.g. upon login on new phone or reinstall)
     */
    suspend fun restoreFullAccountFromCloud(accountId: String) = withContext(Dispatchers.IO) {
        _syncStatus.value = SyncStatus.SYNCING
        try {
            // 1. Settings restoration
            restoreSettingsFromCloud(accountId)

            // 2. Bills restoration
            restoreBillsFromCloud(accountId)

            // 3. Stock transactions restoration
            restoreStockFromCloud(accountId)

            _syncStatus.value = SyncStatus.SYNCED
            _lastSyncTimestamp.value = System.currentTimeMillis()
        } catch (e: Exception) {
            Log.e(TAG, "restoreFullAccountFromCloud error", e)
            _syncStatus.value = SyncStatus.ERROR
        }
    }

    private suspend fun restoreSettingsFromCloud(accountId: String) {
        var settingsRestored = false
        // Try Firestore
        try {
            val firestore = getFirestore()
            if (firestore != null) {
                val doc = safeFirestoreCall {
                    firestore.collection("jeweller_accounts")
                        .document(accountId)
                        .collection("settings")
                        .document("profile")
                        .get()
                        .await()
                }

                if (doc?.exists() == true) {
                    val settings = JewellerSettings(
                        accountId = accountId,
                        jewellerName = doc.getString("jewellerName") ?: "",
                        address = doc.getString("address") ?: "",
                        gstNumber = doc.getString("gstNumber") ?: "",
                        logoBase64 = doc.getString("logoBase64"),
                        contactNumber = doc.getString("contactNumber") ?: "",
                        goldRate22k = doc.getDouble("goldRate22k") ?: 0.0,
                        silverRate = doc.getDouble("silverRate") ?: 0.0,
                        language = doc.getString("language") ?: "en",
                        updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                    )
                    db.settingsDao().insertOrUpdate(settings)
                    settingsRestored = true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firestore settings restore skipped: ${e.message}")
        }

        // Check fallback Cloud Shared Store
        if (!settingsRestored) {
            val json = cloudStorePref.getString("settings_$accountId", null)
            if (json != null) {
                try {
                    val obj = org.json.JSONObject(json)
                    val settings = JewellerSettings(
                        accountId = accountId,
                        jewellerName = obj.optString("jewellerName"),
                        address = obj.optString("address"),
                        gstNumber = obj.optString("gstNumber"),
                        logoBase64 = if (obj.has("logoBase64") && !obj.isNull("logoBase64")) obj.getString("logoBase64") else null,
                        contactNumber = obj.optString("contactNumber"),
                        goldRate22k = obj.optDouble("goldRate22k", 0.0),
                        silverRate = obj.optDouble("silverRate", 0.0),
                        language = obj.optString("language", "en"),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                    db.settingsDao().insertOrUpdate(settings)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing saved cloud settings", e)
                }
            }
        }
    }

    private suspend fun restoreBillsFromCloud(accountId: String) {
        val restoredBills = mutableListOf<Bill>()

        // Try Firestore
        try {
            val firestore = getFirestore()
            if (firestore != null) {
                val docs = safeFirestoreCall {
                    firestore.collection("jeweller_accounts")
                        .document(accountId)
                        .collection("bills")
                        .get()
                        .await()
                }

            if (docs != null) {
                for (doc in docs.documents) {
                    val bill = Bill(
                        id = doc.id,
                        accountId = accountId,
                        billNumber = doc.getString("billNumber") ?: "",
                        billType = doc.getString("billType") ?: "SALE",
                        isGstBill = doc.getBoolean("isGstBill") ?: false,
                        partyName = doc.getString("partyName") ?: "",
                        partyMobile = doc.getString("partyMobile") ?: "",
                        dateTimestamp = doc.getLong("dateTimestamp") ?: System.currentTimeMillis(),
                        itemsJson = doc.getString("itemsJson") ?: "[]",
                        subtotal = doc.getDouble("subtotal") ?: 0.0,
                        gstPercent = doc.getDouble("gstPercent") ?: 3.0,
                        gstAmount = doc.getDouble("gstAmount") ?: 0.0,
                        discount = doc.getDouble("discount") ?: 0.0,
                        grandTotal = doc.getDouble("grandTotal") ?: 0.0,
                        cashReceivedOrPaid = doc.getDouble("cashReceivedOrPaid") ?: 0.0,
                        oldMetalExchangeAmount = doc.getDouble("oldMetalExchangeAmount") ?: 0.0,
                        netBalanceDue = doc.getDouble("netBalanceDue") ?: 0.0,
                        notes = doc.getString("notes") ?: "",
                        paymentsJson = doc.getString("paymentsJson") ?: "[]",
                        createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                        updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                    )
                    restoredBills.add(bill)
                }
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Firestore bills restore skipped: ${e.message}")
    }

        // Also check fallback Cloud Shared Store
        val fallbackJson = cloudStorePref.getString("bills_$accountId", "[]") ?: "[]"
        try {
            val jsonArray = org.json.JSONArray(fallbackJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.optString("id")
                if (restoredBills.none { it.id == id }) {
                    restoredBills.add(
                        Bill(
                            id = id,
                            accountId = accountId,
                            billNumber = obj.optString("billNumber"),
                            billType = obj.optString("billType", "SALE"),
                            isGstBill = obj.optBoolean("isGstBill", false),
                            partyName = obj.optString("partyName"),
                            partyMobile = obj.optString("partyMobile"),
                            dateTimestamp = obj.optLong("dateTimestamp", System.currentTimeMillis()),
                            itemsJson = obj.optString("itemsJson", "[]"),
                            subtotal = obj.optDouble("subtotal", 0.0),
                            gstPercent = obj.optDouble("gstPercent", 3.0),
                            gstAmount = obj.optDouble("gstAmount", 0.0),
                            discount = obj.optDouble("discount", 0.0),
                            grandTotal = obj.optDouble("grandTotal", 0.0),
                            cashReceivedOrPaid = obj.optDouble("cashReceivedOrPaid", 0.0),
                            oldMetalExchangeAmount = obj.optDouble("oldMetalExchangeAmount", 0.0),
                            netBalanceDue = obj.optDouble("netBalanceDue", 0.0),
                            notes = obj.optString("notes"),
                            paymentsJson = obj.optString("paymentsJson", "[]"),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring fallback bills", e)
        }

        if (restoredBills.isNotEmpty()) {
            db.billDao().insertBills(restoredBills)
        }
    }

    private suspend fun restoreStockFromCloud(accountId: String) {
        val restoredTransactions = mutableListOf<StockTransaction>()

        // Try Firestore
        try {
            val firestore = getFirestore()
            if (firestore != null) {
                val docs = safeFirestoreCall {
                    firestore.collection("jeweller_accounts")
                        .document(accountId)
                        .collection("stock_transactions")
                        .get()
                        .await()
                }

                if (docs != null) {
                    for (doc in docs.documents) {
                        val tx = StockTransaction(
                            id = doc.id,
                            accountId = accountId,
                            type = doc.getString("type") ?: "CREDIT",
                            category = doc.getString("category") ?: "GOLD_JEWELLERY",
                            quantityOrAmount = doc.getDouble("quantityOrAmount") ?: 0.0,
                            unit = doc.getString("unit") ?: "g",
                            dateTimestamp = doc.getLong("dateTimestamp") ?: System.currentTimeMillis(),
                            remark = doc.getString("remark") ?: "",
                            isAutomaticFromBill = doc.getBoolean("isAutomaticFromBill") ?: false,
                            linkedBillId = doc.getString("linkedBillId"),
                            createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                            updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                        )
                        restoredTransactions.add(tx)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firestore stock restore skipped: ${e.message}")
        }

        // Also check fallback Cloud Shared Store
        val fallbackJson = cloudStorePref.getString("stock_$accountId", "[]") ?: "[]"
        try {
            val jsonArray = org.json.JSONArray(fallbackJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.optString("id")
                if (restoredTransactions.none { it.id == id }) {
                    restoredTransactions.add(
                        StockTransaction(
                            id = id,
                            accountId = accountId,
                            type = obj.optString("type", "CREDIT"),
                            category = obj.optString("category", "GOLD_JEWELLERY"),
                            quantityOrAmount = obj.optDouble("quantityOrAmount", 0.0),
                            unit = obj.optString("unit", "g"),
                            dateTimestamp = obj.optLong("dateTimestamp", System.currentTimeMillis()),
                            remark = obj.optString("remark"),
                            isAutomaticFromBill = obj.optBoolean("isAutomaticFromBill", false),
                            linkedBillId = if (obj.has("linkedBillId") && !obj.isNull("linkedBillId")) obj.getString("linkedBillId") else null,
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring fallback stock", e)
        }

        if (restoredTransactions.isNotEmpty()) {
            val allBillIds = db.billDao().getAllBillsDirect(accountId).map { it.id }.toSet()
            // Clean up: For any bill that has deterministic automatic transactions (starts with stk_), drop legacy random UUID transactions
            val deterministicBills = restoredTransactions
                .filter { it.isAutomaticFromBill && it.linkedBillId != null && it.id.startsWith("stk_") }
                .mapNotNull { it.linkedBillId }
                .toSet()

            val cleanTransactions = restoredTransactions.filter { tx ->
                if (!tx.isAutomaticFromBill || tx.linkedBillId == null) {
                    true
                } else {
                    val billStillExists = allBillIds.isEmpty() || allBillIds.contains(tx.linkedBillId)
                    if (!billStillExists) {
                        false
                    } else if (deterministicBills.contains(tx.linkedBillId)) {
                        tx.id.startsWith("stk_")
                    } else {
                        true
                    }
                }
            }
            db.stockTransactionDao().insertTransactions(cleanTransactions)
        }
    }

    /**
     * Push a bill update/insert to Cloud
     */
    suspend fun syncBillToCloud(bill: Bill): Boolean = withContext(Dispatchers.IO) {
        var cloudSuccess = false
        try {
            _syncStatus.value = SyncStatus.SYNCING
            val firestore = getFirestore()
            if (firestore != null && isNetworkAvailable()) {
                try {
                    val billMap = hashMapOf(
                        "id" to bill.id,
                        "accountId" to bill.accountId,
                        "billNumber" to bill.billNumber,
                        "billType" to bill.billType,
                        "isGstBill" to bill.isGstBill,
                        "partyName" to bill.partyName,
                        "partyMobile" to bill.partyMobile,
                        "dateTimestamp" to bill.dateTimestamp,
                        "itemsJson" to bill.itemsJson,
                        "subtotal" to bill.subtotal,
                        "gstPercent" to bill.gstPercent,
                        "gstAmount" to bill.gstAmount,
                        "discount" to bill.discount,
                        "grandTotal" to bill.grandTotal,
                        "cashReceivedOrPaid" to bill.cashReceivedOrPaid,
                        "oldMetalExchangeAmount" to bill.oldMetalExchangeAmount,
                        "netBalanceDue" to bill.netBalanceDue,
                        "notes" to bill.notes,
                        "paymentsJson" to bill.paymentsJson,
                        "createdAt" to bill.createdAt,
                        "updatedAt" to System.currentTimeMillis()
                    )
                    val res = safeFirestoreCall(8000L) {
                        firestore.collection("jeweller_accounts")
                            .document(bill.accountId)
                            .collection("bills")
                            .document(bill.id)
                            .set(billMap, SetOptions.merge())
                            .await()
                    }
                    cloudSuccess = (res != null)
                } catch (e: Exception) {
                    Log.w(TAG, "Firestore syncBill skipped or failed: ${e.message}")
                    cloudSuccess = false
                }
            }

            // Sync to fallback Cloud Shared Store
            val billsJson = cloudStorePref.getString("bills_${bill.accountId}", "[]") ?: "[]"
            val array = org.json.JSONArray(billsJson)
            val updated = org.json.JSONArray()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                if (obj.optString("id") != bill.id) {
                    updated.put(obj)
                }
            }
            val newObj = org.json.JSONObject().apply {
                put("id", bill.id)
                put("accountId", bill.accountId)
                put("billNumber", bill.billNumber)
                put("billType", bill.billType)
                put("isGstBill", bill.isGstBill)
                put("partyName", bill.partyName)
                put("partyMobile", bill.partyMobile)
                put("dateTimestamp", bill.dateTimestamp)
                put("itemsJson", bill.itemsJson)
                put("subtotal", bill.subtotal)
                put("gstPercent", bill.gstPercent)
                put("gstAmount", bill.gstAmount)
                put("discount", bill.discount)
                put("grandTotal", bill.grandTotal)
                put("cashReceivedOrPaid", bill.cashReceivedOrPaid)
                put("oldMetalExchangeAmount", bill.oldMetalExchangeAmount)
                put("netBalanceDue", bill.netBalanceDue)
                put("notes", bill.notes)
                put("paymentsJson", bill.paymentsJson)
                put("createdAt", bill.createdAt)
                put("updatedAt", System.currentTimeMillis())
            }
            updated.put(newObj)
            cloudStorePref.edit().putString("bills_${bill.accountId}", updated.toString()).commit()

            if (cloudSuccess) {
                _syncStatus.value = SyncStatus.SYNCED
            }
            _lastSyncTimestamp.value = System.currentTimeMillis()
            cloudSuccess
        } catch (e: Exception) {
            Log.e(TAG, "syncBillToCloud failed", e)
            _syncStatus.value = SyncStatus.ERROR
            false
        }
    }

    /**
     * Delete bill from Cloud
     */
    suspend fun deleteBillFromCloud(accountId: String, billId: String): Boolean = withContext(Dispatchers.IO) {
        var cloudSuccess = false
        try {
            val firestore = getFirestore()
            if (firestore != null && isNetworkAvailable()) {
                try {
                    val res = safeFirestoreCall(8000L) {
                        firestore.collection("jeweller_accounts")
                            .document(accountId)
                            .collection("bills")
                            .document(billId)
                            .delete()
                            .await()
                    }
                    cloudSuccess = (res != null)
                } catch (e: Exception) {
                    Log.w(TAG, "Firestore deleteBill skipped or failed: ${e.message}")
                    cloudSuccess = false
                }
            }

            val billsJson = cloudStorePref.getString("bills_$accountId", "[]") ?: "[]"
            val array = org.json.JSONArray(billsJson)
            val updated = org.json.JSONArray()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                if (obj.optString("id") != billId) {
                    updated.put(obj)
                }
            }
            cloudStorePref.edit().putString("bills_$accountId", updated.toString()).commit()
            _lastSyncTimestamp.value = System.currentTimeMillis()
            cloudSuccess
        } catch (e: Exception) {
            Log.e(TAG, "deleteBillFromCloud error", e)
            false
        }
    }

    /**
     * Push stock transaction to Cloud
     */
    suspend fun syncStockTransactionToCloud(tx: StockTransaction): Boolean = withContext(Dispatchers.IO) {
        var cloudSuccess = false
        try {
            _syncStatus.value = SyncStatus.SYNCING
            val firestore = getFirestore()
            if (firestore != null && isNetworkAvailable()) {
                try {
                    val txMap = hashMapOf(
                        "id" to tx.id,
                        "accountId" to tx.accountId,
                        "type" to tx.type,
                        "category" to tx.category,
                        "quantityOrAmount" to tx.quantityOrAmount,
                        "unit" to tx.unit,
                        "dateTimestamp" to tx.dateTimestamp,
                        "remark" to tx.remark,
                        "isAutomaticFromBill" to tx.isAutomaticFromBill,
                        "linkedBillId" to (tx.linkedBillId ?: ""),
                        "createdAt" to tx.createdAt,
                        "updatedAt" to System.currentTimeMillis()
                    )
                    val res = safeFirestoreCall(8000L) {
                        firestore.collection("jeweller_accounts")
                            .document(tx.accountId)
                            .collection("stock_transactions")
                            .document(tx.id)
                            .set(txMap, SetOptions.merge())
                            .await()
                    }
                    cloudSuccess = (res != null)
                } catch (e: Exception) {
                    Log.w(TAG, "Firestore syncStock skipped or failed: ${e.message}")
                    cloudSuccess = false
                }
            }

            val stockJson = cloudStorePref.getString("stock_${tx.accountId}", "[]") ?: "[]"
            val array = org.json.JSONArray(stockJson)
            val updated = org.json.JSONArray()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                if (obj.optString("id") != tx.id) {
                    updated.put(obj)
                }
            }
            val newObj = org.json.JSONObject().apply {
                put("id", tx.id)
                put("accountId", tx.accountId)
                put("type", tx.type)
                put("category", tx.category)
                put("quantityOrAmount", tx.quantityOrAmount)
                put("unit", tx.unit)
                put("dateTimestamp", tx.dateTimestamp)
                put("remark", tx.remark)
                put("isAutomaticFromBill", tx.isAutomaticFromBill)
                if (tx.linkedBillId != null) put("linkedBillId", tx.linkedBillId)
                put("createdAt", tx.createdAt)
                put("updatedAt", System.currentTimeMillis())
            }
            updated.put(newObj)
            cloudStorePref.edit().putString("stock_${tx.accountId}", updated.toString()).commit()

            if (cloudSuccess) {
                _syncStatus.value = SyncStatus.SYNCED
            }
            _lastSyncTimestamp.value = System.currentTimeMillis()
            cloudSuccess
        } catch (e: Exception) {
            Log.e(TAG, "syncStockTransactionToCloud failed", e)
            _syncStatus.value = SyncStatus.ERROR
            false
        }
    }

    /**
     * Delete stock transaction from Cloud
     */
    suspend fun deleteStockTransactionFromCloud(accountId: String, txId: String): Boolean = withContext(Dispatchers.IO) {
        var cloudSuccess = false
        try {
            val firestore = getFirestore()
            if (firestore != null && isNetworkAvailable()) {
                try {
                    val res = safeFirestoreCall(8000L) {
                        firestore.collection("jeweller_accounts")
                            .document(accountId)
                            .collection("stock_transactions")
                            .document(txId)
                            .delete()
                            .await()
                    }
                    cloudSuccess = (res != null)
                } catch (e: Exception) {
                    Log.w(TAG, "Firestore deleteStock error: ${e.message}")
                    cloudSuccess = false
                }
            }

            val stockJson = cloudStorePref.getString("stock_$accountId", "[]") ?: "[]"
            val array = org.json.JSONArray(stockJson)
            val updated = org.json.JSONArray()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                if (obj.optString("id") != txId) {
                    updated.put(obj)
                }
            }
            cloudStorePref.edit().putString("stock_$accountId", updated.toString()).commit()
            _lastSyncTimestamp.value = System.currentTimeMillis()
            cloudSuccess
        } catch (e: Exception) {
            Log.e(TAG, "deleteStockTransactionFromCloud error", e)
            false
        }
    }

    /**
     * Delete all stock transactions linked to a bill from Cloud
     */
    suspend fun deleteLinkedStockTransactionsFromCloud(accountId: String, billId: String): Boolean = withContext(Dispatchers.IO) {
        var cloudSuccess = false
        try {
            val firestore = getFirestore()
            if (firestore != null && isNetworkAvailable()) {
                try {
                    val res = safeFirestoreCall(8000L) {
                        val query = firestore.collection("jeweller_accounts")
                            .document(accountId)
                            .collection("stock_transactions")
                            .whereEqualTo("linkedBillId", billId)
                            .get()
                            .await()

                        for (doc in query.documents) {
                            doc.reference.delete().await()
                        }
                    }
                    cloudSuccess = (res != null)
                } catch (e: Exception) {
                    Log.w(TAG, "Firestore delete linked stock error: ${e.message}")
                    cloudSuccess = false
                }
            }

            val stockJson = cloudStorePref.getString("stock_$accountId", "[]") ?: "[]"
            val array = org.json.JSONArray(stockJson)
            val updated = org.json.JSONArray()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                if (obj.optString("linkedBillId") != billId) {
                    updated.put(obj)
                }
            }
            cloudStorePref.edit().putString("stock_$accountId", updated.toString()).commit()
            _lastSyncTimestamp.value = System.currentTimeMillis()
            cloudSuccess
        } catch (e: Exception) {
            Log.e(TAG, "deleteLinkedStockTransactionsFromCloud error", e)
            false
        }
    }

    /**
     * Sync Settings (including Logo, GST, Address) to Cloud
     */
    suspend fun syncSettingsToCloud(settings: JewellerSettings): Boolean = withContext(Dispatchers.IO) {
        var cloudSuccess = false
        try {
            _syncStatus.value = SyncStatus.SYNCING
            val firestore = getFirestore()
            if (firestore != null && isNetworkAvailable()) {
                try {
                    val map = hashMapOf(
                        "accountId" to settings.accountId,
                        "jewellerName" to settings.jewellerName,
                        "address" to settings.address,
                        "gstNumber" to settings.gstNumber,
                        "logoBase64" to (settings.logoBase64 ?: ""),
                        "contactNumber" to settings.contactNumber,
                        "goldRate22k" to settings.goldRate22k,
                        "silverRate" to settings.silverRate,
                        "language" to settings.language,
                        "updatedAt" to System.currentTimeMillis()
                    )
                    val res = safeFirestoreCall(8000L) {
                        firestore.collection("jeweller_accounts")
                            .document(settings.accountId)
                            .collection("settings")
                            .document("profile")
                            .set(map, SetOptions.merge())
                            .await()
                    }
                    cloudSuccess = (res != null)
                } catch (e: Exception) {
                    Log.w(TAG, "Firestore syncSettings skipped or failed: ${e.message}")
                    cloudSuccess = false
                }
            }

            val obj = org.json.JSONObject().apply {
                put("accountId", settings.accountId)
                put("jewellerName", settings.jewellerName)
                put("address", settings.address)
                put("gstNumber", settings.gstNumber)
                if (settings.logoBase64 != null) put("logoBase64", settings.logoBase64)
                put("contactNumber", settings.contactNumber)
                put("goldRate22k", settings.goldRate22k)
                put("silverRate", settings.silverRate)
                put("language", settings.language)
                put("updatedAt", System.currentTimeMillis())
            }
            cloudStorePref.edit().putString("settings_${settings.accountId}", obj.toString()).commit()

            if (cloudSuccess) {
                _syncStatus.value = SyncStatus.SYNCED
            }
            _lastSyncTimestamp.value = System.currentTimeMillis()
            cloudSuccess
        } catch (e: Exception) {
            Log.e(TAG, "syncSettingsToCloud failed", e)
            _syncStatus.value = SyncStatus.ERROR
            false
        }
    }

    /**
     * Attach real-time cloud listeners when active on an account
     */
    fun startRealtimeCloudSync(accountId: String) {
        stopRealtimeCloudSync()
        try {
            val firestore = getFirestore() ?: return

            // Listen for bills changes from another phone
            billListener = firestore.collection("jeweller_accounts")
                .document(accountId)
                .collection("bills")
                .addSnapshotListener { snapshot, err ->
                    if (err != null || snapshot == null) return@addSnapshotListener
                    scope.launch {
                        for (change in snapshot.documentChanges) {
                            val doc = change.document
                            val billId = doc.id
                            when (change.type) {
                                com.google.firebase.firestore.DocumentChange.Type.ADDED,
                                com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> {
                                    val bill = Bill(
                                        id = billId,
                                        accountId = accountId,
                                        billNumber = doc.getString("billNumber") ?: "",
                                        billType = doc.getString("billType") ?: "SALE",
                                        isGstBill = doc.getBoolean("isGstBill") ?: false,
                                        partyName = doc.getString("partyName") ?: "",
                                        partyMobile = doc.getString("partyMobile") ?: "",
                                        dateTimestamp = doc.getLong("dateTimestamp") ?: System.currentTimeMillis(),
                                        itemsJson = doc.getString("itemsJson") ?: "[]",
                                        subtotal = doc.getDouble("subtotal") ?: 0.0,
                                        gstPercent = doc.getDouble("gstPercent") ?: 3.0,
                                        gstAmount = doc.getDouble("gstAmount") ?: 0.0,
                                        discount = doc.getDouble("discount") ?: 0.0,
                                        grandTotal = doc.getDouble("grandTotal") ?: 0.0,
                                        cashReceivedOrPaid = doc.getDouble("cashReceivedOrPaid") ?: 0.0,
                                        oldMetalExchangeAmount = doc.getDouble("oldMetalExchangeAmount") ?: 0.0,
                                        netBalanceDue = doc.getDouble("netBalanceDue") ?: 0.0,
                                        notes = doc.getString("notes") ?: "",
                                        paymentsJson = doc.getString("paymentsJson") ?: "[]",
                                        createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                                        updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                                    )
                                    db.billDao().insertBill(bill)
                                }
                                com.google.firebase.firestore.DocumentChange.Type.REMOVED -> {
                                    db.billDao().deleteBillById(billId, accountId)
                                }
                            }
                        }
                    }
                }

            // Listen for stock changes from another phone
            stockListener = firestore.collection("jeweller_accounts")
                .document(accountId)
                .collection("stock_transactions")
                .addSnapshotListener { snapshot, err ->
                    if (err != null || snapshot == null) return@addSnapshotListener
                    scope.launch {
                        for (change in snapshot.documentChanges) {
                            val doc = change.document
                            val txId = doc.id
                            when (change.type) {
                                com.google.firebase.firestore.DocumentChange.Type.ADDED,
                                com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> {
                                    val tx = StockTransaction(
                                        id = txId,
                                        accountId = accountId,
                                        type = doc.getString("type") ?: "CREDIT",
                                        category = doc.getString("category") ?: "GOLD_JEWELLERY",
                                        quantityOrAmount = doc.getDouble("quantityOrAmount") ?: 0.0,
                                        unit = doc.getString("unit") ?: "g",
                                        dateTimestamp = doc.getLong("dateTimestamp") ?: System.currentTimeMillis(),
                                        remark = doc.getString("remark") ?: "",
                                        isAutomaticFromBill = doc.getBoolean("isAutomaticFromBill") ?: false,
                                        linkedBillId = doc.getString("linkedBillId"),
                                        createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                                        updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                                    )
                                    db.stockTransactionDao().insertTransaction(tx)
                                }
                                com.google.firebase.firestore.DocumentChange.Type.REMOVED -> {
                                    db.stockTransactionDao().deleteById(txId, accountId)
                                }
                            }
                        }
                    }
                }

            // Listen for settings and gold/silver rate changes from another phone
            settingsListener = firestore.collection("jeweller_accounts")
                .document(accountId)
                .collection("settings")
                .document("profile")
                .addSnapshotListener { snapshot, err ->
                    if (err != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                    scope.launch {
                        val settings = JewellerSettings(
                            accountId = accountId,
                            jewellerName = snapshot.getString("jewellerName") ?: "",
                            address = snapshot.getString("address") ?: "",
                            gstNumber = snapshot.getString("gstNumber") ?: "",
                            logoBase64 = snapshot.getString("logoBase64"),
                            contactNumber = snapshot.getString("contactNumber") ?: "",
                            goldRate22k = snapshot.getDouble("goldRate22k") ?: 0.0,
                            silverRate = snapshot.getDouble("silverRate") ?: 0.0,
                            language = snapshot.getString("language") ?: "en",
                            updatedAt = snapshot.getLong("updatedAt") ?: System.currentTimeMillis()
                        )
                        db.settingsDao().insertOrUpdate(settings)
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Realtime sync listener registration skipped: ${e.message}")
        }
    }

    private var periodicSyncJob: Job? = null

    /**
     * Executes a complete two-way auto sync:
     * 1. Pulls all remote Bills, Stock Transactions, and Settings from Cloud into local Room DB (Safe merge/restore).
     * 2. Pushes local Bills, Stock Transactions, Settings, and Accounts to Cloud.
     * 3. Processes pending sync queue.
     */
    suspend fun performFullTwoWayAutoSync(accountId: String) = withContext(Dispatchers.IO) {
        if (accountId.isBlank()) return@withContext
        try {
            _syncStatus.value = SyncStatus.SYNCING

            // 1, 2, 3. PULL: Pull latest cloud data and merge/restore into local Room DB safely
            restoreSettingsFromCloud(accountId)
            restoreBillsFromCloud(accountId)
            restoreStockFromCloud(accountId)

            // 4. PUSH: Local changes to Cloud
            try {
                val localAccount = db.accountDao().getAccountById(accountId)
                if (localAccount != null) {
                    registerAccountToCloud(localAccount)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Account push skipped: ${e.message}")
            }

            try {
                val localBills = db.billDao().getAllBillsDirect(accountId)
                for (bill in localBills) {
                    syncBillToCloud(bill)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Bills push skipped: ${e.message}")
            }

            try {
                val localStock = db.stockTransactionDao().getAllTransactionsDirect(accountId)
                for (tx in localStock) {
                    syncStockTransactionToCloud(tx)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Stock transactions push skipped: ${e.message}")
            }

            try {
                val localSettings = db.settingsDao().getSettingsDirect(accountId)
                if (localSettings != null) {
                    syncSettingsToCloud(localSettings)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Settings push skipped: ${e.message}")
            }

            // 5. Process pending sync queue
            processPendingQueue()

            _syncStatus.value = if (pendingQueue.isEmpty()) SyncStatus.SYNCED else SyncStatus.PENDING_SYNC
            _lastSyncTimestamp.value = System.currentTimeMillis()
        } catch (e: Exception) {
            Log.e(TAG, "performFullTwoWayAutoSync failed", e)
            _syncStatus.value = SyncStatus.ERROR
        }
    }

    /**
     * Starts continuous background automatic synchronization and realtime Firestore listeners
     */
    fun startPeriodicAutoSync(accountId: String) {
        if (accountId.isBlank()) return
        startRealtimeCloudSync(accountId)
        periodicSyncJob?.cancel()
        periodicSyncJob = scope.launch {
            // Immediate sync on launch / login
            try {
                performFullTwoWayAutoSync(accountId)
            } catch (e: Exception) {
                Log.w(TAG, "Initial auto-sync skipped: ${e.message}")
            }

            // Periodic auto sync every 20 seconds while app is active
            while (isActive) {
                delay(20000L)
                try {
                    performFullTwoWayAutoSync(accountId)
                } catch (e: Exception) {
                    Log.w(TAG, "Periodic auto-sync iteration skipped: ${e.message}")
                }
            }
        }
    }

    /**
     * Triggers an immediate asynchronous background sync
     */
    fun triggerAutoSync(accountId: String) {
        if (accountId.isBlank()) return
        scope.launch {
            try {
                performFullTwoWayAutoSync(accountId)
            } catch (e: Exception) {
                Log.w(TAG, "triggerAutoSync skipped: ${e.message}")
            }
        }
    }

    fun stopPeriodicAutoSync() {
        periodicSyncJob?.cancel()
        periodicSyncJob = null
        stopRealtimeCloudSync()
    }

    fun stopRealtimeCloudSync() {
        billListener?.remove()
        billListener = null
        stockListener?.remove()
        stockListener = null
        settingsListener?.remove()
        settingsListener = null
    }

    /**
     * Processes all queued pending sync operations when network connectivity is established
     */
    suspend fun processPendingQueue() = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable()) {
            if (pendingQueue.isNotEmpty()) {
                _syncStatus.value = SyncStatus.OFFLINE
            }
            return@withContext
        }

        queueMutex.withLock {
            if (pendingQueue.isEmpty()) {
                _syncStatus.value = SyncStatus.SYNCED
                return@withLock
            }

            _syncStatus.value = SyncStatus.SYNCING
            val itemsToProcess = synchronized(pendingQueue) { pendingQueue.toList() }
            val successfullyProcessed = mutableListOf<PendingSyncItem>()

            for (item in itemsToProcess) {
                try {
                    var success = false
                    when (item.entityType) {
                        "BILL" -> {
                            if (item.action == "DELETE") {
                                val d1 = deleteBillFromCloud(item.accountId, item.entityId)
                                val d2 = deleteLinkedStockTransactionsFromCloud(item.accountId, item.entityId)
                                success = d1 && d2
                            } else {
                                val bill = db.billDao().getBillById(item.entityId)
                                if (bill != null) {
                                    success = syncBillToCloud(bill)
                                } else {
                                    // Bill was deleted locally, nothing left to upsert
                                    success = true
                                }
                            }
                        }
                        "STOCK" -> {
                            if (item.action == "DELETE") {
                                success = deleteStockTransactionFromCloud(item.accountId, item.entityId)
                            } else {
                                val tx = db.stockTransactionDao().getById(item.entityId)
                                if (tx != null) {
                                    success = syncStockTransactionToCloud(tx)
                                } else {
                                    success = true
                                }
                            }
                        }
                        "SETTINGS" -> {
                            val settings = db.settingsDao().getSettingsDirect(item.accountId)
                            if (settings != null) {
                                success = syncSettingsToCloud(settings)
                            } else {
                                success = true
                            }
                        }
                        "ACCOUNT" -> {
                            val account = db.accountDao().getAccountById(item.entityId)
                            if (account != null) {
                                val res = registerAccountToCloud(account)
                                success = res
                            } else {
                                success = true
                            }
                        }
                        else -> {
                            success = true
                        }
                    }

                    if (success) {
                        successfullyProcessed.add(item)
                    } else {
                        item.retryCount++
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error syncing pending item ${item.entityId}: ${e.message}")
                    item.retryCount++
                }
            }

            synchronized(pendingQueue) {
                pendingQueue.removeAll(successfullyProcessed)
            }
            savePendingQueueToDisk()
            updateQueueStateFlows()

            if (pendingQueue.isEmpty()) {
                _syncStatus.value = SyncStatus.SYNCED
                _lastSyncTimestamp.value = System.currentTimeMillis()
            } else {
                _syncStatus.value = SyncStatus.PENDING_SYNC
            }
        }
    }

    fun enqueueBill(bill: Bill, action: String = "UPSERT") {
        scope.launch(Dispatchers.IO) {
            queueMutex.withLock {
                synchronized(pendingQueue) {
                    pendingQueue.removeAll { it.entityType == "BILL" && it.entityId == bill.id }
                    pendingQueue.add(
                        PendingSyncItem(
                            accountId = bill.accountId,
                            entityType = "BILL",
                            entityId = bill.id,
                            action = action
                        )
                    )
                }
                savePendingQueueToDisk()
                updateQueueStateFlows()
            }
            if (isNetworkAvailable()) {
                processPendingQueue()
            } else {
                _syncStatus.value = SyncStatus.PENDING_SYNC
            }
        }
    }

    fun enqueueDeleteBill(accountId: String, billId: String) {
        scope.launch(Dispatchers.IO) {
            queueMutex.withLock {
                synchronized(pendingQueue) {
                    pendingQueue.removeAll { it.entityType == "BILL" && it.entityId == billId }
                    pendingQueue.add(
                        PendingSyncItem(
                            accountId = accountId,
                            entityType = "BILL",
                            entityId = billId,
                            action = "DELETE"
                        )
                    )
                }
                savePendingQueueToDisk()
                updateQueueStateFlows()
            }
            if (isNetworkAvailable()) {
                processPendingQueue()
            } else {
                _syncStatus.value = SyncStatus.PENDING_SYNC
            }
        }
    }

    fun enqueueStockTransaction(tx: StockTransaction, action: String = "UPSERT") {
        scope.launch(Dispatchers.IO) {
            queueMutex.withLock {
                synchronized(pendingQueue) {
                    pendingQueue.removeAll { it.entityType == "STOCK" && it.entityId == tx.id }
                    pendingQueue.add(
                        PendingSyncItem(
                            accountId = tx.accountId,
                            entityType = "STOCK",
                            entityId = tx.id,
                            action = action
                        )
                    )
                }
                savePendingQueueToDisk()
                updateQueueStateFlows()
            }
            if (isNetworkAvailable()) {
                processPendingQueue()
            } else {
                _syncStatus.value = SyncStatus.PENDING_SYNC
            }
        }
    }

    fun enqueueDeleteStockTransaction(accountId: String, txId: String) {
        scope.launch(Dispatchers.IO) {
            queueMutex.withLock {
                synchronized(pendingQueue) {
                    pendingQueue.removeAll { it.entityType == "STOCK" && it.entityId == txId }
                    pendingQueue.add(
                        PendingSyncItem(
                            accountId = accountId,
                            entityType = "STOCK",
                            entityId = txId,
                            action = "DELETE"
                        )
                    )
                }
                savePendingQueueToDisk()
                updateQueueStateFlows()
            }
            if (isNetworkAvailable()) {
                processPendingQueue()
            } else {
                _syncStatus.value = SyncStatus.PENDING_SYNC
            }
        }
    }

    fun enqueueSettings(settings: JewellerSettings) {
        scope.launch(Dispatchers.IO) {
            queueMutex.withLock {
                synchronized(pendingQueue) {
                    pendingQueue.removeAll { it.entityType == "SETTINGS" && it.accountId == settings.accountId }
                    pendingQueue.add(
                        PendingSyncItem(
                            accountId = settings.accountId,
                            entityType = "SETTINGS",
                            entityId = settings.accountId,
                            action = "UPSERT"
                        )
                    )
                }
                savePendingQueueToDisk()
                updateQueueStateFlows()
            }
            if (isNetworkAvailable()) {
                processPendingQueue()
            } else {
                _syncStatus.value = SyncStatus.PENDING_SYNC
            }
        }
    }

    fun enqueueAccount(account: JewellerAccount) {
        scope.launch(Dispatchers.IO) {
            queueMutex.withLock {
                synchronized(pendingQueue) {
                    pendingQueue.removeAll { it.entityType == "ACCOUNT" && it.entityId == account.accountId }
                    pendingQueue.add(
                        PendingSyncItem(
                            accountId = account.accountId,
                            entityType = "ACCOUNT",
                            entityId = account.accountId,
                            action = "UPSERT"
                        )
                    )
                }
                savePendingQueueToDisk()
                updateQueueStateFlows()
            }
            if (isNetworkAvailable()) {
                processPendingQueue()
            } else {
                _syncStatus.value = SyncStatus.PENDING_SYNC
            }
        }
    }
}
