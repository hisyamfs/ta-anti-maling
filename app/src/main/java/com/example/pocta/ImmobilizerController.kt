package com.example.pocta

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Handler
import android.os.Message
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.security.KeyPair
import java.util.*
import javax.crypto.SecretKey


class ImmobilizerController(private val context: Context) : ImmobilizerStateMachineIO {
    private val btHandler = BluetoothHandler(this)
    private val dao = ImmobilizerDatabase.getDatabase(context).immobilizerDao()
    private val cm = UserCredentialManager(context)
    private val btService = ImmobilizerBluetoothService(btHandler)

    /** Occasionally need to be used outside the controller **/
    val stateMachine: ImmobilizerStateMachine = ImmobilizerStateMachine(this)
    val adapter = btService.btAdapter

    /** LiveData of UI infos **/
    val immobilizerListLD: LiveData<List<Immobilizer>> = dao.getImmobilizerList()
    val addressListLD: LiveData<List<String>> = dao.getAddressList()
    val immobilizerLogLD: MutableLiveData<String> = MutableLiveData()
    val activeImmobilizerLD: MutableLiveData<ActiveImmobilizer> = MutableLiveData(
        ActiveImmobilizer("-", "Disconnected")
    )
    val userPromptLD: MutableLiveData<UserPrompt> = MutableLiveData(
        UserPrompt(ImmobilizerIOEvent.MESSAGE_NAH.code, "Nah", false)
    )
    val toastLD: MutableLiveData<UserPrompt> = MutableLiveData(
        UserPrompt(ImmobilizerIOEvent.MESSAGE_TOAST.code, "", false)
    )

    /** Helper variables **/
    private val userKeyPair = getRSAKey()
    var activeImmobilizer = ActiveImmobilizer("-", "Disconnected")
    private val TAG = "ImmobilizerController"

    /** ImmobilizerStateMachineIO Implementations **/
    override fun readBt(bytes: ByteArray) =
        stateMachine.onBTInput(bytes, bytes.size)

    override fun setActiveConnection(
        immobilizer: Immobilizer,
        initRequest: ImmobilizerUserRequest
    ) {
        if (stateMachine.isConnected && immobilizer.address == stateMachine.deviceAddress) {
            activeImmobilizer.name = immobilizer.name
            activeImmobilizerLD.postValue(activeImmobilizer)
            stateMachine.onUserRequest(initRequest)
        } else if (adapter.isEnabled) {
            val decryptedKey =
                cm.decryptSecretKey(immobilizer.key, userKeyPair.private)
            val device = adapter.getRemoteDevice(immobilizer.address)
            activeImmobilizer.name = immobilizer.name
            activeImmobilizerLD.postValue(activeImmobilizer)
            stateMachine.initConnection(device, initRequest, decryptedKey, immobilizer.name)
        } else
            Log.i(TAG, "Bluetooth not enabled")
    }

    override fun setActiveConnection(
        address: String,
        name: String,
        initRequest: ImmobilizerUserRequest
    ) {
        if (stateMachine.isConnected && address == stateMachine.deviceAddress)
            stateMachine.onUserRequest(initRequest)
        else if (adapter.isEnabled) {
            val device = adapter.getRemoteDevice(address)
            stateMachine.initConnection(device, initRequest, getDefaultKey(), name)
        } else {
            Log.i(TAG, "Bluetooth not enabled")
        }
    }

    override fun setUserRequest(request: ImmobilizerUserRequest) =
        stateMachine.onUserRequest(request)

    override fun setUserInput(bytes: ByteArray) =
        stateMachine.onUserInput(bytes)

    override fun writeBt(bytes: ByteArray, encrypted: Boolean) {
        btService.apply {
            useOutputEncryption = encrypted
            write(bytes)
        }
    }

    override fun setBtEncryption(enableEncryption: Boolean) {
        btService.useOutputEncryption = enableEncryption
    }

