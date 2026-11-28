package apap.oneverandah.chat

import apap.oneverandah.api.STUDY_COMPREHEND_GRID_SIZE
import apap.oneverandah.api.STUDY_COMPREHEND_MIN_ACTIONS
import apap.oneverandah.api.STUDY_COMPREHEND_SCENE_COUNT
import apap.oneverandah.api.STUDY_COMPREHEND_TILE_COUNT
import apap.oneverandah.api.StudyComprehendActionKind
import apap.oneverandah.api.StudyComprehendEnvironment
import apap.oneverandah.api.StudyComprehendGroundType
import apap.oneverandah.api.StudyComprehendObject
import apap.oneverandah.api.StudyComprehendObjectAction
import apap.oneverandah.api.StudyComprehendScene
import apap.oneverandah.api.StudyComprehendScenePlayState
import apap.oneverandah.api.StudyComprehendSpriteSpec
import apap.oneverandah.api.StudyComprehendTask
import apap.oneverandah.api.StudyComprehendTile
import apap.oneverandah.api.StudyComprehendTimeOfDay
import apap.oneverandah.api.StudyComprehendWeather
import apap.oneverandah.api.StudyComprehendWorld
import apap.oneverandah.api.isTileWalkable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs

internal data class ParsedComprehendDraft(
    val playerAppearance: String,
    val world: StudyComprehendWorld,
)

