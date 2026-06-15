package com.neo.domain.usecase

import com.neo.data.dao.EventLogDao
import com.neo.data.model.Post
import com.neo.data.repository.PostRepository
import com.neo.domain.port.IImageCompressor
import com.neo.domain.port.ISyncPort
import com.neo.domain.port.CompressedImageResult
import com.neo.media.ImageFileStore
import com.neo.security.CryptoManager
import com.neo.security.IdentityManager
import com.neo.security.RateLimiter
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.SecureRandom

class CreatePostUseCaseTest {

    private val postRepository: PostRepository = mockk()
    private val cryptoManager: CryptoManager = mockk()
    private val rateLimiter: RateLimiter = mockk()
    private val imageFileStore: ImageFileStore = mockk()
    private val imageCompressor: IImageCompressor = mockk()
    private val syncPort: ISyncPort = mockk()
    private val identityManager: IdentityManager = mockk()
    private val eventLogDao: com.neo.data.dao.EventLogDao = mockk(relaxed = true)

    // Real RSA keypair for signing in tests (mockk relaxed PrivateKey doesn't work)
    private val testKeyPair = KeyPairGenerator.getInstance("RSA").apply {
        initialize(2048, SecureRandom())
    }.generateKeyPair()
    private val testIdentity = IdentityManager.Identity(
        seed = ByteArray(32),
        privateKey = testKeyPair.private,
        publicKey = testKeyPair.public,
        did = "did:key:testdevice"
    )

