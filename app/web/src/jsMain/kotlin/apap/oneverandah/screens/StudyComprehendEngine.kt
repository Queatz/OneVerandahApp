package apap.oneverandah.screens

import apap.oneverandah.api.STUDY_COMPREHEND_GRID_SIZE
import apap.oneverandah.api.StudyComprehendEnvironment
import apap.oneverandah.api.StudyComprehendGroundType
import apap.oneverandah.api.StudyComprehendObject
import apap.oneverandah.api.StudyComprehendScene
import apap.oneverandah.api.StudyComprehendScenePlayState
import apap.oneverandah.api.StudyComprehendSpriteSpec
import apap.oneverandah.api.StudyComprehendTimeOfDay
import apap.oneverandah.api.StudyComprehendWeather
import apap.oneverandah.api.StudyComprehendWorld
import apap.oneverandah.api.findWalkPath
import apap.oneverandah.api.isObjectPresentOnMap
import apap.oneverandah.api.isTileWalkable
import apap.oneverandah.babylon.ArcRotateCamera
import apap.oneverandah.babylon.BabylonLiteModule
import apap.oneverandah.babylon.EngineContext
import apap.oneverandah.babylon.Mesh
import apap.oneverandah.babylon.SceneContext
import apap.oneverandah.babylon.StandardMaterialProps
import apap.oneverandah.babylon.Texture2D
import apap.oneverandah.babylon.loadBabylonLite
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.await
import org.khronos.webgl.Float32Array
import org.khronos.webgl.Uint32Array
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.set
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLImageElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.MouseEvent
import org.w3c.dom.events.WheelEvent
import kotlin.js.Promise
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

internal enum class WebGpuBrowserKind { CHROME_FAMILY, FIREFOX, SAFARI, OTHER }

internal data class WebGpuSupportResult(
    val supported: Boolean,
    val browserKind: WebGpuBrowserKind,
)

internal fun detectWebGpuBrowserKind(userAgent: String): WebGpuBrowserKind {
    val ua = userAgent.lowercase()
    return when {
        "firefox" in ua -> WebGpuBrowserKind.FIREFOX
        ("chrome" in ua || "edg/" in ua || "chromium" in ua || "crios" in ua) -> WebGpuBrowserKind.CHROME_FAMILY
        "safari" in ua -> WebGpuBrowserKind.SAFARI
        else -> WebGpuBrowserKind.OTHER
    }
}

internal suspend fun checkWebGpuSupport(): WebGpuSupportResult {
    val ua = runCatching { window.navigator.userAgent }.getOrDefault("")
    val browserKind = detectWebGpuBrowserKind(ua)
    val gpu = window.navigator.asDynamic().gpu
    if (gpu == null || js("typeof gpu.requestAdapter !== 'function'") as Boolean) {
        return WebGpuSupportResult(supported = false, browserKind = browserKind)
    }
    val adapter = runCatching {
        (gpu.requestAdapter() as Promise<dynamic>).await()
    }.getOrNull()
    return WebGpuSupportResult(supported = adapter != null, browserKind = browserKind)
}

private const val TILE_WORLD_SIZE = 1.0
private const val HALF_GRID = STUDY_COMPREHEND_GRID_SIZE / 2.0
private const val PLAYER_MOVE_SPEED = 5.4 // tiles per second
internal const val PARTICLE_WORLD_HALF_SPAN = HALF_GRID + 1.0

internal fun comprehendTileToWorldX(tileX: Int): Double = (tileX + 0.5 - HALF_GRID) * TILE_WORLD_SIZE
internal fun comprehendTileToWorldZ(tileZ: Int): Double = (tileZ + 0.5 - HALF_GRID) * TILE_WORLD_SIZE
internal fun comprehendWorldToTileX(worldX: Double): Int = floor(worldX / TILE_WORLD_SIZE + HALF_GRID).toInt()
internal fun comprehendWorldToTileZ(worldZ: Double): Int = floor(worldZ / TILE_WORLD_SIZE + HALF_GRID).toInt()

internal fun wrapWorldParticleCoord(value: Double, halfSpan: Double = PARTICLE_WORLD_HALF_SPAN): Double {
    val span = halfSpan * 2.0
    var v = value
    while (v > halfSpan) v -= span
    while (v < -halfSpan) v += span
    return v
}

/**
 * Computes world-space `(width, height)` for a sprite billboard while strictly preserving
 * `width / height == aspectRatio` so characters and objects are never stretched.
 */
internal fun computeBillboardDimensions(
    aspectRatio: Double,
    isCharacter: Boolean,
    pickable: Boolean,
    spec: StudyComprehendSpriteSpec? = null,
): Pair<Double, Double> {
    val safeAspect = aspectRatio.takeIf { it.isFinite() && it > 0.05 } ?: 1.0
    if (isCharacter) {
        var h = 1.65
        var w = h * safeAspect
        val maxCharWidth = 2.05
        if (w > maxCharWidth) {
            val scale = maxCharWidth / w
            w *= scale
            h *= scale
        }
        return w to h
    }
    var h = (spec?.scaleHeight?.toDouble() ?: if (pickable) 0.72 else 1.35).coerceIn(0.45, 3.4)
    var w = h * safeAspect
    val maxPropWidth = max(1.15, (spec?.scaleWidth?.toDouble() ?: 1.25) * 1.45).coerceIn(0.65, 3.6)
    if (w > maxPropWidth) {
        val scale = maxPropWidth / w
        w *= scale
        h *= scale
    }
    return w to h
}

/**
 * Creates a vertical quad facing `-Z` whose local origin `(0, 0, 0)` is at its bottom center
 * (`y in [0, height]`), so rotations such as wind sway (`rollZ`) pivot around the base on the ground.
 */
internal fun createBottomPivotBillboardPlane(
    bjs: BabylonLiteModule,
    engine: EngineContext,
    name: String,
    width: Double,
    height: Double,
): Mesh {
    val hw = (width / 2.0).toFloat()
    val h = height.toFloat()
    val positions = Float32Array(12)
    positions[0] = -hw; positions[1] = 0f; positions[2] = 0f
    positions[3] = hw; positions[4] = 0f; positions[5] = 0f
    positions[6] = hw; positions[7] = h; positions[8] = 0f
    positions[9] = -hw; positions[10] = h; positions[11] = 0f

    val normals = Float32Array(12)
    normals[2] = -1f
    normals[5] = -1f
    normals[8] = -1f
    normals[11] = -1f

    val uvs = Float32Array(8)
    uvs[0] = 0f; uvs[1] = 0f
    uvs[2] = 1f; uvs[3] = 0f
    uvs[4] = 1f; uvs[5] = 1f
    uvs[6] = 0f; uvs[7] = 1f

    val indices = Uint32Array(6)
    val idxDyn = indices.asDynamic()
    idxDyn[0] = 0; idxDyn[1] = 1; idxDyn[2] = 2
    idxDyn[3] = 0; idxDyn[4] = 2; idxDyn[5] = 3

    return bjs.createMeshFromData(engine, name, positions, normals, indices, uvs)
}

/**
 * Compute vertical Y-axis billboard yaw so a Babylon Lite plane (whose front normal is -Z)
 * faces horizontally toward `(camX, camZ)` without tilting vertically.
 */
internal fun billboardYawTowardCamera(
    objX: Double,
    objZ: Double,
    camX: Double,
    camZ: Double,
): Double = atan2(-(camX - objX), -(camZ - objZ))

/**
 * Computes the opening camera `alpha` (radians) for a scene so the camera stands behind
 * `(playerSpawnX, playerSpawnZ)` and looks across the custom layout toward its characters
 * and interactive areas instead of always facing one hardcoded compass axis.
 */
internal fun computeInitialCameraAlpha(scene: StudyComprehendScene): Double {
    val spawnWorldX = comprehendTileToWorldX(scene.playerSpawnX)
    val spawnWorldZ = comprehendTileToWorldZ(scene.playerSpawnZ)

    val characterObjs = scene.objects.filter { it.isCharacter }
    val interactiveObjs = scene.objects.filter { it.actions.isNotEmpty() }
    val focalCandidates = when {
        characterObjs.isNotEmpty() -> characterObjs
        interactiveObjs.isNotEmpty() -> interactiveObjs
        else -> emptyList()
    }
    val focusWorldX = if (focalCandidates.isNotEmpty()) {
        focalCandidates.sumOf { comprehendTileToWorldX(it.x) } / focalCandidates.size
    } else {
        0.0
    }
    val focusWorldZ = if (focalCandidates.isNotEmpty()) {
        focalCandidates.sumOf { comprehendTileToWorldZ(it.z) } / focalCandidates.size
    } else {
        0.0
    }

    var dx = focusWorldX - spawnWorldX
    var dz = focusWorldZ - spawnWorldZ
    if (hypot2(dx, dz) < 1.5) {
        dx = -spawnWorldX
        dz = -spawnWorldZ
    }
    val dist = hypot2(dx, dz)
    val naturalAlpha = if (dist > 0.25) atan2(-dz, -dx) else PI / 2.0

    val customDeg = scene.initialCameraYawDegrees
    if (customDeg != null && customDeg.isFinite()) {
        val customRad = customDeg.toDouble() * PI / 180.0
        if (dist < 2.5) return customRad
        val customFwdX = -cos(customRad)
        val customFwdZ = -sin(customRad)
        val dot = (customFwdX * (dx / dist)) + (customFwdZ * (dz / dist))
        if (dot > 0.25) return customRad
    }
    return naturalAlpha
}

internal fun parseHexColorRgbOrNull(hex: String?): Triple<Double, Double, Double>? {
    val cleaned = hex?.trim()?.removePrefix("#") ?: return null
    if (cleaned.length != 6) return null
    val rgb = cleaned.toIntOrNull(16) ?: return null
    val r = ((rgb shr 16) and 0xff) / 255.0
    val g = ((rgb shr 8) and 0xff) / 255.0
    val b = (rgb and 0xff) / 255.0
    return Triple(r, g, b)
}

internal fun resolveIndoorWallGround(scene: StudyComprehendScene): StudyComprehendGroundType {
    scene.wallMaterial?.let { return it }
    val wallCounts = HashMap<StudyComprehendGroundType, Int>()
    val floorCounts = HashMap<StudyComprehendGroundType, Int>()
    for (tile in scene.tiles) {
        if (tile.ground.isWallStructure) {
            wallCounts[tile.ground] = (wallCounts[tile.ground] ?: 0) + 1
        } else if (!tile.ground.blocksMovementByDefault) {
            floorCounts[tile.ground] = (floorCounts[tile.ground] ?: 0) + 1
        }
    }
    wallCounts.maxByOrNull { it.value }?.key?.let { return it }
    return when (floorCounts.maxByOrNull { it.value }?.key) {
        StudyComprehendGroundType.TATAMI_MAT, StudyComprehendGroundType.WOOD_FLOOR -> StudyComprehendGroundType.WOOD_WALL
        StudyComprehendGroundType.MARBLE_TILE -> StudyComprehendGroundType.PLASTER_WALL
        StudyComprehendGroundType.STONE_PAVING,
        StudyComprehendGroundType.MOSS_STONE,
        StudyComprehendGroundType.DARK_SLATE,
        -> StudyComprehendGroundType.STONE_WALL
        StudyComprehendGroundType.COURTYARD_BRICK,
        StudyComprehendGroundType.TERRACOTTA_TILE,
        -> StudyComprehendGroundType.BRICK_WALL
        else -> StudyComprehendGroundType.PLASTER_WALL
    }
}

internal fun resolveOutdoorSurroundingGround(scene: StudyComprehendScene): StudyComprehendGroundType {
    scene.surroundingGround?.takeUnless { it.isWallStructure }?.let { return it }
    val borderCounts = HashMap<StudyComprehendGroundType, Int>()
    val last = STUDY_COMPREHEND_GRID_SIZE - 1
    for (i in 0 until STUDY_COMPREHEND_GRID_SIZE) {
        listOf(
            scene.tileAt(i, 0),
            scene.tileAt(i, last),
            scene.tileAt(0, i),
            scene.tileAt(last, i),
        ).forEach { tile ->
            val g = tile?.ground ?: return@forEach
            if (!g.isWallStructure) {
                borderCounts[g] = (borderCounts[g] ?: 0) + 1
            }
        }
    }
    val dominantBorder = borderCounts.maxByOrNull { it.value }?.key
    return when {
        scene.weather == StudyComprehendWeather.SNOWY -> StudyComprehendGroundType.SNOW
        dominantBorder != null -> dominantBorder
        scene.weather == StudyComprehendWeather.DUSTY || scene.weather == StudyComprehendWeather.HOT -> StudyComprehendGroundType.SAND
        else -> StudyComprehendGroundType.GRASS
    }
}

private data class IndoorPerimeterWallRuntime(
    val mesh: Mesh,
    val normalOutX: Double,
    val normalOutZ: Double,
    val fullHeight: Double = 2.65,
    val cutawayHeight: Double = 0.46,
    var currentHeight: Double = 2.65,
)

private data class LoadedSpriteAsset(
    val texture: Texture2D,
    val aspectRatio: Double,
)

private class EntityBillboardRuntime(
    val objectData: StudyComprehendObject,
    val spriteMesh: Mesh,
    val labelMesh: Mesh,
    val shadowDisc: Mesh,
    val baseWidth: Double,
    val baseHeight: Double,
    val phaseOffset: Double,
    var currentScaleFactor: Double = 1.0,
)

