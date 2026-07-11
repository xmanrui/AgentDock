package com.agentdock.ui

import com.agentdock.model.AgentSession
import com.agentdock.model.CLIProvider
import com.agentdock.service.SessionContentPreview
import com.agentdock.service.SessionContentRole
import com.intellij.openapi.util.IconLoader
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
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
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
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
            contentPane = content
            size = popupSize
            RoundedPopupWindowSupport.configure(this, JBUI.scale(CORNER_RADIUS))
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
            accessibleContext?.accessibleName = "Session conversation preview"
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

        val conversation = SessionPreviewConversationPanel(
            providerName = providerName,
            providerIcon = SessionProviderIcons.forProvider(session.providerId),
            preview = preview,
            contentWidth = popupSize.width - JBUI.scale(28)
        ).apply {
            accessibleContext?.accessibleName = "Conversation messages"
        }
        val scrollPane = JScrollPane(conversation).apply {
            border = BorderFactory.createMatteBorder(1, 0, 0, 0, Color(0x34, 0x3b, 0x35))
            viewport.background = conversation.background
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

    private object SessionProviderIcons {
        private val codex = IconLoader.getIcon("/icons/codex.svg", SessionProviderIcons::class.java)
        private val claude = IconLoader.getIcon("/icons/claude.svg", SessionProviderIcons::class.java)

        fun forProvider(providerId: String): Icon? {
            return when (providerId) {
                CLIProvider.CODEX_ID -> codex
                CLIProvider.CLAUDE_CODE_ID -> claude
                else -> null
            }
        }
    }
}

internal enum class SessionMessageSide {
    Assistant,
    User
}

internal class SessionPreviewConversationPanel(
    providerName: String,
    providerIcon: Icon?,
    preview: SessionContentPreview,
    contentWidth: Int
) : JPanel() {
    private val mutableMessageRows = mutableListOf<SessionChatMessageRow>()
    val messageRows: List<SessionChatMessageRow> get() = mutableMessageRows
    private val mutableStatusMessages = mutableListOf<String>()
    val statusMessages: List<String> get() = mutableStatusMessages

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = CHAT_BACKGROUND
        border = JBUI.Borders.empty(7, 10, 10, 10)
        val rowWidth = (contentWidth - JBUI.scale(20)).coerceAtLeast(JBUI.scale(220))

        if (preview.messages.isEmpty()) {
            add(statusRow("No conversation content is available.", rowWidth))
        } else {
            preview.messages.forEachIndexed { index, message ->
                val row = SessionChatMessageRow(
                    role = message.role,
                    messageText = message.text,
                    providerName = providerName,
                    providerIcon = providerIcon,
                    contentWidth = rowWidth
                )
                mutableMessageRows += row
                add(row)
                if (index == 0 && preview.omittedMessageCount > 0) {
                    val count = preview.omittedMessageCount
                    add(statusRow("$count earlier ${if (count == 1) "message" else "messages"} omitted", rowWidth))
                }
            }
        }
        preview.notice?.takeIf { it.isNotBlank() }?.let { notice ->
            add(statusRow(notice, rowWidth))
        }
        add(Box.createVerticalGlue().apply { setAlignmentX(Component.LEFT_ALIGNMENT) })
    }

    private fun statusRow(text: String, width: Int): JComponent {
        mutableStatusMessages += text
        return JPanel(BorderLayout()).apply {
            isOpaque = false
            alignmentX = LEFT_ALIGNMENT
            val height = JBUI.scale(30)
            preferredSize = Dimension(width, height)
            minimumSize = Dimension(0, height)
            maximumSize = Dimension(Int.MAX_VALUE, height)
            add(JLabel(text, JLabel.CENTER).apply {
                foreground = STATUS_TEXT
                font = font.deriveFont((font.size2D - 1f).coerceAtLeast(10f))
                border = JBUI.Borders.empty(7, 8)
                accessibleContext?.accessibleName = text
            }, BorderLayout.CENTER)
        }
    }

    companion object {
        private val CHAT_BACKGROUND = Color(0x1b, 0x1f, 0x1b)
        private val STATUS_TEXT = Color(0x88, 0x92, 0x87)
    }
}

