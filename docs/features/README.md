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

Status: ✅ done · 🔶 partial · ⬜ backlog

| ID | Feature | Status | Priority | Milestone | Depends on | Effort |
| --- | --- | --- | --- | --- | --- | --- |
| [AIED-05](AIED-05-undo-redo.md) | Per-field undo / redo | ✅ done (Phase 1) | Must | **M1 (first)** | — | M |
| [AIED-02](AIED-02-api-key-storage.md) | Secure API key storage | ✅ done | Must | M1 | — | M |
| [AIED-12](AIED-12-api-integration.md) | OpenRouter API integration (replace mock) | 🔶 backend done; UI pending | Must | M1 | AIED-01, AIED-02 | M |
| [AIED-01](AIED-01-ai-editing-panel.md) | AI editing panel (UI + mocked response) | ⬜ backlog | Must | M1 | — | M |
| [AIED-04](AIED-04-save-to-anki.md) | Save AI changes back to Anki | ⬜ backlog | Must | M1 | AIED-01 | S |
| [AIED-03](AIED-03-preset-prompts.md) | Reusable prompt presets from history | ⬜ backlog | Must | M1 | AIED-01 | M |
| [AIED-06](AIED-06-split-card.md) | Split a card into two | ⬜ backlog | Should | M2 | AIED-01, AIED-04 | L |
| [AIED-07](AIED-07-fact-check-mcp.md) | Fact-check via Microsoft Learn MCP | ⬜ backlog | Should | M2 | AIED-01 | L |
| [AIED-08](AIED-08-conversation-history.md) | Local AI conversation history | ⬜ backlog | Could | M3 | AIED-01 | M |
| [AIED-09](AIED-09-prompt-autosuggest.md) | Prompt auto-suggest from usage patterns | ⬜ backlog | Could | M3 | AIED-08 | L |
| [AIED-10](AIED-10-pin-presets.md) | Pin presets to the top | ⬜ backlog | P3 / low | M3+ | AIED-03 | S |
| [AIED-11](AIED-11-export-presets.md) | Export preset/prompt DB to a text file | ⬜ backlog | P3 / low | M3+ | AIED-03 | S |

Effort key (T-shirt): **S** ≈ < 1 day, **M** ≈ 1–3 days, **L** ≈ ≥ 1 week (rough, for a contributor new to Kotlin/Android).

## Suggested sequencing

- **M1 — MVP edit loop:** `AIED-05 ✅ → AIED-01 → AIED-04 → AIED-03 → AIED-02 ✅ → AIED-12 🔶`.
  **AIED-05 (undo/redo)** shipped first as a standalone foundation (field-snapshot history + buttons).
  **AIED-02** (encrypted key store) and the **AIED-12 backend** (`OpenRouterNoteEditorRewriter`) are done.
  The remaining gap is **AIED-01** — the panel + apply pipeline (mock first), which also unblocks AIED-04
  (save), AIED-03 (presets), and the **AIED-12 UI wiring** that swaps the mock for the real OpenRouter call.
  Outcome: select text (or whole field) → describe or pick a preset → AI rewrites the field → Preview →
  Undo if wrong → Save. Full UX flow from the decision doc.
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
