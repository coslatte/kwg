package engine.audio

/**
 * What a keyboard can play. [LiveEngine] is the real implementation; keeping the
 * surface this small is what lets the key handling be tested without opening an
 * audio device.
 */
interface NoteSink {

    fun noteOn(midi: Int)

    fun noteOff(midi: Int)

    /** Releases everything that is still sounding. */
    fun panic()
}
