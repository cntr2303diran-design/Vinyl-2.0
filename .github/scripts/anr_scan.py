#!/usr/bin/env python3
"""Baca laporan ANR paket tertentu dari berkas logcat (format `logcat -v threadtime`).

Pemakaian: anr_scan.py <logcat.txt> <paket>
Keluaran: satu baris per ANR, kolom dipisah TAB: <ErrorId> <beban rata-rata 1 menit> <alasan>.
Beban (baris "Load: 21.54 / 5.66 / 1.91" dari ActivityManager) dipakai skrip uji asap untuk membedakan ANR yang
terjadi saat seluruh emulator kehabisan CPU dari ANR yang terjadi pada emulator yang tenang.
Status 0 selalu (berkas tak terbaca = tanpa keluaran); status 2 bila argumen salah.
"""
import re
import sys


def main() -> int:
    if len(sys.argv) != 3:
        print("pemakaian: anr_scan.py <logcat.txt> <paket>", file=sys.stderr)
        return 2
    path, pkg = sys.argv[1], sys.argv[2]
    try:
        with open(path, encoding="utf-8", errors="replace") as f:
            lines = f.read().splitlines()
    except OSError:
        return 0
    start = re.compile(r"ActivityManager: ANR in " + re.escape(pkg) + r"(\s|$)")
    n = len(lines)
    i = 0
    while i < n:
        if not start.search(lines[i]):
            i += 1
            continue
        block = lines[i : i + 40]
        eid, load, reason = "", "", ""
        for ln in block:
            m = re.search(r"ActivityManager: ErrorId: (\S+)", ln)
            if m and not eid:
                eid = m.group(1)
            m = re.search(r"ActivityManager: Load: ([0-9.]+)", ln)
            if m and not load:
                load = m.group(1)
            m = re.search(r"ActivityManager: Reason: (.*)$", ln)
            if m and not reason:
                reason = m.group(1).strip()
        if not eid:
            # tanpa ErrorId (versi Android lama): pakai nomor baris supaya tetap unik
            eid = f"baris{i}"
        reason = re.sub(r"\s+", " ", reason)[:160]
        print(f"{eid}\t{load}\t{reason}")
        i += 40
    return 0


if __name__ == "__main__":
    sys.exit(main())
