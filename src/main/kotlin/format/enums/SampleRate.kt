package format.enums

enum class SampleRate(val hz: UInt) {
    _8000(8000u),
    _11025(11025u),
    _22050(22050u),
    _44100(44100u),
    _48000(48000u),
    _96000(96000u);
}