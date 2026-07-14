package me.diamondforge.tokn.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import me.diamondforge.tokn.data.db.entity.OtpAccountEntity
import me.diamondforge.tokn.data.db.entity.toDomain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {

    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) = db.execSQL(CREATE_V1)
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                Unit
        }
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(callback)
            .build()
        db = FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `chain from 1 to 5 keeps the row and adds the new columns`() {
        db.execSQL(
            "INSERT INTO otp_accounts " +
                    "(issuer,accountName,secret,algorithm,digits,period,counter,type,sortOrder) " +
                    "VALUES ('ACME','alice','SEC','SHA1',6,30,0,'TOTP',0)",
        )

        AppDatabase.MIGRATION_1_2.migrate(db)
        db.execSQL("UPDATE otp_accounts SET `group` = 'Work'")
        AppDatabase.MIGRATION_2_3.migrate(db)
        AppDatabase.MIGRATION_3_4.migrate(db)
        AppDatabase.MIGRATION_4_5.migrate(db)
        AppDatabase.MIGRATION_5_6.migrate(db)

        db.query("SELECT `group`, usage_count, last_used_at, icon_pack_id, deleted_at FROM otp_accounts")
            .use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("['Work']", c.getString(0))
                assertEquals(0, c.getInt(1))
                assertEquals(0L, c.getLong(2))
                assertTrue(c.isNull(3))
                assertEquals(0L, c.getLong(4))
            }
    }

    @Test
    fun `migration 4 to 5 wraps a plain group as a json list and decodes back`() {
        toV4()
        insertWithGroup("Work")
        AppDatabase.MIGRATION_4_5.migrate(db)

        val raw = singleGroupColumn()
        assertEquals("['Work']", raw)
        assertEquals(listOf("Work"), entityWithGroups(raw).toDomain().groups)
    }

    @Test
    fun `migration 4 to 5 nulls out empty group strings`() {
        toV4()
        insertWithGroup("")
        AppDatabase.MIGRATION_4_5.migrate(db)

        db.query("SELECT `group` FROM otp_accounts").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue(c.isNull(0))
        }
    }

    @Test
    fun `migration 4 to 5 leaves null groups untouched`() {
        toV4()
        insertWithGroup(null)
        AppDatabase.MIGRATION_4_5.migrate(db)

        db.query("SELECT `group` FROM otp_accounts").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue(c.isNull(0))
        }
    }

    private fun toV4() {
        AppDatabase.MIGRATION_1_2.migrate(db)
        AppDatabase.MIGRATION_2_3.migrate(db)
        AppDatabase.MIGRATION_3_4.migrate(db)
    }

    private fun insertWithGroup(group: String?) {
        db.execSQL(
            "INSERT INTO otp_accounts " +
                    "(issuer,accountName,secret,algorithm,digits,period,counter,type,sortOrder,`group`) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?)",
            arrayOf<Any?>("i", "n", "s", "SHA1", 6, 30, 0, "TOTP", 0, group),
        )
    }

    private fun singleGroupColumn(): String? =
        db.query("SELECT `group` FROM otp_accounts").use { c ->
            c.moveToFirst()
            if (c.isNull(0)) null else c.getString(0)
        }

    private fun entityWithGroups(raw: String?) = OtpAccountEntity(
        id = 1,
        issuer = "i",
        accountName = "n",
        secret = "s",
        algorithm = "SHA1",
        digits = 6,
        period = 30,
        counter = 0,
        type = "TOTP",
        sortOrder = 0,
        groupsJson = raw,
    )

    private companion object {
        const val CREATE_V1 =
            "CREATE TABLE otp_accounts (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "issuer TEXT NOT NULL, " +
                    "accountName TEXT NOT NULL, " +
                    "secret TEXT NOT NULL, " +
                    "algorithm TEXT NOT NULL, " +
                    "digits INTEGER NOT NULL, " +
                    "period INTEGER NOT NULL, " +
                    "counter INTEGER NOT NULL, " +
                    "type TEXT NOT NULL, " +
                    "sortOrder INTEGER NOT NULL)"
    }
}
