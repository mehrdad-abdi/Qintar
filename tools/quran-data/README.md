# Quran data pipeline

Turns pinned Tanzil sources into the read-only database the app ships,
`data/src/main/assets/quran.db` (DESIGN.md §7.4). The app never downloads Quran text.

## Files

| File | What it is |
|---|---|
| `source/quran-uthmani.xml` | Tanzil Uthmani text 1.1, XML, pause marks and sajdah signs on, rub-el-hizb signs off. Copyright block intact. |
| `source/quran-data.xml` | Tanzil metadata 1.0: surahs, juz, hizb quarters, Madinah-mushaf pages, sajdas with type. |
| `source/SHA256SUMS` | Pinned hashes of the two source files; the generator refuses to run if they differ. |
| `source/LICENSE-tanzil.txt` | Terms for both files, as published by Tanzil. |
| `fetch_sources.py` | Re-downloads the sources (records the exact Tanzil request). |
| `build_quran_db.py` | The generator. Standard library only. |
| `quran.db.sha256` | SHA-256 of the committed `quran.db`; a `:data` test checks it. |
| `allowed_codepoints.txt` | Every code point used in the ayah texts; a `:data` test checks the set is equal. |
| `quran-structure.csv` | `id,surah,number,page,juz,hizb_quarter,sajda` for all 6,236 ayahs, a fixture for other modules' tests. |
| `check_font_coverage.py` | Checks a font's `cmap` covers `allowed_codepoints.txt` plus U+06DD and the Arabic-Indic digits. |

## Regenerate

```sh
python3 -I tools/quran-data/build_quran_db.py          # rewrite all outputs
python3 -I tools/quran-data/build_quran_db.py --check  # CI: fail if committed outputs differ from a fresh build
./gradlew :data:testDebugUnitTest                      # validation tests against the asset, through Room
```

The output is byte-identical for the same inputs: fixed page size (4096), rollback
journal, rows inserted in id order, `VACUUM`, no timestamps, and the two header fields
that record the writing SQLite version (offsets 92–99) zeroed.

`PRAGMA user_version` is set to `ROOM_VERSION` in the generator and must equal
`QuranDatabase.VERSION`. When the data changes, bump both: Room then replaces the old
on-device copy with the new asset (destructive fallback is fine here, there is no user data).

## Schema notes

The tables and indexes are those of DESIGN.md §7.4, with one change Room needs: the primary
keys are declared `INTEGER NOT NULL PRIMARY KEY` (still the rowid alias), because Room models
them as non-null and compares `notnull`. The Tanzil copyright notice is stored as SQL comments
inside the `CREATE TABLE ayah` statement, so it travels with the file as the terms require.
Room adds its own `room_master_table` on first open of the device copy.

## Updating the sources

```sh
python3 -I tools/quran-data/fetch_sources.py            # fails if upstream changed
python3 -I tools/quran-data/fetch_sources.py --update   # accept and rewrite SHA256SUMS
```

Then regenerate, read the diff of the source XML and of `allowed_codepoints.txt`, run the
tests, and bump the database version. Downloaded files are untrusted input: run Python that
reads them with `-I`, never execute anything from `source/`.

## Font check

```sh
python3 -I tools/quran-data/check_font_coverage.py path/to/AmiriQuran.ttf
```

Amiri Quran from Amiri 1.003 (`https://github.com/aliftype/amiri/releases/download/1.003/Amiri-1.003.zip`,
SIL OFL 1.1, `OFL.txt` in the zip) covers all 80 code points.

## Attribution

Quran text and metadata: Tanzil Project, https://tanzil.net — Creative Commons Attribution 3.0.
The text may be copied verbatim only; it must not be changed.
