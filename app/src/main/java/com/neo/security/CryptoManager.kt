package com.neo.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
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
        private const val TAG = "CryptoManager"
        private const val PREF_PRIVATE_KEY = "private_key"
        private const val PREF_PUBLIC_KEY = "public_key"
        private const val PREF_DEVICE_ID = "device_id"
        private const val PREF_KEY_ALGORITHM = "key_algorithm"

        // Force RSA everywhere — Ed25519 availability varies wildly across
        // Android OEMs (present on Xiaomi, absent on Huawei). RSA is universal.
        private const val KEY_ALGORITHM = "RSA"
        private const val RSA_KEY_SIZE = 2048
    }
    
    init {
        // Generate keys on first launch, OR if previous keys used a different algorithm.
        // Always regenerate to ensure RSA is used for cross-OEM compatibility.
        val existingAlgorithm = getKeyAlgorithm()
        if (!hasKeys() || existingAlgorithm != KEY_ALGORITHM) {
            if (existingAlgorithm != KEY_ALGORITHM && hasKeys()) {
                Log.w(TAG, "Existing key is $existingAlgorithm — regenerating as $KEY_ALGORITHM for cross-device compatibility")
            }
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
        return sharedPreferences.getString(PREF_KEY_ALGORITHM, KEY_ALGORITHM)
            ?: KEY_ALGORITHM
    }
    
    /**
     * Generate keypair and store securely.
     * Tries Ed25519 first, falls back to RSA if not available.
     */
    private fun generateAndStoreKeys() {
        try {
            val keyPairGenerator = KeyPairGenerator.getInstance(KEY_ALGORITHM)
            keyPairGenerator.initialize(RSA_KEY_SIZE)
            val keyPair = keyPairGenerator.generateKeyPair()

            val privateKeyBytes = keyPair.private.encoded
            val publicKeyBytes = keyPair.public.encoded

            val privateKeyBase64 = Base64.encodeToString(privateKeyBytes, Base64.NO_WRAP)
            val publicKeyBase64 = Base64.encodeToString(publicKeyBytes, Base64.NO_WRAP)

            sharedPreferences.edit().apply {
                putString(PREF_PRIVATE_KEY, privateKeyBase64)
                putString(PREF_PUBLIC_KEY, publicKeyBase64)
                putString(PREF_DEVICE_ID, UUID.randomUUID().toString())
                putString(PREF_KEY_ALGORITHM, KEY_ALGORITHM)
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

    fun getPublicKeyString(): String = getPublicKey()

    fun getKeyAlgorithmPublic(): String = getKeyAlgorithm()
    
    private var cachedPrivateKey: PrivateKey? = null

    /**
     * Get the private key.
     */
    private fun getPrivateKey(): PrivateKey {
        return cachedPrivateKey ?: run {
            val privateKeyBase64 = sharedPreferences.getString(PREF_PRIVATE_KEY, null)
                ?: throw IllegalStateException("Private key not found")
            
            val privateKeyBytes = Base64.decode(privateKeyBase64, Base64.NO_WRAP)
            val keySpec = PKCS8EncodedKeySpec(privateKeyBytes)
            val key = KeyFactory.getInstance(getKeyAlgorithm()).generatePrivate(keySpec)
            cachedPrivateKey = key
            key
        }
    }
    
    /**
     * Sign a message with the device's private key.
     */
    fun sign(message: String): String {
        try {
            val privateKey = getPrivateKey()
            val signature = Signature.getInstance("SHA256withRSA")
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
     * @param keyAlgorithm The algorithm used by the signer ("Ed25519" or "RSA").
     *                     This MUST come from the message itself, not guessed locally,
     *                     because different Android OEMs have incompatible Ed25519 implementations.
     */
    fun verify(message: String, signatureBase64: String, publicKeyBase64: String, keyAlgorithm: String = KEY_ALGORITHM): Boolean {
        return try {
            val publicKeyBytes = Base64.decode(publicKeyBase64, Base64.NO_WRAP)
            val keySpec = X509EncodedKeySpec(publicKeyBytes)

            val keyFactory = KeyFactory.getInstance(keyAlgorithm)
            val publicKey = keyFactory.generatePublic(keySpec)
            val signatureAlgorithm = if (keyAlgorithm == "Ed25519") "Ed25519" else "SHA256withRSA"

            val signatureBytes = Base64.decode(signatureBase64, Base64.NO_WRAP)

            val signature = Signature.getInstance(signatureAlgorithm)
            signature.initVerify(publicKey)
            signature.update(message.toByteArray())
            val result = signature.verify(signatureBytes)
            if (!result) {
                Log.e(TAG, "Signature verification FAILED — algorithm=$keyAlgorithm, message=${message.take(80)}, pubKey=${publicKeyBase64.take(40)}..., sig=${signatureBase64.take(40)}...")
            } else {
                Log.d(TAG, "Signature verification OK — algorithm=$keyAlgorithm")
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Signature verification error: ${e.message} (algorithm=$keyAlgorithm, pubKey=${publicKeyBase64.take(40)}...)", e)
            false
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
