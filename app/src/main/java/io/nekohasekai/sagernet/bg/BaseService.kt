package io.nekohasekai.sagernet.bg

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.nekohasekai.sagernet.Action
import io.nekohasekai.sagernet.BootReceiver
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.aidl.ISagerNetService
import io.nekohasekai.sagernet.aidl.ISagerNetServiceCallback
import io.nekohasekai.sagernet.bg.proto.ProxyInstance
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.plugin.PluginManager
import io.nekohasekai.sagernet.utils.DefaultNetworkListener
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import libcore.Libcore
import moe.matsuri.nb4a.Protocols
import moe.matsuri.nb4a.utils.Util
import java.net.UnknownHostException

class BaseService {

    enum class State(
        val canStop: Boolean = false,
        val started: Boolean = false,
        val connected: Boolean = false,
    ) {
        /**
         * Idle state is only used by UI and will never be returned by BaseService.
         */
        Idle, Connecting(true, true, false), Connected(true, true, true), Stopping, Stopped,
    }

    interface ExpectedException

    class Data internal constructor(private val service: Interface) {
        var state = State.Stopped
        var proxy: ProxyInstance? = null
        var notification: ServiceNotification? = null

        val receiver = broadcastReceiver { ctx, intent ->
            when (intent.action) {
                Action.RELOAD -> service.reload()
                // Action.SWITCH_WAKE_LOCK -> runOnDefaultDispatcher { service.switchWakeLock() }
                Action.RESET_UPSTREAM_CONNECTIONS -> runOnDefaultDispatcher {
                    Libcore.resetAllConnections(true)
                    runOnMainDispatcher {
                        Util.collapseStatusBar(ctx)
                        Toast.makeText(ctx, "Reset upstream connections done", Toast.LENGTH_SHORT)
                            .show()
                    }
                }

                else -> service.stopRunner()
            }
        }

        // System broadcasts arrive from system_server, which does not
        // hold this app's signature-level SERVICE permission, so they
        // can never reach the receiver above (#1142). This second
        // receiver is registered without a broadcast permission and
        // only listens for system actions.
        val systemReceiver = broadcastReceiver { _, intent ->
            when (intent.action) {
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    if (DataStore.wakeResetConnections) {
                        Libcore.resetAllConnections(true)
                    }
                }

                PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (SagerNet.power.isDeviceIdleMode) {
                            proxy?.box?.sleep()
                        } else {
                            proxy?.box?.wake()
                            if (DataStore.wakeResetConnections) {
                                Libcore.resetAllConnections(true)
                            }
                        }
                    }
                }

