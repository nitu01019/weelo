package com.weelo.logistics.data.local.dao

import androidx.room.*
import com.weelo.logistics.data.local.entity.CachedBookingEntity
import kotlinx.coroutines.flow.Flow

/**
 * =============================================================================
 * CACHED BOOKING DAO - Local Booking Cache Operations
 * =============================================================================
 * 
 * Manages locally cached bookings for offline access.
 * 
 * =============================================================================
 */
@Dao
interface CachedBookingDao {
    
    /**
     * Insert or update a booking
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(booking: CachedBookingEntity)
    
    /**
     * Insert or update multiple bookings
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(bookings: List<CachedBookingEntity>)
    
    /**
     * Get all bookings ordered by creation time
     */
    @Query("SELECT * FROM cached_bookings ORDER BY createdAt DESC")
    fun getAllBookings(): Flow<List<CachedBookingEntity>>
    
    /**
     * Get active bookings (not completed/cancelled)
     */
    @Query("""
        SELECT * FROM cached_bookings 
        WHERE status NOT IN ('COMPLETED', 'CANCELLED')
        ORDER BY createdAt DESC
    """)
    fun getActiveBookings(): Flow<List<CachedBookingEntity>>
    
    /**
     * Get booking by ID
     */
    @Query("SELECT * FROM cached_bookings WHERE id = :bookingId")
    suspend fun getById(bookingId: String): CachedBookingEntity?
    
    /**
     * Get booking by ID as Flow
     */
    @Query("SELECT * FROM cached_bookings WHERE id = :bookingId")
    fun getByIdFlow(bookingId: String): Flow<CachedBookingEntity?>
    
    /**
     * Get bookings by status
     */
    @Query("SELECT * FROM cached_bookings WHERE status = :status ORDER BY createdAt DESC")
    fun getByStatus(status: String): Flow<List<CachedBookingEntity>>
    
    /**
     * Get bookings that need sync
     */
    @Query("SELECT * FROM cached_bookings WHERE needsSync = 1 ORDER BY lastSyncAt ASC")
    suspend fun getBookingsNeedingSync(): List<CachedBookingEntity>
    
    /**
     * Get local-only bookings (created offline)
     */
    @Query("SELECT * FROM cached_bookings WHERE isLocalOnly = 1 ORDER BY createdAt DESC")
    suspend fun getLocalOnlyBookings(): List<CachedBookingEntity>
    
    /**
     * Update booking status
     */
    @Query("UPDATE cached_bookings SET status = :status, lastSyncAt = :syncTime WHERE id = :bookingId")
    suspend fun updateStatus(bookingId: String, status: String, syncTime: Long = System.currentTimeMillis())
    
    /**
     * Update truck fill count
     */
    @Query("""
        UPDATE cached_bookings 
        SET trucksFilled = :filled, lastSyncAt = :syncTime 
        WHERE id = :bookingId
    """)
    suspend fun updateTrucksFilled(bookingId: String, filled: Int, syncTime: Long = System.currentTimeMillis())
    
    /**
     * Mark as synced
     */
    @Query("UPDATE cached_bookings SET needsSync = 0, lastSyncAt = :syncTime WHERE id = :bookingId")
    suspend fun markSynced(bookingId: String, syncTime: Long = System.currentTimeMillis())
    
    /**
     * Mark as needing sync
     */
    @Query("UPDATE cached_bookings SET needsSync = 1 WHERE id = :bookingId")
    suspend fun markNeedsSync(bookingId: String)
    
    /**
     * Delete booking
     */
    @Query("DELETE FROM cached_bookings WHERE id = :bookingId")
    suspend fun deleteById(bookingId: String)
    
    /**
     * Clear old completed bookings (older than 30 days)
     */
    @Query("""
        DELETE FROM cached_bookings 
        WHERE status IN ('COMPLETED', 'CANCELLED') AND completedAt < :olderThan
    """)
    suspend fun clearOldBookings(olderThan: Long)
    
    /**
     * Clear all cached bookings
     */
    @Query("DELETE FROM cached_bookings")
    suspend fun clearAll()
    
    /**
     * Get count of active bookings
     */
    @Query("""
        SELECT COUNT(*) FROM cached_bookings 
        WHERE status NOT IN ('COMPLETED', 'CANCELLED')
    """)
    fun getActiveBookingCount(): Flow<Int>
}
