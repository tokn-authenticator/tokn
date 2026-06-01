package me.diamondforge.tokn.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.diamondforge.tokn.data.db.dao.OtpAccountDao
import me.diamondforge.tokn.data.db.entity.OtpAccountEntity

@Database(
    entities = [OtpAccountEntity::class],
    version = 5,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun otpAccountDao(): OtpAccountDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE otp_accounts ADD COLUMN `group` TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE otp_accounts ADD COLUMN custom_icon_blob BLOB")
                db.execSQL("ALTER TABLE otp_accounts ADD COLUMN icon_pack_id TEXT")
                db.execSQL("ALTER TABLE otp_accounts ADD COLUMN icon_pack_file TEXT")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE otp_accounts ADD COLUMN usage_count INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE otp_accounts ADD COLUMN last_used_at INTEGER NOT NULL DEFAULT 0")
            }
        }

        // As of: 20260601
        // v5 changes the semantics of the `group` column from a single
        // string to a JSON-encoded list of strings. quote() handles inner
        // quote / backslash escaping correctly so a legacy group value
        // like he"llo serialises to ["he\"llo"].
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE otp_accounts SET `group` = NULL WHERE `group` = ''")
                db.execSQL(
                    "UPDATE otp_accounts SET `group` = '[' || quote(`group`) || ']' " +
                            "WHERE `group` IS NOT NULL"
                )
            }
        }
    }
}
