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

    companion object {
        const val EXTRA_ADDRESS: String = "com.example.pocta.hub.EXTRA_ADDRESS"
    }

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
                enableBtButton.setOnClickListener { enableBluetooth(it) }
                refreshListButton.setOnClickListener { listPairedDevices(it) }
            }
        }
        binding.changePinButton.setOnClickListener {
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
            val dict = HashMap<String, String>()
            if (btDevices.isNotEmpty()) {
                for (device in btDevices) {
                    list.add(device.name)
                    dict[device.name] = device.address
                }

                val numDevices: Int = list.size
                val listAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, list)
                val scoreDevices: String = "Found $numDevices paired devices"

                binding.apply {
                    hubHeaderText.text = scoreDevices
                    pairedDevicesList.adapter = listAdapter
                    pairedDevicesList.visibility = View.VISIBLE
                    pairedDevicesList.setOnItemClickListener { _, _, position, _ ->
                        val selectedAddress: String? = dict[list[position]]
                        if (selectedAddress != null) {
                            val startConnect =
                                Intent(this@HubActivity, ConnectActivity::class.java).apply {
                                    putExtra(EXTRA_ADDRESS, selectedAddress)
                                }
                            startActivity(startConnect)
                        } else {
                            Toast.makeText(this@HubActivity, "Null Address????", Toast.LENGTH_LONG).show()
                        }
                    }
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
        val toActivity = Intent(this, ChangePinActivity::class.java)
        startActivity(toActivity)
    }
}
