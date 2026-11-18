package io.nekohasekai.sagernet.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the v1.4.37 batch: the 7 upstream non-bug
 * suggestions approved by the user 2026-11-16 (#1035, #934, #1027,
 * #1124, #1117, #968, #955).
 *
 * Same file-parsing style as the other tests in this package: the
 * production code touches Android APIs, so the tests assert on the
 * source text instead of running it.
 */
class Fixes1437Test {

    private fun readSource(relativePath: String): String {
        val file = File(relativePath)
        assertTrue("source file missing: $relativePath", file.exists())
        return file.readText()
    }

    private fun stringValue(xml: String, name: String): String {
        val start = xml.indexOf("name=\"$name\"")
        assertTrue("string $name missing", start >= 0)
        val open = xml.indexOf('>', start)
        val close = xml.indexOf("</string>", open)
        assertTrue("string $name has no closing tag", open >= 0 && close > open)
        return xml.substring(open + 1, close)
    }

    private fun functionBody(src: String, signature: String): String {
        val start = src.indexOf(signature)
        assertTrue("function $signature missing", start >= 0)
        val nextFun = src.indexOf("\n    fun ", start + signature.length)
        return if (nextFun >= 0) src.substring(start, nextFun) else src.substring(start)
    }

    // NOTE: version pin retired in v1.4.38; now pinned by Fixes1438Test.

    // #1035: wireguard:// link parsing keeps the preshared key.
    // Note: no wireguard:// parser existed before this version, so this
    // task adds the parser itself (WireGuardFmt.parseWireGuardLink).

    @Test
    fun `wireguard link keeps presharedkey`() {
        val fmt = readSource("src/main/java/io/nekohasekai/sagernet/fmt/wireguard/WireGuardFmt.kt")
        assertTrue("parseWireGuardLink missing", fmt.contains("fun parseWireGuardLink"))
        assertTrue("presharedkey param not read", fmt.lowercase().contains("presharedkey"))
        assertTrue("preshared key not stored", fmt.contains("peerPreSharedKey ="))
        // HttpUrl silently misparses '/' in the userinfo, so the parser
        // must split the authority off by hand (verified on JVM).
        assertTrue("userinfo not split by hand", fmt.contains("lastIndexOf('@')"))
        val formats = readSource("src/main/java/io/nekohasekai/sagernet/ktx/Formats.kt")
        assertTrue("wireguard:// branch missing", formats.contains("wireguard://"))
        assertTrue("Formats does not call parseWireGuardLink", formats.contains("parseWireGuardLink"))
    }

    @Test
    fun `wireguard scheme is in manifest`() {
        val manifest = readSource("src/main/AndroidManifest.xml")
        assertTrue("wireguard scheme missing from manifest", manifest.contains("android:scheme=\"wireguard\""))
    }

    // #934: per-app proxy gains a third state, block (屏蔽).

    @Test
    fun `per app block datastore keys exist`() {
        val store = readSource("src/main/java/io/nekohasekai/sagernet/database/DataStore.kt")
        assertTrue("individualBlock missing", store.contains("individualBlock"))
        assertTrue("appBlock missing", store.contains("appBlock"))
    }

    @Test
    fun `per app block chip exists`() {
        val layout = readSource("src/main/res/layout/layout_apps.xml")
        assertTrue("appProxyModeBlock chip missing", layout.contains("appProxyModeBlock"))
        assertTrue("block chip does not use route_block", layout.contains("@string/route_block"))
    }

    @Test
    fun `app manager handles block mode`() {
        val activity = readSource("src/main/java/io/nekohasekai/sagernet/ui/AppManagerActivity.kt")
        assertTrue("block chip not handled", activity.contains("appProxyModeBlock"))
        assertTrue("block list not used", activity.contains("individualBlock"))
    }

    @Test
    fun `config builder rejects blocked packages`() {
        val builder = readSource("src/main/java/io/nekohasekai/sagernet/fmt/ConfigBuilder.kt")
        assertTrue("block list not read", builder.contains("individualBlock"))
        assertTrue("block rule does not reject", builder.contains("action = \"reject\""))
    }

    @Test
    fun `vpn service skips allow list in block mode`() {
        val vpn = readSource("src/main/java/io/nekohasekai/sagernet/bg/VpnService.kt")
        assertTrue("block mode not handled", vpn.contains("appBlock"))
    }

    @Test
    fun `route block copy is 屏蔽`() {
        val zh = readSource("src/main/res/values-zh-rCN/strings.xml")
        assertTrue("route_block is not 屏蔽", stringValue(zh, "route_block") == "屏蔽")
    }

    // #1027: SS import defaults UoT on via a global setting.

    @Test
    fun `ss default uot setting exists`() {
        val store = readSource("src/main/java/io/nekohasekai/sagernet/database/DataStore.kt")
        assertTrue("ssDefaultUoT missing", store.contains("ssDefaultUoT"))
    }

    @Test
    fun `ss default uot preference exists`() {
        val prefs = readSource("src/main/res/xml/global_preferences.xml")
        assertTrue("ssDefaultUoT preference missing", prefs.contains("app:key=\"ssDefaultUoT\""))
    }

    @Test
    fun `ss import applies default uot`() {
        val fmt = readSource("src/main/java/io/nekohasekai/sagernet/fmt/shadowsocks/ShadowsocksFmt.kt")
        assertTrue("ss link import ignores the setting", fmt.contains("sUoT = DataStore.ssDefaultUoT"))
        val updater = readSource("src/main/java/io/nekohasekai/sagernet/group/RawUpdater.kt")
        assertTrue("clash ss import ignores the setting", updater.contains("sUoT = DataStore.ssDefaultUoT"))
    }

    @Test
    fun `ss default uot copy is exact`() {
        val expected = mapOf(
            "src/main/res/values/strings.xml" to mapOf(
                "ss_default_uot" to "Default UoT for imported Shadowsocks",
                "ss_default_uot_sum" to "Turn on UDP over TCP when importing Shadowsocks nodes"
            ),
            "src/main/res/values-zh-rCN/strings.xml" to mapOf(
                "ss_default_uot" to "导入 SS 默认开启 UoT",
                "ss_default_uot_sum" to "导入 Shadowsocks 节点时默认打开 UDP over TCP"
            ),
            "src/main/res/values-ru/strings.xml" to mapOf(
                "ss_default_uot" to "Включать UoT для импортируемых Shadowsocks",
                "ss_default_uot_sum" to "Включать UDP over TCP при импорте узлов Shadowsocks"
            )
        )
        for ((path, strings) in expected) {
            val xml = readSource(path)
            for ((name, value) in strings) {
                assertTrue("$name in $path is not the expected copy", stringValue(xml, name) == value)
            }
        }
    }

    // #1124: URL test timeout is adjustable.

    @Test
    fun `url test timeout setting exists`() {
        val store = readSource("src/main/java/io/nekohasekai/sagernet/database/DataStore.kt")
        assertTrue("urlTestTimeout missing", store.contains("urlTestTimeout"))
        assertTrue("urlTestTimeout default is not 5000", store.contains("5000"))
    }

    @Test
    fun `url test uses the setting`() {
        val test = readSource("src/main/java/io/nekohasekai/sagernet/bg/proto/UrlTest.kt")
        assertTrue("UrlTest ignores the setting", test.contains("DataStore.urlTestTimeout"))
        assertTrue("timeout range not enforced", test.contains("coerceIn(1000, 30000)"))
    }

    @Test
    fun `url test timeout preference exists`() {
        val prefs = readSource("src/main/res/xml/global_preferences.xml")
        assertTrue("urlTestTimeout preference missing", prefs.contains("app:key=\"urlTestTimeout\""))
    }

    @Test
    fun `url test timeout copy is exact`() {
        val expected = mapOf(
            "src/main/res/values/strings.xml" to mapOf(
                "url_test_timeout" to "URL test timeout (ms)",
                "url_test_timeout_sum" to "Timeout for URL tests in milliseconds (default 5000)"
            ),
            "src/main/res/values-zh-rCN/strings.xml" to mapOf(
                "url_test_timeout" to "测速超时 (ms)",
                "url_test_timeout_sum" to "URL 测速超时时间（毫秒），默认 5000"
            ),
            "src/main/res/values-ru/strings.xml" to mapOf(
                "url_test_timeout" to "Таймаут URL-теста (ms)",
                "url_test_timeout_sum" to "Таймаут URL-теста в миллисекундах (по умолчанию 5000)"
            )
        )
        for ((path, strings) in expected) {
            val xml = readSource(path)
            for ((name, value) in strings) {
                assertTrue("$name in $path is not the expected copy", stringValue(xml, name) == value)
            }
        }
    }

    // #1117: WireGuard persistent keepalive.

    @Test
    fun `wireguard keepalive field exists`() {
        val bean = readSource("src/main/java/io/nekohasekai/sagernet/fmt/wireguard/WireGuardBean.java")
        assertTrue("persistentKeepalive field missing", bean.contains("persistentKeepalive"))
        assertTrue("kryo version not bumped to 3", bean.contains("writeInt(3)"))
    }

    @Test
    fun `wireguard fmt emits keepalive`() {
        val fmt = readSource("src/main/java/io/nekohasekai/sagernet/fmt/wireguard/WireGuardFmt.kt")
        assertTrue("keepalive not emitted", fmt.contains("persistent_keepalive_interval"))
    }

    @Test
    fun `wireguard keepalive setting exists`() {
        val activity = readSource("src/main/java/io/nekohasekai/sagernet/ui/profile/WireGuardSettingsActivity.kt")
        assertTrue("keepalive binding missing", activity.contains("persistentKeepalive"))
        val prefs = readSource("src/main/res/xml/wireguard_preferences.xml")
        assertTrue("keepalive preference missing", prefs.contains("persistentKeepalive"))
    }

    @Test
    fun `wireguard conf import reads keepalive`() {
        val updater = readSource("src/main/java/io/nekohasekai/sagernet/group/RawUpdater.kt")
        assertTrue("conf import ignores PersistentKeepalive", updater.contains("PersistentKeepalive"))
    }

    @Test
    fun `wireguard keepalive copy is exact`() {
        val expected = mapOf(
            "src/main/res/values/strings.xml" to mapOf(
                "wireguard_persistent_keepalive" to "Persistent keepalive",
                "wireguard_persistent_keepalive_sum" to "Interval in seconds, 0 disables it"
            ),
            "src/main/res/values-zh-rCN/strings.xml" to mapOf(
                "wireguard_persistent_keepalive" to "Persistent Keepalive",
                "wireguard_persistent_keepalive_sum" to "间隔（秒），0 为关闭"
            ),
            "src/main/res/values-ru/strings.xml" to mapOf(
                "wireguard_persistent_keepalive" to "Persistent keepalive",
                "wireguard_persistent_keepalive_sum" to "Интервал в секундах, 0 — отключено"
            )
        )
        for ((path, strings) in expected) {
            val xml = readSource(path)
            for ((name, value) in strings) {
                assertTrue("$name in $path is not the expected copy", stringValue(xml, name) == value)
            }
        }
    }

    // #968: AnyTLS idle parameters.

    @Test
    fun `anytls idle fields exist`() {
        val bean = readSource("src/main/java/moe/matsuri/nb4a/proxy/anytls/AnyTLSBean.java")
        assertTrue("idleSessionCheckInterval missing", bean.contains("idleSessionCheckInterval"))
        assertTrue("idleSessionTimeout missing", bean.contains("idleSessionTimeout"))
        assertTrue("minIdleSession missing", bean.contains("minIdleSession"))
        assertTrue("kryo version not bumped to 1", bean.contains("writeInt(1)"))
    }

    @Test
    fun `singbox options has min idle session`() {
        val opts = readSource("src/main/java/moe/matsuri/nb4a/SingBoxOptions.java")
        assertTrue("min_idle_session missing", opts.contains("min_idle_session"))
    }

    @Test
    fun `anytls fmt emits idle params`() {
        val fmt = readSource("src/main/java/moe/matsuri/nb4a/proxy/anytls/AnyTLSFmt.kt")
        assertTrue("check interval not emitted", fmt.contains("idle_session_check_interval"))
        assertTrue("timeout not emitted", fmt.contains("idle_session_timeout"))
        assertTrue("min idle session not emitted", fmt.contains("min_idle_session"))
    }

    @Test
    fun `anytls idle settings exist`() {
        val activity = readSource("src/main/java/moe/matsuri/nb4a/proxy/anytls/AnyTLSSettingsActivity.kt")
        val prefs = readSource("src/main/res/xml/anytls_preferences.xml")
        for (key in listOf("idleSessionCheckInterval", "idleSessionTimeout", "minIdleSession")) {
            assertTrue("$key binding missing", activity.contains(key))
            assertTrue("$key preference missing", prefs.contains(key))
        }
    }

    @Test
    fun `anytls idle copy is exact`() {
        val expected = mapOf(
            "src/main/res/values/strings.xml" to mapOf(
                "anytls_idle_session_check_interval" to "Idle session check interval (s)",
                "anytls_idle_session_check_interval_sum" to "e.g. 15s, empty to unset",
                "anytls_idle_session_timeout" to "Idle session timeout (s)",
                "anytls_idle_session_timeout_sum" to "e.g. 30s, empty to unset",
                "anytls_min_idle_session" to "Min idle sessions",
                "anytls_min_idle_session_sum" to "0 to unset"
            ),
            "src/main/res/values-zh-rCN/strings.xml" to mapOf(
                "anytls_idle_session_check_interval" to "空闲会话检查间隔 (s)",
                "anytls_idle_session_check_interval_sum" to "如 15s，留空为不设置",
                "anytls_idle_session_timeout" to "空闲会话超时 (s)",
                "anytls_idle_session_timeout_sum" to "如 30s，留空为不设置",
                "anytls_min_idle_session" to "最小空闲会话数",
                "anytls_min_idle_session_sum" to "0 为不设置"
            ),
            "src/main/res/values-ru/strings.xml" to mapOf(
                "anytls_idle_session_check_interval" to "Интервал проверки неактивных сессий (s)",
                "anytls_idle_session_check_interval_sum" to "например 15s, пусто — не задано",
                "anytls_idle_session_timeout" to "Таймаут неактивной сессии (s)",
                "anytls_idle_session_timeout_sum" to "например 30s, пусто — не задано",
                "anytls_min_idle_session" to "Мин. неактивных сессий",
                "anytls_min_idle_session_sum" to "0 — не задано"
            )
        )
        for ((path, strings) in expected) {
            val xml = readSource(path)
            for ((name, value) in strings) {
                assertTrue("$name in $path is not the expected copy", stringValue(xml, name) == value)
            }
        }
    }

    // #955: Shadowsocks multiplex.

    @Test
    fun `shadowsocks mux field exists`() {
        val bean = readSource("src/main/java/io/nekohasekai/sagernet/fmt/shadowsocks/ShadowsocksBean.java")
        assertTrue("enableMux field missing", bean.contains("enableMux"))
        assertTrue("kryo version not bumped to 3", bean.contains("writeInt(3)"))
    }

    @Test
    fun `sing mux covers shadowsocks`() {
        val entity = readSource("src/main/java/io/nekohasekai/sagernet/database/ProxyEntity.kt")
        val body = functionBody(entity, "fun singMux()")
        assertTrue("singMux has no Shadowsocks branch", body.contains("TYPE_SS"))
        assertTrue("singMux does not use enableMux", body.contains("enableMux"))
    }

    @Test
    fun `shadowsocks mux setting exists`() {
        val activity = readSource("src/main/java/io/nekohasekai/sagernet/ui/profile/ShadowsocksSettingsActivity.kt")
        assertTrue("mux binding missing", activity.contains("enableMux"))
        val prefs = readSource("src/main/res/xml/shadowsocks_preferences.xml")
        assertTrue("mux preference missing", prefs.contains("enableMux"))
    }

    @Test
    fun `shadowsocks mux copy is exact`() {
        val expected = mapOf(
            "src/main/res/values/strings.xml" to mapOf(
                "shadowsocks_enable_mux" to "Multiplex",
                "shadowsocks_enable_mux_sum" to "Multiplex multiple streams over one connection"
            ),
            "src/main/res/values-zh-rCN/strings.xml" to mapOf(
                "shadowsocks_enable_mux" to "多路复用",
                "shadowsocks_enable_mux_sum" to "多个流复用同一个连接"
            ),
            "src/main/res/values-ru/strings.xml" to mapOf(
                "shadowsocks_enable_mux" to "Мультиплексирование",
                "shadowsocks_enable_mux_sum" to "Несколько потоков в одном соединении"
            )
        )
        for ((path, strings) in expected) {
            val xml = readSource(path)
            for ((name, value) in strings) {
                assertTrue("$name in $path is not the expected copy", stringValue(xml, name) == value)
            }
        }
    }
}
