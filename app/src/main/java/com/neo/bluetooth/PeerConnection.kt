package com.neo.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Represents a connection to a single peer device.
 * Handles bidirectional message exchange.
 */
class PeerConnection(
    private val device: BluetoothDevice,
    private val socket: BluetoothSocket,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "PeerConnection"
        private const val MESSAGE_DELIMITER = "\n"
    }
    
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var isConnected = false
    
    private val _receivedMessages = MutableSharedFlow<Message>(replay = 0)
    val receivedMessages: SharedFlow<Message> = _receivedMessages
    
    private val _connectionState = MutableSharedFlow<ConnectionState>(replay = 1)
    val connectionState: SharedFlow<ConnectionState> = _connectionState
    
    sealed class ConnectionState {
        object Connected : ConnectionState()
        object Disconnected : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }
    
    /**
     * Start the connection and begin listening for messages.
     */
    fun start() {
        scope.launch(Dispatchers.IO) {
            try {
                if (!socket.isConnected) {
                    socket.connect()
                }
                
                inputStream = socket.inputStream
                outputStream = socket.outputStream
                isConnected = true
                
                _connectionState.emit(ConnectionState.Connected)
                Log.d(TAG, "Connected to ${device.name ?: device.address}")
                
                // Start listening for incoming messages
                listenForMessages()
                
            } catch (e: IOException) {
                Log.e(TAG, "Connection failed: ${e.message}")
                _connectionState.emit(ConnectionState.Error(e.message ?: "Connection failed"))
                disconnect()
            }
        }
    }
    
    /**
     * Listen for incoming messages from the peer.
     */
    private suspend fun listenForMessages() {
        val buffer = ByteArray(1024)
        var messageBuffer = StringBuilder()
        
        try {
            while (isConnected && inputStream != null) {
                val bytesRead = inputStream!!.read(buffer)
                if (bytesRead == -1) {
                    // End of stream
                    break
                }
                
                val data = String(buffer, 0, bytesRead)
                messageBuffer.append(data)
                
                // Process complete messages (delimited by newline)
                var delimiterIndex = messageBuffer.indexOf(MESSAGE_DELIMITER)
                while (delimiterIndex != -1) {
                    val messageJson = messageBuffer.substring(0, delimiterIndex)
                    messageBuffer.delete(0, delimiterIndex + MESSAGE_DELIMITER.length)
                    
                    // Deserialize and emit message
                    val message = MessageProtocol.deserialize(messageJson)
                    if (message != null) {
                        _receivedMessages.emit(message)
                        Log.d(TAG, "Received message: ${message::class.simpleName}")
                    } else {
                        Log.w(TAG, "Failed to deserialize message: $messageJson")
                    }
                    
                    delimiterIndex = messageBuffer.indexOf(MESSAGE_DELIMITER)
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error reading from stream: ${e.message}")
        } finally {
            disconnect()
        }
    }
    
    /**
     * Send a message to the peer.
     */
    suspend fun sendMessage(message: Message): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (!isConnected || outputStream == null) {
                    Log.w(TAG, "Cannot send message: not connected")
                    return@withContext false
                }
                
                val json = MessageProtocol.serialize(message)
                val data = (json + MESSAGE_DELIMITER).toByteArray()
                
                outputStream!!.write(data)
                outputStream!!.flush()
                
                Log.d(TAG, "Sent message: ${message::class.simpleName}")
                true
            } catch (e: IOException) {
                Log.e(TAG, "Error sending message: ${e.message}")
                disconnect()
                false
            }
        }
    }
    
    /**
     * Disconnect from the peer.
     */
    fun disconnect() {
        if (!isConnected) return
        
        isConnected = false
        
        try {
            inputStream?.close()
            outputStream?.close()
            socket.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing connection: ${e.message}")
        }
        
        scope.launch {
            _connectionState.emit(ConnectionState.Disconnected)
        }
        
        Log.d(TAG, "Disconnected from ${device.name ?: device.address}")
    }
    
    /**
     * Get the remote device address.
     */
    fun getDeviceAddress(): String = device.address
    
    /**
     * Get the remote device name.
     */
    fun getDeviceName(): String? = device.name
}
