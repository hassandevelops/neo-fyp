package com.neo.data.repository

import com.neo.data.dao.DeviceDao
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DeviceRepositoryTest {

    private val deviceDao: DeviceDao = mockk()

    private lateinit var repository: DeviceRepository

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        repository = DeviceRepository(deviceDao)
    }

    @Test
    fun `getAllDevices delegates`() {
        coEvery { deviceDao.getAllDevices() } returns flowOf(emptyList())

        val result = repository.getAllDevices()

        assertNotNull(result)
    }

    @Test
    fun `insertDevice delegates`() = runTest {
        val device = mockk<com.neo.data.model.Device>(relaxed = true)
        coEvery { deviceDao.insert(device) } just Runs

        repository.insertDevice(device)

        coVerify { deviceDao.insert(device) }
    }

    @Test
    fun `getDeviceById delegates`() = runTest {
        coEvery { deviceDao.getDeviceById("device-1") } returns null

        val result = repository.getDeviceById("device-1")

        assertNull(result)
    }

    @Test
    fun `updateLastSeen delegates`() = runTest {
        coEvery { deviceDao.updateLastSeen("device-1", 1000L) } just Runs

        repository.updateLastSeen("device-1", 1000L)

        coVerify { deviceDao.updateLastSeen("device-1", 1000L) }
    }
}
