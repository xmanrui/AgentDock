package com.agentdock.terminal

import java.awt.image.BufferedImage
import kotlin.random.Random
import javax.swing.ImageIcon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TerminalStreamGifCatalogTest {
    @Test
    fun `visible bounds ignore transparent padding across every animation frame`() {
        val first = BufferedImage(20, 24, BufferedImage.TYPE_INT_ARGB).apply {
            setRGB(5, 7, 0xFFFFFFFF.toInt())
            setRGB(8, 12, 0xFFFFFFFF.toInt())
        }
        val second = BufferedImage(20, 24, BufferedImage.TYPE_INT_ARGB).apply {
            setRGB(3, 9, 0xFFFFFFFF.toInt())
            setRGB(10, 18, 0xFFFFFFFF.toInt())
        }

        assertEquals(
            TerminalStreamGifBounds(x = 3, y = 7, width = 8, height = 12),
            TerminalStreamGifContentBounds.detect(
                frames = listOf(first, second),
                canvasSize = TerminalStreamGifSize(width = 20, height = 24)
            )
        )
    }

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
            assertTrue(selection.visibleBounds.width > 0)
            assertTrue(selection.visibleBounds.height > 0)
            assertTrue(selection.visibleBounds.height < selection.icon.iconHeight)
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
    fun `first gif in each active group is ikun jj running right and later gifs remain random`() {
        val expectedFirstPath = "/images/gifs/ikun-jj-running-right.gif"
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
