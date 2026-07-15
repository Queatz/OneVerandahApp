package apap.oneverandah

import apap.oneverandah.config.AppConfig
import apap.oneverandah.images.ApiError
import apap.oneverandah.images.ImageSystem
import apap.oneverandah.routes.imageRoutes
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
        module = { module(config, imageSystem) },
    )

    if (config.autoStart) imageSystem.startup()
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
    module(config, imageSystem)
}

/**
 * Installs plugins and routes for [imageSystem]. Pure wiring: it performs no
 * seeding and starts no workers (see [ImageSystem.startup]).
 */
fun Application.module(config: AppConfig, imageSystem: ImageSystem) {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        })
    }
    install(CallLogging) { level = Level.INFO }
    install(CORS) {
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Options)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
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

    monitor.subscribe(ApplicationStopped) { imageSystem.shutdown() }

    routing {
        // Preserved for backwards compatibility / simple liveness probe.
        get("/") {
            call.respondText(sayHello("Ktor"))
        }
        imageRoutes(imageSystem, config)
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
