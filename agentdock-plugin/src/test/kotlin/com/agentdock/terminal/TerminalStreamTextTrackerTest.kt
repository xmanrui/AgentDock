package com.agentdock.terminal

import com.jediterm.terminal.util.CharUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TerminalStreamTextTrackerTest {
    @Test
    fun `emits model output while ignoring prompts and terminal status lines`() {
        val tracker = TerminalStreamTextTracker(emitIntervalMs = 500)
        tracker.update(listOf("› Refactor the parser"), working = false, nowMs = 0)

        val text = tracker.update(
            listOf(
                "› Refactor the parser",
                "✻ Thinking...",
                "• I will inspect the parser before changing it.",
                "Working (2s - esc to interrupt)"
            ),
            working = true,
            nowMs = 100
        )

        assertEquals("I will inspect the parser before changing it.", text)
    }

    @Test
    fun `throttles a growing stream and emits the latest readable line`() {
        val tracker = TerminalStreamTextTracker(
            emitIntervalMs = 500,
            minimumGrowthCharacters = 5
        )
        tracker.update(emptyList(), working = false, nowMs = 0)

        assertEquals(
            "Building",
            tracker.update(listOf("• Building"), working = true, nowMs = 100)
        )
        assertNull(
            tracker.update(listOf("• Building the"), working = true, nowMs = 200)
        )
        assertEquals(
            "Building the project",
            tracker.update(listOf("• Building the project"), working = true, nowMs = 650)
        )
    }

    @Test
    fun `preserves multilingual response text and resets between turns`() {
        val tracker = TerminalStreamTextTracker(emitIntervalMs = 500)
        tracker.update(emptyList(), working = false, nowMs = 0)

        assertEquals(
            "正在检查项目中的终端监听逻辑。",
            tracker.update(listOf("⏺ 正在检查项目中的终端监听逻辑。"), working = true, nowMs = 10)
        )
        tracker.update(listOf("\$ Ready"), working = false, nowMs = 20)

        assertEquals(
            "第二轮任务已经开始。",
            tracker.update(listOf("• 第二轮任务已经开始。"), working = true, nowMs = 30)
        )
    }

    @Test
    fun `removes terminal double width cell markers from CJK text`() {
        val marker = CharUtils.DWC
        val terminalLine = "验证${marker}画${marker}面${marker}、音${marker}轨${marker}"

        assertEquals("验证画面、音轨", TerminalStreamTextSanitizer.sanitize(terminalLine))
    }

    @Test
    fun `does not reemit lines that only moved because the terminal scrolled`() {
        val tracker = TerminalStreamTextTracker(emitIntervalMs = 0)
        tracker.update(listOf("First line", "Second line"), working = false, nowMs = 0)

        assertNull(
            tracker.update(listOf("Second line", "First line"), working = true, nowMs = 1)
        )
    }
}
