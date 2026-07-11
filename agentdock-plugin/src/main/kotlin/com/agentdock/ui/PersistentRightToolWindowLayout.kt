package com.agentdock.ui

import com.intellij.icons.AllIcons
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.LayoutManager
import java.awt.LayoutManager2
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.UIManager
import kotlin.math.max
import kotlin.math.min

internal class PersistentRightToolWindowLayout(
    private val content: JComponent,
    private val nativeToolWindowComponent: () -> JComponent?,
    private val onHide: () -> Unit,
    private val controlsContainerLocator: (JComponent?) -> Container? = { component ->
        findToolWindowControlsContainer(component)
    },
    private val nativeDecoratorLocator: (JComponent?) -> JComponent? = { component ->
        findNativeDecorator(component)
    }
) {
    private val contentHolder = JPanel(BorderLayout())
    private val hostPanel = createHostPanel()
    private var rootContainer: Container? = null
    private var originalRootLayout: BorderLayout? = null
    private var installedRootLayout: PersistentRightLayoutManager? = null
    private var nativeDecorator: JComponent? = null
    private var panelWidth = 0

    val isShowing: Boolean
        get() = rootContainer != null &&
            hostPanel.parent === rootContainer &&
            rootContainer?.layout === installedRootLayout

    fun show(): Boolean {
        if (isShowing) {
            hideNativeDecorator()
            return true
        }

        val nativeComponent = nativeToolWindowComponent()
        val root = controlsContainerLocator(nativeComponent) ?: return false
        val originalLayout = root.layout as? BorderLayout ?: return false
        if (originalLayout.getLayoutComponent(root, BorderLayout.CENTER) == null) return false

        nativeDecorator = nativeDecoratorLocator(nativeComponent) ?: nativeDecorator
        panelWidth = preferredPanelWidth(root, nativeComponent)
        hostPanel.preferredSize = Dimension(panelWidth, 0)

        val persistentLayout = PersistentRightLayoutManager(originalLayout, hostPanel)
        root.layout = persistentLayout
        root.add(hostPanel)
        rootContainer = root
        originalRootLayout = originalLayout
        installedRootLayout = persistentLayout
        hostContent()
        hideNativeDecorator()
        root.revalidate()
        root.repaint()
        return true
    }

    fun hide() {
        val root = rootContainer
        if (root != null) {
            if (hostPanel.parent === root) {
                panelWidth = max(MIN_PANEL_WIDTH, hostPanel.width)
                root.remove(hostPanel)
            }
            if (root.layout === installedRootLayout) {
                originalRootLayout?.let { root.layout = it }
            }
            root.revalidate()
            root.repaint()
        }
        nativeDecorator = null
        rootContainer = null
        originalRootLayout = null
        installedRootLayout = null
    }

    fun dispose() {
        hide()
        contentHolder.remove(content)
    }

    private fun createHostPanel(): JPanel {
        val background = uiColor("ToolWindow.background", "Panel.background")
        val header = JPanel(BorderLayout()).apply {
            this.background = background
            isOpaque = false
            border = BorderFactory.createEmptyBorder(
                0,
                CONTENT_HORIZONTAL_PADDING,
                0,
                HEADER_BUTTON_PADDING
            )
            preferredSize = Dimension(0, HEADER_HEIGHT)
        }
        header.add(
            JLabel("AgentDock").apply {
                font = font.deriveFont(Font.BOLD)
            },
            BorderLayout.CENTER
        )
        header.add(
            JButton(AllIcons.General.HideToolWindow).apply {
                toolTipText = "Hide AgentDock"
                accessibleContext.accessibleName = toolTipText
                isFocusable = false
                isOpaque = false
                isContentAreaFilled = false
                border = BorderFactory.createEmptyBorder()
                preferredSize = Dimension(HEADER_HEIGHT, HEADER_HEIGHT)
                addActionListener { onHide() }
            },
            BorderLayout.EAST
        )

        contentHolder.background = background
        val surface = RoundedSurfacePanel(BorderLayout(), CORNER_RADIUS).apply {
            this.background = background
            add(header, BorderLayout.NORTH)
            add(contentHolder, BorderLayout.CENTER)
        }
        val surfaceWrapper = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = BorderFactory.createEmptyBorder(
                SURFACE_MARGIN,
                0,
                SURFACE_MARGIN,
                SURFACE_MARGIN
            )
            add(surface, BorderLayout.CENTER)
        }

        return JPanel(BorderLayout()).apply {
            isOpaque = false
            add(createResizeHandle(), BorderLayout.WEST)
            add(surfaceWrapper, BorderLayout.CENTER)
        }
    }

    private fun createResizeHandle(): JComponent {
        return JPanel().apply {
            isOpaque = false
            preferredSize = Dimension(RESIZE_HANDLE_WIDTH, 0)
            cursor = Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR)
            val listener = object : MouseAdapter() {
                private var startScreenX = 0
                private var startWidth = 0

                override fun mousePressed(event: MouseEvent) {
                    startScreenX = event.xOnScreen
                    startWidth = hostPanel.width
                }

                override fun mouseDragged(event: MouseEvent) {
                    val root = rootContainer ?: return
                    val maximumWidth = max(MIN_PANEL_WIDTH, (root.width * MAX_PANEL_PROPORTION).toInt())
                    panelWidth = (startWidth + startScreenX - event.xOnScreen)
                        .coerceIn(MIN_PANEL_WIDTH, maximumWidth)
                    hostPanel.preferredSize = Dimension(panelWidth, 0)
                    root.revalidate()
                }
            }
            addMouseListener(listener)
            addMouseMotionListener(listener)
        }
    }

    private fun hostContent() {
        if (content.parent !== contentHolder) {
            content.parent?.remove(content)
            contentHolder.add(content, BorderLayout.CENTER)
            contentHolder.revalidate()
            contentHolder.repaint()
        }
    }

    private fun hideNativeDecorator() {
        val decorator = nativeDecoratorLocator(nativeToolWindowComponent()) ?: nativeDecorator ?: return
        nativeDecorator = decorator
        if (decorator.isVisible) {
            decorator.isVisible = false
            decorator.parent?.revalidate()
            decorator.parent?.repaint()
        }
    }

    private fun preferredPanelWidth(root: Container, nativeComponent: JComponent?): Int {
        if (panelWidth >= MIN_PANEL_WIDTH) return panelWidth
        val availableWidth = max(root.width, root.preferredSize.width)
        val nativeWidth = nativeComponent?.width ?: 0
        val fallbackWidth = (availableWidth * DEFAULT_PANEL_PROPORTION).toInt()
        return min(
            max(nativeWidth, max(MIN_PANEL_WIDTH, fallbackWidth)),
            max(MIN_PANEL_WIDTH, (availableWidth * MAX_PANEL_PROPORTION).toInt())
        )
    }

    private fun uiColor(primaryKey: String, fallbackKey: String): Color {
        return UIManager.getColor(primaryKey)
            ?: UIManager.getColor(fallbackKey)
            ?: Color.GRAY
    }

    companion object {
        private const val INTERNAL_DECORATOR_CLASS = "com.intellij.toolWindow.InternalDecoratorImpl"
        private const val TOOL_WINDOW_PANE_CLASS = "com.intellij.toolWindow.ToolWindowPane"
        private const val DEFAULT_PANEL_PROPORTION = 0.36
        private const val MAX_PANEL_PROPORTION = 0.55
        private const val MIN_PANEL_WIDTH = 360
        private const val MIN_IDE_CONTENT_WIDTH = 240
        private const val RESIZE_HANDLE_WIDTH = 5
        private const val SURFACE_MARGIN = 4
        private const val CORNER_RADIUS = 8
        private const val HEADER_HEIGHT = 40
        private const val CONTENT_HORIZONTAL_PADDING = 10
        private const val HEADER_BUTTON_PADDING = 4

        private fun findNativeDecorator(start: JComponent?): JComponent? {
            var current: Container? = start
            while (current != null) {
                if (current.javaClass.name == INTERNAL_DECORATOR_CLASS) {
                    return current as? JComponent
                }
                current = current.parent
            }
            return null
        }

        private fun findToolWindowControlsContainer(start: JComponent?): Container? {
            var current: Container? = start
            while (current != null) {
                if (current.javaClass.name == TOOL_WINDOW_PANE_CLASS) {
                    val controlsContainer = current.parent
                    if (controlsContainer?.layout is BorderLayout) {
                        return controlsContainer
                    }
                    return null
                }
                current = current.parent
            }
            return null
        }
    }

    private class RoundedSurfacePanel(
        layout: LayoutManager,
        private val cornerRadius: Int
    ) : JPanel(layout) {
        init {
            isOpaque = false
        }

        override fun paintComponent(graphics: Graphics) {
            super.paintComponent(graphics)
            val copy = graphics.create() as Graphics2D
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            copy.color = background
            copy.fill(createSurfaceShape())
            copy.dispose()
        }

        override fun paintChildren(graphics: Graphics) {
            val copy = graphics.create() as Graphics2D
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            copy.clip(createSurfaceShape())
            super.paintChildren(copy)
            copy.dispose()
        }

        private fun createSurfaceShape(): RoundRectangle2D.Float {
            val diameter = cornerRadius * 2f
            return RoundRectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat(), diameter, diameter)
        }
    }

    private class PersistentRightLayoutManager(
        private val delegate: BorderLayout,
        private val sidePanel: JComponent
    ) : LayoutManager2 {
        override fun addLayoutComponent(name: String?, component: Component) {
            if (component !== sidePanel) {
                delegate.addLayoutComponent(component, name)
            }
        }

        override fun addLayoutComponent(component: Component, constraints: Any?) {
            if (component !== sidePanel) {
                delegate.addLayoutComponent(component, constraints)
            }
        }

        override fun removeLayoutComponent(component: Component) {
            if (component !== sidePanel) {
                delegate.removeLayoutComponent(component)
            }
        }

        override fun preferredLayoutSize(parent: Container): Dimension {
            return addSidePanelWidth(delegate.preferredLayoutSize(parent))
        }

        override fun minimumLayoutSize(parent: Container): Dimension {
            return addSidePanelWidth(delegate.minimumLayoutSize(parent))
        }

        override fun maximumLayoutSize(target: Container): Dimension {
            return addSidePanelWidth(delegate.maximumLayoutSize(target))
        }

        override fun getLayoutAlignmentX(target: Container): Float = delegate.getLayoutAlignmentX(target)

        override fun getLayoutAlignmentY(target: Container): Float = delegate.getLayoutAlignmentY(target)

        override fun invalidateLayout(target: Container) {
            delegate.invalidateLayout(target)
        }

        override fun layoutContainer(parent: Container) {
            delegate.layoutContainer(parent)
            val center = delegate.getLayoutComponent(parent, BorderLayout.CENTER)
            if (center == null || !center.isVisible) {
                sidePanel.setBounds(0, 0, 0, 0)
                return
            }

            val centerBounds = center.bounds
            val availableWidth = max(0, centerBounds.width - MIN_IDE_CONTENT_WIDTH)
            val sidePanelWidth = min(sidePanel.preferredSize.width, availableWidth)
            val centerWidth = centerBounds.width - sidePanelWidth
            center.setBounds(centerBounds.x, centerBounds.y, centerWidth, centerBounds.height)
            sidePanel.setBounds(
                centerBounds.x + centerWidth,
                centerBounds.y,
                sidePanelWidth,
                centerBounds.height
            )
        }

        private fun addSidePanelWidth(size: Dimension): Dimension {
            val sidePanelWidth = max(0, sidePanel.preferredSize.width)
            val width = if (size.width > Int.MAX_VALUE - sidePanelWidth) {
                Int.MAX_VALUE
            } else {
                size.width + sidePanelWidth
            }
            return Dimension(width, size.height)
        }
    }
}
