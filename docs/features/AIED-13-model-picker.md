# AIED-13 — Model picker for AI rewrites

| | |
| --- | --- |
| **Priority** | 🟡 Should |
| **Milestone** | M2 |
| **Status** | Backlog |
| **Depends on** | [AIED-12](AIED-12-api-integration.md) (OpenRouter call), [AIED-02](AIED-02-api-key-storage.md) (settings home) |
| **Effort** | M |

## Problem / motivation

AIED-12 hard-codes the OpenRouter model (`anthropic/claude-sonnet-4`). Different cards/needs suit different
models (cheaper/faster vs. stronger), and OpenRouter exposes many. The owner should be able to choose which
model the AI rewrite uses, without rebuilding the app.

## User story

> As the owner, I want to pick which OpenRouter model the AI editor uses (and change it later), so I can
> trade off cost, speed, and quality per my needs.

## Scope

**In:**
- **Fetch the live model catalogue from OpenRouter** (`GET https://openrouter.ai/api/v1/models`) and show a
  selectable list of models **with their pricing** (prompt and completion price per token/1M tokens), plus
  the model name/id and ideally context length.
- A picker UI to choose a model from that list; the choice is **persisted** across restarts.
- The note editor uses the selected model when constructing `OpenRouterNoteEditorRewriter` (its `model` param).
- A sensible default (`anthropic/claude-sonnet-4`) when nothing is chosen or the catalogue can't be fetched.
- Graceful handling when the list can't be loaded (offline / no key): fall back to the stored/default model
  and let the user still type a model id manually.

**Out (later, if wanted):**
- Per-preset or per-prompt model overrides; temperature/max_tokens controls.
- Sorting/filtering/search over the (large) catalogue beyond a basic list.
- Caching strategy beyond a simple in-memory/short-lived cache.

## Acceptance criteria

- [ ] The model list is fetched from OpenRouter and each entry shows its **id/name and pricing** (prompt + completion).
- [ ] Selecting a model persists it; AI rewrites use the selected model.
- [ ] With nothing selected, or if the fetch fails, the default model is used (no crash) and a manual entry is still possible.

## Technical notes

- `OpenRouterNoteEditorRewriter` already takes a `model` constructor param (default `DEFAULT_MODEL`); persist
  the chosen model (e.g. a small `OpenRouterModelStore`, or alongside the key) and read it **fresh** when the
  rewriter is built in `NoteEditorFragment` (mirror the fresh-key-read pattern) so changes apply without restart.
- **Catalogue fetch:** reuse OkHttp + coroutines (already used by `OpenRouterNoteEditorRewriter`). `GET /api/v1/models`
  returns `data[]` with `id`, `name`, `context_length`, and a `pricing` object (`prompt`, `completion`, … as
  per-token USD strings). Parse id/name/pricing; the endpoint works without auth but send the key if present.
  Consider a dedicated `OpenRouterModelCatalog` class with unit-testable JSON parsing (mirror the rewriter's
  `parse*` helpers + tests).
- Pricing strings are per-token USD; display normalised (e.g. per 1M tokens) for readability.

## Open questions

- Where to surface the picker — Advanced settings next to the API key, or inside the AI panel?
- How to present pricing (per-token vs per-1M-tokens; show prompt+completion separately or combined)?
- Refresh cadence / caching for the catalogue (it's large and changes infrequently).
- Expose temperature/max_tokens here too, or keep model-only for now?
