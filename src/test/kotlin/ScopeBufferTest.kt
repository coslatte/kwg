package test

import engine.audio.ScopeBuffer
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The buffer is what the oscilloscope reads, so a wrong wraparound shows up as a
 * trace that jumps instead of scrolling. It is written by the audio thread and
 * read by the Swing timer, so the order it hands samples back has to be
 * chronological regardless of where the write head happens to be.
 */
class ScopeBufferTest {

    @Test
    fun `a partly filled buffer only returns what it has`() {
        val buffer = ScopeBuffer(capacity = 4)
        buffer.push(0.1)
        buffer.push(0.2)

        assertArrayEquals(floatArrayOf(0.1f, 0.2f), buffer.snapshot(), 1e-6f)
    }

    @Test
    fun `an empty buffer returns nothing rather than silence`() {
        assertEquals(0, ScopeBuffer(capacity = 4).snapshot().size)
    }

    @Test
    fun `the oldest samples are dropped once it wraps`() {
        val buffer = ScopeBuffer(capacity = 4)
        listOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0).forEach(buffer::push)

        assertArrayEquals(floatArrayOf(3f, 4f, 5f, 6f), buffer.snapshot(), 1e-6f)
    }

    @Test
    fun `a narrower window is the most recent slice, in order`() {
        val buffer = ScopeBuffer(capacity = 8)
        (1..10).forEach { buffer.push(it.toDouble()) }

        assertArrayEquals(floatArrayOf(8f, 9f, 10f), buffer.snapshot(3), 1e-6f)
        assertArrayEquals(floatArrayOf(10f), buffer.snapshot(1), 1e-6f)
    }

    @Test
    fun `asking for more than the capacity is not an error`() {
        val buffer = ScopeBuffer(capacity = 4)
        listOf(1.0, 2.0, 3.0, 4.0, 5.0).forEach(buffer::push)

        assertArrayEquals(floatArrayOf(2f, 3f, 4f, 5f), buffer.snapshot(9999), 1e-6f)
    }

    @Test
    fun `peak reports the loudest magnitude and then resets`() {
        val buffer = ScopeBuffer(capacity = 16)
        buffer.push(0.25)
        buffer.push(-0.8)
        buffer.push(0.4)

        assertEquals(0.8f, buffer.takePeak(), 1e-6f, "negative peaks count too")
        assertEquals(0.0f, buffer.takePeak(), 1e-6f, "each ui frame should meter only its own samples")

        buffer.push(0.1)
        assertEquals(0.1f, buffer.takePeak(), 1e-6f)
    }
}
