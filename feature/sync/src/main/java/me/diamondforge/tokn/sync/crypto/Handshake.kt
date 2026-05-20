package me.diamondforge.tokn.sync.crypto

import org.bouncycastle.crypto.CryptoException
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.security.SecureRandom

object Handshake {
    fun newPairingCode(): String {
        val r = SecureRandom()
        val digits = (1..6).map { r.nextInt(10) }
        return digits.joinToString("")
    }

    /**
     * Note on ordering: BC's [org.bouncycastle.crypto.agreement.jpake.JPAKEParticipant]
     * enforces a strict state machine. For round 3 specifically,
     * `createRound3PayloadToSend()` requires state < `STATE_ROUND_3_CREATED` and
     * advances it; `validateRound3PayloadReceived()` then advances to
     * `STATE_ROUND_3_VALIDATED`. Once validate runs, create can no longer be
     * called. So on both sides we *create* our round-3 payload first (just
     * holding it in memory) and only then validate the peer's — regardless of
     * which side talks first on the wire.
     */
    fun initiator(input: DataInputStream, output: DataOutputStream, code: String): PairingKeys {
        val p = Pairing(Role.INITIATOR, code)

        sendText(output, p.round1Out())
        p.round1In(readText(input))

        sendText(output, p.round2Out())
        p.round2In(readText(input))

        p.computeKeyingMaterial()

        val ourR3 = p.round3Out()
        sendText(output, ourR3)
        try {
            p.round3In(readText(input))
        } catch (e: CryptoException) {
            throw PairingFailedException("Pairing code did not match")
        } catch (e: EOFException) {
            throw PairingFailedException("Peer closed before pairing completed")
        } catch (e: IOException) {
            throw PairingFailedException("Connection lost during pairing")
        }
        return p.deriveKeys()
    }

    fun responder(input: DataInputStream, output: DataOutputStream, code: String): PairingKeys {
        val p = Pairing(Role.RESPONDER, code)

        val ourR1 = p.round1Out()
        p.round1In(readText(input))
        sendText(output, ourR1)

        val ourR2 = p.round2Out()
        p.round2In(readText(input))
        sendText(output, ourR2)

        p.computeKeyingMaterial()

        val ourR3 = p.round3Out()
        try {
            p.round3In(readText(input))
        } catch (e: CryptoException) {
            throw PairingFailedException("Pairing code did not match")
        } catch (e: EOFException) {
            throw PairingFailedException("Peer closed before pairing completed")
        } catch (e: IOException) {
            throw PairingFailedException("Connection lost during pairing")
        }
        sendText(output, ourR3)
        return p.deriveKeys()
    }

    private fun sendText(out: DataOutputStream, s: String) {
        val bytes = s.toByteArray(Charsets.UTF_8)
        out.writeInt(bytes.size)
        out.write(bytes)
        out.flush()
    }

    private fun readText(input: DataInputStream): String {
        val len = input.readInt()
        require(len in 1..1_048_576) { "handshake frame too large: $len" }
        val buf = ByteArray(len).also { input.readFully(it) }
        return String(buf, Charsets.UTF_8)
    }
}
