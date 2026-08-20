package engine

import engine.fxs.BiquadFilter
import engine.fxs.Clipper
import engine.fxs.Distortion
import engine.fxs.Flanger
import engine.fxs.SpecialFx

/**
 * The current sound design: one live instance of every fx module plus the flags
 * saying which ones are in the chain.
 *
 * The UI mutates these instances directly, so a slider move is heard on the very
 * next sample without rebuilding (and therefore resetting) any delay line or
 * filter state. For file rendering, [snapshotChain] copies the values into fresh
 * modules built for the target sample rate, which keeps the exported file
 * sounding like what was played and leaves the live chain untouched.
 */
class Patch(private val sampleRate: UInt = 44100u) {

    val flanger = Flanger(sampleRate = sampleRate)
    val distortion = Distortion()
    val filter = BiquadFilter(sampleRate = sampleRate, cutoffFreq = 8_000.0)
    val clipper = Clipper()

    /** The merged fx keeps private module instances so it can run next to the standalone ones. */
    val specialFx = SpecialFx(
        flanger = Flanger(sampleRate = sampleRate, rateHz = 0.2, depthMs = 4.0, feedback = 0.4, mix = 0.6),
        distortion = Distortion(drive = 12.0, saturation = 0.5),
        feedback = 0.5,
        mix = 0.9
    )

    var specialFxEnabled = false
    var flangerEnabled = false
    var distortionEnabled = false
    var filterEnabled = false
    var clipperEnabled = true

    /** The chain the live engine plays through; [syncEnabled] attaches or bypasses modules. */
    val liveChain = SignalChain(volume = 0.8)

    init {
        syncEnabled()
    }

    /** Applies the enable flags to [liveChain]. A bypassed module keeps its settings. */
    fun syncEnabled() {
        liveChain.specialFx = specialFx.takeIf { specialFxEnabled }
        liveChain.flanger = flanger.takeIf { flangerEnabled }
        liveChain.distortion = distortion.takeIf { distortionEnabled }
        liveChain.filter = filter.takeIf { filterEnabled }
        liveChain.clipper = clipper.takeIf { clipperEnabled }
    }

    /**
     * An independent copy of the enabled chain, with time-based modules rebuilt
     * for [targetSampleRate] so delay times and cutoffs land where they should.
     */
    fun snapshotChain(targetSampleRate: UInt = sampleRate): SignalChain = SignalChain(
        volume = liveChain.volume,
        specialFx = if (specialFxEnabled) {
            SpecialFx(
                flanger = copyOf(specialFx.flanger, targetSampleRate),
                distortion = copyOf(specialFx.distortion),
                feedback = specialFx.feedback,
                mix = specialFx.mix
            )
        } else null,
        flanger = if (flangerEnabled) copyOf(flanger, targetSampleRate) else null,
        distortion = if (distortionEnabled) copyOf(distortion) else null,
        filter = if (filterEnabled) {
            BiquadFilter(
                sampleRate = targetSampleRate,
                cutoffFreq = filter.cutoffFreq,
                q = filter.q
            )
        } else null,
        clipper = if (clipperEnabled) {
            Clipper(ceilingDb = clipper.ceilingDb, softness = clipper.softness)
        } else null
    )

    private fun copyOf(source: Flanger, targetSampleRate: UInt) = Flanger(
        sampleRate = targetSampleRate,
        rateHz = source.rateHz,
        depthMs = source.depthMs,
        feedback = source.feedback,
        mix = source.mix
    )

    private fun copyOf(source: Distortion) = Distortion(
        drive = source.drive,
        saturation = source.saturation,
        mix = source.mix,
        oversample = source.oversample
    )
}
