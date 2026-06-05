package me.diamondforge.tokn.security.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

object Argon2KeyDeriver {
    const val DEFAULT_MEMORY_KIB = 47_104
    const val DEFAULT_ITERATIONS = 3
    const val DEFAULT_PARALLELISM = 2
    const val KEY_LENGTH = 32

    fun derive(
        password: ByteArray,
        salt: ByteArray,
        memoryKib: Int = DEFAULT_MEMORY_KIB,
        iterations: Int = DEFAULT_ITERATIONS,
        parallelism: Int = DEFAULT_PARALLELISM,
        keyLength: Int = KEY_LENGTH,
    ): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withSalt(salt)
            .withMemoryAsKB(memoryKib)
            .withIterations(iterations)
            .withParallelism(parallelism)
            .build()
        val generator = Argon2BytesGenerator().apply { init(params) }
        val out = ByteArray(keyLength)
        generator.generateBytes(password, out)
        return out
    }
}
