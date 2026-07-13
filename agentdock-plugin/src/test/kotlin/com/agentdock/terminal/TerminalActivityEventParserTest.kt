package com.agentdock.terminal

import com.agentdock.model.CLIProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TerminalActivityEventParserTest {
    @Test
    fun `parses Codex task lifecycle events`() {
        val turnId = "019f5af2-60c4-79e1-a63c-a5c29d94e07d"
        assertEquals(
            TerminalActivityEvent.Started(turnId),
            TerminalActivityEventParser.parse(
                CLIProvider.CODEX_ID,
                """{"type":"event_msg","payload":{"type":"task_started","turn_id":"$turnId"}}"""
            )
        )
        assertEquals(
            TerminalActivityEvent.Completed(turnId),
            TerminalActivityEventParser.parse(
                CLIProvider.CODEX_ID,
                """{"type":"event_msg","payload":{"type":"task_complete","turn_id":"$turnId"}}"""
            )
        )
        assertEquals(
            TerminalActivityEvent.Completed(turnId),
            TerminalActivityEventParser.parse(
                CLIProvider.CODEX_ID,
                """{"type":"event_msg","payload":{"type":"turn_aborted","turn_id":"$turnId"}}"""
            )
        )
        assertNull(
            TerminalActivityEventParser.parse(
                CLIProvider.CODEX_ID,
                """{"type":"response_item","payload":{"type":"message","role":"assistant"}}"""
            )
        )
    }

    @Test
    fun `parses Claude Code prompt and completion events`() {
        assertEquals(
            TerminalActivityEvent.Started(),
            TerminalActivityEventParser.parse(
                CLIProvider.CLAUDE_CODE_ID,
                """{"type":"user","message":{"role":"user","content":"Implement the feature"}}"""
            )
        )
        assertEquals(
            TerminalActivityEvent.Completed(),
            TerminalActivityEventParser.parse(
                CLIProvider.CLAUDE_CODE_ID,
                """{"type":"system","subtype":"turn_duration","durationMs":1200}"""
            )
        )
    }

    @Test
    fun `ignores Claude Code tool results and metadata`() {
        assertNull(
            TerminalActivityEventParser.parse(
                CLIProvider.CLAUDE_CODE_ID,
                """{"type":"user","message":{"role":"user","content":[{"type":"tool_result","content":"done"}]}}"""
            )
        )
        assertNull(
            TerminalActivityEventParser.parse(
                CLIProvider.CLAUDE_CODE_ID,
                """{"type":"user","isMeta":true,"message":{"role":"user","content":"metadata"}}"""
            )
        )
    }
}
