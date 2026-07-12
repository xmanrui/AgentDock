package com.agentdock.terminal

import com.agentdock.model.CLIProvider
import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalTerminalActivityMonitorTest {
    @Test
    fun `emits only lifecycle events appended after monitoring starts`() {
        val history = Files.createTempFile("agentdock-terminal-activity", ".jsonl").toFile()
        history.writeText("""{"type":"event_msg","payload":{"type":"task_complete"}}""" + "\n")
        val events = Collections.synchronizedList(mutableListOf<TerminalActivityEvent>())
        val latch = CountDownLatch(2)
        val monitor = LocalTerminalActivityMonitor(
            source = TerminalActivitySource(CLIProvider.CODEX_ID, history.absolutePath),
            onEvent = { event ->
                events.add(event)
                latch.countDown()
            },
            dispatch = { action -> action() }
        )

        try {
            monitor.start()
            history.appendText(
                """{"type":"event_msg","payload":{"type":"task_started"}}""" + "\n" +
                    """{"type":"event_msg","payload":{"type":"task_complete"}}""" + "\n"
            )

            assertTrue(latch.await(3, TimeUnit.SECONDS))
            assertEquals(
                listOf(TerminalActivityEvent.Started, TerminalActivityEvent.Completed),
                events.toList()
            )
        } finally {
            monitor.stop()
            history.delete()
        }
    }

    @Test
    fun `continues to completion after an oversized history line`() {
        val history = Files.createTempFile("agentdock-terminal-activity-large-line", ".jsonl").toFile()
        val events = Collections.synchronizedList(mutableListOf<TerminalActivityEvent>())
        val latch = CountDownLatch(2)
        val monitor = LocalTerminalActivityMonitor(
            source = TerminalActivitySource(CLIProvider.CODEX_ID, history.absolutePath),
            onEvent = { event ->
                events.add(event)
                latch.countDown()
            },
            dispatch = { action -> action() }
        )

        try {
            monitor.start()
            val oversizedOutput = "x".repeat(1_048_576 + 128)
            history.appendText(
                """{"type":"event_msg","payload":{"type":"task_started"}}""" + "\n" +
                    """{"type":"response_item","payload":{"type":"function_call_output","output":"$oversizedOutput"}}""" + "\n" +
                    """{"type":"event_msg","payload":{"type":"task_complete"}}""" + "\n"
            )

            assertTrue(latch.await(4, TimeUnit.SECONDS))
            assertEquals(
                listOf(TerminalActivityEvent.Started, TerminalActivityEvent.Completed),
                events.toList()
            )
        } finally {
            monitor.stop()
            history.delete()
        }
    }

    @Test
    fun `emits lifecycle events added to a Gemini session file`() {
        val history = Files.createTempFile("agentdock-gemini-activity", ".json").toFile()
        history.writeText("""{"sessionId":"test","messages":[{"type":"user","content":"existing"}]}""")
        val events = Collections.synchronizedList(mutableListOf<TerminalActivityEvent>())
        val latch = CountDownLatch(2)
        val monitor = LocalTerminalActivityMonitor(
            source = TerminalActivitySource(CLIProvider.GEMINI_ID, history.absolutePath),
            onEvent = { event ->
                events.add(event)
                latch.countDown()
            },
            dispatch = { action -> action() }
        )

        try {
            monitor.start()
            history.writeText(
                """{"sessionId":"test","messages":[{"type":"user","content":"existing"},{"type":"user","content":"new prompt"},{"type":"gemini","content":"new response"}]}"""
            )

            assertTrue(latch.await(3, TimeUnit.SECONDS))
            assertEquals(
                listOf(TerminalActivityEvent.Started, TerminalActivityEvent.Completed),
                events.toList()
            )
        } finally {
            monitor.stop()
            history.delete()
        }
    }
}
