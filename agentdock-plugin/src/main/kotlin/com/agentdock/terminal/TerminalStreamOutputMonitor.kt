package com.agentdock.terminal

import com.jediterm.terminal.model.TerminalLine
import com.jediterm.terminal.model.TerminalTextBuffer
import com.jediterm.terminal.model.TextBufferChangesListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal class TerminalStreamOutputMonitor(
    private val textBuffer: TerminalTextBuffer,
    private val isWorking: () -> Boolean,
    private val onText: (String) -> Unit,
    private val tracker: TerminalStreamTextTracker = TerminalStreamTextTracker(),
    private val dispatch: (() -> Unit) -> Unit = { action ->
        ApplicationManager.getApplication().invokeLater(action)
    }
) : TextBufferChangesListener {
    private val stopped = AtomicBoolean(false)
    private val dirty = AtomicBoolean(true)
    private var lastSnapshot = emptyList<String>()
    private var future: ScheduledFuture<*>? = null

    fun start() {
        if (future != null || stopped.get()) return
        textBuffer.addChangesListener(this)
        future = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
            { poll() },
            POLL_INTERVAL_MS,
            POLL_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        )
    }

    fun stop() {
        if (!stopped.compareAndSet(false, true)) return
        textBuffer.removeChangesListener(this)
        future?.cancel(false)
        future = null
    }

    override fun linesChanged(fromIndex: Int) {
        dirty.set(true)
    }

    override fun linesDiscardedFromHistory(lines: List<TerminalLine>) = Unit

    override fun historyCleared() {
        dirty.set(true)
    }

    override fun widthResized() {
        dirty.set(true)
    }

    private fun poll() {
        if (stopped.get()) return
        val working = isWorking()
        val shouldRead = dirty.getAndSet(false)
        if (!shouldRead && !working && !tracker.hasPendingText()) return

        if (shouldRead) {
            val snapshot = snapshotLines()
            if (snapshot == null) {
                dirty.set(true)
                return
            }
            lastSnapshot = snapshot
        }
        val text = tracker.update(lastSnapshot, working, System.currentTimeMillis()) ?: return
        dispatch {
            if (!stopped.get() && isWorking()) {
                onText(text)
            }
        }
    }

    private fun snapshotLines(): List<String>? {
        return runCatching {
            textBuffer.lock()
            try {
                List(textBuffer.screenLinesCount) { index -> textBuffer.getLine(index).text }
            } finally {
                textBuffer.unlock()
            }
        }.getOrNull()
    }

    companion object {
        private const val POLL_INTERVAL_MS = 160L
    }
}
