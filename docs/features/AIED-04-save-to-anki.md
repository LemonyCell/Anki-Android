# AIED-04 — Save AI changes back to Anki

| | |
| --- | --- |
| **Priority** | 🔴 Must |
| **Milestone** | M1 (MVP) |
| **Status** | Backlog |
| **Depends on** | [AIED-01](AIED-01-ai-editing-panel.md) |
| **Effort** | S |

## Problem / motivation

AI edits applied in-memory (AIED-01) are worthless unless they persist to the collection so the improved
card shows up in future reviews and syncs.

## User story

> As a reviewer, after the AI rewrites a field, I want to save it to my collection with the existing Save
> action, so the fix is permanent and syncs to my other devices.

## Scope

**In:**
- Reuse the note editor's existing **Save** path so AI-edited fields are committed like any manual edit.
- Ensure HTML is preserved and the newline→`<br>` behavior isn't double-applied.

**Out:** a custom confirm/apply dialog — the decision doc explicitly replaces "confirm/reject" with Undo/Redo (AIED-05).

## Acceptance criteria

- [ ] Saving after an AI edit persists the new field content (`col.updateNote`), visible after reopening the card.
- [ ] The change participates in Anki's normal undo and sync.
- [ ] No double newline/`<br>` encoding regressions.

## Technical notes

Saving runs through libanki `undoableOp { col.updateNote(note) }` inside `launchCatchingTask` — see
[`../ai-editor-technical-reference.md`](../ai-editor-technical-reference.md) §2. Prefer reusing the
existing `saveNote()`-style method rather than writing a new save path.

## Open questions

- Confirm the exact save method signature in the current `NoteEditorFragment.kt` after cloning.
