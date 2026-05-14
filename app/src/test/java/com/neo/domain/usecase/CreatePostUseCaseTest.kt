package com.neo.domain.usecase

import com.neo.data.model.Post
import com.neo.data.repository.PostRepository
import com.neo.domain.port.IImageCompressor
import com.neo.domain.port.ISyncPort
import com.neo.domain.port.CompressedImageResult
import com.neo.media.ImageFileStore
import com.neo.security.CryptoManager
import com.neo.security.RateLimiter
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CreatePostUseCaseTest {

    private val postRepository: PostRepository = mockk()
    private val cryptoManager: CryptoManager = mockk(relaxed = true)
    private val rateLimiter: RateLimiter = mockk()
    private val imageFileStore: ImageFileStore = mockk()
    private val imageCompressor: IImageCompressor = mockk()
    private val syncPort: ISyncPort = mockk()

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
            syncPort = syncPort
        )
    }

    @Test
    fun `create post with valid content and author succeeds`() = runTest {
        val deviceId = "device-1"
        val publicKey = "pubkey-base64"
        val signature = "sig-base64"
        coEvery { cryptoManager.getDeviceId() } returns deviceId
        coEvery { rateLimiter.canCreatePost(deviceId) } returns true
        coEvery { cryptoManager.getPublicKey() } returns publicKey
        coEvery { cryptoManager.sign(any()) } returns signature
        coEvery { postRepository.insertPost(any()) } returns true
        coEvery { syncPort.broadcastPost(any()) } just Runs

        val result = useCase("Test content", "Test Author")

        assertTrue(result.isSuccess)
        val post = result.getOrNull()
        assertEquals("Test Author", post?.authorName)
        assertEquals("Test content", post?.content)
        assertEquals(deviceId, post?.authorId)
        assertEquals(publicKey, post?.publicKey)
        assertEquals(signature, post?.signature)
        assertEquals(7, post?.ttl)
        coVerify { postRepository.insertPost(any()) }
        coVerify { syncPort.broadcastPost(any()) }
    }

    @Test
    fun `create post with blank content fails`() = runTest {
        coEvery { cryptoManager.getDeviceId() } returns "device-1"
        coEvery { rateLimiter.canCreatePost(any()) } returns true

        val result = useCase("", "Author")

        assertTrue(result.isFailure)
        assertEquals("Content cannot be empty", result.exceptionOrNull()?.message)
    }

    @Test
    fun `create post with blank author fails`() = runTest {
        coEvery { cryptoManager.getDeviceId() } returns "device-1"
        coEvery { rateLimiter.canCreatePost(any()) } returns true

        val result = useCase("Content", "")

        assertTrue(result.isFailure)
        assertEquals("Author name cannot be empty", result.exceptionOrNull()?.message)
    }

    @Test
    fun `create post exceeding max length fails`() = runTest {
        coEvery { cryptoManager.getDeviceId() } returns "device-1"
        coEvery { rateLimiter.canCreatePost(any()) } returns true

        val longContent = "A".repeat(CreatePostUseCase.MAX_CONTENT_LENGTH + 1)
        val result = useCase(longContent, "Author")

        assertTrue(result.isFailure)
        assertEquals("Content exceeds maximum length", result.exceptionOrNull()?.message)
    }

    @Test
    fun `create post when rate limited fails`() = runTest {
        val deviceId = "device-1"
        coEvery { cryptoManager.getDeviceId() } returns deviceId
        coEvery { rateLimiter.canCreatePost(deviceId) } returns false
        coEvery { rateLimiter.getTimeUntilNextPost(deviceId) } returns 120000L

        val result = useCase("Content", "Author")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Rate limit") == true)
        coVerify(exactly = 0) { postRepository.insertPost(any()) }
        coVerify(exactly = 0) { syncPort.broadcastPost(any()) }
    }

    @Test
    fun `create post with image compression success`() = runTest {
        val compressedResult = CompressedImageResult("hash-123", ByteArray(100), 100, 200, 200)
        coEvery { cryptoManager.getDeviceId() } returns "device-1"
        coEvery { rateLimiter.canCreatePost(any()) } returns true
        coEvery { cryptoManager.getPublicKey() } returns "pubkey"
        coEvery { cryptoManager.sign(any()) } returns "sig"
        coEvery { imageCompressor.compress("content://image.jpg") } returns compressedResult
        coEvery { imageFileStore.save(any(), any()) } returns "/path/to/image.jpg"
        coEvery { postRepository.insertPost(any()) } returns true
        coEvery { syncPort.broadcastPost(any()) } just Runs

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
        coEvery { cryptoManager.getDeviceId() } returns "device-1"
        coEvery { rateLimiter.canCreatePost(any()) } returns true
        coEvery { imageCompressor.compress(any()) } returns null

        val result = useCase("Content", "Author", "content://bad.jpg")

        assertTrue(result.isFailure)
        assertEquals("Failed to compress image", result.exceptionOrNull()?.message)
        coVerify(exactly = 0) { postRepository.insertPost(any()) }
    }

    @Test
    fun `create post calls broadcast after insert`() = runTest {
        coEvery { cryptoManager.getDeviceId() } returns "device-1"
        coEvery { rateLimiter.canCreatePost(any()) } returns true
        coEvery { cryptoManager.getPublicKey() } returns "pubkey"
        coEvery { cryptoManager.sign(any()) } returns "sig"
        coEvery { postRepository.insertPost(any()) } returns true
        coEvery { syncPort.broadcastPost(any()) } just Runs

        val result = useCase("Content", "Author")

        assertTrue(result.isSuccess)
        coVerifyOrder {
            postRepository.insertPost(any())
            syncPort.broadcastPost(any())
        }
    }

    @Test
    fun `create post repository failure returns error`() = runTest {
        coEvery { cryptoManager.getDeviceId() } returns "device-1"
        coEvery { rateLimiter.canCreatePost(any()) } returns true
        coEvery { cryptoManager.getPublicKey() } returns "pubkey"
        coEvery { cryptoManager.sign(any()) } returns "sig"
        coEvery { postRepository.insertPost(any()) } throws RuntimeException("DB error")

        val result = useCase("Content", "Author")

        assertTrue(result.isFailure)
    }

    @Test
    fun `create post populates all fields correctly`() = runTest {
        val deviceId = "device-1"
        coEvery { cryptoManager.getDeviceId() } returns deviceId
        coEvery { rateLimiter.canCreatePost(any()) } returns true
        coEvery { cryptoManager.getPublicKey() } returns "pubkey"
        coEvery { cryptoManager.sign(any()) } returns "sig"
        coEvery { syncPort.broadcastPost(any()) } just Runs

        val capturedPostSlot = slot<Post>()
        coEvery { postRepository.insertPost(capture(capturedPostSlot)) } returns true

        val result = useCase("Hello World", "Alice")

        assertTrue(result.isSuccess)
        val post = capturedPostSlot.captured
        assertEquals("Alice", post.authorName)
        assertEquals("Hello World", post.content)
        assertEquals(deviceId, post.authorId)
        assertTrue(post.id.isNotEmpty())
        assertTrue(post.timestamp > 0)
        assertNotNull(post.signature)
        assertNotNull(post.publicKey)
        assertEquals(7, post.ttl)
    }

    @Test
    fun `create post uses crypto signature chain`() = runTest {
        coEvery { cryptoManager.getDeviceId() } returns "device-1"
        coEvery { rateLimiter.canCreatePost(any()) } returns true
        coEvery { cryptoManager.getPublicKey() } returns "pubkey"
        coEvery { cryptoManager.sign(any()) } returns "signed-message"
        coEvery { postRepository.insertPost(any()) } returns true
        coEvery { syncPort.broadcastPost(any()) } just Runs

        useCase("Content", "Author")

        coVerify { cryptoManager.getDeviceId() }
        coVerify { cryptoManager.getPublicKey() }
        coVerify { cryptoManager.sign(any()) }
    }
}
