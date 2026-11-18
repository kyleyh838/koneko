package io.nekohasekai.sagernet.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1.4.29: two protocols are removed entirely, so their BLOB columns
 * go away too. Any rows of those two types (7 and 21) are deleted
 * first: requireBean() throws on an unknown type, so a stale row
 * would crash the profile list on open. In practice those rows can
 * only be the fake test nodes from the core-upgrade verification —
 * no real node of either type could ever connect, both plugins are
 * dead.
 *
 * Manual migration, not an AutoMigration: Room cannot express "delete
 * rows, then drop columns" as an auto-migration, and a same-version
 * column deletion makes the KSP build fail until the version goes up,
 * so the bump and the migration land together.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DELETE FROM proxy_entities WHERE type IN (7, 21)")
        db.execSQL("ALTER TABLE proxy_entities DROP COLUMN trojanGoBean")
        db.execSQL("ALTER TABLE proxy_entities DROP COLUMN mieruBean")
    }
}
