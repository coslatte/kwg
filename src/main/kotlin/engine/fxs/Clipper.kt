package engine.fxs

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.tanh

class Clipper(
    private val ceilingDb: Double = -0.0,
    private val softness: Double = 0.5
) {
    private val ceiling = 10.0.pow(ceilingDb / 20.0)
    private val linearZone = (1.0 - softness) * ceiling

    fun process(x: Double): Double {
        if (softness <= 0.0) return x.coerceIn(-ceiling, ceiling)

        val absX = abs(x)
        if (absX <= linearZone) return x

        val curved = linearZone +
            (ceiling - linearZone) *
            tanh((absX - linearZone) / (ceiling - linearZone))

        return sign(x) * curved
    }
}