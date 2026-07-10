package com.agentdock.ui

import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PersistentRightToolWindowLayoutTest {
    @Test
    fun `show places the panel inside the right toolbar and hide restores the IDE center`() {
        val idePane = JPanel()
        val rightToolbar = JPanel().apply { preferredSize = Dimension(48, 0) }
        val content = JPanel()
        val nativeDecorator = JPanel().apply { isVisible = true }
        val root = JPanel(BorderLayout()).apply {
            size = Dimension(1_200, 800)
            add(idePane, BorderLayout.CENTER)
            add(rightToolbar, BorderLayout.EAST)
        }
        val originalLayout = root.layout
        val layout = PersistentRightToolWindowLayout(
            content = content,
            nativeToolWindowComponent = { null },
            onHide = {},
            controlsContainerLocator = { root },
            nativeDecoratorLocator = { nativeDecorator }
        )

        assertTrue(layout.show())
        assertFalse(nativeDecorator.isVisible)
        root.doLayout()

        val hostPanel = assertNotNull(SwingTestHelpers.directChildOf(content, root)) as JPanel
        val hostLayout = hostPanel.layout as BorderLayout
        val resizeHandle = assertNotNull(hostLayout.getLayoutComponent(hostPanel, BorderLayout.WEST)) as JPanel
        val surfaceWrapper = assertNotNull(
            hostLayout.getLayoutComponent(hostPanel, BorderLayout.CENTER)
        ) as JPanel
        val surface = assertNotNull(
            (surfaceWrapper.layout as BorderLayout).getLayoutComponent(surfaceWrapper, BorderLayout.CENTER)
        ) as JPanel
        assertFalse(resizeHandle.isOpaque)
        assertFalse(surface.isOpaque)
        surface.size = Dimension(200, 200)
        surface.doLayout()
        val image = BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            surface.paint(graphics)
        } finally {
            graphics.dispose()
        }
        assertEquals(0, image.alphaAt(0, 0))
        assertTrue(image.alphaAt(100, 100) > 0)
        val originalHostWidth = hostPanel.width
        val pressed = MouseEvent(
            resizeHandle,
            MouseEvent.MOUSE_PRESSED,
            0,
            InputEvent.BUTTON1_DOWN_MASK,
            0,
            0,
            1_000,
            200,
            1,
            false,
            MouseEvent.BUTTON1
        )
        val dragged = MouseEvent(
            resizeHandle,
            MouseEvent.MOUSE_DRAGGED,
            0,
            InputEvent.BUTTON1_DOWN_MASK,
            0,
            0,
            900,
            200,
            0,
            false,
            MouseEvent.NOBUTTON
        )
        resizeHandle.mouseListeners.forEach { listener -> listener.mousePressed(pressed) }
        resizeHandle.mouseMotionListeners.forEach { listener -> listener.mouseDragged(dragged) }
        root.doLayout()

        assertTrue(hostPanel.width > originalHostWidth)
        assertTrue(hostPanel.x > idePane.x)
        assertEquals(idePane.x + idePane.width, hostPanel.x)
        assertEquals(hostPanel.x + hostPanel.width, rightToolbar.x)
        assertEquals(root.width, rightToolbar.x + rightToolbar.width)
        assertTrue(layout.isShowing)

        layout.hide()
        root.doLayout()

        assertSame(originalLayout, root.layout)
        assertSame(idePane, (root.layout as BorderLayout).getLayoutComponent(root, BorderLayout.CENTER))
        assertSame(rightToolbar, (root.layout as BorderLayout).getLayoutComponent(root, BorderLayout.EAST))
        assertEquals(idePane.x + idePane.width, rightToolbar.x)
        assertFalse(layout.isShowing)
        assertFalse(nativeDecorator.isVisible)
    }

    @Test
    fun `show is idempotent`() {
        val idePane = JPanel()
        val rightToolbar = JPanel().apply { preferredSize = Dimension(48, 0) }
        val root = JPanel(BorderLayout()).apply {
            size = Dimension(1_200, 800)
            add(idePane, BorderLayout.CENTER)
            add(rightToolbar, BorderLayout.EAST)
        }
        val content = JPanel()
        val layout = PersistentRightToolWindowLayout(
            content = content,
            nativeToolWindowComponent = { null },
            onHide = {},
            controlsContainerLocator = { root }
        )

        assertTrue(layout.show())
        val firstHost = SwingTestHelpers.directChildOf(content, root)
        val firstLayout = root.layout
        val firstComponentCount = root.componentCount
        assertTrue(layout.show())

        assertSame(firstHost, SwingTestHelpers.directChildOf(content, root))
        assertSame(firstLayout, root.layout)
        assertEquals(firstComponentCount, root.componentCount)
    }

    private object SwingTestHelpers {
        fun directChildOf(component: java.awt.Component, ancestor: java.awt.Container): java.awt.Component? {
            var current: java.awt.Component? = component
            while (current != null) {
                if (current.parent === ancestor) return current
                current = current.parent
            }
            return null
        }
    }

    private fun BufferedImage.alphaAt(x: Int, y: Int): Int = getRGB(x, y) ushr 24 and 0xff
}