internal class SessionChatMessageRow(
    role: SessionContentRole,
    val messageText: String,
    providerName: String,
    providerIcon: Icon?,
    contentWidth: Int
) : JPanel() {
    val side: SessionMessageSide = if (role == SessionContentRole.User) {
        SessionMessageSide.User
    } else {
        SessionMessageSide.Assistant
    }
    val roleLabel: String = if (side == SessionMessageSide.User) "Your question" else "$providerName reply"
    val showsProviderAvatar: Boolean = side == SessionMessageSide.Assistant
    val showsUserAvatar: Boolean = side == SessionMessageSide.User

    init {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        isOpaque = false
        alignmentX = LEFT_ALIGNMENT
        border = JBUI.Borders.empty(6, 0)

        val avatarSize = JBUI.scale(30)
        val gap = JBUI.scale(7)
        val maximumBubbleWidth = minOf(
            JBUI.scale(MAX_BUBBLE_WIDTH),
            contentWidth - avatarSize - gap
        ).coerceAtLeast(JBUI.scale(MIN_BUBBLE_WIDTH))
        val bubble = SessionChatBubble(side, messageText, maximumBubbleWidth)

        if (side == SessionMessageSide.Assistant) {
            add(SessionProviderAvatar(providerIcon, avatarSize))
            add(Box.createHorizontalStrut(gap))
            add(bubble)
            add(Box.createHorizontalGlue())
        } else {
            add(Box.createHorizontalGlue())
            add(bubble)
            add(Box.createHorizontalStrut(gap))
            add(SessionUserAvatar(avatarSize))
        }
        accessibleContext?.accessibleName = roleLabel
        accessibleContext?.accessibleDescription = messageText

        val height = maxOf(avatarSize, bubble.preferredSize.height) + JBUI.scale(12)
        preferredSize = Dimension(contentWidth, height)
        minimumSize = Dimension(0, height)
        maximumSize = Dimension(Int.MAX_VALUE, height)
    }

    companion object {
        private const val MAX_BUBBLE_WIDTH = 328
        private const val MIN_BUBBLE_WIDTH = 72
    }
}

private class SessionChatBubble(
    private val side: SessionMessageSide,
    text: String,
    maximumBubbleWidth: Int
) : JPanel(BorderLayout()) {
    private val fillColor = if (side == SessionMessageSide.User) USER_BUBBLE else ASSISTANT_BUBBLE
    private val lineColor = if (side == SessionMessageSide.User) USER_BUBBLE else ASSISTANT_LINE
    private val tailWidth = JBUI.scale(7)
    private val cornerRadius = JBUI.scale(8)

    init {
        isOpaque = false
        val horizontalPadding = JBUI.scale(11)
        val verticalPadding = JBUI.scale(8)
        border = if (side == SessionMessageSide.Assistant) {
            JBUI.Borders.empty(verticalPadding, horizontalPadding + tailWidth, verticalPadding, horizontalPadding)
        } else {
            JBUI.Borders.empty(verticalPadding, horizontalPadding, verticalPadding, horizontalPadding + tailWidth)
        }

        val textArea = JTextArea(text).apply {
            isEditable = false
            isFocusable = false
            isOpaque = false
            lineWrap = true
            wrapStyleWord = true
            foreground = if (side == SessionMessageSide.User) USER_TEXT else ASSISTANT_TEXT
            font = JLabel().font
            border = null
            margin = Insets(0, 0, 0, 0)
            accessibleContext?.accessibleName = if (side == SessionMessageSide.User) "You" else "AI"
        }
        val availableTextWidth = (maximumBubbleWidth - horizontalPadding * 2 - tailWidth)
            .coerceAtLeast(JBUI.scale(48))
        val measuredTextWidth = text.lineSequence()
            .maxOfOrNull { line -> textArea.getFontMetrics(textArea.font).stringWidth(line.ifEmpty { " " }) }
            ?.plus(JBUI.scale(2))
            ?: JBUI.scale(48)
        val textWidth = measuredTextWidth.coerceIn(JBUI.scale(48), availableTextWidth)
        textArea.setSize(Dimension(textWidth, JBUI.scale(10_000)))
        val textHeight = textArea.preferredSize.height.coerceAtLeast(textArea.getFontMetrics(textArea.font).height)
        textArea.preferredSize = Dimension(textWidth, textHeight)
        add(textArea, BorderLayout.CENTER)
        val naturalSize = preferredSize
        minimumSize = naturalSize
        maximumSize = naturalSize
    }

    override fun paintComponent(graphics: Graphics) {
        val copy = graphics.create() as Graphics2D
        copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val bodyX = if (side == SessionMessageSide.Assistant) tailWidth else 0
        val bodyWidth = (width - tailWidth).coerceAtLeast(0)
        val bodyHeight = (height - 1).coerceAtLeast(0)
        copy.color = fillColor
        copy.fillRoundRect(bodyX, 0, bodyWidth, bodyHeight, cornerRadius * 2, cornerRadius * 2)

        val tailCenter = minOf(JBUI.scale(16), height / 2)
        val tail = if (side == SessionMessageSide.Assistant) {
            java.awt.Polygon(
                intArrayOf(tailWidth, tailWidth, 0),
                intArrayOf(tailCenter - JBUI.scale(5), tailCenter + JBUI.scale(5), tailCenter),
                3
            )
        } else {
            java.awt.Polygon(
                intArrayOf(width - tailWidth, width - tailWidth, width),
                intArrayOf(tailCenter - JBUI.scale(5), tailCenter + JBUI.scale(5), tailCenter),
                3
            )
        }
        copy.fillPolygon(tail)
        if (side == SessionMessageSide.Assistant) {
            copy.color = lineColor
            copy.drawRoundRect(bodyX, 0, (bodyWidth - 1).coerceAtLeast(0), bodyHeight, cornerRadius * 2, cornerRadius * 2)
        }
        copy.dispose()
    }

    companion object {
        private val ASSISTANT_BUBBLE = Color(0x2a, 0x2f, 0x2a)
        private val ASSISTANT_LINE = Color(0x3a, 0x42, 0x3b)
        private val ASSISTANT_TEXT = Color(0xee, 0xf2, 0xec)
        private val USER_BUBBLE = Color(0x95, 0xec, 0x69)
        private val USER_TEXT = Color(0x10, 0x17, 0x10)
    }
}

