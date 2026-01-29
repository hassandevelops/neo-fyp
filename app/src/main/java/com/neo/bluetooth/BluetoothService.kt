package com.neo.bluetooth

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.neo.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

/**
 * Foreground service for managing Bluetooth connections and peer discovery.
 * Runs continuously to maintain the mesh network.
 */
@AndroidEntryPoint
class BluetoothService : Service() {
    
    companion object {
        private const val TAG = "BluetoothService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "neo_bluetooth_channel"
        
        // UUID for Neo app's Bluetooth service
        private val SERVICE_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val SERVICE_NAME = "NeoBluetoothService"
    }
    
    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var serverSocket: BluetoothServerSocket? = null
    private var isRunning = false
    
    // Active peer connections
    private val connections = mutableMapOf<String, PeerConnection>()
    
    private val _connectedPeers = MutableStateFlow<List<String>>(emptyList())
    val connectedPeers: StateFlow<List<String>> = _connectedPeers
    
    // Callback for received messages
    var onMessageReceived: ((String, Message) -> Unit)? = null
    
    inner class LocalBinder : Binder() {
        fun getService(): BluetoothService = this@BluetoothService
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        
        if (!isRunning) {
            startBluetoothOperations()
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        stopBluetoothOperations()
        serviceScope.cancel()
    }
    
    /**
     * Start Bluetooth server and discovery.
     */
    private fun startBluetoothOperations() {
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Log.w(TAG, "Bluetooth not available or disabled")
            return
        }
        
        isRunning = true
        
        // Start server to accept incoming connections
        serviceScope.launch {
            startBluetoothServer()
        }
        
        // Start discovering nearby devices
        serviceScope.launch {
            startDeviceDiscovery()
        }
    }
    
    /**
     * Stop all Bluetooth operations.
     */
    private fun stopBluetoothOperations() {
        isRunning = false
        
        // Close all connections
        connections.values.forEach { it.disconnect() }
        connections.clear()
        
        // Close server socket
        try {
            serverSocket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing server socket: ${e.message}")
        }
        
        updateConnectedPeers()
    }
    
    /**
     * Start Bluetooth server to accept incoming connections.
     */
    private suspend fun startBluetoothServer() {
        withContext(Dispatchers.IO) {
            try {
                if (ActivityCompat.checkSelfPermission(
                        this@BluetoothService,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Log.w(TAG, "Missing BLUETOOTH_CONNECT permission")
                    return@withContext
                }
                
                serverSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord(
                    SERVICE_NAME,
                    SERVICE_UUID
                )
                
                Log.d(TAG, "Bluetooth server started")
                
                while (isRunning) {
                    try {
                        val socket = serverSocket?.accept()
                        if (socket != null) {
                            handleIncomingConnection(socket)
                        }
                    } catch (e: IOException) {
                        if (isRunning) {
                            Log.e(TAG, "Error accepting connection: ${e.message}")
                        }
                        break
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error starting server: ${e.message}")
            }
        }
    }
    
    /**
     * Handle an incoming Bluetooth connection.
     */
    private fun handleIncomingConnection(socket: BluetoothSocket) {
        val device = socket.remoteDevice
        val address = device.address
        
        if (connections.containsKey(address)) {
            Log.d(TAG, "Already connected to $address")
            return
        }
        
        Log.d(TAG, "Incoming connection from ${device.name ?: address}")
        
        val connection = PeerConnection(device, socket, serviceScope)
        connections[address] = connection
        
        // Listen for messages from this peer
        serviceScope.launch {
            connection.receivedMessages.collect { message ->
                onMessageReceived?.invoke(address, message)
            }
        }
        
        // Monitor connection state
        serviceScope.launch {
            connection.connectionState.collect { state ->
                when (state) {
                    is PeerConnection.ConnectionState.Disconnected -> {
                        connections.remove(address)
                        updateConnectedPeers()
                    }
                    is PeerConnection.ConnectionState.Connected -> {
                        updateConnectedPeers()
                    }
                    else -> {}
                }
            }
        }
        
        connection.start()
    }
    
    /**
     * Discover nearby Bluetooth devices and attempt connections.
     */
    private suspend fun startDeviceDiscovery() {
        withContext(Dispatchers.IO) {
            while (isRunning) {
                try {
                    if (ActivityCompat.checkSelfPermission(
                            this@BluetoothService,
                            Manifest.permission.BLUETOOTH_SCAN
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        Log.w(TAG, "Missing BLUETOOTH_SCAN permission")
                        delay(10000)
                        continue
                    }
                    
                    // Get bonded (paired) devices
                    val pairedDevices = bluetoothAdapter?.bondedDevices ?: emptySet()
                    
                    for (device in pairedDevices) {
                        if (!isRunning) break
                        
                        val address = device.address
                        if (!connections.containsKey(address)) {
                            // Attempt to connect
                            connectToDevice(device)
                        }
                    }
                    
                    // Wait before next discovery cycle
                    delay(30000) // 30 seconds
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error in device discovery: ${e.message}")
                    delay(10000)
                }
            }
        }
    }
    
    /**
     * Connect to a specific Bluetooth device.
     */
    private suspend fun connectToDevice(device: BluetoothDevice) {
        withContext(Dispatchers.IO) {
            try {
                if (ActivityCompat.checkSelfPermission(
                        this@BluetoothService,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return@withContext
                }
                
                Log.d(TAG, "Attempting to connect to ${device.name ?: device.address}")
                
                val socket = device.createRfcommSocketToServiceRecord(SERVICE_UUID)
                socket.connect() // Actually connect the socket!
                handleIncomingConnection(socket)
                
            } catch (e: IOException) {
                Log.e(TAG, "Failed to connect to ${device.address}: ${e.message}")
            }
        }
    }
    
    /**
     * Send a message to a specific peer.
     */
    suspend fun sendMessage(peerAddress: String, message: Message): Boolean {
        val connection = connections[peerAddress]
        if (connection == null) {
            Log.w(TAG, "No connection to $peerAddress")
            return false
        }
        
        return connection.sendMessage(message)
    }
    
    /**
     * Broadcast a message to all connected peers.
     */
    suspend fun broadcastMessage(message: Message, excludePeer: String? = null) {
        connections.forEach { (address, connection) ->
            if (address != excludePeer) {
                connection.sendMessage(message)
            }
        }
    }
    
    /**
     * Get list of connected peer addresses.
     */
    fun getConnectedPeerAddresses(): List<String> {
        return connections.keys.toList()
    }
    
    /**
     * Update the connected peers state.
     */
    private fun updateConnectedPeers() {
        _connectedPeers.value = connections.keys.toList()
        updateNotification()
    }
    
    /**
     * Create notification channel for Android O+.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Neo Bluetooth Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Neo running in the background to sync with nearby devices"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Create foreground service notification.
     */
    private fun createNotification(): Notification {
        val peerCount = connections.size
        val contentText = if (peerCount > 0) {
            getString(R.string.peers_connected, peerCount)
        } else {
            getString(R.string.bluetooth_service_description)
        }
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.bluetooth_service_title))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
    
    /**
     * Update the notification with current peer count.
     */
    private fun updateNotification() {
        val notification = createNotification()
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
