package com.agentdock.terminal

import com.intellij.util.ui.JBUI
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
    fun `gif sits between the bubble pointer and terminal title`() {
        val anchor = TerminalStreamAnchor(centerX = 450, topY = 600, width = 320, height = 30)
        val layout = TerminalStreamBubbleGeometry.layout(
            containerWidth = 900,
            anchor = anchor,
            gifSize = TerminalStreamGifSize(width = 192, height = 208)
        )

        assertTrue(layout.hasGif)
        assertEquals(JBUI.scale(128), layout.gifWidth)
        assertEquals(JBUI.scale(139), layout.gifHeight)
        assertTrue(layout.arrowTipY < layout.gifY)
        assertTrue(layout.gifY + layout.gifHeight < anchor.topY)
        assertEquals(anchor.centerX, layout.gifX + layout.gifWidth / 2)
        assertEquals(layout.gifX + layout.gifWidth / 2, layout.arrowTipX)
    }

    @Test
    fun `gif characters with different transparent padding share the same visible height`() {
        val anchor = TerminalStreamAnchor(centerX = 450, topY = 600, width = 320, height = 30)
        val narrowCharacter = TerminalStreamBubbleGeometry.layout(
            containerWidth = 900,
            anchor = anchor,
            gifSize = TerminalStreamGifSize(width = 145, height = 198)
        )
        val wideCharacter = TerminalStreamBubbleGeometry.layout(
            containerWidth = 900,
            anchor = anchor,
            gifSize = TerminalStreamGifSize(width = 182, height = 198)
        )

        assertEquals(JBUI.scale(139), narrowCharacter.gifHeight)
        assertEquals(narrowCharacter.gifHeight, wideCharacter.gifHeight)
        assertEquals(anchor.centerX, narrowCharacter.gifX + narrowCharacter.gifWidth / 2)
        assertEquals(anchor.centerX, wideCharacter.gifX + wideCharacter.gifWidth / 2)
    }

    @Test
    fun `narrow terminal titles do not shrink their gif character`() {
        val narrowTitle = TerminalStreamBubbleGeometry.layout(
            containerWidth = 900,
            anchor = TerminalStreamAnchor(centerX = 250, topY = 600, width = 96, height = 30),
            gifSize = TerminalStreamGifSize(width = 182, height = 198)
        )
        val wideTitle = TerminalStreamBubbleGeometry.layout(
            containerWidth = 900,
            anchor = TerminalStreamAnchor(centerX = 650, topY = 600, width = 320, height = 30),
            gifSize = TerminalStreamGifSize(width = 182, height = 198)
        )

        assertEquals(JBUI.scale(139), narrowTitle.gifHeight)
        assertEquals(wideTitle.gifWidth, narrowTitle.gifWidth)
        assertEquals(wideTitle.gifHeight, narrowTitle.gifHeight)
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
