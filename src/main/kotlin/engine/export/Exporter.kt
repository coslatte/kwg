package engine.export

import format.WavHeader
import format.enums.SampleDuration
import java.io.File

class Exporter(private val ffmpegCommand: String = "ffmpeg") {

    /**
     * Renders audio through the provided [render] callback (which must write a
     * WAV with the given [header]), then either keeps it as WAV or transcodes
     * to MP3 with ffmpeg using the preset's bitrate.
     */
    fun export(
        outputFile: File,
        format: ExportFormat,
        preset: QualityPreset,
        duration: SampleDuration,
        render: (wavFile: File, header: WavHeader) -> Unit
    ) {
        when (format) {
            ExportFormat.WAV -> renderWav(outputFile, preset, duration, render)
            ExportFormat.MP3 -> exportMp3(outputFile, preset, duration, render)
        }
    }

    private fun renderWav(
        target: File,
        preset: QualityPreset,
        duration: SampleDuration,
        render: (wavFile: File, header: WavHeader) -> Unit
    ) {
        val header = WavHeader(
            channels = preset.channels,
            sampleRate = preset.sampleRate,
            bitDepth = preset.bitDepth,
            sampleDuration = duration
        )
        render(target, header)
    }

    private fun exportMp3(
        outputFile: File,
        preset: QualityPreset,
        duration: SampleDuration,
        render: (wavFile: File, header: WavHeader) -> Unit
    ) {
        val tempWav = File.createTempFile("kwg_render_", ".wav")
        try {
            renderWav(tempWav, preset, duration, render)

            val command = listOf(
                ffmpegCommand,
                "-y",
                "-hide_banner",
                "-loglevel", "error",
                "-i", tempWav.absolutePath,
                "-codec:a", "libmp3lame",
                "-b:a", "${preset.mp3BitrateKbps}k",
                "-ar", preset.sampleRate.hz.toString(),
                "-ac", preset.channels.value.toString(),
                outputFile.absolutePath
            )

            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            val log = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()

            if (exitCode != 0) {
                throw IllegalStateException("ffmpeg failed (exit $exitCode): $log")
            }
        } finally {
            tempWav.delete()
        }
    }
}