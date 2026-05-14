package com.neo.sync

import kotlinx.coroutines.test.TestScope
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SeenMessageCacheTest {

    private val scope = TestScope()
    private lateinit var cache: SeenMessageCache

    @Before
    fun setup() {
        cache = SeenMessageCache(scope)
    }

    @Test
    fun `first time message returns true`() {
        assertTrue(cache.checkAndAdd("msg-1"))
    }

    @Test
    fun `duplicate message returns false`() {
        cache.checkAndAdd("msg-1")
        assertFalse(cache.checkAndAdd("msg-1"))
    }

    @Test
    fun `different messages are unique`() {
        cache.checkAndAdd("msg-1")
        assertTrue(cache.checkAndAdd("msg-2"))
    }

    @Test
    fun `contains returns true for seen messages`() {
        cache.checkAndAdd("msg-1")
        assertTrue(cache.contains("msg-1"))
    }

    @Test
    fun `contains returns false for unseen messages`() {
        assertFalse(cache.contains("msg-unknown"))
    }

    @Test
    fun `size tracks unique messages`() {
        cache.checkAndAdd("msg-1")
        cache.checkAndAdd("msg-2")
        assertEquals(2, cache.size())
    }

    @Test
    fun `clear removes all entries`() {
        cache.checkAndAdd("msg-1")
        cache.checkAndAdd("msg-2")
        cache.clear()
        assertEquals(0, cache.size())
        assertFalse(cache.contains("msg-1"))
    }

    @Test
    fun `checkAndAdd is idempotent for same message`() {
        assertTrue(cache.checkAndAdd("msg-1"))
        assertFalse(cache.checkAndAdd("msg-1"))
        assertFalse(cache.checkAndAdd("msg-1"))
        assertEquals(1, cache.size())
    }

    @Test
    fun `add manually marks message as seen`() {
        cache.add("msg-1")
        assertTrue(cache.contains("msg-1"))
    }
}