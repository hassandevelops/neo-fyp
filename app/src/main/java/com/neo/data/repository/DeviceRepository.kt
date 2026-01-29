package com.neo.data.repository

import com.neo.data.dao.DeviceDao
import com.neo.data.model.Device
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for Device data operations.
 */
@Singleton
class DeviceRepository @Inject constructor(
    private val deviceDao: DeviceDao
) {
    
    /**
     * Get all known devices as a Flow.
     */
    fun getAllDevices(): Flow<List<Device>> = deviceDao.getAllDevices()
    
    /**
     * Insert or update a device.
     */
    suspend fun insertDevice(device: Device) {
        deviceDao.insert(device)
    }
    
    /**
     * Get a specific device by ID.
     */
    suspend fun getDeviceById(deviceId: String): Device? {
        return deviceDao.getDeviceById(deviceId)
    }
    
    /**
     * Update last seen timestamp for a device.
     */
    suspend fun updateLastSeen(deviceId: String, timestamp: Long) {
        deviceDao.updateLastSeen(deviceId, timestamp)
    }
    
    /**
     * Get recently seen devices.
     */
    suspend fun getRecentDevices(afterTimestamp: Long): List<Device> {
        return deviceDao.getRecentDevices(afterTimestamp)
    }
}
