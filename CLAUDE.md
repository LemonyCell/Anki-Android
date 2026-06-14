# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## This repository is a personal fork

This is a fork of [ankidroid/Anki-Android](https://github.com/ankidroid/Anki-Android) (`upstream`)
owned by `LemonyCell` (`origin`). Development happens on the **`anki-viktor`** branch, which was
branched from the stable tag **`v2.24.0`**, not from `main`. When updating from upstream, rebase/merge
deliberately — do not assume `main` is the working base.

The app is **renamed to `anki-viktor`** so it installs alongside the official AnkiDroid:
`applicationId` is `com.ichi2.anki.viktor` (debug → `com.ichi2.anki.viktor.debug`) and the displayed
`app_name` is `anki-viktor`. Both are set in `AnkiDroid/build.gradle` (`defaultConfig`). The code
`namespace` remains `com.ichi2.anki`. All provider authorities use `${applicationId}.*`, so they stay
unique automatically — keep it that way to avoid install conflicts with upstream AnkiDroid.

> **Upstream AI policy:** `AI_POLICY.md` forbids new contributors from using AI tools for
> contributions sent to the upstream project. That restriction governs PRs to `ankidroid/Anki-Android`,
> not local work on this personal fork — but do not open upstream PRs from AI-generated changes.

## Build & run

Requires JDK 21, Android SDK with `compileSdk`/build-tools **36** (`gradle/libs.versions.toml`),
`ANDROID_HOME` set, and `local.properties` containing `sdk.dir=...`.

There are three product flavors on the `appStore` dimension: **`full`** (no storage/camera
restrictions — use this for local device builds), `play`, and `amazon`. Combine the flavor with the
build type, e.g. `assembleFullDebug`, `testPlayDebugUnitTest`.

```bash
./gradlew assembleFullDebug          # build debug APK (the variant used for local install)
# APK output: AnkiDroid/build/outputs/apk/full/debug/
./gradlew installFullDebug           # build + install onto the connected adb device
```

`local.properties` knobs that speed up local builds: `enable_coverage=false` (skip JaCoCo),
`enable_languages=false` (English only), `enable_leak_canary=false`.

Custom release rename without editing gradle:
`./gradlew assembleFullRelease -PcustomSuffix="suffix" -PcustomName="New name"`.

## Test & lint

```bash
./gradlew testPlayDebugUnitTest      # Robolectric JVM unit tests (substitute flavor as needed)
./gradlew testPlayDebugUnitTest --tests "com.ichi2.anki.SomeTest"   # single test class
./gradlew connectedPlayDebugAndroidTest   # instrumented tests (needs device/emulator)
./gradlew ktlintCheck                # Kotlin style check (ktlint 1.8, config in .editorconfig)
./gradlew ktlintFormat               # auto-fix Kotlin style
./gradlew lint                       # Android lint (custom rules from :lint-rules)
```

Unit tests use Robolectric 4.16; `robolectricDownloader.gradle` fetches the SDK jar needed for the
configured `targetSdk` — if `targetSdk` changes, that file may need a new entry.

## Architecture

AnkiDroid is an Android front-end over Anki's shared Rust backend. The Rust collection engine is **not
built here** — it is consumed as the prebuilt `Anki-Android-Backend` dependency
(`libs.versions.ankiBackend`), exposed to Kotlin via protobuf-generated APIs. This is why no
Rust/NDK toolchain is needed for an ordinary debug build.

Gradle modules (`settings.gradle`):

- **`AnkiDroid/`** — the application module: UI (activities/fragments), reviewer, deck picker, sync,
  widgets, the `CardContentProvider` (AnkiDroid API for third-party apps), and product flavors.
- **`libanki/`** — Kotlin wrapper around the backend collection (`Collection`, notes, cards,
  scheduling, decks/notetypes). The bridge between the Rust backend and the rest of the app; most
  data-model logic lives here rather than in `AnkiDroid/`.
- **`api/`** — the public AnkiDroid API library other Android apps use to read/write the collection
  via `CardContentProvider`. `FlashCardsContract.AUTHORITY` here is a fixed public constant
  (`com.ichi2.anki.flashcards`); it intentionally does not track `applicationId`.
- **`common/`** — utilities shared across modules with no Android-framework dependency.
- **`compat/`** — Android API-level compatibility shims (behavior that varies by SDK version).
- **`annotations/`** — project annotations.
- **`lint-rules/`** — custom Android lint rules enforced by `./gradlew lint`.
- **`vbpd/`** — view-binding property delegate helper.

When changing data-model or scheduling behavior, prefer `libanki/` and keep `AnkiDroid/` as the
presentation/coordination layer.
