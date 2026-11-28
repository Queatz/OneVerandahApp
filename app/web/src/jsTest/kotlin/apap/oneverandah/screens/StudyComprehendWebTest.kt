package apap.oneverandah.screens

import apap.oneverandah.Styles
import apap.oneverandah.api.StudyComprehendSpriteSpec
import apap.oneverandah.babylon.loadBabylonLite
import apap.oneverandah.i18n.stringsFor
import apap.oneverandah.model.Language
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.promise
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StudyComprehendWebTest {
    @Test
    fun `detectWebGpuBrowserKind classifies major browsers and next scene label formats cleanly`() {
        assertEquals(
            WebGpuBrowserKind.CHROME_FAMILY,
            detectWebGpuBrowserKind("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36"),
        )
        assertEquals(
            WebGpuBrowserKind.FIREFOX,
            detectWebGpuBrowserKind("Mozilla/5.0 (X11; Linux x86_64; rv:133.0) Gecko/20100101 Firefox/133.0"),
        )
        assertEquals(
            WebGpuBrowserKind.SAFARI,
            detectWebGpuBrowserKind("Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) Version/17.5 Safari/605.1.15"),
        )

        val enStrings = stringsFor(Language.ENGLISH).chatComprehend
        assertEquals(
            "Next scene: Going inside the palace",
            enStrings.nextSceneButton("Going inside the palace"),
        )
        assertContains(enStrings.webGpuChromeSteps, "chrome://flags")
        assertEquals(10_000L, STUDY_COMPREHEND_POLL_INTERVAL_MS)
        assertEquals(0.15, STUDY_COMPREHEND_BGM_VOLUME, 1e-6)
        assertEquals("Generating music for scene 3…", comprehendInFlightStepLabel(enStrings, "MUSIC:3"))
        assertEquals("Generating image: lantern…", comprehendInFlightStepLabel(enStrings, "SPRITE:lantern"))
        val bgmAudio = createComprehendSceneMusicAudio("https://cdn.example/comprehend-scene-1.mp3")
        assertTrue(bgmAudio.loop)
        assertEquals(0.15, bgmAudio.volume, 1e-6)
        assertEquals("auto", bgmAudio.preload)
        assertContains(bgmAudio.src, "comprehend-scene-1.mp3")
        Styles.install()
        val css = document.getElementById("app-styles")?.textContent.orEmpty()
        assertContains(css, ".${Styles.studyComprehendSpinner}")
        assertContains(css, "animation: spin 8s linear infinite;")
        assertContains(css, ".${Styles.studyComprehendInteractionStack}")
    }

    @Test
    fun `computeBillboardDimensions preserves exact sprite aspect ratio and wrapWorldParticleCoord stays in world bounds`() {
        for (aspect in listOf(0.42, 0.625, 1.0, 1.65)) {
            val (charW, charH) = computeBillboardDimensions(
                aspectRatio = aspect,
                isCharacter = true,
                pickable = false,
                spec = StudyComprehendSpriteSpec(
                    id = "char-test",
                    isCharacter = true,
                    visualPrompt = "Character",
                    scaleWidth = 0.85f,
                    scaleHeight = 1.95f,
                ),
            )
            assertTrue(abs(charW / charH - aspect) < 1e-6, "Character aspect ratio must match sprite ($aspect)")

            val (propW, propH) = computeBillboardDimensions(
                aspectRatio = aspect,
                isCharacter = false,
                pickable = true,
                spec = StudyComprehendSpriteSpec(
                    id = "prop-test",
                    isCharacter = false,
                    visualPrompt = "Prop",
                    scaleWidth = 0.9f,
                    scaleHeight = 1.4f,
                ),
            )
            assertTrue(abs(propW / propH - aspect) < 1e-6, "Prop aspect ratio must match sprite ($aspect)")
        }

        assertEquals(0.0, wrapWorldParticleCoord(0.0, 17.0), 1e-6)
        assertEquals(-16.5, wrapWorldParticleCoord(17.5, 17.0), 1e-6)
        assertEquals(16.25, wrapWorldParticleCoord(-17.75, 17.0), 1e-6)
    }

    @Test
    fun `tile and world coordinate conversion and vertical billboard yaw are consistent`() {
        for (tile in 0 until 32) {
            val wx = comprehendTileToWorldX(tile)
            val wz = comprehendTileToWorldZ(tile)
            assertEquals(tile, comprehendWorldToTileX(wx))
            assertEquals(tile, comprehendWorldToTileZ(wz))
        }

        // Camera directly south (-Z) of object at origin -> front normal (-Z) faces camera with yaw = 0
        val yawSouth = billboardYawTowardCamera(objX = 0.0, objZ = 0.0, camX = 0.0, camZ = -10.0)
        assertTrue(abs(yawSouth) < 1e-6)

        // Camera directly east (+X) of object at origin -> yaw = -PI/2
        val yawEast = billboardYawTowardCamera(objX = 0.0, objZ = 0.0, camX = 10.0, camZ = 0.0)
        assertTrue(abs(yawEast - (-PI / 2.0)) < 1e-6)
    }

    @Test
    fun `custom per scene camera framing indoor wall materials and outdoor horizon skirts adapt to scene layout`() {
        fun makeScene(
            spawnX: Int,
            spawnZ: Int,
            charX: Int,
            charZ: Int,
            env: apap.oneverandah.api.StudyComprehendEnvironment = apap.oneverandah.api.StudyComprehendEnvironment.OUTDOOR,
            borderGround: apap.oneverandah.api.StudyComprehendGroundType = apap.oneverandah.api.StudyComprehendGroundType.WATER,
            floorGround: apap.oneverandah.api.StudyComprehendGroundType = apap.oneverandah.api.StudyComprehendGroundType.TATAMI_MAT,
            wallMaterial: apap.oneverandah.api.StudyComprehendGroundType? = null,
            surroundingGround: apap.oneverandah.api.StudyComprehendGroundType? = null,
        ): apap.oneverandah.api.StudyComprehendScene {
            val tiles = List(apap.oneverandah.api.STUDY_COMPREHEND_TILE_COUNT) { idx ->
                val x = idx % apap.oneverandah.api.STUDY_COMPREHEND_GRID_SIZE
                val z = idx / apap.oneverandah.api.STUDY_COMPREHEND_GRID_SIZE
                val onBorder = x == 0 || z == 0 || x == 31 || z == 31
                apap.oneverandah.api.StudyComprehendTile(
                    ground = if (onBorder && env == apap.oneverandah.api.StudyComprehendEnvironment.OUTDOOR) borderGround else floorGround,
                )
            }
            return apap.oneverandah.api.StudyComprehendScene(
                id = "custom-scene",
                title = "Harbor Tea House",
                description = "Custom dockside pavilion",
                environment = env,
                timeOfDay = apap.oneverandah.api.StudyComprehendTimeOfDay.MORNING,
                weather = apap.oneverandah.api.StudyComprehendWeather.BREEZY,
                playerSpawnX = spawnX,
                playerSpawnZ = spawnZ,
                tiles = tiles,
                objects = listOf(
                    apap.oneverandah.api.StudyComprehendObject(
                        id = "host",
                        spriteId = "host-sprite",
                        x = charX,
                        z = charZ,
                        targetName = "Chủ quán",
                        isCharacter = true,
                        spokenText = "Xin mời vào.",
                    ),
                ),
                tasks = emptyList(),
                wallMaterial = wallMaterial,
                surroundingGround = surroundingGround,
                ambientTintHex = "#f4a261",
            )
        }

        // West spawn (3, 16) looking East toward character at (24, 16): forward vector (-cos(alpha), -sin(alpha)) points +X!
        val westScene = makeScene(spawnX = 3, spawnZ = 16, charX = 24, charZ = 16)
        val westAlpha = computeInitialCameraAlpha(westScene)
        assertTrue(-kotlin.math.cos(westAlpha) > 0.9, "West-entry camera must look East into the scene")

        // East spawn (28, 16) looking West toward character at (8, 16): forward vector points -X!
        val eastScene = makeScene(spawnX = 28, spawnZ = 16, charX = 8, charZ = 16)
        val eastAlpha = computeInitialCameraAlpha(eastScene)
        assertTrue(-kotlin.math.cos(eastAlpha) < -0.9, "East-entry camera must look West into the scene")

        // Outdoor island/waterfront border automatically uses WATER horizon skirt instead of generic GRASS
        assertEquals(
            apap.oneverandah.api.StudyComprehendGroundType.WATER,
            resolveOutdoorSurroundingGround(westScene),
        )

        // Indoor tatami scene automatically selects WOOD_WALL instead of generic COURTYARD_BRICK
        val tatamiIndoor = makeScene(
            spawnX = 6,
            spawnZ = 6,
            charX = 22,
            charZ = 20,
            env = apap.oneverandah.api.StudyComprehendEnvironment.INDOOR,
            floorGround = apap.oneverandah.api.StudyComprehendGroundType.TATAMI_MAT,
        )
        assertEquals(
            apap.oneverandah.api.StudyComprehendGroundType.WOOD_WALL,
            resolveIndoorWallGround(tatamiIndoor),
        )
        val rgb = assertNotNull(parseHexColorRgbOrNull("#f4a261"))
        assertTrue(rgb.first > rgb.second && rgb.second > rgb.third)
    }

    @Test
    fun `babylon lite loads and steps headless scene camera bottom pivot billboard and picking ray`() = MainScope().promise {
        val bjs = loadBabylonLite()
        val engine = bjs.createNullEngine()
        assertNotNull(engine)
        // NullEngine omits WebGPU _device by default; attach a minimal mapped-buffer stub for mesh geometry tests
        engine.asDynamic()._device = js(
            "({ createBuffer: function(desc) { var buf = new ArrayBuffer(desc.size); return { size: desc.size, getMappedRange: function() { return buf; }, unmap: function() {}, destroy: function() {} }; } })",
        )
        val scene = bjs.createSceneContext(engine, js("({ defaultRenderTask: false })"))
        val targetX = comprehendTileToWorldX(16)
        val targetZ = comprehendTileToWorldZ(16)
        val cam = bjs.createArcRotateCamera(-PI / 2.0, 0.96, 12.5, bjs.vec3(targetX, 0.0, targetZ))
        scene.camera = cam

        // Verify bottom-pivoted billboard spans y in [0, height] so swaying around local (0, 0, 0) pivots from base
        val treeMesh = createBottomPivotBillboardPlane(bjs, engine, "tree-test", width = 1.4, height = 2.2)
        val rollZ = 0.22
        treeMesh.position.set(targetX, 0.0, targetZ)
        treeMesh.rotation.set(0.0, 0.0, rollZ)
        val boundMinY = (treeMesh.asDynamic().boundMin[1] as Number).toDouble()
        val boundMaxY = (treeMesh.asDynamic().boundMax[1] as Number).toDouble()
        assertEquals(0.0, boundMinY, 1e-5)
        assertEquals(2.2, boundMaxY, 1e-5)

        // Verify via Babylon Lite's own worldMatrix that bottom-center (0, 0, 0) stays pinned at (targetX, 0, targetZ)
        // while top-center (0, 2.2, 0) sways horizontally!
        val wm = treeMesh.asDynamic().worldMatrix
        val baseWorldX = (wm[12] as Number).toDouble()
        val baseWorldY = (wm[13] as Number).toDouble()
        val baseWorldZ = (wm[14] as Number).toDouble()
        assertEquals(targetX, baseWorldX, 1e-5)
        assertEquals(0.0, baseWorldY, 1e-5)
        assertEquals(targetZ, baseWorldZ, 1e-5)
        val topWorldX = (wm[4] as Number).toDouble() * 2.2 + baseWorldX
        assertTrue(abs(topWorldX - targetX) > 0.2, "Tree canopy should sway horizontally while base stays fixed")

        var steppedDelta = 0.0
        bjs.onBeforeRender(scene) { dt ->
            steppedDelta += dt
        }
        bjs.stepScene(engine, scene, 16.0)
        assertEquals(16.0, steppedDelta)

        // Verify picking ray aimed slightly above ground hits the swaying tree, and center ray hits ground tile (16, 16)
        val vp = bjs.getViewProjectionMatrix(cam, 1.0)
        val treeRay = bjs.createPickingRay(200.0, 175.0, vp, 400.0, 400.0)
        assertNotNull(treeRay)
        val pick = bjs.pickMeshesWithRay(arrayOf(treeMesh), treeRay)
        assertTrue(pick.hit)

        val ray = bjs.createPickingRay(200.0, 200.0, vp, 400.0, 400.0)
        assertNotNull(ray)
        val t = -ray.origin[1] / ray.direction[1]
        val hitX = ray.origin[0] + t * ray.direction[0]
        val hitZ = ray.origin[2] + t * ray.direction[2]
        assertEquals(16, comprehendWorldToTileX(hitX))
        assertEquals(16, comprehendWorldToTileZ(hitZ))

        bjs.disposeScene(scene)
    }
}
