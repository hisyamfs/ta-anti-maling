package com.example.pocta

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Handler
import android.os.Message
import android.util.Base64
import android.util.Log
import java.lang.ref.WeakReference
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

class BluetoothHandler(sm: PhoneStateMachine) : Handler() {
    private val rStateMachine = WeakReference<PhoneStateMachine>(sm)
    override fun handleMessage(msg: Message) {
        rStateMachine.get()?.apply {
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
}

class PhoneStateMachine(context: Context, private val extHandler: Handler) {
    private var btDevice: BluetoothDevice? = null
    val ACK_UNL = "3"
    val ERR = "2"
    val ACK = "1"
    val NACK = "0"
    var deviceName = btDevice?.name ?: "Device_PH"
    var deviceAddress = btDevice?.address
    var userRequest: USER_REQUEST = USER_REQUEST.NOTHING
    var isConnected: Boolean = false
    private val btHandler = BluetoothHandler(this)
    private val bt: MyBluetoothService = MyBluetoothService(context, btHandler)
    private val cm: MyCredentialManager = MyCredentialManager(context)
    private var hpRSAKeyPair: KeyPair = cm.getStoredRSAKeyPair() ?: cm.getDefaultRSAKeyPair()
    private var myKey: SecretKey = cm.getDefaultSymmetricKey()
    private var appState: PhoneState = DisconnectState(this)

    companion object {
        const val TAG = "PhoneStateMachine"
        const val MESSAGE_LOG: Int = 0
        const val MESSAGE_STATUS: Int = 1
        const val MESSAGE_PROMPT_PIN: Int = 2
        const val MESSAGE_PROMPT_RENAME: Int = 3
    }

    /* Handle state transition */
    fun changeState(state: PhoneState) {
        appState = state
        appState.onTransition()
    }

    /* FSM events */
    fun onBTInput(bytes: ByteArray, len: Int) = appState.onBTInput(bytes, len)

    fun onBTOutput(bytes: ByteArray) = appState.onBTOutput(bytes)
    fun onUserRequest(req: USER_REQUEST, device: BluetoothDevice? = null) {
        userRequest = req
        appState.onUserRequest()
    }

    fun onUserInput(bytes: ByteArray) = appState.onUserInput(bytes)
    fun onBTConnection() = appState.onBTConnection()
    fun onBTDisconnect() = appState.onBTDisconnect()

    /* Common methods for all states */
    fun initConnection(
        device: BluetoothDevice,
        req: USER_REQUEST = USER_REQUEST.NOTHING,
        custom_name: String? = null
    ) {
        Log.i(TAG, "Initializing connection at ${device.address}")
        // Reset State and Connection
        disconnect()
//        if (appState != DisconnectState(this))
//            appState = DisconnectState(this)
        // Set private variables
        btDevice = device
        deviceName = custom_name ?: device.name
        deviceAddress = device.address
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

    fun disconnect() {
        isConnected = false
        bt.stop()
    }

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

    fun updateLog(str: String) {
        extHandler.obtainMessage(MESSAGE_LOG, str).sendToTarget()
    }

    fun updateStatus(status: String) {
        val statusStr = "$deviceName:\n$status"
        extHandler.obtainMessage(MESSAGE_STATUS, statusStr).sendToTarget()
    }

    fun promptUserPinInput(str: String) {
        updateLog(str)
        extHandler.obtainMessage(MESSAGE_PROMPT_PIN).sendToTarget()
    }

    fun promptUserNameInput(address: String?) {
        val myAddress = address ?: "0"
        updateLog("Renaming $myAddress")
        extHandler.obtainMessage(MESSAGE_PROMPT_RENAME, myAddress).sendToTarget()
    }

    fun disableEncryption() {
        updateLog("Encryption Off")
        bt.useOutputEncryption = false
    }

    fun enableEncryption() {
        updateLog("Encryption On")
        bt.useOutputEncryption = true
    }

    fun toggleEncryption() {
        if (bt.useOutputEncryption) disableEncryption()
        else enableEncryption()
    }

    fun disableDecryption() {
        updateLog("Decryption On")
        bt.useInputDecryption = false
    }

    fun enableDecryption() {
        updateLog("Decryption Off")
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

    fun updateDatabase(name: String) {
        if (btDevice != null)
            cm.setStoredKey(btDevice!!.address, name, myKey)
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
        updateLog(keyPrint)

        return SecretKeySpec(secretKeyByteArray, "AES")
    }
}

open class PhoneState(val sm: PhoneStateMachine) {
    open fun onTransition() {}
    open fun onBTInput(bytes: ByteArray, len: Int) {}
    open fun onBTOutput(bytes: ByteArray) {
        val outgoingMessage = String(bytes)
        sm.updateLog("You : $outgoingMessage")
    }

    open fun onUserRequest() {}
    open fun onUserInput(bytes: ByteArray) {}

    open fun onBTConnection() {
        sm.isConnected = true
        sm.updateLog(BT_CONNECT_MSG)
    }

    open fun onBTDisconnect() {
        sm.isConnected = false
        sm.updateLog(BT_DISCONNECT_MSG)
    }
}

class DisconnectState(sm: PhoneStateMachine) : PhoneState(sm) {
    override fun onTransition() {
        super.onTransition()
        sm.updateStatus("Disconnected")
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
    override fun onTransition() {
        super.onTransition()
        sm.updateStatus("Connecting")
        sm.sendData(sm.ACK.toByteArray())
    }

    override fun onBTInput(bytes: ByteArray, len: Int) {
        val incomingMessage = String(bytes, 0, len)
        sm.updateLog("${sm.deviceName} : $incomingMessage")
        when (incomingMessage) {
            sm.ACK -> {
                sm.changeState(RequestState(sm))
            }
            sm.ACK_UNL -> {
                sm.changeState(UnlockState(sm))
            }
            else -> {
                sm.updateLog("HP anda tidak dikenal/belum terdaftar")
                // TODO("Lakukan penghapusan alamat device dari HP")
                sm.changeState(DisconnectState(sm))
            }
        }
    }

    override fun onBTDisconnect() {
        super.onBTDisconnect()
//        sm.updateUI("To DisconnectState")
        sm.changeState(DisconnectState(sm))
    }
}

class RequestState(sm: PhoneStateMachine) : PhoneState(sm) {
    override fun onTransition() {
        super.onTransition()
        sm.updateStatus("Connected, locked")
        if (sm.userRequest != USER_REQUEST.NOTHING)
            onUserRequest()
    }

    override fun onBTInput(bytes: ByteArray, len: Int) {
        val incomingMessage = String(bytes, 0, len)
        sm.updateLog("${sm.deviceName} : $incomingMessage")
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
            sm.updateLog("Request tidak dikenal atau belum diimplementasi")
        }
    }

    override fun onBTOutput(bytes: ByteArray) {
        val outgoingMessage = String(bytes)
        sm.updateLog("You : $outgoingMessage")
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
    override fun onTransition() {
        super.onTransition()
        sm.updateStatus("Waiting for challenge")
    }

    override fun onBTInput(bytes: ByteArray, len: Int) {
        val incomingMessage = Base64.encodeToString(bytes, Base64.DEFAULT)
        sm.updateLog("${sm.deviceName} : $incomingMessage")
        sm.sendEncryptedData(bytes)
//        sm.updateUI("To response state")
        sm.changeState(ResponseState(sm))
    }

    override fun onBTOutput(bytes: ByteArray) {
        val outgoingMessage = Base64.encodeToString(bytes, Base64.DEFAULT)
        sm.updateLog("You : $outgoingMessage")
    }

    override fun onBTDisconnect() {
        super.onBTDisconnect()
        sm.changeState(DisconnectState(sm))
    }
}

class ResponseState(sm: PhoneStateMachine) : PhoneState(sm) {
    override fun onTransition() {
        super.onTransition()
        sm.updateStatus("Sending response")
    }

    override fun onBTInput(bytes: ByteArray, len: Int) {
        val incomingMessage = String(bytes, 0, len)
        sm.updateLog("${sm.deviceName} : $incomingMessage")
        if (incomingMessage == sm.ACK) {
            sm.promptUserPinInput("Masukkan password anda!")
//            sm.updateUI("To pin state")
            sm.changeState(PinState(sm))
        } else {
            sm.updateLog("HP anda tidak dikenal\nTo AlarmState")
            sm.changeState(AlarmState(sm))
        }
    }

    override fun onBTDisconnect() {
        sm.updateLog(BT_DISCONNECT_MSG)
        sm.changeState(DisconnectState(sm))
    }
}

class PinState(sm: PhoneStateMachine) : PhoneState(sm) {
    override fun onTransition() {
        super.onTransition()
        sm.updateStatus("Enter your PIN")
    }

    override fun onBTInput(bytes: ByteArray, len: Int) {
        val incomingMessage = String(bytes, 0, len)
        sm.updateLog("${sm.deviceName} : $incomingMessage")
        val oldRequest = sm.userRequest
        sm.userRequest = USER_REQUEST.NOTHING // cegah request berulang
        if (incomingMessage == sm.ACK) {
            sm.updateLog("Password anda benar!")
            when (oldRequest) {
                USER_REQUEST.UNLOCK -> {
                    sm.disableEncryption()
                    sm.changeState(UnlockState(sm))
                }
                USER_REQUEST.CHANGE_PIN -> {
                    sm.enableEncryption()
                    sm.promptUserPinInput("Masukkan password baru anda!")
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
            sm.updateLog("Password anda salah")
            sm.changeState(AlarmState(sm))
        }
    }

    override fun onUserInput(bytes: ByteArray) {
        super.onUserInput(bytes)
        sm.sendEncryptedData(bytes)
    }

    override fun onBTDisconnect() {
        sm.updateLog(BT_DISCONNECT_MSG)
        sm.changeState(DisconnectState(sm))
    }
}

class UnlockState(sm: PhoneStateMachine) : PhoneState(sm) {
    override fun onTransition() {
        super.onTransition()
        sm.updateStatus("Unlocked")
        // Sebelum state ini dipanggil dari state pin, sharusnya request sudah clear.
        // Jika tidak, berarti state dipanggil dari state unlock-disconnected, yg berarti
        // pengguna meminta immobilizer kembali ke posisi lock
        if (sm.userRequest == USER_REQUEST.UNLOCK)
            onUserRequest()
    }

    override fun onBTInput(bytes: ByteArray, len: Int) {
        val incomingMessage = String(bytes, 0, len)
        sm.updateLog("${sm.deviceName} : $incomingMessage")
        when (incomingMessage) {
            sm.ACK -> {
                sm.userRequest = USER_REQUEST.NOTHING
                sm.changeState(RequestState(sm))
            }
            else -> sm.disconnect()
        }
    }

    override fun onUserRequest() {
        val reqBytes = sm.userRequest.reqString.toByteArray()
        sm.sendData(reqBytes)
    }

    override fun onBTDisconnect() {
        sm.updateLog(BT_DISCONNECT_MSG)
        sm.changeState(DisconnectState(sm))
    }
}

class NewPinState(sm: PhoneStateMachine) : PhoneState(sm) {
    override fun onTransition() {
        super.onTransition()
        sm.updateStatus("Enter your new PIN")
    }

    override fun onBTInput(bytes: ByteArray, len: Int) {
        val incomingMessage = String(bytes, 0, len)
        sm.updateLog("${sm.deviceName} : $incomingMessage")
        if (incomingMessage == sm.ACK)
            sm.updateLog("Password berhasil diperbaharui!")
        else
            sm.updateLog("Password gagal didaftarkan")
        when (sm.userRequest) {
            USER_REQUEST.REGISTER_PHONE -> {
                sm.updateDatabase()
                sm.changeState(RegisterState(sm))
            }
            else -> {
                sm.userRequest = USER_REQUEST.NOTHING
                sm.changeState(RequestState(sm))
            }
        }
    }

    override fun onUserInput(bytes: ByteArray) {
        super.onUserInput(bytes)
        sm.sendEncryptedData(bytes)
    }

    override fun onBTDisconnect() {
        sm.updateLog(BT_DISCONNECT_MSG)
        sm.changeState(DisconnectState(sm))
    }
}

class RegisterState(sm: PhoneStateMachine) : PhoneState(sm) {
    override fun onTransition() {
        super.onTransition()
        sm.promptUserNameInput(sm.deviceAddress)
        sm.updateStatus("Registering device")
        sm.userRequest = USER_REQUEST.NOTHING
        sm.changeState(RequestState(sm))
    }

    override fun onBTDisconnect() {
        sm.updateLog(BT_DISCONNECT_MSG)
        sm.changeState(DisconnectState(sm))
    }
}

class KeyExchangeState(sm: PhoneStateMachine) : PhoneState(sm) {
    override fun onTransition() {
        super.onTransition()
        sm.updateStatus("Exchanging key")
    }

    override fun onBTInput(bytes: ByteArray, len: Int) {
        super.onBTInput(bytes, len)
        val incomingMessage = String(bytes, 0, len)
        sm.updateLog("${sm.deviceName} : $incomingMessage")
        when (incomingMessage) {
            sm.ACK -> {
                // Kunci berhasil didaftarkan, masukkan PIN baru
                sm.enableEncryption()
                sm.promptUserPinInput("Masukkan password baru anda!")
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

class DeleteState(sm: PhoneStateMachine) : PhoneState(sm) {
    override fun onTransition() {
        super.onTransition()
        sm.updateStatus("Deleting account")
    }

    override fun onBTInput(bytes: ByteArray, len: Int) {
        val incomingMessage = String(bytes, 0, len)
        sm.updateLog("${sm.deviceName} : $incomingMessage")
        if (incomingMessage == sm.ACK) {
            sm.updateLog("Akun berhasil dihapus!")
            sm.deleteAccount()
        } else {
            sm.updateLog("Akun gagal dihapus!")
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
    override fun onTransition() {
        super.onTransition()
        sm.updateStatus("ALARM ON!")
        sm.changeState(RequestState(sm))
    }

    override fun onBTDisconnect() {
        sm.updateLog(BT_DISCONNECT_MSG)
//        sm.updateUI("To DisconnectState")
        sm.changeState(DisconnectState(sm))
    }
}