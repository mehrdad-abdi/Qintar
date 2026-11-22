#!/usr/bin/env python3
"""Build data/src/main/assets/quran.db from the pinned Tanzil sources.

Standard library only. Run from anywhere with:

    python3 -I tools/quran-data/build_quran_db.py

Inputs (checked in, verified against source/SHA256SUMS before use):
    source/quran-uthmani.xml   Tanzil Uthmani text, XML, pause marks + sajdah signs
    source/quran-data.xml      Tanzil metadata (surahs, juz, quarters, pages, sajdas)

Outputs:
    data/src/main/assets/quran.db       the read-only Quran database (DESIGN.md section 7.4)
    tools/quran-data/quran.db.sha256    SHA-256 of quran.db (checked by a :data test)
    tools/quran-data/allowed_codepoints.txt   every code point used in ayah texts
    tools/quran-data/quran-structure.csv      id,surah,number,page,juz,hizb_quarter,sajda

The output is deterministic: the same inputs give a byte-identical quran.db.
Pass --check to rebuild into a temporary directory and fail if any committed
output differs (what CI runs).
"""
import argparse
import bisect
import hashlib
import os
import sqlite3
import sys
import tempfile
import unicodedata
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))
SOURCE_DIR = os.path.join(HERE, "source")
TEXT_XML = os.path.join(SOURCE_DIR, "quran-uthmani.xml")
META_XML = os.path.join(SOURCE_DIR, "quran-data.xml")
SUMS = os.path.join(SOURCE_DIR, "SHA256SUMS")

DB_OUT = os.path.join(REPO, "data", "src", "main", "assets", "quran.db")
SHA_OUT = os.path.join(HERE, "quran.db.sha256")
CODEPOINTS_OUT = os.path.join(HERE, "allowed_codepoints.txt")
CSV_OUT = os.path.join(HERE, "quran-structure.csv")

# Must equal QuranDatabase's Room version. Room compares PRAGMA user_version of the
# copied asset with its own version; a mismatch makes it treat the asset as outdated.
ROOM_VERSION = 1
PAGE_SIZE = 4096

SURAH_PREFIX = "سورة"
SAJDA_TYPES = {"recommended": 1, "obligatory": 2}
SAJDA_SIGN = "۩"

# Exactly the schema of DESIGN.md section 7.4, with two adjustments Room requires:
# primary keys are declared NOT NULL (Room models them as non-null Int and compares
# PRAGMA table_info.notnull). Semantics are unchanged: an INTEGER PRIMARY KEY column is
# still the rowid alias. The Tanzil copyright notice is kept inside the CREATE TABLE ayah
# statement as SQL comments: SQLite stores that text verbatim in sqlite_master, so the
# notice travels with the file as the Tanzil terms require, without an extra table.
SURAH_DDL = """CREATE TABLE surah (
  number        INTEGER NOT NULL PRIMARY KEY,
  name_ar       TEXT NOT NULL,
  name_latin    TEXT NOT NULL,
  name_en       TEXT NOT NULL,
  revelation    TEXT NOT NULL,
  ayah_count    INTEGER NOT NULL,
  first_ayah_id INTEGER NOT NULL,
  has_basmala   INTEGER NOT NULL
)"""

AYAH_DDL_TEMPLATE = """CREATE TABLE ayah (
{notice}
  id           INTEGER NOT NULL PRIMARY KEY,
  surah        INTEGER NOT NULL,
  number       INTEGER NOT NULL,
  text         TEXT NOT NULL,
  page         INTEGER NOT NULL,
  juz          INTEGER NOT NULL,
  hizb_quarter INTEGER NOT NULL,
  sajda        INTEGER NOT NULL
)"""

INDEX_DDL = [
    "CREATE INDEX ayah_page ON ayah(page)",
    "CREATE UNIQUE INDEX ayah_ref ON ayah(surah, number)",
]


class SourceError(Exception):
    pass


def sha256_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 16), b""):
            h.update(chunk)
    return h.hexdigest()


def verify_sources():
    with open(SUMS, encoding="ascii") as f:
        for line in f:
            if not line.strip():
                continue
            digest, name = line.split(None, 1)
            name = name.strip().lstrip("*")
            actual = sha256_file(os.path.join(SOURCE_DIR, name))
            if actual != digest:
                raise SourceError(f"{name}: sha256 {actual} does not match SHA256SUMS {digest}")


