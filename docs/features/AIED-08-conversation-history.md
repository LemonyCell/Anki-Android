# AIED-08 — Local AI conversation history

| | |
| --- | --- |
| **Priority** | 🟢 Could |
| **Milestone** | M3 |
| **Status** | **Partial — storage + rewriter decorator backend implemented; history UI pending** |
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
- **Keep the whole AI request and response** — store the complete raw payloads (the full JSON request sent
  to Claude, including model/params/messages, and the full raw response), not just the parsed
  instruction/result, so interactions can be fully reconstructed and audited.
- A simple way to view recent interactions.

**Out:** cloud sync of history; sharing.

## Acceptance criteria

- [x] Each AI edit is recorded locally and survives restart. *(backend: `ConversationHistoryNoteEditorRewriter` + `PromptPresetStore`)*
- [x] The full raw request and response payloads are retained for each interaction. *(via `TraceableNoteEditorRewriter` / `OpenRouterNoteEditorRewriter`)*
- [ ] History is viewable in-app (basic list is enough). *(pending AIED-01 panel wiring)*
- [x] Storage is bounded or prunable (no unbounded growth). *(`PromptPresetStore.MAX_CONVERSATION_HISTORY_ENTRIES`)*

## Technical notes

Implemented as an extension of `PromptPresetStore` (JSON in app `SharedPreferences`) so prompt presets
and AI conversation history stay in one local store. Conversation writes are done by a rewriter decorator
(`ConversationHistoryNoteEditorRewriter`) that records both successful and failed rewrite calls. The OpenRouter
implementation now surfaces raw request/response payloads for audit logging via `TraceableNoteEditorRewriter`.

## Open questions

- Room vs. a simple JSON/DataStore log for this volume?
- Retention policy (count/age cap)?
