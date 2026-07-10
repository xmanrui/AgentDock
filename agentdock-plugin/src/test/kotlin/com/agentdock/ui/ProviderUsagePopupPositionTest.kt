package com.agentdock.ui

import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProviderUsagePopupPositionTest {
    @Test
    fun `anchors popup above the hovered provider logo`() {
        val pointer = Point(1_080, 150)
        val logo = ProviderUsagePopupPosition.pointerAnchor(pointer, width = 28, height = 28)
        val popupSize = Dimension(286, 92)
        val location = ProviderUsagePopupPosition.above(
            anchor = logo,
            popupSize = popupSize,
            screen = Rectangle(0, 0, 1_440, 900),
            componentBounds = Rectangle(980, 88, 330, 680),
            gap = 8,
            margin = 8
        )

        assertEquals(pointer.y - 14, logo.y)
        assertEquals(logo.y - 8, location.y + popupSize.height)
        assertTrue(location.x >= 988)
        assertTrue(location.x + popupSize.width <= 1_302)
    }
}
