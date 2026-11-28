package apap.oneverandah.api

import apap.oneverandah.model.Language
import kotlinx.serialization.Serializable
import kotlin.math.abs

const val STUDY_COMPREHEND_SCENE_COUNT = 3
const val STUDY_COMPREHEND_GRID_SIZE = 32
const val STUDY_COMPREHEND_TILE_COUNT = STUDY_COMPREHEND_GRID_SIZE * STUDY_COMPREHEND_GRID_SIZE
const val STUDY_COMPREHEND_MIN_ACTIONS = 16

@Serializable
enum class StudyComprehendEnvironment {
    OUTDOOR,
    INDOOR,
}

@Serializable
enum class StudyComprehendTimeOfDay {
    MORNING,
    DAYTIME,
    EVENING,
    NIGHT,
}

@Serializable
enum class StudyComprehendWeather {
    SUNNY,
    CLOUDY,
    RAINY,
    DUSTY,
    HOT,
    COLD,
    MISTY,
    SNOWY,
    BREEZY,
}

@Serializable
enum class StudyComprehendGroundType(
    val blocksMovementByDefault: Boolean = false,
    val isWallStructure: Boolean = false,
) {
    GRASS,
    STONE_PAVING,
    COBBLESTONE,
    DIRT_PATH,
    SAND,
    WOOD_FLOOR,
    MARBLE_TILE,
    TATAMI_MAT,
    CARPET,
    WATER(blocksMovementByDefault = true),
    MOSS_STONE,
    GRAVEL,
    COURTYARD_BRICK,
    SNOW,
    TERRACOTTA_TILE,
    DARK_SLATE,
    STONE_WALL(blocksMovementByDefault = true, isWallStructure = true),
    WOOD_WALL(blocksMovementByDefault = true, isWallStructure = true),
    PLASTER_WALL(blocksMovementByDefault = true, isWallStructure = true),
    BRICK_WALL(blocksMovementByDefault = true, isWallStructure = true),
}

@Serializable
enum class StudyComprehendActionKind {
    LISTEN,
    TAKE,
    GIVE,
    USE,
    OPEN,
    TOGGLE,
    INSPECT,
}

@Serializable
enum class StudyComprehendBuildStatus {
    GENERATING_SCENES,
    GENERATING_ASSETS,
    READY,
    FAILED,
}

@Serializable
data class StudyComprehendSpriteSpec(
    val id: String,
    val isCharacter: Boolean = false,
    val characterId: String? = null,
    val visualPrompt: String,
    val imageUrl: String? = null,
    val scaleWidth: Float = 1.15f,
    val scaleHeight: Float = 1.45f,
)

@Serializable
data class StudyComprehendTile(
    val ground: StudyComprehendGroundType,
    val objectId: String? = null,
)

@Serializable
data class StudyComprehendObjectAction(
    val id: String,
    val kind: StudyComprehendActionKind,
    val label: String,
    val requiredItemId: String? = null,
    val feedbackText: String = "",
)

@Serializable
data class StudyComprehendObject(
    val id: String,
    val spriteId: String,
    val x: Int,
    val z: Int,
    /** Label shown above the billboard in the study/target language. */
    val targetName: String,
    val blocksMovement: Boolean = false,
    val isCharacter: Boolean = false,
    val characterId: String? = null,
    /** Spoken line in the target language when this character is tapped. */
    val spokenText: String? = null,
    val spokenAudioUrl: String? = null,
    /** Spoken line in the target language when a character accepts the right action/item. */
    val acceptSpokenText: String? = null,
    val acceptAudioUrl: String? = null,
    /** Spoken line in the target language when offered an unrelated item. */
    val declineSpokenText: String? = null,
    val declineAudioUrl: String? = null,
    val pickable: Boolean = false,
    val opensPassage: Boolean = false,
    val emitsWarmLight: Boolean = false,
    val swaysInWind: Boolean = false,
    val actions: List<StudyComprehendObjectAction> = emptyList(),
)

@Serializable
data class StudyComprehendTask(
    val id: String,
    /** Task description shown in the left-side task list (in learner UI language). */
    val description: String,
    /** Character object IDs whose spoken lines the player must listen to for this task. */
    val requiredCharacterObjectIds: List<String> = emptyList(),
    /** Action IDs that must be performed (after listening to required characters) to complete this task. */
    val requiredActionIds: List<String> = emptyList(),
)

