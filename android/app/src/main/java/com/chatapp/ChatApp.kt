package com.chatapp

import android.app.Application
import com.chatapp.data.SocketManager
import com.chatapp.data.Session

class ChatApp : Application() {

    companion object {
        @Volatile
        var isAppInForeground = false
    }

    override fun onCreate() {
        super.onCreate()
        Session.init(this)
        if (Session.current.token != null) {
            SocketManager.connect(Session.current.token!!)
        }
    }
}
