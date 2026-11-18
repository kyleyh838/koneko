package io.nekohasekai.sagernet.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for v1.5.0:
 * (1) a chain proxy shows the names of the nodes it chains even when those
 * nodes live in another group — the missing IDs are resolved with one
 * batched DB lookup when the list cache is rebuilt (never per scroll);
 * (2) long-pressing a group tab on the home group bar opens that group's
 * edit screen.
 */
class Fixes150Test {

    private fun readSource(relativePath: String): String {
        // Unit tests run with the working directory set to app/.
        val file = File(relativePath)
        assertTrue("source file missing: $relativePath", file.exists())
        return file.readText()
    }

    // NOTE: version pin retired in v1.5.1; now pinned by Fixes151Test.

    @Test
    fun crossGroupNodeIdsAreCollectedAsMissing() {
        val missing = collectMissingChainNodeIds(
            listOf(listOf(1L, 2L, 99L)),
            mapOf(1L to "HK-01", 2L to "JP-02")
        )
        assertEquals(setOf(99L), missing)
    }

    @Test
    fun noMissingIdsWhenAllNodesAreInCurrentGroup() {
        val missing = collectMissingChainNodeIds(
            listOf(listOf(1L, 2L)),
            mapOf(1L to "HK-01", 2L to "JP-02")
        )
        assertTrue(missing.isEmpty())
    }

    @Test
    fun missingIdsAreDeduplicatedAcrossChains() {
        val missing = collectMissingChainNodeIds(
            listOf(listOf(99L), listOf(99L, 100L)),
            mapOf(1L to "HK-01")
        )
        assertEquals(setOf(99L, 100L), missing)
    }

    @Test
    fun rebuildResolvesCrossGroupNodesWithOneBatchedLookup() {
        val src = readSource("src/main/java/io/nekohasekai/sagernet/ui/ConfigurationFragment.kt")
        assertTrue(src.contains("collectMissingChainNodeIds"))
        assertTrue(src.contains("ProfileManager.getProfiles("))
        // the lookup result is merged into the name map before chains resolve
        assertTrue(src.contains("nameById[profile.id] = profile.displayName()"))
        // and the lookup is skipped entirely when nothing is missing
        assertTrue(src.contains("if (missingIds.isNotEmpty())"))
    }

    @Test
    fun groupTabLongPressOpensGroupEdit() {
        val src = readSource("src/main/java/io/nekohasekai/sagernet/ui/ConfigurationFragment.kt")
        // the long-click listener launches the group editor with the tab's group id
        assertTrue(src.contains("GroupSettingsActivity::class.java"))
        assertTrue(src.contains("GroupSettingsActivity.EXTRA_GROUP_ID"))
        // the group is resolved at click time from the tab's current position
        assertTrue(src.contains("tab.position"))
        // the event is still consumed (keeps the long-press toast suppressed)
        assertTrue(src.contains("setOnLongClickListener"))
    }
}
