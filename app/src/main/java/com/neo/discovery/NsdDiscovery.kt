package com.neo.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LAN discovery service for Neo using Android's NSD (Network Service Discovery)
 * API. NSD is the platform replacement for the broken Go-side mDNS that the
 * project's own source code recommends in cmd/neoserver/main.go:580-590.
 *
 * NSD only does *discovery* — it tells us the host:port of a peer that has
 * registered a Neo service. Actual data transport is delegated to the libp2p
 * stack by the caller, who collects from [discoveredMultiaddrs] and calls
 * [com.neo.libp2p.LibP2pNode.addPeerByMultiaddr] on the running node.
 *
 * Discovered multiaddrs are published on [discoveredMultiaddrs] for the
 * application (typically [com.neo.ui.MainActivity]) to consume and feed into
 * the libp2p stack. NsdDiscovery is intentionally decoupled from the
 * libp2p service so its construction is not blocked by service startup.
 */
@Singleton
class NsdDiscovery @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "NsdDiscovery"
        private const val SERVICE_TYPE = "_neo._tcp."
        private const val PROTOCOL_VERSION = "v1"
    }

    private var nsdManager: NsdManager? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    private val _discoveredMultiaddrs = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 32)
    val discoveredMultiaddrs: SharedFlow<String> = _discoveredMultiaddrs.asSharedFlow()

    /**
     * Start NSD discovery. Call from the application component that owns the
     * libp2p service lifecycle.
     */
    fun start() {
        val mgr = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
        if (mgr == null) {
            Log.w(TAG, "NSD not available on this device")
            return
        }
        nsdManager = mgr

        val wifi = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wifi != null) {
            multicastLock = wifi.createMulticastLock("neo_nsd_lock").apply {
                setReferenceCounted(false)
                acquire()
            }
        }

        startDiscovery(mgr)
    }

    fun stop() {
        val mgr = nsdManager ?: return
        discoveryListener?.let { runCatching { mgr.stopServiceDiscovery(it) } }
        discoveryListener = null
        try { multicastLock?.release() } catch (_: Exception) {}
        multicastLock = null
        nsdManager = null
        Log.d(TAG, "NSD stopped")
    }

    private fun startDiscovery(mgr: NsdManager) {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "NSD discovery started for $regType")
            }
            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType.contains(SERVICE_TYPE)) {
                    resolveService(service)
                }
            }
            override fun onServiceLost(service: NsdServiceInfo) {
                Log.d(TAG, "NSD service lost: ${service.serviceName}")
            }
            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "NSD discovery stopped for $serviceType")
            }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "NSD start discovery failed: $errorCode")
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "NSD stop discovery failed: $errorCode")
            }
        }
        discoveryListener = listener
        runCatching { mgr.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { Log.w(TAG, "NSD discover threw", it) }
    }

    private fun resolveService(service: NsdServiceInfo) {
        val mgr = nsdManager ?: return
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "NSD resolve failed for ${serviceInfo.serviceName}: $errorCode")
            }
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val peerId = serviceInfo.attributes["p2p"]?.let { String(it, Charsets.UTF_8) }
                if (peerId.isNullOrBlank()) {
                    Log.w(TAG, "NSD service ${serviceInfo.serviceName} has no p2p attr")
                    return
                }
                val host = serviceInfo.host?.hostAddress ?: return
                val port = serviceInfo.port
                val multiaddr = "/ip4/$host/tcp/$port/p2p/$peerId"
                Log.i(TAG, "NSD discovered: $multiaddr")
                _discoveredMultiaddrs.tryEmit(multiaddr)
            }
        }
        runCatching { mgr.resolveService(service, resolveListener) }
            .onFailure { Log.w(TAG, "NSD resolve threw", it) }
    }
}
