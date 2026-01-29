package com.neo.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a known device/peer in the network.
 * Used for tracking peers and their public keys.
 */
@Entity(tableName = "devices")
data class Device(
    @PrimaryKey
    val deviceId: String,              // UUID of the device
    val deviceName: String,            // Display name of the device owner
    val publicKey: String,             // Device's public key
    val lastSeenTimestamp: Long,       // Last time we connected to this device
    val bluetoothAddress: String? = null  // Bluetooth MAC address (if available)
)
