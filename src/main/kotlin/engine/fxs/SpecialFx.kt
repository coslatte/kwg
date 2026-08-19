package engine.fxs

/**
 * Merged flanger + distortion special effect.
 * The distorted signal feeds back into the flanger's delay line,
 * producing resonant growls and warped, metallic textures.
 */
class SpecialFx(
    private val flanger: Flanger,
    private val distortion: Distortion,
    private val feedback: Double = 0.4,
    private val mix: Double = 1.0
) {
    private var feedbackSample = 0.0

    fun process(input: Double): Double {
        val flanged = flanger.process(input + (feedbackSample * feedback))
        val distorted = distortion.process(flanged)
        feedbackSample = distorted
        return (input * (1.0 - mix)) + (distorted * mix)
    }
}