package com.chatapp.ui.chat

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.chatapp.R
import com.chatapp.data.Message
import com.chatapp.data.Session
import com.chatapp.databinding.ItemMessageImageInBinding
import com.chatapp.databinding.ItemMessageImageOutBinding
import com.chatapp.databinding.ItemMessageTextInBinding
import com.chatapp.databinding.ItemMessageTextOutBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessagesAdapter(
    private val context: Context,
    private val myId: String,
    private val peerId: String
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val messages = mutableListOf<Message>()

    companion object {
        private const val TYPE_TEXT_IN = 0
        private const val TYPE_TEXT_OUT = 1
        private const val TYPE_IMAGE_IN = 2
        private const val TYPE_IMAGE_OUT = 3
    }

    fun submit(list: List<Message>) {
        messages.clear()
        messages.addAll(list.sortedBy { it.createdAt })
        notifyDataSetChanged()
    }

    fun addMessage(message: Message) {
        messages.removeAll { it.id == message.id }
        messages.add(message)
        messages.sortBy { it.createdAt }
        notifyItemInserted(messages.size - 1)
    }

    fun upsert(message: Message) {
        val index = messages.indexOfFirst { it.id == message.id }
        if (index >= 0) {
            messages[index] = message
            notifyItemChanged(index)
        } else {
            addMessage(message)
        }
    }

    fun markRead(fromId: String) {
        var changed = false
        messages.forEachIndexed { index, msg ->
            if (msg.fromId == myId && msg.toId == fromId && !msg.read) {
                messages[index] = msg.copy(read = true)
                changed = true
                notifyItemChanged(index)
            }
        }
        if (changed) Unit
    }

    fun isEmpty(): Boolean = messages.isEmpty()

    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        val isMine = message.fromId == myId
        return when {
            isMine && message.type == "image" -> TYPE_IMAGE_OUT
            isMine -> TYPE_TEXT_OUT
            message.type == "image" -> TYPE_IMAGE_IN
            else -> TYPE_TEXT_IN
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_TEXT_OUT -> TextOutVH(
                ItemMessageTextOutBinding.inflate(inflater, parent, false)
            )
            TYPE_IMAGE_OUT -> ImageOutVH(
                ItemMessageImageOutBinding.inflate(inflater, parent, false)
            )
            TYPE_IMAGE_IN -> ImageInVH(
                ItemMessageImageInBinding.inflate(inflater, parent, false)
            )
            else -> TextInVH(
                ItemMessageTextInBinding.inflate(inflater, parent, false)
            )
        }
    }

    override fun getItemCount(): Int = messages.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        when (holder) {
            is TextOutVH -> holder.bind(message)
            is TextInVH -> holder.bind(message)
            is ImageOutVH -> holder.bind(message)
            is ImageInVH -> holder.bind(message)
        }
    }

    private fun timeLabel(message: Message): String {
        return try {
            val date = Date(java.time.OffsetDateTime.parse(message.createdAt).toInstant().toEpochMilli())
            SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
        } catch (e: Exception) {
            ""
        }
    }

    inner class TextOutVH(
        private val binding: ItemMessageTextOutBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: Message) {
            binding.messageText.text = message.content
            binding.timeLabel.text = timeLabel(message) + readState(message)
        }
    }

    inner class TextInVH(
        private val binding: ItemMessageTextInBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: Message) {
            binding.messageText.text = message.content
            binding.timeLabel.text = timeLabel(message)
        }
    }

    inner class ImageOutVH(
        private val binding: ItemMessageImageOutBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: Message) {
            message.mediaUrl?.let {
                binding.messageImage.load(it) {
                    placeholder(R.drawable.bg_image_placeholder)
                    error(R.drawable.bg_image_placeholder)
                }
            }
            binding.timeLabel.text = timeLabel(message) + readState(message)
            binding.messageImage.setOnClickListener {
                openImage(message.mediaUrl)
            }
        }
    }

    inner class ImageInVH(
        private val binding: ItemMessageImageInBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: Message) {
            message.mediaUrl?.let {
                binding.messageImage.load(it) {
                    placeholder(R.drawable.bg_image_placeholder)
                    error(R.drawable.bg_image_placeholder)
                }
            }
            binding.timeLabel.text = timeLabel(message)
            binding.messageImage.setOnClickListener {
                openImage(message.mediaUrl)
            }
        }
    }

    private fun readState(message: Message): String {
        return if (message.read) "  ✓✓" else "  ✓"
    }

    private fun openImage(url: String?) {
        url ?: return
        val intent = android.content.Intent(
            android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse(url)
        )
        context.startActivity(intent)
    }
}
