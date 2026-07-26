package apap.oneverandah.chat

import apap.oneverandah.api.ChatMiniApp
import apap.oneverandah.api.ChatSender
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
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.util.Base64

class ArangoSavedMessagesRepository private constructor(
    private val config: ArangoConfig,
    private val client: HttpClient,
) : SavedMessagesRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        classDiscriminator = "type"
    }
    private val authHeader = "Basic " + Base64.getEncoder()
        .encodeToString("${config.user}:${config.password}".toByteArray())
    private val dbUrl = "${config.baseUrl}/_db/${config.database}"
    private val collection = "saved_chat_messages"

    override suspend fun listByDevice(deviceTokenHash: String): List<SavedChatMessageRecord> {
        val body = buildJsonObject {
            put(
                "query",
                "FOR doc IN $collection FILTER doc.deviceTokenHash == @deviceTokenHash SORT doc.savedAt DESC RETURN doc",
            )
            put("bindVars", buildJsonObject { put("deviceTokenHash", deviceTokenHash) })
        }
        return queryDocuments(body, "list saved messages")
    }

    override suspend fun findByDeviceAndSource(
        deviceTokenHash: String,
        sourceMessageId: String,
    ): SavedChatMessageRecord? {
        val body = buildJsonObject {
            put(
                "query",
                """
                FOR doc IN $collection
                  FILTER doc.deviceTokenHash == @deviceTokenHash
                  FILTER doc.sourceMessageId == @sourceMessageId
                  LIMIT 1
                  RETURN doc
                """.trimIndent(),
            )
            put(
                "bindVars",
                buildJsonObject {
                    put("deviceTokenHash", deviceTokenHash)
                    put("sourceMessageId", sourceMessageId)
                },
            )
        }
        return queryDocuments(body, "find saved message by source").firstOrNull()
    }

    override suspend fun findById(id: String): SavedChatMessageRecord? {
        val body = buildJsonObject {
            put(
                "query",
                "FOR doc IN $collection FILTER doc._key == @id LIMIT 1 RETURN doc",
            )
            put("bindVars", buildJsonObject { put("id", sanitizeKey(id)) })
        }
        return queryDocuments(body, "find saved message by id").firstOrNull()
    }

    override suspend fun upsert(record: SavedChatMessageRecord): SavedChatMessageRecord {
        val document = SavedChatMessageDocument.fromRecord(record)
        val response = client.post("$dbUrl/_api/document/$collection?overwrite=true") {
            authed()
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(SavedChatMessageDocument.serializer(), document))
        }
        ensureSuccess(response, "upsert saved message")
        return record
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
        ensureSuccess(response, "delete saved message")
        return json.parseToJsonElement(text).jsonObject["result"]?.jsonArray?.isNotEmpty() == true
    }

    override suspend fun deleteOldestBeyond(deviceTokenHash: String, keepNewest: Int) {
        val body = buildJsonObject {
            put(
                "query",
                """
                FOR doc IN $collection
                  FILTER doc.deviceTokenHash == @deviceTokenHash
                  SORT doc.savedAt DESC
                  LIMIT @keepNewest, 100000
                  REMOVE doc IN $collection
                """.trimIndent(),
            )
            put(
                "bindVars",
                buildJsonObject {
                    put("deviceTokenHash", deviceTokenHash)
                    put("keepNewest", keepNewest)
                },
            )
        }
        val response = client.post("$dbUrl/_api/cursor") {
            authed()
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(JsonObject.serializer(), body))
        }
        ensureSuccess(response, "enforce saved message cap")
    }

    private suspend fun queryDocuments(body: JsonObject, action: String): List<SavedChatMessageRecord> {
        val response = client.post("$dbUrl/_api/cursor") {
            authed()
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(JsonObject.serializer(), body))
        }
        val text = response.bodyAsText()
        ensureSuccess(response, action)
        return json.parseToJsonElement(text).jsonObject["result"]?.jsonArray.orEmpty().mapNotNull {
            runCatching { json.decodeFromJsonElement(SavedChatMessageDocument.serializer(), it).toRecord() }
                .getOrNull()
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.authed() {
        header(HttpHeaders.Authorization, authHeader)
    }

    private suspend fun ensureSuccess(response: HttpResponse, action: String) {
        if (response.status.value >= 400) {
            throw ArangoSavedMessagesException(
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
            throw ArangoSavedMessagesException("create database failed: ${databaseResponse.bodyAsText()}")
        }
        val collectionResponse = postJson(
            "$dbUrl/_api/collection",
            "{\"name\":\"$collection\"}",
        )
        if (collectionResponse.status.value >= 400 && collectionResponse.status != HttpStatusCode.Conflict) {
            throw ArangoSavedMessagesException("create collection failed: ${collectionResponse.bodyAsText()}")
        }
        postJson(
            "$dbUrl/_api/index?collection=$collection",
            buildJsonObject {
                put("type", "persistent")
                put("unique", false)
                put(
                    "fields",
                    buildJsonArray {
                        add(kotlinx.serialization.json.JsonPrimitive("deviceTokenHash"))
                        add(kotlinx.serialization.json.JsonPrimitive("savedAt"))
                    },
                )
            }.let { json.encodeToString(JsonObject.serializer(), it) },
        )
        postJson(
            "$dbUrl/_api/index?collection=$collection",
            buildJsonObject {
                put("type", "persistent")
                put("unique", true)
                put("sparse", true)
                put(
                    "fields",
                    buildJsonArray {
                        add(kotlinx.serialization.json.JsonPrimitive("deviceTokenHash"))
                        add(kotlinx.serialization.json.JsonPrimitive("sourceMessageId"))
                    },
                )
            }.let { json.encodeToString(JsonObject.serializer(), it) },
        )
    }

    companion object {
        suspend fun connect(config: ArangoConfig, client: HttpClient): ArangoSavedMessagesRepository? {
            if (!config.enabled) return null
            return runCatching {
                ArangoSavedMessagesRepository(config, client).also { it.ensureSchema() }
            }.getOrNull()
        }

        internal fun sanitizeKey(id: String): String =
            id.replace(Regex("[^A-Za-z0-9_:-]"), "_")
    }
}

@Serializable
private data class SavedChatMessageDocument(
    val _key: String,
    val deviceTokenHash: String,
    val sourceMessageId: String? = null,
    val sender: ChatSender,
    val text: String,
    val createdAt: Long,
    val savedAt: Long,
    val miniApp: ChatMiniApp? = null,
    val followUpQuestions: List<String> = emptyList(),
) {
    fun toRecord() = SavedChatMessageRecord(
        id = _key,
        deviceTokenHash = deviceTokenHash,
        sourceMessageId = sourceMessageId,
        sender = sender,
        text = text,
        createdAt = createdAt,
        savedAt = savedAt,
        miniApp = miniApp,
        followUpQuestions = followUpQuestions,
    )

    companion object {
        fun fromRecord(record: SavedChatMessageRecord) = SavedChatMessageDocument(
            _key = ArangoSavedMessagesRepository.sanitizeKey(record.id),
            deviceTokenHash = record.deviceTokenHash,
            sourceMessageId = record.sourceMessageId,
            sender = record.sender,
            text = record.text,
            createdAt = record.createdAt,
            savedAt = record.savedAt,
            miniApp = record.miniApp,
            followUpQuestions = record.followUpQuestions,
        )
    }
}

class ArangoSavedMessagesException(message: String) : RuntimeException(message)