@Serializable
data class StudyComprehendScene(
    val id: String,
    val title: String,
    val description: String,
    val environment: StudyComprehendEnvironment,
    val timeOfDay: StudyComprehendTimeOfDay,
    val weather: StudyComprehendWeather,
    val playerSpawnX: Int,
    val playerSpawnZ: Int,
    val tiles: List<StudyComprehendTile>,
    val objects: List<StudyComprehendObject>,
    val tasks: List<StudyComprehendTask>,
    val musicPrompt: String = "",
    val musicAudioUrl: String? = null,
    val layoutSummary: String = "",
    val wallMaterial: StudyComprehendGroundType? = null,
    val surroundingGround: StudyComprehendGroundType? = null,
    val ambientTintHex: String? = null,
    val initialCameraYawDegrees: Float? = null,
) {
    fun tileAt(x: Int, z: Int): StudyComprehendTile? {
        if (x !in 0 until STUDY_COMPREHEND_GRID_SIZE || z !in 0 until STUDY_COMPREHEND_GRID_SIZE) return null
        val index = z * STUDY_COMPREHEND_GRID_SIZE + x
        return tiles.getOrNull(index)
    }

    fun possibleActionCount(): Int = objects.sumOf { it.actions.size }
}

@Serializable
data class StudyComprehendWorld(
    val setting: String,
    val playerSpriteUrl: String? = null,
    val playerTargetName: String = "",
    val playerVisualPrompt: String = "",
    val sprites: List<StudyComprehendSpriteSpec>,
    val scenes: List<StudyComprehendScene>,
    val assetsComplete: Boolean = true,
) {
    fun hasAllAssets(
        requireAudio: Boolean = false,
        requireMusic: Boolean = false,
    ): Boolean {
        if (scenes.size != STUDY_COMPREHEND_SCENE_COUNT) return false
        if (playerSpriteUrl.isNullOrBlank()) return false
        if (sprites.any { it.imageUrl.isNullOrBlank() }) return false
        if (requireMusic && scenes.any { it.musicAudioUrl.isNullOrBlank() }) return false
        if (requireAudio) {
            val missingSpeech = scenes.any { scene ->
                scene.objects.any { obj ->
                    obj.isCharacter && (
                        (!obj.spokenText.isNullOrBlank() && obj.spokenAudioUrl.isNullOrBlank()) ||
                            (!obj.acceptSpokenText.isNullOrBlank() && obj.acceptAudioUrl.isNullOrBlank()) ||
                            (!obj.declineSpokenText.isNullOrBlank() && obj.declineAudioUrl.isNullOrBlank())
                        )
                }
            }
            if (missingSpeech) return false
        }
        return true
    }

    fun isReadyToPlay(): Boolean = assetsComplete && hasAllAssets()

    internal fun populatedAssetCount(): Int {
        var count = 0
        if (!playerSpriteUrl.isNullOrBlank()) count += 1
        count += sprites.count { !it.imageUrl.isNullOrBlank() }
        scenes.forEach { scene ->
            if (!scene.musicAudioUrl.isNullOrBlank()) count += 1
            scene.objects.forEach { obj ->
                if (obj.isCharacter) {
                    if (!obj.spokenAudioUrl.isNullOrBlank()) count += 1
                    if (!obj.acceptAudioUrl.isNullOrBlank()) count += 1
                    if (!obj.declineAudioUrl.isNullOrBlank()) count += 1
                }
            }
        }
        return count
    }
}

