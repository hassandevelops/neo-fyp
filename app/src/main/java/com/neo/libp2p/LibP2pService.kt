package com.neo.libp2p

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.neo.R
import com.neo.bluetooth.Message
import com.neo.data.preferences.UserPreferences
import com.neo.security.CryptoManager
import com.neo.transport.TransportPort
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@AndroidEntryPoint
class LibP2pService : Service(), TransportPort {

    @Inject lateinit var cryptoManager: CryptoManager
    @Inject lateinit var userPreferences: UserPreferences

    companion object {
        private const val TAG = "LibP2pService"
        private const val NOTIFICATION_ID = 2
        private const val CHANNEL_ID = "neo_libp2p_channel"
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var libP2pNode: LibP2pNode

    // Expose TransportPort-compatible properties
    override val connectedPeers: StateFlow<List<String>> get() = libP2pNode.connectedPeers
    override val allIncomingMessages: Flow<Pair<String, Message>> get() = libP2pNode.incomingMessages

    inner class LocalBinder : Binder() {
        fun getService(): LibP2pService = this@LibP2pService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "LibP2pService created")
        createNotificationChannel()

        libP2pNode = LibP2pNode(
            context = this,
            scope = serviceScope,
            deviceId = cryptoManager.getDeviceId(),
            publicKeyBase64 = cryptoManager.getPublicKeyString(),
            userPreferences = userPreferences
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        libP2pNode.start()
        Log.d(TAG, "LibP2p node started")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        libP2pNode.stop()
        serviceScope.cancel()
        Log.d(TAG, "LibP2pService destroyed")
    }

    override suspend fun sendMessage(peerId: String, message: Message): Boolean =
        libP2pNode.sendToPeer(peerId, message)

    override suspend fun broadcastMessage(message: Message, excludePeer: String?) =
        libP2pNode.broadcastToPeers(message, excludePeer)

    override fun getConnectedPeerAddresses(): List<String> = connectedPeers.value

    /** Allow user to manually add a peer by multiaddress string */
    fun addPeerByMultiaddr(addr: String) = libP2pNode.addPeerByMultiaddr(addr)

    /** Get this node's multiaddresses to share with others */
    fun getListenAddresses(): List<String> = libP2pNode.listenAddresses

    /** Get underlying LibP2pNode for direct operations like PEX */
    fun getNode(): LibP2pNode = libP2pNode

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Neo Internet Sync",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Neo syncing with peers over the internet"
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val peerCount = libP2pNode.connectedPeers.value.size
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Neo · Internet Sync Active")
            .setContentText(
                if (peerCount > 0) "$peerCount internet peer(s) connected"
                else "Searching for Neo peers on the internet…"
            )
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}