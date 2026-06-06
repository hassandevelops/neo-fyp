package com.neo.libp2p

import android.content.Context
import android.util.Log
import com.neo.bluetooth.Message
import com.neo.bluetooth.MessageProtocol
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Wraps the Go neoserver binary (libp2p) as a subprocess.
 * Communicates via JSON lines over local TCP.
 *
 * Architecture:
 *   Kotlin App ──localhost:9877 TCP──▶ Go neoserver binary (embedded in assets/)
 */
class GoLibP2pNode(
    private val context: Context,
    private val scope: CoroutineScope,
    private val deviceId: String,
    private val publicKeyBase64: String
) {
    companion object {
        private const val TAG = "GoLibP2pNode"
        private const val IPC_PORT = 9877
        private const val LIBP2P_PORT = 9876
        private const val BINARY_NAME = "neoserver"
        private const val CONNECT_TIMEOUT_MS = 5000L
        private const val RECONNECT_DELAY_MS = 2000L
    }

    private var process: Process? = null
    private var socket: Socket? = null
    private var writer: OutputStreamWriter? = null
    private var reader: BufferedReader? = null
    private var connected = false
    private val _connectedPeers = MutableStateFlow<List<String>>(emptyList())
    val connectedPeers: StateFlow<List<String>> = _connectedPeers.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<Pair<String, Message>>(
        replay = 0, extraBufferCapacity = 128
    )
    val incomingMessages: Flow<Pair<String, Message>> = _incomingMessages

    // Saved peers for backward compatibility
    private val savedPeers = mutableListOf<String>()

    /**
     * Start the Go binary and connect to it.
     */
    fun start() {
        scope.launch(Dispatchers.IO) {
            try {
                startProcess()
                connectToProcess()
                // launch reader loop in separate coroutine so sendInit runs
                scope.launch(Dispatchers.IO) { startReaderLoop() }
                sendInit()
                startPeerMonitor()

                // Connect to any saved peers
                for (saved in savedPeers.toList()) {
                    sendCommand(JSONObject().apply {
                        put("cmd", "connect")
                        put("multiaddr", saved)
                    })
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start Go libp2p", e)
            }
        }
    }

    /**
     * Extract and run the Go binary from assets.
     */
    private fun startProcess() {
        val binFile = File(context.filesDir, BINARY_NAME)
        // Always overwrite the binary on startup so updated Go code is picked up
        context.assets.open(BINARY_NAME).use { input ->
            binFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        binFile.setExecutable(true)
        Log.d(TAG, "Extracted neoserver binary (${binFile.length()} bytes)")

        val pb = ProcessBuilder(
            "/system/bin/linker64",
            binFile.absolutePath,
            "-port", IPC_PORT.toString()
        )
        pb.directory(context.filesDir)
        pb.redirectErrorStream(true)
        process = pb.start()
        Log.d(TAG, "Started neoserver process")
        // Log Go process stderr (includes log.Printf output)
        scope.launch(Dispatchers.IO) {
            try {
                process?.inputStream?.bufferedReader()?.use { r ->
                    r.lines().forEach { line -> Log.d("neoserver", line) }
                }
            } catch (_: Exception) { }
        }
    }

    /**
     * Connect to the Go binary's TCP command server.
     */
    private suspend fun connectToProcess() {
        val maxRetries = 20
        for (i in 0 until maxRetries) {
            try {
                val s = Socket()
                s.connect(InetSocketAddress("127.0.0.1", IPC_PORT), CONNECT_TIMEOUT_MS.toInt())
                socket = s
                writer = OutputStreamWriter(s.getOutputStream())
                reader = BufferedReader(InputStreamReader(s.getInputStream()))
                connected = true
                Log.d(TAG, "Connected to neoserver after ${i + 1} attempt(s)")
                return
            } catch (e: Exception) {
                if (i >= maxRetries - 1) throw e
                delay(500)
            }
        }
    }

    /**
     * Send init command. The Go binary generates/persists its own key.
     */
    private suspend fun sendInit() {
        val cmd = JSONObject().apply {
            put("cmd", "init")
            put("port", LIBP2P_PORT)
        }
        sendCommand(cmd)
    }

    /**
     * Read JSON events from the Go process and dispatch them.
     */
    private suspend fun startReaderLoop() {
        withContext(Dispatchers.IO) {
            try {
                while (true) {
                    val line = reader?.readLine() ?: break
                    if (line.isBlank()) continue
                    handleEvent(line)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Reader loop error", e)
            } finally {
                connected = false
            }
        }
    }

    /**
     * Handle incoming JSON events from the Go process.
     */
    private fun handleEvent(json: String) {
        try {
            val obj = JSONObject(json)
            when (obj.optString("event")) {
                "server_ready" -> Log.d(TAG, "Go server ready")
                "ready" -> {
                    val peerId = obj.optString("peerID")
                    val addrs = obj.optString("addrs")
                    Log.d(TAG, "libp2p ready. PeerID: $peerId, Addrs: $addrs")
                }
                "message" -> {
                    val peerId = obj.optString("peerID")
                    val msgStr = obj.optString("message")
                    val message = MessageProtocol.deserialize(msgStr)
                    if (message != null) {
                        scope.launch { _incomingMessages.emit(peerId to message) }
                    }
                }
                "peer_connected" -> {
                    val pid = obj.optString("peerID")
                    _connectedPeers.value = _connectedPeers.value + pid
                }
                "peer_disconnected" -> {
                    val pid = obj.optString("peerID")
                    _connectedPeers.value = _connectedPeers.value.filter { it != pid }
                }
                "error" -> Log.w(TAG, "Go error: ${obj.optString("error")}")
                "dht_count" -> Log.d(TAG, "DHT peers: ${obj.optInt("count")}")
                "peers" -> { }
                "pong" -> { }
                else -> Log.d(TAG, "Unknown event: ${obj.optString("event")}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse event: $json", e)
        }
    }

    /**
     * Periodically query connected peers and DHT count.
     */
    private fun startPeerMonitor() {
        scope.launch(Dispatchers.IO) {
            while (isActive && connected) {
                try {
                    sendCommand(JSONObject().apply { put("cmd", "peers") })
                    sendCommand(JSONObject().apply { put("cmd", "dht_count") })
                } catch (_: Exception) { }
                delay(15_000L)
            }
        }
    }

    /**
     * Send a JSON command and wait for response.
     */
    private suspend fun sendCommand(cmd: JSONObject) {
        withContext(Dispatchers.IO) {
            try {
                val line = cmd.toString()
                writer?.write(line + "\n")
                writer?.flush()
            } catch (e: Exception) {
                Log.w(TAG, "Command failed: $cmd", e)
            }
        }
    }

    fun stop() {
        kotlinx.coroutines.runBlocking {
            try {
                sendCommand(JSONObject().apply { put("cmd", "stop") })
            } catch (_: Exception) { }
        }
        try { writer?.close() } catch (_: Exception) { }
        try { reader?.close() } catch (_: Exception) { }
        try { socket?.close() } catch (_: Exception) { }
        process?.destroy()
        process?.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
        process?.destroyForcibly()
        process = null
        connected = false
        _connectedPeers.value = emptyList()
        Log.d(TAG, "Go libp2p stopped")
    }

    // -------- Public API (matches current LibP2pNode) --------

    suspend fun sendToPeer(peerId: String, message: Message): Boolean {
        return try {
            val json = MessageProtocol.serialize(message)
            sendCommand(JSONObject().apply {
                put("cmd", "send")
                put("peerID", peerId)
                put("message", json)
            })
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send to $peerId", e)
            false
        }
    }

    suspend fun broadcastToPeers(message: Message, excludePeer: String? = null) {
        try {
            val json = MessageProtocol.serialize(message)
            sendCommand(JSONObject().apply {
                put("cmd", "broadcast")
                put("message", json)
            })
        } catch (e: Exception) {
            Log.e(TAG, "Broadcast failed", e)
        }
    }

    val listenAddresses: List<String> = emptyList()

    fun addPeerByMultiaddr(multiaddrStr: String) {
        savePeer(multiaddrStr)
        scope.launch(Dispatchers.IO) {
            try {
                sendCommand(JSONObject().apply {
                    put("cmd", "connect")
                    put("multiaddr", multiaddrStr)
                })
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to $multiaddrStr", e)
            }
        }
    }

    fun savePeer(multiaddrStr: String) {
        if (!savedPeers.contains(multiaddrStr)) {
            savedPeers.add(multiaddrStr)
        }
        val prefs = context.getSharedPreferences("neo_libp2p_prefs", Context.MODE_PRIVATE)
        val current = prefs.getStringSet("saved_peers", emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(multiaddrStr)
        prefs.edit().putStringSet("saved_peers", current).apply()
    }

    fun getSavedPeers(): List<String> {
        val prefs = context.getSharedPreferences("neo_libp2p_prefs", Context.MODE_PRIVATE)
        return prefs.getStringSet("saved_peers", emptySet())?.toList() ?: emptyList()
    }

    suspend fun publishToTopic(message: Message): Boolean {
        // Broadcast handles this for direct streams
        broadcastToPeers(message)
        return true
    }
}
