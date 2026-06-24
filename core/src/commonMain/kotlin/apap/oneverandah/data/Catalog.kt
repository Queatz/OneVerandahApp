package apap.oneverandah.data

import apap.oneverandah.model.Category
import apap.oneverandah.model.LocalizedText
import apap.oneverandah.model.Service

/**
 * In-memory catalog of [Category] and [Service] data.
 *
 * This stands in for what will eventually be served by an API. Keeping it in
 * `commonMain` lets both the web client and the (future) server share exactly
 * the same shape of data. The actual service definitions live in
 * [CatalogServices] to keep this file focused on lookups.
 */
object Catalog {

    val categories: List<Category> = listOf(
        Category("massages", LocalizedText(en = "Massages", vi = "Mát-xa"), icon = "spa"),
        Category("english", LocalizedText(en = "English Activities", vi = "Hoạt động tiếng Anh"), icon = "child_care"),
        Category("dinners", LocalizedText(en = "Dinners", vi = "Bữa tối"), icon = "restaurant"),
        Category("cleaning", LocalizedText(en = "House Cleaning", vi = "Dọn dẹp nhà cửa"), icon = "cleaning_services"),
        Category("music", LocalizedText(en = "Music Lessons", vi = "Lớp học nhạc"), icon = "music_note"),
        Category("workshops", LocalizedText(en = "Workshops", vi = "Hội thảo"), icon = "palette"),
        Category("babysitting", LocalizedText(en = "Babysitting", vi = "Giữ trẻ"), icon = "crib"),
        Category("petsitting", LocalizedText(en = "Petsitting", vi = "Trông giữ thú chưng"), icon = "pets"),
    )

    val services: List<Service> = CatalogServices.all

    fun categoryById(id: String?): Category? = categories.firstOrNull { it.id == id }

    fun servicesByCategory(categoryId: String?): List<Service> =
        services.filter { it.categoryId == categoryId }

    fun serviceById(id: String?): Service? = services.firstOrNull { it.id == id }
}
