# Project Goals

## Overview

This repository is a personal fork of AnkiDroid maintained by the project owner. The aim is to build a custom version of AnkiDroid that starts from the latest stable upstream release and is gradually enhanced with the owner's own features.

In the owner's words:

> My goal is to develop my own fork of AnkiDroid. I want to take the latest stable release commit as a base and then enhance AnkiDroid with my own custom features. Ideally I want to clone, develop, build, and work from WSL or a Linux container so I don't clutter my main system. I work from VS Code and can open Docker containers or WSL in it; I prefer a dedicated WSL used exclusively for Anki development.

In short: take a clean, stable base, develop and build entirely inside a dedicated Linux environment, and keep the main Windows system uncluttered.

## Requirements / Tasks

- [x] Create a dedicated WSL instance for Anki development (created: `anki-dev`, Ubuntu 24.04, default user `viktor`)
- [x] Fork AnkiDroid to the owner's GitHub account (`LemonyCell/Anki-Android`)
- [x] Clone the forked repo to WSL (`/home/viktor/Anki-Android`)
- [x] Create a branch from the latest stable release tag (branch `anki-viktor` from tag `v2.24.0`)
- [x] Configure building Anki locally (Gradle `assembleFullDebug`, JDK 21, Android SDK 36; GitHub Actions build planned later)
- [ ] Configure debugging via a real Android device — *in progress / pending phone setup* (wireless adb chosen as easiest; pending phone pairing)
- [x] Rename the app to `anki-viktor` to avoid conflicts (applicationId `com.ichi2.anki.viktor`, app_name `anki-viktor`)

## Environment

- **Dedicated WSL distro:** `anki-dev` is used exclusively for Anki development.
- **Editor:** development is done from VS Code (connected to WSL).
- **Tooling preference:** WSL is preferred over Docker containers.
- **System hygiene:** the main Windows system should be kept clean, with all cloning, development, and building happening inside WSL.
