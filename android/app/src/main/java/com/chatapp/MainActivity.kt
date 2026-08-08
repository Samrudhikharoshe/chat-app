package com.chatapp

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.chatapp.data.Session
import com.chatapp.ui.contacts.ContactsActivity
import com.chatapp.ui.login.LoginActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Handler(Looper.getMainLooper()).postDelayed({
            val target = if (Session.current.isLoggedIn()) {
                Intent(this, ContactsActivity::class.java)
            } else {
                Intent(this, LoginActivity::class.java)
            }
            startActivity(target)
            finish()
        }, 600)
    }
}
