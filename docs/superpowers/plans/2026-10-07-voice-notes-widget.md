# Hlasové poznámky s AI — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Compose widget na stránce Trip, který jedním ťuknutím nahraje hlasovou poznámku, nechá ji přepsat Groq Whisperem a učesat + zařadit do kategorie Groq LLM, s offline frontou přes WorkManager.

**Architecture:** `VoiceRecorder` (MediaRecorder + `SilenceDetector`) zapíše `.m4a` → `VoiceNoteRepository` vloží Room řádek `PENDING` a naplánuje `ProcessNoteWorker` s podmínkou sítě → worker deleguje na čistý `NoteProcessor` (Whisper → LLM → uložit → smazat audio). UI (`VoiceNotesWidget`, `VoiceNotesScreen`) čte jen z `VoiceNotesViewModel` → repository. HTTP přes `HttpURLConnection` + `org.json` (stejně jako zbytek appky).

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose (BOM 2024.09.00), Hilt 2.51.1 (kapt), Room 2.6.1, WorkManager 2.9.1 + androidx.hilt:hilt-work 1.2.0, MediaRecorder, Groq OpenAI-kompatibilní REST API. Testy: JUnit 4, kotlinx-coroutines-test, OkHttp MockWebServer, org.json (JVM).

**Spec:** `docs/superpowers/specs/2026-10-07-voice-notes-widget-design.md`

## Global Constraints

- Balíček appky: `com.example.carlauncher`; minSdk 31, compileSdk/targetSdk 36; landscape tablet ~1143×686 dp.
- Groq modely (konstanty v `GroqClient`): přepis `whisper-large-v3`, úprava `openai/gpt-oss-120b`. **Ne** `llama-3.3-70b-versatile` (není ve free plánu), **ne** `whisper-large-v3-turbo`.
- Groq base URL: `https://api.groq.com/openai/v1`. Free plán; účet se nepovyšuje na Developer tier.
- `GROQ_API_KEY` jen v `local.properties` → `BuildConfig.GROQ_API_KEY` (default `""`). `local.properties` **nikdy** nestageovat/necommitovat (`git add` vždy s konkrétními cestami).
- Kategorie (pevná sada, pořadí): `IDEA 💡 Nápad`, `TASK ✅ Úkol`, `SHOPPING 🛒 Nákup`, `CAR 🚗 Auto`, `OTHER 📝 Ostatní`.
- Auto-stop po **5 000 ms** ticha, strop **120 000 ms**, nahrávky kratší než **1 000 ms** se zahazují.
- Max **5** pokusů workeru pro retryable chyby, pak `FAILED`.
- Audio: `filesDir/voice_notes/note_<yyyyMMdd_HHmmss>.m4a`; po `DONE` smazat.
- UI: `CarColors` tokeny, tmavé, touch targets ≥ 48 dp, mikrofon 96 dp, všechny texty česky.
- Long-press = 1500 ms, vzor z `ui/dock/DockBar.kt` (`withTimeout` + `PointerEventTimeoutCancellationException`).
- Commity: konvenční prefix (`feat(notes): …`), končí řádkem `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Gradle z kořene worktree přes Bash: `./gradlew …`.

## File Structure

```
gradle/libs.versions.toml                     MODIFY  verze + knihovny (work, hilt-work, test deps)
app/build.gradle.kts                          MODIFY  deps, GROQ_API_KEY, testOptions
app/src/main/AndroidManifest.xml              MODIFY  RECORD_AUDIO, vypnutí default WorkManager initializeru
app/src/main/java/com/example/carlauncher/
  CarLauncherApp.kt                           MODIFY  Configuration.Provider + startup cleanup
  di/VoiceNotesModule.kt                      CREATE  DB, DAO, NoteAi
  data/notes/
    NoteCategory.kt                           CREATE  enum kategorií + fromApi()
    SilenceDetector.kt                        CREATE  čistá logika auto-stopu
    RefinedNote.kt                            CREATE  RefinedNote + RefinedNoteParser
    GroqError.kt                              CREATE  typované chyby Groq
    NoteAi.kt                                 CREATE  rozhraní transcribe/refine
    GroqClient.kt                             CREATE  HTTP implementace NoteAi
    NoteStatus.kt                             CREATE  enum stavů
    VoiceNoteEntity.kt                        CREATE  Room entita
    VoiceNoteDao.kt                           CREATE  Room DAO
    VoiceNotesDatabase.kt                     CREATE  Room DB
    NoteProcessor.kt                          CREATE  orchestrace zpracování (testovatelná)
    VoiceRecorder.kt                          CREATE  MediaRecorder + stav
    VoiceNoteCleanup.kt                       CREATE  findOrphanAudio()
    ProcessNoteWorker.kt                      CREATE  HiltWorker → NoteProcessor
    VoiceNoteRepository.kt                    CREATE  vstupní bod pro UI
  ui/notes/
    NoteFormat.kt                             CREATE  formátování času/délky/řádku + barvy kategorií
    VoiceNotesViewModel.kt                    CREATE
    VoiceNotesScreen.kt                       CREATE  celoobrazovkový seznam
    VoiceNotesWidget.kt                       CREATE  dlaždice na Trip stránce
  ui/widgets/TripScreen.kt                    MODIFY  třetí dlaždice + přepnutí na seznam
app/src/test/java/com/example/carlauncher/
  data/notes/NoteCategoryTest.kt              CREATE
  data/notes/SilenceDetectorTest.kt           CREATE
  data/notes/RefinedNoteParserTest.kt         CREATE
  data/notes/GroqClientTest.kt                CREATE
  data/notes/FakeVoiceNoteDao.kt              CREATE  test double
  data/notes/NoteProcessorTest.kt             CREATE
  data/notes/VoiceNoteCleanupTest.kt          CREATE
  ui/notes/NoteFormatTest.kt                  CREATE
CLAUDE.md                                     MODIFY  sekce Module: Voice Notes + logcat tagy
```

---

### Task 1: Testovací infrastruktura + `NoteCategory` + `SilenceDetector`

Projekt zatím nemá žádné unit testy ani testové závislosti — tento task je zavádí.

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/example/carlauncher/data/notes/NoteCategory.kt`
- Create: `app/src/main/java/com/example/carlauncher/data/notes/SilenceDetector.kt`
- Test: `app/src/test/java/com/example/carlauncher/data/notes/NoteCategoryTest.kt`
- Test: `app/src/test/java/com/example/carlauncher/data/notes/SilenceDetectorTest.kt`

**Interfaces:**
- Produces: `enum class NoteCategory(val emoji: String, val label: String) { IDEA, TASK, SHOPPING, CAR, OTHER }` + `NoteCategory.fromApi(value: String?): NoteCategory`
- Produces: `data class SilenceStatus(val elapsedMs: Long, val silenceMs: Long, val shouldStop: Boolean)`; `class SilenceDetector(amplitudeThreshold: Int = 1_500, silenceLimitMs: Long = 5_000, maxDurationMs: Long = 120_000)` s `fun start(nowMs: Long)`, `fun onSample(amplitude: Int, nowMs: Long): SilenceStatus`; konstanty `SilenceDetector.SILENCE_LIMIT_MS`, `MAX_DURATION_MS`, `DEFAULT_THRESHOLD`.

- [ ] **Step 1: Přidat verze a knihovny do `gradle/libs.versions.toml`**

Do sekce `[versions]` (za `mlkitText = "16.0.1"`) přidat:

```toml
work = "2.9.1"
hiltWork = "1.2.0"
junit = "4.13.2"
okhttp = "4.12.0"
orgJson = "20240303"
```

Do sekce `[libraries]` (za `mlkit-text-recognition`) přidat:

```toml
androidx-work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "work" }
androidx-hilt-work        = { group = "androidx.hilt", name = "hilt-work", version.ref = "hiltWork" }
androidx-hilt-compiler    = { group = "androidx.hilt", name = "hilt-compiler", version.ref = "hiltWork" }

junit                = { group = "junit", name = "junit", version.ref = "junit" }
coroutines-test      = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
okhttp-mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver", version.ref = "okhttp" }
org-json             = { group = "org.json", name = "json", version.ref = "orgJson" }
```

- [ ] **Step 2: Testové závislosti a `testOptions` v `app/build.gradle.kts`**

Do bloku `android { … }` za `buildFeatures { … }` přidat:

```kotlin
    testOptions {
        // android.util.Log apod. vrací v JVM testech default místo "Method not mocked"
        unitTests.isReturnDefaultValues = true
    }
```

Na konec bloku `dependencies { … }` přidat:

```kotlin
    // Unit testy (JVM) — org.json přebíjí prázdné stuby z android.jar
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.org.json)
```

- [ ] **Step 3: Napsat padající testy**

`app/src/test/java/com/example/carlauncher/data/notes/NoteCategoryTest.kt`:

```kotlin
package com.example.carlauncher.data.notes

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteCategoryTest {
    @Test fun fromApi_matchesNameCaseInsensitive() {
        assertEquals(NoteCategory.TASK, NoteCategory.fromApi("task"))
        assertEquals(NoteCategory.SHOPPING, NoteCategory.fromApi(" SHOPPING "))
    }

    @Test fun fromApi_unknownOrNull_isOther() {
        assertEquals(NoteCategory.OTHER, NoteCategory.fromApi("nonsense"))
        assertEquals(NoteCategory.OTHER, NoteCategory.fromApi(null))
    }
}
```

`app/src/test/java/com/example/carlauncher/data/notes/SilenceDetectorTest.kt`:

```kotlin
package com.example.carlauncher.data.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SilenceDetectorTest {
    private val quiet = 100
    private val loud = 5_000

    @Test fun stopsAfterFiveSecondsOfSilence() {
        val d = SilenceDetector().apply { start(0) }
        assertFalse(d.onSample(quiet, 4_800).shouldStop)
        assertTrue(d.onSample(quiet, 5_000).shouldStop)
    }

    @Test fun loudSampleResetsSilence() {
        val d = SilenceDetector().apply { start(0) }
        d.onSample(loud, 3_000)
        val beforeLimit = d.onSample(quiet, 7_900)
        assertEquals(4_900, beforeLimit.silenceMs)
        assertFalse(beforeLimit.shouldStop)
        assertTrue(d.onSample(quiet, 8_000).shouldStop)
    }

    @Test fun stopsAtMaxDurationEvenWhenTalking() {
        val d = SilenceDetector().apply { start(0) }
        for (t in 1_000L..119_000L step 1_000L) {
            assertFalse("t=$t", d.onSample(loud, t).shouldStop)
        }
        val last = d.onSample(loud, 120_000)
        assertEquals(120_000, last.elapsedMs)
        assertTrue(last.shouldStop)
    }
}
```

- [ ] **Step 4: Spustit testy — musí selhat**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.carlauncher.data.notes.*"`
Expected: FAIL — kompilace: `Unresolved reference: NoteCategory` / `SilenceDetector`.

- [ ] **Step 5: Implementace**

`app/src/main/java/com/example/carlauncher/data/notes/NoteCategory.kt`:

```kotlin
package com.example.carlauncher.data.notes

/** Pevná sada kategorií hlasových poznámek. `name` je zároveň hodnota, kterou vrací LLM. */
enum class NoteCategory(val emoji: String, val label: String) {
    IDEA("💡", "Nápad"),
    TASK("✅", "Úkol"),
    SHOPPING("🛒", "Nákup"),
    CAR("🚗", "Auto"),
    OTHER("📝", "Ostatní");

    companion object {
        fun fromApi(value: String?): NoteCategory {
            val v = value?.trim() ?: return OTHER
            return entries.firstOrNull { it.name.equals(v, ignoreCase = true) } ?: OTHER
        }
    }
}
```

`app/src/main/java/com/example/carlauncher/data/notes/SilenceDetector.kt`:

```kotlin
package com.example.carlauncher.data.notes

data class SilenceStatus(val elapsedMs: Long, val silenceMs: Long, val shouldStop: Boolean)

/**
 * Čistá logika auto-stopu nahrávání: ticho = amplituda pod prahem. Hlasitý vzorek nuluje čítač
 * ticha. Stop při [silenceLimitMs] ticha nebo [maxDurationMs] celkem. Čas dodává volající
 * (SystemClock.elapsedRealtime v appce), takže je to testovatelné bez zařízení.
 */
class SilenceDetector(
    private val amplitudeThreshold: Int = DEFAULT_THRESHOLD,
    private val silenceLimitMs: Long = SILENCE_LIMIT_MS,
    private val maxDurationMs: Long = MAX_DURATION_MS,
) {
    private var startedAt = 0L
    private var lastLoudAt = 0L

    fun start(nowMs: Long) {
        startedAt = nowMs
        lastLoudAt = nowMs
    }

    fun onSample(amplitude: Int, nowMs: Long): SilenceStatus {
        if (amplitude >= amplitudeThreshold) lastLoudAt = nowMs
        val elapsed = nowMs - startedAt
        val silence = nowMs - lastLoudAt
        return SilenceStatus(
            elapsedMs = elapsed,
            silenceMs = silence,
            shouldStop = silence >= silenceLimitMs || elapsed >= maxDurationMs,
        )
    }

    companion object {
        /** MediaRecorder.getMaxAmplitude() škála 0..32767; doladit na zařízení. */
        const val DEFAULT_THRESHOLD = 1_500
        const val SILENCE_LIMIT_MS = 5_000L
        const val MAX_DURATION_MS = 120_000L
    }
}
```

- [ ] **Step 6: Spustit testy — musí projít**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.carlauncher.data.notes.*"`
Expected: `BUILD SUCCESSFUL`, 5 testů PASS.

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/com/example/carlauncher/data/notes/NoteCategory.kt app/src/main/java/com/example/carlauncher/data/notes/SilenceDetector.kt app/src/test/java/com/example/carlauncher/data/notes/NoteCategoryTest.kt app/src/test/java/com/example/carlauncher/data/notes/SilenceDetectorTest.kt
git commit -m "feat(notes): add note categories, silence detector and unit test setup

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: `RefinedNote` + `RefinedNoteParser`

**Files:**
- Create: `app/src/main/java/com/example/carlauncher/data/notes/RefinedNote.kt`
- Test: `app/src/test/java/com/example/carlauncher/data/notes/RefinedNoteParserTest.kt`

**Interfaces:**
- Consumes: `NoteCategory.fromApi(String?)` (Task 1)
- Produces: `data class RefinedNote(val title: String, val text: String, val category: NoteCategory)`; `object RefinedNoteParser { fun parse(content: String?, rawTranscript: String): RefinedNote; fun fallback(rawTranscript: String): RefinedNote }`

- [ ] **Step 1: Napsat padající test**

`app/src/test/java/com/example/carlauncher/data/notes/RefinedNoteParserTest.kt`:

```kotlin
package com.example.carlauncher.data.notes

import org.junit.Assert.assertEquals
import org.junit.Test

class RefinedNoteParserTest {
    private val raw = "ehm tak jedna dva tři čtyři pět šest"

    @Test fun validJson_isParsed() {
        val r = RefinedNoteParser.parse(
            """{"title":"Servis","text":"Zavolat do servisu.","category":"TASK"}""", raw,
        )
        assertEquals(RefinedNote("Servis", "Zavolat do servisu.", NoteCategory.TASK), r)
    }

    @Test fun unknownCategory_becomesOtherButKeepsText() {
        val r = RefinedNoteParser.parse("""{"title":"A","text":"B","category":"FOOD"}""", raw)
        assertEquals(RefinedNote("A", "B", NoteCategory.OTHER), r)
    }

    @Test fun missingText_usesFallback() {
        val r = RefinedNoteParser.parse("""{"title":"A","category":"TASK"}""", raw)
        assertEquals(RefinedNoteParser.fallback(raw), r)
    }

    @Test fun invalidJsonOrNull_usesFallback() {
        assertEquals(RefinedNoteParser.fallback(raw), RefinedNoteParser.parse("not json", raw))
        assertEquals(RefinedNoteParser.fallback(raw), RefinedNoteParser.parse(null, raw))
    }

    @Test fun fallback_titleIsFirstFiveWordsWithEllipsis() {
        val r = RefinedNoteParser.fallback("  jedna dva  tři čtyři pět šest ")
        assertEquals("jedna dva tři čtyři pět…", r.title)
        assertEquals("jedna dva  tři čtyři pět šest", r.text)
        assertEquals(NoteCategory.OTHER, r.category)
    }

    @Test fun fallback_shortTranscriptHasNoEllipsis() {
        assertEquals("krátká věta", RefinedNoteParser.fallback("krátká věta").title)
    }
}
```

- [ ] **Step 2: Spustit — musí selhat**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.carlauncher.data.notes.RefinedNoteParserTest"`
Expected: FAIL — `Unresolved reference: RefinedNoteParser`.

- [ ] **Step 3: Implementace**

`app/src/main/java/com/example/carlauncher/data/notes/RefinedNote.kt`:

```kotlin
package com.example.carlauncher.data.notes

import org.json.JSONObject

data class RefinedNote(val title: String, val text: String, val category: NoteCategory)

/**
 * Převádí `message.content` z LLM na [RefinedNote]. Cokoliv neúplného → [fallback] ze surového
 * přepisu, protože i neučesaný přepis má pro řidiče větší cenu než chyba.
 */
object RefinedNoteParser {
    private const val FALLBACK_TITLE_WORDS = 5

    fun parse(content: String?, rawTranscript: String): RefinedNote {
        val json = content?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?: return fallback(rawTranscript)
        val title = json.optString("title").trim()
        val text = json.optString("text").trim()
        if (title.isEmpty() || text.isEmpty()) return fallback(rawTranscript)
        return RefinedNote(title, text, NoteCategory.fromApi(json.optString("category")))
    }

    fun fallback(rawTranscript: String): RefinedNote {
        val clean = rawTranscript.trim()
        val words = clean.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val title = words.take(FALLBACK_TITLE_WORDS).joinToString(" ") +
            if (words.size > FALLBACK_TITLE_WORDS) "…" else ""
        return RefinedNote(title, clean, NoteCategory.OTHER)
    }
}
```

- [ ] **Step 4: Spustit — musí projít**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.carlauncher.data.notes.RefinedNoteParserTest"`
Expected: 6 testů PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/carlauncher/data/notes/RefinedNote.kt app/src/test/java/com/example/carlauncher/data/notes/RefinedNoteParserTest.kt
git commit -m "feat(notes): parse LLM note output with transcript fallback

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: `GroqClient` (Whisper + chat completions)

**Files:**
- Create: `app/src/main/java/com/example/carlauncher/data/notes/GroqError.kt`
- Create: `app/src/main/java/com/example/carlauncher/data/notes/NoteAi.kt`
- Create: `app/src/main/java/com/example/carlauncher/data/notes/GroqClient.kt`
- Test: `app/src/test/java/com/example/carlauncher/data/notes/GroqClientTest.kt`

**Interfaces:**
- Consumes: `RefinedNote`, `RefinedNoteParser.parse(content, raw)` (Task 2); `NoteCategory` (Task 1)
- Produces:
  - `sealed class GroqError : Exception` s podtřídami `Network(cause: Throwable)`, `RateLimited()`, `Server(code: Int)`, `Auth(code: Int)`, `Client(code: Int, body: String)` a `val isRetryable: Boolean` (true pro Network/RateLimited/Server). `message` je česky (zobrazí se uživateli).
  - `interface NoteAi { suspend fun transcribe(audio: File): String; suspend fun refine(rawTranscript: String): RefinedNote }`
  - `class GroqClient(apiKey: String, baseUrl: String = GroqClient.DEFAULT_BASE_URL) : NoteAi`; konstanty `DEFAULT_BASE_URL`, `TRANSCRIBE_MODEL = "whisper-large-v3"`, `REFINE_MODEL = "openai/gpt-oss-120b"`.

- [ ] **Step 1: Napsat padající test**

`app/src/test/java/com/example/carlauncher/data/notes/GroqClientTest.kt`:

```kotlin
package com.example.carlauncher.data.notes

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GroqClientTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var server: MockWebServer
    private lateinit var client: GroqClient

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        client = GroqClient("test-key", server.url("/openai/v1").toString().trimEnd('/'))
    }

    @After fun tearDown() {
        runCatching { server.shutdown() }
    }

    private var fileCounter = 0
    private fun audio(name: String = "note_${fileCounter++}.m4a") =
        tmp.newFile(name).apply { writeBytes(byteArrayOf(1, 2, 3)) }

    @Test fun transcribe_sendsCzechMultipartAndReturnsTrimmedText() = runTest {
        server.enqueue(MockResponse().setBody("""{"text":"  Ahoj světe  "}"""))

        assertEquals("Ahoj světe", client.transcribe(audio("note_test.m4a")))

        val req = server.takeRequest()
        assertEquals("/openai/v1/audio/transcriptions", req.path)
        assertEquals("Bearer test-key", req.getHeader("Authorization"))
        assertTrue(req.getHeader("Content-Type")!!.startsWith("multipart/form-data; boundary="))
        val body = req.body.readUtf8()
        assertTrue(body.contains("name=\"model\"\r\n\r\nwhisper-large-v3\r\n"))
        assertTrue(body.contains("name=\"language\"\r\n\r\ncs\r\n"))
        assertTrue(body.contains("filename=\"note_test.m4a\""))
    }

    @Test fun refine_requestsStrictSchemaAndParsesContent() = runTest {
        val content = """{"title":"Servis","text":"Zavolat do servisu.","category":"TASK"}"""
        val response = JSONObject().put(
            "choices",
            org.json.JSONArray().put(JSONObject().put("message", JSONObject().put("content", content))),
        )
        server.enqueue(MockResponse().setBody(response.toString()))

        val r = client.refine("zavolat ehm do servisu")

        assertEquals(RefinedNote("Servis", "Zavolat do servisu.", NoteCategory.TASK), r)
        val req = server.takeRequest()
        assertEquals("/openai/v1/chat/completions", req.path)
        val sent = JSONObject(req.body.readUtf8())
        assertEquals("openai/gpt-oss-120b", sent.getString("model"))
        val format = sent.getJSONObject("response_format")
        assertEquals("json_schema", format.getString("type"))
        assertTrue(format.getJSONObject("json_schema").getBoolean("strict"))
        assertEquals("zavolat ehm do servisu",
            sent.getJSONArray("messages").getJSONObject(1).getString("content"))
    }

    @Test fun refine_malformedResponse_fallsBackToTranscript() = runTest {
        server.enqueue(MockResponse().setBody("""{"choices":[]}"""))
        assertEquals(RefinedNoteParser.fallback("surový text"), client.refine("surový text"))
    }

    @Test fun httpErrors_mapToGroqErrors() = runTest {
        suspend fun errorFor(code: Int): Throwable? {
            server.enqueue(MockResponse().setResponseCode(code).setBody("""{"error":"x"}"""))
            return runCatching { client.transcribe(audio()) }.exceptionOrNull()
        }
        assertTrue(errorFor(401) is GroqError.Auth)
        assertTrue(errorFor(429) is GroqError.RateLimited)
        assertTrue(errorFor(503) is GroqError.Server)
        assertTrue(errorFor(400) is GroqError.Client)
    }

    @Test fun connectionFailure_isNetworkErrorAndRetryable() = runTest {
        server.shutdown()
        val e = runCatching { client.refine("x") }.exceptionOrNull()
        assertTrue(e is GroqError.Network)
        assertTrue((e as GroqError).isRetryable)
    }
}
```

