package com.example.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Persistent background worker ensuring pending offline items (bills, stock, settings, accounts)
 * are reliably synced to Firestore even if the application process was killed.
 * Uses the existing PendingSyncItem queue as the single source of truth.
 */
class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "SyncWorker"
        const val WORK_NAME_ONE_TIME = "offline_pending_sync_work"
        const val WORK_NAME_PERIODIC = "periodic_cloud_safety_sync"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val syncManager = CloudSyncManager.getInstance(applicationContext)

            // 1. Check network availability
            if (!syncManager.isNetworkAvailable()) {
                Log.d(TAG, "Network unavailable, requesting WorkManager retry")
                return@withContext Result.retry()
            }

            // 2. Process existing persisted pending queue
            // Process BILL, STOCK, SETTINGS, ACCOUNT through CloudSyncManager
            // Queue items are removed ONLY after successful cloud confirmation
            val allSynced = syncManager.processPendingQueue()

            if (allSynced) {
                Log.d(TAG, "All pending items successfully synchronized to cloud")
                Result.success()
            } else {
                Log.w(TAG, "Some pending items failed or remain in queue, retrying with backoff")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "SyncWorker error: ${e.message}", e)
            Result.retry()
        }
    }
}
