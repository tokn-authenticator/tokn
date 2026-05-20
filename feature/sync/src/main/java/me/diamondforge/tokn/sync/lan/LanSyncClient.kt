package me.diamondforge.tokn.sync.lan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.diamondforge.tokn.sync.SyncProtocol
import me.diamondforge.tokn.sync.crypto.Handshake
import me.diamondforge.tokn.sync.crypto.PairingFailedException
import me.diamondforge.tokn.sync.crypto.SecureChannel
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket

class LanSyncClient {

    suspend fun receivePayload(
        peer: DiscoveredPeer,
        code: String,
        localApp: String,
        localBuild: Long,
    ): ReceiveResult = withContext(Dispatchers.IO) {
        val socket = Socket().apply {
            soTimeout = HANDSHAKE_TIMEOUT_MS
            tcpNoDelay = true
        }
        try {
            socket.connect(InetSocketAddress(peer.host, peer.port), CONNECT_TIMEOUT_MS)
            val input = DataInputStream(socket.getInputStream().buffered())
            val output = DataOutputStream(socket.getOutputStream().buffered())
            val keys = try {
                Handshake.responder(input, output, code)
            } catch (e: PairingFailedException) {
                return@withContext ReceiveResult.BadCode
            }
            socket.soTimeout = TRANSFER_TIMEOUT_MS
            val secure = SecureChannel(input, output, keys)
            // Receiver reads peer's hello first, then sends back its own.
            val peerHello = SyncProtocol.parseHello(secure.receive())
            if (!SyncProtocol.isCompatible(peerHello)) {
                return@withContext ReceiveResult.VersionMismatch(peerHello.app, peerHello.build)
            }
            secure.send(SyncProtocol.makeHello(localApp, localBuild))
            ReceiveResult.Ok(secure.receive())
        } finally {
            runCatching { socket.close() }
        }
    }

    sealed class ReceiveResult {
        data class Ok(val payload: ByteArray) : ReceiveResult()
        data object BadCode : ReceiveResult()
        data class VersionMismatch(val peerApp: String, val peerBuild: Long) : ReceiveResult()
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val HANDSHAKE_TIMEOUT_MS = 60_000
        private const val TRANSFER_TIMEOUT_MS = 30_000
    }
}
