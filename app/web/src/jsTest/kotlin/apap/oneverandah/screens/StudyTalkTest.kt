package apap.oneverandah.screens

import apap.oneverandah.api.StudyTalkScene
import apap.oneverandah.api.StudyTalkTurn
import apap.oneverandah.api.StudyTalkWorld
import kotlin.test.Test
import kotlin.test.assertEquals

class StudyTalkTest {
    @Test
    fun `retry leaves the current turn selected`() {
        val progress = StudyTalkProgress(sceneIndex = 2, turnIndex = 1, phase = StudyTalkPhase.REVEAL)

        val retry = retryStudyTalkTurn(progress)

        assertEquals(2, retry.sceneIndex)
        assertEquals(1, retry.turnIndex)
        assertEquals(StudyTalkPhase.INTENTION, retry.phase)
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

    private fun sampleTalkWorld() = StudyTalkWorld(
        setting = "A friendly neighborhood",
        scenes = List(6) { sceneIndex ->
            StudyTalkScene(
                id = "scene-${sceneIndex + 1}",
                title = "Scene ${sceneIndex + 1}",
                description = "A connected moment",
                turns = List(3) { turnIndex ->
                    StudyTalkTurn(
                        id = "scene-${sceneIndex + 1}-turn-${turnIndex + 1}",
                        speakerCharacterId = "main",
                        expressionId = "main-neutral",
                        characterLine = "Hello.",
                        intention = "I want to reply.",
                        reply = "Hello.",
                    )
                },
            )
        },
    )
}