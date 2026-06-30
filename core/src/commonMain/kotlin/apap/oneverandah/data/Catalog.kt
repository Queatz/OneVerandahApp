package apap.oneverandah.data

import apap.oneverandah.model.Category
import apap.oneverandah.model.LocalizedText
import apap.oneverandah.model.Service
import apap.oneverandah.model.ServiceStatus
import apap.oneverandah.model.SuperCategory

/**
 * In-memory catalog of [Category] and [Service] data.
 *
 * This stands in for what will eventually be served by an API. Keeping it in
 * `commonMain` lets both the web client and the (future) server share exactly
 * the same shape of data. The actual service definitions live in
 * [CatalogServices] to keep this file focused on lookups.
 */
object Catalog {
    val superCategories: List<SuperCategory> = listOf(
        SuperCategory("relax", LocalizedText(en = "Relax", vi = "Thư giãn", zh = "放松")),
        SuperCategory("train", LocalizedText(en = "Train", vi = "Rèn luyện", zh = "训练")),
        SuperCategory("study", LocalizedText(en = "Study", vi = "Học tập", zh = "学习")),
        SuperCategory("eatdrink", LocalizedText(en = "Eat & Drink", vi = "Ăn uống", zh = "餐饮")),
        SuperCategory("health", LocalizedText(en = "Health", vi = "Sức khỏe", zh = "健康")),
        SuperCategory("travel", LocalizedText(en = "Travel", vi = "Du lịch", zh = "出行")),
        SuperCategory("fun", LocalizedText(en = "Fun", vi = "Vui chơi", zh = "娱乐")),
        SuperCategory("home", LocalizedText(en = "Home", vi = "Nhà cửa", zh = "家居")),
        SuperCategory("kids", LocalizedText(en = "Kids", vi = "Trẻ em", zh = "儿童")),
        SuperCategory("pets", LocalizedText(en = "Pets", vi = "Thú cưng", zh = "宠物")),
        SuperCategory("productivity", LocalizedText(en = "Productivity", vi = "Năng suất", zh = "生产力")),
    )

