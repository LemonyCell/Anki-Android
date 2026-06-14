# AIED-10 — Pin presets to the top

| | |
| --- | --- |
| **Priority** | 🟢 P3 / low |
| **Milestone** | M3 (or later) |
| **Status** | Backlog |
| **Depends on** | [AIED-03](AIED-03-preset-prompts.md) |
| **Effort** | S |

## Problem / motivation

As the history-based preset list (AIED-03) grows, the most-used prompts get buried. Letting the user pin
favorites to the top keeps them reachable without scrolling.

## User story

> As a reviewer, I want to pin my favorite presets so they always appear at the top of the list, so I don't
> have to scroll to find the ones I use most.

## Scope

**In:**
- Mark/unmark a preset as pinned.
- Pinned presets sort to the top of the AIED-03 checkbox list; the pin state persists across restarts.

**Out:** ordering among pinned items (manual drag-reorder); auto-pinning by frequency (that's closer to AIED-09).

## Acceptance criteria

- [ ] A preset can be pinned and unpinned.
- [ ] Pinned presets render above unpinned ones and the state survives restart.
- [ ] Pinning does not affect dedup or soft-delete behavior.

## Technical notes

Add a `pinned` flag to the AIED-03 prompt store and order by `pinned DESC, created_at DESC` (or chosen
default). Pure storage + sort change on top of AIED-03; no networking.

## Open questions

- Sort order among multiple pinned items?
- Is pinning redundant once AIED-09 (auto-suggest by pattern) exists, or complementary?
