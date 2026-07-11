package com.agentdock.terminal

import com.jediterm.terminal.model.CharBuffer
import com.jediterm.terminal.model.StyleState
import com.jediterm.terminal.model.TerminalTextBuffer
import com.jediterm.terminal.util.CharUtils
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerminalStreamOutputMonitorTest {
    @Test
    fun `reads changed terminal buffer lines while a model is working`() {
        val buffer = TerminalTextBuffer(80, 24, StyleState())
        val working = AtomicBoolean(true)
        val emitted = AtomicReference<String>()
        val latch = CountDownLatch(1)
        val monitor = TerminalStreamOutputMonitor(
            textBuffer = buffer,
            isWorking = working::get,
            onText = { text ->
                emitted.set(text)
                latch.countDown()
            },
            tracker = TerminalStreamTextTracker(emitIntervalMs = 0),
            dispatch = { action -> action() }
        )

        try {
            monitor.start()
            buffer.writeString(0, 1, CharBuffer("• Streaming answer from the model"))

            assertTrue(latch.await(2, TimeUnit.SECONDS))
            assertEquals("Streaming answer from the model", emitted.get())
        } finally {
            working.set(false)
            monitor.stop()
        }
    }

    @Test
    fun `does not emit double width continuation markers`() {
        val buffer = TerminalTextBuffer(80, 24, StyleState())
        val emitted = AtomicReference<String>()
        val latch = CountDownLatch(1)
        val monitor = TerminalStreamOutputMonitor(
            textBuffer = buffer,
            isWorking = { true },
            onText = { text ->
                emitted.set(text)
                latch.countDown()
            },
            tracker = TerminalStreamTextTracker(emitIntervalMs = 0),
            dispatch = { action -> action() }
        )

        try {
            monitor.start()
            val marker = CharUtils.DWC
            buffer.writeString(0, 1, CharBuffer("验${marker}证${marker}画${marker}面${marker}"))

            assertTrue(latch.await(2, TimeUnit.SECONDS))
            assertEquals("验证画面", emitted.get())
        } finally {
            monitor.stop()
        }
    }
}
