package apap.oneverandah.data

import apap.oneverandah.model.ApartmentFact
import apap.oneverandah.model.LocalizedText

object FactsCatalog {
    val all: List<ApartmentFact> = listOf(
        ApartmentFact(
            id = "prime-location",
            title = LocalizedText(
                en = "Prime riverside location",
                vi = "Vị trí ven sông đắc địa",
                zh = "优越的河畔地段",
            ),
            body = LocalizedText(
                en = "One Verandah sits at 103 Bat Nan, Thanh My Loi Ward, Thu Duc City. It is about 5 minutes to Thu Thiem and 10 minutes to District 1, with easy links across Ho Chi Minh City.",
                vi = "One Verandah tọa lạc tại 103 Bát Nàn, phường Thạnh Mỹ Lợi, TP. Thủ Đức. Chỉ khoảng 5 phút đến Thủ Thiêm và 10 phút đến Quận 1, kết nối thuận tiện khắp TP.HCM.",
                zh = "One Verandah 位于胡志明市守德市盛美利坊 Bát Nàn 路 103 号。距守添约 5 分钟、第一郡约 10 分钟，交通便利。",
            ),
            icon = "location_on",
            keywords = listOf("location", "address", "thu thiem", "district 1", "thanh my loi", "river"),
        ),
        ApartmentFact(
            id = "project-scale",
            title = LocalizedText(
                en = "Five towers, 779 homes",
                vi = "Năm tháp, 779 căn hộ",
                zh = "五座塔楼，779 套住宅",
            ),
            body = LocalizedText(
                en = "Developed by Mapletree on 16,684 m² with 35% building density, One Verandah has five 22-storey blocks—Luminosa, Ciel, Jardin, Viento, and Soleil—plus 779 apartments and 27 shophouses.",
                vi = "Do Mapletree phát triển trên 16.684 m² với mật độ xây dựng 35%, One Verandah gồm năm block 22 tầng—Luminosa, Ciel, Jardin, Viento và Soleil—cùng 779 căn hộ và 27 shophouse.",
                zh = "由 Mapletree 开发，占地 16,684 平方米，建筑密度 35%。One Verandah 有五座 22 层塔楼——Luminosa、Ciel、Jardin、Viento 和 Soleil——共 779 套公寓和 27 间商铺。",
            ),
            icon = "apartment",
            keywords = listOf("towers", "blocks", "mapletree", "scale", "shophouse", "luminosa", "ciel", "jardin", "viento", "soleil"),
        ),
        ApartmentFact(
            id = "unit-types",
            title = LocalizedText(
                en = "Layouts from 1BR to penthouse",
                vi = "Bố cục từ 1 phòng ngủ đến penthouse",
                zh = "户型从一房到顶层公寓",
            ),
            body = LocalizedText(
                en = "Homes include 1BR (55–56 m²), 2BR (76–85 m²), 3BR (104–111 m²), duplex (142–231 m²), and penthouse (308–312 m²). Designs emphasize natural light, airflow, and Saigon River views.",
                vi = "Căn hộ gồm 1PN (55–56 m²), 2PN (76–85 m²), 3PN (104–111 m²), duplex (142–231 m²) và penthouse (308–312 m²). Thiết kế tối ưu ánh sáng tự nhiên, gió trời và tầm nhìn sông Sài Gòn.",
                zh = "户型包括一房（55–56 ㎡）、两房（76–85 ㎡）、三房（104–111 ㎡）、复式（142–231 ㎡）和顶层公寓（308–312 ㎡）。设计注重自然采光、通风与西贡河景。",
            ),
            icon = "home",
            keywords = listOf("apartment", "bedroom", "duplex", "penthouse", "size", "layout", "unit"),
        ),
        ApartmentFact(
            id = "amenities-park",
            title = LocalizedText(
                en = "Over 8,000 m² of amenities",
                vi = "Hơn 8.000 m² tiện ích",
                zh = "超过 8,000 平方米配套",
            ),
            body = LocalizedText(
                en = "More than 8,000 m² and 32 internal amenities serve all ages—pools, gym, tennis and basketball courts, mini soccer, jogging track, kids zones, BBQ pavilions, yoga deck, herb garden, and more.",
                vi = "Hơn 8.000 m² với 32 tiện ích nội khu phục vụ mọi lứa tuổi—hồ bơi, gym, sân tennis và bóng rổ, sân bóng mini, đường chạy bộ, khu trẻ em, nhà BBQ, sàn yoga, vườn thảo mộc và nhiều hơn nữa.",
                zh = "超过 8,000 平方米、32 项内部配套覆盖全龄需求——泳池、健身房、网球与篮球场、迷你足球场、慢跑道、儿童区、烧烤亭、瑜伽台、香草园等。",
            ),
            icon = "pool",
            keywords = listOf("amenities", "pool", "gym", "tennis", "kids", "bbq", "park", "facilities"),
        ),
        ApartmentFact(
            id = "ready-to-move-in",
            title = LocalizedText(
                en = "Ready to move in",
                vi = "Sẵn sàng dọn vào ở",
                zh = "即可入住",
            ),
            body = LocalizedText(
                en = "Estimated completion was Q2/2023. Homes are ready to move in with basic furnished handover. Locals receive freehold ownership; foreigners can hold 50-year ownership.",
                vi = "Dự kiến hoàn thành quý 2/2023. Căn hộ sẵn sàng dọn vào ở, bàn giao nội thất cơ bản. Người Việt sở hữu lâu dài; người nước ngoài sở hữu 50 năm.",
                zh = "预计于 2023 年第二季度竣工。房屋已可入住，基础精装修交付。本地居民可获永久产权；外籍人士可享 50 年产权。",
            ),
            icon = "key",
            keywords = listOf("handover", "completion", "ownership", "freehold", "furnished", "move in"),
        ),
        ApartmentFact(
            id = "last-luxury-riverside",
            title = LocalizedText(
                en = "Last luxury riverside project here",
                vi = "Dự án ven sông cao cấp cuối cùng tại đây",
                zh = "本区最后的高端河畔项目",
            ),
            body = LocalizedText(
                en = "One Verandah is the last luxury riverside apartment project in Thanh My Loi, District 2 (now Thu Duc City). Panoramic city and Saigon River views and a rare high-end supply set it apart.",
                vi = "One Verandah là dự án căn hộ cao cấp ven sông cuối cùng tại Thạnh Mỹ Lợi, Quận 2 (nay là TP. Thủ Đức). Tầm nhìn toàn cảnh thành phố và sông Sài Gòn cùng nguồn cung cao cấp khan hiếm tạo nên điểm khác biệt.",
                zh = "One Verandah 是盛美利（原第二郡，现守德市）最后的高端河畔公寓项目。城市与西贡河全景，以及稀缺的高端供应，使其脱颖而出。",
            ),
            icon = "water",
            keywords = listOf("luxury", "riverside", "saigon river", "rare", "thanh my loi", "thu duc"),
        ),
    )

    fun factById(id: String?): ApartmentFact? = all.firstOrNull { it.id == id }
}
