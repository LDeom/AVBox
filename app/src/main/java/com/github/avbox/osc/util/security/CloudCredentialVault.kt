package com.github.avbox.osc.util.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.github.avbox.osc.util.AppContextHolder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileNotFoundException
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

object CloudCredentialVault {

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "avbox_cloud_credentials_v1"
    private val lock = Any()
    private val gson = Gson()
    private val entriesType = object : TypeToken<LinkedHashMap<String, String>>() {}.type

    @JvmStatic
    fun read(providerId: String): String? = synchronized(lock) {
        validateProviderId(providerId)
        readEntries()[providerId]
    }

    @JvmStatic
    fun write(providerId: String, credential: String) = synchronized(lock) {
        validateProviderId(providerId)
        require(credential.isNotBlank()) { "Credential must not be blank" }
        val entries = readEntries()
        entries[providerId] = credential
        persist(entries)
    }

    @JvmStatic
    fun remove(providerId: String) = synchronized(lock) {
        validateProviderId(providerId)
        val entries = readEntries()
        if (entries.remove(providerId) != null) persist(entries)
    }

    @JvmStatic
    fun clear() = synchronized(lock) {
        credentialFile().delete()
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keyStore.load(null)
        if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
    }

    private fun readEntries(): LinkedHashMap<String, String> {
        val file = credentialFile()
        val encrypted = try {
            file.openRead().use { it.readBytes() }
        } catch (error: FileNotFoundException) {
            return LinkedHashMap()
        }
        val plain = AesGcmEnvelope.decrypt(encrypted, existingKey())
        val decoded = gson.fromJson<LinkedHashMap<String, String>>(String(plain, StandardCharsets.UTF_8), entriesType)
            ?: throw IllegalStateException("Cloud credential data is empty")
        return LinkedHashMap(decoded)
    }

    private fun persist(entries: LinkedHashMap<String, String>) {
        val file = credentialFile()
        if (entries.isEmpty()) {
            file.delete()
            return
        }
        val plain = gson.toJson(entries).toByteArray(StandardCharsets.UTF_8)
        val encrypted = AesGcmEnvelope.encrypt(plain, getOrCreateKey())
        val output = file.startWrite()
        try {
            output.write(encrypted)
            output.fd.sync()
            file.finishWrite(output)
        } catch (error: Throwable) {
            file.failWrite(output)
            throw error
        }
    }

    private fun credentialFile(): AtomicFile {
        val context = requireNotNull(AppContextHolder.context()) { "Application context is unavailable" }
        return AtomicFile(File(context.filesDir, "cloud_credentials.enc"))
    }

    private fun existingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keyStore.load(null)
        val key = keyStore.getKey(KEY_ALIAS, null)
        return key as? SecretKey ?: throw IllegalStateException("Cloud credential key is unavailable; sign in again")
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keyStore.load(null)
        val existing = keyStore.getKey(KEY_ALIAS, null)
        if (existing != null) {
            return existing as? SecretKey ?: throw IllegalStateException("Cloud credential key has an invalid type")
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private fun validateProviderId(providerId: String) {
        require(providerId.matches(Regex("[a-z0-9_-]{1,40}"))) { "Invalid cloud provider id" }
    }
}
