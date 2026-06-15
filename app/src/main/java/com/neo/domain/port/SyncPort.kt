package com.neo.domain.port

import com.neo.data.model.Comment
import com.neo.data.model.Follow
import com.neo.data.model.Post
import com.neo.data.model.Reaction
import kotlinx.coroutines.flow.StateFlow

interface ISyncPort {
    suspend fun broadcastPost(post: Post)
    suspend fun broadcastComment(comment: Comment)
    suspend fun broadcastReaction(reaction: Reaction)
    suspend fun broadcastFollow(follow: Follow, followerName: String)
    suspend fun broadcastProfile()
    val connectedPeersCount: StateFlow<Int>
    val connectedPeers: StateFlow<List<String>>
    fun forceSyncNow()
}