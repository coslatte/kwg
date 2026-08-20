package engine.audio

import engine.SignalChain
import engine.enums.Waveform
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Real-time synth: renders notes through the shared [chain] into a live audio
 * line and mirrors every sample into a [ScopeBuffer] the UI reads for the
 * waveform display.
 *
 * Voices are summed before the chain (paraphonic rack: one set of fx for the
 * whole mix), so chords are playable while the fx state stays single-instance.
 * Notes are triggered with [noteOn]/[noteOff] using MIDI numbers and faded in
 * and out by [attackMs]/[releaseMs] so keys don't click.
 */
class LiveEngine(
    val sampleRate: Int = 44100,
    val chain: SignalChain = SignalChain(),
    var waveform: Waveform = Waveform.SAWTOOTH
) : NoteSink, AutoCloseable {

    /** Fade-in applied when a key goes down, in milliseconds. */
    var attackMs: Double = 8.0

    /** Fade-out applied when a key is let go, in milliseconds. */
    var releaseMs: Double = 120.0

    private class Voice(val midi: Int, val frequency: Double) {
        var phase = 0.0
        var gain = 0.0
        var held = true
    }

    private val voices = ArrayList<Voice>()
    private var lastNote: Int? = null

    private val format = AudioFormat(sampleRate.toFloat(), 16, 1, true, false)
    private val line: SourceDataLine = AudioSystem.getSourceDataLine(format)

    private val scope = ScopeBuffer(capacity = SCOPE_CAPACITY)

    @Volatile
    private var running = false
    private var renderThread: Thread? = null

    init {
        line.open(format, BLOCK_FRAMES * 2 * 4)
    }

    fun start() {
        if (running) return
        running = true
        line.start()
        renderThread = Thread(::renderLoop, "kwg-live-engine").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        if (!running) return
        running = false

        // unblocks the render thread if it is parked inside line.write()
        line.stop()
        line.flush()

        renderThread?.join(500)
        renderThread = null
    }

    override fun close() {
        stop()
        line.close()
    }

    override fun noteOn(midi: Int) {
        synchronized(voices) {
            val existing = voices.firstOrNull { it.midi == midi }
            if (existing != null) {
                existing.held = true
            } else {
                voices.add(Voice(midi, midiToFrequency(midi)))
            }
            lastNote = midi
        }
    }

    /**
     * Lets a note go. The voice is *not* dropped here — it is marked released so
     * [releaseMs] can fade it out, and [nextSample] reaps it once it is inaudible.
     * Deleting the voice outright would silence it mid-cycle, which is a click.
     */
    override fun noteOff(midi: Int) {
        synchronized(voices) {
            voices.filter { it.midi == midi }.forEach { it.held = false }
        }
    }

    /** Releases everything that is still sounding. */
    override fun panic() {
        synchronized(voices) { voices.forEach { it.held = false } }
    }

    /** MIDI numbers of the keys currently held down, lowest first. */
    fun heldNotes(): List<Int> = synchronized(voices) {
        voices.filter { it.held }.map { it.midi }.sorted()
    }

    /** The most recently triggered note, kept after release for the UI read-out. */
    fun lastNote(): Int? = synchronized(voices) { lastNote }

    /** The last [count] samples that went to the audio line. */
    fun scopeSnapshot(count: Int = SCOPE_CAPACITY): FloatArray = scope.snapshot(count)

    /** Peak level since the previous call, for the level meter. */
    fun scopePeak(): Float = scope.takePeak()

    private fun renderLoop() {
        val bytes = ByteArray(BLOCK_FRAMES * 2)

        while (running) {
            for (frame in 0 until BLOCK_FRAMES) {
                val sample = nextSample()
                scope.push(sample)

                val scaled = (sample * Short.MAX_VALUE).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())

                bytes[frame * 2] = (scaled and 0xFF).toByte()          // lower byte
                bytes[frame * 2 + 1] = ((scaled shr 8) and 0xFF).toByte()  // upper byte
            }

            line.write(bytes, 0, bytes.size)
        }
    }

    /**
     * Advances every live voice by one sample, sums them, and runs the mix
     * through the shared fx chain (which also applies volume and the clipper).
     */
    private fun nextSample(): Double {
        val attackCoefficient = smoothingCoefficient(attackMs)
        val releaseCoefficient = smoothingCoefficient(releaseMs)

        var mix = 0.0

        synchronized(voices) {
            val iterator = voices.iterator()
            while (iterator.hasNext()) {
                val voice = iterator.next()

                val target = if (voice.held) 1.0 else 0.0
                val coefficient = if (voice.held) attackCoefficient else releaseCoefficient
                voice.gain += (target - voice.gain) * coefficient

                if (!voice.held && voice.gain <= SILENCE_THRESHOLD) {
                    iterator.remove()
                    continue
                }

                voice.phase = (voice.phase + voice.frequency / sampleRate) % 1.0
                mix += oscillator(voice.phase) * voice.gain
            }
        }

        return chain.process(mix)
    }

    /**
     * Phase-accumulator oscillator: unlike the file renderer's `frequency * time`
     * form, this keeps its phase continuous when the pitch changes mid-note.
     */
    private fun oscillator(phase: Double): Double = when (waveform) {
        Waveform.SINE -> sin(2.0 * PI * phase)
        Waveform.SQUARE -> if (phase < 0.5) 1.0 else -1.0
        Waveform.SAWTOOTH -> 2.0 * phase - 1.0
        Waveform.TRIANGLE -> 4.0 * abs(phase - 0.5) - 1.0
        Waveform.WHITE_NOISE -> Random.nextDouble(-1.0, 1.0)
    }

    /** One-pole coefficient reaching ~63% of the target after [ms] milliseconds. */
    private fun smoothingCoefficient(ms: Double): Double {
        val samples = (ms * 0.001 * sampleRate).coerceAtLeast(1.0)
        return 1.0 - exp(-1.0 / samples)
    }

    companion object {
        private const val BLOCK_FRAMES = 256
        private const val SCOPE_CAPACITY = 8192
        private const val SILENCE_THRESHOLD = 1e-4

        fun midiToFrequency(midi: Int): Double =
            440.0 * 2.0.pow((midi - 69) / 12.0)

        fun midiToName(midi: Int): String {
            val names = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
            val octave = midi / 12 - 1
            return "${names[midi % 12]}-$octave"
        }
    }
}
