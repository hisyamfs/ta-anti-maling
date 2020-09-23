package com.example.pocta

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Handler
import android.os.Message
import android.util.Base64
import android.util.Log
import android.widget.TextView
import java.security.KeyPair
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

const val BT_DISCONNECT_MSG = "Saluran Bluetooth terputus."
const val BT_CONNECT_MSG = "Saluran Bluetooth tersambung"

enum class USER_REQUEST(val reqString: String) {
    NOTHING("!0"),
    UNLOCK("!1"),
    CHANGE_PIN("!2"),
    REGISTER_PHONE("!3"),
    REMOVE_PHONE("!4"),
    DISABLE("!5")
}

class PhoneStateMachine(context: Context, private val ui: TextView) {
    private var btDevice: BluetoothDevice? = null
    val ERR = '2'
    val ACK = "1"
    val NACK = "0"
    companion object {
        const val TAG = "PhoneStateMachine"
    }
    var deviceName = btDevice?.name ?: "Device_PH"
    var userRequest: USER_REQUEST = USER_REQUEST.NOTHING
    private val myHandler = @SuppressLint("HandlerLeak")
    object : Handler() {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MyBluetoothService.MESSAGE_READ -> {
                    val incomingBytes = msg.obj as ByteArray
                    onBTInput(incomingBytes, msg.arg1)
                }
                MyBluetoothService.MESSAGE_WRITE -> {
                    val outgoingBytes = msg.obj as ByteArray
                    onBTOutput(outgoingBytes)
                }
                MyBluetoothService.CONNECTION_LOST -> onBTDisconnect()
                MyBluetoothService.CONNECTION_START -> onBTConnection()
            }
        }
    }
    private val bt: MyBluetoothService = MyBluetoothService(context, myHandler)
    private val cm: MyCredentialManager = MyCredentialManager(context)
    private var hpRSAKeyPair: KeyPair = cm.getDefaultRSAKeyPair()
    private var myKey: SecretKey = cm.getDefaultSymmetricKey()
    private var appState: PhoneState = DisconnectState(this)

    /* Handle state transition */
    fun changeState(state: PhoneState) {
        appState = state
        appState.announce()
    }

    /* FSM events */
    fun onBTInput(bytes: ByteArray, len: Int) = appState.onBTInput(bytes, len)
    fun onBTOutput(bytes: ByteArray) = appState.onBTOutput(bytes)
    fun onUserRequest(req: USER_REQUEST) {
        userRequest = req
        appState.onUserRequest()
    }
    fun onUserInput(bytes: ByteArray) = appState.onUserInput(bytes)
    fun onBTConnection() = appState.onBTConnection()
    fun onBTDisconnect() = appState.onBTDisconnect()

    /* Common methods for all states */
    fun initConnection(device: BluetoothDevice, req: USER_REQUEST = USER_REQUEST.NOTHING) {
        Log.i(TAG, "Initializing connection at ${device.address}")
        // Reset State and Connection
        disconnect()
//        if (appState != DisconnectState(this))
//            appState = DisconnectState(this)
        // Set private variables
        btDevice = device
        userRequest = req
        myKey = cm.getStoredKey(btDevice!!.address)
        bt.apply {
            setAESKey(myKey)
            useOutputEncryption = false
            useInputDecryption = false
        }
        if (userRequest != USER_REQUEST.NOTHING)
            onUserRequest(req)
    }

    fun connect() {
        if (btDevice != null)
            bt.startClient(btDevice, MyBluetoothService.uuid)
        else
            Log.e(TAG, "btDevice is null")
    }
    fun disconnect() = bt.stop()

    // send unencrypted data to the device
    fun sendData(bytes: ByteArray) {
        disableEncryption()
        bt.write(bytes)
    }

    fun sendEncryptedData(bytes: ByteArray) {
        enableEncryption()
        bt.write(bytes)
        disableEncryption()
    }

    fun updateUI(str: String) {
        val textUpdate = "${ui.text}\n$str"
        ui.text = textUpdate
    }

    fun promptUserInput(str: String) {
//        TODO("Ubah prompt menjadi tampilan lain")
        updateUI(str)
    }

    fun disableEncryption() {
        updateUI("Encryption Off")
        bt.useOutputEncryption = false
    }

    fun enableEncryption() {
        updateUI("Encryption On")
        bt.useOutputEncryption = true
    }

    fun toggleEncryption() {
        if (bt.useOutputEncryption) disableEncryption()
        else enableEncryption()
    }

    fun disableDecryption() {
        updateUI("Decryption On")
        bt.useInputDecryption = false
    }

    fun enableDecryption() {
        updateUI("Decryption Off")
        bt.useInputDecryption = true
    }

    fun toggleDecryption() {
        if (bt.useInputDecryption) disableDecryption()
        else enableDecryption()
    }

    fun setPubKey(kp: KeyPair) {
        hpRSAKeyPair = kp
    }

    fun setMyAESKey(newKey: SecretKey) {
//        cm.setStoredKey(btDevice.address, myKey)
        myKey = newKey
        bt.setAESKey(myKey)
    }

    fun updateDatabase() {
        if (btDevice != null)
            cm.setStoredKey(btDevice!!.address, btDevice!!.name, myKey)
    }

    fun deleteAccount() {
        if (btDevice != null)
            cm.deleteAccount(btDevice!!.address)
    }

    fun sendRSAPubKey() {
        val publicKeyHeader = "-----BEGIN PUBLIC KEY-----"
        val publicKeyBottom = "-----END PUBLIC KEY-----"
        val encodedPublicKey = Base64.encodeToString(hpRSAKeyPair.public?.encoded, Base64.DEFAULT)
        val publicKeyString = "$publicKeyHeader\n$encodedPublicKey$publicKeyBottom"
        sendData(publicKeyString.toByteArray())
    }

    fun resetKeyPair() {
        cm.resetStoredRSAKeyPair()
    }

    fun decryptSecretKey(bytes: ByteArray): SecretKey {
        var secretKeyByteArray: ByteArray
        val defaultKeyString = "abcdefghijklmnop"
        secretKeyByteArray = if (hpRSAKeyPair.private != null) {
            try {
                val cipher: Cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
                cipher.init(Cipher.DECRYPT_MODE, hpRSAKeyPair.private)
                cipher.doFinal(bytes)
            } catch (e: Exception) {
                defaultKeyString.toByteArray()
            }
        } else {
            defaultKeyString.toByteArray()
        }
        if (secretKeyByteArray.size != 16) secretKeyByteArray = defaultKeyString.toByteArray()

        val b64key = Base64.encodeToString(secretKeyByteArray, Base64.DEFAULT)
        val keyString = secretKeyByteArray.toString()
        val keyPrint = "Secret Key: \n$keyString \n$b64key"
        updateUI(keyPrint)

        return SecretKeySpec(secretKeyByteArray, "AES")
    }
}

