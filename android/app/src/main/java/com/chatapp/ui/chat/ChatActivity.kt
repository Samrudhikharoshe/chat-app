package com.chatapp.ui.chat

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
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
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PEER_ID = "peer_id"
        const val EXTRA_PEER_NAME = "peer_name"
    }

    private lateinit var binding: ActivityChatBinding
    private lateinit var adapter: MessagesAdapter
    private val repository = ChatRepository()

    private lateinit var peerId: String
    private var peerName: String = ""

    private var lastTypingSent = 0L
    private var typingJob: Job? = null

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            uploadAndSendImage(uri)
        }
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
                    if (typing) binding.typingIndicator.text =
                        "$name is typing..."
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

        adapter = MessagesAdapter(this, Session.current.userId.orEmpty(), peerId)
        binding.recycler.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.recycler.adapter = adapter

        setupToolbar()
        SocketManager.addListener(socketListener)

        binding.btnSend.setOnClickListener { sendCurrentText() }
        binding.btnAttach.setOnClickListener { pickImage.launch("image/*") }

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
        SocketManager.sendMessage(peerId, "text", text, null)
    }

    private fun uploadAndSendImage(uri: android.net.Uri) {
        setSending(true)
        lifecycleScope.launch {
            repository.uploadImage(this@ChatActivity, uri).onSuccess { url ->
                setSending(false)
                SocketManager.sendMessage(peerId, "image", null, url)
            }.onFailure {
                setSending(false)
                Toast.makeText(
                    this@ChatActivity,
                    it.message ?: "Upload failed.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
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
