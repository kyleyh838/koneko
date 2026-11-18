package io.nekohasekai.sagernet.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test for the About page update options (v1.4.30).
 *
 * The preview-update option is gone: KoNeko ships stable releases
 * only, and the preview tag is not a user-facing update channel.
 * The stable-update row gets the refresh icon so it matches the
 * version row above it, which already carries an icon.
 */
class AboutPageTest {

    private fun readSource(relativePath: String): String {
        val file = File(relativePath)
        assertTrue("source file missing: $relativePath", file.exists())
        return file.readText()
    }

    @Test
    fun `about page has no preview update option`() {
        val about = readSource("src/main/java/io/nekohasekai/sagernet/ui/AboutFragment.kt")
        assertFalse("Preview update item still on About page", about.contains("check_update_preview"))
        assertFalse("Preview update still checked", about.contains("checkUpdate(true)"))
        val strings = readSource("src/main/res/values/strings.xml")
        assertFalse("check_update_preview string still present", strings.contains("check_update_preview"))
    }

    @Test
    fun `stable update row carries the refresh icon`() {
        val about = readSource("src/main/java/io/nekohasekai/sagernet/ui/AboutFragment.kt")
        assertTrue(
            "Stable update row has no refresh icon",
            about.contains(
                ".icon(R.drawable.ic_baseline_refresh_24)\n" +
                    "                                .text(R.string.check_update_release)"
            )
        )
    }

    @Test
    fun `library card overlay matches app card style`() {
        // About page middle cards are drawn by material-about-library.
        // The app overrides the library layout with same-name resource
        // so the cards match the app spec: 20dp corners, 16dp side
        // margins, nkb_card background, nkb_card_stroke outline.
        val layout = readSource("src/main/res/layout/mal_material_about_list_card.xml")
        assertTrue("overlay missing 20dp corner", layout.contains("cardCornerRadius=\"20dp\""))
        assertTrue("overlay missing 16dp side margin", layout.contains("layout_marginStart=\"16dp\""))
        assertTrue("overlay missing nkb_card background", layout.contains("cardBackgroundColor=\"@color/nkb_card\""))
        assertTrue("overlay missing nkb_card_stroke", layout.contains("strokeColor=\"@color/nkb_card_stroke\""))
        // Structure the library adapter binds to must stay intact.
        assertTrue("overlay lost card id", layout.contains("@+id/mal_list_card"))
        assertTrue("overlay lost title id", layout.contains("@+id/mal_list_card_title"))
        assertTrue("overlay lost recyclerview id", layout.contains("@+id/mal_card_recyclerview"))
    }

    @Test
    fun `about cards use outline style`() {
        // The library adapter forces stroke 0 + elevation 2dp for
        // outline(false) cards at bind time; outline(true) gives
        // 1dp stroke + 0 elevation, which is the app card spec.
        val about = readSource("src/main/java/io/nekohasekai/sagernet/ui/AboutFragment.kt")
        assertFalse("About cards still use outline(false)", about.contains(".outline(false)"))
        val outlineTrueCount = about.split(".outline(true)").size - 1
        assertTrue("Expected 2 outline(true) cards, found $outlineTrueCount", outlineTrueCount == 2)
    }

    @Test
    fun `app_desc names NekoBox and sing-box`() {
        val english = readSource("src/main/res/values/strings.xml")
        assertTrue(
            "English app_desc not updated",
            english.contains("Based on a modified version of NekoBox, a universal proxy tool for Android using sing-box.")
        )
        assertFalse("Old English app_desc still present", english.contains("written in Kotlin"))
        val chinese = readSource("src/main/res/values-zh-rCN/strings.xml")
        assertTrue(
            "Chinese app_desc not updated",
            chinese.contains("基于 NekoBox 修改版，使用 sing-box 的 Android 通用代理工具。")
        )
        assertFalse("Old Chinese app_desc still present", chinese.contains("使用 Kotlin 编写"))
    }

    @Test
    fun `about list uses activity context for strings`() {
        // material-about-library calls getMaterialAboutList from a
        // background AsyncTask; Fragment.getString() on a detached
        // fragment crashes, the passed activityContext stays valid.
        val about = readSource("src/main/java/io/nekohasekai/sagernet/ui/AboutFragment.kt")
        val fn = about.substringAfter("override fun getMaterialAboutList")
            .substringBefore("override fun onViewCreated")
        val total = fn.split("getString(").size - 1
        val contextual = fn.split("activityContext.getString(").size - 1
        assertTrue("expected 2 activityContext.getString calls, found $contextual", contextual == 2)
        assertTrue("bare getString calls remain: ${total - contextual}", total - contextual == 0)
    }

    @Test
    fun `check update label is short`() {
        val english = readSource("src/main/res/values/strings.xml")
        assertTrue(
            "English label not shortened",
            english.contains("<string name=\"check_update_release\">Check for updates</string>")
        )
        val chinese = readSource("src/main/res/values-zh-rCN/strings.xml")
        assertTrue(
            "Chinese label not shortened",
            chinese.contains("<string name=\"check_update_release\">检查更新</string>")
        )
        val russian = readSource("src/main/res/values-ru/strings.xml")
        assertTrue(
            "Russian label not shortened",
            russian.contains("<string name=\"check_update_release\">Проверить наличие обновлений</string>")
        )
    }
}
