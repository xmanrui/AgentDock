package com.agentdock.ui

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class AgentDockPluginXmlTest {
    @Test
    fun `registers AgentDock as the primary right tool window`() {
        val xml = checkNotNull(javaClass.classLoader.getResource("META-INF/plugin.xml")).readText()

        assertContains(xml, "id=\"AgentDock\"")
        assertContains(xml, "anchor=\"right\"")
        assertFalse(xml.contains("side=\"true\""))
    }
}
