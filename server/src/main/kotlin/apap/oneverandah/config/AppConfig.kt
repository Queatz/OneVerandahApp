package apap.oneverandah.config

import java.io.File

/** Runtime environment the server is started in. */
enum class AppEnv {
    DEVELOPMENT,
    PRODUCTION;

    companion object {
        fun from(value: String?): AppEnv = when (value?.trim()?.lowercase()) {
            "prod", "production" -> PRODUCTION
            else -> DEVELOPMENT
        }
    }
}

/** HTTPS connector settings, only present when running with SSL enabled. */
data class SslConfig(
    val keyStorePath: String,
    val keyStorePassword: String,
    val keyAlias: String,
    val privateKeyPassword: String,
    val port: Int,
)

/** ArangoDB connection settings. [enabled] is false when no host is configured. */
data class ArangoConfig(
    val host: String,
    val port: Int,
    val database: String,
    val user: String,
    val password: String,
    val useSsl: Boolean,
) {
    val enabled: Boolean get() = host.isNotBlank()
    val baseUrl: String get() = "${if (useSsl) "https" else "http"}://$host:$port"
}

/**
 * Alibaba OSS settings. [enabled] requires the full set of credentials; when
 * any is missing the server transparently falls back to local file storage.
 */
data class OssConfig(
    val endpoint: String,
    val accessKeyId: String,
    val accessKeySecret: String,
    val bucket: String,
    val region: String,
    /** Optional CDN / custom-domain base used to build public URLs. */
    val publicBaseUrl: String?,
) {
    val enabled: Boolean
        get() = endpoint.isNotBlank() &&
            accessKeyId.isNotBlank() &&
            accessKeySecret.isNotBlank() &&
            bucket.isNotBlank()

    /** Public URL of [objectKey], honouring a CDN base when configured. */
    fun urlFor(objectKey: String): String {
        val base = publicBaseUrl?.trimEnd('/')
            ?: "https://$bucket.${endpoint.removePrefix("https://").removePrefix("http://")}"
        return "$base/$objectKey"
    }
}

/** Qwen (DashScope) image-synthesis settings. [enabled] requires an API key. */
data class QwenConfig(
    val endpoint: String,
    val apiKey: String,
    val model: String,
) {
    val enabled: Boolean get() = apiKey.isNotBlank()
}

/**
 * Fully resolved server configuration.
 *
 * Values come from (in increasing precedence): built-in defaults, an optional
 * `.env` file and finally real process environment variables. This makes the
 * same build run unchanged in development and production; only the environment
 * differs.
 */
