#!/usr/bin/env python3
"""Download the pinned Tanzil source files into ./source/ and check them.

Standard library only. Run with `python3 -I tools/quran-data/fetch_sources.py`.

The files are checked in, so this script is only needed to re-fetch them (for
example to see whether Tanzil has published a new release). It refuses to
overwrite a checked-in file whose SHA-256 differs from SHA256SUMS unless
--update is given; with --update it rewrites SHA256SUMS and you must then
review the diff of the source files and of the generated database.
"""
import argparse
import hashlib
import os
import sys
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
SOURCE_DIR = os.path.join(HERE, "source")
SUMS = os.path.join(SOURCE_DIR, "SHA256SUMS")

# The request made by the form on https://tanzil.net/download/ with:
#   Quran text type: Uthmani; Output: XML;
#   [x] pause marks  [x] sajdah signs  [ ] rub-el-hizb signs
#   [x] tatweel below superscript alefs (form default)  [ ] sequential tanweens
#   [x] I agree with the Terms of Use
# Unchecked boxes are not sent by an HTML form, so they are absent here.
TEXT_URL = (
    "https://tanzil.net/pub/download/index.php"
    "?marks=true&sajdah=true&tatweel=true&quranType=uthmani&outType=xml&agree=true"
)
# Linked from https://tanzil.net/docs/quran_metadata
METADATA_URL = "https://tanzil.net/res/text/metadata/quran-data.xml"

FILES = {
    "quran-uthmani.xml": TEXT_URL,
    "quran-data.xml": METADATA_URL,
}


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def read_sums() -> dict:
    sums = {}
    if os.path.exists(SUMS):
        with open(SUMS, encoding="ascii") as f:
            for line in f:
                line = line.strip()
                if line:
                    digest, name = line.split(None, 1)
                    sums[name.lstrip("*")] = digest
    return sums


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--update", action="store_true",
                        help="accept new upstream content and rewrite SHA256SUMS")
    args = parser.parse_args()

    os.makedirs(SOURCE_DIR, exist_ok=True)
    pinned = read_sums()
    status = 0
    for name, url in FILES.items():
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=60) as resp:
            data = resp.read()
        digest = sha256(data)
        if name in pinned and pinned[name] != digest and not args.update:
            print(f"{name}: upstream differs from pinned SHA-256 "
                  f"({digest} != {pinned[name]}); not overwritten", file=sys.stderr)
            status = 1
            continue
        with open(os.path.join(SOURCE_DIR, name), "wb") as f:
            f.write(data)
        pinned[name] = digest
        print(f"{name}: {len(data)} bytes, sha256 {digest}")

    if args.update or not os.path.exists(SUMS):
        write_sums(pinned)
    return status


def write_sums(sums: dict) -> None:
    with open(SUMS, "w", encoding="ascii", newline="\n") as f:
        for name in sorted(sums):
            f.write(f"{sums[name]}  {name}\n")


if __name__ == "__main__":
    sys.exit(main())
