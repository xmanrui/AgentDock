package com.agentdock.ui

import com.agentdock.model.AgentSession
import com.agentdock.service.SessionContentPreview
import com.agentdock.service.SessionContentRole
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.geom.RoundRectangle2D
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JWindow
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities
import javax.swing.Timer

internal data class SessionPreviewAnchor(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int
)

internal class SessionPreviewPopup(
    private val anchorComponent: JComponent
) {
    private var popupWindow: JWindow? = null
    private var ownerWindow: Window? = null
    private var cardHovered = false
    private val hideTimer = Timer(HIDE_GRACE_MS) {
        if (!pointerInsidePopup()) {
            hideImmediately()
        }
    }.apply {
        isRepeats = false
    }
    private val pointerWatchTimer = Timer(POINTER_WATCH_INTERVAL_MS) {
        if (!cardHovered && !pointerInsidePopup()) {
            hideImmediately()
        }
    }.apply {
        isRepeats = true
    }
    private val ownerFocusListener = object : WindowAdapter() {
        override fun windowLostFocus(event: WindowEvent) {
            hideImmediately()
        }
    }

    fun show(
        session: AgentSession,
        providerName: String,
        preview: SessionContentPreview,
        anchor: SessionPreviewAnchor
    ) {
        check(SwingUtilities.isEventDispatchThread()) { "Session preview must be shown on the EDT" }
        if (!anchorComponent.isShowing) return

        hideImmediately()
        cardHovered = true
        val owner = SwingUtilities.getWindowAncestor(anchorComponent) ?: return
        val screenBounds = usableScreenBounds()
        val anchorLocation = anchorComponent.locationOnScreen
        val cardBounds = Rectangle(
            anchorLocation.x + anchor.left,
            anchorLocation.y + anchor.top,
            anchor.width,
            anchor.height
        )
        val popupSize = preferredPopupSize(cardBounds, screenBounds)
        val content = createContent(session, providerName, preview, popupSize)
        val window = JWindow(owner).apply {
            type = Window.Type.POPUP
            focusableWindowState = false
            runCatching { background = Color(0, 0, 0, 0) }
            contentPane = content
            size = popupSize
            location = popupLocation(cardBounds, popupSize, screenBounds)
        }

        popupWindow = window
        ownerWindow = owner
        owner.addWindowFocusListener(ownerFocusListener)
        window.isVisible = true
        pointerWatchTimer.start()
    }

    fun requestHide() {
        cardHovered = false
        if (popupWindow != null) {
            hideTimer.restart()
        }
    }

    fun hideImmediately() {
        hideTimer.stop()
        pointerWatchTimer.stop()
        cardHovered = false
        ownerWindow?.removeWindowFocusListener(ownerFocusListener)
        ownerWindow = null
        popupWindow?.dispose()
        popupWindow = null
    }

    fun dispose() {
        hideImmediately()
    }

    private fun createContent(
        session: AgentSession,
        providerName: String,
        preview: SessionContentPreview,
        popupSize: Dimension
    ): JComponent {
        val background = Color(0x20, 0x24, 0x1f)
        val line = Color(0x3d, 0x45, 0x3e)
        val root = RoundedPopupPanel(BorderLayout(), background, line, JBUI.scale(CORNER_RADIUS)).apply {
            border = JBUI.Borders.empty(1)
            preferredSize = popupSize
            accessibleContext.accessibleName = "Session conversation preview"
        }

        val title = JLabel(ellipsize(session.name, MAX_TITLE_LENGTH)).apply {
            foreground = Color(0xee, 0xf2, 0xec)
            font = font.deriveFont(Font.BOLD, font.size2D + 1f)
            toolTipText = session.name
        }
        val metadata = JLabel("$providerName  |  ${messageCountLabel(preview)}").apply {
            foreground = Color(0x88, 0x92, 0x87)
            font = font.deriveFont((font.size2D - 1f).coerceAtLeast(11f))
        }
        val header = JPanel(BorderLayout(0, JBUI.scale(3))).apply {
            isOpaque = false
            border = JBUI.Borders.empty(12, 14, 11, 14)
            add(title, BorderLayout.NORTH)
            add(metadata, BorderLayout.SOUTH)
        }
        root.add(header, BorderLayout.NORTH)

        val editor = JEditorPane("text/html", SessionPreviewHtml.render(providerName, preview)).apply {
            isEditable = false
            isOpaque = true
            this.background = Color(0x1b, 0x1f, 0x1b)
            border = BorderFactory.createEmptyBorder()
            putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
            accessibleContext.accessibleName = "Conversation messages"
            caretPosition = 0
        }
        val scrollPane = JScrollPane(editor).apply {
            border = BorderFactory.createMatteBorder(1, 0, 0, 0, Color(0x34, 0x3b, 0x35))
            viewport.background = editor.background
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBar.unitIncrement = JBUI.scale(18)
        }
        root.add(scrollPane, BorderLayout.CENTER)
        return root
    }

    private fun preferredPopupSize(cardBounds: Rectangle, screenBounds: Rectangle): Dimension {
        val gap = JBUI.scale(POPUP_GAP)
        val margin = JBUI.scale(SCREEN_MARGIN)
        val desiredWidth = JBUI.scale(POPUP_WIDTH)
        val minimumWidth = JBUI.scale(MIN_POPUP_WIDTH)
        val availableLeft = cardBounds.x - gap - (screenBounds.x + margin)
        val maximumScreenWidth = (screenBounds.width - margin * 2).coerceAtLeast(minimumWidth)
        val width = when {
            availableLeft >= minimumWidth -> minOf(desiredWidth, availableLeft)
            else -> minOf(desiredWidth, maximumScreenWidth)
        }
        val height = minOf(
            JBUI.scale(POPUP_HEIGHT),
            (screenBounds.height - margin * 2).coerceAtLeast(JBUI.scale(MIN_POPUP_HEIGHT))
        )
        return Dimension(width, height)
    }

    private fun popupLocation(cardBounds: Rectangle, popupSize: Dimension, screenBounds: Rectangle): Point {
        val gap = JBUI.scale(POPUP_GAP)
        val margin = JBUI.scale(SCREEN_MARGIN)
        val minX = screenBounds.x + margin
        val maxX = screenBounds.x + screenBounds.width - margin - popupSize.width
        val preferredX = cardBounds.x - gap - popupSize.width
        val x = preferredX.coerceIn(minX, maxOf(minX, maxX))

        val minY = screenBounds.y + margin
        val maxY = screenBounds.y + screenBounds.height - margin - popupSize.height
        val preferredY = cardBounds.y - JBUI.scale(12)
        val y = preferredY.coerceIn(minY, maxOf(minY, maxY))
        return Point(x, y)
    }

    private fun usableScreenBounds(): Rectangle {
        val graphicsConfiguration = anchorComponent.graphicsConfiguration
        val bounds = Rectangle(graphicsConfiguration?.bounds ?: anchorComponent.toolkit.screenSize.let {
            Rectangle(0, 0, it.width, it.height)
        })
        val insets = graphicsConfiguration?.let { Toolkit.getDefaultToolkit().getScreenInsets(it) } ?: Insets(0, 0, 0, 0)
        return Rectangle(
            bounds.x + insets.left,
            bounds.y + insets.top,
            bounds.width - insets.left - insets.right,
            bounds.height - insets.top - insets.bottom
        )
    }

    private fun pointerInsidePopup(): Boolean {
        val window = popupWindow ?: return false
        if (!window.isShowing) return false
        val pointer = runCatching { java.awt.MouseInfo.getPointerInfo()?.location }.getOrNull() ?: return false
        return window.bounds.contains(pointer)
    }

    private fun messageCountLabel(preview: SessionContentPreview): String {
        val total = preview.messages.size + preview.omittedMessageCount
        return if (total == 1) "1 message" else "$total messages"
    }

    private fun ellipsize(value: String, maxLength: Int): String {
        return if (value.length <= maxLength) value else value.take(maxLength - 3).trimEnd() + "..."
    }

    private class RoundedPopupPanel(
        layout: BorderLayout,
        private val fillColor: Color,
        private val lineColor: Color,
        private val cornerRadius: Int
    ) : JPanel(layout) {
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
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
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

    companion object {
        private const val POPUP_WIDTH = 440
        private const val POPUP_HEIGHT = 500
        private const val MIN_POPUP_WIDTH = 300
        private const val MIN_POPUP_HEIGHT = 280
        private const val POPUP_GAP = 8
        private const val SCREEN_MARGIN = 12
        private const val CORNER_RADIUS = 8
        private const val MAX_TITLE_LENGTH = 72
        private const val HIDE_GRACE_MS = 220
        private const val POINTER_WATCH_INTERVAL_MS = 120
    }
}

internal object SessionPreviewHtml {
    fun render(providerName: String, preview: SessionContentPreview): String {
        val assistantLabel = escapeHtml(providerName)
        val messages = buildString {
            if (preview.messages.isEmpty()) {
                append("<div class=\"empty\">No conversation content is available.</div>")
            } else {
                preview.messages.forEachIndexed { index, message ->
                    append("<div class=\"message\">")
                    append("<div class=\"role ")
                    append(if (message.role == SessionContentRole.User) "user\">You" else "assistant\">$assistantLabel")
                    append("</div><div class=\"copy\">")
                    append(escapeHtml(message.text).replace("\n", "<br>"))
                    append("</div></div>")
                    if (index == 0 && preview.omittedMessageCount > 0) {
                        append("<div class=\"omitted\">")
                        append(preview.omittedMessageCount)
                        append(if (preview.omittedMessageCount == 1) " earlier message omitted" else " earlier messages omitted")
                        append("</div>")
                    }
                }
            }
            preview.notice?.takeIf { it.isNotBlank() }?.let { notice ->
                append("<div class=\"notice\">")
                append(escapeHtml(notice))
                append("</div>")
            }
        }
        return """
            <html>
            <head>
              <style>
                body { margin: 0; padding: 0; color: #eef2ec; background: #1b1f1b; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; font-size: 12px; }
                .message { margin: 0; padding: 11px 14px 12px; border-bottom: 1px solid #29302a; }
                .role { margin: 0 0 5px; font-size: 10px; font-weight: bold; }
                .role.user { color: #73a7ff; }
                .role.assistant { color: #78d992; }
                .copy { color: #d8ded6; line-height: 1.45; }
                .omitted { padding: 7px 14px; color: #889287; background: #20241f; border-bottom: 1px solid #29302a; font-size: 10px; text-align: center; }
                .notice, .empty { padding: 12px 14px; color: #889287; line-height: 1.4; }
                .notice { border-top: 1px solid #29302a; }
              </style>
            </head>
            <body>$messages</body>
            </html>
        """.trimIndent()
    }

    private fun escapeHtml(value: String): String {
        return buildString(value.length) {
            value.forEach { character ->
                append(
                    when (character) {
                        '&' -> "&amp;"
                        '<' -> "&lt;"
                        '>' -> "&gt;"
                        '"' -> "&quot;"
                        '\'' -> "&#39;"
                        else -> character
                    }
                )
            }
        }
    }
}
