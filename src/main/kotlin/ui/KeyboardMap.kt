package ui

import java.awt.event.KeyEvent

/**
 * Ableton-style computer keyboard -> MIDI mapping.
 * White keys: Z X C V B N M = C3..B3, Q W E R T Y U I O P = C4..E5
 * Black keys: S D G H J = C#3..A#3, 2 3 5 6 7 9 0 = C#4..D#5
 * Q plays C-4.
 */
object KeyboardMap {
    val layout: Map<Int, Int> = buildMap {
        // white keys, lower octave
        put(vk('Z'), 48); put(vk('X'), 50); put(vk('C'), 52); put(vk('V'), 53)
        put(vk('B'), 55); put(vk('N'), 57); put(vk('M'), 59)
        // black keys, lower octave
        put(vk('S'), 49); put(vk('D'), 51); put(vk('G'), 54); put(vk('H'), 56); put(vk('J'), 58)
        // white keys, C-4 row
        put(vk('Q'), 60); put(vk('W'), 62); put(vk('E'), 64); put(vk('R'), 65); put(vk('T'), 67)
        put(vk('Y'), 69); put(vk('U'), 71); put(vk('I'), 72); put(vk('O'), 74); put(vk('P'), 76)
        // black keys, C-4 row
        put(vk('2'), 61); put(vk('3'), 63); put(vk('5'), 66); put(vk('6'), 68)
        put(vk('7'), 70); put(vk('9'), 73); put(vk('0'), 75)
    }

    fun midiForKey(code: Int): Int? = layout[code]

    private fun vk(c: Char): Int = KeyEvent.getExtendedKeyCodeForChar(c.code)
}