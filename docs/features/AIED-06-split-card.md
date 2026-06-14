# AIED-06 — Split a card into two

| | |
| --- | --- |
| **Priority** | 🟡 Should |
| **Milestone** | M2 |
| **Status** | Backlog |
| **Depends on** | [AIED-01](AIED-01-ai-editing-panel.md), [AIED-04](AIED-04-save-to-anki.md) |
| **Effort** | L |

## Problem / motivation

Some cards cram two concepts into one and should become two cards. Doing this manually (create note, copy
fields, set deck/tags) is tedious — a frequent reason cards get tagged and abandoned.

## User story

> As a reviewer, when a card covers two ideas, I want the AI to propose splitting it into two cards and
> create the second note for me, so I end up with two clean cards in the same deck.

## Scope

**In:**
- AI proposes a split (content for card A vs. card B) from the current note.
- Apply: keep the current note as card A; create a **new note** (card B) in the same deck/notetype, carrying tags.
- User can review both before committing.

**Out:** bulk splitting across many cards; cloze-specific splitting heuristics (TBD).

## Acceptance criteria

- [ ] AI returns a structured two-card proposal from one note.
- [ ] Confirming creates a new note via `col.addNote(note)` with the same notetype/deck/tags.
- [ ] Both notes are visible/reviewable afterward; the operation is undoable via Anki undo.

## Technical notes

New-note creation uses libanki `addNote` (reference §2). Needs a structured AI response (e.g. JSON with
two field sets) rather than a single rewritten string — more involved than AIED-01's single-field replace,
hence **L**.

## Open questions

- How to map AI output to fields for arbitrary notetypes (Basic vs. cloze vs. custom)?
- Same deck always, or let the user pick the destination for card B?
