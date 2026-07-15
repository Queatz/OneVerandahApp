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
    fun forCategory(category: Category): String =
        "A professional, modern editorial illustration representing the category " +
            "'${category.title.en}' for a premium lifestyle services marketplace. " +
            "Clean minimal composition, soft natural lighting, warm inviting palette, " +
            "no text, high quality, 1024x1024."

    fun forService(service: Service, categoryName: String): String =
        "A professional, modern editorial illustration representing the service " +
            "'${service.title.en}' in the category '$categoryName' for a premium " +
            "lifestyle services marketplace. Clean minimal composition, soft natural " +
            "lighting, warm inviting palette, no text, high quality, 1024x1024."
}
