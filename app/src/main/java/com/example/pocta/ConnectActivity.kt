package com.example.pocta

import android.annotation.SuppressLint
import android.annotation.TargetApi
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
import java.lang.Exception
import java.nio.charset.Charset
import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.*
import javax.crypto.spec.SecretKeySpec

class ConnectActivity : AppCompatActivity() {
    private lateinit var binding: ActivityConnectBinding
    private lateinit var myAddress: String
    private lateinit var myBluetoothService: MyBluetoothService
    private lateinit var myKey: SecretKey
    private lateinit var incomingBytes: ByteArray
    private lateinit var hpRSAKeyPair: KeyPair
    private var btDevice: BluetoothDevice? = null
    private var btAdapter: BluetoothAdapter? = null
    private val tag = "ConnectActivity"

    // TODO("Buat agar kunci enkripsi/dekripsi disimpan di Android KeyStore")
    // Kunci AES default
    private val defaultKeyString = "YWJjZGVmZ2hpamtsbW5vcA=="

    companion object {
        const val KEY_ALIAS = "keyaliashisyamAES"
        const val KEY_ALIAS_DEF = "keyaliascobaAES"
        const val USE_DEF_KEY = true
        const val lt = "ConnectActivity"
        const val ACK = "1"
        const val NACK = "0"
        const val USER_ID = "1998"
    }

    private enum class APP_STATE {
        NORMAL,
        REQUEST,
        ID_CHECK,
        CHALLENGE,
        RESPONSE,
        PIN,
        UNLOCK,
        NEW_PIN,
        ALARM,
        KEY_EXCHANGE,
        REGISTER
    }

    private enum class USER_REQUEST(val reqString: String) {
        NOTHING("!0"),
        UNLOCK("!1"),
        CHANGE_PIN("!2"),
        REGISTER_PHONE("!3"),
        REMOVE_PHONE("!4"),
        DISABLE("!5")
    }

    private var appState: APP_STATE = APP_STATE.NORMAL
    private var userRequest: USER_REQUEST = USER_REQUEST.NOTHING

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
        myKey = getStoredKey()
        hpRSAKeyPair = if (hasMarshmallow()) {
            createAsymmetricKeyPair()
            getAsymmetricKeyPair()!!
        } else {
            createAsymmetricKeyPair()
        }
        myBluetoothService.apply {
            setAESKey(myKey)
            useOutputEncryption = false
            useInputDecryption = false
        }

        appState = APP_STATE.NORMAL

        binding.apply {
            connectButton.setOnClickListener { connectToDevice() }
            sendMessageButton.setOnClickListener { sendMessage() }
            disconnectButton.setOnClickListener { disconnectFromDevice() }
            toggleEncryptionButton.setOnClickListener { toggleEncryption() }
            toggleDecryptionButton.setOnClickListener { toggleDecryption() }
            hashReplyButton.setOnClickListener { hashReply() }
            unlockDeviceButton.setOnClickListener { sendUnlockRequest() }
            changeUserPinButton.setOnClickListener { sendPinChangeRequest() }
            keyExchangeButton.setOnClickListener { sendKey() }
            phoneRegistrationButton.setOnClickListener { sendRegistrationRequest() }

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
        val encodedKey = Base64.encodeToString(myKey.encoded, Base64.DEFAULT)
        myBluetoothService.write(encodedKey.toByteArray(Charset.defaultCharset()))
    }

    private fun sendRSAPubKey() {
        val publicKeyHeader = "-----BEGIN PUBLIC KEY-----"
        val publicKeyBottom = "-----END PUBLIC KEY-----"
        val encodedPublicKey = Base64.encodeToString(hpRSAKeyPair.public.encoded, Base64.DEFAULT)
        val publicKeyString = "$publicKeyHeader\n$encodedPublicKey$publicKeyBottom"
        myBluetoothService.write(publicKeyString.toByteArray())
    }