- [ ] **Step 2: Spustit — musí selhat**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.carlauncher.data.notes.GroqClientTest"`
Expected: FAIL — `Unresolved reference: GroqClient`.

- [ ] **Step 3: Implementace**

`app/src/main/java/com/example/carlauncher/data/notes/GroqError.kt`:

```kotlin
package com.example.carlauncher.data.notes

/** Chyby volání Groq. `message` je česky — zobrazuje se u poznámky ve stavu FAILED. */
sealed class GroqError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Network(cause: Throwable) : GroqError("Bez připojení ke Groq", cause)
    class RateLimited : GroqError("Překročen denní limit Groq (429)")
    class Server(val code: Int) : GroqError("Chyba serveru Groq ($code)")
    class Auth(val code: Int) : GroqError("Neplatný Groq API klíč ($code)")
    class Client(val code: Int, body: String) : GroqError("Chyba požadavku Groq ($code): ${body.take(200)}")

    /** Síť, rate limit a 5xx se zkouší znovu (WorkManager backoff); klíč a 4xx ne. */
    val isRetryable: Boolean get() = this is Network || this is RateLimited || this is Server
}
```

`app/src/main/java/com/example/carlauncher/data/notes/NoteAi.kt`:

```kotlin
package com.example.carlauncher.data.notes

import java.io.File

/** AI zpracování poznámky. Hází [GroqError]. Rozhraní kvůli fake implementaci v testech. */
interface NoteAi {
    suspend fun transcribe(audio: File): String
    suspend fun refine(rawTranscript: String): RefinedNote
}
```

`app/src/main/java/com/example/carlauncher/data/notes/GroqClient.kt`:

```kotlin
package com.example.carlauncher.data.notes

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Groq REST (OpenAI-kompatibilní) přes HttpURLConnection — stejně jako zbytek appky, bez nové
 * síťové knihovny. Bezstavové; [apiKey] z BuildConfig.GROQ_API_KEY (viz VoiceNotesModule).
 */
class GroqClient(
    private val apiKey: String,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : NoteAi {

    override suspend fun transcribe(audio: File): String = withContext(Dispatchers.IO) {
        val boundary = "----CarLauncher${System.nanoTime()}"
        val body = post("audio/transcriptions", "multipart/form-data; boundary=$boundary") { out ->
            fun field(name: String, value: String) = out.write(
                "--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n"
                    .toByteArray(),
            )
            field("model", TRANSCRIBE_MODEL)
            field("language", "cs")
            field("response_format", "json")
            out.write(
                ("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; " +
                    "filename=\"${audio.name}\"\r\nContent-Type: audio/mp4\r\n\r\n").toByteArray(),
            )
            audio.inputStream().use { it.copyTo(out) }
            out.write("\r\n--$boundary--\r\n".toByteArray())
        }
        runCatching { JSONObject(body).getString("text").trim() }
            .getOrElse { throw GroqError.Server(200) }
    }

    override suspend fun refine(rawTranscript: String): RefinedNote = withContext(Dispatchers.IO) {
        val request = JSONObject()
            .put("model", REFINE_MODEL)
            .put("temperature", 0.2)
            .put("reasoning_effort", "low")
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                    .put(JSONObject().put("role", "user").put("content", rawTranscript)),
            )
            .put("response_format", responseFormat())
        val body = post("chat/completions", "application/json") {
            it.write(request.toString().toByteArray())
        }
        val content = runCatching {
            JSONObject(body).getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content")
        }.getOrNull()
        RefinedNoteParser.parse(content, rawTranscript)
    }

    private fun post(path: String, contentType: String, writeBody: (OutputStream) -> Unit): String {
        val conn = try {
            URL("$baseUrl/$path").openConnection() as HttpURLConnection
        } catch (e: IOException) {
            throw GroqError.Network(e)
        }
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("Content-Type", contentType)
            conn.outputStream.use(writeBody)
            val code = conn.responseCode
            if (code in 200..299) return conn.inputStream.bufferedReader().use { it.readText() }
            val error = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            throw when {
                code == 401 || code == 403 -> GroqError.Auth(code)
                code == 429 -> GroqError.RateLimited()
                code >= 500 -> GroqError.Server(code)
                else -> GroqError.Client(code, error)
            }
        } catch (e: IOException) {
            throw GroqError.Network(e)
        } finally {
            conn.disconnect()
        }
    }

    private fun responseFormat(): JSONObject {
        val categories = JSONArray().apply { NoteCategory.entries.forEach { put(it.name) } }
        val schema = JSONObject()
            .put("type", "object")
            .put(
                "properties",
                JSONObject()
                    .put("title", JSONObject().put("type", "string"))
                    .put("text", JSONObject().put("type", "string"))
                    .put("category", JSONObject().put("type", "string").put("enum", categories)),
            )
            .put("required", JSONArray().put("title").put("text").put("category"))
            .put("additionalProperties", false)
        return JSONObject()
            .put("type", "json_schema")
            .put(
                "json_schema",
                JSONObject().put("name", "voice_note").put("strict", true).put("schema", schema),
            )
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.groq.com/openai/v1"
        const val TRANSCRIBE_MODEL = "whisper-large-v3"
        const val REFINE_MODEL = "openai/gpt-oss-120b"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 60_000

        private val SYSTEM_PROMPT = """
            Jsi asistent, který upravuje diktované poznámky řidiče. Dostaneš surový přepis řeči v češtině.
            Vrať JSON s poli:
            - "title": výstižný nadpis, nejvýše 6 slov, bez tečky na konci.
            - "text": učesaný text poznámky — odstraň vycpávková slova (ehm, jako, prostě…), opakování
              a přeřeknutí, doplň interpunkci a velká písmena. Význam neměň, nic nepřidávej.
            - "category": IDEA (nápad, myšlenka), TASK (úkol, něco udělat, zavolat), SHOPPING (koupit),
              CAR (auto, servis, tankování, pneumatiky), OTHER (vše ostatní).
        """.trimIndent()
    }
}
```

- [ ] **Step 4: Spustit — musí projít**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.carlauncher.data.notes.GroqClientTest"`
Expected: 5 testů PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/carlauncher/data/notes/GroqError.kt app/src/main/java/com/example/carlauncher/data/notes/NoteAi.kt app/src/main/java/com/example/carlauncher/data/notes/GroqClient.kt app/src/test/java/com/example/carlauncher/data/notes/GroqClientTest.kt
git commit -m "feat(notes): add Groq client for Whisper transcription and note refinement

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Room vrstva + `NoteProcessor` + DI modul

**Files:**
- Modify: `app/build.gradle.kts` (pole `GROQ_API_KEY`)
- Create: `app/src/main/java/com/example/carlauncher/data/notes/NoteStatus.kt`
- Create: `app/src/main/java/com/example/carlauncher/data/notes/VoiceNoteEntity.kt`
- Create: `app/src/main/java/com/example/carlauncher/data/notes/VoiceNoteDao.kt`
- Create: `app/src/main/java/com/example/carlauncher/data/notes/VoiceNotesDatabase.kt`
- Create: `app/src/main/java/com/example/carlauncher/data/notes/NoteProcessor.kt`
- Create: `app/src/main/java/com/example/carlauncher/di/VoiceNotesModule.kt`
- Test: `app/src/test/java/com/example/carlauncher/data/notes/FakeVoiceNoteDao.kt`
- Test: `app/src/test/java/com/example/carlauncher/data/notes/NoteProcessorTest.kt`

**Interfaces:**
- Consumes: `NoteAi`, `GroqError`, `GroqClient` (Task 3); `RefinedNote` (Task 2); `NoteCategory` (Task 1)
- Produces:
  - `enum class NoteStatus { PENDING, PROCESSING, DONE, FAILED }`
  - `data class VoiceNoteEntity(id: Long = 0, createdAt: Long, durationMs: Long, status: NoteStatus = PENDING, audioPath: String?, rawTranscript: String? = null, title: String? = null, text: String? = null, category: NoteCategory? = null, errorMessage: String? = null, attempts: Int = 0)` — tabulka `voice_notes`
  - `interface VoiceNoteDao { fun observeAll(): Flow<List<VoiceNoteEntity>>; suspend fun getById(id: Long): VoiceNoteEntity?; suspend fun getAll(): List<VoiceNoteEntity>; suspend fun insert(note: VoiceNoteEntity): Long; suspend fun update(note: VoiceNoteEntity); suspend fun deleteById(id: Long) }`
  - `enum class ProcessOutcome { DONE, DELETED, RETRY, FAILED }`; `class NoteProcessor @Inject constructor(dao: VoiceNoteDao, ai: NoteAi) { suspend fun process(noteId: Long): ProcessOutcome }`; `NoteProcessor.MAX_ATTEMPTS = 5`
  - Hilt: `VoiceNotesDatabase`, `VoiceNoteDao`, `NoteAi` jsou injektovatelné.

- [ ] **Step 1: `GROQ_API_KEY` do BuildConfig**

V `app/build.gradle.kts` pod řádek `val incidentRecorderEnabled: String = …` přidat:

```kotlin

// Groq API klíč pro hlasové poznámky (free plán) — jen v local.properties, nikdy v gitu.
// Prázdný → widget ukáže "Chybí Groq API klíč" a nenahrává.
val groqApiKey: String = localProps.getProperty("GROQ_API_KEY", "")
```

a do `defaultConfig { … }` pod `buildConfigField("boolean", "INCIDENT_RECORDER_ENABLED", …)` přidat:

```kotlin
        buildConfigField("String", "GROQ_API_KEY", "\"$groqApiKey\"")
```

- [ ] **Step 2: Entita, stav, DAO, databáze**

`app/src/main/java/com/example/carlauncher/data/notes/NoteStatus.kt`:

```kotlin
package com.example.carlauncher.data.notes

enum class NoteStatus { PENDING, PROCESSING, DONE, FAILED }
```

