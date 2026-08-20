package test

import engine.audio.LiveEngine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ui.KeyboardMap
import java.awt.event.KeyEvent

/**
 * The keyboard layout is a hard requirement, not a preference: playing `Q` has
 * to sound C-4, the way it does in Ableton. These tests use the same key codes
 * a real `KeyEvent` carries, so a change in how the map is built cannot quietly
 * shift the whole keyboard by a semitone.
 */
class KeyboardMapTest {

    @Test
    fun `Q plays C-4`() {
        val midi = KeyboardMap.midiForKey(KeyEvent.VK_Q)

        assertEquals(60, midi)
        assertEquals("C-4", LiveEngine.midiToName(60))
    }

    @Test
    fun `upper row is the C-4 octave`() {
        val expected = mapOf(
            KeyEvent.VK_Q to 60, // C-4
            KeyEvent.VK_W to 62, // D-4
            KeyEvent.VK_E to 64, // E-4
            KeyEvent.VK_R to 65, // F-4
            KeyEvent.VK_T to 67, // G-4
            KeyEvent.VK_Y to 69, // A-4
            KeyEvent.VK_U to 71, // B-4
            KeyEvent.VK_I to 72, // C-5
            KeyEvent.VK_O to 74, // D-5
            KeyEvent.VK_P to 76  // E-5
        )

        expected.forEach { (code, midi) ->
            assertEquals(midi, KeyboardMap.midiForKey(code), "key ${KeyEvent.getKeyText(code)}")
        }
    }

    @Test
    fun `lower row is one octave down`() {
        assertEquals(48, KeyboardMap.midiForKey(KeyEvent.VK_Z)) // C-3
        assertEquals(59, KeyboardMap.midiForKey(KeyEvent.VK_M)) // B-3
        assertEquals(
            12,
            KeyboardMap.midiForKey(KeyEvent.VK_Q)!! - KeyboardMap.midiForKey(KeyEvent.VK_Z)!!
        )
    }

    @Test
    fun `black keys sit a semitone above their white key`() {
        assertEquals(61, KeyboardMap.midiForKey(KeyEvent.VK_2)) // C#4
        assertEquals(63, KeyboardMap.midiForKey(KeyEvent.VK_3)) // D#4
        assertEquals(49, KeyboardMap.midiForKey(KeyEvent.VK_S)) // C#3
        assertEquals(58, KeyboardMap.midiForKey(KeyEvent.VK_J)) // A#3
    }

    @Test
    fun `no key is mapped to two notes and no note to two keys`() {
        val notes = KeyboardMap.layout.values

        assertEquals(notes.size, notes.toSet().size, "a note is reachable from two keys")
        assertEquals(24, KeyboardMap.layout.size, "12 lower + 12 upper keys expected")
    }

    @Test
    fun `unmapped keys are silent`() {
        assertEquals(null, KeyboardMap.midiForKey(KeyEvent.VK_SPACE))
        assertEquals(null, KeyboardMap.midiForKey(KeyEvent.VK_ESCAPE))
        assertEquals(null, KeyboardMap.midiForKey(KeyEvent.VK_F1))
    }

    @Test
    fun `midi notes convert to concert pitch`() {
        assertEquals(440.0, LiveEngine.midiToFrequency(69), 1e-9)   // A-4
        assertEquals(261.6255653, LiveEngine.midiToFrequency(60), 1e-6) // C-4
        assertEquals(880.0, LiveEngine.midiToFrequency(81), 1e-9)   // A-5

        // an octave up is exactly double
        assertTrue(LiveEngine.midiToFrequency(72) / LiveEngine.midiToFrequency(60) - 2.0 < 1e-9)
    }

    @Test
    fun `note names follow the octave boundary at C`() {
        assertEquals("C-4", LiveEngine.midiToName(60))
        assertEquals("B-3", LiveEngine.midiToName(59))
        assertEquals("A#3", LiveEngine.midiToName(58))
        assertEquals("C-0", LiveEngine.midiToName(12))
    }
}
