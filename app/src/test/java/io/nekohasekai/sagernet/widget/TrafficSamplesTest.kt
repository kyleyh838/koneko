package io.nekohasekai.sagernet.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class TrafficSamplesTest {

    @Test
    fun `push keeps insertion order`() {
        val samples = TrafficSamples()
        samples.push(1f)
        samples.push(2f)
        samples.push(3f)
        assertEquals(listOf(1f, 2f, 3f), samples.snapshot())
    }

    @Test
    fun `push trims to 60 samples`() {
        val samples = TrafficSamples()
        repeat(65) { samples.push(it.toFloat()) }
        assertEquals(60, samples.size)
        assertEquals(5f, samples.snapshot().first(), 0f)
        assertEquals(64f, samples.snapshot().last(), 0f)
    }

    @Test
    fun `max is zero when empty`() {
        assertEquals(0f, TrafficSamples().max(), 0f)
    }

    @Test
    fun `max tracks the largest sample`() {
        val samples = TrafficSamples()
        samples.push(10f)
        samples.push(30f)
        samples.push(20f)
        assertEquals(30f, samples.max(), 0f)
    }

    @Test
    fun `snapshot is a copy`() {
        val samples = TrafficSamples()
        samples.push(1f)
        val snapshot = samples.snapshot()
        samples.push(2f)
        assertEquals(1, snapshot.size)
    }

    @Test
    fun `custom capacity trims to that size`() {
        val samples = TrafficSamples(5)
        repeat(6) { samples.push(it.toFloat()) }
        assertEquals(5, samples.size)
        assertEquals(5, samples.capacity)
        assertEquals(listOf(1f, 2f, 3f, 4f, 5f), samples.snapshot())
    }
}
