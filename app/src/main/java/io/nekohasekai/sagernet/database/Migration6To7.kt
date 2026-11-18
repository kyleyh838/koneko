package io.nekohasekai.sagernet.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1.4.28's step, manual since v1.4.29: the three protocols added in
 * 1.4.28 (Snell / OpenVPN / OpenConnect) add three nullable BLOB
 * columns to the profile table. It used to be an AutoMigration, but
 * Room validates every AutoMigration against the CURRENT entity
 * schema — and the current schema no longer has the two removed
 * columns, so the 6 -> 7 auto-migration fails KSP demanding a
 * @DeleteColumn spec for a deletion that belongs to 7 -> 8. A manual
 * migration does the same three ADD COLUMNs without that check. The
 * new columns are nullable, so every existing node survives.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE proxy_entities ADD COLUMN snellBean BLOB")
        db.execSQL("ALTER TABLE proxy_entities ADD COLUMN openVPNBean BLOB")
        db.execSQL("ALTER TABLE proxy_entities ADD COLUMN openConnectBean BLOB")
    }
}
