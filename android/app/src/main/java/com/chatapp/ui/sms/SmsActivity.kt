package com.chatapp.ui.sms

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.chatapp.R
import com.chatapp.data.MessageCache
import com.chatapp.data.Message
import com.chatapp.data.Session
import com.chatapp.data.SmsMessenger
import com.chatapp.data.SocketManager
import com.chatapp.data.User
import com.chatapp.databinding.ActivitySmsBinding
import com.chatapp.ui.chat.ChatActivity

class SmsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySmsBinding
    private lateinit var adapter: SmsConversationAdapter

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) {
            prefillMyNumber()
        } else {
            Toast.makeText(this, getString(R.string.sms_permission_denied), Toast.LENGTH_LONG).show()
        }
    }

    private val messageListener = object : SocketManager.Listener {
        override fun onMessageReceived(message: Message) {
            runOnUiThread { loadConversations() }
        }

        override fun onMessageSent(message: Message) {
            runOnUiThread { loadConversations() }
        }

        override fun onUserStatus(user: User) = Unit
        override fun onTyping(fromId: String, name: String, typing: Boolean) = Unit
        override fun onMessagesRead(fromId: String) = Unit
        override fun onConnected() = Unit
        override fun onDisconnected() = Unit
        override fun onError(message: String) = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySmsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = SmsConversationAdapter { number ->
            startActivity(
                Intent(this, ChatActivity::class.java)
                    .putExtra(ChatActivity.EXTRA_PEER_ID, number)
                    .putExtra(ChatActivity.EXTRA_PEER_NAME, number)
                    .putExtra(ChatActivity.EXTRA_SMS, true)
            )
        }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.saveNumber.setOnClickListener { saveMyNumber() }
        binding.newChat.setOnClickListener { promptNewChat() }

        SmsMessenger.addListener(messageListener)
        prefillMyNumber()
        requestPermissionsIfNeeded()
        loadConversations()
    }

    override fun onResume() {
        super.onResume()
        loadConversations()
    }

    override fun onDestroy() {
        SmsMessenger.removeListener(messageListener)
        super.onDestroy()
    }

    private fun requestPermissionsIfNeeded() {
        val needed = neededPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun neededPermissions(): List<String> {
        val perms = mutableListOf(Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS)
        if (Build.VERSION.SDK_INT >= 26) {
            perms.add(Manifest.permission.READ_PHONE_NUMBERS)
        }
        perms.add(Manifest.permission.READ_PHONE_STATE)
        return perms
    }

    private fun prefillMyNumber() {
        val saved = Session.current.smsNumber
        if (!saved.isNullOrBlank()) {
            binding.myNumberInput.setText(saved)
            return
        }
        SmsMessenger.detectMyNumber()?.takeIf { it.isNotBlank() }?.let { detected ->
            binding.myNumberInput.setText(detected)
        }
    }

    private fun saveMyNumber() {
        val number = SmsMessenger.normalize(binding.myNumberInput.text?.toString().orEmpty())
        if (number.isEmpty()) {
            Toast.makeText(this, getString(R.string.sms_invalid_number), Toast.LENGTH_SHORT).show()
            return
        }
        Session.current.smsNumber = number
        Toast.makeText(this, getString(R.string.sms_number_saved), Toast.LENGTH_SHORT).show()
    }

    private fun promptNewChat() {
        val input = EditText(this).apply {
            hint = getString(R.string.sms_enter_number)
            inputType = android.text.InputType.TYPE_CLASS_PHONE
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.sms_new_chat))
            .setView(input)
            .setPositiveButton(getString(R.string.sms_start)) { _, _ ->
                val number = SmsMessenger.normalize(input.text.toString())
                if (number.isEmpty()) {
                    Toast.makeText(this, getString(R.string.sms_invalid_number), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                startActivity(
                    Intent(this, ChatActivity::class.java)
                        .putExtra(ChatActivity.EXTRA_PEER_ID, number)
                        .putExtra(ChatActivity.EXTRA_PEER_NAME, number)
                        .putExtra(ChatActivity.EXTRA_SMS, true)
                )
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun loadConversations() {
        val conversations = MessageCache.current.peerIds()
            .filter { isPhoneNumber(it) }
            .sortedByDescending { lastMessageTime(it) }
        adapter.submit(conversations)
        binding.emptyState.visibility = if (conversations.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun isPhoneNumber(key: String): Boolean =
        key.length < 16 && !key.contains("-") && key.any { it.isDigit() }

    private fun lastMessageTime(number: String): Long {
        return MessageCache.current.historyFor(number)
            .lastOrNull()?.createdAt
            ?.let { runCatching { java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }
            ?: 0L
    }
}
