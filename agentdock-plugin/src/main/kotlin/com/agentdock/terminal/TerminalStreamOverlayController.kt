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
import javax.swing.ImageIcon
import javax.swing.JComponent
import javax.swing.JLayeredPane
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.UIManager
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

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
    private val ticker: TerminalStreamTickerModel = TerminalStreamTickerModel(),
    private val gifProvider: () -> TerminalStreamGifSelection? = TerminalStreamGifCatalog::acquire,
    private val gifReleaser: (TerminalStreamGifSelection) -> Unit = TerminalStreamGifCatalog::release
) : JComponent() {
    private var selectedGif: TerminalStreamGifSelection? = null
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
        if (!ticker.isActive()) selectedGif = gifProvider()
        val anchor = anchorProvider()
        val viewportWidth = anchor
            ?.let { TerminalStreamBubbleGeometry.layout(width, it, selectedGif?.icon?.gifSize()).contentWidth }
            ?: JBUI.scale(FALLBACK_VIEWPORT_WIDTH)
        ticker.offer(text, viewportWidth, clock())
        isVisible = true
        if (!timer.isRunning) timer.start()
        repaint()
    }

    fun hideTicker() {
        timer.stop()
        ticker.clear()
        selectedGif?.let(gifReleaser)
        selectedGif = null
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

            val layout = TerminalStreamBubbleGeometry.layout(width, anchor, selectedGif?.icon?.gifSize())
            paintBubble(copy, layout)
            paintGif(copy, layout)
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

    private fun paintGif(graphics: Graphics2D, layout: TerminalStreamBubbleLayout) {
        val gif = selectedGif?.icon ?: return
        if (!layout.hasGif) return
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        graphics.drawImage(
            gif.image,
            layout.gifX,
            layout.gifY,
            layout.gifWidth,
            layout.gifHeight,
            this
        )
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

internal data class TerminalStreamGifSize(
    val width: Int,
    val height: Int
)

internal data class TerminalStreamGifSelection(
    val resourcePath: String,
    val icon: ImageIcon
)

private fun ImageIcon.gifSize(): TerminalStreamGifSize? {
    return if (iconWidth > 0 && iconHeight > 0) TerminalStreamGifSize(iconWidth, iconHeight) else null
}

internal object TerminalStreamGifCatalog {
    private const val RESOURCE_DIRECTORY = "/images/gifs"
    private const val CATALOG_RESOURCE = "$RESOURCE_DIRECTORY/catalog.txt"
    private const val FIRST_GIF_RESOURCE = "$RESOURCE_DIRECTORY/basketball-kunkun-running-right.gif"

    private val gifResourcePaths: List<String> by lazy { discoverResourcePaths() }
    private val usageCountsByPath = mutableMapOf<String, Int>()

    @Synchronized
    fun acquire(random: Random = Random.Default): TerminalStreamGifSelection? {
        if (gifResourcePaths.isEmpty()) return null
        val unusedPaths = gifResourcePaths.filter { usageCountsByPath.getOrDefault(it, 0) == 0 }
        val availablePaths = unusedPaths.ifEmpty { gifResourcePaths }
        val preferredFirstPath = FIRST_GIF_RESOURCE.takeIf {
            usageCountsByPath.isEmpty() && it in availablePaths
        }
        val candidates = buildList {
            preferredFirstPath?.let(::add)
            addAll(availablePaths.filterNot { it == preferredFirstPath }.shuffled(random))
        }
        for (path in candidates) {
            val url = TerminalStreamGifCatalog::class.java.getResource(path) ?: continue
            val icon = ImageIcon(url)
            if (icon.iconWidth > 0 && icon.iconHeight > 0) {
                usageCountsByPath[path] = usageCountsByPath.getOrDefault(path, 0) + 1
                return TerminalStreamGifSelection(path, icon)
            }
        }
        return null
    }

    @Synchronized
    fun release(selection: TerminalStreamGifSelection) {
        val usageCount = usageCountsByPath[selection.resourcePath] ?: 0
        if (usageCount <= 1) {
            usageCountsByPath.remove(selection.resourcePath)
        } else {
            usageCountsByPath[selection.resourcePath] = usageCount - 1
        }
        selection.icon.image.flush()
    }

    internal fun resourcePaths(): List<String> = gifResourcePaths

    private fun discoverResourcePaths(): List<String> {
        return runCatching {
            TerminalStreamGifCatalog::class.java.getResourceAsStream(CATALOG_RESOURCE)
                ?.bufferedReader()
                ?.useLines { lines ->
                    lines.map(String::trim)
                        .filter { it.isNotEmpty() && it.endsWith(".gif", ignoreCase = true) }
                        .map { "$RESOURCE_DIRECTORY/$it" }
                        .distinct()
                        .sorted()
                        .toList()
                }
                .orEmpty()
        }.getOrDefault(emptyList())
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
    val arrowTipY: Int,
    val gifX: Int,
    val gifY: Int,
    val gifWidth: Int,
    val gifHeight: Int
) {
    val contentWidth: Int
        get() = (boxWidth - horizontalPadding * 2).coerceAtLeast(1)

    val hasGif: Boolean
        get() = gifWidth > 0 && gifHeight > 0
}

internal data class TerminalStreamAnchor(
    val centerX: Int,
    val topY: Int,
    val width: Int,
    val height: Int
)

internal object TerminalStreamBubbleGeometry {
    fun layout(
        containerWidth: Int,
        anchor: TerminalStreamAnchor,
        gifSize: TerminalStreamGifSize? = null
    ): TerminalStreamBubbleLayout {
        val margin = JBUI.scale(6)
        val siblingGap = JBUI.scale(6)
        val preferredBoxWidth = (anchor.width - siblingGap).coerceAtLeast(1)
        val boxWidth = preferredBoxWidth.coerceAtMost((containerWidth - margin * 2).coerceAtLeast(1))
        val boxHeight = JBUI.scale(28)
        val arrowHeight = JBUI.scale(6)
        val arrowHalfWidth = minOf(JBUI.scale(8), (boxWidth / 4).coerceAtLeast(1))
        val verticalGap = JBUI.scale(3)
        val gifBubbleGap = JBUI.scale(2)
        val gifTitleGap = JBUI.scale(2)
        val maximumGifHeight = (
            anchor.topY - margin - boxHeight - arrowHeight - gifBubbleGap - gifTitleGap
            ).coerceAtLeast(0).coerceAtMost(JBUI.scale(104))
        val maximumGifWidth = minOf(boxWidth, JBUI.scale(128))
        val scaledGifSize = gifSize?.scaleToFit(maximumGifWidth, maximumGifHeight)
        val gifWidth = scaledGifSize?.width ?: 0
        val gifHeight = scaledGifSize?.height ?: 0
        val hasGif = gifWidth > 0 && gifHeight > 0
        val gifX = if (hasGif) {
            (anchor.centerX - gifWidth / 2).coerceIn(
                margin,
                (containerWidth - gifWidth - margin).coerceAtLeast(margin)
            )
        } else {
            anchor.centerX
        }
        val gifY = if (hasGif) anchor.topY - gifTitleGap - gifHeight else anchor.topY
        val boxX = (anchor.centerX - boxWidth / 2).coerceIn(
            margin,
            (containerWidth - boxWidth - margin).coerceAtLeast(margin)
        )
        val boxBottom = if (hasGif) gifY - gifBubbleGap - arrowHeight else anchor.topY - verticalGap - arrowHeight
        val boxY = (boxBottom - boxHeight).coerceAtLeast(margin)
        val cornerRadius = minOf(JBUI.scale(7), boxHeight / 2)
        val desiredArrowTipX = if (hasGif) gifX + gifWidth / 2 else anchor.centerX
        val minimumArrowTipX = boxX + arrowHalfWidth
        val maximumArrowTipX = boxX + boxWidth - arrowHalfWidth
        val arrowTipX = if (minimumArrowTipX <= maximumArrowTipX) {
            desiredArrowTipX.coerceIn(minimumArrowTipX, maximumArrowTipX)
        } else {
            boxX + boxWidth / 2
        }
        val arrowBaseY = boxY + boxHeight
        return TerminalStreamBubbleLayout(
            boxX = boxX,
            boxY = boxY,
            boxWidth = boxWidth,
            boxHeight = boxHeight,
            cornerRadius = cornerRadius,
            horizontalPadding = JBUI.scale(10),
            arrowBaseLeftX = arrowTipX - arrowHalfWidth,
            arrowBaseRightX = arrowTipX + arrowHalfWidth,
            arrowBaseY = arrowBaseY,
            arrowTipX = arrowTipX,
            arrowTipY = arrowBaseY + arrowHeight,
            gifX = gifX,
            gifY = gifY,
            gifWidth = gifWidth,
            gifHeight = gifHeight
        )
    }

    private fun TerminalStreamGifSize.scaleToFit(maximumWidth: Int, maximumHeight: Int): TerminalStreamGifSize? {
        if (width <= 0 || height <= 0 || maximumWidth <= 0 || maximumHeight <= 0) return null
        val scale = min(maximumWidth.toDouble() / width, maximumHeight.toDouble() / height)
        return TerminalStreamGifSize(
            width = (width * scale).roundToInt().coerceAtLeast(1),
            height = (height * scale).roundToInt().coerceAtLeast(1)
        )
    }
}
