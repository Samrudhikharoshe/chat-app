package com.chatapp.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.chatapp.ChatApp
import com.chatapp.R
import com.chatapp.data.Message
import com.chatapp.data.Session
import com.chatapp.data.SocketManager
import com.chatapp.data.User
import com.chatapp.ui.chat.ChatActivity

class ChatConnectionService : Service() {

    companion object {
        const val CHANNEL_ID = "chat_messages"
        const val NOTIFICATION_ID = 1
        private const val TAG = "ChatConnectionSvc"
    }

    private val socketListener = object : SocketManager.Listener {
        override fun onMessageReceived(message: Message) {
            if (!ChatApp.isAppInForeground) {
                notifyMessage(message)
            }
        }

        override fun onMessageSent(message: Message) = Unit
        override fun onUserStatus(user: User) = Unit
        override fun onTyping(fromId: String, name: String, typing: Boolean) = Unit
        override fun onMessagesRead(fromId: String) = Unit
        override fun onConnected() = Unit
        override fun onDisconnected() = Unit
        override fun onError(message: String) = Unit
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        SocketManager.addListener(socketListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        Session.current.token?.let { SocketManager.connect(it) }
        return START_STICKY
    }

    override fun onDestroy() {
        SocketManager.removeListener(socketListener)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildForegroundNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, ChatActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Chat App")
            .setContentText("Connected to the chat server.")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun notifyMessage(message: Message) {
        val contentIntent = PendingIntent.getActivity(
            this,
            message.id.hashCode(),
            Intent(this, ChatActivity::class.java)
                .putExtra(ChatActivity.EXTRA_PEER_ID, message.fromId)
                .putExtra(ChatActivity.EXTRA_PEER_NAME, message.content ?: "New message")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(Session.current.userName ?: "New message")
            .setContentText(message.content ?: "Shared a photo")
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.content ?: "Shared a photo"))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        try {
            NotificationManagerCompat.from(this).notify(message.id.hashCode(), notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission missing.", e)
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Chat messages",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for new chat messages"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
