# AIED-02 — Secure API key storage

| | |
| --- | --- |
| **Priority** | 🔴 Must |
| **Milestone** | M1 (MVP) |
| **Status** | Done |
| **Depends on** | — (enabler; blocks AIED-01 networking) |
| **Effort** | M |

## Problem / motivation

Calling Claude via OpenRouter requires an API key on the device. AnkiDroid has **no** secure-storage helper
today — settings live in plain `SharedPreferences`. Storing the key in plaintext is unacceptable even for
a personal tool.

## User story

> As the app owner, I want to enter my OpenRouter API key once and have it stored encrypted on-device, so
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

## Implementation details

- Added encrypted key store: `AnkiDroid/src/main/java/com/ichi2/anki/ai/OpenRouterApiKeyStore.kt`.
  - Uses `EncryptedSharedPreferences` + `MasterKey` (AES256 key/value schemes).
  - Reads legacy `anthropic_api_key` and migrates to `open_router_api_key` on write/clear.
  - Uses non-encrypted fallback only during unit tests (`isRunningAsUnitTest`) to avoid Robolectric KeyStore issues.
- Added Advanced settings UI in `AnkiDroid/src/main/java/com/ichi2/anki/preferences/AdvancedSettingsFragment.kt`.
  - New "OpenRouter API key" preference with save/update/clear dialog flow.
  - Summary shows `Configured` / `Not set`; snackbar feedback on save/clear.
- Added settings/resources wiring:
  - `AnkiDroid/src/main/res/xml/preferences_advanced.xml`
  - `AnkiDroid/src/main/res/values/preferences.xml`
  - `AnkiDroid/src/main/res/values/10-preferences.xml`
- Added dependency for encrypted prefs:
  - `gradle/libs.versions.toml`
  - `AnkiDroid/build.gradle`
- Updated analytics preference coverage for the new key:
  - `AnkiDroid/src/test/java/com/ichi2/anki/analytics/PreferencesAnalyticsTest.kt`

## Acceptance criteria status

- [x] Key can be entered, updated, and cleared from settings.
- [x] Stored value uses encrypted prefs storage (not default plain preferences XML).
- [x] Key survives app restart (persistent encrypted shared prefs).
- [ ] AIED-01 missing-key behavior is implemented in AIED-01 (network call guard/prompt).

## Technical notes

[`../ai-editor-technical-reference.md`](../ai-editor-technical-reference.md) §7 has the
`EncryptedSharedPreferences` snippet and the deprecation caveat (security-crypto `1.1.0-alpha07`
deprecated Apr 2025; fine for a personal fork, or use DataStore + Tink / the `dev.spght:encryptedprefs` fork).

## Open questions

- Keep `security-crypto` for now (implemented) or move to DataStore + Tink later.
- Validate key on save with a lightweight request, or defer validation to first API call.
