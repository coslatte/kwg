package engine

import engine.fxs.BiquadFilter
import engine.fxs.Clipper
import engine.fxs.Distortion
import engine.fxs.Flanger
import engine.fxs.SpecialFx

/**
 * Canonical DSP chain shared by file rendering and live playback.
 * Order: specialFx -> flanger -> distortion -> filter -> volume -> clipper.
 * All parameters are mutable so they can be tweaked in real time.
 */
class SignalChain(
    var volume: Double = 1.0,
    var specialFx: SpecialFx? = null,
    var flanger: Flanger? = null,
    var distortion: Distortion? = null,
    var filter: BiquadFilter? = null,
    var clipper: Clipper? = null
) {
    fun process(rawSample: Double): Double {
        var sample = rawSample
        specialFx?.let { sample = it.process(sample) }
        flanger?.let { sample = it.process(sample) }
        distortion?.let { sample = it.process(sample) }
        filter?.let { sample = it.process(sample) }
        sample *= volume
        clipper?.let { sample = it.process(sample) }
        return sample
    }
}