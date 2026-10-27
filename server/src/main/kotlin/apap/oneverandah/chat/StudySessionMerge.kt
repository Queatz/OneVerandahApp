package apap.oneverandah.chat

import apap.oneverandah.api.StudyPlayScenario
import apap.oneverandah.api.StudyPlayChatMessage
import apap.oneverandah.api.StudySentence
import apap.oneverandah.api.StudySessionResponse
import apap.oneverandah.api.StudyToken
import apap.oneverandah.api.StudyTokenStatus

object StudySessionMerge {
    fun mergeClientSave(
        stored: SavedStudySessionRecord,
        incoming: SavedStudySessionRecord,
    ): SavedStudySessionRecord {
        val sameLanguage = stored.languageTag == incoming.languageTag
        val sameBuildContext = sameLanguage &&
                stored.text == incoming.text &&
                stored.textLanguageTag == incoming.textLanguageTag
        val session = if (sameBuildContext) {
            mergeSession(stored.session, incoming.session)
        } else if (sameLanguage) {
            incoming.session.copy(examScene = stored.session.examScene ?: incoming.session.examScene)
        } else {
            incoming.session
        }
        return incoming.copy(
            id = stored.id,
            deviceTokenHash = stored.deviceTokenHash,
            sourceMessageId = stored.sourceMessageId ?: incoming.sourceMessageId,
            createdAt = stored.createdAt,
            name = stored.name.ifBlank { incoming.name },
            examGrade = if (sameLanguage) stored.examGrade ?: incoming.examGrade else incoming.examGrade,
            visitorExamGrades = stored.visitorExamGrades,
            build = if (sameBuildContext) stored.build ?: incoming.build else incoming.build,
            buildRequest = if (sameBuildContext) stored.buildRequest
                ?: incoming.buildRequest else incoming.buildRequest,
            session = session,
        )
    }

    private fun mergeSession(
        stored: StudySessionResponse,
        incoming: StudySessionResponse,
    ): StudySessionResponse {
        val incomingScenarios = incoming.playScenarios.associateBy { it.id }
        val scenarios = if (stored.playScenarios.isEmpty()) {
            incoming.playScenarios
        } else {
            stored.playScenarios.map { scenario ->
                val clientScenario = incomingScenarios[scenario.id]
                scenario.copy(
                    imageId = preferServer(scenario.imageId, clientScenario?.imageId),
                    imageUrl = preferServer(scenario.imageUrl, clientScenario?.imageUrl),
                    messages = mergeMessages(scenario.messages, clientScenario?.messages.orEmpty()),
                )
            }
        }
        val sentences = incoming.sentences.mapIndexed { sentenceIndex, sentence ->
            val serverSentence = stored.sentences.getOrNull(sentenceIndex)
                ?.takeIf { it.text == sentence.text }
                ?: return@mapIndexed sentence
            mergeSentence(serverSentence, sentence)
        } + stored.sentences.drop(incoming.sentences.size)

        return incoming.copy(
            sentences = sentences,
            characters = if (stored.characters.isEmpty()) {
                incoming.characters
            } else {
                val clientCharacters = incoming.characters.associateBy { it.id }
                stored.characters.map { character ->
                    val clientCharacter = clientCharacters[character.id]
                    character.copy(
                        imageId = preferServer(character.imageId, clientCharacter?.imageId),
                        imageUrl = preferServer(character.imageUrl, clientCharacter?.imageUrl),
                    )
                }
            },
            playScenarios = scenarios,
            practiceSentences = incoming.practiceSentences,
            examScene = stored.examScene ?: incoming.examScene,
        )
    }

    private fun mergeSentence(server: StudySentence, client: StudySentence): StudySentence {
        val tokens = client.tokens.mapIndexed { tokenIndex, token ->
            val serverToken = server.tokens.getOrNull(tokenIndex)
                ?.takeIf { it.text == token.text }
                ?: return@mapIndexed token
            mergeToken(serverToken, token)
        } + server.tokens.drop(client.tokens.size)
        return client.copy(
            tokens = tokens,
            imageId = preferServer(server.imageId, client.imageId),
            imageUrl = preferServer(server.imageUrl, client.imageUrl),
        )
    }

    private fun mergeToken(server: StudyToken, client: StudyToken): StudyToken {
        val clientCompletedPendingToken = server.status == StudyTokenStatus.PENDING &&
                client.status != StudyTokenStatus.PENDING
        return if (clientCompletedPendingToken) {
            client.copy(
                imageId = preferServer(server.imageId, client.imageId),
                imageUrl = preferServer(server.imageUrl, client.imageUrl),
            )
        } else {
            client.copy(
                status = server.status,
                literalMeaning = server.literalMeaning,
                register = server.register,
                contextualMeaning = server.contextualMeaning,
                nearSynonyms = server.nearSynonyms,
                exampleSentences = server.exampleSentences,
                imageId = preferServer(server.imageId, client.imageId),
                imageUrl = preferServer(server.imageUrl, client.imageUrl),
            )
        }
    }

    private fun mergeMessages(
        server: List<StudyPlayChatMessage>,
        client: List<StudyPlayChatMessage>,
    ) =
        (if (client.size > server.size) client else server).mapIndexed { index, message ->
            val serverMessage = server.getOrNull(index)
                ?.takeIf { it.speaker == message.speaker && it.text == message.text }
                ?: return@mapIndexed message
            message.copy(
                imageId = preferServer(serverMessage.imageId, message.imageId),
                imageUrl = preferServer(serverMessage.imageUrl, message.imageUrl),
            )
        }

    private fun preferServer(server: String?, client: String?): String? =
        server?.trim()?.takeIf { it.isNotEmpty() } ?: client
}
