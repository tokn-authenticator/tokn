package me.diamondforge.tokn.sync.crypto

import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class SecureChannel(
    private val input: DataInputStream,
    private val output: DataOutputStream,
    keys: PairingKeys,
) {
    private val sendKey = SecretKeySpec(keys.sendKey, "AES")
    private val recvKey = SecretKeySpec(keys.recvKey, "AES")
    private val random = SecureRandom()

    fun send(plaintext: ByteArray) {
        val nonce = ByteArray(NONCE_LEN).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, sendKey, GCMParameterSpec(GCM_TAG_BITS, nonce))
        val ct = cipher.doFinal(plaintext)
        output.writeInt(NONCE_LEN + ct.size)
        output.write(nonce)
        output.write(ct)
        output.flush()
    }

    fun receive(): ByteArray {
        val len = input.readInt()
        require(len in (NONCE_LEN + 1)..MAX_FRAME_SIZE) { "bad frame size $len" }
        val nonce = ByteArray(NONCE_LEN).also { input.readFully(it) }
        val ct = ByteArray(len - NONCE_LEN).also { input.readFully(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, recvKey, GCMParameterSpec(GCM_TAG_BITS, nonce))
        return cipher.doFinal(ct)
    }

    companion object {
        private const val NONCE_LEN = 12
        private const val GCM_TAG_BITS = 128
        const val MAX_FRAME_SIZE = 16 * 1024 * 1024
    }
}
