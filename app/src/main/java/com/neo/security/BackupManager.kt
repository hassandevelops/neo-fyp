package com.neo.security

import android.content.Context
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.neo.data.dao.EventLogDao
import com.neo.data.model.EventLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles encrypted export and import of the user's identity seed and event log.
 * Complies with Android Scoped Storage by returning/accepting byte arrays,
 * leaving the actual file I/O (ACTION_CREATE_DOCUMENT / ACTION_OPEN_DOCUMENT) to the UI layer.
 */
@Singleton
class BackupManager @Inject constructor(
    private val context: Context,
    private val eventLogDao: EventLogDao
) {
    companion object {
        private const val PREFS_NAME = "neo_identity_prefs"
        private const val KEY_MASTER_SEED = "master_seed"
        private const val SALT_LENGTH = 16
        private const val IV_LENGTH = 12
        private const val TAG_LENGTH = 128
        private const val ITERATIONS = 100000
    }

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class BackupData(
        val masterSeed: String,
        val events: List<EventLog>
    )

    /**
     * Generates an encrypted byte array of the user's master seed and recent events.
     * The UI layer should write this to a file chosen via Intent.ACTION_CREATE_DOCUMENT.
     */
    suspend fun generateEncryptedBackup(passphrase: String): ByteArray = withContext(Dispatchers.IO) {
        val prefs = getIdentityPrefs()
        val masterSeed = prefs.getString(KEY_MASTER_SEED, null) 
            ?: throw IllegalStateException("No identity found to backup. Create an account first.")
        
        // Limit to last 10k events for MVP to prevent massive backup files
        val events = eventLogDao.getRecentEvents(10000)
        
        val backupData = BackupData(masterSeed = masterSeed, events = events)
        val plaintext = json.encodeToString(backupData).toByteArray(Charsets.UTF_8)
        
        encryptData(plaintext, passphrase)
    }

    /**
     * Decrypts and restores the user's master seed and events from a backup file.
     * The UI layer should provide the Uri obtained via Intent.ACTION_OPEN_DOCUMENT.
     */
    suspend fun restoreFromEncryptedBackup(uri: Uri, passphrase: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val inputStream: InputStream = context.contentResolver.openInputStream(uri) 
                ?: return@withContext false
            
            val encryptedData = inputStream.readBytes()
            inputStream.close()
            
            val plaintext = decryptData(encryptedData, passphrase)
            val backupData = json.decodeFromString<BackupData>(String(plaintext, Charsets.UTF_8))
            
            // 1. Restore identity seed
            val prefs = getIdentityPrefs().edit()
            prefs.putString(KEY_MASTER_SEED, backupData.masterSeed)
            // Note: We do NOT restore the private/public keys directly. 
            // IdentityManager will deterministically re-derive them from the restored seed on next launch.
            prefs.apply()
            
            // 2. Restore events (OnConflictStrategy.IGNORE in DAO handles duplicates safely)
            eventLogDao.insertEvents(backupData.events)
            
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun getIdentityPrefs(): android.content.SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun encryptData(plaintext: ByteArray, passphrase: String): ByteArray {
        val salt = ByteArray(SALT_LENGTH).apply { SecureRandom().nextBytes(this) }
        val iv = ByteArray(IV_LENGTH).apply { SecureRandom().nextBytes(this) }
        
        val secretKey = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH, iv))
        
        val ciphertext = cipher.doFinal(plaintext)
        
        // Format: salt (16) + iv (12) + ciphertext
        return salt + iv + ciphertext
    }

    private fun decryptData(encryptedData: ByteArray, passphrase: String): ByteArray {
        if (encryptedData.size < SALT_LENGTH + IV_LENGTH) {
            throw IllegalArgumentException("Invalid backup file format")
        }
        
        val salt = encryptedData.copyOfRange(0, SALT_LENGTH)
        val iv = encryptedData.copyOfRange(SALT_LENGTH, SALT_LENGTH + IV_LENGTH)
        val ciphertext = encryptedData.copyOfRange(SALT_LENGTH + IV_LENGTH, encryptedData.size)
        
        val secretKey = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH, iv))
        
        return cipher.doFinal(ciphertext)
    }

    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, 256)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }
}