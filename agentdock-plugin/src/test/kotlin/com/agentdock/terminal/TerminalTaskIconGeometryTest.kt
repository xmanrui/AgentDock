package com.agentdock.terminal

import kotlin.test.Test
import kotlin.test.assertEquals

class TerminalTaskIconGeometryTest {
    @Test
    fun `working scale loops around the original logo size`() {
        assertEquals(1.0, TerminalTaskIconGeometry.workingScale(0), 0.0001)
        assertEquals(1.08, TerminalTaskIconGeometry.workingScale(300), 0.0001)
        assertEquals(1.0, TerminalTaskIconGeometry.workingScale(600), 0.0001)
        assertEquals(0.92, TerminalTaskIconGeometry.workingScale(900), 0.0001)
        assertEquals(1.0, TerminalTaskIconGeometry.workingScale(1_200), 0.0001)
    }
}