fun mergeStudyComprehendWorlds(
    primary: StudyComprehendWorld?,
    secondary: StudyComprehendWorld?,
): StudyComprehendWorld? {
    if (primary == null) return secondary
    if (secondary == null) return primary

    val sameStructure = primary.scenes.size == secondary.scenes.size &&
        primary.scenes.map { it.id } == secondary.scenes.map { it.id } &&
        primary.sprites.map { it.id } == secondary.sprites.map { it.id }

    if (!sameStructure) {
        return when {
            primary.isReadyToPlay() && !secondary.isReadyToPlay() -> primary
            secondary.isReadyToPlay() && !primary.isReadyToPlay() -> secondary
            secondary.populatedAssetCount() > primary.populatedAssetCount() -> secondary
            else -> primary
        }
    }

    fun preferUrl(first: String?, second: String?): String? =
        first?.trim()?.takeIf { it.isNotEmpty() } ?: second?.trim()?.takeIf { it.isNotEmpty() }

    val secondarySpritesById = secondary.sprites.associateBy { it.id }
    val mergedSprites = primary.sprites.map { sprite ->
        val other = secondarySpritesById[sprite.id]
        sprite.copy(imageUrl = preferUrl(sprite.imageUrl, other?.imageUrl))
    }

    val mergedScenes = primary.scenes.mapIndexed { index, scene ->
        val otherScene = secondary.scenes[index]
        val otherObjectsById = otherScene.objects.associateBy { it.id }
        scene.copy(
            musicPrompt = scene.musicPrompt.ifBlank { otherScene.musicPrompt },
            musicAudioUrl = preferUrl(scene.musicAudioUrl, otherScene.musicAudioUrl),
            layoutSummary = scene.layoutSummary.ifBlank { otherScene.layoutSummary },
            wallMaterial = scene.wallMaterial ?: otherScene.wallMaterial,
            surroundingGround = scene.surroundingGround ?: otherScene.surroundingGround,
            ambientTintHex = scene.ambientTintHex ?: otherScene.ambientTintHex,
            initialCameraYawDegrees = scene.initialCameraYawDegrees ?: otherScene.initialCameraYawDegrees,
            objects = scene.objects.map { obj ->
                val otherObj = otherObjectsById[obj.id]
                if (!obj.isCharacter || otherObj == null) {
                    obj
                } else {
                    obj.copy(
                        spokenAudioUrl = preferUrl(obj.spokenAudioUrl, otherObj.spokenAudioUrl),
                        acceptAudioUrl = preferUrl(obj.acceptAudioUrl, otherObj.acceptAudioUrl),
                        declineAudioUrl = preferUrl(obj.declineAudioUrl, otherObj.declineAudioUrl),
                    )
                }
            },
        )
    }

    val eitherComplete = (primary.assetsComplete && primary.hasAllAssets()) ||
        (secondary.assetsComplete && secondary.hasAllAssets())

    return primary.copy(
        playerSpriteUrl = preferUrl(primary.playerSpriteUrl, secondary.playerSpriteUrl),
        sprites = mergedSprites,
        scenes = mergedScenes,
        assetsComplete = eitherComplete,
    )
}

@Serializable
data class StudyComprehendGenerateRequest(
    val sessionId: String? = null,
    val text: String,
    val textLanguageTag: String,
    val languageTag: String,
    val sentences: List<StudySentence> = emptyList(),
    val characters: List<StudyStoryCharacter> = emptyList(),
) {
    fun normalizedOrNull(): StudyComprehendGenerateRequest? {
        val normalizedText = text.trim().takeIf { it.isNotEmpty() } ?: return null
        val cleanedSentences = sentences.filter { it.text.isNotBlank() }
        if (cleanedSentences.isEmpty()) return null
        return copy(
            sessionId = sessionId?.trim()?.takeIf { it.isNotEmpty() },
            text = normalizedText,
            textLanguageTag = Language.normalizeContentTag(textLanguageTag),
            languageTag = Language.normalizeLearnerLanguageTag(languageTag),
            sentences = cleanedSentences,
            characters = characters.mapNotNull { character ->
                val name = character.name.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val role = character.role.trim().ifEmpty { "main" }
                character.copy(
                    id = character.id.trim().ifEmpty { role },
                    name = name,
                    role = role,
                    description = character.description.trim(),
                    imageId = character.imageId?.trim()?.takeIf { it.isNotEmpty() },
                    imageUrl = character.imageUrl?.trim()?.takeIf { it.isNotEmpty() },
                )
            },
        )
    }
}

@Serializable
data class StudyComprehendJobResponse(
    val jobId: String,
    val status: StudyComprehendBuildStatus,
    val completedSteps: Int = 0,
    val totalSteps: Int = 1,
    val inFlightSteps: List<String> = emptyList(),
    val error: String? = null,
    val world: StudyComprehendWorld? = null,
)

/**
 * Ephemeral client-side gameplay state for a single Comprehend scene.
 * Deliberately never persisted to the server.
 */
