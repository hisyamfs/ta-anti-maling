package com.example.pocta

import android.content.Intent
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import com.example.pocta.databinding.ActivityChangePinBinding

class ChangePinActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChangePinBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_change_pin)
        binding.apply {
            confirmNewPinButton.setOnClickListener{ updateUserPin() }
        }
    }

    private fun updateUserPin() {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        val pwd1 = binding.newPinFieldUpper.text.toString()
        val pwd2 = binding.newPinFieldBottom.text.toString()

        if (pwd1.equals("") || pwd2.equals("")) {
            Toast.makeText(this, "Silakan isi PIN terlebih dahulu",
                Toast.LENGTH_SHORT).show()
        } else if (pwd1.equals(pwd2)) {
            val editor =
                getSharedPreferences("PREFS", 0)
                .edit()
                .putString("PIN", pwd1)
                .apply()

            Toast.makeText(this, "PIN berhasil diubah", Toast.LENGTH_LONG).show()

            val toActivity = Intent(this, MainActivity::class.java)
            startActivity(toActivity)
        } else {
            Toast.makeText(this, "Kedua PIN yang anda masukkan tidak sama, silakan coba lagi",
                Toast.LENGTH_SHORT).show()
        }
    }
}
