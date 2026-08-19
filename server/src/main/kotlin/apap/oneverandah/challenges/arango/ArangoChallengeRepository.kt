package apap.oneverandah.challenges.arango

import apap.oneverandah.challenges.Challenge
import apap.oneverandah.challenges.ChallengeRepository
import apap.oneverandah.challenges.ChallengeSubmission
import apap.oneverandah.challenges.ChallengeVote
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

class ArangoChallengeRepository private constructor(
    private val config: ArangoConfig,
    private val client: HttpClient,
) : ChallengeRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private val authHeader = "Basic " + Base64.getEncoder()
        .encodeToString("${config.user}:${config.password}".toByteArray())
    private val dbUrl = "${config.baseUrl}/_db/${config.database}"

    override suspend fun findAllChallenges(): List<Challenge> =
        query(CHALLENGES, "FOR doc IN $CHALLENGES SORT doc.lastActivityAt DESC RETURN doc", null, Challenge.serializer())

    override suspend fun findChallenge(id: String): Challenge? =
        query(
            CHALLENGES,
            "FOR doc IN $CHALLENGES FILTER doc.id == @id RETURN doc",
            buildJsonObject { put("id", id) },
            Challenge.serializer(),
        ).firstOrNull()

    override suspend fun saveChallenge(challenge: Challenge) = save(CHALLENGES, challenge.id, challenge, Challenge.serializer())

    override suspend fun deleteChallenge(id: String): Boolean {
        findChallenge(id) ?: return false
        query<Challenge>(
            CHALLENGES,
            "FOR doc IN $CHALLENGES FILTER doc.id == @id REMOVE doc IN $CHALLENGES RETURN OLD",
            buildJsonObject { put("id", id) },
            Challenge.serializer(),
        )
        query<ChallengeSubmission>(
            SUBMISSIONS,
            "FOR doc IN $SUBMISSIONS FILTER doc.challengeId == @id REMOVE doc IN $SUBMISSIONS RETURN OLD",
            buildJsonObject { put("id", id) },
            ChallengeSubmission.serializer(),
        )
        query<ChallengeVote>(
            VOTES,
            "FOR doc IN $VOTES FILTER doc.challengeId == @id REMOVE doc IN $VOTES RETURN OLD",
            buildJsonObject { put("id", id) },
            ChallengeVote.serializer(),
        )
        return true
    }

    override suspend fun findAllSubmissions(): List<ChallengeSubmission> =
        query(SUBMISSIONS, "FOR doc IN $SUBMISSIONS SORT doc.createdAt DESC RETURN doc", null, ChallengeSubmission.serializer())

    override suspend fun findSubmission(id: String): ChallengeSubmission? =
        query(
            SUBMISSIONS,
            "FOR doc IN $SUBMISSIONS FILTER doc.id == @id RETURN doc",
            buildJsonObject { put("id", id) },
            ChallengeSubmission.serializer(),
        ).firstOrNull()

    override suspend fun findSubmissionsByChallenge(challengeId: String): List<ChallengeSubmission> =
        query(
            SUBMISSIONS,
            "FOR doc IN $SUBMISSIONS FILTER doc.challengeId == @challengeId SORT doc.createdAt DESC RETURN doc",
            buildJsonObject { put("challengeId", challengeId) },
            ChallengeSubmission.serializer(),
        )

    override suspend fun saveSubmission(submission: ChallengeSubmission) =
        save(SUBMISSIONS, submission.id, submission, ChallengeSubmission.serializer())

    override suspend fun deleteSubmission(id: String): Boolean {
        findSubmission(id) ?: return false
        query<ChallengeSubmission>(
            SUBMISSIONS,
            "FOR doc IN $SUBMISSIONS FILTER doc.id == @id REMOVE doc IN $SUBMISSIONS RETURN OLD",
            buildJsonObject { put("id", id) },
            ChallengeSubmission.serializer(),
        )
        query<ChallengeVote>(
            VOTES,
            "FOR doc IN $VOTES FILTER doc.submissionId == @id REMOVE doc IN $VOTES RETURN OLD",
            buildJsonObject { put("id", id) },
            ChallengeVote.serializer(),
        )
        return true
    }

    override suspend fun findVotesByChallenge(challengeId: String): List<ChallengeVote> =
        query(
            VOTES,
            "FOR doc IN $VOTES FILTER doc.challengeId == @challengeId RETURN doc",
            buildJsonObject { put("challengeId", challengeId) },
            ChallengeVote.serializer(),
        )

    override suspend fun findVote(challengeId: String, tokenHash: String): ChallengeVote? =
        query(
            VOTES,
            "FOR doc IN $VOTES FILTER doc.challengeId == @challengeId AND doc.deviceTokenHash == @hash RETURN doc",
            buildJsonObject {
                put("challengeId", challengeId)
                put("hash", tokenHash)
            },
            ChallengeVote.serializer(),
        ).firstOrNull()

    override suspend fun saveVote(vote: ChallengeVote) = save(VOTES, vote.id, vote, ChallengeVote.serializer())

    override suspend fun deleteVotesForSubmission(submissionId: String) {
        query<ChallengeVote>(
            VOTES,
            "FOR doc IN $VOTES FILTER doc.submissionId == @id REMOVE doc IN $VOTES RETURN OLD",
            buildJsonObject { put("id", submissionId) },
            ChallengeVote.serializer(),
        )
    }

    override suspend fun deleteVotesForChallenge(challengeId: String) {
        query<ChallengeVote>(
            VOTES,
            "FOR doc IN $VOTES FILTER doc.challengeId == @id REMOVE doc IN $VOTES RETURN OLD",
            buildJsonObject { put("id", challengeId) },
            ChallengeVote.serializer(),
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
        private val log = LoggerFactory.getLogger(ArangoChallengeRepository::class.java)
        private const val CHALLENGES = "challenges"
        private const val SUBMISSIONS = "challenge_submissions"
        private const val VOTES = "challenge_votes"

        suspend fun connect(config: ArangoConfig, client: HttpClient?): ArangoChallengeRepository? {
            if (!config.enabled || client == null) return null
            return try {
                val repo = ArangoChallengeRepository(config, client)
                repo.ensureSchema()
                log.info("Challenge repository connected to ArangoDB collections")
                repo
            } catch (e: Exception) {
                log.warn(
                    "ArangoDB challenges unavailable at {} ({}); falling back to in-memory",
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

        listOf(CHALLENGES, SUBMISSIONS, VOTES).forEach { collection ->
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
            "$dbUrl/_api/index?collection=$SUBMISSIONS",
            buildJsonObject {
                put("type", "persistent")
                put("unique", false)
                put("fields", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("challengeId")) })
            }.let { json.encodeToString(JsonObject.serializer(), it) },
        )
        postJson(
            "$dbUrl/_api/index?collection=$VOTES",
            buildJsonObject {
                put("type", "persistent")
                put("unique", false)
                put(
                    "fields",
                    buildJsonArray {
                        add(kotlinx.serialization.json.JsonPrimitive("challengeId"))
                        add(kotlinx.serialization.json.JsonPrimitive("deviceTokenHash"))
                    },
                )
            }.let { json.encodeToString(JsonObject.serializer(), it) },
        )
    }
}
