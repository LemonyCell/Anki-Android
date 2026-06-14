# AIED-03 — Preset prompts (Format / Simplify / Table)

| | |
| --- | --- |
| **Priority** | 🔴 Must |
| **Milestone** | M1 (MVP) |
| **Status** | Backlog |
| **Depends on** | [AIED-01](AIED-01-ai-editing-panel.md) |
| **Effort** | S |

## Problem / motivation

Most fixes are the same few transformations (improve formatting, simplify wording, turn content into a
table). Typing the instruction every time is friction. One-tap presets cover the common cases.

## User story

> As a reviewer, I want one-tap buttons for the transformations I do most (format, simplify, make a table),
> so I can fix common issues without writing a prompt.

## Scope

**In:**
- Preset buttons in the AI panel: **Format**, **Simplify**, **Table** (each a canned instruction sent through the AIED-01 pipeline).
- Presets respect the same selection rule (selection vs. whole field).

**Out:** user-editable/custom presets (could be a later enhancement); auto-suggest is AIED-09.

## Acceptance criteria

- [ ] Three preset buttons are visible in the panel.
- [ ] Tapping a preset applies its transformation to the selection/field via the AIED-01 flow.
- [ ] Preset prompts are defined in one place (easy to tweak wording).

## Technical notes

Pure reuse of the AIED-01 call path — a preset is just a predefined instruction string. No new networking.

## Open questions

- Exact wording of each preset prompt? (esp. "Table" — what columns/structure heuristic?)
- Should a preset and free-text be combinable (preset + extra note)?
