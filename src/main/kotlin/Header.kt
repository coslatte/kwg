import ChunkID.Companion.DATA
import ChunkID.Companion.FMT
import ChunkID.Companion.RIFF
import ChunkID.Companion.WAVE
import enums.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * this structure describe the ID's the header chunk constants needs by format
 */
@JvmInline
private value class ChunkID(val value: ByteArray) {
    companion object {
        val RIFF = "RIFF".toByteArray(Charsets.US_ASCII)
        val WAVE = "WAVE".toByteArray(Charsets.US_ASCII)
        val FMT = "fmt ".toByteArray(Charsets.US_ASCII)
        val DATA = "data".toByteArray(Charsets.US_ASCII)
    }
}

data class WavHeader(
    val channels: ChannelCount = ChannelCount.MONO,
    val sampleRate: SampleRate = SampleRate._44100,
    val bitDepth: BitDepth = BitDepth._16,
    val sampleDuration: SampleDuration = SampleDuration._1SEC,
    val audioFormat: AudioFormat = AudioFormat.PCM,
) {
    val byteRate: UInt = sampleRate.hz * channels.value.toUInt() * (bitDepth.bits.toUInt() / 8u)
    val dataSize: UInt = (sampleDuration.seconds.toULong() * byteRate.toULong()).toUInt()

    val subchunk1Size: UInt = 16u
    val blockAlign: UShort = (channels.value * bitDepth.bits / 8u).toUShort()
    val chunkSizeBytes: UInt = 32u * dataSize

    fun toByteArray(): ByteArray {
        val buffer = ByteBuffer.allocate(44).apply {
            order(ByteOrder.LITTLE_ENDIAN)

            /*
            'RIFF' -> chunk size -> 'WAVE'
            'fmt ' -> subchunk1size -> audio format -> channels -> sample rate -> byte rate -> block align -> bit depth
            'data' -> data size
             */

            // [master RIFF chunk]
            put(RIFF)
            putInt(chunkSizeBytes.toInt())
            put(WAVE)

            // [format chunk]
            put(FMT)
            putInt(subchunk1Size.toInt())
            putShort(audioFormat.code.toShort())
            putShort(channels.value.toShort())
            putInt(sampleRate.hz.toInt())
            putInt(byteRate.toInt())
            putShort(blockAlign.toShort())

            // [data chunk]
            put(DATA)
            putInt(chunkSizeBytes.toInt())
        }
        return buffer.array()
    }

    override fun toString(): String {
        return """WavHeader(
    channels=$channels (value = ${channels.value}),
    sampleRate=$sampleRate (${(sampleRate.hz)} Hz),
    bitDepth=$bitDepth (${bitDepth.bits} bits),
    sampleDuration=$sampleDuration (${sampleDuration.seconds} seconds :: (${sampleDuration.seconds / 60} mins)),
    audioFormat=$audioFormat (${audioFormat.code.toShort()}),
    byteRate=$byteRate,
    dataSize=$dataSize,
    subchunk1Size=$subchunk1Size,
    blockAlign=$blockAlign,
    chunkSizeBytes=$chunkSizeBytes)"""
    }
}
