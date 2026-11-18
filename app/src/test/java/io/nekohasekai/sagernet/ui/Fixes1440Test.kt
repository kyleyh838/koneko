package io.nekohasekai.sagernet.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the v1.4.40 batch: #934 block mode actually blocks.
 * The core never looked up which app a connection belonged to, because
 * Router.isLocalSource did not recognise the TUN source address
 * (172.19.0.1) as local: MyInterfaceAddress() returned nil and the
 * interface monitor is an empty stub. The fix captures the TUN addresses
 * in OpenInterface and reports them from MyInterfaceAddress(), so the
 * UID lookup runs and the user_id block rule can match.
 *
 * Same file-parsing style as the other tests in this package: the
 * production code touches Android APIs, so the tests assert on the
 * source text instead of running it.
 */
class Fixes1440Test {

    private fun readSource(relativePath: String): String {
        val file = File(relativePath)
        assertTrue("source file missing: $relativePath", file.exists())
        return file.readText()
    }

    // NOTE: version pin retired in v1.4.41; now pinned by Fixes1441Test.

    private fun platformBoxSource(): String {
        return readSource("../libcore/platform_box.go")
    }

    @Test
    fun myInterfaceAddressReportsTunAddresses() {
        val src = platformBoxSource()
        val start = src.indexOf("func (w *boxPlatformInterfaceWrapper) MyInterfaceAddress()")
        assertTrue("MyInterfaceAddress missing", start >= 0)
        val body = src.substring(start, src.indexOf("\n}\n", start))
        assertFalse("MyInterfaceAddress still returns nil unconditionally", body.trimEnd().endsWith("return nil"))
        assertTrue("tun addresses not returned", body.contains("tunAddresses"))
    }

    @Test
    fun openInterfaceCapturesTunAddresses() {
        val src = platformBoxSource()
        val start = src.indexOf("func (w *boxPlatformInterfaceWrapper) OpenInterface(")
        assertTrue("OpenInterface missing", start >= 0)
        val end = src.indexOf("func (w *boxPlatformInterfaceWrapper) ProcessPlatformOptions", start)
        assertTrue("OpenInterface body end missing", end > start)
        val body = src.substring(start, end)
        assertTrue("options.Inet4Address not captured", body.contains("options.Inet4Address"))
        assertTrue("options.Inet6Address not captured", body.contains("options.Inet6Address"))
        assertTrue("captured addresses not stored", body.contains("w.tunAddresses = addrs"))
    }
}
