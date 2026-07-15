package apap.oneverandah.data

import apap.oneverandah.model.BodyMapOption
import apap.oneverandah.model.BodyPart
import apap.oneverandah.model.BookingTypeOption
import apap.oneverandah.model.Choice
import apap.oneverandah.model.CommentPrompt
import apap.oneverandah.model.CounterOption
import apap.oneverandah.model.DurationOption
import apap.oneverandah.model.LocalizedText
import apap.oneverandah.model.Money
import apap.oneverandah.model.MultiSelectOption
import apap.oneverandah.model.Review
import apap.oneverandah.model.Service
import apap.oneverandah.model.SingleSelectOption
import apap.oneverandah.model.ServiceProvider
import apap.oneverandah.model.ServiceStatus
import apap.oneverandah.model.Sex

/**
 * Concrete [Service] definitions: two per category, each combining a different
 * mix of [apap.oneverandah.model.ServiceOption]s so every booking screen looks
 * and behaves uniquely. Prices are in VND via [Money.thousands].
 */
internal object CatalogServices {

    // --- small local helpers to keep the data terse ----------------------

    private fun lt(en: String, vi: String, zh: String = en) = LocalizedText(en, vi, zh)
    private fun vnd(thousands: Long) = Money.thousands(thousands)

    private val trialAndFollowup = listOf(
        BookingTypeOption(
            id = "initial",
            label = lt("Initial trial booking", "Đặt lịch dùng thử lần đầu"),
            priceDelta = vnd(-50),
        ),
        BookingTypeOption("followup", lt("Follow-up booking", "Đặt lịch tiếp theo")),
    )

    // ---------------------------------------------------------------------

    private val jacob = ServiceProvider("Jacob", Sex.MALE, "Block B - B07.01", "+84 708 739 214", "face_6", lt("Builder, coach and resident who loves turning ideas into realities.", "Lập trình viên, người khai vấn và cư dân, yêu thích biến ý tưởng thành hiện thực."))

    private val appDesignJacob = Service(
        id = "app-design-jacob",
        categoryId = "app_design",
        icon = "terminal",
        title = lt("Personal App Design & Development by Jacob", "Thiết kế & Phát triển ứng dụng cá nhân bởi Jacob"),
        description = lt("Custom mobile and web applications tailored to your needs.", "Ứng dụng di động và web tùy chỉnh theo nhu cầu của bạn."),
        rating = 5.0,
        reviewCount = 12,
        basePrice = vnd(2000),
        bookingTypes = trialAndFollowup,
        durations = listOf(
            DurationOption("60", minutes = 60, price = vnd(2000)),
        ),
        provider = jacob,
        commentPrompt = CommentPrompt(
            label = lt("Tell me about your app idea", "Hãy kể cho tôi nghe về ý tưởng ứng dụng của bạn"),
            placeholder = lt("e.g. A fitness tracker, a personal blog, a small shop...", "vd. Ứng dụng theo dõi sức khỏe, blog cá nhân, cửa hàng nhỏ..."),
        ),
    )

    private val lifeCoachingJacob = Service(
        id = "life-coaching-jacob",
        categoryId = "life_coaching",
        icon = "psychology",
        title = lt("Personal Life Coaching by Jacob", "Khai vấn đời sống cá nhân bởi Jacob"),
        description = lt("Unlock your potential and achieve your personal goals.", "Khai phá tiềm năng và đạt được các mục tiêu cá nhân của bạn."),
        rating = 4.9,
        reviewCount = 25,
        basePrice = vnd(1000),
        bookingTypes = trialAndFollowup,
        durations = listOf(
            DurationOption("60", minutes = 60, price = vnd(1000)),
        ),
        provider = jacob,
    )

