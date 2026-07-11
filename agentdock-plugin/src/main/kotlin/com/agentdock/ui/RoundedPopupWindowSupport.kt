package com.agentdock.ui

import java.awt.Color
import java.awt.Dimension
import java.awt.geom.RoundRectangle2D
import javax.swing.JComponent
import javax.swing.JWindow

internal object RoundedPopupWindowSupport {
    private val transparent = Color(0, 0, 0, 0)

    fun configure(window: JWindow, cornerRadius: Int) {
        runCatching { window.background = transparent }
        window.rootPane.apply {
            isOpaque = false
            background = transparent
        }
        window.layeredPane.apply {
            isOpaque = false
            background = transparent
        }
        (window.contentPane as? JComponent)?.apply {
            isOpaque = false
            background = transparent
        }
        runCatching { window.shape = roundedShape(window.size, cornerRadius) }
    }

    internal fun roundedShape(size: Dimension, cornerRadius: Int): RoundRectangle2D.Float {
        val diameter = cornerRadius.coerceAtLeast(0) * 2f
        return RoundRectangle2D.Float(
            0f,
            0f,
            size.width.coerceAtLeast(0).toFloat(),
            size.height.coerceAtLeast(0).toFloat(),
            diameter,
            diameter
        )
    }
}
