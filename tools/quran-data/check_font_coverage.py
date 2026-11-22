#!/usr/bin/env python3
"""Check that a font maps every code point of allowed_codepoints.txt to a glyph.

Standard library only (reads the font's `cmap` table, formats 4 and 12).

    python3 -I tools/quran-data/check_font_coverage.py path/to/AmiriQuran.ttf

Besides the text's code points it also checks the ones the reader adds when
formatting (DESIGN.md section 5.4.2): U+06DD END OF AYAH and the Arabic-Indic digits.
Exit status 1 if anything is missing. A code point absent from `cmap` cannot be
rescued by shaping (GSUB works on glyphs), so `cmap` coverage is the criterion.
"""
import os
import struct
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ALLOWED = os.path.join(HERE, "allowed_codepoints.txt")
FORMATTING_EXTRAS = [0x06DD] + list(range(0x0660, 0x066A))


def read_cmap(path):
    with open(path, "rb") as f:
        data = f.read()
    num_tables = struct.unpack(">H", data[4:6])[0]
    cmap_offset = None
    for i in range(num_tables):
        tag, _, offset, _ = struct.unpack(">4sIII", data[12 + 16 * i: 28 + 16 * i])
        if tag == b"cmap":
            cmap_offset = offset
    if cmap_offset is None:
        raise ValueError("font has no cmap table")
    _, n = struct.unpack(">HH", data[cmap_offset:cmap_offset + 4])
    mapped = set()
    for i in range(n):
        platform, encoding, sub = struct.unpack(
            ">HHI", data[cmap_offset + 4 + 8 * i: cmap_offset + 12 + 8 * i])
        if (platform, encoding) not in {(0, 3), (0, 4), (3, 1), (3, 10)}:
            continue
        base = cmap_offset + sub
        fmt = struct.unpack(">H", data[base:base + 2])[0]
        if fmt == 4:
            mapped |= _format4(data, base)
        elif fmt == 12:
            mapped |= _format12(data, base)
    return mapped


def _format4(data, base):
    seg_x2 = struct.unpack(">H", data[base + 6:base + 8])[0]
    segs = seg_x2 // 2
    ends = struct.unpack(f">{segs}H", data[base + 14:base + 14 + seg_x2])
    starts_at = base + 16 + seg_x2
    starts = struct.unpack(f">{segs}H", data[starts_at:starts_at + seg_x2])
    deltas = struct.unpack(f">{segs}h", data[starts_at + seg_x2:starts_at + 2 * seg_x2])
    ro_at = starts_at + 2 * seg_x2
    range_offsets = struct.unpack(f">{segs}H", data[ro_at:ro_at + seg_x2])
    out = set()
    for s in range(segs):
        for cp in range(starts[s], ends[s] + 1):
            if cp == 0xFFFF:
                continue
            if range_offsets[s] == 0:
                glyph = (cp + deltas[s]) & 0xFFFF
            else:
                addr = ro_at + 2 * s + range_offsets[s] + 2 * (cp - starts[s])
                glyph = struct.unpack(">H", data[addr:addr + 2])[0]
                if glyph:
                    glyph = (glyph + deltas[s]) & 0xFFFF
            if glyph:
                out.add(cp)
    return out


def _format12(data, base):
    n = struct.unpack(">I", data[base + 12:base + 16])[0]
    out = set()
    for i in range(n):
        start, end, glyph = struct.unpack(">III", data[base + 16 + 12 * i: base + 28 + 12 * i])
        for cp in range(start, end + 1):
            if glyph + (cp - start):
                out.add(cp)
    return out


def main():
    if len(sys.argv) != 2:
        print(__doc__, file=sys.stderr)
        return 2
    mapped = read_cmap(sys.argv[1])
    wanted = []
    with open(ALLOWED, encoding="utf-8") as f:
        for line in f:
            if line.strip():
                cp_hex, name = line.strip().split(" ", 1)
                wanted.append((int(cp_hex[2:], 16), name))
    wanted += [(cp, "(formatting)") for cp in FORMATTING_EXTRAS]
    missing = [(cp, name) for cp, name in wanted if cp not in mapped]
    for cp, name in missing:
        print(f"missing U+{cp:04X} {name}")
    print(f"{len(wanted) - len(missing)}/{len(wanted)} code points covered by {os.path.basename(sys.argv[1])}")
    return 1 if missing else 0


if __name__ == "__main__":
    sys.exit(main())
