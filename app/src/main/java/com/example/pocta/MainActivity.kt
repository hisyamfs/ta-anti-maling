package com.example.pocta

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import com.example.pocta.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var userPin: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        userPin = getSharedPreferences("PREFS", 0)
            .getString("PIN", "1998")
            ?: "1998"
        binding.inputPinButton.setOnClickListener{
            processPinInput()
        }
    }

    private fun processPinInput() {
        val inputPin = binding.inputPinField.text.toString()
        // check if user's input is the correct PIN
        userPin = getSharedPreferences("PREFS", 0)
            .getString("PIN", "1998")
            ?: "1998"
        if (inputPin == userPin) {
            // continue to next activity
            Toast.makeText(this, "PIN benar", Toast.LENGTH_SHORT).show()
            val toHub = Intent(this, HubActivity::class.java)
            startActivity(toHub)
        }
        else {
            Toast.makeText(this, "PIN yang anda masukkan salah! Seharusnya $userPin.", Toast.LENGTH_LONG).show()
        }
    }
}
