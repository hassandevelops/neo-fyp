package com.neo.media

import java.security.MessageDigest

/**
 * Utility class for chunking large image data for Bluetooth transmission.
 * Splits Base64 image data into smaller chunks with sequence numbers and checksums.
 */
class ImageChunker {
    
    companion object {
        private const val TAG = "ImageChunker"
        const val CHUNK_SIZE = 4096 // 4KB per chunk (safe for Bluetooth)
    }
    
    /**
     * Split image data into chunks.
     * 
     * @param imageData Base64 encoded image data
     * @param postId ID of the post this image belongs to
     * @return List of image chunks
     */
    fun chunkImage(imageData: String, postId: String): List<ImageChunk> {
        val chunks = mutableListOf<ImageChunk>()
        val totalChunks = (imageData.length + CHUNK_SIZE - 1) / CHUNK_SIZE
        
        for (i in 0 until totalChunks) {
            val start = i * CHUNK_SIZE
            val end = minOf(start + CHUNK_SIZE, imageData.length)
            val chunkData = imageData.substring(start, end)
            
            chunks.add(
                ImageChunk(
                    postId = postId,
                    chunkIndex = i,
                    totalChunks = totalChunks,
                    data = chunkData,
                    checksum = calculateChecksum(chunkData)
                )
            )
        }
        
        android.util.Log.d(TAG, "Split image into $totalChunks chunks for post $postId")
        return chunks
    }
    
    /**
     * Reassemble chunks into complete image data.
     * 
     * @param chunks List of image chunks (must be complete set)
     * @return Reassembled Base64 image data, or null if chunks are invalid
     */
    fun reassembleChunks(chunks: List<ImageChunk>): String? {
        if (chunks.isEmpty()) return null
        
        // Verify we have all chunks
        val totalChunks = chunks.first().totalChunks
        if (chunks.size != totalChunks) {
            android.util.Log.w(TAG, "Missing chunks: have ${chunks.size}, need $totalChunks")
            return null
        }
        
        // Sort by chunk index
        val sortedChunks = chunks.sortedBy { it.chunkIndex }
        
        // Verify sequence is complete
        for (i in sortedChunks.indices) {
            if (sortedChunks[i].chunkIndex != i) {
                android.util.Log.w(TAG, "Chunk sequence broken at index $i")
                return null
            }
        }
        
        // Verify checksums
        for (chunk in sortedChunks) {
            val expectedChecksum = calculateChecksum(chunk.data)
            if (chunk.checksum != expectedChecksum) {
                android.util.Log.w(TAG, "Checksum mismatch for chunk ${chunk.chunkIndex}")
                return null
            }
        }
        
        // Reassemble
        val reassembled = sortedChunks.joinToString("") { it.data }
        android.util.Log.d(TAG, "Reassembled ${sortedChunks.size} chunks into ${reassembled.length} chars")
        
        return reassembled
    }
    
    /**
     * Calculate CRC32 checksum for chunk data.
     */
    private fun calculateChecksum(data: String): String {
        val crc = java.util.zip.CRC32()
        crc.update(data.toByteArray())
        return crc.value.toString(16)
    }
    
    /**
     * Data class representing a single image chunk.
     */
    data class ImageChunk(
        val postId: String,      // ID of the post this chunk belongs to
        val chunkIndex: Int,     // Index of this chunk (0-based)
        val totalChunks: Int,    // Total number of chunks
        val data: String,        // Chunk data (portion of Base64 string)
        val checksum: String     // CRC32 checksum of this chunk
    )
    
    /**
     * Manager for tracking chunk reassembly progress.
     */
    class ChunkAssemblyManager {
        private val receivedChunks = mutableMapOf<String, MutableList<ImageChunk>>()
        
        /**
         * Add a received chunk.
         * 
         * @param chunk The received chunk
         * @return Complete image data if all chunks received, null otherwise
         */
        fun addChunk(chunk: ImageChunk): String? {
            val chunks = receivedChunks.getOrPut(chunk.postId) { mutableListOf() }
            
            // Check if we already have this chunk
            if (chunks.any { it.chunkIndex == chunk.chunkIndex }) {
                android.util.Log.d(TAG, "Duplicate chunk ${chunk.chunkIndex} for post ${chunk.postId}")
                return null
            }
            
            chunks.add(chunk)
            android.util.Log.d(TAG, "Received chunk ${chunk.chunkIndex}/${chunk.totalChunks} for post ${chunk.postId}")
            
            // Check if we have all chunks
            if (chunks.size == chunk.totalChunks) {
                val chunker = ImageChunker()
                val imageData = chunker.reassembleChunks(chunks)
                
                if (imageData != null) {
                    // Clean up
                    receivedChunks.remove(chunk.postId)
                    android.util.Log.d(TAG, "Completed reassembly for post ${chunk.postId}")
                }
                
                return imageData
            }
            
            return null
        }
        
        /**
         * Get progress for a specific post.
         * 
         * @param postId ID of the post
         * @return Pair of (received chunks, total chunks), or null if no chunks received
         */
        fun getProgress(postId: String): Pair<Int, Int>? {
            val chunks = receivedChunks[postId] ?: return null
            if (chunks.isEmpty()) return null
            return Pair(chunks.size, chunks.first().totalChunks)
        }
        
        /**
         * Clear chunks for a specific post (e.g., on timeout).
         */
        fun clearChunks(postId: String) {
            receivedChunks.remove(postId)
            android.util.Log.d(TAG, "Cleared chunks for post $postId")
        }
        
        /**
         * Get all posts currently being assembled.
         */
        fun getActiveAssemblies(): Set<String> {
            return receivedChunks.keys
        }
    }
}
