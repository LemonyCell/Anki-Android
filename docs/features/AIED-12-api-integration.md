# AIED-12 — Anthropic API integration (replace the mock)

| | |
| --- | --- |
| **Priority** | 🔴 Must |
| **Milestone** | M1 (MVP) |
| **Status** | Backlog |
| **Depends on** | [AIED-01](AIED-01-ai-editing-panel.md) (panel + apply pipeline), [AIED-02](AIED-02-api-key-storage.md) (API key) |
| **Effort** | M |

## Problem / motivation

[AIED-01](AIED-01-ai-editing-panel.md) ships the panel and apply pipeline against a **mock** that echoes
the input. This story replaces that mock with a real call to Claude so the panel actually rewrites field
content from the user's instruction.

## User story

> As a reviewer, when I describe what's wrong and tap Apply, I want the real AI to rewrite the selected
> text (or whole field) using my stored API key, so the card is actually improved.

## Scope

**In:**
- Implement the rewrite function behind AIED-01's interface with an **OkHttp POST to `api.anthropic.com`**,
  sending the field input + the user's instruction, running on `Dispatchers.IO`.
- Read the API key from AIED-02 secure storage; if missing, prompt the user to set it (no call attempted).
- **Loading + error states:** show progress during the call; surface network/API errors readably; leave the field unchanged on failure.

**Out:** streaming responses; multi-turn conversation; model/params settings UI (could be a follow-up).

## Acceptance criteria

- [ ] Tapping Apply performs a real round-trip to Claude and writes the rewritten result into the field within the editor (no app switch).
- [ ] The stored API key (AIED-02) is used; when no key is set, the user is told to set one and no call is made.
- [ ] Network/API errors show a readable message and the field is left unchanged.
- [ ] Swapping the mock for this implementation required no changes to the AIED-01 UI (interface preserved).

## Technical notes

OkHttp + coroutines are already declared deps. Use the `launchCatchingTask { withContext(Dispatchers.IO) { … } }`
call site from AIED-01; only the rewrite implementation changes. The minimal POST (headers `x-api-key`,
`anthropic-version`, `content-type`) and response parsing are in
[`../ai-editor-technical-reference.md`](../ai-editor-technical-reference.md) §3. INTERNET permission already present.

## Open questions

- Model + `max_tokens` defaults? (decision-doc example uses a Sonnet model)
- System prompt: how to frame "rewrite this field per the instruction; return only the new field content"?
- Timeout/retry behavior on flaky networks.
