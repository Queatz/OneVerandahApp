package apap.oneverandah.locations.arango

import apap.oneverandah.config.ArangoConfig
import apap.oneverandah.images.arango.ArangoException
import apap.oneverandah.locations.Location
import apap.oneverandah.locations.LocationCommitment
import apap.oneverandah.locations.LocationContact
import apap.oneverandah.locations.LocationLike
import apap.oneverandah.locations.LocationRepository
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

class ArangoLocationRepository private constructor(
    private val config: ArangoConfig,
    private val client: HttpClient,
) : LocationRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private val authHeader = "Basic " + Base64.getEncoder()
        .encodeToString("${config.user}:${config.password}".toByteArray())
    private val dbUrl = "${config.baseUrl}/_db/${config.database}"

    override suspend fun findAll(): List<Location> =
        query(LOCATIONS, "FOR doc IN $LOCATIONS SORT doc.createdAt DESC RETURN doc", null, Location.serializer())

    override suspend fun find(id: String): Location? =
        query(
            LOCATIONS,
            "FOR doc IN $LOCATIONS FILTER doc.id == @id RETURN doc",
            buildJsonObject { put("id", id) },
            Location.serializer(),
        ).firstOrNull()

    override suspend fun save(location: Location) = save(LOCATIONS, location.id, location, Location.serializer())

    override suspend fun delete(id: String): Boolean {
        find(id) ?: return false
        query<Location>(
            LOCATIONS,
            "FOR doc IN $LOCATIONS FILTER doc.id == @id REMOVE doc IN $LOCATIONS RETURN OLD",
            buildJsonObject { put("id", id) },
            Location.serializer(),
        )
        deleteLikesFor(id)
        deleteCommitmentsFor(id)
        deleteContactsFor(id)
        return true
    }

    override suspend fun findLikesByLocation(locationId: String): List<LocationLike> =
        query(
            LIKES,
            "FOR doc IN $LIKES FILTER doc.locationId == @id SORT doc.createdAt DESC RETURN doc",
            buildJsonObject { put("id", locationId) },
            LocationLike.serializer(),
        )

    override suspend fun findLike(locationId: String, deviceTokenHash: String): LocationLike? =
        query(
            LIKES,
            "FOR doc IN $LIKES FILTER doc.locationId == @id AND doc.deviceTokenHash == @hash RETURN doc",
            buildJsonObject {
                put("id", locationId)
                put("hash", deviceTokenHash)
            },
            LocationLike.serializer(),
        ).firstOrNull()

    override suspend fun saveLike(like: LocationLike) = save(LIKES, like.id, like, LocationLike.serializer())

    override suspend fun deleteLike(id: String): Boolean {
        val existing = query(
            LIKES,
            "FOR doc IN $LIKES FILTER doc.id == @id RETURN doc",
            buildJsonObject { put("id", id) },
            LocationLike.serializer(),
        ).firstOrNull() ?: return false
        query<LocationLike>(
            LIKES,
            "FOR doc IN $LIKES FILTER doc.id == @id REMOVE doc IN $LIKES RETURN OLD",
            buildJsonObject { put("id", existing.id) },
            LocationLike.serializer(),
        )
        return true
    }

    override suspend fun deleteLikesFor(locationId: String) {
        query<LocationLike>(
            LIKES,
            "FOR doc IN $LIKES FILTER doc.locationId == @id REMOVE doc IN $LIKES RETURN OLD",
            buildJsonObject { put("id", locationId) },
            LocationLike.serializer(),
        )
    }

    override suspend fun findCommitmentsByLocation(locationId: String): List<LocationCommitment> =
        query(
            COMMITMENTS,
            "FOR doc IN $COMMITMENTS FILTER doc.locationId == @id SORT doc.createdAt DESC RETURN doc",
            buildJsonObject { put("id", locationId) },
            LocationCommitment.serializer(),
        )

    override suspend fun saveCommitment(commitment: LocationCommitment) =
        save(COMMITMENTS, commitment.id, commitment, LocationCommitment.serializer())

    override suspend fun deleteCommitmentsFor(locationId: String) {
        query<LocationCommitment>(
            COMMITMENTS,
            "FOR doc IN $COMMITMENTS FILTER doc.locationId == @id REMOVE doc IN $COMMITMENTS RETURN OLD",
            buildJsonObject { put("id", locationId) },
            LocationCommitment.serializer(),
        )
    }

    override suspend fun findContactsByLocation(locationId: String): List<LocationContact> =
        query(
            CONTACTS,
            "FOR doc IN $CONTACTS FILTER doc.locationId == @id SORT doc.createdAt DESC RETURN doc",
            buildJsonObject { put("id", locationId) },
            LocationContact.serializer(),
        )

    override suspend fun saveContact(contact: LocationContact) =
        save(CONTACTS, contact.id, contact, LocationContact.serializer())

    override suspend fun deleteContactsFor(locationId: String) {
        query<LocationContact>(
            CONTACTS,
            "FOR doc IN $CONTACTS FILTER doc.locationId == @id REMOVE doc IN $CONTACTS RETURN OLD",
            buildJsonObject { put("id", locationId) },
            LocationContact.serializer(),
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
        ensureSuccess(response, "save $collection")
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
        private val log = LoggerFactory.getLogger(ArangoLocationRepository::class.java)
        private const val LOCATIONS = "locations"
        private const val LIKES = "location_likes"
        private const val COMMITMENTS = "location_commitments"
        private const val CONTACTS = "location_contacts"

        suspend fun connect(config: ArangoConfig, client: HttpClient?): ArangoLocationRepository? {
            if (!config.enabled || client == null) return null
            return try {
                val repo = ArangoLocationRepository(config, client)
                repo.ensureSchema()
                log.info("Location repository connected to ArangoDB collections")
                repo
            } catch (e: Exception) {
                log.warn(
                    "ArangoDB locations unavailable at {} ({}); falling back to in-memory",
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

        listOf(LOCATIONS, LIKES, COMMITMENTS, CONTACTS).forEach { collection ->
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
        listOf(LIKES, COMMITMENTS, CONTACTS).forEach { collection ->
            postJson(
                "$dbUrl/_api/index?collection=$collection",
                buildJsonObject {
                    put("type", "persistent")
                    put("unique", false)
                    put("fields", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("locationId")) })
                }.let { json.encodeToString(JsonObject.serializer(), it) },
            )
        }
    }
}
