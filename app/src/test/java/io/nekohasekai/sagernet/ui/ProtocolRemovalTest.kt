package io.nekohasekai.sagernet.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test for removing the trojan-go and mieru protocols
 * (v1.4.29).
 *
 * Both protocols only ever ran through external plugin binaries, and
 * both plugin routes are dead: the trojan-go plugin has no download
 * anywhere (Play listing gone, F-Droid 404, no releases, docs silent)
 * and the mieru plugin is stuck at v2.2.0 from 2024-01-09 while
 * upstream mieru is at v3.39.0. So the protocols go away entirely:
 * no type constants, no beans, no parsers, no settings pages, no
 * plugin entries, no database columns.
 *
 * These tests read the real source files, so they fail while any
 * reference remains. Historical files are out of scope on purpose:
 * Room schema snapshots (app/schemas) and the upgrade/batch records
 * keep the old column names as history. So is Migration7To8.kt: the
 * migration has to name the columns it drops, so its exact SQL is
 * pinned by the database test below instead of this scan.
 */
class ProtocolRemovalTest {

    private val removedReferences = Regex("trojan[-_]?go|mieru", RegexOption.IGNORE_CASE)

    private fun readSource(relativePath: String): String {
        val file = File(relativePath)
        assertTrue("source file missing: $relativePath", file.exists())
        return file.readText()
    }

    @Test
    fun `no trojan-go or mieru references remain in app source`() {
        val root = File("src/main")
        assertTrue("src/main missing", root.exists())
        val hits = root.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java" || it.extension == "xml") }
            .filter { it.name != "Migration7To8.kt" }
            .filter { removedReferences.containsMatchIn(it.readText()) }
            .map { it.path }
            .toList()
        assertTrue("trojan-go/mieru references remain in: $hits", hits.isEmpty())
    }

    @Test
    fun `database is version 8 with manual migrations from 6 and 7`() {
        val db = readSource("src/main/java/io/nekohasekai/sagernet/database/SagerDatabase.kt")
        assertTrue("Database version is not 8", db.contains("version = 8"))
        assertTrue("No MIGRATION_6_7 registered", db.contains("MIGRATION_6_7"))
        assertTrue("No MIGRATION_7_8 registered", db.contains("MIGRATION_7_8"))
        val migration67 = readSource("src/main/java/io/nekohasekai/sagernet/database/Migration6To7.kt")
        assertTrue("Migration is not Migration(6, 7)", migration67.contains("Migration(6, 7)"))
        assertTrue("Migration 6->7 does not add snellBean", migration67.contains("ADD COLUMN snellBean BLOB"))
        assertTrue("Migration 6->7 does not add openVPNBean", migration67.contains("ADD COLUMN openVPNBean BLOB"))
        assertTrue("Migration 6->7 does not add openConnectBean", migration67.contains("ADD COLUMN openConnectBean BLOB"))
        val migration = readSource("src/main/java/io/nekohasekai/sagernet/database/Migration7To8.kt")
        assertTrue("Migration is not Migration(7, 8)", migration.contains("Migration(7, 8)"))
        assertTrue(
            "Migration does not delete stale trojan-go/mieru rows",
            migration.contains("DELETE FROM proxy_entities") && migration.contains("IN (7, 21)")
        )
        assertTrue("Migration does not drop trojanGoBean", migration.contains("DROP COLUMN trojanGoBean"))
        assertTrue("Migration does not drop mieruBean", migration.contains("DROP COLUMN mieruBean"))
    }
}
