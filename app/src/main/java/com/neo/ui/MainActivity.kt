package com.neo.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.neo.bluetooth.BluetoothService
import com.neo.ui.navigation.EnhancedNeoNavigation
import com.neo.ui.theme.NeoTheme
import com.neo.ui.viewmodel.FeedViewModel
import dagger.hilt.android.AndroidEntryPoint

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
    private var bluetoothService: BluetoothService? = null
    private var isBound = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BluetoothService.LocalBinder
            bluetoothService = binder.getService()
            isBound = true
            
            // Set service in ViewModel
            bluetoothService?.let { viewModel.setBluetoothService(it) }
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            bluetoothService = null
            isBound = false
        }
    }
    
    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            startBluetoothService()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Handle the splash screen transition.
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
        super.onCreate(savedInstanceState)
        
        setContent {
            NeoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    EnhancedNeoNavigation(viewModel = viewModel)
                }
            }
        }
        
        // Request permissions and start service
        requestPermissionsAndStartService()
    }
    
    override fun onStart() {
        super.onStart()
        // Bind to Bluetooth service
        val intent = Intent(this, BluetoothService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    
    override fun onStop() {
        super.onStop()
        // Unbind from service
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
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
            // All permissions granted, start service
            startBluetoothService()
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
}
