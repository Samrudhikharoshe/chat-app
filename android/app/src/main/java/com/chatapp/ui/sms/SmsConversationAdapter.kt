package com.chatapp.ui.sms

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.chatapp.R
import com.chatapp.data.Message
import com.chatapp.data.MessageCache

class SmsConversationAdapter(
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<SmsConversationAdapter.VH>() {

    private val items = mutableListOf<String>()

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val avatar: TextView = view.findViewById(R.id.avatarText)
        val number: TextView = view.findViewById(R.id.number)
        val preview: TextView = view.findViewById(R.id.preview)
    }

    fun submit(numbers: List<String>) {
        items.clear()
        items.addAll(numbers)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sms_conversation, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val number = items[position]
        holder.number.text = number
        holder.avatar.text = number.lastOrNull()?.uppercase() ?: "?"
        holder.preview.text = previewText(number)
        holder.itemView.setOnClickListener { onClick(number) }
    }

    private fun previewText(number: String): String {
        val last = MessageCache.current.historyFor(number).lastOrNull() ?: return ""
        return when (last.type) {
            "text" -> last.content ?: ""
            "image" -> "Photo"
            "video" -> "Video"
            "voice" -> "Voice note"
            else -> last.content ?: ""
        }
    }
}
