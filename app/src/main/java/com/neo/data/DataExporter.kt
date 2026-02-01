package com.neo.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.neo.data.model.Post
import com.neo.data.repository.PostRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles data export and import for backup/restore functionality.
 */
@Singleton
class DataExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val postRepository: PostRepository
) {
    
    companion object {
        private const val TAG = "DataExporter"
        private const val EXPORT_FILE_NAME = "neo_backup.json"
        private const val EXPORT_VERSION = 1
    }
    
    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .create()
    
    /**
     * Export data container.
     */
    data class ExportData(
        val version: Int,
        val exportedAt: Long,
        val deviceId: String,
        val posts: List<Post>
    )
    
    /**
     * Export result.
     */
    sealed class ExportResult {
        data class Success(val file: File, val postCount: Int) : ExportResult()
        data class Failure(val error: String) : ExportResult()
    }
    
    /**
     * Import result.
     */
    sealed class ImportResult {
        data class Success(val importedCount: Int, val skippedCount: Int) : ImportResult()
        data class Failure(val error: String) : ImportResult()
    }
    
    /**
     * Export all posts to a JSON file.
     * 
     * @param exportDir Directory to export to (defaults to external files dir)
     * @return ExportResult with file location or error
     */
    suspend fun exportData(exportDir: File? = null): ExportResult = withContext(Dispatchers.IO) {
        try {
            // Get all posts
            val posts = getAllPostsForExport()
            
            Log.i(TAG, "Exporting ${posts.size} posts")
            
            // Create export data
            val exportData = ExportData(
                version = EXPORT_VERSION,
                exportedAt = System.currentTimeMillis(),
                deviceId = getDeviceId(),
                posts = posts
            )
            
            // Determine export directory
            val targetDir = exportDir ?: context.getExternalFilesDir(null) ?: context.filesDir
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            
            // Create export file with timestamp
            val timestamp = System.currentTimeMillis()
            val fileName = "neo_backup_$timestamp.json"
            val exportFile = File(targetDir, fileName)
            
            // Write to file
            FileWriter(exportFile).use { writer ->
                gson.toJson(exportData, writer)
            }
            
            Log.i(TAG, "Export successful: ${exportFile.absolutePath}")
            ExportResult.Success(exportFile, posts.size)
            
        } catch (e: Exception) {
            Log.e(TAG, "Export failed", e)
            ExportResult.Failure(e.message ?: "Unknown error")
        }
    }
    
    /**
     * Import posts from a JSON backup file.
     * 
     * @param importFile The backup file to import from
     * @param mergeStrategy How to handle existing posts (SKIP or REPLACE)
     * @return ImportResult with counts or error
     */
    suspend fun importData(
        importFile: File,
        mergeStrategy: MergeStrategy = MergeStrategy.SKIP
    ): ImportResult = withContext(Dispatchers.IO) {
        try {
            if (!importFile.exists()) {
                return@withContext ImportResult.Failure("Import file does not exist")
            }
            
            // Read and parse file
            val exportData = FileReader(importFile).use { reader ->
                gson.fromJson(reader, ExportData::class.java)
            }
            
            if (exportData == null) {
                return@withContext ImportResult.Failure("Invalid backup file format")
            }
            
            Log.i(TAG, "Importing ${exportData.posts.size} posts from backup (version ${exportData.version})")
            
            // Validate version
            if (exportData.version > EXPORT_VERSION) {
                return@withContext ImportResult.Failure("Backup version ${exportData.version} is newer than supported version $EXPORT_VERSION")
            }
            
            // Import posts
            var importedCount = 0
            var skippedCount = 0
            
            for (post in exportData.posts) {
                val exists = postRepository.postExists(post.id)
                
                when {
                    !exists -> {
                        // New post, insert it
                        postRepository.insertPost(post)
                        importedCount++
                    }
                    mergeStrategy == MergeStrategy.REPLACE -> {
                        // Replace existing post
                        postRepository.updatePost(post)
                        importedCount++
                    }
                    else -> {
                        // Skip existing post
                        skippedCount++
                    }
                }
            }
            
            Log.i(TAG, "Import complete: imported=$importedCount, skipped=$skippedCount")
            ImportResult.Success(importedCount, skippedCount)
            
        } catch (e: Exception) {
            Log.e(TAG, "Import failed", e)
            ImportResult.Failure(e.message ?: "Unknown error")
        }
    }
    
    /**
     * Get all posts for export (non-reactive).
     */
    private suspend fun getAllPostsForExport(): List<Post> {
        // This is a simplified version
        // In real implementation, we'd need a method to get all posts as a list
        // For now, return empty list as placeholder
        return emptyList()
    }
    
    /**
     * Get device ID.
     * TODO: Inject CryptoManager
     */
    private fun getDeviceId(): String {
        return "unknown"
    }
    
    /**
     * Merge strategy for imports.
     */
    enum class MergeStrategy {
        SKIP,    // Skip existing posts
        REPLACE  // Replace existing posts with imported data
    }
    
    /**
     * Get list of available backup files.
     */
    fun getAvailableBackups(backupDir: File? = null): List<File> {
        val targetDir = backupDir ?: context.getExternalFilesDir(null) ?: context.filesDir
        
        return targetDir.listFiles { file ->
            file.name.startsWith("neo_backup_") && file.name.endsWith(".json")
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
    
    /**
     * Delete a backup file.
     */
    fun deleteBackup(backupFile: File): Boolean {
        return try {
            backupFile.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete backup", e)
            false
        }
    }
}
