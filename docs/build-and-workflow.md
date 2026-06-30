# Build & Workflow — anki-viktor

Quick reference for building, testing, and installing the fork. Everything runs inside the dedicated
WSL2 distro **`anki-dev`** at `/home/viktor/Anki-Android`.

## Installed toolchain (in `anki-dev`)

| Tool | Version / note | Why |
| --- | --- | --- |
| JDK | OpenJDK **21** | Required by AnkiDroid / AGP |
| Gradle | **9.5** (wrapper, `./gradlew`) | Build system |
| Android SDK | `platform-tools`, `platforms;android-36`, `build-tools;36.0.0`, `cmdline-tools;latest` | Compile + adb |
| `gh` | GitHub CLI (auth: `LemonyCell`) | Fork / clone / push |
| **No Rust, no NDK** | — | The Anki core is a **prebuilt** dependency (see Optimizations) |

`ANDROID_HOME` is exported in `~/.bashrc`; `local.properties` holds `sdk.dir` + speedup flags.

## Optimizations (why builds are fast)

1. **Prebuilt Rust backend — not compiled locally.** Anki's core engine (Rust) ships as the prebuilt
   `net.ankiweb.rsdroid` dependency (`ankiBackend` in `gradle/libs.versions.toml`). So we need **no Rust
   toolchain and no NDK**, and Gradle only compiles Kotlin/Java + packages the APK.
2. **Coverage off.** `enable_coverage=false` in `local.properties` disables JaCoCo (documented as slow).
3. **`full` flavor, `debug` type.** No store restrictions; `.debug` applicationId installs alongside real AnkiDroid.
4. **Gradle configuration cache.** Enabled in the project → repeat builds ~20–45s (`Configuration cache entry reused`).
5. **Targeted tests.** Run one package with `--tests`, not the whole suite.

`local.properties`:
```properties
sdk.dir=/home/viktor/Android/Sdk
enable_coverage=false
```

## Workflow — command chain

Run from `/home/viktor/Anki-Android` inside `anki-dev`. Typical change → ship loop:

```bash
# 1. format Kotlin (auto-fix style)
./gradlew ktlintFormat

# 2. run the relevant unit tests (fast: one package, not all)
./gradlew testFullDebugUnitTest --tests "com.ichi2.anki.noteeditor.ai.*"

# 3. style gate (CI-equivalent check)
./gradlew ktlintCheck

# 4. compile the APK (full + debug flavor)
./gradlew assembleFullDebug
#    → APK at AnkiDroid/build/outputs/apk/full/debug/

# 5. connect the phone over Wi-Fi (port rotates; VPN must be OFF → LAN IP)
adb connect <phone-ip>:<port>

# 6. build + install onto the connected phone in one step
ANDROID_SERIAL=<phone-ip>:<port> ./gradlew installFullDebug
```

Steps 1–4 are the "is it correct + clean" gate; 5–6 put it on the device. Day-to-day you often run just
2 + 6 (test, then build-install).

### What each step does (brief)

- **ktlintFormat / ktlintCheck** — Kotlin style; format auto-fixes, check fails the build on violations.
- **testFullDebugUnitTest** — JVM/Robolectric unit tests; `--tests "FQN"` limits scope (a single class or package).
- **assembleFullDebug** — compiles the `full` flavor, `debug` build type → debuggable APK signed with the debug key.
- **installFullDebug** — same build, then pushes to the device named by `ANDROID_SERIAL`.

## Device connection (wireless adb)

The phone's Wireless-debugging port **rotates**, and a **VPN** makes it advertise an unreachable `10.x` IP.
Use the **LAN `192.168.x.x:port`** from *Settings → Developer options → Wireless debugging*. If `adb connect`
is refused, re-pair: `adb pair <ip>:<pairPort> <6-digit-code>` then `adb connect <ip>:<connectPort>`.

## Git remotes

- `origin` = `LemonyCell/Anki-Android` (the fork) · `upstream` = `ankidroid/Anki-Android`.
- Default working branch: **`anki-viktor`**. Feature work on `feature/*` branches, merged into `anki-viktor`.

## From Windows

Edit files via `\\wsl.localhost\anki-dev\home\viktor\Anki-Android\...`; run all gradle/adb/git through
`wsl -d anki-dev -- bash -lc '...'`.
