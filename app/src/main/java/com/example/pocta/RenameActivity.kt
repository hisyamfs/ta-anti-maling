package com.example.pocta

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.example.pocta.databinding.ActivityRenameBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jetbrains.anko.db.update

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
            renameScreenRenameField.setOnEditorActionListener { _, actionId, event ->
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

    override fun onDestroy() {
        val inputMethodManager =
            getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(
            binding.renameScreenRenameField.windowToken, 0
        )
        super.onDestroy()
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
        GlobalScope.launch(Dispatchers.IO) {
            try {
                database.use {
                    update(
                        Immobilizer.TABLE_IMMOBILIZER, Immobilizer.NAME to userInput
                    )
                        .whereSimple("${Immobilizer.ADDRESS} = ?", immobilizerAddress)
                        .exec()
                }
            } catch (e: Exception) {
                Log.e("ConnectActivity", "renameDevice() ERROR:", e)
            }
        }
        val inputMethodManager =
            getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(
            binding.renameScreenRenameField.windowToken, 0
        )
        finish()
    }
}