    val all: List<Service> = listOf(
        appDesignJacob,
        lifeCoachingJacob,
        // ===================== MASSAGES =====================
        Service(
            id = "word-massages-jacob",
            categoryId = "massages",
            icon = "record_voice_over",
            title = lt("Word Massages by Jacob", "Mát-xa ngôn từ bởi Jacob"),
            description = lt("Therapeutic spoken words.", "Những lời nói trị liệu."),
            rating = 4.9,
            reviewCount = 124,
            basePrice = vnd(300),
            bookingTypes = trialAndFollowup,
            durations = listOf(
                DurationOption("60", minutes = 60, price = vnd(300)),
                DurationOption("90", minutes = 90, price = vnd(420)),
            ),
            provider = jacob,
            options = listOf(
                SingleSelectOption(
                    id = "tone",
                    title = lt("Tone of voice", "Giọng điệu"),
                    choices = listOf(
                        Choice("calm", lt("Calm", "Nhẹ nhàng")),
                        Choice("uplifting", lt("Uplifting", "Truyền cảm hứng"), vnd(40)),
                        Choice("poetic", lt("Poetic", "Thơ ca"), vnd(60)),
                    ),
                ),
            ),
            reviews = listOf(
                Review("r1", "Mai", 5, lt("Surprisingly soothing — I drifted off.", "Thư giãn đến bất ngờ — tôi đã thiu thiu ngủ.")),
                Review("r2", "Daniel", 5, lt("Jacob's voice is pure calm.", "Giọng của Jacob thật sự rất êm dịu.")),
            ),
            commentPrompt = CommentPrompt(
                label = lt("What do you want to hear?", "Bạn muốn nghe điều gì?"),
                placeholder = lt(
                    "e.g. Affirmations, calming poetry, positive reinforcement...",
                    "vd. Lời khẳng định, thơ thư giãn, lời động viên tích cực...",
                ),
            ),
        ),
        Service(
            id = "one-hour-massages-luke",
            categoryId = "massages",
            icon = "self_improvement",
            title = lt("1 hour massages by Jacob", "Mát-xa 1 giờ bởi Jacob"),
            description = lt("Deep tissue and relaxation.", "Mô sâu và thư giãn."),
            rating = 4.8,
            reviewCount = 89,
            basePrice = vnd(450),
            bookingTypes = trialAndFollowup,
            durations = listOf(
                DurationOption("60", minutes = 60, price = vnd(450)),
                DurationOption("90", minutes = 90, price = vnd(650)),
            ),
            provider = jacob,
            options = listOf(
                BodyMapOption(
                    id = "target-area",
                    title = lt("Target areas", "Vùng cần chăm sóc"),
                    parts = listOf(
                        BodyPart("head", lt("Head & neck", "Đầu & cổ"), vnd(40)),
                        BodyPart("shoulders", lt("Shoulders", "Vai"), vnd(50)),
                        BodyPart("chest", lt("Chest", "Ngực"), vnd(60)),
                        BodyPart("belly", lt("Belly", "Bụng"), vnd(60)),
                        BodyPart("upper-back", lt("Upper back", "Lưng trên"), vnd(60)),
                        BodyPart("lower-back", lt("Lower back", "Lưng dưới"), vnd(60)),
                        BodyPart("arms", lt("Arms", "Tay"), vnd(40)),
                        BodyPart("legs", lt("Legs", "Chân"), vnd(50)),
                        BodyPart("feet", lt("Feet", "Bàn chân"), vnd(40)),
                    ),
                    defaultSelectedIds = setOf("upper-back", "lower-back"),
                ),
                SingleSelectOption(
                    id = "oil",
                    title = lt("Massage oil", "Dầu mát-xa"),
                    choices = listOf(
                        Choice("none", lt("None", "Không dùng")),
                        Choice("lavender", lt("Lavender", "Oải hương"), vnd(30)),
                        Choice("eucalyptus", lt("Eucalyptus", "Khuynh diệp"), vnd(30)),
                    ),
                ),
            ),
            reviews = listOf(
                Review("r1", "Huong", 5, lt("Best deep tissue in the building.", "Mát-xa mô sâu tốt nhất tòa nhà.")),
                Review("r2", "Tom", 4, lt("Great pressure, booked again.", "Lực ấn rất tốt, tôi đã đặt lại.")),
            ),
        ),

        // ===================== ENGLISH ACTIVITIES =====================
        Service(
            id = "kids-english-playgroup",
            categoryId = "english",
            icon = "child_care",
            title = lt("Kids English Playgroup", "Nhóm vui học tiếng Anh cho trẻ"),
            description = lt("Songs, games and stories.", "Bài hát, trò chơi và kể chuyện."),
            rating = 4.7,
            reviewCount = 56,
            basePrice = vnd(200),
            bookingTypes = trialAndFollowup,
            durations = listOf(
                DurationOption("45", minutes = 45, price = vnd(200)),
                DurationOption("60", minutes = 60, price = vnd(260)),
            ),
            provider = jacob,
            options = listOf(
                CounterOption(
                    id = "children",
                    title = lt("Number of children", "Số lượng trẻ"),
                    unitLabel = lt("Child", "Trẻ"),
                    unitPrice = vnd(80),
                    min = 1,
                    max = 6,
                    default = 1,
                ),
                SingleSelectOption(
                    id = "level",
                    title = lt("Level", "Trình độ"),
                    choices = listOf(
                        Choice("beginner", lt("Beginner", "Mới bắt đầu")),
                        Choice("intermediate", lt("Intermediate", "Trung cấp")),
                    ),
                ),
            ),
            reviews = listOf(
                Review("r1", "Linh", 5, lt("My son loves the songs!", "Con trai tôi rất thích các bài hát!")),
            ),
        ),
        Service(
            id = "business-english-coaching",
            categoryId = "english",
            icon = "business_center",
            title = lt("Business English Coaching", "Huấn luyện tiếng Anh thương mại"),
            description = lt("Presentations and emails.", "Thuyết trình và email."),
            rating = 4.9,
            reviewCount = 41,
            basePrice = vnd(500),
            bookingTypes = trialAndFollowup,
            durations = listOf(
                DurationOption("60", minutes = 60, price = vnd(500)),
                DurationOption("90", minutes = 90, price = vnd(700)),
            ),
            provider = jacob,
            options = listOf(
                SingleSelectOption(
                    id = "focus",
                    title = lt("Focus area", "Trọng tâm"),
                    choices = listOf(
                        Choice("presentations", lt("Presentations", "Thuyết trình")),
                        Choice("negotiation", lt("Negotiation", "Đàm phán"), vnd(60)),
                        Choice("writing", lt("Email writing", "Viết email")),
                    ),
                ),
                MultiSelectOption(
                    id = "materials",
                    title = lt("Extra materials", "Tài liệu bổ sung"),
                    choices = listOf(
                        Choice("workbook", lt("Workbook", "Sách bài tập"), vnd(50)),
                        Choice("recording", lt("Session recording", "Ghi âm buổi học"), vnd(40)),
                    ),
                ),
            ),
            reviews = listOf(
                Review("r1", "Phuc", 5, lt("Nailed my investor pitch after this.", "Tôi đã thành công với bài thuyết trình nhà đầu tư.")),
                Review("r2", "Anna", 5, lt("Practical and confidence-building.", "Thực tế và giúp tự tin hơn.")),
            ),
        ),

        // ===================== DINNERS =====================
        Service(
            id = "private-chef-dinner",
            categoryId = "dinners",
            icon = "restaurant",
            title = lt("Private Chef Dinner", "Bữa tối đầu bếp riêng"),
            description = lt("A chef cooks in your home.", "Đầu bếp nấu tại nhà bạn."),
            rating = 4.9,
            reviewCount = 73,
            basePrice = vnd(900),
            bookingTypes = trialAndFollowup,
            durations = listOf(
                DurationOption("120", minutes = 120, price = vnd(900)),
                DurationOption("180", minutes = 180, price = vnd(1200)),
            ),
            provider = jacob,
            options = listOf(
                CounterOption(
                    id = "guests",
                    title = lt("Number of guests", "Số lượng khách"),
                    unitLabel = lt("Guest", "Khách"),
                    unitPrice = vnd(150),
                    min = 2,
                    max = 12,
                    default = 2,
                ),
                SingleSelectOption(
                    id = "cuisine",
                    title = lt("Cuisine", "Ẩm thực"),
                    choices = listOf(
                        Choice("vietnamese", lt("Vietnamese", "Việt Nam")),
                        Choice("italian", lt("Italian", "Ý"), vnd(80)),
                        Choice("french", lt("French", "Pháp"), vnd(120)),
                    ),
                ),
                MultiSelectOption(
                    id = "courses",
                    title = lt("Add courses", "Thêm món"),
                    choices = listOf(
                        Choice("dessert", lt("Dessert", "Tráng miệng"), vnd(120)),
                        Choice("wine", lt("Wine pairing", "Kết hợp rượu vang"), vnd(200)),
                    ),
                ),
            ),
            reviews = listOf(
                Review("r1", "Sophie", 5, lt("Felt like a fine-dining restaurant at home.", "Như nhà hàng cao cấp ngay tại nhà.")),
                Review("r2", "Khoa", 5, lt("The wine pairing was perfect.", "Kết hợp rượu vang thật hoàn hảo.")),
            ),
        ),
        Service(
            id = "cooking-class-dinner",
            categoryId = "dinners",
            icon = "soup_kitchen",
            title = lt("Hands-on Cooking Class", "Lớp học nấu ăn thực hành"),
            description = lt("Cook then eat together.", "Cùng nấu rồi cùng ăn."),
            rating = 4.6,
            reviewCount = 38,
            basePrice = vnd(400),
            bookingTypes = trialAndFollowup,
            durations = listOf(
                DurationOption("90", minutes = 90, price = vnd(400)),
                DurationOption("150", minutes = 150, price = vnd(600)),
            ),
            provider = jacob,
            options = listOf(
                CounterOption(
                    id = "participants",
                    title = lt("Participants", "Người tham gia"),
                    unitLabel = lt("Person", "Người"),
                    unitPrice = vnd(120),
                    min = 1,
                    max = 8,
                    default = 2,
                ),
                SingleSelectOption(
                    id = "dish",
                    title = lt("Dish", "Món ăn"),
                    choices = listOf(
                        Choice("pho", lt("Phở", "Phở")),
                        Choice("springrolls", lt("Spring rolls", "Gỏi cuốn")),
                        Choice("banhmi", lt("Bánh mì", "Bánh mì")),
                    ),
                ),
            ),
            reviews = listOf(
                Review("r1", "Greg", 5, lt("Made the best pho of my life.", "Nấu được tô phở ngon nhất đời tôi.")),
            ),
        ),

        // ===================== HOUSE CLEANING =====================
        Service(
            id = "standard-house-cleaning",
            categoryId = "cleaning",
            icon = "cleaning_services",
            title = lt("Standard House Cleaning", "Dọn dẹp nhà tiêu chuẩn"),
            description = lt("Regular tidy and clean.", "Dọn dẹp định kỳ sạch sẽ."),
            rating = 4.7,
            reviewCount = 210,
            basePrice = vnd(250),
            bookingTypes = trialAndFollowup,
            durations = listOf(
                DurationOption("120", minutes = 120, price = vnd(250)),
                DurationOption("180", minutes = 180, price = vnd(350)),
            ),
            provider = jacob,
            options = listOf(
                CounterOption(
                    id = "rooms",
                    title = lt("Rooms to clean", "Số phòng cần dọn"),
                    unitLabel = lt("Room", "Phòng"),
                    unitPrice = vnd(60),
                    min = 1,
                    max = 10,
                    default = 2,
                ),
                MultiSelectOption(
                    id = "extras",
                    title = lt("Extras", "Dịch vụ thêm"),
                    choices = listOf(
                        Choice("windows", lt("Windows", "Lau cửa kính"), vnd(80)),
                        Choice("fridge", lt("Inside fridge", "Bên trong tủ lạnh"), vnd(70)),
                        Choice("ironing", lt("Ironing", "Ủi đồ"), vnd(60)),
                    ),
                ),
            ),
            reviews = listOf(
                Review("r1", "Trang", 5, lt("Spotless every time.", "Lần nào cũng sạch bong.")),
                Review("r2", "Ben", 4, lt("Reliable and friendly.", "Đáng tin cậy và thân thiện.")),
            ),
        ),
        Service(
            id = "deep-cleaning",
            categoryId = "cleaning",
            icon = "mop",
            title = lt("Deep Cleaning", "Dọn dẹp tổng thể"),
            description = lt("Top-to-bottom intensive clean.", "Dọn dẹp kỹ lưỡng toàn diện."),
            rating = 4.8,
            reviewCount = 97,
            basePrice = vnd(500),
            bookingTypes = trialAndFollowup,
            durations = listOf(
                DurationOption("240", minutes = 240, price = vnd(500)),
                DurationOption("300", minutes = 300, price = vnd(650)),
            ),
            provider = jacob,
            options = listOf(
                CounterOption(
                    id = "bedrooms",
                    title = lt("Bedrooms", "Phòng ngủ"),
                    unitLabel = lt("Bedroom", "Phòng ngủ"),
                    unitPrice = vnd(90),
                    min = 1,
                    max = 8,
                    default = 2,
                ),
                MultiSelectOption(
                    id = "extras",
                    title = lt("Add-ons", "Tùy chọn thêm"),
                    choices = listOf(
                        Choice("oven", lt("Oven", "Lò nướng"), vnd(100)),
                        Choice("balcony", lt("Balcony", "Ban công"), vnd(80)),
                        Choice("mould", lt("Mould treatment", "Xử lý nấm mốc"), vnd(150)),
                    ),
                ),
            ),
            reviews = listOf(
                Review("r1", "Quynh", 5, lt("Apartment looked brand new.", "Căn hộ như mới tinh.")),
            ),
        ),

        // ===================== HOME ORGANIZING =====================
        Service(
            id = "closet-organization-lan",
            categoryId = "home_organizing",
            icon = "checkroom",
            title = lt("Closet Organization by Jacob", "Sắp xếp tủ quần áo bởi Jacob"),
            description = lt("De-clutter and organize your wardrobe.", "Sắp xếp và tối giản tủ đồ của bạn."),
            rating = 4.8,
            reviewCount = 42,
            basePrice = vnd(300),
            bookingTypes = trialAndFollowup,
            durations = listOf(
                DurationOption("120", minutes = 120, price = vnd(300)),
                DurationOption("180", minutes = 180, price = vnd(420)),
            ),
            provider = jacob,
            options = listOf(
                CounterOption(
                    id = "wardrobes",
                    title = lt("Number of wardrobes", "Số lượng tủ quần áo"),
                    unitLabel = lt("Wardrobe", "Tủ"),
                    unitPrice = vnd(80),
                    min = 1,
                    max = 5,
                    default = 1,
                ),
                SingleSelectOption(
                    id = "foldingStyle",
                    title = lt("Folding style", "Kiểu gấp đồ"),
                    choices = listOf(
                        Choice("standard", lt("Standard", "Tiêu chuẩn")),
                        Choice("konmari", lt("KonMari style", "Phương pháp KonMari"), vnd(50)),
                    ),
                ),
            ),
            reviews = listOf(
                Review("r1", "Hanh", 5, lt("Jacob made my messy closet look like a boutique!", "Jacob làm chiếc tủ lộn xộn của tôi trông như cửa hiệu thời trang!")),
            ),
        ),
        Service(
            id = "kitchen-organization-an",
            categoryId = "home_organizing",
            icon = "kitchen",
            title = lt("Kitchen & Pantry Organization", "Sắp xếp nhà bếp & Phòng ăn"),
            description = lt("Systematize your kitchen, pantry, and fridge.", "Sắp xếp hệ thống nhà bếp, tủ đồ ăn và tủ lạnh."),
            rating = 4.9,
            reviewCount = 29,
            basePrice = vnd(400),
            bookingTypes = trialAndFollowup,
            durations = listOf(
                DurationOption("120", minutes = 120, price = vnd(400)),
                DurationOption("240", minutes = 240, price = vnd(750)),
            ),
            provider = jacob,
            options = listOf(
                MultiSelectOption(
                    id = "focusAreas",
                    title = lt("Focus areas", "Khu vực trọng tâm"),
                    choices = listOf(
                        Choice("pantry", lt("Pantry & Cabinets", "Tủ thực phẩm & Tủ bếp"), vnd(100)),
                        Choice("fridge", lt("Refrigerator organizing", "Sắp xếp tủ lạnh"), vnd(80)),
                    ),
                ),
            ),
            reviews = listOf(
                Review("r1", "Cuong", 5, lt("Everything is so easy to find now.", "Bây giờ mọi thứ thật dễ tìm.")),
            ),
        ),

        // ===================== PERSONAL TRAINERS =====================
        Service(
            id = "strength-conditioning-duc",
            categoryId = "personal_trainers",
            icon = "fitness_center",
            title = lt("Strength & Conditioning by Jacob", "Sức bền & Thể lực bởi Jacob"),
            description = lt("Customized workouts to build strength, power, and lean muscle.", "Các bài tập được thiết kế riêng giúp xây dựng sức mạnh, thể lực và cơ bắp."),
            rating = 4.9,
            reviewCount = 56,
            basePrice = vnd(350),
            bookingTypes = trialAndFollowup,
            durations = listOf(
                DurationOption("60", minutes = 60, price = vnd(350)),
                DurationOption("90", minutes = 90, price = vnd(500)),
            ),
            provider = jacob,
            options = listOf(
                MultiSelectOption(
                    id = "goals",
                    title = lt("Focus Goals", "Mục tiêu tập luyện"),
                    choices = listOf(
                        Choice("weight_loss", lt("Weight Loss", "Giảm cân")),
                        Choice("muscle_gain", lt("Muscle Gain", "Tăng cơ")),
                        Choice("endurance", lt("Endurance", "Sức bền")),
                    ),
                ),
            ),
            reviews = listOf(
                Review("r1", "Long", 5, lt("Jacob is highly professional and motivating.", "Jacob rất chuyên nghiệp và đầy động lực.")),
            ),
        ),
        Service(
            id = "hiit-cardio-vy",
            categoryId = "personal_trainers",
            icon = "directions_run",
            title = lt("Fat Loss & HIIT with Jacob", "Giảm mỡ & HIIT cùng Jacob"),
            description = lt("High intensity interval training sessions for efficient calorie burning.", "Các buổi tập cường độ cao ngắt quãng giúp đốt cháy calo hiệu quả."),
            rating = 4.8,
            reviewCount = 38,
            basePrice = vnd(300),
            bookingTypes = trialAndFollowup,
            durations = listOf(
                DurationOption("45", minutes = 45, price = vnd(300)),
                DurationOption("60", minutes = 60, price = vnd(400)),
            ),
            provider = jacob,
            options = listOf(
                SingleSelectOption(
                    id = "fitnessLevel",
                    title = lt("Fitness Level", "Trình độ thể chất"),
                    choices = listOf(
                        Choice("beginner", lt("Beginner", "Mới bắt đầu")),
                        Choice("advanced", lt("Advanced", "Nâng cao"), vnd(50)),
                    ),
                ),
            ),
            reviews = listOf(
                Review("r1", "Hanh", 5, lt("Sweaty but extremely fun and energetic!", "Đầy mồ hôi nhưng cực kỳ vui vẻ và năng động!")),
            ),
        ),

        // ===================== YOGA =====================
        Service(
            id = "vinyasa-yoga-linh",
            categoryId = "yoga",
            icon = "self_improvement",
            title = lt("Vinyasa Flow Yoga with Jacob", "Yoga Vinyasa Flow cùng Jacob"),
            description = lt("Connect breath with movement in an energizing flow.", "Kết nối hơi thở với chuyển động trong một dòng chảy tràn đầy năng lượng."),
            rating = 4.9,
            reviewCount = 74,
            basePrice = vnd(280),
            bookingTypes = trialAndFollowup,
            durations = listOf(
                DurationOption("60", minutes = 60, price = vnd(280)),
                DurationOption("90", minutes = 90, price = vnd(400)),
            ),
            provider = jacob,
            options = listOf(
                SingleSelectOption(
                    id = "classType",
                    title = lt("Class Type", "Loại lớp học"),
                    choices = listOf(
                        Choice("private", lt("Private 1-on-1", "Riêng tư 1 kèm 1")),
                        Choice("small_group", lt("Small Group (up to 3)", "Nhóm nhỏ (tối đa 3 người)"), vnd(100)),
                    ),
                ),
            ),
            reviews = listOf(
                Review("r1", "Thao", 5, lt("Jacob has amazing energy. Perfect instruction.", "Jacob có nguồn năng lượng tuyệt vời. Hướng dẫn rất hoàn hảo.")),
            ),
        ),
        Service(
            id = "yin-yoga-ha",
            categoryId = "yoga",
            icon = "spa",
            title = lt("Yin & Relaxation Yoga by Jacob", "Yoga Yin & Thư giãn bởi Jacob"),
            description = lt("Slow-paced style of yoga with postures held for longer periods.", "Phong cách yoga chậm rãi với các tư thế được giữ trong thời gian dài hơn."),
            rating = 4.7,
            reviewCount = 41,
            basePrice = vnd(250),
            bookingTypes = trialAndFollowup,
            durations = listOf(
                DurationOption("60", minutes = 60, price = vnd(250)),
                DurationOption("90", minutes = 90, price = vnd(350)),
            ),
            provider = jacob,
            options = listOf(
                MultiSelectOption(
                    id = "props",
                    title = lt("Props Needed", "Dụng cụ hỗ trợ"),
                    choices = listOf(
                        Choice("mat", lt("Bring own mat", "Tự mang thảm")),
                        Choice("blocks_straps", lt("Request blocks & straps", "Yêu cầu gạch & dây hỗ trợ"), vnd(20)),
                    ),
                ),
            ),
            reviews = listOf(
                Review("r1", "Minh", 5, lt("Deeply relaxing session, slept so well after.", "Buổi tập thư giãn sâu, sau đó ngủ rất ngon.")),
            ),
        ),

        // ===================== PARTY PLANNING =====================
        Service(
            id = "birthday-anniversary-planning-chi",
            categoryId = "party_planning",
            icon = "celebration",
            title = lt("Birthday & Anniversary Planning by Jacob", "Lên kế hoạch sinh nhật & kỷ niệm bởi Jacob"),
            description = lt("Create unforgettable celebrations with custom decor and themes.", "Tạo nên những lễ kỷ niệm khó quên với thiết kế và chủ đề tùy chỉnh."),
            rating = 4.9,
            reviewCount = 45,
            basePrice = vnd(800),
            bookingTypes = trialAndFollowup,
            durations = listOf(
                DurationOption("60", minutes = 60, price = vnd(800)),
                DurationOption("120", minutes = 120, price = vnd(1500)),
            ),
            provider = jacob,
            options = listOf(
                SingleSelectOption(
                    id = "theme",
                    title = lt("Party Theme", "Chủ đề bữa tiệc"),
                    choices = listOf(
                        Choice("classic", lt("Classic", "Cổ điển")),
                        Choice("themed", lt("Premium Themed", "Chủ đề đặc biệt"), vnd(200)),
                    ),
                ),
            ),
            reviews = listOf(
                Review("r1", "Huy", 5, lt("Jacob made our daughter's birthday absolutely magical!", "Jacob đã làm cho ngày sinh nhật của con gái chúng tôi thực sự kỳ diệu!")),
            ),
        ),
        Service(
            id = "corporate-events-huy",
            categoryId = "party_planning",
            icon = "business_center",
            title = lt("Corporate & Large Events by Jacob", "Sự kiện doanh nghiệp & Sự kiện lớn bởi Jacob"),
            description = lt("Professional planning for corporate milestones, networking, and large parties.", "Lập kế hoạch chuyên nghiệp cho các mốc kỷ niệm doanh nghiệp, kết nối và tiệc lớn."),
            rating = 4.8,
            reviewCount = 32,
            basePrice = vnd(1500),
            bookingTypes = trialAndFollowup,
            status = ServiceStatus.TEMPORARILY_UNAVAILABLE,
            durations = listOf(
                DurationOption("120", minutes = 120, price = vnd(1500)),
                DurationOption("240", minutes = 240, price = vnd(2800)),
            ),
            provider = jacob,
            options = listOf(
                CounterOption(
                    id = "attendees",
                    title = lt("Estimated Guest Count", "Số lượng khách ước tính"),
                    unitLabel = lt("Guests", "Khách"),
                    unitPrice = vnd(10),
                    min = 10,
                    max = 200,
                    default = 50,
                ),
            ),
            reviews = listOf(
                Review("r1", "An", 5, lt("Highly professional coordination. Everything ran smoothly.", "Phối hợp cực kỳ chuyên nghiệp. Mọi thứ diễn ra rất suôn sẻ.")),
            ),
        ),

        // ===================== VACATION PLANNING =====================
        Service(
            id = "custom-itinerary-luke",
            categoryId = "vacation_planning",
            icon = "explore",
            title = lt("Custom Itinerary Planning by Jacob", "Lên lịch trình tùy chỉnh bởi Jacob"),
            description = lt("Personalized travel plans, bookings, and hidden gem recommendations.", "Kế hoạch du lịch cá nhân hóa, đặt phòng và đề xuất các điểm đến ẩn giấu."),
            rating = 4.9,
            reviewCount = 61,
            basePrice = vnd(500),
            bookingTypes = trialAndFollowup,
            durations = listOf(
                DurationOption("60", minutes = 60, price = vnd(500)),
                DurationOption("120", minutes = 120, price = vnd(900)),
            ),
            provider = jacob,
            options = listOf(
                MultiSelectOption(
                    id = "interests",
                    title = lt("Travel Interests", "Sở thích du lịch"),
                    choices = listOf(
                        Choice("nature", lt("Nature & Adventure", "Thiên nhiên & Phiêu lưu")),
                        Choice("food", lt("Food & Culture", "Ẩm thực & Văn hóa")),
                        Choice("luxury", lt("Luxury & Relaxation", "Sang trọng & Thư giãn"), vnd(150)),
                    ),
                ),
            ),
            reviews = listOf(
                Review("r1", "Linh", 5, lt("Jacob planned the perfect family trip to Phu Quoc!", "Jacob đã lên kế hoạch cho chuyến đi gia đình hoàn hảo đến Phú Quốc!")),
            ),
        ),
        Service(
            id = "weekend-getaway-lan",
            categoryId = "vacation_planning",
            icon = "wb_sunny",
            title = lt("Weekend Getaway Planner by Jacob", "Lập kế hoạch nghỉ dưỡng cuối tuần bởi Jacob"),
            description = lt("Quick, stress-free weekend trips and staycations near the city.", "Chuyến đi cuối tuần nhanh chóng, không lo nghĩ và nghỉ dưỡng tại chỗ gần thành phố."),
            rating = 4.7,
            reviewCount = 28,
            basePrice = vnd(350),
            bookingTypes = trialAndFollowup,
            durations = listOf(
                DurationOption("45", minutes = 45, price = vnd(350)),
                DurationOption("90", minutes = 90, price = vnd(600)),
            ),
            provider = jacob,
            options = listOf(
                SingleSelectOption(
                    id = "destinationType",
                    title = lt("Destination Preference", "Sở thích điểm đến"),
                    choices = listOf(
                        Choice("beach", lt("Beachside", "Bờ biển")),
                        Choice("mountain", lt("Mountain/Forest", "Núi/Rừng")),
                        Choice("resort", lt("Luxury Resort Stay", "Nghỉ dưỡng resort sang trọng"), vnd(100)),
                    ),
                ),
            ),
            reviews = listOf(
                Review("r1", "Duc", 5, lt("Exactly what we needed for a quick refresh.", "Chính xác là những gì chúng tôi cần để làm mới bản thân nhanh chóng.")),
            ),
        ),

        // ===================== MUSIC LESSONS =====================
        Service(
            id = "piano-lessons",
            categoryId = "music",
            icon = "piano",
            title = lt("Piano Lessons", "Lớp học piano"),
            description = lt("For all ages and levels.", "Cho mọi lứa tuổi và trình độ."),
            rating = 4.9,
            reviewCount = 64,
            basePrice = vnd(350),
            bookingTypes = trialAndFollowup,
            durations = listOf(
                DurationOption("45", minutes = 45, price = vnd(350)),
                DurationOption("60", minutes = 60, price = vnd(450)),
            ),
            provider = jacob,
            options = listOf(
                SingleSelectOption(
                    id = "level",
                    title = lt("Level", "Trình độ"),
                    choices = listOf(
                        Choice("beginner", lt("Beginner", "Mới bắt đầu")),
                        Choice("intermediate", lt("Intermediate", "Trung cấp"), vnd(50)),
                        Choice("advanced", lt("Advanced", "Nâng cao"), vnd(100)),
                    ),
                ),
                SingleSelectOption(
                    id = "location",
                    title = lt("Location", "Địa điểm"),
                    choices = listOf(
                        Choice("studio", lt("At the studio", "Tại phòng học")),
                        Choice("home", lt("At your home", "Tại nhà bạn"), vnd(80)),
                    ),
                ),
            ),
            reviews = listOf(
                Review("r1", "Mark", 5, lt("Patient and inspiring teacher.", "Giáo viên kiên nhẫn và truyền cảm hứng.")),
                Review("r2", "Vy", 5, lt("Progressed faster than expected.", "Tiến bộ nhanh hơn mong đợi.")),
            ),
        ),
        Service(
            id = "guitar-lessons",
            categoryId = "music",
            icon = "music_note",
            title = lt("Guitar Lessons", "Lớp học guitar"),
            description = lt("Acoustic and electric.", "Guitar mộc và điện."),
            rating = 4.7,
            reviewCount = 52,
            basePrice = vnd(300),
            bookingTypes = trialAndFollowup,
            durations = listOf(
                DurationOption("45", minutes = 45, price = vnd(300)),
                DurationOption("60", minutes = 60, price = vnd(400)),
            ),
            provider = jacob,
            options = listOf(
                SingleSelectOption(
                    id = "style",
                    title = lt("Style", "Phong cách"),
                    choices = listOf(
                        Choice("acoustic", lt("Acoustic", "Mộc")),
                        Choice("electric", lt("Electric", "Điện"), vnd(60)),
                        Choice("fingerstyle", lt("Fingerstyle", "Fingerstyle"), vnd(40)),
                    ),
                ),
                SingleSelectOption(
                    id = "level",
                    title = lt("Level", "Trình độ"),
                    choices = listOf(
                        Choice("beginner", lt("Beginner", "Mới bắt đầu")),
                        Choice("intermediate", lt("Intermediate", "Trung cấp")),
                    ),
                ),
            ),
            reviews = listOf(
                Review("r1", "Long", 5, lt("Learned my first song in a week.", "Học được bài hát đầu tiên trong một tuần.")),
            ),
        ),
        Service(
            id = "flute-lessons",
            categoryId = "music",
            icon = "music_note",
            title = lt("Flute Lessons", "Lớp học sáo"),
            description = lt("Classical and modern flute for all levels.", "Sáo cổ điển và hiện đại cho mọi trình độ."),
            rating = 4.8,
            reviewCount = 38,
            basePrice = vnd(320),
            bookingTypes = trialAndFollowup,
            durations = listOf(
                DurationOption("45", minutes = 45, price = vnd(320)),
                DurationOption("60", minutes = 60, price = vnd(420)),
            ),
            provider = jacob,
            options = listOf(
                SingleSelectOption(
                    id = "level",
                    title = lt("Level", "Trình độ"),
                    choices = listOf(
                        Choice("beginner", lt("Beginner", "Mới bắt đầu")),
                        Choice("intermediate", lt("Intermediate", "Trung cấp"), vnd(50)),
                        Choice("advanced", lt("Advanced", "Nâng cao"), vnd(100)),
                    ),
                ),
                SingleSelectOption(
                    id = "location",
                    title = lt("Location", "Địa điểm"),
                    choices = listOf(
                        Choice("studio", lt("At the studio", "Tại phòng học")),
                        Choice("home", lt("At your home", "Tại nhà bạn"), vnd(80)),
                    ),
                ),
            ),
            reviews = listOf(
                Review("r1", "My", 5, lt("Beautiful tone in just a few lessons.", "Âm thanh tuyệt đẹp chỉ sau vài buổi học.")),
            ),
        ),

        // ===================== LANGUAGE LEARNING =====================
        Service(
            id = "chinese-language-learning",
            categoryId = "languages",
            icon = "translate",
            title = lt("Chinese Language Learning", "Học tiếng Trung"),
            description = lt("Mandarin lessons from greetings to fluency.", "Học tiếng Quan Thoại từ chào hỏi đến thành thạo."),
            rating = 4.9,
            reviewCount = 41,
            basePrice = vnd(300),
            bookingTypes = trialAndFollowup,
            durations = listOf(
                DurationOption("60", minutes = 60, price = vnd(300)),
                DurationOption("90", minutes = 90, price = vnd(420)),
            ),
            provider = jacob,
            options = listOf(
                SingleSelectOption(
                    id = "level",
                    title = lt("Level", "Trình độ"),
                    choices = listOf(
                        Choice("beginner", lt("Beginner (HSK 1-2)", "Mới bắt đầu (HSK 1-2)")),
                        Choice("intermediate", lt("Intermediate (HSK 3-4)", "Trung cấp (HSK 3-4)"), vnd(50)),
                        Choice("advanced", lt("Advanced (HSK 5-6)", "Nâng cao (HSK 5-6)"), vnd(100)),
                    ),
                ),
                SingleSelectOption(
                    id = "focus",
                    title = lt("Focus", "Trọng tâm"),
                    choices = listOf(
                        Choice("conversation", lt("Conversation", "Giao tiếp")),
                        Choice("business", lt("Business Chinese", "Tiếng Trung thương mại"), vnd(60)),
                        Choice("characters", lt("Reading & Writing", "Đọc & Viết"), vnd(40)),
                    ),
                ),
            ),
            reviews = listOf(
                Review("r1", "Hoa", 5, lt("Made tones finally click for me.", "Giúp tôi cuối cùng cũng nắm được thanh điệu.")),
            ),
        ),

        // ===================== WORKSHOPS =====================
        Service(
            id = "pottery-workshop",
            categoryId = "workshops",
            icon = "palette",
            title = lt("Pottery Workshop", "Hội thảo gốm sứ"),
            description = lt("Throw your own clay.", "Tự tay nặn gốm."),
            rating = 4.8,
            reviewCount = 45,
            basePrice = vnd(400),
            bookingTypes = trialAndFollowup,
            durations = listOf(
                DurationOption("120", minutes = 120, price = vnd(400)),
                DurationOption("180", minutes = 180, price = vnd(550)),
            ),
            provider = jacob,
            options = listOf(
                CounterOption(
                    id = "seats",
                    title = lt("Seats", "Số chỗ"),
                    unitLabel = lt("Seat", "Chỗ"),
                    unitPrice = vnd(130),
                    min = 1,
                    max = 8,
                    default = 1,
                ),
                SingleSelectOption(
                    id = "piece",
                    title = lt("What to make", "Sản phẩm"),
                    choices = listOf(
                        Choice("mug", lt("Mug", "Cốc")),
                        Choice("bowl", lt("Bowl", "Bát")),
                        Choice("vase", lt("Vase", "Bình hoa"), vnd(60)),
                    ),
                ),
            ),
            reviews = listOf(
                Review("r1", "Hana", 5, lt("So relaxing and fun!", "Rất thư giãn và vui!")),
            ),
        ),
        Service(
            id = "watercolor-workshop",
            categoryId = "workshops",
            icon = "brush",
            title = lt("Watercolor Workshop", "Hội thảo màu nước"),
            description = lt("Paint a small landscape.", "Vẽ một bức phong cảnh nhỏ."),
            rating = 4.6,
            reviewCount = 33,
            basePrice = vnd(300),
            bookingTypes = trialAndFollowup,
            durations = listOf(
                DurationOption("90", minutes = 90, price = vnd(300)),
                DurationOption("120", minutes = 120, price = vnd(400)),
            ),
            provider = jacob,
            options = listOf(
                CounterOption(
                    id = "seats",
                    title = lt("Seats", "Số chỗ"),
                    unitLabel = lt("Seat", "Chỗ"),
                    unitPrice = vnd(100),
                    min = 1,
                    max = 10,
                    default = 1,
                ),
                MultiSelectOption(
                    id = "supplies",
                    title = lt("Take-home supplies", "Dụng cụ mang về"),
                    choices = listOf(
                        Choice("brushset", lt("Brush set", "Bộ cọ"), vnd(90)),
                        Choice("paper", lt("Paper pad", "Tập giấy"), vnd(50)),
                    ),
                ),
            ),
            reviews = listOf(
                Review("r1", "Mia", 5, lt("Lovely calm afternoon.", "Một buổi chiều yên bình dễ thương.")),
                Review("r2", "Tuan", 4, lt("Great for beginners.", "Tuyệt cho người mới.")),
            ),
        ),

        // ===================== BABYSITTING =====================
        Service(
            id = "evening-babysitting",
            categoryId = "babysitting",
            icon = "crib",
            title = lt("Evening Babysitting", "Giữ trẻ buổi tối"),
            description = lt("Caring sitter for a night out.", "Người giữ trẻ tận tâm cho buổi tối của bạn."),
            rating = 4.9,
            reviewCount = 88,
            basePrice = vnd(200),
            bookingTypes = trialAndFollowup,
            durations = listOf(
                DurationOption("180", minutes = 180, price = vnd(200)),
                DurationOption("240", minutes = 240, price = vnd(260)),
            ),
            provider = jacob,
            options = listOf(
                CounterOption(
                    id = "children",
                    title = lt("Number of children", "Số lượng trẻ"),
                    unitLabel = lt("Child", "Trẻ"),
                    unitPrice = vnd(60),
                    min = 1,
                    max = 5,
                    default = 1,
                ),
                MultiSelectOption(
                    id = "extras",
                    title = lt("Extras", "Dịch vụ thêm"),
                    choices = listOf(
                        Choice("meal", lt("Prepare a meal", "Chuẩn bị bữa ăn"), vnd(80)),
                        Choice("bedtime", lt("Bedtime routine", "Cho bé đi ngủ"), vnd(50)),
                    ),
                ),
            ),
            reviews = listOf(
                Review("r1", "Ngoc", 5, lt("The kids adored her.", "Bọn trẻ rất quý cô ấy.")),
                Review("r2", "James", 5, lt("Punctual and trustworthy.", "Đúng giờ và đáng tin cậy.")),
            ),
        ),
        Service(
            id = "daytime-nanny",
            categoryId = "babysitting",
            icon = "stroller",
            title = lt("Daytime Nanny", "Bảo mẫu ban ngày"),
            description = lt("Full-day care and play.", "Chăm sóc và chơi cả ngày."),
            rating = 4.8,
            reviewCount = 51,
            basePrice = vnd(350),
            bookingTypes = trialAndFollowup,
            durations = listOf(
                DurationOption("240", minutes = 240, price = vnd(350)),
                DurationOption("480", minutes = 480, price = vnd(620)),
            ),
            provider = jacob,
            options = listOf(
                CounterOption(
                    id = "children",
                    title = lt("Number of children", "Số lượng trẻ"),
                    unitLabel = lt("Child", "Trẻ"),
                    unitPrice = vnd(80),
                    min = 1,
                    max = 4,
                    default = 1,
                ),
                SingleSelectOption(
                    id = "ageGroup",
                    title = lt("Age group", "Độ tuổi"),
                    choices = listOf(
                        Choice("infant", lt("Infant", "Sơ sinh"), vnd(60)),
                        Choice("toddler", lt("Toddler", "Tập đi")),
                        Choice("school", lt("School age", "Tuổi đi học")),
                    ),
                ),
            ),
            reviews = listOf(
                Review("r1", "Hoa", 5, lt("So patient with my toddler.", "Rất kiên nhẫn với con tôi.")),
            ),
        ),

        // ===================== PETSITTING =====================
        Service(
            id = "dog-walking",
            categoryId = "petsitting",
            icon = "pets",
            title = lt("Dog Walking", "Dắt chó đi dạo"),
            description = lt("Daily walks for your dog.", "Dắt chó đi dạo hằng ngày."),
            rating = 4.8,
            reviewCount = 96,
            basePrice = vnd(120),
            bookingTypes = trialAndFollowup,
            durations = listOf(
                DurationOption("30", minutes = 30, price = vnd(120)),
                DurationOption("60", minutes = 60, price = vnd(180)),
            ),
            provider = jacob,
            options = listOf(
                CounterOption(
                    id = "dogs",
                    title = lt("Number of dogs", "Số lượng chó"),
                    unitLabel = lt("Dog", "Chó"),
                    unitPrice = vnd(50),
                    min = 1,
                    max = 4,
                    default = 1,
                ),
                SingleSelectOption(
                    id = "size",
                    title = lt("Dog size", "Kích cỡ chó"),
                    choices = listOf(
                        Choice("small", lt("Small", "Nhỏ")),
                        Choice("medium", lt("Medium", "Vừa")),
                        Choice("large", lt("Large", "Lớn"), vnd(40)),
                    ),
                ),
            ),
            reviews = listOf(
                Review("r1", "Duc", 5, lt("My dog comes home happy and tired.", "Chú chó của tôi về nhà vui vẻ và mệt nhoài.")),
                Review("r2", "Emma", 5, lt("Sends photos every walk.", "Gửi ảnh mỗi lần đi dạo.")),
            ),
        ),
        Service(
            id = "in-home-pet-sitting",
            categoryId = "petsitting",
            icon = "home",
            title = lt("In-home Pet Sitting", "Trông thú cưng tại nhà"),
            description = lt("Care while you're away.", "Chăm sóc khi bạn vắng nhà."),
            rating = 4.9,
            reviewCount = 64,
            basePrice = vnd(300),
            bookingTypes = trialAndFollowup,
            durations = listOf(
                DurationOption("60", minutes = 60, price = vnd(300)),
                DurationOption("120", minutes = 120, price = vnd(420)),
            ),
            provider = jacob,
            options = listOf(
                CounterOption(
                    id = "pets",
                    title = lt("Number of pets", "Số lượng thú cưng"),
                    unitLabel = lt("Pet", "Thú cưng"),
                    unitPrice = vnd(70),
                    min = 1,
                    max = 5,
                    default = 1,
                ),
                MultiSelectOption(
                    id = "care",
                    title = lt("Care tasks", "Công việc chăm sóc"),
                    choices = listOf(
                        Choice("feeding", lt("Feeding", "Cho ăn"), vnd(40)),
                        Choice("litter", lt("Litter & clean-up", "Dọn vệ sinh"), vnd(50)),
                        Choice("meds", lt("Medication", "Cho uống thuốc"), vnd(60)),
                    ),
                ),
            ),
            reviews = listOf(
                Review("r1", "Lan", 5, lt("My cats were calm and well fed.", "Mèo của tôi rất bình tĩnh và được cho ăn đầy đủ.")),
            ),
        ),

        // ===================== PLANTSITTING =====================
        Service(
            id = "plantsitting",
            categoryId = "plantsitting",
            icon = "local_florist",
            title = lt("Plantsitting", "Trông cây hộ"),
            description = lt("Watering and care for your plants while you're away.", "Tưới nước và chăm sóc cây của bạn khi bạn vắng nhà."),
            rating = 4.9,
            reviewCount = 33,
            basePrice = vnd(150),
            bookingTypes = trialAndFollowup,
            durations = listOf(
                DurationOption("30", minutes = 30, price = vnd(150)),
                DurationOption("60", minutes = 60, price = vnd(220)),
            ),
            provider = jacob,
            options = listOf(
                CounterOption(
                    id = "plants",
                    title = lt("Number of plants", "Số lượng cây"),
                    unitLabel = lt("Plant", "Cây"),
                    unitPrice = vnd(20),
                    min = 1,
                    max = 20,
                    default = 3,
                ),
                MultiSelectOption(
                    id = "care",
                    title = lt("Care tasks", "Công việc chăm sóc"),
                    choices = listOf(
                        Choice("watering", lt("Watering", "Tưới nước")),
                        Choice("fertilizing", lt("Fertilizing", "Bón phân"), vnd(40)),
                        Choice("pruning", lt("Pruning & cleaning", "Cắt tỉa & lau lá"), vnd(50)),
                    ),
                ),
            ),
            reviews = listOf(
                Review("r1", "Emma", 5, lt("Came home to thriving plants!", "Về nhà thấy cây cối xanh tốt!")),
            ),
        ),

        // ===================== PHOTOGRAPHY =====================
        Service(
            id = "portrait-photography-jacob",
            categoryId = "photography",
            icon = "photo_camera",
            title = lt("Portrait Photography by Jacob", "Chụp ảnh chân dung bởi Jacob"),
            description = lt("Professional portraits and headshots.", "Ảnh chân dung và ảnh thẻ chuyên nghiệp."),
            rating = 4.8,
            reviewCount = 37,
            basePrice = vnd(400),
            bookingTypes = trialAndFollowup,
            durations = listOf(
                DurationOption("30", minutes = 30, price = vnd(400)),
                DurationOption("60", minutes = 60, price = vnd(700)),
            ),
            provider = jacob,
            options = listOf(
                SingleSelectOption(
                    id = "style",
                    title = lt("Photo Style", "Phong cách chụp"),
                    choices = listOf(
                        Choice("business", lt("Business / Formal", "Công sở / Trang trọng")),
                        Choice("casual", lt("Casual / Outdoor", "Tự nhiên / Ngoài trời"), vnd(50)),
                    ),
                ),
            ),
            reviews = listOf(
                Review("r1", "Oanh", 5, lt("Great headshots for my LinkedIn!", "Ảnh chân dung tuyệt vời cho LinkedIn!")),
            ),
        ),
        Service(
            id = "event-photography-luke",
            categoryId = "photography",
            icon = "camera_roll",
            title = lt("Event Photography by Jacob", "Chụp ảnh sự kiện bởi Jacob"),
            description = lt("Capture high-quality memories of your parties.", "Lưu giữ những kỷ niệm chất lượng cao cho bữa tiệc."),
            rating = 4.9,
            reviewCount = 52,
            basePrice = vnd(1000),
            bookingTypes = trialAndFollowup,
            durations = listOf(
                DurationOption("120", minutes = 120, price = vnd(1000)),
                DurationOption("240", minutes = 240, price = vnd(1800)),
            ),
            provider = jacob,
            options = listOf(
                CounterOption(
                    id = "photographers",
                    title = lt("Number of Photographers", "Số lượng thợ chụp"),
                    unitLabel = lt("Photographer", "Thợ"),
                    unitPrice = vnd(400),
                    min = 1,
                    max = 3,
                    default = 1,
                ),
            ),
            reviews = listOf(
                Review("r1", "Cuong", 5, lt("Incredible party photos!", "Hình ảnh bữa tiệc vô cùng tuyệt vời!")),
            ),
        ),

        // ===================== PET CARE =====================
        Service(
            id = "pet-grooming-linh",
            categoryId = "petcare",
            icon = "content_cut",
            title = lt("Pet Grooming by Jacob", "Tỉa lông thú cưng bởi Jacob"),
            description = lt("Wash, trim, and style for your pets.", "Tắm rửa, cắt tỉa và tạo kiểu cho thú cưng."),
            rating = 4.7,
            reviewCount = 43,
            basePrice = vnd(250),
            bookingTypes = trialAndFollowup,
            durations = listOf(
                DurationOption("60", minutes = 60, price = vnd(250)),
                DurationOption("90", minutes = 90, price = vnd(350)),
            ),
            provider = jacob,
            options = listOf(
                SingleSelectOption(
                    id = "serviceType",
                    title = lt("Grooming type", "Loại dịch vụ"),
                    choices = listOf(
                        Choice("basic", lt("Basic Wash & Brush", "Tắm & Chải cơ bản")),
                        Choice("full", lt("Full Trim & Styling", "Cắt tỉa & Tạo kiểu toàn diện"), vnd(100)),
                    ),
                ),
            ),
            reviews = listOf(
                Review("r1", "Thao", 5, lt("My poodle looks gorgeous!", "Chú poodle của tôi trông cực kỳ đáng yêu!")),
            ),
        ),
        Service(
            id = "pet-training-mark",
            categoryId = "petcare",
            icon = "sound_detection_dog_barking",
            title = lt("Pet Training by Jacob", "Huấn luyện thú cưng bởi Jacob"),
            description = lt("Basic obedience and behavior training.", "Huấn luyện vâng lời và hành vi cơ bản."),
            rating = 4.9,
            reviewCount = 28,
            basePrice = vnd(500),
            bookingTypes = trialAndFollowup,
            durations = listOf(
                DurationOption("60", minutes = 60, price = vnd(500)),
                DurationOption("120", minutes = 120, price = vnd(900)),
            ),
            provider = jacob,
            options = listOf(
                MultiSelectOption(
                    id = "focusAreas",
                    title = lt("Focus Training", "Trọng tâm huấn luyện"),
                    choices = listOf(
                        Choice("leash", lt("Leash walking", "Đi dây dắt"), vnd(50)),
                        Choice("barking", lt("Barking control", "Kiểm soát sủa"), vnd(80)),
                    ),
                ),
            ),
            reviews = listOf(
                Review("r1", "Phuong", 5, lt("Very patient trainer.", "Huấn luyện viên rất kiên nhẫn.")),
            ),
        ),

        // ===================== GARDENING =====================
        Service(
            id = "plant-care-thuy",
            categoryId = "gardening",
            icon = "yard",
            title = lt("Plant Care Consultation", "Tư vấn chăm sóc cây"),
            description = lt("Expert advice on sick plants and soil health.", "Lời khuyên chuyên gia cho cây bị bệnh và đất."),
            rating = 4.9,
            reviewCount = 31,
            basePrice = vnd(300),
            bookingTypes = trialAndFollowup,
            durations = listOf(
                DurationOption("60", minutes = 60, price = vnd(300)),
            ),
            provider = jacob,
            options = listOf(
                SingleSelectOption(
                    id = "plantType",
                    title = lt("Plant Type", "Loại cây"),
                    choices = listOf(
                        Choice("indoor", lt("Indoor Foliage", "Cây cảnh trong nhà")),
                        Choice("succulents", lt("Succulents & Cacti", "Sen đá & Xương rồng"), vnd(30)),
                    ),
                ),
            ),
            reviews = listOf(
                Review("r1", "Giang", 5, lt("My monstera is finally thriving!", "Cây trầu bà Nam Mỹ của tôi cuối cùng đã phát triển tươi tốt!")),
            ),
        ),

        // ===================== TOOL RENTALS =====================
        Service(
            id = "power-tool-rental-ha",
            categoryId = "rentals",
            icon = "construction",
            title = lt("Power Tool Rental", "Thuê dụng cụ điện"),
            description = lt("Rent drills, saws, and basic hand tools.", "Thuê máy khoan, máy cưa và dụng cụ cầm tay."),
            rating = 4.8,
            reviewCount = 61,
            basePrice = vnd(100),
            bookingTypes = trialAndFollowup,
            durations = listOf(
                DurationOption("120", minutes = 120, price = vnd(100)),
                DurationOption("480", minutes = 480, price = vnd(300)),
            ),
            provider = jacob,
            options = listOf(
                SingleSelectOption(
                    id = "toolType",
                    title = lt("Choose Tool", "Chọn dụng cụ"),
                    choices = listOf(
                        Choice("drill", lt("Power Drill", "Máy khoan điện")),
                        Choice("saw", lt("Electric Jigsaw", "Máy cưa lọng điện"), vnd(50)),
                    ),
                ),
            ),
            reviews = listOf(
                Review("r1", "Duy", 5, lt("Very convenient for weekend DIY.", "Rất tiện lợi cho việc tự sửa nhà cuối tuần.")),
            ),
        ),
        Service(
            id = "phone-charger-rental-vy",
            categoryId = "rentals",
            icon = "battery_charging_full",
            title = lt("Phone Charger & Powerbank", "Sạc điện thoại & Sạc dự phòng"),
            description = lt("Rent portable chargers for any device.", "Thuê sạc dự phòng di động cho mọi thiết bị."),
            rating = 4.5,
            reviewCount = 88,
            basePrice = vnd(20),
            bookingTypes = trialAndFollowup,
            durations = listOf(
                DurationOption("60", minutes = 60, price = vnd(20)),
                DurationOption("180", minutes = 180, price = vnd(50)),
            ),
            provider = jacob,
            options = listOf(
                SingleSelectOption(
                    id = "cableType",
                    title = lt("Cable Type", "Loại cáp sạc"),
                    choices = listOf(
                        Choice("usbc", lt("USB-C", "Cổng USB-C")),
                        Choice("lightning", lt("Lightning", "Cổng Lightning")),
                    ),
                ),
            ),
        ),

        // ===================== FURNITURE ASSEMBLY =====================
        Service(
            id = "furniture-assembly-my",
            categoryId = "furniture",
            icon = "build",
            title = lt("Furniture Assembly", "Lắp ráp đồ nội thất"),
            description = lt("Flatpack assembly for bed frames, desks, tables.", "Lắp ráp giường, bàn, tủ từ hộp phẳng."),
            rating = 4.9,
            reviewCount = 74,
            basePrice = vnd(300),
            bookingTypes = trialAndFollowup,
            durations = listOf(
                DurationOption("60", minutes = 60, price = vnd(300)),
                DurationOption("120", minutes = 120, price = vnd(550)),
            ),
            provider = jacob,
            options = listOf(
                CounterOption(
                    id = "items",
                    title = lt("Number of items", "Số lượng vật dụng"),
                    unitLabel = lt("Item", "Vật dụng"),
                    unitPrice = vnd(100),
                    min = 1,
                    max = 10,
                    default = 1,
                ),
            ),
            reviews = listOf(
                Review("r1", "Vinh", 5, lt("Assembled my complex wardrobe perfectly.", "Lắp ráp chiếc tủ quần áo phức tạp của tôi thật hoàn hảo.")),
            ),
        ),
        Service(
            id = "moving-help-huy",
            categoryId = "furniture",
            icon = "local_shipping",
            title = lt("Moving Help", "Hỗ trợ vận chuyển"),
            description = lt("Lifting, loading, and transporting boxes.", "Khênh vác, xếp đồ và vận chuyển thùng hàng."),
            rating = 4.7,
            reviewCount = 45,
            basePrice = vnd(500),
            bookingTypes = trialAndFollowup,
            durations = listOf(
                DurationOption("120", minutes = 120, price = vnd(500)),
                DurationOption("240", minutes = 240, price = vnd(900)),
            ),
            provider = jacob,
            options = listOf(
                SingleSelectOption(
                    id = "truckNeeded",
                    title = lt("Need Transport Truck?", "Cần xe tải chở đồ?"),
                    choices = listOf(
                        Choice("no", lt("Just lifting help", "Chỉ cần nâng vác")),
                        Choice("yes", lt("Lifting + Truck", "Nâng vác + Xe tải"), vnd(400)),
                    ),
                ),
            ),
        ),

        // ===================== HOUSE SITTING =====================
        Service(
            id = "vacation-housesitting-lan",
            categoryId = "housesitting",
            icon = "lock",
            title = lt("Vacation House Sitting", "Trông coi nhà ngày nghỉ"),
            description = lt("Keep your home secure and safe while you travel.", "Giữ nhà an toàn và an ninh khi bạn đi du lịch."),
            rating = 4.9,
            reviewCount = 29,
            basePrice = vnd(400),
            bookingTypes = trialAndFollowup,
            durations = listOf(
                DurationOption("1440", minutes = 1440, price = vnd(400)),
            ),
            provider = jacob,
            options = listOf(
                MultiSelectOption(
                    id = "tasks",
                    title = lt("Extra daily tasks", "Nhiệm vụ hàng ngày thêm"),
                    choices = listOf(
                        Choice("mail", lt("Collect Mail", "Nhận thư từ"), vnd(20)),
                        Choice("trash", lt("Take out trash", "Đổ rác"), vnd(10)),
                    ),
                ),
            ),
        ),
        Service(
            id = "overnight-housesitting-vy2",
            categoryId = "housesitting",
            icon = "home",
            title = lt("Overnight House Sitting", "Trông nhà qua đêm"),
            description = lt("In-house overnight stay with fish & cat feeding.", "Ở lại qua đêm và cho cá & mèo ăn."),
            rating = 4.8,
            reviewCount = 34,
            basePrice = vnd(600),
            bookingTypes = trialAndFollowup,
            durations = listOf(
                DurationOption("1440", minutes = 1440, price = vnd(600)),
            ),
            provider = jacob,
            options = listOf(
                CounterOption(
                    id = "pets",
                    title = lt("Number of pets to feed", "Số lượng thú cưng cần cho ăn"),
                    unitLabel = lt("Pet", "Thú cưng"),
                    unitPrice = vnd(40),
                    min = 1,
                    max = 5,
                    default = 1,
                ),
            ),
        ),

        // ===================== RIDE SHARING =====================
        Service(
            id = "commute-rideshare-chi",
            categoryId = "ridesharing",
            icon = "commute",
            title = lt("Daily Commute Ride Share", "Đi chung xe đi làm"),
            description = lt("Share rides to popular office districts.", "Chia sẻ chuyến xe đến các quận văn phòng phổ biến."),
            rating = 4.8,
            reviewCount = 95,
            basePrice = vnd(50),
            bookingTypes = trialAndFollowup,
            durations = listOf(
                DurationOption("30", minutes = 30, price = vnd(50)),
                DurationOption("60", minutes = 60, price = vnd(80)),
            ),
            provider = jacob,
            options = listOf(
                SingleSelectOption(
                    id = "destination",
                    title = lt("Destination District", "Quận đến"),
                    choices = listOf(
                        Choice("q1", lt("District 1", "Quận 1")),
                        Choice("q7", lt("District 7", "Quận 7"), vnd(20)),
                    ),
                ),
            ),
        ),
        Service(
            id = "airport-rideshare-thom",
            categoryId = "ridesharing",
            icon = "local_taxi",
            title = lt("Airport Ride Share", "Đi chung xe ra sân bay"),
            description = lt("Reliable airport drop-off and pickup sharing.", "Đón tiễn sân bay đáng tin cậy."),
            rating = 4.9,
            reviewCount = 112,
            basePrice = vnd(200),
            bookingTypes = trialAndFollowup,
            durations = listOf(
                DurationOption("45", minutes = 45, price = vnd(200)),
            ),
            provider = jacob,
            options = listOf(
                CounterOption(
                    id = "luggage",
                    title = lt("Number of luggage pieces", "Số lượng hành lý"),
                    unitLabel = lt("Bag", "Kiện"),
                    unitPrice = vnd(20),
                    min = 0,
                    max = 4,
                    default = 1,
                ),
            ),
        ),

        // ===================== IT HELP =====================
        Service(
            id = "wifi-setup-nam",
            categoryId = "ithelp",
            icon = "router",
            title = lt("Wi-Fi Setup & Troubleshooting", "Cài đặt & Sửa lỗi Wi-Fi"),
            description = lt("Optimize your home network and eliminate dead spots.", "Tối ưu hóa mạng gia đình và xóa điểm chết Wi-Fi."),
            rating = 4.7,
            reviewCount = 41,
            basePrice = vnd(300),
            bookingTypes = trialAndFollowup,
            durations = listOf(
                DurationOption("60", minutes = 60, price = vnd(300)),
            ),
            provider = jacob,
            options = listOf(
                SingleSelectOption(
                    id = "routerNeeded",
                    title = lt("Need New Router?", "Cần Router mới không?"),
                    choices = listOf(
                        Choice("no", lt("Use existing router", "Dùng router hiện có")),
                        Choice("yes", lt("Buy high-speed router", "Mua router tốc độ cao"), vnd(800)),
                    ),
                ),
            ),
        ),
        Service(
            id = "pc-reinstall-an",
            categoryId = "ithelp",
            icon = "computer",
            title = lt("Laptop & PC OS Reinstall", "Cài đặt lại hệ điều hành PC"),
            description = lt("Clean install of Windows or macOS with drivers.", "Cài đặt sạch Windows hoặc macOS đầy đủ driver."),
            rating = 4.9,
            reviewCount = 38,
            basePrice = vnd(400),
            bookingTypes = trialAndFollowup,
            durations = listOf(
                DurationOption("90", minutes = 90, price = vnd(400)),
            ),
            provider = jacob,
            options = listOf(
                MultiSelectOption(
                    id = "software",
                    title = lt("Additional Software", "Phần mềm bổ sung"),
                    choices = listOf(
                        Choice("office", lt("Office Suite", "Bộ ứng dụng văn phòng"), vnd(150)),
                        Choice("backup", lt("Full Data Backup", "Sao lưu toàn bộ dữ liệu"), vnd(100)),
                    ),
                ),
            ),
        ),

        // ===================== TUTORING =====================
        Service(
            id = "math-tutoring-jacob",
            categoryId = "tutoring",
            icon = "calculate",
            title = lt("Math Tutoring by Jacob", "Gia sư toán bởi Jacob"),
            description = lt("Elementary and high school mathematics help.", "Gia sư toán cấp 1, cấp 2 và cấp 3."),
            rating = 4.8,
            reviewCount = 22,
            basePrice = vnd(250),
            bookingTypes = trialAndFollowup,
            durations = listOf(
                DurationOption("60", minutes = 60, price = vnd(250)),
                DurationOption("90", minutes = 90, price = vnd(350)),
            ),
            provider = jacob,
            options = listOf(
                SingleSelectOption(
                    id = "level",
                    title = lt("Grade Level", "Cấp học"),
                    choices = listOf(
                        Choice("primary", lt("Primary School", "Cấp 1")),
                        Choice("secondary", lt("Secondary School", "Cấp 2"), vnd(50)),
                        Choice("high", lt("High School", "Cấp 3"), vnd(100)),
                    ),
                ),
            ),
        ),
        Service(
            id = "science-tutoring-luke",
            categoryId = "tutoring",
            icon = "science",
            title = lt("Science Project Tutoring", "Gia sư dự án khoa học"),
            description = lt("Guidance for physics, chemistry, biology.", "Hướng dẫn vật lý, hóa học, sinh học."),
            rating = 4.9,
            reviewCount = 18,
            basePrice = vnd(300),
            bookingTypes = trialAndFollowup,
            durations = listOf(
                DurationOption("60", minutes = 60, price = vnd(300)),
            ),
            provider = jacob,
            options = listOf(
                MultiSelectOption(
                    id = "supplies",
                    title = lt("Materials & Kits", "Học cụ & Bộ thí nghiệm"),
                    choices = listOf(
                        Choice("kit", lt("Science Lab Kit", "Bộ dụng cụ thí nghiệm"), vnd(120)),
                    ),
                ),
            ),
        ),

        // ===================== GROCERY RUN =====================
        Service(
            id = "supermarket-grocery-linh",
            categoryId = "grocery",
            icon = "shopping_basket",
            title = lt("Supermarket Grocery Run", "Đi siêu thị hộ"),
            description = lt("Grocery shopping from major supermarkets.", "Mua hộ hàng hóa từ các siêu thị lớn."),
            rating = 4.7,
            reviewCount = 89,
            basePrice = vnd(100),
            bookingTypes = trialAndFollowup,
            durations = listOf(
                DurationOption("60", minutes = 60, price = vnd(100)),
            ),
            provider = jacob,
            options = listOf(
                CounterOption(
                    id = "itemsCount",
                    title = lt("Est. items to buy", "Số lượng vật phẩm ước tính"),
                    unitLabel = lt("Item", "Vật phẩm"),
                    unitPrice = vnd(10),
                    min = 1,
                    max = 30,
                    default = 5,
                ),
            ),
        ),
        Service(
            id = "local-market-grocery-mark",
            categoryId = "grocery",
            icon = "storefront",
            title = lt("Local Market Fresh Run", "Đi chợ truyền thống"),
            description = lt("Fresh ingredients from wet markets.", "Nguyên liệu tươi sống từ chợ truyền thống."),
            rating = 4.9,
            reviewCount = 67,
            basePrice = vnd(120),
            bookingTypes = trialAndFollowup,
            durations = listOf(
                DurationOption("60", minutes = 60, price = vnd(120)),
            ),
            provider = jacob,
            options = listOf(
                SingleSelectOption(
                    id = "marketType",
                    title = lt("Choose Market", "Chọn chợ"),
                    choices = listOf(
                        Choice("cho_chieu", lt("Afternoon Wet Market", "Chợ chiều")),
                        Choice("cho_sang", lt("Early Morning Wet Market", "Chợ sáng"), vnd(30)),
                    ),
                ),
            ),
        ),

        // ===================== COFFEE & TEA =====================
        Service(
            id = "coffee-tea-delivery-duc",
            categoryId = "coffeetea",
            icon = "local_cafe",
            title = lt("Coffee & Tea Delivery", "Giao cà phê & Trà"),
            description = lt("Fresh hot drinks delivered to your door.", "Giao thức uống nóng thơm ngon tận cửa."),
            rating = 4.8,
            reviewCount = 145,
            basePrice = vnd(40),
            bookingTypes = trialAndFollowup,
            durations = listOf(
                DurationOption("20", minutes = 20, price = vnd(40)),
            ),
            provider = jacob,
            options = listOf(
                SingleSelectOption(
                    id = "drinkType",
                    title = lt("Select Beverage", "Chọn đồ uống"),
                    choices = listOf(
                        Choice("black_coffee", lt("Black Coffee", "Cà phê đen")),
                        Choice("milk_coffee", lt("Milk Coffee", "Cà phê sữa"), vnd(10)),
                        Choice("peach_tea", lt("Peach Tea", "Trà đào"), vnd(15)),
                    ),
                ),
            ),
        ),
        Service(
            id = "tea-brewing-workshop-thuy",
            categoryId = "coffeetea",
            icon = "emoji_food_beverage",
            title = lt("Artisan Tea Workshop", "Hội thảo trà nghệ thuật"),
            description = lt("Learn artisan tea brewing and tasting.", "Học cách pha và thưởng thức trà nghệ thuật."),
            rating = 4.9,
            reviewCount = 36,
            basePrice = vnd(350),
            bookingTypes = trialAndFollowup,
            durations = listOf(
                DurationOption("90", minutes = 90, price = vnd(350)),
            ),
            provider = jacob,
            status = ServiceStatus.TEMPORARILY_UNAVAILABLE,
            options = listOf(
                CounterOption(
                    id = "attendees",
                    title = lt("Attendees count", "Số lượng người học"),
                    unitLabel = lt("Attendee", "Học viên"),
                    unitPrice = vnd(150),
                    min = 1,
                    max = 6,
                    default = 1,
                ),
            ),
        ),

        // ===================== VEGAN NUTRITION PLANNING =====================
        Service(
            id = "vegan-nutrition-planning",
            categoryId = "nutrition",
            icon = "eco",
            title = lt("Vegan Nutrition Planning", "Lên kế hoạch dinh dưỡng thuần chay"),
            description = lt("Personalized plant-based meal plans and guidance.", "Thực đơn thuần thực vật và tư vấn cá nhân hóa."),
            rating = 4.9,
            reviewCount = 28,
            basePrice = vnd(400),
            bookingTypes = trialAndFollowup,
            durations = listOf(
                DurationOption("60", minutes = 60, price = vnd(400)),
                DurationOption("90", minutes = 90, price = vnd(550)),
            ),
            provider = jacob,
            options = listOf(
                SingleSelectOption(
                    id = "goal",
                    title = lt("Goal", "Mục tiêu"),
                    choices = listOf(
                        Choice("balanced", lt("Balanced everyday eating", "Ăn uống cân bằng hằng ngày")),
                        Choice("weight", lt("Weight management", "Kiểm soát cân nặng"), vnd(60)),
                        Choice("athletic", lt("Athletic performance", "Hiệu suất thể thao"), vnd(80)),
                    ),
                ),
                MultiSelectOption(
                    id = "extras",
                    title = lt("Add-ons", "Tùy chọn thêm"),
                    choices = listOf(
                        Choice("groceryList", lt("Weekly grocery list", "Danh sách đi chợ hằng tuần"), vnd(40)),
                        Choice("recipes", lt("Custom recipe pack", "Bộ công thức nấu ăn riêng"), vnd(70)),
                    ),
                ),
            ),
            commentPrompt = CommentPrompt(
                label = lt("Tell me about your dietary needs", "Hãy cho tôi biết về nhu cầu ăn uống của bạn"),
                placeholder = lt("e.g. allergies, dislikes, daily routine...", "vd. dị ứng, món không thích, thói quen hằng ngày..."),
            ),
            reviews = listOf(
                Review("r1", "Mai", 5, lt("I finally feel energized on a vegan diet.", "Tôi cuối cùng cũng thấy tràn đầy năng lượng với chế độ thuần chay.")),
            ),
        ),

        // ===================== LIFE COACHING =====================
        Service(
            id = "life-coaching-linh",
            categoryId = "life_coaching",
            icon = "psychology",
            title = lt("Career & Life Coaching by Jacob", "Khai vấn sự nghiệp & đời sống bởi Jacob"),
            description = lt("Find clarity and direction for work and life.", "Tìm sự rõ ràng và định hướng cho công việc và cuộc sống."),
            rating = 4.8,
            reviewCount = 19,
            basePrice = vnd(900),
            bookingTypes = trialAndFollowup,
            durations = listOf(
                DurationOption("60", minutes = 60, price = vnd(900)),
                DurationOption("90", minutes = 90, price = vnd(1200)),
            ),
            provider = jacob,
            options = listOf(
                SingleSelectOption(
                    id = "focus",
                    title = lt("Focus area", "Lĩnh vực trọng tâm"),
                    choices = listOf(
                        Choice("career", lt("Career transition", "Chuyển đổi sự nghiệp")),
                        Choice("balance", lt("Work-life balance", "Cân bằng công việc - cuộc sống")),
                        Choice("confidence", lt("Confidence & mindset", "Sự tự tin & tư duy"), vnd(100)),
                    ),
                ),
            ),
            commentPrompt = CommentPrompt(
                label = lt("What would you like to work on?", "Bạn muốn cải thiện điều gì?"),
                placeholder = lt("e.g. changing jobs, managing stress...", "vd. đổi việc, kiểm soát căng thẳng..."),
            ),
            reviews = listOf(
                Review("r1", "Tâm", 5, lt("Helped me make a big career decision.", "Giúp tôi đưa ra một quyết định lớn về sự nghiệp.")),
            ),
        ),
    )
}
