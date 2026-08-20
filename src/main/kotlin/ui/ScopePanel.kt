package ui

import engine.audio.LiveEngine
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import javax.swing.Timer
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Real-time audio display: reads the latest samples straight from the engine's
 * scope buffer every frame and draws them as an oscilloscope trace.
 *
 * Everything is also reported as a number, because "is it hitting 0 dB?" is not a
 * question a trace can answer: peak and RMS in dBFS, a peak hold that only ever
 * goes up, the clipper's ceiling drawn across the trace, and a clip counter. Click
 * the panel to reset the hold and the counter.
 */
class ScopePanel(
    private val engine: LiveEngine,
    private val ceilingDb: () -> Double = { 0.0 }
) : RoundedPanel(Theme.SCOPE, Theme.BORDER) {

    /** How many samples the trace spans. Lower = more zoomed in on the waveform. */
    var windowSamples: Int = 2048

    private var displayPeak = 0.0f
    private var holdPeak = 0.0f
    private var rms = 0.0f
    private var clipHoldFrames = 0
    private var clipCount = 0

    init {
        preferredSize = Dimension(720, 300)
        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(event: MouseEvent) = resetHold()
        })
        Timer(FRAME_MS) { repaint() }.apply { isRepeats = true }.start()
    }

    /** Clears the peak hold and the clip counter. */
    fun resetHold() {
        holdPeak = 0.0f
        clipCount = 0
        repaint()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)

        val g2 = (g.create() as Graphics2D).smooth()
        try {
            // stay inside the rounded card, or the trace paints over its corners
            g2.clip(RoundRectangle2D.Double(1.0, 1.0, width - 2.0, height - 2.0, RADIUS, RADIUS))

            val samples = engine.scopeSnapshot(windowSamples)
            measure(samples)

            val traceTop = HEADER_HEIGHT
            val traceHeight = (height - HEADER_HEIGHT - METER_HEIGHT).coerceAtLeast(40)

            drawHeader(g2)
            drawGrid(g2, traceTop, traceHeight)
            drawTrace(g2, traceTop, traceHeight, samples)
            drawCeiling(g2, traceTop, traceHeight)
            drawMeter(g2, traceTop + traceHeight)
        } finally {
            g2.dispose()
        }
    }

    /** Folds this frame's block into the meters. */
    private fun measure(samples: FloatArray) {
        val peak = engine.scopePeak()

        // fast attack, slow decay: a single loud frame stays readable
        displayPeak = if (peak > displayPeak) peak else max(peak, displayPeak - PEAK_DECAY)
        holdPeak = max(holdPeak, peak)

        if (peak >= CLIP_LEVEL) {
            if (clipHoldFrames == 0) clipCount++
            clipHoldFrames = CLIP_HOLD_FRAMES
        } else if (clipHoldFrames > 0) {
            clipHoldFrames--
        }

        rms = if (samples.isEmpty()) {
            0.0f
        } else {
            var sum = 0.0
            for (sample in samples) sum += sample.toDouble() * sample
            sqrt(sum / samples.size).toFloat()
        }
    }

    /** The numbers: peak, hold, RMS, and the clip light. */
    private fun drawHeader(g2: Graphics2D) {
        val clipping = clipHoldFrames > 0
        val baseline = 22

        g2.font = Theme.READOUT_FONT
        g2.color = if (clipping) Theme.DANGER else Theme.ACCENT
        g2.drawString(dbText(displayPeak), MARGIN, baseline)
        val peakWidth = g2.fontMetrics.stringWidth(dbText(displayPeak))

        g2.font = SMALL_FONT
        g2.color = Theme.TEXT_FAINT
        g2.drawString("PEAK", MARGIN, baseline + 13)

        var x = MARGIN + peakWidth + 22
        x += readout(g2, x, baseline, "HOLD", dbText(holdPeak), if (holdPeak >= CLIP_LEVEL) Theme.DANGER else Theme.TEXT)
        x += readout(g2, x, baseline, "RMS", dbText(rms), Theme.TEXT_DIM)
        readout(g2, x, baseline, "CEILING", "%+.1f dB".format(ceilingDb()), Theme.TEXT_DIM)

        // clip light, top right
        val lightSize = 9.0
        val lightX = width - MARGIN - lightSize
        g2.color = if (clipping) Theme.DANGER else Theme.TRACK
        g2.fill(Ellipse2D.Double(lightX, 9.0, lightSize, lightSize))

        g2.font = SMALL_FONT
        val label = if (clipCount > 0) "CLIP $clipCount" else "CLIP"
        val labelWidth = g2.fontMetrics.stringWidth(label)
        g2.color = if (clipCount > 0) Theme.DANGER else Theme.TEXT_FAINT
        g2.drawString(label, (lightX - 6 - labelWidth).toInt(), 18)
    }

    /** One small labelled number; returns the width it used. */
    private fun readout(g2: Graphics2D, x: Int, baseline: Int, caption: String, value: String, color: Color): Int {
        g2.font = Theme.VALUE_FONT_BOLD
        g2.color = color
        g2.drawString(value, x, baseline - 2)
        val valueWidth = g2.fontMetrics.stringWidth(value)

        g2.font = SMALL_FONT
        g2.color = Theme.TEXT_FAINT
        g2.drawString(caption, x, baseline + 11)

        return max(valueWidth, g2.fontMetrics.stringWidth(caption)) + 20
    }

    private fun drawGrid(g2: Graphics2D, top: Int, scopeHeight: Int) {
        val midY = top + scopeHeight / 2.0
        val amplitude = scopeHeight / 2.0 * TRACE_HEADROOM

        g2.stroke = BasicStroke(1.0f)
        g2.color = Theme.PANEL
        for (division in 1 until GRID_DIVISIONS) {
            val x = width * division / GRID_DIVISIONS
            g2.drawLine(x, top, x, top + scopeHeight)
        }

        // amplitude rails, labelled in dB — the trace can be read off them
        g2.font = TINY_FONT
        for (db in RAILS) {
            val offset = 10.0.pow(db / 20.0) * amplitude
            g2.color = if (db == 0) Theme.BORDER_HI else Theme.PANEL_HI

            g2.drawLine(RAIL_LABEL_WIDTH, (midY - offset).toInt(), width - MARGIN, (midY - offset).toInt())
            g2.drawLine(RAIL_LABEL_WIDTH, (midY + offset).toInt(), width - MARGIN, (midY + offset).toInt())

            g2.color = Theme.TEXT_FAINT
            g2.drawString("%3d".format(db), MARGIN, (midY - offset).toInt() + 3)
        }

        g2.color = Theme.BORDER
        g2.drawLine(RAIL_LABEL_WIDTH, midY.toInt(), width - MARGIN, midY.toInt())
    }

    private fun drawTrace(g2: Graphics2D, top: Int, scopeHeight: Int, samples: FloatArray) {
        if (samples.size < 2) return

        val midY = top + scopeHeight / 2.0
        val amplitude = scopeHeight / 2.0 * TRACE_HEADROOM
        val left = RAIL_LABEL_WIDTH.toDouble()
        val span = width - MARGIN - left
        val stepX = span / (samples.size - 1)

        val path = Path2D.Double()
        path.moveTo(left, midY - samples[0] * amplitude)
        for (i in 1 until samples.size) {
            path.lineTo(left + i * stepX, midY - samples[i] * amplitude)
        }

        // a soft copy underneath makes the trace read as a glow rather than a hairline
        g2.color = Theme.accent(45)
        g2.stroke = BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g2.draw(path)

        g2.color = Theme.ACCENT
        g2.stroke = BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g2.draw(path)
        g2.stroke = BasicStroke(1.0f)
    }

    /** Where the clipper is actually holding the signal, drawn across the trace. */
    private fun drawCeiling(g2: Graphics2D, top: Int, scopeHeight: Int) {
        val ceiling = ceilingDb()
        if (ceiling > 0.05) return

        val midY = top + scopeHeight / 2.0
        val amplitude = scopeHeight / 2.0 * TRACE_HEADROOM
        val offset = 10.0.pow(ceiling / 20.0) * amplitude

        g2.color = Theme.danger(120)
        g2.stroke = BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1.0f, floatArrayOf(4f, 4f), 0f)
        g2.drawLine(RAIL_LABEL_WIDTH, (midY - offset).toInt(), width - MARGIN, (midY - offset).toInt())
        g2.drawLine(RAIL_LABEL_WIDTH, (midY + offset).toInt(), width - MARGIN, (midY + offset).toInt())
        g2.stroke = BasicStroke(1.0f)
    }

    /** The level bar, scaled in dB with labelled ticks so a glance gives a number. */
    private fun drawMeter(g2: Graphics2D, top: Int) {
        val barY = top + 12.0
        val left = RAIL_LABEL_WIDTH.toDouble()
        val barWidth = width - MARGIN - left
        if (barWidth <= 0) return

        fun xFor(db: Double): Double =
            left + ((db.coerceIn(METER_FLOOR, 0.0) - METER_FLOOR) / -METER_FLOOR) * barWidth

        g2.color = Theme.TRACK
        g2.fill(RoundRectangle2D.Double(left, barY, barWidth, BAR_HEIGHT, BAR_HEIGHT, BAR_HEIGHT))

        val peakDb = amplitudeToDb(displayPeak)
        if (peakDb > METER_FLOOR) {
            val filled = xFor(peakDb) - left
            g2.color = if (clipHoldFrames > 0) Theme.DANGER else Theme.ACCENT
            g2.fill(RoundRectangle2D.Double(left, barY, filled, BAR_HEIGHT, BAR_HEIGHT, BAR_HEIGHT))
        }

        // the hold, as a thin marker that stays put
        val holdDb = amplitudeToDb(holdPeak)
        if (holdDb > METER_FLOOR) {
            val holdX = xFor(holdDb)
            g2.color = if (holdPeak >= CLIP_LEVEL) Theme.DANGER else Theme.TEXT
            g2.fill(RoundRectangle2D.Double(holdX - 1, barY - 2, 2.0, BAR_HEIGHT + 4, 2.0, 2.0))
        }

        // the ceiling, in the same place on the scale
        val ceiling = ceilingDb()
        if (ceiling in METER_FLOOR..0.0) {
            val ceilingX = xFor(ceiling)
            g2.color = Theme.danger(160)
            g2.fill(RoundRectangle2D.Double(ceilingX - 1, barY - 4, 2.0, BAR_HEIGHT + 8, 2.0, 2.0))
        }

        g2.font = TINY_FONT
        g2.color = Theme.TEXT_FAINT
        for (db in METER_TICKS) {
            val tick = db.toDouble()
            val x = xFor(tick)
            g2.drawLine(x.toInt(), (barY + BAR_HEIGHT + 3).toInt(), x.toInt(), (barY + BAR_HEIGHT + 5).toInt())

            val label = if (db == 0) "0" else db.toString()
            val labelWidth = g2.fontMetrics.stringWidth(label)
            val labelX = (x - labelWidth / 2).coerceIn(left, width - MARGIN - labelWidth.toDouble())
            g2.drawString(label, labelX.toInt(), (barY + BAR_HEIGHT + 14).toInt())
        }

        g2.color = Theme.TEXT_FAINT
        g2.drawString("dBFS", MARGIN, (barY + BAR_HEIGHT).toInt())
    }

    private fun dbText(amplitude: Float): String {
        val db = amplitudeToDb(amplitude)
        return if (db <= METER_FLOOR) "  -inf" else "%+6.1f".format(db)
    }

    private fun amplitudeToDb(amplitude: Float): Double {
        val magnitude = abs(amplitude.toDouble())
        return if (magnitude <= 0.0) METER_FLOOR else max(METER_FLOOR, 20.0 * log10(magnitude))
    }

    private companion object {
        const val FRAME_MS = 16
        const val GRID_DIVISIONS = 8
        const val HEADER_HEIGHT = 36
        const val METER_HEIGHT = 44
        const val MARGIN = 8
        const val RAIL_LABEL_WIDTH = 30
        const val BAR_HEIGHT = 8.0
        const val TRACE_HEADROOM = 0.92
        const val CLIP_LEVEL = 0.999f
        const val CLIP_HOLD_FRAMES = 30
        const val PEAK_DECAY = 0.02f
        const val METER_FLOOR = -60.0

        val RADIUS = Theme.RADIUS.toDouble()

        /** Amplitude rails drawn across the trace, in dB below full scale. */
        val RAILS = intArrayOf(0, -6, -12)
        val METER_TICKS = intArrayOf(-60, -48, -36, -24, -18, -12, -6, 0)

        val SMALL_FONT: Font = Theme.VALUE_FONT.deriveFont(9f)
        val TINY_FONT: Font = SMALL_FONT
    }
}