`app/src/main/java/com/example/carlauncher/data/notes/VoiceNoteEntity.kt`:

```kotlin
package com.example.carlauncher.data.notes

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Enumy ukládá Room 2.6 sám jako TEXT (název konstanty) — žádné TypeConvertery. */
@Entity(tableName = "voice_notes")
data class VoiceNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val durationMs: Long,
    val status: NoteStatus = NoteStatus.PENDING,
    /** null po úspěšném zpracování (soubor smazán). */
    val audioPath: String?,
    /** Výstup Whisperu — při opakování se přepis znovu nevolá. */
    val rawTranscript: String? = null,
    val title: String? = null,
    val text: String? = null,
    val category: NoteCategory? = null,
    val errorMessage: String? = null,
    val attempts: Int = 0,
)
```

`app/src/main/java/com/example/carlauncher/data/notes/VoiceNoteDao.kt`:

```kotlin
package com.example.carlauncher.data.notes

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VoiceNoteDao {
    @Query("SELECT * FROM voice_notes ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<VoiceNoteEntity>>

    @Query("SELECT * FROM voice_notes WHERE id = :id")
    suspend fun getById(id: Long): VoiceNoteEntity?

    @Query("SELECT * FROM voice_notes")
    suspend fun getAll(): List<VoiceNoteEntity>

    @Insert
    suspend fun insert(note: VoiceNoteEntity): Long

    @Update
    suspend fun update(note: VoiceNoteEntity)

    @Query("DELETE FROM voice_notes WHERE id = :id")
    suspend fun deleteById(id: Long)
}
```

`app/src/main/java/com/example/carlauncher/data/notes/VoiceNotesDatabase.kt`:

```kotlin
package com.example.carlauncher.data.notes

import androidx.room.Database
import androidx.room.RoomDatabase

/** Samostatná DB — nesahá na verzi/migrace TripDatabase. */
@Database(entities = [VoiceNoteEntity::class], version = 1, exportSchema = false)
abstract class VoiceNotesDatabase : RoomDatabase() {
    abstract fun voiceNoteDao(): VoiceNoteDao
}
```

- [ ] **Step 3: Napsat fake DAO a padající testy procesoru**

`app/src/test/java/com/example/carlauncher/data/notes/FakeVoiceNoteDao.kt`:

```kotlin
package com.example.carlauncher.data.notes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeVoiceNoteDao : VoiceNoteDao {
    val rows = linkedMapOf<Long, VoiceNoteEntity>()
    private var nextId = 1L
    private val flow = MutableStateFlow<List<VoiceNoteEntity>>(emptyList())

    private fun emit() { flow.value = rows.values.sortedByDescending { it.createdAt } }

    override fun observeAll(): Flow<List<VoiceNoteEntity>> = flow
    override suspend fun getById(id: Long) = rows[id]
    override suspend fun getAll() = rows.values.toList()
    override suspend fun insert(note: VoiceNoteEntity): Long {
        val id = nextId++
        rows[id] = note.copy(id = id); emit(); return id
    }
    override suspend fun update(note: VoiceNoteEntity) {
        if (rows.containsKey(note.id)) { rows[note.id] = note; emit() }
    }
    override suspend fun deleteById(id: Long) { rows.remove(id); emit() }
}
```

`app/src/test/java/com/example/carlauncher/data/notes/NoteProcessorTest.kt`:

```kotlin
package com.example.carlauncher.data.notes

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class NoteProcessorTest {
    @get:Rule val tmp = TemporaryFolder()

    private class FakeAi : NoteAi {
        var transcribeCalls = 0
        var transcribe: () -> String = { "zavolat ehm do servisu" }
        var refine: (String) -> RefinedNote = { RefinedNote("Servis", "Zavolat do servisu.", NoteCategory.TASK) }
        override suspend fun transcribe(audio: File): String { transcribeCalls++; return transcribe() }
        override suspend fun refine(rawTranscript: String) = refine.invoke(rawTranscript)
    }

    private val dao = FakeVoiceNoteDao()
    private val ai = FakeAi()
    private val processor = NoteProcessor(dao, ai)

    private suspend fun insertNote(
        attempts: Int = 0,
        rawTranscript: String? = null,
    ): Pair<Long, File> {
        val audio = tmp.newFile().apply { writeBytes(byteArrayOf(1)) }
        val id = dao.insert(
            VoiceNoteEntity(
                createdAt = 1L, durationMs = 3_000, audioPath = audio.absolutePath,
                attempts = attempts, rawTranscript = rawTranscript,
            ),
        )
        return id to audio
    }

    @Test fun happyPath_storesRefinedNoteAndDeletesAudio() = runTest {
        val (id, audio) = insertNote()

        assertEquals(ProcessOutcome.DONE, processor.process(id))

        val n = dao.rows.getValue(id)
        assertEquals(NoteStatus.DONE, n.status)
        assertEquals("Servis", n.title)
        assertEquals("Zavolat do servisu.", n.text)
        assertEquals(NoteCategory.TASK, n.category)
        assertEquals("zavolat ehm do servisu", n.rawTranscript)
        assertNull(n.audioPath)
        assertFalse(audio.exists())
    }

    @Test fun networkError_retriesAndKeepsPending() = runTest {
        val (id, audio) = insertNote()
        ai.transcribe = { throw GroqError.Network(IOException("offline")) }

        assertEquals(ProcessOutcome.RETRY, processor.process(id))

        val n = dao.rows.getValue(id)
        assertEquals(NoteStatus.PENDING, n.status)
        assertEquals(1, n.attempts)
        assertTrue(audio.exists())
    }

    @Test fun authError_failsAndKeepsAudio() = runTest {
        val (id, audio) = insertNote()
        ai.transcribe = { throw GroqError.Auth(401) }

        assertEquals(ProcessOutcome.FAILED, processor.process(id))

        val n = dao.rows.getValue(id)
        assertEquals(NoteStatus.FAILED, n.status)
        assertEquals("Neplatný Groq API klíč (401)", n.errorMessage)
        assertTrue(audio.exists())
    }

    @Test fun retryableErrorOnFifthAttempt_fails() = runTest {
        val (id, _) = insertNote(attempts = NoteProcessor.MAX_ATTEMPTS - 1)
        ai.transcribe = { throw GroqError.Server(503) }

        assertEquals(ProcessOutcome.FAILED, processor.process(id))
        assertEquals(NoteStatus.FAILED, dao.rows.getValue(id).status)
    }

    @Test fun existingTranscript_skipsTranscription() = runTest {
        val (id, _) = insertNote(rawTranscript = "už přepsáno")
        var refinedInput: String? = null
        ai.refine = { refinedInput = it; RefinedNote("A", "B", NoteCategory.IDEA) }

        assertEquals(ProcessOutcome.DONE, processor.process(id))
        assertEquals(0, ai.transcribeCalls)
        assertEquals("už přepsáno", refinedInput)
    }

    @Test fun refineFailure_keepsTranscriptForNextAttempt() = runTest {
        val (id, _) = insertNote()
        ai.refine = { throw GroqError.RateLimited() }

        assertEquals(ProcessOutcome.RETRY, processor.process(id))
        assertEquals("zavolat ehm do servisu", dao.rows.getValue(id).rawTranscript)
    }

    @Test fun blankTranscript_deletesNoteAndAudio() = runTest {
        val (id, audio) = insertNote()
        ai.transcribe = { "   " }

        assertEquals(ProcessOutcome.DELETED, processor.process(id))
        assertFalse(dao.rows.containsKey(id))
        assertFalse(audio.exists())
    }

    @Test fun missingNote_isDeleted() = runTest {
        assertEquals(ProcessOutcome.DELETED, processor.process(42))
    }

    @Test fun missingAudioFile_fails() = runTest {
        val (id, audio) = insertNote()
        audio.delete()

        assertEquals(ProcessOutcome.FAILED, processor.process(id))
        assertEquals("Nahrávka chybí", dao.rows.getValue(id).errorMessage)
    }
}
```

- [ ] **Step 4: Spustit — musí selhat**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.carlauncher.data.notes.NoteProcessorTest"`
Expected: FAIL — `Unresolved reference: NoteProcessor`.

- [ ] **Step 5: Implementace `NoteProcessor`**

`app/src/main/java/com/example/carlauncher/data/notes/NoteProcessor.kt`:

```kotlin
package com.example.carlauncher.data.notes

import java.io.File
import javax.inject.Inject

enum class ProcessOutcome { DONE, DELETED, RETRY, FAILED }

/**
 * Zpracování jedné poznámky: Whisper → LLM → uložit → smazat audio. Čistá logika bez Androidu,
 * ProcessNoteWorker jen mapuje [ProcessOutcome] na WorkManager Result.
 */
class NoteProcessor @Inject constructor(
    private val dao: VoiceNoteDao,
    private val ai: NoteAi,
) {
    suspend fun process(noteId: Long): ProcessOutcome {
        val note = dao.getById(noteId) ?: return ProcessOutcome.DELETED
        if (note.status == NoteStatus.DONE) return ProcessOutcome.DONE

        var current = note.copy(
            status = NoteStatus.PROCESSING,
            attempts = note.attempts + 1,
            errorMessage = null,
        )
        dao.update(current)

        return try {
            val raw = current.rawTranscript ?: run {
                val audio = current.audioPath?.let(::File)?.takeIf { it.exists() }
                    ?: return fail(current, "Nahrávka chybí")
                ai.transcribe(audio).also {
                    current = current.copy(rawTranscript = it)
                    dao.update(current)
                }
            }
            if (raw.isBlank()) {
                deleteAudio(current)
                dao.deleteById(noteId)
                return ProcessOutcome.DELETED
            }
            val refined = ai.refine(raw)
            deleteAudio(current)
            dao.update(
                current.copy(
                    status = NoteStatus.DONE,
                    title = refined.title,
                    text = refined.text,
                    category = refined.category,
                    audioPath = null,
                ),
            )
            ProcessOutcome.DONE
        } catch (e: GroqError) {
            if (e.isRetryable && current.attempts < MAX_ATTEMPTS) {
                dao.update(current.copy(status = NoteStatus.PENDING))
                ProcessOutcome.RETRY
            } else {
                fail(current, e.message ?: "Chyba zpracování")
            }
        }
    }

    private suspend fun fail(note: VoiceNoteEntity, message: String): ProcessOutcome {
        dao.update(note.copy(status = NoteStatus.FAILED, errorMessage = message))
        return ProcessOutcome.FAILED
    }

    private fun deleteAudio(note: VoiceNoteEntity) {
        note.audioPath?.let { File(it).delete() }
    }

    companion object {
        const val MAX_ATTEMPTS = 5
    }
}
```

- [ ] **Step 6: Spustit — musí projít**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.carlauncher.data.notes.NoteProcessorTest"`
Expected: 9 testů PASS.

- [ ] **Step 7: Hilt modul**

`app/src/main/java/com/example/carlauncher/di/VoiceNotesModule.kt`:

