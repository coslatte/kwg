package ui

import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder
import javax.swing.event.MouseInputAdapter
import javax.swing.plaf.basic.BasicComboBoxUI
import javax.swing.plaf.basic.BasicScrollBarUI
import javax.swing.plaf.basic.BasicSliderUI
import javax.swing.plaf.basic.ComboPopup
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * One fx module: a rounded card with its heading and bypass switch in the header,
 * then a row per parameter.
 */
fun fxSection(title: String, toggle: JComponent? = null): JPanel {
    val panel = RoundedPanel(Theme.PANEL)
    panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
    panel.alignmentX = Component.LEFT_ALIGNMENT
    panel.border = EmptyBorder(9, 12, 12, 12)

    val header = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
        maximumSize = Dimension(Int.MAX_VALUE, 22)

        add(sectionTitle(title))
        add(Box.createHorizontalGlue())
        if (toggle != null) add(toggle)
    }

    panel.add(header)
    panel.add(gap(8))
    return panel
}

/** A pill switch that never takes keyboard focus, so the piano keys keep working. */
fun fxToggle(label: String, selected: Boolean, onChange: (Boolean) -> Unit): ToggleSwitch =
    ToggleSwitch(label, selected, onChange)

fun <T> fxCombo(items: Array<T>, selected: T, onChange: (T) -> Unit): JComboBox<T> =
    JComboBox(items).apply {
        selectedItem = selected
        isFocusable = false
        font = Theme.LABEL_FONT
        foreground = Theme.TEXT
        background = Theme.PANEL_HI
        maximumRowCount = 12
        setUI(FlatComboUI())
        border = LineBorder(Theme.BORDER, 1, true)

        // the popup is a separate JList: it keeps the Metal white unless told otherwise
        (ui.getAccessibleChild(this, 0) as? ComboPopup)?.list?.apply {
            background = Theme.PANEL_HI
            foreground = Theme.TEXT
            selectionBackground = Theme.ACCENT_DEEP
            selectionForeground = Theme.TEXT
            font = Theme.LABEL_FONT
        }

        addActionListener {
            @Suppress("UNCHECKED_CAST")
            onChange(selectedItem as T)
        }
    }

/** A flat, dark push button — no bevel, no focus ring. */
fun fxButton(text: String, accent: Boolean = false, onClick: () -> Unit): JButton =
    object : JButton(text) {
        override fun paintComponent(g: Graphics) {
            val g2 = (g.create() as Graphics2D).smooth()
            try {
                val shape = RoundRectangle2D.Double(0.5, 0.5, width - 1.0, height - 1.0, 8.0, 8.0)
                g2.color = when {
                    model.isPressed -> if (accent) Theme.ACCENT_DEEP else Theme.BORDER_HI
                    model.isRollover -> Theme.PANEL_HI
                    else -> Theme.PANEL
                }
                g2.fill(shape)
                g2.color = if (accent) Theme.ACCENT_DEEP else Theme.BORDER
                g2.draw(shape)
            } finally {
                g2.dispose()
            }
            super.paintComponent(g)
        }
    }.apply {
        isFocusable = false
        isContentAreaFilled = false
        isBorderPainted = false
        isOpaque = false
        isRolloverEnabled = true
        font = Theme.LABEL_FONT
        foreground = if (accent) Theme.ACCENT else Theme.TEXT
        border = EmptyBorder(6, 14, 6, 14)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        alignmentX = Component.LEFT_ALIGNMENT
        addActionListener { onClick() }
    }

fun dimLabel(text: String, font: Font = Theme.LABEL_FONT): JLabel =
    JLabel(text).apply {
        foreground = Theme.TEXT_DIM
        this.font = font
        alignmentX = Component.LEFT_ALIGNMENT
    }

/** Vertical spacing between control rows. */
fun gap(height: Int = 6): Component = Box.createVerticalStrut(height)

/** Wraps a value with the label a combo box should show for it. */
class Choice<T>(private val label: String, val value: T) {
    override fun toString(): String = label
}

