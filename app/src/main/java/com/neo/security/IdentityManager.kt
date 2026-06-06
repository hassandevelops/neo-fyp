package com.neo.security

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages deterministic, device-bound identity using RSA-2048.
 * Survives app uninstalls via EncryptedSharedPreferences (backed by Android Keystore).
 * Uses RSA-2048 instead of Ed25519 to guarantee cross-OEM compatibility (e.g., Huawei/Honor).
 */
@Singleton
class IdentityManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "neo_identity_prefs"
        private const val KEY_MASTER_SEED = "master_seed"
        private const val KEY_PRIVATE_KEY = "private_key"
        private const val KEY_PUBLIC_KEY = "public_key"
        private const val KEY_DID = "did_key"
        
        private const val KEY_ALGORITHM = "RSA"
        private const val KEY_SIZE = 2048
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /**
     * Retrieves the existing identity or creates a new one deterministically.
     */
    suspend fun getOrCreateIdentity(): Identity {
        return withContext(Dispatchers.IO) {
            val existingSeed = encryptedPrefs.getString(KEY_MASTER_SEED, null)
            
            if (existingSeed != null) {
                reconstructIdentity(existingSeed)
            } else {
                generateNewIdentity()
            }
        }
    }

    private fun generateNewIdentity(): Identity {
        // 1. Generate 32-byte master seed
        val seedBytes = ByteArray(32).apply { SecureRandom().nextBytes(this) }
        val seedBase64 = Base64.encodeToString(seedBytes, Base64.NO_WRAP)

        // 2. Deterministically derive RSA-2048 keypair from seed
        val secureRandom = SecureRandom(seedBytes)
        val keyPairGenerator = KeyPairGenerator.getInstance(KEY_ALGORITHM)
        keyPairGenerator.initialize(KEY_SIZE, secureRandom)
        val keyPair = keyPairGenerator.generateKeyPair()

        // 3. Format as did:key (Base64Url representation of public key for MVP)
        val didKey = "did:key:${Base64.encodeToString(keyPair.public.encoded, Base64.URL_SAFE or Base64.NO_WRAP)}"

        // 4. Persist securely
        encryptedPrefs.edit().apply {
            putString(KEY_MASTER_SEED, seedBase64)
            putString(KEY_PRIVATE_KEY, Base64.encodeToString(keyPair.private.encoded, Base64.NO_WRAP))
            putString(KEY_PUBLIC_KEY, Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP))
            putString(KEY_DID, didKey)
            apply()
        }

        return Identity(
            seed = seedBytes,
            privateKey = keyPair.private,
            publicKey = keyPair.public,
            did = didKey
        )
    }

    private fun reconstructIdentity(seedBase64: String): Identity {
        val seedBytes = Base64.decode(seedBase64, Base64.NO_WRAP)
        
        // Re-derive the exact same keypair
        val secureRandom = SecureRandom(seedBytes)
        val keyPairGenerator = KeyPairGenerator.getInstance(KEY_ALGORITHM)
        keyPairGenerator.initialize(KEY_SIZE, secureRandom)
        val keyPair = keyPairGenerator.generateKeyPair()

        val didKey = encryptedPrefs.getString(KEY_DID, "") ?: ""

        return Identity(
            seed = seedBytes,
            privateKey = keyPair.private,
            publicKey = keyPair.public,
            did = didKey
        )
    }

    /**
     * Exposes the PrivateKey for signing events.
     */
    fun getPrivateKey(): PrivateKey {
        val privateKeyBase64 = encryptedPrefs.getString(KEY_PRIVATE_KEY, null)
            ?: throw IllegalStateException("Identity not initialized. Call getOrCreateIdentity() first.")
        
        val keyBytes = Base64.decode(privateKeyBase64, Base64.NO_WRAP)
        val keySpec = PKCS8EncodedKeySpec(keyBytes)
        return java.security.KeyFactory.getInstance(KEY_ALGORITHM).generatePrivate(keySpec)
    }

    /**
     * Exposes the PublicKey for verification.
     */
    fun getPublicKey(): PublicKey {
        val publicKeyBase64 = encryptedPrefs.getString(KEY_PUBLIC_KEY, null)
            ?: throw IllegalStateException("Identity not initialized. Call getOrCreateIdentity() first.")
        
        val keyBytes = Base64.decode(publicKeyBase64, Base64.NO_WRAP)
        val keySpec = X509EncodedKeySpec(keyBytes)
        return java.security.KeyFactory.getInstance(KEY_ALGORITHM).generatePublic(keySpec)
    }

    /**
     * Returns the user's decentralized identifier.
     */
    fun getDid(): String {
        return encryptedPrefs.getString(KEY_DID, "") 
            ?: throw IllegalStateException("Identity not initialized. Call getOrCreateIdentity() first.")
    }

    data class Identity(
        val seed: ByteArray,
        val privateKey: PrivateKey,
        val publicKey: PublicKey,
        val did: String
    )
}