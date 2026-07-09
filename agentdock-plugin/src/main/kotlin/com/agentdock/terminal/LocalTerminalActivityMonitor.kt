package com.agentdock.terminal

import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.concurrency.AppExecutorUtil
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

internal class LocalTerminalActivityMonitor(
    private val source: TerminalActivitySource,
    private val onEvent: (TerminalActivityEvent) -> Unit,
    private val dispatch: (() -> Unit) -> Unit = { action ->
        ApplicationManager.getApplication().invokeLater(action)
    }
) {
    private val stopped = AtomicBoolean(false)
    private val historyFile = File(source.historyFilePath)
    private var offset = 0L
    private var future: ScheduledFuture<*>? = null

    fun start() {
        if (future != null || source.historyFilePath.isBlank()) return
        offset = historyFile.takeIf { it.isFile }?.length() ?: 0L
        future = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
            { poll() },
            POLL_INTERVAL_MS,
            POLL_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        )
    }

    fun stop() {
        if (!stopped.compareAndSet(false, true)) return
        future?.cancel(false)
        future = null
    }

    private fun poll() {
        if (stopped.get() || !historyFile.isFile) return
        runCatching { readAppendedLines() }
    }

    private fun readAppendedLines() {
        val fileLength = historyFile.length()
        if (fileLength < offset) {
            offset = 0L
        }
        if (fileLength <= offset) return

        val bytesToRead = min(fileLength - offset, MAX_READ_BYTES.toLong()).toInt()
        val bytes = ByteArray(bytesToRead)
        RandomAccessFile(historyFile, "r").use { file ->
            file.seek(offset)
            file.readFully(bytes)
        }
        val lastNewline = bytes.indexOfLast { byte -> byte == NEWLINE_BYTE }
        if (lastNewline < 0) return

        val completeBytes = bytes.copyOfRange(0, lastNewline + 1)
        offset += lastNewline + 1L
        String(completeBytes, StandardCharsets.UTF_8).lineSequence().forEach { line ->
            val event = TerminalActivityEventParser.parse(source.providerId, line) ?: return@forEach
            dispatch(event)
        }
    }

    private fun dispatch(event: TerminalActivityEvent) {
        dispatch {
            if (!stopped.get()) {
                onEvent(event)
            }
        }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 350L
        private const val MAX_READ_BYTES = 1_048_576
        private const val NEWLINE_BYTE: Byte = 0x0A
    }
}
