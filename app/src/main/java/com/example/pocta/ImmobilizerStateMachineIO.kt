package com.example.pocta

import android.bluetooth.BluetoothDevice
import java.security.KeyPair
import java.util.*
import javax.crypto.SecretKey

val IMMO_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

enum class ImmobilizerIOEvent(val code: Int) {
    MESSAGE_LOG(0),
    MESSAGE_STATUS(1),
    MESSAGE_PROMPT_PIN(2),
    MESSAGE_PROMPT_RENAME(3),
    MESSAGE_NAH(4)
}

interface ImmobilizerStateMachineIO {
    fun setActiveConnection(
        immobilizer: Immobilizer,
        initRequest: ImmobilizerUserRequest
    )

    fun setActiveConnection(
        address: String,
        name: String,
        initRequest: ImmobilizerUserRequest
    )

    fun setUserRequest(request: ImmobilizerUserRequest)
    fun setUserInput(bytes: ByteArray)
    fun readBt(bytes: ByteArray)
    fun writeBt(bytes: ByteArray, encrypted: Boolean = false)
    fun setBtEncryption(enableEncryption: Boolean)
    fun setBtDecryption(enableDecryption: Boolean)
    fun getRSAKey(): KeyPair
    fun getDefaultKey(): SecretKey
    fun setAESKey(secretKey: SecretKey)
    fun startBtClient(device: BluetoothDevice, uuid: UUID = IMMO_UUID)
    fun stopBtClient()
    fun updateLog(logUpdate: String)
    fun updateStatus(statusUpdate: String)
    fun promptUser(promptView: Int, promptMessage: String)
    fun clearPrompt()
    fun renameImmobilizer(address: String, newName: String)
    fun addImmobilizer(address: String, name: String, key: SecretKey)
    fun deleteImmobilizer(address: String)
}