/**
 * A labelled slider over a `Double` range that reports every move through
 * [onChange], so the live chain hears the new value on the next sample.
 *
 * Clicking anywhere on the track jumps the value to that point and keeps
 * tracking the mouse, and a double click restores the value it opened with.
 *
 * [logarithmic] spreads the travel evenly in octaves instead of hertz, which is
 * what you want for cutoff frequencies and lfo rates.
 */
class ParamSlider(
    label: String,
    private val min: Double,
    private val max: Double,
    private val initial: Double,
    private val unit: String = "",
    private val decimals: Int = 2,
    private val logarithmic: Boolean = false,
    private val onChange: (Double) -> Unit
) : JPanel() {

    private val valueLabel = JLabel().apply {
        foreground = Theme.ACCENT
        font = Theme.VALUE_FONT
        horizontalAlignment = JLabel.RIGHT
    }

    private val slider = JSlider(0, TICKS, toTicks(initial)).apply {
        isOpaque = false
        isFocusable = false
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        maximumSize = Dimension(Int.MAX_VALUE, TRACK_HEIGHT)
        preferredSize = Dimension(160, TRACK_HEIGHT)
        addChangeListener { publish(fromTicks(value)) }
    }

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
        maximumSize = Dimension(Int.MAX_VALUE, ROW_HEIGHT)

        val header = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, 16)

            add(dimLabel(label))
            add(Box.createHorizontalGlue())
            add(valueLabel)
        }

        val flatUi = FlatSliderUI(slider)
        slider.setUI(flatUi)

        // click anywhere on the track to set the value, then keep following the mouse
        val track = object : MouseInputAdapter() {
            override fun mousePressed(event: MouseEvent) {
                if (event.clickCount >= 2) set(initial) else slider.value = flatUi.valueAt(event.x)
            }

            override fun mouseDragged(event: MouseEvent) {
                slider.value = flatUi.valueAt(event.x)
            }
        }
        slider.addMouseListener(track)
        slider.addMouseMotionListener(track)

        add(header)
        add(slider)
        render(initial)
    }

    /** Moves the slider without firing [onChange] twice, e.g. from a preset. */
    fun set(value: Double) {
        slider.value = toTicks(value)
    }

    private fun publish(value: Double) {
        render(value)
        onChange(value)
    }

    private fun render(value: Double) {
        valueLabel.text = "%.${decimals}f%s".format(value, if (unit.isEmpty()) "" else " $unit")
    }

    private fun toTicks(value: Double): Int {
        val clamped = value.coerceIn(min, max)
        val position = if (logarithmic) {
            ln(clamped / min) / ln(max / min)
        } else {
            (clamped - min) / (max - min)
        }
        return (position * TICKS).roundToInt()
    }

    private fun fromTicks(ticks: Int): Double {
        val position = ticks.toDouble() / TICKS
        return if (logarithmic) min * (max / min).pow(position) else min + position * (max - min)
    }

    private companion object {
        const val TICKS = 1000
        const val TRACK_HEIGHT = 20
        const val ROW_HEIGHT = 42
    }
}

/**
 * Flat horizontal slider: a thin track, the travelled part filled with the accent
 * colour, and a round handle. The whole track is a click target — Swing's default
 * only pages the value when you miss the handle, which is the opposite of what
 * every synth does.
 */
private class FlatSliderUI(private val host: JSlider) : BasicSliderUI(host) {

    /** The value under a pixel, exposed so the panel can jump on a click. */
    fun valueAt(x: Int): Int = valueForXPosition(x)

    /**
     * Swing's own listener only drags when the press landed on the handle, and
     * pages the value otherwise. [ParamSlider] installs the jump-and-follow
     * behaviour instead, so this one is left out entirely.
     */
    override fun createTrackListener(slider: JSlider): TrackListener? = null

    override fun getThumbSize(): Dimension = Dimension(THUMB, THUMB)

    override fun calculateTrackBuffer() {
        trackBuffer = THUMB / 2 + 1
    }

    override fun paintFocus(g: Graphics) = Unit