def tanzil_notice(text_xml_path):
    """Return the copyright comment block of the Tanzil text file, verbatim."""
    with open(text_xml_path, encoding="utf-8") as f:
        raw = f.read()
    start = raw.index("<!--") + len("<!--")
    end = raw.index("-->", start)
    block = raw[start:end].strip("\r\n\t ")
    if "Tanzil" not in block or "CHANGING IT IS NOT ALLOWED" not in block:
        raise SourceError("Tanzil copyright block not found in text file")
    return block


def notice_as_sql_comment(block):
    lines = []
    for line in block.splitlines():
        line = line.rstrip()
        lines.append("  -- " + line if line else "  --")
    return "\n".join(lines)


def load_text(path):
    """[(surah, number, text, basmala_or_None)] in Mushaf order."""
    root = ET.parse(path).getroot()
    rows = []
    for expected_surah, sura in enumerate(root.findall("sura"), start=1):
        s = int(sura.get("index"))
        if s != expected_surah:
            raise SourceError(f"surah {s} out of order")
        for expected_ayah, aya in enumerate(sura.findall("aya"), start=1):
            n = int(aya.get("index"))
            if n != expected_ayah:
                raise SourceError(f"ayah {s}:{n} out of order")
            rows.append((s, n, aya.get("text"), aya.get("bismillah")))
    return rows


def load_metadata(path):
    root = ET.parse(path).getroot()

    def boundaries(container, tag):
        items = root.find(container).findall(tag)
        for i, el in enumerate(items, start=1):
            if int(el.get("index")) != i:
                raise SourceError(f"{tag} {el.get('index')} out of order")
        return [(int(el.get("sura")), int(el.get("aya"))) for el in items]

    surahs = []
    for i, el in enumerate(root.find("suras").findall("sura"), start=1):
        if int(el.get("index")) != i:
            raise SourceError(f"metadata surah {el.get('index')} out of order")
        surahs.append({
            "number": i,
            "ayas": int(el.get("ayas")),
            "start": int(el.get("start")),
            "name": el.get("name"),
            "tname": el.get("tname"),
            "ename": el.get("ename"),
            "type": el.get("type"),
        })
    sajdas = {}
    for el in root.find("sajdas").findall("sajda"):
        sajdas[(int(el.get("sura")), int(el.get("aya")))] = SAJDA_TYPES[el.get("type")]
    return {
        "surahs": surahs,
        "juz": boundaries("juzs", "juz"),
        "quarters": boundaries("hizbs", "quarter"),
        "pages": boundaries("pages", "page"),
        "sajdas": sajdas,
    }


def build_rows(text_rows, meta):
    ref_to_id = {(s, n): i for i, (s, n, _, _) in enumerate(text_rows, start=1)}

    def starts(bounds):
        ids = [ref_to_id[b] for b in bounds]
        if ids[0] != 1 or ids != sorted(ids) or len(set(ids)) != len(ids):
            raise SourceError("partition boundaries are not strictly increasing from ayah 1")
        return ids

    juz_starts = starts(meta["juz"])
    quarter_starts = starts(meta["quarters"])
    page_starts = starts(meta["pages"])

    def part_of(starts_, ayah_id):
        return bisect.bisect_right(starts_, ayah_id)

    surah_rows = []
    for m in meta["surahs"]:
        n = m["number"]
        ayahs = [r for r in text_rows if r[0] == n]
        if len(ayahs) != m["ayas"]:
            raise SourceError(f"surah {n}: text has {len(ayahs)} ayahs, metadata {m['ayas']}")
        first_id = ref_to_id[(n, 1)]
        if first_id != m["start"] + 1:
            raise SourceError(f"surah {n}: first ayah id {first_id}, metadata start {m['start']}")
        has_basmala = 1 if ayahs[0][3] else 0
        name_ar = m["name"]
        if name_ar.startswith(SURAH_PREFIX):
            name_ar = name_ar[len(SURAH_PREFIX):].strip()
        revelation = {"Meccan": "MECCAN", "Medinan": "MEDINAN"}[m["type"]]
        surah_rows.append((n, name_ar, m["tname"], m["ename"], revelation,
                           m["ayas"], first_id, has_basmala))

    ayah_rows = []
    for ayah_id, (s, n, text, basmala) in enumerate(text_rows, start=1):
        if basmala is not None and n != 1:
            raise SourceError(f"{s}:{n}: basmala attribute on an ayah other than the first")
        sajda = meta["sajdas"].get((s, n), 0)
        if (sajda != 0) != (SAJDA_SIGN in text):
            raise SourceError(f"{s}:{n}: sajda metadata and U+06E9 in the text disagree")
        ayah_rows.append((ayah_id, s, n, text,
                          part_of(page_starts, ayah_id),
                          part_of(juz_starts, ayah_id),
                          part_of(quarter_starts, ayah_id),
                          sajda))
    return surah_rows, ayah_rows


