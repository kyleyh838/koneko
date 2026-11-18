package io.nekohasekai.sagernet.ui

import java.io.File
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Regression test for the KoNeko rename (v1.4.24).
 *
 * The app used to be called NKB (package com.nkb.android). It is now KoNeko
 * (package com.koneko.android): every displayed name, the package id, and
 * every in-app link that pointed at the upstream NekoBox repo now point at
 * kyleyh838/koneko. The About page also drops the donate and Telegram
 * update-channel rows. These tests parse the real resource and source files,
 * so they fail while any of it still says NKB / NekoBox / MatsuriDayo.
 * One deliberate exception (user decision 2026-11-16): the About page
 * subtitle app_desc credits the upstream project ("Based on a modified
 * version of NekoBox ..."), so that one string may say NekoBox.
 */
class KoNekoRenameTest {

    private fun parseStrings(relativePath: String): Element {
        val file = File(relativePath)
        assertTrue("resource file missing: $relativePath", file.exists())
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        return factory.newDocumentBuilder().parse(file).documentElement
    }

    private fun stringValue(root: Element, name: String): String? {
        val nodes = root.getElementsByTagName("string")
        for (i in 0 until nodes.length) {
            val item = nodes.item(i) as Element
            if (item.getAttribute("name") == name) return item.textContent.trim()
        }
        return null
    }

    @Test
    fun `display name is KoNeko everywhere`() {
        val root = parseStrings("src/main/res/values/strings.xml")
        assertEquals("KoNeko", stringValue(root, "app_name"))
        assertEquals("KoNeko for Android", stringValue(root, "app_name_long"))
        assertEquals("KoNeko", stringValue(root, "tile_title"))
        // Every locale override of the displayed name must match too.
        File("src/main/res").listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("values-") }
            ?.forEach { dir ->
                val file = File(dir, "strings.xml")
                if (!file.exists()) return@forEach
                val localeRoot = parseStrings("src/main/res/${dir.name}/strings.xml")
                stringValue(localeRoot, "app_name")?.let {
                    assertEquals("${dir.name} app_name", "KoNeko", it)
                }
                stringValue(localeRoot, "tile_title")?.let {
                    assertEquals("${dir.name} tile_title", "KoNeko", it)
                }
            }
    }

    @Test
    fun `no displayed string still says NKB`() {
        File("src/main/res").walkTopDown()
            .filter { it.isFile && it.name == "strings.xml" }
            .forEach { file ->
                assertFalse(
                    "${file.path} still contains NKB",
                    file.readText().contains(">NKB<") || file.readText().contains(">NKB <")
                )
            }
    }

    @Test
    fun `no displayed string still says NekoBox`() {
        // Exception (user decision 2026-11-16): app_desc deliberately
        // credits the upstream project, so it is excluded from the check.
        File("src/main/res").walkTopDown()
            .filter { it.isFile && it.name == "strings.xml" }
            .forEach { file ->
                val withoutAppDesc = file.readLines()
                    .filter { !it.contains("name=\"app_desc\"") }
                    .joinToString("\n")
                assertFalse(
                    "${file.path} still contains NekoBox",
                    withoutAppDesc.contains("NekoBox")
                )
            }
    }

    @Test
    fun `always-on VPN is a bare title, no summary`() {
        val root = parseStrings("src/main/res/values/strings.xml")
        assertEquals("Always-on VPN", stringValue(root, "always_on_vpn"))
        val zhRoot = parseStrings("src/main/res/values-zh-rCN/strings.xml")
        assertEquals("始终开启VPN", stringValue(zhRoot, "always_on_vpn"))
        assertFalse("summary string remains", stringValue(root, "always_on_vpn_summary") != null)
        assertFalse("zh summary string remains", stringValue(zhRoot, "always_on_vpn_summary") != null)
        val prefs = File("src/main/res/xml/global_preferences.xml").readText()
        assertFalse("preference still has a summary", prefs.contains("always_on_vpn_summary"))
    }

    @Test
    fun `crash report header says KoNeko`() {
        val crash = File("src/main/java/io/nekohasekai/sagernet/utils/CrashHandler.kt").readText()
        assertTrue(crash.contains("KoNeko for Android"))
        assertFalse(crash.contains("NekoBox for Android"))
    }

    @Test
    fun `apk output file is named KoNeko`() {
        val helpers = File("../buildSrc/src/main/kotlin/Helpers.kt").readText()
        assertTrue(helpers.contains("\"KoNeko-\$versionName\""))
        assertTrue(helpers.contains("\"KoNeko-\" + requireMetadata().getProperty(\"PRE_VERSION_NAME\")"))
        assertFalse(helpers.contains("NekoBox-"))
    }

    @Test
    fun `readme points at koneko`() {
        val readme = File("../README.md").readText()
        assertTrue(readme.startsWith("# KoNeko for Android"))
        assertTrue(readme.contains("github.com/kyleyh838/koneko"))
        // Upstream attribution is intentional: exactly one line in the intro.
        // The string appears twice in that line (link text + URL).
        assertTrue(readme.contains("基于 [MatsuriDayo/NekoBoxForAndroid](https://github.com/MatsuriDayo/NekoBoxForAndroid) 修改。"))
        assertEquals(2, readme.split("MatsuriDayo/NekoBoxForAndroid").size - 1)
        assertFalse(readme.contains("t.me/"))
        assertFalse("docs homepage link remains", readme.contains("https://matsuridayo.github.io\n"))
    }

    @Test
    fun `backup filename uses fixed date format`() {
        val backup = File("src/main/java/io/nekohasekai/sagernet/ui/BackupFragment.kt").readText()
        assertTrue(backup.contains("\"yyyyMMdd_HHmmss\""))
        assertFalse(backup.contains("toLocaleString()"))
        assertEquals(1, backup.split("koneko_backup_").size - 1)
        // helper defined once, called at both the export and share sites
        assertEquals(3, backup.split("backupFileName()").size - 1)
    }

    @Test
    fun `subscription user agent stays NekoBox by decision`() {
        // Deliberate exception (v1.4.26): some subscription sources may
        // identify the client by UA, so the UA keeps the old name.
        val nets = File("src/main/java/io/nekohasekai/sagernet/ktx/Nets.kt").readText()
        assertTrue(nets.contains("\"NekoBox/Android/\""))
    }

    @Test
    fun `backup file name starts with koneko`() {
        val backup = File("src/main/java/io/nekohasekai/sagernet/ui/BackupFragment.kt").readText()
        assertTrue(backup.contains("koneko_backup_"))
        assertFalse(backup.contains("nekobox_backup"))
    }

    @Test
    fun `faq dead code is gone`() {
        val main = File("src/main/java/io/nekohasekai/sagernet/ui/MainActivity.kt").readText()
        assertFalse("nav_faq branch remains", main.contains("nav_faq"))
        assertFalse(
            "faq docs link remains",
            main.contains("\"https://matsuridayo.github.io/\"")
        )
        val drawerMenu = File("src/main/res/menu/main_drawer_menu.xml").readText()
        assertFalse("nav_faq menu item remains", drawerMenu.contains("nav_faq"))
    }

    @Test
    fun `package name is com koneko android`() {
        val props = Properties().apply {
            File("../nb4a.properties").inputStream().use { load(it) }
        }
        assertEquals("com.koneko.android", props.getProperty("PACKAGE_NAME"))
        val shortcuts = parseStrings("src/main/res/xml/shortcuts.xml")
        val androidNs = "http://schemas.android.com/apk/res/android"
        val nodes = shortcuts.getElementsByTagName("shortcut")
        assertEquals("expected 4 static shortcuts", 4, nodes.length)
        for (i in 0 until nodes.length) {
            val shortcut = nodes.item(i) as Element
            val intent = shortcut.getElementsByTagName("intent").item(0) as Element
            assertEquals(
                "com.koneko.android",
                intent.getAttributeNS(androidNs, "targetPackage")
            )
        }
        val constants = File("src/main/java/io/nekohasekai/sagernet/Constants.kt").readText()
        assertTrue(constants.contains("com.koneko.android.RESET_UPSTREAM_CONNECTIONS"))
        assertFalse(constants.contains("com.nkb.android"))
    }

    @Test
    fun `about page links point at the koneko repo`() {
        val about = File("src/main/java/io/nekohasekai/sagernet/ui/AboutFragment.kt").readText()
        assertTrue(about.contains("https://github.com/kyleyh838/koneko/releases"))
        assertTrue(about.contains("https://github.com/kyleyh838/koneko\""))
        assertTrue(about.contains("https://api.github.com/repos/kyleyh838/koneko/releases/latest"))
        assertTrue(about.contains("https://api.github.com/repos/kyleyh838/koneko/releases/tags/preview"))
        assertFalse("upstream repo link remains", about.contains("MatsuriDayo"))
        assertFalse("upstream donate link remains", about.contains("matsuridayo.github.io"))
        assertFalse("telegram link remains", about.contains("t.me/"))
        assertFalse("donate row remains", about.contains("R.string.donate"))
        assertFalse("telegram row remains", about.contains("R.string.telegram"))
    }
}
