# AIED-11 — Export the presets / prompt database to a text file

| | |
| --- | --- |
| **Priority** | 🟢 P3 / low |
| **Milestone** | M3 (or later) |
| **Status** | Backlog |
| **Depends on** | [AIED-03](AIED-03-preset-prompts.md) (prompt store); optionally [AIED-08](AIED-08-conversation-history.md) |
| **Effort** | S |

## Problem / motivation

The locally stored prompts/presets (and AI history) live only on the device. The owner already exports
cards to Markdown in a git repo for quality control; exporting the preset/prompt database the same way
gives a backup, lets it be version-controlled, and makes it portable across reinstalls/devices.

## User story

> As the owner, I want to export my whole preset/prompt database to a text file, so I can back it up,
> keep it in git, and review or reuse it outside the app.

## Scope

**In:**
- An export action that writes the **entire** local prompt/preset store to a `.txt` file.
- Include **all** records — active **and** soft-deleted — with their **date/time** and soft-delete flag
  (pinned flag too, if AIED-10 exists), so the export is a faithful dump, not just the visible list.
- Save to a user-accessible location (e.g. Downloads / share sheet) so it can be moved into the git repo.

**Out:** import/restore from the file (could be a follow-up); cloud sync; exporting Anki card content
(that already exists separately).

## Acceptance criteria

- [ ] Export produces a `.txt` file containing every stored prompt with its timestamp and status (active/soft-deleted; pinned if applicable).
- [ ] The file is retrievable off-device (Downloads or share target).
- [ ] Deduplication is **not** applied to the export — it is a complete dump of the underlying store.

## Technical notes

- Reads the same local store defined in AIED-03 (and AIED-08 if conversation history is included). No card
  data or collection access required — this is the AI-feature store only.
- Decide a simple, stable text format (one record per block: timestamp, flags, prompt text; optionally the
  full request/response from AIED-08). Keep it diff-friendly for git.

## Open questions

- Plain lines vs. a structured-but-text format (e.g. one block per entry) — what's most useful in git?
- Include full AIED-08 request/response payloads, or prompts only?
- Filename convention (timestamped?) and default destination.
