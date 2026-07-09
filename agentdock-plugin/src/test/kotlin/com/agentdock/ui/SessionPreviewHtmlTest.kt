package com.agentdock.ui

import com.agentdock.service.SessionContentMessage
import com.agentdock.service.SessionContentPreview
import com.agentdock.service.SessionContentRole
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class SessionPreviewHtmlTest {
    @Test
    fun `renders escaped conversation roles and omitted message count`() {
        val html = SessionPreviewHtml.render(
            providerName = "Codex <Local>",
            preview = SessionContentPreview(
                messages = listOf(
                    SessionContentMessage(SessionContentRole.User, "Inspect <script>alert('x')</script>\nnext line"),
                    SessionContentMessage(SessionContentRole.Assistant, "Done & verified")
                ),
                omittedMessageCount = 3,
                notice = "Local preview only"
            )
        )

        assertContains(html, ">You</div>")
        assertContains(html, "Codex &lt;Local&gt;")
        assertContains(html, "Inspect &lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;<br>next line")
        assertContains(html, "Done &amp; verified")
        assertContains(html, "3 earlier messages omitted")
        assertContains(html, "Local preview only")
        assertFalse(html.contains("<script>alert"))
    }
}