                Intent.ACTION_SHUTDOWN -> service.persistStats()
            }
        }
        var closeReceiverRegistered = false
        var systemReceiverRegistered = false

        val binder = Binder(this)
        // Serializes the start/stop lifecycle (C3). The start sequence holds
        // the guard's lock for its whole duration, and a stop cancels an
        // in-flight start before cleaning up, so the two can never
        // interleave. Replaces the old `connectingJob` field, which was
        // declared but never assigned, making the old "cancel first" step
        // in stopRunner dead code.
        val lifecycleGuard = ServiceLifecycleGuard()

        fun changeState(s: State, msg: String? = null) {
            if (state == s && msg == null) return
            state = s
            DataStore.serviceState = s
            binder.stateChanged(s, msg)
        }
    }

    class Binder(private var data: Data? = null) : ISagerNetService.Stub(), CoroutineScope,
        AutoCloseable {
        private val callbacks = object : RemoteCallbackList<ISagerNetServiceCallback>() {
            override fun onCallbackDied(callback: ISagerNetServiceCallback?, cookie: Any?) {
                super.onCallbackDied(callback, cookie)
            }
        }

        val callbackIdMap = mutableMapOf<ISagerNetServiceCallback, Int>()

        override val coroutineContext = Dispatchers.Main.immediate + Job()

        override fun getState(): Int = (data?.state ?: State.Idle).ordinal
        override fun getProfileName(): String = data?.proxy?.displayProfileName ?: "Idle"

        override fun registerCallback(cb: ISagerNetServiceCallback, id: Int) {
            if (id == SagerConnection.CONNECTION_ID_RESTART_BG) {
                Runtime.getRuntime().exit(0)
                return
            }
            if (!callbackIdMap.contains(cb)) {
                callbacks.register(cb)
            }
            callbackIdMap[cb] = id
        }

        private val broadcastMutex = Mutex()

        suspend fun broadcast(work: (ISagerNetServiceCallback) -> Unit) {
            broadcastMutex.withLock {
                val count = callbacks.beginBroadcast()
                try {
                    repeat(count) {
                        try {
                            work(callbacks.getBroadcastItem(it))
                        } catch (_: RemoteException) {
                        } catch (_: Exception) {
                        }
                    }
                } finally {
                    callbacks.finishBroadcast()
                }
            }
        }

        override fun unregisterCallback(cb: ISagerNetServiceCallback) {
            callbackIdMap.remove(cb)
            callbacks.unregister(cb)
        }

        override fun urlTest(): Int {
            if (data?.proxy?.box == null) {
                error("core not started")
            }
            try {
                return Libcore.urlTest(
                    data!!.proxy!!.box, DataStore.connectionTestURL, 3000
                )
            } catch (e: Exception) {
                error(Protocols.genFriendlyMsg(e.readableMessage))
            }
        }

        fun stateChanged(s: State, msg: String?) = launch {
            val profileName = profileName
            broadcast { it.stateChanged(s.ordinal, profileName, msg) }
        }

        fun missingPlugin(pluginName: String) = launch {
            val profileName = profileName
            broadcast { it.missingPlugin(profileName, pluginName) }
        }

        override fun close() {
            callbacks.kill()
            cancel()
            data = null
        }
    }

    interface Interface {
        val data: Data
        val tag: String
        fun createNotification(profileName: String): ServiceNotification

        fun onBind(intent: Intent): IBinder? =
            if (intent.action == Action.SERVICE) data.binder else null

        fun reload() {
            if (DataStore.selectedProxy == 0L) {
                stopRunner(false, (this as Context).getString(R.string.profile_empty))
            }
            if (canReloadSelector()) {
                val ent = SagerDatabase.proxyDao.getById(DataStore.selectedProxy)
                val tag = data.proxy!!.config.profileTagMap[ent?.id] ?: ""
                if (tag.isNotBlank() && ent != null) {
                    // select from GUI
                    data.proxy!!.box.selectOutbound(tag)
                    // or select from webui
                    // => selector_OnProxySelected
                }
                return
            }
            val s = data.state
            when {
                s == State.Stopped -> startRunner()
                s.canStop -> stopRunner(true)
                else -> Logs.w("Illegal state $s when invoking use")
            }
        }

        fun canReloadSelector(): Boolean {
            if ((data.proxy?.config?.selectorGroupId ?: -1L) < 0) return false
            val ent = SagerDatabase.proxyDao.getById(DataStore.selectedProxy) ?: return false
            val tmpBox = ProxyInstance(ent)
            tmpBox.buildConfigTmp()
            if (tmpBox.lastSelectorGroupId == data.proxy?.lastSelectorGroupId) {
                return true
            }
            return false
        }

        suspend fun startProcesses() {
            data.proxy!!.launch()
        }

        fun startRunner() {
            this as Context
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(Intent(this, javaClass))
            else startService(Intent(this, javaClass))
        }

        fun killProcesses() {
            data.proxy?.close()
            wakeLock?.apply {
                release()
                wakeLock = null
            }
            runOnDefaultDispatcher {
                DefaultNetworkListener.stop(this)
            }
        }

        fun stopRunner(restart: Boolean = false, msg: String? = null) {
            DataStore.baseService = null
            DataStore.vpnService = null

            if (data.state == State.Stopping) return
            data.notification?.destroy()
            data.notification = null
            this as Service

            data.changeState(State.Stopping)

            runOnMainDispatcher {
                // Cancel an in-flight start first and wait for it to unwind;
                // the start sequence holds the guard's lock, so the clean-up
                // below can never run concurrently with it.
                data.lifecycleGuard.runStop {
                    // we use a coroutineScope here to allow clean-up in parallel
                    coroutineScope {
                        killProcesses()
                        val data = data
                        if (data.closeReceiverRegistered) {
                            unregisterReceiver(data.receiver)
                            data.closeReceiverRegistered = false
                        }
                        if (data.systemReceiverRegistered) {
                            unregisterReceiver(data.systemReceiver)
                            data.systemReceiverRegistered = false
                        }
                        data.proxy = null
                    }
                }

                // change the state
                data.changeState(State.Stopped, msg)
                // stop the service if nothing has bound to it
                if (restart) startRunner() else {
                    stopSelf()
                }
            }
        }

        fun persistStats() {
            // TODO NEW save app stats?
        }

        // networks
        var upstreamInterfaceName: String?

        suspend fun preInit() {
            DefaultNetworkListener.start(this) {
                SagerNet.connectivity.getLinkProperties(it)?.also { link ->
                    SagerNet.underlyingNetwork = it
                    DataStore.vpnService?.updateUnderlyingNetwork()
                    //
                    val oldName = upstreamInterfaceName
                    if (oldName != link.interfaceName) {
                        upstreamInterfaceName = link.interfaceName
                    }
                    if (oldName != null && upstreamInterfaceName != null && oldName != upstreamInterfaceName) {
                        Logs.d("Network changed: $oldName -> $upstreamInterfaceName")
                        if (DataStore.networkChangeResetConnections) {
                            Libcore.resetAllConnections(true)
                        }
                    }
                }
            }
        }

        var wakeLock: PowerManager.WakeLock?
        fun acquireWakeLock()

        suspend fun lateInit() {
            wakeLock?.apply {
                release()
                wakeLock = null
            }

            if (DataStore.acquireWakeLock) {
                acquireWakeLock()
                data.notification?.postNotificationWakeLockStatus(true)
            } else {
                data.notification?.postNotificationWakeLockStatus(false)
            }
        }

        fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
            DataStore.baseService = this

            val data = data
            // START_STICKY (C2): if the system kills the :bg process, the
            // service is restarted with a null intent so the VPN comes back
            // up instead of silently leaking traffic onto the direct
            // connection. (The old START_NOT_STICKY left the user unprotected
            // after a low-memory kill, with no notice.)
            if (data.state != State.Stopped) return Service.START_STICKY
            val profile = SagerDatabase.proxyDao.getById(DataStore.selectedProxy)
            this as Context
            if (profile == null) { // gracefully shutdown: https://stackoverflow.com/q/47337857/2245107
                data.notification = createNotification("")
                stopRunner(false, getString(R.string.profile_empty))
                return Service.START_NOT_STICKY
            }

            val proxy = ProxyInstance(profile, this)
            data.proxy = proxy
            BootReceiver.enabled = DataStore.persistAcrossReboot
            if (!data.closeReceiverRegistered) {
                val filter = IntentFilter().apply {
                    addAction(Action.RELOAD)
                    addAction(Action.CLOSE)
                    // addAction(Action.SWITCH_WAKE_LOCK)
                    addAction(Action.RESET_UPSTREAM_CONNECTIONS)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(
                        data.receiver,
                        filter,
                        "$packageName.SERVICE",
                        null,
                        Context.RECEIVER_EXPORTED
                    )
                } else {
                    registerReceiver(
                        data.receiver,
                        filter,
                        "$packageName.SERVICE",
                        null
                    )
                }
                data.closeReceiverRegistered = true
            }
            if (!data.systemReceiverRegistered) {
                val systemFilter = IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_USER_PRESENT)
                    addAction(Intent.ACTION_SHUTDOWN)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(
                        data.systemReceiver,
                        systemFilter,
                        null,
                        null,
                        Context.RECEIVER_EXPORTED
                    )
                } else {
                    registerReceiver(
                        data.systemReceiver,
                        systemFilter,
                        null,
                        null
                    )
                }
                data.systemReceiverRegistered = true
            }

            // With START_STICKY a restart after the system killed the
            // process arrives with a null intent. Remember it so the user
            // can be told the VPN was restored automatically.
            val restoredAfterKill = intent == null

            data.changeState(State.Connecting)
            val startJob = runOnMainDispatcher {
                data.lifecycleGuard.runStart {
                    try {
                        data.notification = createNotification(ServiceNotification.genTitle(profile))

                        Executable.killAll()    // clean up old processes
                        preInit()
                        proxy.init()
                        DataStore.currentProfile = profile.id

                        proxy.processes = GuardedProcessPool {
                            Logs.w(it)
                            stopRunner(false, it.readableMessage)
                        }

                        startProcesses()
                        data.changeState(State.Connected)

                        if (restoredAfterKill) {
                            postRestoredNotification(profile.displayName())
                        }

                        lateInit()
                    } catch (_: CancellationException) { // if the job was cancelled, it is canceller's responsibility to call stopRunner
                    } catch (_: UnknownHostException) {
                        stopRunner(false, getString(R.string.invalid_server))
                    } catch (e: PluginManager.PluginNotFoundException) {
                        Toast.makeText(this@Interface, e.readableMessage, Toast.LENGTH_SHORT).show()
                        Logs.w(e)
                        data.binder.missingPlugin(e.plugin)
                        stopRunner(false, null)
                    } catch (exc: Throwable) {
                        if (exc.javaClass.name.endsWith("proxyerror")) {
                            // error from golang
                            Logs.w(exc.readableMessage)
                        } else {
                            Logs.w(exc)
                        }
                        stopRunner(
                            false, "${getString(R.string.service_failed)}: ${exc.readableMessage}"
                        )
                    }
                }
            }
            // Register the launch job right away: a stop arriving before
            // the start coroutine has entered runStart must still see it
            // and cancel it, instead of letting the start run after the
            // stop (see ServiceLifecycleGuard.noteStartJob).
            data.lifecycleGuard.noteStartJob(startJob)
            return Service.START_STICKY
        }

        /**
         * Tells the user the VPN was restored automatically after the
         * system killed the service process (C2). Best-effort: silently
         * does nothing if notifications are not permitted.
         */
        fun postRestoredNotification(profileName: String) {
            this as Context
            try {
                // The :bg process never runs SagerNet.updateNotificationChannels()
                // (it only runs in the main process), so create the channel
                // here: posting to a channel that does not exist yet silently
                // drops the notification, which would defeat the whole
                // point of this notice. Creating an existing channel is a
                // no-op.
                if (Build.VERSION.SDK_INT >= 26) {
                    SagerNet.notification.createNotificationChannel(
                        NotificationChannel(
                            "service-restored",
                            getString(R.string.service_restored_channel),
                            NotificationManager.IMPORTANCE_DEFAULT
                        )
                    )
                }
                val nm = NotificationManagerCompat.from(this)
                val notification = NotificationCompat.Builder(this, "service-restored")
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText(getString(R.string.service_restored, profileName))
                    .setSmallIcon(R.drawable.ic_service_active)
                    .setCategory(NotificationCompat.CATEGORY_SERVICE)
                    .setContentIntent(SagerNet.configureIntent(this))
                    .setAutoCancel(true)
                nm.notify(3, notification.build())
            } catch (e: Exception) {
                Logs.w(e)
            }
        }
    }

}