open class PhoneState(val sm: PhoneStateMachine) {
    open fun announce() {}
    open fun onBTInput(bytes: ByteArray, len: Int) {}
    open fun onBTOutput(bytes: ByteArray) {
        val outgoingMessage = String(bytes)
        sm.updateUI("You : $outgoingMessage")
    }

    open fun onUserRequest() {}
    open fun onUserInput(bytes: ByteArray) {}

    open fun onBTConnection() {
        sm.updateUI(BT_CONNECT_MSG)
    }

    open fun onBTDisconnect() {
        sm.updateUI(BT_DISCONNECT_MSG)
    }
}

class DisconnectState(sm: PhoneStateMachine) : PhoneState(sm) {
    override fun announce() {
        super.announce()
        sm.updateUI("State: Disconnect")
    }

    override fun onUserRequest() {
        if (sm.userRequest != USER_REQUEST.NOTHING)
            sm.connect()
    }

    override fun onBTConnection() {
        super.onBTConnection()
        sm.changeState(ConnectState(sm))
    }

    override fun onBTDisconnect() {
        sm.changeState(DisconnectState(sm))
    }
}

class ConnectState(sm: PhoneStateMachine) : PhoneState(sm) {
    override fun announce() {
        super.announce()
        sm.updateUI("State: Connect")
    }

    override fun onBTInput(bytes: ByteArray, len: Int) {
        val incomingMessage = String(bytes, 0, len)
        sm.updateUI("${sm.deviceName} : $incomingMessage")
        if (incomingMessage == sm.ACK) {
            sm.changeState(RequestState(sm))
        } else {
            sm.updateUI("HP anda tidak dikenal/belum terdaftar")
            // TODO("Lakukan penghapusan alamat device dari HP")
            sm.changeState(DisconnectState(sm))
        }
    }

    override fun onBTDisconnect() {
        super.onBTDisconnect()
//        sm.updateUI("To DisconnectState")
        sm.changeState(DisconnectState(sm))
    }
}

class RequestState(sm: PhoneStateMachine) : PhoneState(sm) {
    override fun announce() {
        super.announce()
        sm.updateUI("State : Request")
        if (sm.userRequest != USER_REQUEST.NOTHING)
            onUserRequest()
    }

