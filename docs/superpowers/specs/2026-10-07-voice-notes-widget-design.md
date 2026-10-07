# Hlasové poznámky s AI — design

Datum: 2026-10-07 · Stav: schváleno v brainstormingu, čeká na review spec

## Cíl

Vlastní Compose widget na stránce Trip, který jedním ťuknutím nahraje hlasovou poznámku / myšlenku,
pošle ji na přepis (Groq Whisper) a nechá AI (Groq LLM) vytvořit krátký nadpis, učesaný text
a zařadit ji do pevné kategorie. Funguje i při výpadku signálu (fronta).

## Mimo rozsah (v1)

- Akce z poznámek (kalendář, připomínky), dotazy nad poznámkami
- Editace / kopírování textu poznámky
- Přehrávání nahrávky (audio se po zpracování maže)
- Android AppWidget varianta (widget je čistě Compose uvnitř launcheru)
- Offline přepis na zařízení

## Rozhodnutí

| Téma | Volba |
|---|---|
| AI funkce | přepis + úprava (nadpis, učesaný text, kategorie) |
| Poskytovatel | Groq (free plán) |
| Umístění | Compose dlaždice na `TripScreen`, třetí vedle `JizdniDenikWidget` |
| Ovládání | ťuk = start; auto-stop po **5 s ticha**; ťuk = ruční stop; strop **2 min** |
| Kategorie | pevná sada: 💡 Nápad · ✅ Úkol · 🛒 Nákup · 🚗 Auto · 📝 Ostatní |
| Offline | audio do fronty, automatické zpracování po obnovení sítě (WorkManager) |
| Audio po zpracování | smazat |
| Přepis | nahrát `.m4a` → Groq Whisper (ne systémový `SpeechRecognizer`, ne on-device) |

### Modely (ověřeno proti Groq docs 2026-10-07)

| Krok | Model ID | Free limit |
|---|---|---|
| Přepis | `whisper-large-v3` | 20 RPM, 2 000 RPD, 28 800 s audia/den |
| Úprava | `openai/gpt-oss-120b` | 30 RPM, 1 000 RPD, 8K TPM, 200K TPD |

`whisper-large-v3` (ne `-turbo`) kvůli přesnosti v češtině. `llama-3.3-70b-versatile` **není**
ve free plánu, proto `gpt-oss-120b`. Obě ID jsou konstanty v `GroqClient`.

**Cena: 0 Kč.** Free plán nevyžaduje platební kartu; bez zadaných platebních údajů Groq nic
neúčtuje. Překročení limitu = HTTP 429, ne poplatek → poznámka počká ve frontě (viz Chyby).
Účet se proto na Developer tier **nepovyšuje** (tím by se zavedlo účtování).

## Architektura

```
ui/notes/
  VoiceNotesWidget.kt        dlaždice: mikrofon + poslední 3 poznámky
  VoiceNotesViewModel.kt     stav nahrávání, seznam, filtr kategorie
  VoiceNotesScreen.kt        celoobrazovkový seznam (vzor TripHistoryScreen)
data/notes/
  VoiceNoteEntity.kt         Room entita + NoteStatus
  VoiceNoteDao.kt
  VoiceNotesDatabase.kt      samostatná DB (nesahá na TripDatabase migrace)
  VoiceNoteRepository.kt     notes Flow, delete, retry, enqueue práce
  VoiceRecorder.kt           @Singleton, MediaRecorder + detekce ticha
  SilenceDetector.kt         čistá logika auto-stopu (testovatelná)
  GroqClient.kt              HttpURLConnection: transcribe(), refine()
  RefinedNoteParser.kt       parsování JSON odpovědi LLM + fallback
  NoteCategory.kt            enum: emoji, barva, český label
  ProcessNoteWorker.kt       @HiltWorker: Whisper → LLM → uložit → smazat audio
```

Odpovědnosti:
- **VoiceRecorder** — jen mikrofon. `start(): File`, `stop()`, `StateFlow<RecorderState>`
  (`Idle` / `Recording(amplitude, elapsedMs, silenceMs)` / `Finished(file, durationMs)`).
  Amplitudu čte přes `MediaRecorder.getMaxAmplitude()` každých 200 ms a předává `SilenceDetector`u.
  Nic neví o Groqu ani DB.
- **SilenceDetector** — čistá třída: vstup amplituda + čas, výstup „stop teď?“. Ticho = amplituda
  pod prahem (konstanta, doladit na zařízení). Hlasitý vzorek resetuje čítač ticha. Stop při 5 000 ms
  ticha nebo 120 000 ms celkem.
