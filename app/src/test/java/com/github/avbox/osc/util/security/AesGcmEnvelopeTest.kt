package com.github.avbox.osc.util.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.GeneralSecurityException
import javax.crypto.KeyGenerator

class AesGcmEnvelopeTest {

    private fun key() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test
    fun encryptDecryptRoundTrip() {
        val key = key()
        val secret = "refresh-token-敏感内容".toByteArray(Charsets.UTF_8)
        val encrypted = AesGcmEnvelope.encrypt(secret, key)
        assertArrayEquals(secret, AesGcmEnvelope.decrypt(encrypted, key))
    }

    @Test
    fun envelopeStoresGeneratedTwelveByteIv() {
        val encrypted = AesGcmEnvelope.encrypt("secret".toByteArray(), key())
        assertTrue(encrypted[1].toInt() and 0xff == 12)
    }

    @Test
    fun encryptedPayloadChangesForSamePlainText() {
        val key = key()
        val plain = "same-secret".toByteArray()
        val first = AesGcmEnvelope.encrypt(plain, key)
        val second = AesGcmEnvelope.encrypt(plain, key)
        assertFalse(first.contentEquals(second))
        assertArrayEquals(plain, AesGcmEnvelope.decrypt(first, key))
        assertArrayEquals(plain, AesGcmEnvelope.decrypt(second, key))
    }

    @Test
    fun tamperedPayloadFailsAuthentication() {
        val key = key()
        val encrypted = AesGcmEnvelope.encrypt("secret".toByteArray(), key)
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()
        var failed = false
        try {
            AesGcmEnvelope.decrypt(encrypted, key)
        } catch (error: GeneralSecurityException) {
            failed = true
        }
        assertTrue(failed)
    }
}
