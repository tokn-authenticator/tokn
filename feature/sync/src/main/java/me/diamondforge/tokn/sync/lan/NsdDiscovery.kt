package me.diamondforge.tokn.sync.lan

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.InetAddress
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

const val SERVICE_TYPE = "_tokn-sync._tcp."

data class DiscoveredPeer(
    val name: String,
    val host: InetAddress,
    val port: Int,
)

class NsdDiscovery(context: Context) {
    private val manager = context.applicationContext
        .getSystemService(Context.NSD_SERVICE) as NsdManager

    /**
     * Register the service for the sender side. Returns a handle that must be
     * unregistered when done.
     */
    suspend fun register(serviceName: String, port: Int): Registration =
        suspendCancellableCoroutine { cont ->
            val info = NsdServiceInfo().apply {
                this.serviceName = serviceName
                this.serviceType = SERVICE_TYPE
                this.port = port
            }
            val listener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                    cont.resume(Registration(manager, this))
                }
                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    cont.resumeWithException(IllegalStateException("NSD registration failed: $errorCode"))
                }
                override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
            }
            manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
            cont.invokeOnCancellation {
                runCatching { manager.unregisterService(listener) }
            }
        }

    class Registration internal constructor(
        private val manager: NsdManager,
        private val listener: NsdManager.RegistrationListener,
    ) {
        fun close() {
            runCatching { manager.unregisterService(listener) }
        }
    }

    /**
     * Browse for sender services on the local network. Emits the current
     * snapshot of peers each time it changes.
     */
    fun browse(): Flow<List<DiscoveredPeer>> = callbackFlow {
        val peers = mutableMapOf<String, DiscoveredPeer>()
        val pendingResolves = ArrayDeque<NsdServiceInfo>()
        var resolving = false

        fun emit() {
            trySend(peers.values.toList())
        }

        fun resolveNext() {
            if (resolving) return
            val next = pendingResolves.removeFirstOrNull() ?: return
            resolving = true
            manager.resolveService(next, object : NsdManager.ResolveListener {
                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val host = serviceInfo.host
                    if (host != null) {
                        peers[serviceInfo.serviceName] = DiscoveredPeer(
                            name = serviceInfo.serviceName,
                            host = host,
                            port = serviceInfo.port,
                        )
                        emit()
                    }
                    resolving = false
                    resolveNext()
                }
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    resolving = false
                    resolveNext()
                }
            })
        }

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                close(IllegalStateException("Discovery start failed: $errorCode"))
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType.trimEnd('.') != SERVICE_TYPE.trimEnd('.')) return
                pendingResolves.addLast(serviceInfo)
                resolveNext()
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                peers.remove(serviceInfo.serviceName)
                emit()
            }
        }

        manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        awaitClose {
            runCatching { manager.stopServiceDiscovery(discoveryListener) }
        }
    }
}