data class StudyComprehendScenePlayState(
    val playerX: Int,
    val playerZ: Int,
    val inventoryObjectIds: List<String> = emptyList(),
    val pickedObjectIds: Set<String> = emptySet(),
    val openedObjectIds: Set<String> = emptySet(),
    val toggledObjectIds: Set<String> = emptySet(),
    val listenedCharacterObjectIds: Set<String> = emptySet(),
    val completedActionIds: Set<String> = emptySet(),
    val deliveredItemObjectIds: Set<String> = emptySet(),
    val selectedInventoryObjectId: String? = null,
) {
    companion object {
        fun initial(scene: StudyComprehendScene): StudyComprehendScenePlayState =
            StudyComprehendScenePlayState(
                playerX = scene.playerSpawnX.coerceIn(0, STUDY_COMPREHEND_GRID_SIZE - 1),
                playerZ = scene.playerSpawnZ.coerceIn(0, STUDY_COMPREHEND_GRID_SIZE - 1),
            )
    }
}

data class StudyComprehendActionOutcome(
    val state: StudyComprehendScenePlayState,
    val succeeded: Boolean,
    val spokenTextToPlay: String? = null,
    val audioUrlToPlay: String? = null,
    val feedbackMessage: String? = null,
    val newlyCompletedTaskIds: Set<String> = emptySet(),
)

fun StudyComprehendScene.isObjectPresentOnMap(
    obj: StudyComprehendObject,
    state: StudyComprehendScenePlayState,
): Boolean = obj.id !in state.pickedObjectIds && obj.id !in state.deliveredItemObjectIds

fun StudyComprehendScene.isTileWalkable(
    state: StudyComprehendScenePlayState,
    x: Int,
    z: Int,
): Boolean {
    val tile = tileAt(x, z) ?: return false
    val objectById = objects.associateBy { it.id }
    val obj = tile.objectId?.let { objectById[it] }
    if (obj != null && isObjectPresentOnMap(obj, state)) {
        if (obj.isCharacter) return false
        if (obj.opensPassage) {
            return obj.id in state.openedObjectIds
        }
        if (obj.blocksMovement) {
            return false
        }
        // Bridge / stepping stone object on water makes the tile walkable.
        if (!obj.blocksMovement && tile.ground.blocksMovementByDefault) {
            return true
        }
    }
    return !tile.ground.blocksMovementByDefault
}

fun StudyComprehendScene.isAdjacentOrSame(ax: Int, az: Int, bx: Int, bz: Int): Boolean =
    abs(ax - bx) <= 1 && abs(az - bz) <= 1

fun StudyComprehendScene.isTaskCompleted(
    task: StudyComprehendTask,
    state: StudyComprehendScenePlayState,
): Boolean {
    if (task.requiredCharacterObjectIds.isEmpty() && task.requiredActionIds.isEmpty()) return false
    val listenedAll = task.requiredCharacterObjectIds.all { it in state.listenedCharacterObjectIds }
    val actionsAll = task.requiredActionIds.all { it in state.completedActionIds }
    return listenedAll && actionsAll
}

fun StudyComprehendScene.completedTaskIds(state: StudyComprehendScenePlayState): Set<String> =
    tasks.filter { isTaskCompleted(it, state) }.mapTo(linkedSetOf()) { it.id }

fun StudyComprehendScene.isSceneCompleted(state: StudyComprehendScenePlayState): Boolean {
    if (tasks.isEmpty()) return false
    val characterIds = objects.filter { it.isCharacter }.map { it.id }
    val listenedCharacters = characterIds.all { it in state.listenedCharacterObjectIds }
    return listenedCharacters && tasks.all { isTaskCompleted(it, state) }
}

/**
 * Applies a player action on an object or character, enforcing comprehension rules:
 * actions tied to a character's request only satisfy the task after the player has
 * listened to the required character(s).
 */
