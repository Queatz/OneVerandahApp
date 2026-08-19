package apap.oneverandah

import apap.oneverandah.chat.ChatSystem
import apap.oneverandah.config.AppConfig
import apap.oneverandah.images.ApiError
import apap.oneverandah.images.ImageSystem
import apap.oneverandah.phrases.PhraseSystem
import apap.oneverandah.routes.imageRoutes
import apap.oneverandah.routes.chatRoutes
import apap.oneverandah.routes.asrRoutes
import apap.oneverandah.routes.deviceRoutes
import apap.oneverandah.routes.phraseRoutes
import apap.oneverandah.routes.searchRoutes
import apap.oneverandah.routes.challengeRoutes
import apap.oneverandah.routes.storyRoutes
import apap.oneverandah.challenges.ChallengeSystem
import apap.oneverandah.stories.StorySystem
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.connector
import io.ktor.server.engine.sslConnector
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.io.File
import java.security.KeyStore

private val log = LoggerFactory.getLogger("apap.oneverandah.Application")

/**
 * Production entry point.
 *
 * Loads configuration, builds the [ImageSystem] and starts the Ktor server with
 * a plain-HTTP connector plus, when running in production with SSL configured,
 * an additional HTTPS connector. Only the environment (`.env` / real env vars)
 * differs between development and production — the same build runs in both.
 */
fun main() {
    val config = AppConfig.load()
    val imageSystem = runBlocking { ImageSystem.create(config) }
    // Cloned-voice admin uploads need the same storage backend as images.
    val chatSystem = runBlocking { ChatSystem.create(config, imageSystem.storage) }
    val phraseSystem = runBlocking {
        PhraseSystem.create(config, imageSystem.storage, imageSystem.httpClient)
    }

    val server = embeddedServer(
        Netty,
        configure = {
            connector {
                host = config.host
                port = config.port
            }
            config.ssl?.let { ssl ->
                val keyStore = loadKeyStore(ssl.keyStorePath, ssl.keyStorePassword)
                sslConnector(
                    keyStore = keyStore,
                    keyAlias = ssl.keyAlias,
                    keyStorePassword = { ssl.keyStorePassword.toCharArray() },
                    privateKeyPassword = { ssl.privateKeyPassword.toCharArray() },
                ) {
                    host = config.host
                    port = ssl.port
                    keyStorePath = File(ssl.keyStorePath)
                }
                log.info("HTTPS connector enabled on port {}", ssl.port)
            }
        },
        module = { module(config, imageSystem, chatSystem, phraseSystem) },
    )

    if (config.autoStart) {
        imageSystem.startup()
        phraseSystem.startup()
    }
    server.start(wait = true)
}

/**
 * Zero-argument module used by the Ktor test host and any convention-based
 * loader. Wires the app from environment configuration but never starts the
 * background generation worker, keeping tests hermetic.
 */
fun Application.module() {
    val config = AppConfig.load()
    val imageSystem = runBlocking { ImageSystem.create(config) }
    val chatSystem = runBlocking { ChatSystem.create(config, imageSystem.storage) }
    val phraseSystem = runBlocking {
        PhraseSystem.create(config, imageSystem.storage, imageSystem.httpClient)
    }
    module(config, imageSystem, chatSystem, phraseSystem)
}

/**
 * Installs plugins and routes for [imageSystem]. Pure wiring: it performs no
 * seeding and starts no workers (see [ImageSystem.startup]).
 */
fun Application.module(config: AppConfig, imageSystem: ImageSystem) {
    val chatSystem = runBlocking { ChatSystem.create(config, imageSystem.storage) }
    val phraseSystem = runBlocking {
        PhraseSystem.create(config, imageSystem.storage, imageSystem.httpClient)
    }
    module(config, imageSystem, chatSystem, phraseSystem)
}

fun Application.module(config: AppConfig, imageSystem: ImageSystem, chatSystem: ChatSystem) {
    val phraseSystem = runBlocking {
        PhraseSystem.create(config, imageSystem.storage, imageSystem.httpClient)
    }
    module(config, imageSystem, chatSystem, phraseSystem)
}

fun Application.module(
    config: AppConfig,
    imageSystem: ImageSystem,
    chatSystem: ChatSystem,
    phraseSystem: PhraseSystem,
) {
    launchChatStartup(chatSystem::startup)
    // Seed the phrasebook asynchronously so cold starts stay snappy.
    phraseSystem.startup()

    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        })
    }
    install(CallLogging) { level = Level.INFO }
    install(WebSockets)
    install(CORS) {
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowHeader("X-Device-Token")
        if (config.allowedCorsHosts.isEmpty()) {
            anyHost()
        } else {
            config.allowedCorsHosts.forEach { host ->
                allowHost(host, schemes = listOf("http", "https"))
            }
        }
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            log.error("Unhandled error on {}: {}", call.request.local.uri, cause.message, cause)
            call.respond(HttpStatusCode.InternalServerError, ApiError(cause.message ?: "Internal server error"))
        }
    }

    monitor.subscribe(ApplicationStopped) {
        imageSystem.shutdown()
        chatSystem.shutdown()
        phraseSystem.shutdown()
    }

    routing {
        // Preserved for backwards compatibility / simple liveness probe.
        get("/") {
            call.respondText(sayHello("Ktor"))
        }
        imageRoutes(imageSystem, config)
        searchRoutes(imageSystem)
        chatRoutes(
            chatSystem.chatService,
            chatSystem.speechService,
            chatSystem.savedMessagesService,
            chatSystem.studyService,
            chatSystem.studySessionsService,
        )
        asrRoutes(chatSystem.asrRelay)
        deviceRoutes(chatSystem.deviceService)
        phraseRoutes(phraseSystem, config, chatSystem.clonedVoicesService)
        val storySystem = runBlocking {
            StorySystem.create(
                config = config,
                storage = imageSystem.storage,
                phraseSystem = phraseSystem,
                studyService = chatSystem.studyService,
                studySessionsService = chatSystem.studySessionsService,
                httpClient = imageSystem.httpClient,
            )
        }
        storyRoutes(storySystem, config)
        val challengeSystem = runBlocking {
            ChallengeSystem.create(
                config = config,
                storage = imageSystem.storage,
                httpClient = imageSystem.httpClient,
            )
        }
        challengeRoutes(challengeSystem, config)
    }
}

internal fun CoroutineScope.launchChatStartup(startup: suspend () -> Unit): Job {
    return launch {
        try {
            startup()
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            log.error("Asynchronous chat startup failed; speech initialization will retry on demand", cause)
        }
    }
}

/** Loads a JKS/PKCS12 keystore used by the HTTPS connector. */
private fun loadKeyStore(path: String, password: String): KeyStore {
    val file = File(path)
    require(file.exists()) { "SSL keystore not found at $path" }
    val type = if (path.endsWith(".p12") || path.endsWith(".pfx")) "PKCS12" else "JKS"
    val keyStore = KeyStore.getInstance(type)
    file.inputStream().use { keyStore.load(it, password.toCharArray()) }
    return keyStore
}
