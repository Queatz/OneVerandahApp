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

/**
 * Concrete [Service] definitions: two per category, each combining a different
 * mix of [apap.oneverandah.model.ServiceOption]s so every booking screen looks
 * and behaves uniquely. Prices are in VND via [Money.thousands].
 */
internal object CatalogServices {

    // --- small local helpers to keep the data terse ----------------------

    private fun lt(en: String, vi: String) = LocalizedText(en, vi)
    private fun vnd(thousands: Long) = Money.thousands(thousands)

    private val trialAndFollowup = listOf(
        BookingTypeOption(
            "initial",
            lt("Initial trial booking", "Đặt lịch dùng thử lần đầu"),
            priceDelta = vnd(-50),
        ),
        BookingTypeOption("followup", lt("Follow-up booking", "Đặt lịch tiếp theo")),
    )

    // ---------------------------------------------------------------------

    val all: List<Service> = listOf(
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
            title = lt("1 hour massages by Luke", "Mát-xa 1 giờ bởi Luke"),
            description = lt("Deep tissue and relaxation.", "Mô sâu và thư giãn."),
            rating = 4.8,
            reviewCount = 89,
            basePrice = vnd(450),
            bookingTypes = trialAndFollowup,
            durations = listOf(
                DurationOption("60", minutes = 60, price = vnd(450)),
                DurationOption("90", minutes = 90, price = vnd(650)),
            ),
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
    )
}
