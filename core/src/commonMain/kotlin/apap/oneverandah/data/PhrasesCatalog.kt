package apap.oneverandah.data

import apap.oneverandah.model.LocalizedText
import apap.oneverandah.phrases.Phrase

/**
 * The twelve founding Earth-Mystical Parlance phrases from the definitive guide.
 *
 * Used as seed data for the phrasebook API (and as an offline fallback when the
 * backend is unreachable). Keeping the canonical deck in `commonMain` means the
 * web client and server share exactly the same founding set.
 */
object PhrasesCatalog {
    private val infinityPool = LocalizedText(
        en = "Infinity Pool",
        vi = "Hồ vô cực",
        zh = "无边泳池",
    )
    private val verandahGarden = LocalizedText(
        en = "Verandah Garden",
        vi = "Vườn Verandah",
        zh = "Verandah 花园",
    )
    private val communalTable = LocalizedText(
        en = "Communal Table",
        vi = "Bàn chung",
        zh = "公共餐桌",
    )
    private val skyLounge = LocalizedText(
        en = "Sky Lounge",
        vi = "Sảnh trời",
        zh = "空中酒廊",
    )
    private val gym = LocalizedText(
        en = "Gym",
        vi = "Phòng gym",
        zh = "健身房",
    )
    private val library = LocalizedText(
        en = "Library",
        vi = "Thư viện",
        zh = "图书馆",
    )
    private val bbqArea = LocalizedText(
        en = "BBQ Area",
        vi = "Khu BBQ",
        zh = "烧烤区",
    )
    private val lobby = LocalizedText(
        en = "Lobby",
        vi = "Sảnh",
        zh = "大堂",
    )
    private val gardenPath = LocalizedText(
        en = "Garden Path",
        vi = "Lối vườn",
        zh = "花园小径",
    )
    private val anywhere = LocalizedText(
        en = "Anywhere",
        vi = "Mọi nơi",
        zh = "随处",
    )

