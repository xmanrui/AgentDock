package com.agentdock.terminal

import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.content.Content
import com.intellij.util.ui.JBUI
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Component
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.geom.AffineTransform
import java.awt.geom.Path2D
import javax.swing.JComponent
import javax.swing.JLayeredPane
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.UIManager
import kotlin.math.roundToInt

internal class TerminalStreamOverlayController(
    private val content: Content,
    private val titleComponent: () -> Component?
) {
    private val overlay = TerminalStreamTickerOverlay(
        anchorProvider = { resolveTitleAnchor() }
    )
    private var layeredPane: JLayeredPane? = null
    private val resizeListener = object : ComponentAdapter() {
        override fun componentResized(event: ComponentEvent) {
            syncOverlayBounds()
        }

        override fun componentShown(event: ComponentEvent) {
            syncOverlayBounds()
        }
    }

    fun show(text: String) {
        runOnEdt {
            if (!content.isValid || text.isBlank()) return@runOnEdt
            val anchor = resolveAnchorComponent() ?: return@runOnEdt
            val rootPane = SwingUtilities.getRootPane(anchor) ?: return@runOnEdt
            attachTo(rootPane.layeredPane)
            overlay.showText(text)
        }
    }

    fun hide() {
        runOnEdt { overlay.hideTicker() }
    }

    fun dispose() {
        runOnEdt {
            overlay.dispose()
            layeredPane?.let { pane ->
                pane.removeComponentListener(resizeListener)
                pane.remove(overlay)
                pane.repaint()
            }
            layeredPane = null
        }
    }

    private fun resolveAnchorComponent(): Component? {
        return titleComponent()?.takeIf { it.isShowing }
            ?: content.manager?.component?.takeIf { it.isShowing }
    }

    private fun resolveTitleAnchor(): TerminalStreamAnchor? {
        val pane = layeredPane ?: return null
        val anchor = titleComponent()?.takeIf { it.isShowing && it.width > 0 && it.height > 0 }
            ?: return null
        val topLeft = SwingUtilities.convertPoint(anchor, 0, 0, pane)
        return TerminalStreamAnchor(
            centerX = topLeft.x + anchor.width / 2,
            topY = topLeft.y,
            width = anchor.width,
            height = anchor.height
        )
    }

    private fun attachTo(target: JLayeredPane) {
        if (layeredPane === target) {
            syncOverlayBounds()
            return
        }
        layeredPane?.let { previous ->
            previous.removeComponentListener(resizeListener)
            previous.remove(overlay)
        }
        layeredPane = target
        target.addComponentListener(resizeListener)
        target.add(overlay)
        target.setLayer(overlay, JLayeredPane.PALETTE_LAYER)
        syncOverlayBounds()
        target.revalidate()
        target.repaint()
    }

    private fun syncOverlayBounds() {
        val pane = layeredPane ?: return
        overlay.setBounds(0, 0, pane.width, pane.height)
    }

    private fun runOnEdt(action: () -> Unit) {
        if (ApplicationManager.getApplication().isDispatchThread) {
            action()
        } else {
            ApplicationManager.getApplication().invokeLater(action)
        }
    }
}

