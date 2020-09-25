package com.example.pocta

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.text.method.ScrollingMovementMethod
import android.util.Base64
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.LifecycleOwner
import androidx.localbroadcastmanager.content.LocalBroadcastManager
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
    private lateinit var receiver: BroadcastReceiver
    private val tag = "ConnectActivity"
    private var chatMessage: String = ""

    companion object {
        const val lt = "ConnectActivity"
    }

    @SuppressLint("HardwareIds")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_connect)

        // TODO("Cari tahu kenapa dapat tipe nullable dari getStringExtra()")
        myAddress = intent.getStringExtra(HubActivity.EXTRA_ADDRESS) ?: 0.toString()
        chatMessage = "Starting comms with device at MAC address: $myAddress"

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
            resetKeyButton.setOnClickListener { resetKeyPair() }

            chatField.text = chatMessage
            chatField.movementMethod = ScrollingMovementMethod()
        }

        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val newData = intent?.getStringExtra(ImmobilizerService.IMMOBILIZER_SERVICE_DATA)
                newData?.let {
                    chatMessage = "$chatMessage\n$it"
                }
                binding.chatField.text = chatMessage
            }
        }
    }

    override fun onStart() {
        super.onStart()
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(receiver, IntentFilter(ImmobilizerService.IMMOBILIZER_SERVICE_NEW_DATA))
    }

    override fun onStop() {
        LocalBroadcastManager.getInstance(this)
            .unregisterReceiver(receiver)
        super.onStop()
    }

    override fun onDestroy() {
        ImmobilizerService.immobilizerController.disconnect()
        super.onDestroy()
    }

    private fun sendRegistrationRequest() {
        ImmobilizerService.immobilizerController.onUserRequest(USER_REQUEST.REGISTER_PHONE)
    }

    private fun sendPinChangeRequest() {
        ImmobilizerService.immobilizerController.onUserRequest(USER_REQUEST.CHANGE_PIN)
    }

    private fun sendUnlockRequest() {
        ImmobilizerService.immobilizerController.onUserRequest(USER_REQUEST.UNLOCK)
    }

    private fun sendDeleteRequest() {
        ImmobilizerService.immobilizerController.onUserRequest(USER_REQUEST.REMOVE_PHONE)
    }

    //    private fun toggleDecryption() = ImmobilizerService.immobilizerController.toggleDecryption()
    private fun toggleEncryption() {
        ImmobilizerService.immobilizerController.toggleEncryption()
    }

    private fun disconnectFromDevice() {
        ImmobilizerService.immobilizerController.disconnect()
    }

    private fun connectToDevice() {
        ImmobilizerService.immobilizerController.connect()
    }

    private fun resetKeyPair() {
        ImmobilizerService.immobilizerController.resetKeyPair()
    }

    private fun sendMessage() {
        val userInput = binding.userInput.text.toString()
        ImmobilizerService.immobilizerController.onUserInput(userInput.toByteArray())
    }
}
