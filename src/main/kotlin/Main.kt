import engine.Engine
import engine.enums.Waveform
import engine.filters.BiquadFilter
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
        cutoffFreq = 880.0
    )

    engine.writeSample(
        outputFile = file,
        waveform = Waveform.NOISE,
        header = header,
        frequencyHz = 0.0,
        volume = 0.6,
        filter = lpf
    )
}