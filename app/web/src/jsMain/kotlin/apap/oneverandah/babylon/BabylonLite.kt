package apap.oneverandah.babylon

import kotlinx.coroutines.await
import kotlin.js.Promise

external interface EngineContext
external interface SceneContext {
    var clearColor: dynamic
    var camera: dynamic
    var fog: dynamic
    val imageProcessing: ImageProcessingConfig
    var environmentPrimaryColor: Array<Double>?
}

external interface ImageProcessingConfig {
    var exposure: Double
    var contrast: Double
    var toneMappingEnabled: Boolean
}

external interface Vec3 {
    var x: Double
    var y: Double
    var z: Double
}

external interface ObservableVec3 : Vec3 {
    fun set(x: Double, y: Double, z: Double)
}

external interface EulerProxy {
    var x: Double
    var y: Double
    var z: Double
    fun set(x: Double, y: Double, z: Double)
}

external interface Mat4

external interface ArcRotateCamera {
    var alpha: Double
    var beta: Double
    var radius: Double
    var target: Vec3
    var fov: Double
    var nearPlane: Double
    var farPlane: Double
    var inertia: Double
    var wheelPrecision: Double
    var angularSensibility: Double
    var panningSensibility: Double
}

external interface HemisphericLight {
    val direction: ObservableVec3
    var intensity: Double
    var diffuseColor: Array<Double>
    var specularColor: Array<Double>
    var groundColor: Array<Double>
}

external interface DirectionalLight {
    val direction: ObservableVec3
    val position: ObservableVec3
    var diffuse: Array<Double>
    var specular: Array<Double>
    var intensity: Double
}

external interface PointLight {
    val position: ObservableVec3
    var diffuse: Array<Double>
    var specular: Array<Double>
    var intensity: Double
    var range: Double
}

external interface Texture2D {
    val width: Int
    val height: Int
}

external interface DynamicTexture2D : Texture2D

external interface StandardMaterialProps {
    var name: String?
    var diffuseColor: Array<Double>
    var alpha: Double
    var specularColor: Array<Double>
    var specularPower: Double
    var emissiveColor: Array<Double>
    var ambientColor: Array<Double>
    var diffuseTexture: Texture2D?
    var bumpLevel: Double
    var alphaCutOff: Double
    var uvScale: Array<Double>
    var uvOffset: Array<Double>?
    var backFaceCulling: Boolean
    var disableLighting: Boolean
}

external interface Mesh {
    var name: String
    var id: String?
    val position: ObservableVec3
    val rotation: EulerProxy
    val scaling: ObservableVec3
    var material: dynamic
    var receiveShadows: Boolean
    var renderOrder: Int?
    var hasVertexAlpha: Boolean?
    var pickable: Boolean?
    var metadata: dynamic
}

external interface Ray {
    var origin: Array<Double>
    var direction: Array<Double>
    var length: Double
}

external interface PickingInfo {
    val hit: Boolean
    val distance: Double
    val pickedPoint: Array<Double>?
    val pickedMesh: Mesh?
    val thinInstanceIndex: Int
}

external interface BabylonLiteModule {
    fun createEngine(canvas: dynamic, options: dynamic = definedExternally): Promise<EngineContext>
    fun createNullEngine(options: dynamic = definedExternally): EngineContext
    fun startEngine(engine: EngineContext): Promise<Unit>
    fun stopEngine(engine: EngineContext)
    fun disposeEngine(engine: EngineContext)
    fun resizeEngine(engine: EngineContext)
    fun stepScene(engine: EngineContext, scene: SceneContext, deltaMs: Double)

    fun createSceneContext(surface: EngineContext, options: dynamic = definedExternally): SceneContext
    fun registerScene(scene: SceneContext): Promise<Unit>
    fun disposeScene(scene: SceneContext)
    fun addToScene(scene: SceneContext, entity: dynamic)
    fun removeFromScene(scene: SceneContext, entity: dynamic)
    fun onBeforeRender(scene: SceneContext, cb: (deltaMs: Double) -> Unit)
    fun setFog(scene: SceneContext, config: dynamic)

    fun vec3(x: Double, y: Double, z: Double): Vec3
    fun createArcRotateCamera(alpha: Double, beta: Double, radius: Double, target: Vec3): ArcRotateCamera
    fun setCameraLimits(camera: ArcRotateCamera, limits: dynamic, scene: SceneContext = definedExternally): () -> Unit
    fun getViewMatrix(camera: dynamic): Mat4
    fun getViewProjectionMatrix(camera: dynamic, aspectRatio: Double): Mat4

    fun createHemisphericLight(direction: Array<Double> = definedExternally, intensity: Double = definedExternally): HemisphericLight
    fun createDirectionalLight(direction: Array<Double>, intensity: Double = definedExternally): DirectionalLight
    fun createPointLight(position: Array<Double>, intensity: Double = definedExternally): PointLight

    fun createGround(engine: EngineContext, options: dynamic = definedExternally): Mesh
    fun createPlane(engine: EngineContext, options: dynamic = definedExternally): Mesh
    fun createBox(engine: EngineContext, options: dynamic = definedExternally): Mesh
    fun createCylinder(engine: EngineContext, options: dynamic = definedExternally): Mesh
    fun createDisc(engine: EngineContext, options: dynamic = definedExternally): Mesh
    fun createSphere(engine: EngineContext, options: dynamic = definedExternally): Mesh
    fun createMeshFromData(
        engine: EngineContext,
        name: String,
        positions: dynamic,
        normals: dynamic,
        indices: dynamic,
        uvs: dynamic = definedExternally,
    ): Mesh

    fun setMeshOpacity(mesh: Mesh, opacity: Double)
    fun setThinInstances(mesh: Mesh, matrices: dynamic, count: Int)
    fun setThinInstanceColors(mesh: Mesh, colors: dynamic)
    fun enableThinInstanceWorldBounds(mesh: Mesh)
    fun enableSurfaceResizeObserver(surface: EngineContext): () -> Unit
    fun enableStandardVertexColors()
    fun enableStandardUvOffset()

    fun createStandardMaterial(): StandardMaterialProps
    fun setStandardBumpTexture(mat: StandardMaterialProps, texture: Texture2D?)
    fun setStandardOpacityTexture(mat: StandardMaterialProps, texture: Texture2D?)
    fun setStandardEmissiveTexture(mat: StandardMaterialProps, texture: Texture2D?)

    fun createDynamicTexture(engine: EngineContext, width: Int, height: Int, options: dynamic = definedExternally): DynamicTexture2D
    fun updateDynamicTexture(engine: EngineContext, tex: DynamicTexture2D, source: dynamic, opts: dynamic = definedExternally)
    fun createTexture2DFromPixels(engine: EngineContext, data: dynamic, width: Int, height: Int, options: dynamic = definedExternally): Texture2D
    fun loadTexture2D(engine: EngineContext, url: String, opts: dynamic = definedExternally): Promise<Texture2D>

    fun createPickingRay(x: Double, y: Double, vpMatrix: Mat4, width: Double, height: Double): Ray?
    fun pickMeshesWithRay(meshes: Array<Mesh>, ray: Ray, options: dynamic = definedExternally): PickingInfo
}

private var cachedBabylonLite: BabylonLiteModule? = null

suspend fun loadBabylonLite(): BabylonLiteModule {
    cachedBabylonLite?.let { return it }
    val loaded = (js("import('@babylonjs/lite')") as Promise<BabylonLiteModule>).await()
    cachedBabylonLite = loaded
    return loaded
}
