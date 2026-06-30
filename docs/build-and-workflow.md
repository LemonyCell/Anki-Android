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

## Workflow — helper scripts

Two scripts in `tools/` wrap the common loop. Run them from `/home/viktor/Anki-Android` inside `anki-dev`.

| Script | What it does | When |
| --- | --- | --- |
| `./tools/dev-build.sh` | `ktlintFormat` + `assembleFullDebug`, then prints the APK path | After a code change — quick "does it format + compile?" (no phone needed) |
| `./tools/dev-push.sh [ip:port]` | `adb connect` (if `ip:port` given) + `installFullDebug` — Gradle auto-picks the device's ABI | Ship the build onto the phone |

Typical change → ship loop:

```bash
# 1. (per feature) run the unit tests for the package you touched
./gradlew testFullDebugUnitTest --tests "com.ichi2.anki.noteeditor.ai.*"

# 2. format + build the debug APK
./tools/dev-build.sh
#    → APK at AnkiDroid/build/outputs/apk/full/debug/

# 3. install onto the phone (VPN OFF → use the LAN 192.168.x.x:port from Wireless debugging)
./tools/dev-push.sh 192.168.x.x:port   # first time this session: pass ip:port
./tools/dev-push.sh                    # later: phone already connected
```

Tests stay a manual step on purpose — the `--tests` filter is per-feature, so it's not baked into the
stupid build script. The scripts own format / build / install.

### What the scripts wrap (brief)

- **ktlintFormat** — auto-fixes Kotlin style (run inside `dev-build.sh`). The strict gate, **ktlintCheck**,
  is what CI enforces; locally we just auto-format.
- **testFullDebugUnitTest** — JVM/Robolectric unit tests; `--tests "FQN"` limits scope to one class or package.
- **assembleFullDebug** — compiles the `full` flavor, `debug` build type → debuggable APK signed with the debug key.
- **installFullDebug** — same build, then pushes to the device named by `ANDROID_SERIAL` (set by `dev-push.sh`).

## Device connection (wireless adb)

`dev-push.sh <ip:port>` runs the `adb connect` for you. The phone's Wireless-debugging port **rotates**, and a
**VPN** makes it advertise an unreachable `10.x` IP — use the **LAN `192.168.x.x:port`** from
*Settings → Developer options → Wireless debugging*. If `adb connect` is refused (first-ever connect, or after
the phone forgets the host), pair once first: `adb pair <ip>:<pairPort> <6-digit-code>`, then run `dev-push.sh`.

## Git remotes

- `origin` = `LemonyCell/Anki-Android` (the fork) · `upstream` = `ankidroid/Anki-Android`.
- Default working branch: **`anki-viktor`**. Feature work on `feature/*` branches, merged into `anki-viktor`.

## From Windows

Edit files via `\\wsl.localhost\anki-dev\home\viktor\Anki-Android\...`; run all gradle/adb/git through
`wsl -d anki-dev -- bash -lc '...'`.
