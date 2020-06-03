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
import com.example.pocta.databinding.ActivityHubBinding

class HubActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHubBinding
    private var btAdapter: BluetoothAdapter? = null
    private lateinit var btDevices: Set<BluetoothDevice>
    private val REQUEST_ENABLE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_hub)
        // check if bt is enabled
        btAdapter = BluetoothAdapter.getDefaultAdapter()
        if (btAdapter == null) {
            binding.apply {
                enableBtButton.isEnabled = false
                refreshListButton.isEnabled = false
                hubHeaderText.text = getString(R.string.btAdapter_null_message)
            }
        }
        else {
            // Set the listeners
            binding.apply {
                enableBtButton.setOnClickListener { it: View -> enableBluetooth(it) }
                refreshListButton.setOnClickListener { it: View -> listPairedDevices(it) }
            }
        }
        binding.changePinButton.setOnClickListener { it: View ->
            changePin(it)
        }
    }

    private fun enableBluetooth(it: View?) {
        if (!btAdapter!!.isEnabled) {
            val turnOnBt = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(turnOnBt, REQUEST_ENABLE)
            Toast.makeText(this, "Bluetooth On", Toast.LENGTH_SHORT).show()
        }
        else {
            Toast.makeText(this, "Bluetooth Already On", Toast.LENGTH_SHORT).show()
        }
    }

    private fun listPairedDevices(it: View?) {
        if (btAdapter!!.isEnabled) {
            btDevices = btAdapter!!.bondedDevices
            val list = ArrayList<String>() // empty list
            if (btDevices.isNotEmpty()) {
                for (device in btDevices) {
                    list.add(device.name)
                }
                val numDevices: Int = list.size
                val listAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, list)
                val scoreDevices: String = "Found $numDevices paired devices"
                binding.apply {
                    hubHeaderText.text = scoreDevices
                    pairedDevicesList.adapter = listAdapter
                    pairedDevicesList.visibility = View.VISIBLE
                }
            } else {
                binding.apply {
                    hubHeaderText.text = getString(R.string.hub_no_paired_device)
                    pairedDevicesList.visibility = View.GONE
                }
            }
        } else {
            Toast.makeText(this, "Please turn on Bluetooth first", Toast.LENGTH_LONG).show()
        }
    }

    private fun changePin(it: View?) {
        Toast.makeText(this, "Not implemented yet ;)", Toast.LENGTH_SHORT).show()
    }
}
