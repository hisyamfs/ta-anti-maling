package com.example.pocta

import android.content.Context
import android.security.KeyPairGeneratorSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.math.BigInteger
import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import javax.security.auth.x500.X500Principal
import kotlin.math.abs

const val KEY_ALIAS = "ImmobilizerAESKey"

class UserCredentialManager(private val context: Context) {
    private val defPubKeyString = """
    -----BEGIN PUBLIC KEY-----
    MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAp6yN4qhtwMG0/O3yqULK
    hmRd/P+/bqySvlQ9xRZy2Jw8WYLTI9ruX7ToEKwmX7nErvOWJEHj7T03i6aeTymr
    mkX6TF9zyUu2WrETti+8QwlfeF58j2TFpqGtvJiuMVd78XuNdaWpvY0NIaUlDhBb
    snFkzhTcAERQEqEIIQEi65HE0NPuR7Nm4ErtXHYqftiom4Vdnt7DLKJX8k2iJERW
    PTi17HC8cfzHPcaN2D4SPmsogYlOkKaG45hJENjjGfghHIz3W1Xqj2yWjvQd/lIp
    pBBeiYHvkG5IMU+93vP/Gv3OI8DdJIUUrHBuft3BvlCh0daj8+ezYtvTA2M8pG+5
    kQIDAQAB
    -----END PUBLIC KEY-----"""
        .trimIndent()
        .replace("\n", "")
        .replace("-----BEGIN PUBLIC KEY-----", "")
        .replace("-----END PUBLIC KEY-----", "")

    private val defPrivKeyString = """
    -----BEGIN PRIVATE KEY-----
    MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCnrI3iqG3AwbT8
    7fKpQsqGZF38/79urJK+VD3FFnLYnDxZgtMj2u5ftOgQrCZfucSu85YkQePtPTeL
    pp5PKauaRfpMX3PJS7ZasRO2L7xDCV94XnyPZMWmoa28mK4xV3vxe411pam9jQ0h
    pSUOEFuycWTOFNwARFASoQghASLrkcTQ0+5Hs2bgSu1cdip+2KibhV2e3sMsolfy
    TaIkRFY9OLXscLxx/Mc9xo3YPhI+ayiBiU6QpobjmEkQ2OMZ+CEcjPdbVeqPbJaO
    9B3+UimkEF6Jge+QbkgxT73e8/8a/c4jwN0khRSscG5+3cG+UKHR1qPz57Ni29MD
    Yzykb7mRAgMBAAECggEBAJbIdsOoQSKBT7fQZ1LNDIE0isz0U/s7166u5OlymY6v
    WRoJqsPookqQzcwIc23MCdJmnNM4KbbzQRsll+GKkJXobgD2KZKQsoj2CsrgPIVw
    TVlaZtswfQmvBSS/jI40pPHw8LImavFZgcCK2Tq/fSaIEGW+nmTjCbrm8v9zHSsG
    8ui8Cd9eHE8XpFsCubAQgtQnFP7JXI1LA9aZ9aBioI91SHtO/QtNkNi7vRuInXX/
    vf7F2kaNPIWnFC9MW9uzPFOKYUyTEOiA/4bFstMas/lLEQVnhGejea2j3Rz2PnHX
    kPcQYwwpOY64Qa7QH+qWCUJI5DtBJjkABzFtoMb4XC0CgYEA2w+bJMyIfASLK+P6
    JjyzPPU6NpFTE4t7wflJZ3sxv1YGZCR5I8xhntrHf3/KZiXlNydWYUtlE4Cx1j8/
    oIRaVcyn6oxMMp5e3CDWxejNIs172j0SWllt7GrbiWPdp0p8SEU9LwHlee71yTp6
    kA1WTBsG7Sh9HoaS7J72fE8FgQsCgYEAw/KvPkmivvsp1CDuW+UTYrl/bMDrhoai
    R0YkGS+UpX9wCTYoaPbOS9outBpAEpjtsHryf/ZRh/IkXGc8G3fysocLY4p8w/Zy
    8TgV+pKAJxT+3kX6i5IteLRG+XMi+i8l3siM/iZA8AWHrNVsIn+Bw/h4nE0jEWVL
    eZileKnIiVMCgYBbi74ONtui2FNA2FkluaA+DU1ymHDbbiMeAQvIDxfPGig5mXR2
    nWb+d/d/NOxkm9manvneVx+6csHfAzeX4TfPO2PBBTiivsRtwdt/gbaYoL7tiTAu
    SclCT7XHSNDMpLgji6vyBRzdRBu7KJEnuisiSvkuCwmexCaKdDQV5wAp2QKBgEq1
    TaFm+9jq9AC/6YE57tE2PmIdj+8Dh/26vWqo3HjZBMNOVcvnRbJf5myekY1Fp2Ih
    DjJBnMZDSR+98IncirkMiggStg0U+rADnUWi859y/tWKQsNSIWoi+eiDwHM45Kxz
    NGZ1+U5KHXeFC6x/ht9L7dhSBKvOPh+HVpeRzDanAoGALdbAnJoq2qAir8e7xiH5
    YNObTBIHs0iVzMnTVoMuXKwbgpAecZQS5xl96HNnMmp+E/XhpCX4AXi9B9tYBeDn
    ICW0FnTDHiTZhHc2L9XKgtPGlC7XXtOzLfhMm2fRfkbTVeBYSFxNHQNnny+CVNbz
    ECXV4JH+wpBYWfW6Ev2qsfE=
    -----END PRIVATE KEY-----"""
        .trimIndent()
        .replace("\n", "")
        .replace("-----BEGIN PRIVATE KEY-----", "")
        .replace("-----END PRIVATE KEY-----", "")

