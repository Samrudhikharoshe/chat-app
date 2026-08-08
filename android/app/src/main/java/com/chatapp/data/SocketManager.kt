package com.chatapp.data

import io.socket.client.Ack
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList

object SocketManager {

    interface Listener {
        fun onMessageReceived(message: Message)
        fun onMessageSent(message: Message)
        fun onUserStatus(user: User)
        fun onTyping(fromId: String, name: String, typing: Boolean)
        fun onMessagesRead(fromId: String)
        fun onConnected()
        fun onDisconnected()
        fun onError(message: String)
    }

    @Volatile
    private var socket: Socket? = null

    private val listeners = CopyOnWriteArrayList<Listener>()

    val isConnected: Boolean
        get() = socket?.connected() == true

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun connect(token: String) {
        if (socket?.connected() == true) return

        val options = IO.Options.builder()
            .setTransports(arrayOf("websocket"))
            .setReconnection(true)
            .setReconnectionAttempts(Int.MAX_VALUE)
            .setReconnectionDelay(1000)
            .setReconnectionDelayMax(5000)
            .setAuth(mapOf("token" to token))
            .build()

        val newSocket = IO.socket(URI.create(Config.SOCKET_URL), options)

        newSocket.on(Socket.EVENT_CONNECT) {
            listeners.forEach { it.onConnected() }
        }
        newSocket.on(Socket.EVENT_DISCONNECT) {
            listeners.forEach { it.onDisconnected() }
        }
        newSocket.on(Socket.EVENT_CONNECT_ERROR) {
            listeners.forEach { l -> l.onError("Cannot reach the chat server.") }
        }

        newSocket.on("message:new") { args ->
            val message = parseMessage(args)
            if (message != null) listeners.forEach { it.onMessageReceived(message) }
        }
        newSocket.on("message:ack") { args ->
            val message = parseMessage(args)
            if (message != null) listeners.forEach { it.onMessageSent(message) }
        }
        newSocket.on("user:status") { args ->
            val user = parseUser(args)
            if (user != null) listeners.forEach { it.onUserStatus(user) }
        }
        newSocket.on("typing:start") { args ->
            if (args.isNotEmpty() && args[0] is JSONObject) {
                val obj = args[0] as JSONObject
                val from = obj.optString("from")
                val name = obj.optString("name")
                listeners.forEach { it.onTyping(from, name, true) }
            }
        }
        newSocket.on("typing:stop") { args ->
            if (args.isNotEmpty() && args[0] is JSONObject) {
                val obj = args[0] as JSONObject
                val from = obj.optString("from")
                listeners.forEach { it.onTyping(from, "", false) }
            }
        }
        newSocket.on("message:read") { args ->
            if (args.isNotEmpty() && args[0] is JSONObject) {
                val obj = args[0] as JSONObject
                val from = obj.optString("from")
                listeners.forEach { it.onMessagesRead(from) }
            }
        }

        socket = newSocket
        newSocket.connect()
    }

    fun disconnect() {
        socket?.disconnect()
        socket = null
    }

    fun sendMessage(to: String, type: String, content: String?, mediaUrl: String?) {
        val payload = JSONObject()
        payload.put("to", to)
        payload.put("type", type)
        content?.let { payload.put("content", it) }
        mediaUrl?.let { payload.put("mediaUrl", it) }
        socket?.emit("message:send", payload, Ack { args ->
            if (args.isNotEmpty() && args[0] is JSONObject) {
                val obj = args[0] as JSONObject
                if (obj.optBoolean("ok")) {
                    val message = parseMessage(arrayOf(obj.optJSONObject("message")))
                    if (message != null) listeners.forEach { it.onMessageSent(message) }
                }
            }
        })
    }

    fun markRead(fromId: String) {
        val payload = JSONObject()
        payload.put("from", fromId)
        socket?.emit("message:read", payload)
    }

    fun sendTyping(to: String, typing: Boolean) {
        val payload = JSONObject()
        payload.put("to", to)
        socket?.emit(if (typing) "typing:start" else "typing:stop", payload)
    }

    private fun parseMessage(args: Array<Any>): Message? {
        if (args.isEmpty() || args[0] !is JSONObject) return null
        val obj = args[0] as JSONObject
        return try {
            Message(
                id = obj.getString("id"),
                fromId = obj.getString("from"),
                toId = obj.getString("to"),
                type = obj.optString("type", "text"),
                content = if (obj.isNull("content")) null else obj.optString("content"),
                mediaUrl = if (obj.isNull("mediaUrl")) null else obj.optString("mediaUrl"),
                createdAt = obj.optString("createdAt"),
                read = obj.optBoolean("read", false),
                readAt = if (obj.isNull("readAt")) null else obj.optString("readAt")
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseUser(args: Array<Any>): User? {
        if (args.isEmpty() || args[0] !is JSONObject) return null
        val obj = args[0] as JSONObject
        return try {
            User(
                id = obj.getString("id"),
                name = obj.getString("name"),
                email = obj.getString("email"),
                avatar = if (obj.isNull("avatar")) null else obj.optString("avatar"),
                online = obj.optBoolean("online", false),
                lastSeen = if (obj.isNull("lastSeen")) null else obj.optString("lastSeen")
            )
        } catch (e: Exception) {
            null
        }
    }
}
