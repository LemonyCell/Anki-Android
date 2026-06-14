# AIED-03 — Reusable prompt presets from history

| | |
| --- | --- |
| **Priority** | 🔴 Must |
| **Milestone** | M1 (MVP) |
| **Status** | Backlog |
| **Depends on** | [AIED-01](AIED-01-ai-editing-panel.md) |
| **Effort** | M |

## Problem / motivation

Most fixes reuse the same few instructions. Instead of shipping fixed built-in presets, **everything the
user types to the AI is saved locally and becomes a reusable preset next time.** The preset list is the
user's own prompt history — it grows naturally and stays personal. There are **no built-in presets**.

## User story

> As a reviewer, I want every instruction I've given the AI to be saved and offered back as a checkable
> preset, so I can re-apply my common prompts with a tap instead of retyping, and curate the list over time.

## Scope

**In:**
- **Persist every submitted prompt** locally (survives app restart). Each record stores the prompt text and
  a **created date + time**.
- **Deduplicate** for display: the same prompt (normalized text) appears once in the list.
- **Soft delete only:** removing a preset from the UI marks it soft-deleted (hidden) — it is **never hard
  deleted** from storage.
- **Preset list UI:** a **scrollable checkbox list** that stays compact (capped height so it doesn't take
  the whole panel). Shows only non-soft-deleted, deduplicated prompts.
- **Apply behavior:** checking one or more presets **appends** their text to the user's current prompt
  **when the request is built** (under the hood) — the typed prompt field is the user's own text; checked
  presets are concatenated into the effective instruction sent to Claude.
- **Remove the built-in Format/Simplify/Table presets** — the list is sourced entirely from history.

**Out:**
- Pinning presets to the top → [AIED-10](AIED-10-pin-presets.md) (separate, low priority).
- Restoring/viewing soft-deleted prompts (storage keeps them; a restore UI is a later enhancement).
- Editing a stored prompt's text in place.

## Acceptance criteria

- [ ] Submitting a prompt saves it locally with a timestamp; it survives an app restart.
- [ ] Identical prompts appear only once in the list (deduplicated).
- [ ] The list is a scrollable, height-capped checkbox list; it does not dominate the panel.
- [ ] Checking N presets appends their text to the request along with the typed prompt; unchecking removes them from the request.
- [ ] "Removing" a preset hides it from the list but it remains in storage (soft-deleted), and a re-typed identical prompt re-appears (or un-soft-deletes) rather than duplicating.
- [ ] No built-in presets are shown; an empty history shows an empty (or hint) list.

## Technical notes

- **Storage:** local DB/store (Room or DataStore) on-device; no card data leaves the device. Schema sketch:
  `prompt_text`, `created_at` (date-time), `soft_deleted` (bool). Dedup key = normalized `prompt_text`
  (trim + collapse whitespace; decide case sensitivity — see open questions).
- **Relationship to [AIED-08](AIED-08-conversation-history.md):** AIED-03 owns *prompt* persistence;
  AIED-08 may extend the same store with full input/output history. Keep one store, don't build two.
- **Apply path:** reuse the AIED-01 request builder; presets only change the instruction string assembled
  before the OkHttp call — no new networking.

## Open questions

- Dedup normalization: case-insensitive? punctuation-insensitive?
- When checked presets are appended, in what order vs. the typed prompt (before/after)? Separator?
- Re-typing a soft-deleted prompt: un-delete it, or keep hidden and create a new active record?
- Any cap on list length / pruning, or rely on soft-delete curation only?
