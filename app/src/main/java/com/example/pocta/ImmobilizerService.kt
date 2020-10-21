package com.example.pocta

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import com.example.pocta.ImmobilizerService.Companion.immobilizerData
import com.example.pocta.ImmobilizerService.Companion.immobilizerStatus
import java.lang.ref.WeakReference

const val IMMOBILIZER_SERVICE_NEW_DATA = "com.example.pocta.ImmobilizerService.NEW_DATA"
const val IMMOBILIZER_SERVICE_LOG = "com.example.pocta.ImmobilizerService.LOG"
const val IMMOBILIZER_SERVICE_STATUS = "com.example.pocta.ImmobilizerService.STATUS"
const val IMMOBILIZER_SERVICE_ADDRESS = "com.example.pocta.ImmobilizerService.ADDRESS"
const val IMMOBILIZER_SERVICE_PROMPT_MESSAGE = "com.example.pocta.ImmobilizerService.PIN_PROMPT_MESSAGE"

class ImmobilizerHandler(service: ImmobilizerService) : Handler() {
    private val hService = WeakReference<ImmobilizerService>(service)
    override fun handleMessage(msg: Message) {
        hService.get()?.apply {
            when (msg.what) {
                PhoneStateMachine.MESSAGE_LOG -> {
                    val newLog = msg.obj as String
                    immobilizerData = "$immobilizerData\n$newLog"
                    immobilizerDataLD.postValue(immobilizerData)
//                    sendResult(IMMOBILIZER_SERVICE_LOG, immobilizerData)
                }
                PhoneStateMachine.MESSAGE_STATUS -> {
                    immobilizerStatus = msg.obj as String
                    immobilizerStatusLD.postValue(immobilizerStatus)
//                    sendResult(IMMOBILIZER_SERVICE_STATUS, immobilizerStatus)
                }
                PhoneStateMachine.MESSAGE_PROMPT_PIN -> {
                    val hint: String = msg.obj as String
                    activatePinScreen(hint)
                }
                PhoneStateMachine.MESSAGE_PROMPT_RENAME -> {
                    val address: String = msg.obj as String
                    activateRenameScreen(address)
                }
            }
        }
    }
}

class ImmobilizerService : Service() {
    private var isStarted = false
    private lateinit var smHandler: Handler
//    private lateinit var broadcastManager: LocalBroadcastManager
    private val imBinder: IBinder = ImmobilizerBinder()
    val immobilizerStatusLD: MutableLiveData<String> = MutableLiveData()
    val immobilizerDataLD: MutableLiveData<String> = MutableLiveData()

