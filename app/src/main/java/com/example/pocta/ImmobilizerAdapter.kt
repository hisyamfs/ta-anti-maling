package com.example.pocta

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
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
                    ImmobilizerService.immobilizerController.setActiveConnection(
                        immobilizer, ImmobilizerUserRequest.UNLOCK
                    )
                }
                itemChangeUserPinButton.setOnClickListener {
                    ImmobilizerService.immobilizerController.setActiveConnection(
                        immobilizer, ImmobilizerUserRequest.CHANGE_PIN
                    )
                }
                itemRemovePhoneButton.setOnClickListener {
                    ImmobilizerService.immobilizerController.setActiveConnection(
                        immobilizer, ImmobilizerUserRequest.REMOVE_PHONE
                    )
                }
                itemRenameDeviceButton.setOnClickListener {
                    renameImmobilizer(immobilizer)
                }
            }
        }

        private fun renameImmobilizer(immobilizer: Immobilizer) {
            val intent = Intent(mContext, RenameActivity::class.java).apply {
                putExtra(IMMOBILIZER_SERVICE_ADDRESS, immobilizer.address)
            }
            mContext.startActivity(intent)
        }
    }
}