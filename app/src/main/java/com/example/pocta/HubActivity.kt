package com.example.pocta

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pocta.databinding.ActivityHubBinding
import com.getkeepsafe.taptargetview.TapTarget
import com.getkeepsafe.taptargetview.TapTargetSequence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlin.coroutines.CoroutineContext


class HubActivity : AppCompatActivity(), CoroutineScope {
    private lateinit var binding: ActivityHubBinding
    private var immobilizerAdapter: ImmobilizerAdapter? = null
    private var uiStatus: String = ""
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
        ImmobilizerService.immobilizerController.apply {
            activeImmobilizerLD.observe(
                this@HubActivity,
                Observer {
                    uiStatus = "${it.name}\n${it.status}"
                    binding.hubActivityStatusView.text = uiStatus
                }
            )
            immobilizerListLD.observe(
                this@HubActivity,
                Observer {
                    list = it
                    updateImmobilizerCards()
                }
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        job = Job()
        binding = DataBindingUtil.setContentView(this, R.layout.activity_hub)
        setSupportActionBar(binding.hubActivityToolbar)
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
            hubActivityDisconnectButton.setOnClickListener {
                ImmobilizerService.immobilizerController.stopBtClient()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.hub_menu, menu)
        return true
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_delete_all -> {
                deleteAllImmobilizers()
            }
            R.id.action_register_immobilizer -> {
                startRegisterActivity()
            }
            R.id.action_view_log -> {
                startLogActivity()
            }
            R.id.action_refresh -> {
                imsInstance.getRegisteredImmobilizers()
            }
            R.id.action_enable_bluetooth -> {
                enableBluetooth()
            }
            R.id.action_tutorial -> {
                showTutorial()
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onStart() {
        super.onStart()
        Log.i(TAG, "onStart called")
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
        if (!ImmobilizerService.immobilizerController.adapter.isEnabled) {
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

    private fun deleteAllImmobilizers() {
        ImmobilizerService.immobilizerController.deleteAllImmobilizer()
    }

    private fun showTutorial() {
//        TapTargetView.showFor(
//            this,
//            TapTarget.forToolbarMenuItem(
//                binding.hubActivityToolbar,
//                R.id.action_register_immobilizer,
//                "Tambah Perangkat",
//                "Sentuh untuk mendaftarkan HP anda ke perangkat baru"
//            )
//                .textColor(R.color.white)
//                .cancelable(true)
//                .tintTarget(false),
//                object : TapTargetView.Listener() {
//                    override fun onTargetClick(view: TapTargetView) {
//                        super.onTargetClick(view)
//                        view.dismiss(true)
//                    }
//                }
//        )

        var taptargets = mutableListOf<TapTarget>(
            TapTarget.forToolbarMenuItem(
                binding.hubActivityToolbar,
                R.id.action_register_immobilizer,
                "Tambah Perangkat",
                "Sentuh untuk mendaftarkan HP anda ke perangkat baru"
            )
                .textColor(R.color.white)
                .cancelable(true)
                .tintTarget(false),
            TapTarget.forView(
                binding.hubActivityStatusView,
                "Status Perangkat Terhubung",
                "Menunjukkan perangkat mana yang terhubung ke HP anda dan apakah perangkat terkunci atau tidak"
            )
                .textColor(R.color.white)
                .cancelable(true)
                .tintTarget(false),
            TapTarget.forView(
                binding.hubActivityDisconnectButton,
                "Tombol Disconnect ke Perangkat",
                "Memutuskan sambungan antara perangkat dan HP anda"
            )
                .textColor(R.color.white)
                .cancelable(true)
                .tintTarget(false)
        )

        if (list.isNotEmpty()) {
            val immoTapTarget = getFirstRVItem(binding.pairedDevicesList)
            immoTapTarget?.let {
                val unlockTapTarget =
                    binding.pairedDevicesList.getChildViewHolder(immoTapTarget) as ImmobilizerAdapter.ViewHolder
                taptargets.add(
                    TapTarget.forView(
                        unlockTapTarget.unlockView,
                        "Tombol Unlock",
                        "Tekan untuk mengunci atau membuka perangkat"
                    )
                        .textColor(R.color.white)
                        .cancelable(true)
                        .tintTarget(false)
                )
                taptargets.add(
                    TapTarget.forView(
                        unlockTapTarget.changePinView,
                        "Tombol Unlock",
                        "Tekan untuk mengganti PIN pada perangkat"
                    )
                        .textColor(R.color.white)
                        .cancelable(true)
                        .tintTarget(false)
                )
                taptargets.add(
                    TapTarget.forView(
                        unlockTapTarget.deleteView,
                        "Tombol Hapus Akun",
                        "Tekan untuk menghapus HP anda dari perangkat"
                    )
                        .textColor(R.color.white)
                        .cancelable(true)
                        .tintTarget(false)
                )
                taptargets.add(
                    TapTarget.forView(
                        unlockTapTarget.renameView,
                        "Tombol Rename",
                        "Tekan untuk mengganti nama perangkat"
                    )
                        .textColor(R.color.white)
                        .cancelable(true)
                        .tintTarget(false)
                )
            }
        }

        TapTargetSequence(this)
            .targets(taptargets)
            .listener(
                object : TapTargetSequence.Listener {
                    override fun onSequenceStep(lastTarget: TapTarget?, targetClicked: Boolean) {
                    }

                    override fun onSequenceFinish() {
                    }

                    override fun onSequenceCanceled(lastTarget: TapTarget?) {
                    }
                }
            )
            .start()
    }

    private fun getFirstRVItem(rv: RecyclerView): View? {
        val layoutManager = rv.layoutManager
        return if (layoutManager is LinearLayoutManager) {
            val i = layoutManager.findFirstCompletelyVisibleItemPosition()
            layoutManager.findViewByPosition(i)
        } else null
    }
}
