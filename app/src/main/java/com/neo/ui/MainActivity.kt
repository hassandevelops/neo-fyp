package com.neo.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.neo.bluetooth.BluetoothService
import com.neo.data.preferences.UserPreferences
import com.neo.sync.SyncManager
import com.neo.ui.navigation.EnhancedNeoNavigation
import com.neo.ui.theme.NeoTheme
import com.neo.ui.viewmodel.FeedViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Main activity for Neo app.
 */
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

/**
 * Main activity for Neo app.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: FeedViewModel by viewModels()

    @Inject
    lateinit var userPreferences: UserPreferences

    @Inject
    lateinit var syncManager: SyncManager

    private var bluetoothService: BluetoothService? = null
    private var isBound = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BluetoothService.LocalBinder
            bluetoothService = binder.getService()
            isBound = true
            syncManager.setBluetoothService(binder.getService())
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bluetoothService = null
            isBound = false
        }
    }

    private var libP2pService: com.neo.libp2p.LibP2pService? = null
    private var isLibP2pBound = false

    private val libP2pServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as com.neo.libp2p.LibP2pService.LocalBinder
            libP2pService = binder.getService()
            isLibP2pBound = true
            syncManager.setLibP2pService(binder.getService())
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            libP2pService = null
            isLibP2pBound = false
        }
    }
    
    private var showPermissionRationale by mutableStateOf(false)
    private val deepLinkIntent = mutableStateOf<Intent?>(null)

    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            startBluetoothService()
            if (!isBound) {
                bindService(Intent(this, BluetoothService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
            }
            startLibP2pService()
            if (!isLibP2pBound) {
                bindService(Intent(this, com.neo.libp2p.LibP2pService::class.java), libP2pServiceConnection, Context.BIND_AUTO_CREATE)
            }
        } else {
            showPermissionRationale = true
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        window.requestFeature(android.view.Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        deepLinkIntent.value = intent
        
        // Handle the splash screen transition.
        var keepSplashOnScreen = true
        val delay = 1500L
        
        installSplashScreen().setKeepOnScreenCondition { 
            keepSplashOnScreen 
        }
        
        // Remove splash after delay
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            keepSplashOnScreen = false
        }, delay)
        
        setContent {
            if (showPermissionRationale) {
                AlertDialog(
                    onDismissRequest = { showPermissionRationale = false },
                    title = { Text("Bluetooth Required") },
                    text = { Text("Neo needs Bluetooth to discover and connect with nearby peers. Please grant Bluetooth permissions in Settings.") },
                    confirmButton = {
                        TextButton(onClick = {
                            showPermissionRationale = false
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", packageName, null)
                            }
                            startActivity(intent)
                        }) { Text("Open Settings") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPermissionRationale = false }) { Text("Cancel") }
                    }
                )
            }
            NeoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    EnhancedNeoNavigation(
                        viewModel = viewModel,
                        userPreferences = userPreferences,
                        deepLinkIntent = deepLinkIntent.value
                    )
                }
            }
        }
        
        // Request permissions and start service
        requestPermissionsAndStartService()
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        deepLinkIntent.value = intent
    }
    
    override fun onStart() {
        super.onStart()
        if (!isBound) {
            bindService(Intent(this, BluetoothService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
        }
        if (!isLibP2pBound) {
            bindService(Intent(this, com.neo.libp2p.LibP2pService::class.java), libP2pServiceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        if (isLibP2pBound) {
            unbindService(libP2pServiceConnection)
            isLibP2pBound = false
        }
    }
    
    /**
     * Request necessary permissions and start Bluetooth service.
     */
    private fun requestPermissionsAndStartService() {
        val permissions = mutableListOf<String>()
        
        // Bluetooth permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }
        
        // Location permission (required for Bluetooth scanning on older Android)
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        
        // Notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        // Check which permissions are not granted
        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (permissionsToRequest.isEmpty()) {
            // All permissions granted, start and bind services
            startBluetoothService()
            if (!isBound) {
                bindService(Intent(this, BluetoothService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
            }
            startLibP2pService()
            if (!isLibP2pBound) {
                bindService(Intent(this, com.neo.libp2p.LibP2pService::class.java), libP2pServiceConnection, Context.BIND_AUTO_CREATE)
            }
        } else {
            // Request permissions
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
    
    /**
     * Start the Bluetooth foreground service.
     */
    private fun startBluetoothService() {
        val intent = Intent(this, BluetoothService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
    
    /**
     * Start the libp2p foreground service.
     */
    private fun startLibP2pService() {
        val intent = Intent(this, com.neo.libp2p.LibP2pService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
