package com.neo.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.neo.data.model.PeerProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface PeerProfileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: PeerProfile)

    @Query("SELECT * FROM peer_profiles")
    fun observeAll(): Flow<List<PeerProfile>>

    @Query("SELECT * FROM peer_profiles WHERE did = :did")
    fun observe(did: String): Flow<PeerProfile?>

    @Query("SELECT * FROM peer_profiles WHERE did = :did")
    suspend fun getByDid(did: String): PeerProfile?

    @Query("DELETE FROM peer_profiles")
    suspend fun deleteAll()
}
