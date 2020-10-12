package com.example.pocta

import android.content.Context
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.example.pocta.databinding.ActivityPinBinding

class PinActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPinBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_pin)
        /**
         * EditText editText = (EditText) findViewById(R.id.myTextViewId);
         * editText.requestFocus();
         * InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
         * imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
         */
        binding.apply {
            pinScreenSendButton.setOnClickListener { sendPin() }
            pinScreenCancelButton.setOnClickListener { cancelPin() }
            pinScreenInputField.requestFocus()
        }
        val inputMethodManager =
            getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.toggleSoftInput(
            InputMethodManager.SHOW_FORCED,
            InputMethodManager.HIDE_IMPLICIT_ONLY
        )
    }

    private fun cancelPin() {
        ImmobilizerService.immobilizerController.disconnect()
        val inputMethodManager =
            getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(
            binding.pinScreenInputField.windowToken, 0
        )
        finish()
    }

    private fun sendPin() {
        val pin = binding.pinScreenInputField.text.toString()
        ImmobilizerService.immobilizerController.onUserInput(pin.toByteArray())
        val inputMethodManager =
            getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(
            binding.pinScreenInputField.windowToken, 0
        )
        finish()
    }
}
