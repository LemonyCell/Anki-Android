# Feature Roadmap — anki-viktor AI Editor

Product backlog for the AI note-editing capability in this fork. Each feature has its own spec in this
folder using a shared scaffold (problem → user story → scope → acceptance criteria → dependencies →
effort → open questions). Use these as the basis for creating and prioritizing implementation tasks.

Source of truth for rationale: [`../ai-editor-decision.md`](../ai-editor-decision.md) (the "why").
Source of truth for implementation facts: [`../ai-editor-technical-reference.md`](../ai-editor-technical-reference.md) (the "how").

## Priority legend

Priorities map the decision doc's labels to MoSCoW:

| Decision label | MoSCoW | Meaning |
| --- | --- | --- |
| 🔴 Critical | **Must** | MVP — the tool is not usable without it |
| 🟡 Important | **Should** | High value, ships after MVP is solid |
| 🟢 Desired / Nice-to-have | **Could** | Quality-of-life; opportunistic |

## Backlog

| ID | Feature | Priority | Milestone | Depends on | Effort |
| --- | --- | --- | --- | --- | --- |
| [AIED-01](AIED-01-ai-editing-panel.md) | AI editing panel + free-text prompt | Must | M1 | AIED-02 | M |
| [AIED-02](AIED-02-api-key-storage.md) | Secure API key storage | Must | M1 | — | M |
| [AIED-03](AIED-03-preset-prompts.md) | Reusable prompt presets from history | Must | M1 | AIED-01 | M |
| [AIED-04](AIED-04-save-to-anki.md) | Save AI changes back to Anki | Must | M1 | AIED-01 | S |
| [AIED-05](AIED-05-undo-redo.md) | Per-field undo / redo | Must | M1 | AIED-01 | M |
| [AIED-06](AIED-06-split-card.md) | Split a card into two | Should | M2 | AIED-01, AIED-04 | L |
| [AIED-07](AIED-07-fact-check-mcp.md) | Fact-check via Microsoft Learn MCP | Should | M2 | AIED-01 | L |
| [AIED-08](AIED-08-conversation-history.md) | Local AI conversation history | Could | M3 | AIED-01 | M |
| [AIED-09](AIED-09-prompt-autosuggest.md) | Prompt auto-suggest from usage patterns | Could | M3 | AIED-08 | L |
| [AIED-10](AIED-10-pin-presets.md) | Pin presets to the top | P3 / low | M3+ | AIED-03 | S |

Effort key (T-shirt): **S** ≈ < 1 day, **M** ≈ 1–3 days, **L** ≈ ≥ 1 week (rough, for a contributor new to Kotlin/Android).

## Suggested sequencing

- **M1 — MVP edit loop:** `AIED-02 → AIED-01 → AIED-04 → AIED-05 → AIED-03`.
  Outcome: select text (or whole field) → describe or pick a preset → AI rewrites the field → Preview →
  Undo if wrong → Save. This is the full UX flow from the decision doc.
- **M2 — Power editing:** `AIED-06`, `AIED-07`. Card splitting and fact-checking.
- **M3 — Personalization:** `AIED-08`, `AIED-09`, `AIED-10`. Extend the prompt store to full conversations,
  suggest prompts from patterns, and pin favorites.

> **Note:** AIED-03 now owns local prompt persistence (history *is* the preset list). AIED-08 extends that
> same store with full input/output history — keep one store, don't build two.

## Cross-cutting concerns (apply to every feature)

- **Single integration file:** `AnkiDroid/src/main/java/com/ichi2/anki/NoteEditorFragment.kt` + layout
  `note_editor_fragment.xml`. Keep the fork's diff against upstream small to minimize merge conflicts.
- **No new networking deps:** OkHttp + coroutines are already present; use `launchCatchingTask { withContext(Dispatchers.IO) { … } }`.
- **Personal tool, not upstream:** side-load debug APK; do not PR to `ankidroid/Anki-Android` (see `AI_POLICY.md`).
- **Verify signatures after each upstream sync** — method names in the technical reference are inferred, not line-verified.
