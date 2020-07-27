package com.example.pocta

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.text.method.ScrollingMovementMethod
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.example.pocta.MyBluetoothService.Companion.MESSAGE_READ
import com.example.pocta.MyBluetoothService.Companion.MESSAGE_WRITE
import com.example.pocta.MyBluetoothService.Companion.uuid
import com.example.pocta.databinding.ActivityConnectBinding
import java.nio.charset.Charset
import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

class ConnectActivity : AppCompatActivity() {
    private lateinit var binding: ActivityConnectBinding
    private lateinit var myAddress: String
    private lateinit var myBluetoothService: MyBluetoothService
    private lateinit var myKey: SecretKey
    private lateinit var myPIN: String
    private lateinit var incomingBytes: ByteArray
//    private lateinit var hpRSAKeyPair: KeyPair
    private var btDevice: BluetoothDevice? = null
    private var btAdapter: BluetoothAdapter? = null
    private val tag = "ConnectActivity"
    private var appState: Int = APP_STATE_NORMAL

    // TODO("Buat agar kunci enkripsi/dekripsi disimpan di Android KeyStore")
    // Kunci AES default
    private val defaultKeyString = "abcdefghijklmnop"
    // Kunci HP
    private val hpPubKey = """
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

    private val hpPrivateKey = """
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

    // Kunci device
    private val dPubKey = """
    -----BEGIN PUBLIC KEY-----
    MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAuRkf411wgdkPdUd98but
    GW3kLj0GMEh0IR6Y4j9hcAuvupwReW/9cBnlkW0JUGVEIJc09/Gek0tKmTQJnXmG
    bK59lFQ8w2IlkdOC+nas+KDh0oIqv3oOXqsFobARQPf51WMFC2fNIuHF9A7kA4/h
    nKMphwbqlIlzuh6+W1WfXR7J5LFOA1354JRzAPNnWxY8cn21MaP4pO7H17fEmhIT
    xYD6VDuD3vR75VkDIiZj5Kj24fD8Q63HHCYFHMuUXkVlWLjCVncr5Wk4YPj2dCO/
    4BuVy4Xtb6q0mk1TWj7JaJDSktUQlDEPbRRBsNWenbCW8ZMhLEHyX4VHpC+spVLt
    9QIDAQAB
    -----END PUBLIC KEY-----"""
        .trimIndent()
        .replace("\n", "")
        .replace("-----BEGIN PUBLIC KEY-----", "")
        .replace("-----END PUBLIC KEY-----", "")

    companion object {
        const val KEY_ALIAS = "keyaliashisyamAES"
        const val KEY_ALIAS_DEF = "keyaliascobaAES"
        const val USE_DEF_KEY = true
        const val lt = "ConnectActivity"
        const val APP_STATE_NORMAL = 0
        const val APP_STATE_REQUEST = 1
        const val APP_STATE_CHALLENGE = 2
        const val APP_STATE_RESPONSE = 3
        const val APP_STATE_PIN = 4
        const val APP_STATE_UNLOCK = 5
        const val APP_STATE_ALARM = -1
        const val ACK = "1"
        const val NACK = "0"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_connect)

        // TODO("Cari tahu kenapa dapat tipe nullable dari getStringExtra()")
        myAddress = intent.getStringExtra(HubActivity.EXTRA_ADDRESS) ?: 0.toString()
        btAdapter = BluetoothAdapter.getDefaultAdapter()
        if (myAddress != "0") {
            btDevice = btAdapter?.getRemoteDevice(myAddress)
        }
        val chatMessage = "Starting comms with device at MAC address: $myAddress"

        myBluetoothService = MyBluetoothService(this, myHandler)
        myKey = if (USE_DEF_KEY) {
            getDefaultSymmetricKey()
        } else {
            getSymmetricKey()
        }
        myPIN = getSharedPreferences("PREFS", 0)
            .getString("PIN", "1998") ?: "1998"
        myBluetoothService.apply {
            setAESKey(myKey)
            useOutputEncryption = false
            useInputDecryption = false
        }

        appState = APP_STATE_NORMAL