def write_db(path, notice, surah_rows, ayah_rows):
    if os.path.exists(path):
        os.remove(path)
    conn = sqlite3.connect(path, isolation_level=None)
    try:
        conn.execute(f"PRAGMA page_size = {PAGE_SIZE}")
        conn.execute("PRAGMA journal_mode = DELETE")
        conn.execute("PRAGMA encoding = 'UTF-8'")
        conn.execute(f"PRAGMA user_version = {ROOM_VERSION}")
        conn.execute("BEGIN")
        conn.execute(SURAH_DDL)
        conn.execute(AYAH_DDL_TEMPLATE.format(notice=notice_as_sql_comment(notice)))
        conn.executemany("INSERT INTO surah VALUES (?,?,?,?,?,?,?,?)", surah_rows)
        conn.executemany("INSERT INTO ayah VALUES (?,?,?,?,?,?,?,?)", ayah_rows)
        for ddl in INDEX_DDL:
            conn.execute(ddl)
        conn.execute("COMMIT")
        conn.execute("VACUUM")
    finally:
        conn.close()
    normalize_header(path)


def normalize_header(path):
    """Zero the 'version-valid-for' and 'SQLite version number' header fields
    (offsets 92..99). They record which SQLite library last wrote the file, which
    would otherwise make the output depend on the machine that built it."""
    with open(path, "r+b") as f:
        f.seek(92)
        f.write(b"\x00" * 8)


def codepoint_lines(ayah_rows):
    cps = sorted({ord(ch) for row in ayah_rows for ch in row[3]})
    return [f"U+{cp:04X} {unicodedata.name(chr(cp))}" for cp in cps]


def csv_lines(ayah_rows):
    lines = ["id,surah,number,page,juz,hizb_quarter,sajda"]
    for (i, s, n, _, page, juz, quarter, sajda) in ayah_rows:
        lines.append(f"{i},{s},{n},{page},{juz},{quarter},{sajda}")
    return lines


def write_text(path, lines):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")


def generate(db_path, sha_path, cp_path, csv_path):
    verify_sources()
    notice = tanzil_notice(TEXT_XML)
    text_rows = load_text(TEXT_XML)
    meta = load_metadata(META_XML)
    surah_rows, ayah_rows = build_rows(text_rows, meta)
    os.makedirs(os.path.dirname(db_path), exist_ok=True)
    write_db(db_path, notice, surah_rows, ayah_rows)
    write_text(sha_path, [f"{sha256_file(db_path)}  quran.db"])
    write_text(cp_path, codepoint_lines(ayah_rows))
    write_text(csv_path, csv_lines(ayah_rows))
    return surah_rows, ayah_rows


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true",
                        help="rebuild into a temp dir and fail if committed outputs differ")
    args = parser.parse_args()

    if not args.check:
        surahs, ayahs = generate(DB_OUT, SHA_OUT, CODEPOINTS_OUT, CSV_OUT)
        print(f"wrote {DB_OUT}: {len(surahs)} surahs, {len(ayahs)} ayahs, "
              f"sha256 {sha256_file(DB_OUT)} (SQLite {sqlite3.sqlite_version})")
        return 0

    with tempfile.TemporaryDirectory() as tmp:
        outs = {
            DB_OUT: os.path.join(tmp, "quran.db"),
            SHA_OUT: os.path.join(tmp, "quran.db.sha256"),
            CODEPOINTS_OUT: os.path.join(tmp, "allowed_codepoints.txt"),
            CSV_OUT: os.path.join(tmp, "quran-structure.csv"),
        }
        generate(*outs.values())
        differ = [committed for committed, fresh in outs.items()
                  if not os.path.exists(committed)
                  or sha256_file(committed) != sha256_file(fresh)]
    if differ:
        for path in differ:
            print(f"differs from a fresh build: {os.path.relpath(path, REPO)}", file=sys.stderr)
        return 1
    print("all generated files match a fresh build")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except SourceError as e:
        print(f"source error: {e}", file=sys.stderr)
        sys.exit(2)
