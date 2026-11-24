package apap.oneverandah.chat

import apap.oneverandah.api.StudyBuildElement
import apap.oneverandah.api.StudyBuildElementKind
import apap.oneverandah.api.StudyBuildMode
import apap.oneverandah.api.StudyBuildRequest
import apap.oneverandah.api.StudyBuildState
import apap.oneverandah.api.StudyBuildStatus
import apap.oneverandah.api.StudyExamBeat
import apap.oneverandah.api.StudyExamBeatKind
import apap.oneverandah.api.StudyExamScene
import apap.oneverandah.api.StudyExampleSentence
import apap.oneverandah.api.StudyChunk
import apap.oneverandah.api.StudySentence
import apap.oneverandah.api.StudySessionResponse
import apap.oneverandah.api.StudyStoryCharacter
import apap.oneverandah.api.StudyTalkExpression
import apap.oneverandah.api.StudyTalkScene
import apap.oneverandah.api.StudyTalkTurn
import apap.oneverandah.api.StudyTalkWorld
import apap.oneverandah.api.StudyToken
import apap.oneverandah.api.StudyTokenStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StudyBuildPlannerTest {
    private val planner = StudyBuildPlanner(maxAttempts = 3)

    @Test
    fun `preparation and skeleton are strictly ordered`() {
        val preparing = record(
            state = StudyBuildState(status = StudyBuildStatus.PREPARING),
            request = StudyBuildRequest("Prompt", "en", mode = StudyBuildMode.INTENT),
        )
        assertEquals(listOf(StudyBuildElementKind.PREPARE), planner.plan(preparing).map { it.element.kind })

        val prepared = preparing.copy(
            text = "Prepared text.",
            textLanguageTag = "vi",
            session = session(text = "Prepared text.", sentences = emptyList()),
            build = StudyBuildState(status = StudyBuildStatus.BUILDING),
            buildRequest = null,
        )
        assertEquals(listOf(StudyBuildElementKind.SKELETON), planner.plan(prepared).map { it.element.kind })
    }

    @Test
    fun `sentence dissection and cast precede dependent images and exam`() {
        val skeleton = record(
            state = StudyBuildState(status = StudyBuildStatus.BUILDING),
            currentSession = session(
                sentences = listOf(
                    StudySentence("Xin.", listOf(StudyToken("Xin", StudyTokenStatus.PENDING))),
                ),
            ),
        )
        assertEquals(
            setOf(
                StudyBuildElementKind.NAME,
                StudyBuildElementKind.DISSECT,
                StudyBuildElementKind.CHARACTERS,
                StudyBuildElementKind.CHUNKS,
            ),
            planner.plan(skeleton).map { it.element.kind }.toSet(),
        )

        val dissected = skeleton.copy(
            name = "Greeting",
            session = skeleton.session.copy(
                sentences = listOf(
                    StudySentence(
                        "Xin.",
                        listOf(StudyToken("Xin", StudyTokenStatus.READY, literalMeaning = "hello")),
                    ),
                ),
                characters = listOf(StudyStoryCharacter("main", "Lan", "main", "Young neighbor")),
            ),
        )
        assertEquals(
            setOf(
                StudyBuildElementKind.PORTRAIT,
                StudyBuildElementKind.WORD_IMAGE,
                StudyBuildElementKind.CHUNKS,
                StudyBuildElementKind.WORD_RECALL_SCENARIOS,
                StudyBuildElementKind.TALK_WORLD,
            ),
            planner.plan(dissected).map { it.element.kind }.toSet(),
        )

        val portraitReady = dissected.copy(
            session = dissected.session.copy(
                sentences = listOf(
                    StudySentence(
                        "Xin.",
                        listOf(StudyToken("Xin", StudyTokenStatus.READY, literalMeaning = "hello")),
                    ),
                ),
                characters = listOf(
                    StudyStoryCharacter("main", "Lan", "main", "Young neighbor", imageUrl = "https://img/portrait"),
                ),
            ),
        )
        assertEquals(
            setOf(
                StudyBuildElementKind.WORD_IMAGE,
                StudyBuildElementKind.SENTENCE_IMAGE,
                StudyBuildElementKind.PLAY_SCENARIOS,
                StudyBuildElementKind.EXAM,
                StudyBuildElementKind.CHUNKS,
                StudyBuildElementKind.WORD_RECALL_SCENARIOS,
                StudyBuildElementKind.TALK_WORLD,
            ),
            planner.plan(portraitReady).map { it.element.kind }.toSet(),
        )
    }

    @Test
    fun `completed assets are skipped and failed elements are not planned again`() {
        val ready = record(
            state = StudyBuildState(
                status = StudyBuildStatus.BUILDING,
                failed = listOf(StudyBuildElement(StudyBuildElementKind.PLAY_COVER, scenarioId = "play-1")),
            ),
            currentSession = session(
                sentences = listOf(
                    StudySentence(
                        "Xin.",
                        listOf(
                            StudyToken(
                                "Xin",
                                StudyTokenStatus.READY,
                                literalMeaning = "hello",
                                imageUrl = "https://img/word",
                                needConversation = listOf(
                                    apap.oneverandah.api.StudyWordRecallLine("Mina", "___!", "___!"),
                                    apap.oneverandah.api.StudyWordRecallLine("Kai", "___!", "___!"),
                                ),
                            ),
                        ),
                        imageUrl = "https://img/sentence",
                        chunks = listOf(StudyChunk("Xin", "hello", "greeting", listOf(0))),
                        breakdownSuffixes = listOf("Xin."),
                        breakdownAudioReady = true,
                    ),
                ),
                characters = listOf(
                    StudyStoryCharacter("main", "Lan", "main", "Young neighbor", imageUrl = "https://img/portrait"),
                ),
                playScenarios = listOf(
                    apap.oneverandah.api.StudyPlayScenario("play-1", "At a market."),
                ),
                examScene = tenRoundExam(),
                talkWorld = completeTalkWorld(),
            ),
            name = "Greeting",
        )

        assertTrue(planner.plan(ready).isEmpty())
    }

    @Test
    fun `older Talk world waits for the used character portrait before building its expression`() {
        val source = completeTalkWorld()
        val world = source.copy(
            scenes = source.scenes.map { it.copy(backgroundImageUrl = null) },
            expressions = source.expressions.map { it.copy(imageUrl = null) },
        )
        val saved = record(
            state = StudyBuildState(status = StudyBuildStatus.PARTIAL),
            currentSession = session(
                sentences = listOf(StudySentence("Xin.", emptyList())),
                characters = listOf(
                    StudyStoryCharacter("main", "Lan", "main", "Neighbor"),
                    StudyStoryCharacter("other", "Kim", "friend", "Friend"),
                ),
                talkWorld = world,
            ),
        )
        val tasks = planner.plan(saved).map { it.element }
        assertEquals(6, tasks.count { it.kind == StudyBuildElementKind.TALK_SCENE_IMAGE })
        assertTrue(tasks.none { it.kind == StudyBuildElementKind.TALK_EXPRESSION })

        val portraitFailed = saved.copy(
            build = requireNotNull(saved.build).copy(
                failed = listOf(StudyBuildElement(StudyBuildElementKind.PORTRAIT, characterId = "main")),
            ),
        )
        assertEquals(
            listOf("main-neutral"),
            planner.plan(portraitFailed).filter { it.element.kind == StudyBuildElementKind.TALK_EXPRESSION }
                .map { it.element.expressionId },
        )
    }

    @Test
    fun `word images wait until dissection and character design work settles`() {
        val partiallyDissected = record(
            state = StudyBuildState(status = StudyBuildStatus.BUILDING),
            currentSession = session(
                sentences = listOf(
                    StudySentence("Xin.", listOf(StudyToken("Xin", StudyTokenStatus.READY, literalMeaning = "hello"))),
                    StudySentence("Chào.", listOf(StudyToken("Chào", StudyTokenStatus.PENDING))),
                ),
            ),
            name = "Greeting",
        )

        val pendingKinds = planner.plan(partiallyDissected).map { it.element.kind }.toSet()
        assertTrue(StudyBuildElementKind.DISSECT in pendingKinds)
        assertTrue(StudyBuildElementKind.CHUNKS in pendingKinds)
        assertTrue(StudyBuildElementKind.CHARACTERS in pendingKinds)
        assertTrue(StudyBuildElementKind.WORD_IMAGE !in pendingKinds)

        val textWorkSettled = partiallyDissected.copy(
            session = partiallyDissected.session.copy(
                sentences = partiallyDissected.session.sentences.map { sentence ->
                    sentence.copy(tokens = sentence.tokens.map {
                        it.copy(
                            status = StudyTokenStatus.READY,
                            literalMeaning = "meaning"
                        )
                    })
                },
                characters = listOf(StudyStoryCharacter("main", "Lan", "main", "Young neighbor")),
            ),
        )
        val dependentWork = planner.plan(textWorkSettled).map { it.element.kind }.toSet()
        assertTrue(StudyBuildElementKind.WORD_IMAGE in dependentWork)
        assertTrue(StudyBuildElementKind.CHUNKS in dependentWork)
        assertTrue(StudyBuildElementKind.PORTRAIT in dependentWork)
        assertTrue(StudyBuildElementKind.WORD_RECALL_SCENARIOS in dependentWork)
    }

    @Test
    fun `missing scenarios backfill completed sessions`() {
        val complete = record(
            state = StudyBuildState(status = StudyBuildStatus.COMPLETE),
            currentSession = session(
                sentences = listOf(
                    StudySentence(
                        "Xin.",
                        listOf(StudyToken("Xin", StudyTokenStatus.READY, literalMeaning = "hello")),
                    ),
                ),
            ),
        )

        assertEquals(
            setOf(StudyBuildElementKind.CHUNKS, StudyBuildElementKind.WORD_RECALL_SCENARIOS),
            planner.plan(complete).map { it.element.kind }.toSet(),
        )
    }

    @Test
    fun `incomplete talk elements include failed and exhausted missing expressions`() {
        val expression = StudyBuildElement(
            kind = StudyBuildElementKind.TALK_EXPRESSION,
            expressionId = "main-neutral",
        )
        val originalWorld = completeTalkWorld()
        val world = originalWorld.copy(
            expressions = originalWorld.expressions.map {
                if (it.id == expression.expressionId) it.copy(imageUrl = null) else it
            },
        )
        val incomplete = record(
            state = StudyBuildState(
                status = StudyBuildStatus.PARTIAL,
                failed = listOf(expression),
                failureReasons = mapOf(expression.key to "provider timed out"),
                attempts = mapOf(expression.key to 3),
            ),
            currentSession = session(
                sentences = listOf(
                    StudySentence("Xin.", listOf(StudyToken("Xin", StudyTokenStatus.READY, literalMeaning = "hello"))),
                ),
                characters = listOf(
                    StudyStoryCharacter(
                        "main",
                        "Lan",
                        "main",
                        "Young neighbor",
                        imageUrl = "https://img/portrait",
                    ),
                ),
                talkWorld = world,
            ),
        )

        assertEquals(listOf(expression), planner.incompleteTalkElements(incomplete))
        assertTrue(planner.plan(incomplete).none { it.element.key == expression.key })
    }

    @Test
    fun `example dissection jobs have stable word keys and backfill completed sessions`() {
        val exampleToken = StudyToken(
            text = "လှ",
            status = StudyTokenStatus.READY,
            contextualMeaning = "very",
            exampleSentences = listOf(
                StudyExampleSentence("သူမက လှတယ်။", "She is very beautiful."),
                StudyExampleSentence("လှလွန်းတယ်။", "It is very beautiful."),
            ),
        )
        val currentSession = session(sentences = listOf(StudySentence("သင့်အပြုံးက လှတယ်။", listOf(exampleToken))))

        val building = record(
            state = StudyBuildState(status = StudyBuildStatus.BUILDING),
            currentSession = currentSession,
            name = "A smile",
        )
        assertEquals(
            listOf("EXAMPLE_DISSECT:0:0"),
            planner.plan(building).filter { it.element.kind == StudyBuildElementKind.EXAMPLE_DISSECT }
                .map { it.element.key },
        )

        val completed = building.copy(build = StudyBuildState(status = StudyBuildStatus.COMPLETE))
        assertTrue(planner.plan(completed).any { it.element.kind == StudyBuildElementKind.CHUNKS })
        assertEquals(
            listOf("EXAMPLE_DISSECT:0:0"),
            planner.plan(completed).filter { it.element.kind == StudyBuildElementKind.EXAMPLE_DISSECT }
                .map { it.element.key },
        )
    }

    private fun record(
        state: StudyBuildState,
        request: StudyBuildRequest? = null,
        currentSession: StudySessionResponse = session(),
        name: String = "",
    ) = SavedStudySessionRecord(
        id = "study-1",
        deviceTokenHash = "device-hash",
        languageTag = "en",
        text = currentSession.text,
        textLanguageTag = currentSession.textLanguageTag,
        session = currentSession,
        createdAt = 1L,
        updatedAt = 1L,
        name = name,
        build = state,
        buildRequest = request,
    )

    private fun session(
        text: String = "Xin.",
        sentences: List<StudySentence> = emptyList(),
        characters: List<StudyStoryCharacter> = emptyList(),
        playScenarios: List<apap.oneverandah.api.StudyPlayScenario> = emptyList(),
        examScene: StudyExamScene? = null,
        talkWorld: StudyTalkWorld? = null,
    ) = StudySessionResponse(
        text = text,
        textLanguageTag = "vi",
        sentences = sentences,
        characters = characters,
        playScenarios = playScenarios,
        examScene = examScene,
        talkWorld = talkWorld,
    )

    private fun tenRoundExam() = StudyExamScene(
        id = "exam-1",
        setting = "At a market.",
        outline = (1..10).map { "Round $it" },
        beats = (1..10).flatMap { round ->
            listOf(
                StudyExamBeat(
                    id = "listen-$round",
                    kind = StudyExamBeatKind.LISTEN,
                    speakerId = "main",
                    speakerName = "Lan",
                    line = "Xin.",
                    prompt = "What did Lan say?",
                    expectedMeaning = "A greeting.",
                    sourceSentence = "Xin.",
                ),
                StudyExamBeat(
                    id = "speak-$round",
                    kind = StudyExamBeatKind.SPEAK,
                    speakerId = "main",
                    speakerName = "Lan",
                    prompt = "Respond to Lan.",
                    expectedMeaning = "A suitable greeting.",
                    sourceSentence = "Xin.",
                ),
            )
        },
    )

    private fun completeTalkWorld() = StudyTalkWorld(
        setting = "A friendly neighborhood",
        scenes = List(6) { sceneIndex ->
            StudyTalkScene(
                id = "scene-${sceneIndex + 1}",
                title = "Scene ${sceneIndex + 1}",
                description = "A connected moment",
                backgroundImageUrl = "https://img/scene-${sceneIndex + 1}",
                turns = List(3) { turnIndex ->
                    StudyTalkTurn(
                        id = "turn-${turnIndex + 1}",
                        speakerCharacterId = "main",
                        expressionId = "main-neutral",
                        characterLine = "Xin.",
                        intention = "I want to say hello.",
                        reply = "Xin.",
                        characterAudioUrl = "https://audio/character",
                        replyAudioUrl = "https://audio/reply",
                    )
                },
            )
        },
        expressions = listOf("neutral", "happy", "curious", "concerned").map { emotion ->
            StudyTalkExpression("main-$emotion", "main", emotion, "https://img/$emotion")
        },
    )
}
