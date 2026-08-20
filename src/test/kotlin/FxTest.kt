import engine.fxs.BiquadFilter
import engine.fxs.Clipper
import engine.fxs.Distortion
import engine.fxs.Flanger
import engine.fxs.SpecialFx
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

/**
 * Each module is checked for the thing it is supposed to do — a delay that
 * delays, a drive that drives, a low pass that actually rejects treble — and for
 * having every parameter mutable, because the UI writes to them while a note is
 * sounding.
 */
class FxTest {

    // ---------------------------------------------------------------- flanger

    @Test
    fun `flanger at zero mix is a pass through`() {
        val flanger = Flanger(sampleRate = 44100u, mix = 0.0)

        for (step in 0..100) {
            val input = sin(step / 7.0)
            assertEquals(input, flanger.process(input), 1e-12)
        }
    }

    @Test
    fun `flanger delays the signal instead of passing it`() {
        val flanger = Flanger(sampleRate = 44100u, rateHz = 0.0, depthMs = 1.0, feedback = 0.0, mix = 1.0)

        // the delay line starts empty, so a fully wet flanger is silent at first
        assertEquals(0.0, flanger.process(1.0), 1e-12)

        var heard = false
        repeat(200) { if (abs(flanger.process(1.0)) > 0.5) heard = true }
        assertTrue(heard, "the delayed copy never arrived")
    }

    @Test
    fun `flanger stays finite at maximum depth and feedback`() {
        val flanger = Flanger(sampleRate = 44100u, rateHz = 2.0, depthMs = 18.0, feedback = 0.95, mix = 1.0)

        repeat(20_000) { step ->
            val output = flanger.process(sin(2.0 * PI * 220.0 * step / 44100.0))
            assertTrue(output.isFinite(), "blew up at sample $step")
        }
    }

    @Test
    fun `flanger parameters are mutable`() {
        val flanger = Flanger(sampleRate = 44100u)
        flanger.rateHz = 1.0
        flanger.depthMs = 5.0
        flanger.feedback = 0.3
        flanger.mix = 0.2

        assertEquals(1.0, flanger.rateHz)
        assertEquals(5.0, flanger.depthMs)
        assertEquals(0.3, flanger.feedback)
        assertEquals(0.2, flanger.mix)
        assertTrue(flanger.process(0.5).isFinite(), "still usable after being retuned")
    }

    // ------------------------------------------------------------- distortion

    @Test
    fun `distortion at zero mix is a pass through`() {
        val distortion = Distortion(drive = 20.0, mix = 0.0)

        for (step in 0..100) {
            val input = sin(step / 5.0) * 0.7
            assertEquals(input, distortion.process(input), 1e-12)
        }
    }

    @Test
    fun `drive makes a quiet sample louder`() {
        val distortion = Distortion(drive = 10.0, saturation = 0.0, mix = 1.0, oversample = 1)

        val quiet = 0.05
        assertTrue(
            distortion.process(quiet) > quiet * 2,
            "drive should be lifting quiet material, not leaving it alone"
        )
    }

    @Test
    fun `saturation never lets a sample past unity`() {
        listOf(0.0, 0.5, 1.0).forEach { saturation ->
            val distortion = Distortion(drive = 40.0, saturation = saturation, mix = 1.0, oversample = 4)

            for (step in -20..20) {
                val output = distortion.process(step / 4.0)
                assertTrue(abs(output) <= 1.0 + 1e-9, "saturation $saturation let $output through")
            }
        }
    }

    @Test
    fun `distortion parameters are mutable`() {
        val distortion = Distortion()
        distortion.drive = 10.0
        distortion.saturation = 0.9
        distortion.mix = 0.7
        distortion.oversample = 8

        assertEquals(10.0, distortion.drive)
        assertEquals(0.9, distortion.saturation)
        assertEquals(0.7, distortion.mix)
        assertEquals(8, distortion.oversample)
        assertTrue(distortion.process(0.5).isFinite(), "still usable after being retuned")
    }

    // ----------------------------------------------------------------- filter

    @Test
    fun `low pass leaves dc alone`() {
        val filter = BiquadFilter(sampleRate = 44100u, cutoffFreq = 1000.0)

        var output = 0.0
        repeat(2000) { output = filter.process(1.0) }

        assertEquals(1.0, output, 1e-6, "a low pass must have unity gain at dc")
    }

    @Test
    fun `low pass rejects material above the cutoff`() {
        val filter = BiquadFilter(sampleRate = 44100u, cutoffFreq = 1000.0)

        var peak = 0.0
        for (step in 0 until 4000) {
            val output = filter.process(sin(2.0 * PI * 10_000.0 * step / 44100.0))
            if (step > 2000) peak = maxOf(peak, abs(output))
        }

        assertTrue(peak < 0.1, "10 kHz came through at $peak with a 1 kHz cutoff")
    }

    @Test
    fun `cutoff is heard as soon as it is moved`() {
        val filter = BiquadFilter(sampleRate = 44100u, cutoffFreq = 18_000.0)

        fun peakOf(frequency: Double): Double {
            var peak = 0.0
            for (step in 0 until 4000) {
                val output = filter.process(sin(2.0 * PI * frequency * step / 44100.0))
                if (step > 2000) peak = maxOf(peak, abs(output))
            }
            return peak
        }

        val open = peakOf(5_000.0)
        filter.cutoffFreq = 200.0
        val closed = peakOf(5_000.0)

        assertTrue(closed < open / 10.0, "closing the filter did nothing: $open -> $closed")
    }

