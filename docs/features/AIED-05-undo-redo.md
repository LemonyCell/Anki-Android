# AIED-05 — Per-field undo / redo

| | |
| --- | --- |
| **Priority** | 🔴 Must |
| **Milestone** | M1 (MVP) |
| **Status** | **Done (Phase 1)** — verified on device; merged to `anki-viktor` |
| **Depends on** | — (foundation; AI-trigger wiring lands with [AIED-01](AIED-01-ai-editing-panel.md)) |
| **Effort** | M |

> **Implementation order:** this was built **first**, before AIED-01. The history stack and undo/redo UI
> work on field-content snapshots and were developed and tested standalone; once AIED-01 exists, the only
> addition is calling `captureFieldSnapshot(ord)` before/after each AI rewrite (Phase 2).

## Implementation details (Phase 1 — done)

- `AnkiDroid/src/main/java/com/ichi2/anki/noteeditor/FieldHistory.kt`
  - Pure stack + cursor with `push(text, selection)` / `undo()` / `redo()` / `canUndo` / `canRedo`.
  - Stores `FieldSnapshot(text, selection)` so the **caret position** is restored, not just the text.
  - Seeded with the field's initial content; redo-branch overwrite on new push; depth capped at 20.
- `AnkiDroid/src/main/java/com/ichi2/anki/noteeditor/NoteEditorUndoViewModel.kt`
  - Holds `Map<ord, FieldHistory>` so history **survives configuration changes** (rotation).
- `AnkiDroid/src/main/java/com/ichi2/anki/NoteEditorFragment.kt`
  - **Debounced capture** (~1s after typing stops) via the per-field `EditFieldTextWatcher`; captures `selectionEnd`.
  - **Focus tracking** (`lastFocusedFieldOrd`) so undo/redo target the focused field; menu re-validated on focus/snapshot.
  - **Restore** writes text via `setFieldValueFromUi`, then `requestFocus()` + `setSelection(...)`; guarded by `isRestoringHistory` so restores don't record new snapshots.
  - **`captureFieldSnapshot(ord)`** — public manual trigger (bypasses debounce) for Phase 2 AI before/after snapshots.
- `AnkiDroid/src/main/res/menu/note_editor.xml`
  - App-bar items **Preview · Undo · Redo · Save**, all `showAsAction="always"` (reusing `@string/undo`/`@string/redo` + `ic_undo_white`/`ic_redo`).
- `AnkiDroid/src/test/java/com/ichi2/anki/noteeditor/FieldHistoryTest.kt` — 7 unit tests (walk/redo-branch/no-op/cap/caret). Passing.

## Acceptance criteria status

- [x] After states A→B→C, Undo returns B then A; Redo returns B then C.
- [x] A new push after undoing to A produces D and discards C.
- [x] Undo/Redo disabled at stack ends.
- [x] History per field is independent and survives rotation; depth capped (20).
- [x] Caret position is restored on undo/redo (not reset to start).
- [ ] Phase 2: snapshot before each AI rewrite — hook (`captureFieldSnapshot`) added; call sites land with AIED-01/AIED-12.

## Problem / motivation

The decision doc replaces a "confirm/reject after AI" step with **undo/redo**: apply freely, step back if
the result is wrong. This is the safety net that makes fast AI editing comfortable.

## User story

> As a reviewer, I want to undo and redo AI edits within the current edit session per field, so I can try
> an AI change and revert it instantly if I don't like it.

## Scope

Built in two phases because it ships before AIED-01:

**Phase 1 (now, standalone):**
- A history stack per field (keyed by field index), implemented as the `FieldHistory` class from the decision doc.
- A snapshot is pushed at defined points (e.g. on field focus loss / explicit snapshot) so the mechanism is testable without AI.
- Undo/Redo buttons (or menu actions) in the note editor with correct enable/disable state.
- The exact cursor semantics from the decision doc: a new push after an undo overwrites the redo branch.
- Survive configuration changes (rotation) — store in a `ViewModel`; cap stack depth (e.g. 20).

**Phase 2 (when AIED-01 lands):**
- Capture a snapshot **before** an AI rewrite and **after** it is applied, so each AI change is its own
  undo step and is visible via undo/redo. Use `NoteEditorFragment.captureFieldSnapshot(ord)` — a manual
  trigger that records the current field content immediately, bypassing the typing debounce. (Added in
  Phase 1 and ready for the AI panel to call.)

**Out:** integrating with Android IME keystroke-level undo or libanki collection undo (those already exist separately).

## Acceptance criteria

Phrased over generic snapshots so Phase 1 is testable without AI (a snapshot = a pushed field state;
in Phase 2 each AI rewrite is one snapshot):

- [ ] After states A→B→C, Undo returns to B then A; Redo returns to B then C.
- [ ] A new push after undoing to A produces D and discards C (stack: A→B→D).
- [ ] Undo/Redo disabled at stack ends.
- [ ] History per field is independent and survives rotation; stack depth is capped (e.g. 20) to bound memory.
- [ ] Phase 2: a snapshot is captured before each AI rewrite, so undo reverts the AI change.

## Technical notes

Reference implementation (`FieldHistory` + `Map<Int, …>`) is in
[`../ai-editor-decision.md`](../ai-editor-decision.md) §2 and
[`../ai-editor-technical-reference.md`](../ai-editor-technical-reference.md) §5. Restore via
`setContent(previous, replaceNewlines = false)`.

## Open questions

- Should manual keystroke edits between AI actions also push to the stack, or only AI actions?
- Reset history on Save, or keep it for the session?
