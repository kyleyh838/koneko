package io.nekohasekai.sagernet.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for issue #1212 (v1.4.44): a chain proxy shows the names
 * of the nodes it chains, on the card's second line, so the user can see
 * which nodes are in use.
 *
 * The node names are resolved in the list UI layer from the already-loaded
 * in-memory profile map and cached per chain ID (no per-bind DB query).
 * The pure resolver below is the unit under test; the adapter wiring
 * (cache, binding, search) is pinned by file-parsing tests in this class.
 */
class Fixes1444Test {

    private fun readSource(relativePath: String): String {
        // Unit tests run with the working directory set to app/.
        val file = File(relativePath)
        assertTrue("source file missing: $relativePath", file.exists())
        return file.readText()
    }

    // NOTE: version pin retired in v1.5.0; now pinned by Fixes150Test.

    @Test
    fun chainDisplayNamesJoinsInOrder() {
        val result = resolveChainDisplayNames(
            listOf(1L, 2L),
            mapOf(1L to "HK-01", 2L to "JP-02")
        )
        assertEquals("HK-01 → JP-02", result)
    }

    @Test
    fun chainDisplayNamesSkipsMissing() {
        val result = resolveChainDisplayNames(
            listOf(1L, 99L, 2L),
            mapOf(1L to "HK-01", 2L to "JP-02")
        )
        assertEquals("HK-01 → JP-02", result)
    }

    @Test
    fun chainDisplayNamesEmptyWhenNoneResolve() {
        assertEquals("", resolveChainDisplayNames(listOf(99L), mapOf(1L to "HK-01")))
        assertEquals("", resolveChainDisplayNames(emptyList(), mapOf(1L to "HK-01")))
    }

    @Test
    fun adapterHasChainDisplayCache() {
        val src = readSource("src/main/java/io/nekohasekai/sagernet/ui/ConfigurationFragment.kt")
        assertTrue("adapter must have chainDisplayCache",
            src.contains("chainDisplayCache"))
        assertTrue("adapter must have rebuildChainDisplayCache",
            src.contains("rebuildChainDisplayCache"))
        assertTrue("rebuild must call resolveChainDisplayNames",
            src.contains("resolveChainDisplayNames"))
        assertTrue("adapter must have rebindChainsReferencing",
            src.contains("rebindChainsReferencing"))
    }

    @Test
    fun bindingUsesCachedChainString() {
        val src = readSource("src/main/java/io/nekohasekai/sagernet/ui/ConfigurationFragment.kt")
        // The holder is a sibling of the adapter, so onBindViewHolder resolves
        // the chain string from the cache and passes it into bind().
        assertTrue("onBindViewHolder must pass the cached chain string into bind",
            src.contains("chainDisplayCache[profile.id]") ||
                src.contains("chainDisplayCache[getItemAt(position).id]"))
        assertTrue("bind must take a chainDisplay parameter",
            src.contains("chainDisplay: String?"))
        assertTrue("bind must use chainDisplay for the address when present",
            src.contains("if (chainDisplay != null)"))
    }

    @Test
    fun searchMatchesChainNodeNames() {
        val src = readSource("src/main/java/io/nekohasekai/sagernet/ui/ConfigurationFragment.kt")
        assertTrue("filter must also match the cached chain node names",
            src.contains("chainDisplayCache[it.key]") ||
                src.contains("chainDisplayCache[it.value.id]"))
    }
}
