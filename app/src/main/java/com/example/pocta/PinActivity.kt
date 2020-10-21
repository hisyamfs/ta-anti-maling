package com.example.pocta

import android.content.Context
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.example.pocta.databinding.ActivityPinBinding

class PinActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPinBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val hint = intent.getStringExtra(IMMOBILIZER_SERVICE_PROMPT_MESSAGE) ?: "Masukkan PIN Anda!"
        binding = DataBindingUtil.setContentView(this, R.layout.activity_pin)
        binding.apply {
            pinScreenHeader.text = hint
            pinScreenSendButton.setOnClickListener { sendPin() }
            pinScreenCancelButton.setOnClickListener { cancelPin() }
            pinScreenInputField.requestFocus()
            pinScreenInputField.setOnEditorActionListener { _, actionId, _ ->
                return@setOnEditorActionListener when (actionId) {
                    EditorInfo.IME_ACTION_DONE -> {
                        sendPin()
                        true
                    }
                    else -> false
                }
            }
        }

        val inputMethodManager =
            getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.toggleSoftInput(
            InputMethodManager.SHOW_FORCED,
            InputMethodManager.HIDE_IMPLICIT_ONLY
        )
    }

    override fun onStop() {
        val inputMethodManager =
            getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(
            binding.pinScreenInputField.windowToken, 0
        )
        super.onStop()
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
