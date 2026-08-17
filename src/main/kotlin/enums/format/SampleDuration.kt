package enums.format

import WavHeader

enum class SampleDuration(val seconds: Long) {
    _1SEC(1L),
    _3SEC(3L),
    _5SEC(5L),
    _10SEC(10L),
    _30SEC(30L),
    _1MIN(60L),
    _3MIN(180L);

    /**
     * dataSize = duration * sampleRate * channels bitDepth * bytesPerSample
     */
    fun toDataSize(
        sampleRate: SampleRate,
        channels: ChannelCount,
        bitDepth: BitDepth
    ): UInt {
        val bytesPerSample = bitDepth.bits.toLong() / 8L
        val bytesPerSecond = sampleRate.hz.toLong() * channels.value.toLong() * bytesPerSample

        return (seconds * bytesPerSecond).toUInt()
    }

    fun toDataSize(header: WavHeader): UInt {
        return (seconds * header.byteRate.toLong()).toUInt()
    }
}