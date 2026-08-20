import engine.audio.NoteSink
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ui.KeyboardController
import java.awt.event.KeyEvent

/**
 * The stuck-note regression, and the rest of the keyboard bookkeeping.
 *
 * A note that never stops is a bookkeeping bug, so it is provable here without
 * opening an audio device or a window.
 */
class KeyboardControllerTest {

    /** Stands in for the engine: records the calls and tracks what is left sounding. */
    private class RecordingSink : NoteSink {
        val events = mutableListOf<String>()
        val sounding = LinkedHashSet<Int>()

        override fun noteOn(midi: Int) {
            events += "on:$midi"
            sounding += midi
        }

        override fun noteOff(midi: Int) {
            events += "off:$midi"
            sounding -= midi
        }

        override fun panic() {
            events += "panic"
            sounding.clear()
        }
    }

    private fun key(char: Char): Int = KeyEvent.getExtendedKeyCodeForChar(char.code)

    @Test
    fun `pressing a key sounds the note it maps to`() {
        val sink = RecordingSink()
        val keys = KeyboardController(sink)

        assertTrue(keys.handle(key('Q'), pressed = true, suppressed = false))

        assertEquals(listOf("on:60"), sink.events, "Q is C-4, which is midi 60")
    }

    @Test
    fun `letting a key go stops the note`() {
        val sink = RecordingSink()
        val keys = KeyboardController(sink)

        keys.handle(key('Q'), pressed = true, suppressed = false)
        keys.handle(key('Q'), pressed = false, suppressed = false)

        assertTrue(sink.sounding.isEmpty(), "the note should not still be sounding")
        assertEquals(listOf("on:60", "off:60"), sink.events)
    }

    /**
     * The reported bug: the release arrived while the keyboard was not ours (a text
     * field had taken the focus, or the window had been deactivated) and was thrown
     * away with it, so the tone never stopped.
     */
    @Test
    fun `a release is honoured even when the keyboard is suppressed`() {
        val sink = RecordingSink()
        val keys = KeyboardController(sink)

        keys.handle(key('Q'), pressed = true, suppressed = false)
        keys.handle(key('Q'), pressed = false, suppressed = true)

        assertTrue(sink.sounding.isEmpty(), "a suppressed release must still stop the note")
        assertTrue(keys.heldNotes.isEmpty())
    }

    @Test
    fun `a press is ignored while the keyboard is suppressed`() {
        val sink = RecordingSink()
        val keys = KeyboardController(sink)

        assertFalse(keys.handle(key('Q'), pressed = true, suppressed = true))

        assertTrue(sink.events.isEmpty(), "typing into a text field must not play notes")
    }

    @Test
    fun `auto repeat does not stack the same note`() {
        val sink = RecordingSink()
        val keys = KeyboardController(sink)

        repeat(4) { keys.handle(key('Q'), pressed = true, suppressed = false) }
        keys.handle(key('Q'), pressed = false, suppressed = false)

        assertEquals(listOf("on:60", "off:60"), sink.events, "held keys repeat KEY_PRESSED")
    }

    @Test
    fun `a release without a press changes nothing`() {
        val sink = RecordingSink()
        val keys = KeyboardController(sink)

        assertFalse(keys.handle(key('Q'), pressed = false, suppressed = false))
        assertTrue(sink.events.isEmpty())
    }

    @Test
    fun `a release that arrives twice only stops the note once`() {
        val sink = RecordingSink()
        val keys = KeyboardController(sink)

        keys.press(key('Q'))
        keys.release(key('Q'))
        keys.release(key('Q'))

        assertEquals(1, sink.events.count { it == "off:60" })
    }

    @Test
    fun `keys that are not on the map are left alone`() {
        val sink = RecordingSink()
        val keys = KeyboardController(sink)

        assertFalse(keys.handle(KeyEvent.VK_F5, pressed = true, suppressed = false))
        assertFalse(keys.handle(KeyEvent.VK_F5, pressed = false, suppressed = false))
        assertTrue(sink.events.isEmpty())
    }

    @Test
    fun `all notes off empties the keyboard`() {
        val sink = RecordingSink()
        val keys = KeyboardController(sink)

        keys.press(key('Q'))
        keys.press(key('E'))
        keys.press(key('T'))
        assertEquals(3, keys.heldNotes.size)

        keys.releaseAll()

        assertTrue(keys.heldNotes.isEmpty())
        assertTrue(sink.sounding.isEmpty())
        assertTrue(sink.events.contains("panic"))
    }

    @Test
    fun `a key pressed before an octave shift still stops the note it started`() {
        val sink = RecordingSink()
        val keys = KeyboardController(sink)

        keys.press(key('Q'))
        keys.shiftOctave(1)
        keys.release(key('Q'))

        assertEquals(listOf("on:60", "off:60"), sink.events, "the note off must match the note on")
        assertTrue(sink.sounding.isEmpty(), "shifting octaves mid-note must not strand it")
    }

    @Test
    fun `an octave shift transposes the notes played after it`() {
        val sink = RecordingSink()
        val keys = KeyboardController(sink)

        keys.shiftOctave(1)
        keys.press(key('Q'))
        keys.shiftOctave(-2)
        keys.press(key('W'))

        assertEquals(listOf("on:72", "on:50"), sink.events)
    }

    @Test
    fun `two keys sounding the same note keep it alive until both are up`() {
        val sink = RecordingSink()
        val keys = KeyboardController(sink)

        keys.press(key('Q'))        // C-4, midi 60
        keys.shiftOctave(1)
        keys.press(key('Z'))        // C-3 up an octave: also midi 60

        keys.release(key('Q'))
        assertTrue(sink.sounding.contains(60), "the other key is still holding this note")

        keys.release(key('Z'))
        assertTrue(sink.sounding.isEmpty())
        assertEquals(1, sink.events.count { it == "off:60" })
    }

    @Test
    fun `the octave shift is bounded and readable`() {
        val keys = KeyboardController(RecordingSink())

        assertEquals("C4", keys.octaveLabel())

        keys.shiftOctave(1)
        assertEquals("C5", keys.octaveLabel())

        repeat(20) { keys.shiftOctave(1) }
        assertEquals(3, keys.octaveShift, "the shift must stop before the notes run off the top")

        repeat(40) { keys.shiftOctave(-1) }
        assertEquals(-3, keys.octaveShift)
        assertEquals("C1", keys.octaveLabel())
    }

    @Test
    fun `notes stay in range at the extremes of the shift`() {
        val sink = RecordingSink()
        val keys = KeyboardController(sink)

        repeat(3) { keys.shiftOctave(1) }
        keys.press(key('P'))        // the highest key on the map

        repeat(6) { keys.shiftOctave(-1) }
        keys.press(key('Z'))        // the lowest

        assertTrue(sink.sounding.all { it in 0..127 }, "midi notes must stay inside 0..127")
    }

    @Test
    fun `held notes are reported oldest first`() {
        val keys = KeyboardController(RecordingSink())

        keys.press(key('T'))
        keys.press(key('Q'))
        keys.press(key('E'))

        assertEquals(listOf(67, 60, 64), keys.heldNotes)
    }
}
