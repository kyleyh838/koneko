package io.nekohasekai.sagernet.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the v1.4.43 revert: the per-app proxy switch/entry
 * split from v1.4.41 (plus its v1.4.42 follow-up) is rolled back to the
 * 1.4.40 logic. The Chip shrink + scroll and the setting-name units from
 * v1.4.41 are kept (they work fine) and stay pinned by Fixes1441Test.
 *
 * 1.4.40 logic being restored:
 * - the settings row is a plain SwitchPreference; tapping the row toggles
 *   the switch and the change listener opens the app list;
 * - opening the app list auto-enables proxyApps when it is off;
 * - the initial checked Chip follows the mode only (Block/Bypass/On),
 *   never Disable.
 *
 * Same file-parsing style as the other tests in this package.
 */
class Fixes1443Test {

    private fun readSource(relativePath: String): String {
        // Unit tests run with the working directory set to app/.
        val file = File(relativePath)
        assertTrue("source file missing: $relativePath", file.exists())
        return file.readText()
    }

    // NOTE: version pin retired in v1.4.44; now pinned by Fixes1444Test.

    @Test
    fun proxyAppsRowIsPlainSwitchPreference() {
        val xml = readSource("src/main/res/xml/global_preferences.xml")
        val start = xml.indexOf("app:key=\"proxyApps\"")
        assertTrue("proxyApps entry missing", start >= 0)
        val tagStart = xml.lastIndexOf("<", start)
        val tag = xml.substring(tagStart, xml.indexOf(">", start))
        assertTrue("proxyApps must be a plain SwitchPreference, not the split subclass",
            tag.startsWith("<SwitchPreference"))
        assertFalse("split subclass must not be used anywhere",
            xml.contains("ProxyAppsSwitchPreference"))
    }

    @Test
    fun settingsFragmentRestoresRowTapOpensPage() {
        val src = readSource("src/main/java/io/nekohasekai/sagernet/ui/SettingsPreferenceFragment.kt")
        assertTrue("field must be a plain SwitchPreference",
            src.contains("isProxyApps: SwitchPreference"))
        assertFalse("split subclass must not be referenced",
            src.contains("ProxyAppsSwitchPreference"))
        assertFalse("onRowClick wiring must be gone", src.contains("onRowClick"))
        val changeStart = src.indexOf("isProxyApps.setOnPreferenceChangeListener")
        assertTrue("change listener missing", changeStart >= 0)
        val changeEnd = src.indexOf("}", src.indexOf("newValue", changeStart))
        val changeBody = src.substring(changeStart, changeEnd)
        assertTrue("change listener must open the app list again",
            changeBody.contains("AppManagerActivity"))
        assertTrue("dirty flag kept", changeBody.contains("DataStore.dirty = true"))
    }

    private fun appManagerSource(): String {
        return readSource("src/main/java/io/nekohasekai/sagernet/ui/AppManagerActivity.kt")
    }

    @Test
    fun openingPageAutoEnablesProxyAppsAgain() {
        val src = appManagerSource()
        val createStart = src.indexOf("override fun onCreate(savedInstanceState: Bundle?)")
        assertTrue("onCreate missing", createStart >= 0)
        val createEnd = src.indexOf("binding.bypassGroup.setOnCheckedChangeListener", createStart)
        assertTrue("onCreate body end missing", createEnd > createStart)
        val onCreate = src.substring(createStart, createEnd)
        assertTrue("onCreate must auto-enable proxyApps again",
            onCreate.contains("if (!DataStore.proxyApps)"))
        assertTrue("onCreate must set proxyApps = true again",
            onCreate.contains("DataStore.proxyApps = true"))
    }

    @Test
    fun initialChipFollowsModeOnly() {
        val src = appManagerSource()
        val checkStart = src.indexOf("binding.bypassGroup.check(")
        assertTrue("initial chip check missing", checkStart >= 0)
        val checkEnd = src.indexOf(")", src.indexOf("else ->", checkStart))
        assertTrue("initial chip check end missing", checkEnd > checkStart)
        val check = src.substring(checkStart, checkEnd)
        assertFalse("initial check must not select Disable",
            check.contains("appProxyModeDisable"))
        assertTrue("Block branch kept", check.contains("DataStore.appBlock -> R.id.appProxyModeBlock"))
        assertTrue("Bypass branch kept", check.contains("DataStore.bypass -> R.id.appProxyModeBypass"))
    }
}
