package com.neo.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
class CryptoManagerTest {

    private lateinit var cryptoManager: CryptoManager
    private val message = "test message to sign"

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        cryptoManager = CryptoManager(context)
    }

    @Test
    fun `sign and verify with valid signature returns true`() {
        val signature = cryptoManager.sign(message)
        val publicKey = cryptoManager.getPublicKey()

        val isValid = cryptoManager.verify(message, signature, publicKey)

        assertTrue(isValid)
    }

    @Test
    fun `verify with invalid signature returns false`() {
        val publicKey = cryptoManager.getPublicKey()

        val isValid = cryptoManager.verify(message, "invalid-signature", publicKey)

        assertFalse(isValid)
    }

    @Test
    fun `verify with tampered message returns false`() {
        val originalMessage = "original message"
        val signature = cryptoManager.sign(originalMessage)
        val publicKey = cryptoManager.getPublicKey()
        val tamperedMessage = "tampered message"

        val isValid = cryptoManager.verify(tamperedMessage, signature, publicKey)

        assertFalse(isValid)
    }

    @Test
    fun `different messages produce different signatures`() {
        val signature1 = cryptoManager.sign("message 1")
        val signature2 = cryptoManager.sign("message 2")

        assertNotEquals(signature1, signature2)
    }

    @Test
    fun `getDeviceId returns non-empty string`() {
        val deviceId = cryptoManager.getDeviceId()

        assertNotNull(deviceId)
        assertTrue(deviceId.isNotEmpty())
    }

    @Test
    fun `getDeviceId is stable across calls`() {
        val deviceId1 = cryptoManager.getDeviceId()
        val deviceId2 = cryptoManager.getDeviceId()

        assertEquals(deviceId1, deviceId2)
    }

    @Test
    fun `public key is Base64 encoded`() {
        val publicKey = cryptoManager.getPublicKey()

        assertNotNull(publicKey)
        assertTrue(publicKey.isNotEmpty())
        val decoded = Base64.getDecoder().decode(publicKey)
        assertTrue(decoded.isNotEmpty())
    }

    @Test
    fun `verify with wrong public key returns false`() {
        val signature = cryptoManager.sign(message)
        val wrongKey = Base64.getEncoder().encodeToString("wrong-key".toByteArray())

        val isValid = cryptoManager.verify(message, signature, wrongKey)

        assertFalse(isValid)
    }
}
