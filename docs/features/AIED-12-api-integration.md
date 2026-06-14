# AIED-12 — OpenRouter API integration (replace the mock)

| | |
| --- | --- |
| **Priority** | 🔴 Must |
| **Milestone** | M1 (MVP) |
| **Status** | **Partial — backend implemented; UI wiring pending** |
| **Depends on** | [AIED-01](AIED-01-ai-editing-panel.md) (panel + apply pipeline), [AIED-02](AIED-02-api-key-storage.md) (API key) |
| **Effort** | M |

## Problem / motivation

[AIED-01](AIED-01-ai-editing-panel.md) ships the panel and apply pipeline against a **mock** that echoes
the input. This story replaces that mock with a real call to an LLM so the panel actually rewrites field
content from the user's instruction. The fork calls Claude **via [OpenRouter](https://openrouter.ai)**
(`anthropic/claude-sonnet-4` by default) rather than the Anthropic API directly, so the provider/model can
be swapped without code changes.

## User story

> As a reviewer, when I describe what's wrong and tap Apply, I want the real AI to rewrite the selected
> text (or whole field) using my stored API key, so the card is actually improved.

## Scope

**In:**
- Implement the rewrite function behind AIED-01's interface with an **OkHttp POST to OpenRouter**
  (`https://openrouter.ai/api/v1/chat/completions`), sending the field input + the user's instruction,
  running on `Dispatchers.IO`.
- Read the API key from AIED-02 secure storage; if missing, prompt the user to set it (no call attempted).
- **Loading + error states:** show progress during the call; surface network/API errors readably; leave the field unchanged on failure.

**Out:** streaming responses; multi-turn conversation; model/params settings UI (could be a follow-up).

## Implementation details

**Done (backend, brought in from the `copilot/open-router-integration-setup` branch):**
- `AnkiDroid/src/main/java/com/ichi2/anki/noteeditor/ai/OpenRouterNoteEditorRewriter.kt`
  - `NoteEditorRewriter` `fun interface` — `suspend fun rewrite(input, instruction): String` (the seam AIED-01 calls; mock-swappable).
  - `NoteEditorApiKeyProvider` `fun interface` — supplies the key (to be backed by AIED-02's `OpenRouterApiKeyStore`).
  - `OpenRouterNoteEditorRewriter` — OkHttp POST to OpenRouter on `Dispatchers.IO`, default model `anthropic/claude-sonnet-4`, system prompt instructing "return only the rewritten field content".
  - Typed errors: `MissingApiKeyException`, `OpenRouterApiException(statusCode)`, `InvalidOpenRouterResponseException`, `OpenRouterNetworkException`.
- `AnkiDroid/src/test/java/com/ichi2/anki/noteeditor/ai/OpenRouterNoteEditorRewriterTest.kt` — unit tests (request body, response/error parsing). Passing.

**Pending (UI wiring — blocked on AIED-01):**
- Construct the rewriter with a `NoteEditorApiKeyProvider` reading AIED-02's `OpenRouterApiKeyStore`.
- Replace the AIED-01 mock with this rewriter at the apply call site; add loading/error UI and the missing-key prompt.

## Acceptance criteria

- [x] A rewrite implementation performs a real round-trip to the LLM (OpenRouter) and returns the rewritten text. *(backend: `OpenRouterNoteEditorRewriter`)*
- [x] Network/API errors are represented as readable, typed exceptions. *(`OpenRouter*Exception` types)*
- [x] The rewrite sits behind AIED-01's interface so the mock is swappable without UI changes. *(`NoteEditorRewriter`)*
- [ ] Tapping Apply in the panel runs the real call and writes the result into the field (no app switch). *(blocked on AIED-01 wiring)*
- [ ] The stored AIED-02 key is used; when no key is set, the user is told to set one and no call is made. *(provider exists; wiring pending)*

## Technical notes

OkHttp + coroutines are already declared deps; the implementation runs inside
`withContext(Dispatchers.IO)`. The endpoint is OpenRouter's OpenAI-compatible
`/api/v1/chat/completions` with `Authorization: Bearer <key>`. Response parsing reads
`choices[0].message.content`. INTERNET permission already present.

## Open questions

- Default model is `anthropic/claude-sonnet-4`; expose a model/params settings UI later?
- `max_tokens` / temperature defaults (not currently sent — relies on OpenRouter defaults).
- Timeout/retry behavior on flaky networks.
