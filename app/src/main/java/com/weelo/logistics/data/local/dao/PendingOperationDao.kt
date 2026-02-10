package com.weelo.logistics.data.local.dao

import androidx.room.*
import com.weelo.logistics.data.local.entity.OperationStatus
import com.weelo.logistics.data.local.entity.OperationType
import com.weelo.logistics.data.local.entity.PendingOperationEntity
import kotlinx.coroutines.flow.Flow

/**
 * =============================================================================
 * PENDING OPERATION DAO - Offline Queue Operations
 * =============================================================================
 * 
 * Manages the queue of operations waiting to be synced.
 * 
 * =============================================================================
 */
@Dao
interface PendingOperationDao {
    
    /**
     * Insert a new pending operation
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(operation: PendingOperationEntity)
    
    /**
     * Insert multiple operations
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(operations: List<PendingOperationEntity>)
    
    /**
     * Update an operation
     */
    @Update
    suspend fun update(operation: PendingOperationEntity)
    
    /**
     * Delete an operation
     */
    @Delete
    suspend fun delete(operation: PendingOperationEntity)
    
    /**
     * Delete operation by ID
     */
    @Query("DELETE FROM pending_operations WHERE id = :operationId")
    suspend fun deleteById(operationId: String)
    
    /**
     * Get all pending operations ordered by priority and creation time
     */
    @Query("""
        SELECT * FROM pending_operations 
        WHERE status = 'PENDING' 
        ORDER BY priority ASC, createdAt ASC
    """)
    fun getPendingOperations(): Flow<List<PendingOperationEntity>>
    
    /**
     * Get all operations (for debugging/admin)
     */
    @Query("SELECT * FROM pending_operations ORDER BY createdAt DESC")
    fun getAllOperations(): Flow<List<PendingOperationEntity>>
    
    /**
     * Get operation by ID
     */
    @Query("SELECT * FROM pending_operations WHERE id = :operationId")
    suspend fun getById(operationId: String): PendingOperationEntity?
    
    /**
     * Get operations by type
     */
    @Query("""
        SELECT * FROM pending_operations 
        WHERE operationType = :type AND status = 'PENDING'
        ORDER BY priority ASC, createdAt ASC
    """)
    suspend fun getByType(type: OperationType): List<PendingOperationEntity>
    
    /**
     * Get operations by related entity
     */
    @Query("""
        SELECT * FROM pending_operations 
        WHERE relatedEntityId = :entityId
        ORDER BY createdAt DESC
    """)
    suspend fun getByRelatedEntity(entityId: String): List<PendingOperationEntity>
    
    /**
     * Get count of pending operations
     */
    @Query("SELECT COUNT(*) FROM pending_operations WHERE status = 'PENDING'")
    fun getPendingCount(): Flow<Int>
    
    /**
     * Get count of failed operations
     */
    @Query("SELECT COUNT(*) FROM pending_operations WHERE status = 'FAILED'")
    fun getFailedCount(): Flow<Int>
    
    /**
     * Update operation status
     */
    @Query("""
        UPDATE pending_operations 
        SET status = :status, lastAttemptAt = :attemptTime, errorMessage = :error
        WHERE id = :operationId
    """)
    suspend fun updateStatus(
        operationId: String, 
        status: OperationStatus, 
        attemptTime: Long = System.currentTimeMillis(),
        error: String? = null
    )
    
    /**
     * Increment retry count
     */
    @Query("""
        UPDATE pending_operations 
        SET retryCount = retryCount + 1, lastAttemptAt = :attemptTime
        WHERE id = :operationId
    """)
    suspend fun incrementRetryCount(operationId: String, attemptTime: Long = System.currentTimeMillis())
    
    /**
     * Mark operations as failed if max retries exceeded
     */
    @Query("""
        UPDATE pending_operations 
        SET status = 'FAILED', errorMessage = 'Max retries exceeded'
        WHERE status = 'PENDING' AND retryCount >= maxRetries
    """)
    suspend fun markFailedOperations()
    
    /**
     * Clear completed operations older than specified time
     */
    @Query("""
        DELETE FROM pending_operations 
        WHERE status = 'COMPLETED' AND lastAttemptAt < :olderThan
    """)
    suspend fun clearOldCompletedOperations(olderThan: Long)
    
    /**
     * Clear all completed operations
     */
    @Query("DELETE FROM pending_operations WHERE status = 'COMPLETED'")
    suspend fun clearCompletedOperations()
    
    /**
     * Clear all operations (for logout)
     */
    @Query("DELETE FROM pending_operations")
    suspend fun clearAll()
    
    /**
     * Get next operation to process
     */
    @Query("""
        SELECT * FROM pending_operations 
        WHERE status = 'PENDING' AND retryCount < maxRetries
        ORDER BY priority ASC, createdAt ASC
        LIMIT 1
    """)
    suspend fun getNextOperation(): PendingOperationEntity?
}
