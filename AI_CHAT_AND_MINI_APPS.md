# AI Chat and Mini Apps

This guide explains how One Verandah's AI chat works and how to add a new mini app safely. It is intended to be usable by both maintainers and AI coding agents.

## Core principles

- All AI providers in this feature are Qwen/DashScope. Do not introduce a second AI provider.
- Qwen must return structured JSON, never free-form wire responses or Markdown wrappers.
- The user's active language is sent with every request and the assistant is instructed to reply in that language.
- Mini apps are part of an AI `ChatMessage`; they are not separate pages or arbitrary model-generated HTML.
- Trusted application data is resolved on the server. The model may select a trusted ID, but it must not invent catalog or resident details.
- Chat history is persisted in ArangoDB in production.
- One message currently supports zero or one mini app. Supporting several mini apps requires an intentional contract change.

## Architecture at a glance

```text
Home search or /chat ChatInput
        |
        v
ChatScreen -> ChatApi -> POST /api/chat
                           |
                           v
                       ChatService
                +----------+-----------+
                |          |           |
                v          v           v
          ChatRepository  trusted    Qwen chat
          (ArangoDB)      catalogs    JSON reply
                |          |           |
                +----------+-----------+
                           |
                           v
                 ChatMessage + miniApp
                           |
                           v
                    ChatScreen renderer
```

The shared contract lives in `core`, orchestration and AI calls live in `server`, and rendering/API access live in `app/web`.

## Important files

| Area | File | Responsibility |
| --- | --- | --- |
| Shared API | `core/src/commonMain/kotlin/apap/oneverandah/api/ChatApiModels.kt` | Requests, responses, messages, and the sealed `ChatMiniApp` contract |
| Trusted facts | `core/src/commonMain/kotlin/apap/oneverandah/data/FactsCatalog.kt` | Localized apartment fact catalog |
| Chat orchestration | `server/src/main/kotlin/apap/oneverandah/chat/ChatService.kt` | Persists messages, gathers trusted context, calls AI, validates selections, and builds mini apps |
| Qwen chat | `server/src/main/kotlin/apap/oneverandah/chat/QwenChatClient.kt` | System prompt, JSON request, wire schema, and structured reply parsing |
| Speech | `server/src/main/kotlin/apap/oneverandah/chat/QwenSpeechClient.kt` | Qwen voice design and CosyVoice synthesis |
| Composition | `server/src/main/kotlin/apap/oneverandah/chat/ChatSystem.kt` | Creates repositories, clients, services, and resident directory |
| Persistence | `server/src/main/kotlin/apap/oneverandah/chat/ArangoChatRepository.kt` | Stores and retrieves chat messages from ArangoDB |
| HTTP routes | `server/src/main/kotlin/apap/oneverandah/routes/ChatRoutes.kt` | Send, history, and speech endpoints |
| Browser API | `app/web/src/jsMain/kotlin/apap/oneverandah/api/ChatApi.kt` | Calls the backend chat endpoints |
| Chat UI | `app/web/src/jsMain/kotlin/apap/oneverandah/screens/ChatScreen.kt` | Chat state, message rendering, and current mini-app components |
| Styling | `app/web/src/jsMain/kotlin/apap/oneverandah/Styles.kt` | Chat and mini-app CSS |
| Localized UI copy | `app/web/src/jsMain/kotlin/apap/oneverandah/i18n/Localization.kt` | Labels, errors, suggestions, and controls |

## Shared message and mini-app contract

`ChatMessage` contains normal chat content plus an optional polymorphic mini app:

```kotlin
@Serializable
data class ChatMessage(
    val id: String,
    val sender: ChatSender,
    val text: String,
    val createdAt: Long,
    val miniApp: ChatMiniApp? = null,
)

@Serializable
sealed interface ChatMiniApp
```

Every concrete mini app:

1. Is declared in the shared `core` module.
2. Is `@Serializable`.
3. Implements `ChatMiniApp`.
4. Has a stable `@SerialName`, normally in `snake_case`.
5. Contains only the data the browser needs to render or activate the mini app.

For example:

