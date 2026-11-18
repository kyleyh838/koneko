package io.nekohasekai.sagernet

/**
 * One-shot guard for [QuickToggleShortcut].
 *
 * The shortcut binds the service with BIND_AUTO_CREATE. After it stops the
 * service, the system can recreate the service while the binding is still
 * alive and call onServiceConnected a second time; without this guard that
 * callback sees Stopped and starts the VPN again, so one tap disconnects
 * and reconnects by itself (intermittent, timing-dependent). Each shortcut
 * launch gets its own guard instance, so exactly one handling runs.
 */
class QuickToggleGuard {
    private var handled = false

    /**
     * Returns true the first time it is called, false on every later call.
     */
    fun tryHandle(): Boolean {
        if (handled) return false
        handled = true
        return true
    }
}