    override fun paintTrack(g: Graphics) {
        val g2 = (g.create() as Graphics2D).smooth()
        try {
            val centerY = trackRect.y + trackRect.height / 2.0
            val left = trackRect.x.toDouble()
            val right = (trackRect.x + trackRect.width).toDouble()
            val thumbX = thumbRect.x + thumbRect.width / 2.0

            g2.color = Theme.TRACK
            g2.fill(RoundRectangle2D.Double(left, centerY - LINE / 2, right - left, LINE, LINE, LINE))

            g2.color = Theme.ACCENT_DEEP
            g2.fill(RoundRectangle2D.Double(left, centerY - LINE / 2, thumbX - left, LINE, LINE, LINE))
        } finally {
            g2.dispose()
        }
    }

    override fun paintThumb(g: Graphics) {
        val g2 = (g.create() as Graphics2D).smooth()
        try {
            val centerX = thumbRect.x + thumbRect.width / 2.0
            val centerY = thumbRect.y + thumbRect.height / 2.0
            val radius = THUMB / 2.0

            g2.color = Theme.accent(40)
            g2.fill(Ellipse2D.Double(centerX - radius, centerY - radius, THUMB.toDouble(), THUMB.toDouble()))

            val inner = radius - 3
            g2.color = Theme.ACCENT
            g2.fill(Ellipse2D.Double(centerX - inner, centerY - inner, inner * 2, inner * 2))
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        const val THUMB = 14
        const val LINE = 4.0
    }
}

/** Flat combo box: no bevel, a drawn chevron instead of the Metal arrow button. */
private class FlatComboUI : BasicComboBoxUI() {

    override fun createArrowButton(): JButton = object : JButton() {
        override fun paintComponent(g: Graphics) {
            val g2 = (g.create() as Graphics2D).smooth()
            try {
                g2.color = Theme.TEXT_DIM
                g2.stroke = BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)

                val centerX = width / 2.0
                val centerY = height / 2.0
                g2.draw(Path2D.Double().apply {
                    moveTo(centerX - 4, centerY - 2)
                    lineTo(centerX, centerY + 2.5)
                    lineTo(centerX + 4, centerY - 2)
                })
            } finally {
                g2.dispose()
            }
        }
    }.apply {
        isFocusable = false
        isOpaque = false
        isContentAreaFilled = false
        isBorderPainted = false
        border = EmptyBorder(0, 0, 0, 0)
        preferredSize = Dimension(20, 20)
    }

    override fun paintCurrentValueBackground(g: Graphics, bounds: Rectangle, hasFocus: Boolean) {
        g.color = Theme.PANEL_HI
        g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height)
    }
}

/**
 * A pill switch: the bypass control for a module. Painted rather than a
 * [javax.swing.JCheckBox] so it reads as a synth switch, and deliberately not
 * focusable so clicking it never steals the keyboard from the instrument.
 */class ToggleSwitch(
    text: String,
    selected: Boolean,
    private val onChange: (Boolean) -> Unit
) : JPanel() {

    var isSelected: Boolean = selected
        private set(value) {
            field = value
            knob.repaint()
        }

    private val knob = object : JComponent() {
        init {
            preferredSize = Dimension(WIDTH, HEIGHT)
            maximumSize = Dimension(WIDTH, HEIGHT)
            minimumSize = Dimension(WIDTH, HEIGHT)
            isOpaque = false
        }

        override fun paintComponent(g: Graphics) {
            val g2 = (g.create() as Graphics2D).smooth()
            try {
                val pill = RoundRectangle2D.Double(
                    0.5, 0.5, WIDTH - 1.0, HEIGHT - 1.0,
                    HEIGHT.toDouble(), HEIGHT.toDouble()
                )

                g2.color = if (isSelected) Theme.ACCENT_DEEP else Theme.TRACK
                g2.fill(pill)
                g2.color = if (isSelected) Theme.ACCENT else Theme.BORDER_HI
                g2.draw(pill)

                val diameter = HEIGHT - 6.0
                val x = if (isSelected) WIDTH - diameter - 3.0 else 3.0
                g2.color = if (isSelected) Theme.ACCENT else Theme.TEXT_FAINT
                g2.fill(Ellipse2D.Double(x, 3.0, diameter, diameter))
            } finally {
                g2.dispose()
            }
        }
    }

    private val caption = JLabel(text).apply {
        font = Theme.LABEL_FONT
        foreground = Theme.TEXT_DIM
    }

    init {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

        add(caption)
        add(Box.createHorizontalStrut(8))
        add(knob)

        val click = object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) = toggle()
        }
        addMouseListener(click)
        knob.addMouseListener(click)
        caption.addMouseListener(click)

        paint(selected)
    }

    private fun toggle() {
        paint(!isSelected)
        onChange(isSelected)
    }

    private fun paint(selected: Boolean) {
        isSelected = selected
        caption.foreground = if (selected) Theme.TEXT else Theme.TEXT_DIM
    }

    private companion object {
        const val WIDTH = 32
        const val HEIGHT = 18
    }
}

