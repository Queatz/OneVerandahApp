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
    /**
     * When true [endpoint] is treated as a custom domain (CNAME) already bound
     * to the bucket and is used verbatim as the request host, instead of the
     * virtual-hosted `bucket.endpoint` form. This is required for mainland-China
     * buckets, which since 2025-03-20 reject the default public OSS endpoints
     * (`*.aliyuncs.com`) for data operations with `PublicEndpointForbidden`.
     */
    val cname: Boolean,
) {
    val enabled: Boolean
        get() = endpoint.isNotBlank() &&
            accessKeyId.isNotBlank() &&
            accessKeySecret.isNotBlank() &&
            bucket.isNotBlank()

    private val bareEndpoint: String
        get() = endpoint.removePrefix("https://").removePrefix("http://").trimEnd('/')

    /**
     * Host used for OSS data operations. With a CNAME the custom domain is used
     * directly; otherwise the bucket is prefixed to the region endpoint
     * (virtual-hosted style).
     */
    val requestHost: String
        get() = if (cname) bareEndpoint else "$bucket.$bareEndpoint"

    /** Public URL of [objectKey], honouring a CDN base when configured. */
    fun urlFor(objectKey: String): String {
        val base = publicBaseUrl?.trimEnd('/') ?: "https://$requestHost"
        return "$base/$objectKey"
    }
}

/**
 * Qwen (DashScope) image-generation settings. [enabled] requires an API key.
 *
 * [model] / [endpoint] drive text-only generation (catalog covers, study playground
 * comics). Multimodal still models such as `qwen-image-3.0-pro` / `wan2.x-image` must not
 * be paired with classic `text2image/image-synthesis` — DashScope rejects that with
 * `InvalidParameter: url error`. The client auto-routes those models through
 * [r2iEndpoint] (multimodal-generation) when needed.
 *
 * Speed knobs:
 * - [promptExtend] runs an extra LLM rewrite (~3–4s). Off by default because our
 *   prompts are already detailed (study story panels, covers).
 * - Prefer `512*512` only for classic T2I / legacy R2I contracts when those are used.
 * - [multimodalSize] for multimodal still models accepts `1K`/`2K` or `width*height`; pixel
 *   sizes must stay in about `[768*768, 2048*2048]`, so 512 is not valid there.
 * - Default [model] and [r2iModel] both use `qwen-image-3.0-pro` (quality multimodal).
 *   Text-only calls are auto-routed to the multimodal endpoint.
 */
data class QwenImageConfig(
    val endpoint: String,
    val apiKey: String,
    val model: String,
    /**
     * DashScope endpoint for multimodal still-image generation (text-only or
     * reference-grounded). Default is the multimodal generation API used by
     * qwen-image / wan2.x-image models.
     */
    val r2iEndpoint: String =
        "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation",
    /**
     * Model used when apartment reference photos are supplied.
     * Multimodal still models accept multiple reference images + a text prompt.
     */
    val r2iModel: String = "qwen-image-3.0-pro",
    /**
     * When true, DashScope rewrites the prompt with an LLM before generating.
     * Improves short prompts but adds ~3–4s latency per image.
     */
    val promptExtend: Boolean = false,
    /**
     * Output size for multimodal still models (`wan2.x-image`): `1K`/`2K` or
     * `width*height`. Pixel sizes must be at least ~`768*768` on DashScope.
     */
    val multimodalSize: String = "768*768",
    /**
     * Output size for classic text-to-image models (`width*height`).
     * Only used when [model] is a classic wanx / `*-t2i*` model.
     */
    val textToImageSize: String = "512*512",
    /**
     * Output size for legacy VACE / image2image reference contracts (`width*height`).
     */
    val r2iSize: String = "512*512",
) {
    val enabled: Boolean get() = apiKey.isNotBlank()
}

