package io.nekohasekai.sagernet.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the v1.4.33 batch (upstream #1181, #1142, #1090,
 * #963, #954/#1120/#1141/#918).
 *
 * Same file-parsing style as the other tests in this package: the
 * production code touches Android APIs, so the tests assert on the
 * source text instead of running it.
 */
class Fixes1433Test {

    private fun readSource(relativePath: String): String {
        val file = File(relativePath)
        assertTrue("source file missing: $relativePath", file.exists())
        return file.readText()
    }

    // NOTE: the `version is 1_4_33` pin lived here in v1.4.33; it was
    // removed in v1.4.34 — the current version is pinned by
    // Fixes1434Test (`version is 1_4_34`) instead.
    @Test
    fun `subscription update tries direct before proxy`() {
        val updater = readSource("src/main/java/io/nekohasekai/sagernet/group/RawUpdater.kt")
        assertTrue(
            "no direct-first subscription attempt",
            updater.contains("executeSubscriptionRequest(subscription, useProxy = false)")
        )
        assertTrue(
            "no proxy fallback attempt",
            updater.contains("executeSubscriptionRequest(subscription, useProxy = true)")
        )
        assertTrue(
            "direct attempt does not come before proxy attempt",
            updater.indexOf("useProxy = false") < updater.indexOf("useProxy = true")
        )
        assertTrue(
            "proxy fallback is not gated on the service being connected",
            updater.contains("DataStore.serviceState != BaseService.State.Connected")
        )
        assertTrue(
            "RawUpdater does not import BaseService",
            updater.contains("import io.nekohasekai.sagernet.bg.BaseService")
        )
    }

    @Test
    fun `wake reset listens without permission`() {
        val service = readSource("src/main/java/io/nekohasekai/sagernet/bg/BaseService.kt")
        assertTrue(
            "no SCREEN_ON listener",
            service.contains("Intent.ACTION_SCREEN_ON")
        )
        assertTrue(
            "no USER_PRESENT listener",
            service.contains("Intent.ACTION_USER_PRESENT")
        )
        assertTrue(
            "no separate system receiver",
            service.contains("val systemReceiver = broadcastReceiver")
        )
        assertTrue(
            "wake actions do not reset connections",
            service.contains("Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {")
        )
        assertTrue(
            "system receiver has no registration flag",
            service.contains("var systemReceiverRegistered = false")
        )
        assertTrue(
            "system receiver is not registered without a broadcast permission",
            service.contains(
                "registerReceiver(\n" +
                    "                        data.systemReceiver,\n" +
                    "                        systemFilter,\n" +
                    "                        null,\n" +
                    "                        null,\n" +
                    "                        Context.RECEIVER_EXPORTED"
            )
        )
        assertTrue(
            "system receiver is never unregistered",
            service.contains("unregisterReceiver(data.systemReceiver)")
        )
    }

    @Test
    fun `tuic password-only userinfo is not used as uuid`() {
        val fmt = readSource("src/main/java/io/nekohasekai/sagernet/fmt/tuic/TuicFmt.kt")
        assertTrue(
            "parseTuic does not check whether the userinfo is a UUID",
            fmt.contains("UUID.fromString")
        )
        assertTrue(
            "non-UUID userinfo is not treated as the password",
            fmt.contains("token = rawUser")
        )
        assertTrue(
            "no fresh UUID is generated for a password-only link",
            fmt.contains("UUID.randomUUID()")
        )
    }

    @Test
    fun `bypass list has the three missing subnets`() {
        val arrays = readSource("src/main/res/values/arrays.xml")
        assertTrue("112.124.47.0/24 missing", arrays.contains("112.124.47.0/24"))
        assertTrue("114.114.114.0/24 missing", arrays.contains("114.114.114.0/24"))
        assertTrue("192.0.0.0/29 missing", arrays.contains("192.0.0.0/29"))
        // Sorted position, next to their neighbours.
        assertTrue(
            "112.124.47.0/24 out of order",
            arrays.indexOf("112.124.46.0/24") < arrays.indexOf("112.124.47.0/24") &&
                arrays.indexOf("112.124.47.0/24") < arrays.indexOf("112.124.48.0/20")
        )
        assertTrue(
            "114.114.114.0/24 out of order",
            arrays.indexOf("114.114.112.0/23") < arrays.indexOf("114.114.114.0/24") &&
                arrays.indexOf("114.114.114.0/24") < arrays.indexOf("114.114.115.0/24")
        )
        assertTrue(
            "192.0.0.0/29 out of order",
            arrays.indexOf("192.0.0.0/29") < arrays.indexOf("192.0.0.8/29")
        )
    }

    @Test
    fun `single test resets status when service is gone`() {
        val fragment = readSource("src/main/java/io/nekohasekai/sagernet/ui/ConfigurationFragment.kt")
        val fn = fragment.substringAfter("private fun testConnection()")
            .substringBefore("override fun onKeyDown")
        val segments = fn.split("DataStore.serviceState != BaseService.State.Connected")
        assertEquals("expected 2 service-state checks in testConnection", 3, segments.size)
        for (i in 1..2) {
            val beforeReturn = segments[i].substringBefore("return@onMainDispatcher")
            assertTrue(
                "branch $i does not reset the status text before returning",
                beforeReturn.contains("R.string.not_connected")
            )
        }
    }

    @Test
    fun `batch test jobs follow fragment lifecycle`() {
        val fragment = readSource("src/main/java/io/nekohasekai/sagernet/ui/ConfigurationFragment.kt")
        assertFalse(
            "mainJob still uses the GlobalScope dispatcher",
            fragment.contains("val mainJob = runOnDefaultDispatcher")
        )
        val lifecycleJobs = fragment.split("val mainJob = runOnLifecycleDispatcher").size - 1
        assertEquals("expected 2 lifecycle mainJobs (pingTest, urlTest)", 2, lifecycleJobs)
        val guardedCancels = fragment.split("test.isCancelInitialized()").size - 1
        assertEquals("expected 2 guarded cancel calls in finally", 2, guardedCancels)
    }

    @Test
    fun `cancelling a test clears its notification`() {
        val notification =
            readSource("src/main/java/moe/matsuri/nb4a/ui/ConnectionTestNotification.kt")
        assertTrue(
            "ConnectionTestNotification has no cancel()",
            notification.contains("fun cancel()")
        )
        val fragment = readSource("src/main/java/io/nekohasekai/sagernet/ui/ConfigurationFragment.kt")
        val cancelCalls = fragment.split("test.notification?.cancel()").size - 1
        assertEquals("expected 2 notification cancels in test.cancel", 2, cancelCalls)
        val idempotentGuards = fragment.split("test.dialogStatus.getAndSet(2)").size - 1
        assertEquals("expected 2 idempotent cancel guards", 2, idempotentGuards)
    }
}
