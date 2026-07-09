package com.agentdock.terminal

import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.image.BufferedImage
import javax.swing.Icon
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerminalTaskStatusIconTest {
    @Test
    fun `state changes keep a stable icon slot and ready paints a green badge`() {
        val base = SolidIcon(16, 16, Color.GRAY)
        val icon = TerminalTaskStatusIcon(base) {}
        val width = icon.iconWidth
        val height = icon.iconHeight

        icon.state = TerminalTaskState.Working
        assertEquals(width, icon.iconWidth)
        assertEquals(height, icon.iconHeight)

        icon.state = TerminalTaskState.Ready
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        icon.paintIcon(JPanel().apply { background = Color.BLACK }, image.graphics, 0, 0)
        val badge = Color(image.getRGB(width - 2, 2), true)

        assertTrue(badge.green > badge.red)
        assertTrue(badge.green > badge.blue)
        icon.dispose()
    }

    private class SolidIcon(
        private val width: Int,
        private val height: Int,
        private val color: Color
    ) : Icon {
        override fun getIconWidth(): Int = width

        override fun getIconHeight(): Int = height

        override fun paintIcon(component: Component?, graphics: Graphics, x: Int, y: Int) {
            graphics.color = color
            graphics.fillRect(x, y, width, height)
        }
    }
}
