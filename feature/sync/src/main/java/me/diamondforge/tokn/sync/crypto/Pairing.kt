package me.diamondforge.tokn.sync.crypto

import org.bouncycastle.crypto.agreement.jpake.JPAKEParticipant
import org.bouncycastle.crypto.agreement.jpake.JPAKEPrimeOrderGroups
import org.bouncycastle.crypto.agreement.jpake.JPAKERound1Payload
import org.bouncycastle.crypto.agreement.jpake.JPAKERound2Payload
import org.bouncycastle.crypto.agreement.jpake.JPAKERound3Payload
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom

enum class Role(val participantId: String) {
    INITIATOR("tokn-sender"),
    RESPONDER("tokn-receiver"),
}

data class PairingKeys(
    val sendKey: ByteArray,
    val recvKey: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is PairingKeys && sendKey.contentEquals(other.sendKey) && recvKey.contentEquals(other.recvKey)
    override fun hashCode(): Int = sendKey.contentHashCode() * 31 + recvKey.contentHashCode()
}

class Pairing(private val role: Role, code: String) {
    private val participant = JPAKEParticipant(
        role.participantId,
        code.toCharArray(),
        JPAKEPrimeOrderGroups.NIST_3072,
        SHA256Digest(),
        SecureRandom(),
    )
    private var keyingMaterial: BigInteger? = null

    fun round1Out(): String = encodeRound1(participant.createRound1PayloadToSend())
    fun round1In(json: String) = participant.validateRound1PayloadReceived(decodeRound1(json))

    fun round2Out(): String = encodeRound2(participant.createRound2PayloadToSend())
    fun round2In(json: String) = participant.validateRound2PayloadReceived(decodeRound2(json))

    fun computeKeyingMaterial() {
        keyingMaterial = participant.calculateKeyingMaterial()
    }

    fun round3Out(): String {
        val km = keyingMaterial ?: error("computeKeyingMaterial() first")
        return encodeRound3(participant.createRound3PayloadToSend(km))
    }

    fun round3In(json: String) {
        val km = keyingMaterial ?: error("computeKeyingMaterial() first")
        participant.validateRound3PayloadReceived(decodeRound3(json), km)
    }

    fun deriveKeys(): PairingKeys {
        val km = keyingMaterial ?: error("computeKeyingMaterial() first")
        val ikm = MessageDigest.getInstance("SHA-256").digest(km.toByteArray())
        val a2b = hkdf(ikm, "tokn-sync v1 initiator-to-responder")
        val b2a = hkdf(ikm, "tokn-sync v1 responder-to-initiator")
        return when (role) {
            Role.INITIATOR -> PairingKeys(sendKey = a2b, recvKey = b2a)
            Role.RESPONDER -> PairingKeys(sendKey = b2a, recvKey = a2b)
        }
    }

    private fun hkdf(ikm: ByteArray, info: String): ByteArray {
        val out = ByteArray(32)
        val gen = HKDFBytesGenerator(SHA256Digest())
        gen.init(HKDFParameters(ikm, ByteArray(0), info.toByteArray(Charsets.UTF_8)))
        gen.generateBytes(out, 0, 32)
        return out
    }

    private fun encodeRound1(r: JPAKERound1Payload) = JSONObject().apply {
        put("pid", r.participantId)
        put("gx1", r.gx1.toString(16))
        put("gx2", r.gx2.toString(16))
        put("zp1", JSONArray(r.knowledgeProofForX1.map { it.toString(16) }))
        put("zp2", JSONArray(r.knowledgeProofForX2.map { it.toString(16) }))
    }.toString()

    private fun decodeRound1(json: String): JPAKERound1Payload {
        val o = JSONObject(json)
        val zp1 = o.getJSONArray("zp1")
        val zp2 = o.getJSONArray("zp2")
        return JPAKERound1Payload(
            o.getString("pid"),
            BigInteger(o.getString("gx1"), 16),
            BigInteger(o.getString("gx2"), 16),
            arrayOf(BigInteger(zp1.getString(0), 16), BigInteger(zp1.getString(1), 16)),
            arrayOf(BigInteger(zp2.getString(0), 16), BigInteger(zp2.getString(1), 16)),
        )
    }

    private fun encodeRound2(r: JPAKERound2Payload) = JSONObject().apply {
        put("pid", r.participantId)
        put("a", r.a.toString(16))
        put("zp", JSONArray(r.knowledgeProofForX2s.map { it.toString(16) }))
    }.toString()

    private fun decodeRound2(json: String): JPAKERound2Payload {
        val o = JSONObject(json)
        val zp = o.getJSONArray("zp")
        return JPAKERound2Payload(
            o.getString("pid"),
            BigInteger(o.getString("a"), 16),
            arrayOf(BigInteger(zp.getString(0), 16), BigInteger(zp.getString(1), 16)),
        )
    }

    private fun encodeRound3(r: JPAKERound3Payload) = JSONObject().apply {
        put("pid", r.participantId)
        put("mac", r.macTag.toString(16))
    }.toString()

    private fun decodeRound3(json: String): JPAKERound3Payload {
        val o = JSONObject(json)
        return JPAKERound3Payload(o.getString("pid"), BigInteger(o.getString("mac"), 16))
    }
}

class PairingFailedException(message: String) : Exception(message)
