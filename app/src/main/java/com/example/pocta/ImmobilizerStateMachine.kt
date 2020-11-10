package com.example.pocta

import android.bluetooth.BluetoothDevice
import android.util.Base64
import android.util.Log
import java.security.KeyPair
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

const val BT_DISCONNECT_MSG = "Saluran Bluetooth terputus."
const val BT_CONNECT_MSG = "Saluran Bluetooth tersambung"

/**
 * The state machine for data processing and communication between an Android
 * phone and an ImmobilizerITB device
 *
 * @param context Context of the activity/service that calls the state machine
 * @param extHandler Handler in the activity/service that calls the state machine, to notify
 * data update in the state machine to the callee
 */
class ImmobilizerStateMachine(val io: ImmobilizerStateMachineIO) {
    private var btDevice: BluetoothDevice? = null
    private var hpRSAKeyPair: KeyPair = io.getRSAKey()
    private var myKey: SecretKey = io.getDefaultKey()
    private var appState: PhoneState = DisconnectState(this)
    val RETRY = "4"
    val ACK_UNL = "3"
    val ERR = "2"
    val ACK = "1"
    val NACK = "0"
    var deviceName = btDevice?.name ?: "Device_PH"
    var deviceAddress = btDevice?.address
    var deviceStatus: String = "Disconnected"
    var userRequest: ImmobilizerUserRequest = ImmobilizerUserRequest.NOTHING
    var isConnected: Boolean = false

    companion object {
        const val TAG = "PhoneStateMachine"
    }

    /**
     * Change to a new state
     * @param state The desired new state
     */
    fun changeState(state: PhoneState) {
        appState = state
        appState.onTransition()
    }

    /* FSM events */
    /**
     * Bluetooth data input event
     * @param bytes Bluetooth input data array
     * @param len Size of the input data array
     */
    fun onBTInput(bytes: ByteArray, len: Int) = appState.onBTInput(bytes, len)

    /** Bluetooth data output event
     * @param bytes Bluetooth output data array
     */
    fun onBTOutput(bytes: ByteArray) = appState.onBTOutput(bytes)

    /**
     * New user request event
     * @param req New user request
     */
    fun onUserRequest(req: ImmobilizerUserRequest) {
        userRequest = req
        appState.onUserRequest()
    }

    /**
     * User input event
     * @param bytes User input data array
     */
    fun onUserInput(bytes: ByteArray) = appState.onUserInput(bytes)

    /**
     * Bluetooth new connection event
     */
    fun onBTConnection() = appState.onBTConnection()

    /**
     * Bluetooth disconnect event
     */
    fun onBTDisconnect() = appState.onBTDisconnect()

    /* Common methods for all states */
    /**
     * Initialize connection to a device
     * @param device The target device
     * @param initialRequest Initial user request
     * @param customName The user facing device name, to be shown on the UI
     * @note If req is not {@link USER_REQUEST#NOTHING}, onUserRequest event will be triggered
     * at the end of function call
     * @note Will disconnect from the currently connected device
     */
    fun initConnection(
        device: BluetoothDevice,
        initialRequest: ImmobilizerUserRequest = ImmobilizerUserRequest.NOTHING,
        secretKey: SecretKey,
        customName: String? = null
    ) {
        Log.i(TAG, "Initializing connection at ${device.address}")
        // Reset State and Connection
        disconnect()
//        if (appState != DisconnectState(this))
//            appState = DisconnectState(this)
        // Set private variables
        btDevice = device
        deviceName = customName ?: device.name
        deviceAddress = device.address
        userRequest = initialRequest
        myKey = secretKey
        io.setAESKey(myKey)
//        bt.apply {
//            setAESKey(myKey)
//            useOutputEncryption = false
//            useInputDecryption = false
//        }
        if (userRequest != ImmobilizerUserRequest.NOTHING)
            onUserRequest(initialRequest)
    }

    /**
     * Connect to the stored device
     * @note Don't use this
     */
    fun connect() {
        if (btDevice != null)
//            bt.startClient(btDevice, MyBluetoothService.uuid)
            io.startBtClient(btDevice!!)
        else
            Log.e(TAG, "btDevice is null")
    }

