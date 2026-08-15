import enums.BitDepth
import enums.ChannelCount
import enums.SampleDuration
import enums.SampleRate

fun main() {
    val header = WavHeader(
        channels = ChannelCount.MONO,
        sampleRate = SampleRate._44100,
        bitDepth = BitDepth._16,
        sampleDuration = SampleDuration._1SEC
    )

    println(header.toString())
}