internal class TerminalStreamTickerOverlay(
    private val anchorProvider: () -> TerminalStreamAnchor?,
    private val clock: () -> Long = System::currentTimeMillis,
    private val ticker: TerminalStreamTickerModel = TerminalStreamTickerModel()
) : JComponent() {
    private val timer = Timer(FRAME_INTERVAL_MS) {
        if (!ticker.isActive()) {
            (it.source as Timer).stop()
            isVisible = false
        }
        repaint()
    }.apply {
        isRepeats = true
    }

    init {
        isOpaque = false
        isFocusable = false
        isEnabled = false
        isVisible = false
    }

    fun showText(text: String) {
        if (text.isBlank() || width <= 0 || height <= 0) return
        val anchor = anchorProvider()
        val viewportWidth = anchor
            ?.let { TerminalStreamBubbleGeometry.layout(width, it).contentWidth }
            ?: JBUI.scale(FALLBACK_VIEWPORT_WIDTH)
        ticker.offer(text, viewportWidth, clock())
        isVisible = true
        if (!timer.isRunning) timer.start()
        repaint()
    }

    fun hideTicker() {
        timer.stop()
        ticker.clear()
        isVisible = false
        repaint()
    }

    fun dispose() {
        hideTicker()
    }

    override fun contains(x: Int, y: Int): Boolean = false

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        val anchor = anchorProvider() ?: return
        if (!ticker.isActive()) return

        val copy = graphics.create() as Graphics2D
        try {
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            copy.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            copy.font = UIManager.getFont("Label.font")
                ?.deriveFont(Font.PLAIN, JBUI.scale(FONT_SIZE).toFloat())
                ?: Font(Font.SANS_SERIF, Font.PLAIN, JBUI.scale(FONT_SIZE))

            val layout = TerminalStreamBubbleGeometry.layout(width, anchor)
            paintBubble(copy, layout)
            paintTickerText(copy, layout)
        } finally {
            copy.dispose()
        }
    }

    private fun paintBubble(graphics: Graphics2D, layout: TerminalStreamBubbleLayout) {
        val shadowOffset = JBUI.scale(1)
        val bubblePath = createBubblePath(layout)
        val shadowPath = AffineTransform.getTranslateInstance(0.0, shadowOffset.toDouble())
            .createTransformedShape(bubblePath)
        graphics.color = SHADOW_COLOR
        graphics.fill(shadowPath)

        graphics.color = BUBBLE_BACKGROUND
        graphics.fill(bubblePath)
        graphics.stroke = BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        graphics.color = BUBBLE_BORDER
        graphics.draw(bubblePath)
    }

    private fun createBubblePath(layout: TerminalStreamBubbleLayout): Shape {
        val left = layout.boxX.toDouble()
        val top = layout.boxY.toDouble()
        val right = left + layout.boxWidth
        val bottom = top + layout.boxHeight
        val radius = layout.cornerRadius.toDouble()
        return Path2D.Double().apply {
            moveTo(left + radius, top)
            lineTo(right - radius, top)
            quadTo(right, top, right, top + radius)
            lineTo(right, bottom - radius)
            quadTo(right, bottom, right - radius, bottom)
            lineTo(layout.arrowBaseRightX.toDouble(), bottom)
            lineTo(layout.arrowTipX.toDouble(), layout.arrowTipY.toDouble())
            lineTo(layout.arrowBaseLeftX.toDouble(), bottom)
            lineTo(left + radius, bottom)
            quadTo(left, bottom, left, bottom - radius)
            lineTo(left, top + radius)
            quadTo(left, top, left + radius, top)
            closePath()
        }
    }

    private fun paintTickerText(graphics: Graphics2D, layout: TerminalStreamBubbleLayout) {
        val metrics = graphics.fontMetrics
        val contentBounds = Rectangle(
            layout.boxX + layout.horizontalPadding,
            layout.boxY,
            layout.contentWidth,
            layout.boxHeight
        )
        val currentText = ticker.currentText() ?: return
        val textWidth = metrics.stringWidth(currentText)
        val frame = ticker.frame(contentBounds.width, textWidth, clock()) ?: return
        val originalClip: Shape? = graphics.clip
        graphics.clip(contentBounds)
        graphics.color = BUBBLE_FOREGROUND
        val baseline = contentBounds.y + (contentBounds.height - metrics.height) / 2 + metrics.ascent
        graphics.drawString(frame.text, contentBounds.x + frame.x.roundToInt(), baseline)
        graphics.clip = originalClip
    }

    companion object {
        private const val FRAME_INTERVAL_MS = 33
        private const val FONT_SIZE = 12
        private const val FALLBACK_VIEWPORT_WIDTH = 180
        private val BUBBLE_BACKGROUND = Color(0x23, 0x36, 0x57, 220)
        private val BUBBLE_BORDER = Color(0x2E, 0x4D, 0x89)
        private val BUBBLE_FOREGROUND = Color(243, 246, 252, 238)
        private val SHADOW_COLOR = Color(0, 0, 0, 38)
    }
}

internal data class TerminalStreamTickerFrame(
    val text: String,
    val x: Double
)

