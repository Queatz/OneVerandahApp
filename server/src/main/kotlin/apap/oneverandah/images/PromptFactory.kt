package apap.oneverandah.images

import apap.oneverandah.model.Category
import apap.oneverandah.model.Service

/**
 * Builds the text prompts sent to the image generator.
 *
 * English catalog names are used so the visual style stays consistent
 * regardless of the viewer's UI language (the generated cover is language
 * agnostic).
 */
object PromptFactory {
    private const val ART_STYLE =
        "expressive storybook art style, warm inviting palette, soft natural lighting, " +
            "clear composition, gentle everyday realism with a touch of whimsy"

    fun forCategory(category: Category): String =
        "Expressive storybook art style illustration representing the category " +
            "'${category.title.en}' for a premium lifestyle services marketplace. " +
            "$ART_STYLE, no text, no letters, no watermark, high quality square image."

    fun forService(service: Service, categoryName: String): String =
        "Expressive storybook art style illustration representing the service " +
            "'${service.title.en}' in the category '$categoryName' for a premium " +
            "lifestyle services marketplace. $ART_STYLE, no text, no letters, " +
            "no watermark, high quality square image."
}
