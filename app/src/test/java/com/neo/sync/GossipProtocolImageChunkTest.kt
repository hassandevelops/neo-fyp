package com.neo.sync

import com.neo.bluetooth.Message
import com.neo.data.model.Post
import com.neo.data.dao.EventLogDao
import com.neo.data.repository.BlockedUserRepository
import com.neo.data.repository.CommentRepository
import com.neo.data.repository.DeviceRepository
import com.neo.data.repository.PostRepository
import com.neo.data.repository.ReactionRepository
import com.neo.media.ImageChunker
import com.neo.media.ImageFileStore
import com.neo.security.CryptoManager
import com.neo.security.RateLimiter
import io.mockk.*
import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GossipProtocolImageChunkTest {

    private lateinit var postRepository: PostRepository
    private lateinit var imageFileStore: ImageFileStore
    private lateinit var gossipProtocol: GossipProtocol

    @Before
    fun setup() {
        postRepository = mockk()
        imageFileStore = mockk(relaxed = true)
        gossipProtocol = GossipProtocol(
            postRepository = postRepository,
            deviceRepository = mockk<DeviceRepository>(relaxed = true),
            cryptoManager = mockk<CryptoManager>(relaxed = true),
            blockedUserRepository = mockk<BlockedUserRepository>(relaxed = true),
            commentRepository = mockk<CommentRepository>(relaxed = true),
            reactionRepository = mockk<ReactionRepository>(relaxed = true),
            ackManager = mockk<AckManager>(relaxed = true),
            conflictResolver = mockk<ConflictResolver>(relaxed = true),
            seenMessageCache = mockk<SeenMessageCache>(relaxed = true),
            rateLimiter = mockk<RateLimiter>(relaxed = true),
            imageFileStore = imageFileStore,
            eventLogDao = mockk<EventLogDao>(relaxed = true),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        )
    }

    @Test
    fun `assembled image is saved when hash matches post metadata`() = runTest {
        val imageBytes = "valid image bytes".toByteArray()
        val imageHash = sha256(imageBytes)
        val chunk = singleChunkMessage(imageBytes)
        coEvery { postRepository.getPostById("post-1") } returns postWithImageHash(imageHash)
        coEvery { postRepository.updatePost(any()) } just Runs
        every { imageFileStore.save(imageHash, imageBytes) } returns "/tmp/$imageHash.jpg"

        gossipProtocol.handleImageChunk(chunk, "peer-1")

        verify { imageFileStore.save(imageHash, imageBytes) }
    }

    @Test
    fun `assembled image is discarded when hash does not match post metadata`() = runTest {
        val imageBytes = "corrupt image bytes".toByteArray()
        val chunk = singleChunkMessage(imageBytes)
        coEvery { postRepository.getPostById("post-1") } returns postWithImageHash("expected-different-hash")

        gossipProtocol.handleImageChunk(chunk, "peer-1")

        verify(exactly = 0) { imageFileStore.save(any(), any()) }
    }

    private fun singleChunkMessage(imageBytes: ByteArray): Message.ImageChunk {
        val encoded = Base64.getEncoder().encodeToString(imageBytes)
        val chunk = ImageChunker().chunkImage(encoded, "post-1").single()
        return Message.ImageChunk(
            postId = chunk.postId,
            chunkIndex = chunk.chunkIndex,
            totalChunks = chunk.totalChunks,
            data = chunk.data,
            checksum = chunk.checksum
        )
    }

    private fun postWithImageHash(imageHash: String): Post {
        return Post(
            id = "post-1",
            authorId = "author-1",
            authorName = "Alice",
            content = "Post with image",
            imageHash = imageHash,
            imageSize = 100,
            imageWidth = 10,
            imageHeight = 10,
            timestamp = 1000L,
            signature = "sig",
            publicKey = "pk",
            ttl = 7,
            firstSeenTimestamp = 1000L
        )
    }

    private fun sha256(data: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(data)
            .joinToString("") { "%02x".format(it) }
    }
}
