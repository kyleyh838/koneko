package io.nekohasekai.sagernet.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the v1.4.38 batch: upstream PR #1173 (ECH config
 * lost in Clash parsing) merged as-is plus List hardening, and the detour
 * half of PR #1219 (WireGuard speedup) rewritten for endpoint-form
 * WireGuard via _hack_config_map. The network_strategy half of #1219 was
 * reviewed and deliberately NOT merged.
 *
 * Same file-parsing style as the other tests in this package: the
 * production code touches Android APIs, so the tests assert on the
 * source text instead of running it.
 */
class Fixes1438Test {

    private fun readSource(relativePath: String): String {
        val file = File(relativePath)
        assertTrue("source file missing: $relativePath", file.exists())
        return file.readText()
    }

    private fun functionBody(src: String, signature: String): String {
        val start = src.indexOf(signature)
        assertTrue("function $signature missing", start >= 0)
        val nextFun = src.indexOf("\n    fun ", start + signature.length)
        return if (nextFun >= 0) src.substring(start, nextFun) else src.substring(start)
    }

    private fun echOptsBlock(): String {
        val src = readSource("src/main/java/io/nekohasekai/sagernet/group/RawUpdater.kt")
        val start = src.indexOf("\"ech-opts\"")
        assertTrue("ech-opts branch missing", start >= 0)
        val end = src.indexOf("proxies.add(bean)", start)
        assertTrue("ech-opts block end missing", end > start)
        return src.substring(start, end)
    }

    // NOTE: version pin retired in v1.4.39; now pinned by Fixes1439Test.

    // #1173: Clash ech-opts.config must be stored into bean.echConfig.

    @Test
    fun `ech config branch exists`() {
        val block = echOptsBlock()
        assertTrue("enable branch missing", block.contains("\"enable\" -> bean.enableECH"))
        assertTrue("config branch missing", block.contains("\"config\" -> bean.echConfig"))
    }

    @Test
    fun `ech config list value is joined`() {
        val block = echOptsBlock()
        // A non-compliant subscription may write config as a YAML list;
        // it must be joined into lines, not toString()'d into "[a, b]".
        assertTrue("List value not handled", block.contains("as? List<Any>"))
        assertTrue("List not joined with newlines", block.contains("joinToString(\"\\n\")"))
    }

    // #1219 detour (rewritten): endpoint-form WireGuard has no detour
    // field, so it goes through _hack_config_map like domain_resolver.

    @Test
    fun `wireguard endpoint detours via direct`() {
        val fmt = readSource("src/main/java/io/nekohasekai/sagernet/fmt/wireguard/WireGuardFmt.kt")
        val body = functionBody(fmt, "fun buildSingBoxEndpointWireguardBean")
        assertTrue("detour not set", body.contains("_hack_config_map[\"detour\"] = \"direct\""))
        // detour and listen_port are mutually exclusive in the core.
        assertFalse("listen_port must not be set alongside detour", body.contains("listen_port"))
    }

    // #1219 network_strategy was reviewed and deliberately NOT merged:
    // redundant under auto_detect_interface, and it disables the EPERM
    // fallback for old Xiaomi devices.

    @Test
    fun `no network strategy added`() {
        val root = File("src/main/java/io/nekohasekai/sagernet/")
        assertTrue("source root missing", root.exists())
        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains("network_strategy") }
            .map { it.name }
            .toList()
        assertTrue("network_strategy added in: $offenders", offenders.isEmpty())
    }
}