```kotlin
@Serializable
@SerialName("fact")
data class FactMiniApp(
    val factId: String,
    val title: String,
    val body: String,
    val icon: String,
) : ChatMiniApp
```

Kotlin serialization adds the subtype discriminator to JSON automatically. Because `miniApp` is nested in the persisted `ChatMessage`, ArangoDB stores the same serializable shape returned by the API.

## Request lifecycle

### 1. Browser submission

`ChatScreen` immediately adds a temporary user message for responsive UI, then calls:

```http
POST /api/chat
Content-Type: application/json

{
  "conversationId": "optional-existing-id",
  "message": "How do I say this in Vietnamese?",
  "languageTag": "en"
}
```

The browser gets the backend base URL through `ImageApi.apiBaseUrl`; production requests must go to `https://api.oneverandah.app`, not the frontend origin.

The first request omits `conversationId`. The server generates one, and the browser reuses it for later turns in the same screen session.

### 2. Server orchestration

`ChatService.send` performs the authoritative workflow:

1. Normalize the request and reject a blank message.
2. Generate or reuse the conversation ID.
3. Persist the user's message.
4. Resolve the active `Language` from `languageTag`.
5. Match a resident deterministically by a case-insensitive name or address mention.
6. Give Qwen recent history, trusted resident context, and fact summaries.
7. Parse Qwen's structured reply.
8. Resolve any model-selected trusted ID against the server catalog.
9. Build at most one mini app.
10. Persist and return the AI message.

The mini-app precedence is important:

```text
matched resident > generated language study > valid fact ID > no mini app
```

If Qwen returns both language-study data and a fact ID, language study wins. If the user's message directly matches a resident, the resident card wins over all model selections.

### 3. Browser rendering

`ChatMessageView` always renders the text bubble, then exhaustively dispatches the optional mini app:

```kotlin
when (val miniApp = message.miniApp) {
    is LanguageStudyMiniApp -> LanguageStudyCard(miniApp, strings)
    is ResidentCardMiniApp -> ResidentCard(miniApp, strings) { /* open details */ }
    is FactMiniApp -> FactCard(miniApp)
    null -> Unit
}
```

Adding a sealed subtype will force this dispatch to be updated at compile time. Keep the renderer below the AI text so the textual answer and interactive result remain one response.

## Qwen chat integration

`DashScopeChatAiClient` calls the OpenAI-compatible DashScope chat-completions endpoint with:

- Model: `qwen-plus` by default.
- `response_format.type`: `json_object`.
- Temperature: `0.4`.
- A system message containing the active language, response schema, mini-app instructions, and trusted context.
- Up to the last 20 persisted messages, mapped to Qwen `user` and `assistant` roles.

The current model-level reply schema is:

```json
{
  "text": "Helpful answer in the user's active language",
  "languageStudy": null,
  "factId": null
}
```

Language study expands `languageStudy` to:

```json
{
  "sentence": "A sentence in the target language",
  "targetLanguageTag": "vi",
  "tokens": [
    {
      "text": "token",
      "definition": "Definition in the user's active language",
      "contextualMeaning": "Meaning here in the user's active language"
    }
  ]
}
```

`QwenStructuredReplyParser` tolerates an accidental JSON code fence, deserializes the private wire models, rejects blank response text, and maps the wire result to server-domain models. HTTP failures and missing structured content become `QwenChatException` errors.

### Language behavior

The browser sends `Localization.language.tag` with every message. The system prompt instructs Qwen to:

- Reply in the user's active language.
- Recognize language-learning intent semantically in any language.
- Use both the latest message and conversation context.
- Produce complete token definitions in the active language.
- Avoid relying on an English keyword classifier.

Do not add language-specific intent regexes in `ChatService`. If a multilingual intent is missed, improve the structured prompt and add multilingual request-payload tests.

### Trust boundary

Treat Qwen output as untrusted input.

- **Resident card:** Qwen does not choose the resident. `ResidentDirectory` matches a resident from `Catalog.services`; `ChatService` supplies all card fields.
- **Fact card:** Qwen sees fact IDs, localized titles, and keywords, and returns only `factId`. `ChatService` looks that ID up in `FactsCatalog` and supplies the canonical localized title/body/icon. An unknown ID produces no fact card.
- **Language study:** The instructional sentence and definitions are generative content, so Qwen supplies them. Keep parsing strict and normalize the target language tag before sending it to speech.
- **Browser actions:** Resolve IDs against browser-side trusted catalogs before opening privileged or detailed UI. The resident card does this before opening `ProviderDetailsDialog`.

