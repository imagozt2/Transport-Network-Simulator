package com.rmm.app.core.session

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal interface SessionCipher {
    fun encrypt(plainText: ByteArray): String

    fun decrypt(payload: String): ByteArray

    fun resetKey()
}

internal class AndroidKeystoreSessionCipher : SessionCipher {
    override fun encrypt(plainText: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(ASSOCIATED_DATA)

        val encrypted = cipher.doFinal(plainText)
        return listOf(
            PAYLOAD_VERSION,
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
            Base64.encodeToString(encrypted, Base64.NO_WRAP),
        ).joinToString(PAYLOAD_SEPARATOR)
    }

    override fun decrypt(payload: String): ByteArray {
        val parts = payload.split(PAYLOAD_SEPARATOR, limit = 3)
        require(parts.size == 3 && parts[0] == PAYLOAD_VERSION) {
            "Formato de sesion cifrada no compatible"
        }

        val initializationVector = Base64.decode(parts[1], Base64.NO_WRAP)
        require(initializationVector.size == GCM_IV_SIZE_BYTES) {
            "Vector de inicializacion no valido"
        }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(GCM_TAG_SIZE_BITS, initializationVector),
        )
        cipher.updateAAD(ASSOCIATED_DATA)
        return cipher.doFinal(Base64.decode(parts[2], Base64.NO_WRAP))
    }

    override fun resetKey() {
        keyStore().deleteEntry(KEY_ALIAS)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = keyStore()
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        ).run {
            init(
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
            generateKey()
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "rmm_passenger_session_key_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PAYLOAD_VERSION = "1"
        const val PAYLOAD_SEPARATOR = "."
        const val GCM_TAG_SIZE_BITS = 128
        const val GCM_IV_SIZE_BYTES = 12
        val ASSOCIATED_DATA: ByteArray =
            "rmm-passenger-session-v1".toByteArray(StandardCharsets.UTF_8)
    }
}
