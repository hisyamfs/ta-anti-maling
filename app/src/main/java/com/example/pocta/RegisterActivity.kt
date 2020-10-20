package com.example.pocta

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pocta.databinding.ActivityRegisterBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

class RegisterActivity : AppCompatActivity(), CoroutineScope {
    private lateinit var binding: ActivityRegisterBinding
    private var btAdapter: BluetoothAdapter? = null
    private var btDevices: MutableList<BluetoothDevice> = mutableListOf()
    private val REQUEST_ENABLE_BT = 1
    private var isBTOn = false
    private lateinit var registeredImmobilizers: List<Immobilizer>
    private lateinit var registeredAddresses: MutableSet<String>
    private lateinit var listAdapter: ArrayAdapter<BluetoothDevice>
    private lateinit var unregisteredAdapter: UnregisteredImmobilizerAdapter
    private var uImmobilizers: MutableList<UnregisteredImmobilizer> = mutableListOf()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job
    private lateinit var job: Job

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        job = Job()
        binding = DataBindingUtil.setContentView(this, R.layout.activity_register)
        btAdapter = BluetoothAdapter.getDefaultAdapter()
        listAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            emptyList()
        )
        val mLayoutManager = LinearLayoutManager(
            this,
            LinearLayoutManager.VERTICAL, false
        )

        unregisteredAdapter = UnregisteredImmobilizerAdapter(this, emptyList())

        binding.apply {
            registerEnableBtButton.setOnClickListener { enableBT() }
            registerRefreshListButton.setOnClickListener {
                launch {
                    refreshList()
                }
            }
            if (btAdapter == null) {
                registerEnableBtButton.isEnabled = false
                registerRefreshListButton.isEnabled = false

            }
            unregisteredDeviceRList.layoutManager = mLayoutManager
            unregisteredDeviceRList.adapter = unregisteredAdapter

//            unregisteredDeviceList.adapter = listAdapter
            unregisteredDeviceList.visibility = View.GONE
//            unregisteredDeviceList.setOnItemClickListener { _, _, position, _ ->
//                val selectedDevice =
//                    unregisteredDeviceList.getItemAtPosition(position) as BluetoothDevice
//                if (selectedDevice.address != null) {
//                    ImmobilizerService.sendRequest(
//                        USER_REQUEST.REGISTER_PHONE,
//                        selectedDevice.address
//                    )
//                } else {
//                    Toast.makeText(
//                        this@RegisterActivity, "Null Address????",
//                        Toast.LENGTH_LONG
//                    ).show()
//                }
//                finish()
//            }
        }
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(receiver, filter)
    }

    override fun onStart() {
        super.onStart()
        enableBT()
        if (isBTOn) {
            launch {
                refreshList()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }

    private fun enableBT() {
        if (!btAdapter!!.isEnabled) {
            val turnOnBt = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(turnOnBt, REQUEST_ENABLE_BT)
//            Toast.makeText(this, "Bluetooth On", Toast.LENGTH_SHORT).show()
        } else {
            isBTOn = true
            Toast.makeText(this, "Bluetooth Already On", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_ENABLE_BT -> {
                if (resultCode == Activity.RESULT_OK) {
                    Toast.makeText(
                        this,
                        "Bluetooth On", Toast.LENGTH_SHORT
                    ).show()
                    isBTOn = true
                    launch {
                        refreshList()
                    }
                } else {
                    Toast.makeText(
                        this,
                        "Can't turn on Bluetooth", Toast.LENGTH_SHORT
                    ).show()
                }
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private suspend fun refreshList() {
        if (btAdapter!!.isEnabled) {
            // get the list of registered immobilizers, and
            // store the addresses into a set
            Log.i("RegisterActivity", "Syncing to database")
            registeredAddresses = mutableSetOf()
            registeredImmobilizers = getImmobilizerList(this)
            registeredImmobilizers.forEach {
                // registeredAddresses.add(it.address)
                if (!registeredAddresses.contains(it.address))
                    registeredAddresses.add(it.address)
            }

            // do device discovery
            Log.i("RegisterActivity", "Starting Discovery")
            btAdapter!!.startDiscovery()
        } else {
            Toast.makeText(
                this,
                "Tolong hidupkan Bluetooth terlebih dahulu.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    // Discovery has found a device. Get the BluetoothDevice
                    // object and its info from the Intent.
                    Log.i("RegisterActivity", "Bluetooth device found.")
                    val device: BluetoothDevice? =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        Log.i("RegisterActivity", "Adding ${device.address}")
                        updateList(device)
                    }
                }
            }
        }
    }

    private fun updateList(device: BluetoothDevice) {
        val isImmobilizer: Boolean = device.name.startsWith(
            "ImmobilizerITB-",
            ignoreCase = false
        )
        // Check if the device is already registered with user's phone, by checking the address
        // in the registered immobilizer list
        if (isImmobilizer && !registeredAddresses.contains(device.address)) {
            // add to adapter
            uImmobilizers.add(UnregisteredImmobilizer(device))
//            val list: List<UnregisteredImmobilizer> = uImmobilizers.toList()
            unregisteredAdapter = UnregisteredImmobilizerAdapter(this, uImmobilizers)
            unregisteredAdapter.notifyDataSetChanged()
            binding.unregisteredDeviceRList.adapter = unregisteredAdapter

//            btDevices.add(device)
//            listAdapter = ArrayAdapter(
//                this,
//                android.R.layout.simple_list_item_1,
//                btDevices
//            )
//            binding.unregisteredDeviceList.adapter = listAdapter
        }
    }

    private fun registerImmobilizer(unregisteredImmobilizer: UnregisteredImmobilizer) {
        ImmobilizerService.sendRequest(
            USER_REQUEST.REGISTER_PHONE,
            unregisteredImmobilizer.address
        )
        finish()
    }
}
