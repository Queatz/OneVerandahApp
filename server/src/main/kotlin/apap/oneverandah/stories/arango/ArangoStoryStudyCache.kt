package apap.oneverandah.stories.arango

import apap.oneverandah.api.StudySessionResponse
import apap.oneverandah.config.ArangoConfig
import apap.oneverandah.images.arango.ArangoException
import apap.oneverandah.stories.StoryLogic
import apap.oneverandah.stories.StoryPageStudy
import apap.oneverandah.stories.StoryStudyCache
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
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.util.Base64

class ArangoStoryStudyCache private constructor(
    private val config: ArangoConfig,
    private val client: HttpClient,
) : StoryStudyCache {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private val authHeader = "Basic " + Base64.getEncoder()
        .encodeToString("${config.user}:${config.password}".toByteArray())
    private val dbUrl = "${config.baseUrl}/_db/${config.database}"
    private val collection = "story_page_studies"

    override suspend fun get(storyId: String, pageId: String, languageTag: String): StudySessionResponse? =
        query(
            "FOR doc IN $collection FILTER doc.id == @id RETURN doc",
            buildJsonObject { put("id", StoryLogic.studyCacheKey(storyId, pageId, languageTag)) },
        ).firstOrNull()?.session

    override suspend fun put(
        storyId: String,
        pageId: String,
        languageTag: String,
        session: StudySessionResponse,
    ) {
        val language = StoryLogic.normalizeLanguageTag(languageTag)
        val id = StoryLogic.studyCacheKey(storyId, pageId, language)
        val record = StoryPageStudy(
            id = id,
            storyId = storyId,
            pageId = pageId,
            languageTag = language,
            session = session,
        )
        val doc = (json.encodeToJsonElement(record) as JsonObject).toMutableMap()
        doc["_key"] = kotlinx.serialization.json.JsonPrimitive(safeKey(id))
        val body = json.encodeToString(JsonObject.serializer(), JsonObject(doc))
        val response = client.post("$dbUrl/_api/document/$collection?overwrite=true") {
            authed()
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        ensureSuccess(response, "save $id")
    }

    override suspend fun evictPage(storyId: String, pageId: String) {
        query(
            "FOR doc IN $collection FILTER doc.storyId == @storyId AND doc.pageId == @pageId REMOVE doc IN $collection RETURN OLD",
            buildJsonObject {
                put("storyId", storyId)
                put("pageId", pageId)
            },
        )
    }

    override suspend fun evictStory(storyId: String) {
        query(
            "FOR doc IN $collection FILTER doc.storyId == @storyId REMOVE doc IN $collection RETURN OLD",
            buildJsonObject { put("storyId", storyId) },
        )
    }

    private suspend fun query(aql: String, bindVars: JsonObject?): List<StoryPageStudy> {
        val payload = buildJsonObject {
            put("query", aql)
            if (bindVars != null) put("bindVars", bindVars)
        }
        val response = client.post("$dbUrl/_api/cursor") {
            authed()
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(JsonObject.serializer(), payload))
        }
        ensureSuccess(response, "query")
        val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
        val result = root["result"]?.jsonArray ?: return emptyList()
        return result.mapNotNull { element ->
            runCatching { json.decodeFromJsonElement(StoryPageStudy.serializer(), element) }
                .onFailure { err -> log.warn("Skipping undecodable story study: {}", err.message) }
                .getOrNull()
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.authed() {
        header(HttpHeaders.Authorization, authHeader)
    }

    private suspend fun ensureSuccess(response: HttpResponse, action: String) {
        if (response.status.value >= 400) {
            throw ArangoException("ArangoDB $action failed (${response.status}): ${response.bodyAsText()}")
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ArangoStoryStudyCache::class.java)

        suspend fun connect(config: ArangoConfig, client: HttpClient?): ArangoStoryStudyCache? {
            if (!config.enabled || client == null) return null
            return try {
                val repo = ArangoStoryStudyCache(config, client)
                repo.ensureSchema()
                log.info("Story study cache connected to ArangoDB collection '{}'", repo.collection)
                repo
            } catch (e: Exception) {
                log.warn(
                    "ArangoDB story studies unavailable at {} ({}); falling back to in-memory",
                    config.baseUrl,
                    e.message,
                )
                null
            }
        }

        private fun safeKey(id: String): String =
            id.replace(Regex("[^A-Za-z0-9_.:@()+=?*'%-]"), "_")
    }

    private suspend fun ensureSchema() {
        suspend fun postJson(url: String, body: String): HttpResponse =
            client.post(url) {
                header(HttpHeaders.Authorization, authHeader)
                contentType(ContentType.Application.Json)
                setBody(body)
            }

        val dbResp = postJson(
            "${config.baseUrl}/_db/_system/_api/database",
            buildJsonObject { put("name", config.database) }
                .let { json.encodeToString(JsonObject.serializer(), it) },
        )
        if (dbResp.status.value >= 400 && dbResp.status != HttpStatusCode.Conflict) {
            throw ArangoException("create database failed (${dbResp.status}): ${dbResp.bodyAsText()}")
        }

        val colResp = postJson(
            "$dbUrl/_api/collection",
            buildJsonObject { put("name", collection) }
                .let { json.encodeToString(JsonObject.serializer(), it) },
        )
        if (colResp.status.value >= 400 && colResp.status != HttpStatusCode.Conflict) {
            throw ArangoException("create story_page_studies collection failed (${colResp.status}): ${colResp.bodyAsText()}")
        }

        postJson(
            "$dbUrl/_api/index?collection=$collection",
            buildJsonObject {
                put("type", "persistent")
                put("unique", true)
                put(
                    "fields",
                    buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("id")) },
                )
            }.let { json.encodeToString(JsonObject.serializer(), it) },
        )
    }
}
