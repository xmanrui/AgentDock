package com.agentdock.terminal

import kotlin.test.Test
import kotlin.test.assertEquals

class TerminalTaskStateReducerTest {
    @Test
    fun `moves from idle through working and ready back to idle when viewed`() {
        var state = TerminalTaskState.Idle

        state = TerminalTaskStateReducer.reduce(state, TerminalTaskEvent.ActivityStarted)
        assertEquals(TerminalTaskState.Working, state)

        state = TerminalTaskStateReducer.reduce(state, TerminalTaskEvent.ActivityCompleted)
        assertEquals(TerminalTaskState.Ready, state)

        state = TerminalTaskStateReducer.reduce(state, TerminalTaskEvent.Viewed)
        assertEquals(TerminalTaskState.Idle, state)
    }

    @Test
    fun `new work replaces ready state and viewing does not stop active work`() {
        var state = TerminalTaskStateReducer.reduce(TerminalTaskState.Ready, TerminalTaskEvent.ActivityStarted)
        assertEquals(TerminalTaskState.Working, state)

        state = TerminalTaskStateReducer.reduce(state, TerminalTaskEvent.Viewed)
        assertEquals(TerminalTaskState.Working, state)
    }
}
