# AIED-01 — AI editing panel (UI + mocked response)

| | |
| --- | --- |
| **Priority** | 🔴 Must |
| **Milestone** | M1 (MVP) |
| **Status** | **Done** — verified on device; on branch `feature/aied-01-ai-panel` |
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

## Implementation details (done)

- `AnkiDroid/src/main/java/com/ichi2/anki/noteeditor/ai/`
  - `MockNoteEditorRewriter.kt` — implements the existing `NoteEditorRewriter` seam; wraps input as `«input»` (visible + undoable). AIED-12 swaps this for the OpenRouter rewriter — **no UI change**.
  - `NoteEditorRewriteApplier.kt` — pure `compose(fullText, start, end, replacement)` → new text + caret; normalises/clamps the range. Unit-tested.
  - `NoteEditorAiViewModel.kt` — activity-scoped `MutableSharedFlow<String>` (`applyRequested`), mirroring `MultimediaViewModel`; carries the instruction from the sheet to the editor.
  - `AiRewriteBottomSheet.kt` + `res/layout/fragment_bottomsheet_ai_rewrite.xml` — prompt field + Apply; shows scope label (selection vs whole field); **Apply disabled until the prompt is non-blank**.
- `NoteEditorFragment.kt`
  - `openAiPanel()` captures the target field `ord` + selection **before** showing the sheet (the sheet steals focus); whole field when nothing is selected.
  - `applyAiRewrite()` runs in `launchCatchingTask`, calls the rewriter, writes back via `setFieldValueFromUi`, restores caret, and brackets the change with `captureFieldSnapshot` (AIED-05) so it's **one undo step**.
- `res/menu/note_editor.xml` — **AI** app-bar icon (`ic_lightbulb_stars`), order Preview · AI · Undo · Redo · Save.
- Strings in `res/values/01-core.xml`; tests `test/.../noteeditor/ai/NoteEditorRewriteApplierTest.kt`, `MockNoteEditorRewriterTest.kt` (passing).

## Acceptance criteria status

- [x] AI affordance opens the panel (app-bar icon).
- [x] Selection captured/replaced when present; whole field otherwise.
- [x] Apply runs the mock and updates the field (wrapped in `«…»`); Apply blocked on empty prompt.
- [x] Mock isolated behind `NoteEditorRewriter` so AIED-12 swaps without UI changes.
- [x] Existing Preview renders the updated field; change is a single Undo step.

## Technical notes

Integration point: `NoteEditorFragment.kt`, `FieldEditText` (`setContent`, selection getters). The apply
call runs through `launchCatchingTask { … }` so AIED-12 only changes what happens inside the rewrite
function (mock → OpenRouter), not the call site. See
[`../ai-editor-technical-reference.md`](../ai-editor-technical-reference.md) §1–2.

## Open questions

- BottomSheet vs. the GSoC dual-pane `ResizablePaneManager` — which fits the review flow better?
- How to show the selection boundary so the user knows what will be replaced?
