package io.nekohasekai.sagernet.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the v1.4.41 batch:
 * 1. per-app proxy switch and entry split apart (row tap opens the app
 *    list without toggling; only the switch widget toggles);
 * 2. the four mode Chips fit in one row (narrower padding) with a
 *    horizontal-scroll fallback;
 * 3. duration settings show their unit in the title.
 *
 * Same file-parsing style as the other tests in this package: the
 * production code touches Android APIs, so the tests assert on the
 * source text instead of running it.
 */
class Fixes1441Test {

    private fun readSource(relativePath: String): String {
        // Unit tests run with the working directory set to app/, so
        // project-root files need ../ and app files need src/main/...
        val file = File(relativePath)
        assertTrue("source file missing: $relativePath", file.exists())
        return file.readText()
    }

    // NOTE: version pin retired in v1.4.43; now pinned by Fixes1443Test.

    // NOTE: the switch/entry split (v1.4.41) and its v1.4.42 follow-up were
    // reverted in v1.4.43 back to the 1.4.40 logic; the two tests that
    // pinned the split are removed. The reverted behavior is pinned by
    // Fixes1443Test.

    // ---- 2. mode Chips fit ----

    private fun appsLayout(): String {
        return readSource("src/main/res/layout/layout_apps.xml")
    }

    @Test
    fun modeChipsHaveNarrowPadding() {
        val src = appsLayout()
        val groupStart = src.indexOf("android:id=\"@+id/bypassGroup\"")
        assertTrue("bypassGroup missing", groupStart >= 0)
        val groupEnd = src.indexOf("</com.google.android.material.chip.ChipGroup>", groupStart)
        assertTrue("bypassGroup end missing", groupEnd > groupStart)
        val group = src.substring(groupStart, groupEnd)
        assertEquals("all four mode chips must have 20dp textStartPadding",
            4, group.split("app:textStartPadding=\"20dp\"").size - 1)
        assertEquals("all four mode chips must have 20dp textEndPadding",
            4, group.split("app:textEndPadding=\"20dp\"").size - 1)
    }

    @Test
    fun modeChipsScrollFallback() {
        val src = appsLayout()
        val scrollStart = src.indexOf("<HorizontalScrollView")
        assertTrue("HorizontalScrollView missing", scrollStart >= 0)
        val groupStart = src.indexOf("android:id=\"@+id/bypassGroup\"")
        assertTrue("bypassGroup must sit inside the HorizontalScrollView", groupStart > scrollStart)
        val scrollEnd = src.indexOf("</HorizontalScrollView>", groupStart)
        assertTrue("HorizontalScrollView must close after the ChipGroup", scrollEnd > groupStart)
        val groupTag = src.substring(groupStart, src.indexOf(">", groupStart))
        assertTrue("ChipGroup must be wrap_content so it can scroll", groupTag.contains("android:layout_width=\"wrap_content\""))
    }

    // ---- 3. units in setting titles ----

    private fun stringsXml(lang: String): String {
        val path = when (lang) {
            "en" -> "src/main/res/values/strings.xml"
            "zh" -> "src/main/res/values-zh-rCN/strings.xml"
            "ru" -> "src/main/res/values-ru/strings.xml"
            else -> error("unknown lang")
        }
        return readSource(path)
    }

    @Test
    fun durationSettingsShowUnits() {
        val en = stringsXml("en")
        val zh = stringsXml("zh")
        val ru = stringsXml("ru")
        assertTrue("en url test timeout needs (ms)", en.contains("URL test timeout (ms)</string>"))
        assertTrue("zh url test timeout needs (ms)", zh.contains("测速超时 (ms)</string>"))
        assertTrue("ru url test timeout needs (ms)", ru.contains("Таймаут URL-теста (ms)</string>"))
        assertTrue("en idle check interval needs (s)", en.contains("Idle session check interval (s)</string>"))
        assertTrue("zh idle check interval needs (s)", zh.contains("空闲会话检查间隔 (s)</string>"))
        assertTrue("ru idle check interval needs (s)", ru.contains("Интервал проверки неактивных сессий (s)</string>"))
        assertTrue("en idle timeout needs (s)", en.contains("Idle session timeout (s)</string>"))
        assertTrue("zh idle timeout needs (s)", zh.contains("空闲会话超时 (s)</string>"))
        assertTrue("ru idle timeout needs (s)", ru.contains("Таймаут неактивной сессии (s)</string>"))
    }

    @Test
    fun minIdleSessionsHasNoUnit() {
        val en = stringsXml("en")
        val zh = stringsXml("zh")
        assertTrue("min idle sessions title must stay unit-less (en)",
            en.contains("<string name=\"anytls_min_idle_session\">Min idle sessions</string>"))
        assertTrue("min idle sessions title must stay unit-less (zh)",
            zh.contains("<string name=\"anytls_min_idle_session\">最小空闲会话数</string>"))
    }
}
