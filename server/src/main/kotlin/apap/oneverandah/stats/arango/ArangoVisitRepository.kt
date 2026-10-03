package apap.oneverandah.stats.arango

import apap.oneverandah.config.ArangoConfig
import apap.oneverandah.images.arango.ArangoException
import apap.oneverandah.stats.VisitRecord
import apap.oneverandah.stats.VisitRepository
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
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.util.Base64

class ArangoVisitRepository private constructor(
    private val config: ArangoConfig,
    private val client: HttpClient,
) : VisitRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private val authHeader = "Basic " + Base64.getEncoder()
        .encodeToString("${config.user}:${config.password}".toByteArray())
    private val dbUrl = "${config.baseUrl}/_db/${config.database}"

    override suspend fun save(visit: VisitRecord) {
        val document = VisitDocument.from(visit)
        val response = client.post("$dbUrl/_api/document/$COLLECTION") {
            authed()
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(VisitDocument.serializer(), document))
        }
        ensureSuccess(response, "save visit")
    }

    override suspend fun listSince(fromEpochMs: Long): List<VisitRecord> =
        query(
            """
            FOR doc IN $COLLECTION
              FILTER doc.recordedAt >= @from
              RETURN doc
            """.trimIndent(),
            buildJsonObject { put("from", fromEpochMs) },
        ).mapNotNull { element ->
            runCatching { json.decodeFromJsonElement(VisitDocument.serializer(), element).toRecord() }
                .onFailure { err -> log.warn("Skipping undecodable visit: {}", err.message) }
                .getOrNull()
        }

    override suspend fun countAll(): Long = countQuery(
        "FOR doc IN $COLLECTION COLLECT WITH COUNT INTO total RETURN total",
    )

    override suspend fun countUniqueDevices(): Long = countQuery(
        """
        FOR doc IN $COLLECTION
          COLLECT hash = doc.deviceTokenHash
          COLLECT WITH COUNT INTO total
          RETURN total
        """.trimIndent(),
    )

    override suspend fun firstSeenDayKeys(): Map<String, String> {
        val rows = query(
            """
            FOR doc IN $COLLECTION
              COLLECT hash = doc.deviceTokenHash AGGREGATE firstDay = MIN(doc.dayKey)
              RETURN { hash, firstDay }
            """.trimIndent(),
            null,
        )
        return rows.mapNotNull { element ->
            runCatching { json.decodeFromJsonElement(FirstSeenRow.serializer(), element) }
                .getOrNull()
        }.associate { it.hash to it.firstDay }
    }

    private suspend fun query(aql: String, bindVars: JsonObject?): List<kotlinx.serialization.json.JsonElement> {
        val body = buildJsonObject {
            put("query", aql)
            if (bindVars != null) put("bindVars", bindVars)
        }
        val response = client.post("$dbUrl/_api/cursor") {
            authed()
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(JsonObject.serializer(), body))
        }
        val text = response.bodyAsText()
        ensureSuccess(response, "query visits")
        return json.parseToJsonElement(text).jsonObject["result"]?.jsonArray.orEmpty()
    }

    private suspend fun countQuery(aql: String): Long {
        val result = query(aql, null)
        return result.firstOrNull()?.jsonPrimitive?.longOrNull ?: 0L
    }

    private fun io.ktor.client.request.HttpRequestBuilder.authed() {
        header(HttpHeaders.Authorization, authHeader)
    }

    private suspend fun ensureSuccess(response: HttpResponse, action: String) {
        if (response.status.value >= 400) {
            throw ArangoException("ArangoDB $action failed (${response.status}): ${response.bodyAsText()}")
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
            buildJsonObject { put("name", COLLECTION) }
                .let { json.encodeToString(JsonObject.serializer(), it) },
        )
        if (colResp.status.value >= 400 && colResp.status != HttpStatusCode.Conflict) {
            throw ArangoException("create $COLLECTION collection failed (${colResp.status}): ${colResp.bodyAsText()}")
        }

        listOf("recordedAt", "dayKey", "deviceTokenHash").forEach { field ->
            postJson(
                "$dbUrl/_api/index?collection=$COLLECTION",
                buildJsonObject {
                    put("type", "persistent")
                    put("unique", false)
                    put("fields", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(field)) })
                }.let { json.encodeToString(JsonObject.serializer(), it) },
            )
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ArangoVisitRepository::class.java)
        private const val COLLECTION = "visits"

        suspend fun connect(config: ArangoConfig, client: HttpClient?): ArangoVisitRepository? {
            if (!config.enabled || client == null) return null
            return try {
                val repo = ArangoVisitRepository(config, client)
                repo.ensureSchema()
                log.info("Visit repository connected to ArangoDB collection {}", COLLECTION)
                repo
            } catch (e: Exception) {
                log.warn(
                    "ArangoDB visits unavailable at {} ({}); falling back to in-memory",
                    config.baseUrl,
                    e.message,
                )
                null
            }
        }
    }
}

@Serializable
private data class VisitDocument(
    val _key: String,
    val id: String,
    val deviceTokenHash: String,
    val path: String = "/",
    val languageTag: String = "",
    val recordedAt: Long,
    val dayKey: String,
) {
    fun toRecord() = VisitRecord(
        id = id,
        deviceTokenHash = deviceTokenHash,
        path = path,
        languageTag = languageTag,
        recordedAt = recordedAt,
        dayKey = dayKey,
    )

    companion object {
        fun from(visit: VisitRecord) = VisitDocument(
            _key = visit.id,
            id = visit.id,
            deviceTokenHash = visit.deviceTokenHash,
            path = visit.path,
            languageTag = visit.languageTag,
            recordedAt = visit.recordedAt,
            dayKey = visit.dayKey,
        )
    }
}

@Serializable
private data class FirstSeenRow(
    val hash: String,
    val firstDay: String,
)