    /**
     * Disconnect from the device
     */
    fun disconnect() {
        isConnected = false
        io.stopBtClient()
    }

    /**
     * Send unencrypted data to the device
     * @param bytes The data to be sent
     */
    fun sendData(bytes: ByteArray) {
//        disableEncryption()
//        bt.write(bytes)
        io.writeBt(bytes)
    }

    /**
     * Send encrypted data to the device
     * @param bytes The data to be sent
     */
    fun sendEncryptedData(bytes: ByteArray) {
//        enableEncryption()
//        bt.write(bytes)
//        disableEncryption()
        io.writeBt(bytes, encrypted = true)
    }

    /**
     * Notify log update upstream
     * @param logUpdate Newest log update
     */
    fun updateLog(logUpdate: String) {
        io.updateLog(logUpdate)
    }

    /**
     * Notify state machine's status change upstream to the activity/service
     * @param status Newest state machine status
     */
    fun updateStatus(status: String) {
        deviceStatus = status
//        extHandler.obtainMessage(MESSAGE_STATUS, statusStr).sendToTarget()
        io.updateStatus(deviceStatus)
    }

    /**
     * Notify the activity/service to show PIN prompts to the user
     * @param str Message to be shown on PIN prompt screen
     */
    fun promptUserPinInput(str: String) {
        updateLog(str)
//        extHandler.obtainMessage(MESSAGE_PROMPT_PIN, str).sendToTarget()
        io.promptUser(ImmobilizerIOEvent.MESSAGE_PROMPT_PIN.code, str)
    }

    /**
     * Notify the activity/service to show Device Naming prompts to the user
     * @param address Newest log update. Doesn't have any direct effect to the Device Naming
     * prompt UI
     */
    fun promptUserNameInput(address: String?) {
        val myAddress = address ?: "0"
        updateLog("Renaming $myAddress")
//        extHandler.obtainMessage(MESSAGE_PROMPT_RENAME, myAddress).sendToTarget()
        io.promptUser(ImmobilizerIOEvent.MESSAGE_PROMPT_RENAME.code, myAddress)
    }

    /**
     * Disable bluetooth output encryption
     */
    fun disableEncryption() {
        updateLog("Encryption Off")
//        bt.useOutputEncryption = false
        io.setBtEncryption(false)
    }

    /**
     * Enable bluetooth output encryption
     */
    fun enableEncryption() {
        updateLog("Encryption On")
//        bt.useOutputEncryption = true
        io.setBtEncryption(true)
    }

    /**
     * Disable bluetooth input decryption
     */
    fun disableDecryption() {
        updateLog("Decryption Off")
//        bt.useInputDecryption = false
        io.setBtDecryption(false)
    }

    /**
     * Enable bluetooth input decryption
     */
    fun enableDecryption() {
        updateLog("Decryption On")
//        bt.useInputDecryption = true
        io.setBtDecryption(true)
    }

    /**
     * Set state machine's RSA key
     * @param kp The keypair to use
     */
    fun setPubKey(kp: KeyPair) {
        hpRSAKeyPair = kp
    }

    /**
     * Set state machine's AES key
     * @param newKey The AES secret key to use
     */
    fun setMyAESKey(newKey: SecretKey) {
//        hub.setStoredKey(btDevice.address, myKey)
        myKey = newKey
        io.setAESKey(myKey)
    }

    /**
     * Update the Immobilizer database with the currently stored device's address,
     * name, and cipherkey
     */
    fun updateDatabase() {
        if (btDevice != null)
            io.addImmobilizer(btDevice!!.address, btDevice!!.name, myKey)
    }

    /**
     * Update the Immobilizer database with the currently stored device's address,
     * name, and cipherkey
     */
    fun updateDatabase(name: String) {
        if (btDevice != null)
            io.addImmobilizer(btDevice!!.address, name, myKey)
    }

