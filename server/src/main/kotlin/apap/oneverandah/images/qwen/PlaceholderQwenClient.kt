package apap.oneverandah.images.qwen

import apap.oneverandah.images.storage.GeneratedImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Color
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.abs

/**
 * Renders a deterministic, good-looking gradient placeholder image entirely
 * offline using the JDK's 2D graphics. Used automatically when no Qwen API key
 * is configured, so categories/services still get distinct, stable covers and
 * the whole system works end-to-end without any external services.
 *
 * The colour is derived from the prompt hash, so the same target always gets
 * the same cover across restarts.
 */
class PlaceholderQwenClient(
    private val size: Int = 1024,
) : QwenClient {

    override val kind: String = "placeholder"

    override suspend fun generate(
        prompt: String,
        referenceImageUrls: List<String>,
    ): GeneratedImage = withContext(Dispatchers.IO) {
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            val hue = (abs(prompt.hashCode()) % 360) / 360f
            val top = Color.getHSBColor(hue, 0.45f, 0.95f)
            val bottom = Color.getHSBColor((hue + 0.08f) % 1f, 0.55f, 0.70f)
            g.paint = GradientPaint(0f, 0f, top, size.toFloat(), size.toFloat(), bottom)
            g.fillRect(0, 0, size, size)

            // A soft translucent circle to add a little depth.
            val accent = Color.getHSBColor((hue + 0.5f) % 1f, 0.35f, 1.0f)
            g.color = Color(accent.red, accent.green, accent.blue, 60)
            val r = size / 2
            g.fillOval(size / 2, -r / 2, r, r)
            g.fillOval(-r / 3, size / 2, r, r)
        } finally {
            g.dispose()
        }

        val out = ByteArrayOutputStream()
        ImageIO.write(image, "png", out)
        GeneratedImage(bytes = out.toByteArray(), contentType = "image/png", extension = "png")
    }
}
