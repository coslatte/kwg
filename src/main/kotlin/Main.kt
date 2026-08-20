package app

import engine.Engine
import engine.enums.Waveform
import engine.export.ExportFormat
import engine.export.Exporter
import engine.export.QualityPreset
import engine.fxs.Distortion
import engine.fxs.Flanger
import engine.fxs.SpecialFx
import format.enums.SampleDuration
import ui.SynthUi
import java.io.File

private const val USAGE = """kwg - waveform generator

usage:
  (no arguments)  open the live synth: play it with the computer keyboard,
                  tweak every fx parameter, watch the scope, render to file
  --ui            same as above
  --demo          write one demo sample per quality preset to ./samples
  --help          show this
"""

fun main(args: Array<String>) {
    when {
        args.any { it == "--help" || it == "-h" } -> print(USAGE)
        args.any { it == "--demo" } -> renderDemoSamples()
        args.isEmpty() || args.any { it == "--ui" || it == "-ui" } -> SynthUi.launch()
        else -> {
            System.err.println("kwg: unknown argument '${args.first()}'")
            print(USAGE)
        }
    }
}

/**
 * One file per quality preset, so the presets can be compared by ear — from
 * 24-bit 48 kHz down to the 8-bit 8000 Hz crunch, plus the merged
 * flanger + distortion fx on the lo-fi mp3.
 */
private fun renderDemoSamples() {
    val samplesDir = File("resources", "samples").apply { mkdirs() }
    val exporter = Exporter()
    val engine = Engine()

    val demos = listOf(
        Demo("demo_lofi.wav", ExportFormat.WAV, QualityPreset.LO_FI, Waveform.SAWTOOTH, 110.0, 0.8),
        Demo("demo_lofi.mp3", ExportFormat.MP3, QualityPreset.LO_FI, Waveform.SAWTOOTH, 110.0, 0.8, specialFx = true),
        Demo("demo_crunchy.wav", ExportFormat.WAV, QualityPreset.CRUNCHY, Waveform.SAWTOOTH, 110.0, 0.8),
        Demo("demo_crunchy.mp3", ExportFormat.MP3, QualityPreset.CRUNCHY, Waveform.SAWTOOTH, 110.0, 0.8),
        Demo("demo_standard.wav", ExportFormat.WAV, QualityPreset.STANDARD, Waveform.SQUARE, 220.0, 0.7),
        Demo("demo_hires.wav", ExportFormat.WAV, QualityPreset.HI_RES, Waveform.TRIANGLE, 220.0, 0.6)
    )

    for (demo in demos) {
        val target = File(samplesDir, demo.name)

        try {
            exporter.export(
                outputFile = target,
                format = demo.format,
                preset = demo.preset,
                duration = SampleDuration._3SEC
            ) { wavFile, header ->
                // fresh fx per render: they carry state (delay line, previous input)
                val distortion = Distortion(drive = 8.0, saturation = 0.7, mix = 1.0, oversample = 4)

                val specialFx = if (demo.specialFx) {
                    SpecialFx(
                        flanger = Flanger(
                            sampleRate = header.sampleRate.hz,
                            rateHz = 0.2,
                            depthMs = 4.0,
                            feedback = 0.4,
                            mix = 0.6
                        ),
                        distortion = Distortion(drive = 12.0, saturation = 0.5, mix = 1.0, oversample = 4),
                        feedback = 0.5,
                        mix = 0.9
                    )
                } else null

                engine.writeSample(
                    outputFile = wavFile,
                    waveform = demo.waveform,
                    header = header,
                    frequencyHz = demo.frequencyHz,
                    volume = demo.volume,
                    distortion = distortion,
                    specialFx = specialFx
                )
            }

            println("wrote ${target.path}")
        } catch (failure: Exception) {
            // a missing ffmpeg should not stop the wav renders
            System.err.println("skipped ${demo.name}: ${failure.message ?: failure::class.simpleName}")
        }
    }
}

private class Demo(
    val name: String,
    val format: ExportFormat,
    val preset: QualityPreset,
    val waveform: Waveform,
    val frequencyHz: Double,
    val volume: Double,
    val specialFx: Boolean = false
)
