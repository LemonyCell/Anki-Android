# Forking AnkiDroid to Add an AI Editing Panel to the NoteEditor — Technical Reference

## TL;DR
- **NoteEditor is now `NoteEditorFragment`** (a Fragment, refactored from the old `NoteEditor` Activity during the AnkiDroid GSoC 2024 dual-pane Note Editor project by contributor "Haze-jolt"/Ashish), hosted by `SingleFragmentActivity` and launched via a `NoteEditorLauncher` intent helper; it uses classic XML layouts + `findViewById`, **not** Compose. Field content is read/written through the custom `FieldEditText` view and saved to the Anki collection via libanki coroutine ops.
- **The networking and async stack you need is already present**: OkHttp 5.3.2 and kotlinx-coroutines 1.10.2 are declared dependencies, and the project already uses `lifecycleScope`/`launchCatchingTask` with `Dispatchers.IO`. You can POST to api.anthropic.com from a fragment with a few lines of OkHttp inside a coroutine — no new HTTP library needed.
- **Build is standard Gradle**: `./gradlew assembleDebug` (JDK 21 works per the dev wiki; minSdk 24, AGP 9.0.1); no special `local.properties` beyond the Android SDK path. There is **no** secure-key storage in the codebase today, so you must add encrypted storage (e.g. EncryptedSharedPreferences) yourself for the API key.

## Key Findings

### 1. NoteEditor location and structure
- **File:** `AnkiDroid/src/main/java/com/ichi2/anki/NoteEditorFragment.kt` (package `com.ichi2.anki`). It was renamed from `NoteEditor` (`NoteEditor.kt`/`NoteEditor.java`) to `NoteEditorFragment` during the GSoC 2024 dual-pane Note Editor project. Per the project report by AnkiDroid GSoC 2024 contributor Haze-jolt (Ashish), the work "consisteted of many smaller steps including splintering the note editor into a seperate fragment and activity, and creating a interface for the noteeditor to send information on changes to the previewer" (github.com/Haze-jolt/AnkiDroid-GSoC-Report). The layout was renamed from `note_editor.xml` to `note_editor_fragment.xml`.
- **Type:** It is a **Fragment**, hosted inside `com.ichi2.anki.SingleFragmentActivity` (`SingleFragmentActivity.kt`), a generic single-fragment host activity that instantiates a fragment by class name (confirmed by crash stack traces referencing `SingleFragmentActivity.getFragment(SingleFragmentActivity.kt:44)`). Prior to GSoC 2024 it was a standalone `NoteEditor` Activity.
- **Launch:** Opened by building an `Intent` for the host activity through a `NoteEditorLauncher` helper class (with variants for editing an existing card vs. adding a new note). The Reviewer and CardBrowser start it through an `ActivityResultLauncher` (`registerForActivityResult`). Historically — and still in the public intent contract — extras `EXTRA_CALLER` (e.g. `CALLER_CARDBROWSER_ADD`) and `EXTRA_CONTENT` carry the caller context and any prefilled content.
- **UI structure:** Classic Android Views with XML layouts and `findViewById` — **not** Jetpack Compose and not ViewBinding-centric. Fields are rendered as a vertically-stacked list of `FieldEditText` views, with a horizontal RecyclerView toolbar (`com/ichi2/anki/noteeditor/Toolbar.java`) at the bottom.

