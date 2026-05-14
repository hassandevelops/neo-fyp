package com.neo.data.repository

import com.neo.data.dao.BlockedUserDao
import com.neo.data.model.BlockedUser
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class BlockedUserRepositoryTest {

    private val blockedUserDao: BlockedUserDao = mockk()

    private lateinit var repository: BlockedUserRepository

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        repository = BlockedUserRepository(blockedUserDao)
    }

    @Test
    fun `blockUser creates and inserts entity`() = runTest {
        coEvery { blockedUserDao.insert(any()) } just Runs

        repository.blockUser("user-1", "Spam")

        val slot = slot<BlockedUser>()
        coVerify { blockedUserDao.insert(capture(slot)) }
        assertEquals("user-1", slot.captured.blockedUserId)
        assertEquals("Spam", slot.captured.reason)
        assertTrue(slot.captured.blockedAt > 0)
    }

    @Test
    fun `unblockUser delegates`() = runTest {
        coEvery { blockedUserDao.delete("user-1") } just Runs

        repository.unblockUser("user-1")

        coVerify { blockedUserDao.delete("user-1") }
    }

    @Test
    fun `isBlocked delegates`() = runTest {
        coEvery { blockedUserDao.isBlocked("user-1") } returns true

        val result = repository.isBlocked("user-1")

        assertTrue(result)
    }

    @Test
    fun `getAllBlocked returns flow`() {
        val flow = flowOf(emptyList<BlockedUser>())
        coEvery { blockedUserDao.getAllBlocked() } returns flow

        val result = repository.getAllBlocked()

        assertNotNull(result)
    }
}
