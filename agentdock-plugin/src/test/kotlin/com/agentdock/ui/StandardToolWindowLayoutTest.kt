package com.agentdock.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class StandardToolWindowLayoutTest {
    @Test
    fun `enable turns off wide screen tool window layout`() {
        val settings = FakeUiSettingsAccess(initialWideScreenSupport = true)
        val layout = StandardToolWindowLayout { settings }

        layout.enable()

        assertFalse(settings.wideScreenSupport)
        assertEquals(1, settings.fireChangedCount)
    }

    @Test
    fun `enable keeps standard layout after repeated calls`() {
        val settings = FakeUiSettingsAccess(initialWideScreenSupport = true)
        val layout = StandardToolWindowLayout { settings }

        layout.enable()
        layout.enable()

        assertFalse(settings.wideScreenSupport)
        assertEquals(1, settings.fireChangedCount)
    }

    @Test
    fun `enable does not change an already standard layout`() {
        val settings = FakeUiSettingsAccess(initialWideScreenSupport = false)
        val layout = StandardToolWindowLayout { settings }

        layout.enable()

        assertFalse(settings.wideScreenSupport)
        assertEquals(0, settings.fireChangedCount)
    }

    private class FakeUiSettingsAccess(
        initialWideScreenSupport: Boolean
    ) : UiSettingsAccess {
        override var wideScreenSupport: Boolean = initialWideScreenSupport
        var fireChangedCount: Int = 0

        override fun fireChanged() {
            fireChangedCount += 1
        }
    }
}