Never ask Qwen to emit HTML, CSS, executable code, URLs for privileged actions, or complete trusted domain objects when a catalog ID will do.

## Current mini apps

### Language Study

**Trigger:** Qwen detects a request to learn, practise, understand, translate, write, speak, or discuss a topic in another language, including homework help.

**Data ownership:** Qwen generates the sentence, target language tag, ordered tokens, definitions, and contextual meanings.

**UI behavior:**

- The sentence is rendered as tappable token buttons.
- Selecting a token reveals its definition and contextual meaning.
- Sentence and token audio are fetched concurrently as soon as the card mounts.
- Tapping a token plays its audio; the sentence button plays the complete sentence.

### Resident Card

**Trigger:** `ResidentDirectory` finds a catalog resident whose name or block address appears in the user's latest message.

**Data ownership:** All displayed data comes from the matched `ServiceProvider`; the model only receives context so its text can acknowledge the match accurately.

**UI behavior:** Clicking the card resolves its `residentId` in `Catalog.services` and animates the existing `ProviderDetailsDialog` up as a bottom sheet without leaving chat.

### Fact

**Trigger:** Qwen selects a listed fact ID when that catalog fact directly helps the user or the user asks for an apartment fact.

**Data ownership:** The model chooses only the ID. Localized title/body and icon come from `FactsCatalog` after server validation.

**UI behavior:** A themed informational card is rendered below the answer.

## Speech integration

Language Study uses the same chat backend rather than calling Qwen from the browser:

```http
POST /api/chat/speech
Content-Type: application/json

{
  "text": "xin chào",
  "languageTag": "vi"
}
```

The server returns:

```json
{
  "audioBase64": "...",
  "contentType": "audio/mpeg"
}
```

`ChatApi.speechDataUrl` converts this response to a browser data URL.

Speech implementation rules:

- Use `cosyvoice-v3.5-plus`.
- Keep voice design and synthesis in the same DashScope Beijing region.
- Voice design uses the DashScope customization HTTP endpoint.
- Synthesis uses the official DashScope Java SDK/WebSocket protocol.
- The designed voice ID is cached by `SpeechService`.
- Voice initialization is launched asynchronously with the Ktor application, so server readiness is not blocked.
- If startup voice design fails, the server stays available and speech retries initialization on demand.

## HTTP API

| Method | Route | Purpose |
| --- | --- | --- |
| `POST` | `/api/chat` | Normalize a user message, generate an AI response, persist both, and return the AI message |
| `GET` | `/api/chat/{conversationId}` | Return persisted messages ordered by creation time |
| `POST` | `/api/chat/speech` | Synthesize speech and return Base64 audio |

The send response is:

```json
{
  "conversationId": "conversation-id",
  "message": {
    "id": "message-id",
    "sender": "AI",
    "text": "Response text",
    "createdAt": 1752870000000,
    "miniApp": null
  }
}
```

A new purely visual mini app normally does **not** need a new route. Add a route only when the mini app needs a separate server-side operation, as Language Study does for speech.

## Persistence and startup

`ChatSystem.create` attempts to connect `ArangoChatRepository` and prepares the `chat_messages` collection plus its indexes.

- Production fails fast if ArangoDB is unavailable.
- Development may fall back to `InMemoryChatRepository`.
- Messages are stored per `conversationId` and read in ascending `createdAt` order.
- `ChatSystem` owns and closes the shared Ktor `HttpClient` at application shutdown.

Adding fields or subtypes changes persisted JSON. Consider how already-stored messages will deserialize before removing or renaming a subtype or required field.

## Configuration

The relevant configuration is resolved in `AppConfig`:

