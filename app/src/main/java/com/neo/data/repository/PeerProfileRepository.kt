package com.neo.data.repository

import com.neo.data.dao.PeerProfileDao
import com.neo.data.model.PeerProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PeerProfileRepository @Inject constructor(
    private val peerProfileDao: PeerProfileDao
) {
    suspend fun upsert(profile: PeerProfile) = peerProfileDao.upsert(profile)

    suspend fun getByDid(did: String): PeerProfile? = peerProfileDao.getByDid(did)

    fun observe(did: String): Flow<PeerProfile?> = peerProfileDao.observe(did)

    /** All known peer profiles keyed by DID, for fast avatar/name resolution. */
    fun observeProfilesByDid(): Flow<Map<String, PeerProfile>> =
        peerProfileDao.observeAll().map { list -> list.associateBy { it.did } }

    suspend fun deleteAll() = peerProfileDao.deleteAll()
}
