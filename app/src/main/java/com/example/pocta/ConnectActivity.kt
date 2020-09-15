package com.example.pocta

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.text.method.ScrollingMovementMethod
import android.util.Base64
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.example.pocta.MyBluetoothService.Companion.CONNECTION_LOST
import com.example.pocta.MyBluetoothService.Companion.CONNECTION_START
import com.example.pocta.MyBluetoothService.Companion.MESSAGE_READ
import com.example.pocta.MyBluetoothService.Companion.MESSAGE_WRITE
import com.example.pocta.MyBluetoothService.Companion.uuid
import com.example.pocta.databinding.ActivityConnectBinding
import java.security.KeyPair
import javax.crypto.SecretKey

class ConnectActivity : AppCompatActivity() {
    private lateinit var binding: ActivityConnectBinding
    private lateinit var myAddress: String
    private lateinit var myBluetoothService: MyBluetoothService
    private lateinit var credentialManager: MyCredentialManager
    private lateinit var stateMachine: PhoneStateMachine
    private lateinit var myKey: SecretKey
    private lateinit var incomingBytes: ByteArray
    private lateinit var hpRSAKeyPair: KeyPair
    private var btDevice: BluetoothDevice? = null
    private var btAdapter: BluetoothAdapter? = null
    private val tag = "ConnectActivity"

    companion object {
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
        credentialManager = MyCredentialManager(this)
        stateMachine = PhoneStateMachine(myBluetoothService, binding.chatField, credentialManager)
        stateMachine.deviceName = btDevice?.name ?: "Device"

        binding.apply {
            connectButton.setOnClickListener { connectToDevice() }
            sendMessageButton.setOnClickListener { sendMessage() }
            disconnectButton.setOnClickListener { disconnectFromDevice() }
            toggleEncryptionButton.setOnClickListener { toggleEncryption() }
//            toggleDecryptionButton.setOnClickListener { toggleDecryption() }
            unlockDeviceButton.setOnClickListener { sendUnlockRequest() }
            changeUserPinButton.setOnClickListener { sendPinChangeRequest() }
//            keyExchangeButton.setOnClickListener { sendRSAPubKey() }
            phoneRegistrationButton.setOnClickListener { sendRegistrationRequest() }
            removePhoneButton.setOnClickListener { sendDeleteRequest() }

            chatField.text = chatMessage
            chatField.movementMethod = ScrollingMovementMethod()
        }
    }

    override fun onDestroy() {
        if (::myKey.isInitialized) credentialManager.removeKeyStore()
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
//    private fun toggleDecryption() = stateMachine.toggleDecryption()
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
}