- **GroqClient** — bezstavový HTTP klient (`HttpURLConnection`, stejně jako zbytek appky).
  - `transcribe(file): String` — multipart POST `https://api.groq.com/openai/v1/audio/transcriptions`,
    `model=whisper-large-v3`, `language=cs`, `response_format=json`.
  - `refine(raw): RefinedNote` — POST `/openai/v1/chat/completions`, `model=openai/gpt-oss-120b`,
    `response_format={"type":"json_schema", "json_schema": {..., "strict": true}}` se schématem
    `{title: string, text: string, category: enum[IDEA, TASK, SHOPPING, CAR, OTHER]}`
    (strict = constrained decoding, `gpt-oss-120b` ho na Groqu podporuje). Systémový prompt česky:
    `title` ≤ 6 slov; `text` = učesaný text bez vycpávkových slov a opakování se správnou
    interpunkcí, význam neměnit.
  - Chyby mapuje na `GroqError.Network` / `RateLimited` (429) / `Server` (5xx) / `Auth` (401, 403) /
    `Client` (jiné 4xx).
- **RefinedNoteParser** — JSON → `RefinedNote`; chybějící/prázdné pole nebo nevalidní JSON →
  fallback (`title` = prvních 5 slov přepisu, `text` = surový přepis, `category = OTHER`);
  neznámá kategorie → `OTHER`.
- **ProcessNoteWorker** — vstup `noteId`. Orchestrace kroků, viz Tok dat.
- **VoiceNoteRepository** — jediný vstup pro UI. Po nahrání vloží řádek `PENDING` a naplánuje
  `OneTimeWorkRequest<ProcessNoteWorker>` s `NetworkType.CONNECTED`, exponenciálním backoffem,
  `enqueueUniqueWork("note-<id>", KEEP)`.

Konfigurace:
- `GROQ_API_KEY` v `local.properties` → `BuildConfig.GROQ_API_KEY` (stejný vzor jako Mapbox token).
  Prázdný klíč → widget ukáže „Chybí Groq API klíč“, nahrávání nelze spustit.
- Nové závislosti: `androidx.work:work-runtime-ktx`, `androidx.hilt:hilt-work` (+ kapt
  `androidx.hilt:hilt-compiler`). `CarLauncherApp` implementuje `Configuration.Provider`
  s `HiltWorkerFactory`; výchozí WorkManager initializer v manifestu se vypne.
- Manifest: `android.permission.RECORD_AUDIO`. Žádost in-Compose
  (`rememberLauncherForActivityResult`) při prvním ťuknutí na mikrofon, jako `IncidentRecorderScreen`.

## Datový model

`VoiceNoteEntity` (tabulka `voice_notes`):

| Pole | Typ | Poznámka |
|---|---|---|
| `id` | Long, PK autogen | |
| `createdAt` | Long | začátek nahrávání (epoch ms) |
| `durationMs` | Long | |
| `status` | `NoteStatus` | `PENDING` / `PROCESSING` / `DONE` / `FAILED` |
| `audioPath` | String? | `null` po úspěšném zpracování |
| `rawTranscript` | String? | výstup Whisperu (záloha + přeskočení kroku při retry) |
| `title` | String? | |
| `text` | String? | |
| `category` | `NoteCategory`? | `null` dokud není `DONE` |
| `errorMessage` | String? | jen u `FAILED` |
| `attempts` | Int | počet pokusů workeru |

Audio: `filesDir/voice_notes/note_<yyyyMMdd_HHmmss>.m4a` (AAC, interní úložiště).

## Tok dat

```
ťuk mikrofon → (RECORD_AUDIO?) → VoiceRecorder.start()
  → stop: 5 s ticha | ťuk | 2 min
  → Finished(file, duration)
      duration < 1 s → smazat soubor, nic neukládat
      jinak → INSERT PENDING → enqueue ProcessNoteWorker(id)

ProcessNoteWorker (spustí se až při síti):
  status = PROCESSING, attempts++
  rawTranscript == null → transcribe(audio) → uložit rawTranscript
  rawTranscript prázdný/whitespace → smazat poznámku + audio → success
  refine(rawTranscript) → title/text/category, status = DONE
  smazat audio soubor, audioPath = null
```

## Chyby

