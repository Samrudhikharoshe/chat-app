package com.chatapp.data

import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.content.Context
import android.util.Log
import com.chatapp.ChatApp
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Long-distance offline messaging over the cellular network (SMS).
 * No internet and no server are required - messages travel as SMS.
 */
object SmsMessenger {

    private const val TAG = "SmsMessenger"
    private const val MAX_SMS_CHARS = 140

    private val listeners = CopyOnWriteArrayList<SocketManager.Listener>()

    fun addListener(listener: SocketManager.Listener) = listeners.add(listener)
    fun removeListener(listener: SocketManager.Listener) = listeners.remove(listener)

    val myNumber: String
        get() = Session.current.smsNumber?.takeIf { it.isNotBlank() }
            ?: detectMyNumber().orEmpty()

    fun detectMyNumber(): String? {
        return try {
            val tm = ChatApp.appContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            tm.line1Number?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    fun normalize(number: String): String = number.replace(Regex("[\\s\\-()]"), "")

    fun isSendable(content: String): Boolean = bodyLengthFor(content) <= MAX_SMS_CHARS

    private fun bodyLengthFor(content: String): Int {
        val sample = JSONObject()
            .put("v", 1)
            .put("id", "00000000-0000-0000-0000-000000000000")
            .put("f", myNumber)
            .put("c", content)
        return sample.toString().length
    }

    fun sendMessage(peerNumber: String, message: Message): Boolean {
        val body = buildBody(message)
        if (body.length > MAX_SMS_CHARS) return false
        try {
            SmsManager.getDefault().sendTextMessage(peerNumber, null, body, null, null)
            MessageCache.current.confirmSent(message)
            listeners.forEach { it.onMessageSent(message) }
            return true
        } catch (e: Exception) {
            Log.w(TAG, "SMS send failed", e)
            return false
        }
    }

    fun handleIncoming(body: String, sender: String) {
        val obj = try {
            JSONObject(body)
        } catch (e: Exception) {
            return
        }
        if (obj.optInt("v", 0) != 1) return
        val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return
        val from = normalize(obj.optString("f"))
        val content = obj.optString("c")

        val existing = MessageCache.current.historyFor(from).any { it.id == id }
        if (existing) return

        val message = Message(
            id = id,
            fromId = from,
            toId = Session.current.userId.orEmpty(),
            type = "text",
            content = content,
            createdAt = java.time.OffsetDateTime.now().toString(),
            read = false,
            pending = false
        )
        MessageCache.current.saveMessage(message)
        listeners.forEach { it.onMessageReceived(message) }
    }

    private fun buildBody(message: Message): String {
        return JSONObject()
            .put("v", 1)
            .put("id", message.id)
            .put("f", myNumber)
            .put("c", message.content ?: "")
            .toString()
    }
}