    val all: List<Phrase> = listOf(
        Phrase(
            id = "emp-infinity-pool-shoulders",
            locationKey = "infinity-pool",
            location = infinityPool,
            icon = "pool",
            spoken = LocalizedText(
                en = "This child's shoulders hum for healing hands.",
                vi = "Dạ, con mong tay lành chạm vai đang ngân.",
            ),
            verse = LocalizedText(
                en = "Deep in the shoulder's field, a hum arises — a tiny unsettled earth. This child names it aloud: may healing hands, if they are near, ripple stillness into that trembling place.",
                vi = "Trong cánh đồng vai, một tiếng ngân trỗi dậy — một chút đất chưa yên. Con cất lời gọi tên: mong tay lành, nếu ở gần, gợn sóng tĩnh lặng vào nơi đang run ấy.",
            ),
            rawAsk = LocalizedText(
                en = "Ask for a back/shoulder rub.",
                vi = "Nhờ xoa vai / lưng.",
                zh = "请人帮揉肩背。",
            ),
        ),
        Phrase(
            id = "emp-infinity-pool-sunscreen",
            locationKey = "infinity-pool",
            location = infinityPool,
            icon = "pool",
            spoken = LocalizedText(
                en = "This vessel welcomes the anointment of gentle shield.",
                vi = "Dạ, con đón nhận làn thoa dịu lành.",
            ),
            verse = LocalizedText(
                en = "This skin, a vulnerable earth, leans toward the light but asks for a shield. The child welcomes the anointment of sunscreen — a gentle, sacred layer against the burning rays.",
                vi = "Làn da, một mảnh đất mong manh, hướng về ánh sáng nhưng cần một lá chắn. Con đón nhận làn thoa dịu lành — một lớp thiêng nhẹ nhàng chống lại tia nắng gắt.",
            ),
            rawAsk = LocalizedText(
                en = "Ask for sunscreen help.",
                vi = "Nhờ bôi kem chống nắng.",
                zh = "请人帮忙涂防晒。",
            ),
        ),
        Phrase(
            id = "emp-verandah-garden-sit",
            locationKey = "verandah-garden",
            location = verandahGarden,
            icon = "park",
            spoken = LocalizedText(
                en = "This child's stillness reaches for a companion.",
                vi = "Dạ, con lặng mong người cùng ngồi.",
            ),
            verse = LocalizedText(
                en = "This bench breathes between alone and together. The child's stillness sends out a quiet thread, hoping it might weave into another's silence — no words, only shared presence.",
                vi = "Chiếc ghế này thở giữa một mình và có nhau. Sự tĩnh lặng của con nhả một sợi tơ mong manh, hy vọng nó dệt vào sự im lặng của ai đó — không lời, chỉ hiện diện chung.",
            ),
            rawAsk = LocalizedText(
                en = "Invite someone to sit quietly together.",
                vi = "Mời ngồi yên lặng cùng nhau.",
                zh = "邀请一起安静坐着。",
            ),
        ),
        Phrase(
            id = "emp-communal-table-drink",
            locationKey = "communal-table",
            location = communalTable,
            icon = "table_restaurant",
            spoken = LocalizedText(
                en = "This child's thirst flows toward a shared sip.",
                vi = "Dạ, con khát mong chung một mạch.",
            ),
            verse = LocalizedText(
                en = "Thirst sings a single note in the child's vessel. It flows toward the river of another's drink, longing to merge two sips into one cool, shared current.",
                vi = "Cơn khát hát một nốt đơn trong vật chứa của con. Nó chảy về phía dòng nước của người khác, mong hòa hai ngụm thành một dòng mát chung.",
            ),
            rawAsk = LocalizedText(
                en = "Ask to share a drink.",
                vi = "Mời cùng chia sẻ đồ uống.",
                zh = "邀请一起分享饮品。",
            ),
        ),
        Phrase(
            id = "emp-sky-lounge-view",
            locationKey = "sky-lounge",
            location = skyLounge,
            icon = "apartment",
            spoken = LocalizedText(
                en = "This child's gaze unfolds the inner glow of the horizon.",
                vi = "Dạ, con mở rạng chân trời trong mắt.",
            ),
            verse = LocalizedText(
                en = "The horizon blushes with unread colours. The child's eyes unfold this inner glow, hoping another observer might stand beside and name the shimmer together.",
                vi = "Chân trời ửng những sắc màu chưa đọc. Mắt con mở rạng ánh trong ấy, mong một người quan sát khác đứng bên và cùng gọi tên lấp lánh.",
            ),
            rawAsk = LocalizedText(
                en = "Invite to chat about the view.",
                vi = "Mời trò chuyện về khung cảnh.",
                zh = "邀请聊聊眼前的风景。",
            ),
        ),
        Phrase(
            id = "emp-gym-form",
            locationKey = "gym",
            location = gym,
            icon = "fitness_center",
            spoken = LocalizedText(
                en = "This child's form offers its life breath for another's eyes.",
                vi = "Dạ, con dâng sinh khí dáng này cho mắt bạn.",
            ),
            verse = LocalizedText(
                en = "This body reaches for a new form. The child offers its alignment to the eyes of another, trusting that if the life breath bends, a quiet whisper will guide it true.",
                vi = "Thân này vươn tới một dáng hình mới. Con dâng sự thẳng hàng cho mắt người, tin rằng nếu sinh khí lệch, một lời thì thầm sẽ dẫn đường đúng.",
            ),
            rawAsk = LocalizedText(
                en = "Ask for a spot / form check.",
                vi = "Nhờ bảo vệ / kiểm tra tư thế.",
                zh = "请人帮忙保护或检查动作。",
            ),
        ),
        Phrase(
            id = "emp-library-book",
            locationKey = "library",
            location = library,
            icon = "local_library",
            spoken = LocalizedText(
                en = "This child's mind weaves toward another's fragrant word.",
                vi = "Dạ, con dệt suy tưởng mong hương chữ người.",
            ),
            verse = LocalizedText(
                en = "A page breathes many possible worlds. The child's mind weaves through them, longing for another's fragrant word — a single thread pulled from the tangle of stories.",
                vi = "Một trang sách thở nhiều thế giới có thể. Suy tưởng con dệt qua chúng, mong một hương chữ của người — một sợi chỉ kéo ra từ mớ truyện rối.",
            ),
            rawAsk = LocalizedText(
                en = "Ask for a book recommendation.",
                vi = "Xin gợi ý một cuốn sách.",
                zh = "请人推荐一本书。",
            ),
        ),
        Phrase(
            id = "emp-library-quiet",
            locationKey = "library",
            location = library,
            icon = "local_library",
            spoken = LocalizedText(
                en = "This child's corner calls for a bloom of hush.",
                vi = "Dạ, con ươm tĩnh lặng nơi góc này.",
            ),
            verse = LocalizedText(
                en = "A corner of this room is being planted with silence. The child calls for the bloom of hush, inviting all passing souls to drop a seed of stillness for one full hour.",
                vi = "Một góc phòng này đang được gieo bằng sự im lặng. Con ươm nở tĩnh lặng, mời mọi linh hồn đi qua thả một hạt gió lặng trong một giờ tròn.",
            ),
            rawAsk = LocalizedText(
                en = "Ask to keep quiet for a while.",
                vi = "Nhờ giữ yên lặng một lúc.",
                zh = "请大家安静一会儿。",
            ),
        ),
        Phrase(
            id = "emp-bbq-bite",
            locationKey = "bbq-area",
            location = bbqArea,
            icon = "outdoor_grill",
            spoken = LocalizedText(
                en = "This child's spirit leans toward that essence on the grill.",
                vi = "Dạ, con hướng linh về miếng ấy trên lửa.",
            ),
            verse = LocalizedText(
                en = "The grill's fire spirits dance and leap. The child's own spirit leans toward that charred essence, hoping the fire-keeper might offer one tiny morsel of its soul.",
                vi = "Linh hồn lửa trên bếp nướng nhảy múa. Linh con hướng về tinh chất cháy xém ấy, mong người giữ lửa ban cho một miếng linh hồn nhỏ bé.",
            ),
            rawAsk = LocalizedText(
                en = "Ask for a bite of food.",
                vi = "Xin nếm một miếng đồ nướng.",
                zh = "请人分享一口烧烤。",
            ),
        ),
        Phrase(
            id = "emp-lobby-package",
            locationKey = "lobby",
            location = lobby,
            icon = "domain",
            spoken = LocalizedText(
                en = "This child notices the parcel's light has not yet manifested.",
                vi = "Dạ, con để ý kiện hàng chưa rạng.",
            ),
            verse = LocalizedText(
                en = "A parcel from faraway hands was expected. The child notices its light has not yet manifested in this place, and wonders if any other resident has glimpsed its glow.",
                vi = "Một kiện hàng từ bàn tay xa đã được mong. Con để ý ánh sáng của nó chưa hiển lộ nơi này, và tự hỏi có cư dân nào đã thấy tia rạng của nó.",
            ),
            rawAsk = LocalizedText(
                en = "Ask if someone's seen your package.",
                vi = "Hỏi xem ai thấy kiện hàng của mình chưa.",
                zh = "询问是否有人见过自己的包裹。",
            ),
        ),
        Phrase(
            id = "emp-garden-path-flower",
            locationKey = "garden-path",
            location = gardenPath,
            icon = "local_florist",
            spoken = LocalizedText(
                en = "This child's breath catches on an unnamed fragrance.",
                vi = "Dạ, con nín thở trước hương chưa tên.",
            ),
            verse = LocalizedText(
                en = "The garden exhales a secret fragrance. The child's breath catches — a name is missing. If a fellow earth-child knows it, may they simply speak the word into the air.",
                vi = "Khu vườn thở ra một hương bí mật. Hơi thở con nín lại — thiếu một cái tên. Nếu một người con của đất biết, xin hãy nói từ đó vào không trung.",
            ),
            rawAsk = LocalizedText(
                en = "Ask what flower that is.",
                vi = "Hỏi đó là hoa gì.",
                zh = "询问那是什么花。",
            ),
        ),
        Phrase(
            id = "emp-anywhere-goodnight",
            locationKey = "anywhere",
            location = anywhere,
            icon = "nights_stay",
            spoken = LocalizedText(
                en = "This child sends soft radiant dreams into the night.",
                vi = "Dạ, con gửi mơ lành rạng rỡ vào đêm.",
            ),
            verse = LocalizedText(
                en = "As daylight folds into the underworld, this child sends soft radiant dreams into the dark — may they land gently in the hearts of all who hear, seeding a bright and wholesome morning.",
                vi = "Khi ánh ngày gấp mình vào thế giới bên dưới, con gửi những giấc mơ lành rạng rỡ vào bóng tối — mong chúng nhẹ nhàng đậu xuống tim mọi người nghe, gieo một buổi sáng rực rỡ và lành.",
            ),
            rawAsk = LocalizedText(
                en = "Say goodnight.",
                vi = "Chúc ngủ ngon.",
                zh = "道晚安。",
            ),
        ),
    )

    fun phraseById(id: String?): Phrase? = all.firstOrNull { it.id == id }
}
