package com.agentdock.service

import com.agentdock.model.AgentSession
import com.agentdock.model.CLIProvider
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap

data class SessionTokenUsage(
    val totalTokens: Long? = null,
    val dailyTokens: List<Long> = emptyList(),
    val dailyAverageResponseMillis: List<Long?> = emptyList()
)

class LocalSessionTokenUsageService(
    private val sessionContentService: LocalSessionContentService = LocalSessionContentService(),
    private val clock: Clock = Clock.systemDefaultZone()
) {
    private val usageBySource = ConcurrentHashMap<String, CachedUsage>()
    private val usageBySession = ConcurrentHashMap<String, CachedUsage>()

    fun cached(session: AgentSession): SessionTokenUsage {
        val anchorDate = LocalDate.now(clock)
        val memoryCache = usageBySession[session.id]
        if (memoryCache?.anchorDate == anchorDate) {
            return memoryCache.usage
        }
        return persistedUsage(session, anchorDate) ?: unavailableUsage()
    }

    fun cachedProviderTotal(sessions: Iterable<AgentSession>, providerId: String): Long? {
        var total = 0L
        var foundUsage = false
        sessions.forEach { session ->
            if (session.providerId != providerId) return@forEach
            val sessionTotal = cached(session).totalTokens ?: return@forEach
            foundUsage = true
            total = if (sessionTotal > Long.MAX_VALUE - total) Long.MAX_VALUE else total + sessionTotal
        }
        return total.takeIf { foundUsage }
    }

    fun load(session: AgentSession): SessionTokenUsage {
        val source = session.historyFilePath
            .takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.isFile }
            ?: sessionContentService.locateHistoryFile(session)
            ?: return cached(session)

        val anchorDate = LocalDate.now(clock)
        val sourcePath = source.absolutePath
        val cacheKey = "${session.providerId}:$sourcePath"
        val modifiedAt = source.lastModified()
        val length = source.length()
        usageBySource[cacheKey]?.let { cached ->
            if (cached.modifiedAt == modifiedAt && cached.length == length && cached.anchorDate == anchorDate) {
                usageBySession[session.id] = cached
                return cached.usage
            }
        }
        persistedUsage(session, anchorDate)?.let { persisted ->
            if (
                session.tokenUsageSourcePath == sourcePath &&
                session.tokenUsageSourceModifiedAt == modifiedAt &&
                session.tokenUsageSourceLength == length
            ) {
                val cached = CachedUsage(modifiedAt, length, anchorDate, persisted)
                usageBySource[cacheKey] = cached
                usageBySession[session.id] = cached
                return persisted
            }
        }

        val usage = runCatching {
            when (session.providerId) {
                CLIProvider.CODEX_ID -> parseCodex(source, anchorDate)
                CLIProvider.CLAUDE_CODE_ID -> parseClaudeCode(source, anchorDate)
                else -> unavailableUsage()
            }
        }.getOrElse { unavailableUsage() }
        val cached = CachedUsage(modifiedAt, length, anchorDate, usage)
        usageBySource[cacheKey] = cached
        usageBySession[session.id] = cached
        persist(session, sourcePath, cached)
        return usage
    }

    private fun parseCodex(source: File, anchorDate: LocalDate): SessionTokenUsage {
        val dailyTotals = mutableMapOf<LocalDate, Long>()
        val responseTimes = DailyResponseTimeAccumulator(clock)
        val fallbackDate = sourceDate(source, anchorDate)
        var previousCumulative: Long? = null
        var totalTokens = 0L
        var foundUsage = false

        source.useLines { lines ->
            lines.forEach { line ->
                val tokenCandidate = line.contains("\"token_count\"")
                val taskCompleteCandidate = line.contains("\"task_complete\"")
                if (!tokenCandidate && !taskCompleteCandidate) return@forEach
                val json = parseObject(line) ?: return@forEach
                val payload = json.obj("payload") ?: return@forEach
                when {
                    json.string("type") == "event_msg" && payload.string("type") == "token_count" -> {
                        val info = payload.obj("info") ?: return@forEach
                        val cumulative = info.obj("total_token_usage")?.nonNegativeLong("total_tokens")
                        val lastUsage = info.obj("last_token_usage")?.nonNegativeLong("total_tokens")
                        if (cumulative == null && lastUsage == null) return@forEach

                        foundUsage = true
                        val delta = if (cumulative != null) {
                            val previous = previousCumulative
                            previousCumulative = cumulative
                            when {
                                previous == null -> cumulative
                                cumulative >= previous -> cumulative - previous
                                else -> cumulative
                            }
                        } else {
                            lastUsage ?: 0L
                        }
                        totalTokens = totalTokens.safePlus(delta)
                        val date = json.string("timestamp")?.toLocalDate() ?: fallbackDate
                        dailyTotals[date] = dailyTotals.getOrDefault(date, 0L).safePlus(delta)
                    }
                    json.string("type") == "event_msg" && payload.string("type") == "task_complete" -> {
                        val durationMillis = payload.nonNegativeLong("duration_ms") ?: return@forEach
                        responseTimes.record(durationMillis, json.string("timestamp")?.toInstantOrNull())
                    }
                }
            }
        }

        return buildUsage(foundUsage, totalTokens, dailyTotals, responseTimes, anchorDate)
    }

    private fun parseClaudeCode(source: File, anchorDate: LocalDate): SessionTokenUsage {
        val samples = linkedMapOf<String, TokenSample>()
        val responseTimes = DailyResponseTimeAccumulator(clock)
        val fallbackDate = sourceDate(source, anchorDate)
        var anonymousIndex = 0
        var foundUsage = false

        source.useLines { lines ->
            lines.forEach { line ->
                if (!line.contains("\"assistant\"") && !line.contains("\"turn_duration\"")) return@forEach
                val json = parseObject(line) ?: return@forEach
                val type = json.string("type") ?: return@forEach
                val timestamp = json.string("timestamp")?.toInstantOrNull()
                if (type == "system" && json.string("subtype") == "turn_duration") {
                    val durationMillis = json.nonNegativeLong("durationMs") ?: return@forEach
                    responseTimes.record(durationMillis, timestamp)
                    return@forEach
                }
                if (type != "assistant") return@forEach
                val message = json.obj("message")
                val usage = message?.obj("usage") ?: json.obj("usage") ?: return@forEach
                val tokenValues = CLAUDE_TOKEN_FIELDS.mapNotNull { field -> usage.nonNegativeLong(field) }
                if (tokenValues.isEmpty()) return@forEach

                foundUsage = true
                val tokens = tokenValues.fold(0L) { total, value -> total.safePlus(value) }
                val date = json.string("timestamp")?.toLocalDate() ?: fallbackDate
                val sample = TokenSample(tokens, date)
                val messageId = message?.string("id")
                val requestId = json.string("requestId")
                val key = when {
                    !messageId.isNullOrBlank() -> "message:$messageId"
                    !requestId.isNullOrBlank() -> "request:$requestId"
                    else -> "line:${anonymousIndex++}"
                }
                val existing = samples[key]
                if (existing == null || sample.tokens > existing.tokens) {
                    samples[key] = sample
                }
            }
        }

        val dailyTotals = mutableMapOf<LocalDate, Long>()
        var totalTokens = 0L
        samples.values.forEach { sample ->
            totalTokens = totalTokens.safePlus(sample.tokens)
            dailyTotals[sample.date] = dailyTotals.getOrDefault(sample.date, 0L).safePlus(sample.tokens)
        }
        return buildUsage(foundUsage, totalTokens, dailyTotals, responseTimes, anchorDate)
    }

    private fun buildUsage(
        foundUsage: Boolean,
        totalTokens: Long,
        dailyTotals: Map<LocalDate, Long>,
        responseTimes: DailyResponseTimeAccumulator,
        anchorDate: LocalDate
    ): SessionTokenUsage {
        val firstDate = anchorDate.minusDays((DAY_COUNT - 1).toLong())
        val dailyTokens = List(DAY_COUNT) { index ->
            dailyTotals[firstDate.plusDays(index.toLong())] ?: 0L
        }
        return SessionTokenUsage(
            totalTokens = totalTokens.takeIf { foundUsage },
            dailyTokens = dailyTokens,
            dailyAverageResponseMillis = responseTimes.dailyAverages(anchorDate)
        )
    }

    private fun unavailableUsage(): SessionTokenUsage = SessionTokenUsage(
        totalTokens = null,
        dailyTokens = List(DAY_COUNT) { 0L },
        dailyAverageResponseMillis = List(DAY_COUNT) { null }
    )

    private fun persistedUsage(session: AgentSession, anchorDate: LocalDate): SessionTokenUsage? {
        if (
            !session.tokenUsageCached ||
            session.sessionMetricsCacheVersion != CACHE_VERSION ||
            session.tokenUsageAnchorEpochDay != anchorDate.toEpochDay() ||
            session.tokenUsageDaily.size != DAY_COUNT ||
            session.averageResponseTimeDailyMillis.size != DAY_COUNT
        ) {
            return null
        }
        return SessionTokenUsage(
            totalTokens = session.tokenUsageTotal.takeIf { session.tokenUsageAvailable },
            dailyTokens = session.tokenUsageDaily.toList(),
            dailyAverageResponseMillis = session.averageResponseTimeDailyMillis.map { value ->
                value.takeIf { it >= 0L }
            }
        )
    }

    private fun persist(session: AgentSession, sourcePath: String, cached: CachedUsage) {
        session.tokenUsageCached = true
        session.tokenUsageAvailable = cached.usage.totalTokens != null
        session.tokenUsageTotal = cached.usage.totalTokens ?: 0L
        session.tokenUsageDaily = cached.usage.dailyTokens.toMutableList()
        session.tokenUsageSourcePath = sourcePath
        session.tokenUsageSourceModifiedAt = cached.modifiedAt
        session.tokenUsageSourceLength = cached.length
        session.tokenUsageAnchorEpochDay = cached.anchorDate.toEpochDay()
        session.averageResponseTimeDailyMillis = cached.usage.dailyAverageResponseMillis
            .map { value -> value ?: MISSING_RESPONSE_TIME }
            .toMutableList()
        session.sessionMetricsCacheVersion = CACHE_VERSION
    }

    private fun sourceDate(source: File, fallback: LocalDate): LocalDate {
        return source.lastModified()
            .takeIf { it > 0L }
            ?.let { Instant.ofEpochMilli(it).atZone(clock.zone).toLocalDate() }
            ?: fallback
    }

    private fun parseObject(line: String): JsonObject? {
        return try {
            JsonParser.parseString(line).takeIf { it.isJsonObject }?.asJsonObject
        } catch (_: Exception) {
            null
        }
    }

    private fun JsonObject.obj(name: String): JsonObject? {
        val value = get(name) ?: return null
        return if (value.isJsonObject) value.asJsonObject else null
    }

    private fun JsonObject.string(name: String): String? {
        val value = get(name) ?: return null
        return if (value.isJsonPrimitive && value.asJsonPrimitive.isString) value.asString else null
    }

    private fun JsonObject.nonNegativeLong(name: String): Long? {
        val value = get(name) ?: return null
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isNumber) return null
        return runCatching { value.asLong }.getOrNull()?.coerceAtLeast(0L)
    }

    private fun String.toInstantOrNull(): Instant? {
        return runCatching { Instant.parse(this) }
            .recoverCatching { OffsetDateTime.parse(this).toInstant() }
            .getOrNull()
    }

    private fun String.toLocalDate(): LocalDate? = toInstantOrNull()?.atZone(clock.zone)?.toLocalDate()

    private fun Long.safePlus(value: Long): Long {
        return if (value > Long.MAX_VALUE - this) Long.MAX_VALUE else this + value
    }

    private data class TokenSample(
        val tokens: Long,
        val date: LocalDate
    )

    private data class CachedUsage(
        val modifiedAt: Long,
        val length: Long,
        val anchorDate: LocalDate,
        val usage: SessionTokenUsage
    )

    private class DailyResponseTimeAccumulator(private val clock: Clock) {
        private val dailyDurations = mutableMapOf<LocalDate, DurationAggregate>()

        fun record(durationMillis: Long, completedAt: Instant?) {
            completedAt ?: return
            val date = completedAt.atZone(clock.zone).toLocalDate()
            val aggregate = dailyDurations.getOrPut(date) { DurationAggregate() }
            aggregate.totalMillis = if (durationMillis > Long.MAX_VALUE - aggregate.totalMillis) {
                Long.MAX_VALUE
            } else {
                aggregate.totalMillis + durationMillis
            }
            aggregate.count += 1L
        }

        fun dailyAverages(anchorDate: LocalDate): List<Long?> {
            val firstDate = anchorDate.minusDays((DAY_COUNT - 1).toLong())
            return List(DAY_COUNT) { index ->
                val aggregate = dailyDurations[firstDate.plusDays(index.toLong())]
                aggregate?.takeIf { it.count > 0L }?.let { it.totalMillis / it.count }
            }
        }
    }

    private data class DurationAggregate(
        var totalMillis: Long = 0L,
        var count: Long = 0L
    )

    companion object {
        private const val CACHE_VERSION = 2
        private const val DAY_COUNT = 7
        private const val MISSING_RESPONSE_TIME = -1L
        private val CLAUDE_TOKEN_FIELDS = listOf(
            "input_tokens",
            "output_tokens",
            "cache_creation_input_tokens",
            "cache_read_input_tokens"
        )
    }
}
