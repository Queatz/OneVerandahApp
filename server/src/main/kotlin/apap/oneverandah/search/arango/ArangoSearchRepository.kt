package apap.oneverandah.search.arango

import apap.oneverandah.config.ArangoConfig
import apap.oneverandah.search.SearchRecord
import apap.oneverandah.search.SearchRepository
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.util.Base64

/**
 * [SearchRepository] backed by ArangoDB via its HTTP REST API.
 *
 * Mirrors [apap.oneverandah.images.arango.ArangoImageRepository]: it uses the
 * REST API (instead of the official Java driver) to keep the dependency
 * footprint small. Create instances through [ArangoSearchRepository.connect],
 * which provisions the database and collection and returns `null` if ArangoDB
 * is unreachable so the caller can fall back to in-memory storage.
 */
class ArangoSearchRepository private constructor(
    private val config: ArangoConfig,
    private val client: HttpClient,
) : SearchRepository {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val authHeader = "Basic " + Base64.getEncoder()
        .encodeToString("${config.user}:${config.password}".toByteArray())
    private val dbUrl = "${config.baseUrl}/_db/${config.database}"
    private val collection = "search_queries"

    override suspend fun record(record: SearchRecord) {
        val doc = (json.encodeToJsonElement(record) as JsonObject).toMutableMap()
        doc["_key"] = kotlinx.serialization.json.JsonPrimitive(record.id)
        val body = json.encodeToString(JsonObject.serializer(), JsonObject(doc))
        // overwrite=true makes record idempotent (insert or replace).
        val response = client.post("$dbUrl/_api/document/$collection?overwrite=true") {
            authed()
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        ensureSuccess(response, "record ${record.id}")
    }

    override suspend fun recent(limit: Int): List<SearchRecord> =
        query(
            "FOR doc IN $collection SORT doc.createdAt DESC LIMIT @limit RETURN doc",
            buildJsonObject { put("limit", limit) },
        )

    private suspend fun query(aql: String, bindVars: JsonObject?): List<SearchRecord> {
        val body = buildJsonObject {
            put("query", aql)
            if (bindVars != null) put("bindVars", bindVars)
            put("batchSize", 1000)
        }
        val response = client.post("$dbUrl/_api/cursor") {
            authed()
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(JsonObject.serializer(), body))
        }
        val text = response.bodyAsText()
        ensureSuccess(response, "query")
        val result = json.parseToJsonElement(text).jsonObject["result"]?.jsonArray ?: return emptyList()
        return result.mapNotNull { element ->
            runCatching { json.decodeFromJsonElement(SearchRecord.serializer(), element) }.getOrNull()
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.authed() {
        header(HttpHeaders.Authorization, authHeader)
    }

    private suspend fun ensureSuccess(response: HttpResponse, action: String) {
        if (response.status.value >= 400) {
            throw ArangoSearchException("ArangoDB $action failed (${response.status}): ${response.bodyAsText()}")
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ArangoSearchRepository::class.java)

        /**
         * Connects to ArangoDB, ensuring the database and collection exist.
         * Returns `null` (so the caller falls back to in-memory storage) when
         * ArangoDB is disabled or cannot be reached.
         */
        suspend fun connect(config: ArangoConfig, client: HttpClient): ArangoSearchRepository? {
            if (!config.enabled) return null
            return try {
                val repo = ArangoSearchRepository(config, client)
                repo.ensureSchema()
                log.info("Connected to ArangoDB search store at {} (db={})", config.baseUrl, config.database)
                repo
            } catch (e: Exception) {
                log.warn("ArangoDB search store unavailable ({}); falling back to in-memory storage", e.message)
                null
            }
        }
    }

    private suspend fun ensureSchema() {
        val json = Json
        val authHeader = authHeader

        suspend fun postJson(url: String, body: String): HttpResponse =
            client.post(url) {
                header(HttpHeaders.Authorization, authHeader)
                contentType(ContentType.Application.Json)
                setBody(body)
            }

        // 1) Database (created from _system). 409 == already exists.
        val dbResp = postJson(
            "${config.baseUrl}/_db/_system/_api/database",
            buildJsonObject { put("name", config.database) }
                .let { json.encodeToString(JsonObject.serializer(), it) },
        )
        if (dbResp.status.value >= 400 && dbResp.status != HttpStatusCode.Conflict) {
            throw ArangoSearchException("create database failed (${dbResp.status}): ${dbResp.bodyAsText()}")
        }

        // 2) Collection. 409 == already exists.
        val colResp = postJson(
            "$dbUrl/_api/collection",
            buildJsonObject { put("name", collection) }
                .let { json.encodeToString(JsonObject.serializer(), it) },
        )
        if (colResp.status.value >= 400 && colResp.status != HttpStatusCode.Conflict) {
            throw ArangoSearchException("create collection failed (${colResp.status}): ${colResp.bodyAsText()}")
        }

        // 3) Index on createdAt so the "latest searches" query stays fast
        //    (idempotent in ArangoDB).
        postJson(
            "$dbUrl/_api/index?collection=$collection",
            buildJsonObject {
                put("type", "persistent")
                put("unique", false)
                put("fields", kotlinx.serialization.json.buildJsonArray {
                    add(kotlinx.serialization.json.JsonPrimitive("createdAt"))
                })
            }.let { json.encodeToString(JsonObject.serializer(), it) },
        )
    }
}

/** Raised on ArangoDB HTTP API errors from the search store. */
class ArangoSearchException(message: String) : RuntimeException(message)
