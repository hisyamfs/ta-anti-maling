package com.example.pocta

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.databinding.DataBindingUtil
import com.example.pocta.databinding.ActivityConnectBinding

class ConnectActivity : AppCompatActivity() {
    private lateinit var binding: ActivityConnectBinding

    companion object {
        lateinit var myAddress : String
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_connect)
        binding.apply {
            connectButton.setOnClickListener { connectToDevice() }
            sendMessageButton.setOnClickListener { sendMessage() }
            disconnectButton.setOnClickListener { disconnectFromDevice() }
        }
    }

    private fun disconnectFromDevice() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    private fun sendMessage() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    private fun connectToDevice() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}
