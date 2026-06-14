# AIED-02 — Secure API key storage

| | |
| --- | --- |
| **Priority** | 🔴 Must |
| **Milestone** | M1 (MVP) |
| **Status** | Backlog |
| **Depends on** | — (enabler; blocks AIED-01 networking) |
| **Effort** | M |

## Problem / motivation

Calling Claude requires an Anthropic API key on the device. AnkiDroid has **no** secure-storage helper
today — settings live in plain `SharedPreferences`. Storing the key in plaintext is unacceptable even for
a personal tool.

## User story

> As the app owner, I want to enter my Anthropic API key once and have it stored encrypted on-device, so
> the AI features work across restarts without exposing the key in readable form.

## Scope

**In:**
- A settings entry to paste/update/clear the API key.
- Encrypted persistence (`EncryptedSharedPreferences`, AES256; master key in Android KeyStore) — see reference §7.
- A read accessor the networking layer uses; clear behavior when the key is missing (AIED-01 shows a prompt to set it).

**Out:** server-side proxy (noted as the higher-security alternative; out of scope for a personal fork).

## Acceptance criteria

- [ ] Key can be entered, updated, and cleared from settings.
- [ ] Stored value is **not** readable in the app's prefs XML (verify by inspecting the file via adb/run-as).
- [ ] Key survives app restart.
- [ ] AIED-01 cannot call the API when no key is set, and tells the user to set one.

## Technical notes

[`../ai-editor-technical-reference.md`](../ai-editor-technical-reference.md) §7 has the
`EncryptedSharedPreferences` snippet and the deprecation caveat (security-crypto `1.1.0-alpha07`
deprecated Apr 2025; fine for a personal fork, or use DataStore + Tink / the `dev.spght:encryptedprefs` fork).

## Open questions

- Use deprecated `security-crypto` now (simplest) or go straight to DataStore + Tink?
- Validate the key with a cheap test request on save, or defer validation to first use?
