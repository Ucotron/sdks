package com.ucotron.sdk.android

import com.ucotron.sdk.UcotronException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant

/**
 * Configuration for periodic memory sync.
 *
 * @param syncInterval How often to run the sync (minimum 15 minutes on Android WorkManager)
 * @param batchSize Maximum number of pending items to sync per run
 * @param maxRetries Number of retries per sync attempt before giving up
 * @param requireNetwork If true, sync only runs when network is available
 * @param requireUnmetered If true, sync only runs on Wi-Fi (not metered connections)
 * @param requireCharging If true, sync only runs when device is charging
 */
data class SyncConfig(
    val syncInterval: Duration = Duration.ofMinutes(30),
    val batchSize: Int = 50,
    val maxRetries: Int = 3,
    val requireNetwork: Boolean = true,
    val requireUnmetered: Boolean = false,
    val requireCharging: Boolean = false,
)

/**
 * Result of a sync operation.
 */
data class SyncResult(
    val status: SyncStatus,
    val itemsSynced: Int,
    val errors: List<String>,
    val timestamp: Long = Instant.now().epochSecond,
    val durationMs: Long = 0
) {
    val isSuccess: Boolean get() = status == SyncStatus.SUCCESS
}

enum class SyncStatus {
    SUCCESS,
    PARTIAL,
    FAILURE,
    RETRY,
    SKIPPED
}

/**
 * Represents a pending memory item queued for sync.
 *
 * Applications should persist these locally (e.g., Room, SharedPreferences)
 * and pass them to [UcotronSyncWorker.doSync].
 */
data class PendingMemory(
    val text: String,
    val namespace: String? = null,
    val metadata: Map<String, Any>? = null,
    val createdAt: Long = Instant.now().epochSecond
)

/**
 * Callback interface for sync lifecycle events.
 */
interface SyncCallback {
    /** Called before sync starts. Return false to cancel this sync run. */
    fun onSyncStart(): Boolean = true

    /** Called after each successful item sync with the chunk node IDs created. */
    fun onItemSynced(item: PendingMemory, chunkNodeIds: List<Long>) {}

    /** Called when an item fails to sync. */
    fun onItemFailed(item: PendingMemory, error: String) {}

    /** Called when sync completes. */
    fun onSyncComplete(result: SyncResult) {}
}

/**
 * Background memory sync worker for Android.
 *
 * This class provides the core sync logic that can be wired to Android's
 * WorkManager [CoroutineWorker] for periodic background sync.
 *
 * **Pure JVM implementation** — does not depend on AndroidX directly.
 * See the WorkManager integration example below.
 *
 * ## Sync Logic
 * 1. Collects pending memory items from the application's local queue
 * 2. Attempts to sync each item to the Ucotron server via [UcotronAndroid.addMemory]
 * 3. Tracks success/failure per item
 * 4. Returns [SyncResult] with counts and errors
 *
 * ## WorkManager Integration
 *
 * To use with Android WorkManager, create a CoroutineWorker that delegates to this class:
 *
 * ```kotlin
 * class UcotronWorkManagerWorker(
 *     context: Context,
 *     params: WorkerParameters
 * ) : CoroutineWorker(context, params) {
 *
 *     override suspend fun doWork(): Result {
 *         val ucotron = UcotronAndroid("http://your-server:8420")
 *         val worker = UcotronSyncWorker(ucotron, SyncConfig())
 *
 *         // Load pending items from local storage (Room, SharedPreferences, etc.)
 *         val pending = loadPendingMemories()
 *
 *         val result = worker.doSync(pending)
 *         return when (result.status) {
 *             SyncStatus.SUCCESS -> Result.success()
 *             SyncStatus.PARTIAL -> Result.success()
 *             SyncStatus.RETRY -> Result.retry()
 *             SyncStatus.FAILURE -> Result.failure()
 *             SyncStatus.SKIPPED -> Result.success()
 *         }
 *     }
 * }
 * ```
 *
 * ## Scheduling Periodic Sync
 *
 * ```kotlin
 * val constraints = Constraints.Builder()
 *     .setRequiredNetworkType(NetworkType.CONNECTED)
 *     .setRequiresBatteryNotLow(true)
 *     .build()
 *
 * val syncRequest = PeriodicWorkRequestBuilder<UcotronWorkManagerWorker>(
 *     30, TimeUnit.MINUTES   // Minimum 15 min on WorkManager
 * )
 *     .setConstraints(constraints)
 *     .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
 *     .addTag("ucotron-sync")
 *     .build()
 *
 * WorkManager.getInstance(context)
 *     .enqueueUniquePeriodicWork(
 *         "ucotron-periodic-sync",
 *         ExistingPeriodicWorkPolicy.KEEP,
 *         syncRequest
 *     )
 * ```
 *
 * ## Network Constraint Handling
 *
 * When [SyncConfig.requireNetwork] is true, the sync checks connectivity before
 * attempting each batch. On Android, use WorkManager's [Constraints] to enforce
 * network requirements at the OS level:
 *
 * ```kotlin
 * val constraints = Constraints.Builder()
 *     .setRequiredNetworkType(
 *         if (config.requireUnmetered) NetworkType.UNMETERED
 *         else NetworkType.CONNECTED
 *     )
 *     .build()
 * ```
 *
 * @param client The [UcotronAndroid] client instance for server communication
 * @param config Sync configuration parameters
 * @param callback Optional lifecycle callbacks for sync events
 * @param dispatcher Coroutine dispatcher for sync operations (defaults to IO)
 */
