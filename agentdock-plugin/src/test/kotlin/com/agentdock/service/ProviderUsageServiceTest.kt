package com.agentdock.service

import com.agentdock.model.CLIProvider
import com.agentdock.model.ProviderUsageSnapshot
import com.agentdock.model.ProviderUsageWindow
import kotlin.test.Test
import kotlin.test.assertEquals

class ProviderUsageServiceTest {
    @Test
    fun `caches provider usage until ttl expires or cache is invalidated`() {
        val provider = CLIProvider.defaultProviders().first { it.id == CLIProvider.CODEX_ID }
        var now = 1_000L
        var calls = 0
        val source = ProviderUsageSource {
            calls += 1
            ProviderUsageSnapshot(
                providerId = provider.id,
                providerName = provider.displayName,
                status = ProviderUsageSnapshot.STATUS_AVAILABLE,
                fiveHour = ProviderUsageWindow(usedPercent = calls)
            )
        }
        val service = ProviderUsageService(
            sources = mapOf(provider.id to source),
            nowMillis = { now },
            cacheTtlMillis = 100L
        )

        assertEquals(1, service.load(provider).fiveHour?.usedPercent)
        now = 1_100L
        assertEquals(1, service.load(provider).fiveHour?.usedPercent)
        now = 1_101L
        assertEquals(2, service.load(provider).fiveHour?.usedPercent)

        service.invalidate(provider.id)
        assertEquals(3, service.load(provider).fiveHour?.usedPercent)
        assertEquals(3, calls)
    }

    @Test
    fun `returns an unavailable snapshot for providers without a usage source`() {
        val provider = CLIProvider(id = "custom", displayName = "Custom")
        val service = ProviderUsageService(sources = emptyMap())

        val usage = service.load(provider)

        assertEquals(ProviderUsageSnapshot.STATUS_UNAVAILABLE, usage.status)
        assertEquals("Custom", usage.providerName)
    }
}
