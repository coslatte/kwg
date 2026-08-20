package test

import engine.SignalChain
import engine.fxs.Clipper
import engine.fxs.Distortion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The chain is the contract shared by file rendering and live playback, so the
 * order of the stages matters more than any single module: volume has to be
 * applied *before* the clipper, otherwise nothing stops the output from leaving
 * the -0.0 dB ceiling behind.
 */
class SignalChainTest {

    @Test
    fun `an empty chain only applies volume`() {
        val chain = SignalChain(volume = 0.5)

        assertEquals(0.5, chain.process(1.0), 1e-12)
        assertEquals(-0.25, chain.process(-0.5), 1e-12)
        assertEquals(0.0, chain.process(0.0), 1e-12)
    }

    @Test
    fun `volume is applied before the clipper`() {
        val chain = SignalChain(volume = 4.0, clipper = Clipper(ceilingDb = -0.0, softness = 0.0))

        // 0.9 * 4 = 3.6 raw; the clipper has to catch it at the 0 dB ceiling
        assertEquals(1.0, chain.process(0.9), 1e-9)
    }

    @Test
    fun `nothing leaves the chain above the ceiling`() {
        val chain = SignalChain(
            volume = 2.0,
            distortion = Distortion(drive = 30.0, saturation = 1.0, mix = 1.0, oversample = 4),
            clipper = Clipper(ceilingDb = -3.0, softness = 0.5)
        )

        val ceiling = Math.pow(10.0, -3.0 / 20.0)

        for (step in 0..200) {
            val input = -1.0 + step / 100.0
            val output = chain.process(input)
            assertTrue(
                Math.abs(output) <= ceiling + 1e-9,
                "input $input produced $output, over the ${"%.3f".format(ceiling)} ceiling"
            )
        }
    }

    @Test
    fun `a bypassed module is skipped, not neutralised`() {
        val distortion = Distortion(drive = 10.0, saturation = 1.0, mix = 1.0, oversample = 1)
        val chain = SignalChain(volume = 1.0, distortion = distortion)

        val driven = chain.process(0.4)

        chain.distortion = null
        val dry = chain.process(0.4)

        assertEquals(0.4, dry, 1e-12, "with the module detached the sample must pass through untouched")
        assertTrue(driven > dry, "the distortion should have made the sample hotter while it was attached")
        assertEquals(10.0, distortion.drive, "detaching must not reset the module's settings")
    }

    @Test
    fun `modules stay live so the ui can tweak them mid note`() {
        val chain = SignalChain(volume = 1.0, clipper = Clipper(ceilingDb = -0.0, softness = 0.0))

        assertEquals(1.0, chain.process(2.0), 1e-9)

        chain.clipper!!.ceilingDb = -6.0
        assertEquals(Math.pow(10.0, -6.0 / 20.0), chain.process(2.0), 1e-9)

        chain.volume = 0.25
        assertEquals(0.25, chain.process(1.0), 1e-9)
    }
}