internal class TerminalStreamTickerModel(
    private val pixelsPerSecond: Double = DEFAULT_PIXELS_PER_SECOND,
    private val maximumQueuedMessages: Int = DEFAULT_MAXIMUM_QUEUED_MESSAGES
) {
    private val queuedTexts = ArrayDeque<String>()
    private var current: String? = null
    private var x = 0.0
    private var lastFrameAt: Long? = null

    fun offer(text: String, viewportWidth: Int, nowMs: Long) {
        val normalized = text.trim()
        if (normalized.isBlank()) return

        val currentText = current
        if (currentText == null) {
            current = normalized
            x = viewportWidth.toDouble()
            lastFrameAt = nowMs
            return
        }
        if (sameGrowingMessage(currentText, normalized)) {
            if (normalized.length >= currentText.length) current = normalized
            return
        }

        val tail = queuedTexts.lastOrNull()
        if (tail != null && sameGrowingMessage(tail, normalized)) {
            if (normalized.length >= tail.length) {
                queuedTexts.removeLast()
                queuedTexts.addLast(normalized)
            }
            return
        }
        if (normalized == currentText || normalized in queuedTexts) return
        while (queuedTexts.size >= maximumQueuedMessages) {
            queuedTexts.removeFirst()
        }
        queuedTexts.addLast(normalized)
    }

    fun frame(viewportWidth: Int, currentTextWidth: Int, nowMs: Long): TerminalStreamTickerFrame? {
        var text = current ?: return null
        val previousFrameAt = lastFrameAt ?: nowMs
        val elapsedMs = (nowMs - previousFrameAt).coerceIn(0L, MAXIMUM_FRAME_DELTA_MS)
        x -= pixelsPerSecond * elapsedMs / 1_000.0
        lastFrameAt = nowMs

        if (x + currentTextWidth < 0.0) {
            text = if (queuedTexts.isEmpty()) text else queuedTexts.removeFirst()
            current = text
            x = viewportWidth + LOOP_GAP_PIXELS
        }
        return TerminalStreamTickerFrame(text, x)
    }

    fun currentText(): String? = current

    fun isActive(): Boolean = current != null

    fun clear() {
        queuedTexts.clear()
        current = null
        x = 0.0
        lastFrameAt = null
    }

    private fun sameGrowingMessage(first: String, second: String): Boolean {
        return first.startsWith(second) || second.startsWith(first)
    }

    companion object {
        private const val DEFAULT_PIXELS_PER_SECOND = 72.0
        private const val DEFAULT_MAXIMUM_QUEUED_MESSAGES = 5
        private const val MAXIMUM_FRAME_DELTA_MS = 120L
        private const val LOOP_GAP_PIXELS = 28.0
    }
}

internal data class TerminalStreamBubbleLayout(
    val boxX: Int,
    val boxY: Int,
    val boxWidth: Int,
    val boxHeight: Int,
    val cornerRadius: Int,
    val horizontalPadding: Int,
    val arrowBaseLeftX: Int,
    val arrowBaseRightX: Int,
    val arrowBaseY: Int,
    val arrowTipX: Int,
    val arrowTipY: Int
) {
    val contentWidth: Int
        get() = (boxWidth - horizontalPadding * 2).coerceAtLeast(1)
}

internal data class TerminalStreamAnchor(
    val centerX: Int,
    val topY: Int,
    val width: Int,
    val height: Int
)

internal object TerminalStreamBubbleGeometry {
    fun layout(containerWidth: Int, anchor: TerminalStreamAnchor): TerminalStreamBubbleLayout {
        val margin = JBUI.scale(6)
        val siblingGap = JBUI.scale(6)
        val preferredBoxWidth = (anchor.width - siblingGap).coerceAtLeast(1)
        val boxWidth = preferredBoxWidth.coerceAtMost((containerWidth - margin * 2).coerceAtLeast(1))
        val boxHeight = JBUI.scale(28)
        val arrowHeight = JBUI.scale(6)
        val arrowHalfWidth = minOf(JBUI.scale(8), (boxWidth / 4).coerceAtLeast(1))
        val verticalGap = JBUI.scale(3)
        val boxX = (anchor.centerX - boxWidth / 2).coerceIn(
            margin,
            (containerWidth - boxWidth - margin).coerceAtLeast(margin)
        )
        val boxY = (anchor.topY - verticalGap - arrowHeight - boxHeight).coerceAtLeast(margin)
        val arrowTipX = boxX + boxWidth / 2
        val arrowBaseY = boxY + boxHeight
        return TerminalStreamBubbleLayout(
            boxX = boxX,
            boxY = boxY,
            boxWidth = boxWidth,
            boxHeight = boxHeight,
            cornerRadius = minOf(JBUI.scale(7), boxHeight / 2),
            horizontalPadding = JBUI.scale(10),
            arrowBaseLeftX = arrowTipX - arrowHalfWidth,
            arrowBaseRightX = arrowTipX + arrowHalfWidth,
            arrowBaseY = arrowBaseY,
            arrowTipX = arrowTipX,
            arrowTipY = arrowBaseY + arrowHeight
        )
    }
}
