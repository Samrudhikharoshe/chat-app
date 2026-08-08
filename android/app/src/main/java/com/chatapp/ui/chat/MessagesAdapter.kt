package com.chatapp.ui.chat

import android.content.Context
import android.media.MediaPlayer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import coil.decode.VideoFrameDecoder
import coil.request.videoFrameMillis
import coil.load
import com.chatapp.R
import com.chatapp.data.Message
import com.chatapp.databinding.ItemMessageImageInBinding
import com.chatapp.databinding.ItemMessageImageOutBinding
import com.chatapp.databinding.ItemMessageTextInBinding
import com.chatapp.databinding.ItemMessageTextOutBinding
import com.chatapp.databinding.ItemMessageVideoInBinding
import com.chatapp.databinding.ItemMessageVideoOutBinding
import com.chatapp.databinding.ItemMessageVoiceInBinding
import com.chatapp.databinding.ItemMessageVoiceOutBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessagesAdapter(
    private val context: Context,
    private val myId: String
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var onLongPress: ((Message) -> Unit)? = null
    var onReact: ((Message, String) -> Unit)? = null

    private val allMessages = mutableListOf<Message>()
    private var query = ""

    companion object {
        private const val TYPE_TEXT_OUT = 0
        private const val TYPE_TEXT_IN = 1
        private const val TYPE_IMAGE_OUT = 2
        private const val TYPE_IMAGE_IN = 3
        private const val TYPE_VIDEO_OUT = 4
        private const val TYPE_VIDEO_IN = 5
        private const val TYPE_VOICE_OUT = 6
        private const val TYPE_VOICE_IN = 7

        private var mediaPlayer: MediaPlayer? = null
        private var playingId: String? = null
    }

    private fun displayed(): List<Message> {
        val q = query.trim()
        if (q.isEmpty()) return allMessages.sortedBy { it.createdAt }
        return allMessages
            .filter { !it.deleted && it.content?.contains(q, true) == true }
            .sortedBy { it.createdAt }
    }

    fun submit(list: List<Message>) {
        allMessages.clear()
        allMessages.addAll(list)
        notifyDataSetChanged()
    }

    fun addMessage(message: Message) {
        allMessages.removeAll { it.id == message.id }
        allMessages.add(message)
        notifyDataSetChanged()
    }

    fun upsert(message: Message) {
        val index = allMessages.indexOfFirst { it.id == message.id }
        if (index >= 0) {
            allMessages[index] = message
        } else {
            allMessages.add(message)
        }
        notifyDataSetChanged()
    }

    fun filter(newQuery: String) {
        query = newQuery
        notifyDataSetChanged()
    }

    fun markRead(fromId: String) {
        allMessages.forEachIndexed { index, msg ->
            if (msg.fromId == myId && msg.toId == fromId && !msg.read) {
                allMessages[index] = msg.copy(read = true)
                notifyItemChanged(index)
            }
        }
    }

    fun isEmpty(): Boolean = displayed().isEmpty()

    override fun getItemViewType(position: Int): Int {
        val message = displayed()[position]
        val isMine = message.fromId == myId
        if (message.deleted) return if (isMine) TYPE_TEXT_OUT else TYPE_TEXT_IN
        return when {
            isMine && message.type == "image" -> TYPE_IMAGE_OUT
            isMine && message.type == "video" -> TYPE_VIDEO_OUT
            (message.type == "voice" || message.type == "audio") && isMine -> TYPE_VOICE_OUT
            message.type == "image" -> TYPE_IMAGE_IN
            message.type == "video" -> TYPE_VIDEO_IN
            message.type == "voice" || message.type == "audio" -> TYPE_VOICE_IN
            isMine -> TYPE_TEXT_OUT
            else -> TYPE_TEXT_IN
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_TEXT_OUT -> TextOutVH(ItemMessageTextOutBinding.inflate(inflater, parent, false))
            TYPE_TEXT_IN -> TextInVH(ItemMessageTextInBinding.inflate(inflater, parent, false))
            TYPE_IMAGE_OUT -> ImageOutVH(ItemMessageImageOutBinding.inflate(inflater, parent, false))
            TYPE_IMAGE_IN -> ImageInVH(ItemMessageImageInBinding.inflate(inflater, parent, false))
            TYPE_VIDEO_OUT -> VideoOutVH(ItemMessageVideoOutBinding.inflate(inflater, parent, false))
            TYPE_VIDEO_IN -> VideoInVH(ItemMessageVideoInBinding.inflate(inflater, parent, false))
            TYPE_VOICE_OUT -> VoiceOutVH(ItemMessageVoiceOutBinding.inflate(inflater, parent, false))
            else -> VoiceInVH(ItemMessageVoiceInBinding.inflate(inflater, parent, false))
        }
    }

    override fun getItemCount(): Int = displayed().size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = displayed()[position]
        when (holder) {
            is TextOutVH -> holder.bind(message)
            is TextInVH -> holder.bind(message)
            is ImageOutVH -> holder.bind(message)
            is ImageInVH -> holder.bind(message)
            is VideoOutVH -> holder.bind(message)
            is VideoInVH -> holder.bind(message)
            is VoiceOutVH -> holder.bind(message)
            is VoiceInVH -> holder.bind(message)
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

    private fun stateLabel(message: Message): String {
        if (message.pending) return "  ${context.getString(R.string.sending)}"
        val edited = if (message.edited) context.getString(R.string.edited_suffix) else ""
        val read = if (message.fromId == myId) {
            if (message.read) "  ✓✓" else "  ✓"
        } else {
            ""
        }
        return "$edited$read"
    }

    private fun bindText(message: Message, textView: TextView, timeView: TextView, reactions: LinearLayout) {
        if (message.deleted) {
            textView.text = context.getString(R.string.deleted_message)
            textView.setTypeface(textView.typeface, android.graphics.Typeface.ITALIC)
            textView.setTextColor(ContextCompat.getColor(context, R.color.gray_text))
        } else {
            textView.text = message.content
            textView.setTypeface(textView.typeface, android.graphics.Typeface.NORMAL)
        }
        timeView.text = timeLabel(message) + stateLabel(message)
        renderReactions(reactions, message)
    }

    private fun bindMediaTime(message: Message, timeView: TextView, reactions: LinearLayout) {
        timeView.text = timeLabel(message) + stateLabel(message)
        renderReactions(reactions, message)
    }

    private fun renderReactions(container: LinearLayout, message: Message) {
        container.removeAllViews()
        if (message.reactions.isEmpty()) return
        val density = context.resources.displayMetrics.density
        message.reactions.forEach { (emoji, users) ->
            if (users.isEmpty()) return@forEach
            val reacted = users.contains(myId)
            val chip = TextView(context)
            chip.text = "$emoji ${users.size}"
            chip.setTextSize(12f)
            chip.setTextColor(
                ContextCompat.getColor(context, if (reacted) R.color.primary else R.color.gray_text)
            )
            chip.setBackgroundResource(if (reacted) R.drawable.chip_reacted else R.drawable.chip)
            chip.setPadding((8 * density).toInt(), (3 * density).toInt(), (8 * density).toInt(), (3 * density).toInt())
            chip.setOnClickListener { onReact?.invoke(message, emoji) }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = (4 * density).toInt() }
            container.addView(chip, lp)
        }
    }

    private fun formatDuration(seconds: Int?): String {
        val s = (seconds ?: 0).coerceAtLeast(0)
        return "%d:%02d".format(Locale.US, s / 60, s % 60)
    }

    private fun toggleVoice(message: Message) {
        val url = message.mediaUrl ?: return
        if (playingId == message.id) {
            stopPlayback()
            notifyDataSetChanged()
            return
        }
        stopPlayback()
        playingId = message.id
        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(url)
                setOnPreparedListener { start() }
                setOnCompletionListener {
                    playingId = null
                    notifyDataSetChanged()
                }
                setOnErrorListener { _, _, _ ->
                    playingId = null
                    notifyDataSetChanged()
                    true
                }
                prepareAsync()
            } catch (e: Exception) {
                playingId = null
            }
        }
        notifyDataSetChanged()
    }

    private fun stopPlayback() {
        try {
            mediaPlayer?.release()
        } catch (e: Exception) {
        }
        mediaPlayer = null
        playingId = null
    }

    private fun bindVoice(
        playBtn: ImageButton,
        durationView: TextView,
        message: Message
    ) {
        val playing = playingId == message.id
        playBtn.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
        val tint = if (message.fromId == myId) android.graphics.Color.WHITE else ContextCompat.getColor(context, R.color.primary)
        playBtn.imageTintList = android.content.res.ColorStateList.valueOf(tint)
        durationView.text = formatDuration(message.duration)
        playBtn.setOnClickListener { toggleVoice(message) }
    }

    private fun openMedia(url: String?) {
        url ?: return
        val intent = android.content.Intent(
            android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse(url)
        )
        context.startActivity(intent)
    }

    inner class TextOutVH(private val binding: ItemMessageTextOutBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message) {
            bindText(message, binding.messageText, binding.timeLabel, binding.reactionsContainer)
            binding.root.setOnLongClickListener { onLongPress?.invoke(message); true }
        }
    }

    inner class TextInVH(private val binding: ItemMessageTextInBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message) {
            bindText(message, binding.messageText, binding.timeLabel, binding.reactionsContainer)
            binding.root.setOnLongClickListener { onLongPress?.invoke(message); true }
        }
    }

    inner class ImageOutVH(private val binding: ItemMessageImageOutBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message) {
            message.mediaUrl?.let {
                binding.messageImage.load(it) {
                    placeholder(R.drawable.bg_image_placeholder)
                    error(R.drawable.bg_image_placeholder)
                }
            }
            bindMediaTime(message, binding.timeLabel, binding.reactionsContainer)
            binding.messageImage.setOnClickListener { openMedia(message.mediaUrl) }
            binding.root.setOnLongClickListener { onLongPress?.invoke(message); true }
        }
    }

    inner class ImageInVH(private val binding: ItemMessageImageInBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message) {
            message.mediaUrl?.let {
                binding.messageImage.load(it) {
                    placeholder(R.drawable.bg_image_placeholder)
                    error(R.drawable.bg_image_placeholder)
                }
            }
            bindMediaTime(message, binding.timeLabel, binding.reactionsContainer)
            binding.messageImage.setOnClickListener { openMedia(message.mediaUrl) }
            binding.root.setOnLongClickListener { onLongPress?.invoke(message); true }
        }
    }

    inner class VideoOutVH(private val binding: ItemMessageVideoOutBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message) {
            message.mediaUrl?.let {
                binding.videoThumb.load(it) {
                    videoFrameMillis(1000)
                    decoderFactory(VideoFrameDecoder.Factory())
                    placeholder(R.drawable.bg_image_placeholder)
                    error(R.drawable.bg_image_placeholder)
                }
            }
            binding.playBadge.setImageResource(R.drawable.ic_play_circle)
            bindMediaTime(message, binding.timeLabel, binding.reactionsContainer)
            binding.videoThumb.setOnClickListener { openMedia(message.mediaUrl) }
            binding.playBadge.setOnClickListener { openMedia(message.mediaUrl) }
            binding.root.setOnLongClickListener { onLongPress?.invoke(message); true }
        }
    }

    inner class VideoInVH(private val binding: ItemMessageVideoInBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message) {
            message.mediaUrl?.let {
                binding.videoThumb.load(it) {
                    videoFrameMillis(1000)
                    decoderFactory(VideoFrameDecoder.Factory())
                    placeholder(R.drawable.bg_image_placeholder)
                    error(R.drawable.bg_image_placeholder)
                }
            }
            binding.playBadge.setImageResource(R.drawable.ic_play_circle)
            bindMediaTime(message, binding.timeLabel, binding.reactionsContainer)
            binding.videoThumb.setOnClickListener { openMedia(message.mediaUrl) }
            binding.playBadge.setOnClickListener { openMedia(message.mediaUrl) }
            binding.root.setOnLongClickListener { onLongPress?.invoke(message); true }
        }
    }

    inner class VoiceOutVH(private val binding: ItemMessageVoiceOutBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message) {
            bindVoice(binding.playBtn, binding.durationLabel, message)
            bindMediaTime(message, binding.timeLabel, binding.reactionsContainer)
            binding.root.setOnLongClickListener { onLongPress?.invoke(message); true }
        }
    }

    inner class VoiceInVH(private val binding: ItemMessageVoiceInBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message) {
            bindVoice(binding.playBtn, binding.durationLabel, message)
            bindMediaTime(message, binding.timeLabel, binding.reactionsContainer)
            binding.root.setOnLongClickListener { onLongPress?.invoke(message); true }
        }
    }
}
