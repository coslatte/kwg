package engine.fxs

/**
 * Merged flanger + distortion special effect.
 * The distorted signal feeds back into the flanger's delay line,
 * producing resonant growls and warped, metallic textures.
 *
 * Owns its own [flanger] and [distortion] instances so it can run alongside
 * the standalone modules in the same chain without sharing their state.
 * All parameters are mutable so they can be tweaked in real time.
 */
class SpecialFx(
    val flanger: Flanger,
    val distortion: Distortion,
    var feedback: Double = 0.4,
    var mix: Double = 1.0
) {

    private var feedbackSample = 0.0

    fun process(input: Double): Double {
        val flanged = flanger.process(input + (feedbackSample * feedback))
        val distorted = distortion.process(flanged)
        feedbackSample = distorted
        return (input * (1.0 - mix)) + (distorted * mix)
    }
}
