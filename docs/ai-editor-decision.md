# AnkiDroid AI Editor — Рішення та обґрунтування

---

## 1. Контекст, вимоги та обставини

### Проблема

Під час вивчення карточок на Android помічаю що карточка погано сформульована — незрозуміле формулювання, погане форматування, або треба розбити на дві. Виправити вручну на телефоні важко бо там HTML. Тому позначаю тегом з описом проблеми і відкладаю. Теги накопичуються, карточки так і не виправляються. Якість навчання знижується.

**Корінь проблеми:** найкращий момент для виправлення — прямо зараз під час рев'ю. Але тертя занадто велике.

### Що я хочу зробити

Редагувати карточки з AI-допомогою прямо в AnkiDroid під час або після рев'ю, без перемикання в інші аплікації.

**UX-флоу:**
```
Вчу → карточка не подобається → Редагувати
    → в редакторі є нова AI-панель
    → виділяю текст (або нічого → береться весь вміст поля)
    → описую що виправити (або вибираю preset-промпт)
    → AI застосовує зміни одразу в полі
    → натискаю існуючий Preview в AnkiDroid → бачу результат
        → подобається → Save
        → не подобається → Undo → редагую вручну або знову до AI
    → продовжую вчити
```

**Undo/Redo поведінка:**
```
Стан A → [AI] → Стан B → [AI] → Стан C   (cursor на C)
Undo → cursor на B   (C ще є в стеку)
Undo → cursor на A   (B, C ще є в стеку)
Redo → cursor на B
Нова AI дія → Стан D   (C перетерто, стек: A → B → D)
```

### Вимоги

| Пріоритет | Вимога |
|---|---|
| 🔴 Критична | Описати проблему текстом → AI виправляє поля |
| 🔴 Критична | Заготовлені промпти (presets): форматування, таблиця, спрощення |
| 🔴 Критична | Зберегти зміни назад в Anki |
| 🔴 Критична | Undo/Redo в межах поточної edit-сесії |
| 🟡 Важлива | Розбивка карточки на дві |
| 🟡 Важлива | Перевірка фактів через Microsoft Learn MCP |
| 🟢 Бажана | Зберігати переписку з AI локально |
| 🟢 Nice to have | Auto-suggest промптів на основі моїх патернів |

> **Примітка щодо Preview:** окремий кастомний preview не потрібен — використовується існуючий Preview в AnkiDroid. Undo/Redo замінює потребу в "підтвердити/відхилити" після AI.

### Мої обставини та ліміти

- **Платформа:** вчу на Android, карточки роблю на PC
- **Стек:** знаю React/JS — Kotlin/Android не знаю
- **AnkiDroid:** оновлюю рідко → merge conflicts при fork будуть рідко
- **Пристрій:** є фізичний телефон → емулятор не потрібен
- **Вже є:** Microsoft Learn MCP скіл для перевірки фактів
- **Вже є:** експорт карточок в MD → git repo (контроль якості)
- **Мета:** особистий інструмент, не публічний продукт → sideload APK достатньо

---

## 2. Обраний підхід: Fork AnkiDroid

### Суть

Зробити форк AnkiDroid, додати AI-панель безпосередньо в `NoteEditorFragment` — нативний Android редактор нотаток. Зібрати debug APK і встановити через adb на телефон.

### Чому цей підхід

- NoteEditor вже має все що потрібно: поля, превью, теги — треба лише **додати** AI-панель
- OkHttp (5.3.2) і Kotlin coroutines (1.10.2) **вже є** в проекті — мережевий запит до Anthropic API це ~20 рядків
- AI пише Kotlin так само як JS — мені не треба вчити мову щоб зрозуміти що відбувається
- Залишаюсь повністю в AnkiDroid, без перемикань
- Існуючий Preview використовується без змін — не треба писати власний

### Технічна картина

```
NoteEditorFragment.kt
    + кнопка "AI" в існуючому Toolbar
    + BottomSheet:
        [ Prompt field                         ]
        [ Format ] [ Simplify ] [ Table ]
        [ Apply AI ]      [ ← Undo ] [ Redo → ]
    + вхідні дані: виділений текст АБО весь вміст поля
    + launchCatchingTask { withContext(Dispatchers.IO) {
          OkHttp → api.anthropic.com
      }}
    + перед записом: history.push(currentContent)
    + FieldEditText.setContent(aiResult)
    + існуючий Preview button → показує результат без змін в коді
```

