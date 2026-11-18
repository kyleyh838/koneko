package io.nekohasekai.sagernet.bg

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TDD tests for [ServiceLifecycleGuard] (C3: start/stop race in BaseService).
 *
 * The guard is the only part of the service lifecycle that can be tested on
 * the JVM: it must guarantee that a stop requested while a start is in
 * flight cancels the start first, and that the stop cleanup never runs
 * concurrently with the start sequence.
 */
class ServiceLifecycleGuardTest {

    @Test
    fun stopCancelsInFlightStartAndCleanupRunsAfterStartUnwinds() = runTest {
        val guard = ServiceLifecycleGuard()
        val entered = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()

        val startJob = launch {
            guard.runStart {
                entered.complete(Unit)
                try {
                    delay(Long.MAX_VALUE)
                } finally {
                    order.add("start-unwound")
                }
            }
        }
        entered.await()
        guard.runStop { order.add("cleanup") }
        startJob.join()

        assertEquals(listOf("start-unwound", "cleanup"), order)
    }

    @Test
    fun stopWithoutRunningStartRunsCleanupImmediately() = runTest {
        val guard = ServiceLifecycleGuard()
        var cleanupRan = false
        guard.runStop { cleanupRan = true }
        assertTrue(cleanupRan)
    }

    @Test
    fun stopCoroutineContinuesAfterCancellingStart() = runTest {
        val guard = ServiceLifecycleGuard()
        val entered = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()

        val startJob = launch {
            guard.runStart {
                entered.complete(Unit)
                try {
                    delay(Long.MAX_VALUE)
                } finally {
                    order.add("start-unwound")
                }
            }
        }
        entered.await()
        // Mirror BaseService.stopRunner: the stop work runs in its OWN
        // coroutine, and the code after runStop (state change / stopSelf)
        // must still execute once the start has unwound.
        val stopJob = launch {
            guard.runStop { order.add("cleanup") }
            order.add("stopped")
        }
        stopJob.join()
        startJob.join()

        assertEquals(listOf("start-unwound", "cleanup", "stopped"), order)
    }

    @Test
    fun stopBeforeStartEntersPreventsStartFromRunning() = runTest {
        val guard = ServiceLifecycleGuard()
        val order = mutableListOf<String>()

        val startJob = launch {
            guard.runStart {
                order.add("start-ran")
            }
        }
        // Mirror onStartCommand: register the launch job before the start
        // coroutine has necessarily entered runStart.
        guard.noteStartJob(startJob)
        guard.runStop { order.add("cleanup") }
        startJob.join()

        assertEquals(listOf("cleanup"), order)
    }

    @Test
    fun startAfterStopWorks() = runTest {
        val guard = ServiceLifecycleGuard()
        var runs = 0
        guard.runStart { runs++ }
        guard.runStop { runs++ }
        guard.runStart { runs++ }
        assertEquals(3, runs)
    }
}
