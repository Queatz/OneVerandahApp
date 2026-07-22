# One Verandah Mini Stories — Complete Feature Specification

## 1. Overview

**One Verandah Mini Stories** is a new content module inside the apartment complex app. It allows residents to discover, read, and collect interactive micro-stories. Key capabilities include:

- Bite-sized, multi-page stories with optional images and branching buttons.
- Collectable “Phrases” that users can save to a personal collection.
- Inline AI-powered word translation with server-side caching, contextual to the story.
- AI-assisted story writing (accessible via a password-protected editor).
- Readership analytics tracking who finished each page.
- Smooth page-flipping UI with large 9:16 photos.

---

## 2. UI Placement & Entry Points

### 2.1 Main Page Banner
- A single-story banner appears **above the main header text** on the app’s home screen.
- Displays the cover image, title, and a “Read now” call-to-action.
- Tapping the banner navigates directly to the **Mini Stories screen** (the grid/list of all stories).

### 2.2 Side Panel
- A dedicated **“Mini Stories”** item in the app’s navigation drawer. Tapping opens the Mini Stories screen.

### 2.3 App Bar Book Icon
- A book icon in the top app bar, always visible on primary screens.
- Tapping also opens the Mini Stories screen. A badge shows the count of unread stories (optional enhancement).

All entry points lead to the same **Mini Stories screen**.

---

## 3. Mini Stories Screen

This screen has two logical sections: **Phrase Collection Preview** (top) and **Story Grid** (remainder).

### 3.1 Phrase Collection Preview
- Horizontally scrollable row of collected phrase cards, with the **latest collected phrase** centred and slightly larger.
- Tapping the centred phrase (or a “View all” button) navigates to the **Phrases Screen**.
- If the user has no phrases, this area shows a friendly empty state: “Collect phrases while reading to see them here.”

### 3.2 Story Grid
- A responsive grid (2 columns on mobile, 3 on tablet) of all published stories.
- Each story card shows:
  - Cover photo (9:16 aspect ratio thumbnail)
  - Title
  - Author name
  - Synopsis (truncated to 2 lines)
  - Number of pages
  - “Read” button
- Tapping the card or “Read” opens the **Mini Story screen** for that story.
- Stories are sorted with newest first; a **search bar** at the top filters by title/author.

### 3.3 Write a Story (Pencil Icon)
- A floating pencil icon button is fixed at the bottom-right of the grid.
- Tapping it opens a **bottom sheet** asking for a **story creation password**.
  - Password input field and “Submit” button.
  - Incorrect password: subtle error shake and “Incorrect password” message; sheet remains open.
  - Correct password: bottom sheet dismisses, navigates to the **Story Editor** (see Section 6).
- Password is validated against a hardcoded value stored on the backend (environment variable). The client never sees the correct password.

---

## 4. Phrases & Phrases Screen

### 4.1 Collecting a Phrase
- Inside a story, the author can mark special text with `<phrase id="unique_phrase_id"></phrase>` tags.
- The app renders the tagged text as a **highlighted, tappable snippet** (e.g., with a glowing border and a “Collect” icon).
- Tapping the snippet:
  - Shows a toast “Phrase collected!” and adds it to the user’s collection.
  - If the phrase is already collected, show “Already in your collection” and do nothing (no duplicate).
  - Collection is stored per user, per phrase ID.
- Collection action is optimistic with immediate UI feedback; if the API call fails (e.g., network), revert and show an error.

### 4.2 Phrases Screen
- Accessed from the Mini Stories screen (latest phrase tap or “View all”).
- **List of collected phrases**, each showing:
  - The phrase text (styled same as in story)
  - The story title it came from (tappable to re-open that story)
  - A timestamp of when it was collected
  - A trash icon to **delete** the phrase.
- **Delete** action triggers a confirmation dialog: “Remove this phrase from your collection?”. On confirm, phrase is removed and list updated.
- Empty state: “No phrases collected yet. Explore stories and tap glowing phrases to start your collection.”
- Deletion is permanent; no undo required per spec, but an “Undo” snackbar could be added for UX (optional).

---

## 5. Mini Story Screen (Reading Experience)

### 5.1 Story Structure
A story consists of:
- Metadata: title, cover photo, author, synopsis.
- An ordered list of pages. Each page has:
  - `photo` (URL, optional)
  - `text` (Markdown with `<phrase>` support)
  - `buttons` array (optional). Each button: `{ label: string, targetBookId: string?, targetPageIndex: number }`

