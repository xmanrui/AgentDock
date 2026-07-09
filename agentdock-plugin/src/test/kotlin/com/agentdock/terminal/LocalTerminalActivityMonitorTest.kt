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
}
