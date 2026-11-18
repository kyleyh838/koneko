package io.nekohasekai.sagernet.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test for tab press feedback (v1.4.32).
 *
 * The group tabs on the home page and the tabs on the tools page no
 * longer use the Material ripple (a big square wash over the whole
 * tab rect). Pressing a tab shrinks it to 0.95 and springs back on
 * release; the pill indicator still shows the selection.
 */
class TabPressScaleTest {

    private fun readSource(relativePath: String): String {
        val file = File(relativePath)
        assertTrue("source file missing: $relativePath", file.exists())
        return file.readText()
    }

    @Test
    fun `press scale helper shrinks to 0_95`() {
        val helper = readSource("src/main/java/io/nekohasekai/sagernet/ktx/TabPressScale.kt")
        assertTrue("helper does not shrink to 0.95", helper.contains("0.95f"))
        assertTrue("helper ignores press down", helper.contains("ACTION_DOWN"))
        assertTrue("helper ignores release", helper.contains("ACTION_UP"))
        assertTrue("helper ignores cancel", helper.contains("ACTION_CANCEL"))
    }

    @Test
    fun `home group tabs use press scale and no ripple`() {
        val layout = readSource("src/main/res/layout/layout_group_list.xml")
        assertTrue("home tabs still have a ripple", layout.contains("tabRippleColor=\"@null\""))
        assertFalse(
            "home tabs still use accent ripple",
            layout.contains("tabRippleColor=\"@color/nkb_accent_soft\"")
        )
        val fragment = readSource("src/main/java/io/nekohasekai/sagernet/ui/ConfigurationFragment.kt")
        assertTrue("home tabs do not apply press scale", fragment.contains("applyTabPressScale"))
    }

    @Test
    fun `tools tabs use press scale and no ripple`() {
        val layout = readSource("src/main/res/layout/layout_tools.xml")
        assertTrue("tools tabs still have a ripple", layout.contains("tabRippleColor=\"@null\""))
        assertFalse(
            "tools tabs still use accent ripple",
            layout.contains("tabRippleColor=\"@color/nkb_accent_soft\"")
        )
        val fragment = readSource("src/main/java/io/nekohasekai/sagernet/ui/ToolsFragment.kt")
        assertTrue("tools tabs do not apply press scale", fragment.contains("applyTabPressScale"))
    }
}
