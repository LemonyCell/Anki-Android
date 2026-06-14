# AIED-08 — Local AI conversation history

| | |
| --- | --- |
| **Priority** | 🟢 Could |
| **Milestone** | M3 |
| **Status** | Backlog |
| **Depends on** | [AIED-01](AIED-01-ai-editing-panel.md) |
| **Effort** | M |

## Problem / motivation

Keeping a local record of AI interactions (prompt + before/after) helps the owner refine prompts, audit
what changed, and is the data source for prompt auto-suggest (AIED-09).

## User story

> As the owner, I want my AI edit conversations stored locally, so I can review past prompts/results and
> build better presets over time.

## Scope

**In:**
- Persist each AI interaction locally (timestamp, field, instruction, input, output).
- A simple way to view recent interactions.

**Out:** cloud sync of history; sharing.

## Acceptance criteria

- [ ] Each AI edit is recorded locally and survives restart.
- [ ] History is viewable in-app (basic list is enough).
- [ ] Storage is bounded or prunable (no unbounded growth).

## Technical notes

Local persistence via Room or DataStore (no card data leaves the device). Keep it decoupled from the
collection DB to avoid sync side-effects. Feeds AIED-09.

## Open questions

- Room vs. a simple JSON/DataStore log for this volume?
- Retention policy (count/age cap)?
