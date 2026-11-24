package apap.oneverandah.screens

import apap.oneverandah.api.StudyTalkExpression
import apap.oneverandah.api.StudyBuildElement
import apap.oneverandah.api.StudyBuildElementKind
import apap.oneverandah.api.StudyBuildState
import apap.oneverandah.api.StudyBuildStatus
import apap.oneverandah.api.StudyStoryCharacter
import apap.oneverandah.api.StudyTalkAudioKind
import apap.oneverandah.api.StudyTalkScene
import apap.oneverandah.api.StudyTalkTurn
import apap.oneverandah.api.StudyTalkWorld
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StudyTalkTest {
    @Test
    fun `retry leaves the current turn selected`() {
        val progress = StudyTalkProgress(sceneIndex = 2, turnIndex = 1, phase = StudyTalkPhase.REVEAL)

        val retry = retryStudyTalkTurn(progress)

        assertEquals(2, retry.sceneIndex)
        assertEquals(1, retry.turnIndex)
        assertEquals(StudyTalkPhase.LISTEN, retry.phase)
    }

    @Test
    fun `revealing character line keeps the current turn and continue enters think phase`() {
        val progress = StudyTalkProgress(sceneIndex = 2, turnIndex = 1, phase = StudyTalkPhase.LISTEN)

        val revealed = revealStudyTalkCharacterLine(progress)
        val continued = continueStudyTalkToThink(revealed)

        assertEquals(2, revealed.sceneIndex)
        assertEquals(1, revealed.turnIndex)
        assertEquals(StudyTalkPhase.CHARACTER_LINE, revealed.phase)
        assertEquals(2, continued.sceneIndex)
        assertEquals(1, continued.turnIndex)
        assertEquals(StudyTalkPhase.THINK, continued.phase)
    }

    @Test
    fun `good rating advances turns across scenes and finishes the world`() {
        val world = sampleTalkWorld()
        val sameScene = advanceStudyTalkTurn(
            StudyTalkProgress(sceneIndex = 0, turnIndex = 0, phase = StudyTalkPhase.REVEAL),
            world,
        )
        val nextScene = advanceStudyTalkTurn(
            StudyTalkProgress(sceneIndex = 0, turnIndex = 2, phase = StudyTalkPhase.REVEAL),
            world,
        )
        val finished = advanceStudyTalkTurn(
            StudyTalkProgress(sceneIndex = 5, turnIndex = 2, phase = StudyTalkPhase.REVEAL),
            world,
        )

        assertEquals(StudyTalkProgress(0, 1, StudyTalkPhase.LISTEN), sameScene)
        assertEquals(StudyTalkProgress(1, 0, StudyTalkPhase.LISTEN), nextScene)
        assertEquals(StudyTalkProgress(5, 2, StudyTalkPhase.COMPLETE), finished)
    }

    @Test
    fun `conversation recap preserves scenes speaker lines labels and replay media`() {
        val source = sampleTalkWorld()
        val world = source.copy(
            scenes = source.scenes.mapIndexed { sceneIndex, scene ->
                when (sceneIndex) {
                    0 -> scene.copy(
                        turns = listOf(
                            scene.turns[0].copy(
                                characterLine = "Xin chào.",
                                reply = "Chào!",
                                characterAudioUrl = "lan-hello.mp3",
                                replyAudioUrl = "you-hello.mp3",
                            ),
                            scene.turns[1].copy(characterLine = " ", reply = ""),
                        ),
                    )

                    1 -> scene.copy(
                        turns = listOf(
                            scene.turns[0].copy(
                                speakerCharacterId = "missing-character",
                                characterLine = "Welcome back.",
                                reply = "",
                                characterAudioUrl = "welcome.mp3",
                                replyAudioUrl = null,
                            ),
                        ),
                    )

                    else -> scene
                }
            },
            expressions = source.expressions.map { it.copy(imageUrl = "portrait.png") },
        )
        val characters = listOf(
            StudyStoryCharacter(
                id = "main",
                name = "Lan",
                role = "main",
                description = "A friendly neighbor",
            ),
        )

        val recap = studyTalkConversationRecap(world, characters, "You", "Speaking")

        assertEquals(source.scenes.map { it.title }, recap.map { it.title })
        assertEquals(listOf("Lan", "You"), recap[0].lines.map { it.speakerName })
        assertEquals(listOf("Xin chào.", "Chào!"), recap[0].lines.map { it.text })
        assertEquals(listOf("lan-hello.mp3", "you-hello.mp3"), recap[0].lines.map { it.audioUrl })
        assertEquals("portrait.png", recap[0].lines.first().expressionImageUrl)
        assertEquals("Speaking", recap[1].lines.single().speakerName)
        assertEquals("welcome.mp3", recap[1].lines.single().audioUrl)
        assertEquals(27, recap.flatMap { it.lines }.size)
    }

    @Test
    fun `asset progress counts ready media and actual required slots`() {
        val source = sampleTalkWorld()
        val world = source.copy(
            scenes = source.scenes.mapIndexed { sceneIndex, scene ->
                scene.copy(
                    backgroundImageUrl = when (sceneIndex) {
                        0 -> "background.png"
                        1 -> " "
                        else -> null
                    },
                    turns = scene.turns.mapIndexed { turnIndex, turn ->
                        if (sceneIndex == 0 && turnIndex == 0) turn.copy(replyAudioUrl = "reply.mp3") else turn
                    },
                )
            },
            expressions = source.expressions.mapIndexed { index, expression ->
                if (index == 0) expression.copy(imageUrl = "expression.png") else expression.copy(imageUrl = " ")
            },
        )

        val progress = requireNotNull(world.studyTalkAssetProgress())

        assertEquals(StudyTalkAssetGroupProgress(1, 6), progress.backgrounds)
        assertEquals(StudyTalkAssetGroupProgress(1, 2), progress.expressions)
        assertEquals(StudyTalkAssetGroupProgress(1, 36), progress.audio)
        assertEquals(3, progress.ready)
        assertEquals(44, progress.total)
        assertEquals(6, progress.percent)
    }

    @Test
    fun `shared expressions count once and structurally incomplete scripts are not playable`() {
        val source = sampleTalkWorld()
        val sharedExpressionWorld = source.copy(
            expressions = listOf(source.expressions.first()),
            scenes = source.scenes.map { scene ->
                scene.copy(turns = scene.turns.map { it.copy(expressionId = "main-neutral") })
            },
        )

        val progress = requireNotNull(sharedExpressionWorld.studyTalkAssetProgress())

        assertEquals(StudyTalkAssetGroupProgress(0, 1), progress.expressions)
        assertEquals(43, progress.total)
        assertEquals(null, source.copy(scenes = source.scenes.dropLast(1)).studyTalkAssetProgress())
        assertEquals(null, source.copy(scenes = source.scenes.mapIndexed { index, scene ->
            if (index == 0) scene.copy(turns = emptyList()) else scene
        }).studyTalkAssetProgress())
    }

    @Test
    fun `complete world reports all required assets ready`() {
        val source = sampleTalkWorld()
        val world = source.copy(
            scenes = source.scenes.map { scene ->
                scene.copy(
                    backgroundImageUrl = "background.png",
                    turns = scene.turns.map { it.copy(characterAudioUrl = "character.mp3", replyAudioUrl = "reply.mp3") },
                )
            },
            expressions = source.expressions.map { it.copy(imageUrl = "expression.png") },
        )

        val progress = requireNotNull(world.studyTalkAssetProgress())

        assertEquals(44, progress.ready)
        assertEquals(44, progress.total)
        assertEquals(100, progress.percent)
        assertEquals(true, world.hasAllTalkAssets())
    }

    @Test
    fun `unused expression portraits do not block preparation`() {
        val source = sampleTalkWorld()
        val world = source.copy(
            scenes = source.scenes.map { scene ->
                scene.copy(
                    backgroundImageUrl = "background.png",
                    turns = scene.turns.map { it.copy(characterAudioUrl = "character.mp3", replyAudioUrl = "reply.mp3") },
                )
            },
            expressions = source.expressions.map { it.copy(imageUrl = "expression.png") } +
                StudyTalkExpression("unused", "other", "neutral"),
        )

        val progress = requireNotNull(world.studyTalkAssetProgress())

        assertEquals(StudyTalkAssetGroupProgress(2, 2), progress.expressions)
        assertEquals(true, world.hasAllTalkAssets())
    }

    @Test
    fun `preparation progress is determinate from script wait through completion`() {
        val waiting = null.studyTalkBuildProgress()
        assertEquals(0, waiting.completedSteps)
        assertEquals(14, waiting.totalSteps)
        assertEquals(0, waiting.percent)

        val source = sampleTalkWorld()
        val partialWorld = source.copy(
            scenes = source.scenes.mapIndexed { index, scene ->
                scene.copy(
                    backgroundImageUrl = if (index == 0) "background.png" else null,
                    turns = if (index == 0) {
                        scene.turns.map { it.copy(characterAudioUrl = "character.mp3", replyAudioUrl = "reply.mp3") }
                    } else {
                        scene.turns
                    },
                )
            },
            expressions = source.expressions.mapIndexed { index, expression ->
                if (index == 0) expression.copy(imageUrl = "expression.png") else expression
            },
        )
        val partial = partialWorld.studyTalkBuildProgress()
        assertEquals(3, partial.completedSteps)
        assertEquals(14, partial.totalSteps)
        assertEquals(21, partial.percent)
        assertEquals(1, partial.audioScenesReady)

        val completedWorld = source.copy(
            scenes = source.scenes.map { scene ->
                scene.copy(
                    backgroundImageUrl = "background.png",
                    turns = scene.turns.map { it.copy(characterAudioUrl = "character.mp3", replyAudioUrl = "reply.mp3") },
                )
            },
            expressions = source.expressions.map { it.copy(imageUrl = "expression.png") },
        )
        val completed = completedWorld.studyTalkBuildProgress()
        assertEquals(14, completed.completedSteps)
        assertEquals(100, completed.percent)

        val malformed = completedWorld.copy(scenes = completedWorld.scenes.dropLast(1)).studyTalkBuildProgress()
        assertEquals(false, malformed.scriptReady)
        assertEquals(false, malformed.percent == 100)
    }

    @Test
    fun `preparation checklist shows all fourteen steps and live task states`() {
        val source = sampleTalkWorld()
        val background = StudyBuildElement(StudyBuildElementKind.TALK_SCENE_IMAGE, sceneIndex = 0)
        val audio = StudyBuildElement(
            StudyBuildElementKind.TALK_AUDIO,
            sceneIndex = 2,
            turnIndex = 0,
            audioKind = StudyTalkAudioKind.REPLY,
        )
        val state = StudyBuildState(
            status = StudyBuildStatus.BUILDING,
            inFlight = listOf(background),
            failed = listOf(audio),
        )

        val steps = source.studyTalkPreparationSteps(state)

        assertEquals(14, steps.size)
        assertEquals(StudyTalkPreparationStatus.COMPLETE, steps.first().status)
        assertEquals(StudyTalkPreparationStatus.RUNNING, steps[1].status)
        assertEquals(StudyTalkPreparationStatus.UPCOMING, steps[2].status)
        assertEquals(StudyTalkPreparationStatus.FAILED, steps[10].status)

        val completed = source.copy(
            scenes = source.scenes.map { scene ->
                scene.copy(
                    backgroundImageUrl = "background.png",
                    turns = scene.turns.map { it.copy(characterAudioUrl = "character.mp3", replyAudioUrl = "reply.mp3") },
                )
            },
            expressions = source.expressions.map { it.copy(imageUrl = "expression.png") },
        ).studyTalkPreparationSteps(null)
        assertTrue(completed.all { it.status == StudyTalkPreparationStatus.COMPLETE })
    }

    @Test
    fun `Talk retry stays available for failed work while the build is still active`() {
        val failedExpression = StudyBuildElement(
            StudyBuildElementKind.TALK_EXPRESSION,
            expressionId = "main-neutral",
        )
        val failedState = StudyBuildState(
            status = StudyBuildStatus.BUILDING,
            failed = listOf(failedExpression),
        )
        val runningState = StudyBuildState(
            status = StudyBuildStatus.BUILDING,
            inFlight = listOf(StudyBuildElement(StudyBuildElementKind.TALK_SCENE_IMAGE, sceneIndex = 0)),
        )

        assertTrue(canRetryStudyTalk(sampleTalkWorld(), failedState))
        assertEquals(false, canRetryStudyTalk(sampleTalkWorld(), runningState))
    }

    @Test
    fun `failed portrait prerequisite exposes its saved build explanation`() {
        val portrait = StudyBuildElement(StudyBuildElementKind.PORTRAIT, characterId = "main")
        val state = StudyBuildState(
            status = StudyBuildStatus.PARTIAL,
            failed = listOf(portrait),
            failureReasons = mapOf(portrait.key to "The image provider rejected the request (HTTP 503)."),
        )

        assertEquals("The image provider rejected the request (HTTP 503).", state.talkFailureReason())
    }

    @Test
    fun `missing talk assets produce only the incomplete talk retry elements`() {
        val source = sampleTalkWorld()
        val partial = source.copy(
            scenes = source.scenes.mapIndexed { index, scene ->
                scene.copy(
                    backgroundImageUrl = if (index == 0) null else "background.png",
                    turns = scene.turns.mapIndexed { turnIndex, turn ->
                        turn.copy(
                            characterAudioUrl = if (index == 0 && turnIndex == 0) null else "character.mp3",
                            replyAudioUrl = "reply.mp3",
                        )
                    },
                )
            },
            expressions = source.expressions.map {
                it.copy(imageUrl = if (it.id == "main-neutral") null else "expression.png")
            },
        )

        val retryElements = partial.missingTalkBuildElements()

        assertEquals(
            setOf(
                "TALK_SCENE_IMAGE:0",
                "TALK_EXPRESSION:main-neutral",
                "TALK_AUDIO:0:0:CHARACTER_LINE",
            ),
            retryElements.map { it.key }.toSet(),
        )
        assertEquals(
            listOf(StudyBuildElement(StudyBuildElementKind.TALK_WORLD)),
            null.missingTalkBuildElements(),
        )
    }

    private fun sampleTalkWorld() = StudyTalkWorld(
        setting = "A friendly neighborhood",
        expressions = listOf(
            StudyTalkExpression("main-neutral", "main", "neutral"),
            StudyTalkExpression("main-happy", "main", "happy"),
        ),
        scenes = List(6) { sceneIndex ->
            StudyTalkScene(
                id = "scene-${sceneIndex + 1}",
                title = "Scene ${sceneIndex + 1}",
                description = "A connected moment",
                turns = List(3) { turnIndex ->
                    StudyTalkTurn(
                        id = "scene-${sceneIndex + 1}-turn-${turnIndex + 1}",
                        speakerCharacterId = "main",
                        expressionId = if (turnIndex % 2 == 0) "main-neutral" else "main-happy",
                        characterLine = "Hello.",
                        intention = "I want to reply.",
                        reply = "Hello.",
                    )
                },
            )
        },
    )
}