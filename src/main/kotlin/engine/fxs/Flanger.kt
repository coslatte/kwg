package engine.fxs

import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

class Flanger(
    sampleRate: UInt,
    rateHz: Double = 0.5,
    var depthMs: Double = 3.0,
    var feedback: Double = 0.6,
    var mix: Double = 0.5
) {
    private val srDouble = sampleRate.toDouble()

    private val maxDelaySamples = (srDouble * 0.02).toInt()
    private val delayBuffer = DoubleArray(maxDelaySamples)

    private var writeIndex = 0
    private var lfoPhase = 0.0

    var rateHz = rateHz
        set(value) {
            field = value
            lfoPhaseInc = 2.0 * PI * value / srDouble
        }

    private var lfoPhaseInc = 2.0 * PI * rateHz / srDouble

    fun process(input: Double): Double {
        val lfo = (sin(lfoPhase) + 1.0) / 2.0
        lfoPhase += lfoPhaseInc
        if (lfoPhase > 2.0 * PI) lfoPhase -= 2.0 * PI

        val depthSamples = depthMs * 0.001 * srDouble
        val currentDelaySamples = 1.0 + (lfo * depthSamples)

        var readIndex = writeIndex - currentDelaySamples
        if (readIndex < 0) readIndex += maxDelaySamples

        val indexInt = floor(readIndex).toInt()
        val fraction = readIndex - indexInt

        val indexA = wrap(indexInt)
        val indexB = wrap(indexInt + 1)

        val delayedSample = ((1.0 - fraction) * delayBuffer[indexA]) + (fraction * delayBuffer[indexB])

        delayBuffer[writeIndex] = input + (delayedSample * feedback)
        writeIndex = (writeIndex + 1) % maxDelaySamples

        return (input * (1.0 - mix)) + (delayedSample * mix)
    }

    /**
     * Keeps the read index inside the delay line even when [depthMs] is pushed
     * past it: Kotlin's `%` keeps the sign, so a bare modulo can go negative.
     */
    private fun wrap(index: Int): Int = ((index % maxDelaySamples) + maxDelaySamples) % maxDelaySamples
}