object QwenStudyComprehendParser {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
    }

    internal fun parseDraft(content: String): ParsedComprehendDraft {
        val root = parseRootObject(content)
        val setting = root.string("setting")?.trim()?.takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("Qwen study Comprehend missing setting")
        val playerTargetName = root.string("playerTargetName")?.trim().orEmpty()
        val playerAppearance = root.string("playerAppearance")?.trim().orEmpty()
            .ifBlank { "A friendly traveler exploring the local scene, full body standing pose" }

        val rawSprites = (root["sprites"] as? JsonArray).orEmpty()
        val spritesById = LinkedHashMap<String, StudyComprehendSpriteSpec>()
        rawSprites.forEachIndexed { index, element ->
            val obj = element as? JsonObject ?: return@forEachIndexed
            val id = obj.string("id")?.trim()?.takeIf(String::isNotEmpty) ?: "sprite-${index + 1}"
            val prompt = obj.string("visualPrompt")?.trim()?.takeIf(String::isNotEmpty)
                ?: obj.string("description")?.trim()?.takeIf(String::isNotEmpty)
                ?: id.replace('-', ' ')
            val isCharacter = obj.boolean("isCharacter") ?: false
            val characterId = obj.string("characterId")?.trim()?.takeIf(String::isNotEmpty)
            val scaleW = (obj.float("scaleWidth") ?: if (isCharacter) 1.2f else 1.15f).coerceIn(0.55f, 3.2f)
            val scaleH = (obj.float("scaleHeight") ?: if (isCharacter) 1.75f else 1.35f).coerceIn(0.55f, 3.8f)
            spritesById[id] = StudyComprehendSpriteSpec(
                id = id,
                isCharacter = isCharacter,
                characterId = characterId,
                visualPrompt = prompt,
                scaleWidth = scaleW,
                scaleHeight = scaleH,
            )
        }

        val rawScenes = root["scenes"] as? JsonArray
            ?: throw IllegalArgumentException("Qwen study Comprehend missing scenes array")
        require(rawScenes.size == STUDY_COMPREHEND_SCENE_COUNT) {
            "Qwen study Comprehend requires exactly $STUDY_COMPREHEND_SCENE_COUNT scenes, got ${rawScenes.size}"
        }

        val scenes = rawScenes.mapIndexed { sceneIndex, sceneElement ->
            val sceneObj = sceneElement as? JsonObject
                ?: throw IllegalArgumentException("Qwen study Comprehend scene ${sceneIndex + 1} must be an object")
            parseScene(sceneIndex, sceneObj, spritesById)
        }

        val usedSpriteIds = scenes.flatMap { scene -> scene.objects.map { it.spriteId } }.toSet()
        val finalSprites = spritesById.values.filter { it.id in usedSpriteIds }
        require(finalSprites.isNotEmpty()) { "Qwen study Comprehend produced no sprites" }

        return ParsedComprehendDraft(
            playerAppearance = playerAppearance,
            world = StudyComprehendWorld(
                setting = setting,
                playerTargetName = playerTargetName,
                playerVisualPrompt = playerAppearance,
                sprites = finalSprites,
                scenes = scenes,
                assetsComplete = false,
            ),
        )
    }

    fun parse(content: String): StudyComprehendWorld = parseDraft(content).world

    private fun parseScene(
        sceneIndex: Int,
        sceneObj: JsonObject,
        spritesById: MutableMap<String, StudyComprehendSpriteSpec>,
    ): StudyComprehendScene {
        val context = "scene ${sceneIndex + 1}"
        val id = sceneObj.string("id")?.trim().orEmpty().ifBlank { "scene-${sceneIndex + 1}" }
        val title = sceneObj.requiredString("title", context)
        val description = sceneObj.string("description")?.trim().orEmpty().ifBlank { title }
        val musicPrompt = sceneObj.string("musicPrompt")?.trim().orEmpty()
        val layoutSummary = sceneObj.string("layoutSummary")?.trim().orEmpty()
        val ambientTintHex = normalizeHexColorOrNull(sceneObj.string("ambientTintHex"))
        val initialCameraYawDegrees = sceneObj.float("initialCameraYawDegrees")
            ?.takeIf { it.isFinite() }
            ?.let { ((it % 360f) + 360f) % 360f }
        val environment = parseEnumOrDefault(
            sceneObj.string("environment"),
            if (sceneIndex == 2) StudyComprehendEnvironment.INDOOR else StudyComprehendEnvironment.OUTDOOR,
        )
        val wallMaterial = parseGroundType(sceneObj.string("wallMaterial"), environment)
        val surroundingGround = parseGroundType(sceneObj.string("surroundingGround"), environment)
            ?.takeUnless { it.isWallStructure }
        val defaultTimes = listOf(
            StudyComprehendTimeOfDay.MORNING,
            StudyComprehendTimeOfDay.DAYTIME,
            StudyComprehendTimeOfDay.EVENING,
            StudyComprehendTimeOfDay.NIGHT,
        )
        val timeOfDay = parseEnumOrDefault(
            sceneObj.string("timeOfDay"),
            defaultTimes[sceneIndex % defaultTimes.size],
        )
        val defaultWeathers = listOf(
            StudyComprehendWeather.SUNNY,
            StudyComprehendWeather.BREEZY,
            StudyComprehendWeather.RAINY,
        )
        val weather = parseEnumOrDefault(
            sceneObj.string("weather"),
            defaultWeathers[sceneIndex % defaultWeathers.size],
        )

        val defaultBaseGround = if (environment == StudyComprehendEnvironment.INDOOR) {
            StudyComprehendGroundType.WOOD_FLOOR
        } else {
            StudyComprehendGroundType.GRASS
        }
        val baseGround = (parseGroundType(sceneObj.string("baseGround"), environment)
            ?.takeUnless { it.isWallStructure }) ?: defaultBaseGround
        val groundGrid = Array(STUDY_COMPREHEND_TILE_COUNT) { baseGround }

        // Optional 32x32 row characters
        (sceneObj["groundRows"] as? JsonArray)?.forEachIndexed { z, rowElem ->
            if (z !in 0 until STUDY_COMPREHEND_GRID_SIZE) return@forEachIndexed
            val row = rowElem.jsonPrimitive.contentOrNull ?: return@forEachIndexed
            for (x in 0 until minOf(row.length, STUDY_COMPREHEND_GRID_SIZE)) {
                charToGround(row[x], environment)?.let { ground ->
                    groundGrid[z * STUDY_COMPREHEND_GRID_SIZE + x] = ground
                }
            }
        }

        // Rectangular or elliptical ground zones
        (sceneObj["groundZones"] as? JsonArray)?.forEach { zoneElem ->
            val zone = zoneElem as? JsonObject ?: return@forEach
            val ground = parseGroundType(zone.string("ground"), environment) ?: return@forEach
            val shape = zone.string("shape")?.trim()?.uppercase().orEmpty()
            val radius = zone.int("radius")
            val cxExplicit = zone.int("cx")
            val czExplicit = zone.int("cz")
            if (shape in setOf("ELLIPSE", "CIRCLE", "OVAL") || radius != null || (cxExplicit != null && czExplicit != null)) {
                val zx = zone.int("x")
                val zz = zone.int("z")
                val zw = (zone.int("w") ?: zone.int("width"))?.coerceAtLeast(1)
                val zh = (zone.int("h") ?: zone.int("height"))?.coerceAtLeast(1)
                val centerX = when {
                    cxExplicit != null -> cxExplicit.toDouble()
                    zx != null && zw != null && radius == null -> zx + (zw - 1) / 2.0
                    zx != null -> zx.toDouble()
                    else -> return@forEach
                }
                val centerZ = when {
                    czExplicit != null -> czExplicit.toDouble()
                    zz != null && zh != null && radius == null -> zz + (zh - 1) / 2.0
                    zz != null -> zz.toDouble()
                    else -> return@forEach
                }
                val rx = (zone.float("rx")?.toDouble()
                    ?: radius?.toDouble()
                    ?: zw?.let { it / 2.0 }
                    ?: 2.5).coerceAtLeast(0.6)
                val rz = (zone.float("rz")?.toDouble()
                    ?: radius?.toDouble()
                    ?: zh?.let { it / 2.0 }
                    ?: rx).coerceAtLeast(0.6)
                val minZ = (centerZ - rz - 1).toInt().coerceIn(0, STUDY_COMPREHEND_GRID_SIZE - 1)
                val maxZ = (centerZ + rz + 1).toInt().coerceIn(0, STUDY_COMPREHEND_GRID_SIZE - 1)
                val minX = (centerX - rx - 1).toInt().coerceIn(0, STUDY_COMPREHEND_GRID_SIZE - 1)
                val maxX = (centerX + rx + 1).toInt().coerceIn(0, STUDY_COMPREHEND_GRID_SIZE - 1)
                for (tz in minZ..maxZ) {
                    for (tx in minX..maxX) {
                        val nx = (tx - centerX) / rx
                        val nz = (tz - centerZ) / rz
                        if (nx * nx + nz * nz <= 1.02) {
                            groundGrid[tz * STUDY_COMPREHEND_GRID_SIZE + tx] = ground
                        }
                    }
                }
            } else {
                val zx = (zone.int("x") ?: return@forEach).coerceIn(0, STUDY_COMPREHEND_GRID_SIZE - 1)
                val zz = (zone.int("z") ?: return@forEach).coerceIn(0, STUDY_COMPREHEND_GRID_SIZE - 1)
                val zw = (zone.int("w") ?: zone.int("width") ?: 1).coerceAtLeast(1)
                val zh = (zone.int("h") ?: zone.int("height") ?: 1).coerceAtLeast(1)
                for (tz in zz until minOf(STUDY_COMPREHEND_GRID_SIZE, zz + zh)) {
                    for (tx in zx until minOf(STUDY_COMPREHEND_GRID_SIZE, zx + zw)) {
                        groundGrid[tz * STUDY_COMPREHEND_GRID_SIZE + tx] = ground
                    }
                }
            }
        }

        // Winding / diagonal polyline ground paths (rivers, trails, roads, bridges, canals, corridors)
        (sceneObj["groundPaths"] as? JsonArray)?.forEach { pathElem ->
            val pathObj = pathElem as? JsonObject ?: return@forEach
            val ground = parseGroundType(pathObj.string("ground"), environment) ?: return@forEach
            val width = (pathObj.int("width") ?: 2).coerceIn(1, 8)
            val pts = parseCoordPairs(pathObj["points"] as? JsonArray)
            if (pts.isNotEmpty()) {
                rasterizePolyline(groundGrid, pts, width = width, ground = ground)
            }
        }

        // 3D architectural wall/partition lines with optional doorway openings
        val rawWallLines = (sceneObj["wallLines"] as? JsonArray) ?: (sceneObj["partitions"] as? JsonArray)
        rawWallLines?.forEach { wallElem ->
            val wallObj = wallElem as? JsonObject ?: return@forEach
            val defaultWall = wallMaterial?.takeIf { it.isWallStructure }
                ?: if (environment == StudyComprehendEnvironment.INDOOR) {
                    StudyComprehendGroundType.PLASTER_WALL
                } else {
                    StudyComprehendGroundType.STONE_WALL
                }
            val parsedWall = parseGroundType(wallObj.string("ground") ?: wallObj.string("material"), environment)
            val wallGround = parsedWall?.takeIf { it.isWallStructure } ?: defaultWall
            val pts = parseCoordPairs(wallObj["points"] as? JsonArray)
            if (pts.isNotEmpty()) {
                val floorBeforeDoorways = groundGrid.copyOf()
                rasterizePolyline(groundGrid, pts, width = 1, ground = wallGround)
                val doorways = parseCoordPairs(
                    (wallObj["doorways"] as? JsonArray)
                        ?: (wallObj["doorGaps"] as? JsonArray)
                        ?: (wallObj["gaps"] as? JsonArray),
                )
                val fallbackFloor = selectPrimaryWalkableFloor(baseGround, floorBeforeDoorways, environment)
                doorways.forEach { (dx, dz) ->
                    if (dx in 0 until STUDY_COMPREHEND_GRID_SIZE && dz in 0 until STUDY_COMPREHEND_GRID_SIZE) {
                        val idx = dz * STUDY_COMPREHEND_GRID_SIZE + dx
                        val prev = floorBeforeDoorways[idx]
                        groundGrid[idx] = if (!prev.blocksMovementByDefault) prev else fallbackFloor
                    }
                }
            }
        }

        val fallbackSpawns = arrayOf(
            4 to 16,
            16 to 27,
            27 to 8,
            6 to 6,
            26 to 25,
            16 to 4,
        )
        val (defaultSpawnX, defaultSpawnZ) = fallbackSpawns[
            ((sceneIndex * 2 + abs(title.hashCode())) % fallbackSpawns.size),
        ]
        val spawnX = (sceneObj.int("playerSpawnX") ?: defaultSpawnX).coerceIn(1, STUDY_COMPREHEND_GRID_SIZE - 2)
        val spawnZ = (sceneObj.int("playerSpawnZ") ?: defaultSpawnZ).coerceIn(1, STUDY_COMPREHEND_GRID_SIZE - 2)
        val primaryFloor = selectPrimaryWalkableFloor(baseGround, groundGrid, environment)
        // Ensure player spawn ground is walkable
        if (groundGrid[spawnZ * STUDY_COMPREHEND_GRID_SIZE + spawnX].blocksMovementByDefault) {
            groundGrid[spawnZ * STUDY_COMPREHEND_GRID_SIZE + spawnX] = primaryFloor
        }

        val occupied = HashMap<Int, StudyComprehendObject>()
        val usedIds = HashSet<String>()
        fun tileKey(x: Int, z: Int): Int = z * STUDY_COMPREHEND_GRID_SIZE + x

        fun ensureSpriteExists(
            spriteId: String,
            isCharacter: Boolean,
            characterId: String?,
            fallbackDescription: String,
        ) {
            if (spriteId !in spritesById) {
                spritesById[spriteId] = StudyComprehendSpriteSpec(
                    id = spriteId,
                    isCharacter = isCharacter,
                    characterId = characterId,
                    visualPrompt = fallbackDescription,
                    scaleWidth = if (isCharacter) 1.25f else 1.15f,
                    scaleHeight = if (isCharacter) 1.8f else 1.35f,
                )
            }
        }

        // Parse primary interactive objects & characters first (they claim tiles first)
        val rawObjects = (sceneObj["objects"] as? JsonArray)
            ?: throw IllegalArgumentException("Qwen study Comprehend $context missing objects array")

        rawObjects.forEachIndexed { objIndex, element ->
            val obj = element as? JsonObject
                ?: throw IllegalArgumentException("Qwen study Comprehend $context object ${objIndex + 1} must be JSON object")
            val rawId = obj.string("id")?.trim().orEmpty().ifBlank { "s${sceneIndex + 1}-obj-${objIndex + 1}" }
            val uniqueId = uniqueObjectId(rawId, usedIds)
            val isCharacter = obj.boolean("isCharacter") ?: false
            val characterId = obj.string("characterId")?.trim()?.takeIf(String::isNotEmpty)
            val spriteId = obj.string("spriteId")?.trim().orEmpty()
                .ifBlank { if (isCharacter && characterId != null) "char-$characterId" else "sprite-$uniqueId" }
            val targetName = obj.requiredString("targetName", "$context object $uniqueId")
            val visualHint = obj.string("visualPrompt")?.trim().orEmpty()
                .ifBlank { if (isCharacter) "Full-body character $targetName" else "Detailed isolated 3D map prop ($uniqueId)" }
            ensureSpriteExists(spriteId, isCharacter, characterId, visualHint)

            val desiredX = (obj.int("x") ?: (4 + (objIndex * 5) % 24)).coerceIn(0, STUDY_COMPREHEND_GRID_SIZE - 1)
            val desiredZ = (obj.int("z") ?: (4 + (objIndex * 7) % 24)).coerceIn(0, STUDY_COMPREHEND_GRID_SIZE - 1)
            val (placedX, placedZ) = findNearestUnoccupiedTile(
                desiredX = desiredX,
                desiredZ = desiredZ,
                spawnX = spawnX,
                spawnZ = spawnZ,
                occupied = occupied.keys,
                groundGrid = groundGrid,
                mustBeWalkableGround = isCharacter || (obj.boolean("pickable") == true),
            ) ?: throw IllegalArgumentException("Qwen study Comprehend $context has no free tile for $uniqueId")

            val pickable = !isCharacter && (obj.boolean("pickable") ?: false)
            val opensPassage = !isCharacter && (obj.boolean("opensPassage") ?: false)
            // If an interactive object, character, or openable door was placed onto a 3D wall tile,
            // replace the wall block underneath with walkable threshold flooring so it renders cleanly.
            val placedTileIdx = placedZ * STUDY_COMPREHEND_GRID_SIZE + placedX
            if (groundGrid[placedTileIdx].isWallStructure) {
                groundGrid[placedTileIdx] = primaryFloor
            }
            val blocksMovement = if (isCharacter) {
                true
            } else {
                obj.boolean("blocksMovement") ?: opensPassage
            }
            val emitsWarmLight = obj.boolean("emitsWarmLight") ?: false
            val swaysInWind = obj.boolean("swaysInWind") ?: false
            val spokenText = obj.string("spokenText")?.trim()?.takeIf(String::isNotEmpty)
            val acceptSpokenText = obj.string("acceptSpokenText")?.trim()?.takeIf(String::isNotEmpty)
            val declineSpokenText = obj.string("declineSpokenText")?.trim()?.takeIf(String::isNotEmpty)

            if (isCharacter && spokenText.isNullOrBlank()) {
                throw IllegalArgumentException("Qwen study Comprehend $context character $uniqueId missing spokenText")
            }

            val parsedActions = (obj["actions"] as? JsonArray).orEmpty().mapIndexedNotNull { actIdx, actElem ->
                val act = actElem as? JsonObject ?: return@mapIndexedNotNull null
                val kind = parseEnumOrNull<StudyComprehendActionKind>(act.string("kind"))
                    ?: return@mapIndexedNotNull null
                val actId = act.string("id")?.trim().orEmpty().ifBlank { "$uniqueId-${kind.name.lowercase()}-${actIdx + 1}" }
                val label = act.string("label")?.trim().orEmpty().ifBlank { defaultActionLabel(kind) }
                val requiredItemId = act.string("requiredItemId")?.trim()?.takeIf(String::isNotEmpty)
                val feedbackText = act.string("feedbackText")?.trim().orEmpty()
                StudyComprehendObjectAction(
                    id = actId,
                    kind = kind,
                    label = label,
                    requiredItemId = requiredItemId,
                    feedbackText = feedbackText,
                )
            }.toMutableList()

            // Guarantee characters always have a LISTEN action and pickable items always have a TAKE action
            if (isCharacter && parsedActions.none { it.kind == StudyComprehendActionKind.LISTEN }) {
                parsedActions.add(
                    0,
                    StudyComprehendObjectAction(
                        id = "$uniqueId-listen",
                        kind = StudyComprehendActionKind.LISTEN,
                        label = "Listen",
                    ),
                )
            }
            if (pickable && parsedActions.none { it.kind == StudyComprehendActionKind.TAKE }) {
                parsedActions.add(
                    0,
                    StudyComprehendObjectAction(
                        id = "$uniqueId-take",
                        kind = StudyComprehendActionKind.TAKE,
                        label = "Take",
                    ),
                )
            }

            val built = StudyComprehendObject(
                id = uniqueId,
                spriteId = spriteId,
                x = placedX,
                z = placedZ,
                targetName = targetName,
                blocksMovement = blocksMovement,
                isCharacter = isCharacter,
                characterId = characterId,
                spokenText = spokenText,
                acceptSpokenText = acceptSpokenText,
                declineSpokenText = declineSpokenText,
                pickable = pickable,
                opensPassage = opensPassage,
                emitsWarmLight = emitsWarmLight,
                swaysInWind = swaysInWind,
                actions = parsedActions,
            )
            occupied[tileKey(placedX, placedZ)] = built
        }

        // Expand decorative sceneryClusters onto unoccupied tiles
        (sceneObj["sceneryClusters"] as? JsonArray)?.forEachIndexed { clusterIdx, clusterElem ->
            val cluster = clusterElem as? JsonObject ?: return@forEachIndexed
            val spriteId = cluster.string("spriteId")?.trim()?.takeIf(String::isNotEmpty) ?: return@forEachIndexed
            val targetName = cluster.string("targetName")?.trim()?.takeIf(String::isNotEmpty) ?: return@forEachIndexed
            val visualPrompt = cluster.string("visualPrompt")?.trim().orEmpty()
                .ifBlank { "Detailed isolated 3D map scenery prop ($spriteId)" }
            ensureSpriteExists(spriteId, isCharacter = false, characterId = null, fallbackDescription = visualPrompt)
            val blocksMovement = cluster.boolean("blocksMovement") ?: true
            val emitsWarmLight = cluster.boolean("emitsWarmLight") ?: false
            val swaysInWind = cluster.boolean("swaysInWind") ?: false
            val inspectActionLabel = cluster.string("inspectLabel")?.trim()?.takeIf(String::isNotEmpty)
            val positions = cluster["positions"] as? JsonArray ?: return@forEachIndexed
            positions.forEachIndexed { posIdx, posElem ->
                val pair = posElem as? JsonArray ?: return@forEachIndexed
                if (pair.size < 2) return@forEachIndexed
                val px = pair[0].jsonPrimitive.intOrNull ?: return@forEachIndexed
                val pz = pair[1].jsonPrimitive.intOrNull ?: return@forEachIndexed
                if (px !in 0 until STUDY_COMPREHEND_GRID_SIZE || pz !in 0 until STUDY_COMPREHEND_GRID_SIZE) {
                    return@forEachIndexed
                }
                if (px == spawnX && pz == spawnZ) return@forEachIndexed
                val key = tileKey(px, pz)
                if (key in occupied) return@forEachIndexed
                if (groundGrid[key].isWallStructure) return@forEachIndexed
                val objId = uniqueObjectId("s${sceneIndex + 1}-scenery-${clusterIdx + 1}-${posIdx + 1}", usedIds)
                val actions = if (inspectActionLabel != null) {
                    listOf(
                        StudyComprehendObjectAction(
                            id = "$objId-inspect",
                            kind = StudyComprehendActionKind.INSPECT,
                            label = inspectActionLabel,
                        ),
                    )
                } else {
                    emptyList()
                }
                occupied[key] = StudyComprehendObject(
                    id = objId,
                    spriteId = spriteId,
                    x = px,
                    z = pz,
                    targetName = targetName,
                    blocksMovement = blocksMovement,
                    emitsWarmLight = emitsWarmLight,
                    swaysInWind = swaysInWind,
                    actions = actions,
                )
            }
        }

        val allObjects = occupied.values.toList()
        val charactersInScene = allObjects.filter { it.isCharacter }
        require(charactersInScene.isNotEmpty()) {
            "Qwen study Comprehend $context must include at least one character"
        }
        val actionCount = allObjects.sumOf { it.actions.size }
        require(actionCount >= STUDY_COMPREHEND_MIN_ACTIONS) {
            "Qwen study Comprehend $context must provide at least $STUDY_COMPREHEND_MIN_ACTIONS possible player actions, got $actionCount"
        }

        val allActionIds = allObjects.flatMap { obj -> obj.actions.map { it.id } }.toSet()
        val characterIdsSet = charactersInScene.map { it.id }.toSet()
        val rawTasks = (sceneObj["tasks"] as? JsonArray)
            ?: throw IllegalArgumentException("Qwen study Comprehend $context missing tasks array")
        val tasks = rawTasks.mapIndexed { taskIndex, taskElem ->
            val tObj = taskElem as? JsonObject
                ?: throw IllegalArgumentException("Qwen study Comprehend $context task ${taskIndex + 1} must be an object")
            val tId = tObj.string("id")?.trim().orEmpty().ifBlank { "s${sceneIndex + 1}-task-${taskIndex + 1}" }
            val desc = tObj.requiredString("description", "$context task ${taskIndex + 1}")
            val reqChars = (tObj["requiredCharacterObjectIds"] as? JsonArray).orEmpty()
                .mapNotNull { it.jsonPrimitive.contentOrNull?.trim() }
                .filter { it in characterIdsSet }
            val reqActions = (tObj["requiredActionIds"] as? JsonArray).orEmpty()
                .mapNotNull { it.jsonPrimitive.contentOrNull?.trim() }
                .filter { it in allActionIds }
            require(reqChars.isNotEmpty() || reqActions.isNotEmpty()) {
                "Qwen study Comprehend $context task '$tId' does not reference any valid character or action"
            }
            StudyComprehendTask(
                id = tId,
                description = desc,
                requiredCharacterObjectIds = reqChars,
                requiredActionIds = reqActions,
            )
        }
        require(tasks.isNotEmpty()) { "Qwen study Comprehend $context must contain at least one task" }
        // Require at least one non-LISTEN comprehension action across the scene's tasks
        val listenActionIds = allObjects.flatMap { it.actions }
            .filter { it.kind == StudyComprehendActionKind.LISTEN }
            .mapTo(HashSet()) { it.id }
        val nonListenTaskActions = tasks.flatMap { it.requiredActionIds }.filterNot { it in listenActionIds }
        require(nonListenTaskActions.isNotEmpty()) {
            "Qwen study Comprehend $context tasks must require at least one comprehended world action beyond listening"
        }

        // Validate every GIVE / locked action required by a task references a pickable object present in the scene
        val pickableIds = allObjects.filter { it.pickable }.mapTo(HashSet()) { it.id }
        val actionById = allObjects.flatMap { it.actions }.associateBy { it.id }
        nonListenTaskActions.forEach { actId ->
            val act = actionById[actId] ?: return@forEach
            val reqItem = act.requiredItemId
            if (act.kind == StudyComprehendActionKind.GIVE || reqItem != null) {
                require(reqItem != null && reqItem in pickableIds) {
                    "Qwen study Comprehend $context task action '$actId' requires missing/non-pickable item '$reqItem'"
                }
            }
        }

        // Ensure every task ensures character comprehension by linking to scene character(s) if omitted
        val normalizedTasks = tasks.map { task ->
            val onlyListens = task.requiredActionIds.isNotEmpty() && task.requiredActionIds.all { it in listenActionIds }
            if (!onlyListens && task.requiredCharacterObjectIds.isEmpty()) {
                task.copy(requiredCharacterObjectIds = listOf(charactersInScene.first().id))
            } else {
                task
            }
        }

        return ensureReachability(
            StudyComprehendScene(
                id = id,
                title = title,
                description = description,
                environment = environment,
                timeOfDay = timeOfDay,
                weather = weather,
                playerSpawnX = spawnX,
                playerSpawnZ = spawnZ,
                tiles = buildTilesList(groundGrid, occupied),
                objects = allObjects,
                tasks = normalizedTasks,
                musicPrompt = musicPrompt,
                layoutSummary = layoutSummary,
                wallMaterial = wallMaterial,
                surroundingGround = surroundingGround,
                ambientTintHex = ambientTintHex,
                initialCameraYawDegrees = initialCameraYawDegrees,
            ),
        )
    }

    /**
     * Guarantees every character and interactive object in the 32x32 scene has a walkable
     * adjacent tile reachable from `(playerSpawnX, playerSpawnZ)` so AI map walls/water
     * can never soft-lock the player.
     */
    internal fun ensureReachability(scene: StudyComprehendScene): StudyComprehendScene {
        val groundGrid = Array(STUDY_COMPREHEND_TILE_COUNT) { idx -> scene.tiles[idx].ground }
        val objectsByTile = scene.objects.associateByTo(LinkedHashMap()) { it.z * STUDY_COMPREHEND_GRID_SIZE + it.x }
        val passableFloor = selectPrimaryWalkableFloor(
            baseGround = if (scene.environment == StudyComprehendEnvironment.INDOOR) {
                StudyComprehendGroundType.WOOD_FLOOR
            } else {
                StudyComprehendGroundType.STONE_PAVING
            },
            groundGrid = groundGrid,
            environment = scene.environment,
        )

        fun computeReachable(): BooleanArray {
            val currentScene = scene.copy(
                tiles = buildTilesList(groundGrid, objectsByTile),
                objects = objectsByTile.values.toList(),
            )
            // Treat opensPassage doors as openable when checking reachability
            val openState = StudyComprehendScenePlayState(
                playerX = scene.playerSpawnX,
                playerZ = scene.playerSpawnZ,
                openedObjectIds = objectsByTile.values.filter { it.opensPassage }.mapTo(HashSet()) { it.id },
            )
            val visited = BooleanArray(STUDY_COMPREHEND_TILE_COUNT)
            val queue = ArrayDeque<Pair<Int, Int>>()
            val startIdx = scene.playerSpawnZ * STUDY_COMPREHEND_GRID_SIZE + scene.playerSpawnX
            visited[startIdx] = true
            queue.add(scene.playerSpawnX to scene.playerSpawnZ)
            val dirs = arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
            while (queue.isNotEmpty()) {
                val (cx, cz) = queue.removeFirst()
                for ((dx, dz) in dirs) {
                    val nx = cx + dx
                    val nz = cz + dz
                    if (nx !in 0 until STUDY_COMPREHEND_GRID_SIZE || nz !in 0 until STUDY_COMPREHEND_GRID_SIZE) continue
                    val nIdx = nz * STUDY_COMPREHEND_GRID_SIZE + nx
                    if (visited[nIdx]) continue
                    if (currentScene.isTileWalkable(openState, nx, nz)) {
                        visited[nIdx] = true
                        queue.add(nx to nz)
                    }
                }
            }
            return visited
        }

        var reachable = computeReachable()
        val interactiveTargets = objectsByTile.values.filter { it.isCharacter || it.actions.isNotEmpty() }
        val protectedTiles = interactiveTargets.mapTo(HashSet()) { it.z * STUDY_COMPREHEND_GRID_SIZE + it.x }

        for (target in interactiveTargets) {
            val hasReachableNeighbor = arrayOf(
                0 to 0, 1 to 0, -1 to 0, 0 to 1, 0 to -1, 1 to 1, 1 to -1, -1 to 1, -1 to -1,
            ).any { (dx, dz) ->
                val nx = target.x + dx
                val nz = target.z + dz
                nx in 0 until STUDY_COMPREHEND_GRID_SIZE &&
                    nz in 0 until STUDY_COMPREHEND_GRID_SIZE &&
                    reachable[nz * STUDY_COMPREHEND_GRID_SIZE + nx]
            }
            if (hasReachableNeighbor) continue

            // Carve a walkable path from the nearest reachable tile to an adjacent tile of `target`
            var bestStartX = scene.playerSpawnX
            var bestStartZ = scene.playerSpawnZ
            var bestDist = Int.MAX_VALUE
            for (z in 0 until STUDY_COMPREHEND_GRID_SIZE) {
                for (x in 0 until STUDY_COMPREHEND_GRID_SIZE) {
                    if (!reachable[z * STUDY_COMPREHEND_GRID_SIZE + x]) continue
                    val dist = abs(x - target.x) + abs(z - target.z)
                    if (dist < bestDist) {
                        bestDist = dist
                        bestStartX = x
                        bestStartZ = z
                    }
                }
            }

            var cx = bestStartX
            var cz = bestStartZ
            while (abs(cx - target.x) + abs(cz - target.z) > 1) {
                if (cx != target.x && (cz == target.z || abs(target.x - cx) >= abs(target.z - cz))) {
                    cx += if (target.x > cx) 1 else -1
                } else if (cz != target.z) {
                    cz += if (target.z > cz) 1 else -1
                }
                val idx = cz * STUDY_COMPREHEND_GRID_SIZE + cx
                if (groundGrid[idx].blocksMovementByDefault) {
                    groundGrid[idx] = passableFloor
                }
                if (idx !in protectedTiles) {
                    objectsByTile.remove(idx)
                }
            }
            reachable = computeReachable()
        }

        val finalObjects = objectsByTile.values.toList()
        return scene.copy(
            tiles = buildTilesList(groundGrid, objectsByTile),
            objects = finalObjects,
        )
    }

    private fun buildTilesList(
        groundGrid: Array<StudyComprehendGroundType>,
        occupied: Map<Int, StudyComprehendObject>,
    ): List<StudyComprehendTile> = List(STUDY_COMPREHEND_TILE_COUNT) { idx ->
        StudyComprehendTile(
            ground = groundGrid[idx],
            objectId = occupied[idx]?.id,
        )
    }

    private fun findNearestUnoccupiedTile(
        desiredX: Int,
        desiredZ: Int,
        spawnX: Int,
        spawnZ: Int,
        occupied: Set<Int>,
        groundGrid: Array<StudyComprehendGroundType>,
        mustBeWalkableGround: Boolean,
    ): Pair<Int, Int>? {
        for (radius in 0 until STUDY_COMPREHEND_GRID_SIZE) {
            for (dz in -radius..radius) {
                for (dx in -radius..radius) {
                    if (maxOf(abs(dx), abs(dz)) != radius) continue
                    val tx = desiredX + dx
                    val tz = desiredZ + dz
                    if (tx !in 0 until STUDY_COMPREHEND_GRID_SIZE || tz !in 0 until STUDY_COMPREHEND_GRID_SIZE) continue
                    if (tx == spawnX && tz == spawnZ) continue
                    val idx = tz * STUDY_COMPREHEND_GRID_SIZE + tx
                    if (idx in occupied) continue
                    if (mustBeWalkableGround && groundGrid[idx].blocksMovementByDefault) continue
                    return tx to tz
                }
            }
        }
        return null
    }

    private fun uniqueObjectId(base: String, usedIds: MutableSet<String>): String {
        val cleaned = base.trim().ifBlank { "obj" }
        if (usedIds.add(cleaned)) return cleaned
        var suffix = 2
        while (!usedIds.add("$cleaned-$suffix")) suffix++
        return "$cleaned-$suffix"
    }

    private fun defaultActionLabel(kind: StudyComprehendActionKind): String = when (kind) {
        StudyComprehendActionKind.LISTEN -> "Listen"
        StudyComprehendActionKind.TAKE -> "Take"
        StudyComprehendActionKind.GIVE -> "Give"
        StudyComprehendActionKind.USE -> "Use"
        StudyComprehendActionKind.OPEN -> "Open"
        StudyComprehendActionKind.TOGGLE -> "Toggle"
        StudyComprehendActionKind.INSPECT -> "Inspect"
    }

    private fun parseGroundType(
        raw: String?,
        environment: StudyComprehendEnvironment = StudyComprehendEnvironment.OUTDOOR,
    ): StudyComprehendGroundType? {
        parseEnumOrNull<StudyComprehendGroundType>(raw)?.let { return it }
        val normalized = raw?.trim()?.uppercase()?.replace('-', '_')?.replace(' ', '_') ?: return null
        return when (normalized) {
            "WALL", "INTERIOR_WALL", "PARTITION" ->
                if (environment == StudyComprehendEnvironment.INDOOR) {
                    StudyComprehendGroundType.PLASTER_WALL
                } else {
                    StudyComprehendGroundType.STONE_WALL
                }
            "TIMBER_WALL", "WOOD_PARTITION", "SHOJI_WALL" -> StudyComprehendGroundType.WOOD_WALL
            "STUCCO_WALL", "WHITE_WALL", "PLASTER" -> StudyComprehendGroundType.PLASTER_WALL
            "MASONRY_WALL", "CLIFF", "ROCK_WALL" -> StudyComprehendGroundType.STONE_WALL
            "BRICK" -> StudyComprehendGroundType.COURTYARD_BRICK
            "TERRACOTTA", "CLAY_TILE" -> StudyComprehendGroundType.TERRACOTTA_TILE
            "SLATE", "ASPHALT", "BASALT", "PAVEMENT" -> StudyComprehendGroundType.DARK_SLATE
            "STONE", "PAVING", "FLAGSTONE" -> StudyComprehendGroundType.STONE_PAVING
            "WOOD", "PLANK", "BOARDWALK", "DECK" -> StudyComprehendGroundType.WOOD_FLOOR
            "DIRT", "PATH", "TRAIL", "EARTH" -> StudyComprehendGroundType.DIRT_PATH
            "MARBLE", "TILE" -> StudyComprehendGroundType.MARBLE_TILE
            "TATAMI" -> StudyComprehendGroundType.TATAMI_MAT
            "RUG" -> StudyComprehendGroundType.CARPET
            "RIVER", "POND", "CANAL", "LAKE", "SEA" -> StudyComprehendGroundType.WATER
            else -> null
        }
    }

    private fun charToGround(ch: Char, environment: StudyComprehendEnvironment): StudyComprehendGroundType? =
        when (ch.uppercaseChar()) {
            'G', '.' -> if (environment == StudyComprehendEnvironment.INDOOR) StudyComprehendGroundType.WOOD_FLOOR else StudyComprehendGroundType.GRASS
            'P' -> StudyComprehendGroundType.STONE_PAVING
            'B' -> StudyComprehendGroundType.COBBLESTONE
            'D' -> StudyComprehendGroundType.DIRT_PATH
            'S' -> StudyComprehendGroundType.SAND
            'F' -> StudyComprehendGroundType.WOOD_FLOOR
            'M' -> StudyComprehendGroundType.MARBLE_TILE
            'T' -> StudyComprehendGroundType.TATAMI_MAT
            'C' -> StudyComprehendGroundType.CARPET
            'W', '~' -> StudyComprehendGroundType.WATER
            'O' -> StudyComprehendGroundType.MOSS_STONE
            'V' -> StudyComprehendGroundType.GRAVEL
            'K' -> StudyComprehendGroundType.COURTYARD_BRICK
            'N' -> StudyComprehendGroundType.SNOW
            'E', 'A' -> StudyComprehendGroundType.TERRACOTTA_TILE
            'L', 'U' -> StudyComprehendGroundType.DARK_SLATE
            '#', 'X' -> StudyComprehendGroundType.STONE_WALL
            '|', 'I' -> StudyComprehendGroundType.WOOD_WALL
            'H' -> StudyComprehendGroundType.PLASTER_WALL
            'R' -> StudyComprehendGroundType.BRICK_WALL
            else -> null
        }

    private fun parseCoordPairs(array: JsonArray?): List<Pair<Int, Int>> =
        array.orEmpty().mapNotNull { elem ->
            when (elem) {
                is JsonArray -> {
                    if (elem.size < 2) null
                    else {
                        val x = elem[0].jsonPrimitive.intOrNull ?: return@mapNotNull null
                        val z = elem[1].jsonPrimitive.intOrNull ?: return@mapNotNull null
                        x.coerceIn(0, STUDY_COMPREHEND_GRID_SIZE - 1) to z.coerceIn(0, STUDY_COMPREHEND_GRID_SIZE - 1)
                    }
                }
                is JsonObject -> {
                    val x = elem.int("x") ?: return@mapNotNull null
                    val z = elem.int("z") ?: return@mapNotNull null
                    x.coerceIn(0, STUDY_COMPREHEND_GRID_SIZE - 1) to z.coerceIn(0, STUDY_COMPREHEND_GRID_SIZE - 1)
                }
                else -> null
            }
        }

    private fun rasterizePolyline(
        groundGrid: Array<StudyComprehendGroundType>,
        points: List<Pair<Int, Int>>,
        width: Int,
        ground: StudyComprehendGroundType,
    ) {
        fun stamp(cx: Int, cz: Int) {
            val halfLow = (width - 1) / 2
            val halfHigh = width / 2
            for (dz in -halfLow..halfHigh) {
                for (dx in -halfLow..halfHigh) {
                    if (width >= 4 && abs(dx) == halfHigh && abs(dz) == halfHigh) continue
                    val tx = cx + dx
                    val tz = cz + dz
                    if (tx in 0 until STUDY_COMPREHEND_GRID_SIZE && tz in 0 until STUDY_COMPREHEND_GRID_SIZE) {
                        groundGrid[tz * STUDY_COMPREHEND_GRID_SIZE + tx] = ground
                    }
                }
            }
        }
        if (points.size == 1) {
            stamp(points[0].first, points[0].second)
            return
        }
        for (i in 0 until points.lastIndex) {
            val (x0, z0) = points[i]
            val (x1, z1) = points[i + 1]
            val steps = maxOf(abs(x1 - x0), abs(z1 - z0), 1)
            for (step in 0..steps) {
                val t = step.toDouble() / steps.toDouble()
                val cx = kotlin.math.round(x0 + (x1 - x0) * t).toInt()
                val cz = kotlin.math.round(z0 + (z1 - z0) * t).toInt()
                stamp(cx, cz)
            }
        }
    }

    private fun selectPrimaryWalkableFloor(
        baseGround: StudyComprehendGroundType,
        groundGrid: Array<StudyComprehendGroundType>,
        environment: StudyComprehendEnvironment,
    ): StudyComprehendGroundType {
        val counts = HashMap<StudyComprehendGroundType, Int>()
        for (g in groundGrid) {
            if (!g.blocksMovementByDefault) {
                counts[g] = (counts[g] ?: 0) + 1
            }
        }
        // Prefer paved/interior floor types when carving bridges or doorways over grass/sand if present
        val preferredWalkway = counts.keys
            .filter {
                it in setOf(
                    StudyComprehendGroundType.WOOD_FLOOR,
                    StudyComprehendGroundType.STONE_PAVING,
                    StudyComprehendGroundType.COBBLESTONE,
                    StudyComprehendGroundType.DIRT_PATH,
                    StudyComprehendGroundType.MARBLE_TILE,
                    StudyComprehendGroundType.TATAMI_MAT,
                    StudyComprehendGroundType.TERRACOTTA_TILE,
                    StudyComprehendGroundType.COURTYARD_BRICK,
                    StudyComprehendGroundType.DARK_SLATE,
                )
            }
            .maxByOrNull { counts[it] ?: 0 }
        if (preferredWalkway != null) return preferredWalkway
        if (!baseGround.blocksMovementByDefault) return baseGround
        return counts.maxByOrNull { it.value }?.key
            ?: if (environment == StudyComprehendEnvironment.INDOOR) {
                StudyComprehendGroundType.WOOD_FLOOR
            } else {
                StudyComprehendGroundType.STONE_PAVING
            }
    }

    private fun normalizeHexColorOrNull(raw: String?): String? {
        val cleaned = raw?.trim()?.removePrefix("#") ?: return null
        if (cleaned.length != 6 || !cleaned.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
            return null
        }
        return "#${cleaned.lowercase()}"
    }

    private inline fun <reified E : Enum<E>> parseEnumOrNull(raw: String?): E? {
        val normalized = raw?.trim()?.uppercase()?.replace('-', '_')?.replace(' ', '_') ?: return null
        return enumValues<E>().firstOrNull { it.name == normalized }
    }

    private inline fun <reified E : Enum<E>> parseEnumOrDefault(raw: String?, default: E): E =
        parseEnumOrNull<E>(raw) ?: default

    private fun parseRootObject(content: String): JsonObject {
        val stripped = stripJsonFences(content)
        val direct = runCatching { json.parseToJsonElement(stripped) }.getOrNull()
        if (direct is JsonObject) return direct
        val start = stripped.indexOf('{')
        val end = stripped.lastIndexOf('}')
        if (start >= 0 && end > start) {
            val extracted = runCatching { json.parseToJsonElement(stripped.substring(start, end + 1)) }.getOrNull()
            if (extracted is JsonObject) return extracted
        }
        throw IllegalArgumentException("Qwen study Comprehend root must be a valid JSON object")
    }

    private fun JsonObject.requiredString(field: String, context: String): String =
        string(field)?.trim()?.takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("Qwen study Comprehend $context missing $field")

    private fun JsonObject.string(field: String): String? =
        (this[field] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.int(field: String): Int? =
        (this[field] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.float(field: String): Float? =
        (this[field] as? JsonPrimitive)?.floatOrNull

    private fun JsonObject.boolean(field: String): Boolean? =
        (this[field] as? JsonPrimitive)?.booleanOrNull
}
