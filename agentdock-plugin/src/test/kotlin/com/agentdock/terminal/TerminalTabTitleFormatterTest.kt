package com.agentdock.terminal

import kotlin.test.Test
import kotlin.test.assertEquals

class TerminalTabTitleFormatterTest {
    @Test
    fun `keeps titles up to five characters`() {
        assertEquals("东京天气好", TerminalTabTitleFormatter.displayTitle("东京天气好"))
    }

    @Test
    fun `truncates long chinese titles to five characters plus dots`() {
        assertEquals("保留全局案...", TerminalTabTitleFormatter.displayTitle("保留全局案例库的话多用户怎么区分呢?"))
    }

    @Test
    fun `keeps full title available for tooltips`() {
        assertEquals(
            "保留全局案例库的话多用户怎么区分呢?",
            TerminalTabTitleFormatter.fullTitle(" 保留全局案例库的话多用户怎么区分呢? ")
        )
    }

    @Test
    fun `truncates long ascii titles to five characters plus dots`() {
        assertEquals("abcde...", TerminalTabTitleFormatter.displayTitle("abcdefg"))
    }

    @Test
    fun `does not split surrogate pair characters`() {
        assertEquals("AB😀CD...", TerminalTabTitleFormatter.displayTitle("AB😀CDEF"))
    }

    @Test
    fun `uses fallback for blank titles`() {
        assertEquals("Agent", TerminalTabTitleFormatter.displayTitle("   "))
    }
}
