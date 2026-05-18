package com.neo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neo.ui.components.GradientBackground
import com.neo.ui.theme.*
import androidx.compose.material3.MaterialTheme

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
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = SurfaceWhite5
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
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = SurfaceWhite5
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = SurfaceWhite5
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = SurfaceWhite5
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
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
