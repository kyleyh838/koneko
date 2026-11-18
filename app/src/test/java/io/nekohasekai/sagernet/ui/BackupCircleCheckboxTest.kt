package io.nekohasekai.sagernet.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Regression test for the backup page circle checkboxes (v1.4.21 follow-up).
 *
 * v1.4.21 added `app:buttonTint="@null"` to clear the theme tint, but the white
 * check still did not appear on device: the inflated view is a MaterialCheckBox,
 * whose `useMaterialThemeColors=true` (default style) re-applies an accent tint
 * in onAttachedToWindow() exactly when the tint list is null. The fix is
 * `app:useMaterialThemeColors="false"` on every backup CheckBox, so the vector
 * drawable renders its own colors (blue disc + white check).
 *
 * These tests parse the real resource files, so they fail while the layout still
 * lets MaterialCheckBox re-tint and pass once the attribute is present.
 */
class BackupCircleCheckboxTest {

    private fun parseRes(relativePath: String): Element {
        val file = File(relativePath)
        assertTrue("resource file missing: $relativePath", file.exists())
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        return factory.newDocumentBuilder().parse(file).documentElement
    }

    private fun backupCheckBoxes(): List<Element> {
        val root = parseRes("src/main/res/layout/layout_backup.xml")
        val nodes = root.getElementsByTagName("CheckBox")
        assertEquals("backup page must have exactly 3 CheckBoxes", 3, nodes.length)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    @Test
    fun `all backup checkboxes disable Material theme colors`() {
        for (checkBox in backupCheckBoxes()) {
            assertEquals(
                "CheckBox ${checkBox.getAttribute("android:id")} must set " +
                    "app:useMaterialThemeColors=false or MaterialCheckBox re-tints " +
                    "the white check to the accent color",
                "false",
                checkBox.getAttributeNS(
                    "http://schemas.android.com/apk/res-auto", "useMaterialThemeColors"
                )
            )
        }
    }

    @Test
    fun `all backup checkboxes keep buttonTint cleared`() {
        for (checkBox in backupCheckBoxes()) {
            assertEquals(
                "@null",
                checkBox.getAttributeNS(
                    "http://schemas.android.com/apk/res-auto", "buttonTint"
                )
            )
        }
    }

    @Test
    fun `circle drawables are 20dp`() {
        for (name in listOf("ic_circle_checked", "ic_circle_unchecked")) {
            val vector = parseRes("src/main/res/drawable/$name.xml")
            assertEquals("20dp", vector.getAttributeNS(
                "http://schemas.android.com/apk/res/android", "width"))
            assertEquals("20dp", vector.getAttributeNS(
                "http://schemas.android.com/apk/res/android", "height"))
        }
    }

    @Test
    fun `checked circle still draws a white check on a blue disc`() {
        val vector = parseRes("src/main/res/drawable/ic_circle_checked.xml")
        val paths = vector.getElementsByTagName("path")
        var hasBlueDisc = false
        var hasWhiteCheck = false
        for (i in 0 until paths.length) {
            val path = paths.item(i) as Element
            val androidNs = "http://schemas.android.com/apk/res/android"
            if (path.getAttributeNS(androidNs, "fillColor") == "@color/nkb_circle_checked") {
                hasBlueDisc = true
            }
            if (path.getAttributeNS(androidNs, "strokeColor") == "@android:color/white" &&
                path.getAttributeNS(androidNs, "strokeWidth").toFloatOrNull() ?: 0f > 0f
            ) {
                hasWhiteCheck = true
            }
        }
        assertTrue("checked circle must have a blue disc path", hasBlueDisc)
        assertTrue("checked circle must have a white stroked check path", hasWhiteCheck)
    }
}
