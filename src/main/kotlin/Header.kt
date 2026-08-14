class Header {

    @JvmInline
    value class ChunkID(val chunkID: Int) {
        companion object {
            val RIFF: Int = "RIFF".toInt()
        }
    }
}