package io.nekohasekai.sagernet.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the v1.4.34 batch: two real-device crashes found
 * testing v1.4.33 (About NPE, batch-test rotation crash).
 *
 * Same file-parsing style as the other tests in this package: the
 * production code touches Android APIs, so the tests assert on the
 * source text instead of running it.
 */
class Fixes1434Test {

    private fun readSource(relativePath: String): String {
        val file = File(relativePath)
        assertTrue("source file missing: $relativePath", file.exists())
        return file.readText()
    }

    // NOTE: the `version is 1_4_34` pin lived here in v1.4.34; it was
    // removed in v1.4.35 — the current version is pinned by
    // Fixes1435Test (`version is 1_4_35`) instead.

    @Test
    fun `about list accepts null activity context`() {
        // Crash log 09:22:57: ListTask passes fragment.getActivity(),
        // null when detached; the Kotlin non-null parameter check threw
        // NPE before the method body ran. A null context must return an
        // empty list (a detached fragment has nothing to show).
        val about = readSource("src/main/java/io/nekohasekai/sagernet/ui/AboutFragment.kt")
        assertTrue(
            "signature is not nullable",
            about.contains("override fun getMaterialAboutList(activityContext: Context?)")
        )
        assertTrue(
            "no null guard returning empty list",
            about.contains("activityContext ?: return MaterialAboutList.Builder().build()")
        )
    }

    @Test
    fun `test cancel dismiss is guarded`() {
        // Crash log 21:00:54: dialog.dismiss() on a window whose Activity
        // died with rotation throws IllegalArgumentException on the main
        // thread. Both batch tests must dismiss only inside a guard.
        val config = readSource("src/main/java/io/nekohasekai/sagernet/ui/ConfigurationFragment.kt")
        val pingTest = config.substringAfter("fun pingTest(").substringBefore("fun urlTest()")
        val urlTest = config.substringAfter("fun urlTest()").substringBefore("inner class GroupPagerAdapter")
        for ((name, fn) in listOf("pingTest" to pingTest, "urlTest" to urlTest)) {
            assertTrue("$name: no guarded dismiss", fn.contains("if (dialog.isShowing) dialog.dismiss()"))
            assertTrue(
                "$name: no IllegalArgumentException catch",
                fn.contains("catch (e: IllegalArgumentException)")
            )
        }
    }

    @Test
    fun `test dialog dismissed on fragment destroy`() {
        // Crash log 21:00:49 WindowLeaked: the dialog shown by urlTest
        // was still attached when MainActivity was destroyed on rotation.
        // The dialog must be tracked and dismissed in onDestroy (not
        // onDestroyView: on a tab switch the fragment instance is kept by
        // ViewPager2 and the dialog must stay).
        val config = readSource("src/main/java/io/nekohasekai/sagernet/ui/ConfigurationFragment.kt")
        assertTrue(
            "no AlertDialog import",
            config.contains("import androidx.appcompat.app.AlertDialog")
        )
        assertTrue(
            "no activeTestDialog field",
            config.contains("private var activeTestDialog: AlertDialog? = null")
        )
        val tracked = config.split("activeTestDialog = dialog").size - 1
        assertTrue("dialog not tracked in both tests, found $tracked", tracked == 2)
        val onDestroy = config.substringAfter("override fun onDestroy()").substringBefore("super.onDestroy()")
        assertTrue(
            "onDestroy does not dismiss the dialog",
            onDestroy.contains("activeTestDialog") && onDestroy.contains("dismiss()")
        )
    }
}
