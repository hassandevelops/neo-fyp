package com.neo.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.UUID

/**
 * Manages cryptographic operations for Neo.
 * Handles key generation, signing, and verification using Ed25519.
 */
class CryptoManager(context: Context) {
    
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "crypto_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    companion object {
        private const val PREF_PRIVATE_KEY = "private_key"
        private const val PREF_PUBLIC_KEY = "public_key"
        private const val PREF_DEVICE_ID = "device_id"
        private const val PREF_KEY_ALGORITHM = "key_algorithm"
        
        // Try Ed25519 first, fallback to RSA if not available
        private const val PREFERRED_ALGORITHM = "Ed25519"
        private const val FALLBACK_ALGORITHM = "RSA"
        private const val RSA_KEY_SIZE = 2048
    }
    
    init {
        // Generate keys on first launch
        if (!hasKeys()) {
            generateAndStoreKeys()
        }
    }
    
    /**
     * Check if keys already exist.
     */
    private fun hasKeys(): Boolean {
        return sharedPreferences.contains(PREF_PRIVATE_KEY) &&
                sharedPreferences.contains(PREF_PUBLIC_KEY)
    }
    
    /**
     * Get the algorithm being used for this device.
     */
    private fun getKeyAlgorithm(): String {
        return sharedPreferences.getString(PREF_KEY_ALGORITHM, FALLBACK_ALGORITHM) 
            ?: FALLBACK_ALGORITHM
    }
    
    /**
     * Generate keypair and store securely.
     * Tries Ed25519 first, falls back to RSA if not available.
     */
    private fun generateAndStoreKeys() {
        try {
            // Try Ed25519 first
            lateinit var keyPair: KeyPair
            var algorithm = PREFERRED_ALGORITHM
            
            try {
                val keyPairGenerator = KeyPairGenerator.getInstance(PREFERRED_ALGORITHM)
                keyPair = keyPairGenerator.generateKeyPair()
            } catch (e: Exception) {
                // Ed25519 not available, use RSA
                algorithm = FALLBACK_ALGORITHM
                val keyPairGenerator = KeyPairGenerator.getInstance(FALLBACK_ALGORITHM)
                keyPairGenerator.initialize(RSA_KEY_SIZE)
                keyPair = keyPairGenerator.generateKeyPair()
            }
            
            val privateKeyBytes = keyPair.private.encoded
            val publicKeyBytes = keyPair.public.encoded
            
            val privateKeyBase64 = Base64.encodeToString(privateKeyBytes, Base64.NO_WRAP)
            val publicKeyBase64 = Base64.encodeToString(publicKeyBytes, Base64.NO_WRAP)
            
            sharedPreferences.edit().apply {
                putString(PREF_PRIVATE_KEY, privateKeyBase64)
                putString(PREF_PUBLIC_KEY, publicKeyBase64)
                putString(PREF_DEVICE_ID, UUID.randomUUID().toString())
                putString(PREF_KEY_ALGORITHM, algorithm)
                apply()
            }
        } catch (e: Exception) {
            throw RuntimeException("Failed to generate cryptographic keys", e)
        }
    }
    
    /**
     * Get the device's unique ID.
     */
    fun getDeviceId(): String {
        return sharedPreferences.getString(PREF_DEVICE_ID, null)
            ?: throw IllegalStateException("Device ID not found")
    }
    
    /**
     * Get the public key as Base64 string.
     */
    fun getPublicKey(): String {
        return sharedPreferences.getString(PREF_PUBLIC_KEY, null)
            ?: throw IllegalStateException("Public key not found")
    }
    
    /**
     * Get the private key.
     */
    private fun getPrivateKey(): PrivateKey {
        val privateKeyBase64 = sharedPreferences.getString(PREF_PRIVATE_KEY, null)
            ?: throw IllegalStateException("Private key not found")
        
        val privateKeyBytes = Base64.decode(privateKeyBase64, Base64.NO_WRAP)
        val keySpec = PKCS8EncodedKeySpec(privateKeyBytes)
        val keyFactory = KeyFactory.getInstance(getKeyAlgorithm())
        return keyFactory.generatePrivate(keySpec)
    }
    
    /**
     * Sign a message with the device's private key.
     */
    fun sign(message: String): String {
        try {
            val privateKey = getPrivateKey()
            val algorithm = getKeyAlgorithm()
            val signatureAlgorithm = if (algorithm == "Ed25519") "Ed25519" else "SHA256withRSA"
            
            val signature = Signature.getInstance(signatureAlgorithm)
            signature.initSign(privateKey)
            signature.update(message.toByteArray())
            val signatureBytes = signature.sign()
            return Base64.encodeToString(signatureBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            throw RuntimeException("Failed to sign message", e)
        }
    }
    
    /**
     * Verify a signature using the provided public key.
     */
    fun verify(message: String, signatureBase64: String, publicKeyBase64: String): Boolean {
        return try {
            val publicKeyBytes = Base64.decode(publicKeyBase64, Base64.NO_WRAP)
            val keySpec = X509EncodedKeySpec(publicKeyBytes)
            
            // Try both algorithms for compatibility
            var publicKey: PublicKey? = null
            var signatureAlgorithm: String? = null
            
            try {
                val keyFactory = KeyFactory.getInstance("Ed25519")
                publicKey = keyFactory.generatePublic(keySpec)
                signatureAlgorithm = "Ed25519"
            } catch (e: Exception) {
                val keyFactory = KeyFactory.getInstance("RSA")
                publicKey = keyFactory.generatePublic(keySpec)
                signatureAlgorithm = "SHA256withRSA"
            }
            
            val signatureBytes = Base64.decode(signatureBase64, Base64.NO_WRAP)
            
            val signature = Signature.getInstance(signatureAlgorithm)
            signature.initVerify(publicKey)
            signature.update(message.toByteArray())
            signature.verify(signatureBytes)
        } catch (e: Exception) {
            false // Invalid signature or key
        }
    }
    
    /**
     * Create a canonical message string from post data for signing.
     */
    fun createPostMessage(
        id: String,
        authorId: String,
        content: String,
        timestamp: Long
    ): String {
        return "$id|$authorId|$content|$timestamp"
    }
}
