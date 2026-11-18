package io.nekohasekai.sagernet.bg

import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes the VPN service start/stop lifecycle (C3).
 *
 * Background: `BaseService` used to launch the start sequence with
 * `GlobalScope.launch` and never stored the resulting `Job`, so
 * `stopRunner`'s "cancel the in-flight start first" step was dead code.
 * A stop requested mid-start could then interleave with the start
 * sequence (`killProcesses()` racing `startProcesses()`), leaving the
 * VPN half-stopped or still running after a stop.
 *
 * This guard owns the two pieces of coordination state:
 * - a [Mutex] held for the whole start sequence and for the whole stop
 *   cleanup, so the two can never interleave;
 * - the start coroutine's [Job], so a stop can cancel an in-flight
 *   start and wait for it to unwind before cleaning up.
 *
 * The job must be registered with [noteStartJob] immediately after the
 * start coroutine is launched, before it has necessarily entered
 * [runStart]: otherwise a stop running in that window would see no
 * start job, take the mutex first, and let the start run to completion
 * after the stop. [runStart] records the same job again when it starts.
 *
 * Contract: [runStop] must be called from a coroutine other than the one
 * executing [runStart] (in `BaseService` the stop work always runs in the
 * coroutine launched by `stopRunner`, never inside the start coroutine).
 */
class ServiceLifecycleGuard {
    private val mutex = Mutex()
    private var startJob: Job? = null

    /**
     * Registers the start coroutine's job at launch time, before the
     * coroutine has necessarily entered [runStart]. A completed job is
     * ignored (the start already finished, so there is nothing to
     * cancel). [runStart] records the same job again when it starts.
     */
    fun noteStartJob(job: Job) {
        if (job.isActive) startJob = job
    }

    /**
     * Runs the service start sequence, holding the lifecycle lock for its
     * whole duration. Records the running coroutine's job so a concurrent
     * [runStop] can cancel it.
     */
    suspend fun <T> runStart(block: suspend () -> T): T {
        return mutex.withLock {
            startJob = currentCoroutineContext()[Job]
            try {
                block()
            } finally {
                startJob = null
            }
        }
    }

    /**
     * Runs the service stop sequence. Cancels an in-flight start first and
     * waits for it to unwind; the start coroutine releases the mutex when
     * it unwinds, so the lock below cannot deadlock with it.
     */
    suspend fun runStop(block: suspend () -> Unit) {
        startJob?.cancelAndJoin()
        mutex.withLock {
            block()
        }
    }
}
