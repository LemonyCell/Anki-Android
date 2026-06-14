# AIED-01 — AI editing panel + free-text prompt

| | |
| --- | --- |
| **Priority** | 🔴 Must |
| **Milestone** | M1 (MVP) |
| **Status** | Backlog |
| **Depends on** | [AIED-02](AIED-02-api-key-storage.md) (API key) |
| **Effort** | M |

## Problem / motivation

When a card is poorly worded during review, fixing it by hand on the phone is painful (raw HTML, tiny
keyboard). The cards get tagged "fix later" and never fixed, degrading study quality. The best moment to
fix is *during review* — friction must be near zero. See [`../ai-editor-decision.md`](../ai-editor-decision.md).

## User story

> As someone reviewing cards on Android, I want an AI panel inside the note editor where I describe what's
> wrong in plain language and have the AI rewrite the field, so I can fix cards in-flow without switching apps.

## Scope

**In:**
- A BottomSheet (or dual-pane) panel in the note editor with: a prompt text field and an "Apply AI" button.
- Input selection: use the **selected text** in the focused `FieldEditText`; if nothing is selected, use the **whole field** content.
- Send field content + user instruction to Claude (OkHttp, `Dispatchers.IO`); write the result back into the field in-memory via `setContent(...)`.
- Loading + error states (network failure, empty key, API error surfaced to the user, not silent).

**Out (separate features):** presets (AIED-03), persisting to the collection (AIED-04), undo/redo (AIED-05).

## Acceptance criteria

- [ ] An "AI" affordance is visible in the note-editor toolbar and opens the panel.
- [ ] With text selected, only that selection is sent and replaced; with no selection, the full field is sent and replaced.
- [ ] A round-trip to Claude returns a rewritten field and updates the on-screen field within the same screen (no app switch).
- [ ] Network/API errors show a readable message; the field is left unchanged on failure.
- [ ] Existing AnkiDroid **Preview** renders the updated field correctly.

## Technical notes

Integration point and call pattern are documented in
[`../ai-editor-technical-reference.md`](../ai-editor-technical-reference.md) §1–3:
`NoteEditorFragment.kt`, `FieldEditText` (`setContent`), `launchCatchingTask` + OkHttp to
`api.anthropic.com`. INTERNET permission already present.

## Open questions

- Model + max_tokens defaults? (decision doc example uses a Sonnet model)
- BottomSheet vs. the GSoC dual-pane `ResizablePaneManager` — which fits the review flow better?
- How to show the selection boundary so the user knows what will be replaced?
