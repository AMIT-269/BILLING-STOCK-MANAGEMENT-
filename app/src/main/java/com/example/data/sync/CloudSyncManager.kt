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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
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

    // CI/Robolectric test mode: do not start background WorkManager/network
    // monitors or infinite periodic jobs; they can keep the test JVM alive.
    private val isCiTestMode: Boolean
        get() = System.getProperty("billing.skipFirebaseNetworkTests") == "true"

    init {
        loadPendingQueueFromDisk()
        if (!isCiTestMode) {
            initNetworkMonitoring()
            schedulePeriodicSafetySync()
        }
    }

    fun scheduleWorkManagerSync() {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val syncWorkRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    10,
                    TimeUnit.SECONDS
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                SyncWorker.WORK_NAME_ONE_TIME,
                ExistingWorkPolicy.REPLACE,
                syncWorkRequest
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error scheduling WorkManager sync: ${e.message}")
        }
    }

    fun schedulePeriodicSafetySync() {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val periodicRequest = PeriodicWorkRequestBuilder<SyncWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                SyncWorker.WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error scheduling periodic safety sync: ${e.message}")
        }
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
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val activeNet = cm.activeNetwork
                if (activeNet != null) {
                    val caps = cm.getNetworkCapabilities(activeNet)
                    if (caps != null && (
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    )) {
                        return true
                    }
                }
            }
            @Suppress("DEPRECATION")
            val netInfo = cm.activeNetworkInfo
            if (netInfo != null && netInfo.isConnectedOrConnecting) {
                return true
            }
            cm.activeNetwork != null
        } catch (_: Exception) {
            true
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

    sealed class FirestoreInstanceResult {
        data class Success(val firestore: FirebaseFirestore) : FirestoreInstanceResult()
        data class ConfigError(val message: String) : FirestoreInstanceResult()
    }

    private fun getFirestoreInstance(): FirestoreInstanceResult {
        if (System.getProperty("billing.skipFirebaseNetworkTests") == "true") {
            return FirestoreInstanceResult.ConfigError("Firebase network disabled for unit tests")
        }
        return try {
            val app = if (FirebaseApp.getApps(context).isEmpty()) {
                val initialized = FirebaseApp.initializeApp(context)
                if (initialized == null) {
                    val options = FirebaseOptions.fromResource(context)
                    if (options != null) {
                        FirebaseApp.initializeApp(context, options)
                    } else {
                        Log.w(TAG, "LOGIN_LOOKUP: FIREBASE_INIT_ERROR - No Firebase configuration available from resource")
                        return FirestoreInstanceResult.ConfigError("No Firebase configuration available")
                    }
                } else {
                    initialized
                }
            } else {
                FirebaseApp.getInstance()
            }
            if (app == null) {
                Log.w(TAG, "LOGIN_LOOKUP: FIREBASE_INIT_ERROR - FirebaseApp is null")
                return FirestoreInstanceResult.ConfigError("FirebaseApp is null")
            }
            val firestore = FirebaseFirestore.getInstance(app)
            try {
                val settings = FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true)
                    .build()
                firestore.firestoreSettings = settings
            } catch (_: Exception) {}
            FirestoreInstanceResult.Success(firestore)
        } catch (e: Exception) {
            Log.w(TAG, "LOGIN_LOOKUP: FIREBASE_INIT_ERROR - ${e.message}")
            FirestoreInstanceResult.ConfigError(e.message ?: "Firebase initialization failed")
        }
    }

    fun getFirestore(): FirebaseFirestore? {
        return when (val res = getFirestoreInstance()) {
            is FirestoreInstanceResult.Success -> res.firestore
            is FirestoreInstanceResult.ConfigError -> null
        }
    }

    sealed class FirestoreCallResult<out T> {
        data class Success<out T>(val data: T) : FirestoreCallResult<T>()
        object Timeout : FirestoreCallResult<Nothing>()
        object NetworkError : FirestoreCallResult<Nothing>()
        data class FirebaseConfigError(val message: String) : FirestoreCallResult<Nothing>()
        data class Error(val message: String, val cause: Throwable? = null) : FirestoreCallResult<Nothing>()

        fun getOrNull(): T? = (this as? Success)?.data
        fun isSuccess(): Boolean = this is Success
    }

    private suspend fun ensureFirebaseAnonymousAuth(): Boolean {
        return try {
            val app = FirebaseApp.getInstance()
            val auth = FirebaseAuth.getInstance(app)
            if (auth.currentUser != null) return true
            auth.signInAnonymously().await()
            auth.currentUser != null
        } catch (e: Exception) {
            Log.w(TAG, "LOGIN_LOOKUP: FIREBASE_AUTH_ERROR - " + e.message)
            false
        }
    }

    private suspend fun <T> safeFirestoreCall(
        timeoutMs: Long = 8000L,
        block: suspend () -> T
    ): FirestoreCallResult<T> {
        if (System.getProperty("billing.skipFirebaseNetworkTests") == "true") {
            Log.i(TAG, "Firestore call skipped in unit-test environment")
            return FirestoreCallResult.Error("Unit-test Firebase network disabled")
        }
        val netAvailable = isNetworkAvailable()
        if (!netAvailable) {
            Log.w(TAG, "LOGIN_LOOKUP: FIRESTORE_NETWORK_ERROR - Network unavailable")
            return FirestoreCallResult.NetworkError
        }
        // Never let Firebase anonymous-auth initialization block a build/login forever.
        // Robolectric/unstable networks can report connectivity while Firebase auth is unavailable.
        val authAvailable = try {
            kotlinx.coroutines.withTimeout(timeoutMs.coerceAtMost(5000L)) {
                ensureFirebaseAnonymousAuth()
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.w(TAG, "LOGIN_LOOKUP: FIREBASE_AUTH_TIMEOUT - Timeout before Firestore call")
            false
        } catch (e: Exception) {
            Log.w(TAG, "LOGIN_LOOKUP: FIREBASE_AUTH_ERROR - ${e.message}")
            false
        }
        if (!authAvailable) {
            Log.w(TAG, "LOGIN_LOOKUP: FIREBASE_AUTH_ERROR - Anonymous Firebase authentication is unavailable")
            return FirestoreCallResult.Error("Firebase authentication unavailable")
        }
        return try {
            val result = kotlinx.coroutines.withTimeout(timeoutMs) {
                block()
            }
            FirestoreCallResult.Success(result)
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.w(TAG, "LOGIN_LOOKUP: FIRESTORE_TIMEOUT - Timeout after ${timeoutMs}ms")
            FirestoreCallResult.Timeout
        } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
            Log.w(TAG, "LOGIN_LOOKUP: FIRESTORE_ERROR - code: ${e.code}, msg: ${e.message}")
            if (!isNetworkAvailable()) {
                Log.w(TAG, "LOGIN_LOOKUP: FIRESTORE_NETWORK_ERROR - Network disconnected")
                FirestoreCallResult.NetworkError
            } else {
                FirestoreCallResult.Error(e.message ?: "Firestore error (${e.code})", e)
            }
        } catch (e: java.io.IOException) {
            Log.w(TAG, "LOGIN_LOOKUP: FIRESTORE_IO_ERROR - ${e.message}")
            if (!isNetworkAvailable()) {
                FirestoreCallResult.NetworkError
            } else {
                FirestoreCallResult.Error(e.message ?: "Network error", e)
            }
        } catch (e: Exception) {
            Log.w(TAG, "LOGIN_LOOKUP: FIRESTORE_ERROR - ${e.message}")
            if (!isNetworkAvailable()) {
                FirestoreCallResult.NetworkError
            } else {
                FirestoreCallResult.Error(e.message ?: "Firestore call failed", e)
            }
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
                        // 1. Authoritative source: jeweller_accounts/{accountId}
                        fs.collection("jeweller_accounts")
                            .document(account.accountId)
                            .set(accountMap, SetOptions.merge())
                            .await()

                        // 2. Identity index document: jeweller_accounts_by_identity/{mobile}_{gst}
                        if (cleanMob.isNotEmpty() && cleanGst.isNotEmpty()) {
                            fs.collection("jeweller_accounts_by_identity")
                                .document("${cleanMob}_${cleanGst}")
                                .set(accountMap, SetOptions.merge())
                                .await()
                        }

                        // 3. Mobile index: NEVER overwrite an account with a different GST!
                        if (cleanMob.isNotEmpty()) {
                            try {
                                val existingMobDoc = fs.collection("jeweller_accounts_by_mobile").document(cleanMob).get().await()
                                val docAccId = existingMobDoc.getString("accountId")
                                val docGst = PhoneUtil.normalizeGst(existingMobDoc.getString("gstNumber"))
                                if (!existingMobDoc.exists() || docAccId == account.accountId || docGst == cleanGst || docGst.isEmpty()) {
                                    fs.collection("jeweller_accounts_by_mobile")
                                        .document(cleanMob)
                                        .set(accountMap, SetOptions.merge())
                                        .await()
                                }
                            } catch (_: Exception) {}
                        }

                        if (cleanGst.isNotEmpty()) {
                            fs.collection("jeweller_accounts_by_gst")
                                .document(cleanGst)
                                .set(accountMap, SetOptions.merge())
                                .await()
                        }
                    }
                    firestoreSuccess = (res is FirestoreCallResult.Success)
                } catch (e: Exception) {
                    Log.w(TAG, "Firestore direct save skipped or failed: ${e.message}")
                    firestoreSuccess = false
                }
            }

            // 2. Load all existing accounts from local mirrors to avoid overwriting
            val normalizedAccount = account.copy(mobileNumber = cleanMob, gstNumber = cleanGst)
            val existing = getLocalMirroredAccounts().toMutableList()
            val idx = existing.indexOfFirst {
                (account.accountId.isNotBlank() && it.accountId == account.accountId) ||
                (PhoneUtil.normalizeMobile(it.mobileNumber) == cleanMob && PhoneUtil.normalizeGst(it.gstNumber) == cleanGst)
            }
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
     * Replaces an account's login identity while preserving its permanent accountId and all data.
     * Old Mobile/GST identity indexes are removed and the new identity indexes are written atomically.
     */
    suspend fun replaceAccountIdentityInCloud(oldAccount: JewellerAccount, newAccount: JewellerAccount): Boolean = withContext(Dispatchers.IO) {
        try {
            // Unit tests intentionally disable Firebase network access. Return to the
            // local persistence path immediately instead of waiting on anonymous auth.
            if (System.getProperty("billing.skipFirebaseNetworkTests") == "true") {
                return@withContext false
            }
            val fs = getFirestore() ?: return@withContext false
            if (!ensureFirebaseAnonymousAuth()) return@withContext false
            val oldMob = PhoneUtil.normalizeMobile(oldAccount.mobileNumber)
            val oldGst = PhoneUtil.normalizeGst(oldAccount.gstNumber)
            val newMob = PhoneUtil.normalizeMobile(newAccount.mobileNumber)
            val newGst = PhoneUtil.normalizeGst(newAccount.gstNumber)
            val accountMap = hashMapOf<String, Any>(
                "accountId" to newAccount.accountId,
                "jewellerName" to newAccount.jewellerName.trim(),
                "mobileNumber" to newMob,
                "code4Digit" to PhoneUtil.normalizeCode(newAccount.code4Digit),
                "gstNumber" to newGst,
                "isLicensed" to newAccount.isLicensed,
                "status" to newAccount.status,
                "createdAt" to newAccount.createdAt
            )
            val batch = fs.batch()
            batch.set(fs.collection("jeweller_accounts").document(newAccount.accountId), accountMap, SetOptions.merge())
            if (oldMob.isNotEmpty() && oldMob != newMob) batch.delete(fs.collection("jeweller_accounts_by_mobile").document(oldMob))
            if (oldGst.isNotEmpty() && oldGst != newGst) batch.delete(fs.collection("jeweller_accounts_by_gst").document(oldGst))
            if (oldMob.isNotEmpty() && oldGst.isNotEmpty() && (oldMob != newMob || oldGst != newGst)) {
                batch.delete(fs.collection("jeweller_accounts_by_identity").document("${oldMob}_${oldGst}"))
            }
            if (newMob.isNotEmpty()) batch.set(fs.collection("jeweller_accounts_by_mobile").document(newMob), accountMap, SetOptions.merge())
            if (newGst.isNotEmpty()) batch.set(fs.collection("jeweller_accounts_by_gst").document(newGst), accountMap, SetOptions.merge())
            if (newMob.isNotEmpty() && newGst.isNotEmpty()) {
                batch.set(fs.collection("jeweller_accounts_by_identity").document("${newMob}_${newGst}"), accountMap, SetOptions.merge())
            }
            batch.commit().await()
            saveAccountToPersistentMirror(newAccount)
            _syncStatus.value = SyncStatus.SYNCED
            _lastSyncTimestamp.value = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            Log.w(TAG, "replaceAccountIdentityInCloud failed: " + e.message)
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
        val cleanMob = PhoneUtil.normalizeMobile(account.mobileNumber)
        val cleanGst = PhoneUtil.normalizeGst(account.gstNumber)
        val normalizedAccount = account.copy(mobileNumber = cleanMob, gstNumber = cleanGst)
        // One permanent accountId must have exactly one local mirror identity.
        // Remove its OLD Mobile/GST record before writing the NEW identity.
        val existing = getLocalMirroredAccounts()
            .filterNot { it.accountId == account.accountId }
            .toMutableList()
        existing.add(normalizedAccount)
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

        // 2. Query Firestore only in the real app. Robolectric/CI tests must
        // remain deterministic and use the local mirror + Room only.
        if (System.getProperty("billing.skipFirebaseNetworkTests") != "true" && isNetworkAvailable()) {
            val fs = getFirestore()
            if (fs != null) {
                try {
                    val snapshot = safeFirestoreCall(3000L) {
                        fs.collection("jeweller_accounts").get().await()
                    }.getOrNull()
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
        data class FirebaseConfigError(val message: String) : CloudLookupResult()
        data class Error(val message: String) : CloudLookupResult()
    }

    /**
     * Mobile-only account lookup result from Cloud with distinct multiplicity handling
     */
    sealed class CloudMobileLookupResult {
        data class FoundSingle(val account: JewellerAccount) : CloudMobileLookupResult()
        object FoundMultiple : CloudMobileLookupResult()
        object NotFound : CloudMobileLookupResult()
        object Timeout : CloudMobileLookupResult()
        object NetworkError : CloudMobileLookupResult()
        data class FirebaseConfigError(val message: String) : CloudMobileLookupResult()
        data class Error(val message: String) : CloudMobileLookupResult()
    }

    /**
     * Centralized login lookup by Mobile Number and GST Number.
     * Identity rule: 10-Digit Normalized Mobile Number + Uppercase Normalized GST Number.
     * Both MUST match the same account.
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

        // Check local mirror first (local-first resilience)
        val localMatch = getLocalMirroredAccounts().firstOrNull {
            PhoneUtil.normalizeMobile(it.mobileNumber) == normMobile && PhoneUtil.normalizeGst(it.gstNumber) == normGst
        }
        if (localMatch != null) return@withContext CloudLookupResult.Found(localMatch)

        if (!isNetworkAvailable()) {
            Log.w(TAG, "LOGIN_LOOKUP: FIRESTORE_NETWORK_ERROR - Network unavailable for GST login")
            return@withContext CloudLookupResult.NetworkError
        }

        val fsResult = getFirestoreInstance()
        val fs = when (fsResult) {
            is FirestoreInstanceResult.Success -> fsResult.firestore
            is FirestoreInstanceResult.ConfigError -> {
                Log.w(TAG, "LOGIN_LOOKUP: FIREBASE_INIT_ERROR - ${fsResult.message}")
                return@withContext CloudLookupResult.FirebaseConfigError(fsResult.message)
            }
        }

        var networkErrorOccurred = false
        var timeoutOccurred = false
        var configErrorOccurred = false
        var lastError: String? = null

        // 1. Direct lookup in jeweller_accounts_by_identity/{normMobile}_{normGst} (Fast 3500ms)
        val identResult = safeFirestoreCall(3500L) {
            try {
                fs.collection("jeweller_accounts_by_identity").document("${normMobile}_${normGst}").get(Source.SERVER).await()
            } catch (_: Exception) {
                fs.collection("jeweller_accounts_by_identity").document("${normMobile}_${normGst}").get().await()
            }
        }

        when (identResult) {
            is FirestoreCallResult.Success -> {
                val identDoc = identResult.data
                if (identDoc.exists()) {
                    val acc = parseDocToAccount(identDoc)
                    if (acc != null &&
                        PhoneUtil.normalizeMobile(acc.mobileNumber) == normMobile &&
                        PhoneUtil.normalizeGst(acc.gstNumber) == normGst
                    ) {
                        saveAccountToPersistentMirror(acc)
                        Log.i(TAG, "LOGIN_LOOKUP: FIRESTORE_IDENTITY_FOUND ($normMobile, $normGst)")
                        return@withContext CloudLookupResult.Found(acc)
                    }
                }
            }
            is FirestoreCallResult.Timeout -> timeoutOccurred = true
            is FirestoreCallResult.NetworkError -> networkErrorOccurred = true
            is FirestoreCallResult.FirebaseConfigError -> {
                configErrorOccurred = true
                lastError = identResult.message
            }
            is FirestoreCallResult.Error -> lastError = identResult.message
        }

        // 2. Direct lookup in jeweller_accounts where mobileNumber == normMobile AND gstNumber == normGst
        val queryResult = safeFirestoreCall(3500L) {
            try {
                fs.collection("jeweller_accounts")
                    .whereEqualTo("mobileNumber", normMobile)
                    .whereEqualTo("gstNumber", normGst)
                    .limit(1)
                    .get(Source.SERVER)
                    .await()
            } catch (_: Exception) {
                fs.collection("jeweller_accounts")
                    .whereEqualTo("mobileNumber", normMobile)
                    .whereEqualTo("gstNumber", normGst)
                    .limit(1)
                    .get()
                    .await()
            }
        }

        when (queryResult) {
            is FirestoreCallResult.Success -> {
                val querySnap = queryResult.data
                if (!querySnap.isEmpty) {
                    val acc = parseDocToAccount(querySnap.documents[0])
                    if (acc != null &&
                        PhoneUtil.normalizeMobile(acc.mobileNumber) == normMobile &&
                        PhoneUtil.normalizeGst(acc.gstNumber) == normGst
                    ) {
                        saveAccountToPersistentMirror(acc)
                        Log.i(TAG, "LOGIN_LOOKUP: FIRESTORE_QUERY_FOUND ($normMobile, $normGst)")
                        return@withContext CloudLookupResult.Found(acc)
                    }
                }
            }
            is FirestoreCallResult.Timeout -> timeoutOccurred = true
            is FirestoreCallResult.NetworkError -> networkErrorOccurred = true
            is FirestoreCallResult.FirebaseConfigError -> {
                configErrorOccurred = true
                lastError = queryResult.message
            }
            is FirestoreCallResult.Error -> lastError = queryResult.message
        }

        // Fallback to local persistent mirror when server is not reached or account not found on server
        val fallbackLocalMatch = getLocalMirroredAccounts().firstOrNull {
            PhoneUtil.normalizeMobile(it.mobileNumber) == normMobile && PhoneUtil.normalizeGst(it.gstNumber) == normGst
        }
        if (fallbackLocalMatch != null) {
            return@withContext CloudLookupResult.Found(fallbackLocalMatch)
        }

        if (!isNetworkAvailable()) {
            Log.w(TAG, "LOGIN_LOOKUP: FIRESTORE_NETWORK_ERROR")
            return@withContext CloudLookupResult.NetworkError
        }

        if (timeoutOccurred) {
            Log.w(TAG, "LOGIN_LOOKUP: FIRESTORE_TIMEOUT")
            return@withContext CloudLookupResult.Timeout
        }

        if (configErrorOccurred) {
            Log.w(TAG, "LOGIN_LOOKUP: FIREBASE_INIT_ERROR")
            return@withContext CloudLookupResult.FirebaseConfigError(lastError ?: "Firebase configuration error")
        }

        if (lastError != null) {
            Log.w(TAG, "LOGIN_LOOKUP: FIRESTORE_ERROR - $lastError")
            return@withContext CloudLookupResult.Error(lastError)
        }

        Log.i(TAG, "LOGIN_LOOKUP: FIRESTORE_NOT_FOUND")
        return@withContext CloudLookupResult.NotFound
    }

    /**
     * Look up accounts in cloud by mobile number with multiplicity detection.
     */
    suspend fun findAccountsByMobileInCloud(mobileNumber: String): CloudMobileLookupResult = withContext(Dispatchers.IO) {
        val cleanMob = PhoneUtil.normalizeMobile(mobileNumber)
        if (cleanMob.length != 10) return@withContext CloudMobileLookupResult.Error("Invalid Mobile Number")

        val localMatches = getLocalMirroredAccounts().filter { PhoneUtil.normalizeMobile(it.mobileNumber) == cleanMob }
            .distinctBy { PhoneUtil.normalizeGst(it.gstNumber) }

        if (!isNetworkAvailable()) {
            if (localMatches.size > 1) return@withContext CloudMobileLookupResult.FoundMultiple
            if (localMatches.size == 1) return@withContext CloudMobileLookupResult.FoundSingle(localMatches.first())
            Log.w(TAG, "LOGIN_LOOKUP: FIRESTORE_NETWORK_ERROR - Network unavailable for Mobile lookup")
            return@withContext CloudMobileLookupResult.NetworkError
        }

        val fsResult = getFirestoreInstance()
        val fs = when (fsResult) {
            is FirestoreInstanceResult.Success -> fsResult.firestore
            is FirestoreInstanceResult.ConfigError -> {
                Log.w(TAG, "LOGIN_LOOKUP: FIREBASE_INIT_ERROR - ${fsResult.message}")
                if (localMatches.size > 1) return@withContext CloudMobileLookupResult.FoundMultiple
                if (localMatches.size == 1) return@withContext CloudMobileLookupResult.FoundSingle(localMatches.first())
                return@withContext CloudMobileLookupResult.FirebaseConfigError(fsResult.message)
            }
        }

        var networkErrorOccurred = false
        var timeoutOccurred = false
        var configErrorOccurred = false
        var lastError: String? = null
        val discoveredCloudAccounts = mutableListOf<JewellerAccount>()

        // Direct document lookup by clean mobile in jeweller_accounts_by_mobile
        val directResult = safeFirestoreCall(3500L) {
            try {
                fs.collection("jeweller_accounts_by_mobile").document(cleanMob).get(Source.SERVER).await()
            } catch (_: Exception) {
                fs.collection("jeweller_accounts_by_mobile").document(cleanMob).get().await()
            }
        }

        when (directResult) {
            is FirestoreCallResult.Success -> {
                val directDoc = directResult.data
                if (directDoc.exists()) {
                    val acc = parseDocToAccount(directDoc)
                    if (acc != null && PhoneUtil.normalizeMobile(acc.mobileNumber) == cleanMob) {
                        discoveredCloudAccounts.add(acc)
                    }
                }
            }
            is FirestoreCallResult.Timeout -> timeoutOccurred = true
            is FirestoreCallResult.NetworkError -> networkErrorOccurred = true
            is FirestoreCallResult.FirebaseConfigError -> {
                configErrorOccurred = true
                lastError = directResult.message
            }
            is FirestoreCallResult.Error -> lastError = directResult.message
        }

        // Query jeweller_accounts where mobileNumber == cleanMob
        val queryResult = safeFirestoreCall(3500L) {
            try {
                fs.collection("jeweller_accounts")
                    .whereEqualTo("mobileNumber", cleanMob)
                    .limit(10)
                    .get(Source.SERVER)
                    .await()
            } catch (_: Exception) {
                fs.collection("jeweller_accounts")
                    .whereEqualTo("mobileNumber", cleanMob)
                    .limit(10)
                    .get()
                    .await()
            }
        }

        when (queryResult) {
            is FirestoreCallResult.Success -> {
                val querySnap = queryResult.data
                for (doc in querySnap.documents) {
                    val acc = parseDocToAccount(doc)
                    if (acc != null && PhoneUtil.normalizeMobile(acc.mobileNumber) == cleanMob) {
                        if (discoveredCloudAccounts.none { it.accountId == acc.accountId || (PhoneUtil.normalizeGst(it.gstNumber) == PhoneUtil.normalizeGst(acc.gstNumber) && PhoneUtil.normalizeGst(acc.gstNumber).isNotEmpty()) }) {
                            discoveredCloudAccounts.add(acc)
                        }
                    }
                }
            }
            is FirestoreCallResult.Timeout -> timeoutOccurred = true
            is FirestoreCallResult.NetworkError -> networkErrorOccurred = true
            is FirestoreCallResult.FirebaseConfigError -> {
                configErrorOccurred = true
                lastError = queryResult.message
            }
            is FirestoreCallResult.Error -> lastError = queryResult.message
        }

        // Combine discovered cloud accounts with local mirror accounts
        val combined = (discoveredCloudAccounts + localMatches).distinctBy {
            val gst = PhoneUtil.normalizeGst(it.gstNumber)
            if (gst.isNotEmpty()) gst else it.accountId
        }

        if (combined.size > 1) {
            Log.i(TAG, "LOGIN_LOOKUP: MULTIPLE_ACCOUNTS_FOUND (${combined.size})")
            return@withContext CloudMobileLookupResult.FoundMultiple
        }

        if (combined.size == 1) {
            val single = combined.first()
            saveAccountToPersistentMirror(single)
            Log.i(TAG, "LOGIN_LOOKUP: FIRESTORE_FOUND (${single.accountId})")
            return@withContext CloudMobileLookupResult.FoundSingle(single)
        }

        if (!isNetworkAvailable()) {
            Log.w(TAG, "LOGIN_LOOKUP: FIRESTORE_NETWORK_ERROR")
            return@withContext CloudMobileLookupResult.NetworkError
        }

        if (timeoutOccurred) {
            Log.w(TAG, "LOGIN_LOOKUP: FIRESTORE_TIMEOUT")
            return@withContext CloudMobileLookupResult.Timeout
        }

        if (configErrorOccurred) {
            Log.w(TAG, "LOGIN_LOOKUP: FIREBASE_INIT_ERROR")
            return@withContext CloudMobileLookupResult.FirebaseConfigError(lastError ?: "Firebase configuration error")
        }

        if (lastError != null) {
            Log.w(TAG, "LOGIN_LOOKUP: FIRESTORE_ERROR - $lastError")
            return@withContext CloudMobileLookupResult.Error(lastError)
        }

        Log.i(TAG, "LOGIN_LOOKUP: FIRESTORE_NOT_FOUND")
        return@withContext CloudMobileLookupResult.NotFound
    }

    suspend fun findAccountByMobileInCloud(mobileNumber: String): JewellerAccount? = withContext(Dispatchers.IO) {
        val result = findAccountsByMobileInCloud(mobileNumber)
        if (result is CloudMobileLookupResult.FoundSingle) {
            result.account
        } else {
            null
        }
    }

    /**
     * Legacy Cloud account lookup by mobile (replaces old Jeweller Name identity logic).
     */
    suspend fun findAccountInCloud(
        jewellerName: String,
        mobileNumber: String,
        gstNumber: String = ""
    ): JewellerAccount? = withContext(Dispatchers.IO) {
        val normMobile = PhoneUtil.normalizeMobile(mobileNumber)
        val normGst = PhoneUtil.normalizeGst(gstNumber)
        if (normGst.isNotEmpty()) {
            val res = findAccountByMobileAndGstInCloud(normMobile, normGst)
            if (res is CloudLookupResult.Found) return@withContext res.account
            return@withContext null
        }
        if (normMobile.length != 10) return@withContext null

        return@withContext findAccountByMobileInCloud(normMobile)
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
                    }.getOrNull()
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
                }.getOrNull()
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
                        if (accountId.isNotBlank()) {
                            fs.collection("jeweller_accounts")
                                .document(accountId)
                                .update("code4Digit", normCode)
                                .await()
                        }

                        if (normMobile.isNotEmpty() && normGst.isNotEmpty()) {
                            fs.collection("jeweller_accounts_by_identity")
                                .document("${normMobile}_${normGst}")
                                .update("code4Digit", normCode)
                                .await()
                        }

                        if (normMobile.isNotEmpty()) {
                            try {
                                val mobDoc = fs.collection("jeweller_accounts_by_mobile").document(normMobile).get().await()
                                val docAccId = mobDoc.getString("accountId")
                                val docGst = PhoneUtil.normalizeGst(mobDoc.getString("gstNumber"))
                                if (docAccId == accountId || (normGst.isNotEmpty() && docGst == normGst)) {
                                    fs.collection("jeweller_accounts_by_mobile")
                                        .document(normMobile)
                                        .update("code4Digit", normCode)
                                        .await()
                                }
                            } catch (_: Exception) {}
                        }

                        if (normGst.isNotEmpty()) {
                            try {
                                val gstDoc = fs.collection("jeweller_accounts_by_gst").document(normGst).get().await()
                                val docAccId = gstDoc.getString("accountId")
                                if (docAccId == accountId) {
                                    fs.collection("jeweller_accounts_by_gst")
                                        .document(normGst)
                                        .update("code4Digit", normCode)
                                        .await()
                                }
                            } catch (_: Exception) {}
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Firestore code update error: ${e.message}")
                }
            }

            // Update in Cloud Shared Store ONLY for this specific account
            val accountsJson = cloudStorePref.getString("registered_accounts_list", "[]") ?: "[]"
            val jsonArray = org.json.JSONArray(accountsJson)
            val updatedArray = org.json.JSONArray()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val matchesId = accountId.isNotBlank() && obj.optString("accountId") == accountId
                val matchesIdentity = normMobile.isNotEmpty() && normGst.isNotEmpty() &&
                    PhoneUtil.normalizeMobile(obj.optString("mobileNumber")) == normMobile &&
                    PhoneUtil.normalizeGst(obj.optString("gstNumber")) == normGst
                if (matchesId || matchesIdentity) {
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
                        if (accountId.isNotBlank()) {
                            fs.collection("jeweller_accounts")
                                .document(accountId)
                                .delete()
                                .await()
                        }

                        if (normMobile.isNotEmpty() && normGst.isNotEmpty()) {
                            fs.collection("jeweller_accounts_by_identity")
                                .document("${normMobile}_${normGst}")
                                .delete()
                                .await()
                        }

                        // For mobile index, check if another account with the same mobile still exists
                        if (normMobile.isNotEmpty()) {
                            try {
                                val otherAccounts = fs.collection("jeweller_accounts")
                                    .whereEqualTo("mobileNumber", normMobile)
                                    .get()
                                    .await()
                                    .documents
                                    .mapNotNull { parseDocToAccount(it) }
                                    .filter { it.accountId != accountId }

                                if (otherAccounts.isNotEmpty()) {
                                    val remaining = otherAccounts.first()
                                    val remMap = hashMapOf(
                                        "accountId" to remaining.accountId,
                                        "jewellerName" to remaining.jewellerName,
                                        "mobileNumber" to PhoneUtil.normalizeMobile(remaining.mobileNumber),
                                        "code4Digit" to PhoneUtil.normalizeCode(remaining.code4Digit),
                                        "gstNumber" to PhoneUtil.normalizeGst(remaining.gstNumber),
                                        "isLicensed" to remaining.isLicensed,
                                        "status" to remaining.status,
                                        "createdAt" to remaining.createdAt
                                    )
                                    fs.collection("jeweller_accounts_by_mobile")
                                        .document(normMobile)
                                        .set(remMap, SetOptions.merge())
                                        .await()
                                } else {
                                    fs.collection("jeweller_accounts_by_mobile")
                                        .document(normMobile)
                                        .delete()
                                        .await()
                                }
                            } catch (_: Exception) {}
                        }

                        if (normGst.isNotEmpty()) {
                            try {
                                val gstDoc = fs.collection("jeweller_accounts_by_gst").document(normGst).get().await()
                                val docAccId = gstDoc.getString("accountId")
                                if (docAccId == accountId || !gstDoc.exists()) {
                                    fs.collection("jeweller_accounts_by_gst")
                                        .document(normGst)
                                        .delete()
                                        .await()
                                }
                            } catch (_: Exception) {}
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Firestore deleteAccount error: ${e.message}")
                }
            }

            // 2. Filter out ONLY the target account from SharedPreferences and persistent mirror files
            val existing = getAllAccountsFromCloud().filter {
                val matchesId = accountId.isNotBlank() && it.accountId == accountId
                val matchesIdentity = normMobile.isNotEmpty() && normGst.isNotEmpty() &&
                    PhoneUtil.normalizeMobile(it.mobileNumber) == normMobile &&
                    PhoneUtil.normalizeGst(it.gstNumber) == normGst
                !(matchesId || matchesIdentity)
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
                }.getOrNull()

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
                        goldHsnCode = doc.getString("goldHsnCode") ?: "",
                        silverHsnCode = doc.getString("silverHsnCode") ?: "",
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
                        goldHsnCode = obj.optString("goldHsnCode", ""),
                        silverHsnCode = obj.optString("silverHsnCode", ""),
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
                }.getOrNull()

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
                        partyAddress = doc.getString("partyAddress") ?: "",
                        partyAadharNumber = doc.getString("partyAadharNumber") ?: "",
                        partyPanNumber = doc.getString("partyPanNumber") ?: "",
                        partyGstNumber = doc.getString("partyGstNumber") ?: "",
                        dateTimestamp = doc.getLong("dateTimestamp") ?: System.currentTimeMillis(),
                        itemsJson = doc.getString("itemsJson") ?: "[]",
                        subtotal = doc.getDouble("subtotal") ?: 0.0,
                        gstPercent = doc.getDouble("gstPercent") ?: 3.0,
                        gstAmount = doc.getDouble("gstAmount") ?: 0.0,
                        discount = doc.getDouble("discount") ?: 0.0,
                        grandTotal = doc.getDouble("grandTotal") ?: 0.0,
                        cashReceivedOrPaid = doc.getDouble("cashReceivedOrPaid") ?: 0.0,
                        oldMetalExchangeAmount = doc.getDouble("oldMetalExchangeAmount") ?: 0.0,
                        otherCharges = doc.getDouble("otherCharges") ?: 0.0,
                        otherChargesRemark = doc.getString("otherChargesRemark") ?: "",
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
                val fallbackBill = Bill(
                    id = id,
                    accountId = accountId,
                    billNumber = obj.optString("billNumber"),
                    billType = obj.optString("billType", "SALE"),
                    isGstBill = obj.optBoolean("isGstBill", false),
                    partyName = obj.optString("partyName"),
                    partyMobile = obj.optString("partyMobile"),
                    partyAddress = obj.optString("partyAddress"),
                    partyAadharNumber = obj.optString("partyAadharNumber"),
                    partyPanNumber = obj.optString("partyPanNumber"),
                    partyGstNumber = obj.optString("partyGstNumber"),
                    dateTimestamp = obj.optLong("dateTimestamp", System.currentTimeMillis()),
                    itemsJson = obj.optString("itemsJson", "[]"),
                    subtotal = obj.optDouble("subtotal", 0.0),
                    gstPercent = obj.optDouble("gstPercent", 3.0),
                    gstAmount = obj.optDouble("gstAmount", 0.0),
                    discount = obj.optDouble("discount", 0.0),
                    grandTotal = obj.optDouble("grandTotal", 0.0),
                    cashReceivedOrPaid = obj.optDouble("cashReceivedOrPaid", 0.0),
                    oldMetalExchangeAmount = obj.optDouble("oldMetalExchangeAmount", 0.0),
                    otherCharges = obj.optDouble("otherCharges", 0.0),
                    otherChargesRemark = obj.optString("otherChargesRemark", ""),
                    netBalanceDue = obj.optDouble("netBalanceDue", 0.0),
                    notes = obj.optString("notes"),
                    paymentsJson = obj.optString("paymentsJson", "[]"),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                )
                val existingIndex = restoredBills.indexOfFirst { it.id == id }
                if (existingIndex < 0) {
                    restoredBills.add(fallbackBill)
                } else {
                    // If an older Firestore copy has no Other Charges but the persistent
                    // fallback copy does, keep the charge details instead of losing them.
                    val firestoreBill = restoredBills[existingIndex]
                    if (firestoreBill.otherChargesRemark.isBlank() && fallbackBill.otherChargesRemark.isNotBlank()) {
                        restoredBills[existingIndex] = firestoreBill.copy(
                            otherCharges = if (firestoreBill.otherCharges > 0) firestoreBill.otherCharges else fallbackBill.otherCharges,
                            otherChargesRemark = fallbackBill.otherChargesRemark
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring fallback bills", e)
        }

        if (restoredBills.isNotEmpty()) {
            // Never let a cloud copy with an older/missing Other Charges field erase
            // a charge that is already safely stored on this device.
            val protectedBills = restoredBills.map { remoteBill ->
                val localBill = try { db.billDao().getBillById(remoteBill.id) } catch (_: Exception) { null }
                if (remoteBill.otherChargesRemark.isBlank() && localBill?.otherChargesRemark?.isNotBlank() == true) {
                    remoteBill.copy(
                        otherCharges = if (remoteBill.otherCharges > 0) remoteBill.otherCharges else localBill.otherCharges,
                        otherChargesRemark = localBill.otherChargesRemark
                    )
                } else {
                    remoteBill
                }
            }
            db.billDao().insertBills(protectedBills)
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
                }.getOrNull()

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
                        "partyAddress" to bill.partyAddress,
                        "partyAadharNumber" to bill.partyAadharNumber,
                        "partyPanNumber" to bill.partyPanNumber,
                        "partyGstNumber" to bill.partyGstNumber,
                        "dateTimestamp" to bill.dateTimestamp,
                        "itemsJson" to bill.itemsJson,
                        "subtotal" to bill.subtotal,
                        "gstPercent" to bill.gstPercent,
                        "gstAmount" to bill.gstAmount,
                        "discount" to bill.discount,
                        "grandTotal" to bill.grandTotal,
                        "cashReceivedOrPaid" to bill.cashReceivedOrPaid,
                        "oldMetalExchangeAmount" to bill.oldMetalExchangeAmount,
                        "otherCharges" to bill.otherCharges,
                        "otherChargesRemark" to bill.otherChargesRemark,
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
                    cloudSuccess = (res is FirestoreCallResult.Success)
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
                put("partyAddress", bill.partyAddress)
                put("partyAadharNumber", bill.partyAadharNumber)
                put("partyPanNumber", bill.partyPanNumber)
                put("partyGstNumber", bill.partyGstNumber)
                put("dateTimestamp", bill.dateTimestamp)
                put("itemsJson", bill.itemsJson)
                put("subtotal", bill.subtotal)
                put("gstPercent", bill.gstPercent)
                put("gstAmount", bill.gstAmount)
                put("discount", bill.discount)
                put("grandTotal", bill.grandTotal)
                put("cashReceivedOrPaid", bill.cashReceivedOrPaid)
                put("oldMetalExchangeAmount", bill.oldMetalExchangeAmount)
                put("otherCharges", bill.otherCharges)
                put("otherChargesRemark", bill.otherChargesRemark)
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
                    cloudSuccess = (res is FirestoreCallResult.Success)
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
                    cloudSuccess = (res is FirestoreCallResult.Success)
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
                    cloudSuccess = (res is FirestoreCallResult.Success)
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
                    cloudSuccess = (res is FirestoreCallResult.Success)
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
                        "goldHsnCode" to settings.goldHsnCode,
                        "silverHsnCode" to settings.silverHsnCode,
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
                    cloudSuccess = (res is FirestoreCallResult.Success)
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
                put("goldHsnCode", settings.goldHsnCode)
                put("silverHsnCode", settings.silverHsnCode)
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
                                        partyAddress = doc.getString("partyAddress") ?: "",
                                        partyAadharNumber = doc.getString("partyAadharNumber") ?: "",
                                        partyPanNumber = doc.getString("partyPanNumber") ?: "",
                                        partyGstNumber = doc.getString("partyGstNumber") ?: "",
                                        dateTimestamp = doc.getLong("dateTimestamp") ?: System.currentTimeMillis(),
                                        itemsJson = doc.getString("itemsJson") ?: "[]",
                                        subtotal = doc.getDouble("subtotal") ?: 0.0,
                                        gstPercent = doc.getDouble("gstPercent") ?: 3.0,
                                        gstAmount = doc.getDouble("gstAmount") ?: 0.0,
                                        discount = doc.getDouble("discount") ?: 0.0,
                                        grandTotal = doc.getDouble("grandTotal") ?: 0.0,
                                        cashReceivedOrPaid = doc.getDouble("cashReceivedOrPaid") ?: 0.0,
                                        oldMetalExchangeAmount = doc.getDouble("oldMetalExchangeAmount") ?: 0.0,
                                        otherCharges = doc.getDouble("otherCharges") ?: 0.0,
                                        otherChargesRemark = doc.getString("otherChargesRemark") ?: "",
                                        netBalanceDue = doc.getDouble("netBalanceDue") ?: 0.0,
                                        notes = doc.getString("notes") ?: "",
                                        paymentsJson = doc.getString("paymentsJson") ?: "[]",
                                        createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                                        updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                                    )
                                    val existingLocal = db.billDao().getBillById(billId)
                                    val billToSave = if (bill.otherChargesRemark.isBlank() && existingLocal?.otherChargesRemark?.isNotBlank() == true) {
                                        bill.copy(
                                            otherCharges = if (bill.otherCharges > 0) bill.otherCharges else existingLocal.otherCharges,
                                            otherChargesRemark = existingLocal.otherChargesRemark
                                        )
                                    } else bill
                                    db.billDao().insertBill(billToSave)
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
                            goldHsnCode = snapshot.getString("goldHsnCode") ?: "",
                            silverHsnCode = snapshot.getString("silverHsnCode") ?: "",
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

            // Remove automatic stock entries whose source bill has been deleted.
            db.stockTransactionDao().deleteAutomaticTransactionsWithoutBills(accountId)

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

        // Unit/Robolectric tests must not start an infinite background coroutine.
        // The test JVM waits for the Dispatchers.IO worker to terminate, so the
        // periodic 20-second sync loop can make testDebugUnitTest appear hung.
        // Firebase/network calls are already disabled by the same CI flag.
        if (System.getProperty("billing.skipFirebaseNetworkTests") == "true") {
            Log.i(TAG, "Periodic auto-sync disabled for unit tests")
            return
        }

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
        if (System.getProperty("billing.skipFirebaseNetworkTests") == "true") return
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
    suspend fun processPendingQueue(): Boolean = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable()) {
            if (pendingQueue.isNotEmpty()) {
                _syncStatus.value = SyncStatus.OFFLINE
            }
            return@withContext false
        }

        queueMutex.withLock {
            if (pendingQueue.isEmpty()) {
                _syncStatus.value = SyncStatus.SYNCED
                return@withLock true
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
            pendingQueue.isEmpty()
        }
    }

    fun enqueueBill(bill: Bill, action: String = "UPSERT") {
        if (isCiTestMode) return
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
        if (isCiTestMode) return
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
        if (isCiTestMode) return
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
        if (isCiTestMode) return
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
        if (isCiTestMode) return
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
        if (isCiTestMode) return
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