data class AppConfig(
    val env: AppEnv,
    val host: String,
    val port: Int,
    val ssl: SslConfig?,
    /** Base URL clients use to reach this server (used to build local image URLs). */
    val publicBaseUrl: String,
    /** Allowed CORS origins; empty means "allow any host" (handy in development). */
    val allowedCorsHosts: List<String>,
    val arango: ArangoConfig,
    val oss: OssConfig,
    val qwen: QwenConfig,
    /** Directory that backs the local storage fallback. */
    val localStorageDir: String,
    val maxConcurrentGenerations: Int,
    val generationDelayMs: Long,
    val maxRetries: Int,
    val rescheduleIntervalMs: Long,
    /**
     * When true the server seeds the catalog and starts the background
     * generation worker on startup. Disabled in tests to keep them hermetic.
     */
    val autoStart: Boolean,
) {
    val isProduction: Boolean get() = env == AppEnv.PRODUCTION

    companion object {
        /**
         * Loads configuration from [envFile] (if present) overlaid with real
         * process environment variables.
         */
        fun load(envFile: File = File(System.getenv("ENV_FILE") ?: ".env")): AppConfig {
            val source = EnvSource.load(envFile)

            val env = AppEnv.from(source["APP_ENV"])
            val host = source["APP_HOST"] ?: "0.0.0.0"
            val port = source.int("APP_PORT", 7071)

            val ssl = buildSsl(source, env)

            val defaultScheme = if (ssl != null) "https" else "http"
            val defaultHostForUrl = if (host == "0.0.0.0") "localhost" else host
            val defaultPortForUrl = ssl?.port ?: port
            val publicBaseUrl = (source["PUBLIC_BASE_URL"]
                ?: "$defaultScheme://$defaultHostForUrl:$defaultPortForUrl").trimEnd('/')

            val allowedCorsHosts = source["CORS_ALLOWED_HOSTS"]
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?: emptyList()

            val arango = ArangoConfig(
                host = source["ARANGO_HOST"] ?: "",
                port = source.int("ARANGO_PORT", 8529),
                database = source["ARANGO_DB"] ?: "image_generator",
                user = source["ARANGO_USER"] ?: "root",
                password = source["ARANGO_PASSWORD"] ?: "",
                useSsl = source.bool("ARANGO_SSL", false),
            )

            val oss = OssConfig(
                endpoint = source["OSS_ENDPOINT"] ?: "",
                accessKeyId = source["OSS_ACCESS_KEY_ID"] ?: "",
                accessKeySecret = source["OSS_ACCESS_KEY_SECRET"] ?: "",
                bucket = source["OSS_BUCKET_NAME"] ?: "",
                region = source["OSS_REGION"] ?: "",
                publicBaseUrl = source["OSS_PUBLIC_BASE_URL"]?.takeIf { it.isNotBlank() },
            )

            val qwen = QwenConfig(
                endpoint = source["QWEN_API_ENDPOINT"]
                    ?: "https://dashscope.aliyuncs.com/api/v1/services/aigc/text2image/image-synthesis",
                apiKey = source["QWEN_API_KEY"] ?: "",
                model = source["QWEN_MODEL"] ?: "wanx2.1-t2i-turbo",
            )

            return AppConfig(
                env = env,
                host = host,
                port = port,
                ssl = ssl,
                publicBaseUrl = publicBaseUrl,
                allowedCorsHosts = allowedCorsHosts,
                arango = arango,
                oss = oss,
                qwen = qwen,
                localStorageDir = source["LOCAL_STORAGE_DIR"] ?: "data/images",
                maxConcurrentGenerations = source.int("MAX_CONCURRENT_GENERATIONS", 2),
                generationDelayMs = source.long("GENERATION_DELAY_MS", 1000),
                maxRetries = source.int("GENERATION_MAX_RETRIES", 3),
                rescheduleIntervalMs = source.long("RESCHEDULE_INTERVAL_MS", 60 * 60 * 1000),
                autoStart = source.bool("IMAGE_AUTOSTART", true),
            )
        }

        private fun buildSsl(source: EnvSource, env: AppEnv): SslConfig? {
            val keyStorePath = source["SSL_KEYSTORE_PATH"]?.takeIf { it.isNotBlank() } ?: return null
            // SSL is only wired up when explicitly enabled or when running in
            // production, so development stays plain-HTTP by default.
            val explicitlyEnabled = source.bool("SSL_ENABLED", env == AppEnv.PRODUCTION)
            if (!explicitlyEnabled) return null
            val keyStorePassword = source["SSL_KEYSTORE_PASSWORD"] ?: ""
            return SslConfig(
                keyStorePath = keyStorePath,
                keyStorePassword = keyStorePassword,
                keyAlias = source["SSL_KEY_ALIAS"] ?: "oneverandah",
                privateKeyPassword = source["SSL_PRIVATE_KEY_PASSWORD"] ?: keyStorePassword,
                port = source.int("SSL_PORT", 8443),
            )
        }
    }
}

/**
 * A tiny read-only view over `.env` values overlaid with process environment
 * variables (the latter win). Keeps configuration parsing dependency-free.
 */
class EnvSource private constructor(private val values: Map<String, String>) {
    operator fun get(key: String): String? =
        System.getenv(key)?.takeIf { it.isNotBlank() } ?: values[key]?.takeIf { it.isNotBlank() }

    fun int(key: String, default: Int): Int = get(key)?.toIntOrNull() ?: default
    fun long(key: String, default: Long): Long = get(key)?.toLongOrNull() ?: default
    fun bool(key: String, default: Boolean): Boolean = when (get(key)?.trim()?.lowercase()) {
        null -> default
        "1", "true", "yes", "on" -> true
        "0", "false", "no", "off" -> false
        else -> default
    }

    companion object {
        fun load(envFile: File): EnvSource {
            val parsed = if (envFile.exists() && envFile.isFile) parse(envFile.readText()) else emptyMap()
            return EnvSource(parsed)
        }

        /** Parses a minimal `.env`: `KEY=VALUE`, `#` comments, optional quotes. */
        fun parse(text: String): Map<String, String> {
            val result = LinkedHashMap<String, String>()
            text.lineSequence().forEach { raw ->
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) return@forEach
                val withoutExport = line.removePrefix("export ").trim()
                val eq = withoutExport.indexOf('=')
                if (eq <= 0) return@forEach
                val key = withoutExport.substring(0, eq).trim()
                var value = withoutExport.substring(eq + 1).trim()
                if (value.length >= 2 &&
                    ((value.first() == '"' && value.last() == '"') ||
                        (value.first() == '\'' && value.last() == '\''))
                ) {
                    value = value.substring(1, value.length - 1)
                }
                result[key] = value
            }
            return result
        }
    }
}
