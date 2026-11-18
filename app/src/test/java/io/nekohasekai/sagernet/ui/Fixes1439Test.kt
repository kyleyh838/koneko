package io.nekohasekai.sagernet.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the v1.4.39 batch: block-mode reload prompt synced
 * with proxy/bypass modes. MainActivity.onPreferenceDataStoreChanged used
 * to watch only PROXY_APPS / BYPASS_MODE / INDIVIDUAL, so changes made in
 * block mode (APP_BLOCK, INDIVIDUAL_BLOCK) never showed the
 * "reload proxy service to apply changes" snackbar.
 *
 * Same file-parsing style as the other tests in this package: the
 * production code touches Android APIs, so the tests assert on the
 * source text instead of running it.
 */
class Fixes1439Test {

    private fun readSource(relativePath: String): String {
        val file = File(relativePath)
        assertTrue("source file missing: $relativePath", file.exists())
        return file.readText()
    }

    private fun listenerBody(): String {
        val src = readSource("src/main/java/io/nekohasekai/sagernet/ui/MainActivity.kt")
        val start = src.indexOf("override fun onPreferenceDataStoreChanged")
        assertTrue("onPreferenceDataStoreChanged missing", start >= 0)
        val end = src.indexOf("override fun onStart()", start)
        assertTrue("listener body end missing", end > start)
        return src.substring(start, end)
    }

    // NOTE: version pin retired in v1.4.40; now pinned by Fixes1440Test.

    @Test
    fun blockKeysWatchedByReloadListener() {
        val body = listenerBody()
        assertTrue("APP_BLOCK not watched", body.contains("Key.APP_BLOCK"))
        assertTrue("INDIVIDUAL_BLOCK not watched", body.contains("Key.INDIVIDUAL_BLOCK"))
    }

    @Test
    fun allThreeModesShareOneBranch() {
        val body = listenerBody()
        val branch = body.substringAfter("when (key)").substringBefore("-> {")
        assertTrue("PROXY_APPS missing from branch", branch.contains("Key.PROXY_APPS"))
        assertTrue("BYPASS_MODE missing from branch", branch.contains("Key.BYPASS_MODE"))
        assertTrue("INDIVIDUAL missing from branch", branch.contains("Key.INDIVIDUAL"))
        assertTrue("APP_BLOCK missing from branch", branch.contains("Key.APP_BLOCK"))
        assertTrue("INDIVIDUAL_BLOCK missing from branch", branch.contains("Key.INDIVIDUAL_BLOCK"))
    }

    @Test
    fun reloadSnackbarWiringIntact() {
        val body = listenerBody()
        assertTrue("need_reload snackbar missing", body.contains("R.string.need_reload"))
        assertTrue("apply action missing", body.contains("R.string.apply"))
        assertTrue("reloadService call missing", body.contains("SagerNet.reloadService()"))
        assertTrue("canStop guard missing", body.contains("DataStore.serviceState.canStop"))
    }
}