        binding.apply {
            connectButton.setOnClickListener { connectToDevice() }
            sendMessageButton.setOnClickListener { sendMessage() }
            disconnectButton.setOnClickListener { disconnectFromDevice() }
            toggleEncryptionButton.setOnClickListener { toggleEncryption() }
            toggleDecryptionButton.setOnClickListener { toggleDecryption() }
            hashReplyButton.setOnClickListener { hashReply() }
            verifyUserButton.setOnClickListener { verifyUser() }
            keyExchangeButton.setOnClickListener { sendKey() }

            chatField.text = chatMessage
            chatField.movementMethod = ScrollingMovementMethod()
        }

        disableDecryption()
        disableEncryption()
    }

    override fun onDestroy() {
        if (::myKey.isInitialized) removeKeyStore()
        super.onDestroy()
    }

    private val myHandler = @SuppressLint("HandlerLeak")
    object : Handler() {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MESSAGE_READ -> processBTInput(msg)
                MESSAGE_WRITE -> processBTOutput(msg)
            }
        }
    }

    private fun sendKey() {
//        val publicKeyHeader = "-----BEGIN PUBLIC KEY-----"
//        val publicKeyBottom = "-----END PUBLIC KEY-----"
//        val encodedPublicKey = Base64.encodeToString(hpRSAKeyPair.public.encoded, Base64.DEFAULT)
//        val publicKeyString = "$publicKeyHeader\n$encodedPublicKey$publicKeyBottom"
//        myBluetoothService.write(publicKeyString.toByteArray(Charset.defaultCharset()))
        val encodedKey = Base64.encodeToString(myKey.encoded, Base64.DEFAULT)
        myBluetoothService.write(encodedKey.toByteArray(Charset.defaultCharset()))
    }

    private fun verifyUser() {
        // TODO("Ubah password agar tidak di-hardcode")
        appState = APP_STATE_REQUEST
        disableEncryption()
        disableDecryption()
        val bytes: ByteArray = "1998".toByteArray(Charset.defaultCharset())
        myBluetoothService.write(bytes)
    }

    private fun processBTOutput(msg: Message) {
        val writeBuf = msg.obj as ByteArray
        val writeMessage = String(writeBuf)
        val chatUpdate = "${binding.chatField.text} \nYou: $writeMessage"
        binding.chatField.text = chatUpdate
    }

    private fun processBTInput(msg: Message) {
        incomingBytes = msg.obj as ByteArray
        val incomingMessage = String(incomingBytes, 0, msg.arg1)
        val chatUpdate = "${binding.chatField.text} \n${btDevice?.name}: $incomingMessage"
        binding.chatField.text = chatUpdate
        val nextState: Int
        when (appState) {
            APP_STATE_REQUEST -> {
                if (incomingMessage == ACK) {
                    nextState = APP_STATE_CHALLENGE
                } else {
                    val idNotFoundStr = "${binding.chatField.text} \nUser ID not Found"
                    binding.chatField.text = idNotFoundStr
                    nextState = APP_STATE_NORMAL
                }
            }
            APP_STATE_CHALLENGE -> {
                enableEncryption()
                nextState = APP_STATE_RESPONSE
                myBluetoothService.write(incomingBytes)
            }
            APP_STATE_RESPONSE -> {
                if (incomingMessage == ACK) {
                    nextState = APP_STATE_PIN
                    myBluetoothService.write(myPIN.toByteArray())
                } else {
                    val cramMismatchStr = "${binding.chatField.text} \nFailed response from User Phone : Response-Challenge Mismatch"
                    binding.chatField.text = cramMismatchStr
                    nextState = APP_STATE_NORMAL
                }
            }
            APP_STATE_PIN -> {
                if (incomingMessage == ACK) {
                    val unlockStr = "${binding.chatField.text} \n!!!! DEVICE UNLOCKED !!!!\nPlease wait for a second to wait for the device to be locked again."
                    binding.chatField.text = unlockStr
                    nextState = APP_STATE_UNLOCK
                } else {
                    val wrongPinStr = "${binding.chatField.text} \nIncorrect PIN input"
                    binding.chatField.text = wrongPinStr
                    nextState = APP_STATE_NORMAL
                }
            }
            APP_STATE_UNLOCK -> {
                nextState = APP_STATE_NORMAL
            }
            else -> nextState = APP_STATE_NORMAL
        }

        appState = nextState
    }

    private fun toggleDecryption() {
        // TODO("Ubah agar manajemen update text toggling enkripsi dan dekripsi dipindahkan ke handler")
        if (myBluetoothService.useInputDecryption) {
            disableDecryption()
        } else {
            enableDecryption()
        }
    }

    private fun toggleEncryption() {
        myBluetoothService.useOutputEncryption = !myBluetoothService.useOutputEncryption
        if (myBluetoothService.useOutputEncryption) {
            disableEncryption()
        } else {
            enableEncryption()
        }
    }

    private fun enableEncryption() {
        myBluetoothService.useOutputEncryption = true
        binding.toggleEncryptionButton.text = "Encryption On"
    }

    private fun disableEncryption() {
        myBluetoothService.useOutputEncryption = false
        binding.toggleEncryptionButton.text = "Encryption Off"
    }

    private fun enableDecryption() {
        myBluetoothService.useInputDecryption = true
        binding.toggleDecryptionButton.text = "Decryption On"
    }

    private fun disableDecryption() {
        myBluetoothService.useInputDecryption = false
        binding.toggleDecryptionButton.text = "Decryption Off"
    }

    private fun resetKeys() {
        removeKeyStore()
        myKey = if (USE_DEF_KEY) {
            getDefaultSymmetricKey()
        } else {
            getSymmetricKey()
        }
    }

    private fun hashReply() {
        Log.d(lt, "Last message: $incomingBytes")
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(incomingBytes)
        var hexString = ""
        for (i in digest.indices) {
            val hex = if (i % 16 == 0) {
                String.format("\n%02X ", digest[i])
            } else {
                String.format("%02X ", digest[i])
            }
            hexString = "$hexString $hex"
        }
        Log.d(lt, "hash reply: $hexString")
        val digestString = Base64.encodeToString(digest, Base64.DEFAULT)
        myBluetoothService.write(digest)
        val chatUpdate = "${binding.chatField.text} \nHash of last message: $digestString"
        binding.chatField.text = chatUpdate
    }

    private fun disconnectFromDevice() {
        Toast.makeText(this, "ConnectActivity: Disconnecting.....", Toast.LENGTH_SHORT).show()
        myBluetoothService.stop()
    }

    private fun sendMessage() {
        val toSend = binding.userInput.text.toString()
        val bytes: ByteArray = toSend.toByteArray(Charset.defaultCharset())
        myBluetoothService.write(bytes)

        val chatUpdate = "${binding.chatField.text} \nYou: Original: $toSend"
        binding.chatField.text = chatUpdate
    }

    private fun connectToDevice() {
        // TODO("Ubah myBluetoothService agar mengecek koneksi berhasil")
        // TODO("Ubah connectToDevice/handler agar memberi pesan jika koneksi berhasil")
        Toast.makeText(this, "Connecting....", Toast.LENGTH_SHORT).show()
        Log.d(tag, "ConnectActivity: Initializing connection to device.")
        myBluetoothService.startClient(btDevice, uuid)
    }

    private fun getDefaultKeyPair(): KeyPair {
        val keyFactory = KeyFactory.getInstance("RSA")
        val privKeySpec = PKCS8EncodedKeySpec(Base64.decode(hpPrivateKey, Base64.DEFAULT))
        val pubKeySpec = X509EncodedKeySpec(Base64.decode(hpPubKey, Base64.DEFAULT))

        val privateKey: PrivateKey = keyFactory.generatePrivate(privKeySpec)
        val publicKey: PublicKey = keyFactory.generatePublic(pubKeySpec)

        return KeyPair(publicKey, privateKey)
    }

    // TODO("Masih banyak voodoonya")
    private fun getSymmetricKey(): SecretKey {
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
            Log.e(lt, "Failed to create a symmetric key", e)
            return getDefaultSymmetricKey()
        } catch (e: NoSuchProviderException) {
            Log.e(lt, "Failed to create a symmetric key", e)
            return getDefaultSymmetricKey()
        } catch (e: InvalidAlgorithmParameterException) {
            Log.e(lt, "Failed to create a symmetric key", e)
            return getDefaultSymmetricKey()
        }
    }

    private fun getDevicePublicKey(): PublicKey {
        val keyFactory = KeyFactory.getInstance("RSA")
        val pubKeySpec = X509EncodedKeySpec(Base64.decode(dPubKey, Base64.DEFAULT))
        return keyFactory.generatePublic(pubKeySpec)
    }

    private fun removeKeyStore() {
        val ks = createKeyStore()
        Log.i(lt, "ConnectActivity: removing secret key.")
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
}

fun hasMarshmallow(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
}
