package com.agentdock.ui

import com.agentdock.model.ProviderUsageSnapshot
import com.agentdock.model.ProviderUsageWindow
import java.awt.Color
import java.awt.Component
import java.awt.Container
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProviderUsagePopupContentTest {
    @Test
    fun `merges project token total into the usage header`() {
        val content = ProviderUsagePopup(JPanel()).createAvailableContent(
            usage = ProviderUsageSnapshot(
                providerId = "codex",
                providerName = "Codex",
                status = ProviderUsageSnapshot.STATUS_AVAILABLE,
                fiveHour = ProviderUsageWindow(usedPercent = 25),
                weekly = ProviderUsageWindow(usedPercent = 15, resetsAtEpochSeconds = 0),
                resetCount = 2
            ),
            projectTokenTotal = 27_800_000L
        )

        val labels = content.descendantLabels()
        val texts = labels.map(JLabel::getText)
        val headerTexts = (content as Container).components.first().descendantLabels().map(JLabel::getText)

        assertEquals(3, content.componentCount)
        assertTrue("Usage" in headerTexts)
        assertTrue("27.8M tokens" in headerTexts)
        assertTrue("2 resets" in headerTexts)
        assertTrue("Project tokens" !in texts)
        val tokenLabel = labels.single { it.text == "27.8M tokens" }
        val resetBadge = labels.single { it.text == "2 resets" }
        assertEquals("27,800,000 tokens", tokenLabel.toolTipText)
        assertEquals(resetBadge.foreground, tokenLabel.foreground)
        assertEquals(resetBadge.background, tokenLabel.background)
        assertEquals(resetBadge.isOpaque, tokenLabel.isOpaque)
        assertEquals(resetBadge.border.getBorderInsets(resetBadge), tokenLabel.border.getBorderInsets(tokenLabel))
        assertEquals(resetBadge.javaClass, tokenLabel.javaClass)
        assertEquals(Color(0xd9, 0xf4, 0xe4), tokenLabel.background)
        assertEquals(Color(0x00, 0x6a, 0x2b), tokenLabel.foreground)
        assertTrue(!tokenLabel.isOpaque)
        val resetLabel = labels.single { it.toolTipText?.startsWith("Resets ") == true }
        assertTrue(!resetLabel.text.startsWith("Resets "))
        assertEquals("Resets ${resetLabel.text}", resetLabel.toolTipText)
    }

    private fun Component.descendantLabels(): List<JLabel> {
        return buildList {
            if (this@descendantLabels is JLabel) add(this@descendantLabels)
            if (this@descendantLabels is Container) {
                this@descendantLabels.components.forEach { child ->
                    addAll(child.descendantLabels())
                }
            }
        }
    }
}
