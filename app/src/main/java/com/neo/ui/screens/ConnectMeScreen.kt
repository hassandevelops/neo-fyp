package com.neo.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.neo.libp2p.ConnectInfo
import com.neo.ui.theme.*
import java.net.URLEncoder

/**
 * "Connect to me" — turns this phone into a peer others can dial directly.
 *
 * Renders a QR of this node's own multiaddr as a `neo://bootstrap?maddr=…`
 * deep link (the exact format [com.neo.ui.screens.settings.QrScanActivity]
 * already accepts). Another phone scans it from Settings → Network → Scan QR
 * and connects to this device. On the same Wi-Fi this always works; across the
 * internet it works when this device is publicly reachable (see the
 * reachability badge).
 */
@Composable
fun ConnectMeScreen(
    connectInfo: ConnectInfo?,
    reachability: String,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Scan another device's "Connect to me" QR. QrScanActivity persists the
    // scanned multiaddr as the bootstrap override and the running libp2p node
    // dials it on its next supervisor iteration.
    val scanLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(context, "Peer captured — connecting…", Toast.LENGTH_SHORT).show()
        } else {
            val err = result.data?.getStringExtra(
                com.neo.ui.screens.settings.QrScanActivity.EXTRA_ERROR
            )
            if (err != null) Toast.makeText(context, "Scan failed: $err", Toast.LENGTH_SHORT).show()
        }
    }

    val multiaddr = connectInfo?.primaryMultiaddr
    val deepLink = remember(multiaddr) {
        multiaddr?.let { "neo://bootstrap?maddr=" + URLEncoder.encode(it, "UTF-8") }
    }
    val qrBitmap = remember(deepLink) { deepLink?.let { generateQr(it, 720) } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NeoBlack)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextWhite)
            }
            Text(
                text = "Connect a device",
                color = TextWhite,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = TextWhite60)
            }
        }

        Spacer(Modifier.height(8.dp))

        // Reachability badge
        ReachabilityBadge(
            reachability = reachability,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(Modifier.height(20.dp))

        // Primary action — scan a peer's QR to connect to them
        Button(
            onClick = {
                scanLauncher.launch(
                    Intent(context, com.neo.ui.screens.settings.QrScanActivity::class.java)
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(52.dp)
        ) {
            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text("Scan a device's QR", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(28.dp))

        Text(
            text = "Or let them scan you",
            color = TextWhite60,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(Modifier.height(12.dp))

        // QR card
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(androidx.compose.ui.graphics.Color.White)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (qrBitmap != null) {
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "Your connection QR code",
                    modifier = Modifier.size(260.dp)
                )
            } else {
                Box(
                    modifier = Modifier.size(260.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = NeoBlack)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "Have a friend open Neo → Settings → Network → Scan Bootstrap QR and point their camera here.",
            color = TextWhite60,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(Modifier.height(16.dp))

        // Multiaddr + copy
        if (multiaddr != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = NeoShapes.card,
                colors = CardDefaults.cardColors(containerColor = SurfaceElevated1)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Your address", color = TextWhite60, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = multiaddr,
                        color = TextWhite,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { copyToClipboard(context, multiaddr) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Copy address")
                    }
                }
            }
        } else {
            Text(
                text = "Determining your address… make sure the WAN service is running and you're connected to a network.",
                color = TextWhite40,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun ReachabilityBadge(reachability: String, modifier: Modifier = Modifier) {
    val (title, detail, color) = when (reachability) {
        "public" -> Triple(
            "Publicly reachable",
            "Other devices can connect to you over the internet, and you can relay for peers behind NAT.",
            NeoLime
        )
        "private" -> Triple(
            "Behind NAT",
            "On the same Wi-Fi this works directly. Over the internet, the other device may need to scan a shared bootstrap instead.",
            TextWhite60
        )
        else -> Triple(
            "Checking reachability…",
            "Neo is probing whether this device is reachable from the internet.",
            TextWhite60
        )
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = NeoShapes.card,
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated1)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(color)
                )
                Spacer(Modifier.width(8.dp))
                Text(title, color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(6.dp))
            Text(detail, color = TextWhite60, fontSize = 13.sp)
        }
    }
}

private fun generateQr(content: String, size: Int): Bitmap? = try {
    BarcodeEncoder().encodeBitmap(content, BarcodeFormat.QR_CODE, size, size)
} catch (e: Exception) {
    null
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("Neo address", text))
    Toast.makeText(context, "Address copied", Toast.LENGTH_SHORT).show()
}
