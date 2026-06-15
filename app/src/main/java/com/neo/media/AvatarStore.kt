package com.neo.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates small avatar thumbnails for profile gossip and caches received
 * thumbnails on disk so they can be loaded by file:// URI like any other image.
 */
@Singleton
class AvatarStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "AvatarStore"
        private const val AVATARS_DIR = "avatars"
        private const val THUMB_SIZE = 128       // px (longest edge)
        private const val THUMB_QUALITY = 80     // JPEG quality
    }

    private val avatarsDir: File by lazy {
        File(context.filesDir, AVATARS_DIR).also { if (!it.exists()) it.mkdirs() }
    }

    /**
     * Decode the image at [imageUriOrPath] (file:// URI or absolute path),
     * downscale to a small square-ish thumbnail, and return it as base64 JPEG.
     * Returns null if there's no image or it can't be decoded.
     */
    fun makeThumbnailBase64(imageUriOrPath: String?): String? {
        if (imageUriOrPath.isNullOrBlank()) return null
        return try {
            val path = Uri.parse(imageUriOrPath).path ?: imageUriOrPath
            val file = File(path)
            if (!file.exists()) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val sample = maxOf(1, minOf(bounds.outWidth, bounds.outHeight) / THUMB_SIZE)
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
            val scaled = scaleLongestEdge(decoded, THUMB_SIZE)
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, THUMB_QUALITY, out)
            if (scaled != decoded) decoded.recycle()
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to make avatar thumbnail: ${e.message}")
            null
        }
    }

    /**
     * Persist a received base64 JPEG thumbnail for [did] and return its file://
     * URI (stable per DID, overwritten on update). Returns null on failure.
     */
    fun saveAvatar(did: String, base64: String?): String? {
        if (base64.isNullOrBlank()) return null
        return try {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            val file = File(avatarsDir, "${safeName(did)}.jpg")
            FileOutputStream(file).use { it.write(bytes) }
            Uri.fromFile(file).toString()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save avatar for $did: ${e.message}")
            null
        }
    }

    private fun scaleLongestEdge(src: Bitmap, target: Int): Bitmap {
        val longest = maxOf(src.width, src.height)
        if (longest <= target) return src
        val ratio = target.toFloat() / longest
        return Bitmap.createScaledBitmap(
            src, (src.width * ratio).toInt().coerceAtLeast(1),
            (src.height * ratio).toInt().coerceAtLeast(1), true
        )
    }

    private fun safeName(did: String): String =
        did.replace(Regex("[^A-Za-z0-9]"), "_").take(80)
}
