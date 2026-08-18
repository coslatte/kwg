package engine

import engine.enums.Waveform
import format.WavHeader
import format.enums.BitDepth
import format.enums.ChannelCount
import format.enums.SampleRate
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin
import kotlin.ranges.until

class Engine(
    private val sampleRate: SampleRate = SampleRate._44100,
    private val channels: ChannelCount = ChannelCount.MONO,
    private val bitDepth: BitDepth = BitDepth._16
) {
    private fun calculateSample(type: Waveform, frequency: Double, time: Double): Double {
        val phase = frequency * time
        return when (type) {
            Waveform.SINE -> sin(2.0 * PI * phase)
            Waveform.SQUARE -> if (sin(2.0 * PI * phase) >= 0) 1.0 else -1.0
            Waveform.SAWTOOTH -> 2.0 * (phase - floor(phase + 0.5))
            Waveform.TRIANGLE -> 2.0 * abs(2.0 * (phase - floor(phase + 0.5))) - 1.0
        }
    }

    fun writeSample(
        outputFile: File,
        waveform: Waveform,
        header: WavHeader,
        frequencyHz: Double,
        volume: Double,
    ) {
        val totalSamples = header.sampleRate.hz.toLong() * header.sampleDuration.seconds

        FileOutputStream(outputFile).use { fos ->
            val output = DataOutputStream(fos)

            output.write(header.toByteArray())

            for (n in 0 until totalSamples) {
                val time = n.toDouble() / sampleRate.hz.toDouble()
                val rawSample = calculateSample(waveform, frequencyHz, time)

                writeChannel(
                    output,
                    scaleValue(rawSample, volume)
                )
            }
        }
    }

    private fun writeChannel(fileReference: DataOutputStream, value: Int) {
        for (ch in 0 until channels.value.toInt())
            if (bitDepth == BitDepth._16) {
                fileReference.writeByte(value and 0xFF)          // lower byte
                fileReference.writeByte((value shr 8 and 0xFF))  // upper byte
            }
    }

    private fun scaleValue(rawSample: Double, volume: Double): Int {
        val sampleSlice = (rawSample * volume * Short.MAX_VALUE)
        return sampleSlice.toInt().coerceIn(
            Short.MIN_VALUE.toInt(),
            Short.MAX_VALUE.toInt()
        )
    }
}