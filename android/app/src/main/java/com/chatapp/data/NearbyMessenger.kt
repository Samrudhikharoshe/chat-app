package com.chatapp.data

import android.util.Log
import com.chatapp.ChatApp
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Direct phone-to-phone messaging over Wi-Fi Direct / Bluetooth via the
 * Nearby Connections API. No server and no internet are required.
 */
object NearbyMessenger {

    interface DeviceListener {
        fun onDevicesChanged(devices: List<NearbyDevice>)
        fun onPeerConnected(device: NearbyDevice)
        fun onPeerDisconnected(deviceId: String)
        fun onError(message: String)
    }

    data class NearbyDevice(
        val userId: String?,
        val name: String,
        val endpointId: String,
        val connected: Boolean
    )

    const val SERVICE_ID = "com.chatapp.nearby"
    private const val TAG = "NearbyMessenger"

    @Volatile
    private var started = false

    private val client: ConnectionsClient by lazy {
        Nearby.getConnectionsClient(ChatApp.appContext)
    }

    private val messageListeners = CopyOnWriteArrayList<SocketManager.Listener>()
    private val deviceListeners = CopyOnWriteArrayList<DeviceListener>()

    private val remoteUserIdByEndpoint = ConcurrentHashMap<String, String>()
    private val remoteNameByEndpoint = ConcurrentHashMap<String, String>()
    private val connectingEndpoints = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    var devices: List<NearbyDevice> = emptyList()
        private set

    fun addMessageListener(listener: SocketManager.Listener) = messageListeners.add(listener)
    fun removeMessageListener(listener: SocketManager.Listener) = messageListeners.remove(listener)

    fun addDeviceListener(listener: DeviceListener) = deviceListeners.add(listener)
    fun removeDeviceListener(listener: DeviceListener) = deviceListeners.remove(listener)

    fun start() {
        if (started) return
        started = true
        startAdvertising()
        startDiscovery()
    }

    fun stop() {
        started = false
        client.stopAdvertising()
        client.stopDiscovery()
        devices = emptyList()
        remoteUserIdByEndpoint.clear()
        remoteNameByEndpoint.clear()
        connectingEndpoints.clear()
        notifyDevicesChanged()
    }

    fun isNearbyPeer(userId: String): Boolean = remoteUserIdByEndpoint.containsValue(userId)

    private fun startAdvertising() {
        client.startAdvertising(
            Session.current.userName ?: "Chat user",
            SERVICE_ID,
            connectionLifecycleCallback,
            AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        ).addOnFailureListener {
            deviceListeners.forEach { l -> l.onError("Unable to advertise: ${it.message}") }
        }
    }

    private fun startDiscovery() {
        client.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        ).addOnFailureListener {
            deviceListeners.forEach { l -> l.onError("Unable to scan: ${it.message}") }
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            client.acceptConnection(endpointId, payloadCallback)
                .addOnFailureListener {
                    deviceListeners.forEach { l -> l.onError("Connection to ${info.endpointName} failed") }
                }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            connectingEndpoints.remove(endpointId)
            if (result.status.isSuccess) {
                sendHello(endpointId)
            } else {
                devices = devices.filterNot { it.endpointId == endpointId }
                notifyDevicesChanged()
                deviceListeners.forEach { l -> l.onError("Could not connect to a nearby device") }
            }
        }

        override fun onDisconnected(endpointId: String) {
            val userId = remoteUserIdByEndpoint.remove(endpointId)
            remoteNameByEndpoint.remove(endpointId)
            devices = devices.filterNot { it.endpointId == endpointId }
            notifyDevicesChanged()
            deviceListeners.forEach { l -> l.onPeerDisconnected(userId ?: endpointId) }
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (connectingEndpoints.contains(endpointId)) return
            if (devices.any { it.endpointId == endpointId }) return
            connectingEndpoints.add(endpointId)
            devices = devices + NearbyDevice(null, info.endpointName, endpointId, connected = false)
            notifyDevicesChanged()
            client.requestConnection(
                Session.current.userName ?: "Chat user",
                endpointId,
                connectionLifecycleCallback
            ).addOnFailureListener {
                connectingEndpoints.remove(endpointId)
            }
        }

        override fun onEndpointLost(endpointId: String) {
            connectingEndpoints.remove(endpointId)
            devices = devices.filterNot { it.endpointId == endpointId }
            notifyDevicesChanged()
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val bytes = payload.asBytes() ?: return
                handlePayload(endpointId, String(bytes, Charsets.UTF_8))
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    private fun sendHello(endpointId: String) {
        val hello = JSONObject()
        hello.put("type", "hello")
        hello.put("id", Session.current.userId ?: "")
        hello.put("name", Session.current.userName ?: "Chat user")
        sendToEndpoint(endpointId, hello.toString())
    }

    private fun handlePayload(endpointId: String, raw: String) {
        try {
            val obj = JSONObject(raw)
            when (obj.optString("type")) {
                "hello" -> handleHello(endpointId, obj)
                "message" -> handleIncomingMessage(obj.optJSONObject("message")?.toString())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Bad payload", e)
        }
    }

    private fun handleHello(endpointId: String, obj: JSONObject) {
        val userId = obj.optString("id")
        val name = obj.optString("name")
        remoteUserIdByEndpoint[endpointId] = userId
        remoteNameByEndpoint[endpointId] = name
        val device = NearbyDevice(userId, name, endpointId, connected = true)
        devices = devices.filterNot { it.endpointId == endpointId } + device
        notifyDevicesChanged()
        deviceListeners.forEach { l -> l.onPeerConnected(device) }
        flushPendingFor(userId)
    }

    private fun handleIncomingMessage(messageJson: String?) {
        if (messageJson == null) return
        val message = ApiClient.gson.fromJson(messageJson, Message::class.java)
            .copy(pending = false, read = false)
        MessageCache.current.saveMessage(message)
        messageListeners.forEach { it.onMessageReceived(message) }
    }

    fun sendMessage(peerUserId: String, message: Message) {
        val endpointId = remoteUserIdByEndpoint.entries
            .find { it.value == peerUserId }?.key
        if (endpointId == null) return

        val payload = JSONObject()
        payload.put("type", "message")
        payload.put("message", JSONObject(ApiClient.gson.toJson(message.copy(pending = false))))
        sendToEndpoint(endpointId, payload.toString())

        MessageCache.current.confirmSent(message)
        messageListeners.forEach { it.onMessageSent(message) }
    }

    private fun flushPendingFor(peerUserId: String) {
        MessageCache.current.outbox()
            .filter { it.toId == peerUserId }
            .forEach { sendMessage(peerUserId, it) }
    }

    private fun sendToEndpoint(endpointId: String, json: String) {
        client.sendPayload(endpointId, Payload.fromBytes(json.toByteArray(Charsets.UTF_8)))
    }

    private fun notifyDevicesChanged() {
        deviceListeners.forEach { l -> l.onDevicesChanged(devices) }
    }
}
