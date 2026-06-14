# Project Goals

## Overview

This repository is a personal fork of AnkiDroid maintained by the project owner. The aim is to build a custom version of AnkiDroid that starts from the latest stable upstream release and is gradually enhanced with the owner's own features.

In the owner's words:

> My goal is to develop my own fork of AnkiDroid. I want to take the latest stable release commit as a base and then enhance AnkiDroid with my own custom features. Ideally I want to clone, develop, build, and work from WSL or a Linux container so I don't clutter my main system. I work from VS Code and can open Docker containers or WSL in it; I prefer a dedicated WSL used exclusively for Anki development.

In short: take a clean, stable base, develop and build entirely inside a dedicated Linux environment, and keep the main Windows system uncluttered.

## Requirements / Tasks — all complete

- [x] Create a dedicated WSL instance for Anki development — `anki-dev` (Ubuntu 24.04.4 LTS, WSL2, systemd), default user `viktor` with passwordless sudo.
- [x] Fork AnkiDroid to the owner's GitHub account — `LemonyCell/Anki-Android` (`upstream` = `ankidroid/Anki-Android`).
- [x] Clone the forked repo to WSL — `/home/viktor/Anki-Android`.
- [x] Create a branch from the latest stable release tag — branch `anki-viktor` from tag `v2.24.0` (commit `ebcf8e0`).
- [x] Configure building Anki locally — Gradle 9.5 + JDK 21 + Android SDK 36; `./gradlew assembleFullDebug` verified (BUILD SUCCESSFUL). GitHub Actions build still to be wired up.
- [x] Configure debugging via a real Android device — wireless adb to a Samsung Galaxy S21 (`SM-G991B`, Android 15); app built and installed on device.
- [x] Rename the app to `anki-viktor` to avoid conflicts — `applicationId` = `com.ichi2.anki.viktor` (debug: `com.ichi2.anki.viktor.debug`), `app_name` = `anki-viktor`; installs side-by-side with official AnkiDroid.

## Environment & configuration facts

These are the concrete, already-provisioned facts for this setup (see `CLAUDE.md` for the full table
and exact commands):

- **Dedicated WSL distro:** `anki-dev`, used exclusively for Anki development.
- **Repo:** `/home/viktor/Anki-Android` (Windows: `\\wsl.localhost\anki-dev\home\viktor\Anki-Android`).
- **Toolchain:** OpenJDK 21, Gradle 9.5 (wrapper), Kotlin 2.3.20, Android SDK at `/home/viktor/Android/Sdk`
  (`ANDROID_HOME` exported in `~/.bashrc`) with `platform-tools`, `platforms;android-36`, `build-tools;36.0.0`.
- **GitHub CLI:** `gh` authenticated as `LemonyCell` (scopes `repo`, `read:org`, `gist`; no `workflow` yet —
  needed later for editing `.github/workflows`).
- **Device debugging:** wireless adb at `192.168.0.192:41727`; install via
  `ANDROID_SERIAL=192.168.0.192:41727 ./gradlew installFullDebug`.
- **Editor:** VS Code connected to WSL; WSL preferred over Docker; the main Windows system is kept clean
  with all cloning, building, and development happening inside WSL.

## Next / future work

- Wire up GitHub Actions to build the fork (will require `gh auth refresh -s workflow`).
- Begin adding custom features on top of the `anki-viktor` branch.
