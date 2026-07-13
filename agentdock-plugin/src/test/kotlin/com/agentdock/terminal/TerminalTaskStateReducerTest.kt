package com.agentdock.terminal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun `overlapping codex turns stay working until the last turn completes`() {
        val tracker = TerminalTaskActivityTracker()
        val firstTurn = "019f5af2-60c4-79e1-a63c-a5c29d94e07d"
        val secondTurn = "019f5af3-0ee9-7dc0-8624-39e1bb5a58b1"

        assertEquals(
            TerminalTaskEvent.ActivityStarted,
            tracker.accept(TerminalActivityEvent.Started(firstTurn))
        )
        assertNull(tracker.accept(TerminalActivityEvent.Started(secondTurn)))
        assertNull(tracker.accept(TerminalActivityEvent.Completed(firstTurn)))
        assertTrue(tracker.isWorking())
        assertEquals(
            TerminalTaskEvent.ActivityCompleted,
            tracker.accept(TerminalActivityEvent.Completed(secondTurn))
        )
        assertFalse(tracker.isWorking())
    }

    @Test
    fun `completion for an unseen turn does not stop the active turn`() {
        val tracker = TerminalTaskActivityTracker()

        tracker.accept(TerminalActivityEvent.Started("active-turn"))

        assertNull(tracker.accept(TerminalActivityEvent.Completed("older-turn")))
        assertTrue(tracker.isWorking())
    }

    @Test
    fun `providers without turn ids keep their latest event behavior`() {
        val tracker = TerminalTaskActivityTracker()

        assertEquals(TerminalTaskEvent.ActivityStarted, tracker.accept(TerminalActivityEvent.Started()))
        assertNull(tracker.accept(TerminalActivityEvent.Started()))
        assertEquals(TerminalTaskEvent.ActivityCompleted, tracker.accept(TerminalActivityEvent.Completed()))
        assertFalse(tracker.isWorking())
    }

    @Test
    fun `anonymous completion does not stop a codex turn with an id`() {
        val tracker = TerminalTaskActivityTracker()

        tracker.accept(TerminalActivityEvent.Started("codex-turn"))

        assertNull(tracker.accept(TerminalActivityEvent.Completed()))
        assertTrue(tracker.isWorking())
    }
}