    private var keyPair = getDefaultRSAKeyPair()
    private val TAG = "UserCredentialManager"

    private fun createKeyStore(): KeyStore {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        return keyStore
    }

    /**
     * Get default, hard-coded RSA key pair
     * @return RSA keypair, hard-coded
     * @note Use at your own risk
     */
    fun getDefaultRSAKeyPair(): KeyPair {
        Log.i(TAG, "Getting default RSA keypair")
        val keyFactory = KeyFactory.getInstance("RSA")
        val privKeySpec = PKCS8EncodedKeySpec(Base64.decode(defPrivKeyString, Base64.DEFAULT))
        val pubKeySpec = X509EncodedKeySpec(Base64.decode(defPubKeyString, Base64.DEFAULT))

        val privateKey: PrivateKey = keyFactory.generatePrivate(privKeySpec)
        val publicKey: PublicKey = keyFactory.generatePublic(pubKeySpec)

        return KeyPair(publicKey, privateKey)
    }

    /**
     * Get default AES key
     * @return Hard-coded AES key
     * @note Use at your own risk
     */
    fun getDefaultSymmetricKey(): SecretKey {
        val secretKeyByteArray = ByteArray(16) { _ -> 0 } // 16-byte array of zeroes
        return SecretKeySpec(secretKeyByteArray, "AES")
    }

    /**
     * Get stored RSA key pair
     * @return RSA key pair in the Android KeyStore. Null if fails to retrieve
     * @note Will create a new RSA keypair if it didn't exist, and store it in Android KeyStore
     * @note duh.
     */
    fun getStoredRSAKeyPair(): KeyPair? {
        val keyStore = createKeyStore()
        val privateKey = keyStore.getKey(KEY_ALIAS, null) as PrivateKey?
        val publicKey = keyStore.getCertificate(KEY_ALIAS)?.publicKey

        return if (privateKey != null && publicKey != null) {
            Log.i(TAG, "Loading stored keypair")
            KeyPair(publicKey, privateKey)
        } else {
            createStoredRSAKeyPair()
        }
    }

    private fun createStoredRSAKeyPair(): KeyPair? {
        val generator: KeyPairGenerator
        when {
            hasMarshmallow() -> {
                Log.i(TAG, "Creating keypairs, marshmallow version")
                try {
                    generator = KeyPairGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_RSA,
                        "AndroidKeyStore"
                    )
                    val builder = KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_ECB)
                        //.setUserAuthenticationRequired(true)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                    generator.initialize(builder.build())
                    return generator.generateKeyPair()
                } catch (e: Exception) {
                    Log.e(TAG, "createStoredKey ERROR:", e)
                    return null
                }
            }
            hasKitkat() -> {
                Log.i(TAG, "Creating keypairs, kitkat version")
                return try {
                    generator = KeyPairGenerator.getInstance("RSA", "AndroidKeyStore")
                    val start = Calendar.getInstance()
                    val end = Calendar.getInstance()
                    end.add(Calendar.YEAR, 30)
                    val spec = KeyPairGeneratorSpec.Builder(context)
                        .setAlias(KEY_ALIAS)
                        .setKeySize(2048)
                        .setSubject(X500Principal("CN=$KEY_ALIAS"))
                        .setSerialNumber(BigInteger.valueOf(abs(KEY_ALIAS.hashCode()).toLong()))
                        .setStartDate(start.time)
                        .setEndDate(end.time)
                        .build()
                    generator.initialize(spec)
                    generator.generateKeyPair()
                } catch (e: Exception) {
                    Log.e(TAG, "createStoredKey ERROR:", e)
                    null
                }
            }
            else -> return null
        }
    }

    private fun removeStoredRSAKeyPair() {
        Log.i(TAG, "Removing Key Pairs")
        val ks = createKeyStore()
        ks.deleteEntry(KEY_ALIAS)
    }

    /**
     * Reset stored RSA key pair in the Android KeyStore
     * @note Will call {@link #getStoredRSAKeyPair} at the end of the function call, and
     * store the result in a private variable
     */
    fun resetStoredRSAKeyPair() {
        removeStoredRSAKeyPair()
        keyPair = getStoredRSAKeyPair() ?: return
    }

    fun decryptSecretKey(keyByteArray: ByteArray?, privateKey: PrivateKey): SecretKey {
        var sk = getDefaultSymmetricKey()
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        keyByteArray?.let {
            try {
                cipher.init(Cipher.DECRYPT_MODE, privateKey)
                val encodedKey = cipher.doFinal(it)
                sk = SecretKeySpec(encodedKey, 0, encodedKey.size, "AES")
            } catch (e: Exception) {
                Log.e(TAG, "getStoredKey error", e)
            }
        }
        return sk
    }

    fun encryptSecretKey(secretKey: SecretKey, publicKey: PublicKey) : ByteArray? {
        return try {
            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)
            cipher.doFinal(secretKey.encoded)
        } catch (e: Exception) {
            Log.e(TAG, "encryptSecretKey ERROR:", e)
            null
        }
    }
}