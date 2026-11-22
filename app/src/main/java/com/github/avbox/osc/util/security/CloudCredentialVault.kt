package com.github.avbox.osc.util.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.github.avbox.osc.util.AppContextHolder
import java.io.File
import java.io.FileNotFoundException
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

object CloudCredentialVault {

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS_PREFIX = "avbox_cloud_credentials_"
    private const val KEY_ALIAS_SUFFIX = "_v1"
    private const val CREDENTIAL_DIRECTORY = "cloud_credentials"
    private const val LEGACY_FILE_NAME = "cloud_credentials.enc"
    private const val LEGACY_KEY_ALIAS = "avbox_cloud_credentials_v1"
    private val lock = Any()

    @JvmStatic
    fun read(providerId: String): String? = synchronized(lock) {
        validateProviderId(providerId)
        val encrypted = try {
            credentialFile(providerId).openRead().use { it.readBytes() }
        } catch (error: FileNotFoundException) {
            return@synchronized null
        }
        val plain = AesGcmEnvelope.decrypt(encrypted, existingKey(providerId))
        String(plain, StandardCharsets.UTF_8)
    }

    @JvmStatic
    fun write(providerId: String, credential: String) = synchronized(lock) {
        validateProviderId(providerId)
        require(credential.isNotBlank()) { "Credential must not be blank" }
        val encrypted = AesGcmEnvelope.encrypt(
            credential.toByteArray(StandardCharsets.UTF_8),
            getOrCreateKey(providerId),
        )
        val file = credentialFile(providerId)
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

    @JvmStatic
    fun remove(providerId: String) = synchronized(lock) {
        validateProviderId(providerId)
        credentialFile(providerId).delete()
        deleteKey(providerId)
    }

    @JvmStatic
    fun clear() = synchronized(lock) {
        val context = requireNotNull(AppContextHolder.context()) { "Application context is unavailable" }
        val directory = File(context.filesDir, CREDENTIAL_DIRECTORY)
        directory.listFiles().orEmpty().forEach { AtomicFile(it).delete() }
        directory.delete()
        AtomicFile(File(context.filesDir, LEGACY_FILE_NAME)).delete()
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keyStore.load(null)
        val aliases = keyStore.aliases()
        while (aliases.hasMoreElements()) {
            val alias = aliases.nextElement()
            if (alias.startsWith(KEY_ALIAS_PREFIX) || alias == LEGACY_KEY_ALIAS) {
                keyStore.deleteEntry(alias)
            }
        }
    }

    private fun credentialFile(providerId: String): AtomicFile {
        val context = requireNotNull(AppContextHolder.context()) { "Application context is unavailable" }
        val directory = File(context.filesDir, CREDENTIAL_DIRECTORY)
        check(directory.exists() || directory.mkdirs()) { "Unable to create cloud credential directory" }
        return AtomicFile(File(directory, "$providerId.enc"))
    }

    private fun existingKey(providerId: String): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keyStore.load(null)
        val key = keyStore.getKey(keyAlias(providerId), null)
        return key as? SecretKey ?: throw IllegalStateException("Cloud credential key is unavailable; sign in again")
    }

    private fun getOrCreateKey(providerId: String): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keyStore.load(null)
        val alias = keyAlias(providerId)
        val existing = keyStore.getKey(alias, null)
        if (existing != null) {
            return existing as? SecretKey ?: throw IllegalStateException("Cloud credential key has an invalid type")
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
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

    private fun deleteKey(providerId: String) {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keyStore.load(null)
        val alias = keyAlias(providerId)
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
    }

    private fun keyAlias(providerId: String): String = KEY_ALIAS_PREFIX + providerId + KEY_ALIAS_SUFFIX

    private fun validateProviderId(providerId: String) {
        require(providerId.matches(Regex("[a-z0-9_-]{1,40}"))) { "Invalid cloud provider id" }
    }
}