    @Test
    fun `filter parameters are mutable`() {
        val filter = BiquadFilter(sampleRate = 44100u, cutoffFreq = 1000.0)
        filter.cutoffFreq = 2000.0
        filter.q = 1.0

        assertEquals(2000.0, filter.cutoffFreq)
        assertEquals(1.0, filter.q)
        assertTrue(filter.process(0.5).isFinite(), "still usable after being retuned")
    }

    // ---------------------------------------------------------------- clipper

    @Test
    fun `clipper hard clips at the ceiling when softness is zero`() {
        val clipper = Clipper(ceilingDb = -6.0, softness = 0.0)
        val ceiling = 10.0.pow(-6.0 / 20.0)

        assertEquals(ceiling, clipper.process(10.0), 1e-9)
        assertEquals(-ceiling, clipper.process(-10.0), 1e-9)
    }

    @Test
    fun `quiet material passes the clipper untouched`() {
        val clipper = Clipper(ceilingDb = -0.0, softness = 0.5)

        // softness 0.5 keeps the bottom half of the range linear
        assertEquals(0.1, clipper.process(0.1), 1e-12)
        assertEquals(-0.25, clipper.process(-0.25), 1e-12)
    }

    @Test
    fun `nothing exceeds the ceiling at any softness`() {
        listOf(0.0, 0.25, 0.5, 1.0).forEach { softness ->
            listOf(-0.0, -3.0, -12.0).forEach { ceilingDb ->
                val clipper = Clipper(ceilingDb = ceilingDb, softness = softness)
                val ceiling = 10.0.pow(ceilingDb / 20.0)

                for (step in -100..100) {
                    val output = clipper.process(step / 5.0)
                    assertTrue(
                        abs(output) <= ceiling + 1e-9,
                        "softness $softness, ceiling $ceilingDb dB let $output through"
                    )
                }
            }
        }
    }

    @Test
    fun `clipper parameters are mutable`() {
        val clipper = Clipper()
        clipper.ceilingDb = -3.0
        clipper.softness = 0.2

        assertEquals(-3.0, clipper.ceilingDb)
        assertEquals(0.2, clipper.softness)
        assertEquals(10.0.pow(-3.0 / 20.0), clipper.process(5.0), 1e-6, "the new ceiling applies at once")
    }

    // -------------------------------------------------------------- specialFx

    @Test
    fun `special fx at zero mix is a pass through`() {
        val specialFx = SpecialFx(
            flanger = Flanger(sampleRate = 44100u),
            distortion = Distortion(drive = 12.0),
            feedback = 0.5,
            mix = 0.0
        )

        for (step in 0..100) {
            val input = sin(step / 9.0) * 0.6
            assertEquals(input, specialFx.process(input), 1e-12)
        }
    }

    @Test
    fun `special fx colours the signal`() {
        val specialFx = SpecialFx(
            flanger = Flanger(sampleRate = 44100u, rateHz = 0.2, depthMs = 4.0, feedback = 0.4, mix = 0.6),
            distortion = Distortion(drive = 12.0, saturation = 0.5, mix = 1.0, oversample = 4),
            feedback = 0.5,
            mix = 0.9
        )

        var changed = false
        for (step in 0 until 2000) {
            val input = sin(2.0 * PI * 110.0 * step / 44100.0) * 0.7
            if (abs(specialFx.process(input) - input) > 1e-3) changed = true
        }

        assertTrue(changed, "the merged fx left the signal untouched")
    }

    @Test
    fun `special fx stays stable with its feedback wide open`() {
        val specialFx = SpecialFx(
            flanger = Flanger(sampleRate = 44100u, rateHz = 1.0, depthMs = 12.0, feedback = 0.9, mix = 1.0),
            distortion = Distortion(drive = 30.0, saturation = 1.0, mix = 1.0, oversample = 4),
            feedback = 0.95,
            mix = 1.0
        )

        repeat(20_000) { step ->
            val output = specialFx.process(sin(2.0 * PI * 110.0 * step / 44100.0) * 0.8)
            assertTrue(output.isFinite(), "blew up at sample $step")
            assertTrue(abs(output) <= 1.0 + 1e-9, "runaway feedback reached $output at sample $step")
        }
    }

    @Test
    fun `special fx parameters are mutable`() {
        val specialFx = SpecialFx(
            flanger = Flanger(sampleRate = 44100u),
            distortion = Distortion(),
            feedback = 0.4,
            mix = 1.0
        )

        specialFx.feedback = 0.7
        specialFx.mix = 0.3
        specialFx.flanger.depthMs = 6.0
        specialFx.distortion.drive = 15.0

        assertEquals(0.7, specialFx.feedback)
        assertEquals(0.3, specialFx.mix)
        assertEquals(6.0, specialFx.flanger.depthMs)
        assertEquals(15.0, specialFx.distortion.drive)
    }
}
