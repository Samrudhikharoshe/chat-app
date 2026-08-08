package com.chatapp.ui.chat

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.chatapp.R
import com.chatapp.data.ChatRepository
import com.chatapp.data.Message
import com.chatapp.data.MessageCache
import com.chatapp.data.Session
import com.chatapp.data.SocketManager
import com.chatapp.data.User
import com.chatapp.databinding.ActivityChatBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.UUID

class ChatActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PEER_ID = "peer_id"
        const val EXTRA_PEER_NAME = "peer_name"
        private const val RECORDING_DURATION_MAX = 90
    }

    private lateinit var binding: ActivityChatBinding
    private lateinit var adapter: MessagesAdapter
    private val repository = ChatRepository()

    private lateinit var peerId: String
    private var peerName: String = ""

    private var lastTypingSent = 0L
    private var typingJob: Job? = null

    private var mediaRecorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var recordingStartedAt = 0L
    private var recordingTimerJob: Job? = null

    private var reactionDialog: AlertDialog? = null

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) uploadAndSendMedia(uri, "image")
    }

    private val pickVideo = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) uploadAndSendMedia(uri, "video")
    }

    private val recordPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) beginRecording()
        else Toast.makeText(
            this,
            getString(R.string.recording_requires_permission),
            Toast.LENGTH_SHORT
        ).show()
    }

    private val socketListener = object : SocketManager.Listener {
        override fun onMessageReceived(message: Message) {
            if (message.fromId == peerId) {
                runOnUiThread {
                    appendMessage(message)
                    MessageCache.current.saveMessage(message)
                    SocketManager.markRead(peerId)
                }
            }
        }

        override fun onMessageSent(message: Message) {
            if (message.fromId == Session.current.userId && message.toId == peerId) {
                runOnUiThread {
                    upsertMessage(message)
                    MessageCache.current.saveMessage(message)
                }
            }
        }

        override fun onMessageUpdated(message: Message) {
            if (message.fromId == peerId || message.toId == peerId) {
                runOnUiThread {
                    upsertMessage(message)
                    MessageCache.current.saveMessage(message)
                }
            }
        }

        override fun onUserStatus(user: User) {
            if (user.id == peerId) {
                runOnUiThread { updateHeaderStatus(user) }
            }
        }

        override fun onTyping(fromId: String, name: String, typing: Boolean) {
            if (fromId == peerId) {
                runOnUiThread {
                    binding.typingIndicator.visibility =
                        if (typing) View.VISIBLE else View.GONE
                    if (typing) binding.typingIndicator.text = "$name is typing..."
                }
            }
        }

        override fun onMessagesRead(fromId: String) {
            if (fromId == peerId) {
                runOnUiThread { adapter.markRead(peerId) }
            }
        }

        override fun onConnected() {
            runOnUiThread {
                binding.connectionBanner.visibility = View.GONE
                loadHistory()
            }
        }

        override fun onDisconnected() {
            runOnUiThread {
                binding.connectionBanner.visibility = View.VISIBLE
            }
        }

        override fun onError(message: String) {
            runOnUiThread {
                Toast.makeText(this@ChatActivity, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        peerId = intent.getStringExtra(EXTRA_PEER_ID).orEmpty()
        peerName = intent.getStringExtra(EXTRA_PEER_NAME) ?: ""

        adapter = MessagesAdapter(this, Session.current.userId.orEmpty())
        binding.recycler.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.recycler.adapter = adapter

        adapter.onLongPress = { message -> showMessageActions(message) }
        adapter.onReact = { message, emoji -> SocketManager.reactToMessage(message.id, emoji) }

        setupToolbar()
        SocketManager.addListener(socketListener)

        binding.btnSend.setOnClickListener { sendCurrentText() }
        binding.btnAttach.setOnClickListener { showAttachOptions() }

        binding.inputMessage.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                if (s != null && s.isNotEmpty()) {
                    SocketManager.sendTyping(peerId, true)
                    lastTypingSent = System.currentTimeMillis()
                    typingJob?.cancel()
                    typingJob = lifecycleScope.launch {
                        delay(1500)
                        SocketManager.sendTyping(peerId, false)
                    }
                }
            }
        })

        binding.searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                adapter.filter(s?.toString().orEmpty())
            }
        })

        loadHistory()
        if (Session.current.userId != null) SocketManager.markRead(peerId)
    }

    override fun onResume() {
        super.onResume()
        if (Session.current.userId != null) SocketManager.markRead(peerId)
    }

    override fun onDestroy() {
        SocketManager.removeListener(socketListener)
        typingJob?.cancel()
        recordingTimerJob?.cancel()
        reactionDialog?.dismiss()
        try {
            mediaRecorder?.release()
        } catch (e: Exception) {
        }
        super.onDestroy()
    }

    override fun onBackPressed() {
        SocketManager.sendTyping(peerId, false)
        super.onBackPressed()
    }

    private fun setupToolbar() {
        binding.toolbar.title = peerName
        binding.toolbar.subtitle = "Offline"
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun updateHeaderStatus(user: User) {
        binding.toolbar.subtitle = if (user.online) {
            "Online"
        } else {
            user.lastSeen?.takeIf { it.isNotBlank() }
                ?.let { "Last seen ${formatTime(it)}" }
                ?: "Offline"
        }
    }

    private fun formatTime(iso: String): String {
        return try {
            val parsed = java.time.OffsetDateTime.parse(iso)
            val now = java.time.OffsetDateTime.now()
            val sameDay = parsed.toLocalDate() == now.toLocalDate()
            val time = parsed.toLocalTime().truncatedTo(java.time.temporal.ChronoUnit.MINUTES)
                .format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
            if (sameDay) "today at $time"
            else "${parsed.toLocalDate()} $time"
        } catch (e: Exception) {
            "recently"
        }
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            adapter.submit(MessageCache.current.historyFor(peerId))
            repository.fetchMessages(peerId).onSuccess { history ->
                adapter.submit(history)
                MessageCache.current.saveAll(peerId, history)
                if (Session.current.userId != null) SocketManager.markRead(peerId)
            }.onFailure {
                if (adapter.isEmpty()) {
                    Toast.makeText(
                        this@ChatActivity,
                        it.message,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun sendCurrentText() {
        val text = binding.inputMessage.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return
        binding.inputMessage.text?.clear()
        SocketManager.sendTyping(peerId, false)
        sendMessageLocal("text", text, null)
    }

    private fun sendMessageLocal(type: String, content: String?, mediaUrl: String?, duration: Int? = null) {
        val id = UUID.randomUUID().toString()
        val message = Message(
            id = id,
            fromId = Session.current.userId.orEmpty(),
            toId = peerId,
            type = type,
            content = content,
            mediaUrl = mediaUrl,
            duration = duration,
            createdAt = java.time.OffsetDateTime.now().toString(),
            read = false,
            pending = true
        )
        MessageCache.current.queueOutgoing(message)
        adapter.upsert(message)
        binding.recycler.scrollToPosition(adapter.itemCount - 1)
        SocketManager.sendMessage(peerId, type, content, mediaUrl, id, duration)
    }

    private fun showAttachOptions() {
        val options = arrayOf(
            getString(R.string.attach_photo),
            getString(R.string.attach_video),
            getString(R.string.attach_voice)
        )
        AlertDialog.Builder(this)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pickImage.launch("image/*")
                    1 -> pickVideo.launch("video/*")
                    2 -> startRecording()
                }
            }
            .show()
    }

    private fun uploadAndSendMedia(uri: Uri, type: String) {
        setSending(true)
        lifecycleScope.launch {
            repository.uploadMedia(this@ChatActivity, uri).onSuccess { url ->
                setSending(false)
                sendMessageLocal(type, null, url)
            }.onFailure {
                setSending(false)
                Toast.makeText(
                    this@ChatActivity,
                    it.message ?: getString(R.string.error_generic),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun startRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        beginRecording()
    }

    private fun beginRecording() {
        try {
            val file = File(cacheDir, "voice_${System.currentTimeMillis()}.m4a")
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioSamplingRate(44100)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()
            recorder.start()
            mediaRecorder = recorder
            recordingFile = file
            recordingStartedAt = System.currentTimeMillis()
            showRecordingBar()
        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: "Cannot record.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording() {
        val file = recordingFile
        val startedAt = recordingStartedAt
        recordingTimerJob?.cancel()
        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
        }
        mediaRecorder?.release()
        mediaRecorder = null
        recordingFile = null
        hideRecordingBar()
        if (file == null || !file.exists() || file.length() == 0L) return
        val duration = ((System.currentTimeMillis() - startedAt) / 1000).toInt().coerceIn(1, RECORDING_DURATION_MAX)
        uploadAndSendVoice(file, duration)
    }

    private fun cancelRecording() {
        recordingTimerJob?.cancel()
        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
        }
        mediaRecorder?.release()
        mediaRecorder = null
        recordingFile?.delete()
        recordingFile = null
        hideRecordingBar()
    }

    private fun showRecordingBar() {
        binding.recordingBar.visibility = View.VISIBLE
        binding.inputRow.visibility = View.GONE
        binding.btnStopRecording.setOnClickListener { stopRecording() }
        binding.btnCancelRecording.setOnClickListener { cancelRecording() }
        recordingTimerJob = lifecycleScope.launch {
            while (isActive) {
                val elapsed = (System.currentTimeMillis() - recordingStartedAt) / 1000
                binding.recordingTimer.text = String.format(
                    Locale.US,
                    "%s %d:%02d",
                    getString(R.string.recording).removeSuffix("…"),
                    elapsed / 60,
                    elapsed % 60
                )
                delay(500)
            }
        }
    }

    private fun hideRecordingBar() {
        binding.recordingBar.visibility = View.GONE
        binding.inputRow.visibility = View.VISIBLE
    }

    private fun uploadAndSendVoice(file: File, duration: Int) {
        setSending(true)
        lifecycleScope.launch {
            repository.uploadBytes(file.readBytes(), file.name, "audio/mp4").onSuccess { url ->
                setSending(false)
                sendMessageLocal("voice", null, url, duration)
            }.onFailure {
                setSending(false)
                Toast.makeText(
                    this@ChatActivity,
                    it.message ?: getString(R.string.error_generic),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showMessageActions(message: Message) {
        val isMine = message.fromId == Session.current.userId
        val items = mutableListOf(getString(R.string.react))
        if (isMine && message.type == "text" && !message.deleted) {
            items.add(getString(R.string.edit))
        }
        if (!message.deleted) {
            items.add(getString(R.string.delete))
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.choose_action)
            .setItems(items.toTypedArray()) { _, which ->
                when (items[which]) {
                    getString(R.string.react) -> showReactionPicker(message)
                    getString(R.string.edit) -> showEditDialog(message)
                    getString(R.string.delete) -> showDeleteConfirm(message)
                }
            }
            .show()
    }

    private fun showReactionPicker(message: Message) {
        val emojis = arrayOf("👍", "❤️", "😂", "😮", "😢", "🙏")
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(16, 20, 16, 20)
        }
        emojis.forEach { emoji ->
            row.addView(TextView(this).apply {
                text = emoji
                textSize = 30f
                setPadding(18, 0, 18, 0)
                setOnClickListener {
                    SocketManager.reactToMessage(message.id, emoji)
                    reactionDialog?.dismiss()
                }
            })
        }
        reactionDialog = AlertDialog.Builder(this).setView(row).show()
    }

    private fun showEditDialog(message: Message) {
        val input = EditText(this).apply {
            setText(message.content)
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.edit_message_hint)
            .setView(input)
            .setPositiveButton(R.string.edit) { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) SocketManager.editMessage(message.id, text)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showDeleteConfirm(message: Message) {
        AlertDialog.Builder(this)
            .setMessage(R.string.delete_message_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                SocketManager.deleteMessage(message.id)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun setSending(sending: Boolean) {
        binding.btnSend.isEnabled = !sending
        binding.btnAttach.isEnabled = !sending
    }

    private fun appendMessage(message: Message) {
        adapter.addMessage(message)
        binding.recycler.scrollToPosition(adapter.itemCount - 1)
    }

    private fun upsertMessage(message: Message) {
        adapter.upsert(message)
        binding.recycler.scrollToPosition(adapter.itemCount - 1)
    }
}
