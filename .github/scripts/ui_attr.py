#!/usr/bin/env python3
"""Baca satu atribut dari simpul pertama hasil `uiautomator dump` yang teks atau content-desc-nya cocok (regex).

Pemakaian: ui_attr.py <berkas.xml> <regex> <atribut>   (mis. checked, selected, enabled)
Keluaran (status 0): nilai atribut itu ("true" / "false"). Status 1 bila tidak ada simpul yang cocok; status 2 bila berkas
tidak bisa dibaca atau atributnya tidak ada.
"""
import re
import sys
import xml.etree.ElementTree as ET


def main() -> int:
    if len(sys.argv) != 4:
        print("pemakaian: ui_attr.py <berkas.xml> <regex> <atribut>", file=sys.stderr)
        return 2
    path, pattern, attr = sys.argv[1], sys.argv[2], sys.argv[3]
    rx = re.compile(pattern)
    try:
        tree = ET.parse(path)
    except (ET.ParseError, OSError):
        return 2
    for node in tree.iter("node"):
        for field in ("content-desc", "text"):
            value = node.get(field) or ""
            if value and rx.search(value):
                got = node.get(attr)
                if got is None:
                    return 2
                print(got)
                return 0
    return 1


if __name__ == "__main__":
    sys.exit(main())