/**
 * A painted tab strip over a [CardLayout]: the segmented page switch modern synths
 * use, instead of the notebook tabs [javax.swing.JTabbedPane] draws.
 */
class TabStrip(vararg pages: Pair<String, JComponent>) : JPanel(BorderLayout()) {

    private val deck = JPanel(CardLayout()).apply { isOpaque = false }
    private val tabs = ArrayList<Tab>()

    init {
        isOpaque = false

        val header = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            isOpaque = false
            border = EmptyBorder(0, 2, 8, 0)
        }

        pages.forEach { (title, page) ->
            deck.add(page, title)

            val tab = Tab(title) { show(title) }
            tabs += tab
            header.add(tab)
            header.add(Box.createHorizontalStrut(4))
        }

        header.add(Box.createHorizontalGlue())
        add(header, BorderLayout.NORTH)
        add(deck, BorderLayout.CENTER)

        pages.firstOrNull()?.let { show(it.first) }
    }

    /** Brings the named page to the front. */
    fun show(title: String) {
        (deck.layout as CardLayout).show(deck, title)
        tabs.forEach { it.setActive(it.title == title) }
    }

    /** A [JLabel] so the text metrics come for free; the background is painted. */
    private class Tab(val title: String, onClick: () -> Unit) : JLabel(title.uppercase()) {

        private var active = false

        init {
            font = Theme.TITLE_FONT
            foreground = Theme.TEXT_DIM
            isOpaque = false
            border = EmptyBorder(7, 14, 7, 14)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addMouseListener(object : MouseAdapter() {
                override fun mousePressed(event: MouseEvent) = onClick()
            })
        }

        fun setActive(value: Boolean) {
            active = value
            foreground = if (value) Theme.ACCENT else Theme.TEXT_DIM
            repaint()
        }

        override fun paintComponent(g: Graphics) {
            if (active) {
                val g2 = (g.create() as Graphics2D).smooth()
                try {
                    g2.color = Theme.accent(26)
                    g2.fill(RoundRectangle2D.Double(0.0, 0.0, width.toDouble(), height.toDouble(), 8.0, 8.0))
                } finally {
                    g2.dispose()
                }
            }
            super.paintComponent(g)
        }
    }
}

/** A hairline scrollbar: no track, no arrow buttons, just a thumb. */
class ThinScrollBarUI : BasicScrollBarUI() {

    override fun createDecreaseButton(orientation: Int): JButton = hiddenButton()

    override fun createIncreaseButton(orientation: Int): JButton = hiddenButton()

    override fun paintTrack(g: Graphics, c: JComponent, trackBounds: Rectangle) = Unit

    override fun paintThumb(g: Graphics, c: JComponent, thumbBounds: Rectangle) {
        if (thumbBounds.isEmpty || !scrollbar.isEnabled) return

        val g2 = (g.create() as Graphics2D).smooth()
        try {
            g2.color = if (isThumbRollover) Theme.BORDER_HI else Theme.TRACK
            g2.fill(
                RoundRectangle2D.Double(
                    thumbBounds.x + 2.0, thumbBounds.y + 2.0,
                    thumbBounds.width - 4.0, thumbBounds.height - 4.0,
                    5.0, 5.0
                )
            )
        } finally {
            g2.dispose()
        }
    }

    private fun hiddenButton(): JButton = JButton().apply {
        preferredSize = Dimension(0, 0)
        minimumSize = Dimension(0, 0)
        maximumSize = Dimension(0, 0)
        isFocusable = false
    }
}