```kotlin
package com.example.carlauncher.di

import android.content.Context
import androidx.room.Room
import com.example.carlauncher.BuildConfig
import com.example.carlauncher.data.notes.GroqClient
import com.example.carlauncher.data.notes.NoteAi
import com.example.carlauncher.data.notes.VoiceNoteDao
import com.example.carlauncher.data.notes.VoiceNotesDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VoiceNotesModule {

    @Provides
    @Singleton
    fun providesVoiceNotesDatabase(@ApplicationContext context: Context): VoiceNotesDatabase =
        Room.databaseBuilder(context, VoiceNotesDatabase::class.java, "voice_notes_database")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun providesVoiceNoteDao(db: VoiceNotesDatabase): VoiceNoteDao = db.voiceNoteDao()

    @Provides
    @Singleton
    fun providesNoteAi(): NoteAi = GroqClient(BuildConfig.GROQ_API_KEY)
}
```

- [ ] **Step 8: Ověřit kompilaci (Room kapt) a všechny testy**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, všechny testy PASS.

- [ ] **Step 9: Commit**

```bash
git add app/build.gradle.kts app/src/main/java/com/example/carlauncher/data/notes/NoteStatus.kt app/src/main/java/com/example/carlauncher/data/notes/VoiceNoteEntity.kt app/src/main/java/com/example/carlauncher/data/notes/VoiceNoteDao.kt app/src/main/java/com/example/carlauncher/data/notes/VoiceNotesDatabase.kt app/src/main/java/com/example/carlauncher/data/notes/NoteProcessor.kt app/src/main/java/com/example/carlauncher/di/VoiceNotesModule.kt app/src/test/java/com/example/carlauncher/data/notes/FakeVoiceNoteDao.kt app/src/test/java/com/example/carlauncher/data/notes/NoteProcessorTest.kt
git commit -m "feat(notes): add voice note storage and processing pipeline

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: `VoiceRecorder` + oprávnění k mikrofonu

`MediaRecorder` nejde testovat na JVM — logika auto-stopu je už otestovaná v `SilenceDetector`. Ověření je kompilace + ruční test v Tasku 9.

**Files:**
- Create: `app/src/main/java/com/example/carlauncher/data/notes/VoiceRecorder.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `SilenceDetector` (Task 1)
- Produces:
  - `sealed interface RecorderState { data object Idle; data class Recording(val amplitude: Int, val elapsedMs: Long, val silenceMs: Long) }`
  - `data class RecordingResult(val file: File, val startedAt: Long, val durationMs: Long)`
  - `@Singleton class VoiceRecorder @Inject constructor(@ApplicationContext context: Context)` s `val state: StateFlow<RecorderState>`, `fun start(outputDir: File, onFinished: (RecordingResult) -> Unit): Boolean`, `fun stop()`

- [ ] **Step 1: Oprávnění v manifestu**

V `app/src/main/AndroidManifest.xml` za blok Incident Recorderu (`<uses-feature android:name="android.hardware.camera.autofocus" … />`) přidat:

```xml
    <!-- Hlasové poznámky — nahrávání do souboru, přepis přes Groq Whisper -->
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-feature android:name="android.hardware.microphone" android:required="false" />
```

- [ ] **Step 2: Implementace**

`app/src/main/java/com/example/carlauncher/data/notes/VoiceRecorder.kt`:

```kotlin
package com.example.carlauncher.data.notes

import android.content.Context
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

sealed interface RecorderState {
    data object Idle : RecorderState
    data class Recording(val amplitude: Int, val elapsedMs: Long, val silenceMs: Long) : RecorderState
}

data class RecordingResult(val file: File, val startedAt: Long, val durationMs: Long)

/**
 * Nahrává AAC/.m4a z mikrofonu. Každých [POLL_INTERVAL_MS] čte amplitudu a nechá
 * [SilenceDetector] rozhodnout o auto-stopu. O Groqu ani DB nic neví — výsledek předá
 * callbackem `onFinished` (jen když MediaRecorder zapsal platný soubor).
 */
@Singleton
class VoiceRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow<RecorderState>(RecorderState.Idle)
    val state: StateFlow<RecorderState> = _state.asStateFlow()

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAt = 0L
    private var pollJob: Job? = null
    private var onFinished: ((RecordingResult) -> Unit)? = null

    fun start(outputDir: File, onFinished: (RecordingResult) -> Unit): Boolean {
        if (recorder != null) return false
        outputDir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(outputDir, "note_$stamp.m4a")
        val r = MediaRecorder(context)
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(16_000)
            r.setAudioEncodingBitRate(32_000)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
        } catch (e: Exception) {
            Log.w(TAG, "Start nahrávání selhal", e)
            r.release()
            file.delete()
            return false
        }
        recorder = r
        outputFile = file
        startedAt = System.currentTimeMillis()
        this.onFinished = onFinished

        val detector = SilenceDetector().apply { start(SystemClock.elapsedRealtime()) }
        pollJob = scope.launch {
            while (isActive) {
                val amplitude = r.maxAmplitude
                val status = detector.onSample(amplitude, SystemClock.elapsedRealtime())
                _state.value = RecorderState.Recording(amplitude, status.elapsedMs, status.silenceMs)
                if (status.shouldStop) {
                    stop()
                    break
                }
                delay(POLL_INTERVAL_MS)
            }
        }
        return true
    }

    fun stop() {
        val r = recorder ?: return
        val file = outputFile ?: return
        pollJob?.cancel()
        pollJob = null
        val duration = System.currentTimeMillis() - startedAt
        // stop() hází RuntimeException, když se nezapsala žádná platná data (příliš krátké)
        val ok = runCatching { r.stop() }.isSuccess
        r.release()
        recorder = null
        outputFile = null
        _state.value = RecorderState.Idle
        val callback = onFinished
        onFinished = null
        if (ok) callback?.invoke(RecordingResult(file, startedAt, duration)) else file.delete()
    }

    companion object {
        private const val TAG = "VoiceNotes"
        private const val POLL_INTERVAL_MS = 200L
    }
}
```

- [ ] **Step 3: Ověřit kompilaci**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/java/com/example/carlauncher/data/notes/VoiceRecorder.kt
git commit -m "feat(notes): add microphone recorder with silence auto-stop

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: WorkManager + `ProcessNoteWorker` + `VoiceNoteRepository` + úklid při startu

**Files:**
- Modify: `app/build.gradle.kts` (WorkManager + hilt-work)
- Modify: `app/src/main/AndroidManifest.xml` (vypnout default initializer)
- Modify: `app/src/main/java/com/example/carlauncher/CarLauncherApp.kt`
- Create: `app/src/main/java/com/example/carlauncher/data/notes/VoiceNoteCleanup.kt`
- Create: `app/src/main/java/com/example/carlauncher/data/notes/ProcessNoteWorker.kt`
- Create: `app/src/main/java/com/example/carlauncher/data/notes/VoiceNoteRepository.kt`
- Test: `app/src/test/java/com/example/carlauncher/data/notes/VoiceNoteCleanupTest.kt`

**Interfaces:**
- Consumes: `NoteProcessor`, `ProcessOutcome`, `VoiceNoteDao`, `VoiceNoteEntity`, `NoteStatus` (Task 4); `VoiceRecorder`, `RecorderState`, `RecordingResult` (Task 5)
- Produces:
  - `internal fun findOrphanAudio(files: List<File>, knownPaths: Set<String>): List<File>`
  - `@HiltWorker class ProcessNoteWorker` s `ProcessNoteWorker.KEY_NOTE_ID = "note_id"`
  - `@Singleton class VoiceNoteRepository @Inject constructor(@ApplicationContext context, dao: VoiceNoteDao, recorder: VoiceRecorder)` s:
    `val notes: Flow<List<VoiceNoteEntity>>`, `val recorderState: StateFlow<RecorderState>`,
    `fun startRecording(): Boolean`, `fun stopRecording()`, `suspend fun retry(id: Long)`,
    `suspend fun delete(id: Long)`, `fun cleanupOnStartup()`

- [ ] **Step 1: Napsat padající test úklidu**

`app/src/test/java/com/example/carlauncher/data/notes/VoiceNoteCleanupTest.kt`:

```kotlin
package com.example.carlauncher.data.notes

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class VoiceNoteCleanupTest {
    @Test fun returnsOnlyUnknownM4aFiles() {
        val known = File("/x/note_1.m4a")
        val orphan = File("/x/note_2.m4a")
        val other = File("/x/readme.txt")

        val result = findOrphanAudio(listOf(known, orphan, other), setOf(known.absolutePath))

        assertEquals(listOf(orphan), result)
    }
}
```

- [ ] **Step 2: Spustit — musí selhat**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.carlauncher.data.notes.VoiceNoteCleanupTest"`
Expected: FAIL — `Unresolved reference: findOrphanAudio`.

- [ ] **Step 3: Implementace úklidu**

`app/src/main/java/com/example/carlauncher/data/notes/VoiceNoteCleanup.kt`:

```kotlin
package com.example.carlauncher.data.notes

import java.io.File

/** Nahrávky, ke kterým v DB není řádek (proces zabitý mezi zápisem souboru a INSERTem). */
internal fun findOrphanAudio(files: List<File>, knownPaths: Set<String>): List<File> =
    files.filter { it.extension == "m4a" && it.absolutePath !in knownPaths }
```

- [ ] **Step 4: Spustit — musí projít**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.carlauncher.data.notes.VoiceNoteCleanupTest"`
Expected: PASS.

- [ ] **Step 5: Závislosti WorkManageru**

V `app/build.gradle.kts` za blok Room (`kapt(libs.room.compiler)`) přidat:

```kotlin

    // WorkManager — offline fronta zpracování hlasových poznámek (Groq)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    kapt(libs.androidx.hilt.compiler)
```

- [ ] **Step 6: Vypnout výchozí WorkManager initializer v manifestu**

V `app/src/main/AndroidManifest.xml` doplnit do kořenového tagu tools namespace:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">
```

a uvnitř `<application>` za `FileProvider` `<provider … />` blok přidat:

```xml
        <!-- WorkManager se konfiguruje v CarLauncherApp (HiltWorkerFactory) — vypnout auto-init -->
        <provider
            android:name="androidx.startup.InitializationProvider"
            android:authorities="${applicationId}.androidx-startup"
            android:exported="false"
            tools:node="merge">
            <meta-data
                android:name="androidx.work.WorkManagerInitializer"
                android:value="androidx.startup"
                tools:node="remove" />
        </provider>
```

- [ ] **Step 7: Worker**

`app/src/main/java/com/example/carlauncher/data/notes/ProcessNoteWorker.kt`:

```kotlin
package com.example.carlauncher.data.notes

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Tenký obal: veškerá logika je v [NoteProcessor] (unit-testovaný). */
@HiltWorker
class ProcessNoteWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val processor: NoteProcessor,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_NOTE_ID, -1L)
        if (id < 0) return Result.failure()
        return when (processor.process(id)) {
            ProcessOutcome.RETRY -> Result.retry()
            ProcessOutcome.DONE, ProcessOutcome.DELETED, ProcessOutcome.FAILED -> Result.success()
        }
    }

    companion object {
        const val KEY_NOTE_ID = "note_id"
    }
}
```

- [ ] **Step 8: Repository**

`app/src/main/java/com/example/carlauncher/data/notes/VoiceNoteRepository.kt`:

```kotlin
package com.example.carlauncher.data.notes

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Jediný vstupní bod UI pro hlasové poznámky. Ukládání po nahrání běží v app-level scope,
 * takže poznámka se neztratí, ani když se Trip stránka mezitím disposuje.
 */
