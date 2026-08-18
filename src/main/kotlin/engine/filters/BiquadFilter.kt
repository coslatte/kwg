package engine.filters

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class BiquadFilter(
    sampleRate: UInt,
    cutoffFreq: Double,
    q: Double = 0.7071
) {
    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    private val a0: Double
    private val a1: Double
    private val a2: Double
    private val b0: Double
    private val b1: Double
    private val b2: Double

    init {
        val w0 = 2.0 * PI * cutoffFreq / sampleRate.toDouble()
        val alpha = sin(w0) / (2.0 * q)
        val cosW0 = cos(w0)

        b0 = (1.0 - cosW0) / 2.0
        b1 = 1.0 - cosW0
        b2 = (1.0 - cosW0) / 2.0
        a0 = 1.0 + alpha
        a1 = -2.0 * cosW0
        a2 = 1.0 - alpha
    }

    fun process(x: Double): Double {
        val y = (b0 / a0) * x + (b1 / a0) * x1 + (b2 / a0) * x2 - (a1 / a0) * y1 - (a2 / a0) * y2

        x2 = x1
        x1 = x
        y2 = y1
        y1 = y

        return y
    }
}