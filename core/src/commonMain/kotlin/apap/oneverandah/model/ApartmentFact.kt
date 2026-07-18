package apap.oneverandah.model

data class ApartmentFact(
    val id: String,
    val title: LocalizedText,
    val body: LocalizedText,
    val icon: String,
    val keywords: List<String>,
)