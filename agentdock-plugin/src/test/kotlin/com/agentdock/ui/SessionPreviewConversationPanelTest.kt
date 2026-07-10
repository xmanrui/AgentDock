package com.agentdock.ui

import com.agentdock.service.SessionContentMessage
import com.agentdock.service.SessionContentPreview
import com.agentdock.service.SessionContentRole
import com.intellij.openapi.util.IconLoader
import java.awt.Component
import java.awt.Container
import javax.swing.JTextArea
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionPreviewConversationPanelTest {
    @Test
    fun `renders user questions on the right and assistant replies on the left`() {
        val userText = "Inspect <script>alert('x')</script>\nnext line"
        val assistantText = "Done & verified"
        val panel = SessionPreviewConversationPanel(
            providerName = "Codex",
            providerIcon = IconLoader.getIcon("/icons/codex.svg", SessionPreviewConversationPanelTest::class.java),
            preview = SessionContentPreview(
                messages = listOf(
                    SessionContentMessage(SessionContentRole.User, userText),
                    SessionContentMessage(SessionContentRole.Assistant, assistantText)
                ),
                omittedMessageCount = 3,
                notice = "Local preview only"
            ),
            contentWidth = 412
        )

        assertEquals(listOf(SessionMessageSide.User, SessionMessageSide.Assistant), panel.messageRows.map { it.side })
        assertEquals(listOf(userText, assistantText), panel.messageRows.map { it.messageText })
        assertEquals("Your question", panel.messageRows[0].roleLabel)
        assertEquals("Codex reply", panel.messageRows[1].roleLabel)
        assertFalse(panel.messageRows[0].showsProviderAvatar)
        assertTrue(panel.messageRows[0].showsUserAvatar)
        assertTrue(panel.messageRows[1].showsProviderAvatar)
        assertFalse(panel.messageRows[1].showsUserAvatar)

        val renderedTexts = panel.descendants().filterIsInstance<JTextArea>().map { it.text }
        assertEquals(listOf(userText, assistantText), renderedTexts)
        assertEquals(listOf("3 earlier messages omitted", "Local preview only"), panel.statusMessages)
    }

    private fun Component.descendants(): List<Component> {
        return buildList {
            add(this@descendants)
            if (this@descendants is Container) {
                this@descendants.components.forEach { child -> addAll(child.descendants()) }
            }
        }
    }

}
