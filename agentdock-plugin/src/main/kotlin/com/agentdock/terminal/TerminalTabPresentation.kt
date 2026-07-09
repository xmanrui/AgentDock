package com.agentdock.terminal

data class TerminalTabPresentation(
    val title: String,
    val providerId: String? = null,
    val activitySource: TerminalActivitySource? = null,
    val onClosed: (() -> Unit)? = null
)

data class TerminalActivitySource(
    val providerId: String,
    val historyFilePath: String
)
