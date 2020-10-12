package com.example.pocta

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.text.method.ScrollingMovementMethod
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import com.example.pocta.databinding.ActivityLogBinding

class LogActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLogBinding
//    private lateinit var receiver: BroadcastReceiver
    private lateinit var imsInstance: ImmobilizerService
    private var isIMSBound = false
    companion object {
        const val TAG = "LogActivity"
    }

    private val imsConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder =
                service as ImmobilizerService.ImmobilizerBinder
            imsInstance = binder.getService()
            isIMSBound = true
            attachImmobilizerObserver()
            Log.i(TAG, "Immobilizer Service Bound")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isIMSBound = false
            Log.i(TAG, "Immobilizer Service Unbound")
        }
    }

    private fun attachImmobilizerObserver() {
        imsInstance.immobilizerDataLD.observe(
            this,
            Observer {
                binding.logActivityLogView.text = it
            }
        )
    }

    @SuppressLint("HardwareIds")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_log)
        binding.apply {
            logActivityLogView.text = ImmobilizerService.immobilizerData
            logActivityLogView.movementMethod = ScrollingMovementMethod()
        }
        ImmobilizerService.bindService(this, imsConnection)
//        receiver = object : BroadcastReceiver() {
//            override fun onReceive(context: Context?, intent: Intent?) {
//                binding.logActivityLogView.text = ImmobilizerService.immobilizerData
//            }
//        }
    }

//    override fun onStart() {
//        super.onStart()
////        LocalBroadcastManager.getInstance(this)
////            .registerReceiver(
////                receiver,
////                IntentFilter(IMMOBILIZER_SERVICE_LOG)
////            )
//    }

    override fun onResume() {
        super.onResume()
        binding.logActivityLogView.text = ImmobilizerService.immobilizerData
    }

    override fun onStop() {
        unbindService(imsConnection)
        super.onStop()
    }
}
