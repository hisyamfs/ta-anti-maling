package com.example.pocta

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.pocta.HubActivity.Companion.EXTRA_ADDRESS
import com.example.pocta.databinding.ItemImmobilizerBinding

class ImmobilizerAdapter(context: Context, private val list: List<Immobilizer>) :
    RecyclerView.Adapter<ImmobilizerAdapter.ViewHolder>() {
    private var mContext: Context = context

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemImmobilizerBinding.inflate(inflater, parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int {
        return list.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(list[position])
    }

    inner class ViewHolder(private val binding: ItemImmobilizerBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(immobilizer: Immobilizer) {
            binding.apply {
                itemImmoName.text = immobilizer.name
                itemImmoAddress.text = immobilizer.address
                itemUnlockDeviceButton.setOnClickListener {
                    ImmobilizerService.sendRequest(USER_REQUEST.UNLOCK, immobilizer.address)
                    startConnectActivity(immobilizer.address)
                }
                itemChangeUserPinButton.setOnClickListener {
                    ImmobilizerService.sendRequest(USER_REQUEST.CHANGE_PIN, immobilizer.address)
                    startConnectActivity(immobilizer.address)
                }
                itemRemovePhoneButton.setOnClickListener {
                    ImmobilizerService.sendRequest(USER_REQUEST.REMOVE_PHONE, immobilizer.address)
                    startConnectActivity(immobilizer.address)
                }
                itemToggleConnectionButton.setOnClickListener {
                    ImmobilizerService.toggleConnection(immobilizer.address)
                    startConnectActivity(immobilizer.address)
                }
                itemRenameDeviceButton.setOnClickListener {
                    renameImmobilizer(immobilizer)
                }
            }
        }

        private fun startConnectActivity(address: String) {
            val startConnect: Intent = Intent(mContext, ConnectActivity::class.java).apply {
                putExtra(EXTRA_ADDRESS, address)
            }
            mContext.startActivity(startConnect)
        }

        private fun renameImmobilizer(immobilizer: Immobilizer) {
//        TODO("Buat UI khusus untuk rename perangkat")
            startConnectActivity(immobilizer.address)
        }
    }
}