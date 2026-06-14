# AIED-01 — AI editing panel (UI + mocked response)

| | |
| --- | --- |
| **Priority** | 🔴 Must |
| **Milestone** | M1 (MVP) |
| **Status** | Backlog |
| **Depends on** | — (real AI call is [AIED-12](AIED-12-api-integration.md)) |
| **Effort** | M |

## Problem / motivation

When a card is poorly worded during review, fixing it by hand on the phone is painful (raw HTML, tiny
keyboard). The cards get tagged "fix later" and never fixed, degrading study quality. The best moment to
fix is *during review* — friction must be near zero. See [`../ai-editor-decision.md`](../ai-editor-decision.md).

This story builds the **panel UI and the apply pipeline only**. The "AI" is **mocked**: it returns the
selected text (or the whole field, if nothing is selected), so the full interaction can be built and tested
with no network, no API key, and no cost. The real call replaces the mock in [AIED-12](AIED-12-api-integration.md).

## User story

> As a developer of this fork, I want the AI panel and its apply flow working against a mock, so I can
> validate the whole UX (open panel → choose input → apply → result in field → Preview) before wiring the
> real API.

## Scope

**In:**
- A BottomSheet (or dual-pane) panel in the note editor with: a prompt text field and an "Apply" button.
- Input selection: use the **selected text** in the focused `FieldEditText`; if nothing is selected, use the **whole field** content.
- **Mock "AI":** the apply action returns the input unchanged — the selected text if there was a selection, otherwise the whole field — and writes it back into the field via `setContent(...)` (replacing the selection or the whole field accordingly).
- The mock lives behind a single function/interface (e.g. `suspend fun rewrite(input, instruction): String`) so AIED-12 swaps the implementation without touching the UI.

**Out (separate features):** real Anthropic API call + key (AIED-12), presets (AIED-03), persisting to the collection (AIED-04), undo/redo (AIED-05).

## Acceptance criteria

- [ ] An "AI" affordance is visible in the note editor and opens the panel.
- [ ] With text selected, only that selection is captured and replaced; with no selection, the full field is captured and replaced.
- [ ] Tapping "Apply" runs the mock and updates the on-screen field (mock returns the captured input verbatim).
- [ ] The mock is isolated behind one function/interface so AIED-12 can replace it without UI changes.
- [ ] Existing AnkiDroid **Preview** renders the updated field correctly.

## Technical notes

Integration point: `NoteEditorFragment.kt`, `FieldEditText` (`setContent`, selection getters). The apply
call should already run through `launchCatchingTask { … }` so AIED-12 only changes what happens inside the
rewrite function (mock → OkHttp), not the call site. See
[`../ai-editor-technical-reference.md`](../ai-editor-technical-reference.md) §1–2.

## Open questions

- BottomSheet vs. the GSoC dual-pane `ResizablePaneManager` — which fits the review flow better?
- How to show the selection boundary so the user knows what will be replaced?