### 2. Reading and writing field values
- **Custom field view:** `FieldEditText` — a custom `EditText` subclass. Each note field is one `FieldEditText`; the editor keeps them in a list (e.g. `editFields`). PRs #7958/#8011 confirm "selected text in FieldEditText passed to js-evaluator," establishing it as the canonical per-field editor.
- **Reading existing values:** On open, the editor loads the `editorNote` (an Anki `Note`) and populates each `FieldEditText` from the note's field values. Fields in the Anki model are joined by the field separator `\x1f` (`Consts.FIELD_SEPARATOR`, seen in `FlashCardsContract.kt`). The editor exposes getter-style accessors to collect current field strings (e.g. `getCurrentFieldStrings()` / `getFieldsText()`).
- **Writing values programmatically:** `FieldEditText` has a `setContent(content, replaceNewlines)`-style method that sets text and optionally converts newlines to `<br>`. (Behavioral confirmation: editing a note converts insignificant HTML newlines to `<br>` tags.)
- **Saving:** A `saveNote()`-style method commits changes. In the current merged-libanki architecture, saving runs inside `launchCatchingTask`/`lifecycleScope` and uses libanki ops — `collection.addNote(note)` for new notes and `collection.updateNote(note)` for edits — routed through `undoableOp`/`ChangeManager` (rather than the deprecated `note.flush()`). The older code used `CollectionTask.launchCollectionTask(ADD_NOTE, …)`.
- **HTML preservation:** Anki fields are stored as HTML. Reading returns raw HTML; writing preserves HTML, but note the newline→`<br>` conversion on edit (a documented behavior). If you write HTML programmatically, set it directly as the field content and be aware of that newline handling so you don't double-encode.

### 3. HTTP requests / Kotlin coroutines
- **HTTP client:** **OkHttp is already a dependency** — `okhttp = "5.3.2"` in `gradle/libs.versions.toml`. No Retrofit/Ktor. So you do not need to add a networking library.
- **Coroutines:** Yes, heavily used — `coroutines = '1.10.2'` (kotlinx-coroutines). The project's idiom is `lifecycleScope.launch { … }` with `withContext(Dispatchers.IO)` for blocking work, plus an AnkiDroid-specific helper `launchCatchingTask` (defined in `CoroutineHelpers.kt`) which wraps a coroutine with standardized error/exception handling and is defined on `FragmentActivity`. There is also an `applicationScope` (in `com.ichi2.anki.common.coroutines`) for app-lifetime work (used in `TtsVoices.kt`).
- **Existing network-call example:** The Anki backend bridge and sync code are the in-repo precedents; `TtsVoices.kt` is a good example of coroutine + `applicationScope` + `Dispatchers` usage to copy patterns from.
- **Minimum POST to api.anthropic.com from a fragment:**
```kotlin
private val httpClient = OkHttpClient()

private fun callClaude(apiKey: String, prompt: String) {
    launchCatchingTask {                       // AnkiDroid helper; or lifecycleScope.launch
        val responseText = withContext(Dispatchers.IO) {
            val json = """
                {"model":"claude-3-5-sonnet-latest","max_tokens":1024,
                 "messages":[{"role":"user","content":${JSONObject.quote(prompt)}}]}
            """.trimIndent()
            val request = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .post(json.toRequestBody("application/json".toMediaType()))
                .build()
            httpClient.newCall(request).execute().use { it.body!!.string() }
        }
        // back on main thread: parse responseText and call fieldEditText.setContent(...)
    }
}
```
Ensure `<uses-permission android:name="android.permission.INTERNET"/>` is present (it already is — AnkiDroid syncs over the network).

