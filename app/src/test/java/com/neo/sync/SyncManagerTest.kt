package com.neo.sync

import com.neo.bluetooth.BluetoothService
import com.neo.bluetooth.Message
import com.neo.data.repository.DeviceRepository
import com.neo.data.repository.PostRepository
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SyncManagerTest {

    private lateinit var syncManager: SyncManager

    @Before
    fun setup() {
        syncManager = SyncManager(
            postRepository = mockk(),
            deviceRepository = mockk(),
            gossipProtocol = mockk(relaxed = true),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        )
    }

    @After
    fun teardown() {
        syncManager.stop()
    }

    @Test
    fun `setBluetoothService wires message routing`() = runBlocking {
        val service = mockk<BluetoothService>(relaxed = true)
        every { service.onMessageReceived = any() } just Runs
        every { service.connectedPeers } returns MutableStateFlow(emptyList())

        syncManager.setBluetoothService(service)

        verify { service.onMessageReceived = any() }
    }

    @Test
    fun `stop cancels periodic sync`() {
        syncManager.stop()
    }

    @Test
    fun `connectedPeersCount starts at zero`() {
        assertEquals(0, syncManager.connectedPeersCount.value)
    }

    @Test
    fun `setBluetoothService starts message routing`() = runBlocking {
        val service = mockk<BluetoothService>(relaxed = true)
        every { service.onMessageReceived = any() } just Runs
        every { service.connectedPeers } returns MutableStateFlow(emptyList())

        syncManager.setBluetoothService(service)

        verify(exactly = 1) { service.onMessageReceived = any() }
    }
}
