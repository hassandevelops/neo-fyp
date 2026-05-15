package com.neo.data

import android.content.Context
import android.util.Log
import com.neo.data.db.AppDatabase
import com.neo.data.repository.PostRepository
import com.neo.security.CryptoManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages storage limits and cleanup for the Neo application.
 * Implements LRU (Least Recently Used) cleanup strategy.
 */
@Singleton
class StorageManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val postRepository: PostRepository,
    private val database: AppDatabase,
    private val cryptoManager: CryptoManager
) {
    
    companion object {
        private const val TAG = "StorageManager"
        private const val DEFAULT_MAX_STORAGE_MB = 500L // 500 MB
        private const val MB_TO_BYTES = 1024L * 1024L
        private const val CLEANUP_THRESHOLD = 0.9 // Trigger cleanup at 90%
    }
    
    private var maxStorageBytes = DEFAULT_MAX_STORAGE_MB * MB_TO_BYTES
    
    /**
     * Configure maximum storage size.
     */
    fun setMaxStorage(megabytes: Long) {
        maxStorageBytes = megabytes * MB_TO_BYTES
        Log.d(TAG, "Max storage set to ${megabytes}MB")
    }
    
    /**
     * Get current database size in bytes.
     */
    suspend fun getDatabaseSize(): Long = withContext(Dispatchers.IO) {
        val dbFile = context.getDatabasePath(AppDatabase.DATABASE_NAME)
        if (dbFile.exists()) {
            dbFile.length()
        } else {
            0L
        }
    }
    
    /**
     * Get total storage usage in bytes (database + images).
     */
    suspend fun getTotalStorageUsage(): Long = withContext(Dispatchers.IO) {
        val dbSize = getDatabaseSize()
        val imageSize = getImageStorageSize()
        dbSize + imageSize
    }
    
    /**
     * Get image storage size in bytes.
     */
    private fun getImageStorageSize(): Long {
        val imageDir = File(context.filesDir, "images")
        if (!imageDir.exists()) return 0L
        
        return imageDir.walkTopDown()
            .filter { it.isFile }
            .map { it.length() }
            .sum()
    }
    
    /**
     * Check if storage is above threshold.
     */
    suspend fun isStorageAboveThreshold(): Boolean {
        val usage = getTotalStorageUsage()
        val threshold = maxStorageBytes * CLEANUP_THRESHOLD
        return usage > threshold
    }
    
    /**
     * Get storage usage percentage (0-100).
     */
    suspend fun getStorageUsagePercentage(): Int {
        val usage = getTotalStorageUsage()
        return ((usage.toDouble() / maxStorageBytes) * 100).toInt().coerceIn(0, 100)
    }
    
    /**
     * Perform cleanup to reduce storage usage.
     * Uses LRU strategy - deletes oldest posts first.
     * Preserves user's own posts.
     * 
     * @param targetPercentage Target storage percentage after cleanup
     * @return Number of posts deleted
     */
    suspend fun performCleanup(targetPercentage: Int = 70): Int = withContext(Dispatchers.IO) {
        val currentUsage = getTotalStorageUsage()
        val targetBytes = (maxStorageBytes * targetPercentage / 100)
        
        if (currentUsage <= targetBytes) {
            Log.d(TAG, "Storage usage within target, no cleanup needed")
            return@withContext 0
        }
        
        val bytesToFree = currentUsage - targetBytes
        Log.i(TAG, "Starting cleanup: need to free ${bytesToFree / MB_TO_BYTES}MB")
        
        // Get device ID to preserve own posts
        val deviceId = getDeviceId()
        
        // Delete old posts (oldest first, excluding own posts)
        val deletedCount = postRepository.deleteOldPosts(
            beforeTimestamp = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000), // 30 days
            excludeAuthorId = deviceId
        )
        
        // Vacuum database to reclaim space
        vacuumDatabase()
        
        val newUsage = getTotalStorageUsage()
        Log.i(TAG, "Cleanup complete: deleted $deletedCount posts, freed ${(currentUsage - newUsage) / MB_TO_BYTES}MB")
        
        deletedCount
    }
    
    /**
     * Delete posts older than specified days.
     * 
     * @param retentionDays Number of days to retain posts
     * @param preserveOwnPosts Whether to preserve user's own posts
     * @return Number of posts deleted
     */
    suspend fun deleteOldPosts(retentionDays: Int, preserveOwnPosts: Boolean = true): Int = withContext(Dispatchers.IO) {
        val cutoffTimestamp = System.currentTimeMillis() - (retentionDays.toLong() * 24 * 60 * 60 * 1000)
        val deviceId = if (preserveOwnPosts) getDeviceId() else null
        
        val deletedCount = postRepository.deleteOldPosts(cutoffTimestamp, deviceId)
        
        if (deletedCount > 0) {
            vacuumDatabase()
        }
        
        Log.i(TAG, "Deleted $deletedCount posts older than $retentionDays days")
        deletedCount
    }
    
    /**
     * Vacuum database to reclaim space.
     */
    private suspend fun vacuumDatabase() = withContext(Dispatchers.IO) {
        try {
            database.openHelper.writableDatabase.execSQL("VACUUM")
            Log.d(TAG, "Database vacuumed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to vacuum database", e)
        }
    }
    
    /**
     * Clean up orphaned image files (images not referenced by any post).
     */
    suspend fun cleanupOrphanedImages(): Int = withContext(Dispatchers.IO) {
        val imageDir = File(context.filesDir, "images")
        if (!imageDir.exists()) return@withContext 0

        val referencedHashes = postRepository.getReferencedImageHashes()
        var deletedCount = 0

        imageDir.listFiles { file -> file.isFile }?.forEach { file ->
            val imageHash = file.name.removeSuffix(".jpg")
            if (imageHash !in referencedHashes && file.delete()) {
                deletedCount++
                Log.d(TAG, "Deleted orphaned image: ${file.name}")
            }
        }

        deletedCount
    }
    
    /**
     * Get storage statistics.
     */
    suspend fun getStorageStats(): StorageStats = withContext(Dispatchers.IO) {
        val totalUsage = getTotalStorageUsage()
        val dbSize = getDatabaseSize()
        val imageSize = getImageStorageSize()
        val postCount = postRepository.getPostCount()
        
        StorageStats(
            totalUsageBytes = totalUsage,
            databaseSizeBytes = dbSize,
            imageSizeBytes = imageSize,
            maxStorageBytes = maxStorageBytes,
            usagePercentage = getStorageUsagePercentage(),
            postCount = postCount
        )
    }
    
    private fun getDeviceId(): String {
        return cryptoManager.getDeviceId()
    }
    
    /**
     * Storage statistics data class.
     */
    data class StorageStats(
        val totalUsageBytes: Long,
        val databaseSizeBytes: Long,
        val imageSizeBytes: Long,
        val maxStorageBytes: Long,
        val usagePercentage: Int,
        val postCount: Int
    ) {
        val totalUsageMB: Long get() = totalUsageBytes / MB_TO_BYTES
        val databaseSizeMB: Long get() = databaseSizeBytes / MB_TO_BYTES
        val imageSizeMB: Long get() = imageSizeBytes / MB_TO_BYTES
        val maxStorageMB: Long get() = maxStorageBytes / MB_TO_BYTES
    }
}
