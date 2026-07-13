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

internal class TerminalTaskActivityTracker {
    private val activeTurnIds = mutableSetOf<String>()
    private var anonymousActivityActive = false

    fun accept(event: TerminalActivityEvent): TerminalTaskEvent? {
        val wasWorking = isWorking()
        when (event) {
            is TerminalActivityEvent.Started -> start(event.turnId)
            is TerminalActivityEvent.Completed -> complete(event.turnId)
        }
        val working = isWorking()
        return when {
            !wasWorking && working -> TerminalTaskEvent.ActivityStarted
            wasWorking && !working -> TerminalTaskEvent.ActivityCompleted
            else -> null
        }
    }

    fun isWorking(): Boolean = activeTurnIds.isNotEmpty() || anonymousActivityActive

    private fun start(turnId: String?) {
        if (turnId == null) {
            anonymousActivityActive = true
        } else {
            activeTurnIds += turnId
        }
    }

    private fun complete(turnId: String?) {
        if (turnId == null) {
            anonymousActivityActive = false
        } else {
            activeTurnIds -= turnId
        }
    }
}
