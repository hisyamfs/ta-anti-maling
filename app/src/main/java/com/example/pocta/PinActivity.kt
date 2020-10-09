package com.example.pocta

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.example.pocta.databinding.ActivityPinBinding

class PinActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPinBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_pin)
        binding.apply {
            pinScreenSendButton.setOnClickListener { sendPin() }
            pinScreenCancelButton.setOnClickListener { cancelPin() }
        }
    }

    private fun cancelPin() {
        ImmobilizerService.immobilizerController.disconnect()
        finish()
    }

    private fun sendPin() {
        val pin = binding.pinScreenInputField.text.toString()
        ImmobilizerService.immobilizerController.onUserInput(pin.toByteArray())
        finish()
    }
}
