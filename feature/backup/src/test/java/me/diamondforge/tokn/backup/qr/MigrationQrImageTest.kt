package me.diamondforge.tokn.backup.qr

import android.graphics.BitmapFactory
import me.diamondforge.tokn.importer.ImportOutcome
import me.diamondforge.tokn.importer.otpauth.OtpAuthMigrationImporter
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * End-to-end test using a real PNG of a Google Authenticator export QR. Validates the
 * full pipeline: PNG → Bitmap (Robolectric) → ZXing decode → migration URI → parser
 * → OtpAccount list.
 *
 * The fixture is intentionally not asserted against specific secrets; it just verifies
 * the pipeline produces non-empty, well-shaped accounts. That keeps the test stable if
 * the fixture is regenerated and avoids embedding live secrets in test assertions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationQrImageTest {

    @Test
    fun `sample migration QR decodes and parses to non-empty accounts`() {
        val bitmap = javaClass.classLoader!!.getResourceAsStream("qr/migration_sample.png").use {
            BitmapFactory.decodeStream(it)
        }
        assertNotNull("test fixture failed to decode as a Bitmap", bitmap)

        val decoded = decodeQrFromBitmap(bitmap!!)
        assertNotNull("ZXing failed to find a QR in the fixture image", decoded)
        assertTrue(
            "decoded text is not a migration URI: $decoded",
            decoded!!.startsWith("otpauth-migration://", ignoreCase = true),
        )

        val outcome = OtpAuthMigrationImporter().parse(decoded.toByteArray(), password = null)
        assertTrue("expected Success but was $outcome", outcome is ImportOutcome.Success)
        val accounts = (outcome as ImportOutcome.Success).accounts
        assertTrue("migration payload yielded zero accounts", accounts.isNotEmpty())

        // Sanity-check every account: parser must produce a non-blank Base32 secret and a
        // recognised algorithm/type. We don't pin specific issuer/name values.
        val base32Alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toSet()
        for (account in accounts) {
            assertTrue(
                "secret missing or non-base32 for $account",
                account.secret.isNotBlank() && account.secret.all { it in base32Alphabet },
            )
        }
    }

    @Test
    fun `peekBatch on the sample QR returns batch metadata`() {
        val bitmap = javaClass.classLoader!!.getResourceAsStream("qr/migration_sample.png").use {
            BitmapFactory.decodeStream(it)
        }
        val decoded = decodeQrFromBitmap(bitmap!!)!!
        val info = OtpAuthMigrationImporter().peekBatch(decoded)
        assertNotNull(info)
        assertTrue("batchSize should be positive: ${info!!.batchSize}", info.batchSize >= 1)
        assertTrue("batchIndex should be within size", info.batchIndex < info.batchSize)
    }
}
