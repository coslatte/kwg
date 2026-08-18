import engine.Engine
import engine.enums.Waveform
import engine.fxs.BiquadFilter
import engine.fxs.Flanger
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