| Variable | Default/behavior |
| --- | --- |
| `QWEN_API_KEY` | Required to enable Qwen chat and speech |
| `QWEN_CHAT_ENDPOINT` | Defaults to the DashScope OpenAI-compatible chat-completions endpoint |
| `QWEN_CHAT_MODEL` | Defaults to `qwen-plus` |
| `QWEN_VOICE_DESIGN_MODEL` | Defaults to `voice-enrollment` |
| `QWEN_ASR_WORKSPACE_ID` | Beijing Model Studio workspace ID; required to enable realtime voice input |
| `QWEN_ASR_ENDPOINT` | Optional full workspace-scoped realtime endpoint override for another region |
| `QWEN_ASR_MODEL` | Defaults to `qwen3-asr-flash-realtime` |

CosyVoice synthesis and voice-design endpoints are intentionally fixed to matching Beijing-region DashScope endpoints. Do not restore old speech endpoint overrides that can mix regions or use an incompatible multimodal HTTP route.
Realtime ASR uses `wss://{WorkspaceId}.cn-beijing.maas.aliyuncs.com/api-ws/v1/realtime` with the model supplied as a query parameter. The backend adds the Qwen API key and relays WebSocket events so credentials are never sent to the browser.

Hold-to-speak performance notes:

- The browser warms mic + ASR WebSocket after ~150ms of hold; listening UI starts at ~350ms so short clicks never request the microphone.
- The browser keeps the ASR socket warm for 60s between holds and sends raw PCM16 binary frames.
- The server converts binary PCM to Qwen's base64 JSON `input_audio_buffer.append` events and keeps one upstream Qwen socket warm for 60s.

## How to add a new mini app

Use this sequence. Keep the change vertical and minimal: contract, model decision, trusted server enrichment, renderer, and tests.

### 1. Define the behavior and ownership

Before editing, answer:

- What user intent triggers the mini app?
- Is the content generative, catalog-backed, or deterministic?
- Which fields may Qwen generate?
- Which fields must come from trusted server data?
- Does it conflict with another mini app, and where does it sit in the precedence order?
- Is one mini app per message sufficient?
- Does it need a new side-effect API, or only rendering?

If an existing abstraction name no longer describes its expanded responsibility, rename/refactor it first before adding behavior.

### 2. Add the shared contract

In `ChatApiModels.kt`, add a serializable subtype with a stable discriminator:

```kotlin
@Serializable
@SerialName("maintenance_tip")
data class MaintenanceTipMiniApp(
    val tipId: String,
    val title: String,
    val steps: List<String>,
) : ChatMiniApp
```

Prefer IDs and small display payloads. Do not expose secrets, internal database keys, or unnecessary domain fields.

Add a `core` serialization round-trip test that asserts both the discriminator and fields.

### 3. Add server-domain generation fields only if Qwen must decide

If Qwen selects or generates the mini app:

1. Extend `GeneratedChatReply` with the smallest possible selection/generation result.
2. Extend the private Qwen wire model in `QwenChatClient.kt`.
3. Map it in `QwenStructuredReplyParser`.
4. Update the JSON schema and precise trigger instructions in `systemPrompt`.
5. Include only the trusted context Qwen needs.

For catalog-backed content, prefer this pattern:

```json
{
  "text": "...",
  "maintenanceTipId": "trusted-id-or-null"
}
```

Do not ask Qwen to repeat the canonical title, steps, price, resident data, permissions, or other trusted fields.

### 4. Resolve and build the mini app in `ChatService`

Validate any selected ID against its source catalog, localize canonical data with the request's active language, and construct the API mini app in `ChatService.send`.

Update the `when`/precedence logic deliberately. Never let an unknown model-selected ID escape into a card.

If intent is deterministic and based on private application state, perform it in the server rather than asking Qwen. Resident matching is the reference pattern.

### 5. Render it in the browser

In `ChatScreen.kt`:

1. Add the new branch to `ChatMessageView`'s exhaustive `when`.
2. Implement a focused `@Composable` card.
3. Add themed styles to `Styles.kt`, including light/dark compatibility and responsive sizing.
4. Add all visible copy and accessibility labels to `Localization.kt` in every supported language.
5. Resolve catalog IDs again before opening detailed or privileged UI.

Reuse existing components and interaction patterns. Mini apps should visually belong to the AI message and should not navigate away unless that is explicitly part of the experience.