@Singleton
class VoiceNoteRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: VoiceNoteDao,
    private val recorder: VoiceRecorder,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val audioDir = File(context.filesDir, AUDIO_DIR)

    // Líně: repository se injektuje do CarLauncherApp ve stejném kroku jako HiltWorkerFactory —
    // WorkManager.getInstance() v konstruktoru by sáhl na workManagerConfiguration dřív,
    // než je workerFactory nastavená.
    private val workManager by lazy { WorkManager.getInstance(context) }

    val notes: Flow<List<VoiceNoteEntity>> = dao.observeAll()
    val recorderState: StateFlow<RecorderState> = recorder.state

    fun startRecording(): Boolean =
        recorder.start(audioDir) { result -> scope.launch { saveRecording(result) } }

    fun stopRecording() = recorder.stop()

    private suspend fun saveRecording(result: RecordingResult) {
        if (result.durationMs < MIN_DURATION_MS) {
            result.file.delete()
            return
        }
        val id = dao.insert(
            VoiceNoteEntity(
                createdAt = result.startedAt,
                durationMs = result.durationMs,
                audioPath = result.file.absolutePath,
            ),
        )
        enqueue(id, ExistingWorkPolicy.KEEP)
    }

    suspend fun retry(id: Long) {
        val note = dao.getById(id) ?: return
        dao.update(note.copy(status = NoteStatus.PENDING, attempts = 0, errorMessage = null))
        enqueue(id, ExistingWorkPolicy.REPLACE)
    }

    suspend fun delete(id: Long) {
        workManager.cancelUniqueWork(workName(id))
        dao.getById(id)?.audioPath?.let { File(it).delete() }
        dao.deleteById(id)
    }

    /** Smaže osiřelé nahrávky a znovu zařadí rozpracované poznámky (proces mohl být zabit). */
    fun cleanupOnStartup() {
        scope.launch {
            val all = dao.getAll()
            val files = audioDir.listFiles()?.toList().orEmpty()
            findOrphanAudio(files, all.mapNotNull { it.audioPath }.toSet()).forEach { it.delete() }
            all.filter { it.status == NoteStatus.PENDING || it.status == NoteStatus.PROCESSING }
                .forEach { note ->
                    if (note.status == NoteStatus.PROCESSING) {
                        dao.update(note.copy(status = NoteStatus.PENDING))
                    }
                    enqueue(note.id, ExistingWorkPolicy.KEEP)
                }
        }
    }

    private fun enqueue(id: Long, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<ProcessNoteWorker>()
            .setInputData(workDataOf(ProcessNoteWorker.KEY_NOTE_ID to id))
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(workName(id), policy, request)
    }

    companion object {
        const val AUDIO_DIR = "voice_notes"
        const val MIN_DURATION_MS = 1_000L
        private const val BACKOFF_SECONDS = 30L
        fun workName(id: Long) = "note-$id"
    }
}
```

- [ ] **Step 9: `CarLauncherApp` — HiltWorkerFactory + úklid**

Nahradit obsah `app/src/main/java/com/example/carlauncher/CarLauncherApp.kt` (zachovat stávající Mapbox kód i komentáře beze změny):

```kotlin
package com.example.carlauncher

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.carlauncher.data.navigation.MapboxArrivalTeardownObserver
import com.example.carlauncher.data.navigation.MapboxVoiceGuidanceObserver
import com.example.carlauncher.data.notes.VoiceNoteRepository
import com.mapbox.common.MapboxOptions
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CarLauncherApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var voiceNoteRepository: VoiceNoteRepository

    // Výchozí initializer je v manifestu vypnutý — WorkManager se inicializuje na vyžádání
    // s HiltWorkerFactory, aby ProcessNoteWorker dostal NoteProcessor přes Hilt.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        MapboxOptions.accessToken = BuildConfig.MAPBOX_ACCESS_TOKEN
        MapboxNavigationApp.setup(
            NavigationOptions.Builder(this).build()
        )
        // Hlas musí přežít teardown MapWidgetu při přepnutí panelu Mapa → Navigace (spec §3),
        // takže visí na app-level navigation lifecycle, ne na kompozici — viz KDoc observeru.
        // Registrace před prvním attach() je v pořádku: onAttached se zavolá, až se nějaký
        // LifecycleOwner (MainActivity přes requireMapboxNavigation) dostane na STARTED.
        MapboxNavigationApp.registerObserver(MapboxVoiceGuidanceObserver(this))
        // Ukončení navigace po dojezdu musí přežít teardown MapWidgetu ze stejného důvodu —
        // a navíc Mapbox si dojezd pro danou trasu latchuje a vystřelí ho jen jednou, takže
        // observer registrovaný až po přepnutí zpět na Mapu už nic nedostane. Viz KDoc.
        MapboxNavigationApp.registerObserver(MapboxArrivalTeardownObserver())
        voiceNoteRepository.cleanupOnStartup()
    }
}
```

- [ ] **Step 10: Ověřit build + testy**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, všechny testy PASS. Pokud kapt hlásí `@AssistedInject` chybu, zkontrolovat, že je přidaný `kapt(libs.androidx.hilt.compiler)` (androidx, ne dagger).

- [ ] **Step 11: Commit**

```bash
git add app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/java/com/example/carlauncher/CarLauncherApp.kt app/src/main/java/com/example/carlauncher/data/notes/VoiceNoteCleanup.kt app/src/main/java/com/example/carlauncher/data/notes/ProcessNoteWorker.kt app/src/main/java/com/example/carlauncher/data/notes/VoiceNoteRepository.kt app/src/test/java/com/example/carlauncher/data/notes/VoiceNoteCleanupTest.kt
git commit -m "feat(notes): queue note processing with WorkManager until network is available

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: `NoteFormat` + `VoiceNotesViewModel` + `VoiceNotesScreen`

**Files:**
- Create: `app/src/main/java/com/example/carlauncher/ui/notes/NoteFormat.kt`
- Create: `app/src/main/java/com/example/carlauncher/ui/notes/VoiceNotesViewModel.kt`
- Create: `app/src/main/java/com/example/carlauncher/ui/notes/VoiceNotesScreen.kt`
- Test: `app/src/test/java/com/example/carlauncher/ui/notes/NoteFormatTest.kt`

**Interfaces:**
- Consumes: `VoiceNoteRepository` (Task 6); `VoiceNoteEntity`, `NoteStatus` (Task 4); `RecorderState` (Task 5); `NoteCategory` (Task 1)
- Produces:
  - `fun formatNoteTime(timestamp: Long, now: Long, zone: TimeZone = TimeZone.getDefault()): String`, `fun formatDuration(ms: Long): String`, `fun noteRowTitle(note: VoiceNoteEntity): String`, `val NoteCategory.color: Color`
  - `@HiltViewModel class VoiceNotesViewModel` s `notes: StateFlow<List<VoiceNoteEntity>>`, `filteredNotes: StateFlow<List<VoiceNoteEntity>>`, `filter: StateFlow<NoteCategory?>`, `recorderState: StateFlow<RecorderState>`, `justSaved: StateFlow<Boolean>`, `val hasApiKey: Boolean`, `fun onMicTap(): Boolean`, `fun setFilter(c: NoteCategory?)`, `fun retry(id: Long)`, `fun delete(id: Long)`
  - `@Composable fun VoiceNotesScreen(onBack: () -> Unit, initialNoteId: Long? = null, viewModel: VoiceNotesViewModel = hiltViewModel())`

- [ ] **Step 1: Napsat padající test**

`app/src/test/java/com/example/carlauncher/ui/notes/NoteFormatTest.kt`:

```kotlin
package com.example.carlauncher.ui.notes

import com.example.carlauncher.data.notes.NoteCategory
import com.example.carlauncher.data.notes.NoteStatus
import com.example.carlauncher.data.notes.VoiceNoteEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class NoteFormatTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val now = 1_759_838_400_000L // 2025-10-07 12:00 UTC

    @Test fun sameDay_showsHoursAndMinutes() {
        assertEquals("09:05", formatNoteTime(now - (2 * 60 + 55) * 60_000L, now, utc))
    }

    @Test fun otherDay_showsDayAndMonth() {
        assertEquals("6. 10.", formatNoteTime(now - 24 * 3_600_000L, now, utc))
    }

    @Test fun duration_isMinutesAndSeconds() {
        assertEquals("0:07", formatDuration(7_400))
        assertEquals("2:00", formatDuration(120_000))
    }

    @Test fun rowTitle_dependsOnStatus() {
        val base = VoiceNoteEntity(createdAt = 0, durationMs = 0, audioPath = null)
        assertEquals("⏳ Zpracovávám…", noteRowTitle(base.copy(status = NoteStatus.PENDING)))
        assertEquals("⏳ Zpracovávám…", noteRowTitle(base.copy(status = NoteStatus.PROCESSING)))
        assertEquals("⚠ Chyba zpracování", noteRowTitle(base.copy(status = NoteStatus.FAILED)))
        assertEquals(
            "✅ Servis",
            noteRowTitle(base.copy(status = NoteStatus.DONE, title = "Servis", category = NoteCategory.TASK)),
        )
    }
}
```

- [ ] **Step 2: Spustit — musí selhat**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.carlauncher.ui.notes.NoteFormatTest"`
Expected: FAIL — `Unresolved reference: formatNoteTime`.

- [ ] **Step 3: Implementace `NoteFormat.kt`**

`app/src/main/java/com/example/carlauncher/ui/notes/NoteFormat.kt`:

```kotlin
package com.example.carlauncher.ui.notes

import androidx.compose.ui.graphics.Color
import com.example.carlauncher.data.notes.NoteCategory
import com.example.carlauncher.data.notes.NoteStatus
import com.example.carlauncher.data.notes.VoiceNoteEntity
import com.example.carlauncher.ui.theme.CarColors
import java.util.Calendar
import java.util.TimeZone

/** Dnes → "HH:mm", jindy → "d. M." */
fun formatNoteTime(timestamp: Long, now: Long, zone: TimeZone = TimeZone.getDefault()): String {
    val t = Calendar.getInstance(zone).apply { timeInMillis = timestamp }
    val n = Calendar.getInstance(zone).apply { timeInMillis = now }
    val sameDay = t.get(Calendar.YEAR) == n.get(Calendar.YEAR) &&
        t.get(Calendar.DAY_OF_YEAR) == n.get(Calendar.DAY_OF_YEAR)
    return if (sameDay) {
        "%02d:%02d".format(t.get(Calendar.HOUR_OF_DAY), t.get(Calendar.MINUTE))
    } else {
        "${t.get(Calendar.DAY_OF_MONTH)}. ${t.get(Calendar.MONTH) + 1}."
    }
}

fun formatDuration(ms: Long): String {
    val s = ms / 1_000
    return "%d:%02d".format(s / 60, s % 60)
}

fun noteRowTitle(note: VoiceNoteEntity): String = when (note.status) {
    NoteStatus.PENDING, NoteStatus.PROCESSING -> "⏳ Zpracovávám…"
    NoteStatus.FAILED -> "⚠ Chyba zpracování"
    NoteStatus.DONE -> "${(note.category ?: NoteCategory.OTHER).emoji} ${note.title.orEmpty()}"
}

val NoteCategory.color: Color
    get() = when (this) {
        NoteCategory.IDEA -> CarColors.Warn
        NoteCategory.TASK -> CarColors.Go
        NoteCategory.SHOPPING -> CarColors.Accent
        NoteCategory.CAR -> CarColors.Danger
        NoteCategory.OTHER -> CarColors.Text3
    }
```

