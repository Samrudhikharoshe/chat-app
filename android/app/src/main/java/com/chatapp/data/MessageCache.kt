package com.chatapp.data

import android.content.Context
import android.content.SharedPreferences

class MessageCache private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("chat_cache", Context.MODE_PRIVATE)

    fun historyFor(peerId: String): List<Message> {
        val json = prefs.getString(keyFor(peerId), null) ?: return emptyList()
        return ApiClient.gsonList<Message>(json).sortedBy { it.createdAt }
    }

    fun saveMessage(message: Message) {
        val peerId = if (message.fromId == Session.current.userId) message.toId else message.fromId
        val current = historyFor(peerId).toMutableList()
        current.removeAll { it.id == message.id }
        current.add(message)
        prefs.edit().putString(keyFor(peerId), ApiClient.gson.toJson(current)).apply()
    }

    fun saveAll(peerId: String, messages: List<Message>) {
        val sorted = messages.sortedBy { it.createdAt }
        prefs.edit().putString(keyFor(peerId), ApiClient.gson.toJson(sorted)).apply()
    }

    private fun keyFor(peerId: String): String = "chat_$peerId"

    companion object {
        @Volatile
        private var instance: MessageCache? = null

        fun init(context: Context) {
            if (instance == null) {
                instance = MessageCache(context.applicationContext)
            }
        }

        val current: MessageCache
            get() = checkNotNull(instance) { "MessageCache.init() must be called first" }
    }
}
