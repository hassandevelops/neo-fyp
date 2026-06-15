package com.neo.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.neo.data.model.Follow
import kotlinx.coroutines.flow.Flow

@Dao
interface FollowDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(follow: Follow)

    @Query("SELECT * FROM follows WHERE id = :id")
    suspend fun getById(id: String): Follow?

    @Query("SELECT EXISTS(SELECT 1 FROM follows WHERE followerDid = :followerDid AND followeeDid = :followeeDid AND active = 1)")
    fun observeIsFollowing(followerDid: String, followeeDid: String): Flow<Boolean>

    /** Number of active followers of [did]. */
    @Query("SELECT COUNT(*) FROM follows WHERE followeeDid = :did AND active = 1")
    fun observeFollowerCount(did: String): Flow<Int>

    /** Number of users [did] is actively following. */
    @Query("SELECT COUNT(*) FROM follows WHERE followerDid = :did AND active = 1")
    fun observeFollowingCount(did: String): Flow<Int>

    /** All outgoing edges authored by [followerDid] (for bootstrap re-broadcast). */
    @Query("SELECT * FROM follows WHERE followerDid = :followerDid")
    suspend fun getOutgoing(followerDid: String): List<Follow>

    @Query("DELETE FROM follows")
    suspend fun deleteAll()
}
