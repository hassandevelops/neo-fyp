package com.neo.ui.screens.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.google.zxing.integration.android.IntentIntegrator
import com.neo.data.preferences.UserPreferences
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Single-purpose activity that opens a QR scanner, parses the result as either
 * a raw libp2p multiaddr or a neo://bootstrap deep link, and stores it in
 * [UserPreferences]. The running libp2p node picks up the new value on its
 * next supervisor-loop iteration (within ~30s of save).
 *
 * Launched from the Settings screen. The captured multiaddr is returned via
 * setResult() so the caller can display it.
 */
@AndroidEntryPoint
class QrScanActivity : ComponentActivity() {

    @Inject lateinit var userPreferences: UserPreferences

    private val scanLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val text = data?.getStringExtra("SCAN_RESULT")
                ?: IntentIntegrator.parseActivityResult(result.resultCode, data)?.contents
            if (text.isNullOrBlank()) {
                Toast.makeText(this, "QR scan returned no content", Toast.LENGTH_SHORT).show()
                finishWithError("No content")
                return@registerForActivityResult
            }
            handleScannedText(text)
        } else {
            finishWithError("Scan cancelled")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            LaunchedEffect(Unit) {
                launchScanner()
            }
        }
    }

    private fun launchScanner() {
        val integrator = IntentIntegrator(this)
        integrator.setOrientationLocked(true)
        integrator.setBeepEnabled(false)
        integrator.setBarcodeImageEnabled(false)
        integrator.captureActivity = PortraitCaptureActivity::class.java
        integrator.setPrompt("Scan a Neo bootstrap QR code")
        scanLauncher.launch(integrator.createScanIntent())
    }

    private fun handleScannedText(text: String) {
        val multiaddr = parseBootstrapPayload(text)
        if (multiaddr == null) {
            Log.w(TAG, "Scanned text is not a valid bootstrap payload: $text")
            finishWithError("Invalid QR content")
            return
        }
        userPreferences.bootstrapMultiaddr = multiaddr
        userPreferences.bootstrapEnabled = true
        Log.i(TAG, "Captured bootstrap multiaddr: $multiaddr")
        Toast.makeText(this, "Bootstrap updated — reconnecting…", Toast.LENGTH_SHORT).show()
        val data = Intent().apply { putExtra(EXTRA_MULTIADDR, multiaddr) }
        setResult(Activity.RESULT_OK, data)
        finish()
    }

    private fun parseBootstrapPayload(text: String): String? {
        val trimmed = text.trim()
        // Case 1: a neo://bootstrap?maddr=<urlencoded-multiaddr> deep link
        if (trimmed.startsWith("neo://")) {
            val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
            if (uri.host != "bootstrap") return null
            val maddr = uri.getQueryParameter("maddr") ?: return null
            return maddr.takeIf { it.startsWith("/") && it.contains("/p2p/") }
        }
        // Case 2: a raw libp2p multiaddr
        return trimmed.takeIf { it.startsWith("/") && it.contains("/p2p/") }
    }

    private fun finishWithError(reason: String) {
        val data = Intent().apply { putExtra(EXTRA_ERROR, reason) }
        setResult(Activity.RESULT_CANCELED, data)
        finish()
    }

    companion object {
        private const val TAG = "QrScanActivity"
        const val EXTRA_MULTIADDR = "extra_multiaddr"
        const val EXTRA_ERROR = "extra_error"
    }
}