fun StudyComprehendScene.performAction(
    state: StudyComprehendScenePlayState,
    targetObjectId: String,
    actionId: String,
    offeredInventoryObjectId: String? = state.selectedInventoryObjectId,
): StudyComprehendActionOutcome {
    val obj = objects.firstOrNull { it.id == targetObjectId }
        ?: return StudyComprehendActionOutcome(state, succeeded = false)
    val action = obj.actions.firstOrNull { it.id == actionId }
        ?: return StudyComprehendActionOutcome(state, succeeded = false)
    val beforeTasks = completedTaskIds(state)

    // Check if any task gated on this action requires listening to a character first.
    val gatingTasks = tasks.filter { action.id in it.requiredActionIds }
    val unlistenedRequiredCharacters = gatingTasks
        .flatMap { it.requiredCharacterObjectIds }
        .any { it !in state.listenedCharacterObjectIds }

    val nextState: StudyComprehendScenePlayState
    val succeeded: Boolean
    var spokenText: String? = null
    var audioUrl: String? = null
    var feedback: String? = action.feedbackText.takeIf { it.isNotBlank() }

    when (action.kind) {
        StudyComprehendActionKind.LISTEN -> {
            spokenText = obj.spokenText
            audioUrl = obj.spokenAudioUrl
            nextState = state.copy(
                listenedCharacterObjectIds = state.listenedCharacterObjectIds + obj.id,
                completedActionIds = state.completedActionIds + action.id,
            )
            succeeded = true
        }

        StudyComprehendActionKind.TAKE -> {
            if (!obj.pickable || !isObjectPresentOnMap(obj, state)) {
                return StudyComprehendActionOutcome(state, succeeded = false)
            }
            val updatedInventory = (state.inventoryObjectIds + obj.id).distinct()
            val updatedCompleted = if (unlistenedRequiredCharacters) {
                state.completedActionIds
            } else {
                state.completedActionIds + action.id
            }
            nextState = state.copy(
                inventoryObjectIds = updatedInventory,
                pickedObjectIds = state.pickedObjectIds + obj.id,
                selectedInventoryObjectId = state.selectedInventoryObjectId ?: obj.id,
                completedActionIds = updatedCompleted,
            )
            succeeded = true
        }

        StudyComprehendActionKind.GIVE -> {
            val requiredId = action.requiredItemId
            val candidateId = offeredInventoryObjectId
                ?: requiredId?.takeIf { it in state.inventoryObjectIds }
                ?: state.inventoryObjectIds.firstOrNull()
            if (candidateId == null || candidateId !in state.inventoryObjectIds) {
                spokenText = obj.spokenText
                audioUrl = obj.spokenAudioUrl
                return StudyComprehendActionOutcome(
                    state = state,
                    succeeded = false,
                    spokenTextToPlay = spokenText,
                    audioUrlToPlay = audioUrl,
                )
            }
            val matchesRequired = requiredId != null && candidateId == requiredId && !unlistenedRequiredCharacters
            if (matchesRequired) {
                val remaining = state.inventoryObjectIds.filterNot { it == candidateId }
                // Also credit the TAKE action for that item if the player picked it up before listening!
                val itemTakeActions = objects.firstOrNull { it.id == candidateId }
                    ?.actions
                    ?.filter { it.kind == StudyComprehendActionKind.TAKE }
                    ?.map { it.id }
                    .orEmpty()
                spokenText = obj.acceptSpokenText?.takeIf { it.isNotBlank() } ?: obj.spokenText
                audioUrl = obj.acceptAudioUrl?.takeIf { it.isNotBlank() } ?: obj.spokenAudioUrl
                nextState = state.copy(
                    inventoryObjectIds = remaining,
                    deliveredItemObjectIds = state.deliveredItemObjectIds + candidateId,
                    selectedInventoryObjectId = state.selectedInventoryObjectId
                        ?.takeIf { it != candidateId }
                        ?: remaining.firstOrNull(),
                    completedActionIds = state.completedActionIds + action.id + itemTakeActions,
                )
                succeeded = true
            } else {
                spokenText = obj.declineSpokenText?.takeIf { it.isNotBlank() } ?: obj.spokenText
                audioUrl = obj.declineAudioUrl?.takeIf { it.isNotBlank() } ?: obj.spokenAudioUrl
                feedback = null
                return StudyComprehendActionOutcome(
                    state = state,
                    succeeded = false,
                    spokenTextToPlay = spokenText,
                    audioUrlToPlay = audioUrl,
                )
            }
        }

        StudyComprehendActionKind.OPEN -> {
            val requiredId = action.requiredItemId
            if (requiredId != null && requiredId !in state.inventoryObjectIds) {
                return StudyComprehendActionOutcome(
                    state = state,
                    succeeded = false,
                    feedbackMessage = action.feedbackText.takeIf { it.isNotBlank() },
                )
            }
            if (unlistenedRequiredCharacters && gatingTasks.isNotEmpty()) {
                return StudyComprehendActionOutcome(
                    state = state,
                    succeeded = false,
                )
            }
            val itemTakeActions = requiredId?.let { reqId ->
                objects.firstOrNull { it.id == reqId }
                    ?.actions
                    ?.filter { it.kind == StudyComprehendActionKind.TAKE }
                    ?.map { it.id }
            }.orEmpty()
            nextState = state.copy(
                openedObjectIds = state.openedObjectIds + obj.id,
                completedActionIds = state.completedActionIds + action.id + itemTakeActions,
            )
            succeeded = true
        }

        StudyComprehendActionKind.TOGGLE -> {
            val toggled = if (obj.id in state.toggledObjectIds) {
                state.toggledObjectIds - obj.id
            } else {
                state.toggledObjectIds + obj.id
            }
            val completed = if (!unlistenedRequiredCharacters) {
                state.completedActionIds + action.id
            } else {
                state.completedActionIds
            }
            nextState = state.copy(
                toggledObjectIds = toggled,
                completedActionIds = completed,
            )
            succeeded = !unlistenedRequiredCharacters || gatingTasks.isEmpty()
        }

        StudyComprehendActionKind.USE,
        StudyComprehendActionKind.INSPECT -> {
            val requiredId = action.requiredItemId
            if (requiredId != null && requiredId !in state.inventoryObjectIds) {
                return StudyComprehendActionOutcome(state = state, succeeded = false)
            }
            val itemTakeActions = requiredId?.let { reqId ->
                objects.firstOrNull { it.id == reqId }
                    ?.actions
                    ?.filter { it.kind == StudyComprehendActionKind.TAKE }
                    ?.map { it.id }
            }.orEmpty()
            val completed = if (!unlistenedRequiredCharacters) {
                state.completedActionIds + action.id + itemTakeActions
            } else {
                state.completedActionIds
            }
            val opened = if (obj.opensPassage && !unlistenedRequiredCharacters) {
                state.openedObjectIds + obj.id
            } else {
                state.openedObjectIds
            }
            nextState = state.copy(
                openedObjectIds = opened,
                completedActionIds = completed,
            )
            succeeded = !unlistenedRequiredCharacters || gatingTasks.isEmpty()
        }
    }

    // Reconcile any carried items whose TAKE actions were performed before listening to a character
    val carriedTakeActions = nextState.inventoryObjectIds.flatMap { carriedId ->
        objects.firstOrNull { it.id == carriedId }
            ?.actions
            ?.filter { it.kind == StudyComprehendActionKind.TAKE }
            ?.filter { takeAction ->
                tasks.filter { takeAction.id in it.requiredActionIds }
                    .flatMap { it.requiredCharacterObjectIds }
                    .all { it in nextState.listenedCharacterObjectIds }
            }
            ?.map { it.id }
            .orEmpty()
    }
    val reconciledState = if (carriedTakeActions.isEmpty()) {
        nextState
    } else {
        nextState.copy(completedActionIds = nextState.completedActionIds + carriedTakeActions)
    }

    val afterTasks = completedTaskIds(reconciledState)
    return StudyComprehendActionOutcome(
        state = reconciledState,
        succeeded = succeeded,
        spokenTextToPlay = spokenText,
        audioUrlToPlay = audioUrl,
        feedbackMessage = feedback,
        newlyCompletedTaskIds = afterTasks - beforeTasks,
    )
}

