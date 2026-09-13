package apap.oneverandah.clubs.arango

import apap.oneverandah.clubs.Club
import apap.oneverandah.clubs.ClubCreateRequest
import apap.oneverandah.clubs.ClubJoin
import apap.oneverandah.clubs.ClubRepository
import apap.oneverandah.config.ArangoConfig
import apap.oneverandah.images.arango.ArangoException
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

class ArangoClubRepository private constructor(
    private val config: ArangoConfig,
    private val client: HttpClient,
) : ClubRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private val authHeader = "Basic " + Base64.getEncoder()
        .encodeToString("${config.user}:${config.password}".toByteArray())
    private val dbUrl = "${config.baseUrl}/_db/${config.database}"

    override suspend fun findAll(): List<Club> =
        query(CLUBS, "FOR doc IN $CLUBS SORT doc.createdAt DESC RETURN doc", null, Club.serializer())

    override suspend fun find(id: String): Club? =
        query(
            CLUBS,
            "FOR doc IN $CLUBS FILTER doc.id == @id RETURN doc",
            buildJsonObject { put("id", id) },
            Club.serializer(),
        ).firstOrNull()

    override suspend fun save(club: Club) = save(CLUBS, club.id, club, Club.serializer())

    override suspend fun delete(id: String): Boolean {
        find(id) ?: return false
        query<Club>(
            CLUBS,
            "FOR doc IN $CLUBS FILTER doc.id == @id REMOVE doc IN $CLUBS RETURN OLD",
            buildJsonObject { put("id", id) },
            Club.serializer(),
        )
        deleteJoinsFor(id)
        return true
    }

    override suspend fun findCreateRequests(): List<ClubCreateRequest> =
        query(
            CREATE_REQUESTS,
            "FOR doc IN $CREATE_REQUESTS SORT doc.createdAt DESC RETURN doc",
            null,
            ClubCreateRequest.serializer(),
        )

    override suspend fun saveCreateRequest(request: ClubCreateRequest) =
        save(CREATE_REQUESTS, request.id, request, ClubCreateRequest.serializer())

    override suspend fun deleteCreateRequest(id: String): Boolean {
        val existing = query(
            CREATE_REQUESTS,
            "FOR doc IN $CREATE_REQUESTS FILTER doc.id == @id RETURN doc",
            buildJsonObject { put("id", id) },
            ClubCreateRequest.serializer(),
        ).firstOrNull() ?: return false
        query<ClubCreateRequest>(
            CREATE_REQUESTS,
            "FOR doc IN $CREATE_REQUESTS FILTER doc.id == @id REMOVE doc IN $CREATE_REQUESTS RETURN OLD",
            buildJsonObject { put("id", existing.id) },
            ClubCreateRequest.serializer(),
        )
        return true
    }

    override suspend fun findJoins(): List<ClubJoin> =
        query(
            JOINS,
            "FOR doc IN $JOINS SORT doc.createdAt DESC RETURN doc",
            null,
            ClubJoin.serializer(),
        )

    override suspend fun findJoinsByClub(clubId: String): List<ClubJoin> =
        query(
            JOINS,
            "FOR doc IN $JOINS FILTER doc.clubId == @id SORT doc.createdAt DESC RETURN doc",
            buildJsonObject { put("id", clubId) },
            ClubJoin.serializer(),
        )

    override suspend fun saveJoin(join: ClubJoin) = save(JOINS, join.id, join, ClubJoin.serializer())

    override suspend fun deleteJoin(id: String): Boolean {
        val existing = query(
            JOINS,
            "FOR doc IN $JOINS FILTER doc.id == @id RETURN doc",
            buildJsonObject { put("id", id) },
            ClubJoin.serializer(),
        ).firstOrNull() ?: return false
        query<ClubJoin>(
            JOINS,
            "FOR doc IN $JOINS FILTER doc.id == @id REMOVE doc IN $JOINS RETURN OLD",
            buildJsonObject { put("id", existing.id) },
            ClubJoin.serializer(),
        )
        return true
    }

    override suspend fun deleteJoinsFor(clubId: String) {
        query<ClubJoin>(
            JOINS,
            "FOR doc IN $JOINS FILTER doc.clubId == @id REMOVE doc IN $JOINS RETURN OLD",
            buildJsonObject { put("id", clubId) },
            ClubJoin.serializer(),
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
        ensureSuccess(response, "save $collection/$id")
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
        private val log = LoggerFactory.getLogger(ArangoClubRepository::class.java)
        private const val CLUBS = "clubs"
        private const val CREATE_REQUESTS = "club_create_requests"
        private const val JOINS = "club_joins"

        suspend fun connect(config: ArangoConfig, client: HttpClient?): ArangoClubRepository? {
            if (!config.enabled || client == null) return null
            return try {
                val repo = ArangoClubRepository(config, client)
                repo.ensureSchema()
                log.info("Club repository connected to ArangoDB collections")
                repo
            } catch (e: Exception) {
                log.warn(
                    "ArangoDB clubs unavailable at {} ({}); falling back to in-memory",
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

        listOf(CLUBS, CREATE_REQUESTS, JOINS).forEach { collection ->
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
            "$dbUrl/_api/index?collection=$JOINS",
            buildJsonObject {
                put("type", "persistent")
                put("unique", false)
                put("fields", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("clubId")) })
            }.let { json.encodeToString(JsonObject.serializer(), it) },
        )
    }
}
