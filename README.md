# Qintar

Qintar is an Android app that keeps an honest daily count of the Quran ayahs you read and shows
which of seven ranks the day reached. Read in the app, listen to a recitation, or read from a paper
mushaf and log it: every ayah counts the same.

It works offline, has no account and no analytics, and nothing you read leaves your phone unless
you export a backup yourself. English, فارسی and العربية, with right-to-left layouts.

## The seven ranks

The ladder follows a narration about nightly recitation: whoever recites ten ayahs in a night is not
written among the heedless, and whoever recites a thousand is given a *qintar* of reward.

| Ayahs in a day | Rank |
|---:|---|
| 10 | Not among the heedless (غیر غافل) |
| 50 | Dhakir · the remembering (ذاکر) |
| 100 | Qanit · the devout (قانت) |
| 200 | Khashi‘ · the humble (خاشع) |
| 300 | Fa’iz · the victorious (فائز) |
| 500 | Mujtahid · the striver (مجتهد) |
| 1000 | Qintar (صاحب قنطار) |

Ranks, streaks and totals are always computed from the reading log; nothing derived is stored.

## Features

- **Today**: the day's count and rank, distance to the next rank or your daily goal, a 7-day streak
  strip, the khatm to continue, and the bookmarks scheduled for now.
- **Reader**: one continuous, vertically scrolling text in the Uthmani script (Amiri Quran font),
  per-ayah check that counts a reading (tap again for repeated readings), "Mark page", "Mark up to
  here", jump to surah / juz / page / ayah, adjustable text size, optional line breaks at pause
  marks, keep-screen-on, and an always-visible pill with today's count.
- **Khatms**: any number at once, started from the beginning or from any position, with an optional
  pages-per-day goal and projected finish, progress by surah, juz and the whole Quran, coverage per
  juz, and "first unread".
- **Bookmarks and groups**: an ayah, range, surah, page or juz, with a name and a description, a
  number of readings per day, and a schedule (days of the week, a time window, or once). Groups
  have their own schedule and can be read as one passage. Views: due now, by group, all.
- **Audio**: ayah-by-ayah recitation from nine reciters, played in the background with lock-screen
  and notification controls, repeat an ayah or a passage, speed control; listened ayahs can count
  as read. Audio is cached for offline use, and a passage can be downloaded ahead.
- **Insights**: totals, active days, averages, best day and streaks over 7 days, 30 days, a year
  or all time; a daily chart; days at each rank; a month calendar, and the detail of any day, where
  readings can be added or removed after the fact.
- **Log reading**: add a passage, surah, pages, juz or just a number of ayahs to today or any earlier day.
- **Reminders**: a daily reminder at a chosen time (skipped once the goal is reached) and a streak
  reminder if fewer than ten ayahs have been read that day.
- **Backup**: export everything to a JSON file and import it again (merge or replace) through the
  system file picker. Android Auto Backup keeps the reading log and settings as well.
- **Upgrading from Qintar 1**: bookmarks, tags (as groups), daily readings, the khatm position and
  settings are carried over on the first start; v1 backup files can be imported too.

## Data sources and licences

- **Quran text**: [Tanzil Quran Text](https://tanzil.net) (Uthmani, version 1.1), © 2007-2026
  Tanzil Project, licensed under
  [Creative Commons Attribution 3.0](https://creativecommons.org/licenses/by/3.0/). The text is
  shown exactly as published and is not modified. Structure (surahs, juz, hizb, pages, sajdas) is
  Tanzil's metadata. The database is built reproducibly from pinned sources by
  [`tools/quran-data`](tools/quran-data/README.md).
- **Fonts**: [Amiri Quran](https://github.com/aliftype/amiri) for the Quran text and
  [Vazirmatn](https://github.com/rastikerdar/vazirmatn) for the interface, both under the
  [SIL Open Font License 1.1](https://openfontlicense.org). The licence texts ship in the app
  (Settings → Licences).
- **Audio**: recitations are streamed from `cdn.islamic.network` (the Islamic Network CDN behind
  alquran.cloud) when you play them, and cached on the device. Nothing else is sent; requests carry
  no identifier, only a fixed user agent.

## Building

Requirements: JDK 17 and the Android SDK (compile SDK 36). The app runs on Android 8.0 (API 26) and later.

```bash
./gradlew :app:assembleDebug                      # debug APK, app id ...quranbookmarks.debug
./gradlew :app:assembleRelease                    # unsigned unless the signing variables are set
```

Signing and publishing are described in [`docs/v2/RELEASING.md`](docs/v2/RELEASING.md).

## Testing

```bash
./gradlew :domain:test :data:testDebugUnitTest :app:testDebugUnitTest   # all unit tests (JVM + Robolectric)
./gradlew :app:verifyRoborazziDebug                                     # screenshot comparison
./gradlew :app:lintDebug
python3 -I tools/quran-data/build_quran_db.py --check                   # the Quran database is reproducible
```

Screenshot references live in `app/src/test/screenshots/` (light and dark, English and Persian);
re-record them with `./gradlew :app:recordRoborazziDebug` after an intended UI change. Pull requests
run all of the above in GitHub Actions.

## Project layout

- `domain/` — pure Kotlin: rules for counting, ranks, streaks, khatms, schedules, backups.
- `data/` — Room databases, preferences, backup codec, the carry-over from version 1.
- `app/` — Jetpack Compose UI, playback, reminders.
- `tools/quran-data/` — the generator for the bundled Quran database.
- `docs/v2/DESIGN.md` — the product and technical design.
