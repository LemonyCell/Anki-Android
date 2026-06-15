# AIED-15 — OpenRouter MCP tool-calling orchestration (fact-check & beyond)

| | |
| --- | --- |
| **Priority** | 🟡 Should |
| **Milestone** | M2 |
| **Status** | Backlog |
| **Depends on** | [AIED-01](AIED-01-ai-editing-panel.md) (AI panel), [AIED-12](AIED-12-api-integration.md) (OpenRouter) |
| **Effort** | L |

## Problem / motivation

The AI panel can rewrite text (AIED-12), but cards often contain **factual claims that need verification**. The owner already uses Microsoft Learn MCP for fact-checking in Claude Code; bringing that verification into the phone app enables fact-check actions at review time without context-switching.

This task adds **tool-calling orchestration**: the app runs an agentic loop where OpenRouter decides which tools to invoke (e.g., search Microsoft Docs, fetch a specific page), the phone executes those tools via local MCP, and OpenRouter uses the results to produce grounded fact-check output.

## User story

> As a reviewer, I want to fact-check the current field by asking the AI to verify claims against Microsoft Learn, so I can trust the card content before it reinforces wrong memory.

## Scope

**In:**
- Local MCP server for Microsoft Learn (MCP stdlib + custom `microsoft_docs_search` and `microsoft_docs_fetch` tools).
- Agentic tool-calling loop on the phone:
  1. App sends field text + fact-check instruction to OpenRouter.
  2. OpenRouter returns tool calls (e.g., `{"type":"tool_use", "name":"microsoft_docs_search", "input":{"query":"..."}}`).
  3. App executes tool via local MCP, collects result.
  4. App sends result back to OpenRouter as a tool response.
  5. Loop repeats until OpenRouter returns final text (no more tool calls).
- Fact-check action in the note editor UI:
  - Button/menu item to trigger the loop.
  - Show progress (searching, analyzing).
  - Display findings (verified ✓, incorrect ✗, cite source).
  - Optionally apply suggested correction via existing AIED-01 apply + undo pipeline.

**Out:**
- Streaming responses (wait for full completion).
- Non-Microsoft sources (Microsoft Learn only).
- Real-time collaboration (single-user action).
- Multi-tool advanced reasoning (start with search + fetch).

## Architecture

### Phone-side components

**1. MCP Bridge (`MicrosoftLearMcpBridge`)**
- Runs a local MCP client connecting to the Microsoft Learn MCP server.
- Exposes `callTool(name: String, input: Map<String, Any>): String` suspend function.
- Tools available: `microsoft_docs_search`, `microsoft_docs_fetch` (and others from MCP stdlib as needed).
- Error handling: wraps MCP errors into readable `McpToolExecutionException`.

**2. OpenRouter Tool-Calling Orchestrator (`OpenRouterToolCallingOrchestrator`)**
- Implements agentic loop:
  1. Build system prompt: "You are a fact-checker. Use tools to verify claims. Return findings with citations."
  2. Send initial request: `{"model":"...", "messages":[...], "tools":[...]}`
  3. Parse response:
     - If `stop_reason == "tool_use"`, extract tool call, execute via MCP, add result to messages, loop.
     - If `stop_reason == "end_turn"`, extract final text, return.
  4. Safety: max 5 iterations (prevent infinite loops).
- Typed errors: `McpToolExecutionException`, `OpenRouterToolCallingException`, `ToolCallingTimeoutException`.

**3. Fact-Check Action in NoteEditorFragment**
- New button: "Verify facts" (or icon in toolbar).
- Flow:
  1. Show loading spinner.
  2. Call `OpenRouterToolCallingOrchestrator.factCheck(fieldText)`.
  3. On success:
     - Show result dialog: findings + source links.
     - Optional "Apply correction" button → routes to AIED-01 apply pipeline.
  4. On error:
     - Show snackbar with error message (MCP down, OpenRouter error, timeout).
     - Offer "Retry" action.

### Request/Response contracts

#### Initial request to OpenRouter (with tool definitions)

```json
{
  "model": "anthropic/claude-sonnet-4",
  "temperature": 0,
  "max_tokens": 2000,
  "system": "You are a fact-checking assistant. Use the provided tools to verify factual claims from the card field. For each claim, search Microsoft Learn documentation, fetch relevant pages, and determine if the claim is accurate. Return a structured summary: verified claims (with source links), incorrect or outdated claims (with corrections), and unclear claims (where docs are not definitive). Be precise and cite sources.",
  "messages": [
    {
      "role": "user",
      "content": "Fact-check this card field:\n\n{FIELD_TEXT}"
    }
  ],
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "microsoft_docs_search",
        "description": "Search Microsoft Learn documentation for a topic",
        "input_schema": {
          "type": "object",
          "properties": {
            "query": { "type": "string", "description": "Search query (e.g., 'Azure App Service deployment')" }
          },
          "required": ["query"]
        }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "microsoft_docs_fetch",
        "description": "Fetch the full content of a specific Microsoft Learn page",
        "input_schema": {
          "type": "object",
          "properties": {
            "url": { "type": "string", "description": "Full URL to the Microsoft Learn page" }
          },
          "required": ["url"]
        }
      }
    }
  ]
}
```

#### Tool-use response from OpenRouter (example)

```json
{
  "id": "msg_…",
  "type": "message",
  "role": "assistant",
  "content": [
    {
      "type": "text",
      "text": "I'll verify the claims in this field by searching Microsoft Learn."
    },
    {
      "type": "tool_use",
      "id": "toolu_…",
      "name": "microsoft_docs_search",
      "input": {
        "query": "Azure App Service autoscaling duration metric"
      }
    }
  ],
  "stop_reason": "tool_use"
}
```