private class WeatherParticleRuntime(
    val mesh: Mesh,
    var worldX: Double,
    var y: Double,
    var worldZ: Double,
    val maxY: Double,
    val speedY: Double,
    val driftX: Double,
    val driftZ: Double,
    val scaleX: Double,
    val scaleY: Double,
    val spinSpeed: Double,
    val phase: Double,
)

/**
 * Self-contained Babylon Lite WebGPU 3D controller for a single 32x32 Comprehend scene.
 */
internal class StudyComprehendSceneController(
    private val canvas: HTMLCanvasElement,
    private val world: StudyComprehendWorld,
    private val sceneData: StudyComprehendScene,
    initialState: StudyComprehendScenePlayState,
    private val onObjectTapped: (StudyComprehendObject) -> Unit = {},
    private val onObjectInteracted: (StudyComprehendObject) -> Unit,
    private val onPlayerTileChanged: (tileX: Int, tileZ: Int) -> Unit,
) {
    private var bjsModule: BabylonLiteModule? = null
    private var engine: EngineContext? = null
    private var scene: SceneContext? = null
    private var resizeDisposer: (() -> Unit)? = null
    private var cameraLimitsDisposer: (() -> Unit)? = null
    private var disposed = false

    private var playState: StudyComprehendScenePlayState = initialState
    var speakingObjectId: String? = null
    var keyboardEnabled: Boolean = true

    private var playerWorldX = comprehendTileToWorldX(initialState.playerX)
    private var playerWorldZ = comprehendTileToWorldZ(initialState.playerZ)
    private var walkQueue: ArrayDeque<Pair<Int, Int>> = ArrayDeque()
    private var pendingInteractObject: StudyComprehendObject? = null
    private var walkAnimPhase = 0.0
    private var elapsedSec = 0.0

    private var targetMarkerTimeLeft = 0.0
    private var targetMarkerX = playerWorldX
    private var targetMarkerZ = playerWorldZ

    private val pressedKeys = HashSet<String>()
    private var pointerDownX = 0.0
    private var pointerDownY = 0.0
    private var pointerIsDown = false
    private var pointerDragged = false
    private var lastPointerX = 0.0

    private val entityRuntimes = HashMap<String, EntityBillboardRuntime>()
    private val pickableBillboardMeshes = ArrayList<Mesh>()
    private val weatherParticles = ArrayList<WeatherParticleRuntime>()
    private val indoorPerimeterWalls = ArrayList<IndoorPerimeterWallRuntime>()
    private var waterMaterial: StandardMaterialProps? = null

    private var keyDownListener: ((Event) -> Unit)? = null
    private var keyUpListener: ((Event) -> Unit)? = null
    private var blurListener: ((Event) -> Unit)? = null
    private var mouseDownListener: ((Event) -> Unit)? = null
    private var mouseMoveListener: ((Event) -> Unit)? = null
    private var mouseUpListener: ((Event) -> Unit)? = null
    private var wheelListener: ((Event) -> Unit)? = null
    private var resizeListener: ((Event) -> Unit)? = null

    fun syncPlayState(updated: StudyComprehendScenePlayState) {
        playState = updated
    }

    suspend fun initialize() {
        val bjs = loadBabylonLite()
        if (disposed) return
        bjsModule = bjs
        bjs.enableStandardVertexColors()
        bjs.enableStandardUvOffset()

        val engineOpts = js("({ antialias: true })")
        val createdEngine = bjs.createEngine(canvas, engineOpts).await()
        if (disposed) {
            runCatching { bjs.disposeEngine(createdEngine) }
            return
        }
        engine = createdEngine
        resizeDisposer = runCatching { bjs.enableSurfaceResizeObserver(createdEngine) }.getOrNull()

        val createdScene = bjs.createSceneContext(createdEngine)
        scene = createdScene

        // 1. Camera following player, oriented dynamically from the custom scene entry toward its focal space
        val camera = bjs.createArcRotateCamera(
            alpha = computeInitialCameraAlpha(sceneData),
            beta = 0.96, // ~55 degree comfortable isometric/3D look angle
            radius = 12.5,
            target = bjs.vec3(playerWorldX, 0.85, playerWorldZ),
        )
        camera.fov = 0.72
        camera.nearPlane = 0.25
        camera.farPlane = 140.0
        camera.inertia = 0.82
        createdScene.camera = camera
        val limits = js("({})")
        limits.lowerBetaLimit = 0.58
        limits.upperBetaLimit = 1.24
        limits.lowerRadiusLimit = 7.0
        limits.upperRadiusLimit = 21.0
        cameraLimitsDisposer = bjs.setCameraLimits(camera, limits, createdScene)

        // 2. Atmosphere, sky, lighting & fog for timeOfDay + weather + indoor/outdoor
        buildAtmosphere(bjs, createdEngine, createdScene, sceneData)

        // 3. Realistic 32x32 ground tiles (thin-instanced per ground type with bump maps)
        buildGroundTiles(bjs, createdEngine, createdScene, sceneData)

        // 4. Indoor architectural walls / Outdoor distant horizon skirt
        buildBoundaryEnvironment(bjs, createdEngine, createdScene, sceneData)

        // 5. Load sprite textures & create vertical camera-facing billboards + target-language nameplates
        val spritesById = world.sprites.associateBy { it.id }
        val spriteAssets = HashMap<String, LoadedSpriteAsset>()
        for (spec in world.sprites) {
            val asset = loadMaskedSpriteTexture(bjs, createdEngine, spec)
            spriteAssets[spec.id] = asset
        }
        val playerSprite = loadPlayerSpriteTexture(bjs, createdEngine, world.playerSpriteUrl)
        val labelTextureCache = HashMap<String, Pair<Texture2D, Double>>()

        // Player billboard (bottom-center pivot, exact sprite aspect ratio) + nameplate + ground shadow
        val (playerWidth, playerHeight) = computeBillboardDimensions(
            aspectRatio = playerSprite.aspectRatio,
            isCharacter = true,
            pickable = false,
        )
        val playerMat = createBillboardMaterial(bjs, "player-mat", playerSprite.texture)
        val playerMesh = createBottomPivotBillboardPlane(
            bjs = bjs,
            engine = createdEngine,
            name = "player-billboard",
            width = playerWidth,
            height = playerHeight,
        )
        playerMesh.material = playerMat
        playerMesh.pickable = false
        playerMesh.position.set(playerWorldX, 0.0, playerWorldZ)
        bjs.addToScene(createdScene, playerMesh)

        val playerShadow = createShadowDisc(
            bjs,
            createdEngine,
            radius = (playerWidth * 0.42).coerceIn(0.28, 0.52),
            opacity = 0.36,
        )
        playerShadow.position.set(playerWorldX, 0.015, playerWorldZ)
        bjs.addToScene(createdScene, playerShadow)

        val playerLabelMesh = if (world.playerTargetName.isNotBlank()) {
            createLabelBillboard(
                bjs = bjs,
                engine = createdEngine,
                text = world.playerTargetName,
                accentColor = "#6ee7b7",
                cache = labelTextureCache,
            ).also {
                it.position.set(playerWorldX, playerHeight + 0.28, playerWorldZ)
                bjs.addToScene(createdScene, it)
            }
        } else {
            null
        }

        // Destination click ring
        val destinationRing = createHighlightRing(bjs, createdEngine, radius = 0.42, r = 0.98, g = 0.84, b = 0.35)
        destinationRing.position.set(playerWorldX, -10.0, playerWorldZ)
        bjs.addToScene(createdScene, destinationRing)

        // Scene objects & characters (all vertical camera-facing billboards with bottom-center pivot & target-language names)
        for ((index, obj) in sceneData.objects.withIndex()) {
            val spec = spritesById[obj.spriteId]
            val asset = spriteAssets[obj.spriteId] ?: createFallbackPropTexture(bjs, createdEngine, obj)
            val (baseW, baseH) = computeBillboardDimensions(
                aspectRatio = asset.aspectRatio,
                isCharacter = obj.isCharacter,
                pickable = obj.pickable,
                spec = spec,
            )

            val mesh = createBottomPivotBillboardPlane(
                bjs = bjs,
                engine = createdEngine,
                name = "obj-${obj.id}",
                width = baseW,
                height = baseH,
            )
            mesh.id = obj.id
            val mat = createBillboardMaterial(bjs, "mat-${obj.id}", asset.texture)
            if (obj.emitsWarmLight) {
                mat.emissiveColor = arrayOf(0.32, 0.22, 0.08)
            }
            mesh.material = mat
            mesh.pickable = true
            val meta = js("({})")
            meta.objectId = obj.id
            mesh.metadata = meta

            val wx = comprehendTileToWorldX(obj.x)
            val wz = comprehendTileToWorldZ(obj.z)
            mesh.position.set(wx, 0.0, wz)
            bjs.addToScene(createdScene, mesh)
            pickableBillboardMeshes.add(mesh)

            val shadow = createShadowDisc(
                bjs,
                createdEngine,
                radius = (baseW * 0.36).coerceIn(0.24, 0.72),
                opacity = 0.3,
            )
            shadow.position.set(wx, 0.012, wz)
            bjs.addToScene(createdScene, shadow)

            val labelAccent = when {
                obj.isCharacter -> "#fde68a"
                obj.pickable -> "#93c5fd"
                obj.actions.isNotEmpty() -> "#e2e8f0"
                else -> "#cbd5e1"
            }
            val labelMesh = createLabelBillboard(
                bjs = bjs,
                engine = createdEngine,
                text = obj.targetName,
                accentColor = labelAccent,
                cache = labelTextureCache,
            )
            labelMesh.position.set(wx, baseH + 0.30, wz)
            bjs.addToScene(createdScene, labelMesh)

            // Warm point lights for lanterns/lamps/braziers (especially atmospheric at night / indoors)
            if (obj.emitsWarmLight) {
                val pt = bjs.createPointLight(
                    arrayOf(wx, baseH * 0.75 + 0.35, wz),
                    if (sceneData.timeOfDay == StudyComprehendTimeOfDay.NIGHT) 1.65 else 0.75,
                )
                pt.diffuse = arrayOf(1.0, 0.78, 0.44)
                pt.specular = arrayOf(0.5, 0.38, 0.2)
                pt.range = 7.5
                bjs.addToScene(createdScene, pt)
            }

            entityRuntimes[obj.id] = EntityBillboardRuntime(
                objectData = obj,
                spriteMesh = mesh,
                labelMesh = labelMesh,
                shadowDisc = shadow,
                baseWidth = baseW,
                baseHeight = baseH,
                phaseOffset = (index * 1.37) % (2.0 * PI),
                currentScaleFactor = if (sceneData.isObjectPresentOnMap(obj, playState)) 1.0 else 0.0,
            )
        }

        // 6. Animated 3D weather particles
        buildWeatherParticles(bjs, createdEngine, createdScene, sceneData)

        // 7. Frame loop: movement, smooth camera follow, vertical billboard facing, animations
        bjs.onBeforeRender(createdScene) { rawDeltaMs ->
            if (disposed) return@onBeforeRender
            val dt = (rawDeltaMs / 1000.0).coerceIn(0.001, 0.06)
            elapsedSec += dt

            updatePlayerMovement(dt, camera)
            updateCameraFollow(dt, camera)

            val camX = camera.target.x + camera.radius * cos(camera.alpha) * sin(camera.beta)
            val camZ = camera.target.z + camera.radius * sin(camera.alpha) * sin(camera.beta)

            // Player billboard vertical facing + walk stride animation (uniform scale preserves exact proportions)
            val playerYaw = billboardYawTowardCamera(playerWorldX, playerWorldZ, camX, camZ)
            val strideBob = abs(sin(walkAnimPhase)) * 0.10
            val idleBreath = 1.0 + sin(elapsedSec * 2.4) * 0.010
            playerMesh.position.set(playerWorldX, strideBob, playerWorldZ)
            playerMesh.rotation.set(0.0, playerYaw, sin(walkAnimPhase) * 0.032)
            playerMesh.scaling.set(idleBreath, idleBreath, 1.0)
            playerShadow.position.set(playerWorldX, 0.015, playerWorldZ)
            playerLabelMesh?.let { lbl ->
                lbl.position.set(playerWorldX, playerHeight * idleBreath + 0.28 + strideBob, playerWorldZ)
                lbl.rotation.set(0.0, playerYaw, 0.0)
            }

            // Destination click pulse
            if (targetMarkerTimeLeft > 0.0) {
                targetMarkerTimeLeft = max(0.0, targetMarkerTimeLeft - dt)
                val progress = 1.0 - (targetMarkerTimeLeft / 0.85)
                val pulseScale = 0.65 + progress * 0.55
                destinationRing.position.set(targetMarkerX, 0.028, targetMarkerZ)
                destinationRing.scaling.set(pulseScale, 1.0, pulseScale)
                bjs.setMeshOpacity(destinationRing, (1.0 - progress).coerceIn(0.0, 0.85))
            } else {
                destinationRing.position.set(playerWorldX, -10.0, playerWorldZ)
            }

            // Water UV animation
            waterMaterial?.let { wMat ->
                wMat.uvOffset = arrayOf(
                    (elapsedSec * 0.045) % 1.0,
                    (sin(elapsedSec * 0.7) * 0.03),
                )
            }

            // Update all object & character vertical billboards & animations
            val currentState = playState
            for (runtime in entityRuntimes.values) {
                val obj = runtime.objectData
                val present = sceneData.isObjectPresentOnMap(obj, currentState)
                val isOpenedPassage = obj.opensPassage && obj.id in currentState.openedObjectIds
                val targetScale = when {
                    !present -> 0.0
                    isOpenedPassage -> 0.18
                    else -> 1.0
                }
                val blend = 1.0 - exp(-dt * 12.0)
                runtime.currentScaleFactor += (targetScale - runtime.currentScaleFactor) * blend

                if (runtime.currentScaleFactor < 0.02) {
                    runtime.spriteMesh.position.set(comprehendTileToWorldX(obj.x), -20.0, comprehendTileToWorldZ(obj.z))
                    runtime.labelMesh.position.set(comprehendTileToWorldX(obj.x), -20.0, comprehendTileToWorldZ(obj.z))
                    runtime.shadowDisc.position.set(comprehendTileToWorldX(obj.x), -20.0, comprehendTileToWorldZ(obj.z))
                    continue
                }

                val wx = comprehendTileToWorldX(obj.x) + if (isOpenedPassage) 0.42 else 0.0
                val wz = comprehendTileToWorldZ(obj.z)
                val yaw = billboardYawTowardCamera(wx, wz, camX, camZ)
                val isSpeaking = speakingObjectId == obj.id
                val isToggled = obj.id in currentState.toggledObjectIds

                var bobY = 0.0
                var rollZ = 0.0
                var sy = runtime.currentScaleFactor
                var sx = runtime.currentScaleFactor

                when {
                    obj.isCharacter -> {
                        if (isSpeaking) {
                            bobY = abs(sin(elapsedSec * 8.5)) * 0.06
                            val talkPulse = 1.0 + sin(elapsedSec * 12.0) * 0.024
                            sx *= talkPulse
                            sy *= talkPulse
                        } else {
                            val breath = 1.0 + sin(elapsedSec * 2.2 + runtime.phaseOffset) * 0.010
                            sx *= breath
                            sy *= breath
                        }
                    }
                    obj.pickable -> {
                        bobY = (sin(elapsedSec * 3.1 + runtime.phaseOffset) + 1.0) * 0.045
                    }
                    obj.swaysInWind -> {
                        val windStrength = if (sceneData.weather == StudyComprehendWeather.BREEZY ||
                            sceneData.weather == StudyComprehendWeather.RAINY
                        ) 0.048 else 0.022
                        rollZ = sin(elapsedSec * 1.85 + runtime.phaseOffset) * windStrength
                    }
                }
                if (isToggled) {
                    val mat = runtime.spriteMesh.material.unsafeCast<StandardMaterialProps>()
                    mat.emissiveColor = arrayOf(0.38, 0.30, 0.12)
                }

                val currentH = runtime.baseHeight * sy
                // Bottom-pivoted billboard: local (0, 0, 0) is bottom-center so rollZ sways from the base!
                runtime.spriteMesh.position.set(wx, bobY, wz)
                runtime.spriteMesh.rotation.set(0.0, yaw, rollZ)
                runtime.spriteMesh.scaling.set(sx, sy, 1.0)

                runtime.shadowDisc.position.set(wx, 0.012, wz)
                runtime.shadowDisc.scaling.set(runtime.currentScaleFactor, 1.0, runtime.currentScaleFactor)

                runtime.labelMesh.position.set(wx, currentH + 0.26 + bobY, wz)
                runtime.labelMesh.rotation.set(0.0, yaw, 0.0)
                runtime.labelMesh.scaling.set(runtime.currentScaleFactor, runtime.currentScaleFactor, 1.0)
            }

            // Animate weather particles in world space (independent of player movement)
            updateWeatherParticles(dt)
            updateIndoorPerimeterCutaway(dt, camera)
        }

        attachInputListeners(bjs, camera)
        bjs.registerScene(createdScene).await()
        if (disposed) return
        bjs.startEngine(createdEngine).await()
    }

    private fun updatePlayerMovement(dt: Double, camera: ArcRotateCamera) {
        var inputForward = 0.0
        var inputRight = 0.0
        if ("w" in pressedKeys || "arrowup" in pressedKeys) inputForward += 1.0
        if ("s" in pressedKeys || "arrowdown" in pressedKeys) inputForward -= 1.0
        if ("d" in pressedKeys || "arrowright" in pressedKeys) inputRight += 1.0
        if ("a" in pressedKeys || "arrowleft" in pressedKeys) inputRight -= 1.0

        val minCoord = comprehendTileToWorldX(0) - 0.35
        val maxCoord = comprehendTileToWorldX(STUDY_COMPREHEND_GRID_SIZE - 1) + 0.35

        if (inputForward != 0.0 || inputRight != 0.0) {
            // Manual WASD / Arrow movement overrides click-to-walk queue
            walkQueue.clear()
            pendingInteractObject = null

            val len = sqrt(inputForward * inputForward + inputRight * inputRight)
            val nf = inputForward / len
            val nr = inputRight / len

            // Camera-relative horizontal forward & right vectors in left-handed XZ space
            val fwdX = -cos(camera.alpha)
            val fwdZ = -sin(camera.alpha)
            val rightX = fwdZ
            val rightZ = -fwdX

            val vx = (fwdX * nf + rightX * nr) * PLAYER_MOVE_SPEED * dt
            val vz = (fwdZ * nf + rightZ * nr) * PLAYER_MOVE_SPEED * dt

            val candidateX = (playerWorldX + vx).coerceIn(minCoord, maxCoord)
            val candidateZ = (playerWorldZ + vz).coerceIn(minCoord, maxCoord)

            if (canStandAtWorld(candidateX, playerWorldZ)) {
                playerWorldX = candidateX
            }
            if (canStandAtWorld(playerWorldX, candidateZ)) {
                playerWorldZ = candidateZ
            }
            walkAnimPhase += dt * 13.5
            commitPlayerTile()
            return
        }

        val nextWaypoint = walkQueue.firstOrNull()
        if (nextWaypoint != null) {
            val targetX = comprehendTileToWorldX(nextWaypoint.first)
            val targetZ = comprehendTileToWorldZ(nextWaypoint.second)
            val dx = targetX - playerWorldX
            val dz = targetZ - playerWorldZ
            val dist = sqrt(dx * dx + dz * dz)
            val step = PLAYER_MOVE_SPEED * dt
            if (dist <= step + 0.02) {
                playerWorldX = targetX
                playerWorldZ = targetZ
                walkQueue.removeFirst()
                commitPlayerTile()
                if (walkQueue.isEmpty()) {
                    pendingInteractObject?.let { targetObj ->
                        pendingInteractObject = null
                        onObjectInteracted(targetObj)
                    }
                }
            } else {
                val nx = playerWorldX + (dx / dist) * step
                val nz = playerWorldZ + (dz / dist) * step
                if (canStandAtWorld(nx, nz)) {
                    playerWorldX = nx
                    playerWorldZ = nz
                    walkAnimPhase += dt * 13.5
                    commitPlayerTile()
                } else {
                    walkQueue.clear()
                    pendingInteractObject = null
                }
            }
        } else {
            // Smoothly decay walk stride phase back to neutral standing
            walkAnimPhase *= exp(-dt * 10.0)
        }
    }

    private fun canStandAtWorld(wx: Double, wz: Double): Boolean {
        val r = 0.24
        val corners = arrayOf(
            (wx - r) to (wz - r),
            (wx + r) to (wz - r),
            (wx - r) to (wz + r),
            (wx + r) to (wz + r),
        )
        for ((cx, cz) in corners) {
            val tx = comprehendWorldToTileX(cx)
            val tz = comprehendWorldToTileZ(cz)
            if (!sceneData.isTileWalkable(playState, tx, tz)) return false
        }
        return true
    }

    private fun commitPlayerTile() {
        val tx = comprehendWorldToTileX(playerWorldX).coerceIn(0, STUDY_COMPREHEND_GRID_SIZE - 1)
        val tz = comprehendWorldToTileZ(playerWorldZ).coerceIn(0, STUDY_COMPREHEND_GRID_SIZE - 1)
        if (tx != playState.playerX || tz != playState.playerZ) {
            playState = playState.copy(playerX = tx, playerZ = tz)
            onPlayerTileChanged(tx, tz)
        }
    }

    private fun updateCameraFollow(dt: Double, camera: ArcRotateCamera) {
        val followBlend = 1.0 - exp(-dt * 8.5)
        camera.target.x += (playerWorldX - camera.target.x) * followBlend
        camera.target.y += (0.85 - camera.target.y) * followBlend
        camera.target.z += (playerWorldZ - camera.target.z) * followBlend
    }

    private fun attachInputListeners(bjs: BabylonLiteModule, camera: ArcRotateCamera) {
        val onKeyDown: (Event) -> Unit = { raw ->
            if (keyboardEnabled) {
                val ev = raw.unsafeCast<KeyboardEvent>()
                val activeTag = (document.activeElement as? org.w3c.dom.HTMLElement)?.tagName?.lowercase()
                if (activeTag != "input" && activeTag != "textarea" && activeTag != "select") {
                    val k = ev.key.lowercase()
                    if (k in setOf("w", "a", "s", "d", "arrowup", "arrowdown", "arrowleft", "arrowright")) {
                        ev.preventDefault()
                        pressedKeys.add(k)
                    } else if (k == "q") {
                        camera.alpha -= 0.14
                    } else if (k == "e") {
                        camera.alpha += 0.14
                    }
                }
            }
        }
        val onKeyUp: (Event) -> Unit = { raw ->
            val ev = raw.unsafeCast<KeyboardEvent>()
            pressedKeys.remove(ev.key.lowercase())
        }
        val onBlur: (Event) -> Unit = { pressedKeys.clear() }

        val onPointerDown: (Event) -> Unit = { raw ->
            val ev = raw.unsafeCast<MouseEvent>()
            if (ev.button.toInt() == 0) {
                pointerIsDown = true
                pointerDragged = false
                pointerDownX = ev.clientX.toDouble()
                pointerDownY = ev.clientY.toDouble()
                lastPointerX = pointerDownX
            }
        }
        val onPointerMove: (Event) -> Unit = { raw ->
            if (pointerIsDown) {
                val ev = raw.unsafeCast<MouseEvent>()
                val cx = ev.clientX.toDouble()
                val cy = ev.clientY.toDouble()
                val dist = hypot2(cx - pointerDownX, cy - pointerDownY)
                if (dist > 7.0) {
                    pointerDragged = true
                    val dx = cx - lastPointerX
                    camera.alpha -= dx * 0.0055
                }
                lastPointerX = cx
            }
        }
        val onPointerUp: (Event) -> Unit = { raw ->
            val ev = raw.unsafeCast<MouseEvent>()
            if (pointerIsDown && ev.button.toInt() == 0) {
                pointerIsDown = false
                if (!pointerDragged) {
                    handleCanvasTap(bjs, ev.clientX.toDouble(), ev.clientY.toDouble(), camera)
                }
            }
        }
        val onWheel: (Event) -> Unit = { raw ->
            val ev = raw.unsafeCast<WheelEvent>()
            ev.preventDefault()
            val delta = if (ev.deltaY > 0) 1.1 else -1.1
            camera.radius = (camera.radius + delta).coerceIn(7.0, 21.0)
        }
        val onResize: (Event) -> Unit = {
            engine?.let { runCatching { bjs.resizeEngine(it) } }
        }

        keyDownListener = onKeyDown
        keyUpListener = onKeyUp
        blurListener = onBlur
        mouseDownListener = onPointerDown
        mouseMoveListener = onPointerMove
        mouseUpListener = onPointerUp
        wheelListener = onWheel
        resizeListener = onResize

        window.addEventListener("keydown", onKeyDown)
        window.addEventListener("keyup", onKeyUp)
        window.addEventListener("blur", onBlur)
        window.addEventListener("resize", onResize)
        canvas.addEventListener("pointerdown", onPointerDown)
        window.addEventListener("pointermove", onPointerMove)
        window.addEventListener("pointerup", onPointerUp)
        canvas.addEventListener("wheel", onWheel, js("({ passive: false })"))
    }

    private fun handleCanvasTap(bjs: BabylonLiteModule, clientX: Double, clientY: Double, camera: ArcRotateCamera) {
        if (scene == null) return
        val rect = canvas.getBoundingClientRect()
        if (rect.width <= 1.0 || rect.height <= 1.0) return
        val localX = clientX - rect.left
        val localY = clientY - rect.top
        if (localX < 0 || localY < 0 || localX > rect.width || localY > rect.height) return

        val aspect = rect.width / rect.height
        val vp = bjs.getViewProjectionMatrix(camera, aspect)
        val ray = bjs.createPickingRay(localX, localY, vp, rect.width, rect.height) ?: return

        // 1. Raycast active object & character billboards first
        val activePickables = pickableBillboardMeshes.filter { mesh ->
            val objId = mesh.metadata?.objectId as? String ?: return@filter false
            val obj = entityRuntimes[objId]?.objectData ?: return@filter false
            sceneData.isObjectPresentOnMap(obj, playState)
        }.toTypedArray()

        var spokenNonBlockingDecorId: String? = null
        if (activePickables.isNotEmpty()) {
            val hit = bjs.pickMeshesWithRay(activePickables, ray)
            val hitMesh = hit.pickedMesh
            val hitObjId = hitMesh?.metadata?.objectId as? String
            val hitObj = hitObjId?.let { entityRuntimes[it]?.objectData }
            if (hit.hit && hitObj != null) {
                if (hitObj.isCharacter || hitObj.actions.isNotEmpty()) {
                    handleObjectTap(hitObj)
                    return
                }
                // Decorative non-character prop: speak its floating target-language name out loud!
                onObjectTapped(hitObj)
                spokenNonBlockingDecorId = hitObj.id
                if (hitObj.blocksMovement) {
                    return
                }
            }
        }

        // 2. Analytic intersection with ground plane y = 0
        val dirY = ray.direction[1]
        if (abs(dirY) < 1e-5) return
        val t = -ray.origin[1] / dirY
        if (t <= 0.0) return
        val hitX = ray.origin[0] + t * ray.direction[0]
        val hitZ = ray.origin[2] + t * ray.direction[2]
        val tileX = comprehendWorldToTileX(hitX)
        val tileZ = comprehendWorldToTileZ(hitZ)
        if (tileX !in 0 until STUDY_COMPREHEND_GRID_SIZE || tileZ !in 0 until STUDY_COMPREHEND_GRID_SIZE) return

        // Check if that tile holds a present object/character
        val tileObj = sceneData.objects.firstOrNull {
            it.x == tileX && it.z == tileZ && sceneData.isObjectPresentOnMap(it, playState)
        }
        if (tileObj != null) {
            if (tileObj.isCharacter || tileObj.actions.isNotEmpty()) {
                handleObjectTap(tileObj)
                return
            }
            if (tileObj.id != spokenNonBlockingDecorId) {
                onObjectTapped(tileObj)
            }
            if (tileObj.blocksMovement) {
                return
            }
        }

        // Otherwise walk to that ground tile via A*
        val startX = comprehendWorldToTileX(playerWorldX).coerceIn(0, STUDY_COMPREHEND_GRID_SIZE - 1)
        val startZ = comprehendWorldToTileZ(playerWorldZ).coerceIn(0, STUDY_COMPREHEND_GRID_SIZE - 1)
        val path = sceneData.findWalkPath(playState, startX, startZ, tileX, tileZ)
        walkQueue.clear()
        pendingInteractObject = null
        if (path.isNotEmpty()) {
            val dest = path.last()
            targetMarkerX = comprehendTileToWorldX(dest.first)
            targetMarkerZ = comprehendTileToWorldZ(dest.second)
            targetMarkerTimeLeft = 0.85
            walkQueue.addAll(path)
        }
    }

    private fun handleObjectTap(obj: StudyComprehendObject) {
        if (!obj.isCharacter) {
            onObjectTapped(obj)
        }
        val pTileX = comprehendWorldToTileX(playerWorldX).coerceIn(0, STUDY_COMPREHEND_GRID_SIZE - 1)
        val pTileZ = comprehendWorldToTileZ(playerWorldZ).coerceIn(0, STUDY_COMPREHEND_GRID_SIZE - 1)
        val distChebyshev = max(abs(pTileX - obj.x), abs(pTileZ - obj.z))

        // Tapping a character repeats what they said immediately if in earshot, or walks up and listens
        if (distChebyshev <= 2) {
            walkQueue.clear()
            pendingInteractObject = null
            onObjectInteracted(obj)
            return
        }

        val path = sceneData.findWalkPath(playState, pTileX, pTileZ, obj.x, obj.z)
        walkQueue.clear()
        if (path.isNotEmpty()) {
            val dest = path.last()
            targetMarkerX = comprehendTileToWorldX(dest.first)
            targetMarkerZ = comprehendTileToWorldZ(dest.second)
            targetMarkerTimeLeft = 0.85
            pendingInteractObject = obj
            walkQueue.addAll(path)
        } else if (obj.isCharacter) {
            // Even if separated by a counter, let the player hear the character
            onObjectInteracted(obj)
        }
    }

    private fun buildAtmosphere(
        bjs: BabylonLiteModule,
        engine: EngineContext,
        scene: SceneContext,
        data: StudyComprehendScene,
    ) {
        data class AtmospherePalette(
            val skyTop: Triple<Double, Double, Double>,
            val skyHorizon: Triple<Double, Double, Double>,
            val clearColor: Triple<Double, Double, Double>,
            val sunDir: Array<Double>,
            val sunColor: Array<Double>,
            val sunIntensity: Double,
            val hemiSky: Array<Double>,
            val hemiGround: Array<Double>,
            val hemiIntensity: Double,
            val fogDensity: Double,
        )

        val base = when (data.timeOfDay) {
            StudyComprehendTimeOfDay.MORNING -> AtmospherePalette(
                skyTop = Triple(0.36, 0.56, 0.84),
                skyHorizon = Triple(0.96, 0.76, 0.62),
                clearColor = Triple(0.88, 0.76, 0.68),
                sunDir = arrayOf(-0.68, -0.46, -0.56),
                sunColor = arrayOf(1.0, 0.86, 0.68),
                sunIntensity = 1.05,
                hemiSky = arrayOf(0.85, 0.84, 0.94),
                hemiGround = arrayOf(0.42, 0.36, 0.32),
                hemiIntensity = 0.72,
                fogDensity = 0.012,
            )
            StudyComprehendTimeOfDay.DAYTIME -> AtmospherePalette(
                skyTop = Triple(0.24, 0.54, 0.90),
                skyHorizon = Triple(0.74, 0.88, 0.98),
                clearColor = Triple(0.70, 0.85, 0.96),
                sunDir = arrayOf(-0.38, -0.84, -0.38),
                sunColor = arrayOf(1.0, 0.97, 0.90),
                sunIntensity = 1.18,
                hemiSky = arrayOf(0.86, 0.92, 1.0),
                hemiGround = arrayOf(0.44, 0.42, 0.36),
                hemiIntensity = 0.78,
                fogDensity = 0.007,
            )
            StudyComprehendTimeOfDay.EVENING -> AtmospherePalette(
                skyTop = Triple(0.20, 0.22, 0.48),
                skyHorizon = Triple(0.95, 0.52, 0.34),
                clearColor = Triple(0.82, 0.48, 0.36),
                sunDir = arrayOf(0.72, -0.38, -0.58),
                sunColor = arrayOf(1.0, 0.64, 0.36),
                sunIntensity = 1.0,
                hemiSky = arrayOf(0.76, 0.58, 0.68),
                hemiGround = arrayOf(0.32, 0.24, 0.28),
                hemiIntensity = 0.64,
                fogDensity = 0.014,
            )
            StudyComprehendTimeOfDay.NIGHT -> AtmospherePalette(
                skyTop = Triple(0.04, 0.07, 0.16),
                skyHorizon = Triple(0.11, 0.17, 0.32),
                clearColor = Triple(0.08, 0.12, 0.24),
                sunDir = arrayOf(-0.42, -0.76, 0.48),
                sunColor = arrayOf(0.54, 0.68, 0.96),
                sunIntensity = 0.58,
                hemiSky = arrayOf(0.36, 0.44, 0.68),
                hemiGround = arrayOf(0.16, 0.18, 0.24),
                hemiIntensity = 0.52,
                fogDensity = 0.016,
            )
        }

        // Weather modulation
        val (fogMult, horizonBlend) = when (data.weather) {
            StudyComprehendWeather.RAINY -> 1.9 to Triple(0.42, 0.48, 0.56)
            StudyComprehendWeather.CLOUDY -> 1.45 to Triple(0.62, 0.66, 0.72)
            StudyComprehendWeather.MISTY -> 2.35 to Triple(0.78, 0.84, 0.88)
            StudyComprehendWeather.DUSTY -> 2.1 to Triple(0.84, 0.68, 0.46)
            StudyComprehendWeather.HOT -> 1.25 to Triple(0.96, 0.84, 0.62)
            StudyComprehendWeather.COLD, StudyComprehendWeather.SNOWY -> 1.55 to Triple(0.82, 0.90, 0.98)
            StudyComprehendWeather.SUNNY, StudyComprehendWeather.BREEZY -> 0.85 to base.skyHorizon
        }

        val mixFactor = if (data.weather == StudyComprehendWeather.SUNNY || data.weather == StudyComprehendWeather.BREEZY) 0.0 else 0.42
        val weatherMixedHorizon = Triple(
            lerp(base.skyHorizon.first, horizonBlend.first, mixFactor),
            lerp(base.skyHorizon.second, horizonBlend.second, mixFactor),
            lerp(base.skyHorizon.third, horizonBlend.third, mixFactor),
        )
        val sceneTint = parseHexColorRgbOrNull(data.ambientTintHex)
        val finalHorizon = if (sceneTint != null) {
            Triple(
                lerp(weatherMixedHorizon.first, sceneTint.first, 0.16),
                lerp(weatherMixedHorizon.second, sceneTint.second, 0.16),
                lerp(weatherMixedHorizon.third, sceneTint.third, 0.16),
            )
        } else {
            weatherMixedHorizon
        }
        val clear = if (data.environment == StudyComprehendEnvironment.INDOOR) {
            Triple(
                finalHorizon.first * 0.42,
                finalHorizon.second * 0.40,
                finalHorizon.third * 0.44,
            )
        } else {
            finalHorizon
        }

        val cc = js("({})")
        cc.r = clear.first
        cc.g = clear.second
        cc.b = clear.third
        cc.a = 1.0
        scene.clearColor = cc

        // Sky dome
        val tintedTop = if (sceneTint != null) {
            Triple(
                lerp(base.skyTop.first, sceneTint.first, 0.12),
                lerp(base.skyTop.second, sceneTint.second, 0.12),
                lerp(base.skyTop.third, sceneTint.third, 0.12),
            )
        } else {
            base.skyTop
        }
        val skyTex = createSkyGradientTexture(bjs, engine, tintedTop, finalHorizon, data.timeOfDay == StudyComprehendTimeOfDay.NIGHT)
        val skyOpts = js("({})")
        skyOpts.diameter = 115.0
        skyOpts.segments = 20
        val skySphere = bjs.createSphere(engine, skyOpts)
        skySphere.name = "sky-dome"
        skySphere.pickable = false
        val skyMat = bjs.createStandardMaterial()
        skyMat.backFaceCulling = false
        skyMat.disableLighting = true
        skyMat.emissiveColor = arrayOf(1.0, 1.0, 1.0)
        skyMat.diffuseColor = arrayOf(1.0, 1.0, 1.0)
        skyMat.diffuseTexture = skyTex
        skySphere.material = skyMat
        bjs.addToScene(scene, skySphere)

        val hemi = bjs.createHemisphericLight(arrayOf(0.0, 1.0, 0.0), if (data.environment == StudyComprehendEnvironment.INDOOR) base.hemiIntensity + 0.16 else base.hemiIntensity)
        hemi.diffuseColor = if (sceneTint != null) {
            arrayOf(
                lerp(base.hemiSky[0], sceneTint.first, 0.14),
                lerp(base.hemiSky[1], sceneTint.second, 0.14),
                lerp(base.hemiSky[2], sceneTint.third, 0.14),
            )
        } else {
            base.hemiSky
        }
        hemi.groundColor = base.hemiGround
        hemi.specularColor = arrayOf(0.18, 0.18, 0.18)
        bjs.addToScene(scene, hemi)

        val dir = bjs.createDirectionalLight(base.sunDir, if (data.weather == StudyComprehendWeather.RAINY || data.weather == StudyComprehendWeather.CLOUDY) base.sunIntensity * 0.68 else base.sunIntensity)
        dir.diffuse = base.sunColor
        dir.specular = arrayOf(0.32, 0.30, 0.26)
        bjs.addToScene(scene, dir)

        val fogCfg = js("({})")
        fogCfg.mode = 2 // EXP2 atmospheric fog
        fogCfg.density = (base.fogDensity * fogMult * if (data.environment == StudyComprehendEnvironment.INDOOR) 0.55 else 1.0).coerceIn(0.004, 0.032)
        fogCfg.start = 18.0
        fogCfg.end = 68.0
        fogCfg.color = arrayOf(clear.first, clear.second, clear.third)
        bjs.setFog(scene, fogCfg)
    }

    private fun buildGroundTiles(
        bjs: BabylonLiteModule,
        engine: EngineContext,
        scene: SceneContext,
        data: StudyComprehendScene,
    ) {
        val groupedIndices = HashMap<StudyComprehendGroundType, MutableList<Pair<Int, Int>>>()
        val wallIndices = HashMap<StudyComprehendGroundType, MutableList<Pair<Int, Int>>>()
        val fallbackUnderWall = data.tiles
            .asSequence()
            .map { it.ground }
            .filter { !it.blocksMovementByDefault }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?: if (data.environment == StudyComprehendEnvironment.INDOOR) {
                StudyComprehendGroundType.WOOD_FLOOR
            } else {
                StudyComprehendGroundType.STONE_PAVING
            }

        for (z in 0 until STUDY_COMPREHEND_GRID_SIZE) {
            for (x in 0 until STUDY_COMPREHEND_GRID_SIZE) {
                val tile = data.tileAt(x, z) ?: continue
                if (tile.ground.isWallStructure) {
                    groupedIndices.getOrPut(fallbackUnderWall) { ArrayList() }.add(x to z)
                    wallIndices.getOrPut(tile.ground) { ArrayList() }.add(x to z)
                } else {
                    groupedIndices.getOrPut(tile.ground) { ArrayList() }.add(x to z)
                }
            }
        }

        val wetWeather = data.weather == StudyComprehendWeather.RAINY
        val sceneSeed = data.id.hashCode() * 31 + data.title.hashCode()
        for ((groundType, coords) in groupedIndices) {
            if (coords.isEmpty()) continue
            val (diffuseTex, bumpTex) = createProceduralGroundTextures(bjs, engine, groundType)
            val mat = bjs.createStandardMaterial()
            mat.name = "ground-${groundType.name}"
            mat.diffuseTexture = diffuseTex
            bjs.setStandardBumpTexture(mat, bumpTex)
            mat.bumpLevel = 0.72
            if (groundType == StudyComprehendGroundType.WATER) {
                mat.specularColor = arrayOf(0.75, 0.82, 0.90)
                mat.specularPower = 96.0
                waterMaterial = mat
            } else if (groundType == StudyComprehendGroundType.MARBLE_TILE || wetWeather) {
                mat.specularColor = arrayOf(0.34, 0.35, 0.38)
                mat.specularPower = 56.0
            } else {
                mat.specularColor = arrayOf(0.08, 0.08, 0.08)
                mat.specularPower = 24.0
            }

            val tileOpts = js("({})")
            tileOpts.width = TILE_WORLD_SIZE * 1.004 // micro-overlap prevents subpixel seams
            tileOpts.height = TILE_WORLD_SIZE * 1.004
            tileOpts.subdivisions = 1
            val proto = bjs.createGround(engine, tileOpts)
            proto.name = "tiles-${groundType.name}"
            proto.material = mat
            proto.pickable = false
            proto.hasVertexAlpha = false

            val count = coords.size
            val matrices = Float32Array(count * 16)
            val colors = Float32Array(count * 4)
            val yOffset = if (groundType == StudyComprehendGroundType.WATER) -0.045 else 0.0

            for (i in 0 until count) {
                val (tx, tz) = coords[i]
                val wx = comprehendTileToWorldX(tx)
                val wz = comprehendTileToWorldZ(tz)
                val base = i * 16
                matrices[base + 0] = 1f
                matrices[base + 5] = 1f
                matrices[base + 10] = 1f
                matrices[base + 12] = wx.toFloat()
                matrices[base + 13] = yOffset.toFloat()
                matrices[base + 14] = wz.toFloat()
                matrices[base + 15] = 1f

                // Subtle organic per-tile shade variation seeded per scene
                val hash = tileHash(tx, tz, sceneSeed)
                val shade = (0.94 + (hash and 0xff) / 255.0 * 0.10).toFloat()
                val warmShift = (((hash shr 8) and 0xff) / 255.0 * 0.03 - 0.015).toFloat()
                val cb = i * 4
                colors[cb + 0] = (shade + warmShift).coerceIn(0.8f, 1.1f)
                colors[cb + 1] = shade
                colors[cb + 2] = (shade - warmShift).coerceIn(0.8f, 1.1f)
                colors[cb + 3] = 1f
            }

            bjs.setThinInstances(proto, matrices, count)
            bjs.setThinInstanceColors(proto, colors)
            bjs.enableThinInstanceWorldBounds(proto)
            bjs.addToScene(scene, proto)
        }

        // Render 3D interior room partitions, courtyard walls, and masonry walls
        val wallHeight = 1.95
        for ((wallType, coords) in wallIndices) {
            if (coords.isEmpty()) continue
            val (wallDiff, wallBump) = createProceduralGroundTextures(bjs, engine, wallType)
            val wallMat = bjs.createStandardMaterial()
            wallMat.name = "wall3d-${wallType.name}"
            wallMat.diffuseTexture = wallDiff
            bjs.setStandardBumpTexture(wallMat, wallBump)
            wallMat.bumpLevel = 0.80
            wallMat.specularColor = arrayOf(0.07, 0.07, 0.07)
            wallMat.specularPower = 24.0

            val boxOpts = js("({})")
            boxOpts.width = TILE_WORLD_SIZE * 1.004
            boxOpts.height = wallHeight
            boxOpts.depth = TILE_WORLD_SIZE * 1.004
            val wallProto = bjs.createBox(engine, boxOpts)
            wallProto.name = "walls-${wallType.name}"
            wallProto.material = wallMat
            wallProto.pickable = false
            wallProto.hasVertexAlpha = false

            val count = coords.size
            val matrices = Float32Array(count * 16)
            val colors = Float32Array(count * 4)
            for (i in 0 until count) {
                val (tx, tz) = coords[i]
                val wx = comprehendTileToWorldX(tx)
                val wz = comprehendTileToWorldZ(tz)
                val base = i * 16
                matrices[base + 0] = 1f
                matrices[base + 5] = 1f
                matrices[base + 10] = 1f
                matrices[base + 12] = wx.toFloat()
                matrices[base + 13] = (wallHeight * 0.5).toFloat()
                matrices[base + 14] = wz.toFloat()
                matrices[base + 15] = 1f

                val hash = tileHash(tx, tz, sceneSeed + 7919)
                val shade = (0.93 + (hash and 0xff) / 255.0 * 0.11).toFloat()
                val cb = i * 4
                colors[cb + 0] = shade
                colors[cb + 1] = shade
                colors[cb + 2] = shade
                colors[cb + 3] = 1f
            }

            bjs.setThinInstances(wallProto, matrices, count)
            bjs.setThinInstanceColors(wallProto, colors)
            bjs.enableThinInstanceWorldBounds(wallProto)
            bjs.addToScene(scene, wallProto)
        }
    }

    private fun buildBoundaryEnvironment(
        bjs: BabylonLiteModule,
        engine: EngineContext,
        scene: SceneContext,
        data: StudyComprehendScene,
    ) {
        if (data.environment == StudyComprehendEnvironment.INDOOR) {
            // Custom per-scene interior perimeter walls with Sims-style camera-side cutaway so any camera angle stays clear
            val wallSurface = resolveIndoorWallGround(data)
            val (wallDiff, wallBump) = createProceduralGroundTextures(bjs, engine, wallSurface)
            val wallMat = bjs.createStandardMaterial()
            wallMat.diffuseTexture = wallDiff
            bjs.setStandardBumpTexture(wallMat, wallBump)
            val tint = parseHexColorRgbOrNull(data.ambientTintHex)
            wallMat.diffuseColor = if (tint != null) {
                arrayOf(
                    lerp(0.92, tint.first, 0.14),
                    lerp(0.90, tint.second, 0.14),
                    lerp(0.86, tint.third, 0.14),
                )
            } else {
                arrayOf(0.92, 0.90, 0.86)
            }
            wallMat.specularColor = arrayOf(0.08, 0.08, 0.08)
            wallMat.uvScale = arrayOf(10.0, 1.4)

            fun addPerimeterWall(
                w: Double,
                d: Double,
                x: Double,
                z: Double,
                nx: Double,
                nz: Double,
            ) {
                val opts = js("({})")
                opts.width = w
                opts.height = 1.0 // scaled dynamically on Y
                opts.depth = d
                val wall = bjs.createBox(engine, opts)
                wall.material = wallMat
                wall.pickable = false
                val fullH = 2.65
                wall.scaling.set(1.0, fullH, 1.0)
                wall.position.set(x, fullH * 0.5, z)
                bjs.addToScene(scene, wall)
                indoorPerimeterWalls.add(
                    IndoorPerimeterWallRuntime(
                        mesh = wall,
                        normalOutX = nx,
                        normalOutZ = nz,
                        fullHeight = fullH,
                    ),
                )
            }
            val span = STUDY_COMPREHEND_GRID_SIZE * TILE_WORLD_SIZE + 0.8
            val half = STUDY_COMPREHEND_GRID_SIZE * TILE_WORLD_SIZE / 2.0 + 0.2
            addPerimeterWall(span, 0.45, 0.0, half, 0.0, 1.0)
            addPerimeterWall(span, 0.45, 0.0, -half, 0.0, -1.0)
            addPerimeterWall(0.45, span, -half, 0.0, -1.0, 0.0)
            addPerimeterWall(0.45, span, half, 0.0, 1.0, 0.0)
            scene.camera?.unsafeCast<ArcRotateCamera>()?.let { cam ->
                updateIndoorPerimeterCutaway(1.0, cam)
            }
        } else {
            // Custom per-scene outdoor surrounding terrain skirt matching the map's border biome & weather
            val outerType = resolveOutdoorSurroundingGround(data)
            val (skirtDiff, skirtBump) = createProceduralGroundTextures(bjs, engine, outerType)
            val skirtMat = bjs.createStandardMaterial()
            skirtMat.diffuseTexture = skirtDiff
            bjs.setStandardBumpTexture(skirtMat, skirtBump)
            skirtMat.bumpLevel = 0.45
            skirtMat.uvScale = arrayOf(24.0, 24.0)
            skirtMat.specularColor = if (outerType == StudyComprehendGroundType.WATER) {
                arrayOf(0.55, 0.64, 0.74)
            } else {
                arrayOf(0.04, 0.04, 0.04)
            }
            val skirtOpts = js("({})")
            skirtOpts.width = 110.0
            skirtOpts.height = 110.0
            val skirt = bjs.createGround(engine, skirtOpts)
            skirt.position.set(0.0, -0.08, 0.0)
            skirt.material = skirtMat
            skirt.pickable = false
            bjs.addToScene(scene, skirt)
        }
    }

    private fun updateIndoorPerimeterCutaway(dt: Double, camera: ArcRotateCamera) {
        if (indoorPerimeterWalls.isEmpty()) return
        val camDirX = cos(camera.alpha)
        val camDirZ = sin(camera.alpha)
        val blend = (1.0 - exp(-dt * 10.0)).coerceIn(0.0, 1.0)
        for (wall in indoorPerimeterWalls) {
            val facingCam = wall.normalOutX * camDirX + wall.normalOutZ * camDirZ
            val targetH = if (facingCam > 0.22) wall.cutawayHeight else wall.fullHeight
            wall.currentHeight += (targetH - wall.currentHeight) * blend
            wall.mesh.scaling.set(1.0, wall.currentHeight, 1.0)
            wall.mesh.position.set(wall.mesh.position.x, wall.currentHeight * 0.5, wall.mesh.position.z)
        }
    }

    private fun buildWeatherParticles(
        bjs: BabylonLiteModule,
        engine: EngineContext,
        scene: SceneContext,
        data: StudyComprehendScene,
    ) {
        if (data.environment == StudyComprehendEnvironment.INDOOR &&
            data.weather != StudyComprehendWeather.DUSTY
        ) return

        data class ParticleStyle(
            val count: Int,
            val isRainStreak: Boolean,
            val baseRadius: Double,
            val streakW: Double,
            val streakH: Double,
            val maxY: Double,
            val color: Array<Double>,
            val opacity: Double,
            val speedY: Double,
            val driftX: Double,
        )

        val style = when (data.weather) {
            StudyComprehendWeather.RAINY -> ParticleStyle(
                count = 110,
                isRainStreak = true,
                baseRadius = 0.0,
                streakW = 0.028,
                streakH = 0.48,
                maxY = 8.4,
                color = arrayOf(0.72, 0.84, 0.98),
                opacity = 0.46,
                speedY = -12.5,
                driftX = -1.6,
            )
            StudyComprehendWeather.SNOWY, StudyComprehendWeather.COLD -> ParticleStyle(
                count = 96,
                isRainStreak = false,
                baseRadius = 0.044,
                streakW = 0.0,
                streakH = 0.0,
                maxY = 8.0,
                color = arrayOf(0.96, 0.98, 1.0),
                opacity = 0.72,
                speedY = -1.45,
                driftX = 0.52,
            )
            StudyComprehendWeather.DUSTY, StudyComprehendWeather.HOT -> ParticleStyle(
                count = 96,
                isRainStreak = false,
                baseRadius = 0.022,
                streakW = 0.0,
                streakH = 0.0,
                maxY = 5.4,
                color = arrayOf(0.92, 0.76, 0.50),
                opacity = 0.44,
                speedY = -0.20,
                driftX = 1.65,
            )
            else -> ParticleStyle(
                count = 54,
                isRainStreak = false,
                baseRadius = 0.024,
                streakW = 0.0,
                streakH = 0.0,
                maxY = 6.2,
                color = arrayOf(1.0, 0.96, 0.78),
                opacity = 0.36,
                speedY = -0.16,
                driftX = 0.32,
            )
        }

        val mat = bjs.createStandardMaterial()
        mat.disableLighting = true
        mat.emissiveColor = style.color
        mat.diffuseColor = arrayOf(1.0, 1.0, 1.0)
        mat.backFaceCulling = false
        if (!style.isRainStreak) {
            val moteOpacityTex = createSoftMoteOpacityTexture(bjs, engine)
            bjs.setStandardOpacityTexture(mat, moteOpacityTex)
        }

        val worldSpan = PARTICLE_WORLD_HALF_SPAN * 2.0
        for (i in 0 until style.count) {
            // Quasi-random R3 sequence spreads particles evenly across the full 32x32 scene in world space
            val u1 = (i * 0.7548776662 + 0.13) % 1.0
            val u2 = (i * 0.5698402909 + 0.37) % 1.0
            val u3 = (i * 0.4301597090 + 0.71) % 1.0
            val pMesh = if (style.isRainStreak) {
                val opts = js("({})")
                opts.width = style.streakW
                opts.height = style.streakH
                bjs.createPlane(engine, opts)
            } else {
                val opts = js("({})")
                opts.radius = style.baseRadius
                opts.tessellation = 10
                bjs.createDisc(engine, opts)
            }
            pMesh.material = mat
            pMesh.pickable = false
            bjs.setMeshOpacity(pMesh, style.opacity * (0.72 + u3 * 0.45))

            val wx = (u1 - 0.5) * worldSpan
            val py = u2 * style.maxY + 0.25
            val wz = (u3 - 0.5) * worldSpan
            val sx = if (style.isRainStreak) 1.0 else (0.68 + ((i * 37) % 19) / 18.0 * 0.68)
            val sy = if (style.isRainStreak) 1.0 else (0.62 + ((i * 53) % 17) / 16.0 * 0.56)
            val spin = if (style.isRainStreak) 0.0 else ((i % 7) - 3) * 0.35

            pMesh.position.set(wx, py, wz)
            pMesh.scaling.set(sx, sy, 1.0)
            bjs.addToScene(scene, pMesh)
            weatherParticles.add(
                WeatherParticleRuntime(
                    mesh = pMesh,
                    worldX = wx,
                    y = py,
                    worldZ = wz,
                    maxY = style.maxY,
                    speedY = style.speedY * (0.82 + u2 * 0.36),
                    driftX = style.driftX,
                    driftZ = (u3 - 0.5) * 0.42,
                    scaleX = sx,
                    scaleY = sy,
                    spinSpeed = spin,
                    phase = i * 0.73,
                ),
            )
        }
    }

    private fun updateWeatherParticles(dt: Double) {
        val cam = scene?.camera?.unsafeCast<ArcRotateCamera>() ?: return
        val camX = cam.target.x + cam.radius * cos(cam.alpha) * sin(cam.beta)
        val camZ = cam.target.z + cam.radius * sin(cam.alpha) * sin(cam.beta)

        for (p in weatherParticles) {
            p.y += p.speedY * dt
            p.worldX = wrapWorldParticleCoord(
                p.worldX + (p.driftX + sin(elapsedSec * 1.4 + p.phase) * 0.32) * dt,
            )
            p.worldZ = wrapWorldParticleCoord(
                p.worldZ + (p.driftZ + cos(elapsedSec * 1.1 + p.phase) * 0.22) * dt,
            )
            if (p.y < 0.12) p.y += p.maxY
            if (p.y > p.maxY + 0.25) p.y -= p.maxY
            p.mesh.position.set(p.worldX, p.y, p.worldZ)
            val roll = p.phase + elapsedSec * p.spinSpeed
            p.mesh.rotation.set(0.0, billboardYawTowardCamera(p.worldX, p.worldZ, camX, camZ), roll)
            p.mesh.scaling.set(p.scaleX, p.scaleY, 1.0)
        }
    }

    private fun createBillboardMaterial(bjs: BabylonLiteModule, name: String, texture: Texture2D): StandardMaterialProps {
        val mat = bjs.createStandardMaterial()
        mat.name = name
        mat.diffuseTexture = texture
        mat.alphaCutOff = 0.22
        mat.backFaceCulling = false
        mat.specularColor = arrayOf(0.06, 0.06, 0.06)
        mat.specularPower = 32.0
        mat.ambientColor = arrayOf(0.18, 0.18, 0.20)
        return mat
    }

    private fun createShadowDisc(bjs: BabylonLiteModule, engine: EngineContext, radius: Double, opacity: Double): Mesh {
        val opts = js("({})")
        opts.radius = radius
        opts.tessellation = 20
        val disc = bjs.createDisc(engine, opts)
        disc.rotation.set(PI / 2.0, 0.0, 0.0)
        disc.pickable = false
        val mat = bjs.createStandardMaterial()
        mat.disableLighting = true
        mat.emissiveColor = arrayOf(0.04, 0.05, 0.07)
        mat.diffuseColor = arrayOf(1.0, 1.0, 1.0)
        mat.backFaceCulling = false
        disc.material = mat
        bjs.setMeshOpacity(disc, opacity)
        return disc
    }

    private fun createHighlightRing(
        bjs: BabylonLiteModule,
        engine: EngineContext,
        radius: Double,
        r: Double,
        g: Double,
        b: Double,
    ): Mesh {
        val size = 128
        val pixels = Uint8Array(size * size * 4)
        val half = size / 2.0
        for (py in 0 until size) {
            for (px in 0 until size) {
                val dx = (px + 0.5 - half) / half
                val dy = (py + 0.5 - half) / half
                val d = sqrt(dx * dx + dy * dy)
                val ring = if (d in 0.72..0.96) 255 else if (d < 0.72 && d > 0.58) 90 else 0
                val idx = (py * size + px) * 4
                pixels.asDynamic()[idx] = (r * 255).roundToInt()
                pixels.asDynamic()[idx + 1] = (g * 255).roundToInt()
                pixels.asDynamic()[idx + 2] = (b * 255).roundToInt()
                pixels.asDynamic()[idx + 3] = ring
            }
        }
        val texOpts = js("({ srgb: true, mipMaps: false })")
        val tex = bjs.createTexture2DFromPixels(engine, pixels, size, size, texOpts)
        val planeOpts = js("({})")
        planeOpts.width = radius * 2.0
        planeOpts.height = radius * 2.0
        val ringMesh = bjs.createPlane(engine, planeOpts)
        ringMesh.rotation.set(PI / 2.0, 0.0, 0.0)
        ringMesh.pickable = false
        val mat = bjs.createStandardMaterial()
        mat.diffuseTexture = tex
        mat.alphaCutOff = 0.2
        mat.disableLighting = true
        mat.emissiveColor = arrayOf(1.0, 1.0, 1.0)
        mat.backFaceCulling = false
        ringMesh.material = mat
        return ringMesh
    }

    private fun createLabelBillboard(
        bjs: BabylonLiteModule,
        engine: EngineContext,
        text: String,
        accentColor: String,
        cache: MutableMap<String, Pair<Texture2D, Double>>,
    ): Mesh {
        val key = "$accentColor|$text"
        val (tex, aspect) = cache.getOrPut(key) {
            renderTargetLanguageLabelTexture(bjs, engine, text, accentColor)
        }
        val labelHeight = 0.34
        val labelWidth = (labelHeight * aspect).coerceIn(0.65, 3.6)
        val opts = js("({})")
        opts.width = labelWidth
        opts.height = labelHeight
        val mesh = bjs.createPlane(engine, opts)
        mesh.pickable = false
        val mat = bjs.createStandardMaterial()
        mat.diffuseTexture = tex
        mat.alphaCutOff = 0.32
        mat.disableLighting = true
        mat.emissiveColor = arrayOf(1.0, 1.0, 1.0)
        mat.diffuseColor = arrayOf(1.0, 1.0, 1.0)
        mat.backFaceCulling = false
        mesh.material = mat
        return mesh
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        pressedKeys.clear()
        keyDownListener?.let { window.removeEventListener("keydown", it) }
        keyUpListener?.let { window.removeEventListener("keyup", it) }
        blurListener?.let { window.removeEventListener("blur", it) }
        resizeListener?.let { window.removeEventListener("resize", it) }
        mouseDownListener?.let { canvas.removeEventListener("pointerdown", it) }
        mouseMoveListener?.let { window.removeEventListener("pointermove", it) }
        mouseUpListener?.let { window.removeEventListener("pointerup", it) }
        wheelListener?.let { canvas.removeEventListener("wheel", it) }

        cameraLimitsDisposer?.invoke()
        resizeDisposer?.invoke()
        val bjs = bjsModule
        if (bjs != null) {
            engine?.let { eng -> runCatching { bjs.stopEngine(eng) } }
            scene?.let { sc -> runCatching { bjs.disposeScene(sc) } }
            engine?.let { eng -> runCatching { bjs.disposeEngine(eng) } }
        }
        scene = null
        engine = null
        bjsModule = null
    }
}