**Undo/Redo реалізація (Kotlin):**
```kotlin
class FieldHistory {
    private val states = mutableListOf<String>()
    private var cursor = -1

    fun push(state: String) {
        if (cursor < states.lastIndex)
            states.subList(cursor + 1, states.size).clear()
        states.add(state)
        cursor = states.lastIndex
    }

    fun undo(): String? = if (cursor > 0) states[--cursor] else null
    fun redo(): String? = if (cursor < states.lastIndex) states[++cursor] else null

    val canUndo get() = cursor > 0
    val canRedo get() = cursor < states.lastIndex
}
// Map<Int, FieldHistory> — один стек на кожне поле
```

**Ключові файли:**
- `AnkiDroid/src/main/java/com/ichi2/anki/NoteEditorFragment.kt` — єдиний файл де живе вся логіка
- `AnkiDroid/src/main/res/layout/note_editor_fragment.xml` — layout куди додається панель

**Build:**
```bash
./gradlew assembleDebug   # JDK 21, без Android Studio
adb install -r AnkiDroid/build/outputs/apk/debug/AnkiDroid-debug.apk
```

**Stages:**
1. Клонувати → збілдити → встановити (benchmark: DeckPicker запускається)
2. Знайти `editFields`, `setContent`, `saveNote` в NoteEditorFragment
3. Додати BottomSheet панель + OkHttp виклик до Claude
4. Додати `FieldHistory` per field + кнопки Undo/Redo
5. Додати EncryptedSharedPreferences для API ключа

---

## 3. Відкинуті варіанти

### ❌ AnkiDroid JS Note Editor addon

**Ідея:** написати JavaScript аддон що додає кнопку в тулбар редактора і викликає AI.

**Чому відкинули:** система аддонів використовує `js-evaluator-for-android` — виклик `AnkiJSFunction` строго синхронний. Функція повинна повернути рядок **негайно**. Async/await або fetch() до Anthropic API не працює — AnkiDroid читає порожній return value ще до того як прийде відповідь від AI. Архітектурно неможливо без хаків.

Додатково: feature в статусі "pre-alpha" з 2020-2021 року, практично не розвивається.

---

### ❌ Reviewer WebView JS (card template)

**Ідея:** вбудувати AI-панель прямо в HTML-шаблон карточки, fetch() до Anthropic під час рев'ю.

**Чому відкинули:** дві стекові проблеми:
1. AnkiDroid рендерить карточки з `file://` origin → opaque origin → `fetch()` до зовнішніх API заблокований на рівні Android
2. Навіть якщо обійти origin — CORS блокує виклики до api.anthropic.com

Плюс JS API reviewer не має жодного методу для запису в поля нотатки (`ankiUpdateNoteFields` не існує). Редагувати контент карточки звідти неможливо.

Також: потрібно додавати скрипт до кожного типу нотаток, проблема double-execution при переході питання→відповідь, JS поводиться по-різному в previewer і study mode.

---

### ❌ React PWA + AnkiconnectAndroid

**Ідея:** React-застосунок на телефоні, AnkiconnectAndroid як background service на localhost:8765, PWA викликає AnkiConnect API для читання і запису карточок.

**Чому відкинули:** вимагає перемикання між AnkiDroid і PWA під час рев'ю. AnkiconnectAndroid — неофіційний проект без гарантій, деякі функції вимагають alpha-версію AnkiDroid. Немає API для отримання "поточної карточки в рев'ю" — треба шукати вручну.

Технічно `updateNoteFields` є і працює — але context switching вбиває UX який я хочу.

---

### ❌ React PWA + AnkiConnect Desktop

**Ідея:** React PWA на телефоні підключається до AnkiConnect (десктопний аддон) через Wi-Fi.

**Чому відкинули:** вимагає щоб Anki Desktop був запущений на PC в тій самій мережі. Це не мобільне рішення — повна залежність від десктопу. Не працює поза домом, не працює якщо PC вимкнений.

---

### ❌ Синхронний XHR хак в Note Editor addon

**Ідея:** використати `XMLHttpRequest` з `async: false` щоб зробити "синхронний" HTTP запит всередині `AnkiJSFunction`.

**Чому відкинули:** Android блокує мережеві запити на main thread (`NetworkOnMainThreadException`). Навіть якщо б не блокував — UI AnkiDroid зависав би на весь час відповіді AI (1-5 секунд). Це хак, а не рішення.

---

*Документ відображає стан після дослідження всіх технічних варіантів*
