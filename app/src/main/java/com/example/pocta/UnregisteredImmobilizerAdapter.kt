package com.example.pocta

import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.pocta.databinding.ItemUnregisteredImmobilizerBinding

class UnregisteredImmobilizerAdapter(
    context: Context,
    private val list: List<UnregisteredImmobilizer>
) : RecyclerView.Adapter<UnregisteredImmobilizerAdapter.ViewHolder>() {
    private var mContext: Context = context
    private val mActivity = context as Activity

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding =
            ItemUnregisteredImmobilizerBinding.inflate(inflater, parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int {
        return list.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        holder.bind(item)
    }

    inner class ViewHolder(private val binding: ItemUnregisteredImmobilizerBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(unregisteredImmobilizer: UnregisteredImmobilizer) {
            binding.apply {
                itemUnregisteredImmoName.text = unregisteredImmobilizer.name
                itemUnregisteredImmoAddress.text = unregisteredImmobilizer.address
                itemRegisterDeviceButton.setOnClickListener {
                    ImmobilizerService.immobilizerController.setActiveConnection(
                        unregisteredImmobilizer.address,
                        unregisteredImmobilizer.name,
                        ImmobilizerUserRequest.REGISTER_PHONE
                    )
                    mActivity.finish()
                }
            }
        }
    }
}