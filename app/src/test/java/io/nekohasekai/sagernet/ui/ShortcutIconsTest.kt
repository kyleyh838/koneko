package io.nekohasekai.sagernet.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Regression test for the long-press shortcut icons (v1.4.23).
 *
 * The four static shortcuts used to share one generic launcher icon. They now
 * use FlClash-style icons: a theme-accent disc with a white glyph (play for
 * enable, pause bars for disable, switch for toggle, QR code for scan).
 * These tests parse the real resource files, so they fail while shortcuts.xml
 * still points at the old icons or a drawable is missing.
 */
class ShortcutIconsTest {

    private fun parseRes(relativePath: String): Element {
        val file = File(relativePath)
        assertTrue("resource file missing: $relativePath", file.exists())
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        return factory.newDocumentBuilder().parse(file).documentElement
    }

    @Test
    fun `each shortcut uses its own FlClash-style icon`() {
        val root = parseRes("src/main/res/xml/shortcuts.xml")
        val androidNs = "http://schemas.android.com/apk/res/android"
        val expected = mapOf(
            "toggle" to "@drawable/ic_shortcut_toggle",
            "enable" to "@drawable/ic_shortcut_start",
            "disable" to "@drawable/ic_shortcut_stop",
            "scan" to "@drawable/ic_shortcut_scan"
        )
        val nodes = root.getElementsByTagName("shortcut")
        assertEquals("expected 4 static shortcuts", 4, nodes.length)
        for (i in 0 until nodes.length) {
            val shortcut = nodes.item(i) as Element
            val id = shortcut.getAttributeNS(androidNs, "shortcutId")
            assertEquals("shortcut $id must use its own icon", expected[id], shortcut.getAttributeNS(androidNs, "icon"))
        }
    }

    @Test
    fun `shortcut icons are accent discs with white glyphs`() {
        val androidNs = "http://schemas.android.com/apk/res/android"
        for (name in listOf("ic_shortcut_start", "ic_shortcut_stop", "ic_shortcut_toggle", "ic_shortcut_scan")) {
            val vector = parseRes("src/main/res/drawable/$name.xml")
            assertEquals("48dp", vector.getAttributeNS(androidNs, "width"))
            assertEquals("48dp", vector.getAttributeNS(androidNs, "height"))
            val paths = vector.getElementsByTagName("path")
            var hasAccentDisc = false
            var hasWhiteGlyph = false
            for (i in 0 until paths.length) {
                val path = paths.item(i) as Element
                when (path.getAttributeNS(androidNs, "fillColor")) {
                    "@color/nkb_accent" -> hasAccentDisc = true
                    "#FFFFFF" -> hasWhiteGlyph = true
                }
            }
            assertTrue("$name must have an accent disc", hasAccentDisc)
            assertTrue("$name must have a white glyph", hasWhiteGlyph)
        }
    }

    @Test
    fun `stop icon is a square, not pause bars`() {
        val vector = parseRes("src/main/res/drawable/ic_shortcut_stop.xml")
        val paths = vector.getElementsByTagName("path")
        var whitePathData = ""
        for (i in 0 until paths.length) {
            val path = paths.item(i) as Element
            if (path.getAttributeNS("http://schemas.android.com/apk/res/android", "fillColor") == "#FFFFFF") {
                whitePathData = path.getAttributeNS("http://schemas.android.com/apk/res/android", "pathData")
            }
        }
        assertTrue("stop icon must have a white glyph", whitePathData.isNotEmpty())
        assertFalse("stop icon must not be pause bars", whitePathData.contains("A1.1,1.1"))
        assertTrue("stop icon glyph must be a rounded square", whitePathData.contains("a1.5,1.5"))
    }

    @Test
    fun `scan icon draws a QR code`() {
        val vector = parseRes("src/main/res/drawable/ic_shortcut_scan.xml")
        // Three white finder squares plus data dots: at least 6 white paths.
        val paths = vector.getElementsByTagName("path")
        var whitePaths = 0
        for (i in 0 until paths.length) {
            val path = paths.item(i) as Element
            if (path.getAttributeNS("http://schemas.android.com/apk/res/android", "fillColor") == "#FFFFFF") {
                whitePaths++
            }
        }
        assertTrue("scan icon must have at least 6 white paths, got $whitePaths", whitePaths >= 6)
    }
}
