package com.example.pocta

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.Observer


const val IMMOBILIZER_SERVICE_NEW_DATA = "com.example.pocta.ImmobilizerService.NEW_DATA"
const val IMMOBILIZER_SERVICE_LOG = "com.example.pocta.ImmobilizerService.LOG"
const val IMMOBILIZER_SERVICE_STATUS = "com.example.pocta.ImmobilizerService.STATUS"
const val IMMOBILIZER_SERVICE_ADDRESS = "com.example.pocta.ImmobilizerService.ADDRESS"
const val IMMOBILIZER_SERVICE_PROMPT_MESSAGE =
    "com.example.pocta.ImmobilizerService.PIN_PROMPT_MESSAGE"

class ImmobilizerService : LifecycleService() {
    private var isStarted = false
    private val imBinder: IBinder = ImmobilizerBinder()

    companion object {
        const val REQUEST_ENABLE_BT = 1
        const val TAG = "ImmobilizerService"
        const val CHANNEL_ID = "ImmobilizerService"
        lateinit var immobilizerController: ImmobilizerController

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
    }

    override fun onCreate() {
        super.onCreate()
        // TODO("Refactor PhoneStateMachine agar tidak ter-couple ke TextView langsung")
        immobilizerController = ImmobilizerController(this@ImmobilizerService).apply {
            adapter.enable()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (!isStarted) {
            startNotification()
            isStarted = true
        }
        return START_NOT_STICKY
    }

    inner class ImmobilizerBinder : Binder() {
        fun getService(): ImmobilizerService = this@ImmobilizerService
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        if (!isStarted) {
            startNotification()
            isStarted = true
            immobilizerController.userPromptLD.observe(
                this@ImmobilizerService,
                Observer {
                    showPrompt(it)
                }
            )
        }
        return imBinder
    }

    private fun showPrompt(prompt: UserPrompt?) {
        if (prompt == null || !prompt.showPrompt)
            return
        when (prompt.promptType) {
            ImmobilizerIOEvent.MESSAGE_PROMPT_PIN.code -> {
                activatePinScreen(prompt.promptMessage)
            }
            ImmobilizerIOEvent.MESSAGE_PROMPT_RENAME.code -> {
                activateRenameScreen(prompt.promptMessage)
            }
        }
    }

    override fun onDestroy() {
        immobilizerController.stopBtClient()
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
     * Refresh the LiveData of list of registered immobilizers
     */
    fun getRegisteredImmobilizers() {
//        launch {
//            val list =
//                ImmobilizerRepository.getImmobilizerList(this@ImmobilizerService)
//            immobilizerListLD.postValue(list)
//        }
    }

    /**
     * Show the device naming prompt
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