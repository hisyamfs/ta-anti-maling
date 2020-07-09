package com.example.pocta

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import com.example.pocta.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var userPin: String = "1998"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        // Attach listeners
        binding.inputPinButton.setOnClickListener{it: View ->
            processPinInput(it)
        }
    }

    private fun processPinInput(it: View?) {
        val inputPin = binding.inputPinField.text.toString()
        // check if user's input is the correct PIN
        if (inputPin == userPin) {
            // continue to next activity
            Toast.makeText(this, "PIN benar", Toast.LENGTH_SHORT).show()
            val toHub = Intent(this, HubActivity::class.java)
            startActivity(toHub)
        }
        else {
            Toast.makeText(this, "PIN yang anda masukkan salah! Silahkan coba lagi.", Toast.LENGTH_SHORT).show()
        }
    }
}
