package com.example.pocta

import android.annotation.TargetApi
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.os.Build
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.text.method.ScrollingMovementMethod
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.example.pocta.MyBluetoothService.Companion.uuid
import com.example.pocta.databinding.ActivityConnectBinding
import java.nio.charset.Charset
import java.security.*
import javax.crypto.Cipher

class ConnectActivity : AppCompatActivity() {
    private lateinit var binding: ActivityConnectBinding
    private lateinit var myAddress: String
    private lateinit var myBluetoothService: MyBluetoothService
    private lateinit var myRSAKeyPair: KeyPair
    private var btDevice: BluetoothDevice? = null
    private var btAdapter: BluetoothAdapter? = null
    private val tag = "ConnectActivity"
    private lateinit var encryptedMsg: String

    companion object {
        var message: String = "Default"
        const val KEY_ALIAS = "keyaliashisyam"
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

        myBluetoothService = MyBluetoothService(this)

        binding.apply {
            connectButton.setOnClickListener { connectToDevice() }
            sendMessageButton.setOnClickListener { sendMessage() }
            disconnectButton.setOnClickListener { disconnectFromDevice() }
            encryptButton.setOnClickListener { encryptMyMessage() }
            decryptButton.setOnClickListener { decryptMyMessage() }
            chatField.text = chatMessage
            chatField.movementMethod = ScrollingMovementMethod()
        }
    }

    override fun onDestroy() {
        if (::myRSAKeyPair.isInitialized) removeKeyStore()
        super.onDestroy()
    }

    private fun decryptMyMessage() {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        val decryptedMsg= decrypt(encryptedMsg, myRSAKeyPair.private)
        val chatUpdate = "${binding.chatField.text} \nDecrypted: $decryptedMsg"
        binding.chatField.text = chatUpdate
    }

    private fun encryptMyMessage() {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        myRSAKeyPair = if (hasMarshmallow()) {
            createAsymmetricKeyPair()
            getAsymmetricKeyPair()!!
        } else {
            createAsymmetricKeyPair()
        }

        val toEncrypt = binding.userInput.text.toString()
        encryptedMsg = encrypt(toEncrypt, myRSAKeyPair.public)
        val chatUpdate = "${binding.chatField.text} \nEncrypted: $encryptedMsg"
        binding.chatField.text = chatUpdate
    }

    private fun disconnectFromDevice() {
        Toast.makeText(this, "Disconnecting.....", Toast.LENGTH_SHORT).show()
        myBluetoothService.stop()
    }

    private fun sendMessage() {
        // TODO("Ubah myBluetoothService agar mengecek pesan terkirim atau tidak")
        // TODO("Ubah sendMessage agar memberi sinyal apakah pesan terkirim atau tidak")
        // TODO("Ubah myBluetoothService agar dapat menyalurkan pesan yang diterima ke ConnectActivity")
        message = binding.userInput.text.toString()
        val bytes = message.toByteArray(Charset.defaultCharset())
        myBluetoothService.write(bytes)
        val chatUpdate = "${binding.chatField.text} \nYou: $message"
        binding.chatField.text = chatUpdate
    }

    private fun connectToDevice() {
        // TODO("Ubah myBluetoothService agar mengecek koneksi berhasil")
        // TODO("Ubah connectToDevice agar memberi pesan jika koneksi berhasil")
        Toast.makeText(this, "Connecting....", Toast.LENGTH_SHORT).show()
        Log.d(tag, "Initializing connection to device.")
        myBluetoothService.startClient(btDevice, uuid)
    }

    private fun encrypt(data: String, publicKey: Key?): String {
        val cipher: Cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        val bytes = cipher.doFinal(data.toByteArray())
        return Base64.encodeToString(bytes, Base64.DEFAULT)
    }

    private fun decrypt(data: String, privateKey: Key?): String {
        val cipher: Cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.DECRYPT_MODE, privateKey)
        val encryptedData = Base64.decode(data, Base64.DEFAULT)
        val decodedData = cipher.doFinal(encryptedData)
        return String(decodedData)
    }

    private fun createAsymmetricKeyPair(): KeyPair {
        val generator: KeyPairGenerator

        if (hasMarshmallow()) {
            generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
            getKeyGenParameterSpec(generator)
        } else {
            generator = KeyPairGenerator.getInstance("RSA")
            generator.initialize(2048)
        }

        return generator.generateKeyPair()
    }

    @TargetApi(23)
    private fun getKeyGenParameterSpec(generator: KeyPairGenerator) {
        val builder = KeyGenParameterSpec.Builder(KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_ECB)
            //.setUserAuthenticationRequired(true)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)

        generator.initialize(builder.build())
    }

    private fun getAsymmetricKeyPair(): KeyPair? {
        val keyStore: KeyStore = createKeyStore()

        val privateKey = keyStore.getKey(KEY_ALIAS, null) as PrivateKey?
        val publicKey = keyStore.getCertificate(KEY_ALIAS)?.publicKey

        return if (privateKey != null && publicKey != null) {
            KeyPair(publicKey, privateKey)
        } else {
            null
        }
    }

    private fun removeKeyStore() = createKeyStore().deleteEntry(KEY_ALIAS)

    private fun createKeyStore(): KeyStore {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        return keyStore
    }
}

fun hasMarshmallow() : Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
}
