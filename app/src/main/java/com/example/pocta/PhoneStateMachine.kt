package com.example.pocta

import android.provider.Settings
import android.util.Base64
import android.widget.TextView

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

class PhoneStateMachine(private val bt: MyBluetoothService, private val ui: TextView) {
    val ERR = '2'
    val ACK = "1"
    val NACK = "0"
    // TODO("Ganti agar menggunakan nilai dari MyBluetoothService")
    var userId = "test"
    //    val userId = Settings.Secure.getString(contentResolver,"bluetooth_address")
    var deviceName = "Device_PH"
    var userRequest: USER_REQUEST = USER_REQUEST.NOTHING

    init {
        bt.useInputDecryption = false
        bt.useOutputEncryption = false
    }

    private var appState: PhoneState = DisconnectState(this)

    /* Handle state transition */
    fun changeState(state: PhoneState) {
        appState = state
    }

    /* UI and Bluetooth handler delegate to active state */
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
    // send unencrypted data to the device
    fun sendData(bytes: ByteArray) {
//         TODO("Not Implemented")
        disableEncryption()
        bt.write(bytes)
    }

    fun sendEncryptedData(bytes: ByteArray) {
//         TODO("Not Implemented")
        enableEncryption()
        bt.write(bytes)
        disableEncryption()
    }

    fun updateUI(str: String) {
//        TODO("Not Implemented")
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
}

open class PhoneState(val sm: PhoneStateMachine) {
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
    override fun onBTConnection() {
        super.onBTConnection()
        sm.updateUI("To ConnectState")
        sm.changeState(ConnectState(sm))
    }

    override fun onBTDisconnect() {
        sm.changeState(DisconnectState(sm))
    }
}

class ConnectState(sm: PhoneStateMachine) : PhoneState(sm) {
    override fun onBTInput(bytes: ByteArray, len: Int) {
        val incomingMessage = String(bytes, 0, len)
        sm.updateUI("${sm.deviceName} : $incomingMessage")
        if (incomingMessage == sm.ACK) {
            sm.updateUI("To RequestState")
            sm.changeState(RequestState(sm))
        } else {
            sm.updateUI("HP anda tidak dikenal/belum terdaftar")
            // TODO("Lakukan penghapusan alamat device dari HP")
            sm.changeState(DisconnectState(sm))
        }
    }

    override fun onBTDisconnect() {
        super.onBTDisconnect()
        sm.updateUI("To DisconnectState")
        sm.changeState(DisconnectState(sm))
    }
}

class RequestState(sm: PhoneStateMachine) : PhoneState(sm) {
    override fun onBTInput(bytes: ByteArray, len: Int) {
        val incomingMessage = String(bytes, 0, len)
        sm.updateUI("${sm.deviceName} : $incomingMessage")
        if (incomingMessage == sm.ACK) {
            sm.updateUI("To ChallengeState")
            sm.changeState(ChallengeState(sm))
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
        sm.updateUI("To DisconnectState")
        sm.changeState(DisconnectState(sm))
    }
}

class ChallengeState(sm: PhoneStateMachine) : PhoneState(sm) {
    override fun onBTInput(bytes: ByteArray, len: Int) {
        val incomingMessage = Base64.encodeToString(bytes, Base64.DEFAULT)
        sm.updateUI("${sm.deviceName} : $incomingMessage")
        sm.sendEncryptedData(bytes)
        sm.updateUI("To response state")
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
    override fun onBTInput(bytes: ByteArray, len: Int) {
        val incomingMessage = String(bytes, 0, len)
        sm.updateUI("${sm.deviceName} : $incomingMessage")
        if (incomingMessage == sm.ACK) {
            sm.promptUserInput("Masukkan password anda!")
            sm.updateUI("To pin state")
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
    override fun onBTInput(bytes: ByteArray, len: Int) {
        val incomingMessage = String(bytes, 0, len)
        sm.updateUI("${sm.deviceName} : $incomingMessage")
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
    override fun onBTInput(bytes: ByteArray, len: Int) {
        sm.changeState(RequestState(sm))
    }

    override fun onBTDisconnect() {
        sm.updateUI(BT_DISCONNECT_MSG)
        sm.changeState(DisconnectState(sm))
    }
}

class NewPinState(sm: PhoneStateMachine) : PhoneState(sm) {
    override fun onBTInput(bytes: ByteArray, len: Int) {
        val incomingMessage = String(bytes, 0, len)
        sm.updateUI("${sm.deviceName} : $incomingMessage")
        if (incomingMessage == sm.ACK) {
            sm.updateUI("Password berhasil diperbaharui!")
        } else {
            sm.updateUI("Password gagal didaftarkan")
        }
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
    override fun onBTInput(bytes: ByteArray, len: Int) {
        val incomingMessage = String(bytes, 0, len)
        sm.updateUI("${sm.deviceName} : $incomingMessage")
        if (incomingMessage == sm.ACK) {
            // TODO("Lakukan penghapusan akun terdaftar di HP juga")
            sm.updateUI("Akun berhasil dihapus!")
        } else {
            sm.updateUI("Akun gagal dihapus!")
        }
        sm.changeState(RequestState(sm))
    }

    override fun onBTDisconnect() {
        super.onBTDisconnect()
        sm.changeState(DisconnectState(sm))
    }
}

class AlarmState(sm: PhoneStateMachine) : PhoneState(sm) {
    override fun onBTInput(bytes: ByteArray, len: Int) {
        val incomingMessage = String(bytes, 0, len)
        sm.updateUI("${sm.deviceName} : $incomingMessage")
        if (incomingMessage == sm.ACK) {
            sm.updateUI("To RequestState")
            sm.changeState(RequestState(sm))
        }
    }

    override fun onBTDisconnect() {
        sm.updateUI(BT_DISCONNECT_MSG)
        sm.updateUI("To DisconnectState")
        sm.changeState(DisconnectState(sm))
    }
}

class KeyExchangeState(sm: PhoneStateMachine) : PhoneState(sm) {
    override fun onBTDisconnect() {
        sm.updateUI(BT_DISCONNECT_MSG)
        sm.changeState(DisconnectState(sm))
    }
}

class RegisterState(sm: PhoneStateMachine) : PhoneState(sm) {
    override fun onBTDisconnect() {
        sm.updateUI(BT_DISCONNECT_MSG)
        sm.changeState(DisconnectState(sm))
    }

}