| Situace | Výsledek |
|---|---|
| Síť / timeout / 5xx / 429 | status zpět `PENDING`, `Result.retry()` (backoff) |
| `attempts` ≥ 5 a znovu retryable chyba | `FAILED`, audio zůstává |
| 401 / 403 / jiné 4xx | `FAILED` + `errorMessage`, audio zůstává |
| LLM vrátí nevalidní JSON / chybí pole | fallback z `RefinedNoteParser` → `DONE` |
| Whisper vrátí prázdný text | poznámka i audio smazány |
| Ruční „Zkusit znovu“ u `FAILED` | `PENDING`, `attempts = 0`, nový enqueue |
| Smazání poznámky | smaže řádek i audio soubor (pokud existuje), zruší unique work |
| Start appky | `.m4a` ve `voice_notes/` bez odpovídajícího řádku → smazat; řádky `PROCESSING` → `PENDING` + re-enqueue |

## UI

### VoiceNotesWidget (Trip stránka)

`TripScreen` `Row`: `TripComputerWidget` | `JizdniDenikWidget` | `VoiceNotesWidget`, každý
`weight(1f)` (~370 dp). Karta ve stylu `JizdniDenikWidget` (`CarColors.Surface`, zaoblené rohy).

```
┌───────────────────────────────┐
│ HLASOVÉ POZNÁMKY          ⤢   │
│          ╭───────╮            │
│          │  🎤   │  96dp      │
│          ╰───────╯            │
│     Ťukni a mluv              │
│ ───────────────────────────── │
│ 💡 Aplikace na tankování 14:02 │
│ ✅ Zavolat do servisu    13:40 │
│ ⏳ Zpracovávám…          13:38 │
└───────────────────────────────┘
```

Stavy tlačítka:
- **Idle** — `CarColors.Accent`, ikona mikrofonu, „Ťukni a mluv“.
- **Nahrávání** — červené, ikona stop, pulzující prstenec dle amplitudy, čas `0:12`,
  „Zastaví se po 5 s ticha“; během ticha ubývající oblouk (zbývající čas do auto-stopu).
- **Po zastavení** — krátce „Uloženo ✓“.
- **Chybí klíč / zamítnuté oprávnění** — tlumené tlačítko + vysvětlující text.

Řádek poznámky: emoji kategorie + barevný proužek vlevo, nadpis (1 řádek, ellipsis), čas
(dnes `HH:mm`, jinak `d. M.`). `PENDING`/`PROCESSING` = „⏳ Zpracovávám…“, `FAILED` = „⚠ Chyba“.
Ťuk na řádek nebo ⤢ otevře `VoiceNotesScreen`.

### VoiceNotesScreen

Přepínané stavem `showNotes` v `TripScreen` (stejně jako `showHistory`).
- Horní lišta: zpět + filtr chipy `Vše · 💡 · ✅ · 🛒 · 🚗 · 📝`.
- Seznam karet: nadpis, celý text, datum/čas, délka nahrávky.
- `FAILED` karta: `errorMessage` + tlačítko „Zkusit znovu“.
- Mazání: dlouhý stisk 1500 ms → potvrzovací dialog.

Touch targets ≥ 48 dp; mikrofon 96 dp. Za jízdy je potřeba jen ťuk na mikrofon.

Trip Computer a Jízdní deník se zúží z ½ na ⅓ šířky. Před dokončením ověřit, že se obsah vejde.
Pokud ne, zmenšit typografii/odsazení v těchto widgetech (nic jiného neměnit).

## Testování

Unit (JVM):
- `GroqClient` proti lokálnímu fake HTTP serveru: multipart tvar + `language=cs` + Bearer hlavička,
  parsování odpovědí, mapování 401 / 429 / 500 / timeout na `GroqError`.
- `RefinedNoteParser`: validní JSON, chybějící pole, nevalidní JSON, neznámá kategorie.
- `SilenceDetector`: stop po 5 s ticha, reset při hlasitém vzorku, stop na 2 min.
- `ProcessNoteWorker` (`TestListenableWorkerBuilder`, fake klient + in-memory DB): retry při síťové
  chybě, `FAILED` při 401, `FAILED` po 5 pokusech, nevolá `transcribe` když `rawTranscript` existuje,
  smaže audio po `DONE`, prázdný přepis smaže poznámku.

Ručně na tabletu:
- Nahrát → auto-stop po 5 s ticha → ⏳ → během pár sekund nadpis + kategorie.
- Režim letadlo: nahrát → zůstane ⏳ → vypnout režim letadlo → zpracuje se samo.
- Neplatný klíč → `FAILED` → opravit klíč → „Zkusit znovu“ → `DONE`.
- Trip Computer a Jízdní deník čitelné v ⅓ šířky.
