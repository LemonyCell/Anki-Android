# AIED-05 — Per-field undo / redo

| | |
| --- | --- |
| **Priority** | 🔴 Must |
| **Milestone** | M1 (MVP) |
| **Status** | **In progress — implementing first** |
| **Depends on** | — (foundation; AI-trigger wiring lands with [AIED-01](AIED-01-ai-editing-panel.md)) |
| **Effort** | M |

> **Implementation order:** this is being built **first**, before AIED-01. The history stack and
> undo/redo UI work on field-content snapshots and can be developed and tested standalone; once AIED-01
> exists, the only addition is pushing a snapshot before each AI rewrite.

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
- Push a snapshot **before each AI rewrite**, so undo/redo steps through AI edits exactly as in the decision-doc flow.

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
