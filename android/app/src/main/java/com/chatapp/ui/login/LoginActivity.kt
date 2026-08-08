package com.chatapp.ui.login

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.chatapp.R
import com.chatapp.data.ApiClient
import com.chatapp.data.ChatRepository
import com.chatapp.data.Session
import com.chatapp.data.SocketManager
import com.chatapp.databinding.ActivityLoginBinding
import com.chatapp.service.ChatConnectionService
import com.chatapp.ui.contacts.ContactsActivity
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val repository = ChatRepository()
    private var isRegisterMode = false

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.inputName.visibility = View.GONE
        binding.inputServer.setText(Session.serverBase().removeSuffix("/"))

        binding.toggleMode.setOnClickListener {
            isRegisterMode = !isRegisterMode
            binding.inputName.visibility = if (isRegisterMode) View.VISIBLE else View.GONE
            binding.toggleMode.text =
                if (isRegisterMode) getString(R.string.already_account) else getString(R.string.create_account)
            binding.btnSubmit.text =
                if (isRegisterMode) getString(R.string.sign_up) else getString(R.string.sign_in)
        }

        binding.btnSubmit.setOnClickListener { submit() }

        binding.inputPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (s != null && s.length >= 6) binding.tilPassword.error = null
            }
        })
    }

    private fun submit() {
        val name = binding.inputName.text.toString().trim()
        val email = binding.inputEmail.text.toString().trim()
        val password = binding.inputPassword.text.toString()
        val server = binding.inputServer.text.toString().trim()

        if (server.isEmpty()) {
            binding.tilServer.error = getString(R.string.server_required)
            return
        }
        if (!server.startsWith("http://") && !server.startsWith("https://")) {
            binding.tilServer.error = getString(R.string.server_invalid)
            return
        }

        Session.current.serverUrl = server
        ApiClient.configure(server)

        if (isRegisterMode && name.isEmpty()) {
            binding.tilName.error = getString(R.string.name_required)
            return
        }
        if (email.isEmpty() || !email.contains("@")) {
            binding.tilEmail.error = getString(R.string.email_invalid)
            return
        }
        if (password.length < 6) {
            binding.tilPassword.error = getString(R.string.password_short)
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            val result = if (isRegisterMode) {
                repository.register(name, email, password)
            } else {
                repository.login(email, password)
            }

            result.onSuccess { auth ->
                Session.current.saveAuth(auth.user, auth.token)
                SocketManager.connect(auth.token)
                startService(Intent(this@LoginActivity, ChatConnectionService::class.java))
                requestNotificationPermission()
                startActivity(Intent(this@LoginActivity, ContactsActivity::class.java))
                finish()
            }.onFailure { error ->
                setLoading(false)
                Toast.makeText(
                    this@LoginActivity,
                    error.message ?: getString(R.string.error_generic),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.btnSubmit.isEnabled = !loading
        binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
