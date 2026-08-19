package engine.export

import format.enums.BitDepth
import format.enums.ChannelCount
import format.enums.SampleRate

enum class QualityPreset(
    val sampleRate: SampleRate,
    val bitDepth: BitDepth,
    val channels: ChannelCount,
    val mp3BitrateKbps: Int
) {
    HI_RES(SampleRate._48000, BitDepth._24, ChannelCount.STEREO, 320),
    STANDARD(SampleRate._44100, BitDepth._16, ChannelCount.STEREO, 192),
    LO_FI(SampleRate._11025, BitDepth._8, ChannelCount.MONO, 32),
    CRUNCHY(SampleRate._8000, BitDepth._8, ChannelCount.MONO, 8);
}