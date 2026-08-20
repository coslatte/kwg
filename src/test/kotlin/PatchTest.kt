import engine.Patch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.sin

/**
 * The patch is the bridge between the two use cases: the sliders mutate live
 * modules while a note is sounding, and a render has to capture those same
 * values without borrowing the instances the audio thread is using.
 */
class PatchTest {

    @Test
    fun `the live chain starts with the clipper guarding the output`() {
        val patch = Patch(44100u)

        assertSame(patch.clipper, patch.liveChain.clipper, "the clipper is the pre-master safety net")
        assertNull(patch.liveChain.flanger)
        assertNull(patch.liveChain.distortion)
        assertNull(patch.liveChain.filter)
        assertNull(patch.liveChain.specialFx)
    }

    @Test
    fun `enabling a module attaches the live instance, not a copy`() {
        val patch = Patch(44100u)

        patch.flangerEnabled = true
        patch.distortionEnabled = true
        patch.filterEnabled = true
        patch.specialFxEnabled = true
        patch.syncEnabled()

        // same instances: a slider move has to be heard on the next sample
        assertSame(patch.flanger, patch.liveChain.flanger)
        assertSame(patch.distortion, patch.liveChain.distortion)
        assertSame(patch.filter, patch.liveChain.filter)
        assertSame(patch.specialFx, patch.liveChain.specialFx)
    }

    @Test
    fun `bypassing a module keeps its settings`() {
        val patch = Patch(44100u)
        patch.distortionEnabled = true
        patch.syncEnabled()
        patch.distortion.drive = 17.0

        patch.distortionEnabled = false
        patch.syncEnabled()

        assertNull(patch.liveChain.distortion, "a bypassed module must leave the chain")
        assertEquals(17.0, patch.distortion.drive, "but keep what was dialled in")
    }

    @Test
    fun `a snapshot copies the values and cuts the shared state`() {
        val patch = Patch(44100u)
        patch.distortionEnabled = true
        patch.flangerEnabled = true
        patch.filterEnabled = true
        patch.syncEnabled()

        patch.distortion.drive = 5.0
        patch.flanger.depthMs = 7.5
        patch.filter.cutoffFreq = 1200.0
        patch.liveChain.volume = 0.42

        val snapshot = patch.snapshotChain(11025u)

        // keeps playing while the file renders: no instance may be shared
        assertNotSame(patch.distortion, snapshot.distortion)
        assertNotSame(patch.flanger, snapshot.flanger)
        assertNotSame(patch.filter, snapshot.filter)

        assertEquals(5.0, snapshot.distortion!!.drive)
        assertEquals(7.5, snapshot.flanger!!.depthMs)
        assertEquals(1200.0, snapshot.filter!!.cutoffFreq)
        assertEquals(0.42, snapshot.volume)

        // moving a slider after the snapshot must not change the render
        patch.distortion.drive = 30.0
        assertEquals(5.0, snapshot.distortion!!.drive)
    }

    @Test
    fun `a snapshot only carries the enabled modules`() {
        val patch = Patch(44100u)
        patch.specialFxEnabled = true
        patch.clipperEnabled = false
        patch.syncEnabled()

        val snapshot = patch.snapshotChain()

        assertNotNull(snapshot.specialFx)
        assertNull(snapshot.flanger)
        assertNull(snapshot.distortion)
        assertNull(snapshot.filter)
        assertNull(snapshot.clipper)
    }

    @Test
    fun `the merged fx keeps its own modules so it cannot double process`() {
        val patch = Patch(44100u)

        assertNotSame(patch.flanger, patch.specialFx.flanger)
        assertNotSame(patch.distortion, patch.specialFx.distortion)

        patch.distortion.drive = 25.0
        assertTrue(patch.specialFx.distortion.drive != 25.0)
    }

    @Test
    fun `a snapshot at another sample rate still renders`() {
        val patch = Patch(44100u)
        patch.flangerEnabled = true
        patch.filterEnabled = true
        patch.specialFxEnabled = true
        patch.syncEnabled()

        // the crunchy preset: time based modules are rebuilt for 8000 Hz
        val snapshot = patch.snapshotChain(8000u)

        for (step in 0..500) {
            val output = snapshot.process(sin(step / 20.0) * 0.8)
            assertTrue(output.isFinite(), "sample $step came out as $output")
        }
    }
}
