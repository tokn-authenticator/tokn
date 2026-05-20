package me.diamondforge.tokn.sync.wfd

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Wraps WifiP2pManager. Lifecycle:
 *   1. `register()` — call when the screen becomes active.
 *   2. issue suspending operations (discover/createGroup/connect).
 *   3. `unregister()` when the screen leaves.
 */
class WfdManager(private val context: Context) {

    private val manager: WifiP2pManager? =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private val channel: WifiP2pManager.Channel? =
        manager?.initialize(context, Looper.getMainLooper(), null)

    val isSupported: Boolean = manager != null && channel != null

    private val _peers = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    val peers: StateFlow<List<WifiP2pDevice>> = _peers.asStateFlow()

    private val _connectionInfo = MutableStateFlow<WifiP2pInfo?>(null)
    val connectionInfo: StateFlow<WifiP2pInfo?> = _connectionInfo.asStateFlow()

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            val mgr = manager ?: return
            val ch = channel ?: return
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    _enabled.value = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    if (!hasNearbyPermission()) return
                    @SuppressLint("MissingPermission")
                    mgr.requestPeers(ch) { devices: WifiP2pDeviceList ->
                        _peers.value = devices.deviceList.toList()
                    }
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    mgr.requestConnectionInfo(ch) { info -> _connectionInfo.value = info }
                }
            }
        }
    }

    private var registered = false

    fun register() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        registered = true
    }

    fun unregister() {
        if (!registered) return
        runCatching { context.unregisterReceiver(receiver) }
        registered = false
    }

    suspend fun discoverPeers(): Boolean = suspendCancellableCoroutine { cont ->
        val mgr = manager
        val ch = channel
        if (mgr == null || ch == null || !hasNearbyPermission()) {
            cont.resume(false)
            return@suspendCancellableCoroutine
        }
        @SuppressLint("MissingPermission")
        mgr.discoverPeers(ch, actionListener(cont))
    }

    suspend fun createGroup(): Boolean = suspendCancellableCoroutine { cont ->
        val mgr = manager
        val ch = channel
        if (mgr == null || ch == null || !hasNearbyPermission()) {
            cont.resume(false)
            return@suspendCancellableCoroutine
        }
        @SuppressLint("MissingPermission")
        mgr.createGroup(ch, actionListener(cont))
    }

    suspend fun removeGroup(): Boolean = suspendCancellableCoroutine { cont ->
        val mgr = manager
        val ch = channel
        if (mgr == null || ch == null) {
            cont.resume(false)
            return@suspendCancellableCoroutine
        }
        mgr.removeGroup(ch, actionListener(cont))
    }

    suspend fun connect(device: WifiP2pDevice): Boolean = suspendCancellableCoroutine { cont ->
        val mgr = manager
        val ch = channel
        if (mgr == null || ch == null || !hasNearbyPermission()) {
            cont.resume(false)
            return@suspendCancellableCoroutine
        }
        val config = WifiP2pConfig().apply { deviceAddress = device.deviceAddress }
        @SuppressLint("MissingPermission")
        mgr.connect(ch, config, actionListener(cont))
    }

    private fun actionListener(cont: kotlinx.coroutines.CancellableContinuation<Boolean>) =
        object : WifiP2pManager.ActionListener {
            override fun onSuccess() { if (cont.isActive) cont.resume(true) }
            override fun onFailure(reason: Int) { if (cont.isActive) cont.resume(false) }
        }

    private fun hasNearbyPermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.NEARBY_WIFI_DEVICES
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val DEFAULT_PORT = 8765
    }
}