    companion object {
        const val REQUEST_ENABLE_BT = 1
        const val TAG = "ImmobilizerService"
        const val CHANNEL_ID = "ImmobilizerService"
        lateinit var btAdapter: BluetoothAdapter
        lateinit var btDevice: BluetoothDevice
        lateinit var immobilizerController: PhoneStateMachine
        var immobilizerStatus: String = "Uhuy!"
        var immobilizerData: String = ""

        /**
         * Start the service
         * @param context Context of the caller
         */
        fun startService(context: Context) {
            val startIntent = Intent(context, ImmobilizerService::class.java)
            ContextCompat.startForegroundService(context, startIntent)
        }

        /**
         * Bind the service to an activity
         * @param context Context of the activity
         * @param connection ServiceConnection object in the activity
         */
        fun bindService(context: Context, connection: ServiceConnection) {
            val bindIntent = Intent(context, ImmobilizerService::class.java)
            context.bindService(bindIntent, connection, Context.BIND_AUTO_CREATE)
        }

        /**
         * Stop the service
         * @param context Context of the caller
         */
        fun stopService(context: Context) {
            val stopIntent = Intent(context, ImmobilizerService::class.java)
            context.stopService(stopIntent)
        }

        /**
         * Initialize a connection to an immobilizer device
         * @param address Address of the immobilizer
         * @param initRequest Initial request to be made after the connection is established
         * @param name User-facing name of the immobilizer
         */
        private fun initConnection(
            address: String,
            initRequest: USER_REQUEST = USER_REQUEST.NOTHING,
            name: String? = null
        ) {
            if (btAdapter.isEnabled) {
                btDevice = btAdapter.getRemoteDevice(address)
                immobilizerController.initConnection(btDevice, initRequest)
            }
        }

        /**
         * Toggle a connection to an immobilizer device
         * @param address Address of the immobilizer
         * @param name User-facing name of the immobilizer
         * @param alwaysDisconnect If true, will allways terminate the connection to the immobilizer
         */
        fun toggleConnection(
            address: String,
            name: String? = null,
            alwaysDisconnect: Boolean = false
        ) {
            if (alwaysDisconnect || immobilizerController.isConnected)
                immobilizerController.disconnect()
            else
                initConnection(address, USER_REQUEST.NOTHING, name)
        }

        /**
         * Send data to an immobilizer device
         * @param bytes The data to be sent
         */
        fun sendData(bytes: ByteArray) = immobilizerController.onUserInput(bytes)

        /**
         * Send a request to an immobilizer device
         * @param request Request to be sent
         * @param address Address of the immobilizer device
         * @param name User-facing name of the immobilizer
         * @note Will initialize a connection if user's phone isn't connected yet to
         * the immobilizer with the specified address
         */
        fun sendRequest(request: USER_REQUEST, address: String, name: String? = null) {
//            Log.i(TAG, "sendRequest() called")
            if (immobilizerController.isConnected && address == immobilizerController.deviceAddress) {
                Log.i(TAG, "sendRequest() called on a connected device")
                immobilizerController.onUserRequest(request)
            } else if (btAdapter.isEnabled) {
                Log.i(TAG, "sendRequest() called on a disconnected device")
                btDevice = btAdapter.getRemoteDevice(address)
                immobilizerController.initConnection(btDevice, request, name)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // TODO("Refactor PhoneStateMachine agar tidak ter-couple ke TextView langsung")
//        broadcastManager = LocalBroadcastManager.getInstance(this)
        smHandler = ImmobilizerHandler(this)
        immobilizerController = PhoneStateMachine(this, smHandler)
        btAdapter = BluetoothAdapter.getDefaultAdapter().apply { enable() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isStarted) {
            startNotification()
            isStarted = true
        }
        return START_NOT_STICKY
    }

    inner class ImmobilizerBinder: Binder() {
        fun getService(): ImmobilizerService = this@ImmobilizerService
    }

    override fun onBind(intent: Intent): IBinder? {
        if (!isStarted) {
            startNotification()
            isStarted = true
        }
        return imBinder
    }

    override fun onDestroy() {
        immobilizerController.disconnect()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID, "Foreground Service Immobilizer",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager =
                getSystemService(NotificationManager::class.java)
            manager!!.createNotificationChannel(serviceChannel)
        }
    }

    private fun startNotification() {
        createNotificationChannel()
        val notificationIntent = Intent(this, HubActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0, notificationIntent, 0
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Foreground Service Immobilizer")
            .setContentText("Is Running")
            .setContentIntent(pendingIntent)
            .build()

        startForeground(1, notification)
    }

//    fun sendResult(message: String, type: String) {
//        val intent = Intent(type)
//            .putExtra(type, message)
//        broadcastManager.sendBroadcast(intent)
//    }

    /**
     * Show the device naming promp
     * @param address Address of the immobilizer to be named
     */
    fun activateRenameScreen(address: String) {
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK
        val intent = Intent(this, RenameActivity::class.java)
            .putExtra(IMMOBILIZER_SERVICE_ADDRESS, address)
            .addFlags(flags)
        startActivity(intent)
    }

    /**
     * Show the PIN prompt
     * @param msg Message to be shown on the PIN prompt screen
     */
    fun activatePinScreen(msg: String) {
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK
        val intent = Intent(this, PinActivity::class.java)
            .addFlags(flags)
            .putExtra(IMMOBILIZER_SERVICE_PROMPT_MESSAGE, msg)
        startActivity(intent)
    }
}