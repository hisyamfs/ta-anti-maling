package com.example.pocta

import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.jetbrains.anko.db.*
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

class MyCredentialManager(private val context: Context) {
    companion object {
        const val KEY_ALIAS = "ImmobilizerAESKey"
        const val TAG = "MyCredentialManager"
    }

    private fun createKeyStore(): KeyStore {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        return keyStore
    }

    fun getDefaultSymmetricKey(): SecretKey {
        val secretKeyByteArray = ByteArray(16) { _ -> 0} // 16-byte array of zeroes
        return SecretKeySpec(secretKeyByteArray, "AES")
    }

    private fun createAsymmetricKeyPair(): KeyPair {
        val generator: KeyPairGenerator

        if (hasMarshmallow()) {
            generator =
                KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
            getKeyGenParameterSpec(generator)
        } else {
            generator = KeyPairGenerator.getInstance("RSA")
            generator.initialize(2048)
        }

        return generator.generateKeyPair()
    }

    @TargetApi(23)
    private fun getKeyGenParameterSpec(generator: KeyPairGenerator) {
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_ECB)
            //.setUserAuthenticationRequired(true)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)

        generator.initialize(builder.build())
    }

    private fun getAsymmetricKeyPair(): KeyPair? {
        val keyStore: KeyStore = createKeyStore()
        val alias: String = KEY_ALIAS
        val privateKey = keyStore.getKey(alias, null) as PrivateKey?
        val publicKey = keyStore.getCertificate(alias)?.publicKey
        return if (privateKey != null && publicKey != null) {
            KeyPair(publicKey, privateKey)
        } else {
            null
        }
    }

    // TODO("Ubah penyimpanan cipher key tidak menggunakan plaintext")
    fun getStoredKey(immobilizerAddress: String): SecretKey {
//        val cipherKeyStr = context.getSharedPreferences("PREFS", 0)
//            .getString("CIPHERKEY", defaultKeyString)
//            ?: defaultKeyString
//        if (cipherKeyStr == defaultKeyString) {
//            return getDefaultSymmetricKey()
//        } else {
//            val encodedKey = Base64.decode(cipherKeyStr, Base64.DEFAULT)
//            return SecretKeySpec(encodedKey, 0, encodedKey.size, "AES")
//        }
        var immobilizer: Immobilizer? = null
        context.database.use {
            val result = select(Immobilizer.TABLE_IMMOBILIZER)
                .whereSimple("${Immobilizer.ADDRESS} = ?", immobilizerAddress)
            immobilizer = result.parseOpt(classParser())
        }
        val cipherKeyStr = immobilizer?.key
        return if (cipherKeyStr != null) {
            val encodedKey = Base64.decode(cipherKeyStr, Base64.DEFAULT)
            SecretKeySpec(encodedKey, 0, encodedKey.size, "AES")
        } else {
            getDefaultSymmetricKey()
        }
    }

    // TODO("Ubah penyimpanan cipher key tidak menggunakan plaintext")
    fun setStoredKey(immobilizerAddress: String, immobilizerName: String, newKey: SecretKey) {
        val newKeyStr: String = Base64.encodeToString(newKey.encoded, Base64.DEFAULT)
        context.database.use {
            replace(
                Immobilizer.TABLE_IMMOBILIZER,
                Immobilizer.ADDRESS to immobilizerAddress,
                Immobilizer.NAME to immobilizerName,
                Immobilizer.KEY to newKeyStr
            )
        }
    }

    fun getStoredRSAKeyPair() : KeyPair {
        var kp = if (hasMarshmallow()) {
            createAsymmetricKeyPair()
            getAsymmetricKeyPair()
        } else {
            createAsymmetricKeyPair()
        }
        if (kp == null) kp = createAsymmetricKeyPair()
        return kp
    }

    fun deleteAccount(immobilizerAddress: String): Int {
        var numDeleted: Int = 0
        context.database.use {
            numDeleted = delete(
                Immobilizer.TABLE_IMMOBILIZER,
                "${Immobilizer.ADDRESS} = {qAddress}",
                "qAddress" to immobilizerAddress
            )
        }
        return numDeleted
    }
}

fun hasMarshmallow(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
}