    private fun sendRegistrationRequest() {
        if (appState == APP_STATE.NORMAL) {
            userRequest = USER_REQUEST.REGISTER_PHONE
            disableEncryption()
            disableDecryption()
            appState = APP_STATE.REQUEST
            val bytes: ByteArray = userRequest.reqString.toByteArray(Charset.defaultCharset())
            myBluetoothService.write(bytes)
        }
    }

    private fun sendPinChangeRequest() {
        if (appState == APP_STATE.NORMAL) {
            userRequest = USER_REQUEST.CHANGE_PIN
            disableEncryption()
            disableDecryption()
            appState = APP_STATE.REQUEST
            val bytes: ByteArray = userRequest.reqString.toByteArray(Charset.defaultCharset())
            myBluetoothService.write(bytes)
        }
    }

    private fun sendUnlockRequest() {
        if (appState == APP_STATE.NORMAL) {
            userRequest = USER_REQUEST.UNLOCK
            disableEncryption()
            disableDecryption()
            appState = APP_STATE.REQUEST
            val bytes: ByteArray = userRequest.reqString.toByteArray(Charset.defaultCharset())
            myBluetoothService.write(bytes)
        }
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
        val nextState: APP_STATE
        when (appState) {
            APP_STATE.REQUEST -> {
                if (incomingMessage == ACK) {
                    disableEncryption()
                    when (userRequest) {
                        USER_REQUEST.REGISTER_PHONE -> {
                            nextState = APP_STATE.KEY_EXCHANGE
                            sendRSAPubKey()
                        }
                        else -> {
                            nextState = APP_STATE.ID_CHECK
                            myBluetoothService.write(USER_ID.toByteArray())
                        }
                    }
                } else {
                    val reqNotFoundStr = "${binding.chatField.text} \nRequest not found"
                    binding.chatField.text = reqNotFoundStr
                    nextState = APP_STATE.NORMAL
                }
            }
            APP_STATE.ID_CHECK -> {
                if (incomingMessage == ACK) {
                    nextState = APP_STATE.CHALLENGE
                } else {
                    val idNotFoundStr = "${binding.chatField.text} \nUser ID not Found"
                    binding.chatField.text = idNotFoundStr
                    nextState = APP_STATE.NORMAL
                }
            }
            APP_STATE.KEY_EXCHANGE -> {
                when (incomingMessage) {
                    ACK -> {
                        nextState = APP_STATE.PIN
                        val pinInputPrompt =
                            "${binding.chatField.text}\nMasukkan PIN anda! (CATATAN: Pastikan enkripsi aktif.)\n"
                        binding.chatField.text = pinInputPrompt
                        enableEncryption()
                    }
                    NACK -> nextState = APP_STATE.NORMAL
                    else -> {
                        myKey = decryptSecretKey()
                        disableEncryption()
                        disableDecryption()
                        myBluetoothService.setAESKey(myKey)
                        nextState = APP_STATE.KEY_EXCHANGE
                    }
                }
            }
            APP_STATE.CHALLENGE -> {
                enableEncryption()
                nextState = APP_STATE.RESPONSE
                myBluetoothService.write(incomingBytes)
            }
            APP_STATE.RESPONSE -> {
                if (incomingMessage == ACK) {
                    nextState = APP_STATE.PIN
                    val pinInputPrompt =
                        "${binding.chatField.text}\nMasukkan PIN anda! (CATATAN: Pastikan enkripsi aktif.)\n"
                    binding.chatField.text = pinInputPrompt
                    enableEncryption()
                } else {
                    val cramMismatchStr =
                        "${binding.chatField.text} \nFailed response from User Phone : Response-Challenge Mismatch"
                    binding.chatField.text = cramMismatchStr
                    nextState = APP_STATE.NORMAL
                }
            }
            APP_STATE.PIN -> {
                if (incomingMessage == ACK) {
                    val unlockStr = "${binding.chatField.text} \nPIN benar."
                    binding.chatField.text = unlockStr
                    nextState = when (userRequest) {
                        USER_REQUEST.UNLOCK -> {
                            disableEncryption()
                            APP_STATE.UNLOCK
                        }
                        USER_REQUEST.REGISTER_PHONE, USER_REQUEST.CHANGE_PIN -> {
                            enableEncryption()
                            val newPinStr =
                                "${binding.chatField.text}\nMasukkan PIN baru (Pastikan enkripsi aktif)."
                            binding.chatField.text = newPinStr
                            APP_STATE.NEW_PIN
                        }
                        else -> {
                            disableEncryption()
                            APP_STATE.NORMAL
                        }
                    }
                } else {
                    val wrongPinStr = "${binding.chatField.text}\nPIN yang anda masukkan salah."
                    binding.chatField.text = wrongPinStr
                    nextState = APP_STATE.NORMAL
                }
            }
            APP_STATE.NEW_PIN -> {
                disableEncryption()
                val confirmationStr: String = if (incomingMessage == ACK) {
                    "${binding.chatField.text}\nPIN berhasil didaftarkan."
                } else {
                    "${binding.chatField.text}\nPIN gagal didaftarkan."
                }
                binding.chatField.text = confirmationStr

                nextState = when (userRequest) {
                    USER_REQUEST.REGISTER_PHONE -> APP_STATE.REGISTER
                    else -> APP_STATE.NORMAL
                }
            }
            APP_STATE.REGISTER -> {
                disableEncryption()
                val confirmationStr: String = if (incomingMessage == ACK) {
                    setStoredKey(myKey)
                    "${binding.chatField.text}\nHP berhasil didaftarkan."
                } else {
                    "${binding.chatField.text}\nHP gagal didaftarkan."
                }
                binding.chatField.text = confirmationStr
                nextState = APP_STATE.NORMAL
            }
            APP_STATE.UNLOCK -> {
                nextState = APP_STATE.NORMAL
            }
            else -> nextState = APP_STATE.NORMAL
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

        val alias: String = if (USE_DEF_KEY) {
            KEY_ALIAS_DEF
        } else {
            KEY_ALIAS
        }

        val privateKey = keyStore.getKey(alias, null) as PrivateKey?
        val publicKey = keyStore.getCertificate(alias)?.publicKey

        return if (privateKey != null && publicKey != null) {
            KeyPair(publicKey, privateKey)
        } else {
            null
        }
    }

    // TODO("Rapihin")
    private fun decryptSecretKey(): SecretKey {
        var secretKeyByteArray: ByteArray
        secretKeyByteArray = if (hpRSAKeyPair.private != null) {
            try {
                val cipher: Cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
                cipher.init(Cipher.DECRYPT_MODE, hpRSAKeyPair.private)
                cipher.doFinal(incomingBytes)
            } catch (e: Exception) {
                defaultKeyString.toByteArray()
            }
        } else {
            defaultKeyString.toByteArray()
        }
        if (secretKeyByteArray.size != 16) secretKeyByteArray = defaultKeyString.toByteArray()

        val b64key = Base64.encodeToString(secretKeyByteArray, Base64.DEFAULT)
        val keyString = secretKeyByteArray.toString()
        val keyPrint = "${binding.chatField.text}\nSecret Key: \n$keyString \n$b64key"
        binding.chatField.text = keyPrint

        return SecretKeySpec(secretKeyByteArray, "AES")
    }

    // TODO("Ubah penyimpanan cipher key tidak menggunakan plaintext")
    private fun getStoredKey(): SecretKey {
        val cipherKeyStr = getSharedPreferences("PREFS", 0)
            .getString("CIPHERKEY", defaultKeyString)
            ?: defaultKeyString
        val encodedKey = Base64.decode(cipherKeyStr, Base64.DEFAULT)
        return SecretKeySpec(encodedKey, 0, encodedKey.size, "AES")
    }

    // TODO("Ubah penyimpanan cipher key tidak menggunakan plaintext")
    private fun setStoredKey(newKey: SecretKey?) {
        if (newKey != null) {
            val newKeyStr = Base64.encodeToString(newKey.encoded, Base64.DEFAULT)
            val editor =
                getSharedPreferences("PREFS", 0)
                    .edit()
                    .putString("CIPHERKEY", newKeyStr)
                    .apply()
        }
    }
}

fun hasMarshmallow(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
}
