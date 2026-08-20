package ui

import engine.Patch
import engine.audio.LiveEngine
import engine.enums.Waveform
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.KeyboardFocusManager
import java.awt.KeyEventDispatcher
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.WindowConstants
import javax.swing.border.EmptyBorder
import javax.swing.text.JTextComponent

/**
 * The window, assembled around the two use cases:
 *
 *  - **play**: the computer keyboard is the instrument, Ableton-style, with `Q`
 *    on C-4. Notes go straight into [LiveEngine], which plays them through the
 *    shared fx chain of a [Patch].
 *  - **render**: the same patch is frozen and written to a file by [RenderPanel].
 *
 * The fx rack is always on screen, so every parameter can be tweaked while a
 * chord is held and the oscilloscope shows the result.
 */
object SynthUi {

    /**
     * Opens the window, or explains on stderr why it cannot: a missing display
     * or a busy audio device should not come back as a stack trace.
     */
    fun launch() {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("kwg: no display available, so the synth UI cannot open")
            return
        }

        SwingUtilities.invokeLater {
            try {
                createAndShow()
            } catch (failure: Exception) {
                val reason = failure.message ?: failure::class.simpleName ?: "unknown error"
                System.err.println("kwg: could not start the live synth: $reason")
                JOptionPane.showMessageDialog(
                    null,
                    "Could not start the live synth:\n$reason",
                    "kwg",
                    JOptionPane.ERROR_MESSAGE
                )
            }
        }
    }

    private fun createAndShow() {
        val patch = Patch(SAMPLE_RATE.toUInt())
        val engine = LiveEngine(sampleRate = SAMPLE_RATE, chain = patch.liveChain)

        val scope = ScopePanel(engine) { patch.clipper.ceilingDb }
        val rack = FxRackPanel(patch, engine, scope)
        val keyboard = Keyboard(engine)

        val frame = JFrame("kwg · synth")
        frame.defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
        frame.contentPane.background = Theme.BACKGROUND
        frame.layout = BorderLayout()

        frame.add(transportBar(engine, keyboard), BorderLayout.NORTH)
        frame.add(scopeArea(scope), BorderLayout.CENTER)
        frame.add(rackScroller(rack), BorderLayout.EAST)
        frame.add(useCasePages(patch, engine, keyboard), BorderLayout.SOUTH)

        val dispatcher = keyboard.dispatcher(frame)
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dispatcher)

        frame.addWindowListener(object : WindowAdapter() {
            /**
             * Alt-tabbing away means the releases land in the other window, so let
             * go of everything here rather than leaving a note sounding.
             */
            override fun windowDeactivated(event: WindowEvent) = keyboard.releaseAll()

            override fun windowIconified(event: WindowEvent) = keyboard.releaseAll()

            override fun windowClosing(event: WindowEvent) {
                KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(dispatcher)
                engine.close()
                frame.dispose()
            }
        })

        engine.start()

        frame.size = Dimension(1120, 780)
        frame.minimumSize = Dimension(880, 620)
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
    }

    private fun scopeArea(scope: ScopePanel): JPanel = JPanel(BorderLayout()).apply {
        background = Theme.BACKGROUND
        border = EmptyBorder(0, 10, 8, 10)
        add(scope, BorderLayout.CENTER)
    }

    private fun rackScroller(rack: FxRackPanel): JScrollPane = JScrollPane(rack).apply {
        border = EmptyBorder(0, 0, 0, 0)
        background = Theme.BACKGROUND
        viewport.background = Theme.BACKGROUND
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        verticalScrollBar.unitIncrement = 16
        verticalScrollBar.isFocusable = false
        verticalScrollBar.setUI(ThinScrollBarUI())
        verticalScrollBar.preferredSize = Dimension(9, 0)
        preferredSize = Dimension(RACK_WIDTH, 0)
    }

    /** Waveform, octave and the live note read-out. */
    private fun transportBar(engine: LiveEngine, keyboard: Keyboard): JPanel {
        val waveforms = Waveform.entries
            .map { Choice(it.name.lowercase().replace('_', ' '), it) }
            .toTypedArray()

        val left = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            isOpaque = false

            add(dimLabel("wave"))
            add(Box.createHorizontalStrut(8))
            add(fxCombo(waveforms, waveforms.first { it.value == engine.waveform }) {
                engine.waveform = it.value
            }.apply {
                maximumSize = Dimension(132, 26)
                preferredSize = Dimension(132, 26)
            })

            add(Box.createHorizontalStrut(20))
            add(dimLabel("octave"))
            add(Box.createHorizontalStrut(8))
            add(fxButton("−") { keyboard.shiftOctave(-1) })
            add(keyboard.octaveLabel)
            add(fxButton("+") { keyboard.shiftOctave(1) })
        }

        val right = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            isOpaque = false
            add(keyboard.heldLabel)
            add(Box.createHorizontalStrut(14))
            add(keyboard.noteLabel)
        }

        val bar = RoundedPanel(Theme.PANEL).apply {
            layout = BorderLayout()
            border = EmptyBorder(9, 12, 9, 14)
            add(left, BorderLayout.WEST)
            add(right, BorderLayout.EAST)
        }

        return JPanel(BorderLayout()).apply {
            background = Theme.BACKGROUND
            border = EmptyBorder(10, 10, 8, 10)
            add(bar, BorderLayout.CENTER)
        }
    }

    private fun useCasePages(patch: Patch, engine: LiveEngine, keyboard: Keyboard): JPanel =
        JPanel(BorderLayout()).apply {
            background = Theme.BACKGROUND
            border = EmptyBorder(0, 10, 10, 10)
            preferredSize = Dimension(0, 250)

            add(
                TabStrip(
                    "play" to playPanel(keyboard),
                    "render to file" to RenderPanel(patch, engine)
                ),
                BorderLayout.CENTER
            )
        }

    /** The keyboard legend plus what is currently sounding. */
    private fun playPanel(keyboard: Keyboard): JPanel = RoundedPanel(Theme.PANEL).apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = EmptyBorder(12, 14, 12, 14)

        add(keyLegend("black", "  S D   G H J          2 3   5 6 7   9 0"))
        add(gap(2))
        add(keyLegend("white", " Z X C V B N M       Q W E R T Y U I O P"))
        add(gap(2))
        add(keyLegend("     ", " C-3         B-3     C-4             E-5"))
        add(gap(14))
        add(
            dimLabel("−  /  =   shift octave          ESC   all notes off", Theme.VALUE_FONT)
                .apply { foreground = Theme.TEXT_FAINT }
        )
        add(gap(12))
        add(fxButton("all notes off", accent = true) { keyboard.releaseAll() })
        add(Box.createVerticalGlue())
    }

    /** One line of the legend: a dim caption, then the keys in accent-tinted mono. */
    private fun keyLegend(caption: String, keys: String): JPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
        maximumSize = Dimension(Int.MAX_VALUE, 18)

        add(dimLabel(caption, Theme.VALUE_FONT).apply { foreground = Theme.TEXT_FAINT })
        add(dimLabel(keys, Theme.VALUE_FONT_BOLD).apply { foreground = Theme.TEXT })
        add(Box.createHorizontalGlue())
    }

    /**
     * The keyboard side of the instrument: the read-outs, the refresh timer, and
     * the global key dispatcher. The note bookkeeping itself lives in
     * [KeyboardController] so it can be tested without a window.
     *
     * A global [KeyEventDispatcher] is used rather than a `KeyListener`, so the
     * keys play no matter which control was clicked last — while text fields
     * (the render file name) still get their characters.
     */
    private class Keyboard(private val engine: LiveEngine) {

        private val controller = KeyboardController(engine)

        val noteLabel: JLabel = JLabel().apply {
            font = Theme.READOUT_FONT
            foreground = Theme.ACCENT
        }

        val octaveLabel: JLabel = dimLabel("", Theme.VALUE_FONT_BOLD).apply {
            foreground = Theme.TEXT
            border = EmptyBorder(0, 10, 0, 10)
        }

        val heldLabel: JLabel = dimLabel("", Theme.VALUE_FONT)

        init {
            refresh()
            Timer(READOUT_MS) { refresh() }.apply { isRepeats = true }.start()
        }

        fun dispatcher(frame: JFrame): KeyEventDispatcher = KeyEventDispatcher { event ->
            // a throw here would be swallowed by the focus manager and could leave
            // the key half-handled, so nothing escapes
            runCatching { handle(frame, event) }.getOrElse { false }
        }

        fun shiftOctave(delta: Int) {
            controller.shiftOctave(delta)
            refresh()
        }

        fun releaseAll() {
            controller.releaseAll()
            refresh()
        }

        fun refresh() {
            // read what the engine is actually sounding, not what we think we sent:
            // a note that ever gets stuck shows up here
            val held = engine.heldNotes()

            noteLabel.text = when {
                held.isNotEmpty() -> held.joinToString("  ") { LiveEngine.midiToName(it) }
                else -> engine.lastNote()?.let { "(${LiveEngine.midiToName(it)})" } ?: "—"
            }

            octaveLabel.text = controller.octaveLabel()
            heldLabel.text = "holding ${held.size} " + if (held.size == 1) "note" else "notes"
        }

        /**
         * The keyboard policy, and the fix for the stuck note: a press is only ours
         * when the window is active and no text field has the focus, but **a release
         * is always passed on**. Gating both directions on the same condition is
         * what stranded notes — move the focus to the file name field between press
         * and release and the note-off was dropped, so the tone never stopped.
         *
         * A suppressed release is not consumed either, so the text field still sees
         * its own key event.
         */
        private fun handle(frame: JFrame, event: KeyEvent): Boolean {
            val pressed = when (event.id) {
                KeyEvent.KEY_PRESSED -> true
                KeyEvent.KEY_RELEASED -> false
                else -> return false
            }

            val focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
            val suppressed = !frame.isActive || focused is JTextComponent

            if (pressed && !suppressed && shortcut(event.keyCode)) return true

            val handled = controller.handle(event.keyCode, pressed, suppressed, event.extendedKeyCode)
            if (handled) refresh()
            return handled && !suppressed
        }

        /** Transport keys: panic and octave shifts. */
        private fun shortcut(code: Int): Boolean {
            when (code) {
                KeyEvent.VK_ESCAPE -> releaseAll()
                KeyEvent.VK_MINUS -> shiftOctave(-1)
                KeyEvent.VK_EQUALS -> shiftOctave(1)
                else -> return false
            }
            return true
        }

        private companion object {
            const val READOUT_MS = 80
        }
    }

    private const val SAMPLE_RATE = 44100
    private const val RACK_WIDTH = 372
}
