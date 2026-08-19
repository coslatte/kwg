import engine.Engine
import engine.enums.Waveform
import engine.export.ExportFormat
import engine.export.Exporter
import engine.export.QualityPreset
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
import java.io.File
import java.io.FileOutputStream

fun main() {
    testHeader()
    testEngine()
    testNoise()
    testFlanger()
    testDistortion()
    testClipper()
    testSpecialFx()
    testExport()
}

fun testHeader() {
    val file = File("test_header.wav")

    val header = WavHeader(
        channels = ChannelCount.MONO,
        sampleRate = SampleRate._44100,
        bitDepth = BitDepth._16,
        sampleDuration = SampleDuration._1SEC
    )

    FileOutputStream(file).use { fos ->
        fos.write(header.toByteArray())
        fos.write(ByteArray(header.dataSize.toInt())) // add silence data for test
    }
}

fun testEngine() {
    val file = File("test_engine.wav")

    val engine = Engine()
    val header = WavHeader()

    val frequency = 440.0
    val volume = 0.5

    engine.writeSample(
        outputFile = file,
        waveform = Waveform.SAWTOOTH,
        header = header,
        frequencyHz = frequency,
        volume = volume
    )
}

fun testNoise() {
    val file = File("test_noise.wav")
    val engine = Engine()
    val header = WavHeader()

    val lpf = BiquadFilter(
        sampleRate = header.sampleRate.hz,
        cutoffFreq = 1.550
    )

    engine.writeSample(
        outputFile = file,
        waveform = Waveform.WHITE_NOISE,
        header = header,
        frequencyHz = 0.0,
        volume = 0.6,
        filter = lpf
    )
}

fun testFlanger() {
    val file = File("test_flanger.wav")
    val engine = Engine()
    val header = WavHeader(sampleDuration = SampleDuration._10SEC)

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
}

fun testDistortion() {
    val file = File("test_distortion.wav")
    val engine = Engine()
    val header = WavHeader(sampleDuration = SampleDuration._3SEC)

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
}

fun testClipper() {
    val file = File("test_clipper.wav")
    val engine = Engine()
    val header = WavHeader(sampleDuration = SampleDuration._3SEC)

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
}

fun testSpecialFx() {
    val file = File("test_special_fx.wav")
    val engine = Engine()
    val header = WavHeader(sampleDuration = SampleDuration._5SEC)

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
}

fun testExport() {
    val exporter = Exporter()
    val engine = Engine()

    val distortion = Distortion(
        drive = 8.0,
        saturation = 0.7,
        mix = 1.0,
        oversample = 4
    )

    // lo-fi 8-bit mono WAV (11025 Hz)
    exporter.export(
        outputFile = File("export_lofi.wav"),
        format = ExportFormat.WAV,
        preset = QualityPreset.LO_FI,
        duration = SampleDuration._3SEC
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

    // lo-fi 32 kbps MP3 with the merged flanger+distortion fx
    exporter.export(
        outputFile = File("export_lofi.mp3"),
        format = ExportFormat.MP3,
        preset = QualityPreset.LO_FI,
        duration = SampleDuration._3SEC
    ) { wavFile, header ->
        val flanger = Flanger(
            sampleRate = header.sampleRate.hz,
            rateHz = 0.2,
            depthMs = 4.0,
            feedback = 0.4,
            mix = 0.6
        )

        val specialFx = SpecialFx(
            flanger = flanger,
            distortion = distortion,
            feedback = 0.5,
            mix = 0.9
        )

        engine.writeSample(
            outputFile = wavFile,
            waveform = Waveform.SAWTOOTH,
            header = header,
            frequencyHz = 110.0,
            volume = 0.8,
            specialFx = specialFx
        )
    }

    // extreme lo-fi 8-bit 8000 Hz WAV + 8 kbps MP3
    exporter.export(
        outputFile = File("export_crunchy.wav"),
        format = ExportFormat.WAV,
        preset = QualityPreset.CRUNCHY,
        duration = SampleDuration._3SEC
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

    exporter.export(
        outputFile = File("export_crunchy.mp3"),
        format = ExportFormat.MP3,
        preset = QualityPreset.CRUNCHY,
        duration = SampleDuration._3SEC
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

    // hi-res 24-bit stereo WAV (48 kHz)
    exporter.export(
        outputFile = File("export_hires.wav"),
        format = ExportFormat.WAV,
        preset = QualityPreset.HI_RES,
        duration = SampleDuration._3SEC
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
}