package com.neo.media

import android.content.Context
import android.net.Uri
import com.neo.domain.port.CompressedImageResult
import com.neo.domain.port.IImageCompressor
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageCompressorAdapter @Inject constructor(
    @ApplicationContext private val context: Context
) : IImageCompressor {

    private val compressor = ImageCompressor(context)

    override suspend fun compress(imageUri: String): CompressedImageResult? {
        return try {
            val uri = Uri.parse(imageUri)
            val compressed = compressor.compressImage(uri) ?: return null

            CompressedImageResult(
                hash = compressed.hash,
                data = android.util.Base64.decode(compressed.data, android.util.Base64.DEFAULT),
                sizeBytes = compressed.sizeBytes,
                width = compressed.width,
                height = compressed.height
            )
        } catch (e: Exception) {
            null
        }
    }
}