package com.example.pocta

import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.annotation.RequiresApi
import java.lang.Exception
import java.security.*
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

class MyCredentialManager(private val context: Context) {
    private val defaultKeyString = "abcdefghijklmnop"
    companion object {
        const val KEY_ALIAS = "DebugImmoblizerAESKey"
        const val KEY_ALIAS_DEF = "ImmobilizerAESKey"
        const val TAG = "MyCredentialManager"
    }

    fun resetKey() = removeKeyStore()

    // TODO("Masih banyak voodoonya")
    fun getSymmetricKey(): SecretKey {
        return if (hasMarshmallow()) {
            val ks = createKeyStore()
            if (!isKeyExists(ks)) {
                createSymmetricKey()
            }
            ks.getKey(KEY_ALIAS, null) as SecretKey
        } else {
            val keyGenerator = KeyGenerator.getInstance("AES")
            keyGenerator.init(128)
            keyGenerator.generateKey()
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun createSymmetricKey(): SecretKey {
        try {
            val keygen =
                KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_ECB)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .setKeySize(128)
                .build()
            keygen.init(keyGenParameterSpec)
            return keygen.generateKey()
        } catch (e: NoSuchAlgorithmException) {
            Log.e(TAG, "Failed to create a symmetric key", e)
            return getDefaultSymmetricKey()
        } catch (e: NoSuchProviderException) {
            Log.e(TAG, "Failed to create a symmetric key", e)
            return getDefaultSymmetricKey()
        } catch (e: InvalidAlgorithmParameterException) {
            Log.e(TAG, "Failed to create a symmetric key", e)
            return getDefaultSymmetricKey()
        }
    }

    fun removeKeyStore() {
        val ks = createKeyStore()
        Log.i(ConnectActivity.lt, "ConnectActivity: removing secret key.")
        ks.deleteEntry(KEY_ALIAS)
    }

    private fun isKeyExists(ks: KeyStore): Boolean {
        val aliases = ks.aliases()
        while (aliases.hasMoreElements()) {
            return (KEY_ALIAS == aliases.nextElement())
        }
        return false
    }

    private fun createKeyStore(): KeyStore {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        return keyStore
    }

    private fun getDefaultSymmetricKey(): SecretKey {
        val secretKeyByteArray = defaultKeyString.toByteArray()
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
    fun getStoredKey(): SecretKey {
        val cipherKeyStr = context.getSharedPreferences("PREFS", 0)
            .getString("CIPHERKEY", defaultKeyString)
            ?: defaultKeyString
        if (cipherKeyStr == defaultKeyString) {
            return getDefaultSymmetricKey()
        } else {
            val encodedKey = Base64.decode(cipherKeyStr, Base64.DEFAULT)
            return SecretKeySpec(encodedKey, 0, encodedKey.size, "AES")
        }
    }

    // TODO("Ubah penyimpanan cipher key tidak menggunakan plaintext")
    fun setStoredKey(newKey: SecretKey?) {
        if (newKey != null) {
            val newKeyStr = Base64.encodeToString(newKey.encoded, Base64.DEFAULT)
            val editor = context.getSharedPreferences("PREFS", 0)
                    .edit()
                    .putString("CIPHERKEY", newKeyStr)
                    .apply()
        }
    }

    fun getStoredRSAKeyPair() : KeyPair {
        return if (hasMarshmallow()) {
            createAsymmetricKeyPair()
            getAsymmetricKeyPair()!!
        } else {
            createAsymmetricKeyPair()
        }
    }
}