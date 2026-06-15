package com.neo.media

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

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
     * Thread-safe with bounded concurrency and stale-entry timeout.
     */
    class ChunkAssemblyManager(private val scope: CoroutineScope? = null) {

        companion object {
            private const val TAG = "ChunkAssemblyManager"
            private const val MAX_CONCURRENT_ASSEMBLIES = 10
            // Generous margin: chunks are now delivered reliably (acked + retried
            // with backoff up to 15s/chunk) and may pause briefly across a
            // reconnect, so don't sweep a slow-but-recovering transfer mid-flight.
            private const val ASSEMBLY_TIMEOUT_MS = 180_000L
        }

        private data class Assembly(
            val chunks: CopyOnWriteArrayList<ImageChunk> = CopyOnWriteArrayList(),
            val startedAt: Long = System.currentTimeMillis()
        )

        private val assemblies = ConcurrentHashMap<String, Assembly>()
        private val mutex = Mutex()

        init {
            scope?.launch {
                while (isActive) {
                    delay(30_000)
                    sweepStaleAssemblies()
                }
            }
        }

        /**
         * Add a received chunk.
         * 
         * @param chunk The received chunk
         * @return Complete image data if all chunks received, null otherwise
         */
        suspend fun addChunk(chunk: ImageChunk): String? = mutex.withLock {
            val imageId = chunk.postId

            // Enforce max concurrent assemblies
            if (!assemblies.containsKey(imageId) && assemblies.size >= MAX_CONCURRENT_ASSEMBLIES) {
                Log.w(TAG, "Max concurrent assemblies reached ($MAX_CONCURRENT_ASSEMBLIES), dropping chunk for $imageId")
                return@withLock null
            }

            val assembly = assemblies.getOrPut(imageId) { Assembly() }

            // Check if we already have this chunk
            if (assembly.chunks.any { it.chunkIndex == chunk.chunkIndex }) {
                Log.d(TAG, "Duplicate chunk ${chunk.chunkIndex} for post $imageId")
                return@withLock null
            }

            assembly.chunks.add(chunk)
            Log.d(TAG, "Received chunk ${chunk.chunkIndex}/${chunk.totalChunks} for post $imageId")

            // Check if we have all chunks
            if (assembly.chunks.size == chunk.totalChunks) {
                assemblies.remove(imageId)
                val chunker = ImageChunker()
                val imageData = chunker.reassembleChunks(assembly.chunks)

                if (imageData != null) {
                    Log.d(TAG, "Completed reassembly for post $imageId")
                }
                return@withLock imageData
            }

            return@withLock null
        }

        /**
         * Get progress for a specific post.
         */
        fun getProgress(imageId: String): Pair<Int, Int>? {
            val assembly = assemblies[imageId] ?: return null
            val chunks = assembly.chunks
            if (chunks.isEmpty()) return null
            return Pair(chunks.size, chunks.first().totalChunks)
        }

        /**
         * Clear chunks for a specific post (e.g., on timeout).
         */
        fun clearChunks(imageId: String) {
            assemblies.remove(imageId)
            Log.d(TAG, "Cleared chunks for post $imageId")
        }

        /**
         * Get all posts currently being assembled.
         */
        fun getActiveAssemblies(): Set<String> {
            return assemblies.keys
        }

        private fun sweepStaleAssemblies() {
            val now = System.currentTimeMillis()
            val stale = assemblies.entries
                .filter { (_, assembly) -> now - assembly.startedAt > ASSEMBLY_TIMEOUT_MS }
                .map { it.key }
            stale.forEach { imageId ->
                Log.w(TAG, "Sweeping stale assembly for $imageId")
                assemblies.remove(imageId)
            }
        }
    }
}