private fun renderTargetLanguageLabelTexture(
    bjs: BabylonLiteModule,
    engine: EngineContext,
    rawText: String,
    accentColor: String,
): Pair<Texture2D, Double> {
    val label = rawText.trim().ifEmpty { "•" }
    val cnv = document.createElement("canvas") as HTMLCanvasElement
    val ctx = cnv.getContext("2d") as CanvasRenderingContext2D
    val fontSpec = "600 30px system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif"
    ctx.font = fontSpec
    val textWidth = ctx.measureText(label).width
    val canvasHeight = 64
    val padX = 28.0
    val canvasWidth = max(128, min(768, (textWidth + padX * 2).roundToInt()))
    cnv.width = canvasWidth
    cnv.height = canvasHeight

    ctx.clearRect(0.0, 0.0, canvasWidth.toDouble(), canvasHeight.toDouble())
    // Crisp pill capsule
    val r = 24.0
    val x0 = 4.0
    val y0 = 6.0
    val w = canvasWidth - 8.0
    val h = canvasHeight - 12.0
    ctx.beginPath()
    ctx.moveTo(x0 + r, y0)
    ctx.lineTo(x0 + w - r, y0)
    ctx.quadraticCurveTo(x0 + w, y0, x0 + w, y0 + r)
    ctx.lineTo(x0 + w, y0 + h - r)
    ctx.quadraticCurveTo(x0 + w, y0 + h, x0 + w - r, y0 + h)
    ctx.lineTo(x0 + r, y0 + h)
    ctx.quadraticCurveTo(x0, y0 + h, x0, y0 + h - r)
    ctx.lineTo(x0, y0 + r)
    ctx.quadraticCurveTo(x0, y0, x0 + r, y0)
    ctx.closePath()
    ctx.fillStyle = "rgba(15, 23, 42, 0.88)"
    ctx.fill()
    ctx.lineWidth = 3.0
    ctx.strokeStyle = accentColor
    ctx.stroke()

    ctx.font = fontSpec
    ctx.fillStyle = "#f8fafc"
    ctx.asDynamic().textAlign = "center"
    ctx.asDynamic().textBaseline = "middle"
    ctx.fillText(label, canvasWidth / 2.0, canvasHeight / 2.0 + 1.0, canvasWidth - 20.0)

    val opts = js("({ srgb: true, mipMaps: true })")
    val dynTex = bjs.createDynamicTexture(engine, canvasWidth, canvasHeight, opts)
    bjs.updateDynamicTexture(engine, dynTex, cnv)
    return dynTex to (canvasWidth.toDouble() / canvasHeight.toDouble())
}

