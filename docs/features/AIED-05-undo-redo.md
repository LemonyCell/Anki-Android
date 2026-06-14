# AIED-05 — Per-field undo / redo

| | |
| --- | --- |
| **Priority** | 🔴 Must |
| **Milestone** | M1 (MVP) |
| **Status** | Backlog |
| **Depends on** | [AIED-01](AIED-01-ai-editing-panel.md) |
| **Effort** | M |

## Problem / motivation

The decision doc replaces a "confirm/reject after AI" step with **undo/redo**: apply freely, step back if
the result is wrong. This is the safety net that makes fast AI editing comfortable.

## User story

> As a reviewer, I want to undo and redo AI edits within the current edit session per field, so I can try
> an AI change and revert it instantly if I don't like it.

## Scope

**In:**
- A history stack per field (keyed by field index); before each AI rewrite, push the current content.
- Undo/Redo buttons in the panel with correct enable/disable state.
- The exact cursor semantics from the decision doc: a new AI action after an undo overwrites the redo branch.
- Survive configuration changes (rotation) — store in a `ViewModel`.

**Out:** integrating with Android IME keystroke-level undo or libanki collection undo (those already exist separately).

## Acceptance criteria

- [ ] After AI edits A→B→C, Undo returns to B then A; Redo returns to B then C.
- [ ] A new AI edit after undoing to A produces D and discards C (stack: A→B→D).
- [ ] Undo/Redo disabled at stack ends.
- [ ] History per field is independent and survives rotation; stack depth is capped (e.g. 20) to bound memory.

## Technical notes

Reference implementation (`FieldHistory` + `Map<Int, …>`) is in
[`../ai-editor-decision.md`](../ai-editor-decision.md) §2 and
[`../ai-editor-technical-reference.md`](../ai-editor-technical-reference.md) §5. Restore via
`setContent(previous, replaceNewlines = false)`.

## Open questions

- Should manual keystroke edits between AI actions also push to the stack, or only AI actions?
- Reset history on Save, or keep it for the session?
