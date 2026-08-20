package ui

import java.awt.Color
import java.awt.Component
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.RenderingHints
import java.awt.font.TextAttribute
import java.awt.geom.RoundRectangle2D
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * The look: flat, dark, rounded, one accent colour — the vocabulary modern soft
 * synths (Serum, Vital) use, rather than the raised grey bevels Swing ships with.
 *
 * Everything is painted by hand from these tokens, so there is a single place to
 * retune the palette.
 */
object Theme {

    val BACKGROUND = Color(0x0E1014)
    val PANEL = Color(0x161A21)
    val PANEL_HI = Color(0x1C212B)
    val SCOPE = Color(0x08090C)
    val TRACK = Color(0x232936)
    val BORDER = Color(0x272E3B)
    val BORDER_HI = Color(0x39414F)

    val TEXT = Color(0xE7EAF0)
    val TEXT_DIM = Color(0x828D9F)
    val TEXT_FAINT = Color(0x5A6474)

    val ACCENT = Color(0x35E0BE)
    val ACCENT_DEEP = Color(0x12A48A)
    val DANGER = Color(0xFF5E6E)

    /** Semi-transparent accent, for fills and glows. */
    fun accent(alpha: Int): Color = Color(ACCENT.red, ACCENT.green, ACCENT.blue, alpha)

    fun danger(alpha: Int): Color = Color(DANGER.red, DANGER.green, DANGER.blue, alpha)

    private val uiFamily = firstAvailable("Segoe UI Variable Text", "Segoe UI", "Inter", Font.SANS_SERIF)
    private val monoFamily = firstAvailable("Cascadia Mono", "Consolas", "JetBrains Mono", Font.MONOSPACED)

    val LABEL_FONT: Font = Font(uiFamily, Font.PLAIN, 12)

    /** Tracked out a little, the way module headings are set on a hardware panel. */
    val TITLE_FONT: Font = Font(uiFamily, Font.BOLD, 11)
        .deriveFont(mapOf(TextAttribute.TRACKING to 0.1f))

    val VALUE_FONT: Font = Font(monoFamily, Font.PLAIN, 12)
    val VALUE_FONT_BOLD: Font = Font(monoFamily, Font.BOLD, 12)
    val READOUT_FONT: Font = Font(monoFamily, Font.BOLD, 19)

    const val RADIUS = 10

    /**
     * The first font family actually installed. Naming "Segoe UI" outright would
     * fall back to something unpredictable on a machine that does not have it.
     */
    private fun firstAvailable(vararg families: String): String {
        val installed = runCatching {
            GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toHashSet()
        }.getOrDefault(hashSetOf())

        return families.firstOrNull { it in installed } ?: families.last()
    }
}

/** Turns on the rendering hints every hand-painted control in here expects. */
fun Graphics2D.smooth(): Graphics2D = apply {
    setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
    setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
}

/** A panel with rounded corners and a hairline border, painted rather than beveled. */
open class RoundedPanel(
    private val fill: Color = Theme.PANEL,
    private val line: Color? = Theme.BORDER,
    private val radius: Int = Theme.RADIUS
) : JPanel() {

    init {
        isOpaque = false
        background = fill
    }

    override fun paintComponent(g: Graphics) {
        val g2 = (g.create() as Graphics2D).smooth()
        try {
            val shape = RoundRectangle2D.Double(
                0.5, 0.5,
                width - 1.0, height - 1.0,
                radius.toDouble(), radius.toDouble()
            )

            g2.color = fill
            g2.fill(shape)

            if (line != null) {
                g2.color = line
                g2.draw(shape)
            }
        } finally {
            g2.dispose()
        }
        super.paintComponent(g)
    }
}

/** A small, dim heading — the label style used above a group of controls. */
fun sectionTitle(text: String): JLabel = JLabel(text.uppercase()).apply {
    foreground = Theme.TEXT_DIM
    font = Theme.TITLE_FONT
    alignmentX = Component.LEFT_ALIGNMENT
}
