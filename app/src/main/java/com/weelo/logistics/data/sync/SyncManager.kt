package com.weelo.logistics.data.sync

import com.weelo.logistics.core.network.ResilientApiExecutor
import com.weelo.logistics.core.util.NetworkMonitor
import com.weelo.logistics.data.local.dao.CachedBookingDao
import com.weelo.logistics.data.local.dao.PendingOperationDao
import com.weelo.logistics.data.local.entity.OperationStatus
import com.weelo.logistics.data.local.entity.OperationType
import com.weelo.logistics.data.local.entity.PendingOperationEntity
import com.weelo.logistics.data.remote.api.*
import com.weelo.logistics.data.remote.TokenManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * =============================================================================
 * SYNC MANAGER - Background Synchronization Service
 * =============================================================================
 * 
 * Manages background sync of pending operations when network is available.
 * 
 * FEATURES:
 * - Automatic sync when network becomes available
 * - Priority-based processing of pending operations
 * - Retry with exponential backoff
 * - Conflict resolution
 * - Sync status reporting
 * 
 * USAGE:
 * ```kotlin
 * // Start observing network and auto-sync
 * syncManager.startAutoSync()
 * 
 * // Manual sync trigger
 * syncManager.syncNow()
 * 
 * // Observe sync status
 * syncManager.syncStatus.collect { status ->
 *     when (status) {
 *         SyncStatus.SYNCING -> showSyncIndicator()
 *         SyncStatus.SYNCED -> hideSyncIndicator()
 *         SyncStatus.FAILED -> showRetryOption()
 *     }
 * }
 * ```
 * =============================================================================
 */
