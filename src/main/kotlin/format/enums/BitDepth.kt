package format.enums

enum class BitDepth(val bits: UShort) {
    _8(8u),
    _16(16u),
    _24(24u),
    _32(32u);

    companion object {
        fun from(bits: Number): BitDepth = when (bits.toInt()) {
            8 -> _8
            16 -> _16
            24 -> _24
            32 -> _32
            else -> throw IllegalArgumentException("BitDepth not recognized: $bits")
        }
    }
}