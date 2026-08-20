package engine

import engine.enums.Waveform
import engine.fxs.BiquadFilter
import engine.fxs.Clipper
import engine.fxs.Distortion
import engine.fxs.Flanger
import engine.fxs.SpecialFx
import format.WavHeader
import format.enums.BitDepth
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

class Engine {
    private fun calculateSample(type: Waveform, frequency: Double, time: Double): Double {
        val phase = frequency * time
        return when (type) {
            Waveform.SINE -> sin(2.0 * PI * phase)
            Waveform.SQUARE -> if (sin(2.0 * PI * phase) >= 0) 1.0 else -1.0
            Waveform.SAWTOOTH -> 2.0 * (phase - floor(phase + 0.5))
            Waveform.TRIANGLE -> 2.0 * abs(2.0 * (phase - floor(phase + 0.5))) - 1.0
            Waveform.WHITE_NOISE -> Random.nextDouble(-1.0, 1.0)
        }
    }

    fun writeSample(
        outputFile: File,
        waveform: Waveform,
        header: WavHeader,
        frequencyHz: Double,
        volume: Double,
        filter: BiquadFilter? = null,
        flanger: Flanger? = null,
        distortion: Distortion? = null,
        specialFx: SpecialFx? = null,
        clipper: Clipper? = null
    ) {
        renderToFile(
            outputFile,
            waveform,
            header,
            frequencyHz,
            SignalChain(
                volume = volume,
                specialFx = specialFx,
                flanger = flanger,
                distortion = distortion,
                filter = filter,
                clipper = clipper
            )
        )
    }

    fun renderToFile(
        outputFile: File,
        waveform: Waveform,
        header: WavHeader,
        frequencyHz: Double,
        chain: SignalChain
    ) {
        val totalSamples = header.sampleRate.hz.toLong() * header.sampleDuration.seconds

        FileOutputStream(outputFile).use { fos ->
            val output = DataOutputStream(fos)

            output.write(header.toByteArray())

            for (n in 0 until totalSamples) {
                val time = n.toDouble() / header.sampleRate.hz.toDouble()
                val rawSample = calculateSample(waveform, frequencyHz, time)
                val processed = chain.process(rawSample)

                writeChannel(
                    output,
                    scaleValue(processed, header.bitDepth),
                    header
                )
            }
        }
    }

    private fun writeChannel(fileReference: DataOutputStream, value: Int, header: WavHeader) {
        for (ch in 0 until header.channels.value.toInt()) {
            when (header.bitDepth) {
                BitDepth._8 -> fileReference.writeByte(value and 0xFF)
                BitDepth._16 -> {
                    fileReference.writeByte(value and 0xFF)          // lower byte
                    fileReference.writeByte((value shr 8 and 0xFF))  // upper byte
                }
                BitDepth._24 -> {
                    fileReference.writeByte(value and 0xFF)
                    fileReference.writeByte((value shr 8) and 0xFF)
                    fileReference.writeByte((value shr 16) and 0xFF)
                }
                BitDepth._32 -> {
                    fileReference.writeByte(value and 0xFF)
                    fileReference.writeByte((value shr 8) and 0xFF)
                    fileReference.writeByte((value shr 16) and 0xFF)
                    fileReference.writeByte((value shr 24) and 0xFF)
                }
            }
        }
    }

    /**
     * Scales a final (volume/clipper processed) sample into the target bit depth range.
     */
    private fun scaleValue(sample: Double, bitDepth: BitDepth): Int {
        return when (bitDepth) {
            BitDepth._8 -> (sample * 127.0 + 128.0).toInt().coerceIn(0, 255)
            BitDepth._16 -> (sample * Short.MAX_VALUE).toInt().coerceIn(
                Short.MIN_VALUE.toInt(),
                Short.MAX_VALUE.toInt()
            )
            BitDepth._24 -> (sample * 8388607.0).toInt().coerceIn(-8388608, 8388607)
            BitDepth._32 -> (sample * 2147483647.0).toLong().coerceIn(
                Int.MIN_VALUE.toLong(),
                Int.MAX_VALUE.toLong()
            ).toInt()
        }
    }
}