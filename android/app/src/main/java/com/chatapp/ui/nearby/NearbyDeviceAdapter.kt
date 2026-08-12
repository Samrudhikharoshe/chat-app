package com.chatapp.ui.nearby

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.chatapp.R
import com.chatapp.data.NearbyMessenger

class NearbyDeviceAdapter(
    private val onClick: (NearbyMessenger.NearbyDevice) -> Unit
) : RecyclerView.Adapter<NearbyDeviceAdapter.VH>() {

    private val items = mutableListOf<NearbyMessenger.NearbyDevice>()

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val avatar: TextView = view.findViewById(R.id.avatarText)
        val name: TextView = view.findViewById(R.id.deviceName)
        val status: TextView = view.findViewById(R.id.deviceStatus)
    }

    fun submit(list: List<NearbyMessenger.NearbyDevice>) {
        items.clear()
        items.addAll(list.distinctBy { it.endpointId })
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_nearby_device, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val device = items[position]
        holder.name.text = device.name
        holder.avatar.text = device.name.firstOrNull()?.uppercase() ?: "?"
        holder.status.text = holder.itemView.context.getString(
            if (device.connected) R.string.nearby_connected else R.string.nearby_connecting
        )
        holder.status.setTextColor(
            ContextCompat.getColor(
                holder.itemView.context,
                if (device.connected) R.color.primary else R.color.gray_text
            )
        )
        holder.itemView.isEnabled = device.connected
        holder.itemView.setOnClickListener {
            if (device.connected) onClick(device)
        }
    }
}
