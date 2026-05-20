package me.diamondforge.tokn.sync.wfd

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.diamondforge.tokn.sync.SyncProtocol
import me.diamondforge.tokn.sync.crypto.Handshake
import me.diamondforge.tokn.sync.crypto.PairingFailedException
import me.diamondforge.tokn.sync.crypto.SecureChannel
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

object WfdSyncTransport {

    sealed class SendResult {
        data object Ok : SendResult()
        data object BadCode : SendResult()
        data class VersionMismatch(val peerApp: String, val peerBuild: Long) : SendResult()
    }

    sealed class ReceiveResult {
        data class Ok(val payload: ByteArray) : ReceiveResult()
        data object BadCode : ReceiveResult()
        data class VersionMismatch(val peerApp: String, val peerBuild: Long) : ReceiveResult()
    }

    suspend fun sendOverWfd(
        code: String,
        payload: ByteArray,
        localApp: String,
        localBuild: Long,
        port: Int = WfdManager.DEFAULT_PORT,
    ): SendResult = withContext(Dispatchers.IO) {
        ServerSocket(port).use { server ->
            server.soTimeout = HANDSHAKE_TIMEOUT_MS
            val client = server.accept().apply {
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
                secure.send(SyncProtocol.makeHello(localApp, localBuild))
                val peer = SyncProtocol.parseHello(secure.receive())
                if (!SyncProtocol.isCompatible(peer)) {
                    return@withContext SendResult.VersionMismatch(peer.app, peer.build)
                }
                secure.send(payload)
                SendResult.Ok
            }
        }
    }

    suspend fun receiveOverWfd(
        groupOwner: InetAddress,
        code: String,
        localApp: String,
        localBuild: Long,
        port: Int = WfdManager.DEFAULT_PORT,
    ): ReceiveResult = withContext(Dispatchers.IO) {
        val socket = Socket().apply {
            soTimeout = HANDSHAKE_TIMEOUT_MS
            tcpNoDelay = true
        }
        try {
            socket.connect(InetSocketAddress(groupOwner, port), CONNECT_TIMEOUT_MS)
            val input = DataInputStream(socket.getInputStream().buffered())
            val output = DataOutputStream(socket.getOutputStream().buffered())
            val keys = try {
                Handshake.responder(input, output, code)
            } catch (e: PairingFailedException) {
                return@withContext ReceiveResult.BadCode
            }
            socket.soTimeout = TRANSFER_TIMEOUT_MS
            val secure = SecureChannel(input, output, keys)
            val peer = SyncProtocol.parseHello(secure.receive())
            if (!SyncProtocol.isCompatible(peer)) {
                return@withContext ReceiveResult.VersionMismatch(peer.app, peer.build)
            }
            secure.send(SyncProtocol.makeHello(localApp, localBuild))
            ReceiveResult.Ok(secure.receive())
        } finally {
            runCatching { socket.close() }
        }
    }

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val HANDSHAKE_TIMEOUT_MS = 60_000
    private const val TRANSFER_TIMEOUT_MS = 30_000
}
