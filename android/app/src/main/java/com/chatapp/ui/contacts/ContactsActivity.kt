package com.chatapp.ui.contacts

import android.content.Intent
import android.net.Uri
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
import com.chatapp.databinding.ActivityContactsBinding
import com.chatapp.ui.chat.ChatActivity
import com.chatapp.ui.login.LoginActivity
import kotlinx.coroutines.launch

class ContactsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityContactsBinding
    private lateinit var adapter: ContactsAdapter
    private val repository = ChatRepository()
    private var users = listOf<User>()

    private val pickProfilePhoto = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) uploadProfilePhoto(uri)
    }

    private val socketListener = object : SocketManager.Listener {
        override fun onUserStatus(user: User) {
            runOnUiThread {
                users = users.map { if (it.id == user.id) user else it }
                adapter.submit(users)
            }
        }

        override fun onMessageReceived(message: Message) {
            runOnUiThread {
                updateLastMessage(message)
            }
        }

        override fun onMessageSent(message: Message) {
            runOnUiThread {
                updateLastMessage(message)
            }
        }

        override fun onMessageUpdated(message: Message) {
            runOnUiThread {
                updateLastMessage(message)
            }
        }

        override fun onConnected() {
            runOnUiThread { loadUsers() }
        }

        override fun onError(message: String) = Unit
        override fun onTyping(fromId: String, name: String, typing: Boolean) = Unit
        override fun onMessagesRead(fromId: String) = Unit
        override fun onDisconnected() = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.title = getString(R.string.contacts)
        binding.toolbar.inflateMenu(R.menu.menu_contacts)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_profile_photo -> {
                    pickProfilePhoto.launch("image/*")
                    true
                }
                R.id.action_logout -> {
                    logout()
                    true
                }
                else -> false
            }
        }

        adapter = ContactsAdapter { user ->
            startActivity(
                Intent(this, ChatActivity::class.java)
                    .putExtra(ChatActivity.EXTRA_PEER_ID, user.id)
                    .putExtra(ChatActivity.EXTRA_PEER_NAME, user.name)
            )
        }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                val q = s?.toString()?.trim().orEmpty()
                adapter.submit(users.filter {
                    it.name.contains(q, true) || it.email.contains(q, true)
                })
            }
        })

        binding.refresh.setOnClickListener { loadUsers() }
        SocketManager.addListener(socketListener)
        loadUsers()
    }

    override fun onResume() {
        super.onResume()
        loadUsers()
    }

    override fun onDestroy() {
        SocketManager.removeListener(socketListener)
        super.onDestroy()
    }

    private fun loadUsers() {
        lifecycleScope.launch {
            repository.fetchUsers().onSuccess { list ->
                users = list
                val q = binding.searchInput.text.toString().trim()
                adapter.submit(
                    if (q.isEmpty()) list
                    else list.filter { it.name.contains(q, true) || it.email.contains(q, true) }
                )
                binding.emptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                binding.progress.visibility = View.GONE
            }.onFailure {
                binding.progress.visibility = View.GONE
                Toast.makeText(this@ContactsActivity, it.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateLastMessage(message: Message) {
        val peerId = if (message.fromId == Session.current.userId) message.toId else message.fromId
        val peer = users.find { it.id == peerId } ?: return
        adapter.updatePreview(peerId, previewOf(message), message.createdAt)
    }

    private fun previewOf(message: Message): String {
        if (message.deleted) return getString(R.string.deleted_message)
        return when (message.type) {
            "image" -> getString(R.string.photo)
            "video" -> getString(R.string.video)
            "voice" -> getString(R.string.voice_note)
            "audio" -> getString(R.string.audio)
            else -> message.content ?: ""
        }
    }

    private fun uploadProfilePhoto(uri: Uri) {
        lifecycleScope.launch {
            repository.uploadMedia(this@ContactsActivity, uri).onSuccess { url ->
                Session.current.avatarUrl = url
                SocketManager.setAvatar(url)
                Toast.makeText(
                    this@ContactsActivity,
                    getString(R.string.set_profile_photo) + " ✓",
                    Toast.LENGTH_SHORT
                ).show()
            }.onFailure {
                Toast.makeText(
                    this@ContactsActivity,
                    it.message ?: getString(R.string.error_generic),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun logout() {
        SocketManager.disconnect()
        Session.current.clear()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
