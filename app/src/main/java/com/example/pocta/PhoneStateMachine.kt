package com.example.pocta

import android.widget.TextView

enum class USER_REQUEST(val reqString: String) {
    NOTHING("!0"),
    UNLOCK("!1"),
    CHANGE_PIN("!2"),
    REGISTER_PHONE("!3"),
    REMOVE_PHONE("!4"),
    DISABLE("!5")
}

class PhoneStateMachine(private val bt: MyBluetoothService, private val ui: TextView) {
    val ACK = "1"
    val NACK = "0"

    // TODO("Ganti agar menggunakan nilai dari MyBluetoothService")
    val userId = "test"
    val deviceName = "Device_PH"

    private var appState: PhoneState = NormalState(this)
    var userRequest: USER_REQUEST = USER_REQUEST.NOTHING

    /* Handle state transition */
    fun changeState(state: PhoneState) {
        appState = state
    }

    /* UI and Bluetooth handler delegate to active state */
    fun onBTInput(bytes: ByteArray, len: Int) = appState.onBTInput(bytes, len)
    fun onBTOutput(bytes: ByteArray) = appState.onBTOutput(bytes)
    fun onUserRequest() = appState.onUserRequest()
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

abstract class PhoneState(val sm: PhoneStateMachine) {
    abstract fun onBTInput(bytes: ByteArray, len: Int)
    abstract fun onBTOutput(bytes: ByteArray)
    abstract fun onUserRequest()
    abstract fun onUserInput(bytes: ByteArray)
    abstract fun onBTConnection()
    abstract fun onBTDisconnect()
}

class NormalState(sm: PhoneStateMachine) : PhoneState(sm) {
    override fun onBTInput(bytes: ByteArray, len: Int) {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        val incomingMessage = String(bytes, 0, len)
        sm.updateUI("${sm.deviceName} : $incomingMessage")
    }

    override fun onBTOutput(bytes: ByteArray) {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        val outgoingMessage = String(bytes)
        sm.updateUI("You : $outgoingMessage")
    }

    override fun onUserRequest() {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        sm.sendData(sm.userRequest.reqString.toByteArray())
        sm.changeState(RequestState(sm))
    }

    override fun onUserInput(bytes: ByteArray) {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        sm.sendData(bytes)
    }

    override fun onBTConnection() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onBTDisconnect() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

class RequestState(sm: PhoneStateMachine): PhoneState(sm) {
    override fun onBTInput(bytes: ByteArray, len: Int) {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        val incomingMessage = String(bytes, 0, len)
        if (incomingMessage == sm.ACK) {
            sm.sendData(sm.userId.toByteArray())
            sm.changeState(IdCheckState(sm))
        } else {
            sm.updateUI("Request unrecognized or unimplemented")
            sm.changeState(NormalState(sm))
        }
    }

    override fun onBTOutput(bytes: ByteArray) {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        val outgoingMessage = String(bytes)
        sm.updateUI("You : $outgoingMessage")
    }

    override fun onUserRequest() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onUserInput(bytes: ByteArray) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onBTConnection() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onBTDisconnect() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}

class IdCheckState(sm: PhoneStateMachine): PhoneState(sm) {
    override fun onBTInput(bytes: ByteArray, len: Int) {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        val incomingMessage = String(bytes, 0, len)
        if (incomingMessage == sm.ACK) {
            sm.changeState(ChallengeState(sm))
        } else {
            sm.updateUI("User ID not found")
            sm.changeState(NormalState(sm))
        }
    }

    override fun onBTOutput(bytes: ByteArray) {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        val outgoingMessage = String(bytes)
        sm.updateUI("You : $outgoingMessage")
    }

    override fun onUserRequest() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onUserInput(bytes: ByteArray) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onBTConnection() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onBTDisconnect() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}

class ChallengeState(sm: PhoneStateMachine): PhoneState(sm) {
    override fun onBTInput(bytes: ByteArray, len: Int) {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        sm.sendEncryptedData(bytes)
        sm.changeState(ResponseState(sm))
    }

    override fun onBTOutput(bytes: ByteArray) {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        val outgoingMessage = String(bytes)
        sm.updateUI("You : $outgoingMessage")
    }

