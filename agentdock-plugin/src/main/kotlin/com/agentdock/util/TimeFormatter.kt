package com.agentdock.util

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object TimeFormatter {
    private val absoluteFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())

    fun relative(timestampMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        if (timestampMillis <= 0) return "-"
        val duration = Duration.between(Instant.ofEpochMilli(timestampMillis), Instant.ofEpochMilli(nowMillis))
        val seconds = duration.seconds.coerceAtLeast(0)
        return when {
            seconds < 60 -> "刚刚"
            seconds < 3600 -> "${seconds / 60} 分钟前"
            seconds < 86400 -> "${seconds / 3600} 小时前"
            seconds < 604800 -> "${seconds / 86400} 天前"
            else -> absoluteFormatter.format(Instant.ofEpochMilli(timestampMillis))
        }
    }
}
