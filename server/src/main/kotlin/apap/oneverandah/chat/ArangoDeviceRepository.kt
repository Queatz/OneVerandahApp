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
import java.util.Base64

class ArangoDeviceRepository private constructor(
    private val config: ArangoConfig,
    private val client: HttpClient,
) : DeviceRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
    private val authHeader = "Basic " + Base64.getEncoder()
        .encodeToString("${config.user}:${config.password}".toByteArray())
    private val dbUrl = "${config.baseUrl}/_db/${config.database}"
    private val collection = "devices"

    override suspend fun create(tokenHash: String, createdAt: Long, lastSeenAt: Long): DeviceRecord {
        val document = DeviceDocument(
            _key = tokenHash,
            tokenHash = tokenHash,
            createdAt = createdAt,
            lastSeenAt = lastSeenAt,
            revokedAt = null,
        )
        val response = client.post("$dbUrl/_api/document/$collection") {
            authed()
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(DeviceDocument.serializer(), document))
        }
        ensureSuccess(response, "create device")
        return document.toRecord()
    }

    override suspend fun findByTokenHash(tokenHash: String): DeviceRecord? {
        val body = buildJsonObject {
            put(
                "query",
                "FOR doc IN $collection FILTER doc.tokenHash == @tokenHash LIMIT 1 RETURN doc",
            )
            put("bindVars", buildJsonObject { put("tokenHash", tokenHash) })
        }
        val response = client.post("$dbUrl/_api/cursor") {
            authed()
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(JsonObject.serializer(), body))
        }
        val text = response.bodyAsText()
        ensureSuccess(response, "find device")
        val result = json.parseToJsonElement(text).jsonObject["result"]?.jsonArray.orEmpty()
        val first = result.firstOrNull() ?: return null
        return runCatching { json.decodeFromJsonElement(DeviceDocument.serializer(), first).toRecord() }
            .getOrNull()
    }

    override suspend fun touch(tokenHash: String, lastSeenAt: Long) {
        val body = buildJsonObject {
            put(
                "query",
                """
                FOR doc IN $collection
                  FILTER doc.tokenHash == @tokenHash
                  UPDATE doc WITH { lastSeenAt: @lastSeenAt } IN $collection
                """.trimIndent(),
            )
            put(
                "bindVars",
                buildJsonObject {
                    put("tokenHash", tokenHash)
                    put("lastSeenAt", lastSeenAt)
                },
            )
        }
        val response = client.post("$dbUrl/_api/cursor") {
            authed()
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(JsonObject.serializer(), body))
        }
        ensureSuccess(response, "touch device")
    }

    private fun io.ktor.client.request.HttpRequestBuilder.authed() {
        header(HttpHeaders.Authorization, authHeader)
    }

    private suspend fun ensureSuccess(response: HttpResponse, action: String) {
        if (response.status.value >= 400) {
            throw ArangoDeviceException("ArangoDB $action failed (${response.status}): ${response.bodyAsText()}")
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
            throw ArangoDeviceException("create database failed: ${databaseResponse.bodyAsText()}")
        }
        val collectionResponse = postJson(
            "$dbUrl/_api/collection",
            "{\"name\":\"$collection\"}",
        )
        if (collectionResponse.status.value >= 400 && collectionResponse.status != HttpStatusCode.Conflict) {
            throw ArangoDeviceException("create collection failed: ${collectionResponse.bodyAsText()}")
        }
        postJson(
            "$dbUrl/_api/index?collection=$collection",
            buildJsonObject {
                put("type", "persistent")
                put("unique", true)
                put(
                    "fields",
                    kotlinx.serialization.json.buildJsonArray {
                        add(kotlinx.serialization.json.JsonPrimitive("tokenHash"))
                    },
                )
            }.let { json.encodeToString(JsonObject.serializer(), it) },
        )
    }

    companion object {
        suspend fun connect(config: ArangoConfig, client: HttpClient): ArangoDeviceRepository? {
            if (!config.enabled) return null
            return runCatching {
                ArangoDeviceRepository(config, client).also { it.ensureSchema() }
            }.getOrNull()
        }
    }
}

@Serializable
private data class DeviceDocument(
    val _key: String,
    val tokenHash: String,
    val createdAt: Long,
    val lastSeenAt: Long,
    val revokedAt: Long? = null,
) {
    fun toRecord() = DeviceRecord(
        tokenHash = tokenHash,
        createdAt = createdAt,
        lastSeenAt = lastSeenAt,
        revokedAt = revokedAt,
    )
}

class ArangoDeviceException(message: String) : RuntimeException(message)
