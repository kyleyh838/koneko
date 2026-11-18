package io.nekohasekai.sagernet

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test for the QuickToggleShortcut double-fire (v1.4.23).
 *
 * The shortcut binds with BIND_AUTO_CREATE. After it stops the service, the
 * system can recreate the service while the binding is still alive and call
 * onServiceConnected a second time; without a one-shot guard that callback
 * sees Stopped and starts the VPN again, so one tap disconnects and
 * reconnects by itself (intermittent, timing-dependent). The guard allows
 * exactly one handling per shortcut launch.
 */
class QuickToggleGuardTest {

    @Test
    fun `first handling is allowed`() {
        assertTrue(QuickToggleGuard().tryHandle())
    }

    @Test
    fun `second handling is refused`() {
        val guard = QuickToggleGuard()
        assertTrue(guard.tryHandle())
        assertFalse(guard.tryHandle())
    }

    @Test
    fun `further handlings stay refused`() {
        val guard = QuickToggleGuard()
        guard.tryHandle()
        guard.tryHandle()
        assertFalse(guard.tryHandle())
    }

    @Test
    fun `each guard instance is independent`() {
        val first = QuickToggleGuard()
        assertTrue(first.tryHandle())
        assertTrue(QuickToggleGuard().tryHandle())
    }
}