    private lateinit var useCase: CreatePostUseCase

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        useCase = CreatePostUseCase(
            postRepository = postRepository,
            cryptoManager = cryptoManager,
            rateLimiter = rateLimiter,
            imageFileStore = imageFileStore,
            imageCompressor = imageCompressor,
            syncPort = syncPort,
            identityManager = identityManager,
            eventLogDao = eventLogDao
        )
    }

    @Test
    fun `create post with valid content and author succeeds`() = runTest {
        val deviceId = "device-1"
        val expectedPubKey = java.util.Base64.getEncoder().encodeToString(testKeyPair.public.encoded)
        every { cryptoManager.getDeviceId() } returns deviceId
        every { cryptoManager.getKeyAlgorithmPublic() } returns "RSA"
        every { rateLimiter.canCreatePost(deviceId) } returns true
        coEvery { postRepository.insertPost(any()) } returns true
        coEvery { syncPort.broadcastPost(any()) } just Runs
        every { cryptoManager.createPostMessage(any(), any(), any(), any()) } returns "message"
        every { cryptoManager.sign(any()) } returns "sig"
        coEvery { identityManager.getOrCreateIdentity() } returns testIdentity
        coEvery { eventLogDao.getLastSequenceNum(any()) } returns 0L
        coEvery { eventLogDao.insertEvent(any()) } returns 1L

        val result = useCase("Test content", "Test Author")

        assertTrue(result.isSuccess)
        val post = result.getOrNull()
        assertEquals("Test Author", post?.authorName)
        assertEquals("Test content", post?.content)
        // Use case now uses the DID as the authorId (post v11+ schema).
        assertEquals(testIdentity.did, post?.authorId)
        assertEquals(expectedPubKey, post?.publicKey)
        assertNotNull(post?.signature)
        assertEquals(7, post?.ttl)
        coVerify { postRepository.insertPost(any()) }
        coVerify { syncPort.broadcastPost(any()) }
    }

    @Test
    fun `create post with blank content fails`() = runTest {
        every { cryptoManager.getDeviceId() } returns "device-1"
        every { rateLimiter.canCreatePost(any()) } returns true
        coEvery { identityManager.getOrCreateIdentity() } returns testIdentity

        val result = useCase("", "Author")

        assertTrue(result.isFailure)
        assertEquals("Content cannot be empty", result.exceptionOrNull()?.message)
    }

    @Test
    fun `create post with blank author fails`() = runTest {
        every { cryptoManager.getDeviceId() } returns "device-1"
        every { rateLimiter.canCreatePost(any()) } returns true
        coEvery { identityManager.getOrCreateIdentity() } returns testIdentity

        val result = useCase("Content", "")

        assertTrue(result.isFailure)
        assertEquals("Author name cannot be empty", result.exceptionOrNull()?.message)
    }

    @Test
    fun `create post exceeding max length fails`() = runTest {
        every { cryptoManager.getDeviceId() } returns "device-1"
        every { rateLimiter.canCreatePost(any()) } returns true
        coEvery { identityManager.getOrCreateIdentity() } returns testIdentity

        val longContent = "A".repeat(CreatePostUseCase.MAX_CONTENT_LENGTH + 1)
        val result = useCase(longContent, "Author")

        assertTrue(result.isFailure)
        assertEquals("Content exceeds maximum length", result.exceptionOrNull()?.message)
    }

    @Test
    fun `create post when rate limited fails`() = runTest {
        val deviceId = "device-1"
        every { cryptoManager.getDeviceId() } returns deviceId
        every { rateLimiter.canCreatePost(deviceId) } returns false
        every { rateLimiter.getTimeUntilNextPost(deviceId) } returns 120000L
        coEvery { identityManager.getOrCreateIdentity() } returns testIdentity

        val result = useCase("Content", "Author")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Rate limit") == true)
        coVerify(exactly = 0) { postRepository.insertPost(any()) }
        coVerify(exactly = 0) { syncPort.broadcastPost(any()) }
    }

    @Test
    fun `create post with image compression success`() = runTest {
        val compressedResult = CompressedImageResult("hash-123", ByteArray(100), 100, 200, 200)
        every { cryptoManager.getDeviceId() } returns "device-1"
        every { cryptoManager.getKeyAlgorithmPublic() } returns "RSA"
        every { rateLimiter.canCreatePost(any()) } returns true
        every { cryptoManager.createPostMessage(any(), any(), any(), any()) } returns "message"
        coEvery { imageCompressor.compress("content://image.jpg") } returns compressedResult
        coEvery { imageFileStore.save(any(), any()) } returns "/path/to/image.jpg"
        coEvery { postRepository.insertPost(any()) } returns true
        coEvery { syncPort.broadcastPost(any()) } just Runs
        coEvery { identityManager.getOrCreateIdentity() } returns testIdentity
        coEvery { eventLogDao.getLastSequenceNum(any()) } returns 0L
        coEvery { eventLogDao.insertEvent(any()) } returns 1L

        val result = useCase("Content", "Author", "content://image.jpg")

        assertTrue(result.isSuccess)
        val post = result.getOrNull()
        assertEquals("hash-123", post?.imageHash)
        assertEquals(100, post?.imageSize)
        assertEquals(200, post?.imageWidth)
        assertEquals(200, post?.imageHeight)
        coVerify { imageFileStore.save("hash-123", any()) }
    }

    @Test
    fun `create post with image compression failure fails`() = runTest {
        every { cryptoManager.getDeviceId() } returns "device-1"
        every { rateLimiter.canCreatePost(any()) } returns true
        coEvery { identityManager.getOrCreateIdentity() } returns testIdentity
        coEvery { imageCompressor.compress(any()) } returns null

        val result = useCase("Content", "Author", "content://bad.jpg")

        assertTrue(result.isFailure)
        assertEquals("Failed to compress image", result.exceptionOrNull()?.message)
        coVerify(exactly = 0) { postRepository.insertPost(any()) }
    }

    @Test
    fun `create post calls broadcast after insert`() = runTest {
        every { cryptoManager.getDeviceId() } returns "device-1"
        every { cryptoManager.getKeyAlgorithmPublic() } returns "RSA"
        every { rateLimiter.canCreatePost(any()) } returns true
        every { cryptoManager.createPostMessage(any(), any(), any(), any()) } returns "message"
        coEvery { postRepository.insertPost(any()) } returns true
        coEvery { syncPort.broadcastPost(any()) } just Runs
        coEvery { identityManager.getOrCreateIdentity() } returns testIdentity
        coEvery { eventLogDao.getLastSequenceNum(any()) } returns 0L
        coEvery { eventLogDao.insertEvent(any()) } returns 1L

        val result = useCase("Content", "Author")

        assertTrue(result.isSuccess)
        coVerifyOrder {
            postRepository.insertPost(any())
            syncPort.broadcastPost(any())
        }
    }

    @Test
    fun `create post repository failure returns error`() = runTest {
        every { cryptoManager.getDeviceId() } returns "device-1"
        every { cryptoManager.getKeyAlgorithmPublic() } returns "RSA"
        every { rateLimiter.canCreatePost(any()) } returns true
        coEvery { identityManager.getOrCreateIdentity() } returns testIdentity
        coEvery { postRepository.insertPost(any()) } throws RuntimeException("DB error")

        val result = useCase("Content", "Author")

        assertTrue(result.isFailure)
    }

    @Test
    fun `create post populates all fields correctly`() = runTest {
        val deviceId = "device-1"
        every { cryptoManager.getDeviceId() } returns deviceId
        every { cryptoManager.getKeyAlgorithmPublic() } returns "RSA"
        every { rateLimiter.canCreatePost(any()) } returns true
        every { cryptoManager.createPostMessage(any(), any(), any(), any()) } returns "message"
        coEvery { syncPort.broadcastPost(any()) } just Runs
        coEvery { identityManager.getOrCreateIdentity() } returns testIdentity
        coEvery { eventLogDao.getLastSequenceNum(any()) } returns 0L
        coEvery { eventLogDao.insertEvent(any()) } returns 1L

        val capturedPostSlot = slot<Post>()
        coEvery { postRepository.insertPost(capture(capturedPostSlot)) } returns true

        val result = useCase("Hello World", "Alice")

        assertTrue(result.isSuccess)
        val post = capturedPostSlot.captured
        assertEquals("Alice", post.authorName)
        assertEquals("Hello World", post.content)
        // Use case now uses the DID as the authorId (post v11+ schema).
        assertEquals(testIdentity.did, post.authorId)
        assertTrue(post.id.isNotEmpty())
        assertTrue(post.timestamp > 0)
        assertNotNull(post.signature)
        assertNotNull(post.publicKey)
        assertEquals(7, post.ttl)
    }

    @Test
    fun `create post uses crypto signature chain`() = runTest {
        every { cryptoManager.getDeviceId() } returns "device-1"
        every { cryptoManager.getKeyAlgorithmPublic() } returns "RSA"
        every { rateLimiter.canCreatePost(any()) } returns true
        every { cryptoManager.createPostMessage(any(), any(), any(), any()) } returns "message"
        coEvery { postRepository.insertPost(any()) } returns true
        coEvery { syncPort.broadcastPost(any()) } just Runs
        coEvery { identityManager.getOrCreateIdentity() } returns testIdentity
        coEvery { eventLogDao.getLastSequenceNum(any()) } returns 0L
        coEvery { eventLogDao.insertEvent(any()) } returns 1L

        useCase("Content", "Author")

        coVerify { cryptoManager.getDeviceId() }
        coVerify { identityManager.getOrCreateIdentity() }
    }
}
