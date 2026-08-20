package engine.fxs

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.tanh

class Clipper(
    ceilingDb: Double = -0.0,
    softness: Double = 0.5
) {
    var ceilingDb = ceilingDb
        set(value) {
            field = value
            recompute()
        }

    var softness = softness
        set(value) {
            field = value
            recompute()
        }

    private var ceiling = 1.0
    private var linearZone = 0.5

    init {
        recompute()
    }

    private fun recompute() {
        ceiling = 10.0.pow(ceilingDb / 20.0)
        linearZone = (1.0 - softness) * ceiling
    }

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