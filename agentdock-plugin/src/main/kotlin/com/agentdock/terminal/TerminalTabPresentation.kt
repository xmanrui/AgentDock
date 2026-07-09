package com.agentdock.terminal

data class TerminalTabPresentation(
    val title: String,
    val providerId: String? = null,
    val onClosed: (() -> Unit)? = null
)
