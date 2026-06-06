package com.neo.bluetooth

import kotlinx.serialization.Serializable

/**
 * Lightweight Data Transfer Object for EventLog to avoid serializing Room entities directly.
 */
@Serializable
data class EventLogDto(
    val eventId: String,
    val authorDid: String,
    val sequenceNum: Long,
    val eventType: String,
    val payload: String,
    val signature: String,
    val timestamp: Long
) {
    fun toEntity(): com.neo.data.model.EventLog {
        return com.neo.data.model.EventLog(
            eventId = eventId,
            authorDid = authorDid,
            sequenceNum = sequenceNum,
            eventType = eventType,
            payload = payload,
            signature = signature,
            timestamp = timestamp
        )
    }

    companion object {
        fun fromEntity(entity: com.neo.data.model.EventLog): EventLogDto {
            return EventLogDto(
                eventId = entity.eventId,
                authorDid = entity.authorDid,
                sequenceNum = entity.sequenceNum,
                eventType = entity.eventType,
                payload = entity.payload,
                signature = entity.signature,
                timestamp = entity.timestamp
            )
        }
    }
}