    override fun onBTInput(bytes: ByteArray, len: Int) {
        val incomingMessage = String(bytes, 0, len)
        sm.updateUI("${sm.deviceName} : $incomingMessage")
        if (incomingMessage == sm.ACK) {
            if (sm.userRequest == USER_REQUEST.REGISTER_PHONE) {
//                sm.updateUI("To KeyExchangeState")
                sm.sendRSAPubKey()
                sm.changeState(KeyExchangeState(sm))
            } else {
//                sm.updateUI("To ChallengeState")
                sm.changeState(ChallengeState(sm))
            }
        } else {
            sm.updateUI("Request tidak dikenal atau belum diimplementasi")
        }
    }

    override fun onBTOutput(bytes: ByteArray) {
        val outgoingMessage = String(bytes)
        sm.updateUI("You : $outgoingMessage")
    }

    override fun onUserRequest() {
        val reqBytes = sm.userRequest.reqString.toByteArray()
        sm.sendData(reqBytes)
    }

    override fun onUserInput(bytes: ByteArray) = sm.sendData(bytes)

    override fun onBTDisconnect() {
        super.onBTDisconnect()
//        sm.updateUI("To DisconnectState")
        sm.changeState(DisconnectState(sm))
    }
}

class ChallengeState(sm: PhoneStateMachine) : PhoneState(sm) {
    override fun announce() {
        super.announce()
        sm.updateUI("State : Challenge")
    }

    override fun onBTInput(bytes: ByteArray, len: Int) {
        val incomingMessage = Base64.encodeToString(bytes, Base64.DEFAULT)
        sm.updateUI("${sm.deviceName} : $incomingMessage")
        sm.sendEncryptedData(bytes)
//        sm.updateUI("To response state")
        sm.changeState(ResponseState(sm))
    }

    override fun onBTOutput(bytes: ByteArray) {
        val outgoingMessage = Base64.encodeToString(bytes, Base64.DEFAULT)
        sm.updateUI("You : $outgoingMessage")
    }

    override fun onBTDisconnect() {
        super.onBTDisconnect()
        sm.changeState(DisconnectState(sm))
    }
}

class ResponseState(sm: PhoneStateMachine) : PhoneState(sm) {
    override fun announce() {
        super.announce()
        sm.updateUI("State : Response")
    }

    override fun onBTInput(bytes: ByteArray, len: Int) {
        val incomingMessage = String(bytes, 0, len)
        sm.updateUI("${sm.deviceName} : $incomingMessage")
        if (incomingMessage == sm.ACK) {
            sm.promptUserInput("Masukkan password anda!")
//            sm.updateUI("To pin state")
            sm.changeState(PinState(sm))
        } else {
            sm.updateUI("HP anda tidak dikenal\nTo AlarmState")
            sm.changeState(AlarmState(sm))
        }
    }

    override fun onBTDisconnect() {
        sm.updateUI(BT_DISCONNECT_MSG)
        sm.changeState(DisconnectState(sm))
    }
}

class PinState(sm: PhoneStateMachine) : PhoneState(sm) {
    override fun announce() {
        super.announce()
        sm.updateUI("State : Pin")
    }

    override fun onBTInput(bytes: ByteArray, len: Int) {
        val incomingMessage = String(bytes, 0, len)
        sm.updateUI("${sm.deviceName} : $incomingMessage")
//        val oldRequest = sm.userRequest
//        sm.userRequest = USER_REQUEST.NOTHING // cegah request berulang
        if (incomingMessage == sm.ACK) {
            sm.updateUI("Password anda benar!")
            when (sm.userRequest) {
                USER_REQUEST.UNLOCK -> {
                    sm.disableEncryption()
                    sm.changeState(UnlockState(sm))
                }
                USER_REQUEST.CHANGE_PIN -> {
                    sm.enableEncryption()
                    sm.promptUserInput("Masukkan password baru anda!")
                    sm.changeState(NewPinState(sm))
                }
                USER_REQUEST.REMOVE_PHONE -> {
                    sm.disableEncryption()
                    sm.changeState(DeleteState(sm))
                }
                else -> {
                    sm.userRequest = USER_REQUEST.NOTHING
                    sm.disableEncryption()
                    sm.changeState(RequestState(sm))
                }
            }
        } else {
            sm.updateUI("Password anda salah")
            sm.changeState(AlarmState(sm))
        }
    }

    override fun onUserInput(bytes: ByteArray) {
        super.onUserInput(bytes)
        sm.sendEncryptedData(bytes)
    }

    override fun onBTDisconnect() {
        sm.updateUI(BT_DISCONNECT_MSG)
        sm.changeState(DisconnectState(sm))
    }
}

class UnlockState(sm: PhoneStateMachine) : PhoneState(sm) {
    override fun announce() {
        super.announce()
        sm.updateUI("State : Unlock")
    }

