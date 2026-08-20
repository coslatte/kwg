package ui

import engine.audio.NoteSink

/**
 * Turns key codes into notes.
 *
 * This lives apart from the window on purpose: a stuck note is a bug in the
 * bookkeeping, not in the audio, so the bookkeeping has to be testable without
 * opening a sound card. [KeyboardControllerTest] is the regression.
 */
class KeyboardController(private val notes: NoteSink) {

    /** Key code -> the note it is currently sounding. Insertion ordered for [heldNotes]. */
    private val sounding = LinkedHashMap<Int, Int>()

    var octaveShift: Int = 0
        private set

    /** The notes held right now, oldest first. */
    val heldNotes: List<Int> get() = sounding.values.toList()

    /**
     * Handles one key event.
     *
     * [suppressed] says the keystroke belongs to something else — a text field has
     * the focus, or the window is not active. A press is then ignored, but **a
     * release is always honoured**: dropping one leaves the note sounding forever,
     * which is exactly the stuck key that was reported. The two directions are not
     * symmetrical, and treating them as if they were is the whole bug.
     *
     * [layoutCode] is the layout-aware code ([java.awt.event.KeyEvent.getExtendedKeyCode]);
     * it makes the map work on a non-QWERTY keyboard. Bookkeeping always keys on
     * [keyCode] so a press and its release match even if the layout changed between
     * the two.
     */
    fun handle(keyCode: Int, pressed: Boolean, suppressed: Boolean, layoutCode: Int = keyCode): Boolean {
        return if (pressed) {
            if (suppressed) false else press(keyCode, layoutCode)
        } else {
            release(keyCode)
        }
    }

    /** Starts the note for [keyCode], if it maps to one and is not already down. */
    fun press(keyCode: Int, layoutCode: Int = keyCode): Boolean {
        val semitone = KeyboardMap.midiForKey(layoutCode) ?: KeyboardMap.midiForKey(keyCode) ?: return false
        if (sounding.containsKey(keyCode)) return true      // auto repeat: already sounding

        val midi = (semitone + octaveShift * 12).coerceIn(0, 127)
        sounding[keyCode] = midi
        notes.noteOn(midi)
        return true
    }

    /**
     * Releases the note [keyCode] started. Silent about keys that were never down,
     * so a release that arrives twice, or without its press, changes nothing.
     */
    fun release(keyCode: Int): Boolean {
        val midi = sounding.remove(keyCode) ?: return false
        // two keys can sound the same note across an octave shift — only the last
        // one out turns it off
        if (midi !in sounding.values) notes.noteOff(midi)
        return true
    }

    /** Drops every held note. Called when the window loses the keyboard, and by panic. */
    fun releaseAll() {
        sounding.clear()
        notes.panic()
    }

    /**
     * Transposes by whole octaves. Notes already down keep their pitch — they were
     * recorded when pressed — so shifting mid chord cannot orphan a note off.
     */
    fun shiftOctave(delta: Int): Int {
        octaveShift = (octaveShift + delta).coerceIn(MIN_OCTAVE, MAX_OCTAVE)
        return octaveShift
    }

    /** The octave of the `Q` key, in the usual naming where midi 60 is C4. */
    fun octaveLabel(): String = "C${4 + octaveShift}"

    private companion object {
        const val MIN_OCTAVE = -3
        const val MAX_OCTAVE = 3
    }
}