### 4. Build setup
- **Debug APK command:** `./gradlew assembleDebug` (Linux/Mac) or `gradlew.bat assembleDebug` (Windows). Per the AnkiDroid Development Guide wiki, this produces "an apk file signed with a standard 'debug' key will be generated named 'AnkiDroid-debug.apk' in: `%AnkiDroidRoot%/AnkiDroid/build/outputs/apk/`." Building from the command line works without Android Studio if the Android SDK is installed.
- **local.properties / SDK:** No special steps beyond a standard Android setup — you need `local.properties` pointing `sdk.dir` to your Android SDK (Android Studio writes this automatically). The maintainers explicitly warn (Development Guide wiki): "By following these instructions and avoiding manual updates, you'll help ensure a stable and reliable development environment for the project" — i.e. **do not manually update dependencies or the Gradle plugin.** `local.properties` can optionally hold `enable_coverage=false`.
- **JDK version:** Per the AnkiDroid Development Guide wiki, "Before running automated tests… For the latest version of Anki-Android-Backend (0.1.15), it has been confirmed that **JDK 21** works seamlessly." (The catalog now pins a newer backend, `0.1.64-anki25.09.2`, but JDK 21 remains the confirmed working JDK.) The root `build.gradle` enforces a JVM version range and fails the build with a clear message if you're outside it.
- **Docker / CI:** No official Docker image for local builds; CI runs via GitHub Actions workflows in `.github/workflows/`. You can reuse those workflow definitions as a build reference, but the simplest local path is `./gradlew assembleDebug`.
- **Install + launch on device (adb):**
```
adb install -r AnkiDroid/build/outputs/apk/debug/AnkiDroid-debug.apk
adb shell am start -n com.ichi2.anki/com.ichi2.anki.DeckPicker
```
For a parallel side-by-side build that won't clobber your real collection, use `./gradlew assembleDebug -PcustomSuffix="ai" -PcustomName="AnkiDroid AI"`.

### 5. History/undo in NoteEditor
- The NoteEditor does not maintain its own per-field text history/undo stack; text editing relies on the standard Android `EditText`/IME undo, and collection-level undo is handled by libanki's `ChangeManager`/`undoableOp` (which covers note save/add operations, not in-progress field keystrokes).
- **Simplest way to add per-field history:** Keep an in-memory `ArrayDeque<String>` (stack) per field, keyed by field index, in the fragment or a small `ViewModel`. Before each AI rewrite, push the current `fieldEditText.text` onto that field's stack; an "Undo AI edit" button pops the stack and calls `setContent(previous, replaceNewlines = false)`. A `ViewModel` with a `Map<Int, ArrayDeque<String>>` survives configuration changes; cap the stack depth (e.g. 20) to bound memory.

### 6. Card preview from NoteEditor
- The Preview action renders the note's generated cards. After GSoC 2024, the previewer was integrated into the Note Editor as a live/dual-pane preview. The Haze-jolt GSoC report README states: "Introduces a dual-pane layout for the Note Editor, integrating a live previewer… creating a interface for the noteeditor to send information on changes to the previewer."
- **Class:** The preview is rendered by `TemplatePreviewerFragment` (package `com.ichi2.anki.previewer`), backed by `TemplatePreviewerViewModel`, which renders card HTML through the Anki backend; the older standalone class was `CardTemplatePreviewer`. Arguments are passed via a parcelable arguments object (e.g. `TemplatePreviewerArguments`).
- **Programmatic trigger:** Yes. The previewer reads field/note state, so after you change field content you can re-launch or refresh the previewer with updated arguments. In the dual-pane design there is an interface that the NoteEditor uses to notify the previewer of changes (the "interface for the noteeditor to send information on changes to the previewer" cited above) — that is exactly the hook to call after an AI edit.

### 7. Secure API key storage
- **Status:** ✅ **Done in this fork** (`AIED-02` implemented).
- **Main implementation:** `AnkiDroid/src/main/java/com/ichi2/anki/ai/OpenRouterApiKeyStore.kt` + Advanced settings entry in `AdvancedSettingsFragment`.
- **Storage model:** API key is stored in `EncryptedSharedPreferences` (AES256-SIV keys / AES256-GCM values, master key in Android KeyStore). The implementation uses key name `open_router_api_key` and includes legacy fallback/migration from `anthropic_api_key`.
- **Dependency note:** this uses `androidx.security:security-crypto:1.1.0-alpha07` (deprecated Apr 2025 but still functional for this personal fork). Future option remains DataStore + Tink / maintained forks.
- **Reference snippet (same pattern used):**
```kotlin
val masterKey = MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
val prefs = EncryptedSharedPreferences.create(
    context, "ai_secure_prefs", masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
prefs.edit().putString("open_router_api_key", key).apply()
```

## Details