/** Qwen chat, speech synthesis, and speech recognition settings. */
data class QwenChatConfig(
    val chatEndpoint: String,
    /** Fast default model for chat and lighter study tasks. */
    val chatModel: String,
    /**
     * Stronger model reserved for heavy study-session breakdown work
     * (skeleton + per-sentence dissection).
     */
    val chatHeavyModel: String,
    val ttsEndpoint: String,
    val ttsModel: String,
    /** Built-in Qwen-Audio-TTS system voice used as the app default. */
    val ttsVoice: String,
    val voiceDesignEndpoint: String,
    val voiceDesignModel: String,
    val asrEndpoint: String,
    val asrModel: String,
    val apiKey: String,
) {
    val enabled: Boolean get() = apiKey.isNotBlank()
    val asrUnavailableReason: String?
        get() = when {
            !enabled -> "QWEN_API_KEY"
            asrEndpoint.isBlank() -> "QWEN_ASR_WORKSPACE_ID or QWEN_ASR_ENDPOINT"
            asrModel.isBlank() -> "QWEN_ASR_MODEL"
            else -> null
        }
    val asrEnabled: Boolean get() = asrUnavailableReason == null

    fun asrUrl(language: String? = null): String {
        val base = "$asrEndpoint${if ('?' in asrEndpoint) '&' else '?'}model=$asrModel"
        return if (language.isNullOrBlank()) base else "$base&language=$language"
    }

    fun asrHeaders(): Map<String, String> = mapOf(
        "Authorization" to "Bearer $apiKey",
        "OpenAI-Beta" to "realtime=v1",
    )
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
    val qwen: QwenImageConfig,
    val qwenChat: QwenChatConfig,
    /** Directory that backs the local storage fallback. */
    val localStorageDir: String,
    /**
     * Directory holding a local backup of every generated image, used to retry
     * a failed upload without regenerating the image.
     */
    val imageCacheDir: String,
    /**
     * Directory for durable Qwen TTS audio cache entries (exact text + language
     * + voice). Survives restarts so repeated speech requests skip the provider.
     */
    val ttsCacheDir: String,
    /** Local file every Qwen/DashScope generation failure is appended to. */
    val qwenErrorLogPath: String,
    val maxConcurrentGenerations: Int,
    val generationDelayMs: Long,
    val maxRetries: Int,
    val rescheduleIntervalMs: Long,
    /**
     * When true the server seeds the catalog and starts the background
     * generation worker on startup. Disabled in tests to keep them hermetic.
     */
    val autoStart: Boolean,
    /** Shared password for the hidden admin portal sign-in sheet. */
    val adminPassword: String,
    /** HS256 secret used to mint never-expiring admin JWTs. */
    val adminJwtSecret: String,
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

            // The public base URL is what clients use to reach this server and,
            // crucially, the prefix of every locally-served image link. When it
            // isn't set explicitly we derive it, but MUST NOT fall back to
            // "localhost" in production: the server binds 0.0.0.0, so "localhost"
            // there produced broken image URLs like https://localhost/images/... .
            // Prefer the configured public domain (SSL_DOMAIN — the hostname the
            // server's certificate is issued for and that it is actually reached
            // at, e.g. api.oneverandah.app).
            val defaultScheme = if (ssl != null) "https" else "http"
            val sslDomain = source["SSL_DOMAIN"]?.takeIf { it.isNotBlank() }
            val defaultHostForUrl = when {
                sslDomain != null -> sslDomain
                host == "0.0.0.0" -> "localhost"
                else -> host
            }
            val defaultPortForUrl = ssl?.port ?: port
            // Omit the port when it is the scheme's default (443 for https, 80
            // for http) so public URLs stay clean (https://api.example.app/...
            // rather than https://api.example.app:443/...).
            val portSuffix = when {
                defaultScheme == "https" && defaultPortForUrl == 443 -> ""
                defaultScheme == "http" && defaultPortForUrl == 80 -> ""
                else -> ":$defaultPortForUrl"
            }
            val publicBaseUrl = (source["PUBLIC_BASE_URL"]
                ?: "$defaultScheme://$defaultHostForUrl$portSuffix").trimEnd('/')

            val allowedCorsHosts = source["CORS_ALLOWED_HOSTS"]
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?: emptyList()

            val defaultArangoHost = if (env == AppEnv.PRODUCTION) "127.0.0.1" else ""

            val arango = ArangoConfig(
                host = source["ARANGO_HOST"]?.takeIf { it.isNotBlank() } ?: defaultArangoHost,
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
                cname = source.bool("OSS_CNAME", false),
            )

            val qwen = QwenImageConfig(
                endpoint = source["QWEN_API_ENDPOINT"]
                    ?: "https://dashscope.aliyuncs.com/api/v1/services/aigc/text2image/image-synthesis",
                apiKey = source["QWEN_API_KEY"] ?: "",
                // Text-only + reference-grounded both default to qwen-image-3.0-pro (multimodal).
                model = source["QWEN_MODEL"] ?: "qwen-image-3.0-pro",
                r2iEndpoint = source["QWEN_R2I_ENDPOINT"]
                    ?: "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation",
                r2iModel = source["QWEN_R2I_MODEL"] ?: "qwen-image-3.0-pro",
                promptExtend = source.bool("QWEN_PROMPT_EXTEND", false),
                multimodalSize = source["QWEN_MULTIMODAL_SIZE"]?.takeIf { it.isNotBlank() } ?: "768*768",
                textToImageSize = source["QWEN_T2I_SIZE"]?.takeIf { it.isNotBlank() } ?: "512*512",
                r2iSize = source["QWEN_R2I_SIZE"]?.takeIf { it.isNotBlank() } ?: "512*512",
            )

            val qwenChat = QwenChatConfig(
                chatEndpoint = source["QWEN_CHAT_ENDPOINT"]
                    ?: "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
                // Fast default for chat and lighter study tasks; heavy breakdown uses chatHeavyModel.
                chatModel = source["QWEN_CHAT_MODEL"] ?: "qwen3.8-flash",
                chatHeavyModel = source["QWEN_CHAT_HEAVY_MODEL"] ?: "qwen3-max-preview",
                // Qwen-Audio-TTS shares the CosyVoice-compatible WebSocket synthesis protocol.
                ttsEndpoint = "wss://dashscope.aliyuncs.com/api-ws/v1/inference/",
                ttsModel = source["QWEN_TTS_MODEL"] ?: "qwen-audio-3.0-tts-flash",
                // Default curated social-companion voice for qwen-audio-3.0-tts-flash.
                ttsVoice = source["QWEN_TTS_VOICE"] ?: "longanhuan_v3.6",
                voiceDesignEndpoint = "https://dashscope.aliyuncs.com/api/v1/services/audio/tts/customization",
                voiceDesignModel = source["QWEN_VOICE_DESIGN_MODEL"] ?: "voice-enrollment",
                asrEndpoint = source["QWEN_ASR_ENDPOINT"]
                    ?: source["QWEN_ASR_WORKSPACE_ID"]?.let { workspaceId ->
                        "wss://$workspaceId.cn-beijing.maas.aliyuncs.com/api-ws/v1/realtime"
                    }.orEmpty(),
                asrModel = source["QWEN_ASR_MODEL"] ?: "qwen3-asr-flash-realtime",
                apiKey = source["QWEN_API_KEY"] ?: "",
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
                qwenChat = qwenChat,
                localStorageDir = source["LOCAL_STORAGE_DIR"] ?: "data/images",
                imageCacheDir = source["IMAGE_CACHE_DIR"] ?: "data/image-cache",
                ttsCacheDir = source["TTS_CACHE_DIR"] ?: "data/tts-cache",
                qwenErrorLogPath = source["QWEN_ERROR_LOG_PATH"] ?: "qwen_errors.txt",
                maxConcurrentGenerations = source.int("MAX_CONCURRENT_GENERATIONS", 2),
                generationDelayMs = source.long("GENERATION_DELAY_MS", 1000),
                maxRetries = source.int("GENERATION_MAX_RETRIES", 3),
                rescheduleIntervalMs = source.long("RESCHEDULE_INTERVAL_MS", 60 * 60 * 1000),
                autoStart = source.bool("IMAGE_AUTOSTART", true),
                adminPassword = source["ADMIN_PASSWORD"] ?: "oneverandah",
                adminJwtSecret = source["ADMIN_JWT_SECRET"]
                    ?: source["ADMIN_PASSWORD"]
                    ?: "oneverandah-dev-jwt-secret",
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
