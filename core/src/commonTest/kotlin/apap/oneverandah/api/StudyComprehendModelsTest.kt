package apap.oneverandah.api

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StudyComprehendModelsTest {
    @Test
    fun `comprehension requires listening to character before completing quest action`() {
        val scene = samplePalaceCourtyardScene()
        var state = StudyComprehendScenePlayState.initial(scene)

        // Pick up the jade token BEFORE listening to Guard Minh
        val preTake = scene.performAction(state, targetObjectId = "jade-token", actionId = "take-jade")
        assertTrue(preTake.succeeded)
        state = preTake.state
        assertEquals(listOf("jade-token"), state.inventoryObjectIds)
        assertFalse(scene.isObjectPresentOnMap(scene.objects.first { it.id == "jade-token" }, state))

        // Attempting to hand the token to Minh before listening to Minh must NOT complete the task
        val prematureGive = scene.performAction(
            state = state,
            targetObjectId = "guard-minh",
            actionId = "give-minh",
            offeredInventoryObjectId = "jade-token",
        )
        assertFalse(prematureGive.succeeded)
        assertFalse(scene.isSceneCompleted(prematureGive.state))

        // Listen to Minh -> hears spokenText; tapping Minh again repeats spokenText
        val firstListen = scene.performAction(state, targetObjectId = "guard-minh", actionId = "listen-minh")
        assertTrue(firstListen.succeeded)
        assertEquals("Bạn hãy mang thẻ ngọc ở bàn trà đến cho tôi.", firstListen.spokenTextToPlay)
        state = firstListen.state

        val repeatListen = scene.performAction(state, targetObjectId = "guard-minh", actionId = "listen-minh")
        assertTrue(repeatListen.succeeded)
        assertEquals("Bạn hãy mang thẻ ngọc ở bàn trà đến cho tôi.", repeatListen.spokenTextToPlay)
        state = repeatListen.state

        // Offering a wrong item plays decline line and keeps item in inventory
        val takeDecoy = scene.performAction(state, targetObjectId = "wooden-cup", actionId = "take-cup")
        state = takeDecoy.state
        val wrongGive = scene.performAction(
            state = state,
            targetObjectId = "guard-minh",
            actionId = "give-minh",
            offeredInventoryObjectId = "wooden-cup",
        )
        assertFalse(wrongGive.succeeded)
        assertEquals("Cái đó không phải là thẻ ngọc.", wrongGive.spokenTextToPlay)
        assertTrue("wooden-cup" in wrongGive.state.inventoryObjectIds)

        // Offering the comprehended item completes the scene!
        val rightGive = scene.performAction(
            state = state,
            targetObjectId = "guard-minh",
            actionId = "give-minh",
            offeredInventoryObjectId = "jade-token",
        )
        assertTrue(rightGive.succeeded)
        assertEquals("Cảm ơn bạn, mời đi vào cung điện!", rightGive.spokenTextToPlay)
        assertTrue(scene.isSceneCompleted(rightGive.state))
        assertFalse("jade-token" in rightGive.state.inventoryObjectIds)
    }

    @Test
    fun `water rocks and closed gates block movement while bridges opened gates and dropped items behave naturally`() {
        val scene = samplePalaceCourtyardScene()
        var state = StudyComprehendScenePlayState.initial(scene)

        // Rock at (10, 15) blocks movement
        assertFalse(scene.isTileWalkable(state, 10, 15))
        // Water at (5, 12) blocks movement, while bridge on water at (16, 12) is walkable
        assertFalse(scene.isTileWalkable(state, 5, 12))
        assertTrue(scene.isTileWalkable(state, 16, 12))

        // Closed gate at (16, 8) blocks movement until listened & opened with iron-key
        assertFalse(scene.isTileWalkable(state, 16, 8))
        state = scene.performAction(state, "guard-minh", "listen-minh").state
        state = scene.performAction(state, "iron-key", "take-key").state
        state = scene.performAction(state, "palace-gate", "open-gate", offeredInventoryObjectId = "iron-key").state
        assertTrue(scene.isTileWalkable(state, 16, 8))

        // Dropping iron-key returns it to the map
        val afterDrop = scene.dropInventoryItem(state, "iron-key")
        assertFalse("iron-key" in afterDrop.inventoryObjectIds)
        assertTrue(scene.isObjectPresentOnMap(scene.objects.first { it.id == "iron-key" }, afterDrop))
    }

    @Test
    fun `findWalkPath routes around water via bridge and stops adjacent to blocking character`() {
        val scene = samplePalaceCourtyardScene()
        val state = StudyComprehendScenePlayState.initial(scene)

        // Player starts at (16, 20), river spans z=12 except bridge at (16, 12), character Minh is at (16, 10)
        val path = scene.findWalkPath(state, startX = 14, startZ = 16, goalX = 16, goalZ = 10)
        assertTrue(path.isNotEmpty(), "Expected valid A* path across bridge to Minh")
        assertTrue(path.any { it == (16 to 12) }, "Path must cross the walkable bridge tile at (16, 12)")
        val destination = path.last()
        assertTrue(scene.isTileWalkable(state, destination.first, destination.second))
        assertTrue(abs(destination.first - 16) <= 1 && abs(destination.second - 10) <= 1)
    }

    private fun samplePalaceCourtyardScene(): StudyComprehendScene {
        val objects = mutableListOf(
            StudyComprehendObject(
                id = "guard-minh",
                spriteId = "char-minh",
                x = 16,
                z = 10,
                targetName = "Minh",
                blocksMovement = true,
                isCharacter = true,
                spokenText = "Bạn hãy mang thẻ ngọc ở bàn trà đến cho tôi.",
                acceptSpokenText = "Cảm ơn bạn, mời đi vào cung điện!",
                declineSpokenText = "Cái đó không phải là thẻ ngọc.",
                actions = listOf(
                    StudyComprehendObjectAction("listen-minh", StudyComprehendActionKind.LISTEN, "Listen"),
                    StudyComprehendObjectAction("give-minh", StudyComprehendActionKind.GIVE, "Give", requiredItemId = "jade-token"),
                ),
            ),
            StudyComprehendObject(
                id = "jade-token",
                spriteId = "jade",
                x = 12,
                z = 18,
                targetName = "Thẻ ngọc",
                pickable = true,
                actions = listOf(StudyComprehendObjectAction("take-jade", StudyComprehendActionKind.TAKE, "Take")),
            ),
            StudyComprehendObject(
                id = "wooden-cup",
                spriteId = "cup",
                x = 14,
                z = 18,
                targetName = "Chén gỗ",
                pickable = true,
                actions = listOf(StudyComprehendObjectAction("take-cup", StudyComprehendActionKind.TAKE, "Take")),
            ),
            StudyComprehendObject(
                id = "iron-key",
                spriteId = "key",
                x = 18,
                z = 18,
                targetName = "Chìa khóa",
                pickable = true,
                actions = listOf(StudyComprehendObjectAction("take-key", StudyComprehendActionKind.TAKE, "Take")),
            ),
            StudyComprehendObject(
                id = "palace-gate",
                spriteId = "gate",
                x = 16,
                z = 8,
                targetName = "Cổng cung điện",
                blocksMovement = true,
                opensPassage = true,
                actions = listOf(
                    StudyComprehendObjectAction("open-gate", StudyComprehendActionKind.OPEN, "Unlock", requiredItemId = "iron-key"),
                ),
            ),
            StudyComprehendObject(
                id = "stone-bridge",
                spriteId = "bridge",
                x = 16,
                z = 12,
                targetName = "Cầu đá",
                blocksMovement = false,
            ),
            StudyComprehendObject(
                id = "boulder",
                spriteId = "rock",
                x = 10,
                z = 15,
                targetName = "Tảng đá",
                blocksMovement = true,
            ),
        )
        // Add decoy inspectable/toggleable props so scene has >= 16 actions
        for (i in 1..12) {
            objects += StudyComprehendObject(
                id = "lantern-$i",
                spriteId = "lantern",
                x = (i * 2) % 30 + 1,
                z = 24,
                targetName = "Đèn lồng $i",
                emitsWarmLight = true,
                actions = listOf(
                    StudyComprehendObjectAction("toggle-lantern-$i", StudyComprehendActionKind.TOGGLE, "Light"),
                ),
            )
        }
        val byCoord = objects.associateBy { it.z * STUDY_COMPREHEND_GRID_SIZE + it.x }
        val tiles = List(STUDY_COMPREHEND_TILE_COUNT) { idx ->
            val z = idx / STUDY_COMPREHEND_GRID_SIZE
            val ground = if (z == 12) StudyComprehendGroundType.WATER else StudyComprehendGroundType.STONE_PAVING
            StudyComprehendTile(ground = ground, objectId = byCoord[idx]?.id)
        }
        assertNotNull(tiles.firstOrNull())
        assertTrue(objects.sumOf { it.actions.size } >= STUDY_COMPREHEND_MIN_ACTIONS)

        return StudyComprehendScene(
            id = "scene-1",
            title = "Courtyard Bridge",
            description = "Evening bridge before the palace gate",
            environment = StudyComprehendEnvironment.OUTDOOR,
            timeOfDay = StudyComprehendTimeOfDay.EVENING,
            weather = StudyComprehendWeather.MISTY,
            playerSpawnX = 16,
            playerSpawnZ = 20,
            tiles = tiles,
            objects = objects,
            tasks = listOf(
                StudyComprehendTask(
                    id = "task-listen",
                    description = "Listen to Guard Minh",
                    requiredCharacterObjectIds = listOf("guard-minh"),
                    requiredActionIds = listOf("listen-minh"),
                ),
                StudyComprehendTask(
                    id = "task-deliver",
                    description = "Bring Minh what he asked for",
                    requiredCharacterObjectIds = listOf("guard-minh"),
                    requiredActionIds = listOf("give-minh"),
                ),
            ),
        )
    }
}
