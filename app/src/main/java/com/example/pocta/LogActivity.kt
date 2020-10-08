package com.example.pocta

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.pocta.databinding.ActivityLogBinding

class LogActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLogBinding
    private lateinit var receiver: BroadcastReceiver
    private val tag = "ConnectActivity"
    private var chatMessage: String = ""

    companion object {
        const val lt = "ConnectActivity"
    }

    @SuppressLint("HardwareIds")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_log)
        binding.apply {
            logActivityLogView.text = ImmobilizerService.immobilizerStatus
            logActivityLogView.movementMethod = ScrollingMovementMethod()
        }

        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                binding.logActivityLogView.text = ImmobilizerService.immobilizerData
            }
        }
    }

    override fun onStart() {
        super.onStart()
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(
                receiver,
                IntentFilter(IMMOBILIZER_SERVICE_LOG)
            )
    }

    override fun onResume() {
        super.onResume()
        binding.logActivityLogView.text = ImmobilizerService.immobilizerData
    }

    override fun onStop() {
        LocalBroadcastManager.getInstance(this)
            .unregisterReceiver(receiver)
        super.onStop()
    }
}
