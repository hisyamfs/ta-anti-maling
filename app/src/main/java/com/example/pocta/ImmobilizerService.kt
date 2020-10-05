package com.example.pocta

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.widget.Toast
import androidx.core.app.ActivityCompat.startActivityForResult
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.lang.ref.WeakReference

class ImmobilizerHandler(service: ImmobilizerService) : Handler() {
    private val hService = WeakReference<ImmobilizerService>(service)
    override fun handleMessage(msg: Message) {
        hService.get()?.apply {
            when (msg.what) {
                PhoneStateMachine.MESSAGE_UI -> {
                    immobilizerData = msg.obj as String
                    sendResult(immobilizerData)
                }
            }
        }
    }
}

class ImmobilizerService : Service() {
    private var immobilizerStatus: String = "Uhuy!"
    var immobilizerData: String = ""
    private lateinit var smHandler: Handler
    private lateinit var broadcastManager: LocalBroadcastManager

    companion object {
        const val REQUEST_ENABLE_BT = 1
        const val CHANNEL_ID = "ImmobilizerService"
        const val IMMOBILIZER_SERVICE_NEW_DATA = "com.example.pocta.ImmobilizerService.NEW_DATA"
        const val IMMOBILIZER_SERVICE_DATA = "com.example.pocta.ImmobilizerService.DATA"
        lateinit var btAdapter: BluetoothAdapter
        lateinit var btDevice: BluetoothDevice
        lateinit var immobilizerController: PhoneStateMachine

        fun startService(context: Context) {
            val startIntent = Intent(context, ImmobilizerService::class.java)
            ContextCompat.startForegroundService(context, startIntent)
        }

        fun stopService(context: Context) {
            val stopIntent = Intent(context, ImmobilizerService::class.java)
            context.stopService(stopIntent)
        }

        private fun initConnection(
            address: String,
            initRequest: USER_REQUEST = USER_REQUEST.NOTHING
        ) {
            if (btAdapter.isEnabled) {
                btDevice = btAdapter.getRemoteDevice(address)
                immobilizerController.initConnection(btDevice, initRequest)
            }
        }

        fun toggleConnection(address: String, alwaysDisconnect: Boolean = false) {
            if (alwaysDisconnect || immobilizerController.isConnected)
                immobilizerController.disconnect()
            else
                initConnection(address)
        }

        fun sendData(bytes: ByteArray) = immobilizerController.onUserInput(bytes)

        fun sendRequest(request: USER_REQUEST, address: String) {
            if (immobilizerController.isConnected && address == immobilizerController.deviceAddress) {
                immobilizerController.onUserRequest(request)
            } else if (btAdapter.isEnabled){
                btDevice = btAdapter.getRemoteDevice(address)
                immobilizerController.initConnection(btDevice, request)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // TODO("Refactor PhoneStateMachine agar tidak ter-couple ke TextView langsung")
        broadcastManager = LocalBroadcastManager.getInstance(this)
        smHandler = ImmobilizerHandler(this)
        immobilizerController = PhoneStateMachine(this, smHandler)
        btAdapter = BluetoothAdapter.getDefaultAdapter().apply { enable() }

        val pendingIntent: PendingIntent =
            Intent(this, HubActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent, 0)
            }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Foreground Service Immobilizer")
            .setContentText(immobilizerStatus)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        immobilizerController.disconnect()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID, "Foreground Service Immobilizer",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager!!.createNotificationChannel(serviceChannel)
        }
    }

    fun sendResult(message: String) {
        val intent = Intent(IMMOBILIZER_SERVICE_NEW_DATA)
            .putExtra(IMMOBILIZER_SERVICE_DATA, message)
        broadcastManager.sendBroadcast(intent)
    }
}