### 5.2 Page Navigation & Buttons
- The story starts at page 0 (first page).
- At the bottom of the page content, buttons are rendered in the order defined.
  - If the page has **one or more buttons**, tapping a button transitions to the corresponding `targetBookId?` and `targetPageIndex`.
  - If the page has **no buttons**, a default “Next page” button is shown that goes to the next sequential page (current index + 1). On the last page, the default button becomes “Finish” and leads to the end screen.
- Page transitions use a **page-flip animation** (curl effect) or a **flying-off animation** (slide + fade) as specified. The user can also swipe left/right to navigate sequentially, but only if the current page has no explicit buttons that would conflict; swiping is disabled on pages with multiple branching buttons to avoid accidental navigation. Swipe gesture still works for pages with the default next button.

### 5.3 Photo Display
- If a page has a photo, it appears at the top of the page with 24px rounded corners.
- Photos are loaded asynchronously; a spinner is shown during loading, then the image **fades in**.
- Aspect ratio is fixed at **9:16** (portrait). If the image intrinsic ratio differs, it is cropped to fit (object-fit: cover) within the 9:16 container.

### 5.4 Translation Help
- **Applicability:** Only available when the story’s language differs from the user’s selected app language. The story has a `language` field (ISO 639-1); user language is set in profile. If they match, no translation UI is shown.
- **Tokenized Words:** Story text is split into tokens (words/punctuation). Tokens not in the user’s language (based on dictionary heuristics or a language-detection library) are rendered with a subtle underline. Tapping a token opens a **translation popup**.
- **AI-Powered Translation:** The app sends `{ storyId, pageIndex, word, contextSentence }` to a backend endpoint.
  - The backend first checks a **translation cache** (see 5.5). If found, returns cached result.
  - Otherwise, it calls an AI translation service (e.g., OpenAI) with a prompt like: “Translate the word ‘X’ in the context: ‘…’ from [story language] to [user language]. Provide: translated word, part of speech, a concise explanation in user’s language, and an example sentence.”
  - Response structure: `{ translatedWord, partOfSpeech, explanation, example }`
  - The result is cached and returned.
- **Popup UI:** Shows translated word prominently, then part of speech, explanation, and example. A small “Report” button flags a bad translation (optional). Popup dismisses on tap outside.

### 5.5 Translation Cache
- Cache key: `{ storyId, word (lowercased, trimmed), contextSentenceHash }` to ensure context-sensitive caching. Words reused in the exact same sentence within the same story retrieve the same translation instantly.
- Cache is stored server-side in a database table `translation_cache`. It has a TTL of 30 days; after that, the entry is stale and will be re-fetched from AI the next time it’s requested.
- The cache is story-scoped, so the same word in a different story will be independently translated and cached.

### 5.6 End of Story
- After the final page, an “End of Story” card appears with:
  - “You finished the story!” message
  - Cover image of the story
  - “Stories you might like” section: **4 random other stories** presented as horizontal scrollable cards (cover + title). Tapping navigates to that story.

---

## 6. Writing Stories

Access to the story editor is restricted by a password. Once authenticated, the user enters a full-featured AI-assisted editor.

### 6.1 Access Flow
1. Tap pencil icon on Mini Stories screen.
2. Bottom sheet appears with “Enter Story Creator Password”.
3. Submit password → backend validates against `STORY_CREATION_PASSWORD` env var.
4. Success: navigate to Story Editor; failure: stay on sheet with error.

### 6.2 Story Editor – Main Details
Form fields:
- **Title** (required, text)
- **Cover Photo** (image picker, crop to 9:16 recommended). Upload to server, get URL.
- **Author** (pre-filled with current user’s display name, editable)
- **Synopsis** (textarea, max 300 chars)
- **Language** dropdown (the story’s language). Default to user’s selected app language.

