package apap.oneverandah.phrases

import apap.oneverandah.model.LocalizedText
import kotlinx.serialization.Serializable

/**
 * A single Earth-Mystical Parlance starter phrase.
 *
 * [spoken] is the brisk statement residents say aloud; [verse] is the fuller
 * poetic form; [rawAsk] is the plain-language intention behind the invitation;
 * [instructions] teaches residents how to use the phrase in the wild.
 */
@Serializable
data class Phrase(
    val id: String,
    /** Stable location key used for grouping (e.g. `"infinity-pool"`). */
    val locationKey: String,
    val location: LocalizedText,
    /** Material Symbols ligature used as a location affordance. */
    val icon: String = "auto_awesome",
    val spoken: LocalizedText,
    val verse: LocalizedText,
    val rawAsk: LocalizedText,
    /** How to use this phrase gently in person. */
    val instructions: LocalizedText = PhraseDefaults.instructions,
    /** Optional dreamlike cover photo URL for the phrase card. */
    val imageUrl: String? = null,
    /** Only approved phrases appear on the public phrasebook. */
    val approved: Boolean = true,
    /** Anonymous emoji reaction counts (🌿, ✨, 🪷, 🌙, 🌺). */
    val reactions: Map<String, Int> = emptyMap(),
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

/** Shared defaults used when a phrase omits optional copy. */
object PhraseDefaults {
    val instructions = LocalizedText(
        en = "Speak this aloud as a gentle statement — never a demand or a question. Leave the other person free to answer, smile, or let the words pass.",
        vi = "Nói thành lời như một lời mời dịu dàng — không đòi hỏi, không hỏi han. Để người kia tự do đáp lại, mỉm cười, hoặc để lời trôi qua.",
        zh = "把这句话轻轻说出来——不是要求，也不是提问。让对方自由回应、微笑，或让这句话自然掠过。",
    )
}

/** Public list payload for `GET /api/v1/phrases`. */
@Serializable
data class PhrasesResponse(
    val phrases: List<Phrase> = emptyList(),
    /** Optional admin-crafted cover photo for the phrasebook screen. */
    val coverImageUrl: String? = null,
)

/** Body for creating or replacing a phrase from the admin portal. */
@Serializable
data class PhraseUpsertRequest(
    val id: String? = null,
    val locationKey: String,
    val location: LocalizedText,
    val icon: String = "auto_awesome",
    val spoken: LocalizedText,
    val verse: LocalizedText,
    val rawAsk: LocalizedText,
    val instructions: LocalizedText = PhraseDefaults.instructions,
    val imageUrl: String? = null,
    val approved: Boolean = true,
)

/** Body for `POST /api/v1/phrases/{id}/react`. */
@Serializable
data class PhraseReactRequest(
    val emoji: String,
)

/** Named reference photo used with Qwen R2I scene crafting. */
@Serializable
data class ReferencePhoto(
    val id: String,
    /** Human-chosen label (e.g. `"infinity-pool-dusk"`). */
    val name: String,
    val imageUrl: String,
    val createdAt: Long = 0,
)

@Serializable
data class ReferencePhotosResponse(
    val photos: List<ReferencePhoto> = emptyList(),
)

/**
 * Admin request to craft a scene with Qwen R2I from named apartment references.
 *
 * When [phraseId] is set, the generated URL is written onto that phrase.
 * When [asCover] is true, the URL becomes the phrasebook cover image.
 */
@Serializable
data class GeneratePhraseImageRequest(
    val prompt: String,
    val referencePhotoIds: List<String> = emptyList(),
    val phraseId: String? = null,
    val asCover: Boolean = false,
)

@Serializable
data class GeneratePhraseImageResponse(
    val imageUrl: String,
    val phrase: Phrase? = null,
    val coverImageUrl: String? = null,
)

@Serializable
data class PhrasesCoverResponse(
    val imageUrl: String? = null,
)

@Serializable
data class AdminSignInRequest(
    val password: String,
)

@Serializable
data class AdminSignInResponse(
    val token: String,
)

@Serializable
data class PhraseApiError(
    val error: String,
)

/** Allowed anonymous reaction emojis for the digital phrasebook. */
object PhraseReactions {
    val allowed: Set<String> = setOf("🌿", "✨", "🪷", "🌙", "🌺")
}

/** Canonical location keys/labels used by the founding deck (admin shortcuts). */
object PhraseLocations {
    data class Entry(
        val key: String,
        val location: LocalizedText,
        val icon: String,
    )

    val all: List<Entry> = listOf(
        Entry("infinity-pool", LocalizedText("Infinity Pool", "Hồ vô cực", "无边泳池"), "pool"),
        Entry("verandah-garden", LocalizedText("Verandah Garden", "Vườn Verandah", "Verandah 花园"), "park"),
        Entry("communal-table", LocalizedText("Communal Table", "Bàn chung", "公共餐桌"), "table_restaurant"),
        Entry("sky-lounge", LocalizedText("Sky Lounge", "Sảnh trời", "空中酒廊"), "apartment"),
        Entry("gym", LocalizedText("Gym", "Phòng gym", "健身房"), "fitness_center"),
        Entry("library", LocalizedText("Library", "Thư viện", "图书馆"), "local_library"),
        Entry("bbq-area", LocalizedText("BBQ Area", "Khu BBQ", "烧烤区"), "outdoor_grill"),
        Entry("lobby", LocalizedText("Lobby", "Sảnh", "大堂"), "domain"),
        Entry("garden-path", LocalizedText("Garden Path", "Lối vườn", "花园小径"), "local_florist"),
        Entry("anywhere", LocalizedText("Anywhere", "Mọi nơi", "随处"), "nights_stay"),
    )

    fun byKey(key: String): Entry? = all.firstOrNull { it.key == key }
}
