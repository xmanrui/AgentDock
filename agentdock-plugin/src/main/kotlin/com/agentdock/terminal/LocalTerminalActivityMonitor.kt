package com.agentdock.terminal

import com.agentdock.model.CLIProvider
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.concurrency.AppExecutorUtil
import java.io.ByteArrayOutputStream
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
    private var geminiMessageCount = 0
    private var geminiLastModified = 0L
    private var geminiFileLength = 0L
    private val lineDecoder = BoundedUtf8LineDecoder(MAX_ACTIVITY_LINE_BYTES)
    private var future: ScheduledFuture<*>? = null

    fun start() {
        if (future != null || source.historyFilePath.isBlank()) return
        offset = historyFile.takeIf { it.isFile }?.length() ?: 0L
        if (source.providerId == CLIProvider.GEMINI_ID) {
            geminiMessageCount = readGeminiMessages()?.size() ?: 0
            geminiLastModified = historyFile.lastModified()
            geminiFileLength = historyFile.length()
        }
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
        runCatching {
            if (source.providerId == CLIProvider.GEMINI_ID) {
                readGeminiActivity()
            } else {
                readAppendedLines()
            }
        }
    }

    private fun readGeminiActivity() {
        val lastModified = historyFile.lastModified()
        val fileLength = historyFile.length()
        if (lastModified == geminiLastModified && fileLength == geminiFileLength) return

        val messages = readGeminiMessages() ?: return
        if (messages.size() < geminiMessageCount) {
            geminiMessageCount = 0
        }
        for (index in geminiMessageCount until messages.size()) {
            val message = messages[index].takeIf { it.isJsonObject }?.asJsonObject ?: continue
            when (message.string("type")) {
                "user" -> dispatch(TerminalActivityEvent.Started())
                "gemini" -> dispatch(TerminalActivityEvent.Completed())
            }
        }
        geminiMessageCount = messages.size()
        geminiLastModified = lastModified
        geminiFileLength = fileLength
    }

    private fun readGeminiMessages() = try {
        historyFile.reader().use { reader ->
            JsonParser.parseReader(reader)
                .takeIf { it.isJsonObject }
                ?.asJsonObject
                ?.let { json -> json.get("messages")?.takeIf { it.isJsonArray }?.asJsonArray }
        }
    } catch (_: Exception) {
        null
    }

    private fun JsonObject.string(name: String): String? {
        val value = get(name) ?: return null
        return if (value.isJsonPrimitive && value.asJsonPrimitive.isString) value.asString else null
    }

    private fun readAppendedLines() {
        val fileLength = historyFile.length()
        if (fileLength < offset) {
            offset = 0L
            lineDecoder.reset()
        }
        if (fileLength <= offset) return

        var bytesRemaining = min(fileLength - offset, MAX_READ_BYTES_PER_POLL.toLong()).toInt()
        val buffer = ByteArray(min(bytesRemaining, READ_BUFFER_BYTES))
        RandomAccessFile(historyFile, "r").use { file ->
            file.seek(offset)
            while (bytesRemaining > 0) {
                val bytesRead = file.read(buffer, 0, min(bytesRemaining, buffer.size))
                if (bytesRead <= 0) break
                offset += bytesRead
                bytesRemaining -= bytesRead
                lineDecoder.accept(buffer, bytesRead).forEach { line ->
                    val event = TerminalActivityEventParser.parse(source.providerId, line) ?: return@forEach
                    dispatch(event)
                }
            }
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
        private const val READ_BUFFER_BYTES = 65_536
        private const val MAX_READ_BYTES_PER_POLL = 8_388_608
        private const val MAX_ACTIVITY_LINE_BYTES = 1_048_576
    }
}

private class BoundedUtf8LineDecoder(
    private val maximumLineBytes: Int
) {
    init {
        require(maximumLineBytes > 0)
    }

    private val pending = ByteArrayOutputStream(min(maximumLineBytes, INITIAL_CAPACITY_BYTES))
    private var discardingOversizedLine = false

    fun accept(bytes: ByteArray, length: Int = bytes.size): List<String> {
        require(length in 0..bytes.size)
        if (length == 0) return emptyList()

        val lines = mutableListOf<String>()
        var segmentStart = 0
        for (index in 0 until length) {
            if (bytes[index] != NEWLINE_BYTE) continue

            if (!discardingOversizedLine) {
                append(bytes, segmentStart, index - segmentStart)
            }
            if (!discardingOversizedLine) {
                lines += String(pending.toByteArray(), StandardCharsets.UTF_8).removeSuffix("\r")
            }
            pending.reset()
            discardingOversizedLine = false
            segmentStart = index + 1
        }

        if (segmentStart < length && !discardingOversizedLine) {
            append(bytes, segmentStart, length - segmentStart)
        }
        return lines
    }

    fun reset() {
        pending.reset()
        discardingOversizedLine = false
    }

    private fun append(bytes: ByteArray, offset: Int, length: Int) {
        if (length <= 0) return
        if (pending.size() + length > maximumLineBytes) {
            // Lifecycle records are small; skip oversized tool output without blocking later events.
            pending.reset()
            discardingOversizedLine = true
            return
        }
        pending.write(bytes, offset, length)
    }

    companion object {
        private const val INITIAL_CAPACITY_BYTES = 4_096
        private const val NEWLINE_BYTE: Byte = 0x0A
    }
}
