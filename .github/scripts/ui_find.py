#!/usr/bin/env python3
"""Cari simpul di berkas hasil `uiautomator dump` berdasarkan teks atau content-desc (regex).

Pemakaian: ui_find.py <berkas.xml> <regex>
Keluaran (status 0): "cx cy x1 y1 x2 y2" untuk simpul pertama (urutan dokumen) yang cocok dan punya batas tak kosong.
Status 1 bila tidak ada yang cocok; status 2 bila berkas tidak bisa dibaca.
"""
import re
import sys
import xml.etree.ElementTree as ET


def main() -> int:
    if len(sys.argv) != 3:
        print("pemakaian: ui_find.py <berkas.xml> <regex>", file=sys.stderr)
        return 2
    path, pattern = sys.argv[1], sys.argv[2]
    rx = re.compile(pattern)
    try:
        tree = ET.parse(path)
    except (ET.ParseError, OSError):
        return 2
    for node in tree.iter("node"):
        for attr in ("content-desc", "text"):
            value = node.get(attr) or ""
            if value and rx.search(value):
                m = re.match(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]", node.get("bounds") or "")
                if m:
                    x1, y1, x2, y2 = (int(g) for g in m.groups())
                    if x2 > x1 and y2 > y1:
                        print((x1 + x2) // 2, (y1 + y2) // 2, x1, y1, x2, y2)
                        return 0
    return 1


if __name__ == "__main__":
    sys.exit(main())
