package engine.fxs

import kotlin.math.tanh

class Distortion(
    drive: Double = 1.0,
    saturation: Double = 0.7,
    mix: Double = 1.0,
    oversample: Int = 4
) {
    var drive = drive
    var saturation = saturation
    var mix = mix
    var oversample = oversample

    private var previousInput = 0.0

    fun process(input: Double): Double {
        var wet = 0.0
        for (i in 0 until oversample) {
            val t = (i + 1).toDouble() / oversample.toDouble()
            val interpolated = previousInput + (input - previousInput) * t
            wet += saturate(interpolated * drive) / oversample.toDouble()
        }
        previousInput = input
        return (input * (1.0 - mix)) + (wet * mix)
    }

    /**
     * Blends a soft tanh curve with a hard clip:
     * saturation = 0.0 -> pure tanh (smooth, warm)
     * saturation = 1.0 -> hard clip (aggressive, fizzy)
     */
    private fun saturate(x: Double): Double {
        val soft = tanh(x)
        val hard = x.coerceIn(-1.0, 1.0)
        return (soft * (1.0 - saturation)) + (hard * saturation)
    }
}