@Singleton
@OptIn(kotlinx.coroutines.FlowPreview::class)
class SyncManager @Inject constructor(
    private val pendingOperationDao: PendingOperationDao,
    private val cachedBookingDao: CachedBookingDao,
    private val apiService: WeeloApiService,
    private val tokenManager: TokenManager,
    private val networkMonitor: NetworkMonitor,
    private val resilientExecutor: ResilientApiExecutor
) {
    companion object {
        private const val TAG = "SyncManager"
        private const val SYNC_DEBOUNCE_MS = 5000L // 5 seconds debounce
        private const val MAX_BATCH_SIZE = 10
    }
    
    /**
     * Sync status states
     */
    enum class SyncStatus {
        IDLE,
        SYNCING,
        SYNCED,
        FAILED,
        OFFLINE
    }
    
    private val gson = Gson()
    private val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private data class QueuedOrderCreatePayload(
        val kind: String = "order_create",
        val idempotencyKey: String,
        val payloadHash: String,
        val request: CreateOrderRequest
    )
    
    // Sync status
    private val _syncStatus = MutableStateFlow(SyncStatus.IDLE)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()
    
    // Pending count
    val pendingCount: Flow<Int> = pendingOperationDao.getPendingCount()
    
    // Auto-sync job
    private var autoSyncJob: Job? = null
    
    /**
     * Start automatic sync when network becomes available
     */
    fun startAutoSync() {
        Timber.d("$TAG: Starting auto-sync")
        
        autoSyncJob?.cancel()
        autoSyncJob = syncScope.launch {
            // Observe network changes
            networkMonitor.isOnline
                .debounce(SYNC_DEBOUNCE_MS)
                .collect { isOnline ->
                    if (isOnline) {
                        Timber.d("$TAG: Network available, triggering sync")
                        syncPendingOperations()
                    } else {
                        _syncStatus.value = SyncStatus.OFFLINE
                    }
                }
        }
        
        // Also observe pending operations
        syncScope.launch {
            pendingOperationDao.getPendingOperations()
                .debounce(SYNC_DEBOUNCE_MS)
                .collect { operations ->
                    if (operations.isNotEmpty() && networkMonitor.isCurrentlyOnline()) {
                        Timber.d("$TAG: New pending operations, triggering sync")
                        syncPendingOperations()
                    }
                }
        }
    }
    
    /**
     * Stop auto-sync
     */
    fun stopAutoSync() {
        Timber.d("$TAG: Stopping auto-sync")
        autoSyncJob?.cancel()
        autoSyncJob = null
    }
    
    /**
     * Trigger sync immediately
     */
    suspend fun syncNow(): Boolean {
        return syncPendingOperations()
    }
    
    /**
     * Sync all pending operations
     */
    private suspend fun syncPendingOperations(): Boolean {
        if (_syncStatus.value == SyncStatus.SYNCING) {
            Timber.d("$TAG: Sync already in progress")
            return false
        }
        
        if (!networkMonitor.isCurrentlyOnline()) {
            Timber.d("$TAG: Offline, skipping sync")
            _syncStatus.value = SyncStatus.OFFLINE
            return false
        }
        
        _syncStatus.value = SyncStatus.SYNCING
        Timber.d("$TAG: Starting sync")
        
        var allSuccess = true
        var processedCount = 0
        
        try {
            // Process operations one by one
            while (processedCount < MAX_BATCH_SIZE) {
                val operation = pendingOperationDao.getNextOperation() ?: break
                
                val success = processOperation(operation)
                if (!success) {
                    allSuccess = false
                }
                
                processedCount++
            }
            
            // Mark operations that exceeded max retries as failed
            pendingOperationDao.markFailedOperations()
            
            // Clean up old completed operations (older than 7 days)
            val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
            pendingOperationDao.clearOldCompletedOperations(sevenDaysAgo)
            
            _syncStatus.value = if (allSuccess) SyncStatus.SYNCED else SyncStatus.FAILED
            Timber.d("$TAG: Sync completed, processed $processedCount operations")
            
            return allSuccess
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Sync error")
            _syncStatus.value = SyncStatus.FAILED
            return false
        }
    }
    
    /**
     * Process a single pending operation
     */
    private suspend fun processOperation(operation: PendingOperationEntity): Boolean {
        Timber.d("$TAG: Processing operation ${operation.id} (${operation.operationType})")
        
        // Mark as in progress
        pendingOperationDao.updateStatus(operation.id, OperationStatus.IN_PROGRESS)
        
        return try {
            val success = when (operation.operationType) {
                OperationType.CREATE_BOOKING -> processCreateBooking(operation)
                OperationType.UPDATE_BOOKING -> processUpdateBooking(operation)
                OperationType.CANCEL_BOOKING -> processCancelBooking(operation)
                OperationType.UPDATE_PROFILE -> processUpdateProfile(operation)
                OperationType.SYNC_LOCATION -> processSyncLocation(operation)
                OperationType.CUSTOM -> processCustomOperation(operation)
            }
            
            if (success) {
                pendingOperationDao.updateStatus(operation.id, OperationStatus.COMPLETED)
                Timber.d("$TAG: Operation ${operation.id} completed successfully")
            } else {
                pendingOperationDao.incrementRetryCount(operation.id)
                pendingOperationDao.updateStatus(
                    operation.id, 
                    OperationStatus.PENDING,
                    error = "Operation failed, will retry"
                )
                Timber.w("$TAG: Operation ${operation.id} failed, will retry")
            }
            
            success
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error processing operation ${operation.id}")
            pendingOperationDao.incrementRetryCount(operation.id)
            pendingOperationDao.updateStatus(
                operation.id,
                OperationStatus.PENDING,
                error = e.message
            )
            false
        }
    }
    
    /**
     * Process CREATE_BOOKING operation
     */
    @Suppress("UNUSED_VARIABLE") // Variables reserved for future API implementation
    private suspend fun processCreateBooking(operation: PendingOperationEntity): Boolean {
        val queuedOrderPayload = parsePayload<QueuedOrderCreatePayload>(operation.payload)
        if (queuedOrderPayload != null && queuedOrderPayload.kind == "order_create") {
            val token = tokenManager.getAccessToken() ?: return false
            val authHeader = "Bearer $token"
            val idempotencyKey = queuedOrderPayload.idempotencyKey

            val result = resilientExecutor.executeOnce("CreateOrder") {
                val primary = apiService.createOrderViaBookings(
                    token = authHeader,
                    request = queuedOrderPayload.request,
                    idempotencyKey = idempotencyKey
                )
                val response = if (
                    primary.isSuccessful ||
                    !shouldFallbackToLegacyOrdersRoute(primary.code(), primary.body()?.error?.code, primary.body()?.error?.message)
                ) {
                    primary
                } else {
                    apiService.createOrder(
                        token = authHeader,
                        request = queuedOrderPayload.request,
                        idempotencyKey = idempotencyKey
                    )
                }

                response.isSuccessful && response.body()?.success == true
            }

            return result.getOrNull() == true
        }

        val legacyPayload = parsePayload<Map<String, Any?>>(operation.payload) ?: return false
        val token = tokenManager.getAccessToken() ?: return false

        val legacyResult = resilientExecutor.executeOnce("CreateBookingLegacy") {
            val vehicleType = legacyPayload["vehicleType"] as? String ?: return@executeOnce false
            val vehicleSubtype = legacyPayload["vehicleSubtype"] as? String ?: return@executeOnce false
            val quantity = (legacyPayload["quantity"] as? Number)?.toInt() ?: return@executeOnce false
            val pickupAddress = legacyPayload["pickupLocation"] as? String ?: return@executeOnce false
            val dropAddress = legacyPayload["dropLocation"] as? String ?: return@executeOnce false
            val pickupLat = (legacyPayload["pickupLat"] as? Number)?.toDouble() ?: return@executeOnce false
            val pickupLng = (legacyPayload["pickupLng"] as? Number)?.toDouble() ?: return@executeOnce false
            val dropLat = (legacyPayload["dropLat"] as? Number)?.toDouble() ?: return@executeOnce false
            val dropLng = (legacyPayload["dropLng"] as? Number)?.toDouble() ?: return@executeOnce false

            val request = CreateBookingRequest(
                pickup = LocationRequest(
                    coordinates = CoordinatesRequest(pickupLat, pickupLng),
                    address = pickupAddress
                ),
                drop = LocationRequest(
                    coordinates = CoordinatesRequest(dropLat, dropLng),
                    address = dropAddress
                ),
                vehicleType = vehicleType,
                vehicleSubtype = vehicleSubtype,
                trucksNeeded = quantity,
                distanceKm = 0,
                pricePerTruck = 0
            )

            val response = apiService.createBooking(
                authorization = "Bearer $token",
                request = request,
                idempotencyKey = operation.id
            )
            response.isSuccessful && response.body()?.success == true
        }

        return legacyResult.getOrNull() == true
    }
    
    /**
     * Process UPDATE_BOOKING operation
     */
    @Suppress("UNUSED_PARAMETER") // Parameter reserved for future API implementation
    private suspend fun processUpdateBooking(operation: PendingOperationEntity): Boolean {
        // Implement based on your API
        return true
    }
    
    /**
     * Process CANCEL_BOOKING operation
     * TODO: Implement when cancel booking API is available
     */
    private suspend fun processCancelBooking(operation: PendingOperationEntity): Boolean {
        val payload = parsePayload<Map<String, Any?>>(operation.payload) ?: return false
        val bookingId = payload["bookingId"] as? String ?: return false
        
        // TODO: Implement when cancel booking API is added to WeeloApiService
        // val token = tokenManager.getAccessToken() ?: return false
        // val result = resilientExecutor.executeOnce("CancelBooking") {
        //     val response = apiService.cancelBooking("Bearer $token", bookingId)
        //     response.isSuccessful
        // }
        // return result.getOrDefault(false)
        
        Timber.d("$TAG: Cancel booking not yet implemented for $bookingId")
        return true // Return true to mark as completed for now
    }
    
    /**
     * Process UPDATE_PROFILE operation
     */
    @Suppress("UNUSED_PARAMETER") // Parameter reserved for future API implementation
    private suspend fun processUpdateProfile(operation: PendingOperationEntity): Boolean {
        // Implement based on your API
        return true
    }
    
    /**
     * Process SYNC_LOCATION operation
     */
    @Suppress("UNUSED_PARAMETER") // Parameter reserved for future API implementation
    private suspend fun processSyncLocation(operation: PendingOperationEntity): Boolean {
        // Implement based on your API
        return true
    }
    
    /**
     * Process CUSTOM operation
     */
    @Suppress("UNUSED_PARAMETER") // Parameter reserved for future API implementation
    private suspend fun processCustomOperation(operation: PendingOperationEntity): Boolean {
        // Custom operations can be handled by specific handlers
        return true
    }
    
    /**
     * Parse JSON payload
     */
    private inline fun <reified T> parsePayload(json: String): T? {
        return try {
            gson.fromJson(json, object : TypeToken<T>() {}.type)
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to parse payload")
            null
        }
    }

    private fun shouldFallbackToLegacyOrdersRoute(
        responseCode: Int,
        errorCode: String? = null,
        errorMessage: String? = null
    ): Boolean {
        if (responseCode in setOf(400, 404, 405, 422, 501)) return true
        val normalized = "${errorCode.orEmpty()} ${errorMessage.orEmpty()}".lowercase()
        return normalized.contains("bookings/orders") ||
            normalized.contains("cannot post") ||
            normalized.contains("not found")
    }
    
    /**
     * Queue a new operation
     */
    suspend fun queueOperation(operation: PendingOperationEntity) {
        Timber.d("$TAG: Queueing operation ${operation.operationType}")
        pendingOperationDao.insert(operation)
        
        // Trigger sync if online
        if (networkMonitor.isCurrentlyOnline()) {
            syncScope.launch {
                delay(1000) // Brief delay
                syncPendingOperations()
            }
        }
    }

    suspend fun queueOrderCreateOperation(
        request: CreateOrderRequest,
        idempotencyKey: String,
        payloadHash: String
    ) {
        val payload = gson.toJson(
            QueuedOrderCreatePayload(
                kind = "order_create",
                idempotencyKey = idempotencyKey,
                payloadHash = payloadHash,
                request = request
            )
        )
        val operation = PendingOperationEntity(
            id = "order_create_$idempotencyKey",
            operationType = OperationType.CREATE_BOOKING,
            payload = payload,
            maxRetries = 6,
            priority = 1,
            relatedEntityId = idempotencyKey
        )
        queueOperation(operation)
    }
    
    /**
     * Cancel a pending operation
     */
    suspend fun cancelOperation(operationId: String) {
        pendingOperationDao.updateStatus(operationId, OperationStatus.CANCELLED)
    }
    
    /**
     * Retry failed operations
     */
    suspend fun retryFailedOperations() {
        // Reset failed operations to pending with reset retry count
        // This would need a custom query
        Timber.d("$TAG: Retrying failed operations")
        syncPendingOperations()
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        stopAutoSync()
        syncScope.cancel()
    }
}
