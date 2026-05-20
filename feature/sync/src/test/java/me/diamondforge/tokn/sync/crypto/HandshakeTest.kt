package me.diamondforge.tokn.sync.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class HandshakeTest {

    @Test
    fun `matching codes derive symmetric keys`() {
        val (initKeys, respKeys) = runHandshake("482931", "482931")
        // initiator's send key is what the responder receives, and vice versa
        assertArrayEquals(initKeys.sendKey, respKeys.recvKey)
        assertArrayEquals(initKeys.recvKey, respKeys.sendKey)
        // forward and reverse keys must differ within a single side
        assertFalse(initKeys.sendKey.contentEquals(initKeys.recvKey))
    }

    @Test
    fun `mismatched codes reject as pairing failure on both sides`() {
        val initiatorError = AtomicReference<Throwable?>()
        val responderError = AtomicReference<Throwable?>()
        runFailingHandshake("482931", "123456", initiatorError, responderError)

        val initThrew = initiatorError.get()
        val respThrew = responderError.get()
        assertTrue(
            "initiator should fail with PairingFailedException, got $initThrew",
            initThrew is PairingFailedException,
        )
        assertTrue(
            "responder should fail with PairingFailedException, got $respThrew",
            respThrew is PairingFailedException,
        )
    }

    private fun runHandshake(
        initiatorCode: String,
        responderCode: String,
    ): Pair<PairingKeys, PairingKeys> {
        val (initIn, initOut, respIn, respOut) = wirePipes()
        val pool = Executors.newFixedThreadPool(2)
        try {
            val initFuture = pool.submit<PairingKeys> {
                Handshake.initiator(initIn, initOut, initiatorCode)
            }
            val respFuture = pool.submit<PairingKeys> {
                Handshake.responder(respIn, respOut, responderCode)
            }
            return initFuture.get(30, TimeUnit.SECONDS) to respFuture.get(30, TimeUnit.SECONDS)
        } finally {
            pool.shutdownNow()
        }
    }

    private fun runFailingHandshake(
        initiatorCode: String,
        responderCode: String,
        initError: AtomicReference<Throwable?>,
        respError: AtomicReference<Throwable?>,
    ) {
        val (initIn, initOut, respIn, respOut) = wirePipes()
        val pool = Executors.newFixedThreadPool(2)
        try {
            val initFuture = pool.submit<PairingKeys?> {
                try {
                    Handshake.initiator(initIn, initOut, initiatorCode)
                } catch (e: Throwable) {
                    initError.set(e)
                    null
                } finally {
                    // Simulates socket close-on-exit (try-with-resources in
                    // production). Without it the peer hangs on read forever.
                    runCatching { initOut.close() }
                    runCatching { initIn.close() }
                }
            }
            val respFuture = pool.submit<PairingKeys?> {
                try {
                    Handshake.responder(respIn, respOut, responderCode)
                } catch (e: Throwable) {
                    respError.set(e)
                    null
                } finally {
                    runCatching { respOut.close() }
                    runCatching { respIn.close() }
                }
            }
            try {
                initFuture.get(30, TimeUnit.SECONDS)
                respFuture.get(30, TimeUnit.SECONDS)
            } catch (_: ExecutionException) {
                // swallowed by the inner catch — errors are captured in refs
            }
        } finally {
            pool.shutdownNow()
        }
    }

    private fun wirePipes(): PipeBundle {
        val a2b = PipedInputStream(64 * 1024)
        val a2bOut = PipedOutputStream(a2b)
        val b2a = PipedInputStream(64 * 1024)
        val b2aOut = PipedOutputStream(b2a)
        return PipeBundle(
            initIn = DataInputStream(b2a),
            initOut = DataOutputStream(a2bOut),
            respIn = DataInputStream(a2b),
            respOut = DataOutputStream(b2aOut),
        )
    }

    private data class PipeBundle(
        val initIn: DataInputStream,
        val initOut: DataOutputStream,
        val respIn: DataInputStream,
        val respOut: DataOutputStream,
    )
}
