package com.example.pocta

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import com.example.pocta.HubActivity.Companion.EXTRA_ADDRESS
import com.example.pocta.databinding.ActivityRegisterBinding

class RegisterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterBinding
    private var btAdapter: BluetoothAdapter? = null
    private lateinit var btDevices: Set<BluetoothDevice>
    private val REQUEST_ENABLE_BT = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_register)
        btAdapter = BluetoothAdapter.getDefaultAdapter()
        binding.apply {
            registerEnableBtButton.setOnClickListener { enableBT() }
            registerRefreshListButton.setOnClickListener { refreshList() }
            if (btAdapter == null) {
                registerEnableBtButton.isEnabled = false
                registerRefreshListButton.isEnabled = false
            }
        }
    }

    private fun enableBT() {
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

    private fun refreshList() {
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
                    unregisteredDeviceList.adapter = listAdapter
                    unregisteredDeviceList.visibility = View.VISIBLE
                    unregisteredDeviceList.setOnItemClickListener { _, _, position, _ ->
                        val selectedAddress: String? = dict[list[position]]
                        if (selectedAddress != null) {
                            ImmobilizerService.sendRequest(USER_REQUEST.REGISTER_PHONE, selectedAddress)
                            val startConnect =
                                Intent(this@RegisterActivity, LogActivity::class.java).apply {
                                    putExtra(EXTRA_ADDRESS, selectedAddress)
                                }
                            startActivity(startConnect)
                        } else {
                            Toast.makeText(this@RegisterActivity, "Null Address????", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } else {
                binding.apply {
                    unregisteredDeviceList.visibility = View.GONE
                }
            }
        } else {
            Toast.makeText(this, "Please turn on Bluetooth first", Toast.LENGTH_LONG).show()
        }
    }
}