### 6. Add a route only when required

If interaction requires server work:

1. Add shared request/response models.
2. Normalize and validate the request on the server.
3. Keep provider credentials server-side.
4. Add the route under `/api/chat/...`.
5. Add the browser method to `ChatApi` using `chatApiUrl`.
6. Add route and client URL tests.

Do not call Qwen directly from browser code.

### 7. Test the vertical slice

At minimum, cover:

- **Shared contract:** subtype serialization/deserialization and discriminator.
- **Qwen parser:** valid selection/generation, null selection, malformed structure, and blank text where relevant.
- **Prompt payload:** exact schema/trigger guidance, multilingual examples when intent is language-dependent, active language, and trusted context.
- **Service:** positive trigger, no-trigger case, invalid/invented ID, localization, persistence, and mini-app precedence.
- **Route:** response serialization, input validation, and failures.
- **Browser:** API URL construction and any testable state/interaction logic.
- **Build:** production Kotlin/JS compilation to catch exhaustive-rendering and serialization integration errors.

Relevant existing test files include:

- `core/src/commonTest/kotlin/apap/oneverandah/api/ChatApiModelsTest.kt`
- `server/src/test/kotlin/apap/oneverandah/chat/ChatServiceTest.kt`
- `server/src/test/kotlin/apap/oneverandah/chat/ChatRoutesTest.kt`
- `server/src/test/kotlin/apap/oneverandah/chat/QwenSpeechClientTest.kt`
- `app/web/src/jsTest/kotlin/apap/oneverandah/api/ChatApiTest.kt`

Typical verification commands are:

```bash
./gradlew :core:jvmTest :server:test
./gradlew :app:web:jsBrowserProductionWebpack
```

Run browser tests as well when a compatible browser is installed:

```bash
./gradlew :app:web:jsBrowserTest
```

## AI coding-agent checklist

Before considering a new mini app complete, verify all of the following:

- [ ] The trigger and non-trigger behavior are explicit.
- [ ] Generative and trusted fields have a documented boundary.
- [ ] The `ChatMiniApp` subtype has `@Serializable` and a stable `@SerialName`.
- [ ] Shared serialization tests cover the subtype.
- [ ] The Qwen wire schema and parser changed only if the model must decide or generate content.
- [ ] The system prompt requires one valid JSON object and describes the new field precisely.
- [ ] Intent instructions work semantically in all supported user languages.
- [ ] Catalog IDs are validated and canonical fields are filled by the server.
- [ ] Mini-app precedence is intentional and tested.
- [ ] `ChatMessageView` renders the new subtype.
- [ ] Styling supports light/dark themes and narrow screens.
- [ ] Visible copy and accessibility labels are localized.
- [ ] No Qwen credential or direct AI call was added to browser code.
- [ ] Persistence/deserialization impact was considered.
- [ ] Relevant shared, server, route, browser, and production web build checks pass.

## Common mistakes

- Returning prose around the Qwen JSON object.
- Adding an English-only keyword regex for multilingual intent.
- Trusting model-generated resident, fact, service, price, or permission data.
- Adding a shared subtype but forgetting the exhaustive browser renderer.
- Allowing several model selections without defining precedence.
- Calling the frontend domain instead of `ImageApi.apiBaseUrl` for API traffic.
- Calling Qwen from the browser and exposing `QWEN_API_KEY`.
- Mixing international and Beijing DashScope endpoints for speech.
- Blocking Ktor startup while designing the voice.
- Adding a new route for data already present in `ChatMessage.miniApp`.
- Testing only the prompt or only the UI instead of the complete vertical slice.

## When the current shape is not enough

Make an explicit design change rather than working around these constraints:

- To render multiple mini apps in one answer, replace the nullable `miniApp` with a collection and update serialization, persistence, precedence, and rendering together.
- To resume conversations across browser sessions, add a durable client-side conversation index and use the existing history route.
- To support model tool calls instead of one-pass JSON selection, design a server-side tool execution loop with strict schemas, authorization, timeouts, and bounded iterations. Do not execute arbitrary model output.
- To stream responses, introduce a streaming contract and incremental UI state; the current system intentionally waits for one structured response.