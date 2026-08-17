import enums.BitDepth
import enums.ChannelCount
import enums.SampleDuration
import enums.SampleRate
import java.io.File
import java.io.FileOutputStream

fun main() {
    test()
}

fun test() {
    val wavFile = File("test.wav")

    val header = WavHeader(
        channels = ChannelCount.MONO,
        sampleRate = SampleRate._44100,
        bitDepth = BitDepth._16,
        sampleDuration = SampleDuration._1SEC
    )

    FileOutputStream(wavFile).use { fos ->
        fos.write(header.toByteArray())
        fos.write(ByteArray(header.dataSize.toInt())) // add silence data for test
    }

    return
}