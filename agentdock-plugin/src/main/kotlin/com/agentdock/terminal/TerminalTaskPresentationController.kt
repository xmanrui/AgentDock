package com.agentdock.terminal

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.KeyboardFocusManager
import java.awt.RenderingHints
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.beans.PropertyChangeListener
import javax.swing.Icon
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.UIManager
import kotlin.math.PI
import kotlin.math.sin

internal class TerminalTaskPresentationController(
    private val content: Content,
    baseIcon: Icon,
    private val fullTitle: String
) {
    @Volatile
    private var state = TerminalTaskState.Idle
    private val activityTracker = TerminalTaskActivityTracker()
    private val statusIcon = TerminalTaskStatusIcon(baseIcon) { repaintTab() }
    private val streamOverlay = TerminalStreamOverlayController(content) { statusIcon.lastPaintComponent }
    private val viewedTimer = Timer(VIEWED_DELAY_MS) {
        if (state == TerminalTaskState.Ready && isViewed()) {
            transition(TerminalTaskEvent.Viewed)
        }
    }.apply {
        isRepeats = false
    }
    private val selectionListener = object : ContentManagerListener {
        override fun selectionChanged(event: ContentManagerEvent) {
            if (event.content == content) {
                scheduleViewedTransition()
            } else {
                viewedTimer.stop()
            }
        }
    }
    private val focusListener = PropertyChangeListener {
        scheduleViewedTransition()
    }
    private val visibilityListener = object : ComponentAdapter() {
        override fun componentShown(event: ComponentEvent) {
            scheduleViewedTransition()
        }

        override fun componentHidden(event: ComponentEvent) {
            viewedTimer.stop()
        }
    }

    init {
        content.manager?.addContentManagerListener(selectionListener)
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
            .addPropertyChangeListener("focusOwner", focusListener)
        content.component.addComponentListener(visibilityListener)
        applyPresentation()
        reapplyPresentation()
    }

    fun onActivity(event: TerminalActivityEvent) {
        activityTracker.accept(event)?.let(::transition)
    }

    fun isWorking(): Boolean = state == TerminalTaskState.Working

    fun onStreamText(text: String) {
        if (isWorking()) {
            streamOverlay.show(text)
        }
    }

    fun dispose() {
        viewedTimer.stop()
        statusIcon.dispose()
        streamOverlay.dispose()
        content.manager?.removeContentManagerListener(selectionListener)
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
            .removePropertyChangeListener("focusOwner", focusListener)
        content.component.removeComponentListener(visibilityListener)
    }

    private fun transition(event: TerminalTaskEvent) {
        val nextState = TerminalTaskStateReducer.reduce(state, event)
        if (nextState == state) return
        state = nextState
        if (state != TerminalTaskState.Ready) {
            viewedTimer.stop()
        }
        if (state != TerminalTaskState.Working) {
            streamOverlay.hide()
        }
        applyPresentation()
        if (state == TerminalTaskState.Ready) {
            scheduleViewedTransition()
        }
    }

    private fun applyPresentation() {
        if (!content.isValid) return
        statusIcon.state = state
        content.description = "$fullTitle - ${state.label}"
        content.toolwindowTitle = fullTitle
        content.putUserData(ToolWindow.SHOW_CONTENT_ICON, true)
        content.icon = statusIcon
        content.popupIcon = statusIcon
        repaintTab()
    }

    private fun reapplyPresentation() {
        ApplicationManager.getApplication().invokeLater {
            applyPresentation()
        }
        listOf(250, 1_000).forEach { delay ->
            Timer(delay) {
                applyPresentation()
            }.apply {
                isRepeats = false
                start()
            }
        }
    }

    private fun scheduleViewedTransition() {
        if (state != TerminalTaskState.Ready || !isViewed()) {
            viewedTimer.stop()
            return
        }
        if (!viewedTimer.isRunning) {
            viewedTimer.start()
        }
    }

    private fun isViewed(): Boolean {
        if (!content.isValid || !content.component.isShowing) return false
        if (content.manager?.selectedContent !== content) return false
        val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner ?: return false
        return SwingUtilities.isDescendingFrom(focusOwner, content.component)
    }

    private fun repaintTab() {
        statusIcon.lastPaintComponent?.repaint()
        content.manager?.component?.repaint()
    }

    companion object {
        private const val VIEWED_DELAY_MS = 500
    }
}

internal class TerminalTaskStatusIcon(
    private val baseIcon: Icon,
    private val repaint: () -> Unit
) : Icon {
    private val padding = JBUI.scale(2)
    private var animationStartedAt = System.currentTimeMillis()
    private val animationTimer = Timer(ANIMATION_FRAME_MS) {
        repaint()
    }.apply {
        isRepeats = true
    }
    var lastPaintComponent: Component? = null
        private set

    var state: TerminalTaskState = TerminalTaskState.Idle
        set(value) {
            if (field == value) return
            field = value
            animationStartedAt = System.currentTimeMillis()
            if (value == TerminalTaskState.Working) {
                if (!animationTimer.isRunning) animationTimer.start()
            } else {
                animationTimer.stop()
            }
            repaint()
        }

    override fun getIconWidth(): Int = baseIcon.iconWidth + padding * 2

    override fun getIconHeight(): Int = baseIcon.iconHeight + padding * 2

    override fun paintIcon(component: Component?, graphics: Graphics, x: Int, y: Int) {
        lastPaintComponent = component
        val copy = graphics.create() as Graphics2D
        try {
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val centerX = x + iconWidth / 2.0
            val centerY = y + iconHeight / 2.0
            val scale = if (state == TerminalTaskState.Working) {
                TerminalTaskIconGeometry.workingScale(System.currentTimeMillis() - animationStartedAt)
            } else {
                1.0
            }
            copy.translate(centerX, centerY)
            copy.scale(scale, scale)
            baseIcon.paintIcon(
                component,
                copy,
                -baseIcon.iconWidth / 2,
                -baseIcon.iconHeight / 2
            )
            copy.scale(1.0 / scale, 1.0 / scale)
            copy.translate(-centerX, -centerY)
            if (state == TerminalTaskState.Ready) {
                paintReadyBadge(component, copy, x, y)
            }
        } finally {
            copy.dispose()
        }
    }

    fun dispose() {
        animationTimer.stop()
        lastPaintComponent = null
    }

    private fun paintReadyBadge(component: Component?, graphics: Graphics2D, x: Int, y: Int) {
        val diameter = JBUI.scale(7)
        val badgeX = x + iconWidth - diameter
        val badgeY = y
        graphics.color = component?.background
            ?: UIManager.getColor("ToolWindow.background")
            ?: Color(0x1B1F1B)
        graphics.fillOval(badgeX - 1, badgeY - 1, diameter + 2, diameter + 2)
        graphics.color = READY_COLOR
        graphics.fillOval(badgeX, badgeY, diameter, diameter)
    }

    companion object {
        private const val ANIMATION_FRAME_MS = 50
        private val READY_COLOR = Color(0x68D982)
    }
}

internal object TerminalTaskIconGeometry {
    private const val CYCLE_MS = 1_200L
    private const val SCALE_AMPLITUDE = 0.08

    fun workingScale(elapsedMs: Long): Double {
        val progress = (elapsedMs.mod(CYCLE_MS)).toDouble() / CYCLE_MS
        return 1.0 + SCALE_AMPLITUDE * sin(progress * 2.0 * PI)
    }
}