- [ ] **Step 4: Spustit — musí projít**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.carlauncher.ui.notes.NoteFormatTest"`
Expected: 4 testy PASS.

- [ ] **Step 5: ViewModel**

`app/src/main/java/com/example/carlauncher/ui/notes/VoiceNotesViewModel.kt`:

```kotlin
package com.example.carlauncher.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.carlauncher.BuildConfig
import com.example.carlauncher.data.notes.NoteCategory
import com.example.carlauncher.data.notes.RecorderState
import com.example.carlauncher.data.notes.VoiceNoteEntity
import com.example.carlauncher.data.notes.VoiceNoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VoiceNotesViewModel @Inject constructor(
    private val repository: VoiceNoteRepository,
) : ViewModel() {

    val hasApiKey: Boolean = BuildConfig.GROQ_API_KEY.isNotBlank()

    val notes: StateFlow<List<VoiceNoteEntity>> = repository.notes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _filter = MutableStateFlow<NoteCategory?>(null)
    val filter: StateFlow<NoteCategory?> = _filter.asStateFlow()

    val filteredNotes: StateFlow<List<VoiceNoteEntity>> = combine(notes, _filter) { list, f ->
        if (f == null) list else list.filter { it.category == f }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recorderState: StateFlow<RecorderState> = repository.recorderState

    private val _justSaved = MutableStateFlow(false)
    val justSaved: StateFlow<Boolean> = _justSaved.asStateFlow()

    init {
        // Recording → Idle = nahrávání skončilo → krátce ukázat "Uloženo ✓"
        viewModelScope.launch {
            var wasRecording = false
            recorderState.collect { state ->
                if (wasRecording && state is RecorderState.Idle) {
                    _justSaved.value = true
                    delay(JUST_SAVED_MS)
                    _justSaved.value = false
                }
                wasRecording = state is RecorderState.Recording
            }
        }
    }

    /** @return false, když se nahrávání nepodařilo spustit. */
    fun onMicTap(): Boolean {
        if (recorderState.value is RecorderState.Recording) {
            repository.stopRecording()
            return true
        }
        if (!hasApiKey) return false
        return repository.startRecording()
    }

    fun setFilter(category: NoteCategory?) { _filter.value = category }

    fun retry(id: Long) { viewModelScope.launch { repository.retry(id) } }

    fun delete(id: Long) { viewModelScope.launch { repository.delete(id) } }

    private companion object {
        const val JUST_SAVED_MS = 1_500L
    }
}
```

- [ ] **Step 6: Obrazovka se seznamem**

`app/src/main/java/com/example/carlauncher/ui/notes/VoiceNotesScreen.kt`:

```kotlin
package com.example.carlauncher.ui.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.carlauncher.data.notes.NoteCategory
import com.example.carlauncher.data.notes.NoteStatus
import com.example.carlauncher.data.notes.VoiceNoteEntity
import com.example.carlauncher.ui.theme.CarColors

private const val LONG_PRESS_MS = 1_500L

@Composable
fun VoiceNotesScreen(
    onBack: () -> Unit,
    initialNoteId: Long? = null,
    viewModel: VoiceNotesViewModel = hiltViewModel(),
) {
    val notes by viewModel.filteredNotes.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<VoiceNoteEntity?>(null) }
    val listState = rememberLazyListState()
    var scrolledToInitial by remember { mutableStateOf(false) }
    val now = remember(notes) { System.currentTimeMillis() }

    BackHandler(onBack = onBack)

    LaunchedEffect(notes, initialNoteId) {
        if (scrolledToInitial || initialNoteId == null) return@LaunchedEffect
        val index = notes.indexOfFirst { it.id == initialNoteId }
        if (index >= 0) {
            listState.scrollToItem(index)
            scrolledToInitial = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CarColors.Bg)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpět", tint = CarColors.Text)
            }
            Text("Hlasové poznámky", color = CarColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            CategoryChips(selected = filter, onSelect = viewModel::setFilter)
        }
        Spacer(Modifier.height(8.dp))
        if (notes.isEmpty()) {
            Text("Žádné poznámky.", color = CarColors.Text2, fontSize = 14.sp)
        } else {
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(notes, key = { it.id }) { note ->
                    NoteCard(
                        note = note,
                        now = now,
                        onRetry = { viewModel.retry(note.id) },
                        onLongPress = { pendingDelete = note },
                    )
                }
            }
        }
    }

    pendingDelete?.let { note ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Smazat poznámku?") },
            text = { Text(note.title ?: "Nezpracovaná poznámka") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(note.id); pendingDelete = null }) {
                    Text("Smazat", color = CarColors.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Zrušit", color = CarColors.Text2) }
            },
            containerColor = CarColors.Surface2,
            titleContentColor = CarColors.Text,
            textContentColor = CarColors.Text2,
        )
    }
}

@Composable
private fun CategoryChips(selected: NoteCategory?, onSelect: (NoteCategory?) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Chip(label = "Vše", active = selected == null) { onSelect(null) }
        NoteCategory.entries.forEach { c ->
            Chip(label = c.emoji, active = selected == c) { onSelect(c) }
        }
    }
}

@Composable
private fun Chip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .width(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) CarColors.Accent else CarColors.Surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = CarColors.Text, fontSize = 15.sp)
    }
}

@Composable
private fun NoteCard(
    note: VoiceNoteEntity,
    now: Long,
    onRetry: () -> Unit,
    onLongPress: () -> Unit,
) {
    val accent = note.category?.color ?: CarColors.Surface3
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CarColors.Surface)
            .pointerInput(note.id) {
                // Stejný vzor jako DockBar: Compose withTimeout hází
                // PointerEventTimeoutCancellationException, ne kotlinx TimeoutCancellationException.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    try {
                        withTimeout(LONG_PRESS_MS) { waitForUpOrCancellation() }
                    } catch (_: PointerEventTimeoutCancellationException) {
                        onLongPress()
                        waitForUpOrCancellation()
                    }
                }
            },
    ) {
        Box(Modifier.width(5.dp).heightIn(min = 72.dp).background(accent))
        Column(Modifier.padding(12.dp).weight(1f)) {
            Text(noteRowTitle(note), color = CarColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            note.text?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, color = CarColors.Text2, fontSize = 13.sp)
            }
            if (note.status == NoteStatus.FAILED) {
                Spacer(Modifier.height(4.dp))
                Text(note.errorMessage.orEmpty(), color = CarColors.Danger, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = CarColors.Surface3),
                    shape = RoundedCornerShape(10.dp),
                ) { Text("Zkusit znovu", color = CarColors.Text, fontSize = 12.sp) }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${formatNoteTime(note.createdAt, now)}  ·  ${formatDuration(note.durationMs)}",
                color = CarColors.Text3,
                fontSize = 11.sp,
            )
        }
    }
}
```

- [ ] **Step 7: Ověřit build + testy**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, všechny testy PASS.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/example/carlauncher/ui/notes/NoteFormat.kt app/src/main/java/com/example/carlauncher/ui/notes/VoiceNotesViewModel.kt app/src/main/java/com/example/carlauncher/ui/notes/VoiceNotesScreen.kt app/src/test/java/com/example/carlauncher/ui/notes/NoteFormatTest.kt
git commit -m "feat(notes): add voice notes list screen with category filter

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: `VoiceNotesWidget` + zapojení do `TripScreen`

**Files:**
- Create: `app/src/main/java/com/example/carlauncher/ui/notes/VoiceNotesWidget.kt`
- Modify: `app/src/main/java/com/example/carlauncher/ui/widgets/TripScreen.kt`

**Interfaces:**
- Consumes: `VoiceNotesViewModel`, `VoiceNotesScreen`, `formatNoteTime`, `formatDuration`, `noteRowTitle`, `NoteCategory.color` (Task 7); `RecorderState` (Task 5); `SilenceDetector.SILENCE_LIMIT_MS` (Task 1)
- Produces: `@Composable fun VoiceNotesWidget(modifier: Modifier = Modifier, onOpenNotes: (noteId: Long?) -> Unit, viewModel: VoiceNotesViewModel = hiltViewModel())`

- [ ] **Step 1: Widget**

`app/src/main/java/com/example/carlauncher/ui/notes/VoiceNotesWidget.kt`:

```kotlin
package com.example.carlauncher.ui.notes

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.carlauncher.data.notes.RecorderState
import com.example.carlauncher.data.notes.SilenceDetector
import com.example.carlauncher.data.notes.VoiceNoteEntity
import com.example.carlauncher.ui.theme.CarColors

private const val RECENT_NOTES = 3
/** MediaRecorder amplituda, při které je prstenec úplně roztažený. */
private const val FULL_RING_AMPLITUDE = 12_000f
/** Oblouk odpočtu ticha ukázat až po krátké pauze, ne při každém nádechu. */
private const val SILENCE_ARC_VISIBLE_MS = 800L

@Composable
fun VoiceNotesWidget(
    modifier: Modifier = Modifier,
    onOpenNotes: (noteId: Long?) -> Unit,
    viewModel: VoiceNotesViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val recorderState by viewModel.recorderState.collectAsStateWithLifecycle()
    val justSaved by viewModel.justSaved.collectAsStateWithLifecycle()
    val now = remember(notes) { System.currentTimeMillis() }

    fun micGranted() = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED

    var micDenied by remember { mutableStateOf(false) }
    var startFailed by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        micDenied = !granted
        if (granted) startFailed = !viewModel.onMicTap()
    }

    val recording = recorderState as? RecorderState.Recording
    val hint = when {
        !viewModel.hasApiKey -> "Chybí Groq API klíč"
        micDenied -> "Povol mikrofon v nastavení aplikace"
        recording != null -> "${formatDuration(recording.elapsedMs)} · zastaví se po 5 s ticha"
        startFailed -> "Mikrofon se nepodařilo spustit"
        justSaved -> "Uloženo ✓"
        else -> "Ťukni a mluv"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(CarColors.Surface)
            .border(1.dp, CarColors.BorderSoft, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "HLASOVÉ POZNÁMKY",
                color = CarColors.Text3,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.08.em,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onOpenNotes(null) }) {
                Icon(Icons.Default.OpenInFull, contentDescription = "Všechny poznámky", tint = CarColors.Text2)
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            MicButton(
                recording = recording,
                enabled = viewModel.hasApiKey,
                onClick = {
                    startFailed = false
                    if (recording == null && !micGranted()) {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        startFailed = !viewModel.onMicTap()
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            Text(hint, color = CarColors.Text2, fontSize = 13.sp, textAlign = TextAlign.Center)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = CarColors.BorderSoft)

        if (notes.isEmpty()) {
            Text("Zatím žádné poznámky", color = CarColors.Text3, fontSize = 11.sp)
        } else {
            notes.take(RECENT_NOTES).forEach { note ->
                RecentNoteRow(note = note, now = now, onClick = { onOpenNotes(note.id) })
            }
        }
    }
}

@Composable
private fun MicButton(recording: RecorderState.Recording?, enabled: Boolean, onClick: () -> Unit) {
    val level by animateFloatAsState(
        targetValue = ((recording?.amplitude ?: 0) / FULL_RING_AMPLITUDE).coerceIn(0f, 1f),
        label = "micLevel",
    )
    val base = when {
        recording != null -> CarColors.Danger
        enabled -> CarColors.Accent
        else -> CarColors.Surface3
    }
    val arcColor = CarColors.Text2

    Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            if (recording == null) return@Canvas
            val inner = 48.dp.toPx()
            val outer = size.minDimension / 2
            drawCircle(base.copy(alpha = 0.25f), radius = inner + (outer - inner) * level)
            if (recording.silenceMs > SILENCE_ARC_VISIBLE_MS) {
                val remaining = 1f - (recording.silenceMs.toFloat() / SilenceDetector.SILENCE_LIMIT_MS)
                    .coerceIn(0f, 1f)
                val inset = 4.dp.toPx()
                drawArc(
                    color = arcColor,
                    startAngle = -90f,
                    sweepAngle = 360f * remaining,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - 2 * inset, size.height - 2 * inset),
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(base)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (recording != null) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = if (recording != null) "Zastavit nahrávání" else "Nahrát poznámku",
                tint = Color.White,
                modifier = Modifier.size(44.dp),
            )
        }
    }
}

@Composable
private fun RecentNoteRow(note: VoiceNoteEntity, now: Long, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(note.category?.color ?: CarColors.Surface3),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            noteRowTitle(note),
            color = CarColors.Text,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(formatNoteTime(note.createdAt, now), color = CarColors.Text3, fontSize = 11.sp)
    }
}
```