private fun createSoftMoteOpacityTexture(
    bjs: BabylonLiteModule,
    engine: EngineContext,
): Texture2D {
    val size = 64
    val pixels = Uint8Array(size * size * 4)
    val half = size / 2.0
    for (py in 0 until size) {
        for (px in 0 until size) {
            val dx = (px + 0.5 - half) / half
            val dy = (py + 0.5 - half) / half
            val d = sqrt(dx * dx + dy * dy)
            val theta = atan2(dy, dx)
            val contour = 0.88 + 0.08 * sin(3.0 * theta) + 0.04 * cos(5.0 * theta)
            val t = (1.0 - d / contour).coerceIn(0.0, 1.0)
            val alpha = (t * t * 255.0).roundToInt().coerceIn(0, 255)
            val idx = (py * size + px) * 4
            pixels.asDynamic()[idx] = 255
            pixels.asDynamic()[idx + 1] = 255
            pixels.asDynamic()[idx + 2] = 255
            pixels.asDynamic()[idx + 3] = alpha
        }
    }
    val opts = js("({ srgb: false, mipMaps: true })")
    return bjs.createTexture2DFromPixels(engine, pixels, size, size, opts)
}

private suspend fun loadMaskedSpriteTexture(
    bjs: BabylonLiteModule,
    engine: EngineContext,
    spec: StudyComprehendSpriteSpec,
): LoadedSpriteAsset {
    val url = spec.imageUrl?.trim()?.takeIf { it.isNotEmpty() }
    if (url != null) {
        loadExternalImageWithBorderCutout(bjs, engine, url)?.let { return it }
    }
    return createProceduralBillboardFallback(bjs, engine, spec.isCharacter, spec.id)
}

