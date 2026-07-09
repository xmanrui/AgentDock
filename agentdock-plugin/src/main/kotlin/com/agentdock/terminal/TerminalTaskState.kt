package com.agentdock.terminal

enum class TerminalTaskState(val label: String) {
    Idle("Idle"),
    Working("Working"),
    Ready("Ready for review")
}

internal enum class TerminalTaskEvent {
    ActivityStarted,
    ActivityCompleted,
    Viewed
}

internal object TerminalTaskStateReducer {
    fun reduce(state: TerminalTaskState, event: TerminalTaskEvent): TerminalTaskState {
        return when (event) {
            TerminalTaskEvent.ActivityStarted -> TerminalTaskState.Working
            TerminalTaskEvent.ActivityCompleted -> TerminalTaskState.Ready
            TerminalTaskEvent.Viewed -> if (state == TerminalTaskState.Ready) TerminalTaskState.Idle else state
        }
    }
}
