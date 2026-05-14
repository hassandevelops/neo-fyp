package com.neo.domain.port

data class CompressedImageResult(
    val hash: String,
    val data: ByteArray,
    val sizeBytes: Int,
    val width: Int,
    val height: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as CompressedImageResult
        return hash == other.hash
    }

    override fun hashCode(): Int = hash.hashCode()
}

interface IImageCompressor {
    suspend fun compress(imageUri: String): CompressedImageResult?
}