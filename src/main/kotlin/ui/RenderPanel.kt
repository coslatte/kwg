package ui

import engine.Engine
import engine.Patch
import engine.audio.LiveEngine
import engine.export.ExportFormat
import engine.export.Exporter
import engine.export.QualityPreset
import format.enums.SampleDuration
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder
import java.io.File

/**
 * The render use case: freeze whatever is currently dialled in and write it to
 * disk as a WAV or MP3 at the chosen quality preset.
 *
 * The chain is copied through [Patch.snapshotChain] at the preset's sample rate,
 * so the file matches what the keyboard is playing and the live voice keeps
 * sounding while the render runs on its own thread.
 */
class RenderPanel(
    private val patch: Patch,
    private val liveEngine: LiveEngine
) : JPanel() {

    private val fileEngine = Engine()
    private val exporter = Exporter()

    private var format = ExportFormat.WAV
    private var preset = QualityPreset.STANDARD
    private var duration = SampleDuration._3SEC
    private var frequencyHz = 220.0

    private val nameField = JTextField("kwg_sample").apply {
        background = Theme.PANEL
        foreground = Theme.TEXT
        caretColor = Theme.ACCENT
        font = Theme.VALUE_FONT
        maximumSize = Dimension(Int.MAX_VALUE, 24)
    }

    private val frequencySlider = ParamSlider("frequency", 20.0, 4_000.0, frequencyHz, "Hz", 1, true) {
        frequencyHz = it
    }

    private val statusLabel = dimLabel("idle", Theme.VALUE_FONT)
    private val exportButton = JButton("render to file").apply {
        isFocusable = false
        alignmentX = Component.LEFT_ALIGNMENT
        addActionListener { export() }
    }

    // built once: JComboBox rejects a selection that is not one of its own items
    private val formatChoices = ExportFormat.entries
        .map { Choice(it.name.lowercase(), it) }
        .toTypedArray()

    private val presetChoices = QualityPreset.entries
        .map {
            Choice(
                "${it.name.lowercase()} · ${it.sampleRate.hz} Hz · ${it.bitDepth.bits} bit · ${it.mp3BitrateKbps}k",
                it
            )
        }
        .toTypedArray()

    private val durationChoices = SampleDuration.entries
        .map { Choice("${it.seconds} s", it) }
        .toTypedArray()

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = Theme.BACKGROUND
        border = EmptyBorder(10, 12, 10, 12)

        add(dimLabel("waveform and fx come from the rack — what you hear is what gets written"))
        add(gap(8))

        add(row("format", fxCombo(formatChoices, formatChoices.first { it.value == format }) {
            format = it.value
        }))
        add(row("quality", fxCombo(presetChoices, presetChoices.first { it.value == preset }) {
            preset = it.value
        }))
        add(row("duration", fxCombo(durationChoices, durationChoices.first { it.value == duration }) {
            duration = it.value
        }))

        add(gap())
        add(frequencySlider)
        add(JButton("← use last played key").apply {
            isFocusable = false
            alignmentX = Component.LEFT_ALIGNMENT
            addActionListener {
                liveEngine.lastNote()?.let { frequencySlider.set(LiveEngine.midiToFrequency(it)) }
            }
        })

        add(gap(8))
        add(row("file name", nameField))
        add(gap(8))
        add(exportButton)
        add(gap())
        add(statusLabel)
        add(gap())
        add(dimLabel("mp3 needs ffmpeg on PATH"))
    }

    private fun row(label: String, field: Component): JPanel = JPanel(BorderLayout(8, 0)).apply {
        background = Theme.BACKGROUND
        alignmentX = Component.LEFT_ALIGNMENT
        maximumSize = Dimension(Int.MAX_VALUE, 26)
        add(dimLabel(label).apply { preferredSize = Dimension(70, 20) }, BorderLayout.WEST)
        add(field, BorderLayout.CENTER)
    }

    private fun export() {
        val extension = if (format == ExportFormat.MP3) ".mp3" else ".wav"
        val typed = nameField.text.trim().ifEmpty { "kwg_sample" }
        val outputFile = File(if (typed.endsWith(extension, ignoreCase = true)) typed else typed + extension)

        val waveform = liveEngine.waveform
        val frequency = frequencyHz
        val chosenFormat = format
        val chosenPreset = preset
        val chosenDuration = duration

        exportButton.isEnabled = false
        statusLabel.text = "rendering…"

        Thread {
            val result = runCatching {
                exporter.export(
                    outputFile = outputFile,
                    format = chosenFormat,
                    preset = chosenPreset,
                    duration = chosenDuration
                ) { wavFile, header ->
                    fileEngine.renderToFile(
                        outputFile = wavFile,
                        waveform = waveform,
                        header = header,
                        frequencyHz = frequency,
                        chain = patch.snapshotChain(header.sampleRate.hz)
                    )
                }
                outputFile.absolutePath
            }

            SwingUtilities.invokeLater {
                statusLabel.text = result.fold(
                    onSuccess = { "wrote ${outputFile.name}" },
                    onFailure = { "failed: ${it.message ?: it::class.simpleName}" }
                )
                exportButton.isEnabled = true
            }
        }.apply { isDaemon = true }.start()
    }
}
