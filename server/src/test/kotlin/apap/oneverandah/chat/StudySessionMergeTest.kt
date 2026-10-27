package apap.oneverandah.chat

import apap.oneverandah.api.StudyBuildState
import apap.oneverandah.api.StudyBuildStatus
import apap.oneverandah.api.StudyExamScene
import apap.oneverandah.api.StudyExampleSentence
import apap.oneverandah.api.StudyPlayChatMessage
import apap.oneverandah.api.StudyPlayScenario
import apap.oneverandah.api.StudyPlaySpeaker
import apap.oneverandah.api.StudySentence
import apap.oneverandah.api.StudySessionResponse
import apap.oneverandah.api.StudyStoryCharacter
import apap.oneverandah.api.StudyToken
import apap.oneverandah.api.StudyTokenStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class StudySessionMergeTest {
    @Test
    fun `stale client save cannot downgrade server fields and keeps longer play history`() {
        val exam = StudyExamScene(id = "server-exam", setting = "At the market.")
        val build = StudyBuildState(status = StudyBuildStatus.BUILDING, attempts = mapOf("DISSECT:0" to 1))
        val serverMessage = StudyPlayChatMessage(
            speaker = StudyPlaySpeaker.OTHER,
            text = "Xin chào.",
            description = "Server opening.",
            imageId = "message-image",
            imageUrl = "https://img/message",
        )
        val stored = record(
            StudySessionResponse(
                text = "Xin chào.",
                textLanguageTag = "vi",
                sentences = listOf(
                    StudySentence(
                        text = "Xin chào.",
                        tokens = listOf(
                            StudyToken(
                                text = "Xin",
                                status = StudyTokenStatus.READY,
                                literalMeaning = "hello",
                                contextualMeaning = "A greeting.",
                                imageId = "word-image",
                                imageUrl = "https://img/word",
                            ),
                        ),
                        imageId = "sentence-image",
                        imageUrl = "https://img/sentence",
                    ),
                ),
                characters = listOf(
                    StudyStoryCharacter(
                        "main",
                        "Lan",
                        "main",
                        "Portrait",
                        "portrait",
                        "https://img/portrait"
                    )
                ),
                playScenarios = listOf(
                    StudyPlayScenario(
                        id = "play-1",
                        description = "Server scenario.",
                        openingLine = "Server opening.",
                        imageId = "cover-image",
                        imageUrl = "https://img/cover",
                        messages = listOf(serverMessage),
                    ),
                ),
                practiceSentences = listOf(StudyExampleSentence("Server practice.", "Old practice.")),
                examScene = exam,
            ),
            build = build,
        )
        val incoming = record(
            StudySessionResponse(
                text = "Xin chào.",
                textLanguageTag = "vi",
                sentences = listOf(
                    StudySentence(
                        text = "Xin chào.",
                        tokens = listOf(
                            StudyToken(
                                text = "Xin",
                                status = StudyTokenStatus.PENDING,
                                imageId = "stale-word-image",
                                imageUrl = "https://img/stale-word",
                            ),
                        ),
                        imageId = "stale-sentence-image",
                        imageUrl = "https://img/stale-sentence",
                    ),
                ),
                playScenarios = listOf(
                    StudyPlayScenario(
                        id = "play-1",
                        description = "Stale scenario.",
                        openingLine = "Stale opening.",
                        imageId = "stale-cover-image",
                        imageUrl = "https://img/stale-cover",
                        messages = listOf(
                            serverMessage.copy(imageId = "stale-message-image", imageUrl = "https://img/stale-message"),
                            StudyPlayChatMessage(speaker = StudyPlaySpeaker.USER, text = "How are you?"),
                        ),
                    ),
                ),
                practiceSentences = listOf(StudyExampleSentence("Client practice.", "New practice.")),
            ),
        )

        val merged = StudySessionMerge.mergeClientSave(stored, incoming)
        val sentence = merged.session.sentences.single()
        val scenario = merged.session.playScenarios.single()

        assertEquals(StudyTokenStatus.READY, sentence.tokens.single().status)
        assertEquals("hello", sentence.tokens.single().literalMeaning)
        assertEquals("A greeting.", sentence.tokens.single().contextualMeaning)
        assertEquals("word-image", sentence.tokens.single().imageId)
        assertEquals("https://img/word", sentence.tokens.single().imageUrl)
        assertEquals("sentence-image", sentence.imageId)
        assertEquals("https://img/sentence", sentence.imageUrl)
        assertEquals(stored.session.characters, merged.session.characters)
        assertEquals("Server scenario.", scenario.description)
        assertEquals("Server opening.", scenario.openingLine)
        assertEquals("cover-image", scenario.imageId)
        assertEquals("https://img/cover", scenario.imageUrl)
        assertEquals(2, scenario.messages.size)
        assertEquals("message-image", scenario.messages.first().imageId)
        assertEquals("https://img/message", scenario.messages.first().imageUrl)
        assertEquals(incoming.session.practiceSentences, merged.session.practiceSentences)
        assertEquals(exam, merged.session.examScene)
        assertEquals(build, merged.build)
        assertEquals(stored.createdAt, merged.createdAt)
    }

    @Test
    fun `save in a different language does not carry server build content forward`() {
        val stored = record(
            StudySessionResponse(
                text = "Hello.",
                textLanguageTag = "en",
                sentences = listOf(
                    StudySentence(
                        text = "Hello.",
                        tokens = listOf(
                            StudyToken(
                                text = "Hello",
                                status = StudyTokenStatus.READY,
                                literalMeaning = "greeting"
                            )
                        ),
                    ),
                ),
            ),
            build = StudyBuildState(status = StudyBuildStatus.COMPLETE),
        )
        val incoming = record(
            StudySessionResponse(
                text = "Hello.",
                textLanguageTag = "en",
                sentences = listOf(
                    StudySentence(
                        text = "Hello.",
                        tokens = listOf(StudyToken(text = "Hello", status = StudyTokenStatus.PENDING)),
                    ),
                ),
            ),
            languageTag = "fr",
        )

        val merged = StudySessionMerge.mergeClientSave(stored, incoming)

        assertEquals(StudyTokenStatus.PENDING, merged.session.sentences.single().tokens.single().status)
        assertEquals(null, merged.build)
    }

    private fun record(
        session: StudySessionResponse,
        languageTag: String = "en",
        build: StudyBuildState? = null,
    ) = SavedStudySessionRecord(
        id = "study-1",
        deviceTokenHash = "device-hash",
        sourceMessageId = "source-1",
        languageTag = languageTag,
        text = session.text,
        textLanguageTag = session.textLanguageTag,
        session = session,
        createdAt = 10L,
        updatedAt = 20L,
        name = "Session",
        build = build,
    )
}
