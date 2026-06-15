package com.neo.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neo.data.preferences.UserPreferences
import com.neo.ui.components.GradientBackground
import com.neo.ui.components.organicPattern
import com.neo.ui.screens.settings.QrScanActivity
import com.neo.ui.theme.*
import androidx.compose.material3.MaterialTheme

/**
 * Network Settings Screen
 *
 * Lets the user configure a custom bootstrap multiaddr via QR code scan
 * or manual entry, and toggle the bootstrap override. The current
 * multiaddr is shown read-only.
 */
@Composable
fun NetworkSettingsScreen(
    userPreferences: UserPreferences,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(userPreferences.bootstrapEnabled) }
    var current by remember { mutableStateOf(userPreferences.bootstrapMultiaddr.orEmpty()) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val qrLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val multiaddr = result.data?.getStringExtra(QrScanActivity.EXTRA_MULTIADDR)
            if (!multiaddr.isNullOrBlank()) {
                current = multiaddr
                enabled = true
                userPreferences.bootstrapMultiaddr = multiaddr
                userPreferences.bootstrapEnabled = true
                statusMessage = "Bootstrap captured. Reconnecting in a few seconds…"
            }
        } else {
            val err = result.data?.getStringExtra(QrScanActivity.EXTRA_ERROR)
            if (err != null) statusMessage = "Scan failed: $err"
        }
    }

    fun openQrScanner() {
        val intent = Intent(context, QrScanActivity::class.java)
        qrLauncher.launch(intent)
    }

    fun clearOverride() {
        userPreferences.bootstrapMultiaddr = null
        userPreferences.bootstrapEnabled = false
        current = ""
        enabled = false
        statusMessage = "Bootstrap override cleared. Reconnecting with defaults…"
    }

    GradientBackground(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = NeoBlack
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = TextWhite
                        )
                    }
                    Text(
                        text = "Network Settings",
                        color = TextWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = NeoShapes.card,
                        colors = CardDefaults.cardColors(containerColor = SurfaceElevated1)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Bootstrap Override",
                                color = TextWhite,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "By default Neo connects to public IPFS DHT nodes. " +
                                    "To use a self-hosted bootstrap (a friend's server, your " +
                                    "own machine, or any other user-operated relay), scan its " +
                                    "QR code here.",
                                color = TextWhite80,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )

                            Button(
                                onClick = { openQrScanner() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = NeoShapes.pill,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeoLime,
                                    contentColor = NeoBlack
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text("Scan Bootstrap QR Code")
                            }
                        }
                    }
                }

                if (current.isNotBlank()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = NeoShapes.card,
                            colors = CardDefaults.cardColors(containerColor = SurfaceElevated1)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Current Bootstrap",
                                    color = TextWhite60,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = current,
                                    color = TextWhite,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }

                item {
                    SwitchSettingCard(
                        icon = Icons.Default.Cloud,
                        title = "Use Custom Bootstrap",
                        subtitle = "If off, Neo connects to public IPFS DHT nodes",
                        checked = enabled,
                        onCheckedChange = {
                            enabled = it
                            userPreferences.bootstrapEnabled = it
                            statusMessage = "Setting saved. Reconnecting…"
                        },
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (current.isNotBlank()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = NeoShapes.card,
                            colors = CardDefaults.cardColors(containerColor = SurfaceElevated1)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.size(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Clear Override",
                                        color = TextWhite,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "Revert to public IPFS DHT bootstrap nodes",
                                        color = TextWhite60,
                                        fontSize = 14.sp
                                    )
                                }
                                TextButton(onClick = { clearOverride() }) {
                                    Text("Clear", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                statusMessage?.let { msg ->
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = NeoShapes.control,
                            colors = CardDefaults.cardColors(containerColor = SurfaceElevated1)
                        ) {
                            Text(
                                text = msg,
                                color = TextWhite80,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Account Settings Screen
 */
@Composable
fun AccountSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    GradientBackground(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = NeoBlack
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = TextWhite
                        )
                    }
                    Text(
                        text = "Account Settings",
                        color = TextWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    SettingCard(
                        icon = Icons.Default.Person,
                        title = "Display Name",
                        subtitle = "Change your display name",
                        color = NeoLime
                    )
                }
                item {
                    SettingCard(
                        icon = Icons.Default.Key,
                        title = "Public Key",
                        subtitle = "View your cryptographic public key",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                item {
                    SettingCard(
                        icon = Icons.Default.Devices,
                        title = "Device ID",
                        subtitle = "Your unique device identifier",
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                item {
                    SettingCard(
                        icon = Icons.Default.Storage,
                        title = "Storage",
                        subtitle = "Manage local data and cache",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/**
 * Privacy Settings Screen
 */
@Composable
fun PrivacySettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var allowDiscovery by remember { mutableStateOf(true) }
    var autoSync by remember { mutableStateOf(true) }
    var shareAnalytics by remember { mutableStateOf(false) }
    
    GradientBackground(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = NeoBlack
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = TextWhite
                        )
                    }
                    Text(
                        text = "Privacy Settings",
                        color = TextWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    SwitchSettingCard(
                        icon = Icons.Default.Bluetooth,
                        title = "Allow Discovery",
                        subtitle = "Let other devices discover you via Bluetooth",
                        checked = allowDiscovery,
                        onCheckedChange = { allowDiscovery = it },
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                item {
                    SwitchSettingCard(
                        icon = Icons.Default.Sync,
                        title = "Auto Sync",
                        subtitle = "Automatically sync with nearby peers",
                        checked = autoSync,
                        onCheckedChange = { autoSync = it },
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                item {
                    SwitchSettingCard(
                        icon = Icons.Default.Analytics,
                        title = "Share Analytics",
                        subtitle = "Help improve Neo by sharing anonymous usage data",
                        checked = shareAnalytics,
                        onCheckedChange = { shareAnalytics = it },
                        color = MaterialTheme.colorScheme.error
                    )
                }
                item {
                    SettingCard(
                        icon = Icons.Default.Delete,
                        title = "Clear Data",
                        subtitle = "Delete all local posts and cache",
                        color = StatusOffline
                    )
                }
            }
        }
    }
}

/**
 * Notification Settings Screen
 */
@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var enableNotifications by remember { mutableStateOf(true) }
    var newPosts by remember { mutableStateOf(true) }
    var newPeers by remember { mutableStateOf(true) }
    var syncComplete by remember { mutableStateOf(false) }
    
    GradientBackground(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = NeoBlack
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = TextWhite
                        )
                    }
                    Text(
                        text = "Notification Settings",
                        color = TextWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    SwitchSettingCard(
                        icon = Icons.Default.Notifications,
                        title = "Enable Notifications",
                        subtitle = "Receive notifications from Neo",
                        checked = enableNotifications,
                        onCheckedChange = { enableNotifications = it },
                        color = MaterialTheme.colorScheme.error
                    )
                }
                item {
                    SwitchSettingCard(
                        icon = Icons.Default.Article,
                        title = "New Posts",
                        subtitle = "Notify when new posts are received",
                        checked = newPosts,
                        onCheckedChange = { newPosts = it },
                        color = MaterialTheme.colorScheme.primary,
                        enabled = enableNotifications
                    )
                }
                item {
                    SwitchSettingCard(
                        icon = Icons.Default.People,
                        title = "New Peers",
                        subtitle = "Notify when new peers connect",
                        checked = newPeers,
                        onCheckedChange = { newPeers = it },
                        color = MaterialTheme.colorScheme.secondary,
                        enabled = enableNotifications
                    )
                }
                item {
                    SwitchSettingCard(
                        icon = Icons.Default.CloudDone,
                        title = "Sync Complete",
                        subtitle = "Notify when sync finishes",
                        checked = syncComplete,
                        onCheckedChange = { syncComplete = it },
                        color = StatusOnline,
                        enabled = enableNotifications
                    )
                }
            }
        }
    }
}

/**
 * About Screen
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    GradientBackground(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = NeoBlack
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = TextWhite
                        )
                    }
                    Text(
                        text = "About Neo",
                        color = TextWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = NeoShapes.cardLarge,
                        colors = CardDefaults.cardColors(
                            containerColor = SurfaceElevated1
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "✦",
                                fontSize = 48.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "NEO",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                            Text(
                                text = "Version 1.0.0",
                                fontSize = 14.sp,
                                color = TextWhite60
                            )
                        }
                    }
                }
                
                item {
                    InfoCard(
                        title = "Decentralized Social Network",
                        description = "Neo is a fully decentralized, offline-first social media platform that uses peer-to-peer Bluetooth connections to share posts without any central server."
                    )
                }
                
                item {
                    InfoCard(
                        title = "Privacy First",
                        description = "All posts are cryptographically signed using Ed25519 keys. Your data stays on your device and is only shared with peers you connect to."
                    )
                }
                
                item {
                    InfoCard(
                        title = "Offline Capable",
                        description = "Create and view posts even without internet. Posts automatically sync when you're near other Neo users."
                    )
                }
                
                item {
                    SettingCard(
                        icon = Icons.Default.Code,
                        title = "Open Source",
                        subtitle = "View source code on GitHub",
                        color = NeoLime
                    )
                }
                
                item {
                    SettingCard(
                        icon = Icons.Default.Description,
                        title = "License",
                        subtitle = "MIT License",
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = NeoShapes.card,
        colors = CardDefaults.cardColors(
            containerColor = SurfaceElevated1
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .organicPattern(SurfaceElevated1, richness = 4)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = TextWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    color = TextWhite60,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun SwitchSettingCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    color: androidx.compose.ui.graphics.Color,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = NeoShapes.card,
        colors = CardDefaults.cardColors(
            containerColor = SurfaceElevated1
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .organicPattern(SurfaceElevated1, richness = 4)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) color else TextWhite40,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (enabled) TextWhite else TextWhite60,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    color = if (enabled) TextWhite60 else TextWhite40,
                    fontSize = 14.sp
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = color,
                    checkedTrackColor = color.copy(alpha = 0.5f)
                )
            )
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = NeoShapes.card,
        colors = CardDefaults.cardColors(
            containerColor = SurfaceElevated1
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .organicPattern(SurfaceElevated1, richness = 4)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                color = TextWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                color = TextWhite80,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}
