import enums.engine.Waveform
import enums.format.BitDepth
import enums.format.ChannelCount
import enums.format.SampleDuration
import enums.format.SampleRate
import java.io.File
import java.io.FileOutputStream

fun main() {
    testHeader()
    testEngine()
}

fun testHeader() {
    val file = File("test.wav")

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
    val file = File("testEngine.wav")

    val engine = Engine()
    val header = WavHeader()

//    engine.writeSample(file, Waveform.SAWTOOTH, header)
}