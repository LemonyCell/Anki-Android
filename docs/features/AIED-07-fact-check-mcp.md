# AIED-07 — Fact-check via Microsoft Learn MCP

| | |
| --- | --- |
| **Priority** | 🟡 Should |
| **Milestone** | M2 |
| **Status** | Backlog |
| **Depends on** | [AIED-01](AIED-01-ai-editing-panel.md) |
| **Effort** | L |

## Problem / motivation

Cards can contain factual errors. The owner already uses a Microsoft Learn MCP skill for fact-checking
elsewhere; bringing that into the editor lets bad facts be caught and corrected at review time.

## User story

> As a reviewer, I want to ask the AI to verify the facts in a card against authoritative docs (Microsoft
> Learn), so I can trust and correct the content before it reinforces a wrong memory.

## Scope

**In:**
- A "Fact-check" action that has the AI assess the field's factual claims and cite/correct using Microsoft Learn content.
- Surface findings (and optionally an AI-suggested correction routed through the AIED-01 apply path).

**Out:** general web search; non-Microsoft sources.

## Acceptance criteria

- [ ] Fact-check returns claims + verification notes for the current field.
- [ ] When a correction is offered, it can be applied through the existing AI-apply + undo flow.
- [ ] Clear handling when the topic isn't covered by Microsoft Learn (no false confidence).

## Technical notes

**Architectural unknown:** the Microsoft Learn MCP integration is a Claude-side / desktop tool today. How
an on-device Android app reaches MCP needs investigation — likely the device app calls a backend/agent that
has MCP access, rather than the phone speaking MCP directly. **Spike required** before estimating firmly;
**L** is a placeholder.

## Open questions

- Where does MCP run relative to the phone (on-device? proxy? the existing skill)?
- Is this better as a desktop-side quality gate (the owner already exports cards to a git repo) than an in-app action?
