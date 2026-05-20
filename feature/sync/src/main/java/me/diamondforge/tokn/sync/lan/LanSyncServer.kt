package me.diamondforge.tokn.sync.lan

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.diamondforge.tokn.sync.SyncProtocol
import me.diamondforge.tokn.sync.crypto.Handshake
import me.diamondforge.tokn.sync.crypto.PairingFailedException
import me.diamondforge.tokn.sync.crypto.SecureChannel
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket

class LanSyncServer(private val context: Context) {

    suspend fun sendPayload(
        serviceName: String,
        code: String,
        payload: ByteArray,
        localApp: String,
        localBuild: Long,
        onReady: suspend (port: Int) -> Unit,
    ): SendResult = withContext(Dispatchers.IO) {
        val socket = ServerSocket(0).apply { soTimeout = HANDSHAKE_TIMEOUT_MS }
        val port = socket.localPort
        val nsd = NsdDiscovery(context)
        val registration = nsd.register(serviceName, port)
        try {
            onReady(port)
            val client = socket.accept().apply {
                soTimeout = HANDSHAKE_TIMEOUT_MS
                tcpNoDelay = true
            }
            client.use { c ->
                val input = DataInputStream(c.getInputStream().buffered())
                val output = DataOutputStream(c.getOutputStream().buffered())
                val keys = try {
                    Handshake.initiator(input, output, code)
                } catch (e: PairingFailedException) {
                    return@withContext SendResult.BadCode
                }
                c.soTimeout = TRANSFER_TIMEOUT_MS
                val secure = SecureChannel(input, output, keys)
                // Sender writes hello first, then reads peer's. See SyncProtocol.
                secure.send(SyncProtocol.makeHello(localApp, localBuild))
                val peer = SyncProtocol.parseHello(secure.receive())
                if (!SyncProtocol.isCompatible(peer)) {
                    return@withContext SendResult.VersionMismatch(peer.app, peer.build)
                }
                secure.send(payload)
                SendResult.Ok
            }
        } finally {
            registration.close()
            runCatching { socket.close() }
        }
    }

    sealed class SendResult {
        data object Ok : SendResult()
        data object BadCode : SendResult()
        data class VersionMismatch(val peerApp: String, val peerBuild: Long) : SendResult()
    }

    companion object {
        private const val HANDSHAKE_TIMEOUT_MS = 60_000
        private const val TRANSFER_TIMEOUT_MS = 30_000
    }
}
