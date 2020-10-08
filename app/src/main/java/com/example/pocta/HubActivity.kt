package com.example.pocta

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pocta.databinding.ActivityHubBinding
import kotlinx.coroutines.*
import org.jetbrains.anko.db.select
import kotlin.coroutines.CoroutineContext

class HubActivity : AppCompatActivity(), CoroutineScope {
    private lateinit var binding: ActivityHubBinding
    private lateinit var receiver: BroadcastReceiver
    private var immobilizerAdapter: ImmobilizerAdapter? = null
    private var list: List<Immobilizer> = emptyList()
    private val REQUEST_ENABLE_BT = 1

    companion object {
        const val EXTRA_ADDRESS: String = "com.example.pocta.hub.EXTRA_ADDRESS"
        const val TAG = "HubActivity"
    }

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job
    private lateinit var job: Job

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

        binding.apply {
            pairedDevicesList.apply {
                layoutManager = mLayoutManager
                adapter = immobilizerAdapter
            }
            enableBtButton.setOnClickListener { enableBluetooth() }
            refreshListButton.setOnClickListener {
                launch {
                    listPairedDevices()
                }
            }
            addImmobilizerButton.setOnClickListener { startRegisterActivity() }
            hubActivityViewLogButton.setOnClickListener { startLogActivity() }
            hubActivityStatusView.text = ImmobilizerService.immobilizerStatus
        }

        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                binding.hubActivityStatusView.text = ImmobilizerService.immobilizerStatus
            }
        }
    }

    override fun onStart() {
        super.onStart()
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(
                receiver,
                IntentFilter(IMMOBILIZER_SERVICE_STATUS)
            )
    }

    override fun onResume() {
        super.onResume()
        binding.hubActivityStatusView.text = ImmobilizerService.immobilizerStatus
        launch {
            listPairedDevices()
        }
    }

    override fun onStop() {
        LocalBroadcastManager.getInstance(this)
            .unregisterReceiver(receiver)
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
        ImmobilizerService.stopService(this)
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

    private suspend fun getImmobilizerList(): List<Immobilizer> {
        return withContext(Dispatchers.IO) {
            var rList: List<Immobilizer> = emptyList()
            try {
                database.use {
                    val result = select(Immobilizer.TABLE_IMMOBILIZER)
                    rList = result.parseList(immobilizerParser)
                }
            } catch (e: Exception) {
                Log.e(TAG, "getImmobilizerList ERROR:", e)
            }
            rList
        }
    }

    private suspend fun listPairedDevices() {
        list = getImmobilizerList()
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
