package com.agentdock.service

import com.agentdock.model.CLIProvider
import com.agentdock.model.ProviderUsageSnapshot
import java.util.concurrent.ConcurrentHashMap

internal fun interface ProviderUsageSource {
    fun load(provider: CLIProvider): ProviderUsageSnapshot
}

internal class ProviderUsageService(
    private val sources: Map<String, ProviderUsageSource> = mapOf(
        CLIProvider.CODEX_ID to CodexProviderUsageSource(),
        CLIProvider.CLAUDE_CODE_ID to ClaudeProviderUsageSource()
    ),
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val cacheTtlMillis: Long = DEFAULT_CACHE_TTL_MILLIS
) {
    private data class CacheEntry(
        val snapshot: ProviderUsageSnapshot,
        val loadedAtMillis: Long
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val providerLocks = ConcurrentHashMap<String, Any>()

    fun load(provider: CLIProvider): ProviderUsageSnapshot {
        cached(provider.id)?.let { return it }
        val lock = providerLocks.computeIfAbsent(provider.id) { Any() }
        return synchronized(lock) {
            cached(provider.id)?.let { return@synchronized it }
            val source = sources[provider.id]
            val snapshot = if (source == null) {
                ProviderUsageSnapshot.unavailable(provider, "Usage limits are not supported for this provider.")
            } else {
                runCatching { source.load(provider) }
                    .getOrElse {
                        ProviderUsageSnapshot.unavailable(provider, "Could not load usage limits right now.")
                    }
            }
            cache[provider.id] = CacheEntry(snapshot, nowMillis())
            snapshot
        }
    }

    fun invalidate(providerId: String? = null) {
        if (providerId == null) {
            cache.clear()
        } else {
            cache.remove(providerId)
        }
    }

    private fun cached(providerId: String): ProviderUsageSnapshot? {
        val entry = cache[providerId] ?: return null
        if (nowMillis() - entry.loadedAtMillis <= cacheTtlMillis) {
            return entry.snapshot
        }
        cache.remove(providerId, entry)
        return null
    }

    companion object {
        private const val DEFAULT_CACHE_TTL_MILLIS = 120_000L
    }
}