    override fun onBTInput(bytes: ByteArray, len: Int) {
        sm.userRequest = USER_REQUEST.NOTHING
        sm.changeState(RequestState(sm))
    }

    override fun onBTDisconnect() {
        sm.updateUI(BT_DISCONNECT_MSG)
        sm.changeState(DisconnectState(sm))
    }
}

class NewPinState(sm: PhoneStateMachine) : PhoneState(sm) {
    override fun announce() {
        super.announce()
        sm.updateUI("State: New Pin")
    }

    override fun onBTInput(bytes: ByteArray, len: Int) {
        val incomingMessage = String(bytes, 0, len)
        sm.updateUI("${sm.deviceName} : $incomingMessage")
        if (incomingMessage == sm.ACK) {
            sm.updateUI("Password berhasil diperbaharui!")
            // update Database Immobilizer dengan cipherkey terbaru
            sm.updateDatabase()
        } else {
            sm.updateUI("Password gagal didaftarkan")
        }
        sm.userRequest = USER_REQUEST.NOTHING
        sm.changeState(RequestState(sm))
    }

    override fun onUserInput(bytes: ByteArray) {
        super.onUserInput(bytes)
        sm.sendEncryptedData(bytes)
    }

    override fun onBTDisconnect() {
        sm.updateUI(BT_DISCONNECT_MSG)
        sm.changeState(DisconnectState(sm))
    }
}

class DeleteState(sm: PhoneStateMachine) : PhoneState(sm) {
    override fun announce() {
        super.announce()
        sm.updateUI("State: Delete")
    }

    override fun onBTInput(bytes: ByteArray, len: Int) {
        val incomingMessage = String(bytes, 0, len)
        sm.updateUI("${sm.deviceName} : $incomingMessage")
        if (incomingMessage == sm.ACK) {
            // TODO("Lakukan penghapusan akun terdaftar di HP juga")
            sm.updateUI("Akun berhasil dihapus!")
            sm.deleteAccount()
        } else {
            sm.updateUI("Akun gagal dihapus!")
        }
        sm.userRequest = USER_REQUEST.NOTHING
        sm.changeState(RequestState(sm))
    }

    override fun onBTDisconnect() {
        super.onBTDisconnect()
        sm.changeState(DisconnectState(sm))
    }
}

class AlarmState(sm: PhoneStateMachine) : PhoneState(sm) {
    override fun announce() {
        super.announce()
        sm.updateUI("State : Alarm")
    }

    override fun onBTInput(bytes: ByteArray, len: Int) {
        val incomingMessage = String(bytes, 0, len)
        sm.updateUI("${sm.deviceName} : $incomingMessage")
        if (incomingMessage == sm.ACK) {
//            sm.updateUI("To RequestState")
            sm.userRequest = USER_REQUEST.NOTHING
            sm.changeState(RequestState(sm))
        }
    }

    override fun onBTDisconnect() {
        sm.updateUI(BT_DISCONNECT_MSG)
//        sm.updateUI("To DisconnectState")
        sm.changeState(DisconnectState(sm))
    }
}

class KeyExchangeState(sm: PhoneStateMachine) : PhoneState(sm) {
    override fun announce() {
        super.announce()
        sm.updateUI("State: Key Exchange")
    }

    override fun onBTInput(bytes: ByteArray, len: Int) {
        super.onBTInput(bytes, len)
        val incomingMessage = String(bytes, 0, len)
        sm.updateUI("${sm.deviceName} : $incomingMessage")
        when (incomingMessage) {
            sm.ACK -> {
                // Kunci berhasil didaftarkan, masukkan PIN baru
                sm.enableEncryption()
                sm.promptUserInput("Masukkan password baru anda!")
//                sm.updateUI("To NewPinState")
                sm.changeState(NewPinState(sm))
            }
            sm.NACK -> {
                // Pertukaran kunci gagal
//                sm.updateUI("To RequestState")
                sm.changeState(RequestState(sm))
            }
            else -> {
                // Dekripsi kunci dari device dan load
                val myKey: SecretKey = sm.decryptSecretKey(bytes)
                sm.disableEncryption()
                sm.disableDecryption()
                sm.setMyAESKey(myKey)
            }
        }
    }

    override fun onBTDisconnect() {
        super.onBTDisconnect()
        sm.changeState(DisconnectState(sm))
    }
}

class RegisterState(sm: PhoneStateMachine) : PhoneState(sm) {
    override fun onBTDisconnect() {
        sm.updateUI(BT_DISCONNECT_MSG)
        sm.changeState(DisconnectState(sm))
    }
}