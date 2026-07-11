package com.agentdock.terminal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TerminalStreamTickerModelTest {
    @Test
    fun `text moves from right to left at a stable rate`() {
        val ticker = TerminalStreamTickerModel(pixelsPerSecond = 100.0)
        ticker.offer("Streaming response", viewportWidth = 200, nowMs = 1_000)

        val initial = ticker.frame(viewportWidth = 200, currentTextWidth = 120, nowMs = 1_000)!!
        val later = ticker.frame(viewportWidth = 200, currentTextWidth = 120, nowMs = 1_100)!!

        assertEquals("Streaming response", later.text)
        assertTrue(later.x < initial.x)
        assertEquals(10.0, initial.x - later.x, 0.0001)
    }

    @Test
    fun `growing stream updates in place and unrelated messages queue`() {
        val ticker = TerminalStreamTickerModel(pixelsPerSecond = 1_000.0)
        ticker.offer("Building", viewportWidth = 100, nowMs = 0)
        ticker.offer("Building the project", viewportWidth = 100, nowMs = 10)
        ticker.offer("Running tests", viewportWidth = 100, nowMs = 20)

        assertEquals("Building the project", ticker.currentText())
        ticker.frame(viewportWidth = 100, currentTextWidth = 40, nowMs = 120)
        val next = ticker.frame(viewportWidth = 100, currentTextWidth = 40, nowMs = 240)!!

        assertEquals("Running tests", next.text)
        assertTrue(next.x > 100)
    }

    @Test
    fun `clear removes the ticker immediately`() {
        val ticker = TerminalStreamTickerModel()
        ticker.offer("Done", viewportWidth = 200, nowMs = 0)

        ticker.clear()

        assertNull(ticker.currentText())
        assertTrue(!ticker.isActive())
    }
}
