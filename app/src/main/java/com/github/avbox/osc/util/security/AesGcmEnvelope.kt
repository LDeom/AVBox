package com.github.avbox.osc.util.security

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object AesGcmEnvelope {

    private const val VERSION = 1
    private const val IV_SIZE = 12
    private const val TAG_SIZE_BITS = 128
    private const val HEADER_SIZE = 2

    @JvmStatic
    fun encrypt(plainText: ByteArray, key: SecretKey): ByteArray {
        val iv = ByteArray(IV_SIZE)
        SecureRandom().nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_SIZE_BITS, iv))
        val encrypted = cipher.doFinal(plainText)
        return ByteBuffer.allocate(HEADER_SIZE + iv.size + encrypted.size)
            .put(VERSION.toByte())
            .put(iv.size.toByte())
            .put(iv)
            .put(encrypted)
            .array()
    }

    @JvmStatic
    fun decrypt(envelope: ByteArray, key: SecretKey): ByteArray {
        require(envelope.size > HEADER_SIZE + IV_SIZE) { "Invalid credential envelope" }
        val buffer = ByteBuffer.wrap(envelope)
        val version = buffer.get().toInt() and 0xff
        require(version == VERSION) { "Unsupported credential envelope version" }
        val ivSize = buffer.get().toInt() and 0xff
        require(ivSize == IV_SIZE && buffer.remaining() > ivSize) { "Invalid credential envelope" }
        val iv = ByteArray(ivSize)
        buffer.get(iv)
        val encrypted = ByteArray(buffer.remaining())
        buffer.get(encrypted)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_SIZE_BITS, iv))
        return cipher.doFinal(encrypted)
    }
}
