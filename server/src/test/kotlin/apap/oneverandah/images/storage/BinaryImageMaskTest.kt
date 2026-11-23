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
    fun `binary mask becomes transparent background and opaque person pixels`() {
        val source = BufferedImage(2, 1, BufferedImage.TYPE_INT_RGB).apply {
            setRGB(0, 0, 0xffff0000.toInt())
            setRGB(1, 0, 0xff00ff00.toInt())
        }
        val mask = BufferedImage(2, 1, BufferedImage.TYPE_INT_RGB).apply {
            setRGB(0, 0, 0xffffffff.toInt())
            setRGB(1, 0, 0xff000000.toInt())
        }
        val result = applyBinaryImageMask(source.toGeneratedImage(), mask.toGeneratedImage())
        val decoded = ImageIO.read(ByteArrayInputStream(result.bytes))

        assertEquals("image/png", result.contentType)
        assertEquals(2, decoded.width)
        assertEquals(1, decoded.height)
        assertEquals(255, decoded.getRGB(0, 0) ushr 24)
        assertEquals(0, decoded.getRGB(1, 0) ushr 24)
        assertEquals(0xffff0000.toInt(), decoded.getRGB(0, 0))
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