    override fun onUserRequest() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onUserInput(bytes: ByteArray) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onBTConnection() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onBTDisconnect() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}

class ResponseState(sm: PhoneStateMachine): PhoneState(sm) {
    override fun onBTInput(bytes: ByteArray, len: Int) {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        val incomingMessage = String(bytes, 0, len)
        if (incomingMessage == sm.ACK) {
            sm.promptUserInput("Masukkan password anda!")
            sm.changeState(PinState(sm))
        } else {
            sm.updateUI("HP anda tidak dikenal")
            sm.changeState(NormalState(sm))
        }
    }

    override fun onBTOutput(bytes: ByteArray) {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        val outgoingMessage = String(bytes)
        sm.updateUI("You : $outgoingMessage")
    }

    override fun onUserRequest() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onUserInput(bytes: ByteArray) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onBTConnection() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onBTDisconnect() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}

class PinState(sm: PhoneStateMachine): PhoneState(sm) {
    override fun onBTInput(bytes: ByteArray, len: Int) {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        val incomingMessage = String(bytes, 0, len)
        if (incomingMessage == sm.ACK) {
            sm.updateUI("Password anda benar!")
            when (sm.userRequest) {
                USER_REQUEST.UNLOCK -> {
                    sm.disableEncryption()
                    sm.changeState(UnlockState(sm))
                }
                USER_REQUEST.REGISTER_PHONE, USER_REQUEST.CHANGE_PIN -> {
                    sm.enableEncryption()
                    sm.promptUserInput("Masukkan password baru anda!")
                    sm.changeState(NewPinState(sm))
                }
                else -> {
                    sm.disableEncryption()
                    sm.changeState(NormalState(sm))
                }
            }
        } else {
            sm.updateUI("Password anda salah")
            sm.changeState(NormalState(sm))
        }
    }

    override fun onBTOutput(bytes: ByteArray) {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        val outgoingMessage = String(bytes)
        sm.updateUI("You : $outgoingMessage")
    }

    override fun onUserRequest() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onUserInput(bytes: ByteArray) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onBTConnection() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onBTDisconnect() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}

class UnlockState(sm: PhoneStateMachine): PhoneState(sm) {
    override fun onBTInput(bytes: ByteArray, len: Int) {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        sm.changeState(NormalState(sm))
    }

    override fun onBTOutput(bytes: ByteArray) {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        val outgoingMessage = String(bytes)
        sm.updateUI("You : $outgoingMessage")
    }

    override fun onUserRequest() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onUserInput(bytes: ByteArray) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onBTConnection() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onBTDisconnect() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}

class NewPinState(sm: PhoneStateMachine): PhoneState(sm) {
    override fun onBTInput(bytes: ByteArray, len: Int) {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        sm.disableEncryption()
        val incomingMessage = String(bytes, 0, len)
        if (incomingMessage == sm.ACK) {
            sm.updateUI("Password berhasil diperbaharui!")
        } else {
            sm.updateUI("Password gagal didaftarkan")
        }
        sm.changeState(NormalState(sm))
    }

    override fun onBTOutput(bytes: ByteArray) {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        val outgoingMessage = String(bytes)
        sm.updateUI("You : $outgoingMessage")
    }

    override fun onUserRequest() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onUserInput(bytes: ByteArray) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onBTConnection() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onBTDisconnect() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}

class AlarmState(sm: PhoneStateMachine): PhoneState(sm) {
    override fun onBTInput(bytes: ByteArray, len: Int) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onBTOutput(bytes: ByteArray) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onUserRequest() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onUserInput(bytes: ByteArray) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onBTConnection() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onBTDisconnect() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}

class KeyExchangeState(sm: PhoneStateMachine): PhoneState(sm) {
    override fun onBTInput(bytes: ByteArray, len: Int) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onBTOutput(bytes: ByteArray) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onUserRequest() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onUserInput(bytes: ByteArray) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onBTConnection() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onBTDisconnect() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}

class RegisterState(sm: PhoneStateMachine): PhoneState(sm) {
    override fun onBTInput(bytes: ByteArray, len: Int) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onBTOutput(bytes: ByteArray) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onUserRequest() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onUserInput(bytes: ByteArray) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onBTConnection() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onBTDisconnect() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}