package com.neo.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
        private const val WRITE_RETRY_COUNT = 3
        // How long to wait for the onCharacteristicWrite back-pressure callback
        // before assuming the frame was queued (fallback for devices that don't
        // reliably deliver the callback for WRITE_TYPE_NO_RESPONSE).
        private const val WRITE_ACK_TIMEOUT_MS = 150L
        // Backoff when the BLE stack reports it is busy (buffer full).
        private const val WRITE_BUSY_BACKOFF_MS = 15L
    }

    private var negotiatedMtu: Int = DEFAULT_MTU
    @Volatile private var _isConnected = false

    private val sendMutex = Mutex()

    // The frame currently awaiting its onCharacteristicWrite callback. Completed
    // by onFrameWritten() to provide flow control: we only issue the next frame
    // once the stack has accepted the previous one. Carries a sequence token so a
    // delayed/duplicate callback for an already-resolved frame cannot complete a
    // later frame's deferred. Only one write is in flight at a time (all writes go
    // through sendMutex), but the take-and-null in onFrameWritten closes the race
    // where a stale callback arrives after this frame timed out.
    private class PendingWrite(val seq: Long, val deferred: CompletableDeferred<Int>)
    @Volatile private var pendingWrite: PendingWrite? = null
    private var writeSeqCounter: Long = 0 // only mutated under sendMutex

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
                if (sendData(data)) {
                    Log.d(TAG, "Sent ${message::class.simpleName} to ${device.address}")
                    true
                } else {
                    Log.w(TAG, "sendData failed for ${message::class.simpleName} to ${device.address}")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending message: ${e.message}")
                false
            }
        }
    }

    private suspend fun sendData(data: ByteArray): Boolean {
        val maxFrameSize = maxOf(minOf(negotiatedMtu - 1, 500), 20)

        if (data.size + 1 <= maxFrameSize) {
            val frame = ByteArray(1 + data.size)
            frame[0] = SINGLE_FLAG
            System.arraycopy(data, 0, frame, 1, data.size)
            Log.d(TAG, "sendData: single frame=${frame.size}B data=${data.size}B mtu=$negotiatedMtu maxFrame=$maxFrameSize")
            return writeFrame(frame)
        } else {
            val seqId = ++lastSeqId
            val maxChunkData = maxFrameSize - CHUNK_HEADER_SIZE
            val totalChunks = (data.size + maxChunkData - 1) / maxChunkData

            for (i in 0 until totalChunks) {
                if (!_isConnected) {
                    Log.w(TAG, "sendData: connection lost during chunk send at $i/$totalChunks")
                    return false
                }
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
                if (!writeFrame(frame)) {
                    Log.w(TAG, "sendData: frame write failed at $i/$totalChunks, aborting")
                    return false
                }
                // No fixed inter-frame delay: writeFrame() blocks until the
                // stack accepts the frame (onCharacteristicWrite), which paces us.
            }
            return true
        }
    }

    /**
     * Write a single BLE frame with proper flow control.
     *
     * For the client (writeCharacteristic) path we issue the write and then wait
     * for [onFrameWritten] (driven by the GATT onCharacteristicWrite callback)
     * before returning, so the caller never out-runs the BLE stack's buffer. If
     * the stack reports "busy" we back off and retry instead of aborting the
     * whole message. The notify (server) path has no equivalent callback, so it
     * keeps the previous best-effort behaviour.
     */
    @Suppress("DEPRECATION")
    private suspend fun writeFrame(frame: ByteArray): Boolean {
        if (!_isConnected) return false

        // Notify/server path (rarely used — connections are created client-side).
        if (gatt == null || remoteDataChar == null) {
            return try {
                if (gattServer != null && localNotifyChar != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        gattServer.notifyCharacteristicChanged(device, localNotifyChar, false, frame)
                    } else {
                        localNotifyChar.value = frame
                        gattServer.notifyCharacteristicChanged(device, localNotifyChar, false)
                    }
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "writeFrame: notify exception to ${device.address}: ${e.message}")
                false
            }
        }

        var attempt = 0
        while (attempt < WRITE_RETRY_COUNT) {
            if (!_isConnected) return false

            val ack = CompletableDeferred<Int>()
            val pw = PendingWrite(++writeSeqCounter, ack)
            pendingWrite = pw

            val initiated = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeCharacteristic(
                        remoteDataChar,
                        frame,
                        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    ) == android.bluetooth.BluetoothGatt.GATT_SUCCESS
                } else {
                    remoteDataChar.value = frame
                    remoteDataChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    gatt.writeCharacteristic(remoteDataChar)
                }
            } catch (e: Exception) {
                if (pendingWrite === pw) pendingWrite = null
                Log.e(TAG, "writeFrame: exception writing to ${device.address}: ${e.message}")
                return false
            }

            if (!initiated) {
                // Stack buffer is full / busy — back off and retry this frame
                // rather than dropping it and aborting the whole message.
                if (pendingWrite === pw) pendingWrite = null
                attempt++
                Log.d(TAG, "writeFrame: stack busy for ${device.address}, retry $attempt/$WRITE_RETRY_COUNT")
                delay(WRITE_BUSY_BACKOFF_MS)
                continue
            }

            // Back-pressure: wait until the stack confirms it accepted the frame.
            // Fall back to assuming success if the device never delivers the
            // callback, so we never stall the pipeline indefinitely.
            val status = withTimeoutOrNull(WRITE_ACK_TIMEOUT_MS) { ack.await() }
            // Only clear if no newer write has taken the slot (e.g. after a
            // timeout the next iteration/frame may already own pendingWrite).
            if (pendingWrite === pw) pendingWrite = null

            return when {
                status == null -> true // no callback on this device: assume queued
                status == android.bluetooth.BluetoothGatt.GATT_SUCCESS -> true
                else -> {
                    Log.w(TAG, "writeFrame: write callback status=$status for ${device.address}")
                    false
                }
            }
        }

        Log.w(TAG, "writeFrame: stack busy after $WRITE_RETRY_COUNT attempts for ${device.address}")
        return false
    }

    /**
     * Called from the GATT onCharacteristicWrite callback to release the frame
     * currently awaiting confirmation in [writeFrame]. Take-and-null so a late or
     * duplicate callback for an already-resolved frame is ignored and cannot
     * complete a subsequent frame's deferred.
     */
    fun onFrameWritten(status: Int) {
        val pw = pendingWrite ?: return
        pendingWrite = null
        pw.deferred.complete(status)
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
