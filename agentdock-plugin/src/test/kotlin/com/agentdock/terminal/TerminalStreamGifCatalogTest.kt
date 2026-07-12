package com.agentdock.terminal

import kotlin.random.Random
import javax.swing.ImageIcon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TerminalStreamGifCatalogTest {
    @Test
    fun `catalog discovers gif resources and loads a random animation`() {
        val paths = TerminalStreamGifCatalog.resourcePaths()

        assertTrue(paths.isNotEmpty())
        assertTrue(paths.all { it.startsWith("/images/gifs/") && it.endsWith(".gif") })
        paths.forEach { path ->
            val resource = assertNotNull(TerminalStreamGifCatalog::class.java.getResource(path))
            val directIcon = ImageIcon(resource)
            assertTrue(
                directIcon.iconWidth > 0,
                "$path failed with GIF load status ${directIcon.imageLoadStatus}"
            )
        }
        val selection = assertNotNull(TerminalStreamGifCatalog.acquire(Random(7)))
        try {
            assertTrue(selection.icon.iconWidth > 0)
            assertTrue(selection.icon.iconHeight > 0)
        } finally {
            TerminalStreamGifCatalog.release(selection)
        }
    }

    @Test
    fun `concurrent overlays receive different gifs while unused resources remain`() {
        val selectionCount = minOf(3, TerminalStreamGifCatalog.resourcePaths().size)
        val selections = (0 until selectionCount).map { index ->
            assertNotNull(TerminalStreamGifCatalog.acquire(Random(index)))
        }

        try {
            assertEquals(selectionCount, selections.map { it.resourcePath }.distinct().size)
        } finally {
            selections.forEach(TerminalStreamGifCatalog::release)
        }
    }

    @Test
    fun `first gif in each active group is running right and later gifs remain random`() {
        val expectedFirstPath = "/images/gifs/basketball-kunkun-running-right.gif"
        val first = assertNotNull(TerminalStreamGifCatalog.acquire(Random(11)))
        val second = assertNotNull(TerminalStreamGifCatalog.acquire(Random(11)))

        try {
            assertEquals(expectedFirstPath, first.resourcePath)
            assertTrue(second.resourcePath != expectedFirstPath)
        } finally {
            TerminalStreamGifCatalog.release(first)
            TerminalStreamGifCatalog.release(second)
        }

        val nextGroupFirst = assertNotNull(TerminalStreamGifCatalog.acquire(Random(29)))
        try {
            assertEquals(expectedFirstPath, nextGroupFirst.resourcePath)
        } finally {
            TerminalStreamGifCatalog.release(nextGroupFirst)
        }
    }
}
