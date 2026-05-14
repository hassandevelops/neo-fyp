package com.neo.media

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton for handling image file storage in internal storage.
 * Handles save, load, delete, and eviction of image files.
 */
@Singleton
class ImageFileStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ImageFileStore"
        private const val IMAGES_DIR = "images"
        private const val MAX_STORAGE_SIZE_MB = 100
        private const val MAX_FILE_AGE_DAYS = 30
    }

    private val imagesDir: File by lazy {
        File(context.filesDir, IMAGES_DIR).also { dir ->
            if (!dir.exists()) {
                dir.mkdirs()
            }
        }
    }

    /**
     * Save image data to internal storage.
     * @param imageHash The SHA-256 hash of the image (used as filename)
     * @param imageData The image data bytes
     * @return The file path if successful, null otherwise
     */
    fun save(imageHash: String, imageData: ByteArray): String? {
        return try {
            val file = File(imagesDir, "$imageHash.jpg")
            FileOutputStream(file).use { output ->
                output.write(imageData)
            }
            Log.d(TAG, "Saved image: $imageHash, size: ${imageData.size} bytes")
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save image: $imageHash", e)
            null
        }
    }

    /**
     * Load image data from internal storage.
     * @param imageHash The SHA-256 hash of the image
     * @return The image data bytes, or null if not found
     */
    fun load(imageHash: String): ByteArray? {
        return try {
            val file = File(imagesDir, "$imageHash.jpg")
            if (file.exists()) {
                FileInputStream(file).use { input ->
                    input.readBytes()
                }
            } else {
                Log.w(TAG, "Image not found: $imageHash")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load image: $imageHash", e)
            null
        }
    }

    /**
     * Get the file path for an image.
     * @param imageHash The SHA-256 hash of the image
     * @return The absolute file path, or null if not found
     */
    fun getFilePath(imageHash: String): String? {
        val file = File(imagesDir, "$imageHash.jpg")
        return if (file.exists()) file.absolutePath else null
    }

    /**
     * Delete an image from storage.
     * @param imageHash The SHA-256 hash of the image
     * @return true if deleted, false otherwise
     */
    fun delete(imageHash: String): Boolean {
        return try {
            val file = File(imagesDir, "$imageHash.jpg")
            if (file.exists()) {
                val deleted = file.delete()
                Log.d(TAG, "Deleted image: $imageHash, success: $deleted")
                deleted
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete image: $imageHash", e)
            false
        }
    }

    /**
     * Check if an image exists in storage.
     * @param imageHash The SHA-256 hash of the image
     * @return true if exists, false otherwise
     */
    fun exists(imageHash: String): Boolean {
        return File(imagesDir, "$imageHash.jpg").exists()
    }

    /**
     * Evict old images to free up storage space.
     * Removes files older than MAX_FILE_AGE_DAYS and enforces MAX_STORAGE_SIZE_MB limit.
     */
    fun evict(): Long {
        var freedBytes = 0L
        val now = System.currentTimeMillis()
        val maxAge = MAX_FILE_AGE_DAYS * 24 * 60 * 60 * 1000L
        val maxSize = MAX_STORAGE_SIZE_MB * 1024 * 1024L

        try {
            // First, remove old files
            imagesDir.listFiles()?.forEach { file ->
                val age = now - file.lastModified()
                if (age > maxAge) {
                    val size = file.length()
                    if (file.delete()) {
                        freedBytes += size
                        Log.d(TAG, "Evicted old file: ${file.name}, size: $size")
                    }
                }
            }

            // Then, enforce storage limit if needed
            var currentSize = imagesDir.listFiles()?.sumOf { it.length() } ?: 0L
            if (currentSize > maxSize) {
                val files = imagesDir.listFiles()?.sortedBy { it.lastModified() } ?: return freedBytes
                for (file in files) {
                    if (currentSize <= maxSize / 2) break // Stop when we're at half capacity
                    val size = file.length()
                    if (file.delete()) {
                        freedBytes += size
                        currentSize -= size
                        Log.d(TAG, "Evicted file for size limit: ${file.name}, size: $size")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during eviction", e)
        }

        Log.d(TAG, "Total space freed: $freedBytes bytes")
        return freedBytes
    }

    /**
     * Get the total size of images stored.
     * @return Total size in bytes
     */
    fun getTotalSize(): Long {
        return imagesDir.listFiles()?.sumOf { it.length() } ?: 0L
    }
}