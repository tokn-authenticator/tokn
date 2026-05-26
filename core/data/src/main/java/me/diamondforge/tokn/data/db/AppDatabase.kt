package me.diamondforge.tokn.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.diamondforge.tokn.data.db.dao.OtpAccountDao
import me.diamondforge.tokn.data.db.entity.OtpAccountEntity

@Database(
    entities = [OtpAccountEntity::class],
    version = 3,
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
    }
}