#### Tool result (from phone-side MCP execution)

Phone sends back:

```json
{
  "role": "user",
  "content": [
    {
      "type": "tool_result",
      "tool_use_id": "toolu_…",
      "content": "Search results:\n1. https://learn.microsoft.com/en-us/azure/app-service/overview — App Service overview.\n2. https://learn.microsoft.com/en-us/azure/app-service/manage-scale-up — Scaling...\n..."
    }
  ]
}
```

OpenRouter responds with next tool call, or final text.

#### Final response (after all tool calls complete)

```json
{
  "id": "msg_…",
  "type": "message",
  "role": "assistant",
  "content": [
    {
      "type": "text",
      "text": "**Verification Summary**\n\n✓ **Verified**: 'Duration is aggregated over the time grain' — Confirmed in Azure autoscale documentation (https://learn.microsoft.com/en-us/azure/azure-monitor/autoscale/autoscale-common-scale-patterns).\n\n? **Unclear**: Exact value of 'time grain' in minutes — documentation shows it can be 1–60 min depending on metric; suggest clarifying in the card.\n\n**Suggested refinement**: 'The Duration setting (e.g. 10 minutes) defines the aggregation window for metric values; the time grain determines how often metrics are sampled within that window.'"
    }
  ],
  "stop_reason": "end_turn"
}
```

## Implementation details

### Files to create/modify

**New files:**
- `AnkiDroid/src/main/java/com/ichi2/anki/noteeditor/ai/MicrosoftLearMcpBridge.kt`
  - Manages local MCP client connection.
  - `suspend fun callTool(name: String, input: Map<String, Any>): String`
  - Typed errors: `McpConnectionException`, `McpToolExecutionException`.

- `AnkiDroid/src/main/java/com/ichi2/anki/noteeditor/ai/OpenRouterToolCallingOrchestrator.kt`
  - Agentic loop implementation.
  - `suspend fun factCheck(fieldText: String, model: String = DEFAULT_MODEL): FactCheckResult`
  - Returns `FactCheckResult(verifiedClaims: List<Claim>, incorrectClaims: List<Claim>, unclearClaims: List<Claim>, rawOutput: String, sources: List<String>)`.
  - Typed errors: `OpenRouterToolCallingException`, `ToolCallingTimeoutException`.

- `AnkiDroid/src/test/java/com/ichi2/anki/noteeditor/ai/OpenRouterToolCallingOrchestratorTest.kt`
  - Unit tests: tool-call parsing, loop iteration, error cases, max-iteration cap.

**Modified files:**
- `AnkiDroid/src/main/java/com/ichi2/anki/NoteEditorFragment.kt`
  - Add "Verify facts" button/menu item.
  - Hook into `applyAiFactCheck()` → show dialog with findings.
  - UI for applying correction via AIED-01 pipeline.

- `AnkiDroid/src/main/res/layout/note_editor_fragment.xml`
  - Add fact-check button or toolbar item.

- `AnkiDroid/src/main/res/values/strings.xml`
  - New strings: `ai_fact_check_button`, `ai_fact_check_progress`, `ai_fact_check_result_title`, `ai_fact_check_verified`, `ai_fact_check_incorrect`, `ai_fact_check_unclear`, `ai_fact_check_error`, `ai_fact_check_apply_correction`.

### Dependencies

- **MCP SDK** (if not already present; check `libs.versions.toml`):
  - Anthropic MCP client library for Kotlin/Java (or lightweight HTTP-based MCP client if SDK unavailable).
  - Install via gradle: `implementation "com.anthropic:mcp-sdk:..."` (or mirror/vendored).

- **Existing deps** (already in use):
  - OkHttp (for tool result HTTP calls if needed).
  - kotlinx-coroutines (Dispatchers.IO).
  - Kotlin serialization (JSON parsing).

## Acceptance criteria

- [ ] Local MCP bridge can connect to Microsoft Learn MCP server and execute `microsoft_docs_search` and `microsoft_docs_fetch` tools.
- [ ] Tool-calling loop correctly parses OpenRouter responses and iterates on tool calls (max 5).
- [ ] Final response is extracted and structured into verified/incorrect/unclear claims with source links.
- [ ] "Verify facts" action in note editor is wired and shows findings dialog.
- [ ] Applying a suggested correction routes through existing AIED-01 pipeline (undo/redo works).
- [ ] Errors (MCP down, timeout, network) are caught and shown as readable snackbars.
- [ ] Unit tests cover loop iteration, error parsing, and edge cases (empty response, invalid JSON, max iterations).

## Technical notes

### MCP setup
The Microsoft Learn MCP server must be available locally (either bundled in the APK or downloaded at runtime). For MVP, assume it is pre-configured or provide a setup guide.

### Tool safety
- Max 5 iterations per fact-check to prevent infinite loops.
- Tool execution timeout: 10 seconds per tool call.
- Parse errors → stop loop and return partial results to user.

### UI feedback
- Show tool calls in progress (e.g., "Searching Microsoft Learn...").
- After complete, show summary with checkmarks/X for each claim.
- Sources displayed as clickable links (intent to browser if INTERNET permission allows).

### Fallback
If MCP is unavailable, disable the fact-check action and show a message "Microsoft Learn verification not available."

## Open questions

- Where does the Microsoft Learn MCP server live? (In APK, vendored, or downloaded at first use?)
- Should fact-check results be cached per field to avoid re-verifying identical content?
- What is the user's expected latency tolerance? (Tool-calling can be slow; consider showing a timeout option after 30 sec.)
- Should we expose model/temperature selection for fact-check separately from rewrite?