class UcotronSyncWorker(
    private val client: UcotronAndroid,
    private val config: SyncConfig = SyncConfig(),
    private val callback: SyncCallback? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    /**
     * Execute a sync operation for the given pending memory items.
     *
     * Processes items in order, up to [SyncConfig.batchSize] items per run.
     * Each item is sent to the server via [UcotronAndroid.addMemory].
     * Failed items are collected in the result for the caller to re-queue.
     *
     * @param pendingItems Items awaiting sync to the server
     * @return [SyncResult] indicating overall outcome
     */
    suspend fun doSync(pendingItems: List<PendingMemory>): SyncResult = withContext(dispatcher) {
        val startTime = System.currentTimeMillis()

        // Check if callback allows this sync run
        if (callback?.onSyncStart() == false) {
            return@withContext SyncResult(
                status = SyncStatus.SKIPPED,
                itemsSynced = 0,
                errors = emptyList(),
                durationMs = System.currentTimeMillis() - startTime
            )
        }

        if (pendingItems.isEmpty()) {
            val result = SyncResult(
                status = SyncStatus.SUCCESS,
                itemsSynced = 0,
                errors = emptyList(),
                durationMs = System.currentTimeMillis() - startTime
            )
            callback?.onSyncComplete(result)
            return@withContext result
        }

        val batch = pendingItems.take(config.batchSize)
        var synced = 0
        val errors = mutableListOf<String>()

        for (item in batch) {
            try {
                val result = syncItemWithRetry(item)
                synced++
                callback?.onItemSynced(item, result)
            } catch (e: CancellationException) {
                throw e // Don't catch coroutine cancellation
            } catch (e: Exception) {
                val errorMsg = "Failed to sync item '${item.text.take(50)}': ${e.message}"
                errors.add(errorMsg)
                callback?.onItemFailed(item, e.message ?: "Unknown error")
            }
        }

        val status = when {
            synced == batch.size -> SyncStatus.SUCCESS
            synced > 0 -> SyncStatus.PARTIAL
            errors.all { it.contains("connection", ignoreCase = true) ||
                    it.contains("timeout", ignoreCase = true) } -> SyncStatus.RETRY
            else -> SyncStatus.FAILURE
        }

        val result = SyncResult(
            status = status,
            itemsSynced = synced,
            errors = errors,
            durationMs = System.currentTimeMillis() - startTime
        )
        callback?.onSyncComplete(result)
        result
    }

    /**
     * Sync a single item with retry logic.
     *
     * @return The chunk node IDs created by the server
     * @throws UcotronException if all retries are exhausted
     */
    private suspend fun syncItemWithRetry(item: PendingMemory): List<Long> {
        var lastException: Exception? = null

        for (attempt in 0 until config.maxRetries) {
            try {
                val result = client.addMemory(item.text, item.namespace, item.metadata)
                return result.chunkNodeIds ?: emptyList()
            } catch (e: CancellationException) {
                throw e
            } catch (e: UcotronException) {
                lastException = e
                // Don't retry on client errors (4xx)
                if (e.message?.contains("4") == true && e is com.ucotron.sdk.UcotronServerException) {
                    val statusCode = try {
                        // UcotronServerException contains status code in message
                        e.message?.substringBefore(":")?.trim()?.toIntOrNull() ?: 0
                    } catch (_: Exception) { 0 }
                    if (statusCode in 400..499) throw e
                }
                if (attempt < config.maxRetries - 1) {
                    val delay = (1L shl attempt) * 200 // Exponential backoff: 200ms, 400ms, 800ms...
                    kotlinx.coroutines.delay(delay)
                }
            } catch (e: Exception) {
                lastException = e
                if (attempt < config.maxRetries - 1) {
                    val delay = (1L shl attempt) * 200
                    kotlinx.coroutines.delay(delay)
                }
            }
        }

        throw lastException ?: UcotronException("Sync failed after ${config.maxRetries} retries")
    }

    /**
     * Convenience: build WorkManager-compatible constraints from [SyncConfig].
     *
     * Returns a map of constraint keys suitable for passing to WorkManager:
     * ```
     * mapOf(
     *     "networkType" to "CONNECTED" | "UNMETERED" | "NOT_REQUIRED",
     *     "requiresCharging" to "true" | "false"
     * )
     * ```
     */
    fun constraintsMap(): Map<String, String> = mapOf(
        "networkType" to when {
            config.requireUnmetered -> "UNMETERED"
            config.requireNetwork -> "CONNECTED"
            else -> "NOT_REQUIRED"
        },
        "requiresCharging" to config.requireCharging.toString()
    )
}
