package com.agentdock.terminal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerminalStreamBubbleGeometryTest {
    @Test
    fun `bubble stays above the terminal title with a centered downward pointer`() {
        val anchor = TerminalStreamAnchor(
            centerX = 450,
            topY = 600,
            width = 320,
            height = 30
        )
        val layout = TerminalStreamBubbleGeometry.layout(900, anchor)

        assertTrue(layout.boxWidth < anchor.width)
        assertTrue(layout.boxY + layout.boxHeight < anchor.topY)
        assertEquals(layout.boxX + layout.boxWidth / 2, layout.arrowTipX)
        assertEquals(layout.arrowTipX, anchor.centerX)
        assertTrue(layout.arrowTipY < anchor.topY)
        assertTrue(anchor.topY - layout.arrowTipY <= 5)
    }

    @Test
    fun `bubble remains inside a narrow IDE window`() {
        val anchor = TerminalStreamAnchor(
            centerX = 30,
            topY = 500,
            width = 300,
            height = 30
        )
        val layout = TerminalStreamBubbleGeometry.layout(260, anchor)

        assertTrue(layout.boxX >= 0)
        assertTrue(layout.boxX + layout.boxWidth <= 260)
        assertTrue(layout.boxWidth < anchor.width)
    }

    @Test
    fun `bubble height does not inherit extra title component height`() {
        val compact = TerminalStreamBubbleGeometry.layout(
            900,
            TerminalStreamAnchor(centerX = 450, topY = 600, width = 320, height = 30)
        )
        val oversizedTitle = TerminalStreamBubbleGeometry.layout(
            900,
            TerminalStreamAnchor(centerX = 450, topY = 600, width = 320, height = 90)
        )

        assertEquals(compact.boxHeight, oversizedTitle.boxHeight)
    }

    @Test
    fun `adjacent terminal bubbles keep a visual gap`() {
        val first = TerminalStreamBubbleGeometry.layout(
            900,
            TerminalStreamAnchor(centerX = 250, topY = 600, width = 300, height = 30)
        )
        val second = TerminalStreamBubbleGeometry.layout(
            900,
            TerminalStreamAnchor(centerX = 550, topY = 600, width = 300, height = 30)
        )

        assertTrue(second.boxX > first.boxX + first.boxWidth)
        assertEquals(first.arrowTipX, 250)
        assertEquals(second.arrowTipX, 550)
    }
}
