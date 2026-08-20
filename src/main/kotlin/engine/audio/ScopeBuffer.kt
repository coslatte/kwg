package engine.audio

import kotlin.math.abs
import kotlin.math.min

/**
 * Thread-safe ring buffer of the most recent audio samples, used to feed
 * the real-time scope display in the UI.
 */
class ScopeBuffer(private val capacity: Int) {
    private val data = FloatArray(capacity)
    private var writePos = 0
    private var size = 0

    /** Loudest absolute sample seen since the last [takePeak], for the level meter. */
    private var peak = 0.0f

    @Synchronized
    fun push(sample: Double) {
        val value = sample.toFloat()

        data[writePos] = value
        writePos = (writePos + 1) % capacity
        if (size < capacity) size++

        val magnitude = abs(value)
        if (magnitude > peak) peak = magnitude
    }

    /** The last [count] samples in chronological order (fewer if the buffer isn't full yet). */
    @Synchronized
    fun snapshot(count: Int = capacity): FloatArray {
        val taken = min(count, size)
        val out = FloatArray(taken)
        val start = (writePos - taken + capacity) % capacity

        for (i in 0 until taken) {
            out[i] = data[(start + i) % capacity]
        }
        return out
    }

    /** Reads and resets the peak, so each UI frame reports the peak of that frame. */
    @Synchronized
    fun takePeak(): Float {
        val value = peak
        peak = 0.0f
        return value
    }
}
