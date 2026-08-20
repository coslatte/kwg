package ui

import engine.Patch
import engine.audio.LiveEngine
import java.awt.Component
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.border.EmptyBorder

/**
 * Every parameter of every module, wired straight to the live instances in
 * [patch]: moving a slider is heard on the next sample, and the same values are
 * what [Patch.snapshotChain] copies when a file is rendered.
 *
 * The sections are listed in the order the chain processes them.
 */
class FxRackPanel(
    private val patch: Patch,
    private val engine: LiveEngine,
    private val scope: ScopePanel
) : JPanel() {

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = Theme.BACKGROUND
        border = EmptyBorder(8, 8, 8, 8)

        addSection(specialFxSection())
        addSection(flangerSection())
        addSection(distortionSection())
        addSection(filterSection())
        addSection(masterSection())
        addSection(envelopeSection())
        addSection(displaySection())
    }

    private fun addSection(section: JPanel) {
        section.alignmentX = Component.LEFT_ALIGNMENT
        add(section)
        add(gap(8))
    }

    /** 1. flanger ⊗ distortion, with its own module instances. */
    private fun specialFxSection(): JPanel {
        val toggle = fxToggle("enabled", patch.specialFxEnabled) {
            patch.specialFxEnabled = it
            patch.syncEnabled()
        }

        return fxSection("1 · special fx (flanger ⊗ distortion)", toggle).apply {
            add(ParamSlider("feedback", 0.0, 0.95, patch.specialFx.feedback) {
                patch.specialFx.feedback = it
            })
            add(ParamSlider("mix", 0.0, 1.0, patch.specialFx.mix) {
                patch.specialFx.mix = it
            })

            add(gap())
            add(dimLabel("· inner flanger"))
            add(ParamSlider("rate", MIN_RATE_HZ, MAX_RATE_HZ, patch.specialFx.flanger.rateHz, "Hz", 2, true) {
                patch.specialFx.flanger.rateHz = it
            })
            add(ParamSlider("depth", MIN_DEPTH_MS, MAX_DEPTH_MS, patch.specialFx.flanger.depthMs, "ms", 2) {
                patch.specialFx.flanger.depthMs = it
            })
            add(ParamSlider("feedback", 0.0, MAX_FEEDBACK, patch.specialFx.flanger.feedback) {
                patch.specialFx.flanger.feedback = it
            })
            add(ParamSlider("mix", 0.0, 1.0, patch.specialFx.flanger.mix) {
                patch.specialFx.flanger.mix = it
            })

            add(gap())
            add(dimLabel("· inner distortion"))
            add(ParamSlider("drive", MIN_DRIVE, MAX_DRIVE, patch.specialFx.distortion.drive, "", 2, true) {
                patch.specialFx.distortion.drive = it
            })
            add(ParamSlider("saturation", 0.0, 1.0, patch.specialFx.distortion.saturation) {
                patch.specialFx.distortion.saturation = it
            })
            add(ParamSlider("mix", 0.0, 1.0, patch.specialFx.distortion.mix) {
                patch.specialFx.distortion.mix = it
            })
            add(ParamSlider("oversample", 1.0, 8.0, patch.specialFx.distortion.oversample.toDouble(), "x", 0) {
                patch.specialFx.distortion.oversample = it.toInt().coerceAtLeast(1)
            })
        }
    }

    /** 2. standalone flanger. */
    private fun flangerSection(): JPanel {
        val toggle = fxToggle("enabled", patch.flangerEnabled) {
            patch.flangerEnabled = it
            patch.syncEnabled()
        }

        return fxSection("2 · flanger", toggle).apply {
            add(ParamSlider("rate", MIN_RATE_HZ, MAX_RATE_HZ, patch.flanger.rateHz, "Hz", 2, true) {
                patch.flanger.rateHz = it
            })
            add(ParamSlider("depth", MIN_DEPTH_MS, MAX_DEPTH_MS, patch.flanger.depthMs, "ms", 2) {
                patch.flanger.depthMs = it
            })
            add(ParamSlider("feedback", 0.0, MAX_FEEDBACK, patch.flanger.feedback) {
                patch.flanger.feedback = it
            })
            add(ParamSlider("mix", 0.0, 1.0, patch.flanger.mix) {
                patch.flanger.mix = it
            })
        }
    }

    /** 3. standalone distortion. */
    private fun distortionSection(): JPanel {
        val toggle = fxToggle("enabled", patch.distortionEnabled) {
            patch.distortionEnabled = it
            patch.syncEnabled()
        }

        return fxSection("3 · distortion", toggle).apply {
            add(ParamSlider("drive", MIN_DRIVE, MAX_DRIVE, patch.distortion.drive, "", 2, true) {
                patch.distortion.drive = it
            })
            add(ParamSlider("saturation", 0.0, 1.0, patch.distortion.saturation) {
                patch.distortion.saturation = it
            })
            add(ParamSlider("mix", 0.0, 1.0, patch.distortion.mix) {
                patch.distortion.mix = it
            })
            add(ParamSlider("oversample", 1.0, 8.0, patch.distortion.oversample.toDouble(), "x", 0) {
                patch.distortion.oversample = it.toInt().coerceAtLeast(1)
            })
        }
    }

    /** 4. low pass biquad. */
    private fun filterSection(): JPanel {
        val toggle = fxToggle("enabled", patch.filterEnabled) {
            patch.filterEnabled = it
            patch.syncEnabled()
        }

        return fxSection("4 · filter (low pass 12 dB)", toggle).apply {
            add(ParamSlider("cutoff", MIN_CUTOFF_HZ, MAX_CUTOFF_HZ, patch.filter.cutoffFreq, "Hz", 0, true) {
                patch.filter.cutoffFreq = it
            })
            add(ParamSlider("resonance", 0.1, 12.0, patch.filter.q, "Q", 2, true) {
                patch.filter.q = it
            })
        }
    }

    /** 5. volume then the clipper: the pre-master stage. */
    private fun masterSection(): JPanel {
        val toggle = fxToggle("clipper enabled", patch.clipperEnabled) {
            patch.clipperEnabled = it
            patch.syncEnabled()
        }

        return fxSection("5 · master (volume → clipper)", toggle).apply {
            add(ParamSlider("volume", 0.0, 2.0, patch.liveChain.volume) {
                patch.liveChain.volume = it
            })
            add(ParamSlider("ceiling", -24.0, 0.0, patch.clipper.ceilingDb, "dB", 1) {
                patch.clipper.ceilingDb = it
            })
            add(ParamSlider("softness", 0.0, 1.0, patch.clipper.softness) {
                patch.clipper.softness = it
            })
        }
    }

    private fun envelopeSection(): JPanel = fxSection("envelope").apply {
        add(ParamSlider("attack", 0.5, 500.0, engine.attackMs, "ms", 1, true) {
            engine.attackMs = it
        })
        add(ParamSlider("release", 5.0, 2000.0, engine.releaseMs, "ms", 1, true) {
            engine.releaseMs = it
        })
    }

    private fun displaySection(): JPanel = fxSection("display").apply {
        add(ParamSlider("scope window", 128.0, 8192.0, scope.windowSamples.toDouble(), "smp", 0, true) {
            scope.windowSamples = it.toInt()
        })
    }

    private companion object {
        const val MIN_RATE_HZ = 0.01
        const val MAX_RATE_HZ = 10.0

        /** The flanger's delay line is 20 ms, so stay clear of the far end of it. */
        const val MIN_DEPTH_MS = 0.05
        const val MAX_DEPTH_MS = 18.0

        const val MAX_FEEDBACK = 0.95
        const val MIN_DRIVE = 0.1
        const val MAX_DRIVE = 40.0

        const val MIN_CUTOFF_HZ = 20.0
        const val MAX_CUTOFF_HZ = 18_000.0
    }
}
