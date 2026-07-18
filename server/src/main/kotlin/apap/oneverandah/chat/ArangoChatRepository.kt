package apap.oneverandah.chat

import apap.oneverandah.api.ChatMessage
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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.util.Base64

class ArangoChatRepository private constructor(
    private val config: ArangoConfig,
    private val client: HttpClient,
) : ChatRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        classDiscriminator = "type"
    }
    private val authHeader = "Basic " + Base64.getEncoder()
        .encodeToString("${config.user}:${config.password}".toByteArray())
    private val dbUrl = "${config.baseUrl}/_db/${config.database}"
    private val collection = "chat_messages"

    override suspend fun append(conversationId: String, message: ChatMessage) {
        val document = ChatDocument(
            _key = message.id.replace(Regex("[^A-Za-z0-9_:-]"), "_"),
            conversationId = conversationId,
            message = message,
        )
        val response = client.post("$dbUrl/_api/document/$collection?overwrite=true") {
            authed()
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(ChatDocument.serializer(), document))
        }
        ensureSuccess(response, "append message")
    }

    override suspend fun history(conversationId: String): List<ChatMessage> {
        val body = buildJsonObject {
            put("query", "FOR doc IN $collection FILTER doc.conversationId == @conversationId SORT doc.message.createdAt ASC RETURN doc")
            put("bindVars", buildJsonObject { put("conversationId", conversationId) })
        }
        val response = client.post("$dbUrl/_api/cursor") {
            authed()
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(JsonObject.serializer(), body))
        }
        val text = response.bodyAsText()
        ensureSuccess(response, "load history")
        return json.parseToJsonElement(text).jsonObject["result"]?.jsonArray.orEmpty().mapNotNull {
            runCatching { json.decodeFromJsonElement(ChatDocument.serializer(), it).message }.getOrNull()
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.authed() {
        header(HttpHeaders.Authorization, authHeader)
    }

    private suspend fun ensureSuccess(response: HttpResponse, action: String) {
        if (response.status.value >= 400) {
            throw ArangoChatException("ArangoDB $action failed (${response.status}): ${response.bodyAsText()}")
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
            throw ArangoChatException("create database failed: ${databaseResponse.bodyAsText()}")
        }
        val collectionResponse = postJson(
            "$dbUrl/_api/collection",
            "{\"name\":\"$collection\"}",
        )
        if (collectionResponse.status.value >= 400 && collectionResponse.status != HttpStatusCode.Conflict) {
            throw ArangoChatException("create collection failed: ${collectionResponse.bodyAsText()}")
        }
        postJson(
            "$dbUrl/_api/index?collection=$collection",
            buildJsonObject {
                put("type", "persistent")
                put("unique", false)
                put("fields", buildJsonArray {
                    add(kotlinx.serialization.json.JsonPrimitive("conversationId"))
                    add(kotlinx.serialization.json.JsonPrimitive("message.createdAt"))
                })
            }.let { json.encodeToString(JsonObject.serializer(), it) },
        )
    }

    companion object {
        suspend fun connect(config: ArangoConfig, client: HttpClient): ArangoChatRepository? {
            if (!config.enabled) return null
            return runCatching {
                ArangoChatRepository(config, client).also { it.ensureSchema() }
            }.getOrNull()
        }
    }
}

@Serializable
private data class ChatDocument(
    val _key: String,
    val conversationId: String,
    val message: ChatMessage,
)

class ArangoChatException(message: String) : RuntimeException(message)