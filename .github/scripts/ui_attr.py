#!/usr/bin/env python3
"""Baca satu atribut dari simpul pertama hasil `uiautomator dump` yang teks atau content-desc-nya cocok (regex).

Pemakaian: ui_attr.py <berkas.xml> <regex> <atribut> [--baris]   (mis. checked, selected, enabled)
Keluaran (status 0): nilai atribut itu ("true" / "false"). Status 1 bila tidak ada simpul yang cocok; status 2 bila berkas
tidak bisa dibaca atau atributnya tidak ada.

--baris: untuk sakelar/kotak centang yang labelnya terpisah. Compose menampilkan baris yang bisa diketuk sebagai satu
simpul (checkable, tanpa teks) dan labelnya sebagai simpul TextView lain di dalam batasnya, sehingga atribut `checked`
pada simpul berlabel selalu "false". Dengan --baris, bila simpul yang cocok tidak checkable, atribut dibaca dari simpul
checkable terkecil yang batasnya memuat simpul berlabel itu (baris sakelarnya). Bila tidak ada, status 2.
"""
import re
import sys
import xml.etree.ElementTree as ET

BOUNDS = re.compile(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]")


def bounds_of(node):
    m = BOUNDS.fullmatch(node.get("bounds") or "")
    return tuple(int(g) for g in m.groups()) if m else None


def contains(outer, inner):
    return outer[0] <= inner[0] and outer[1] <= inner[1] and outer[2] >= inner[2] and outer[3] >= inner[3]


def area(b):
    return max(0, b[2] - b[0]) * max(0, b[3] - b[1])


def main() -> int:
    args = [a for a in sys.argv[1:] if a != "--baris"]
    row = len(args) != len(sys.argv) - 1
    if len(args) != 3:
        print("pemakaian: ui_attr.py <berkas.xml> <regex> <atribut> [--baris]", file=sys.stderr)
        return 2
    path, pattern, attr = args
    rx = re.compile(pattern)
    try:
        tree = ET.parse(path)
    except (ET.ParseError, OSError):
        return 2
    found = None
    for node in tree.iter("node"):
        for field in ("content-desc", "text"):
            value = node.get(field) or ""
            if value and rx.search(value):
                found = node
                break
        if found is not None:
            break
    if found is None:
        return 1
    target = found
    if row and found.get("checkable") != "true":
        label = bounds_of(found)
        if label is None:
            return 2
        best = None
        for node in tree.iter("node"):
            if node.get("checkable") != "true":
                continue
            b = bounds_of(node)
            if b is not None and contains(b, label) and (best is None or area(b) < area(bounds_of(best))):
                best = node
        if best is None:
            return 2
        target = best
    got = target.get(attr)
    if got is None:
        return 2
    print(got)
    return 0


if __name__ == "__main__":
    sys.exit(main())
