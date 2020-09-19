package com.example.pocta

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pocta.databinding.ActivityHubBinding
import kotlinx.android.synthetic.main.activity_hub.*
import org.jetbrains.anko.db.classParser
import org.jetbrains.anko.db.select

class HubActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHubBinding
    private var btAdapter: BluetoothAdapter? = null
    private lateinit var btDevices: Set<BluetoothDevice>
    private var immobilizerAdapter: ImmobilizerAdapter? = null
    private var list: List<Immobilizer> = emptyList()
    private val REQUEST_ENABLE = 1

    companion object {
        const val EXTRA_ADDRESS: String = "com.example.pocta.hub.EXTRA_ADDRESS"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_hub)
        // check if bt is enabled
        btAdapter = BluetoothAdapter.getDefaultAdapter()
        // refresh list
        getImmobilizerList()
        val layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        immobilizerAdapter = ImmobilizerAdapter(this, list)

        binding.pairedDevicesList.layoutManager = layoutManager
        binding.pairedDevicesList.adapter = immobilizerAdapter

        if (btAdapter == null) {
            binding.apply {
                enableBtButton.isEnabled = false
                refreshListButton.isEnabled = false
            }
        }
        else {
            // Set the listeners
            binding.apply {
                enableBtButton.setOnClickListener { enableBluetooth() }
                refreshListButton.setOnClickListener { listPairedDevices() }
            }
        }
    }

    private fun enableBluetooth() {
        if (!btAdapter!!.isEnabled) {
            val turnOnBt = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(turnOnBt, REQUEST_ENABLE)
            Toast.makeText(this, "Bluetooth On", Toast.LENGTH_SHORT).show()
        }
        else {
            Toast.makeText(this, "Bluetooth Already On", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getImmobilizerList() {
        database.use {
            val result = select(Immobilizer.TABLE_IMMOBILIZER)
            list = result.parseList(classParser())
        }
    }
    private fun listPairedDevices() {
        if (btAdapter!!.isEnabled) {
            btDevices = btAdapter!!.bondedDevices
            getImmobilizerList()
            if (list.isNotEmpty()) {
                immobilizerAdapter = ImmobilizerAdapter(this, list)
                immobilizerAdapter?.notifyDataSetChanged()
                binding.pairedDevicesList.adapter = immobilizerAdapter
            } else {
                Toast.makeText(this,
                    "Tidak ada device immobilizer yang terdaftar!",
                    Toast.LENGTH_SHORT)
                    .show()
            }
        } else {
            Toast.makeText(this, "Please turn on Bluetooth first", Toast.LENGTH_LONG).show()
        }
    }
}
