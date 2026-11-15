# Qintar v2 — Product & Technical Design

| | |
|---|---|
| Status | Draft 2, revised after owner review on 2026-11-15 |
| Audience | The owner, and the AI model (Fable / Opus / Sonnet) that will implement and maintain v2 |
| Scope | Complete rewrite of the Android app. v1 (the current `app/` tree) is a behavioural reference only |
| Tracks | GitHub issues #3–#21 and the owner's Keep note of user feedback (all addressed here, see §3.3) |

## 0. How to use this document

If you are the implementing model, read this first.

- **This document is the source of truth for v2.** Where it disagrees with `README.md`, `CLAUDE.md` or v1 code, this document wins. Both of those files describe v1 and are partly inaccurate (the README still describes bookmark groups and image-based ayahs, which no longer exist).
- **Do not port v1 code.** v1 was an experiment; its data model is the root cause of most open issues (§2). Reuse only: the translated string resources where they still apply, the launcher icon, and v1's on-disk formats, which you must be able to *read* for migration (§7.11).
- **Words like "must", "never", "always" are requirements.** Sections marked *Decision* record a choice that was made deliberately, with the reason; do not reopen them without the owner. Sections marked *Owner to confirm* have a working default that you should implement, and the owner may change.
- **Requirement IDs** (`R-TRK-3`) are stable. Reference them in commit messages, tests and PR descriptions.
- Things in this document that were **verified** against the repository or the web on 2026-11-15 are stated plainly. Things that you must verify at implementation time are marked **Verify**.
- Build in the order of §9. Each milestone ends in a working, tested app.

---

## 1. Product

### 1.1 What Qintar is

Qintar is a Quran reading **habit tracker**. It counts the ayahs a person reads each day and shows where that day stands on a ladder of seven ranks taken from a hadith of the Prophet (peace be upon him and his family):

> Whoever recites **ten** ayahs in a night is not written among the heedless; **fifty**, is written among those who remember; **one hundred**, among the devout; **two hundred**, among the humble; **three hundred**, among the victorious; **five hundred**, among the strivers; and whoever recites **one thousand** ayahs, a *qintar* of good is written for them.

Everything else in the app (a reader, khatm, bookmarks, audio, statistics) exists so that counting is effortless, truthful and motivating.

One-sentence pitch: *read however you like — in the app, by listening, or from a paper mushaf — and Qintar keeps an honest daily count and shows you the rank you reached.*

### 1.2 Who it is for

A practising Muslim who reads Quran regularly and wants consistency. Typical day:

1. Reads a fixed daily set (a *wird*): e.g. Ayat al-Kursi every day, Surah al-Kahf on Fridays, al-Mulk at night.
2. Continues a *khatm* (reading the whole Quran front to back) a few pages at a time.
3. Sometimes reads from a printed mushaf at the mosque and wants that counted too.
4. Glances at today's count and rank, and occasionally at the month.

The owner is a Persian speaker; the app is trilingual (Persian, Arabic, English) and must feel native in right-to-left.

### 1.3 Product principles

1. **Tracking is the product.** Any screen where the user reads must make counting a single, obvious gesture, and must show the day's count reacting to it.
2. **One honest log.** Every count on every screen derives from one append-only log of reading events. Nothing is stored twice (§4.2).
3. **Offline first.** Text, navigation, tracking, statistics and backup work with no network. The network is used only to fetch audio.
4. **Calm, not gamified.** The ladder comes from a hadith, so it is presented with dignity: no confetti storms, no guilt notifications, no streak-freeze currency, no emoji medals.
5. **Private.** No account, no analytics, no ads, no third-party SDKs that phone home.
6. **Never lose the user's history.** Every schema change ships with a tested migration; destructive fallback is forbidden (§7.5).
7. **Small enough for one maintainer.** Prefer one reusable mechanism over three special cases (one reader, one tracker, one player).

### 1.4 Non-goals for 2.0

Not in 2.0, and the design does not need to make room for them beyond what is noted:

- Translations and tafsir (the Quran schema leaves room: §7.4).
- Cloud sync or accounts (backup files cover portability).
- **Shared khatm** (from the feedback note: random ayahs are handed out to participants, what they read is stored on a server, completed group khatms are counted every few hours and participants are notified). *Decision (owner): deferred.* It needs a backend, identity and push notifications. The event log keyed by AyahId with a `context_type` column lets a future `SHARED_KHATM` context be added without reshaping local data.
- **A hadith feed** — a browsable page of narrations about particular ayahs and surahs, each with an "add to my bookmarks" action. *Decision (owner): version 3.* What 2.0 does for it: a bookmark carries a free-text **description** that can hold such a narration ("whoever reads this ayah every morning…"), a repeat target and a schedule, and the bookmark editor can be opened from a pre-filled draft (§4.7). A feed item is then nothing more than a draft. 2.0 has no server and no content feed.
- Memorisation tooling (repeat-N-times loops, hide-text testing).
- Mushaf page-image / line-accurate layout. The reader is text in a continuous scroll.
- Prayer times, qibla, social features, widgets, Wear OS. (A home-screen widget is the first candidate after 2.0.)
- A user-selectable primary colour (v1 had nine). v2 has one considered palette.

---

## 2. What v1 taught us

Findings from reading the v1 code and data on 2026-11-15. Each one maps to a v2 rule.

