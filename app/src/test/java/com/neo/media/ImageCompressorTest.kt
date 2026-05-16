package com.neo.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
class ImageCompressorTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var imageCompressor: ImageCompressor

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        imageCompressor = ImageCompressor(context)
    }

    @Test
    fun `compressImage returns null for invalid URI`() {
        val invalidUri = Uri.parse("file:///nonexistent/path/image.jpg")

        val result = imageCompressor.compressImage(invalidUri)

        assertNull(result)
    }

    @Test
    fun `compress and decompress round-trip maintains bitmap`() {
        val testFile = createTestBitmapFile(800, 600)
        val uri = Uri.fromFile(testFile)

        val compressed = imageCompressor.compressImage(uri)
        assertNotNull("compressImage returned null for valid file", compressed)

        val bitmap = imageCompressor.decompressImage(compressed!!.data)
        assertNotNull("decompressImage returned null", bitmap)

        assertEquals(compressed.width, bitmap!!.width)
        assertEquals(compressed.height, bitmap.height)
    }

    @Test
    fun `compressImage generates valid SHA-256 hash`() {
        val testFile = createTestBitmapFile(200, 200)
        val uri = Uri.fromFile(testFile)

        val compressed = imageCompressor.compressImage(uri)
        assertNotNull("compressImage returned null", compressed)

        assertNotNull(compressed!!.hash)
        assertEquals(64, compressed.hash.length)
    }

    @Test
    fun `verifyImage with correct hash returns true`() {
        val testFile = createTestBitmapFile(200, 200)
        val uri = Uri.fromFile(testFile)

        val compressed = imageCompressor.compressImage(uri)
        assertNotNull("compressImage returned null", compressed)

        val isValid = imageCompressor.verifyImage(compressed!!.data, compressed.hash)
        assertTrue(isValid)
    }

    @Test
    fun `verifyImage with incorrect hash returns false`() {
        val testFile = createTestBitmapFile(200, 200)
        val uri = Uri.fromFile(testFile)

        val compressed = imageCompressor.compressImage(uri)
        assertNotNull("compressImage returned null", compressed)

        val wrongHash = "0".repeat(64)
        val isValid = imageCompressor.verifyImage(compressed!!.data, wrongHash)
        assertFalse(isValid)
    }

    @Test
    fun `verifyImage with null data returns false`() {
        val result = imageCompressor.verifyImage("AAAA", "hash")

        assertFalse(result)
    }

    @Test
    fun `compressImage respects max dimension`() {
        val testFile = createTestBitmapFile(2000, 1500)
        val uri = Uri.fromFile(testFile)

        val compressed = imageCompressor.compressImage(uri)
        assertNotNull("compressImage returned null", compressed)
        assertTrue(
            "Width (${compressed!!.width}) > MAX_DIMENSION",
            compressed.width <= 1024
        )
        assertTrue(
            "Height (${compressed.height}) > MAX_DIMENSION",
            compressed.height <= 1024
        )
    }

    private fun createTestBitmapFile(width: Int, height: Int): File {
        val file = temporaryFolder.newFile("test_image_${width}x${height}.jpg")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.rgb(200, 100, 50))
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        bitmap.recycle()
        file.writeBytes(stream.toByteArray())
        return file
    }
}
