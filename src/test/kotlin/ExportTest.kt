import engine.Engine
import engine.enums.Waveform
import engine.export.ExportFormat
import engine.export.Exporter
import engine.export.QualityPreset
import engine.fxs.Distortion
import format.enums.SampleDuration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ExportTest {

    @Test
    fun `exporter renders lo-fi WAV`(@TempDir tempDir: File) {
        val file = File(tempDir, "export_lofi.wav")
        val exporter = Exporter()
        val engine = Engine()
        val distortion = Distortion(
            drive = 8.0,
            saturation = 0.7,
            mix = 1.0,
            oversample = 4
        )

        exporter.export(
            outputFile = file,
            format = ExportFormat.WAV,
            preset = QualityPreset.LO_FI,
            duration = SampleDuration._1SEC
        ) { wavFile, header ->
            engine.writeSample(
                outputFile = wavFile,
                waveform = Waveform.SAWTOOTH,
                header = header,
                frequencyHz = 110.0,
                volume = 0.8,
                distortion = distortion
            )
        }

        assertTrue(file.exists())
        // 11025 Hz * 1 channel * 1 byte * 1 sec + 44 header
        assertEquals(44 + 11025, file.length())
    }

    @Test
    fun `exporter renders hi-res WAV`(@TempDir tempDir: File) {
        val file = File(tempDir, "export_hires.wav")
        val exporter = Exporter()
        val engine = Engine()

        val distortion = Distortion(
            drive = 8.0,
            saturation = 0.7,
            mix = 1.0,
            oversample = 4
        )

        exporter.export(
            outputFile = file,
            format = ExportFormat.WAV,
            preset = QualityPreset.HI_RES,
            duration = SampleDuration._1SEC
        ) { wavFile, header ->
            engine.writeSample(
                outputFile = wavFile,
                waveform = Waveform.TRIANGLE,
                header = header,
                frequencyHz = 220.0,
                volume = 0.6,
                distortion = distortion
            )
        }

        assertTrue(file.exists())
        // 48000 Hz * 2 channels * 3 bytes * 1 sec + 44 header
        assertEquals(44 + 48000 * 2 * 3, file.length())
    }

    @Test
    fun `exporter renders standard WAV`(@TempDir tempDir: File) {
        val file = File(tempDir, "export_standard.wav")
        val exporter = Exporter()
        val engine = Engine()

        exporter.export(
            outputFile = file,
            format = ExportFormat.WAV,
            preset = QualityPreset.STANDARD,
            duration = SampleDuration._1SEC
        ) { wavFile, header ->
            engine.writeSample(
                outputFile = wavFile,
                waveform = Waveform.SINE,
                header = header,
                frequencyHz = 440.0,
                volume = 0.5
            )
        }

        assertTrue(file.exists())
        // 44100 Hz * 2 channels * 2 bytes * 1 sec + 44 header
        assertEquals(44 + 44100 * 2 * 2, file.length())
    }
}