package com.chatapp.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.chatapp.data.SmsMessenger

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        for (message in messages) {
            val body = message.messageBody ?: continue
            if (!body.contains("\"v\":1")) continue
            val sender = message.originatingAddress ?: continue
            SmsMessenger.handleIncoming(body, SmsMessenger.normalize(sender))
        }
    }
}
