package com.agentdock.ui

import com.agentdock.model.AgentSession
import com.agentdock.model.AgentSessionStatus
import com.agentdock.model.CLIProvider
import com.agentdock.util.TimeFormatter
import com.agentdock.util.SessionTextSanitizer
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.AlphaComposite
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridLayout
import java.awt.RenderingHints
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.Timer

class SessionCardPanel(
    private val session: AgentSession,
    provider: CLIProvider?,
    onOpen: (AgentSession) -> Unit,
    onPin: (AgentSession) -> Unit
) : JPanel(BorderLayout(0, 8)) {
    init {
        border = javax.swing.BorderFactory.createCompoundBorder(
            JBUI.Borders.customLine(LINE_SOFT, 1),
            JBUI.Borders.empty(10)
        )
        background = CARD_BACKGROUND

        val providerName = provider?.displayName ?: session.providerId
        val providerBadge = pill(
            text = providerBadgeText(providerName),
            foreground = providerForeground(session.providerId),
            background = providerBackground(session.providerId)
        )
        val title = JLabel(truncate(SessionTextSanitizer.title(session.name), 48)).apply {
            font = font.deriveFont(Font.BOLD)
            toolTipText = session.name
        }
        val displayStatus = displayStatus(session)
        val top = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
                isOpaque = false
                add(providerBadge)
                add(title)
            }, BorderLayout.CENTER)
            add(terminalIndicator(displayStatus), BorderLayout.EAST)
        }

        val meta = JLabel(
            listOfNotNull(
                providerName,
                TimeFormatter.relative(session.updatedAt)
            ).joinToString(" · ")
        ).apply {
            foreground = TEXT_DIM
        }
        val summaryText = SessionTextSanitizer.summary(session.summary)
            .ifBlank { SessionTextSanitizer.summary(session.name) }
            .ifBlank { "No summary captured yet" }
        val summary = JLabel(truncate(summaryText, 120)).apply {
            foreground = TEXT_SOFT
            toolTipText = summaryText.takeIf { it.isNotBlank() }
        }
        val text = JPanel(GridLayout(0, 1, 0, 4)).apply {
            isOpaque = false
            add(top)
            add(meta)
            add(summary)
        }

        val actions = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
            isOpaque = false
            add(actionButton("Open") { onOpen(session) })
            add(actionButton(if (session.pinned) "Unpin" else "Pin") { onPin(session) })
        }

        add(text, BorderLayout.CENTER)
        add(actions, BorderLayout.SOUTH)
        maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
    }

    private fun displayStatus(session: AgentSession): AgentSessionStatus {
        return if (session.archived) AgentSessionStatus.Archived else session.status
    }

    private fun terminalIndicator(status: AgentSessionStatus): JComponent {
        val active = status == AgentSessionStatus.Active
        return object : JComponent() {
            private val pulseTimer = Timer(120) { repaint() }

            init {
                preferredSize = Dimension(18, 18)
                minimumSize = preferredSize
                maximumSize = preferredSize
                toolTipText = if (active) "Terminal open" else "Terminal closed"
                pulseTimer.isRepeats = true
            }

            override fun addNotify() {
                super.addNotify()
                if (active) pulseTimer.start()
            }

            override fun removeNotify() {
                pulseTimer.stop()
                super.removeNotify()
            }

            override fun paintComponent(graphics: Graphics) {
                super.paintComponent(graphics)
                val g = graphics.create() as Graphics2D
                try {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    val size = JBUI.scale(8)
                    val x = (width - size) / 2
                    val y = (height - size) / 2
                    if (active) {
                        val phase = (System.currentTimeMillis() % TERMINAL_PULSE_MS).toFloat() / TERMINAL_PULSE_MS
                        val pulse = if (phase < 0.5f) phase * 2f else (1f - phase) * 2f
                        val haloSize = JBUI.scale(12) + (JBUI.scale(4) * pulse).toInt()
                        val haloX = (width - haloSize) / 2
                        val haloY = (height - haloSize) / 2
                        val originalComposite = g.composite
                        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.22f - 0.1f * pulse)
                        g.color = ACTIVE_DOT
                        g.fillOval(haloX, haloY, haloSize, haloSize)
                        g.composite = originalComposite
                    }
                    g.color = if (active) ACTIVE_DOT else INACTIVE_DOT
                    g.fillOval(x, y, size, size)
                } finally {
                    g.dispose()
                }
            }
        }
    }

    private fun providerBadgeText(providerName: String): String {
        return when (session.providerId) {
            CLIProvider.CODEX_ID -> "Codex"
            CLIProvider.CLAUDE_CODE_ID -> "Claude"
            CLIProvider.GEMINI_ID -> "Gemini"
            else -> providerName
        }
    }

    private fun providerForeground(providerId: String): JBColor {
        return when (providerId) {
            CLIProvider.CODEX_ID -> JBColor(0x0B8043, 0x8FD19E)
            CLIProvider.CLAUDE_CODE_ID -> JBColor(0xA14200, 0xDFA878)
            CLIProvider.GEMINI_ID -> JBColor(0x185ABC, 0x9BBEFF)
            else -> JBColor(0x3C4043, 0xC9CCD1)
        }
    }

    private fun providerBackground(providerId: String): JBColor {
        return when (providerId) {
            CLIProvider.CODEX_ID -> JBColor(0xE6F4EA, 0x24382B)
            CLIProvider.CLAUDE_CODE_ID -> JBColor(0xFCE8D5, 0x4A3324)
            CLIProvider.GEMINI_ID -> JBColor(0xE8F0FE, 0x26364F)
            else -> JBColor(0xF1F3F4, 0x343638)
        }
    }

    private fun pill(text: String, foreground: JBColor, background: JBColor): JLabel {
        return JLabel(text).apply {
            isOpaque = true
            this.foreground = foreground
            this.background = background
            border = JBUI.Borders.empty(2, 6)
            font = font.deriveFont(Font.PLAIN, (font.size2D - 1f).coerceAtLeast(10f))
        }
    }

    private fun actionButton(text: String, action: () -> Unit): JButton {
        return JButton(text).apply {
            isFocusable = false
            margin = JBUI.insets(3, 9)
            border = JBUI.Borders.customLine(LINE_SOFT, 1)
            foreground = TEXT_SOFT
            background = ACTION_BACKGROUND
            font = font.deriveFont(Font.PLAIN, (font.size2D - 1f).coerceAtLeast(11f))
            addActionListener { action() }
        }
    }

    private fun truncate(text: String, maxLength: Int): String {
        return if (text.length <= maxLength) text else text.take(maxLength - 3) + "..."
    }

    companion object {
        private val CARD_BACKGROUND = JBColor(0xFFFFFF, 0x252A24)
        private val ACTION_BACKGROUND = JBColor(0xF7F8F7, 0x272C26)
        private val LINE_SOFT = JBColor(0xE5E7EB, 0x29302A)
        private val TEXT_SOFT = JBColor(0x3C4043, 0xC0C8BF)
        private val TEXT_DIM = JBColor(0x5F6368, 0x889287)
        private val ACTIVE_DOT = JBColor(0x188038, 0x68D982)
        private val INACTIVE_DOT = JBColor(0x9AA39A, 0x69736B)
        private const val TERMINAL_PULSE_MS = 1350
    }
}
