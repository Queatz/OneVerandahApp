package apap.oneverandah.chat

import apap.oneverandah.config.ArangoConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.util.Base64

class ArangoClonedVoicesRepository private constructor(
    private val config: ArangoConfig,
    private val client: HttpClient,
) : ClonedVoicesRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
    private val authHeader = "Basic " + Base64.getEncoder()
        .encodeToString("${config.user}:${config.password}".toByteArray())
    private val dbUrl = "${config.baseUrl}/_db/${config.database}"
    private val collection = "cloned_voices"

    override suspend fun list(): List<ClonedVoiceRecord> {
        val body = buildJsonObject {
            put("query", "FOR doc IN $collection SORT doc.createdAt DESC RETURN doc")
        }
        return queryDocuments(body, "list cloned voices")
    }

    override suspend fun findById(id: String): ClonedVoiceRecord? {
        val body = buildJsonObject {
            put("query", "FOR doc IN $collection FILTER doc._key == @id LIMIT 1 RETURN doc")
            put("bindVars", buildJsonObject { put("id", sanitizeKey(id)) })
        }
        return queryDocuments(body, "find cloned voice").firstOrNull()
    }

    override suspend fun save(record: ClonedVoiceRecord) {
        val document = ClonedVoiceDocument.fromRecord(record)
        val response = client.post("$dbUrl/_api/document/$collection?overwrite=true") {
            authed()
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(ClonedVoiceDocument.serializer(), document))
        }
        ensureSuccess(response, "save cloned voice")
    }

    override suspend fun delete(id: String): Boolean {
        val body = buildJsonObject {
            put(
                "query",
                "FOR doc IN $collection FILTER doc._key == @id REMOVE doc IN $collection RETURN OLD",
            )
            put("bindVars", buildJsonObject { put("id", sanitizeKey(id)) })
        }
        val response = client.post("$dbUrl/_api/cursor") {
            authed()
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(JsonObject.serializer(), body))
        }
        val text = response.bodyAsText()
        ensureSuccess(response, "delete cloned voice")
        return json.parseToJsonElement(text).jsonObject["result"]?.jsonArray?.isNotEmpty() == true
    }

    override suspend fun deactivateLanguage(languageTag: String, exceptId: String?) {
        val body = buildJsonObject {
            put(
                "query",
                """
                FOR doc IN $collection
                  FILTER doc.languageTag == @languageTag
                  FILTER doc.active == true
                  FILTER @exceptId == null OR doc._key != @exceptId
                  UPDATE doc WITH { active: false } IN $collection
                """.trimIndent(),
            )
            put(
                "bindVars",
                buildJsonObject {
                    put("languageTag", languageTag)
                    put("exceptId", exceptId?.let(::sanitizeKey))
                },
            )
        }
        val response = client.post("$dbUrl/_api/cursor") {
            authed()
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(JsonObject.serializer(), body))
        }
        ensureSuccess(response, "deactivate cloned voices")
    }

    private suspend fun queryDocuments(body: JsonObject, action: String): List<ClonedVoiceRecord> {
        val response = client.post("$dbUrl/_api/cursor") {
            authed()
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(JsonObject.serializer(), body))
        }
        val text = response.bodyAsText()
        ensureSuccess(response, action)
        return json.parseToJsonElement(text).jsonObject["result"]?.jsonArray.orEmpty().mapNotNull {
            runCatching { json.decodeFromJsonElement(ClonedVoiceDocument.serializer(), it).toRecord() }
                .getOrNull()
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.authed() {
        header(HttpHeaders.Authorization, authHeader)
    }

    private suspend fun ensureSuccess(response: HttpResponse, action: String) {
        if (response.status.value >= 400) {
            throw IllegalStateException(
                "ArangoDB $action failed (${response.status}): ${response.bodyAsText()}",
            )
        }
    }

    private suspend fun ensureSchema() {
        suspend fun postJson(url: String, body: String): HttpResponse = client.post(url) {
            authed()
            contentType(ContentType.Application.Json)
            setBody(body)
        }

        val databaseResponse = postJson(
            "${config.baseUrl}/_db/_system/_api/database",
            "{\"name\":\"${config.database}\"}",
        )
        if (databaseResponse.status.value >= 400 && databaseResponse.status != HttpStatusCode.Conflict) {
            throw IllegalStateException("create database failed: ${databaseResponse.bodyAsText()}")
        }
        val collectionResponse = postJson(
            "$dbUrl/_api/collection",
            "{\"name\":\"$collection\"}",
        )
        if (collectionResponse.status.value >= 400 && collectionResponse.status != HttpStatusCode.Conflict) {
            throw IllegalStateException("create collection failed: ${collectionResponse.bodyAsText()}")
        }
        postJson(
            "$dbUrl/_api/index?collection=$collection",
            buildJsonObject {
                put("type", "persistent")
                put("unique", false)
                put(
                    "fields",
                    kotlinx.serialization.json.buildJsonArray {
                        add(kotlinx.serialization.json.JsonPrimitive("languageTag"))
                        add(kotlinx.serialization.json.JsonPrimitive("active"))
                    },
                )
            }.let { json.encodeToString(JsonObject.serializer(), it) },
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(ArangoClonedVoicesRepository::class.java)

        suspend fun connect(config: ArangoConfig, client: HttpClient): ArangoClonedVoicesRepository? {
            if (!config.enabled) return null
            return runCatching {
                ArangoClonedVoicesRepository(config, client).also {
                    it.ensureSchema()
                    log.info("Cloned voices repository connected to ArangoDB collection '{}'", it.collection)
                }
            }.onFailure {
                log.warn("ArangoDB cloned voices unavailable ({}); falling back to in-memory", it.message)
            }.getOrNull()
        }

        internal fun sanitizeKey(id: String): String =
            id.replace(Regex("[^A-Za-z0-9_:-]"), "_")
    }
}

@Serializable
private data class ClonedVoiceDocument(
    val _key: String,
    val id: String,
    val name: String,
    val languageTag: String,
    val providerVoiceId: String,
    val sampleUrl: String? = null,
    val active: Boolean = false,
    val createdAt: Long,
) {
    fun toRecord() = ClonedVoiceRecord(
        id = id,
        name = name,
        languageTag = languageTag,
        providerVoiceId = providerVoiceId,
        sampleUrl = sampleUrl,
        active = active,
        createdAt = createdAt,
    )

    companion object {
        fun fromRecord(record: ClonedVoiceRecord) = ClonedVoiceDocument(
            _key = ArangoClonedVoicesRepository.sanitizeKey(record.id),
            id = record.id,
            name = record.name,
            languageTag = record.languageTag,
            providerVoiceId = record.providerVoiceId,
            sampleUrl = record.sampleUrl,
            active = record.active,
            createdAt = record.createdAt,
        )
    }
}
