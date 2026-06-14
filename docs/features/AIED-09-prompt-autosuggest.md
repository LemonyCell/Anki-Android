# AIED-09 — Prompt auto-suggest from usage patterns

| | |
| --- | --- |
| **Priority** | 🟢 Nice-to-have |
| **Milestone** | M3 |
| **Status** | Backlog |
| **Depends on** | [AIED-08](AIED-08-conversation-history.md) |
| **Effort** | L |

## Problem / motivation

Over time the owner reuses similar instructions. Suggesting likely prompts (or proposing new presets) from
past usage reduces typing further and surfaces the owner's own best prompts.

## User story

> As the owner, I want the panel to suggest prompts based on what I've asked before, so I rarely have to
> type an instruction from scratch.

## Scope

**In:**
- Derive frequent/recent prompts from AIED-08 history and offer them as quick-pick suggestions.
- Optionally suggest promoting a recurring prompt to a saved preset.

**Out:** server-side ML; cross-user models (this is a single-user personal tool).

## Acceptance criteria

- [ ] Suggestions appear based on stored history and are one-tap to apply.
- [ ] With no history, the panel degrades gracefully to the built-in presets (AIED-03).

## Technical notes

Pure on-device heuristic over AIED-08 data (frequency/recency) — no new model needed. Blocked until history
exists.

## Open questions

- Ranking: frequency, recency, or context (field/notetype)-aware?
- Auto-promote to preset automatically, or only on explicit confirm?
