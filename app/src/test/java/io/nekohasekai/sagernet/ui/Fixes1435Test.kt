package io.nekohasekai.sagernet.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the v1.4.35 batch: TCPing display — skipped
 * nodes (TUIC/Hysteria/WireGuard/OpenVPN/OpenConnect/custom JSON
 * configs) are listed in the dialog and counted in the progress total,
 * and an empty group toasts instead of flashing an empty dialog.
 *
 * Same file-parsing style as the other tests in this package: the
 * production code touches Android APIs, so the tests assert on the
 * source text instead of running it.
 */
class Fixes1435Test {

    private fun readSource(relativePath: String): String {
        val file = File(relativePath)
        assertTrue("source file missing: $relativePath", file.exists())
        return file.readText()
    }

    // NOTE: the `version is 1_4_35` pin lived here in v1.4.35; it was
    // removed in v1.4.36 — the current version is pinned by
    // Fixes1436Test (`version is 1_4_36`) instead.

    @Test
    fun `tcping ux strings exist in all locales`() {
        for (name in listOf(
            "connection_test_tcp_ping_unsupported",
            "connection_test_icmp_ping_unsupported",
            "connection_test_no_testable_nodes_tcp",
            "connection_test_no_testable_nodes_icmp",
            "connection_test_empty_group"
        )) {
            for (path in listOf(
                "src/main/res/values/strings.xml",
                "src/main/res/values-zh-rCN/strings.xml",
                "src/main/res/values-ru/strings.xml"
            )) {
                val xml = readSource(path)
                assertTrue("$name missing in $path", xml.contains("name=\"$name\""))
            }
        }
    }

    @Test
    fun `skipped profiles are listed in the dialog`() {
        val config = readSource("src/main/java/io/nekohasekai/sagernet/ui/ConfigurationFragment.kt")
        val dialog = config.substringAfter("inner class TestDialog").substringBefore("fun pingTest(")
        assertTrue("no skippedText field", dialog.contains("val skippedText = SpannableStringBuilder()"))
        assertTrue("no showSkipped", dialog.contains("fun showSkipped(profile: ProxyEntity, unsupportedLabel: String)"))
        // Bound to the showSkipped body only (it closes at the first "\n    }").
        val skipped = dialog.substringAfter("fun showSkipped(").substringBefore("\n    }")
        assertTrue("showSkipped does not advance progress", skipped.contains("finishedN.addAndGet(1)"))
        assertTrue("showSkipped does not update notification", skipped.contains("notification?.updateNotification("))
        assertTrue("update does not keep skipped lines", dialog.contains("SpannableStringBuilder(skippedText).append(text)"))
    }

    @Test
    fun `skipped profiles are never persisted`() {
        val config = readSource("src/main/java/io/nekohasekai/sagernet/ui/ConfigurationFragment.kt")
        val dialog = config.substringAfter("inner class TestDialog").substringBefore("fun pingTest(")
        // Bound to the showSkipped body only: the function closes at the
        // first "\n    }" after its start (nested blocks close deeper).
        val skipped = dialog.substringAfter("fun showSkipped(").substringBefore("\n    }")
        assertTrue("showSkipped touches results", !skipped.contains("results.add"))
        assertTrue("showSkipped touches profile status", !skipped.contains("profile.status"))
        assertTrue("showSkipped touches profile ping", !skipped.contains("profile.ping"))
        assertTrue("showSkipped touches profile error", !skipped.contains("profile.error"))
    }

    @Test
    fun `skipped line uses secondary color and unsupported label`() {
        val config = readSource("src/main/java/io/nekohasekai/sagernet/ui/ConfigurationFragment.kt")
        val dialog = config.substringAfter("inner class TestDialog").substringBefore("fun pingTest(")
        val skipped = dialog.substringAfter("fun showSkipped(").substringBefore("\n    }")
        assertTrue("no secondary color", skipped.contains("android.R.attr.textColorSecondary"))
        assertTrue("no unsupported label param used", skipped.contains("unsupportedLabel"))
        val layout = readSource("src/main/res/layout/layout_progress_list.xml")
        assertTrue("nowTesting has no max height", layout.contains("android:maxHeight=\"320dp\""))
        assertTrue("nowTesting has no scrollbars", layout.contains("android:scrollbars=\"vertical\""))
        assertTrue("no movement method", dialog.contains("ScrollingMovementMethod()"))
    }

    @Test
    fun `progress counts all profiles`() {
        val config = readSource("src/main/java/io/nekohasekai/sagernet/ui/ConfigurationFragment.kt")
        val ping = config.substringAfter("fun pingTest(").substringBefore("fun urlTest()")
        assertTrue("no partition split", ping.contains("val (testableProfiles, skippedProfiles) = allProfiles.partition"))
        assertTrue("proxyN is not the total", ping.contains("test.proxyN = testableProfiles.size + skippedProfiles.size"))
        assertTrue("skipped not rendered", ping.contains("skippedProfiles.forEach { test.showSkipped(it, unsupportedLabel) }"))
        assertTrue("workers do not use testable only", ping.contains("ConcurrentLinkedQueue(testableProfiles)"))
    }

    @Test
    fun `empty group toasts instead of showing dialog`() {
        val config = readSource("src/main/java/io/nekohasekai/sagernet/ui/ConfigurationFragment.kt")
        val ping = config.substringAfter("fun pingTest(").substringBefore("fun urlTest()")
        val emptyCheck = ping.indexOf("testableProfiles.isEmpty()")
        val show = ping.indexOf("test.builder.show()")
        assertTrue("no empty check", emptyCheck >= 0)
        assertTrue("dialog shown before the empty check", show > emptyCheck)
        val branch = ping.substring(emptyCheck, ping.indexOf("return@runOnLifecycleDispatcher", emptyCheck))
        assertTrue("empty branch does not toast", branch.contains("snackbar("))
        assertTrue("empty branch does not reset runningTest", branch.contains("DataStore.runningTest = false"))
        assertTrue("dialog shown from wrong thread", ping.contains("onMainDispatcher {\n            dialog = test.builder.show()"))
    }

    @Test
    fun `cancel tolerates a dialog that was never shown`() {
        val config = readSource("src/main/java/io/nekohasekai/sagernet/ui/ConfigurationFragment.kt")
        val ping = config.substringAfter("fun pingTest(").substringBefore("fun urlTest()")
        assertTrue("dialog is not nullable", ping.contains("var dialog: AlertDialog? = null"))
        assertTrue("cancel does not guard null dialog", ping.contains("dialog?.let { dialog ->"))
    }

    @Test
    fun `urlTest is untouched`() {
        val config = readSource("src/main/java/io/nekohasekai/sagernet/ui/ConfigurationFragment.kt")
        val url = config.substringAfter("fun urlTest()").substringBefore("inner class GroupPagerAdapter")
        assertTrue("urlTest gained a canTCPing filter", !url.contains("canTCPing"))
        assertTrue("urlTest gained a canICMPing filter", !url.contains("canICMPing"))
        assertTrue("urlTest gained showSkipped", !url.contains("showSkipped"))
        assertTrue("urlTest gained skippedProfiles", !url.contains("skippedProfiles"))
    }
}
