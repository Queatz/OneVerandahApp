package apap.oneverandah.phrases.arango

import apap.oneverandah.config.ArangoConfig
import apap.oneverandah.images.arango.ArangoException
import apap.oneverandah.phrases.ReferencePhoto
import apap.oneverandah.phrases.ReferencePhotoRepository
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

class ArangoReferencePhotoRepository private constructor(
    private val config: ArangoConfig,
    private val client: HttpClient,
) : ReferencePhotoRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val authHeader = "Basic " + Base64.getEncoder()
        .encodeToString("${config.user}:${config.password}".toByteArray())
    private val dbUrl = "${config.baseUrl}/_db/${config.database}"
    private val collection = "reference_photos"

    override suspend fun findAll(): List<ReferencePhoto> =
        query("FOR doc IN $collection SORT doc.createdAt DESC RETURN doc", null)

    override suspend fun findById(id: String): ReferencePhoto? =
        query(
            "FOR doc IN $collection FILTER doc.id == @id RETURN doc",
            buildJsonObject { put("id", id) },
        ).firstOrNull()

    override suspend fun save(photo: ReferencePhoto) {
        val doc = (json.encodeToJsonElement(photo) as JsonObject).toMutableMap()
        doc["_key"] = kotlinx.serialization.json.JsonPrimitive(photo.id)
        val body = json.encodeToString(JsonObject.serializer(), JsonObject(doc))
        val response = client.post("$dbUrl/_api/document/$collection?overwrite=true") {
            authed()
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        ensureSuccess(response, "save ${photo.id}")
    }

    override suspend fun delete(id: String): Boolean {
        findById(id) ?: return false
        query(
            "FOR doc IN $collection FILTER doc.id == @id REMOVE doc IN $collection RETURN OLD",
            buildJsonObject { put("id", id) },
        )
        return true
    }

    private suspend fun query(aql: String, bindVars: JsonObject?): List<ReferencePhoto> {
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
            runCatching { json.decodeFromJsonElement(ReferencePhoto.serializer(), element) }.getOrNull()
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
        private val log = LoggerFactory.getLogger(ArangoReferencePhotoRepository::class.java)

        suspend fun connect(config: ArangoConfig, client: HttpClient): ArangoReferencePhotoRepository? {
            if (!config.enabled) return null
            return try {
                val repo = ArangoReferencePhotoRepository(config, client)
                repo.ensureSchema()
                log.info("Reference photo repository connected to ArangoDB collection '{}'", repo.collection)
                repo
            } catch (e: Exception) {
                log.warn("ArangoDB reference photos unavailable ({}); falling back to in-memory", e.message)
                null
            }
        }
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
            throw ArangoException("create reference_photos collection failed (${colResp.status}): ${colResp.bodyAsText()}")
        }

        postJson(
            "$dbUrl/_api/index?collection=$collection",
            buildJsonObject {
                put("type", "persistent")
                put("unique", true)
                put("fields", kotlinx.serialization.json.buildJsonArray {
                    add(kotlinx.serialization.json.JsonPrimitive("id"))
                })
            }.let { json.encodeToString(JsonObject.serializer(), it) },
        )
        postJson(
            "$dbUrl/_api/index?collection=$collection",
            buildJsonObject {
                put("type", "persistent")
                put("unique", true)
                put("fields", kotlinx.serialization.json.buildJsonArray {
                    add(kotlinx.serialization.json.JsonPrimitive("name"))
                })
            }.let { json.encodeToString(JsonObject.serializer(), it) },
        )
    }
}
