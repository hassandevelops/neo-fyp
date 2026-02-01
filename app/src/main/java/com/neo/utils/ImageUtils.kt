package com.neo.utils

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Utility for handling image storage
 */
object ImageUtils {
    
    /**
     * Copy image from content URI to app's internal storage
     * Returns the file path to the saved image
     */
    fun saveImageToInternalStorage(context: Context, imageUri: Uri): String? {
        return try {
            // Create images directory if it doesn't exist
            val imagesDir = File(context.filesDir, "images")
            if (!imagesDir.exists()) {
                imagesDir.mkdirs()
            }
            
            // Generate unique filename
            val filename = "${UUID.randomUUID()}.jpg"
            val imageFile = File(imagesDir, filename)
            
            // Copy image data
            context.contentResolver.openInputStream(imageUri)?.use { input ->
                FileOutputStream(imageFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            // Return absolute path
            imageFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Delete image from internal storage
     */
    fun deleteImage(imagePath: String): Boolean {
        return try {
            val file = File(imagePath)
            if (file.exists()) {
                file.delete()
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Get file URI for image path
     */
    fun getFileUri(imagePath: String): Uri {
        return Uri.fromFile(File(imagePath))
    }
}
