package io.nekohasekai.sagernet.widget

/** Fixed-size ring buffer of recent throughput samples (bytes/sec) for the dashboard sparkline. */
class TrafficSamples(private val maxSamples: Int = 60) {

    private val samples = ArrayDeque<Float>()

    fun push(value: Float) {
        samples.addLast(value)
        while (samples.size > maxSamples) samples.removeFirst()
    }

    fun snapshot(): List<Float> = samples.toList()

    fun max(): Float = samples.maxOrNull() ?: 0f

    val size: Int get() = samples.size

    val capacity: Int get() = maxSamples
}