### 6.3 Pages Editor
- List of pages, drag-to-reorder.
- Add page button.
- Each page:
  - **Photo** (optional): Upload/select image, same 9:16 crop.
  - **Text** editor: rich text area with an AI-assist button.
    - AI-Assisted Writing: Button “Ask AI” opens a prompt input. The user types a prompt like “Describe the character walking through the garden at sunset.” Backend calls AI to generate text, which is inserted at cursor position. The AI respects the story’s language.
    - Phrase insertion: A toolbar button “Insert Phrase” prompts for a phrase ID (alphanumeric, unique per story). It wraps the selected text or inserts `<phrase id="xxx">selected text</phrase>`. If no text selected, inserts placeholder tags. The editor preview renders these as highlighted blocks.
  - **Buttons**: Optional, can add multiple. Each button has:
    - Label (text)
    - Target page: dropdown of all page numbers (indices). Must point to an existing page; if a page is deleted, any buttons targeting it become invalid and are highlighted in red.

### 6.4 Preview & Publish
- “Preview” button shows the story exactly as a reader would see it, using the same animated page interface (without saving read receipts). Can navigate and test buttons.
- “Save Draft” stores story as unpublished.
- “Publish” sets story to `status: published` and it appears in the story grid for all residents.
- **Editing published stories:** The editor can reopen a published story (if they are the author) via a “My Stories” tab (accessed from pencil icon after password). Edits update the live story immediately; read receipts are preserved. A caution message warns about changing button targets.

### 6.5 Phrase ID Uniqueness & Story Integrity
- Phrase IDs must be unique within a story. The editor validates on publish/save.
- If a phrase references a nonexistent ID or duplicate, show error and prevent publishing.

---

## 7. Reading Analytics – “Who Read Up to the End of Each Page”

- On the **Mini Story screen** (reader view), a discreet “Readers” icon (👥) in the top bar opens an overlay.
- For each page, show avatars (or initials) of users who have a `read` event for that page. Only show users who have read **up to the end of that page** (i.e., they triggered the read receipt for that pageIndex). If a user only read page 0 and not page 1, they appear only for page 0.
- Privacy: Only display first name and last initial (or just first name) as set in the app profile. No full names unless permitted by building policy.
- Data is fetched from `GET /stories/{storyId}/readers` and grouped by page.

---

## 8. Backend Data Models

### 8.1 Story
| Field          | Type          | Notes |
|----------------|---------------|-------|
| id             | UUID          | PK |
| title          | string        | required |
| coverPhotoUrl  | string        | |
| author         | string        | |
| synopsis       | string        | |
| language       | string (ISO)  | |
| status         | enum(draft, published) | |
| pages          | JSON array    | ordered, each page: { photoUrl?, text, buttons? } |
| createdBy      | userId        | |
| createdAt, updatedAt | timestamp | |

### 8.2 Phrase (Collected)
| Field      | Type      | Notes |
|------------|-----------|-------|
| id         | UUID      | PK |
| userId     | userId FK | |
| phraseId   | string    | the id from the <phrase> tag |
| storyId    | UUID FK   | |
| collectedAt| timestamp | |

Unique constraint: (userId, phraseId) to prevent duplicates.

### 8.3 TranslationCache
| Field            | Type    | Notes |
|------------------|---------|-------|
| id               | UUID    | PK |
| storyId          | UUID FK | |
| word             | string  | lowercase, trimmed |
| contextSentenceHash | string | SHA256 of trimmed sentence |
| translatedWord   | string  | |
| partOfSpeech     | string  | |
| explanation      | string  | |
| example          | string  | |
| createdAt        | timestamp | for TTL (expire after 30d) |

Unique index on (storyId, word, contextSentenceHash).

### 8.4 ReadReceipt
| Field    | Type      | Notes |
|----------|-----------|-------|
| id       | UUID      | PK |
| userId   | userId FK | |
| storyId  | UUID FK   | |
| pageIndex| integer   | |
| readAt   | timestamp | |

Index on (storyId, pageIndex) for reader queries.

### 8.5 User (already exists)
Profile includes `selectedLanguage` (e.g., “en”, “es”) for translation features.

---

## 9. API Endpoints

### 9.1 Stories
- `GET /stories?status=published&limit=20&offset=0` – list stories with pagination.
- `GET /stories/{id}` – full story with pages, including phrase IDs (but not user’s collection status).
- `GET /stories/{id}/user-phrases` – returns array of phraseIds collected by current user for this story, used to mark already-collected phrases in the UI.
- `POST /stories/{id}/collect-phrase` – body `{ phraseId }`, adds to collection. Returns 409 if duplicate.
- `DELETE /phrases/{phraseId}` – remove collected phrase.
- `GET /phrases` – all collected phrases for user, with story title and timestamp.
- `POST /stories/{storyId}/read` – body `{ pageIndex }`, records read receipt. Idempotent per user+page (upsert latest timestamp).
- `GET /stories/{storyId}/readers` – returns object mapping pageIndex to array of `{ userId, firstName, lastNameInitial, avatarUrl? }`.