**Versions confirmed from `gradle/libs.versions.toml` (main):** `compileSdk = 36`, `minSdk = 24`, `targetSdk = 35`, `androidGradlePlugin = 9.0.1`, `kotlin = 2.3.21`, `coroutines = 1.10.2`, `okhttp = 5.3.2`, `ankiBackend = 0.1.64-anki25.09.2`, `material = 1.13.0`, `androidxFragmentKtx = 1.8.9`. These tell you the libraries you can rely on without adding anything for HTTP + coroutines + fragments + Material UI.

**Architecture path for the AI panel:** Add a new panel (a bottom sheet or a side pane reusing the GSoC dual-pane `ResizablePaneManager`) inside `note_editor_fragment.xml`; wire a button in the existing note-editor `Toolbar`; on tap, read the focused/selected `FieldEditText` content, POST to Claude inside `launchCatchingTask { withContext(Dispatchers.IO) { … } }`, then write the result back via `setContent(...)`, push the previous value to your per-field history stack, and notify the previewer interface to refresh.

**libanki note model:** The collection is accessed through `getColUnsafe`/`withCol`; a `Note` exposes its `fields` (list of strings). The save path uses `undoableOp { col.updateNote(note) }` so the edit is undoable through the standard Anki undo.

## Recommendations
1. **Stage 1 — get a build first.** Clone, open in Android Studio (let it install matching SDK 36 build-tools), set JDK 21 as the Gradle JDK, run `./gradlew assembleDebug`, then `adb install -r`. Don't touch dependency versions. *Benchmark:* a clean debug APK installs and launches the DeckPicker.
2. **Stage 2 — locate the integration points.** Read `NoteEditorFragment.kt` end-to-end and find: the `editFields` list, the field getters (`getCurrentFieldStrings`/`getFieldsText`), `FieldEditText.setContent`, and `saveNote()`. Confirm the `NoteEditorLauncher` variants and the previewer interface added in GSoC 2024.
3. **Stage 3 — add the panel and networking.** Reuse OkHttp + `launchCatchingTask`; build the panel in XML. Keep the Claude call in `Dispatchers.IO`. Write results back through `setContent`.
4. **Stage 4 — add history + secure storage.** Implement the per-field `ArrayDeque` history in a `ViewModel`; add encrypted storage for the key. *Benchmark:* undo restores prior field text; the key survives app restart and is unreadable in the prefs XML.
5. **Threshold to change approach:** If you intend to upstream this (PR to ankidroid/main), follow ktlint (`./gradlew ktlintFormat`) and lint (`./gradlew lintRelease`); if `security-crypto` deprecation warnings block CI, switch to DataStore+Tink or the `dev.spght:encryptedprefs` fork. If you only want a personal APK, the deprecated library is fine.

## Caveats
- The exact current method signatures inside `NoteEditorFragment.kt` (`saveNote()` vs `saveNoteWithProgress`, `getCurrentFieldStrings()` vs `getFieldsText()`, `FieldEditText.setContent(text, replaceNewlines)`), the `NoteEditorLauncher` member names, and the previewer arguments class are inferred from commit history, crash traces, and prior code rather than line-verified against the live `main` file — the GitHub source-file blob/raw URLs were not directly fetchable during research. **Verify these against the actual file after cloning** (`AnkiDroid/src/main/java/com/ichi2/anki/NoteEditorFragment.kt` and the `FieldEditText` source).
- AnkiDroid tracks Anki upstream closely and refactors frequently (the NoteEditor was an Activity until 2024); method names and the host activity (`SingleFragmentActivity` vs a possible dedicated `NoteEditorActivity`) may shift between releases. Pin your fork to a specific commit.
- `androidx.security:security-crypto` is deprecated as of April 2025 (`1.1.0-alpha07`); it works but is not future-proof.
- Storing an API key on-device is inherently lower-security than a backend proxy; EncryptedSharedPreferences mitigates casual extraction but not a rooted device. Consider a server-side proxy if the key is sensitive.