    val categories: List<Category> = listOf(
        Category("massages", "relax", LocalizedText(en = "Massages", vi = "Mát-xa", zh = "按摩"), icon = "spa"),
        Category("yoga", "relax", LocalizedText(en = "Yoga", vi = "Yoga", zh = "瑜伽"), icon = "self_improvement"),
        Category("personal_trainers", "train", LocalizedText(en = "Personal Trainers", vi = "Huấn luyện viên cá nhân", zh = "私人教练"), icon = "fitness_center"),
        Category("music", "study", LocalizedText(en = "Music Lessons", vi = "Lớp học nhạc", zh = "音乐课程"), icon = "music_note"),
        Category("languages", "study", LocalizedText(en = "Language Learning", vi = "Học ngoại ngữ", zh = "语言学习"), icon = "translate"),
        Category("workshops", "study", LocalizedText(en = "Workshops", vi = "Hội thảo", zh = "工作坊"), icon = "palette"),
        Category("tutoring", "study", LocalizedText(en = "Homework Help & Tutoring", vi = "Gia sư & Hỗ trợ bài tập", zh = "作业辅导"), icon = "school"),
        Category("dinners", "eatdrink", LocalizedText(en = "Dinners", vi = "Bữa tối", zh = "晚餐"), icon = "restaurant"),
        Category("coffeetea", "eatdrink", LocalizedText(en = "Coffee & Tea", vi = "Cà phê & Trà", zh = "咖啡与茶"), icon = "local_cafe"),
        Category("grocery", "eatdrink", LocalizedText(en = "Grocery Run", vi = "Đi chợ hộ", zh = "代购杂货"), icon = "shopping_cart"),
        Category("nutrition", "health", LocalizedText(en = "Vegan Nutrition Planning", vi = "Lên kế hoạch dinh dưỡng thuần chay", zh = "纯素营养规划"), icon = "eco"),
        Category("vacation_planning", "travel", LocalizedText(en = "Vacation Planning", vi = "Lên kế hoạch kỳ nghỉ", zh = "度假规划"), icon = "beach_access"),
        Category("ridesharing", "travel", LocalizedText(en = "Ride Sharing", vi = "Đi chung xe", zh = "拼车"), icon = "directions_car"),
        Category("photography", "fun", LocalizedText(en = "Photography", vi = "Nhiếp ảnh", zh = "摄影"), icon = "photo_camera"),
        Category("party_planning", "fun", LocalizedText(en = "Party Planning", vi = "Lên kế hoạch tiệc tùng", zh = "派对策划"), icon = "celebration"),
        Category("cleaning", "home", LocalizedText(en = "Cleaning", vi = "Dọn dẹp nhà cửa", zh = "清洁"), icon = "cleaning_services"),
        Category("home_organizing", "home", LocalizedText(en = "Home Organizing", vi = "Sắp xếp nhà cửa", zh = "家居整理"), icon = "potted_plant"),
        Category("gardening", "home", LocalizedText(en = "Gardening & Plant Care", vi = "Làm vườn & Chăm sóc cây", zh = "园艺与植物护理"), icon = "yard"),
        Category("rentals", "home", LocalizedText(en = "Tool Rentals & Charger", vi = "Cho thuê dụng cụ & Sạc pin", zh = "工具租赁与充电器"), icon = "power"),
        Category("furniture", "home", LocalizedText(en = "Furniture & Moving", vi = "Lắp ráp đồ gỗ & Vận chuyển", zh = "家具与搬运"), icon = "local_shipping"),
        Category("housesitting", "home", LocalizedText(en = "House Sitting", vi = "Trông coi nhà cửa", zh = "房屋看管"), icon = "home"),
        Category("ithelp", "home", LocalizedText(en = "IT Help", vi = "Hỗ trợ công nghệ", zh = "IT协助"), icon = "computer"),
        Category("english", "kids", LocalizedText(en = "English Activities", vi = "Hoạt động tiếng Anh", zh = "英语活动"), icon = "child_care"),
        Category("babysitting", "kids", LocalizedText(en = "Babysitting", vi = "Giữ trẻ", zh = "婴儿看护"), icon = "crib"),
        Category("petsitting", "pets", LocalizedText(en = "Petsitting", vi = "Trông giữ thú chưng", zh = "宠物寄养"), icon = "pets"),
        Category("petcare", "pets", LocalizedText(en = "Pet Care", vi = "Chăm sóc thú cưng", zh = "宠物护理"), icon = "self_care"),
        Category("plantsitting", "home", LocalizedText(en = "Plantsitting", vi = "Trông cây hộ", zh = "植物看护"), icon = "local_florist"),
        Category("app_design", "productivity", LocalizedText(en = "App Design", vi = "Thiết kế ứng dụng", zh = "应用设计"), icon = "terminal"),
        Category("life_coaching", "productivity", LocalizedText(en = "Life Coaching", vi = "Khai vấn đời sống", zh = "生活教练"), icon = "psychology"),
    )

    val services: List<Service> = CatalogServices.all
        .filterNot { it.status == ServiceStatus.UNAVAILABLE }

    /**
     * Curated example "Popular" services shown on the Home screen when the user
     * has no saved or recent services yet. Falls back gracefully: unknown or
     * unavailable ids are simply skipped.
     */
    val popular: List<Service> = listOf(
        "word-massages-jacob",
        "one-hour-massages-luke",
        "kids-english-playgroup",
        "business-english-coaching",
        "app-design-jacob",
        "life-coaching-jacob",
    ).mapNotNull { id -> services.firstOrNull { it.id == id } }

    fun categoryById(id: String?): Category? = categories.firstOrNull { it.id == id }

    fun servicesByCategory(categoryId: String?): List<Service> =
        services.filter { it.categoryId == categoryId }

    fun serviceById(id: String?): Service? = services.firstOrNull { it.id == id }

    /**
     * All services offered by the same [provider] (matched by the provider's
     * unique [apap.oneverandah.model.ServiceProvider.id]).
     */
    fun servicesByProvider(provider: apap.oneverandah.model.ServiceProvider): List<Service> =
        services.filter { it.provider.id == provider.id }

    /**
     * Looks up a [apap.oneverandah.model.ServiceProvider] by its unique,
     * URL-friendly [apap.oneverandah.model.ServiceProvider.id] used in deep
     * links (e.g. `/provider/ha`).
     */
    fun providerById(id: String?): apap.oneverandah.model.ServiceProvider? =
        services.firstOrNull { it.provider.id == id }?.provider
}