private class SessionProviderAvatar(icon: Icon?, size: Int) : JPanel(BorderLayout()) {
    init {
        isOpaque = false
        preferredSize = Dimension(size, size)
        minimumSize = preferredSize
        maximumSize = preferredSize
        icon?.let { add(JLabel(it).apply { horizontalAlignment = JLabel.CENTER }, BorderLayout.CENTER) }
        accessibleContext?.accessibleName = "AI provider"
    }

    override fun paintComponent(graphics: Graphics) {
        val copy = graphics.create() as Graphics2D
        copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        copy.color = AVATAR_BACKGROUND
        copy.fillRoundRect(0, 0, width, height, JBUI.scale(7), JBUI.scale(7))
        copy.color = AVATAR_LINE
        copy.drawRoundRect(0, 0, (width - 1).coerceAtLeast(0), (height - 1).coerceAtLeast(0), JBUI.scale(7), JBUI.scale(7))
        copy.dispose()
    }

    companion object {
        private val AVATAR_BACKGROUND = Color(0x25, 0x2a, 0x25)
        private val AVATAR_LINE = Color(0x3d, 0x45, 0x3e)
    }
}

private class SessionUserAvatar(size: Int) : JPanel() {
    init {
        isOpaque = false
        preferredSize = Dimension(size, size)
        minimumSize = preferredSize
        maximumSize = preferredSize
        accessibleContext?.accessibleName = "You"
    }

    override fun paintComponent(graphics: Graphics) {
        val copy = graphics.create() as Graphics2D
        copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val radius = JBUI.scale(7)
        copy.color = AVATAR_BACKGROUND
        copy.fillRoundRect(0, 0, width, height, radius, radius)
        copy.color = AVATAR_LINE
        copy.drawRoundRect(0, 0, (width - 1).coerceAtLeast(0), (height - 1).coerceAtLeast(0), radius, radius)

        val centerX = width / 2
        val headSize = JBUI.scale(7)
        copy.color = USER_ICON
        copy.fillOval(centerX - headSize / 2, JBUI.scale(6), headSize, headSize)
        val shoulderWidth = JBUI.scale(16)
        val shoulderHeight = JBUI.scale(10)
        copy.fillRoundRect(
            centerX - shoulderWidth / 2,
            JBUI.scale(16),
            shoulderWidth,
            shoulderHeight,
            JBUI.scale(8),
            JBUI.scale(8)
        )
        copy.dispose()
    }

    companion object {
        private val AVATAR_BACKGROUND = Color(0x2d, 0x58, 0x38)
        private val AVATAR_LINE = Color(0x4d, 0x79, 0x58)
        private val USER_ICON = Color(0xe3, 0xf6, 0xe7)
    }
}
