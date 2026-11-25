package apap.oneverandah.images.storage

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/** Applies a generated black/white segmentation mask as alpha to an image. */
internal fun applyBinaryImageMask(image: GeneratedImage, mask: GeneratedImage): GeneratedImage {
    val source = ImageIO.read(ByteArrayInputStream(image.bytes))
        ?: throw IllegalArgumentException("Generated portrait is not a supported image")
    val maskImage = ImageIO.read(ByteArrayInputStream(mask.bytes))
        ?: throw IllegalArgumentException("Generated person mask is not a supported image")
    val output = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_ARGB)
    val foreground = Array(source.height) { BooleanArray(source.width) }
    for (y in 0 until source.height) {
        for (x in 0 until source.width) {
            val maskX = (x.toLong() * maskImage.width / source.width).toInt().coerceAtMost(maskImage.width - 1)
            val maskY = (y.toLong() * maskImage.height / source.height).toInt().coerceAtMost(maskImage.height - 1)
            val maskPixel = maskImage.getRGB(maskX, maskY)
            val red = (maskPixel shr 16) and 0xff
            val green = (maskPixel shr 8) and 0xff
            val blue = maskPixel and 0xff
            foreground[y][x] = (red + green + blue) / 3 >= 128
        }
    }
    var foregroundPixels = 0
    var backgroundPixels = 0
    for (y in 0 until source.height) {
        for (x in 0 until source.width) {
            val sourcePixel = source.getRGB(x, y)
            val isPerson = (y - 1..y + 1).all { neighborY ->
                (x - 1..x + 1).all { neighborX ->
                    neighborX in 0 until source.width &&
                        neighborY in 0 until source.height &&
                        foreground[neighborY][neighborX]
                }
            }
            if (isPerson) foregroundPixels++ else backgroundPixels++
            output.setRGB(x, y, (if (isPerson) 0xff000000.toInt() else 0) or (sourcePixel and 0x00ffffff))
        }
    }
    require(foregroundPixels > 0 && backgroundPixels > 0) {
        "Generated person mask must contain both foreground and background pixels"
    }
    val bytes = ByteArrayOutputStream().use { out ->
        check(ImageIO.write(output, "png", out)) { "PNG image writer is not available" }
        out.toByteArray()
    }
    return GeneratedImage(bytes = bytes, contentType = "image/png", extension = "png")
}