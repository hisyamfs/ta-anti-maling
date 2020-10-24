package com.example.pocta

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pocta.databinding.ActivityHubBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlin.coroutines.CoroutineContext

class HubActivity : AppCompatActivity(), CoroutineScope {
    private lateinit var binding: ActivityHubBinding
    private lateinit var receiver: BroadcastReceiver
    private var immobilizerAdapter: ImmobilizerAdapter? = null
    private var list: List<Immobilizer> = emptyList()
    private val REQUEST_ENABLE_BT = 1
    private lateinit var imsInstance: ImmobilizerService
    private var isIMSBound = false

    companion object {
        const val EXTRA_ADDRESS: String = "com.example.pocta.hub.EXTRA_ADDRESS"
        const val TAG = "HubActivity"
    }

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job
    private lateinit var job: Job

    private val imsConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder =
                service as ImmobilizerService.ImmobilizerBinder
            imsInstance = binder.getService()
            isIMSBound = true
            attachImmobilizerObserver()
            Log.i(TAG, "Immobilizer Service Bound")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isIMSBound = false
            Log.i(TAG, "Immobilizer Service Unbound")
        }
    }

    private fun attachImmobilizerObserver() {
        imsInstance.immobilizerStatusLD.observe(
            this,
            Observer {
                binding.hubActivityStatusView.text = it
            }
        )
        imsInstance.immobilizerListLD.observe(
            this,
            Observer {
                list = it
                updateImmobilizerCards()
            }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        job = Job()
        binding = DataBindingUtil.setContentView(this, R.layout.activity_hub)
        ImmobilizerService.startService(this)
        val mLayoutManager = LinearLayoutManager(
            this,
            LinearLayoutManager.VERTICAL, false
        )
        immobilizerAdapter = ImmobilizerAdapter(this, list)
        ImmobilizerService.bindService(this, imsConnection)

        binding.apply {
            pairedDevicesList.apply {
                layoutManager = mLayoutManager
                adapter = immobilizerAdapter
            }
            enableBtButton.setOnClickListener { enableBluetooth() }
            refreshListButton.setOnClickListener {
                imsInstance.getRegisteredImmobilizers()
            }
            addImmobilizerButton.setOnClickListener { startRegisterActivity() }
            hubActivityViewLogButton.setOnClickListener { startLogActivity() }
            hubActivityStatusView.text = ImmobilizerService.immobilizerStatus
        }
    }

    override fun onStart() {
        super.onStart()
        Log.i(TAG, "onStart called")
        binding.hubActivityStatusView.text = ImmobilizerService.immobilizerStatus
//        imsInstance.getRegisteredImmobilizers()
//        launch {
//            listPairedDevices()
//        }
    }

    override fun onResume() {
        super.onResume()
        Log.i(TAG, "onResume called")
//        binding.hubActivityStatusView.text = ImmobilizerService.immobilizerStatus
//        launch {
//            listPairedDevices()
//        }
    }

//    override fun onStop() {
////        LocalBroadcastManager.getInstance(this)
////            .unregisterReceiver(receiver)
//        super.onStop()
//    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy called")
        job.cancel()
        unbindService(imsConnection)
        ImmobilizerService.stopService(this)
        super.onDestroy()
    }

    private fun startLogActivity() {
        val intent = Intent(this, LogActivity::class.java)
        startActivity(intent)
    }

    private fun startRegisterActivity() {
        val intent = Intent(this, RegisterActivity::class.java)
        startActivity(intent)
    }

    private fun enableBluetooth() {
        if (!ImmobilizerService.btAdapter.isEnabled) {
            val turnOnBt = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(turnOnBt, REQUEST_ENABLE_BT)
        } else {
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

    private fun listPairedDevices() {
//        list = getImmobilizerList(this)
//        immobilizerAdapter = ImmobilizerAdapter(this, list)
//        immobilizerAdapter?.notifyDataSetChanged()
//        binding.pairedDevicesList.adapter = immobilizerAdapter
//        if (list.isEmpty()) {
//            Toast.makeText(
//                this,
//                "Tidak ada device immobilizer yang terdaftar!",
//                Toast.LENGTH_SHORT
//            ).show()
//        }
        imsInstance.getRegisteredImmobilizers()
    }

    private fun updateImmobilizerCards() {
        immobilizerAdapter = ImmobilizerAdapter(this, list)
        immobilizerAdapter?.notifyDataSetChanged()
        binding.pairedDevicesList.adapter = immobilizerAdapter
        if (list.isEmpty()) {
            Toast.makeText(
                this,
                "Tidak ada device immobilizer yang terdaftar!",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
