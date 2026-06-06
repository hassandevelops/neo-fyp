package com.neo.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Append-only event log for decentralized, conflict-free replication.
 * Acts as the cryptographic source of truth for all state changes.
 */
@Serializable
@Entity(
    tableName = "event_log",
    indices = [
        Index(value = ["authorDid", "sequenceNum"], unique = true)
    ]
)
data class EventLog(
    @PrimaryKey
    val eventId: String,          // SHA-256 Hash of (payload + signature + sequenceNum)
    val authorDid: String,        // The decentralized identifier (did:key) of the creator
    val sequenceNum: Long,        // Monotonically increasing counter per author
    val eventType: String,        // "CREATE_POST", "DELETE_POST", "LIKE_POST", etc.
    val payload: String,          // JSON string of the event data
    val signature: String,        // RSA signature of the eventId
    val timestamp: Long           // Unix epoch milliseconds
)