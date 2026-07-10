package com.agentdock.ui

import com.agentdock.model.CLIProvider
import com.agentdock.model.ProviderUsageSnapshot
import com.agentdock.model.ProviderUsageWindow
import com.intellij.openapi.util.IconLoader
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.geom.RoundRectangle2D
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.JWindow
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

internal data class ProviderUsageAnchor(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
    val usePointer: Boolean
)

internal class ProviderUsagePopup(
    private val anchorComponent: JComponent
) {
    private var popupWindow: JWindow? = null
    private var ownerWindow: Window? = null
    private val ownerFocusListener = object : WindowAdapter() {
        override fun windowLostFocus(event: WindowEvent) {
            hide()
        }
    }

    fun showLoading(provider: CLIProvider, anchor: ProviderUsageAnchor) {
        showContent(createMessageContent(provider, "Loading usage limits..."), anchor)
    }

    fun showUsage(usage: ProviderUsageSnapshot, anchor: ProviderUsageAnchor) {
        val content = if (usage.status == ProviderUsageSnapshot.STATUS_AVAILABLE) {
            createAvailableContent(usage)
        } else {
            val provider = CLIProvider.defaultProviders().firstOrNull { it.id == usage.providerId }
                ?: CLIProvider(
                    id = usage.providerId,
                    displayName = usage.providerName,
                    enabled = true,
                    executable = "",
                    detectCommand = "",
                    startCommandTemplate = "",
                    resumeCommandTemplate = ""
                )
            createMessageContent(provider, usage.message ?: "Usage limits are unavailable.")
        }
        showContent(content, anchor)
    }

    fun hide() {
        ownerWindow?.removeWindowFocusListener(ownerFocusListener)
        ownerWindow = null
        popupWindow?.dispose()
        popupWindow = null
    }

    fun dispose() {
        hide()
    }

    private fun showContent(content: JComponent, anchor: ProviderUsageAnchor) {
        check(SwingUtilities.isEventDispatchThread()) { "Provider usage must be shown on the EDT" }
        if (!anchorComponent.isShowing) return

        hide()
        val owner = SwingUtilities.getWindowAncestor(anchorComponent) ?: return
        val anchorBounds = screenAnchor(anchor)
        val window = JWindow(owner).apply {
            type = Window.Type.POPUP
            focusableWindowState = false
            runCatching { background = Color(0, 0, 0, 0) }
            contentPane = content
            pack()
            location = popupLocation(anchorBounds, size, usableScreenBounds())
        }

        popupWindow = window
        ownerWindow = owner
        owner.addWindowFocusListener(ownerFocusListener)
        window.isVisible = true
    }

    private fun screenAnchor(anchor: ProviderUsageAnchor): Rectangle {
        if (anchor.usePointer) {
            val pointer = runCatching { MouseInfo.getPointerInfo()?.location }.getOrNull()
            if (pointer != null) {
                return ProviderUsagePopupPosition.pointerAnchor(pointer, anchor.width, anchor.height)
            }
        }
        val anchorLocation = anchorComponent.locationOnScreen
        return Rectangle(
            anchorLocation.x + anchor.left,
            anchorLocation.y + anchor.top,
            anchor.width,
            anchor.height
        )
    }

    private fun createAvailableContent(usage: ProviderUsageSnapshot): JComponent {
        val root = popupRoot()
        root.add(createHeader(usage.providerId, usage.resetCount))
        usage.fiveHour?.let { root.add(createUsageRow("5-hour limit", it)) }
        usage.weekly?.let { root.add(createUsageRow("Weekly limit", it)) }
        if (usage.fiveHour == null && usage.weekly == null) {
            root.add(createMessageLabel("No plan limits were reported."))
        }
        return root.withPopupWidth()
    }

    private fun createMessageContent(provider: CLIProvider, message: String): JComponent {
        return popupRoot().apply {
            add(createHeader(provider.id, null))
            add(createMessageLabel(message))
        }.withPopupWidth()
    }

    private fun popupRoot(): JPanel {
        return RoundedPopupPanel(
            fillColor = BACKGROUND,
            lineColor = LINE,
            cornerRadius = JBUI.scale(CORNER_RADIUS)
        ).apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(1)
            accessibleContext.accessibleName = "Provider usage limits"
        }
    }

    private fun JPanel.withPopupWidth(): JPanel {
        val naturalSize = preferredSize
        preferredSize = Dimension(JBUI.scale(POPUP_WIDTH), naturalSize.height)
        return this
    }

    private fun createHeader(providerId: String, resetCount: Long?): JComponent {
        val titleLabel = JLabel("Usage", ProviderIcons.forProvider(providerId), SwingConstants.LEADING).apply {
            foreground = TEXT
            font = font.deriveFont(Font.BOLD, font.size2D)
        }
        return JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
            isOpaque = false
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, LINE_SOFT),
                JBUI.Borders.empty(6, 9)
            )
            add(titleLabel, BorderLayout.CENTER)
            resetCount?.let { count ->
                add(
                    JLabel("$count resets").apply {
                        foreground = GREEN
                        background = GREEN_SOFT
                        isOpaque = true
                        font = font.deriveFont(Font.BOLD, (font.size2D - 1f).coerceAtLeast(10f))
                        border = JBUI.Borders.empty(2, 7)
                    },
                    BorderLayout.EAST
                )
            }
        }
    }

    private fun createUsageRow(label: String, usage: ProviderUsageWindow): JComponent {
        val remaining = (100 - usage.usedPercent).coerceIn(0, 100)
        val top = JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
            isOpaque = false
            add(JLabel(label).styled(TEXT_SOFT, Font.BOLD), BorderLayout.WEST)
            usage.resetsAtEpochSeconds?.let { epoch ->
                add(JLabel(formatReset(epoch)).styled(TEXT_DIM, Font.PLAIN), BorderLayout.CENTER)
            }
            add(JLabel("$remaining% left").styled(TEXT, Font.BOLD), BorderLayout.EAST)
        }
        val progress = JProgressBar(0, 100).apply {
            value = usage.usedPercent.coerceIn(0, 100)
            isOpaque = true
            isBorderPainted = false
            foreground = when {
                value >= 90 -> RED
                value >= 75 -> YELLOW
                else -> GREEN
            }
            background = METER_BACKGROUND
            preferredSize = Dimension(JBUI.scale(POPUP_WIDTH - 20), JBUI.scale(3))
            maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(3))
        }
        return JPanel(BorderLayout(0, JBUI.scale(4))).apply {
            isOpaque = false
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, LINE_SOFT),
                JBUI.Borders.empty(5, 9, 5, 9)
            )
            add(top, BorderLayout.NORTH)
            add(progress, BorderLayout.SOUTH)
        }
    }

    private fun createMessageLabel(message: String): JLabel {
        return JLabel(message).apply {
            foreground = TEXT_DIM
            border = JBUI.Borders.empty(7, 9, 8, 9)
        }
    }

    private fun JLabel.styled(color: Color, style: Int): JLabel {
        foreground = color
        font = font.deriveFont(style, (font.size2D - 1f).coerceAtLeast(10f))
        return this
    }

    private fun popupLocation(anchor: Rectangle, popupSize: Dimension, screen: Rectangle): Point {
        val componentLocation = anchorComponent.locationOnScreen
        val componentBounds = Rectangle(
            componentLocation.x,
            componentLocation.y,
            anchorComponent.width,
            anchorComponent.height
        )
        return ProviderUsagePopupPosition.above(
            anchor = anchor,
            popupSize = popupSize,
            screen = screen,
            componentBounds = componentBounds,
            gap = JBUI.scale(POPUP_GAP),
            margin = JBUI.scale(SCREEN_MARGIN)
        )
    }

    private fun usableScreenBounds(): Rectangle {
        val configuration = anchorComponent.graphicsConfiguration
        val bounds = Rectangle(configuration?.bounds ?: anchorComponent.toolkit.screenSize.let {
            Rectangle(0, 0, it.width, it.height)
        })
        val insets = configuration?.let { Toolkit.getDefaultToolkit().getScreenInsets(it) } ?: Insets(0, 0, 0, 0)
        return Rectangle(
            bounds.x + insets.left,
            bounds.y + insets.top,
            bounds.width - insets.left - insets.right,
            bounds.height - insets.top - insets.bottom
        )
    }

    private fun formatReset(epochSeconds: Long): String {
        val zone = ZoneId.systemDefault()
        val reset = Instant.ofEpochSecond(epochSeconds).atZone(zone)
        val now = Instant.now().atZone(zone)
        val pattern = if (reset.toLocalDate() == now.toLocalDate()) "h:mm a" else "MMM d, h:mm a"
        return "Resets " + DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH).format(reset)
    }

    private class RoundedPopupPanel(
        private val fillColor: Color,
        private val lineColor: Color,
        private val cornerRadius: Int
    ) : JPanel() {
        init {
            isOpaque = false
        }

        override fun paintComponent(graphics: Graphics) {
            val copy = graphics.create() as Graphics2D
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            copy.color = fillColor
            copy.fill(surfaceShape())
            copy.color = lineColor
            copy.draw(surfaceShape())
            copy.dispose()
        }

        override fun paintChildren(graphics: Graphics) {
            val copy = graphics.create() as Graphics2D
            copy.clip(surfaceShape())
            super.paintChildren(copy)
            copy.dispose()
        }

        private fun surfaceShape(): RoundRectangle2D.Float {
            val diameter = cornerRadius * 2f
            return RoundRectangle2D.Float(
                0.5f,
                0.5f,
                (width - 1).coerceAtLeast(0).toFloat(),
                (height - 1).coerceAtLeast(0).toFloat(),
                diameter,
                diameter
            )
        }
    }

    private object ProviderIcons {
        private val codex = IconLoader.getIcon("/icons/codex.svg", ProviderIcons::class.java)
        private val claude = IconLoader.getIcon("/icons/claude.svg", ProviderIcons::class.java)

        fun forProvider(providerId: String): Icon? {
            return when (providerId) {
                CLIProvider.CODEX_ID -> codex
                CLIProvider.CLAUDE_CODE_ID -> claude
                else -> null
            }
        }
    }

    companion object {
        private val BACKGROUND = Color(0x17, 0x1a, 0x17)
        private val LINE = Color(0x3d, 0x45, 0x3e)
        private val LINE_SOFT = Color(0x31, 0x38, 0x32)
        private val TEXT = Color(0xee, 0xf2, 0xec)
        private val TEXT_SOFT = Color(0xc3, 0xcb, 0xc0)
        private val TEXT_DIM = Color(0x88, 0x92, 0x87)
        private val GREEN = Color(0x68, 0xd9, 0x82)
        private val GREEN_SOFT = Color(0x24, 0x47, 0x2c)
        private val YELLOW = Color(0xe0, 0xb8, 0x5b)
        private val RED = Color(0xe0, 0x6b, 0x67)
        private val METER_BACKGROUND = Color(0x35, 0x3b, 0x36)
        private const val POPUP_WIDTH = 286
        private const val POPUP_GAP = 8
        private const val SCREEN_MARGIN = 8
        private const val CORNER_RADIUS = 8
    }
}

internal object ProviderUsagePopupPosition {
    fun pointerAnchor(pointer: Point, width: Int, height: Int): Rectangle {
        return Rectangle(
            pointer.x - width / 2,
            pointer.y - height / 2,
            width,
            height
        )
    }

    fun above(
        anchor: Rectangle,
        popupSize: Dimension,
        screen: Rectangle,
        componentBounds: Rectangle,
        gap: Int,
        margin: Int
    ): Point {
        val minX = maxOf(screen.x + margin, componentBounds.x + margin)
        val maxX = minOf(
            screen.x + screen.width - margin - popupSize.width,
            componentBounds.x + componentBounds.width - margin - popupSize.width
        )
        val preferredX = anchor.x + anchor.width / 2 - popupSize.width / 2
        val x = preferredX.coerceIn(minX, maxOf(minX, maxX))
        val minY = screen.y + margin
        val preferredY = anchor.y - popupSize.height - gap
        return Point(x, maxOf(minY, preferredY))
    }
}
