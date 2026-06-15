# AIED-03 — Reusable prompt presets from history

| | |
| --- | --- |
| **Priority** | 🔴 Must |
| **Milestone** | M1 (MVP) |
| **Status** | ✅ Done (functional core) |
| **Depends on** | [AIED-01](AIED-01-ai-editing-panel.md) |
| **Effort** | M |

## Problem / motivation

Most fixes reuse the same few instructions. Instead of shipping fixed built-in presets, **everything the
user types to the AI is saved locally and becomes a reusable preset next time.** The preset list is the
user's own prompt history — it grows naturally and stays personal. There are **no built-in presets**.

## User story

> As a reviewer, I want every instruction I've given the AI to be saved and offered back as a checkable
> preset, so I can re-apply my common prompts with a tap instead of retyping, and curate the list over time.

## Scope

**In:**
- **Persist every submitted prompt** locally (survives app restart). Each record stores the prompt text and
  a **created date + time**.
- **Deduplicate** for display: the same prompt (normalized text) appears once in the list.
- **Soft delete only:** removing a preset from the UI marks it soft-deleted (hidden) — it is **never hard
  deleted** from storage.
- **Preset list UI:** a **scrollable checkbox list** that stays compact (capped height so it doesn't take
  the whole panel). Shows only non-soft-deleted, deduplicated prompts.
- **Apply behavior:** checking one or more presets **appends** their text to the user's current prompt
  **when the request is built** (under the hood) — the typed prompt field is the user's own text; checked
  presets are concatenated into the effective instruction sent to Claude.
- **Remove the built-in Format/Simplify/Table presets** — the list is sourced entirely from history.

**Out:**
- Pinning presets to the top → [AIED-10](AIED-10-pin-presets.md) (separate, low priority).
- Restoring/viewing soft-deleted prompts (storage keeps them; a restore UI is a later enhancement).
- Editing a stored prompt's text in place.

## Acceptance criteria

- [ ] Submitting a prompt saves it locally with a timestamp; it survives an app restart.
- [ ] Identical prompts appear only once in the list (deduplicated).
- [x] The list is a scrollable, height-capped checkbox list; it does not dominate the panel.
- [x] Checking N presets appends their text to the request along with the typed prompt; unchecking removes them from the request.
- [x] "Removing" a preset hides it from the list but it remains in storage (soft-deleted), and a re-typed identical prompt re-appears (or un-soft-deletes) rather than duplicating.
- [ ] No built-in presets are shown; an empty history shows an empty (or hint) list.

## Implementation details (functional core)

Implemented in commit `e196b24d36`:

- `AnkiDroid/src/main/java/com/ichi2/anki/noteeditor/ai/PromptPresetStore.kt`
  - Added local prompt preset storage using `SharedPreferences` + JSON serialization.
  - Every submitted prompt is persisted with timestamp (`PromptSubmission(promptText, createdAt)`).
  - Display list logic is deduplicated by normalized text (`trim + collapse whitespace`) and excludes soft-deleted presets.
  - Preset usage metrics are persisted by normalized prompt: `usageCount` and `lastUsedAt` via `recordPresetUsage(...)`.
  - Soft delete is implemented as hidden normalized keys; history entries are retained.
  - Re-submitting a soft-deleted prompt removes it from the soft-deleted set (un-hides it).
  - Added `PromptPresetRequestBuilder.buildEffectivePrompt(...)` to append checked presets to typed prompt using `\n\n` separators and dedupe checked presets by normalized text.
- `AnkiDroid/src/test/java/com/ichi2/anki/noteeditor/ai/PromptPresetStoreTest.kt`
  - Added tests for persistence, deduplication, soft-delete/un-hide behavior, request assembly, and empty-history behavior.

Implemented acceptance criteria:

- [x] Submitting a prompt saves it locally with a timestamp; it survives an app restart.
- [x] Identical prompts appear only once in the list (deduplicated).
- [ ] The list is a scrollable, height-capped checkbox list; it does not dominate the panel.
- [x] Checking N presets appends their text to the request along with the typed prompt; unchecking removes them from the request.
- [x] "Removing" a preset hides it from the list but it remains in storage (soft-deleted), and a re-typed identical prompt re-appears (or un-soft-deletes) rather than duplicating.
- [x] No built-in presets are shown; an empty history shows an empty (or hint) list.

## UI wiring (AI panel integration)

- `AnkiDroid/src/main/java/com/ichi2/anki/noteeditor/ai/AiRewriteBottomSheet.kt`
  - Loads visible presets from `PromptPresetStore` and renders them as a checkbox list.
  - Applies `PromptPresetRequestBuilder` over typed prompt + checked presets before emitting apply.
  - Persists typed submissions (`saveSubmittedPrompt`) when Apply is used.
  - Records usage for checked presets (`recordPresetUsage`) for future recency/frequency ranking.
  - Supports preset removal via per-row delete icon soft-delete (`softDeletePreset`) and immediate list refresh.
- `AnkiDroid/src/main/res/layout/fragment_bottomsheet_ai_rewrite.xml`
  - Added scrollable, height-capped preset list container and empty-state text.

## Technical notes

- **Storage:** local DB/store (Room or DataStore) on-device; no card data leaves the device. Schema sketch:
  `prompt_text`, `created_at` (date-time), `soft_deleted` (bool), `usage_count` (int), `last_used_at` (date-time). Dedup key = normalized `prompt_text`
  (trim + collapse whitespace; decide case sensitivity — see open questions).
- **Relationship to [AIED-08](AIED-08-conversation-history.md):** AIED-03 owns *prompt* persistence;
  AIED-08 may extend the same store with full input/output history. Keep one store, don't build two.
- **Apply path:** reuse the AIED-01 request builder; presets only change the instruction string assembled
  before the OkHttp call — no new networking.

## Open questions

- Dedup normalization: case-insensitive? punctuation-insensitive?
- When checked presets are appended, in what order vs. the typed prompt (before/after)? Separator?
- Re-typing a soft-deleted prompt: un-delete it, or keep hidden and create a new active record?
- Any cap on list length / pruning, or rely on soft-delete curation only?
