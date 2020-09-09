package com.example.pocta

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.text.method.ScrollingMovementMethod
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.example.pocta.MyBluetoothService.Companion.CONNECTION_LOST
import com.example.pocta.MyBluetoothService.Companion.CONNECTION_START
import com.example.pocta.MyBluetoothService.Companion.MESSAGE_READ
import com.example.pocta.MyBluetoothService.Companion.MESSAGE_WRITE
import com.example.pocta.MyBluetoothService.Companion.uuid
import com.example.pocta.databinding.ActivityConnectBinding
import java.lang.Exception
import java.nio.charset.Charset
import java.security.*
import javax.crypto.*
import javax.crypto.spec.SecretKeySpec

class ConnectActivity : AppCompatActivity() {
    private lateinit var binding: ActivityConnectBinding
    private lateinit var myAddress: String
    private lateinit var myBluetoothService: MyBluetoothService
    private lateinit var stateMachine: PhoneStateMachine
    private lateinit var myKey: SecretKey
    private lateinit var incomingBytes: ByteArray
    private lateinit var hpRSAKeyPair: KeyPair
    private lateinit var myUserId: String
    private var btDevice: BluetoothDevice? = null
    private var btAdapter: BluetoothAdapter? = null
    private val tag = "ConnectActivity"

    // TODO("Buat agar kunci enkripsi/dekripsi disimpan di Android KeyStore")
    // Kunci AES default
    private val defaultKeyString = "abcdefghijklmnop"

    companion object {
        const val KEY_ALIAS = "keyaliashisyamAES"
        const val KEY_ALIAS_DEF = "keyaliascobaAES"
        const val USE_DEF_KEY = true
        const val lt = "ConnectActivity"
    }

    @SuppressLint("HardwareIds")
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
        myKey = getDefaultSymmetricKey() // TODO("Ganti dari pakai kunci default jadi pakai kunci tersimpan")
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
        myUserId = Settings.Secure.getString(contentResolver, "bluetooth_address")
        stateMachine = PhoneStateMachine(myBluetoothService, binding.chatField)
        stateMachine.userId = myUserId
        stateMachine.deviceName = btDevice?.name ?: "Device"

        binding.apply {
            connectButton.setOnClickListener { connectToDevice() }
            sendMessageButton.setOnClickListener { sendMessage() }
            disconnectButton.setOnClickListener { disconnectFromDevice() }
            toggleEncryptionButton.setOnClickListener { toggleEncryption() }
            toggleDecryptionButton.setOnClickListener { toggleDecryption() }
            unlockDeviceButton.setOnClickListener { sendUnlockRequest() }
            changeUserPinButton.setOnClickListener { sendPinChangeRequest() }
            keyExchangeButton.setOnClickListener { sendKey() }
            phoneRegistrationButton.setOnClickListener { sendRegistrationRequest() }
            removePhoneButton.setOnClickListener { sendDeleteRequest() }

            chatField.text = chatMessage
            chatField.movementMethod = ScrollingMovementMethod()
        }
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
                CONNECTION_LOST -> stateMachine.onBTDisconnect()
                CONNECTION_START -> stateMachine.onBTConnection()
            }
        }
    }

    private fun processBTOutput(msg: Message) {
        val writeBuf = msg.obj as ByteArray
        stateMachine.onBTOutput(writeBuf)
    }

    private fun processBTInput(msg: Message) {
        incomingBytes = msg.obj as ByteArray
        stateMachine.onBTInput(incomingBytes, msg.arg1)
    }

    private fun sendRegistrationRequest() = stateMachine.onUserRequest(USER_REQUEST.REGISTER_PHONE)
    private fun sendPinChangeRequest() = stateMachine.onUserRequest(USER_REQUEST.CHANGE_PIN)
    private fun sendUnlockRequest() = stateMachine.onUserRequest(USER_REQUEST.UNLOCK)
    private fun sendDeleteRequest() = stateMachine.onUserRequest(USER_REQUEST.REMOVE_PHONE)
    private fun toggleDecryption() = stateMachine.toggleDecryption()
    private fun toggleEncryption() = stateMachine.toggleEncryption()
    private fun disconnectFromDevice() = myBluetoothService.stop()

    private fun connectToDevice() {
        myBluetoothService.startClient(btDevice, uuid)
    }

    private fun sendMessage() {
        val userInput = binding.userInput.text.toString()
        stateMachine.onUserInput(userInput.toByteArray())
    }

    private fun sendKey() {
        val encodedKey = Base64.encodeToString(myKey.encoded, Base64.DEFAULT)
        stateMachine.onUserInput(encodedKey.toByteArray())
    }

    private fun sendRSAPubKey() {
        val publicKeyHeader = "-----BEGIN PUBLIC KEY-----"
        val publicKeyBottom = "-----END PUBLIC KEY-----"
        val encodedPublicKey = Base64.encodeToString(hpRSAKeyPair.public.encoded, Base64.DEFAULT)
        val publicKeyString = "$publicKeyHeader\n$encodedPublicKey$publicKeyBottom"
        stateMachine.onUserInput(publicKeyString.toByteArray())
    }

    private fun resetKeys() {
        removeKeyStore()
        myKey = if (USE_DEF_KEY) {
            getDefaultSymmetricKey()
        } else {
            getSymmetricKey()
        }
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
