package apap.oneverandah.meets.arango

import apap.oneverandah.config.ArangoConfig
import apap.oneverandah.images.arango.ArangoException
import apap.oneverandah.meets.Meet
import apap.oneverandah.meets.MeetPitch
import apap.oneverandah.meets.MeetRepository
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
import kotlinx.serialization.KSerializer
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

class ArangoMeetRepository private constructor(
    private val config: ArangoConfig,
    private val client: HttpClient,
) : MeetRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private val authHeader = "Basic " + Base64.getEncoder()
        .encodeToString("${config.user}:${config.password}".toByteArray())
    private val dbUrl = "${config.baseUrl}/_db/${config.database}"

    override suspend fun findAllMeets(): List<Meet> =
        query(MEETS, "FOR doc IN $MEETS SORT doc.createdAt DESC RETURN doc", null, Meet.serializer())

    override suspend fun findMeet(id: String): Meet? =
        query(
            MEETS,
            "FOR doc IN $MEETS FILTER doc.id == @id RETURN doc",
            buildJsonObject { put("id", id) },
            Meet.serializer(),
        ).firstOrNull()

    override suspend fun saveMeet(meet: Meet) = save(MEETS, meet.id, meet, Meet.serializer())

    override suspend fun deleteMeet(id: String): Boolean {
        findMeet(id) ?: return false
        query<Meet>(
            MEETS,
            "FOR doc IN $MEETS FILTER doc.id == @id REMOVE doc IN $MEETS RETURN OLD",
            buildJsonObject { put("id", id) },
            Meet.serializer(),
        )
        deletePitchesForMeet(id)
        return true
    }

    override suspend fun findAllPitches(): List<MeetPitch> =
        query(PITCHES, "FOR doc IN $PITCHES SORT doc.createdAt DESC RETURN doc", null, MeetPitch.serializer())

    override suspend fun findPitch(id: String): MeetPitch? =
        query(
            PITCHES,
            "FOR doc IN $PITCHES FILTER doc.id == @id RETURN doc",
            buildJsonObject { put("id", id) },
            MeetPitch.serializer(),
        ).firstOrNull()

    override suspend fun findPitchesByMeet(meetId: String): List<MeetPitch> =
        query(
            PITCHES,
            "FOR doc IN $PITCHES FILTER doc.meetId == @id SORT doc.createdAt DESC RETURN doc",
            buildJsonObject { put("id", meetId) },
            MeetPitch.serializer(),
        )

    override suspend fun savePitch(pitch: MeetPitch) = save(PITCHES, pitch.id, pitch, MeetPitch.serializer())

    override suspend fun deletePitch(id: String): Boolean {
        findPitch(id) ?: return false
        query<MeetPitch>(
            PITCHES,
            "FOR doc IN $PITCHES FILTER doc.id == @id REMOVE doc IN $PITCHES RETURN OLD",
            buildJsonObject { put("id", id) },
            MeetPitch.serializer(),
        )
        return true
    }

    override suspend fun deletePitchesForMeet(meetId: String) {
        query<MeetPitch>(
            PITCHES,
            "FOR doc IN $PITCHES FILTER doc.meetId == @id REMOVE doc IN $PITCHES RETURN OLD",
            buildJsonObject { put("id", meetId) },
            MeetPitch.serializer(),
        )
    }

    private suspend fun <T> save(collection: String, id: String, value: T, serializer: KSerializer<T>) {
        val doc = (json.encodeToJsonElement(serializer, value) as JsonObject).toMutableMap()
        doc["_key"] = kotlinx.serialization.json.JsonPrimitive(id)
        val body = json.encodeToString(JsonObject.serializer(), JsonObject(doc))
        val response = client.post("$dbUrl/_api/document/$collection?overwrite=true") {
            authed()
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        ensureSuccess(response, "save $collection $id")
    }

    private suspend fun <T> query(
        collection: String,
        aql: String,
        bindVars: JsonObject?,
        serializer: KSerializer<T>,
    ): List<T> {
        val payload = buildJsonObject {
            put("query", aql)
            if (bindVars != null) put("bindVars", bindVars)
        }
        val response = client.post("$dbUrl/_api/cursor") {
            authed()
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(JsonObject.serializer(), payload))
        }
        ensureSuccess(response, "query $collection")
        val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
        val result = root["result"]?.jsonArray ?: return emptyList()
        return result.mapNotNull { element ->
            runCatching { json.decodeFromJsonElement(serializer, element) }
                .onFailure { err -> log.warn("Skipping undecodable {} document: {}", collection, err.message) }
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
        private val log = LoggerFactory.getLogger(ArangoMeetRepository::class.java)
        private const val MEETS = "meets"
        private const val PITCHES = "meet_pitches"

        suspend fun connect(config: ArangoConfig, client: HttpClient?): ArangoMeetRepository? {
            if (!config.enabled || client == null) return null
            return try {
                val repo = ArangoMeetRepository(config, client)
                repo.ensureSchema()
                log.info("Meet repository connected to ArangoDB collections")
                repo
            } catch (e: Exception) {
                log.warn(
                    "ArangoDB meets unavailable at {} ({}); falling back to in-memory",
                    config.baseUrl,
                    e.message,
                )
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

        listOf(MEETS, PITCHES).forEach { collection ->
            val colResp = postJson(
                "$dbUrl/_api/collection",
                buildJsonObject { put("name", collection) }
                    .let { json.encodeToString(JsonObject.serializer(), it) },
            )
            if (colResp.status.value >= 400 && colResp.status != HttpStatusCode.Conflict) {
                throw ArangoException("create $collection collection failed (${colResp.status}): ${colResp.bodyAsText()}")
            }
            postJson(
                "$dbUrl/_api/index?collection=$collection",
                buildJsonObject {
                    put("type", "persistent")
                    put("unique", true)
                    put("fields", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("id")) })
                }.let { json.encodeToString(JsonObject.serializer(), it) },
            )
        }
        postJson(
            "$dbUrl/_api/index?collection=$PITCHES",
            buildJsonObject {
                put("type", "persistent")
                put("unique", false)
                put("fields", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("meetId")) })
            }.let { json.encodeToString(JsonObject.serializer(), it) },
        )
    }
}
