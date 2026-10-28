#!/usr/bin/env python3
"""Kenali dialog galat sistem (ANR atau crash) pada hasil `uiautomator dump` dan tentukan tombol yang harus diketuk.

Pemakaian: ui_dialog.py <berkas.xml>
Keluaran (status 0), satu baris dengan kolom dipisah TAB: <judul dialog> <teks tombol> <x tengah> <y tengah>.
Status 1 bila layar yang dibaca bukan dialog galat; status 2 bila berkas tidak bisa dibaca.

Dialog "<aplikasi> isn't responding" muncul ketika sebuah aplikasi kehabisan CPU (mis. Pixel Launcher bawaan emulator selagi
emulator CI baru booting) dan menutupi seluruh layar, sehingga pencarian simpul aplikasi yang diuji gagal terus. Untuk ANR
dipilih "Wait" (aplikasinya tetap hidup dan tidak ada keadaan yang berubah), untuk crash ("<aplikasi> keeps stopping" /
"has stopped") dipilih "Close app". Sebuah layar baru dianggap dialog galat bila ada judul seperti itu DAN tombol yang
bisa diketuk, supaya teks aplikasi yang kebetulan mirip tidak salah dikenali.
"""
import re
import sys
import xml.etree.ElementTree as ET

TITLE = re.compile(r"(isn['’]t responding|has stopped|keeps stopping)\s*$", re.IGNORECASE)
ANR = re.compile(r"isn['’]t responding\s*$", re.IGNORECASE)
BOUNDS = re.compile(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]")


def center(node):
    m = BOUNDS.fullmatch(node.get("bounds") or "")
    if not m:
        return None
    x1, y1, x2, y2 = (int(g) for g in m.groups())
    if x2 <= x1 or y2 <= y1:
        return None
    return (x1 + x2) // 2, (y1 + y2) // 2


def main() -> int:
    if len(sys.argv) != 2:
        print("pemakaian: ui_dialog.py <berkas.xml>", file=sys.stderr)
        return 2
    try:
        tree = ET.parse(sys.argv[1])
    except (ET.ParseError, OSError):
        return 2
    nodes = list(tree.iter("node"))
    title = ""
    for node in nodes:
        text = (node.get("text") or "").strip()
        if text and TITLE.search(text):
            title = text
            break
    if not title:
        return 1
    wanted = ("wait", "close app") if ANR.search(title) else ("close app", "ok")
    for label in wanted:
        for node in nodes:
            if node.get("clickable") != "true":
                continue
            if (node.get("text") or "").strip().lower() != label:
                continue
            pos = center(node)
            if pos is not None:
                print(f"{title}\t{node.get('text').strip()}\t{pos[0]}\t{pos[1]}")
                return 0
    return 1


if __name__ == "__main__":
    sys.exit(main())