    /**
     * Rename an immobilizer
     * @param address Address of the immobilizer
     * @param name The desired user-facing name of the immobilizer
     */
    fun renameImmobilizer(address: String, name: String) {
        io.renameImmobilizer(address, name)
        val currentAddress = btDevice?.address ?: "0"
        if (address == currentAddress) {
            deviceName = name
            updateStatus(deviceStatus)
        }
    }

    /**
     * Delete current device from Immobilizer database
     */
    fun deleteAccount() {
        if (btDevice != null)
            io.deleteImmobilizer(btDevice!!.address)
    }

    /**
     * Send state machine's stored RSA public key to the Immobilizer device
     */
    fun sendRSAPubKey() {
        val publicKeyHeader = "-----BEGIN PUBLIC KEY-----"
        val publicKeyBottom = "-----END PUBLIC KEY-----"
        val encodedPublicKey = Base64.encodeToString(
            hpRSAKeyPair.public?.encoded, Base64.DEFAULT
        )
        val publicKeyString = "$publicKeyHeader\n$encodedPublicKey$publicKeyBottom"
        sendData(publicKeyString.toByteArray())
    }

    /**
     * Decrypt AES secret key
     * @param bytes The encrryped AES secret key, encrypted with RSA
     */
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

/**
 * State prototype class for the state machine
 * @param sm State machine that stores the state (??)
 * @note Don't mind the description
 */
open class PhoneState(val sm: ImmobilizerStateMachine) {
    /** @see ImmobilizerStateMachine#onTransition */
    open fun onTransition() {}

    /** @see ImmobilizerStateMachine#onBTInput */
    open fun onBTInput(bytes: ByteArray, len: Int) {}

    /** @see ImmobilizerStateMachine#onBTOutput */
    open fun onBTOutput(bytes: ByteArray) {
        val outgoingMessage = String(bytes)
        sm.updateLog("You : $outgoingMessage")
    }

    /** @see ImmobilizerStateMachine#onUserRequest */
    open fun onUserRequest() {}

    /** @see ImmobilizerStateMachine#onUserInput */
    open fun onUserInput(bytes: ByteArray) {}

    /** @see ImmobilizerStateMachine#onBTConnection */
    open fun onBTConnection() {
        sm.isConnected = true
        sm.updateLog(BT_CONNECT_MSG)
    }

    /** @see ImmobilizerStateMachine#onBTDisconnect */
    open fun onBTDisconnect() {
        sm.isConnected = false
        sm.updateLog(BT_DISCONNECT_MSG)
    }
}

/**
 * Disconnected State. In this state the phone is not connected to any device.
 * @param sm The state machine that stores the state
 */
class DisconnectState(sm: ImmobilizerStateMachine) : PhoneState(sm) {
    override fun onTransition() {
        super.onTransition()
        sm.updateStatus("Disconnected")
    }

