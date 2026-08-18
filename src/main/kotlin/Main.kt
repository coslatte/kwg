import engine.Engine
import engine.enums.Waveform
import format.enums.BitDepth
import format.enums.ChannelCount
import format.enums.SampleDuration
import format.enums.SampleRate
import format.WavHeader
import java.io.File
import java.io.FileOutputStream

fun main() {
    testHeader()
    testEngine()
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