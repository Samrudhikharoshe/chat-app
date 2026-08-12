package com.chatapp.ui.nearby

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.chatapp.R
import com.chatapp.data.NearbyMessenger
import com.chatapp.databinding.ActivityNearbyBinding
import com.chatapp.ui.chat.ChatActivity

class NearbyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNearbyBinding
    private lateinit var adapter: NearbyDeviceAdapter

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) {
            startNearby()
        } else {
            Toast.makeText(
                this,
                getString(R.string.nearby_permission_denied),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val deviceListener = object : NearbyMessenger.DeviceListener {
        override fun onDevicesChanged(devices: List<NearbyMessenger.NearbyDevice>) {
            runOnUiThread { render(devices) }
        }

        override fun onPeerConnected(device: NearbyMessenger.NearbyDevice) {
            runOnUiThread {
                Toast.makeText(
                    this@NearbyActivity,
                    getString(R.string.nearby_peer_connected, device.name),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        override fun onPeerDisconnected(deviceId: String) = Unit

        override fun onError(message: String) {
            runOnUiThread {
                Toast.makeText(this@NearbyActivity, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNearbyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = NearbyDeviceAdapter { device ->
            startActivity(
                Intent(this, ChatActivity::class.java)
                    .putExtra(ChatActivity.EXTRA_PEER_ID, device.userId)
                    .putExtra(ChatActivity.EXTRA_PEER_NAME, device.name)
                    .putExtra(ChatActivity.EXTRA_NEARBY, true)
            )
        }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        NearbyMessenger.addDeviceListener(deviceListener)
        render(NearbyMessenger.devices)
        requestPermissionsIfNeeded()
    }

    override fun onDestroy() {
        NearbyMessenger.removeDeviceListener(deviceListener)
        NearbyMessenger.stop()
        super.onDestroy()
    }

    private fun requestPermissionsIfNeeded() {
        val needed = neededPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isEmpty()) {
            startNearby()
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun neededPermissions(): List<String> {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            perms.add(Manifest.permission.BLUETOOTH_SCAN)
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
            perms.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            perms.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
        return perms
    }

    private fun startNearby() {
        NearbyMessenger.start()
        render(NearbyMessenger.devices)
    }

    private fun render(devices: List<NearbyMessenger.NearbyDevice>) {
        adapter.submit(devices)
        binding.emptyState.visibility = if (devices.isEmpty()) View.VISIBLE else View.GONE
        binding.statusText.text = getString(
            if (devices.isEmpty()) R.string.nearby_scanning else R.string.nearby_found
        )
    }
}
