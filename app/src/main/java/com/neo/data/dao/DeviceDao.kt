package com.neo.data.dao

import androidx.room.*
import com.neo.data.model.Device
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Device operations.
 */
@Dao
interface DeviceDao {
    
    /**
     * Insert or update a device.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(device: Device)
    
    /**
     * Get all known devices.
     */
    @Query("SELECT * FROM devices ORDER BY lastSeenTimestamp DESC")
    fun getAllDevices(): Flow<List<Device>>
    
    /**
     * Get a specific device by ID.
     */
    @Query("SELECT * FROM devices WHERE deviceId = :deviceId")
    suspend fun getDeviceById(deviceId: String): Device?
    
    /**
     * Update last seen timestamp for a device.
     */
    @Query("UPDATE devices SET lastSeenTimestamp = :timestamp WHERE deviceId = :deviceId")
    suspend fun updateLastSeen(deviceId: String, timestamp: Long)
    
    /**
     * Get recently seen devices (within last 24 hours).
     */
    @Query("SELECT * FROM devices WHERE lastSeenTimestamp > :afterTimestamp ORDER BY lastSeenTimestamp DESC")
    suspend fun getRecentDevices(afterTimestamp: Long): List<Device>
    
    /**
     * Delete all devices (for testing/debugging).
     */
    @Query("DELETE FROM devices")
    suspend fun deleteAll()
}
