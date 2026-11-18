package io.nekohasekai.sagernet.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for v1.5.1:
 * a chain's node-name string is never truncated — even with traffic, a
 * long chain wraps to a second line and shows in full instead of being
 * cut to 27 chars + "...". Regular (non-chain) profiles keep the
 * existing 30-char truncation.
 */
class Fixes151Test {

    private fun readSource(relativePath: String): String {
        // Unit tests run with the working directory set to app/.
        val file = File(relativePath)
        assertTrue("source file missing: $relativePath", file.exists())
        return file.readText()
    }

    @Test
    fun chainDisplayIsNotTruncated() {
        val src = readSource("src/main/java/io/nekohasekai/sagernet/ui/ConfigurationFragment.kt")
        // The 30-char truncation must only apply to non-chain profiles:
        // a chain (chainDisplay != null) keeps its full node-name string
        // so it can wrap instead of being cut off. The guard must sit in
        // the truncation condition itself (next to showTraffic), not just
        // anywhere in the file.
        assertTrue(
            src.contains("chainDisplay == null && showTraffic && address.length >= 30")
        )
    }

    @Test
    fun versionIs1_5_1() {
        // v1.5.1: bug fix only (chain names no longer truncated), so the
        // patch version goes up, not the minor version (SemVer).
        val props = readSource("../nb4a.properties")
        assertTrue(props.contains("VERSION_NAME=1.5.1"))
        assertTrue(props.contains("VERSION_CODE=90"))
    }
}
