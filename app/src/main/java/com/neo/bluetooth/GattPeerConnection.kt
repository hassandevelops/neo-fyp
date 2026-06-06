package com.neo.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Represents a bidirectional peer connection over BLE GATT.
 *
 * Two modes:
 *  - CLIENT:  we initiated the GATT connection; send via writeCharacteristic, receive via onCharacteristicChanged
 *  - SERVER:  peer initiated; send via gattServer.notifyCharacteristicChanged, receive via onCharacteristicWriteRequest
 *
 * Both modes present the same [receivedMessages] / [sendMessage] interface to callers.
 */
class GattPeerConnection(
    val device: BluetoothDevice,
    private val gatt: BluetoothGatt?,
    private val remoteDataChar: BluetoothGattCharacteristic?,
    private val gattServer: BluetoothGattServer?,
    private val localNotifyChar: BluetoothGattCharacteristic?,
    private val scope: CoroutineScope,
    private val onDisconnect: () -> Unit
) {
    companion object {
        private const val TAG = "GattPeerConnection"
        private const val CHUNKED_FLAG: Byte = 0x01
        private const val SINGLE_FLAG: Byte = 0x00
        private const val MAX_MESSAGE_BYTES = 4 * 1024 * 1024
    private const val CHUNK_HEADER_SIZE = 10
    private const val DEFAULT_MTU = 23
    private const val FRAME_DELAY_MS = 100L
    }

    private var negotiatedMtu: Int = DEFAULT_MTU
    @Volatile private var _isConnected = false

    private val sendMutex = Mutex()

    private var lastSeqId: Long = 0
    private val pendingAssemblies = ConcurrentHashMap<Long, PendingMessage>()
    private var lastCleanupTime = System.currentTimeMillis()
    private val cleanupIntervalMs = 30_000L
    private val maxPendingAssemblies = 4

    private data class PendingMessage(
        val seqId: Long,
        val totalChunks: Int,
        val chunks: MutableList<ByteArray?>,
        var receivedCount: Int,
        val startedAt: Long = System.currentTimeMillis()
    )

    private val _receivedMessages = MutableSharedFlow<Message>(replay = 0, extraBufferCapacity = 64)
    val receivedMessages: SharedFlow<Message> = _receivedMessages

    private val _connectionState = MutableSharedFlow<ConnectionState>(replay = 1)
    val connectionState: SharedFlow<ConnectionState> = _connectionState

    sealed class ConnectionState {
        object Connected : ConnectionState()
        object Disconnected : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    fun start() {
        _isConnected = true
        scope.launch {
            _connectionState.emit(ConnectionState.Connected)
        }
        Log.d(TAG, "Started GATT peer connection for ${device.address}")
    }

    suspend fun sendMessage(message: Message): Boolean {
        if (!_isConnected) {
            Log.w(TAG, "Cannot send: not connected to ${device.address}")
            return false
        }
        return sendMutex.withLock {
            try {
                val json = MessageProtocol.serialize(message)
                val data = json.toByteArray(Charsets.UTF_8)
                sendData(data)
                Log.d(TAG, "Sent ${message::class.simpleName} to ${device.address}")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error sending message: ${e.message}")
                false
            }
        }
    }

    private suspend fun sendData(data: ByteArray) {
        val maxFrameSize = maxOf(minOf(negotiatedMtu - 1, 500), 20)

        if (data.size + 1 <= maxFrameSize) {
            val frame = ByteArray(1 + data.size)
            frame[0] = SINGLE_FLAG
            System.arraycopy(data, 0, frame, 1, data.size)
            Log.d(TAG, "sendData: single frame=${frame.size}B data=${data.size}B mtu=$negotiatedMtu maxFrame=$maxFrameSize")
            writeFrame(frame)
        } else {
            val seqId = ++lastSeqId
            val maxChunkData = maxFrameSize - CHUNK_HEADER_SIZE
            val totalChunks = (data.size + maxChunkData - 1) / maxChunkData

            for (i in 0 until totalChunks) {
                val offset = i * maxChunkData
                val chunkSize = minOf(maxChunkData, data.size - offset)
                val frame = ByteArray(CHUNK_HEADER_SIZE + chunkSize)

                frame[0] = CHUNKED_FLAG
                frame[1] = (seqId.toInt() and 0xFF).toByte()
                frame[2] = ((seqId.toInt() shr 8) and 0xFF).toByte()
                frame[3] = ((seqId.toInt() shr 16) and 0xFF).toByte()
                frame[4] = ((seqId.toInt() shr 24) and 0xFF).toByte()
                frame[5] = (totalChunks and 0xFF).toByte()
                frame[6] = ((totalChunks shr 8) and 0xFF).toByte()
                frame[7] = (i and 0xFF).toByte()
                frame[8] = ((i shr 8) and 0xFF).toByte()
                System.arraycopy(data, offset, frame, CHUNK_HEADER_SIZE, chunkSize)
                Log.d(TAG, "sendData: chunk[$i/$totalChunks] seq=$seqId frame=${frame.size}B chunkData=$chunkSize mtu=$negotiatedMtu maxFrame=$maxFrameSize")
                writeFrame(frame)
                if (i < totalChunks - 1) {
                    delay(FRAME_DELAY_MS)
                }
            }
        }
    }

    private fun writeFrame(frame: ByteArray) {
        if (gatt != null && remoteDataChar != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(remoteDataChar, frame, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
            } else {
                @Suppress("DEPRECATION")
                remoteDataChar.value = frame
                @Suppress("DEPRECATION")
                remoteDataChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(remoteDataChar)
            }
        } else if (gattServer != null && localNotifyChar != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gattServer.notifyCharacteristicChanged(device, localNotifyChar, false, frame)
            } else {
                @Suppress("DEPRECATION")
                localNotifyChar.value = frame
                @Suppress("DEPRECATION")
                gattServer.notifyCharacteristicChanged(device, localNotifyChar, false)
            }
        }
    }

    fun onDataReceived(data: ByteArray) {
        if (data.isEmpty()) return

        val flag = data[0]
        when {
            flag == SINGLE_FLAG -> {
                val jsonBytes = data.copyOfRange(1, data.size)
                if (jsonBytes.size > MAX_MESSAGE_BYTES) {
                    Log.w(TAG, "Oversized message (${jsonBytes.size} B) from ${device.address}")
                    return
                }
                val json = String(jsonBytes, Charsets.UTF_8)
                val message = MessageProtocol.deserialize(json)
                if (message != null) {
                    scope.launch { _receivedMessages.emit(message) }
                } else {
                    Log.w(TAG, "Deserialize failed, raw starts: ${json.take(80)}")
                }
            }

            flag == CHUNKED_FLAG && data.size >= CHUNK_HEADER_SIZE -> {
                val seqId = (data[1].toInt() and 0xFF).toLong() or
                    ((data[2].toInt() and 0xFF).toLong() shl 8) or
                    ((data[3].toInt() and 0xFF).toLong() shl 16) or
                    ((data[4].toInt() and 0xFF).toLong() shl 24)
                val totalChunks = (data[5].toInt() and 0xFF) or ((data[6].toInt() and 0xFF) shl 8)
                val chunkIndex = (data[7].toInt() and 0xFF) or ((data[8].toInt() and 0xFF) shl 8)
                val chunkData = data.copyOfRange(CHUNK_HEADER_SIZE, data.size)

                cleanupStaleAssemblies()

                val pm = pendingAssemblies.getOrPut(seqId) {
                    if (pendingAssemblies.size >= maxPendingAssemblies) {
                        val oldest = pendingAssemblies.keys.minOrNull()
                        if (oldest != null) pendingAssemblies.remove(oldest)
                    }
                    PendingMessage(seqId, totalChunks, MutableList(totalChunks) { null }, 0)
                }

                if (pm.chunks[chunkIndex] == null) {
                    pm.chunks[chunkIndex] = chunkData
                    pm.receivedCount++
                }

                if (pm.receivedCount == totalChunks) {
                    pendingAssemblies.remove(seqId)
                    reassembleMessage(pm)
                }
            }

            else -> Log.w(TAG, "Unknown frame flag ${flag} from ${device.address}")
        }
    }

    private fun reassembleMessage(pm: PendingMessage) {
        val totalSize = pm.chunks.sumOf { it?.size ?: 0 }
        if (totalSize > MAX_MESSAGE_BYTES) {
            Log.w(TAG, "Reassembled message too large ($totalSize B), dropping")
            return
        }
        val fullData = ByteArray(totalSize)
        var pos = 0
        for (chunk in pm.chunks) {
            if (chunk != null) {
                System.arraycopy(chunk, 0, fullData, pos, chunk.size)
                pos += chunk.size
            }
        }

        val json = String(fullData, Charsets.UTF_8)
        val message = MessageProtocol.deserialize(json)
        if (message != null) {
            scope.launch { _receivedMessages.emit(message) }
        } else {
            Log.w(TAG, "Reassembled message deserialize failed, raw starts: ${json.take(80)}")
        }
    }

    private fun cleanupStaleAssemblies() {
        val now = System.currentTimeMillis()
        if (now - lastCleanupTime < cleanupIntervalMs) return
        lastCleanupTime = now
        val staleThreshold = now - 60_000L
        val stale = pendingAssemblies.entries
            .filter { it.value.startedAt < staleThreshold }
            .map { it.key }
        stale.forEach { seqId ->
            Log.w(TAG, "Sweeping stale assembly seq=$seqId for ${device.address}")
            pendingAssemblies.remove(seqId)
        }
    }

    fun onMtuChanged(mtu: Int) {
        negotiatedMtu = mtu
        Log.d(TAG, "MTU updated to $mtu for ${device.address}")
    }

    fun disconnect() {
        if (!_isConnected) return
        _isConnected = false

        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing GATT: ${e.message}")
        }

        scope.launch {
            _connectionState.emit(ConnectionState.Disconnected)
        }
        onDisconnect()
        Log.d(TAG, "Disconnected from ${device.address}")
    }

    fun getDeviceAddress(): String = device.address
}
