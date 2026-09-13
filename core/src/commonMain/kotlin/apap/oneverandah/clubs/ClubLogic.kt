package apap.oneverandah.clubs

/**
 * Pure Club rules shared by the server and client.
 *
 * Keep this free of I/O so public cards, search, and join/create sanitizing
 * stay deterministic.
 */
object ClubLogic {
    const val BLOCK_TEXT = "text"
    const val BLOCK_IMAGE = "image"

    const val MAX_NAME_LENGTH = 80
    const val MAX_TAGLINE_LENGTH = 120
    const val MAX_DESCRIPTION_LENGTH = 2_000
    const val MAX_CATEGORY_LENGTH = 40
    const val MAX_ACTIVITY_LENGTH = 80
    const val MAX_ACTIVITIES = 3
    const val MAX_PHOTOS = 20
    const val MAX_CONTENT_BLOCKS = 40
    const val MAX_BLOCK_TEXT_LENGTH = 20_000
    const val MAX_QUESTION_LENGTH = 160
    const val MAX_JOIN_QUESTIONS = 3
    const val MAX_ANSWER_LENGTH = 500
    const val MAX_MESSAGE_LENGTH = 500
    const val MAX_HOST_NAME_LENGTH = 80

    fun sanitizeName(raw: String?): String = collapseSpaces(raw).take(MAX_NAME_LENGTH)

    fun sanitizeTagline(raw: String?): String = collapseSpaces(raw).take(MAX_TAGLINE_LENGTH)

    fun sanitizeDescription(raw: String?): String = raw?.trim().orEmpty().take(MAX_DESCRIPTION_LENGTH)

    fun sanitizeCategory(raw: String?): String = collapseSpaces(raw).take(MAX_CATEGORY_LENGTH)

    fun sanitizeHostName(raw: String?): String = collapseSpaces(raw).take(MAX_HOST_NAME_LENGTH)

    fun sanitizeZalo(raw: String?): String =
        raw.orEmpty().filter { it.isDigit() || it == '+' || it.isWhitespace() }.trim()

    fun sanitizeMessage(raw: String?): String = raw?.trim().orEmpty().take(MAX_MESSAGE_LENGTH)

    fun sanitizeActivities(raw: List<String>?): List<String> =
        raw.orEmpty()
            .map { collapseSpaces(it).take(MAX_ACTIVITY_LENGTH) }
            .filter { it.isNotEmpty() }
            .take(MAX_ACTIVITIES)

    fun sanitizeJoinQuestions(raw: List<String>?): List<String> =
        raw.orEmpty()
            .map { collapseSpaces(it).take(MAX_QUESTION_LENGTH) }
            .filter { it.isNotEmpty() }
            .take(MAX_JOIN_QUESTIONS)

    fun sanitizePhotoUrls(raw: List<String>?): List<String> =
        raw.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(MAX_PHOTOS)

    fun sanitizeContent(raw: List<ClubContentBlock>?): List<ClubContentBlock> =
        raw.orEmpty().mapNotNull { block ->
            when (block.type.trim().lowercase()) {
                BLOCK_IMAGE -> {
                    val url = block.imageUrl.trim()
                    if (url.isEmpty()) null else ClubContentBlock(type = BLOCK_IMAGE, imageUrl = url)
                }
                else -> {
                    val text = block.text.trim().take(MAX_BLOCK_TEXT_LENGTH)
                    if (text.isEmpty()) null else ClubContentBlock(type = BLOCK_TEXT, text = text)
                }
            }
        }.take(MAX_CONTENT_BLOCKS)

    fun sanitizeAnswers(questions: List<String>, raw: List<String>?): List<ClubJoinAnswer> {
        val answers = raw.orEmpty()
        return questions.mapIndexed { index, question ->
            ClubJoinAnswer(
                question = question,
                answer = answers.getOrNull(index).orEmpty().trim().take(MAX_ANSWER_LENGTH),
            )
        }
    }

    fun publicCards(clubs: List<Club>): List<ClubCard> =
        clubs.sortedWith(newestFirst()).map { toPublicCard(it) }

    fun categories(clubs: List<Club>): List<String> =
        clubs.map { it.category.trim() }.filter { it.isNotEmpty() }.distinct().sorted()

    fun matchesQuery(card: ClubCard, query: String): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return true
        return listOf(card.name, card.tagline, card.description, card.category, card.hostName)
            .any { it.lowercase().contains(q) } ||
            card.activities.any { it.lowercase().contains(q) }
    }

    fun toPublicCard(club: Club): ClubCard = ClubCard(
        id = club.id,
        name = club.name,
        tagline = club.tagline,
        description = club.description,
        category = club.category,
        activities = club.activities,
        photoUrls = club.photoUrls,
        hostName = club.hostName,
        createdAt = club.createdAt,
    )

    fun toAdminView(club: Club, joins: List<ClubJoin> = emptyList()): AdminClubView = AdminClubView(
        id = club.id,
        name = club.name,
        tagline = club.tagline,
        description = club.description,
        category = club.category,
        activities = club.activities,
        photoUrls = club.photoUrls,
        content = club.content,
        joinQuestions = club.joinQuestions,
        hostName = club.hostName,
        hostZalo = club.hostZalo,
        createdAt = club.createdAt,
        updatedAt = club.updatedAt,
        joins = joins.sortedWith(newestJoinFirst()).map { toJoinView(it, club.name, club.hostZalo) },
    )

    fun toCreateRequestView(request: ClubCreateRequest): ClubCreateRequestView = ClubCreateRequestView(
        id = request.id,
        name = request.name,
        tagline = request.tagline,
        description = request.description,
        hostName = request.hostName,
        hostZalo = request.hostZalo,
        createdAt = request.createdAt,
    )

    fun toJoinView(join: ClubJoin, clubName: String = "", hostZalo: String = ""): ClubJoinView = ClubJoinView(
        id = join.id,
        clubId = join.clubId,
        clubName = clubName,
        name = join.name,
        zaloNumber = join.zaloNumber,
        hostZalo = hostZalo,
        answers = join.answers,
        message = join.message,
        createdAt = join.createdAt,
    )

    private fun collapseSpaces(raw: String?): String =
        raw?.trim()?.replace(Regex("\\s+"), " ").orEmpty()

    private fun newestFirst(): Comparator<Club> =
        compareByDescending<Club> { it.createdAt }.thenBy { it.id }

    private fun newestJoinFirst(): Comparator<ClubJoin> =
        compareByDescending<ClubJoin> { it.createdAt }.thenBy { it.id }
}
