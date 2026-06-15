# Feature Roadmap — anki-viktor AI Editor

Product backlog for the AI note-editing capability in this fork. Each feature has its own spec in this
folder using a shared scaffold (problem → user story → scope → acceptance criteria → dependencies →
effort → open questions). Use these as the basis for creating and prioritizing implementation tasks.

Source of truth for rationale: [`../ai-editor-decision.md`](../ai-editor-decision.md) (the "why").
Source of truth for implementation facts: [`../ai-editor-technical-reference.md`](../ai-editor-technical-reference.md) (the "how").

## Priority legend

Priorities map the decision doc's labels to MoSCoW:

| Decision label | MoSCoW | Meaning |
| --- | --- | --- |
| 🔴 Critical | **Must** | MVP — the tool is not usable without it |
| 🟡 Important | **Should** | High value, ships after MVP is solid |
| 🟢 Desired / Nice-to-have | **Could** | Quality-of-life; opportunistic |

## Backlog

Status: ✅ done · 🔶 partial · ⬜ backlog

| ID | Feature | Status | Priority | Milestone | Depends on | Effort |
| --- | --- | --- | --- | --- | --- | --- |
| [AIED-05](AIED-05-undo-redo.md) | Per-field undo / redo | ✅ done (Phase 1) | Must | **M1 (first)** | — | M |
| [AIED-02](AIED-02-api-key-storage.md) | Secure API key storage | ✅ done | Must | M1 | — | M |
| [AIED-12](AIED-12-api-integration.md) | OpenRouter API integration (replace mock) | ✅ done | Must | M1 | AIED-01, AIED-02 | M |
| [AIED-01](AIED-01-ai-editing-panel.md) | AI editing panel (UI + mocked response) | ✅ done | Must | M1 | — | M |
| [AIED-04](AIED-04-save-to-anki.md) | Save AI changes back to Anki | ⬜ backlog | Must | M1 | AIED-01 | S |
| [AIED-03](AIED-03-preset-prompts.md) | Reusable prompt presets from history | ✅ done (core) | Must | M1 | AIED-01 | M |
| [AIED-06](AIED-06-split-card.md) | Split a card into two | ⬜ backlog | Should | M2 | AIED-01, AIED-04 | L |
| [AIED-07](AIED-07-fact-check-mcp.md) | Fact-check via Microsoft Learn MCP | ⬜ backlog | Should | M2 | AIED-01 | L |
| [AIED-13](AIED-13-model-picker.md) | Model picker (fetch models + pricing) | ✅ done | Should | M2 | AIED-12 | M |
| [AIED-08](AIED-08-conversation-history.md) | Local AI conversation history | 🔶 backend done; UI pending | Could | M3 | AIED-01 | M |
| [AIED-09](AIED-09-prompt-autosuggest.md) | Prompt auto-suggest from usage patterns | ⬜ backlog | Could | M3 | AIED-08 | L |
| [AIED-10](AIED-10-pin-presets.md) | Pin presets to the top | ⬜ backlog | P3 / low | M3+ | AIED-03 | S |
| [AIED-11](AIED-11-export-presets.md) | Export preset/prompt DB to a text file | ⬜ backlog | P3 / low | M3+ | AIED-03 | S |

Effort key (T-shirt): **S** ≈ < 1 day, **M** ≈ 1–3 days, **L** ≈ ≥ 1 week (rough, for a contributor new to Kotlin/Android).

## Completed

- **[AIED-05](AIED-05-undo-redo.md)** — ✅ done (Phase 1). Per-field undo/redo with caret restore,
  debounced + manual snapshots, rotation-safe `NoteEditorUndoViewModel`, app-bar buttons.
- **[AIED-02](AIED-02-api-key-storage.md)** — ✅ done. Encrypted `OpenRouterApiKeyStore` + Advanced settings UI.
- **[AIED-01](AIED-01-ai-editing-panel.md)** — ✅ done. AI panel (BottomSheet) + apply pipeline behind the
  `NoteEditorRewriter` seam, using a `MockNoteEditorRewriter` (wraps input in `«…»`); selection/whole-field
  handling, empty-prompt guard, single-undo-step integration. AIED-12 swaps in the real call with no UI change.
- **[AIED-12](AIED-12-api-integration.md)** — ✅ done. Real OpenRouter call wired into the panel via
  `ConversationHistoryNoteEditorRewriter → OpenRouterNoteEditorRewriter` (fresh key per call, records history);
  missing-key snackbar → Settings, typed-error snackbars. Replaced the mock with no UI rework.
- **[AIED-03](AIED-03-preset-prompts.md)** — ✅ done (functional core).
  Added local prompt preset persistence with timestamped history, deduplicated visible presets,
  usage metrics (`usageCount`, `lastUsedAt`), soft-delete/un-hide behavior, prompt request-builder concatenation logic, and
  AI panel UI wiring (scrollable checkbox list + apply integration) in
  `PromptPresetStore` + `PromptPresetRequestBuilder` with test coverage.
- **[AIED-13](AIED-13-model-picker.md)** — ✅ done. Advanced-settings model picker that fetches the OpenRouter
  catalogue with pricing (`OpenRouterModelCatalog`), grouped by provider (A→Z) and cheapest-first within each,
  persisted via `OpenRouterModelStore`; rewriter reads the model fresh per call (`modelProvider`). Manual-entry
  + offline fallback. Unit-tested parsing/grouping.

## Suggested sequencing

- **M1 — MVP edit loop:** `AIED-05 ✅ → AIED-01 ✅ → AIED-12 ✅ → AIED-03 ✅ → AIED-02 ✅ → AIED-04 ⬜`.
  Done: undo/redo (AIED-05), the AI panel (AIED-01), the real OpenRouter call wired in (AIED-12), encrypted
  key store (AIED-02), and the preset/history core (AIED-03). The editor now performs real AI rewrites.
  **Remaining for a complete MVP:** **AIED-04** (persist AI edits to the collection via the Save path), and
  wiring the **AIED-03 preset checkboxes into the AIED-01 panel** (UI integration of the existing store).
  Outcome: select text (or whole field) → describe or pick a preset → AI rewrites the field → Preview →
  Undo if wrong → Save. Full UX flow from the decision doc.
- **M2 — Power editing:** `AIED-06`, `AIED-07`. Card splitting and fact-checking.
- **M3 — Personalization:** `AIED-08`, `AIED-09`, `AIED-10`. Extend the prompt store to full conversations,
  suggest prompts from patterns, and pin favorites.

> **Note:** AIED-03 now owns local prompt persistence (history *is* the preset list). AIED-08 extends that
> same store with full input/output history — keep one store, don't build two.

## Cross-cutting concerns (apply to every feature)

- **Single integration file:** `AnkiDroid/src/main/java/com/ichi2/anki/NoteEditorFragment.kt` + layout
  `note_editor_fragment.xml`. Keep the fork's diff against upstream small to minimize merge conflicts.
- **No new networking deps:** OkHttp + coroutines are already present; use `launchCatchingTask { withContext(Dispatchers.IO) { … } }`.
- **Personal tool, not upstream:** side-load debug APK; do not PR to `ankidroid/Anki-Android` (see `AI_POLICY.md`).
- **Verify signatures after each upstream sync** — method names in the technical reference are inferred, not line-verified.
