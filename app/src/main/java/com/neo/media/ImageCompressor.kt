package com.neo.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.math.sqrt

/**
 * Utility class for compressing images before Bluetooth transmission.
 * Compresses images to target size while maintaining acceptable quality.
 */
class ImageCompressor(private val context: Context) {
    
    companion object {
        private const val TAG = "ImageCompressor"
        private const val TARGET_SIZE_BYTES = 100 * 1024 // 100KB target
        private const val MAX_DIMENSION = 1024 // Max width/height
        private const val INITIAL_QUALITY = 85
        private const val MIN_QUALITY = 30
    }
    
    /**
     * Compress an image from URI to Base64 string.
     * 
     * @param imageUri URI of the image to compress
     * @return Base64 encoded compressed image, or null if compression fails
     */
    fun compressImage(imageUri: Uri): CompressedImage? {
        return try {
            // Load bitmap from URI
            val bitmap = loadBitmapFromUri(imageUri) ?: return null
            
            // Resize if too large
            val resizedBitmap = resizeBitmap(bitmap, MAX_DIMENSION)
            
            // Compress to target size
            val compressedData = compressToTargetSize(resizedBitmap)
            
            // Convert to Base64
            val base64Data = Base64.encodeToString(compressedData, Base64.NO_WRAP)
            
            // Calculate hash for verification
            val hash = calculateHash(compressedData)
            
            // Clean up
            if (bitmap != resizedBitmap) {
                bitmap.recycle()
            }
            resizedBitmap.recycle()
            
            CompressedImage(
                data = base64Data,
                sizeBytes = compressedData.size,
                hash = hash,
                width = resizedBitmap.width,
                height = resizedBitmap.height
            )
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to compress image: ${e.message}", e)
            null
        }
    }
    
    /**
     * Decompress Base64 image data to Bitmap.
     * 
     * @param base64Data Base64 encoded image data
     * @return Decoded Bitmap, or null if decoding fails
     */
    fun decompressImage(base64Data: String): Bitmap? {
        return try {
            val imageBytes = Base64.decode(base64Data, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to decompress image: ${e.message}", e)
            null
        }
    }
    
    /**
     * Verify image integrity using hash.
     * 
     * @param base64Data Base64 encoded image data
     * @param expectedHash Expected hash value
     * @return true if hash matches, false otherwise
     */
    fun verifyImage(base64Data: String, expectedHash: String): Boolean {
        return try {
            val imageBytes = Base64.decode(base64Data, Base64.NO_WRAP)
            val actualHash = calculateHash(imageBytes)
            actualHash == expectedHash
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Load bitmap from URI with proper scaling.
     * Handles both content:// URIs and file:// paths.
     */
    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        var inputStream: InputStream? = null
        return try {
            // Check if it's a file path or content URI
            inputStream = when (uri.scheme) {
                "file" -> {
                    // Handle file:// URIs by opening the file directly
                    val filePath = uri.path ?: return null
                    java.io.FileInputStream(java.io.File(filePath))
                }
                "content" -> {
                    // Handle content:// URIs via ContentResolver
                    context.contentResolver.openInputStream(uri)
                }
                else -> {
                    // Try as file path if no scheme
                    val filePath = uri.toString()
                    java.io.FileInputStream(java.io.File(filePath))
                }
            }
            
            if (inputStream == null) {
                android.util.Log.e(TAG, "Failed to open input stream for URI: $uri")
                return null
            }
            
            // First decode to get dimensions
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()
            
            // Calculate sample size
            val sampleSize = calculateSampleSize(options.outWidth, options.outHeight, MAX_DIMENSION)
            
            // Reopen stream for actual decoding
            inputStream = when (uri.scheme) {
                "file" -> {
                    val filePath = uri.path ?: return null
                    java.io.FileInputStream(java.io.File(filePath))
                }
                "content" -> {
                    context.contentResolver.openInputStream(uri)
                }
                else -> {
                    val filePath = uri.toString()
                    java.io.FileInputStream(java.io.File(filePath))
                }
            }
            
            // Decode with sample size
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565 // Use less memory
            }
            BitmapFactory.decodeStream(inputStream, null, decodeOptions)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to load bitmap from $uri: ${e.message}", e)
            null
        } finally {
            inputStream?.close()
        }
    }
    
    /**
     * Calculate appropriate sample size for decoding.
     */
    private fun calculateSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sampleSize = 1
        if (width > maxDimension || height > maxDimension) {
            val halfWidth = width / 2
            val halfHeight = height / 2
            
            while ((halfWidth / sampleSize) >= maxDimension && 
                   (halfHeight / sampleSize) >= maxDimension) {
                sampleSize *= 2
            }
        }
        return sampleSize
    }
    
    /**
     * Resize bitmap to fit within max dimension while maintaining aspect ratio.
     */
    private fun resizeBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= maxDimension && height <= maxDimension) {
            return bitmap
        }
        
        val scale = minOf(
            maxDimension.toFloat() / width,
            maxDimension.toFloat() / height
        )
        
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
    
    /**
     * Compress bitmap to target size by adjusting quality.
     */
    private fun compressToTargetSize(bitmap: Bitmap): ByteArray {
        var quality = INITIAL_QUALITY
        var compressedData: ByteArray
        
        do {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            compressedData = outputStream.toByteArray()
            
            if (compressedData.size <= TARGET_SIZE_BYTES || quality <= MIN_QUALITY) {
                break
            }
            
            // Reduce quality for next iteration
            quality -= 10
        } while (true)
        
        android.util.Log.d(TAG, "Compressed to ${compressedData.size} bytes at quality $quality")
        return compressedData
    }
    
    /**
     * Calculate SHA-256 hash of byte array.
     */
    private fun calculateHash(data: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(data)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Data class representing a compressed image.
     */
    data class CompressedImage(
        val data: String,        // Base64 encoded image data
        val sizeBytes: Int,      // Size in bytes
        val hash: String,        // SHA-256 hash for verification
        val width: Int,          // Image width
        val height: Int          // Image height
    )
}