/**
 * Places a carried inventory object back onto a nearby walkable empty tile so the player
 * can never get stuck holding unwanted items.
 */
fun StudyComprehendScene.dropInventoryItem(
    state: StudyComprehendScenePlayState,
    objectId: String,
): StudyComprehendScenePlayState {
    if (objectId !in state.inventoryObjectIds) return state
    val remaining = state.inventoryObjectIds.filterNot { it == objectId }
    return state.copy(
        inventoryObjectIds = remaining,
        pickedObjectIds = state.pickedObjectIds - objectId,
        selectedInventoryObjectId = state.selectedInventoryObjectId
            ?.takeIf { it != objectId }
            ?: remaining.firstOrNull(),
    )
}

/**
 * Finds a shortest walkable tile path on the 32x32 grid using A*.
 * If `(goalX, goalZ)` is occupied by a character or blocking object, automatically
 * routes to the closest walkable neighbor tile adjacent to `(goalX, goalZ)`.
 */
fun StudyComprehendScene.findWalkPath(
    state: StudyComprehendScenePlayState,
    startX: Int,
    startZ: Int,
    goalX: Int,
    goalZ: Int,
): List<Pair<Int, Int>> {
    if (goalX !in 0 until STUDY_COMPREHEND_GRID_SIZE || goalZ !in 0 until STUDY_COMPREHEND_GRID_SIZE) {
        return emptyList()
    }
    val candidateGoals: List<Pair<Int, Int>> = if (isTileWalkable(state, goalX, goalZ)) {
        listOf(goalX to goalZ)
    } else {
        val cardinal = listOf(0 to 1, 1 to 0, 0 to -1, -1 to 0, 1 to 1, 1 to -1, -1 to 1, -1 to -1)
        cardinal.map { (dx, dz) -> (goalX + dx) to (goalZ + dz) }
            .filter { (nx, nz) -> isTileWalkable(state, nx, nz) }
            .sortedBy { (nx, nz) -> abs(nx - startX) + abs(nz - startZ) }
    }
    if (candidateGoals.isEmpty()) return emptyList()
    if ((startX to startZ) in candidateGoals) return emptyList()

    val goalSet = candidateGoals.toSet()
    val primaryGoal = candidateGoals.first()

    data class Node(val x: Int, val z: Int, val g: Int, val f: Int)

    val gScore = HashMap<Int, Int>()
    val cameFrom = HashMap<Int, Int>()
    val closed = HashSet<Int>()
    fun pack(x: Int, z: Int): Int = z * STUDY_COMPREHEND_GRID_SIZE + x
    fun unpack(code: Int): Pair<Int, Int> = (code % STUDY_COMPREHEND_GRID_SIZE) to (code / STUDY_COMPREHEND_GRID_SIZE)

    val open = ArrayList<Node>()
    val startCode = pack(startX, startZ)
    gScore[startCode] = 0
    open.add(Node(startX, startZ, 0, abs(primaryGoal.first - startX) + abs(primaryGoal.second - startZ)))

    val dirs = arrayOf(
        intArrayOf(1, 0, 10),
        intArrayOf(-1, 0, 10),
        intArrayOf(0, 1, 10),
        intArrayOf(0, -1, 10),
        intArrayOf(1, 1, 14),
        intArrayOf(1, -1, 14),
        intArrayOf(-1, 1, 14),
        intArrayOf(-1, -1, 14),
    )

    while (open.isNotEmpty()) {
        var bestIdx = 0
        for (i in 1 until open.size) {
            if (open[i].f < open[bestIdx].f) bestIdx = i
        }
        val current = open.removeAt(bestIdx)
        val currentCode = pack(current.x, current.z)
        if (!closed.add(currentCode)) continue

        if ((current.x to current.z) in goalSet) {
            val path = ArrayList<Pair<Int, Int>>()
            var cursor = currentCode
            while (cursor != startCode) {
                path.add(unpack(cursor))
                cursor = cameFrom[cursor] ?: break
            }
            path.reverse()
            return path
        }

        for (dir in dirs) {
            val nx = current.x + dir[0]
            val nz = current.z + dir[1]
            if (!isTileWalkable(state, nx, nz)) continue
            // Prevent diagonal corner-cutting between two orthogonal blockers.
            if (dir[0] != 0 && dir[1] != 0) {
                if (!isTileWalkable(state, current.x + dir[0], current.z) ||
                    !isTileWalkable(state, current.x, current.z + dir[1])
                ) {
                    continue
                }
            }
            val nCode = pack(nx, nz)
            if (nCode in closed) continue
            val tentativeG = current.g + dir[2]
            val prevG = gScore[nCode]
            if (prevG == null || tentativeG < prevG) {
                gScore[nCode] = tentativeG
                cameFrom[nCode] = currentCode
                val h = (abs(primaryGoal.first - nx) + abs(primaryGoal.second - nz)) * 10
                open.add(Node(nx, nz, tentativeG, tentativeG + h))
            }
        }
    }
    return emptyList()
}
