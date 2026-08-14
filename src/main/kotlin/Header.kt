class Header {

    /**
     * this structure describe the ID's the header chunk constants needs by format
     */
    @JvmInline
    value class ChunkID(val value: UInt) {
        companion object {
            val RIFF = ChunkID(0x52494646u) // "RIFF"
            val WAVE = ChunkID(0x57415645u) // "WAVE"
            val FMT = ChunkID(0x666D7420u) // "fmt "
            val DATA = ChunkID(0x64617461u) // "data"
        }
    }

    data class WavHeader(
        // [Master RIFF chunk]
        val fileTypeBlocID: ChunkID = ChunkID.RIFF,
        val fileSize: UInt,
        val fileFormatBlocID: ChunkID = ChunkID.WAVE,

        // [Data «format» chunk]
        val formatBlocID: ChunkID = ChunkID.FMT,
        val blocSize: UInt,
        val audioFormat: UShort,
        val nbrChannels: UShort,
        val frequency: UInt,
        val bytePerSec: UInt,
        val bytePerBloc: UShort,
        val bitsPerSample: UShort,

        // [Data chunk]
        val dataBlocID: ChunkID = ChunkID.DATA,
        val dataSize: UInt,
    )
}