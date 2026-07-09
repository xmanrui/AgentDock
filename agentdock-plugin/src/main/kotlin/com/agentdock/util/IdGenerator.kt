package com.agentdock.util

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

object IdGenerator {
    private val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC)

    fun sessionId(nowMillis: Long = System.currentTimeMillis()): String {
        val stamp = formatter.format(Instant.ofEpochMilli(nowMillis))
        val suffix = UUID.randomUUID().toString().substring(0, 8)
        return "agd_${stamp}_$suffix"
    }
}
