package test

import engine.Engine
import engine.enums.Waveform
import engine.fxs.BiquadFilter
import engine.fxs.Clipper
import engine.fxs.Distortion
import engine.fxs.Flanger
import engine.fxs.SpecialFx
import format.WavHeader
import format.enums.BitDepth
import format.enums.ChannelCount
import format.enums.SampleDuration
import format.enums.SampleRate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class EngineTest {

    @Test
    fun `header written correctly`(@TempDir tempDir: File) {
        val file = File(tempDir, "header_test.wav")
        val header = WavHeader(
            channels = ChannelCount.MONO,
            sampleRate = SampleRate._44100,
            bitDepth = BitDepth._16,
            sampleDuration = SampleDuration._1SEC
        )

        file.writeBytes(header.toByteArray())

        assertTrue(file.exists())
        assertTrue(file.length() > 44) // header + at least some data
    }

    @Test
    fun `engine renders basic waveform`(@TempDir tempDir: File) {
        val file = File(tempDir, "engine_test.wav")
        val engine = Engine()
        val header = WavHeader()

        engine.writeSample(
            outputFile = file,
            waveform = Waveform.SAWTOOTH,
            header = header,
            frequencyHz = 440.0,
            volume = 0.5
        )

        assertTrue(file.exists())
        assertEquals(44 + 44100 * 2, file.length()) // 44 header + 1sec * 44100 * 2 bytes
    }

    @Test
    fun `engine renders noise with filter`(@TempDir tempDir: File) {
        val file = File(tempDir, "noise_test.wav")
        val engine = Engine()
        val header = WavHeader()

        val lpf = BiquadFilter(
            sampleRate = header.sampleRate.hz,
            cutoffFreq = 1550.0
        )

        engine.writeSample(
            outputFile = file,
            waveform = Waveform.WHITE_NOISE,
            header = header,
            frequencyHz = 0.0,
            volume = 0.6,
            filter = lpf
        )

        assertTrue(file.exists())
        assertEquals(44 + 44100 * 2, file.length())
    }

    @Test
    fun `engine renders with flanger`(@TempDir tempDir: File) {
        val file = File(tempDir, "flanger_test.wav")
        val engine = Engine()
        val header = WavHeader(sampleDuration = SampleDuration._1SEC)

        val flanger = Flanger(
            sampleRate = header.sampleRate.hz,
            rateHz = 0.1,
            depthMs = 0.5,
            feedback = 0.1
        )

        engine.writeSample(
            outputFile = file,
            waveform = Waveform.WHITE_NOISE,
            header = header,
            frequencyHz = 0.0,
            volume = 0.6,
            flanger = flanger
        )

        assertTrue(file.exists())
        assertEquals(44 + 44100 * 2, file.length())
    }

    @Test
    fun `engine renders with distortion`(@TempDir tempDir: File) {
        val file = File(tempDir, "distortion_test.wav")
        val engine = Engine()
        val header = WavHeader(sampleDuration = SampleDuration._1SEC)

        val distortion = Distortion(
            drive = 8.0,
            saturation = 0.7,
            mix = 1.0,
            oversample = 4
        )

        engine.writeSample(
            outputFile = file,
            waveform = Waveform.SAWTOOTH,
            header = header,
            frequencyHz = 220.0,
            volume = 0.8,
            distortion = distortion
        )

        assertTrue(file.exists())
        assertEquals(44 + 44100 * 2, file.length())
    }

    @Test
    fun `clipper prevents overflow`(@TempDir tempDir: File) {
        val file = File(tempDir, "clipper_test.wav")
        val engine = Engine()
        val header = WavHeader(sampleDuration = SampleDuration._1SEC)

        val distortion = Distortion(
            drive = 20.0,
            saturation = 1.0,
            mix = 1.0,
            oversample = 2
        )

        val clipper = Clipper(
            ceilingDb = -0.0,
            softness = 0.5
        )

        engine.writeSample(
            outputFile = file,
            waveform = Waveform.SAWTOOTH,
            header = header,
            frequencyHz = 220.0,
            volume = 2.0,
            distortion = distortion,
            clipper = clipper
        )

        assertTrue(file.exists())
        assertEquals(44 + 44100 * 2, file.length())
    }

    @Test
    fun `engine renders with special fx`(@TempDir tempDir: File) {
        val file = File(tempDir, "special_fx_test.wav")
        val engine = Engine()
        val header = WavHeader(sampleDuration = SampleDuration._1SEC)

        val flanger = Flanger(
            sampleRate = header.sampleRate.hz,
            rateHz = 0.2,
            depthMs = 4.0,
            feedback = 0.4,
            mix = 0.6
        )

        val distortion = Distortion(
            drive = 12.0,
            saturation = 0.5,
            mix = 1.0,
            oversample = 4
        )

        val specialFx = SpecialFx(
            flanger = flanger,
            distortion = distortion,
            feedback = 0.5,
            mix = 0.9
        )

        engine.writeSample(
            outputFile = file,
            waveform = Waveform.SAWTOOTH,
            header = header,
            frequencyHz = 110.0,
            volume = 0.7,
            specialFx = specialFx
        )

        assertTrue(file.exists())
        assertEquals(44 + 44100 * 2, file.length())
    }
}