- [ ] **Step 2: Zapojit do `TripScreen`**

V `app/src/main/java/com/example/carlauncher/ui/widgets/TripScreen.kt`:

Přidat importy:

```kotlin
import com.example.carlauncher.ui.notes.VoiceNotesScreen
import com.example.carlauncher.ui.notes.VoiceNotesWidget
```

Nahradit začátek těla funkce (stav + early returns):

```kotlin
    var showHistory by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(false) }
    var notesInitialId by remember { mutableStateOf<Long?>(null) }

    if (showHistory) {
        TripHistoryScreen(onBack = { showHistory = false })
        return
    }
    if (showNotes) {
        VoiceNotesScreen(onBack = { showNotes = false }, initialNoteId = notesInitialId)
        return
    }
```

A v `Row` za `JizdniDenikWidget(...)` přidat třetí dlaždici:

```kotlin
            VoiceNotesWidget(
                modifier = Modifier.weight(1f),
                onOpenNotes = { id ->
                    notesInitialId = id
                    showNotes = true
                },
            )
```

(Pokud `TripScreen.kt` ještě nemá import `androidx.compose.runtime.mutableStateOf` / `remember` / `getValue` / `setValue` — má, používá je `showHistory`.)

- [ ] **Step 3: Ověřit build**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Vizuální kontrola na zařízení / emulátoru API 34**

Nainstalovat (absolutní cesta, viz paměť projektu) a ověřit obsah instalace:

```bash
"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" install -r "$(pwd)/app/build/outputs/apk/debug/app-debug.apk"
"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" shell dumpsys package com.example.carlauncher | grep -E "lastUpdateTime|RECORD_AUDIO"
```

Expected: `lastUpdateTime` = právě teď; `android.permission.RECORD_AUDIO` je v seznamu requested permissions.

Přejít na stránku Trip (2× swipe doleva) a zkontrolovat: tři dlaždice vedle sebe; Trip Computer a Jízdní deník čitelné bez oříznutého textu v ⅓ šířky. Pokud se text v `TripComputerWidget`/`JizdniDenikWidget` ořezává, zmenšit v těch souborech jen `fontSize`/`padding` problematických prvků (např. hodnota 20.sp → 17.sp) a nic jiného neměnit.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/carlauncher/ui/notes/VoiceNotesWidget.kt app/src/main/java/com/example/carlauncher/ui/widgets/TripScreen.kt
git commit -m "feat(notes): add voice notes widget to the Trip page

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

(Pokud byly upraveny i `TripComputerWidget.kt`/`JizdniDenikWidget.kt`, přidat je do `git add`.)

---

### Task 9: End-to-end ověření na zařízení + dokumentace

**Files:**
- Modify: `CLAUDE.md`

**Interfaces:**
- Consumes: celá funkce z Tasků 1–8.

- [ ] **Step 1: Klíč do `local.properties`**

Uživatel si vytvoří klíč na https://console.groq.com/keys (free, bez karty) a přidá do `local.properties` řádek `GROQ_API_KEY=gsk_…`. **Nestageovat `local.properties`.** Pak rebuild + install (příkazy z Task 8, Step 4).

- [ ] **Step 2: Ruční scénáře (logcat tag `VoiceNotes` + WorkManager)**

```bash
"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" logcat -s VoiceNotes WM-WorkerWrapper
```

1. Ťuknout na mikrofon → povolit oprávnění → nadiktovat „ehm zítra musím jako zavolat do servisu kvůli přezutí“ → mlčet. Expected: po ~5 s ticha se nahrávání samo zastaví, „Uloženo ✓“, řádek `⏳ Zpracovávám…`, do pár sekund `🚗`/`✅` + nadpis.
2. Ťuknout na mikrofon a hned znovu (< 1 s). Expected: žádný nový řádek.
3. Zapnout režim letadlo → nahrát poznámku → zůstane `⏳` → vypnout režim letadlo. Expected: poznámka se během desítek sekund zpracuje sama.
4. Do `local.properties` dát neplatný klíč → rebuild → nahrát. Expected: `⚠ Chyba zpracování`, v seznamu „Neplatný Groq API klíč (401)“ + „Zkusit znovu“. Vrátit platný klíč → rebuild → „Zkusit znovu“ → `DONE`.
5. V seznamu: filtr chipů funguje; dlouhý stisk 1,5 s → dialog → Smazat.
6. Ověřit, že po zpracování ve složce nezůstává audio:
   ```bash
   "$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" shell run-as com.example.carlauncher ls files/voice_notes
   ```
   Expected: prázdné (pokud žádná poznámka není PENDING/FAILED).

Pokud je ticho detekované špatně (stopuje během mluvení / nestopuje v tichu auta), upravit jen `SilenceDetector.DEFAULT_THRESHOLD` a znovu spustit testy z Task 1.

- [ ] **Step 3: Dokumentace v `CLAUDE.md`**

V `CLAUDE.md`:

a) Do řádku `ADB logcat tags:` přidat `, VoiceNotes`.

b) Do bloku Mapbox setup za odrážku s `MAPBOX_DOWNLOADS_TOKEN` přidat odrážku:

```markdown
- `GROQ_API_KEY` (`gsk_…`, free plán bez karty, https://console.groq.com/keys) v `local.properties` → `BuildConfig.GROQ_API_KEY` — hlasové poznámky. Prázdný = widget nenahrává. Účet **nepovyšovat** na Developer tier (zapnulo by účtování).
```

c) Do stromu `Architecture` doplnit `ui/notes/` a `data/notes/` řádky:

```
  notes/        VoiceNotesWidget (Trip stránka), VoiceNotesScreen, VoiceNotesViewModel, NoteFormat
```
(pod `ui/`) a
```
  notes/        VoiceRecorder, SilenceDetector, GroqClient, NoteProcessor, ProcessNoteWorker,
                VoiceNoteRepository, Room (VoiceNotesDatabase)
```
(pod `data/`).

d) V sekci `Dual-screen layout` změnit popis Page 2 na `TripScreen (Trip Computer + Jízdní deník + Hlasové poznámky)`.

e) Před sekci `## Planned / Not Yet Implemented` vložit:

```markdown
## Module: Voice Notes (data/notes/, ui/notes/)

Třetí dlaždice na Trip stránce. Ťuk na mikrofon → `VoiceRecorder` (MediaRecorder, AAC `.m4a` do `filesDir/voice_notes/`) → auto-stop po 5 s ticha (`SilenceDetector`, čistá logika, práh `DEFAULT_THRESHOLD` na škále `getMaxAmplitude()`), strop 2 min, < 1 s se zahodí. `VoiceNoteRepository` vloží Room řádek `PENDING` (samostatná `VoiceNotesDatabase`, ne `TripDatabase`) a naplánuje `ProcessNoteWorker` (`enqueueUniqueWork("note-<id>")`, `NetworkType.CONNECTED`, exponenciální backoff) → offline poznámky se zpracují samy po obnovení sítě.

**Groq (free plán):** `whisper-large-v3` (`language=cs`; ne `-turbo` kvůli češtině) → `openai/gpt-oss-120b` se striktním `json_schema` (title/text/category enum). `llama-3.3-70b-versatile` **není** ve free plánu. HTTP přes `HttpURLConnection` + `org.json` (`GroqClient`), chyby jako `GroqError` (síť/429/5xx retry, 401/4xx FAILED). Neplatný/neúplný JSON z LLM → `RefinedNoteParser.fallback` ze surového přepisu.

**Logika je v `NoteProcessor`** (unit testy s `FakeVoiceNoteDao`), worker ho jen obaluje. `rawTranscript` se ukládá hned po Whisperu → retry LLM kroku znovu nepřepisuje. Max 5 pokusů. Audio se po `DONE` maže. Při startu `CarLauncherApp` volá `cleanupOnStartup()` (osiřelé `.m4a` pryč, `PROCESSING` → `PENDING` + re-enqueue).

**WorkManager + Hilt:** `CarLauncherApp : Configuration.Provider` s `HiltWorkerFactory`; výchozí `WorkManagerInitializer` je v manifestu odstraněný (`tools:node="remove"`). Repository bere `WorkManager.getInstance()` **líně** — je injektovaný do `CarLauncherApp` současně s `workerFactory`, eager volání by sáhlo na neinicializovanou `lateinit`.

**Oprávnění:** `RECORD_AUDIO` in-Compose při prvním ťuknutí (`VoiceNotesWidget`). Seznam (`VoiceNotesScreen`, přepínaný `showNotes` v `TripScreen`): filtr kategorií, „Zkusit znovu“ u FAILED, mazání dlouhým stiskem 1500 ms (vzor `DockBar`).

**Unit testy:** `./gradlew :app:testDebugUnitTest` — `app/src/test/…/data/notes/`, `ui/notes/`. `org.json` je `testImplementation` (android.jar má jen stuby).
```

- [ ] **Step 4: Finální kontrola**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`
Expected: `BUILD SUCCESSFUL`; lint bez nových chyb v `notes` souborech.

Run: `git status --short`
Expected: `local.properties` **není** ve staged změnách.

- [ ] **Step 5: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: document voice notes module

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