| # | v1 finding | Consequence | v2 rule |
|---|---|---|---|
| F1 | A day is one row: `reading_activity(date, totalAyahsRead, trackedAyahIds JSON, badgeLevel)`. Ayah ids are strings `"bookmarkId:surah:ayah"`; khatm and random both use bookmark id `-1`. | No timestamps, no per-ayah history, an ayah cannot be read twice in a day, khatm cannot know what was read in *this* khatm, count and badge are denormalised and can drift. Blocks #11, #13, #17, #21. | Event log, §4.2 |
| F2 | `GetTodayStatsUseCase` captures `LocalDate.now()` once when the flow is created. | After midnight the screen keeps observing yesterday while writes go to the new day (#10). | `DayClock`, §4.3 |
| F3 | Khatm state is a single row holding `lastPageRead`. | One khatm ever; read marks vanish the next day (#11). | §4.6 |
| F4 | Khatm is a `HorizontalPager` of 604 pages. | Wrong swipe direction in RTL (#8); owner wants a long scroll (#11). | One vertical reader, §5.4 |
| F5 | Audio queueing and read-tracking are re-implemented in three view models (Khatm, Bookmarks, BookmarkReading — 300 to 500 lines each), driven by side effects inside a `combine` lambda with `delay(100)` / `delay(500)` to sequence things. | Divergent behaviour, races. | One reader, one player, §7.8 |
| F6 | Audio is a singleton around raw `MediaPlayer`, released from `ViewModel.onCleared()`. No media session, no foreground service. | Playback dies with the screen; listening in the background is not counted; no lock-screen controls. | Media3 session service, §7.8 |
| F7 | The bundled text is alquran.cloud edition `quran-uthmani-quran-academy`, described by its publisher as *"modified … to work with the Kitab font"*, but v1 renders it with the system default font. It contains no U+064A (Arabic yeh) at all — every yeh is U+06CC (Farsi yeh), 22,024 of them. | Wrong-looking letters, depending on the device font (#6). | Text and font chosen as a pair, §7.4 |
| F8 | Basmala is concatenated onto ayah 1 of each surah; the `sajda` column is 0 for all 6,236 rows (the downloader coerced the API's object value to `False`); surah fields are duplicated on every verse row. | Cannot format the basmala (#14), cannot show sajda (#14). | Data pipeline with validation, §7.4 |
| F9 | Both Room databases use `fallbackToDestructiveMigration()` and `exportSchema = false`. | A schema change silently wipes the user's history. | §7.5 |
| F10 | Streak is counted back from today, so it reads 0 every morning until 10 ayahs are read. | Demotivating, looks like a bug. | §4.5 |
| F11 | `DailyReminderWorker` is a deprecated stub that does nothing; the manifest still requests `SCHEDULE_EXACT_ALARM` and `USE_EXACT_ALARM`. | Advertised feature does not work; `USE_EXACT_ALARM` is a store-policy risk. | §7.9 |
| F12 | Calendar week start is hard-coded to Sunday; Gregorian only. | #3; unnatural for Persian users. | §5.7, R-SET-4/5 |
| F13 | Ranks are emoji (🔹🔺🥉🥈🥇🎖👑); typography is `FontFamily.Default`; 48 places put raw exception text or English literals in the UI; Arabic strings (hadith, rank names) are typed with Persian letters (ی ک). | Looks unfinished; #6, #16. | §6 |
| F14 | ~30 one-line use-case classes wrapping repository calls in `Result`, swallowing exceptions. kapt, Gson, SharedPreferences. | Boilerplate without value. | §7.2, §7.3 |
| F15 | `CLAUDE.md` claims comprehensive ViewModel and UI tests; `main` has 16 test files, none for view models, and one placeholder instrumented test. PR #22 (tests, CI) is open and unmerged. | #18, #19, #20. | §8 |

Reported by a user in the feedback note, not reproduced here, and prevented in v2 by construction:

| Report | v2 answer |
|---|---|
| "Cache doesn't work. When offline, ayahs are not available." | Text is bundled and never fetched (§7.4). Audio is cached by the player itself (§7.8). |
| "Audio not working, sometimes works." | F5/F6: replaced by one Media3 service; every playback error is surfaced with a retry, never swallowed (§7.8). |
| "Random page shows English in Persian." | F13: no string literals; screenshot tests in Persian for every screen (§6.6). |
| "At-Taghabun 13 cannot be added; Al-Ma‘arij 25 crashes." | A test iterates all 6,236 ayahs through bookmark creation, text loading, formatting and queue building (§8.1). |

What v1 got right and v2 keeps: the hadith ladder as the core idea; the seven thresholds; reading by tapping an ayah *or* by listening to it; khatm, bookmarks and random as entry points; JSON backup; three languages; Clean-ish layering with Hilt, Room, Compose and Flow.

---

## 3. Goals and requirements

### 3.1 Goals

- **G1 — Truthful daily count.** Every reading, from any source, at any time of day, lands on the right day exactly once, and can be inspected and corrected.
- **G2 — Effortless marking.** Marking a page of reading takes one gesture; marking by listening takes none.
- **G3 — Khatm that remembers.** Per-khatm coverage, position, pace and statistics that persist across days.
- **G4 — A daily routine.** Bookmarks can be scheduled so that Today shows what is due.
- **G5 — Insight.** Day, week, month and all-time views; per-ayah recency so neglected passages surface.
- **G6 — A reader that is pleasant.** Correct Uthmani text in a proper Quran font, user-sized, formatted the way the owner reads.
- **G7 — Clean, modern, pretty.** A deliberate design system, identical quality in light and dark, in LTR and RTL.
- **G8 — Safe to change.** Migrations, backups, tests and CI such that a model can ship changes without a human re-testing everything by hand.

### 3.2 Requirements

Priority: **P0** required for 2.0 · **P1** should ship in 2.0, may slip to 2.1 · **P2** later.

**Tracking (TRK)**

| ID | P | Requirement |
|---|---|---|
| R-TRK-1 | P0 | Every reading is stored as an event: which ayah, which reading day, when, in what context, by what method. |
| R-TRK-2 | P0 | Every reading counts, every time. Tapping an ayah's check adds one; the row shows the number. There is no decrease by tapping: a "Set count" action in the row's ⋯ menu sets the number directly. |
| R-TRK-9 | P0 | In a khatm, ticking an ayah that skips over unread ayahs is never silent: the app offers to tick everything from the last read ayah up to it. |
| R-TRK-3 | P0 | The reading day is decided at the moment of writing. Screens open across the day boundary keep working without navigation. |
| R-TRK-4 | P0 | Marking is correctable: a single ayah by "Set count", bulk marks by an Undo snackbar, anything on any day in Day detail. |
| R-TRK-5 | P0 | Bulk marking: "mark up to here", "mark page", and "log a full reading" of a whole bookmark or group. |
| R-TRK-6 | P0 | Manual log: add reading done outside the app, for today or any past day, as a passage, a bookmark, or a bare number. Any past day's entries can be added to, changed and deleted. |
| R-TRK-7 | P0 | An ayah whose audio plays to its end is counted (setting, default on), including with the screen off. |
| R-TRK-8 | P1 | "Day starts at" setting so late-night reading can belong to the previous day. |

**Ranks, streaks, Today (RNK)**

| ID | P | Requirement |
|---|---|---|
| R-RNK-1 | P0 | Seven ranks at 10, 50, 100, 200, 300, 500, 1000 ayahs per day. |
| R-RNK-2 | P0 | Today shows count, current rank, distance to next rank, streak, this week, the khatm to continue, and what is due. |
| R-RNK-3 | P0 | Streak does not show as broken merely because today has not been read *yet*. |
| R-RNK-4 | P0 | A mild visual effect when a rank is reached, shown wherever the user is — including inside the reader. |
| R-RNK-5 | P1 | User picks a daily goal rank; Today and reminders refer to it. |

**Reader (RDR)**

| ID | P | Requirement |
|---|---|---|
| R-RDR-1 | P0 | One reader component for every context, vertical continuous scroll, no horizontal paging. |
| R-RDR-2 | P0 | Browse by surah, juz and page; jump to surah:ayah, page, juz. |
| R-RDR-3 | P0 | Quran font size setting with live preview. |
| R-RDR-4 | P0 | Line break after pause marks (setting, default on); basmala on its own line followed by a blank gap; page, juz and sajda markers. |
| R-RDR-5 | P0 | Every ayah row offers: count (+1), quick bookmark (star), play, and a ⋯ menu. |
| R-RDR-6 | P0 | Screen stays on while reading or listening in the reader. |
| R-RDR-7 | P0 | Position indicator: page of 604 with percent, page within juz, page within surah. |
| R-RDR-10 | P0 | The reader always shows today's progress: ayahs so far, current rank, distance to the next. |
| R-RDR-8 | P1 | Random ayah / random page. |
| R-RDR-9 | P2 | Search the Quran text. |

**Khatm (KHT)**

| ID | P | Requirement |
|---|---|---|
| R-KHT-1 | P0 | A khatm remembers every ayah read in it, on any day, and where to resume. |
| R-KHT-2 | P0 | Several khatms can run at the same time, each with a name (its goal); completed khatms are kept as history. |
| R-KHT-3 | P0 | Per-khatm statistics: ayahs and pages per day, progress, pace. |
| R-KHT-4 | P0 | A khatm can be started from a position other than the beginning. |
| R-KHT-5 | P0 | Optional pages-per-day goal per khatm. It is a guide, never a gate. Progress bars for: today's goal, current surah, current juz, whole Quran. |

**Bookmarks (BMK)**

| ID | P | Requirement |
|---|---|---|
| R-BMK-1 | P0 | A bookmark is a passage: one ayah, a range, a surah, a page or a juz, with optional label, description and repeat target ("read 100 times"). |
| R-BMK-2 | P0 | One-tap star on any ayah creates or removes a single-ayah bookmark. |
| R-BMK-3 | P0 | Schedule on a bookmark or a group: weekdays (all = daily), optional time window (08:00–12:00), or once. |
| R-BMK-4 | P0 | While creating or editing, the passage's ayahs themselves are shown live, as text, exactly as chosen. |
| R-BMK-5 | P0 | Sort by mushaf order, custom (drag), or recently added. |
| R-BMK-9 | P0 | Bookmark groups ("Morning", "Friday"): a named set of bookmarks that can be found, opened and read as one, with its own schedule. |
| R-BMK-10 | P0 | A "Now" view lists every bookmark available at this moment. |
| R-BMK-6 | P0 | Opening a bookmark lands on its first unread ayah; a control jumps there at any time. |
| R-BMK-7 | P1 | Play all of today's due bookmarks as one queue. |
| R-BMK-8 | P0 | A bookmark or group whose ayahs have all been read today (to their repeat target) changes colour, everywhere it is listed. |

**Insights (INS)**

| ID | P | Requirement |
|---|---|---|
| R-INS-1 | P0 | Daily chart and calendar with rank per day; totals, average, best day, current and longest streak. |
| R-INS-2 | P0 | Day detail: what was read that day, by context; add, edit, delete. |
| R-INS-3 | P0 | Each ayah knows when it was last read; a Revisit view lists what was read longest ago. |
| R-INS-4 | P1 | Split by source (khatm / bookmarks / browsing / manual) and method (read / listened). |

**Audio (AUD)**

| ID | P | Requirement |
|---|---|---|
| R-AUD-1 | P0 | Per-ayah playback through the current context, background and lock-screen controls, speed 0.5–2×. |
| R-AUD-2 | P0 | Reciter selection. |
| R-AUD-3 | P1 | Played audio is cached; bookmarks' audio can be downloaded for offline use. |

**Settings, data, platform (SET / DAT / PLT)**

| ID | P | Requirement |
|---|---|---|
| R-SET-1 | P0 | Language: system, English, Arabic, Persian. |
| R-SET-2 | P0 | Theme: system, light, dark. Every screen legible in both. |
| R-SET-3 | P0 | Daily reminder at a chosen time that actually fires. |
| R-SET-4 | P0 | First day of week: system, Saturday, Sunday, Monday. |
| R-SET-5 | P1 | Calendar system for display: Gregorian, Persian (Solar Hijri), Islamic (Hijri). |
| R-DAT-1 | P0 | Export everything to one file; import with preview, replace or merge. |
| R-DAT-2 | P0 | v1 data is carried over on upgrade, and v1 backup files import. |
| R-DAT-3 | P0 | No schema change without a tested migration. |
| R-PLT-1 | P0 | CI on every PR: static checks, unit tests, instrumented tests, build, version bump, upgrade test. |
| R-PLT-2 | P0 | Merge to `main` builds a signed APK and publishes a GitHub release. |

### 3.3 Issue traceability

| Issue | Title | Addressed by |
|---|---|---|
| #3 | Start of the week in configuration | R-SET-4, §5.7 |
| #4 | Preview of ayahs when adding a bookmark | R-BMK-4, §5.6 |
| #5 | Font size | R-RDR-3, §5.4 |
| #6 | Encoding Arabic "ya" | F7, §7.4, §6.6 (*Owner to confirm*, Q1) |
| #7 | Prevent screen off | R-RDR-6 |
| #8 | Next page in khatm (RTL swipe) | R-RDR-1 removes the pager; RTL rule §6.6 |
| #9 | Normal reading — index by surah, juz, page | R-RDR-2, §5.3 |
| #10 | 00:00 problem | R-TRK-3, §4.3 |
| #11 | Khatm enhancements | R-KHT-1…3, R-RDR-1, R-RDR-7, §4.6, §5.5 |
| #12 | Quick bookmark | R-BMK-2 |
| #13 | Last time an ayah was read | R-INS-3, §4.8 |
| #14 | Ayah view formatting | R-RDR-4, §5.4.2 |
| #15 | Bookmark with date | R-BMK-3, §4.7 |
| #16 | Time is difficult to read in night mode | R-SET-2, §6.7 |
| #17 | Bookmarks enhancements | R-BMK-5, R-BMK-6, R-TRK-2 |
| #18 | Android tests | §8.2 |
| #19 | Android build on merge to master | R-PLT-2, §8.4 |
| #20 | Main branch checks | R-PLT-1, §8.4 |
| #21 | Add read ayahs | R-TRK-6, R-TRK-5, §5.8 |

The owner's Keep note repeats most of the above. Its additional items:

| Note item | Addressed by |
|---|---|
| Count the same ayah once or more; show a number next to the ayah | R-TRK-2, §4.2 — owner decided on 2026-11-15: always count every time, no setting |
| If all ayahs in a group are read, change its colour | R-BMK-8, R-BMK-9, §5.6 |
| Khatm: do not let later ayahs be ticked, unless an option asks "tick all from the last read to here?" | R-TRK-9, §4.2.1 |
| Offline Quran; cache and audio unreliable; untranslated random page; two failing ayahs | §2, second table |
| Shared khatm with a server | Deferred, §1.4 (Q11) |

PR #22 and branch `issue_21` target v1 and are superseded; close them when v2 lands.

---

## 4. Domain rules

This section is the specification of behaviour. Implement it as pure Kotlin in `:domain` with exhaustive unit tests before building screens.

### 4.1 Glossary

| Term | Meaning |
|---|---|
| **AyahId** | Global ayah number, 1…6236 in mushaf order. The only ayah key used in storage. |
| **AyahRef** | `(surah 1…114, ayah 1…n)`. For display and input only. |
| **AyahRange** | Inclusive, contiguous `start..end` of AyahIds. A surah, page, juz or bookmark is an AyahRange. |
| **Reading day** | A calendar date to which a reading belongs (§4.3). |
| **Read event** | One counted reading of one ayah. |
| **Tally** | A count-only manual entry ("I read 40 ayahs"), with no ayah identity. |
| **Context** | Where a reading happened: a khatm, a bookmark, browsing, random, or the manual log. |
| **Rank** | One of seven levels of the hadith ladder, earned per reading day. |
| **Khatm** | One pass through the whole Quran. |
| **Group** | A named set of bookmarks read together ("Morning"), optionally with a schedule. |
| **Available now** | A bookmark whose schedule (its own or its group's) matches the current day and time. |
| **Repeat target** | How many readings per day make a bookmark done (default 1). |

### 4.2 The reading log

There are two kinds of record, and nothing else contributes to any count.

```kotlin
data class ReadEvent(
    val id: Long,
    val ayahId: AyahId,
    val day: LocalDate,          // reading day, fixed when written
    val readAt: Instant,         // wall-clock moment of recording
    val context: ReadContext,
    val method: ReadMethod,
    val batchId: String?,        // shared by events created in one bulk action
)

sealed interface ReadContext {
    data class Khatm(val id: Long) : ReadContext
    data class Bookmark(val id: Long) : ReadContext
    data object Browse : ReadContext
    data object Random : ReadContext
    data object Log : ReadContext      // manual entry not attributed to a khatm
}

enum class ReadMethod { TAP, BULK, AUDIO, MANUAL, IMPORT }

data class Tally(val id: Long, val day: LocalDate, val count: Int, val note: String?, val createdAt: Instant)
```

Rules:

- **Every reading counts, every time.** Two events for the same ayah on the same day are two readings.
- **Day total** = number of ReadEvents with that `day` + sum of Tally counts for that `day`. Computed by query, never stored (F1). `DayTotals` is the single place this rule lives; no other code may count events.
- *Decision (owner, 2026-11-15):* there is no "count once" mode. Some narrations prescribe reading a passage many times (a hundred, for example), so repeats must always count and the user must be able to raise the number freely.
- The log is append-only in normal use. Removal happens only through the explicit correction paths of R-TRK-4.
- Deleting a bookmark or a khatm does **not** delete its events. The reading happened; history keeps it and shows the context as "deleted bookmark" / "deleted khatm".
- Events are written through exactly one component, `ReadingTracker` (§7.6). Screens, the audio service and importers all go through it.

#### 4.2.1 Counts on screen: scope, tapping, editing

Each ayah row in the reader has a check that doubles as a counter. The number it shows is the count of events for that ayah **in the current context, today**:

| Context | Count shown | Extra state |
|---|---|---|
| Khatm *K* | events with context Khatm *K* today | **covered**: an event in *K* exists on any day, or the ayah is in the khatm's assumed-covered prefix (§4.6) |
| Bookmark *B* | events with context Bookmark *B* today | **target**: the bookmark's repeat target (§4.7) |
| Browse | events with context Browse today | — |
| Random | events with context Random today | — |

*Decision — why per-context and not "read anywhere today":* a user who met Ayat al-Kursi in their khatm this morning and now opens their morning bookmark must see it at zero and tap once to count the second reading. Per-context scope makes every tap mean one thing, and an edit can only ever touch events of the screen you are on.

**Tapping (R-TRK-2):**

- Tap the check → **+1**, always. One event (`TAP`), one haptic tick. Zero → filled check; then `×2`, `×3`, … beside it.
- There is no tap that decreases. *Decision (owner):* a toggle made it impossible to tell "read again" from "undo".
- **Set count…** (in the row's ⋯ menu and in the ayah sheet) opens a number field pre-filled with today's count in this context, 0–9999. Raising it writes the missing events in one batch; lowering it deletes the newest of today's events in this context; 0 removes them all. This is how a user records "I read this 100 times", and the only way to reduce a count from the reader.
- **Long-press on the check** = "mark up to here" (§4.2.2).
- Readings from an earlier day — including khatm coverage ticked on an earlier day — are not editable from the reader. They are changed in that day's Day detail (§5.7).

**Khatm is sequential (R-TRK-9).** A khatm is read in order, so a tick that would leave unread ayahs behind it is almost always a slip. In a khatm, when the tapped ayah is not covered and the ayah before it is *not* covered either (and it is not ayah 1):

- Let `from` = the ayah after the nearest earlier covered ayah, and *n* = the number of uncovered ayahs from `from` to the tapped ayah.
- Do not write anything yet. Show a small dialog: "Mark all *n* ayahs from Al-Anfal 12 to here as read?" with **Mark *n* ayahs** (primary), **Only this ayah**, and **Cancel**.
- "Only this ayah" exists because a khatm may legitimately be started or resumed from a jumped-to position (§8.2 scenario 2). After it is used once at a position, the following ayahs are in sequence again and tick silently. *Owner to confirm* (Q12) whether to keep this escape or forbid gaps entirely.
- An in-sequence tap, and any tap on an already covered ayah (a re-reading), writes immediately with no dialog.

#### 4.2.2 Bulk marking (R-TRK-5)

Two kinds, and the difference matters:

*Fill* — give one reading to ayahs that have none in scope. Never adds to an ayah that already has a count (in a khatm: that is already covered).

- **Mark up to here** (long-press the check, or the ayah sheet): from the start of the unread run through the tapped ayah. "Start of the unread run" = the first ayah after the nearest *earlier* ayah that is read in scope, bounded by the scope start (the bookmark's start; for browse and khatm, the start of the surah containing the first on-screen ayah when the reader was opened, or the resume position, whichever is later).
- **Mark page** (control on each page divider): the page's unread ayahs.

*Full reading* — add one reading to **every** ayah of a passage, whatever its current count.

- **Log a full reading** on a bookmark: +1 for each of its ayahs. Pressing it three times records three complete readings. This is the action for "I read the whole surah elsewhere" and for repeat targets.
- **Log a full reading** on a group: the same for every bookmark in the group.
- **Set count…** on a bookmark: sets every ayah's count for today to the number given (raising or lowering, as for a single ayah).

All events of one bulk action share a `batchId`, use method `BULK`, and are announced by a snackbar "*n* ayahs marked · Undo". Undo deletes the batch.

#### 4.2.3 Manual log and editing other days (R-TRK-6)

Input: a reading day (today by default, any past day, never the future) and one of:

- a passage — from/to AyahRef, or whole surah(s), page range, or juz;
- a bookmark or a group, with a number of full readings (default 1);
- a bare number.

A passage produces one event per ayah, method `MANUAL`, `readAt = now`, `day = chosen day`, one `batchId`, context `Log` — or `Khatm(K)` if the user ticks "count toward khatm *K*" (this is how reading from a paper mushaf advances a khatm). A bookmark or group produces the same with context `Bookmark(id)`, so it also shows as read in that bookmark on that day. A bare number produces a Tally. The sheet shows the resulting ayah count before confirming.

Forgetting to log is normal, so past days are fully editable: from the calendar, any day opens Day detail, where the user can add readings for that day through this same sheet, change the count of an entry, or delete it.

### 4.3 Reading day and the clock (R-TRK-3, R-TRK-8)

```kotlin
interface DayClock {
    fun now(): Instant
    fun today(): LocalDate            // always computed fresh
    val todayFlow: StateFlow<LocalDate>   // emits when the reading day changes
}
```

- `today() = (now() in system zone − dayStartOffset).toLocalDate()`. `dayStartOffset` is the "Day starts at" setting, default 00:00, allowed 00:00–12:00. *Owner to confirm* (Q3): the hadith says "in a night", so a user who reads after midnight may want that counted with the evening before; a 03:00 or 04:00 day start does that.
- `todayFlow` re-emits at each boundary and on `ACTION_TIME_CHANGED`, `ACTION_TIMEZONE_CHANGED`, `ACTION_DATE_CHANGED`, and when the setting changes.
- **Never cache a date.** Writers call `today()` at the moment of writing. Readers build their queries with `todayFlow.flatMapLatest { day -> … }`. A lint rule or detekt rule forbids `LocalDate.now()` and `System.currentTimeMillis()` outside the `DayClock` implementation.
- An event's `day` is never recomputed. Changing time zone or the day-start setting affects only future events.
- All production code receives time through `DayClock`. Tests and instrumented tests replace it (this is the "mock today" asked for in #18).

### 4.4 Ranks (R-RNK-1)

| Order | Enum (stable, stored nowhere but used in code) | Threshold | English | فارسی | العربية |
|---|---|---|---|---|---|
| 0 | `NONE` | 0 | — | — | — |
| 1 | `NOT_HEEDLESS` | 10 | Not among the heedless | غیر غافل | غير الغافلين |
| 2 | `DHAKIR` | 50 | Dhakir · the remembering | ذاکر | الذاكرين |
| 3 | `QANIT` | 100 | Qanit · the devout | قانت | القانتين |
| 4 | `KHASHI` | 200 | Khashi‘ · the humble | خاشع | الخاشعين |
| 5 | `FAIZ` | 300 | Fa’iz · the victorious | فائز | الفائزين |
| 6 | `MUJTAHID` | 500 | Mujtahid · the striver | مجتهد | المجتهدين |
| 7 | `QINTAR` | 1000 | Qintar | صاحب قنطار | القنطار |

- `rankFor(count)` = highest rank whose threshold ≤ count. Rank is always derived from the day total, never stored.
- Before 10 there is no rank. The UI says how many remain to the first one; it does not invent a rank 0 name.
- Arabic strings use Arabic letters (ي ك), Persian strings use Persian letters (ی ک). v1 mixed them (F13).
- *Decision (owner):* a vetted presentation of the hadith's Arabic text with citation is not part of 2.0; narrations as content arrive with the hadith feed in version 3 (§1.4). In 2.0 the ladder is explained in the app's own words (rank names, thresholds, one sentence saying it follows a narration about nightly recitation), and v1's hadith screen and its string are not carried over. *Owner to confirm* (Q2) that this is what was meant.

### 4.5 Streaks (R-RNK-3)

Inputs: a map `day → total` and today's date. A day **qualifies** if total ≥ 10 (rank 1).

- **Current streak**: if today qualifies, the run of consecutive qualifying days ending today; otherwise the run ending yesterday. If neither today nor yesterday qualifies, 0.
- **At risk**: current streak > 0 and today does not yet qualify. The UI shows the streak number with a "read today to keep it" hint, not a zero.
- **Longest streak**: the longest run of consecutive qualifying days ever.
- *Decision:* the streak threshold is fixed at rank 1 and does not follow the user's goal rank, so changing the goal never rewrites the streak. The goal has its own measure in Insights ("goal met 22 of the last 30 days").
- No streak freezes, no repair. (Principle 4.)

### 4.6 Khatm (R-KHT-*)

```kotlin
data class Khatm(
    val id: Long,
    val name: String,                 // the user's name or goal for it: "Ramadan khatm"; default "Khatm" + ordinal
    val startedDay: LocalDate,
    val dailyPages: Float?,           // optional goal, e.g. 4 pages a day. A guide only.
    val assumedCoveredUpTo: Int,      // AyahId; 0 = none. Ayahs 1..this count as covered without events.
    val completedAt: Instant?,
)
```

Derived, never stored:

- **Coverage** = `{1..assumedCoveredUpTo}` ∪ `{ayahId of every event with context Khatm(id)}`. Progress % = `|coverage| / 6236`.
- **Resume position** = the ayah after the most recently recorded (`readAt`) event in this khatm; if there are no events, `assumedCoveredUpTo + 1`. Scrolling or jumping never changes it — only marking does. (This is the behaviour #18's khatm scenario verifies.)
- **First unread** = the smallest AyahId not in coverage. Offered as a jump target whenever it differs from the resume position.
- **Completed** when coverage has all 6,236 ayahs. Set `completedAt`, show a completion screen (dates, days taken, average per day), offer "Start a new khatm". A completed khatm is read-only history.
- **Per-day statistics**: for each reading day, ayahs = events in this khatm that day; pages = Σ over those events of `1 / ayahsOnPage(pageOf(ayah))`, shown to one decimal. This gives a deterministic "pages read" without tracking page visits.
- **Progress bars** (R-KHT-5), shown on the khatm card, in Khatm detail and in the reader's position sheet:
  - *Today* — pages read in this khatm today ÷ `dailyPages`. Hidden when no daily goal is set. It may exceed 100% and is never shown as a failure when below.
  - *Current surah* — covered ayahs of the surah containing the resume position ÷ its ayah count.
  - *Current juz* — the same for the juz containing the resume position.
  - *Whole Quran* — coverage ÷ 6236.
- **Pace**: with a daily goal, projected finish = today + remaining pages ÷ `dailyPages`. Without one, use the average of the last 7 active days. *Decision (owner):* the goal is not strict — it never blocks reading, never produces a "behind" warning or a notification of its own.

`assumedCoveredUpTo` exists for two cases: a user who installs mid-khatm ("I am already at juz 12"), and v1 migration, which knows only the last page (§7.11). *Decision:* one integer instead of synthetic events, so day counts are never polluted by readings that did not happen on a known day.

Several khatms may be active at once (R-KHT-2), for example a personal one and a Ramadan one. They are listed by most recent activity; the first is "primary" and is the one shown in full on Today, with the others as compact rows beneath it.

### 4.7 Bookmarks, groups and schedules (R-BMK-*)

```kotlin
data class Bookmark(
    val id: Long,
    val range: AyahRange,
    val kind: BookmarkKind,           // AYAH, RANGE, SURAH, PAGE, JUZ — how it was created; drives the title
    val label: String?,               // user's name for it, e.g. "Ayat al-Kursi"
    val description: String?,         // free text, up to 2000 chars: why the user reads it, a narration about it
    val repeatTarget: Int,            // readings per day that make it "done"; default 1, e.g. 100
    val groupId: Long?,               // at most one group
    val schedule: Schedule?,          // its own schedule; null = inherit the group's
    val sortIndex: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class BookmarkGroup(
    val id: Long,
    val name: String,                 // "Morning", "Friday", "After prayer"
    val description: String?,
    val schedule: Schedule?,
    val sortIndex: Int,
    val createdAt: Instant,
)

data class Schedule(
    val days: Set<DayOfWeek>,         // non-empty; all seven = every day
    val window: TimeWindow?,          // null = any time of day
    val once: Boolean = false,        // bookmark-level only
)
data class TimeWindow(val from: LocalTime, val to: LocalTime)   // to < from means it crosses midnight

/** Everything needed to open the editor pre-filled. Quick actions and, in v3, hadith-feed items produce one. */
data class BookmarkDraft(val range: AyahRange, val kind: BookmarkKind, val label: String? = null,
                         val description: String? = null, val repeatTarget: Int = 1,
                         val groupId: Long? = null, val schedule: Schedule? = null)
```

**Title** when there is no label: `AYAH` → "Al-Baqarah 255"; `RANGE` → "Al-Kahf 1–10" (or "Al-Baqarah 285 – Āl ‘Imrān 5" across surahs); `SURAH` → "Al-Kahf"; `PAGE` → "Page 293"; `JUZ` → "Juz 30".

**Description** is a first-class part of a bookmark, not a hidden note. It shows as two lines in lists and in full above the bookmark's ayahs in the reader. The owner's intended use is to keep the narration that motivates the reading next to the passage.

**Groups (R-BMK-9).**

- A bookmark belongs to one group or to none ("Ungrouped"). *Decision:* one, not many. Counts are kept per bookmark, so a bookmark shared by "Morning" and "Night" would arrive at night already read. To read the same passage in two groups, use "Duplicate to another group", which makes an independent bookmark.
- Deleting a group asks whether to delete its bookmarks or move them to Ungrouped. Readings are kept either way (§4.2).
- There are no tags in v2; groups replace them (v1 tags are migrated to groups, §7.11).

**Schedule and availability (R-BMK-3, R-BMK-10).** Evaluated against the reading day and the device's local time.

- A bookmark's **effective schedule** is its own if set, otherwise its group's, otherwise none.
- **Scheduled today**: the reading day's weekday is in `days`.
- **Available now**: scheduled today, and either there is no window or the current time is inside it. A window that crosses midnight (22:00–02:00) belongs to the weekday on which it starts; its after-midnight part is tested against the previous weekday.
- **Later today**: scheduled today, window not yet started. **Earlier today**: window passed, not done. Neither is an error; both stay reachable. The window decides what is *shown first*, not what is allowed.
- `once`: scheduled every day (within its window, if any) until done once; at the next day boundary after that, the bookmark's own schedule is cleared. The bookmark is kept.
- A bookmark with no effective schedule is never listed as available; it is found under its group or under All.

**Progress and done (R-BMK-8).**

- Per ayah, today's count in context Bookmark(id), capped at `repeatTarget`.
- Bookmark progress today = Σ capped counts ÷ (range size × `repeatTarget`). **Done today** when every ayah has reached the target. "Full readings today" = the minimum count over its ayahs (shown as "37 / 100" when the target is above 1).
- Group progress = its bookmarks' progress combined the same way; a group is done when all its bookmarks are.

**Quick star** (R-BMK-2): toggles a bookmark of kind `AYAH` for that ayah, with no group, no schedule, target 1. An ayah is "starred" iff such a single-ayah bookmark exists. Removing by star shows an Undo snackbar; if the bookmark has a label, description, group, schedule or a target above 1, un-starring asks for confirmation first.

**Order** (R-BMK-5): groups have a custom order. Inside a group, inside Ungrouped and in All, the sort mode applies: mushaf order (`range.start`), custom (`sortIndex`, drag), recently added. The mode is a persisted preference. New items get `sortIndex = max + 1`.

### 4.8 Recency and Revisit (R-INS-3)

- `lastRead(ayah)` = the greatest `day` over that ayah's events, any context. `timesRead(ayah)` = number of events.
- **Revisit** groups by mushaf page (604 groups, a bounded list). For each page: `oldest` = the minimum `lastRead` over its ayahs, treating never-read as older than everything; `coverage` = share of its ayahs ever read.
- Two tabs: **Read long ago** (pages with every ayah read at least once, oldest first) and **Not read yet** (pages with any never-read ayah, in mushaf order). Each row: surah name(s), page, "last read 4 months ago" or "12 of 15 ayahs never read", and opens the reader in Browse context at that page.
- The ayah sheet (§5.4.3) shows "Last read 12 Nov · 7 times".
- Tallies have no ayah identity and do not affect recency.

---

## 5. User experience

### 5.1 Information architecture

Bottom navigation, four destinations. Settings is reached from the Today top bar.

```
Today        Read          Bookmarks       Insights
  │            │               │               │
  │            ├ Khatm cards   ├ Now           ├ Overview (chart, tiles)
  │            ├ Index:        ├ Groups        ├ Calendar → Day detail
  │            │  Surah|Juz|Page ├ All          ├ Khatm statistics
  │            ├ Go to…        ├ add / edit    └ Revisit
  │            └ Random        │
  └──────────────┬─────────────┘
               Reader  (one screen; opened with a context)
```

**Read is the free-reading view**: no khatm and no bookmark involved — find a surah, juz, page or ayah and read it, with counting. Full-screen, outside the tabs: Reader, Khatm detail, Add/Edit bookmark, Add/Edit group, Log reading (sheet), Day detail, Settings and its sub-screens, Onboarding.

### 5.2 Today (R-RNK-2)

Wireframes are drawn left-to-right; in Persian and Arabic the layout mirrors.

```
┌────────────────────────────────────────┐
│ Sunday 15 November            🔔   ⚙   │  date in the user's calendar system
│                                        │
│              ╭─────────╮               │
│           ╭──╯         ╰──╮            │  Progress ring: 0 → next threshold
│           │      47       │            │  count: display numerals, tabular
│           │   of 50 · 3 to│            │
│           ╰──╮  Dhakir ╭──╯            │
│              ╰─────────╯               │
│    ◆ Not among the heedless            │  current rank mark + name
│    ●  ●  ○  ○  ○  ○  ○                 │  ladder: 7 rank marks, reached = filled
│                                        │
│  ┌ 12-day streak ───────────────────┐  │
│  │ S   M   T   W   T   F   S        │  │  week strip, honours week-start
│  │ ◆   ◆   ●   ◆   ●   ·   47       │  │  each day: its rank mark; today: count
│  └──────────────────────────────────┘  │
│                                        │
│  Continue khatm                        │
│  ┌──────────────────────────────────┐  │
│  │ Ramadan khatm          Page 200  │  │
│  │ ▓▓▓▓▓▓▓░░░░░░░░░░  33%           │  │
│  │ Today  ▓▓▓▓▓▓░░░░  2.5 of 4 pages│  │
│  │                      [ Continue ]│  │
│  └──────────────────────────────────┘  │
│                                        │
│  Available now                 2 left  │
│  ┌──────────────────────────────────┐  │
│  │ Morning · 08:00–12:00    [ Read ]│  │  group header: opens the whole group
│  │ ○ Ayat al-Kursi          1 ayah  │  │  tap → reader
│  │ ◔ Al-Ikhlas             37 / 100 │  │  repeat target
│  │ Ungrouped                        │  │
│  │ ◔ Al-Kahf · Fridays     42 / 110 │  │
│  │ ▸ Later today (3)                │  │
│  │ ▸ Done (1)                       │  │
│  └──────────────────────────────────┘  │
│                                        │
│  [ + Log reading ]                     │  manual entry (§5.8)
├────────────────────────────────────────┤
│  Today     Read    Bookmarks  Insights │
└────────────────────────────────────────┘
```

- The ring fills from 0 to the **next** threshold (47 of 50), so "nearly there" looks nearly full. At rank 7 the ring is complete and the count keeps rising.
- If a goal rank is set (R-RNK-5) and not yet met, the caption names the goal rather than the next rank ("53 to Qanit, your goal").
- When a rank is reached (R-RNK-4): the new rank mark scales in over the ring with a soft gold pulse, one haptic confirmation, a one-line message. No modal, no sound. Once per rank per day. The same event is shown in the reader (§5.4.1), which is where it usually happens.
- The "Available now" section is the same list as the Bookmarks tab's Now view (§5.6), limited to items that are not done; "Later today", "Earlier today" and "Done" are collapsed groups beneath. Long-press an item → Log a full reading.
- Other active khatms appear as compact rows under the primary card.
- Empty states: no khatm → card "Start a khatm"; nothing scheduled → "Give a bookmark or a group a schedule to build a daily routine"; count 0 → ring empty with "10 ayahs to the first rank".
- Streak at risk: "12-day streak · read today to keep it".

### 5.3 Read (R-RDR-2)

- Top: khatm cards (active first; "New khatm"; link to finished khatms).
- Segmented control **Surah · Juz · Page**.
  - Surah: number, Arabic name, transliteration, ayah count, Meccan/Medinan, a thin coverage bar (share of ayahs ever read). Filter field matches number, Arabic name, transliteration.
  - Juz: 30 rows, each with its starting surah:ayah and the eight hizb quarters as tappable segments.
  - Page: a grid of 604 numbers, tinted by recency (§4.8); type a number to jump.
- "Go to…" accepts `2:255`, `255` within a chosen surah, `p300`, `j12`.
- "Random ayah" / "Random page" (R-RDR-8): uniform over AyahIds / pages (v1's random ayah was biased toward short surahs because it picked a surah first).
- "Continue where I left off" restores the last Browse scroll position.

### 5.4 Reader (R-RDR-*)

The reader is opened with `ReaderArgs(segments: List<Segment>, startAt)`, where a `Segment(context, range)` is a contiguous passage counted under one context. Most entries have one segment; a group has one per bookmark.

| Entry | Context | Scope | Opens at |
|---|---|---|---|
| Khatm → Continue | Khatm(id) | 1..6236 | resume position |
| Bookmark | Bookmark(id) | bookmark range | first ayah below its target today, else start |
| Bookmark group | one Bookmark(id) per segment | each bookmark's range, in group order | first bookmark that is not done |
| Index / Go to / Revisit | Browse | 1..6236 | chosen ayah |
| Random | Random | the ayah or page | its start |

#### 5.4.1 Layout

```
┌────────────────────────────────────────┐
│ ←  Ramadan khatm              Aa   ⋮   │  collapses on scroll down
│ ┌────────────────────────────────────┐ │
│ │ Page 200 of 604 · 33%   Juz 10  ▾  │ │  position bar (tap → position sheet)
│ └────────────────────────────────────┘ │
│                                        │
│            ── Al-Anfal ──              │  surah header: name, 75 ayahs, Medinan
│                                        │
│      بِسْمِ ٱللَّهِ ٱلرَّحْمَـٰنِ ٱلرَّحِيمِ       │  basmala: centred, not an ayah
│                                        │
│   يَسْـَٔلُونَكَ عَنِ ٱلْأَنفَالِ ۖ                │  line breaks after pause marks
│   قُلِ ٱلْأَنفَالُ لِلَّهِ وَٱلرَّسُولِ ۖ            │
│   فَٱتَّقُوا۟ ٱللَّهَ … مُّؤْمِنِينَ ۝١            │
│   8:1               ⋯    ☆    ▷    ◯   │  meta line: ref · menu · star · play · check
│  ────────────────────────────────────  │  hairline between ayahs
│   إِنَّمَا ٱلْمُؤْمِنُونَ … ۝٢                  │
│   8:2               ⋯    ★    ▷  ● ×2  │  read twice today in this context
│                                        │
│  ───── Page 201 · Juz 10 · Hizb 19 ──  │  page divider
│              [ Mark page 200 read ]    │  appears if the page has unread ayahs
│                                        │
│ ┌────────────────────────────────────┐ │
│ │ ◆ 47 today · 3 to Dhakir     ▓▓▓▓░ │ │  today pill: rank mark, count, next
│ └────────────────────────────────────┘ │
│ ┌────────────────────────────────────┐ │
│ │ ▷  Al-Anfal 2 · Alafasy   1×   ✕   │ │  mini player (only when audio active)
│ └────────────────────────────────────┘ │
└────────────────────────────────────────┘
```

- One vertical list for the whole scope. No horizontal paging anywhere in the reader (R-RDR-1).
- Row types: surah header, basmala, ayah, page divider (carries juz and hizb when they change), end-of-scope footer.
- The **today pill** (R-RDR-10) is the feedback loop of principle 1 and is always visible in the reader, even when the top bar is collapsed: current rank mark, today's ayah count, distance to the next rank, and a thin bar toward it. Every tap visibly moves it. Tapping it opens Today's summary as a sheet.
- **Rank reached in the reader** (R-RNK-4): the pill's bar fills, the pill takes a gold outline that pulses once (about 600ms), the new rank mark scales in in place of the old one, the text reads "Dhakir reached" for three seconds, and there is one haptic confirmation. Nothing covers the text being read and nothing needs dismissing. With system animations off: the text and mark change without motion.
- In a group or a bookmark, each segment starts with a **bookmark header**: title, schedule chip, "37 / 100" if it has a repeat target, the full description, and a "Log a full reading" button. A bookmark with a repeat target above 1 also pins a large **+1 full reading** button above the today pill, so a short passage can be counted like a tasbih without scrolling.
- The check is a 48dp target at the end of the meta line. States: zero (outline), read today (filled, primary, with `×n` from the second reading), covered on an earlier day — khatm only (filled, muted), playing (ring animation around the play icon). The ⋯ menu holds Set count…, Mark up to here, Bookmark…, Copy, Share.
- **Position bar and sheet** (R-RDR-7), computed from the first fully visible ayah: "Page 200 of 604 · 33%"; "Juz 10 — page 12 of 20"; "Al-Anfal — page 3 of 10" (page index within the surah's page span). The sheet adds jump controls (surah, juz, page, ayah) and, in a khatm, "Resume position" and "First unread". In a khatm the sheet also shows the four progress bars of §4.6. In a bookmark or group, a floating chip "First unread ↓" appears whenever the first ayah below its target is off-screen (R-BMK-6).
- **Aa panel** (R-RDR-3): Quran font size slider (range 20–48sp, default 28sp, live), "Break lines at pause marks" switch, "Keep screen on" switch.
- **Keep screen on** (R-RDR-6): while the reader is in the foreground and either audio is playing or the setting is on (default on). Implemented with the window flag, released when the reader leaves the foreground.
- Scroll follows audio: when the playing ayah changes and the user has not scrolled manually in the last 5 seconds, bring it into view.
- Scope 1..6236 must not load the Quran into memory: see §7.7.

#### 5.4.2 Ayah formatting (R-RDR-4, #14)

- The stored text is never altered. Formatting is applied when building the display string.
- **Pause-mark line breaks** (when the setting is on): insert a line break at the first space after each of U+06D6 (ۖ ṣalā), U+06D7 (ۗ qalā), U+06D8 (ۘ mīm), U+06DA (ۚ jīm). Do not break at U+06D9 (ۙ lā, "do not stop") or U+06DB (ۛ, paired three dots).
- **Basmala**: its own centred row before ayah 1 of every surah except 1 (where it *is* ayah 1) and 9 (none). It is never counted and has no check. The vertical gap after it equals two text lines.
- **Ayah end**: U+06DD followed by the ayah number in Arabic-Indic digits, rendered by the Quran font as the ornamented marker. Always Arabic-Indic, regardless of app language.
- **Sajda**: ayahs with a sajda show ۩ and a small label "Sajdah" on the meta line, with the type (obligatory / recommended) from the dataset. *Owner to confirm* (Q5) whether to show the type, since schools differ.
- **Page / juz / hizb**: shown on the page divider row; a juz start additionally gets a prominent "Juz 10" label.
- Quran text is always right-to-left and uses the Quran font, whatever the app language.

#### 5.4.3 Ayah sheet

Opened by tapping the ayah text (not the meta line).

- Header: "Al-Baqarah 255 · Page 42 · Juz 3" and "Last read 12 Nov · 7 times".
- Actions: **Set count…** · **Mark read up to here** · **Play from here** · **Bookmark…** (opens the editor from a draft; lets the user extend to a range, add a description, pick a group and a schedule) · **Copy** · **Share** (text plus reference).

### 5.5 Khatm

- **Khatm list** (top of Read): every active khatm as a card — name, the four progress bars of §4.6, Continue. "New khatm". Link to finished khatms.
- **New khatm** sheet: name / goal (pre-filled "Khatm 2"), "Start from" (beginning, or pick a position → sets `assumedCoveredUpTo`), optional pages per day with the projected finish date shown live.
- **Khatm detail**: the four progress bars; position; started; daily goal and projected finish; a 30-juz strip showing coverage per juz; per-day chart of pages and ayahs (R-KHT-3) with the daily goal as a guide line; actions: Continue, First unread, Rename, Change daily goal, Delete (confirmation explains that the readings stay in history).
- **Completion** screen as in §4.6.

### 5.6 Bookmarks

Three views, switched by a segmented control at the top. "+" adds a bookmark or a group.

**Now** (R-BMK-10, the default view)

- Everything available at this moment (§4.7), under its group's header, then Ungrouped. A group header shows the group's name, its window ("08:00–12:00"), combined progress, and **Read**, which opens the whole group in the reader.
- Beneath: "Later today", "Earlier today" and "Done" as collapsed sections.
- Empty: "Nothing is scheduled for now" with shortcuts to Groups and All.

**Groups** (R-BMK-9)

- All groups in custom order, then Ungrouped. Each row: name, schedule chip ("Daily 08–12", "Fri"), number of bookmarks, today's progress. Tap → the group's page: description, its bookmarks (sortable, drag between groups from the edit mode), Read all, Log a full reading, Edit, Delete.
- Search field: matches group names, bookmark titles, labels, descriptions and surah names.

**All**

- Every bookmark in one list with the sort menu (R-BMK-5) and the same search.

**Rows and states**

- Bookmark row: title, two lines of description, schedule chip, ayah count, today's progress ("42 / 110" ayahs, or "37 / 100" readings when it has a repeat target). Tap → reader. Swipe or long-press → Log a full reading, Set count…, Edit, Move to group, Duplicate to another group, Delete (Undo).
- **Read state by colour (R-BMK-8):** nothing read today → plain `surface` card; partly read → a thin progress bar in `primary`; done today → the card switches to `primaryContainer` with a filled check in place of the bar. Group headers and group rows follow the same three states from the group's combined progress. The same states apply on Today and to the bookmark header in the reader. Colour is never the only signal: the check icon and a "Read today" label accompany it.

**Add / Edit bookmark** (R-BMK-4)

1. Kind (Ayah · Range · Surah · Page · Juz) and the passage, with the same pickers as Go to….
2. **Ayah preview**: directly under the pickers, the ayahs of the chosen passage themselves — the Quran text in the Quran font with ayah-end markers, formatted as in the reader, every ayah of the passage, in a scrollable area (about 40% of the screen, expandable to full screen; loaded lazily so a juz is not a problem). It updates as the pickers change. Above it, one line: "110 ayahs · pages 293–304". *Decision (owner):* the preview shows the text the user is about to bookmark. It is not a mock-up of the bookmark's card.
3. Label, description (multi-line, 2000 characters), repeat target (default 1), group (None or pick/create), schedule (inherit from group · every day · chosen weekdays, chips ordered by the week-start setting · once; optional "only between" time window).

Validation is inline: end must not precede start; ayah numbers are bounded by the surah; a window needs both times.

**Add / Edit group**: name, description, schedule (weekdays and optional time window), and its bookmarks.

### 5.7 Insights

- **Overview**: period switch Week · Month · Year · All. Bar chart of daily totals with faint horizontal guides at the rank thresholds that fall inside the y-range; bars coloured by the rank reached. Tiles: total ayahs, active days, average per active day, best day, current streak, longest streak. Rank distribution (days at each rank). Source split (R-INS-4).
- **Calendar**: month grid in the chosen calendar system (R-SET-5) starting on the chosen weekday (R-SET-4). Each day cell holds that day's rank mark and, beneath, the count. Horizontal swipe changes month and follows layout direction (in RTL, swiping right goes to the *next* month). Tap a day → Day detail.
- **Day detail** (R-INS-2, R-TRK-6): total and rank for that day; entries grouped by context and batch ("Ramadan khatm · 37 ayahs · pages 200–202 · listened"); each group expandable to ayahs with their counts. For any day, past or today: **Add reading** (the manual log pre-set to that day), **Set count** on an entry or an ayah, **Delete** an entry. Ranks and streaks re-derive at once.
- **Khatm statistics**: list of khatms, each opening Khatm detail.
- **Revisit**: §4.8.

Charts must follow the palette and rules of §6; bars use at most the rank ramp, text uses ink tokens, and every chart has a text alternative for TalkBack ("Monday, 120 ayahs, Qanit").

### 5.8 Log reading (R-TRK-6, #21)

A bottom sheet from Today, Day detail, and the reader's overflow menu.

```
Log reading
Day        [ Today ▾ ]                      any past day; never the future
What       ( Passage ) ( Surah ) ( Pages ) ( Juz ) ( Bookmark ) ( Just a number )
From       Al-Baqarah ▾   1
To         Al-Baqarah ▾   141
           141 ayahs · pages 2–21
☐ Count toward khatm  [ Ramadan khatm ▾ ]
                                   [ Add 141 ayahs ]
```

"Bookmark" lists bookmarks and groups and asks for the number of full readings (default 1). After adding: snackbar "141 ayahs added to today · Undo".

### 5.9 Settings

| Group | Items |
|---|---|
| Reading | Quran font size · Break lines at pause marks · Keep screen on · Daily goal rank · Day starts at |
| Audio | Reciter · Quality · Default speed · Count listened ayahs as read · Download on Wi-Fi only · Storage (cache size, clear, downloads) |
| Appearance | Language · Theme · Numerals (follow language / Latin) · Calendar system · First day of week |
| Reminders | Daily reminder on/off and time · Streak-at-risk reminder on/off and time |
| Data | Export · Import · (P2) automatic export to a folder |
| About | The seven ranks · Sources and licences · Version · Source code |

### 5.10 Onboarding

Three short screens on first launch, skippable: the ladder of seven ranks and what earns each; "mark what you read, or just listen"; choose language, optional reminder time (the notification permission is requested here only if a reminder is turned on). Then Today. If v1 data was migrated, a single card on Today says what was carried over.

### 5.11 Reminders (R-SET-3)

- Daily reminder at the chosen time. Text depends on state: nothing read → "Ten ayahs keep you from among the heedless."; in progress → "47 ayahs today — 3 more to Dhakir."; goal already met → no notification.
- Streak-at-risk reminder (optional, default off): fires only if the current streak is at risk at that time.
- Tapping opens Today. One notification channel per kind, so users can silence one.

---

## 6. Visual design system

The brief is **clean, modern, pretty**. The direction that delivers that for a Quran app: *quiet paper, deep ink, one green, and gold only for achievement.* Because gold appears nowhere else, the user learns that gold means progress.

Build these as a small design-system package (`ui/designsystem`) first; screens may use only its tokens and components. Hard-coded colours, sizes or font families in feature code are a review failure.

### 6.1 Colour tokens

Map onto Material 3 `ColorScheme` plus an `ExtendedColors` holder for the gold and rank roles. **Dynamic colour (Material You) is off**: the palette is part of the identity.

| Role | Light | Dark | Use |
|---|---|---|---|
| `background` | `#FAF7F0` | `#0F1514` | Screen background (warm paper / green-black) |
| `surface` | `#FFFDF8` | `#151C1B` | Cards, sheets |
| `surfaceContainer` | `#F3EEE3` | `#1C2524` | Grouped areas, pills, chips |
| `outlineVariant` | `#E2DACB` | `#2C3836` | Hairlines, card borders |
| `onSurface` | `#1E2A2A` | `#E6ECE9` | Primary text, Quran text |
| `onSurfaceVariant` | `#5E6B68` | `#9DAAA6` | Secondary text, quiet icons |
| `primary` | `#0F5C55` | `#7FCFC3` | Actions, read state, links |
| `onPrimary` | `#FFFFFF` | `#00382F` | |
| `primaryContainer` | `#D5EBE6` | `#1F4A44` | Selected chips, today cell |
| `onPrimaryContainer` | `#05332F` | `#C9EEE7` | |
| `gold` | `#B8862B` | `#E2B659` | Ring, rank marks, rank-up — graphics only |
| `goldInk` | `#7A5A12` | `#E2B659` | Gold-coloured **text** |
| `goldContainer` | `#F4E7C8` | `#4A3A14` | Rank chip background |
| `error` | `#B3261E` | `#F2B8B5` | Destructive, validation |

- An optional "pure black" switch replaces dark `background` with `#000000`.
- **Contrast is enforced by test, not by trust:** a unit test computes WCAG contrast for every text-role/background pair above in both themes and fails under 4.5:1 (3:1 for the graphic-only `gold`). The hex values above were chosen to pass; if one does not, darken or lighten that token and keep the hue. This rule is what fixes #16 for good.

### 6.2 Typography

| Role | Family | Notes |
|---|---|---|
| UI text, all three languages | **Vazirmatn** (variable, OFL) | One family covering Persian, Arabic and Latin gives consistent weight and metrics across languages. Bundle the font; do not use downloadable fonts (offline). |
| Quran text | **Amiri Quran** (OFL) | Paired with the text edition, see §7.4. Second choice: Scheherazade New (OFL). |
| Numbers in counters and charts | Vazirmatn with tabular figures | So counts do not jitter as they change. |

Type scale (sp / line height): Display 56/60 semibold (the Today count) · Headline 24/32 semibold · Title 18/26 medium · Body 16/24 regular · Label 13/18 medium · Caption 12/16 regular. Quran text: user size (default 28) with line height 1.9× to give the diacritics room. All sizes scale with the system font setting, except the Quran text, which has its own slider.

**Verify** at implementation: Vazirmatn's Latin is acceptable to the owner for English UI (alternative: Inter for Latin, Vazirmatn for Arabic script, combined in one `FontFamily` fallback chain).

### 6.3 Shape, spacing, elevation

- 4dp spacing grid. Screen horizontal margin 20dp. Vertical rhythm between cards 16dp, between sections 28dp.
- Corner radii: cards and sheets 20dp, inner elements 12dp, chips and pills fully rounded.
- **No shadows.** Separation comes from the background/surface tone difference and a 1dp `outlineVariant` border. Elevation is used only for the floating today pill and mini player (tonal, level 2).
- Content max width 640dp, centred, on tablets and landscape.
- Edge-to-edge with correct insets; predictive back supported.

### 6.4 Rank marks

Replace v1's emoji with one **parametric vector family**, drawn in Compose `Canvas` by a single function `RankMark(rank, size, state)`. The motif is the eight-pointed star of Quranic ornament (as in ۞), growing in richness:

| Rank | Mark |
|---|---|
| 1 Not heedless | Small solid dot inside a thin ring |
| 2 Dhakir | Solid diamond (square rotated 45°) |
| 3 Qanit | Eight-pointed star, outline |
| 4 Khashi‘ | Eight-pointed star, solid |
| 5 Fa’iz | Solid eight-pointed star inside a thin ring |
| 6 Mujtahid | Twelve-pointed rosette, solid, with a centre dot knocked out |
| 7 Qintar | Sixteen-pointed rosette, solid, ringed, centre dot — the only mark with a soft radial glow |

States: `reached` (gold), `unreached` (`outlineVariant` stroke only), `small` (calendar cells, 16dp: ranks must stay distinguishable at this size — test it with a screenshot). Each has a content description: the rank's name.

### 6.5 Components

Build once, in the design system: `ProgressRing`, `RankMark`, `RankLadder`, `WeekStrip`, `TodayPill`, `QCard` (surface + border), `SectionHeader`, `ScheduleChip`, `BookmarkCard`, `GroupHeader`, `ProgressBarRow`, `CountField`, `AyahRow`, `PageDivider`, `SurahHeader`, `PositionBar`, `MiniPlayer`, `StatTile`, `DayBarChart`, `MonthGrid`, `EmptyState`, pickers (`SurahPicker`, `AyahNumberField`, `PagePicker`, `JuzPicker`, `WeekdayChips`). Each has `@Preview`s for light, dark, and RTL, and a screenshot test.

Icons: Material Symbols, rounded, outline style, one weight. Directional icons (back, next, chevrons, play) use the auto-mirrored variants.

### 6.6 Language, direction, numerals

- Three locales: `en`, `ar`, `fa`. Per-app language through the AndroidX per-app language API with `locales_config.xml`; no custom context wrapping (v1's `LocaleHelper` goes).
- Every string is a resource, including errors and content descriptions. Plurals use plural resources. No string concatenation for sentences.
- **Letters:** Arabic resources use Arabic code points (ي U+064A, ك U+0643); Persian resources use Persian ones (ی U+06CC, ک U+06A9). A unit test scans `values-ar` for U+06CC/U+06A9 and `values-fa` for U+064A/U+0643 and fails on a hit (allow-list for deliberate Arabic quotations in Persian strings). This, together with §7.4, is the fix for #6.
- **Direction:** layouts use start/end only. No gesture has a hard-coded left/right meaning; anything horizontal (month swipe, weekday order, progress fill, chart x-axis) follows layout direction. Screenshot tests run in `fa` for every screen.
- **Numerals:** in `fa` use Persian digits (۰–۹), in `ar` Arabic-Indic (٠–٩), in `en` Latin — through `NumberFormat` for the app locale; the "Numerals: Latin" setting overrides. Ayah-end markers are always Arabic-Indic.
- **Dates:** formatted through ICU for the chosen calendar system. Storage is always ISO.

### 6.7 Motion and accessibility

- Motion is brief and purposeful: ring and bars animate to new values (spring, ≈300ms); the today pill count rolls; the check fills and its `×n` ticks over in 120ms; the rank-reached pulse of §5.4.1 is the largest effect in the app. Honour the system "remove animations" setting.
- Minimum touch target 48dp. TalkBack: an ayah row is one focusable node — "Al-Baqarah, ayah 255, read today" — with custom actions Mark read, Bookmark, Play.
- Every screen has a screenshot test in light and dark (#16 was a dark-mode contrast failure in a hand-rolled time picker; use the Material 3 time picker, themed by tokens).

---

## 7. Architecture

### 7.1 Platform and stack

- Kotlin, Jetpack Compose with Material 3, single activity.
- `minSdk 26` (native `java.time`, adaptive icons; v1 was 24), `targetSdk` and `compileSdk` the latest stable at implementation time (v1 already targets 36).
- **applicationId stays `io.github.mehrdad_abdi.quranbookmarks`** so v2 installs over v1 and can migrate its data. Code namespace becomes `io.github.mehrdad_abdi.qintar`. `versionName` starts at `2.0.0`, `versionCode` at 200.
- Libraries: Hilt (KSP, not kapt) · Room with exported schemas · DataStore · Media3 (ExoPlayer, Session) · WorkManager · Navigation Compose with type-safe routes · kotlinx.serialization · kotlinx.coroutines / Flow.
- Tests: JUnit, Turbine, Robolectric (Room and Android-dependent unit tests), Compose UI test, a screenshot-test library (Roborazzi or the official Compose screenshot testing).
- Versions live only in `gradle/libs.versions.toml`. Use the latest stable releases when scaffolding and look up their current setup docs rather than relying on memory. JDK 17 or newer, whatever the chosen Android Gradle Plugin requires.
- Dropped from v1: Retrofit, Gson, Coil, kapt, SharedPreferences, AppCompat-era theming.

### 7.2 Modules

```
:domain   pure Kotlin/JVM. Models, rules of §4, interfaces. No Android imports.
:data     Android library. Room, DataStore, Quran asset DB, backup codec, legacy importer.
:app      UI (Compose), navigation, DI wiring, playback service, reminders.

app → data → domain,  app → domain
```

*Decision:* three modules, not one and not fifteen. The single boundary that matters — business rules cannot touch Android — is enforced by the compiler, and `:domain` tests run in milliseconds. Feature separation inside `:app` is by package.

```
:app  io.github.mehrdad_abdi.qintar
  ui/designsystem/      tokens, theme, components (§6)
  ui/navigation/
  feature/today/        TodayScreen, TodayViewModel
  feature/read/         index, go-to, random
  feature/reader/       ReaderScreen, ReaderViewModel, formatting
  feature/khatm/
  feature/bookmarks/
  feature/insights/     overview, calendar, daydetail, revisit
  feature/log/
  feature/settings/
  feature/onboarding/
  playback/             PlaybackService, queue builder, PlayerController
  reminders/
  di/
```

### 7.3 Conventions

- **State:** each screen has one immutable `UiState` exposed as `StateFlow`, built with `combine(...)` over repository flows and `stateIn(viewModelScope, WhileSubscribed(5_000), initial)`. UI calls plain functions on the view model. One-off effects (snackbar, navigation) go through a `Channel`.
- **No side effects inside `combine`/`map`.** (F5.)
- **No `delay()` for sequencing.** Await the thing itself.
- **Use cases only where there is logic.** View models may read repositories directly. The domain services that do exist are named in §7.6.
- **Errors:** domain and data throw or return typed failures (`sealed interface`); view models map them to string resources. Never show `exception.message`. Never wrap everything in `Result` to make exceptions disappear.
- **Time** only through `DayClock`. **Dispatchers** injected.
- Comments explain *why*. No log statements in release builds beyond errors.

### 7.4 Quran data (F7, F8, #6, #14)

**Sources — Decision:**

| Asset | Source | Licence, as checked 2026-11-15 |
|---|---|---|
| Text | **Tanzil**, Uthmani, with pause marks and sajda signs, XML format | CC BY 3.0. Verbatim copies only; attribute Tanzil Project and link to tanzil.net; keep the copyright notice. |
| Structure (surahs, juz, hizb quarters, pages of the Madinah mushaf, sajdas with type, ruku, manzil) | Tanzil `quran-data.xml` | Same project. **Verify** the terms file shipped with the download. |
| Quran font | **Amiri Quran**, from the Amiri project | SIL OFL 1.1 (confirmed for the Amiri project). **Verify** the Quran cut is in the release you download and include `OFL.txt`. |

Rejected: the alquran.cloud `quran-uthmani-quran-academy` text v1 ships (it is modified for one specific font, F7); the KFGQPC Hafs font (its licence text forbids reproduction without written approval, so it cannot be bundled without asking).

Why Tanzil's XML: it carries the basmala as an attribute of ayah 1 rather than inside the text, so nothing has to be stripped, and the text stays verbatim.

**Build pipeline.** `tools/quran-data/` holds a script (Kotlin script or Python) that turns the pinned source files (checked in under `tools/quran-data/source/` with their SHA-256 and licence files) into `data/src/main/assets/quran.db`. The generated database is committed; CI re-runs the script and fails if the output differs. The app never downloads Quran text.

```sql
CREATE TABLE surah (
  number        INTEGER PRIMARY KEY,   -- 1..114
  name_ar       TEXT NOT NULL,         -- الفاتحة
  name_latin    TEXT NOT NULL,         -- Al-Fātiḥah
  name_en       TEXT NOT NULL,         -- The Opening
  revelation    TEXT NOT NULL,         -- MECCAN | MEDINAN
  ayah_count    INTEGER NOT NULL,
  first_ayah_id INTEGER NOT NULL,
  has_basmala   INTEGER NOT NULL       -- 0 for surahs 1 and 9
);
CREATE TABLE ayah (
  id           INTEGER PRIMARY KEY,    -- 1..6236
  surah        INTEGER NOT NULL,
  number       INTEGER NOT NULL,       -- within surah
  text         TEXT NOT NULL,          -- verbatim, no basmala prefix
  page         INTEGER NOT NULL,       -- 1..604
  juz          INTEGER NOT NULL,       -- 1..30
  hizb_quarter INTEGER NOT NULL,       -- 1..240
  sajda        INTEGER NOT NULL        -- 0 none, 1 recommended, 2 obligatory
);
CREATE INDEX ayah_page ON ayah(page);
CREATE UNIQUE INDEX ayah_ref ON ayah(surah, number);
-- room for later: translation(edition, ayah_id, text); ayah_search FTS (R-RDR-9)
```

**Validation tests** (run in CI against the generated file): 114 surahs; 6,236 ayahs; ids contiguous; per-surah ayah counts match the metadata; pages 1…604 and non-decreasing; 30 juz; 240 hizb quarters; exactly 15 sajda ayahs; no ayah text begins with the basmala except 1:1; the set of code points equals a checked-in allow-list; every code point in the allow-list has a glyph in the bundled Quran font (so a missing glyph can never reach a user as a box).

**Rendering check — Verify:** before committing to the font, render these in a screenshot test and have the owner look at them: 1:1–7, 2:255, 2:282 (longest ayah), 36:1, 19:1 and 42:1–2 (disjoined letters), 96:19 (sajda), 12:11 (ishmām mark), the ayah-end marker with one-, two- and three-digit numbers. This screenshot is also the artefact that answers issue #6 (Q1).

`QuranIndex` (in `:domain`, implemented in `:data`) loads the structural columns — not the text — into primitive arrays at startup (6,236 × a few ints; a few hundred KB) and answers every structural question synchronously: `ayahId(ref)`, `ref(id)`, `pageOf`, `juzOf`, `hizbQuarterOf`, `sajdaOf`, `surah(n)`, `pageRange(p)`, `juzRange(j)`, `surahRange(n)`, `ayahsOnPage(p)`. Text is read from the database on demand.

### 7.5 User database (F1, F9)

File `qintar.db`, separate from `quran.db`. Room, `exportSchema = true`, schemas committed under `data/schemas/`. **`fallbackToDestructiveMigration` must not appear anywhere for this database.** Every version bump adds a `Migration` and a `MigrationTestHelper` test that migrates a populated database and checks row counts and totals.

```sql
CREATE TABLE read_event (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  ayah_id      INTEGER NOT NULL,          -- 1..6236
  day          INTEGER NOT NULL,          -- reading day as epoch day
  read_at      INTEGER NOT NULL,          -- epoch millis
  context_type TEXT    NOT NULL,          -- KHATM | BOOKMARK | BROWSE | RANDOM | LOG
  context_id   INTEGER,                   -- khatm.id or bookmark.id; NULL otherwise. Deliberately no FK (§4.2)
  method       TEXT    NOT NULL,          -- TAP | BULK | AUDIO | MANUAL | IMPORT
  batch_id     TEXT
);
CREATE INDEX read_event_day     ON read_event(day);
CREATE INDEX read_event_ayah    ON read_event(ayah_id, day);
CREATE INDEX read_event_context ON read_event(context_type, context_id, ayah_id);
CREATE INDEX read_event_batch   ON read_event(batch_id);

CREATE TABLE read_tally (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  day INTEGER NOT NULL, count INTEGER NOT NULL CHECK (count > 0),
  note TEXT, created_at INTEGER NOT NULL
);
CREATE INDEX read_tally_day ON read_tally(day);

CREATE TABLE khatm (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  started_day INTEGER NOT NULL,
  daily_pages REAL,                              -- optional goal
  assumed_covered_up_to INTEGER NOT NULL DEFAULT 0,
  completed_at INTEGER,
  created_at INTEGER NOT NULL
);

CREATE TABLE bookmark_group (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  description TEXT,
  schedule_days INTEGER NOT NULL DEFAULT 0,      -- bitmask, bit 0 = Monday … bit 6 = Sunday; 0 = no schedule
  window_from INTEGER, window_to INTEGER,        -- minutes of day; both NULL = any time
  sort_index INTEGER NOT NULL,
  created_at INTEGER NOT NULL
);

CREATE TABLE bookmark (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  start_ayah_id INTEGER NOT NULL,
  end_ayah_id   INTEGER NOT NULL CHECK (end_ayah_id >= start_ayah_id),
  kind TEXT NOT NULL,                            -- AYAH | RANGE | SURAH | PAGE | JUZ
  label TEXT, description TEXT,
  repeat_target INTEGER NOT NULL DEFAULT 1 CHECK (repeat_target >= 1),
  group_id INTEGER REFERENCES bookmark_group(id) ON DELETE SET NULL,
  schedule_days INTEGER NOT NULL DEFAULT 0,      -- 0 = no own schedule (inherit the group's)
  window_from INTEGER, window_to INTEGER,
  schedule_once INTEGER NOT NULL DEFAULT 0,
  sort_index INTEGER NOT NULL,
  created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL
);
CREATE INDEX bookmark_group_id ON bookmark(group_id);
```

Key queries (all observable as `Flow`):

```sql
-- totals per day in a range (charts, calendar, streak input).
SELECT day, SUM(n) FROM (
  SELECT day, COUNT(*) AS n FROM read_event WHERE day BETWEEN :from AND :to GROUP BY day
  UNION ALL
  SELECT day, SUM(count)    FROM read_tally WHERE day BETWEEN :from AND :to GROUP BY day
) GROUP BY day;

-- counts for a reader window (count per ayah, today, one context); a group runs it per segment
SELECT ayah_id, COUNT(*) FROM read_event
 WHERE context_type = :type AND context_id IS :id AND day = :today
   AND ayah_id BETWEEN :start AND :end GROUP BY ayah_id;

-- khatm coverage (distinct ayahs) and newest event (resume position)
SELECT COUNT(DISTINCT ayah_id) FROM read_event WHERE context_type='KHATM' AND context_id=:k AND ayah_id > :assumed;
SELECT ayah_id FROM read_event WHERE context_type='KHATM' AND context_id=:k ORDER BY read_at DESC, id DESC LIMIT 1;

-- recency per ayah (Revisit; aggregate to pages in Kotlin with QuranIndex)
SELECT ayah_id, MAX(day), COUNT(*) FROM read_event GROUP BY ayah_id;
```

*Decision:* no summary or cache tables. A heavy user writes about 365,000 rows a year (1,000 ayahs a day); with the indexes above every query is a range scan. A performance test seeds 1,000,000 events and asserts the Today and month queries stay under 50ms on the CI emulator. Add a cache only if that test fails.

Preferences live in DataStore as one typed `UserPrefs` (language, theme, pureBlack, quranFontSp, breakAtPauseMarks, keepScreenOn, goalRank, dayStartMinutes, weekStart, calendarSystem, latinNumerals, reciterId, audioQuality, playbackSpeed, countListened, wifiOnlyDownloads, reminder and streak-reminder enabled/time, bookmarkSort, lastBrowseAyahId, onboardingDone, legacyMigrationState).

Android Auto Backup: include `qintar.db` and the DataStore file; exclude `quran.db`, audio cache and downloads.

### 7.6 Domain services

Pure, in `:domain`, each with its own test class:

```kotlin
interface ReadingTracker {                       // the only writer of the log
    /** +1 for one ayah, on today. */
    suspend fun mark(ayah: AyahId, context: ReadContext, method: ReadMethod): ReadEvent
    /** Make the count of (ayah, context, day) equal to [count]: adds events, or deletes the newest. */
    suspend fun setCount(ayah: AyahId, context: ReadContext, day: LocalDate, count: Int): Batch?
    /** FILL: one reading for ayahs that have none in scope. ADD: one more reading for every ayah. */
    suspend fun markRange(range: AyahRange, context: ReadContext, method: ReadMethod,
                          mode: RangeMode, times: Int = 1, day: LocalDate? = null): Batch
    /** In a khatm: is a tap on [ayah] out of sequence, and what would filling the gap cover? (§4.2.1) */
    suspend fun khatmGap(khatmId: Long, ayah: AyahId): AyahRange?
    suspend fun undo(batch: Batch)
    suspend fun delete(eventId: Long)
    suspend fun addTally(day: LocalDate, count: Int, note: String?): Tally
    suspend fun deleteTally(id: Long)
}
enum class RangeMode { FILL, ADD }
```

For a khatm tap the view model calls `khatmGap` first; on a non-null result it shows the dialog and then calls `markRange(FILL)` or `mark`.

Others: `DayClock` (§4.3) · `DayTotals` (the only counter of events) · `RankCalculator` · `StreakCalculator` · `KhatmProgress` (coverage, resume, first unread, per-day pages, pace) · `Availability` (effective schedule, available now / later / earlier, bookmark and group progress, the `once` roll-over) · `RevisitRanker` · `BulkRangeResolver` ("mark up to here" start) · `AyahTextFormatter` (pause-mark breaks, ayah-end marker) · `GoToParser` (`2:255`, `p300`, `j12`) · `BackupCodec` · `LegacyV1Converter`.

All writes that touch more than one row run in one database transaction.

### 7.7 Reader implementation notes

- The list is built from a **row model computed from `QuranIndex` alone** (no text): for a list of segments, the sequence of `BookmarkHeader`, `SurahHeader`, `Basmala`, `Ayah(id, context)`, `PageDivider(page)` rows is deterministic and cheap, so the list knows its full size and any ayah's index immediately. That makes "jump to page 300" and resume instant.
- Ayah text is loaded per page into an LRU cache (about 30 pages) as rows become visible, with neighbours prefetched. A row whose text has not arrived shows a shimmer of the right approximate height.
- Read state for the visible window is one observed query (§7.5) keyed by context and today; it re-keys through `todayFlow` at the day boundary (#10).
- Stable keys (`ayahId`, `"page-200"`), `contentType` per row type, formatted `AnnotatedString`s remembered per (text, font size, break setting).
- Target: no dropped frames while flinging through al-Baqarah on a mid-range device; Macrobenchmark or a JankStats check in CI is P1.

### 7.8 Audio (F5, F6)

- One `PlaybackService : MediaSessionService` owning one ExoPlayer. UI talks to it through a `MediaController` wrapped in `PlayerController` (exposes `StateFlow<PlayerState>`: current ayah, playing, speed, queue bounds, error).
- **Queue:** one `MediaItem` per ayah, `mediaId = "ayah:<id>"`, built lazily for the current scope from the chosen start ayah. Before ayah 1 of each surah that `has_basmala`, insert a basmala item (`mediaId = "basmala:<surah>"`, the audio of 1:1) — never counted. Playback continues to the end of the scope: the bookmark's end, or the end of the Quran for khatm and browse.
- **Counting (R-TRK-7):** in the service, on `onMediaItemTransition` with reason *auto* (the previous item played to its end), and on the final item reaching `STATE_ENDED`, call `ReadingTracker.mark(ayah, context, AUDIO)` for the finished ayah item, if the setting is on. Skipping forward, seeking past the end, or stopping does not count. The queue carries its `ReadContext` in the session extras. Because this lives in the service, it works with the screen off, and because `mark` asks `DayClock` at that moment, playback across midnight lands on the right days.
- Audio focus, becoming-noisy (headphones unplugged), notification and lock-screen controls come from Media3 configuration; do not hand-roll them.
- **Source:** behind an interface `AudioSource { fun url(reciter, ayahId, quality): Uri }`. Default implementation: the Islamic Network CDN v1 used, `https://cdn.islamic.network/quran/audio/{bitrate}/{edition}/{ayahId}.mp3`, default reciter `ar.alafasy` at 64 kbps. The reciter list is a bundled, curated JSON (id, names in three languages, available bitrates), not a runtime API call. **Verify** each bundled reciter's URLs and the CDN's usage terms with a script in `tools/` at implementation time, and whether ayah-1 files include the basmala for each reciter (adjust the basmala-insertion rule per reciter with a flag in the JSON if they differ).
- **Caching (R-AUD-3):** stream through a `CacheDataSource` with an LRU cache (default 500 MB, user-adjustable). "Download" for a bookmark, surah or juz uses Media3's download manager into a non-evictable store, honouring "Wi-Fi only". Scheduled bookmarks' audio downloads automatically when the setting allows.
- Offline with no cached audio: the play button shows a clear, localized "Audio needs a connection" message; everything else is unaffected.

### 7.9 Reminders (F11)

`AlarmManager` with an **inexact** allow-while-idle alarm for the next occurrence, rescheduled after it fires, on boot (`RECEIVE_BOOT_COMPLETED`), on time or zone change, and when settings change. The receiver reads today's total, decides the text (§5.11), and posts or skips. Request `POST_NOTIFICATIONS` only when the user turns a reminder on. **Do not request `SCHEDULE_EXACT_ALARM` or `USE_EXACT_ALARM`**; a reading reminder does not need minute precision.

Manifest permissions for v2: `INTERNET`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK` (Media3).

### 7.10 Backup (R-DAT-1)

One JSON file, `qintar-backup-YYYYMMDD.json`, through the system file picker (no storage permission).

```json
{
  "format": "qintar-backup",
  "version": 2,
  "exportedAt": "2026-11-15T20:10:00Z",
  "app": { "versionName": "2.0.0", "versionCode": 200 },
  "prefs": { "...": "user-facing preferences only" },
  "khatms": [ { "id": 1, "name": "…", "startedDay": "2026-10-01", "dailyPages": 4.0,
               "assumedCoveredUpTo": 0, "completedAt": null } ],
  "groups": [ { "id": 2, "name": "Friday", "description": null,
                "schedule": { "days": ["FRIDAY"], "window": null }, "sortIndex": 1 } ],
  "bookmarks": [ { "id": 7, "start": 2158, "end": 2267, "kind": "SURAH", "label": null, "description": "…",
                   "repeatTarget": 1, "groupId": 2, "schedule": null, "sortIndex": 3 } ],
  "events": {
    "columns": ["ayahId", "day", "readAt", "contextType", "contextId", "method", "batchId"],
    "rows": [ [2158, "2026-11-13", 1794686400000, "BOOKMARK", 7, "TAP", null] ]
  },
  "tallies": [ { "day": "2026-11-14", "count": 40, "note": "mosque" } ]
}
```

- Events use the columnar form to keep large histories compact.
- **Import:** parse → validate (ayah ids in range, days not in the future, references resolvable) → show a summary (date range, events, bookmarks, khatms) → user chooses **Replace everything** or **Merge** → apply in one transaction. Merge re-maps khatm, group and bookmark ids, and skips events that are identical in (ayahId, readAt, contextType, method). A failed import changes nothing.
- A file with `version` greater than the app understands is refused with a clear message. Unknown fields are ignored.
- A file with `"version": "1.0"` and v1's shape is routed through `LegacyV1Converter` (§7.11).

### 7.11 Carrying over v1 data (R-DAT-2)

Two entry points, one converter.

1. **In-place upgrade.** On first v2 launch, if the file `databases/quran_bookmarks_database` exists (Room version 3), open it read-only with plain SQLite, convert, write into `qintar.db` in one transaction, record success in prefs, then rename the old file with a `.v1` suffix (delete it two releases later). Also delete v1's `quran.db` and its cached image/audio files. Read v1's SharedPreferences for reciter, bitrate, reminder enabled/time, theme, playback speed and language.
2. **v1 backup file import.**

Conversion rules:

| v1 | v2 |
|---|---|
| `bookmarks` row, type `AYAH` | Bookmark kind `AYAH`, single-ayah range |
| type `RANGE` | kind `RANGE`, `startSurah:startAyah .. endSurah:endAyah` |
| type `SURAH` | kind `SURAH`, whole surah `startSurah` |
| type `PAGE` | kind `PAGE`; v1 keeps the page number in `startAyah` |
| `description`, timestamps | `description`, timestamps. No schedule, target 1. `sortIndex` by `createdAt`. |
| `tags` | Each distinct first tag becomes a BookmarkGroup of that name; the bookmark joins the group of its first tag. Further tags are appended to the description as a line "Tags: …" so nothing is lost. |
| `reading_activity` row, each id `"b:s:a"` in `trackedAyahIds` | one ReadEvent: ayah `s:a`, `day = date`, `readAt` = noon of that date in the device zone, method `IMPORT`; context `Bookmark(new id)` if `b` maps to an imported bookmark, otherwise `Browse` (v1 used `-1` for both khatm and random, so they cannot be told apart) |
| `totalAyahsRead` greater than the number of ids | the difference becomes a Tally on that day, note "imported" |
| `khatm_progress.lastPageRead = p` (if > 1) | one Khatm, `assumedCoveredUpTo` = last ayah of page `p − 1`, `startedDay` = the earliest imported day (or today) |
| `badgeLevel`, `cached_content` | ignored (derived / obsolete) |

Malformed ids are skipped and counted; the count is logged and shown in the "carried over" card. Check in a real v1 database and a real v1 backup file as test fixtures (the owner can export one from their phone; until then, generate one by running v1's code paths in a test).

---

## 8. Quality

### 8.1 Test strategy

| Layer | What | How |
|---|---|---|
| `:domain` | Every rule in §4 | Plain JUnit, fake clock, table-driven cases. This is where most tests live. |
| `:data` | DAOs and queries, migrations, backup round-trip, v1 conversion, Quran data validation | Robolectric + in-memory Room; `MigrationTestHelper`; fixtures |
| View models | State for each input; day roll-over; error mapping | Fakes (not mocks) of repositories and tracker; Turbine |
| UI | Flows of §8.2 | Compose UI tests with Hilt test modules replacing `DayClock`, audio source and dispatchers |
| Visual | Every screen and design-system component × light/dark × en/fa | Screenshot tests; diffs reviewed in PR |
| Static | Formatting, detekt, Android lint, the forbidden-API rules (`LocalDate.now()`, hard-coded colours, `fallbackToDestructiveMigration`) | CI |

Write the test with the code, in the same change. A milestone is not done until its tests are green in CI.

Cases that must exist by name (they encode past bugs):

- `marking after midnight in an open reader counts on the new day and the check reflects it` (#10)
- `streak is not zero in the morning after a qualifying yesterday` (F10)
- `khatm marks from yesterday are still shown today` (#11)
- `reading the same ayah in a bookmark after reading it in a khatm adds one and removes none` (§4.2.1)
- `basmala is never counted, by tap or by audio`
- `audio completion with the UI destroyed still records the event` (F6)
- `deleting a bookmark keeps the day totals` (§4.2)
- `week strip and calendar honour the week-start setting in RTL` (#3)
- `migration from every previous schema version preserves totals` (F9)
- `every one of the 6236 ayahs can be bookmarked, loaded, formatted and queued` (feedback: At-Taghabun 13, Al-Ma‘arij 25)
- `a khatm tick that skips unread ayahs writes nothing until the user chooses` (R-TRK-9)
- `tapping a read ayah adds one and never removes; set count to n leaves exactly n events for today in that context` (R-TRK-2)
- `a bookmark with repeat target 100 is done only when every ayah has 100 readings today` (§4.7)
- `a 22:00–02:00 window is available at 01:00 on the following calendar day and not at 03:00` (§4.7)
- `a bookmark without its own schedule follows its group's; with its own, ignores the group's` (§4.7)
- `adding a reading to a past day changes that day's rank and the streak` (R-TRK-6)
- `no screen shows an untranslated string in fa or ar` (a test walks every string resource and fails on a key missing from a locale)

### 8.2 End-to-end scenarios (#18, adapted to v2)

Instrumented, with the clock fixed by a test `DayClock`:

1. **Bookmarks.** Note Today's count → create a bookmark (the editor shows its ayahs as text) → open it and tap each ayah → Today's count rose by the ayah count and the bookmark's card changed colour → tap one ayah again → count rose by one and the row shows ×2 → Set count 0 on that ayah → count fell by two → Log a full reading → count rose by the ayah count → edit the bookmark's label → count unchanged → delete the bookmark → count unchanged.
1. **Groups and Now.** With the clock at Friday 09:00: a group "Morning" (daily, 08:00–12:00) with two bookmarks and an ungrouped Friday bookmark → Now lists all three under the right headers → Read on the group opens both bookmarks in one reader and counts each under its own bookmark → move the clock to 13:00 → the group is under "Earlier today" → move to Saturday 09:00 → the Friday bookmark is not listed.
1. **Repeat target and rank effect.** A single-ayah bookmark with target 100 → Set count 100 → it is done; Today rose by 100 → the reader's pill showed the rank-reached state for Qanit.
2. **Khatm.** Note the count → start a khatm → jump to page 200 → mark a few ayahs → scroll back 3 pages, forward 10 → leave → Continue khatm → reader opens at page 200, after the last marked ayah → the marked ayahs are checked → advance the test clock one day → reopen → they are still checked, and the new day's count is 0.
3. **Midnight.** Open a reader at 23:59 → mark an ayah → advance the clock to 00:01 without leaving → mark another → yesterday has 1, today has 1, and the second check is shown.
4. **Manual log and past days.** Log pages 1–2 for yesterday, counted toward the khatm → yesterday's total rose by the right number → khatm coverage rose → Undo restores both. Open the day before yesterday from the calendar → add a bookmark's full reading → that day's total, its rank and the streak update.
5. **Import / export.** Import a prepared v2 file → Insights shows the expected totals, streak and calendar marks for the fixed "today" → read a random ayah and a random page → counts and statistics update → export → the exported file re-imports into a clean install to the same totals.
6. **v1 upgrade.** Start from a v1 database fixture → launch → bookmarks and day totals match the fixture; the carried-over card appears once.
7. **Audio.** With a fake audio source serving short local clips: play from an ayah → after two clips, two events with method `AUDIO` exist and no event exists for the basmala.

### 8.3 Definition of done (every change)

- Requirement IDs it serves are named in the PR.
- Unit tests for new logic; a screenshot for new or changed UI in light, dark and Persian.
- Strings added in all three languages (English first; mark machine-drafted `fa`/`ar` strings for owner review in the PR description).
- No new lint, detekt or contrast-test failures; no hard-coded colours, sizes or strings.
- Schema change → migration + migration test + committed schema JSON.
- `versionCode` increased.

### 8.4 CI/CD (#19, #20)

GitHub Actions.

**`pr.yml`** — on pull requests to `main`, all required:

1. Static checks: formatting, detekt, Android lint.
2. Unit tests for all modules, including Quran data validation and contrast tests.
3. Screenshot verification.
4. `assembleDebug` and `assembleRelease` (R8 on).
5. Instrumented tests on an emulator (the scenarios of §8.2).
6. Version check: `versionCode` is greater than on `main`.
7. Upgrade test: install the APK of the latest GitHub release → load a prepared dump through a debug-only import intent → install the PR's APK over it → run a smoke test asserting totals, bookmarks and khatm state survived.
8. Quran database reproducibility (§7.4).

**`release.yml`** — on push to `main`: build the release APK, sign it with the keystore held in repository secrets, tag `v<versionName>`, create a GitHub release with the APK and the R8 mapping file attached, and generated notes.

Until the first v2 release exists, step 7 uses the last v1 APK and scenario 6's fixture.

*Owner action:* create the signing keystore and add it and its passwords as repository secrets. If v1 APKs were distributed signed with a particular key, v2 must use the same key or it cannot install as an update (Q6).

### 8.5 Budgets

Cold start to an interactive Today under 1 second on a mid-range device · a mark tap reflected in the row and the today pill within one frame of the write completing, under 100ms in total · APK under 15 MB (text ≈ 2 MB, two fonts ≈ 1 MB) · no network request at startup, ever.

---

## 9. Delivery plan

Work on a `v2` branch. Tag the current `main` as `v1-final` first. Replace `app/` rather than editing it. Each milestone is one or more PRs and leaves `v2` releasable.

| # | Milestone | Contents | Exit criteria |
|---|---|---|---|
| M0 | Foundation | Three modules, version catalog, Hilt, CI `pr.yml` (static, unit, build), design-system tokens/theme/fonts, contrast and letter tests, navigation shell with four empty tabs, new `CLAUDE.md` (§10) | CI green; shell runs in en/fa, light/dark |
| M1 | Quran data and reader | `tools/quran-data`, `quran.db`, validation tests, `QuranIndex`, Read tab index, Go to, reader (Browse, read-only), formatting (§5.4.2), Aa panel, keep-screen-on, position bar | Owner approves the rendering screenshot (§7.4); smooth scroll through the whole Quran |
| M2 | Tracking core | `qintar.db`, `DayClock`, `ReadingTracker`, ranks, streaks; reader counting (+1, set count), bulk marking, undo, today pill with rank-reached effect; Today screen (ring, ladder, week strip); manual log; Day detail | Scenarios 3 and 4 pass |
| M3 | Khatm | Khatm entity and rules, several at once, daily goal and the four progress bars, new/continue/detail/completion, Today card, per-khatm statistics | Scenario 2 passes |
| M4 | Bookmarks | CRUD with ayah preview, description, repeat target, quick star; groups; schedules and time windows; Now / Groups / All; group reading; full-reading and set-count actions; done colours | The Bookmarks, Groups and Repeat scenarios pass |
| M5 | Insights | Overview charts and tiles, calendar (week start, calendar systems), Revisit, source split | Screenshots approved in three languages |
| M6 | Audio | Playback service, queue, counting by listening, mini player, reciters, cache, downloads | Scenario 7 passes; background playback counts |
| M7 | Data safety | Export/import, v1 in-place migration and v1 file import, reminders, onboarding, settings complete | Scenarios 5 and 6 pass |
| M8 | Release | Instrumented tests and upgrade test in CI, `release.yml`, accessibility pass, translation review, performance checks, README rewritten for v2, close superseded issues | Signed 2.0.0 on GitHub releases |

P1 items ride with their milestone if cheap, otherwise queue for 2.1. P2 items are out.

---

## 10. Working rules for the implementing model

Replace the current `CLAUDE.md` with the following at M0 (the current file describes v1, refers to a tool that may not exist in your harness, and hard-codes a macOS Java path).

```markdown
# Qintar — instructions for AI contributors

Qintar is an Android app that counts the Quran ayahs a user reads per day and ranks the day on a
seven-step ladder from a hadith. The design is specified in docs/v2/DESIGN.md. Read it before any
non-trivial change; it is the source of truth and its "Decision" sections are not to be reopened
without the owner.

## Invariants (breaking one of these is a bug, whatever the task)
- All counts derive from `read_event` and `read_tally`. Never store a derived count, rank or streak.
- Only `ReadingTracker` writes reading records.
- Time comes from `DayClock`. Never call `LocalDate.now()`, `Instant.now()` or
  `System.currentTimeMillis()` elsewhere. Never hold a "today" value across a suspension without
  observing `todayFlow`.
- No destructive database fallback. Every schema change has a migration and a migration test.
- Quran text is never modified. Formatting happens at display time. `quran.db` is generated by
  tools/quran-data and must be reproducible.
- `:domain` has no Android imports.
- UI uses design-system tokens and components only: no literal colours, sizes, fonts or strings.
- Every user-visible string exists in en, fa and ar. Arabic uses ي ك; Persian uses ی ک.
- Layouts are start/end based; nothing assumes left-to-right.

## Workflow
1. Find the requirement IDs (R-…) the task serves; if none fits, ask the owner.
2. Write or update tests in the same change (docs/v2/DESIGN.md §8).
3. Run: ./gradlew detekt lint test   — and the screenshot task if UI changed.
4. Build: ./gradlew assembleDebug
5. Bump versionCode. Summarise what changed, what was verified, and what was not.
Redirect long Gradle output to a file under build/ and read the tail; do not paste it whole.

## When unsure
Prefer the simpler mechanism already in the codebase over a new one. If a rule in the design
document seems wrong for a case it did not foresee, stop and ask rather than working around it.
```

Guidance on which model for which work: M1's data pipeline, M2's tracking core, M6's playback service and M7's migration are where subtle mistakes cost the most — give those to the strongest model available and have it write the tests first. Screens built from finished design-system components, string translation drafts, and test scaffolding are suitable for a smaller model, provided it is handed the relevant sections of this document rather than a summary.

---

## 11. Open questions for the owner

Each has a default that will be implemented unless changed.

| # | Question | Default taken |
|---|---|---|
| Q1 | Issue #6 has no description. The finding is that v1's text uses the Farsi yeh everywhere and is meant for a font v1 never shipped, and that Arabic UI strings are typed with Persian letters. Is that the problem you saw? | Yes: fixed by Tanzil text + Amiri Quran font + the letter test. You confirm on the M1 rendering screenshot. |
| Q2 | You said the hadith text can wait for version 3. Taken to mean: 2.0 explains the ladder in its own words and has no hadith screen; narrations live in bookmark descriptions; the hadith feed is v3. Right? Also, are the English rank names in §4.4 acceptable? | Yes, as described in §4.4. |
| Q3 | The hadith says "in a night". Should the reading day start at midnight, or later (for example 03:00)? | Midnight, with a "Day starts at" setting. |
| Q4 | *Resolved by the group and schedule design (§4.7):* read items leave the "available now" list for the day; "once" stops recurring after one done day. | — |
| Q5 | Show sajda type (obligatory / recommended) from Tanzil's metadata, or only "Sajdah"? | Show the type. |
| Q6 | Were v1 APKs given to anyone, signed with a key you still have? | Assume yes: keep the applicationId and build in-place migration. |
| Q7 | *Resolved:* bookmark groups are in (§4.7). Open detail: a bookmark belongs to one group only, with "Duplicate to another group" for the rest. Acceptable? | One group per bookmark. |
| Q8 | *Resolved:* every reading always counts; no count-once setting; +1 by tap, "Set count" to edit. | — |
| Q11 | *Resolved:* shared khatm is deferred. | — |
| Q13 | When a group's time window has passed and its bookmarks are unread, they move to "Earlier today" and stay readable. Should a reminder be possible per group ("Morning readings are available")? | No per-group reminders in 2.0. |
| Q14 | "Fully offline" is read as: no server of our own and no content feed. Audio still streams from the public CDN when the user presses play, and is cached. Is that acceptable, or should audio be download-only? | Streaming with cache. |
| Q12 | In a khatm, when a tick would skip unread ayahs, should "Only this ayah" be offered next to "Mark all up to here", or should gaps be impossible? | Offer both. |
| Q9 | Dropping the nine-colour theme picker in favour of one palette. Agreed? | Yes. |
| Q10 | Vazirmatn for all UI text including English, or Inter for Latin? | Vazirmatn everywhere. |

---

## Appendix A — Sources checked on 2026-11-15

- v1 repository at commit `bfec8c8`; GitHub issues #1, #3–#21; PR #22.
- alquran.cloud API: the default text edition is `quran-uthmani-quran-academy`, "Modified Quran Uthmani Text from the Quran Academy to work with the Kitab font".
- Tanzil text licence (tanzil.net/docs/text_license): Creative Commons Attribution 3.0; verbatim copies; attribution and link required; modification not allowed.
- Tanzil metadata (tanzil.net/docs/quran_metadata): surahs, juz, hizb quarters, manzils, rukus, Madinah-mushaf pages, sajda locations and types; `quran-data.xml`.
- Amiri project (github.com/aliftype/amiri): SIL Open Font License 1.1.
- KFGQPC Uthmanic Script Hafs font: licence text states the font may not be reproduced or modified without written approval — not used.

Not checked, to verify at implementation: that the Amiri Quran cut covers every code point of the chosen Tanzil text (automated by the glyph test in §7.4); Vazirmatn's current licence file; Islamic Network CDN terms and per-reciter file conventions; current stable versions and setup of every library in §7.1.
