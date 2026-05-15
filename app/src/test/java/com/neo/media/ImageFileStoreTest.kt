package com.neo.media

import android.content.Context
import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ImageFileStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var fileStore: ImageFileStore

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        every { context.filesDir } returns temporaryFolder.root
        fileStore = ImageFileStore(context)
    }

    @Test
    fun `save image data creates file`() {
        val hash = "abc123def456"
        val data = ByteArray(100)

        val path = fileStore.save(hash, data)

        assertNotNull(path)
        assertTrue(path?.contains(hash) == true)
        assertTrue(fileStore.exists(hash))
        assertArrayEquals(data, fileStore.load(hash))
    }

    @Test
    fun `load returns null for non-existent image`() {
        val data = fileStore.load("nonexistent")

        assertNull(data)
    }

    @Test
    fun `exists returns false for unknown hash`() {
        assertFalse(fileStore.exists("unknown"))
    }

    @Test
    fun `delete removes file`() {
        val hash = "somehash"
        fileStore.save(hash, ByteArray(10))

        val result = fileStore.delete(hash)
        assertTrue(result)
        assertFalse(fileStore.exists(hash))
    }
}