    override fun onUserRequest() {
        if (sm.userRequest != ImmobilizerUserRequest.NOTHING)
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

/**
 * Connect State. In this state the phone is connected to a device, but haven't received a
 * confirmation that it can send a request.
 * @param sm The state machine that stores the state
 */
class ConnectState(sm: ImmobilizerStateMachine) : PhoneState(sm) {
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
                sm.updateLog("HP anda dikenal")
                sm.changeState(RequestState(sm))
            }
            sm.ACK_UNL -> {
                sm.updateLog("HP anda dikenal, immobilizer terbuka")
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

/**
 * Request State. In this state the phone is connected to a device, and can send a request
 * to the device.
 * @param sm The state machine that stores the state
 */
class RequestState(sm: ImmobilizerStateMachine) : PhoneState(sm) {
    override fun onTransition() {
        super.onTransition()
        sm.updateStatus("Connected, locked")
        if (sm.userRequest != ImmobilizerUserRequest.NOTHING)
            onUserRequest()
    }

    override fun onBTInput(bytes: ByteArray, len: Int) {
        val incomingMessage = String(bytes, 0, len)
        sm.updateLog("${sm.deviceName} : $incomingMessage")
        if (incomingMessage == sm.ACK) {
            if (sm.userRequest == ImmobilizerUserRequest.REGISTER_PHONE) {
//                sm.updateUI("To KeyExchangeState")
                sm.sendRSAPubKey()
                sm.changeState(KeyExchangeState(sm))
            } else {
//                sm.updateUI("To ChallengeState")
                sm.changeState(ChallengeState(sm))
            }
        } else {
            sm.userRequest = ImmobilizerUserRequest.NOTHING
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

/**
 * Challenge State. In this state the phone is waiting the device to send a nonce, for
 * challenge-response verification.
 * @param sm The state machine that stores the state
 */
class ChallengeState(sm: ImmobilizerStateMachine) : PhoneState(sm) {
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

/**
 * Response State. In this state the phone has sent a response to the device, and is waiting
 * for confirmation from the device.
 * @param sm The state machine that stores the state
 */
class ResponseState(sm: ImmobilizerStateMachine) : PhoneState(sm) {
    override fun onTransition() {
        super.onTransition()
        sm.updateStatus("Sending response")
    }

    override fun onBTInput(bytes: ByteArray, len: Int) {
        val incomingMessage = String(bytes, 0, len)
        sm.updateLog("${sm.deviceName} : $incomingMessage")
        if (incomingMessage == sm.ACK) {
            sm.promptUserPinInput("Masukkan PIN anda!")
//            sm.updateUI("To pin state")
            sm.changeState(PinState(sm))
        } else {
            sm.userRequest = ImmobilizerUserRequest.NOTHING
            sm.updateLog("HP anda tidak dikenal\nTo AlarmState")
            sm.changeState(AlarmState(sm))
        }
    }

    override fun onBTDisconnect() {
        sm.updateLog(BT_DISCONNECT_MSG)
        sm.changeState(DisconnectState(sm))
    }
}

/**
 * Pin State. In this state the phone is waiting for user to type his/her PIN.
 * @param sm The state machine that stores the state
 */
class PinState(sm: ImmobilizerStateMachine) : PhoneState(sm) {
    private var oldRequest = ImmobilizerUserRequest.NOTHING

    override fun onTransition() {
        super.onTransition()
        sm.updateStatus("Enter your PIN")
        oldRequest = sm.userRequest
        sm.userRequest = ImmobilizerUserRequest.NOTHING // cegah request berulang
    }

    override fun onBTInput(bytes: ByteArray, len: Int) {
        val incomingMessage = String(bytes, 0, len)
        sm.updateLog("${sm.deviceName} : $incomingMessage")
        when (incomingMessage) {
            sm.ACK -> {
                sm.updateLog("Password anda benar!")
                when (oldRequest) {
                    ImmobilizerUserRequest.UNLOCK -> {
                        sm.disableEncryption()
                        sm.changeState(UnlockState(sm))
                    }
                    ImmobilizerUserRequest.CHANGE_PIN -> {
                        sm.enableEncryption()
                        sm.promptUserPinInput("Masukkan PIN baru anda!")
                        sm.changeState(NewPinState(sm))
                    }
                    ImmobilizerUserRequest.REMOVE_PHONE -> {
                        sm.disableEncryption()
                        sm.changeState(DeleteState(sm))
                    }
                    else -> {
                        sm.userRequest = ImmobilizerUserRequest.NOTHING
                        sm.disableEncryption()
                        sm.changeState(RequestState(sm))
                    }
                }
            }
            sm.RETRY -> {
                sm.promptUserPinInput("Masukkan PIN anda!")
            }
            else -> {
                sm.updateLog("Password anda salah")
                sm.changeState(AlarmState(sm))
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

/**
 * Disconnected State. In this state the immobilizer is unlocked, and user's option is to
 * either lock the immobilizer or disconnect from the immobilizer.
 * @param sm The state machine that stores the state
 */
class UnlockState(sm: ImmobilizerStateMachine) : PhoneState(sm) {
    override fun onTransition() {
        super.onTransition()
        sm.updateStatus("Unlocked")
        // Sebelum state ini dipanggil dari state pin, sharusnya request sudah clear.
        // Jika tidak, berarti state dipanggil dari state unlock-disconnected, yg berarti
        // pengguna meminta immobilizer kembali ke posisi lock
        if (sm.userRequest == ImmobilizerUserRequest.UNLOCK)
            onUserRequest()
    }

    override fun onBTInput(bytes: ByteArray, len: Int) {
        val incomingMessage = String(bytes, 0, len)
        sm.updateLog("${sm.deviceName} : $incomingMessage")
        when (incomingMessage) {
            sm.ACK -> {
                sm.userRequest = ImmobilizerUserRequest.NOTHING
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

/**
 * New Pin State. In this state the phone is waiting for user to type his/her new PIN.
 * @param sm The state machine that stores the state
 */
class NewPinState(sm: ImmobilizerStateMachine) : PhoneState(sm) {
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
            ImmobilizerUserRequest.REGISTER_PHONE -> {
                sm.updateDatabase()
                sm.changeState(RegisterState(sm))
            }
            else -> {
                sm.userRequest = ImmobilizerUserRequest.NOTHING
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

/**
 * Register State. Not implemented.
 * @param sm The state machine that stores the state
 */
class RegisterState(sm: ImmobilizerStateMachine) : PhoneState(sm) {
    override fun onTransition() {
        super.onTransition()
        sm.promptUserNameInput(sm.deviceAddress)
        sm.updateStatus("Registering device")
        sm.userRequest = ImmobilizerUserRequest.NOTHING
        sm.changeState(RequestState(sm))
    }

    override fun onBTDisconnect() {
        sm.updateLog(BT_DISCONNECT_MSG)
        sm.changeState(DisconnectState(sm))
    }
}

/**
 * Key exchange State. In this state the phone is sending it's RSA public key, and in return,
 * is waiting for the immobilizer to send it's AES key.
 * @param sm The state machine that stores the state
 */
class KeyExchangeState(sm: ImmobilizerStateMachine) : PhoneState(sm) {
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
                sm.updateLog("Pertukaran kunci berhasil!")
                sm.enableEncryption()
                sm.promptUserPinInput("Masukkan PIN baru anda!")
//                sm.updateUI("To NewPinState")
                sm.changeState(NewPinState(sm))
            }
            sm.NACK -> {
                // Pertukaran kunci gagal
                sm.updateLog("Pertukaran kunci gagal!")
//                sm.updateUI("To RequestState")
                sm.userRequest = ImmobilizerUserRequest.NOTHING
                sm.changeState(RequestState(sm))
            }
            else -> {
                // Dekripsi kunci dari device dan load
                sm.updateLog("Mendekripsi secret key")
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

/**
 * Delete State. In this state the phone is deleting the Immobilizer from it's registered
 * immobilizer database.
 * @param sm The state machine that stores the state
 */
class DeleteState(sm: ImmobilizerStateMachine) : PhoneState(sm) {
    override fun onTransition() {
        super.onTransition()
        sm.updateStatus("Deleting account")
    }

    override fun onBTInput(bytes: ByteArray, len: Int) {
        val incomingMessage = String(bytes, 0, len)
        sm.updateLog("${sm.deviceName} : $incomingMessage")
        sm.userRequest = ImmobilizerUserRequest.NOTHING
        if (incomingMessage == sm.ACK) {
            sm.updateLog("Akun berhasil dihapus!")
            sm.deleteAccount()
            sm.sendData(sm.ACK.toByteArray())
        } else {
            sm.updateLog("Akun gagal dihapus!")
            sm.changeState(RequestState(sm))
        }
    }

    override fun onBTDisconnect() {
        super.onBTDisconnect()
        sm.changeState(DisconnectState(sm))
    }
}

/**
 * Alarm State. In this state the phone is getting information that the immobilizer's alarm
 * is turned on.
 * @param sm The state machine that stores the state
 */
class AlarmState(sm: ImmobilizerStateMachine) : PhoneState(sm) {
    override fun onTransition() {
        super.onTransition()
        sm.userRequest = ImmobilizerUserRequest.NOTHING
        sm.updateStatus("ALARM ON!")
        sm.changeState(RequestState(sm))
    }

    override fun onBTDisconnect() {
        sm.updateLog(BT_DISCONNECT_MSG)
//        sm.updateUI("To DisconnectState")
        sm.changeState(DisconnectState(sm))
    }
}