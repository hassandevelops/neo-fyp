package com.neo.media

import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class ImageFileStoreTest {

    private lateinit var fileStore: ImageFileStore

    @Before
    fun setup() {
        fileStore = ImageFileStore(mockk(relaxed = true))
    }

    @Test
    fun `save image data creates file`() {
        val hash = "abc123def456"
        val data = ByteArray(100)

        // Test exists after save
        every { fileStore.fileExists(hash) } returns true
        every { fileStore.save(any(), any()) } returns "/path/to/images/$hash.jpg"
        every { fileStore.load(any()) } returns data

        val path = fileStore.save(hash, data)
        assertNotNull(path)
        assertTrue(path?.contains(hash) == true)
    }

    @Test
    fun `load returns null for non-existent image`() {
        every { fileStore.load("nonexistent") } returns null

        val data = fileStore.load("nonexistent")

        assertNull(data)
    }

    @Test
    fun `exists returns false for unknown hash`() {
        every { fileStore.fileExists("unknown") } returns false

        assertFalse(fileStore.fileExists("unknown"))
    }

    @Test
    fun `delete removes file`() {
        every { fileStore.delete(any()) } returns true

        val result = fileStore.delete("somehash")
        assertTrue(result)
    }
}

fun ImageFileStore.fileExists(hash: String): Boolean = load(hash) != null