    override fun setBtDecryption(enableDecryption: Boolean) {
        btService.useInputDecryption = enableDecryption
    }

    override fun getRSAKey(): KeyPair {
        return cm.getStoredRSAKeyPair() ?: cm.getDefaultRSAKeyPair()
    }

    override fun getDefaultKey(): SecretKey {
        return cm.getDefaultSymmetricKey()
    }

    override fun setAESKey(secretKey: SecretKey) {
        btService.setAESKey(secretKey)
    }

    override fun startBtClient(device: BluetoothDevice, uuid: UUID) {
        btService.startClient(device, uuid)
    }

    override fun stopBtClient() {
        btService.stop()
    }

    override fun updateLog(logUpdate: String) {
        val prevUpdate: String = immobilizerLogLD.value ?: ""
        val newLog = prevUpdate + "\n" + logUpdate
        immobilizerLogLD.postValue(newLog)
    }

    override fun updateStatus(deviceName: String, statusUpdate: String) {
        activeImmobilizer.name = deviceName
        activeImmobilizer.status = statusUpdate
        activeImmobilizerLD.postValue(activeImmobilizer)
    }

    override fun promptUser(promptView: Int, promptMessage: String) {
        val prompt = UserPrompt(promptView, promptMessage, true)
        userPromptLD.postValue(prompt)
    }

    override fun clearPrompt() {
        val prompt = UserPrompt(
            ImmobilizerIOEvent.MESSAGE_NAH.code, "NAH", false
        )
        userPromptLD.postValue(prompt)
    }

    override fun showToast(message: String) {
        val toast =
            UserPrompt(ImmobilizerIOEvent.MESSAGE_TOAST.code, message, true)
        toastLD.postValue(toast)
    }

    override fun clearToast() {
        val toast =
            UserPrompt(ImmobilizerIOEvent.MESSAGE_TOAST.code, "", false)
        toastLD.postValue(toast)
    }

    override fun renameImmobilizer(address: String, newName: String) {
        GlobalScope.launch(Dispatchers.IO) {
            dao.renameImmobilizer(address, newName)
            if (address == stateMachine.deviceAddress) {
                activeImmobilizer.name = newName
                activeImmobilizerLD.postValue(activeImmobilizer)
            }
        }
    }

    override fun addImmobilizer(address: String, name: String, key: SecretKey) {
        GlobalScope.launch(Dispatchers.IO) {
            val encryptedKey =
                cm.encryptSecretKey(key, userKeyPair.public)
            encryptedKey?.let {
                dao.addImmobilizer(Immobilizer(address, name, encryptedKey))
            }
        }
    }

    override fun deleteImmobilizer(address: String) {
        GlobalScope.launch(Dispatchers.IO) {
            dao.deleteImmobilizer(address)
        }
    }

    /** Extra functions **/
    fun deleteAllImmobilizer() {
        GlobalScope.launch(Dispatchers.IO) {
            dao.deleteAll()
        }
    }
}

/**
 * Handler for communication with MyBluetoothService
 * @param sm PhoneStateMachine that will receive data from MyBluetothService
 */
class BluetoothHandler(controller: ImmobilizerController) : Handler() {
    private val rController = WeakReference<ImmobilizerController>(controller)
    override fun handleMessage(msg: Message) {
        rController.get()?.apply {
            when (msg.what) {
                ImmobilizerBluetoothService.MESSAGE_READ -> {
                    val incomingBytes = msg.obj as ByteArray
                    readBt(incomingBytes)
                }
                ImmobilizerBluetoothService.MESSAGE_WRITE -> {
                    val outgoingBytes = msg.obj as ByteArray
                    stateMachine.onBTOutput(outgoingBytes)
                }
                ImmobilizerBluetoothService.CONNECTION_LOST -> {
//                    activeImmobilizer.name = "-"
                    stateMachine.onBTDisconnect()
                }
                ImmobilizerBluetoothService.CONNECTION_START ->
                    stateMachine.onBTConnection()
            }
        }
    }
}