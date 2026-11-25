package apap.oneverandah.images.storage

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BinaryImageMaskTest {
    @Test
    fun `one pixel foreground masks are rejected after erosion`() {
        val source = BufferedImage(2, 1, BufferedImage.TYPE_INT_RGB)
        val mask = BufferedImage(2, 1, BufferedImage.TYPE_INT_RGB).apply {
            setRGB(0, 0, 0xffffffff.toInt())
            setRGB(1, 0, 0xff000000.toInt())
        }

        assertFailsWith<IllegalArgumentException> {
            applyBinaryImageMask(source.toGeneratedImage(), mask.toGeneratedImage())
        }
    }

    @Test
    fun `erosion shrinks foreground by one pixel`() {
        val source = BufferedImage(5, 5, BufferedImage.TYPE_INT_RGB).apply {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    setRGB(x, y, 0xffff0000.toInt())
                }
            }
        }
        val mask = BufferedImage(5, 5, BufferedImage.TYPE_INT_RGB).apply {
            for (y in 1..3) {
                for (x in 1..3) {
                    setRGB(x, y, 0xffffffff.toInt())
                }
            }
        }
        val result = applyBinaryImageMask(source.toGeneratedImage(), mask.toGeneratedImage())
        val decoded = ImageIO.read(ByteArrayInputStream(result.bytes))

        assertEquals("image/png", result.contentType)
        assertEquals(5, decoded.width)
        assertEquals(5, decoded.height)
        for (y in 0 until 5) {
            for (x in 0 until 5) {
                assertEquals(if (x == 2 && y == 2) 255 else 0, decoded.getRGB(x, y) ushr 24)
            }
        }
        assertEquals(0xffff0000.toInt(), decoded.getRGB(2, 2))
    }

    @Test
    fun `empty foreground masks are rejected`() {
        val source = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)
        val emptyMask = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).apply {
            setRGB(0, 0, 0xff000000.toInt())
        }

        assertFailsWith<IllegalArgumentException> {
            applyBinaryImageMask(source.toGeneratedImage(), emptyMask.toGeneratedImage())
        }
    }

    private fun BufferedImage.toGeneratedImage(): GeneratedImage {
        val bytes = ByteArrayOutputStream().use { output ->
            check(ImageIO.write(this, "png", output))
            output.toByteArray()
        }
        return GeneratedImage(bytes, "image/png", "png")
    }
}