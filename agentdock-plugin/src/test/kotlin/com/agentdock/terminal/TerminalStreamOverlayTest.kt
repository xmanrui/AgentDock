package com.agentdock.terminal

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TerminalStreamOverlayTest {
    @Test
    fun `overlay renders a fixed ticker bubble without intercepting pointer input`() {
        var now = 1_000L
        val anchor = TerminalStreamAnchor(
            centerX = 450,
            topY = 600,
            width = 320,
            height = 30
        )
        val overlay = TerminalStreamTickerOverlay(
            anchorProvider = { anchor },
            clock = { now }
        )
        overlay.setSize(900, 700)
        overlay.showText("Streaming answer from the model")

        repeat(18) {
            now += 100L
            overlay.paint(BufferedImage(900, 700, BufferedImage.TYPE_INT_ARGB).graphics)
        }

        val image = BufferedImage(900, 700, BufferedImage.TYPE_INT_ARGB)
        overlay.paint(image.graphics)
        System.getenv("AGENTDOCK_OVERLAY_SNAPSHOT")?.let { path ->
            ImageIO.write(image, "png", File(path))
        }

        assertFalse(overlay.contains(450, 300))
        assertTrue(hasVisiblePixels(image))
        overlay.dispose()
    }

    private fun hasVisiblePixels(image: BufferedImage): Boolean {
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if ((image.getRGB(x, y) ushr 24) != 0) return true
            }
        }
        return false
    }
}
