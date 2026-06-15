# AIED-14 — Versioned system prompts (switch / edit / revert)

| | |
| --- | --- |
| **Priority** | 🟡 Should |
| **Milestone** | M2 |
| **Status** | Backlog |
| **Depends on** | [AIED-12](AIED-12-api-integration.md) (system prompt is sent in the call), [AIED-02](AIED-02-api-key-storage.md) (settings home) |
| **Effort** | M |

## Problem / motivation

The system prompt that steers every AI rewrite is currently hard-coded
(`OpenRouterNoteEditorRewriter.SYSTEM_PROMPT` — "Rewrite the provided note field content… Return only the
rewritten field content."). The owner should be able to tune it (tone, formatting rules, language) without
rebuilding, see how it has changed over time, and safely roll back.

## User story

> As the owner, I want to keep multiple versions of the system prompt (e.g. v1, v2, v3), switch which one is
> active, go back to an earlier version, and refine an existing one into a new version, so I can experiment
> with prompt wording without losing earlier work.

## Scope

**In:**
- A setting (Advanced settings, next to the API key / model) listing **saved system-prompt versions**, each
  with a label and timestamp, with an indicator of the **active** one.
- The built-in **Default** version is always present and read-only (the current `SYSTEM_PROMPT`).
- Actions: **Activate** a version, **Edit** a version, **Duplicate/refine** a version into a new one
  ("create version 3 from version 1"), **Delete** a custom version, **Reset to Default** (activate the default).
- The rewriter uses the **active** version's text, read **fresh per call** (mirror `modelProvider` — add a
  `systemPromptProvider`). A blank/empty active value falls back to the default.
- Persist all versions + the active selection across restarts.

**Out (later, if wanted):**
- Per-preset or per-prompt system-prompt overrides; auto-naming/diffing between versions.
- Templating/variables inside the system prompt.

## Acceptance criteria

- [ ] Multiple system-prompt versions can be created, labelled, edited, and deleted; the Default is always present.
- [ ] Exactly one version is active at a time; AI rewrites use the active version's text.
- [ ] Switching the active version (e.g. back to v1) takes effect on the next rewrite without restart.
- [ ] Refining = duplicating an existing version into a new editable one, leaving the source unchanged.
- [ ] Empty/Default falls back to the built-in prompt (no crash); selections persist across restarts.

## Technical notes

- `OpenRouterNoteEditorRewriter` currently hard-codes `SYSTEM_PROMPT` and builds the request with it. Make
  the system prompt injectable (a `systemPromptProvider: () -> String` defaulting to the constant), and
  promote the constant (e.g. public `DEFAULT_SYSTEM_PROMPT`) — mirrors the AIED-13 `modelProvider` change.
- **Storage:** versions are the same shape as AIED-03 presets — `{id, label, text, createdAt}` plus an
  `activeVersionId`. Prefer **extending `PromptPresetStore`** (already persists timestamped, soft-deletable
  lists) over a new store — keep AI state in one place (see the AIED-03/AIED-08 note).
- **UI:** an Advanced-settings preference opening a version list (reuse the AIED-13 `ModelRowAdapter`-style
  list); per-row Activate/Edit/Duplicate/Delete and a multi-line edit dialog (reuse AIED-02/AIED-13 dialog
  patterns). The Default row is non-deletable.

## Open questions

- Auto-label new versions ("Version N") or always prompt for a name?
- Show the active version's text inline on the settings summary, or just its label?
- Cap on the number of retained versions?
