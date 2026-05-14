package com.neo.media

import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ImageCompressorAdapterTest {

    private val context: android.content.Context = mockk(relaxed = true)

    private lateinit var adapter: ImageCompressorAdapter

    @Before
    fun setup() {
        adapter = ImageCompressorAdapter(context)
    }

    @Test
    fun `compress handles invalid URI gracefully`() = runBlocking {
        val result = adapter.compress("not-a-valid-uri://")
        assertNull(result)
    }

    @Test
    fun `compress handles null input`() = runBlocking {
        val result = adapter.compress("")
        assertNull(result)
    }
}
