package me.diamondforge.tokn.security.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class Argon2KeyDeriverTest {

    private val pw = "correct horse".toByteArray()
    private val salt = ByteArray(16) { it.toByte() }

    private fun derive(password: ByteArray, salt: ByteArray, keyLength: Int = 32) =
        Argon2KeyDeriver.derive(password, salt, memoryKib = 256, iterations = 1, parallelism = 1, keyLength = keyLength)

    @Test
    fun `same inputs produce the same key`() {
        assertArrayEquals(derive(pw, salt), derive(pw.copyOf(), salt.copyOf()))
    }

    @Test
    fun `a different salt produces a different key`() {
        val other = ByteArray(16) { (it + 1).toByte() }
        assertFalse(derive(pw, salt).contentEquals(derive(pw, other)))
    }

    @Test
    fun `a different password produces a different key`() {
        assertFalse(derive(pw, salt).contentEquals(derive("wrong".toByteArray(), salt)))
    }

    @Test
    fun `key length is honoured`() {
        assertEquals(16, derive(pw, salt, keyLength = 16).size)
        assertEquals(32, derive(pw, salt, keyLength = 32).size)
    }
}
