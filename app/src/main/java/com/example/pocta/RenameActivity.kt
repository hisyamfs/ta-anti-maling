package com.example.pocta

import android.content.Context
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.example.pocta.databinding.ActivityRenameBinding

class RenameActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRenameBinding
    private lateinit var address: String
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_rename)
        val immobilizerAddress = intent.getStringExtra(IMMOBILIZER_SERVICE_ADDRESS) ?: "0"
        binding.apply {
            renameScreenRenameButton.setOnClickListener { renameImmobilizer(immobilizerAddress) }
            renameScreenCancelButton.setOnClickListener { cancelPin() }
            renameScreenRenameField.setOnEditorActionListener { _, actionId, _ ->
                return@setOnEditorActionListener when (actionId) {
                    EditorInfo.IME_ACTION_DONE -> {
                        renameImmobilizer(immobilizerAddress)
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
            binding.renameScreenRenameField.windowToken, 0
        )
        super.onStop()
    }

    private fun cancelPin() {
        val inputMethodManager =
            getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(
            binding.renameScreenRenameField.windowToken, 0
        )
        finish()
    }

    private fun renameImmobilizer(immobilizerAddress: String) {
        val userInput = binding.renameScreenRenameField.text.toString()
        ImmobilizerService.renameImmobilizer(immobilizerAddress, userInput)
        val inputMethodManager =
            getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(
            binding.renameScreenRenameField.windowToken, 0
        )
        finish()
    }
}
