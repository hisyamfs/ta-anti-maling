package com.example.pocta

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pocta.databinding.ActivityHubBinding
import org.jetbrains.anko.db.classParser
import org.jetbrains.anko.db.select

class HubActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHubBinding
    private var btAdapter: BluetoothAdapter? = null
    private var immobilizerAdapter: ImmobilizerAdapter? = null
    private var list: List<Immobilizer> = emptyList()
    private val REQUEST_ENABLE_BT = 1

    companion object {
        const val EXTRA_ADDRESS: String = "com.example.pocta.hub.EXTRA_ADDRESS"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_hub)
        // refresh list
        getImmobilizerList()
        val mLayoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        immobilizerAdapter = ImmobilizerAdapter(this, list)

        binding.apply {
            pairedDevicesList.apply {
                layoutManager = mLayoutManager
                adapter = immobilizerAdapter
            }
            enableBtButton.setOnClickListener { enableBluetooth() }
            refreshListButton.setOnClickListener { listPairedDevices() }
            addImmobilizerButton.setOnClickListener { startRegisterActivity() }
        }
    }

    private fun startRegisterActivity() {
        val intent = Intent(this, RegisterActivity::class.java)
        startActivity(intent)
    }

    private fun enableBluetooth() {
        if (!btAdapter!!.isEnabled) {
            val turnOnBt = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(turnOnBt, REQUEST_ENABLE_BT)
//            Toast.makeText(this, "Bluetooth On", Toast.LENGTH_SHORT).show()
        }
        else {
            Toast.makeText(this, "Bluetooth Already On", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_ENABLE_BT -> {
                if (resultCode == Activity.RESULT_OK) {
                    Toast.makeText(this,
                        "Bluetooth On", Toast.LENGTH_SHORT).show()
                }
                else {
                    Toast.makeText(this,
                        "Can't turn on Bluetooth", Toast.LENGTH_SHORT).show()
                }
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun getImmobilizerList() {
        database.use {
            val result = select(Immobilizer.TABLE_IMMOBILIZER)
            list = result.parseList(classParser())
        }
    }

    private fun listPairedDevices() {
        getImmobilizerList()
        if (list.isNotEmpty()) {
            immobilizerAdapter = ImmobilizerAdapter(this, list)
            immobilizerAdapter?.notifyDataSetChanged()
            binding.pairedDevicesList.adapter = immobilizerAdapter
        } else {
            Toast.makeText(
                this,
                "Tidak ada device immobilizer yang terdaftar!",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

}
