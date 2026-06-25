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
        Category("photography", LocalizedText(en = "Photography", vi = "Nhiếp ảnh"), icon = "photo_camera"),
        Category("petcare", LocalizedText(en = "Pet Care", vi = "Chăm sóc thú cưng"), icon = "pets"),
        Category("gardening", LocalizedText(en = "Gardening & Plant Care", vi = "Làm vườn & Chăm sóc cây"), icon = "yard"),
        Category("rentals", LocalizedText(en = "Tool Rentals & Charger", vi = "Cho thuê dụng cụ & Sạc pin"), icon = "power"),
        Category("furniture", LocalizedText(en = "Furniture & Moving", vi = "Lắp ráp đồ gỗ & Vận chuyển"), icon = "local_shipping"),
        Category("housesitting", LocalizedText(en = "House Sitting", vi = "Trông coi nhà cửa"), icon = "home"),
        Category("ridesharing", LocalizedText(en = "Ride Sharing", vi = "Đi chung xe"), icon = "directions_car"),
        Category("ithelp", LocalizedText(en = "IT Help", vi = "Hỗ trợ công nghệ"), icon = "computer"),
        Category("tutoring", LocalizedText(en = "Homework Help & Tutoring", vi = "Gia sư & Hỗ trợ bài tập"), icon = "school"),
        Category("grocery", LocalizedText(en = "Grocery Run", vi = "Đi chợ hộ"), icon = "shopping_cart"),
        Category("coffeetea", LocalizedText(en = "Coffee & Tea", vi = "Cà phê & Trà"), icon = "local_cafe"),
    )

    val services: List<Service> = CatalogServices.all

    fun categoryById(id: String?): Category? = categories.firstOrNull { it.id == id }

    fun servicesByCategory(categoryId: String?): List<Service> =
        services.filter { it.categoryId == categoryId }

    fun serviceById(id: String?): Service? = services.firstOrNull { it.id == id }
}
