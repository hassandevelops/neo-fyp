package com.neo.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.neo.R
import com.neo.data.repository.NotificationRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.IOException
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * Foreground service for managing Bluetooth connections and peer discovery.
 * Uses BLE GATT for data transport (no RFCOMM/BR-EDR).
 */
@AndroidEntryPoint
class BluetoothService : Service() {

    @Inject
    lateinit var notificationRepository: NotificationRepository

    companion object {
        private const val TAG = "BluetoothService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "neo_bluetooth_channel"

        private val SERVICE_UUID: UUID = UUID.fromString("a1b2c3d4-e5f6-7890-1234-56789abcdef0")
        private const val SERVICE_NAME = "NeoBluetoothService"

        // GATT characteristic UUIDs (under the same service UUID)
        private val GATT_DATA_CHAR_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-1234-56789abcdef1")
        private val GATT_NOTIFY_CHAR_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-1234-56789abcdef2")
        private val CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val btStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            if (state == BluetoothAdapter.STATE_ON) {
                Log.d(TAG, "Bluetooth turned ON — starting operations")
                startBluetoothOperations()
            }
        }
    }

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null

    // GATT server (always running — handles incoming connections from lower-identity peers)
    private var bluetoothGattServer: BluetoothGattServer? = null

    private var isRunning = false

    // BLE state
    private var isAdvertising = false
    private var isScanning = false

    // EMUI/MIUI returns fake MAC (02:00:00:00:00:00), so we embed a persistent
    // random identity in BLE advertisements for role arbitration instead.
    private val deviceIdentity: String by lazy {
        val prefs = getSharedPreferences("neo_identity", Context.MODE_PRIVATE)
        var id = prefs.getString("device_id", null)
        if (id == null) {
            id = UUID.randomUUID().toString().take(8)
            prefs.edit().putString("device_id", id).apply()
        }
        id
    }

    // Active GATT peer connections — thread-safe
    private val connections = ConcurrentHashMap<String, GattPeerConnection>()
    private val pendingConnections = ConcurrentHashMap.newKeySet<String>()
    private val reconnectQueue = ConcurrentHashMap<String, Job>()

    // Peers we intentionally disconnected (skip auto-reconnect)
    private val intentionallyDisconnected = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    // Peers with recent GATT failures — skip reconnection for 60 seconds
    private val failedConnections = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    // Buffer incoming data for peers whose client-side connection is still being set up
    private val pendingIncomingData = ConcurrentHashMap<String, MutableList<ByteArray>>()

    private val _connectedPeers = MutableStateFlow<List<String>>(emptyList())
    val connectedPeers: StateFlow<List<String>> = _connectedPeers

    // Flow of all incoming messages from all connected peers
    private val _allIncomingMessages = MutableSharedFlow<Pair<String, Message>>(
        replay = 0,
        extraBufferCapacity = 64
    )
    val allIncomingMessages: Flow<Pair<String, Message>> = _allIncomingMessages.asSharedFlow()

    inner class LocalBinder : Binder() {
        fun getService(): BluetoothService = this@BluetoothService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        registerReceiver(btStateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))

        createNotificationChannel()
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")

        val adapter = bluetoothAdapter
        if (adapter != null) {
            Log.d(TAG, "Adapter address=${adapter.address}, name=${adapter.name}, state=${adapter.state}, scanMode=${adapter.scanMode}")
        }

        val notification = createNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed: ${e.message}", e)
            stopSelf()
            return START_NOT_STICKY
        }

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
        try { unregisterReceiver(btStateReceiver) } catch (_: Exception) {}
        stopBluetoothOperations()
        serviceScope.cancel()
    }

    /**
     * Start Bluetooth operations: BLE advertising + scanning + GATT server.
     */
    private fun startBluetoothOperations() {
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Log.w(TAG, "Bluetooth not available or disabled")
            return
        }

        isRunning = true

        // Start BLE advertising
        startBleAdvertising()

        // Start BLE scanning
        startBleScanning()

        // Start GATT server to accept incoming connections
        startGattServer()

        // Scan watchdog — restart BLE scan if Android kills it
        startScanWatchdog()

        // Advertising watchdog — restart BLE advertising if Android kills it
        startAdvertisingWatchdog()
    }

    // ------------------------------------------------------------------
    // BLE advertising
    // ------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    private fun startBleAdvertising() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            bluetoothLeAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
            if (bluetoothLeAdvertiser == null) {
                Log.w(TAG, "BLE advertiser not available")
                return
            }

            val advertiseSettings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setConnectable(true)
                .build()

            val advertiseData = AdvertiseData.Builder()
                .addServiceUuid(ParcelUuid(SERVICE_UUID))
                .build()

            val scanResponse = AdvertiseData.Builder()
                .addManufacturerData(0xFFFF, deviceIdentity.toByteArray(Charsets.UTF_8))
                .build()

            try {
                bluetoothLeAdvertiser?.startAdvertising(
                    advertiseSettings,
                    advertiseData,
                    scanResponse,
                    advertiseCallback
                )
                isAdvertising = true
                Log.d(TAG, "Started BLE advertising, identity=$deviceIdentity")
            } catch (e: SecurityException) {
                Log.e(TAG, "BLE advertising failed: permission denied (${e.message})")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start BLE advertising, trying without scan response", e)
                try {
                    bluetoothLeAdvertiser?.startAdvertising(
                        advertiseSettings,
                        advertiseData,
                        advertiseCallback
                    )
                    isAdvertising = true
                    Log.d(TAG, "Started BLE advertising (no scan response)")
                } catch (e2: Exception) {
                    Log.e(TAG, "Failed to start BLE advertising (fallback)", e2)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopBleAdvertising() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && isAdvertising) {
            try {
                bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping BLE advertising: ${e.message}")
            }
            isAdvertising = false
            Log.d(TAG, "Stopped BLE advertising")
        }
    }

    // ------------------------------------------------------------------
    // BLE scanning
    // ------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    private fun startBleScanning() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "Missing BLUETOOTH_SCAN permission")
                    return
                }
            }
            bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
            if (bluetoothLeScanner == null) {
                Log.w(TAG, "BLE scanner not available")
                return
            }

            val scanFilter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(SERVICE_UUID))
                .build()

            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                .build()

            try {
                bluetoothLeScanner?.startScan(listOf(scanFilter), scanSettings, scanCallback)
                isScanning = true
                Log.d(TAG, "Started BLE scanning")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start BLE scanning", e)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopBleScanning() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && isScanning) {
            bluetoothLeScanner?.stopScan(scanCallback)
            isScanning = false
            Log.d(TAG, "Stopped BLE scanning")
        }
    }

    /**
     * BLE Advertise callback.
     */
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settings: AdvertiseSettings?) {
            Log.d(TAG, "BLE advertising started successfully")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "BLE advertising failed with error code: $errorCode")
        }
    }

    /**
     * BLE Scan callback for discovered devices.
     */
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                val address = device.address
                if (connections.containsKey(address)) return@let
                if (pendingConnections.contains(address)) return@let
                if (address == bluetoothAdapter?.address) return@let
                if (failedConnections.contains(address)) return@let

                // Extract peer device identity from BLE manufacturer data.
                val peerId = result.scanRecord
                    ?.getManufacturerSpecificData(0xFFFF)
                    ?.toString(Charsets.UTF_8)

                // Role arbitration: strictly lower identity waits for inbound;
                // higher identity initiates. Using < (not <=) prevents deadlock
                // when both devices have the same identity string.
                if (peerId != null && peerId < deviceIdentity) {
                    Log.d(TAG, "Discovered $address (peer has lower id=$peerId than our $deviceIdentity), waiting for inbound")
                    return@let
                }

                Log.d(TAG, "Discovered BLE device: ${device.name ?: address}")
                pendingConnections.add(address)
                serviceScope.launch {
                    connectToDevice(device)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed: $errorCode, restarting in 5s")
            isScanning = false
            serviceScope.launch {
                delay(5000)
                if (isRunning) startBleScanning()
            }
        }
    }

    // ------------------------------------------------------------------
    // Watchdogs
    // ------------------------------------------------------------------

    private fun startScanWatchdog() {
        serviceScope.launch {
            while (isRunning) {
                delay(45_000L)
                if (isRunning && !isScanning) {
                    Log.w(TAG, "Scan watchdog: BLE scan inactive, restarting")
                    startBleScanning()
                }
            }
        }
    }

    private fun startAdvertisingWatchdog() {
        serviceScope.launch {
            while (isRunning) {
                delay(45_000L)
                if (isRunning && !isAdvertising) {
                    Log.w(TAG, "Advertising watchdog: BLE advertising inactive, restarting")
                    startBleAdvertising()
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // GATT server — receives incoming connections + data from lower-identity peers
    // ------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    private fun startGattServer() {
        try {
            val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
            val server = bluetoothManager.openGattServer(this, gattServerCallback)
            if (server == null) {
                Log.e(TAG, "Failed to open GATT server (null)")
                return
            }

            val gattService = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

            val dataChar = BluetoothGattCharacteristic(
                GATT_DATA_CHAR_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            )
            val notifyChar = BluetoothGattCharacteristic(
                GATT_NOTIFY_CHAR_UUID,
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ
            )

            gattService.addCharacteristic(dataChar)
            gattService.addCharacteristic(notifyChar)
            server.addService(gattService)

            bluetoothGattServer = server
            Log.d(TAG, "GATT server started with service $SERVICE_UUID")
        } catch (e: SecurityException) {
            Log.e(TAG, "GATT server start failed: permission denied", e)
        } catch (e: Exception) {
            Log.e(TAG, "GATT server start failed", e)
        }
    }

    /**
     * Look up a GattPeerConnection by remote device address.
     * If none exists yet, dispatch incoming data via a pass-through so it buffers
     * until the client-side connection is ready.
     */
    private fun findConnection(address: String): GattPeerConnection? = connections[address]

    /**
     * When another device connects to our GATT server, we also initiate a GATT
     * client connection back. This way both sides use writeCharacteristic for
     * sending (no notification timing issues), and server-side only receives.
     */
    private fun onServerPeerConnected(device: BluetoothDevice) {
        val address = device.address
        if (connections.containsKey(address)) return
        if (pendingConnections.contains(address)) return
        if (failedConnections.contains(address)) return

        Log.d(TAG, "Server peer connected ${device.address}, initiating client connection back")
        pendingConnections.add(address)
        serviceScope.launch {
            connectToDevice(device)
        }
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "GATT server connection state change error: status=$status for ${device.address}")
                return
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "GATT server: device CONNECTED ${device.address}")
                // Initiate a GATT client connection back so both sides can use
                // writeCharacteristic for sending (avoids notification timing issues)
                onServerPeerConnected(device)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "GATT server: device DISCONNECTED ${device.address}")
                intentionallyDisconnected.remove(device.address)
                pendingIncomingData.remove(device.address)
                connections.remove(device.address)?.let { conn ->
                    updateConnectedPeers()
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (characteristic.uuid == GATT_DATA_CHAR_UUID) {
                val address = device.address
                val conn = findConnection(address)
                if (conn != null) {
                    conn.onDataReceived(value)
                } else {
                    // Buffer until client-side connection is ready
                    pendingIncomingData.getOrPut(address) { mutableListOf() }.add(value)
                }
                if (responseNeeded) {
                    bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // GATT client — connect to a discovered peer (higher identity initiates)
    // ------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    private suspend fun connectToDevice(device: BluetoothDevice) {
        val address = device.address

        withContext(Dispatchers.Main) {
            if (!isRunning) return@withContext

            val gatt = device.connectGatt(
                this@BluetoothService, false, createGattCallback(device),
                BluetoothDevice.TRANSPORT_LE
            )

            if (gatt == null) {
                Log.e(TAG, "connectGatt returned null for ${device.address}")
                pendingConnections.remove(address)
            } else {
                Log.d(TAG, "connectGatt initiated for ${device.name ?: address}")
            }
        }
    }

    private fun createGattCallback(device: BluetoothDevice): BluetoothGattCallback {
        val address = device.address

        return object : BluetoothGattCallback() {
            private var remoteDataChar: BluetoothGattCharacteristic? = null
            private var remoteNotifyChar: BluetoothGattCharacteristic? = null
            private var gattPeer: GattPeerConnection? = null

            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "GATT client connected to ${device.name ?: address}")
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d(TAG, "GATT client disconnected from $address")
                    pendingConnections.remove(address)
                    if (intentionallyDisconnected.remove(address)) {
                        gatt.close()
                    } else if (connections.containsKey(address)) {
                        connections.remove(address)
                        updateConnectedPeers()
                        gatt.close()
                    } else {
                        // Connection never fully established — rate-limit retries
                        failedConnections.add(address)
                        serviceScope.launch {
                            delay(60_000L)
                            failedConnections.remove(address)
                        }
                        gatt.close()
                    }
                }
            }

            @SuppressLint("MissingPermission")
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.e(TAG, "Service discovery failed for $address: $status")
                    gatt.disconnect()
                    return
                }
                val service = gatt.getService(SERVICE_UUID)
                if (service == null) {
                    Log.e(TAG, "Neo service not found on $address")
                    failedConnections.add(address)
                    serviceScope.launch {
                        delay(60_000L)
                        failedConnections.remove(address)
                    }
                    gatt.disconnect()
                    return
                }

                remoteDataChar = service.getCharacteristic(GATT_DATA_CHAR_UUID)
                remoteNotifyChar = service.getCharacteristic(GATT_NOTIFY_CHAR_UUID)

                if (remoteDataChar == null || remoteNotifyChar == null) {
                    Log.e(TAG, "Required GATT characteristics not found on $address")
                    failedConnections.add(address)
                    serviceScope.launch {
                        delay(60_000L)
                        failedConnections.remove(address)
                    }
                    gatt.disconnect()
                    return
                }

                // Enable notifications on the remote notify characteristic
                gatt.setCharacteristicNotification(remoteNotifyChar, true)
                val cccd = remoteNotifyChar?.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
                if (cccd != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    } else {
                        @Suppress("DEPRECATION")
                        cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        @Suppress("DEPRECATION")
                        gatt.writeDescriptor(cccd)
                    }
                }

                // Request higher MTU for better throughput
                gatt.requestMtu(517)
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.w(TAG, "MTU request failed for $address, using default")
                }

                // Deduplicate: if a connection already exists, close this extra GATT and bail
                if (connections.containsKey(address)) {
                    Log.d(TAG, "Duplicate GATT connection for $address, closing extra")
                    pendingConnections.remove(address)
                    gatt.close()
                    return
                }

                // Create the GattPeerConnection now that we have a working GATT channel
                val connection = GattPeerConnection(
                    device = device,
                    gatt = gatt,
                    remoteDataChar = remoteDataChar,
                    gattServer = null,
                    localNotifyChar = null,
                    scope = serviceScope,
                    onDisconnect = {
                        connections.remove(address)
                        updateConnectedPeers()
                    }
                )
                connection.onMtuChanged(mtu)
                connection.start()
                connections[address] = connection
                gattPeer = connection
                pendingConnections.remove(address)

                // Replay any data that arrived via server-side before this connection was ready
                pendingIncomingData.remove(address)?.forEach { chunk ->
                    connection.onDataReceived(chunk)
                }

                updateConnectedPeers()
                Log.d(TAG, "Created client-side GattPeerConnection for ${device.address} (MTU=$mtu)")

                // Forward received messages to the shared flow
                serviceScope.launch {
                    connection.receivedMessages.collect { message ->
                        _allIncomingMessages.emit(address to message)
                    }
                }
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                // Drives flow control in GattPeerConnection.writeFrame: signals
                // that the stack has accepted the previous frame so the next one
                // may be sent. Without this, bulk transfers (images) overrun the
                // BLE buffer and writes start failing mid-message.
                if (characteristic.uuid == GATT_DATA_CHAR_UUID) {
                    gattPeer?.onFrameWritten(status)
                }
            }

            @Suppress("DEPRECATION")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                if (characteristic.uuid == GATT_NOTIFY_CHAR_UUID) {
                    val data = characteristic.value
                    if (data != null) {
                        gattPeer?.onDataReceived(data)
                    }
                }
            }

            @SuppressLint("NewApi")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                if (characteristic.uuid == GATT_NOTIFY_CHAR_UUID) {
                    gattPeer?.onDataReceived(value)
                }
            }

            override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                Log.d(TAG, "Descriptor write for $address: status=$status")
            }
        }
    }

    // ------------------------------------------------------------------
    // Reconnection
    // ------------------------------------------------------------------

    private fun scheduleReconnect(device: BluetoothDevice, address: String) {
        Log.d(TAG, "Scheduling reconnect for $address")
        reconnectQueue[address]?.cancel()
        reconnectQueue[address] = serviceScope.launch {
            var delayMs = 5000L
            while (isRunning && !connections.containsKey(address)) {
                delay(delayMs)
                try {
                    Log.d(TAG, "Reconnecting to $address (delay=${delayMs}ms)")
                    connectToDevice(device)
                } catch (e: Exception) {
                    Log.w(TAG, "Reconnect attempt to $address failed: ${e.message}")
                }
                delayMs = minOf(delayMs * 2, 60_000L)
            }
            if (connections.containsKey(address)) {
                Log.d(TAG, "Successfully reconnected to $address")
            }
            reconnectQueue.remove(address)
        }
    }

    // ------------------------------------------------------------------
    // Send / broadcast — unchanged interface
    // ------------------------------------------------------------------

    suspend fun sendMessage(peerAddress: String, message: Message): Boolean {
        val connection = connections[peerAddress]
        if (connection == null) {
            Log.e(TAG, "sendMessage: No connection for $peerAddress (available: ${connections.keys})")
            return false
        }
        Log.e(TAG, "sendMessage: sending ${message::class.simpleName} to $peerAddress")
        val result = connection.sendMessage(message)
        Log.e(TAG, "sendMessage: result=$result for ${message::class.simpleName} to $peerAddress")
        return result
    }

    suspend fun broadcastMessage(message: Message, excludePeer: String? = null) {
        connections.forEach { (address, connection) ->
            if (address != excludePeer) {
                connection.sendMessage(message)
            }
        }
    }

    fun getConnectedPeerAddresses(): List<String> {
        return connections.keys.toList()
    }

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------

    private fun updateConnectedPeers() {
        val previousCount = _connectedPeers.value.size
        _connectedPeers.value = connections.keys.toList()
        updateNotification()
        if (connections.size > previousCount && ::notificationRepository.isInitialized) {
            serviceScope.launch {
                notificationRepository.insert(
                    com.neo.data.model.Notification(
                        id = UUID.randomUUID().toString(),
                        type = "peer",
                        message = "${connections.size} peer(s) connected to your mesh network",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    // ------------------------------------------------------------------
    // Cleanup
    // ------------------------------------------------------------------

    private fun stopBluetoothOperations() {
        isRunning = false

        // Stop BLE operations
        stopBleAdvertising()
        stopBleScanning()

        // Cancel all reconnect jobs
        reconnectQueue.values.forEach { it.cancel() }
        reconnectQueue.clear()

        // Clear pending connection tracking
        pendingConnections.clear()
        intentionallyDisconnected.clear()
        failedConnections.clear()
        pendingIncomingData.clear()

        // Close all peer connections
        connections.values.forEach { it.disconnect() }
        connections.clear()

        // Close GATT server
        try {
            bluetoothGattServer?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing GATT server: ${e.message}")
        }
        bluetoothGattServer = null

        updateConnectedPeers()
    }

    // ------------------------------------------------------------------
    // Notification
    // ------------------------------------------------------------------

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
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        val notification = createNotification()
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
