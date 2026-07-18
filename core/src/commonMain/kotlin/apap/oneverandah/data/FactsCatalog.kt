package apap.oneverandah.data

import apap.oneverandah.model.ApartmentFact
import apap.oneverandah.model.LocalizedText

object FactsCatalog {
    val all: List<ApartmentFact> = listOf(
        ApartmentFact(
            id = "balcony-drains",
            title = LocalizedText(
                en = "Keep balcony drains clear",
                vi = "Giữ thông thoáng lỗ thoát nước ban công",
                zh = "保持阳台排水口畅通",
            ),
            body = LocalizedText(
                en = "Clear leaves and debris from balcony drains before heavy rain. Free-flowing drains help prevent water from pooling near doors and walls.",
                vi = "Dọn lá cây và rác khỏi lỗ thoát nước ban công trước khi mưa lớn. Thoát nước thông suốt giúp tránh nước đọng gần cửa và tường.",
                zh = "大雨前请清理阳台排水口的落叶和杂物。排水顺畅有助于防止水积聚在门边和墙边。",
            ),
            icon = "water_drop",
            keywords = listOf("balcony", "drain", "rain", "flooding", "water"),
        ),
        ApartmentFact(
            id = "air-conditioner-filters",
            title = LocalizedText(
                en = "Clean air-conditioner filters",
                vi = "Vệ sinh bộ lọc máy lạnh",
                zh = "清洁空调滤网",
            ),
            body = LocalizedText(
                en = "A clean air-conditioner filter improves airflow and helps the unit run efficiently. Check it regularly and follow the manufacturer's cleaning instructions.",
                vi = "Bộ lọc máy lạnh sạch giúp luồng khí lưu thông tốt hơn và thiết bị hoạt động hiệu quả. Hãy kiểm tra thường xuyên và làm theo hướng dẫn vệ sinh của nhà sản xuất.",
                zh = "干净的空调滤网可以改善气流并提高运行效率。请定期检查，并按照制造商的说明进行清洁。",
            ),
            icon = "air",
            keywords = listOf("air conditioner", "air conditioning", "filter", "airflow", "energy"),
        ),
        ApartmentFact(
            id = "water-shutoff",
            title = LocalizedText(
                en = "Know your water shut-off valve",
                vi = "Biết vị trí van khóa nước",
                zh = "了解总水阀的位置",
            ),
            body = LocalizedText(
                en = "Locate your apartment's water shut-off valve before an emergency happens. Closing it quickly can limit damage from a leaking fixture or burst hose.",
                vi = "Hãy xác định vị trí van khóa nước của căn hộ trước khi có sự cố. Khóa van nhanh chóng có thể hạn chế thiệt hại do thiết bị rò rỉ hoặc ống nước bị vỡ.",
                zh = "请在发生紧急情况前找到公寓的总水阀。出现设备漏水或软管破裂时，迅速关闭水阀可减少损失。",
            ),
            icon = "valve",
            keywords = listOf("water", "valve", "leak", "plumbing", "emergency"),
        ),
        ApartmentFact(
            id = "electrical-breakers",
            title = LocalizedText(
                en = "Label the electrical breakers",
                vi = "Ghi nhãn các cầu dao điện",
                zh = "标记各个断路器",
            ),
            body = LocalizedText(
                en = "Clear breaker labels make it easier to isolate a circuit safely. Test and label circuits with suitable help rather than guessing during an electrical problem.",
                vi = "Nhãn cầu dao rõ ràng giúp ngắt riêng từng mạch điện an toàn hơn. Hãy kiểm tra và ghi nhãn với sự hỗ trợ phù hợp thay vì phỏng đoán khi có sự cố điện.",
                zh = "清晰的断路器标签便于安全隔离电路。请在适当协助下测试并标记电路，不要在电气故障时靠猜测。",
            ),
            icon = "electric_bolt",
            keywords = listOf("electricity", "breaker", "power", "circuit", "outage"),
        ),
        ApartmentFact(
            id = "humidity-and-condensation",
            title = LocalizedText(
                en = "Reduce indoor condensation",
                vi = "Giảm ngưng tụ hơi nước trong nhà",
                zh = "减少室内冷凝水",
            ),
            body = LocalizedText(
                en = "Ventilation helps moisture escape after cooking or showering. Wiping persistent condensation and keeping vents unobstructed can reduce dampness and mould growth.",
                vi = "Thông gió giúp hơi ẩm thoát ra sau khi nấu ăn hoặc tắm. Lau phần nước ngưng tụ kéo dài và giữ lỗ thông gió không bị che có thể giảm ẩm mốc.",
                zh = "烹饪或洗澡后通风有助于排出湿气。擦去持续出现的冷凝水并保持通风口畅通，可减少潮湿和霉菌滋生。",
            ),
            icon = "humidity_high",
            keywords = listOf("humidity", "condensation", "mould", "mold", "ventilation"),
        ),
        ApartmentFact(
            id = "smoke-alarms",
            title = LocalizedText(
                en = "Keep smoke alarms unobstructed",
                vi = "Giữ đầu báo khói không bị che chắn",
                zh = "不要遮挡烟雾报警器",
            ),
            body = LocalizedText(
                en = "Never cover or obstruct a smoke alarm. Report warning chirps or faults promptly and follow building guidance for testing and maintenance.",
                vi = "Không bao giờ che hoặc cản trở đầu báo khói. Hãy báo ngay khi thiết bị phát tiếng cảnh báo hoặc có lỗi và làm theo hướng dẫn của tòa nhà về kiểm tra, bảo trì.",
                zh = "切勿覆盖或遮挡烟雾报警器。如设备发出故障提示音或出现异常，请及时报告，并遵循楼宇的测试和维护指南。",
            ),
            icon = "detector_smoke",
            keywords = listOf("smoke", "alarm", "fire", "safety", "chirp"),
        ),
    )

    fun factById(id: String?): ApartmentFact? = all.firstOrNull { it.id == id }
}