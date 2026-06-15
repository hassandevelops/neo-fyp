package com.neo.data.repository

import com.neo.data.dao.FollowDao
import com.neo.data.model.Follow
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FollowRepository @Inject constructor(
    private val followDao: FollowDao
) {
    /** Stable edge id so follow/unfollow update the same row. */
    fun edgeId(followerDid: String, followeeDid: String): String = "$followerDid:$followeeDid"

    suspend fun upsert(follow: Follow) = followDao.upsert(follow)

    suspend fun getById(id: String): Follow? = followDao.getById(id)

    fun observeIsFollowing(followerDid: String, followeeDid: String): Flow<Boolean> =
        followDao.observeIsFollowing(followerDid, followeeDid)

    fun observeFollowerCount(did: String): Flow<Int> = followDao.observeFollowerCount(did)

    fun observeFollowingCount(did: String): Flow<Int> = followDao.observeFollowingCount(did)

    suspend fun getOutgoing(followerDid: String): List<Follow> = followDao.getOutgoing(followerDid)

    suspend fun deleteAll() = followDao.deleteAll()
}
