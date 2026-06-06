package com.neo.data.dao

import androidx.room.*
import com.neo.data.model.EventLog

@Dao
interface EventLogDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvent(event: EventLog): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvents(events: List<EventLog>)

    @Query("SELECT * FROM event_log WHERE authorDid = :authorDid AND sequenceNum > :lastSequence ORDER BY sequenceNum ASC")
    suspend fun getEventsAfter(authorDid: String, lastSequence: Long): List<EventLog>

    @Query("SELECT MAX(sequenceNum) FROM event_log WHERE authorDid = :authorDid")
    suspend fun getLastSequenceNum(authorDid: String): Long?
    
    @Query("SELECT * FROM event_log ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentEvents(limit: Int): List<EventLog>

    @Query("SELECT * FROM event_log WHERE timestamp > :sinceTimestamp ORDER BY timestamp ASC")
    suspend fun getAllEventsSince(sinceTimestamp: Long): List<EventLog>

    @Query("SELECT MAX(timestamp) FROM event_log")
    suspend fun getMaxTimestamp(): Long?
}