### 9.2 Translation
- `POST /translate` – body `{ storyId, word, contextSentence }`. Returns translation object; uses cache.

### 9.3 Story Writing
- `POST /auth/create-story-password` – body `{ password }`. Validates against server secret, returns a session token (JWT) valid for 30 minutes for story creation/editing.
- `POST /stories` (requires token) – create a draft story. Body includes all fields + pages.
- `PUT /stories/{id}` (requires token, and createdBy match) – update story (draft or published).
- `POST /stories/{id}/publish` – changes status to published.
- `POST /ai/write-story-text` – body `{ prompt, language }`. Returns generated text.
- `GET /my-stories` (requires token) – returns stories created by authenticated user.

### 9.4 Media
- `POST /upload` – multipart image upload, returns URL.

All endpoints follow standard REST conventions, use JWT for normal user auth, and require the story-writing token for creation/editing.

---

## 10. Edge Cases & Loose Ends Tied

- **Empty stories**: Not allowed; must have at least one page to publish.
- **Large stories**: Paginate story grid; stories with many pages load all pages on open but only render the visible page + next for performance.
- **Broken images**: Fallback placeholder with story title initials.
- **Translation for unsupported languages**: If AI cannot translate, return a generic “Translation unavailable” message. Cache that status for 1 day.
- **Multiple users reading simultaneously**: No conflict; read receipts are per user.
- **Deleted phrase in story**: If a story is edited and a phrase ID is removed, collected phrases referencing that ID remain in user collections but display “Original phrase no longer available” and the text is not linked. The story phrase count is unaffected.
- **Password leakage**: Hardcoded password is never sent to client in source; only validated server-side. The client receives a temporary token after successful submission. That token expires, and the password input is not stored.
- **Phrase collection limit**: Optional cap of 500 phrases per user; when reached, show “Collection full, delete some to collect new ones.” (Configurable, no limit by default.)
- **Read receipts for branching paths**: If a user never reaches a certain page due to branching, they simply won’t show in that page’s reader list.
- **Page-flipping conflict with branching buttons**: Swipe is disabled if the page has >1 explicit button. This prevents accidentally skipping a branch. The default next button page still allows swipe.

---

## 11. How to Write a Story — Step-by-Step (Based on the Features Above)

1. **Access Editor:** Tap the pencil icon on the Mini Stories screen → bottom sheet prompts for password. Enter the correct backend-validated password to enter the Story Editor.
2. **Set Main Details:** Fill in Title, upload a Cover Photo (auto-cropped to 9:16), set Author (defaults to you), write a Synopsis, and choose the Story’s Language.
3. **Add Pages:** Start with a blank first page. For each page:
   - Optionally add a Photo (9:16, will fade in when read).
   - Write the story text using the rich-text editor. Use the “Ask AI” button to generate prose based on a prompt, maintaining the chosen language.
   - If you want a piece of text to be collectable, highlight it and click “Insert Phrase”, assign a unique ID (e.g., “wisdom1”), and the text will be wrapped in `<phrase id="wisdom1">…</phrase>`. Readers can tap this to collect it.
   - Add Buttons to branch the narrative. Enter a Label (e.g., “Open the door”) and choose a Target Page from the dropdown. You can add multiple buttons for divergent paths. If you don’t add any buttons, the story will show a “Next page” button automatically that follows linear page order.
4. **Reorder & Edit Pages:** Drag pages in the sidebar to reorder. Ensure button targets remain valid. The editor will warn of broken targets.
5. **Preview:** Tap “Preview” to read the story exactly as a resident would, testing translations, phrase collections, and button flows. No read receipts are recorded.
6. **Save Draft or Publish:** Save as draft to continue later, or Publish to make it instantly available in the community story grid. Once published, you can edit it again; readers will see the latest version. Read progress is preserved.
7. **Manage Your Stories:** Re-enter the editor via password to see “My Stories”, where you can edit, unpublish, or delete your creations.