private suspend fun loadPlayerSpriteTexture(
    bjs: BabylonLiteModule,
    engine: EngineContext,
    url: String?,
): LoadedSpriteAsset {
    val cleanUrl = url?.trim()?.takeIf { it.isNotEmpty() }
    if (cleanUrl != null) {
        loadExternalImageWithBorderCutout(bjs, engine, cleanUrl)?.let { return it }
    }
    return createProceduralBillboardFallback(bjs, engine, isCharacter = true, seedKey = "player-explorer")
}

/**
 * Loads a sprite image into a GPU Texture2D, preserving BinaryImageMask alpha when present,
 * applying border flood-fill background cutout if the source image had an opaque white backdrop,
 * and tight-cropping to the opaque silhouette so feet touch y = 0 and true pixel aspect ratio is preserved.
 */
private suspend fun loadExternalImageWithBorderCutout(
    bjs: BabylonLiteModule,
    engine: EngineContext,
    url: String,
): LoadedSpriteAsset? {
    val deferred = CompletableDeferred<HTMLImageElement?>()
    val img = document.createElement("img") as HTMLImageElement
    img.crossOrigin = "anonymous"
    img.onload = { _: Event -> deferred.complete(img) }
    img.onerror = { _, _, _, _, _ -> deferred.complete(null); null }
    img.src = url
    val loaded = deferred.await() ?: return null
    val w = loaded.naturalWidth.takeIf { it > 0 } ?: return null
    val h = loaded.naturalHeight.takeIf { it > 0 } ?: return null

    val downscale = min(1.0, 512.0 / max(w, h).toDouble())
    val targetW = max(1, (w * downscale).roundToInt())
    val targetH = max(1, (h * downscale).roundToInt())
    val cnv = document.createElement("canvas") as HTMLCanvasElement
    cnv.width = targetW
    cnv.height = targetH
    val ctx = cnv.getContext("2d") as CanvasRenderingContext2D
    ctx.clearRect(0.0, 0.0, targetW.toDouble(), targetH.toDouble())
    ctx.drawImage(loaded, 0.0, 0.0, targetW.toDouble(), targetH.toDouble())

    var uploadCanvas: HTMLCanvasElement = cnv
    var uploadW = targetW
    var uploadH = targetH

    runCatching {
        val imageData = ctx.getImageData(0.0, 0.0, targetW.toDouble(), targetH.toDouble())
        val data = imageData.data.asDynamic()
        var hasTransparency = false
        val totalPixels = targetW * targetH
        for (i in 0 until totalPixels) {
            if ((data[i * 4 + 3] as Int) < 245) {
                hasTransparency = true
                break
            }
        }
        if (!hasTransparency) {
            // Flood-fill near-white studio background from outer borders
            val visited = BooleanArray(totalPixels)
            val queue = IntArray(totalPixels)
            var head = 0
            var tail = 0
            fun isBackdrop(pxIdx: Int): Boolean {
                val r = data[pxIdx * 4] as Int
                val g = data[pxIdx * 4 + 1] as Int
                val b = data[pxIdx * 4 + 2] as Int
                return r >= 232 && g >= 232 && b >= 232
            }
            fun enqueueIfBackdrop(x: Int, y: Int) {
                val idx = y * targetW + x
                if (!visited[idx] && isBackdrop(idx)) {
                    visited[idx] = true
                    queue[tail++] = idx
                }
            }
            for (x in 0 until targetW) {
                enqueueIfBackdrop(x, 0)
                enqueueIfBackdrop(x, targetH - 1)
            }
            for (y in 0 until targetH) {
                enqueueIfBackdrop(0, y)
                enqueueIfBackdrop(targetW - 1, y)
            }
            while (head < tail) {
                val cur = queue[head++]
                data[cur * 4 + 3] = 0
                val cx = cur % targetW
                val cy = cur / targetW
                if (cx > 0) enqueueIfBackdrop(cx - 1, cy)
                if (cx + 1 < targetW) enqueueIfBackdrop(cx + 1, cy)
                if (cy > 0) enqueueIfBackdrop(cx, cy - 1)
                if (cy + 1 < targetH) enqueueIfBackdrop(cx, cy + 1)
            }
            ctx.putImageData(imageData, 0.0, 0.0)
        }

        // Tight-crop to non-transparent bounds so feet/base touch y = 0 and aspect ratio is exact
        var minX = targetW
        var minY = targetH
        var maxX = -1
        var maxY = -1
        for (y in 0 until targetH) {
            for (x in 0 until targetW) {
                val a = data[(y * targetW + x) * 4 + 3] as Int
                if (a >= 24) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        if (maxX >= minX && maxY >= minY) {
            val x0 = max(0, minX - 2)
            val x1 = min(targetW - 1, maxX + 2)
            val y0 = max(0, minY - 2)
            val y1 = min(targetH - 1, maxY + 1)
            val cropW = x1 - x0 + 1
            val cropH = y1 - y0 + 1
            if (cropW >= 8 && cropH >= 8 && (cropW < targetW || cropH < targetH)) {
                val cropped = document.createElement("canvas") as HTMLCanvasElement
                cropped.width = cropW
                cropped.height = cropH
                val cropCtx = cropped.getContext("2d") as CanvasRenderingContext2D
                cropCtx.clearRect(0.0, 0.0, cropW.toDouble(), cropH.toDouble())
                cropCtx.drawImage(
                    cnv,
                    x0.toDouble(),
                    y0.toDouble(),
                    cropW.toDouble(),
                    cropH.toDouble(),
                    0.0,
                    0.0,
                    cropW.toDouble(),
                    cropH.toDouble(),
                )
                uploadCanvas = cropped
                uploadW = cropW
                uploadH = cropH
            }
        }
    }

    val opts = js("({ srgb: true, mipMaps: true })")
    val dynTex = bjs.createDynamicTexture(engine, uploadW, uploadH, opts)
    bjs.updateDynamicTexture(engine, dynTex, uploadCanvas)
    return LoadedSpriteAsset(dynTex, uploadW.toDouble() / uploadH.toDouble())
}

private fun createFallbackPropTexture(
    bjs: BabylonLiteModule,
    engine: EngineContext,
    obj: StudyComprehendObject,
): LoadedSpriteAsset = createProceduralBillboardFallback(bjs, engine, obj.isCharacter, obj.id)

private fun createProceduralBillboardFallback(
    bjs: BabylonLiteModule,
    engine: EngineContext,
    isCharacter: Boolean,
    seedKey: String,
): LoadedSpriteAsset {
    val cnv = document.createElement("canvas") as HTMLCanvasElement
    val texW = if (isCharacter) 160 else 256
    val texH = 256
    cnv.width = texW
    cnv.height = texH
    val ctx = cnv.getContext("2d") as CanvasRenderingContext2D
    ctx.clearRect(0.0, 0.0, texW.toDouble(), texH.toDouble())
    val hue = abs(seedKey.hashCode()) % 360
    if (isCharacter) {
        // Stylized silhouette figure with natural proportions and feet anchored at the bottom edge
        ctx.fillStyle = "hsl($hue, 55%, 48%)"
        ctx.strokeStyle = "#0f172a"
        ctx.lineWidth = 6.0
        ctx.beginPath()
        ctx.arc(80.0, 52.0, 30.0, 0.0, 2.0 * PI)
        ctx.fill()
        ctx.stroke()
        ctx.beginPath()
        ctx.moveTo(40.0, 252.0)
        ctx.quadraticCurveTo(38.0, 92.0, 80.0, 92.0)
        ctx.quadraticCurveTo(122.0, 92.0, 120.0, 252.0)
        ctx.closePath()
        ctx.fill()
        ctx.stroke()
    } else {
        ctx.fillStyle = "hsl($hue, 45%, 44%)"
        ctx.strokeStyle = "#0f172a"
        ctx.lineWidth = 6.0
        ctx.beginPath()
        ctx.arc(128.0, 168.0, 82.0, 0.0, 2.0 * PI)
        ctx.fill()
        ctx.stroke()
    }
    val opts = js("({ srgb: true, mipMaps: true })")
    val tex = bjs.createDynamicTexture(engine, texW, texH, opts)
    bjs.updateDynamicTexture(engine, tex, cnv)
    return LoadedSpriteAsset(tex, texW.toDouble() / texH.toDouble())
}

private fun createSkyGradientTexture(
    bjs: BabylonLiteModule,
    engine: EngineContext,
    top: Triple<Double, Double, Double>,
    horizon: Triple<Double, Double, Double>,
    showStars: Boolean,
): Texture2D {
    val w = 256
    val h = 256
    val pixels = Uint8Array(w * h * 4)
    for (y in 0 until h) {
        val v = y.toDouble() / (h - 1).toDouble()
        // Sphere UV top is near y=0, equator horizon near y=0.52
        val t = (v / 0.54).coerceIn(0.0, 1.0)
        val smooth = t * t * (3.0 - 2.0 * t)
        val r = lerp(top.first, horizon.first, smooth)
        val g = lerp(top.second, horizon.second, smooth)
        val b = lerp(top.third, horizon.third, smooth)
        for (x in 0 until w) {
            var sr = r
            var sg = g
            var sb = b
            if (showStars && v < 0.44) {
                val starHash = tileHash(x * 7 + y * 13, y * 31 + x * 3) and 0x3ff
                if (starHash == 1) {
                    sr = min(1.0, sr + 0.65)
                    sg = min(1.0, sg + 0.68)
                    sb = min(1.0, sb + 0.75)
                }
            }
            val idx = (y * w + x) * 4
            pixels.asDynamic()[idx] = (sr * 255.0).roundToInt().coerceIn(0, 255)
            pixels.asDynamic()[idx + 1] = (sg * 255.0).roundToInt().coerceIn(0, 255)
            pixels.asDynamic()[idx + 2] = (sb * 255.0).roundToInt().coerceIn(0, 255)
            pixels.asDynamic()[idx + 3] = 255
        }
    }
    val opts = js("({ srgb: true, mipMaps: false })")
    return bjs.createTexture2DFromPixels(engine, pixels, w, h, opts)
}

/**
 * Generates a realistic seamless 128x128 diffuse + tangent-space normal map pair
 * for each of the 14 ground tile types.
 */
private fun createProceduralGroundTextures(
    bjs: BabylonLiteModule,
    engine: EngineContext,
    type: StudyComprehendGroundType,
): Pair<Texture2D, Texture2D> {
    val size = 128
    val diffuse = Uint8Array(size * size * 4)
    val bump = Uint8Array(size * size * 4)
    val heights = DoubleArray(size * size)
    val seed = type.ordinal * 977 + 131

    for (y in 0 until size) {
        for (x in 0 until size) {
            val u = x.toDouble() / size
            val v = y.toDouble() / size
            val fbm = seamlessFbm(x, y, size, seed)
            val detail = seamlessNoise(x, y, 4, size, seed + 53)

            var r: Double
            var g: Double
            var b: Double
            var h: Double

            when (type) {
                StudyComprehendGroundType.GRASS -> {
                    val blade = seamlessNoise(x * 2, y, 2, size * 2, seed + 91)
                    h = fbm * 0.6 + blade * 0.4
                    r = 0.24 + fbm * 0.14 + detail * 0.06
                    g = 0.46 + fbm * 0.20 + blade * 0.10
                    b = 0.19 + fbm * 0.09
                }
                StudyComprehendGroundType.STONE_PAVING -> {
                    val row = (v * 4.0).toInt()
                    val offsetU = if (row % 2 == 0) u else (u + 0.5) % 1.0
                    val bx = (offsetU * 3.0) % 1.0
                    val by = (v * 4.0) % 1.0
                    val mortar = bx < 0.055 || bx > 0.945 || by < 0.065 || by > 0.935
                    val blockId = tileHash((offsetU * 3.0).toInt() + row * 11, row)
                    val blockTone = ((blockId and 0xff) / 255.0) * 0.10 - 0.05
                    if (mortar) {
                        h = 0.15
                        r = 0.36; g = 0.35; b = 0.33
                    } else {
                        h = 0.72 + fbm * 0.22
                        val base = 0.56 + blockTone + fbm * 0.10
                        r = base; g = base * 0.98; b = base * 0.94
                    }
                }
                StudyComprehendGroundType.COBBLESTONE -> {
                    val vor = seamlessVoronoi(u * 6.0, v * 6.0, 6, seed)
                    val edge = vor.first
                    val cellTone = vor.second * 0.12 - 0.06
                    if (edge < 0.11) {
                        h = 0.18
                        r = 0.34; g = 0.32; b = 0.29
                    } else {
                        h = min(1.0, 0.45 + edge * 0.8 + detail * 0.15)
                        val base = 0.48 + cellTone + fbm * 0.10
                        r = base * 1.02; g = base * 0.99; b = base * 0.95
                    }
                }
                StudyComprehendGroundType.DIRT_PATH -> {
                    h = fbm * 0.65 + detail * 0.35
                    r = 0.48 + fbm * 0.14
                    g = 0.36 + fbm * 0.12
                    b = 0.24 + fbm * 0.09
                }
                StudyComprehendGroundType.SAND -> {
                    val ripple = sin((u * 3.0 + v * 7.0 + fbm * 0.6) * 2.0 * PI) * 0.5 + 0.5
                    h = ripple * 0.55 + detail * 0.25
                    r = 0.82 + ripple * 0.08 + detail * 0.04
                    g = 0.73 + ripple * 0.07 + detail * 0.04
                    b = 0.52 + ripple * 0.05
                }
                StudyComprehendGroundType.WOOD_FLOOR -> {
                    val plankIdx = (v * 5.0).toInt()
                    val pv = (v * 5.0) % 1.0
                    val seamU = ((u + plankIdx * 0.37) % 1.0)
                    val seam = pv < 0.05 || pv > 0.95 || seamU < 0.018
                    val grain = sin((v * 45.0 + fbm * 4.5) * PI) * 0.5 + 0.5
                    if (seam) {
                        h = 0.20
                        r = 0.24; g = 0.15; b = 0.09
                    } else {
                        h = 0.75 + grain * 0.15
                        r = 0.52 + grain * 0.12 + (plankIdx % 3) * 0.025
                        g = 0.34 + grain * 0.09 + (plankIdx % 3) * 0.018
                        b = 0.19 + grain * 0.05
                    }
                }
                StudyComprehendGroundType.MARBLE_TILE -> {
                    val gx = (u * 2.0) % 1.0
                    val gy = (v * 2.0) % 1.0
                    val grout = gx < 0.03 || gx > 0.97 || gy < 0.03 || gy > 0.97
                    val vein = abs(sin((u * 2.0 + v * 2.0 + fbm * 2.2) * PI))
                    if (grout) {
                        h = 0.35
                        r = 0.62; g = 0.60; b = 0.58
                    } else {
                        h = 0.90 + detail * 0.05
                        val vShade = 0.78 + vein * 0.16
                        r = vShade * 1.02; g = vShade * 1.0; b = vShade * 0.97
                    }
                }
                StudyComprehendGroundType.TATAMI_MAT -> {
                    val border = u < 0.06 || u > 0.94
                    val weave = sin(v * 64.0 * PI) * 0.5 + 0.5
                    if (border) {
                        h = 0.65
                        r = 0.16; g = 0.24; b = 0.18
                    } else {
                        h = 0.55 + weave * 0.25
                        r = 0.68 + weave * 0.07 + fbm * 0.04
                        g = 0.66 + weave * 0.07 + fbm * 0.04
                        b = 0.42 + weave * 0.05
                    }
                }
                StudyComprehendGroundType.CARPET -> {
                    val border = u < 0.08 || u > 0.92 || v < 0.08 || v > 0.92
                    val motif = abs(sin(u * 8.0 * PI) * cos(v * 8.0 * PI))
                    h = 0.50 + detail * 0.35
                    if (border) {
                        r = 0.74 + detail * 0.08; g = 0.56 + detail * 0.06; b = 0.22
                    } else {
                        r = 0.56 + motif * 0.16 + detail * 0.05
                        g = 0.15 + motif * 0.12
                        b = 0.18 + motif * 0.06
                    }
                }
                StudyComprehendGroundType.WATER -> {
                    val wave1 = sin((u * 3.0 + v * 2.0 + fbm * 1.4) * 2.0 * PI)
                    val wave2 = cos((u * 2.0 - v * 4.0 + detail * 1.1) * 2.0 * PI)
                    val caustic = ((wave1 + wave2) * 0.25 + 0.5).coerceIn(0.0, 1.0)
                    h = caustic
                    r = 0.12 + caustic * 0.22
                    g = 0.42 + caustic * 0.26
                    b = 0.62 + caustic * 0.26
                }
                StudyComprehendGroundType.MOSS_STONE -> {
                    val vor = seamlessVoronoi(u * 4.0, v * 4.0, 4, seed)
                    val moss = vor.first < 0.22 || fbm > 0.64
                    if (moss) {
                        h = 0.42 + detail * 0.25
                        r = 0.22 + fbm * 0.10; g = 0.44 + fbm * 0.14; b = 0.18 + fbm * 0.06
                    } else {
                        h = 0.74 + fbm * 0.18
                        val s = 0.48 + fbm * 0.12
                        r = s; g = s * 1.01; b = s * 0.97
                    }
                }
                StudyComprehendGroundType.GRAVEL -> {
                    val pebble = seamlessNoise(x, y, 3, size, seed + 17)
                    h = pebble * 0.75 + fbm * 0.25
                    val base = 0.54 + pebble * 0.18 + (fbm - 0.5) * 0.10
                    r = base * 1.01; g = base * 0.99; b = base * 0.95
                }
                StudyComprehendGroundType.COURTYARD_BRICK -> {
                    val row = (v * 6.0).toInt()
                    val bu = ((if (row % 2 == 0) u else u + 0.5) * 3.0) % 1.0
                    val bv = (v * 6.0) % 1.0
                    val mortar = bu < 0.07 || bv < 0.09
                    if (mortar) {
                        h = 0.22
                        r = 0.66; g = 0.62; b = 0.56
                    } else {
                        h = 0.76 + detail * 0.16
                        r = 0.64 + fbm * 0.12
                        g = 0.34 + fbm * 0.08
                        b = 0.25 + fbm * 0.06
                    }
                }
                StudyComprehendGroundType.SNOW -> {
                    h = fbm * 0.55 + detail * 0.25
                    r = 0.88 + fbm * 0.10
                    g = 0.92 + fbm * 0.07
                    b = 0.98
                }
                StudyComprehendGroundType.TERRACOTTA_TILE -> {
                    val tx = (u * 3.0) % 1.0
                    val ty = (v * 3.0) % 1.0
                    val grout = tx < 0.055 || tx > 0.945 || ty < 0.055 || ty > 0.945
                    val cellTone = ((tileHash((u * 3.0).toInt(), (v * 3.0).toInt(), seed) and 0xff) / 255.0) * 0.09 - 0.045
                    if (grout) {
                        h = 0.24
                        r = 0.52; g = 0.45; b = 0.39
                    } else {
                        h = 0.78 + detail * 0.14
                        r = 0.72 + cellTone + fbm * 0.10
                        g = 0.41 + cellTone * 0.7 + fbm * 0.07
                        b = 0.27 + cellTone * 0.4 + fbm * 0.05
                    }
                }
                StudyComprehendGroundType.DARK_SLATE -> {
                    val row = (v * 3.0).toInt()
                    val su = ((if (row % 2 == 0) u else u + 0.5) * 2.0) % 1.0
                    val sv = (v * 3.0) % 1.0
                    val seam = su < 0.04 || sv < 0.05
                    if (seam) {
                        h = 0.20
                        r = 0.16; g = 0.17; b = 0.19
                    } else {
                        h = 0.72 + fbm * 0.18 + detail * 0.08
                        val base = 0.25 + fbm * 0.10 + detail * 0.04
                        r = base * 0.96; g = base; b = base * 1.06
                    }
                }
                StudyComprehendGroundType.STONE_WALL -> {
                    val row = (v * 4.0).toInt()
                    val su = ((if (row % 2 == 0) u else u + 0.5) * 2.5) % 1.0
                    val sv = (v * 4.0) % 1.0
                    val mortar = su < 0.07 || sv < 0.08 || sv > 0.92
                    val stoneTone = ((tileHash((su * 3.0).toInt() + row * 7, row, seed) and 0xff) / 255.0) * 0.12 - 0.06
                    if (mortar) {
                        h = 0.12
                        r = 0.34; g = 0.33; b = 0.31
                    } else {
                        h = 0.80 + fbm * 0.18
                        val base = 0.54 + stoneTone + detail * 0.08
                        r = base; g = base * 0.99; b = base * 0.95
                    }
                }
                StudyComprehendGroundType.WOOD_WALL -> {
                    val panel = (u * 4.0) % 1.0
                    val frame = panel < 0.09 || panel > 0.91 || v < 0.08 || v > 0.92
                    val grain = sin((u * 38.0 + fbm * 3.8) * PI) * 0.5 + 0.5
                    if (frame) {
                        h = 0.88
                        r = 0.34 + grain * 0.06
                        g = 0.21 + grain * 0.04
                        b = 0.12 + grain * 0.03
                    } else {
                        h = 0.62 + grain * 0.14
                        r = 0.56 + grain * 0.10 + fbm * 0.05
                        g = 0.39 + grain * 0.08 + fbm * 0.04
                        b = 0.23 + grain * 0.05
                    }
                }
                StudyComprehendGroundType.PLASTER_WALL -> {
                    val wainscot = v > 0.76
                    if (wainscot) {
                        val plank = (u * 8.0) % 1.0
                        val seam = plank < 0.06 || v < 0.785
                        h = if (seam) 0.35 else 0.74 + detail * 0.10
                        r = if (seam) 0.34 else 0.48 + fbm * 0.07
                        g = if (seam) 0.23 else 0.34 + fbm * 0.06
                        b = if (seam) 0.15 else 0.23 + fbm * 0.04
                    } else {
                        h = 0.66 + fbm * 0.18 + detail * 0.10
                        r = 0.86 + fbm * 0.06 - detail * 0.03
                        g = 0.82 + fbm * 0.06 - detail * 0.03
                        b = 0.74 + fbm * 0.05 - detail * 0.03
                    }
                }
                StudyComprehendGroundType.BRICK_WALL -> {
                    val row = (v * 6.0).toInt()
                    val bu = ((if (row % 2 == 0) u else u + 0.5) * 3.0) % 1.0
                    val bv = (v * 6.0) % 1.0
                    val mortar = bu < 0.08 || bv < 0.10
                    val brickVar = ((tileHash((bu * 3.0).toInt() + row * 13, row, seed) and 0xff) / 255.0) * 0.10 - 0.05
                    if (mortar) {
                        h = 0.16
                        r = 0.68; g = 0.64; b = 0.58
                    } else {
                        h = 0.82 + detail * 0.14
                        r = 0.62 + brickVar + fbm * 0.10
                        g = 0.31 + brickVar * 0.6 + fbm * 0.06
                        b = 0.22 + brickVar * 0.4 + fbm * 0.05
                    }
                }
            }

            heights[y * size + x] = h
            val idx = (y * size + x) * 4
            diffuse.asDynamic()[idx] = (r * 255.0).roundToInt().coerceIn(0, 255)
            diffuse.asDynamic()[idx + 1] = (g * 255.0).roundToInt().coerceIn(0, 255)
            diffuse.asDynamic()[idx + 2] = (b * 255.0).roundToInt().coerceIn(0, 255)
            diffuse.asDynamic()[idx + 3] = 255
        }
    }

    // Compute seamless tangent-space normal map from heightfield
    val strength = 2.4
    for (y in 0 until size) {
        val ym = (y - 1 + size) % size
        val yp = (y + 1) % size
        for (x in 0 until size) {
            val xm = (x - 1 + size) % size
            val xp = (x + 1) % size
            val dx = (heights[y * size + xp] - heights[y * size + xm]) * strength
            val dy = (heights[yp * size + x] - heights[ym * size + x]) * strength
            val nx = -dx
            val ny = -dy
            val nz = 1.0
            val len = sqrt(nx * nx + ny * ny + nz * nz)
            val idx = (y * size + x) * 4
            bump.asDynamic()[idx] = (((nx / len) * 0.5 + 0.5) * 255.0).roundToInt().coerceIn(0, 255)
            bump.asDynamic()[idx + 1] = (((ny / len) * 0.5 + 0.5) * 255.0).roundToInt().coerceIn(0, 255)
            bump.asDynamic()[idx + 2] = (((nz / len) * 0.5 + 0.5) * 255.0).roundToInt().coerceIn(0, 255)
            bump.asDynamic()[idx + 3] = 255
        }
    }

    val diffOpts = js("({ srgb: true, mipMaps: true, addressModeU: 'repeat', addressModeV: 'repeat' })")
    val bumpOpts = js("({ srgb: false, mipMaps: true, addressModeU: 'repeat', addressModeV: 'repeat' })")
    return bjs.createTexture2DFromPixels(engine, diffuse, size, size, diffOpts) to
        bjs.createTexture2DFromPixels(engine, bump, size, size, bumpOpts)
}

private fun tileHash(x: Int, y: Int, seed: Int = 0): Int {
    var h = x * 374761393 + y * 668265263 + seed * 1442695041
    h = (h xor (h ushr 13)) * 1274126177
    return h xor (h ushr 16)
}

private fun seamlessNoise(x: Int, y: Int, cellSize: Int, periodPx: Int, seed: Int): Double {
    val cells = max(1, periodPx / cellSize)
    val gx = (x.toDouble() / cellSize)
    val gy = (y.toDouble() / cellSize)
    val x0 = floor(gx).toInt()
    val y0 = floor(gy).toInt()
    val tx = gx - x0
    val ty = gy - y0
    val sx = tx * tx * (3.0 - 2.0 * tx)
    val sy = ty * ty * (3.0 - 2.0 * ty)

    fun sample(cx: Int, cy: Int): Double {
        val wx = ((cx % cells) + cells) % cells
        val wy = ((cy % cells) + cells) % cells
        return (tileHash(wx, wy, seed) and 0xffff) / 65535.0
    }

    val n00 = sample(x0, y0)
    val n10 = sample(x0 + 1, y0)
    val n01 = sample(x0, y0 + 1)
    val n11 = sample(x0 + 1, y0 + 1)
    return lerp(lerp(n00, n10, sx), lerp(n01, n11, sx), sy)
}

private fun seamlessFbm(x: Int, y: Int, size: Int, seed: Int): Double {
    val o1 = seamlessNoise(x, y, 32, size, seed) * 0.52
    val o2 = seamlessNoise(x, y, 16, size, seed + 19) * 0.30
    val o3 = seamlessNoise(x, y, 8, size, seed + 43) * 0.18
    return (o1 + o2 + o3).coerceIn(0.0, 1.0)
}

private fun seamlessVoronoi(gx: Double, gy: Double, period: Int, seed: Int): Pair<Double, Double> {
    val ix = floor(gx).toInt()
    val iy = floor(gy).toInt()
    var d1 = 10.0
    var d2 = 10.0
    var bestTone = 0.5
    for (oy in -1..1) {
        for (ox in -1..1) {
            val cx = ix + ox
            val cy = iy + oy
            val wx = ((cx % period) + period) % period
            val wy = ((cy % period) + period) % period
            val h = tileHash(wx, wy, seed)
            val jx = cx + ((h and 0xff) / 255.0) * 0.78 + 0.11
            val jy = cy + (((h shr 8) and 0xff) / 255.0) * 0.78 + 0.11
            val dist = hypot2(gx - jx, gy - jy)
            if (dist < d1) {
                d2 = d1
                d1 = dist
                bestTone = ((h shr 16) and 0xff) / 255.0
            } else if (dist < d2) {
                d2 = dist
            }
        }
    }
    return (d2 - d1).coerceIn(0.0, 1.0) to bestTone
}

private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t
private fun hypot2(dx: Double, dy: Double): Double = sqrt(dx * dx + dy * dy)
