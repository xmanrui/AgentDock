package com.agentdock.ui

import java.awt.Dimension
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoundedPopupWindowSupportTest {
    @Test
    fun `clips all native window corners while preserving the rounded surface edges`() {
        val shape = RoundedPopupWindowSupport.roundedShape(Dimension(440, 500), cornerRadius = 8)

        assertFalse(shape.contains(0.0, 0.0))
        assertFalse(shape.contains(439.0, 0.0))
        assertFalse(shape.contains(0.0, 499.0))
        assertFalse(shape.contains(439.0, 499.0))
        assertTrue(shape.contains(220.0, 1.0))
        assertTrue(shape.contains(1.0, 250.0))
    }
}
