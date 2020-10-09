package com.example.pocta

import android.os.Bundle
import android.util.Log
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
        }
    }

    private fun cancelPin() {
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
        finish()
    }
}
