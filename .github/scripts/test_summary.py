#!/usr/bin/env python3
"""Ringkas laporan tes unit (JUnit XML buatan Gradle) menjadi teks untuk build.log.

Build ini hanya mencatat tes yang GAGAL, jadi tanpa ringkasan ini log tidak menunjukkan berapa tes yang sungguh berjalan.
Skrip hanya MEMBACA laporan: tes yang gagal sudah menggagalkan langkah Gradle-nya sendiri, dan skrip ini selalu selesai
dengan kode 0 supaya tidak menutupi galat yang sebenarnya.

Pemakaian: test_summary.py [folder-laporan]    (bawaan: app/build/test-results)
"""
import glob
import os
import sys
import xml.etree.ElementTree as ET


def read_suite(path):
    """Satu berkas laporan -> kamus ringkasan, atau None bila tidak terbaca."""
    try:
        root = ET.parse(path).getroot()
    except (ET.ParseError, OSError):
        return None
    if root.tag != "testsuite":
        return None
    suite = {
        "name": root.get("name", os.path.basename(path)),
        "tests": int(root.get("tests", "0")),
        "failures": int(root.get("failures", "0")),
        "errors": int(root.get("errors", "0")),
        "skipped": int(root.get("skipped", "0")),
        "failed_cases": [],
        "skipped_cases": [],
    }
    for case in root.iter("testcase"):
        label = "%s.%s" % (case.get("classname", suite["name"]), case.get("name", "?"))
        bad = case.find("failure")
        if bad is None:
            bad = case.find("error")
        if bad is not None:
            first = (bad.get("message") or "").strip().splitlines()
            suite["failed_cases"].append("%s: %s" % (label, first[0][:200] if first else bad.get("type", "")))
        elif case.find("skipped") is not None:
            suite["skipped_cases"].append(label)
    return suite


def totals(suites):
    return {k: sum(s[k] for s in suites) for k in ("tests", "failures", "errors", "skipped")}


def line(prefix, t, classes):
    return "%s%d tes, %d gagal, %d galat, %d dilewati, %d kelas" % (
        prefix, t["tests"], t["failures"], t["errors"], t["skipped"], classes)


def main(root_dir):
    out = ["===== RINGKASAN TES UNIT (%s) =====" % root_dir]
    files = sorted(glob.glob(os.path.join(root_dir, "**", "TEST-*.xml"), recursive=True))
    if not files:
        out.append("tidak ada laporan tes di folder ini")
        emit(out, None)
        return

    by_task = {}
    unreadable = []
    for f in files:
        suite = read_suite(f)
        if suite is None:
            unreadable.append(f)
            continue
        task = os.path.relpath(f, root_dir).split(os.sep)[0]
        by_task.setdefault(task, []).append(suite)

    everything = []
    for task in sorted(by_task):
        suites = sorted(by_task[task], key=lambda s: s["name"])
        everything.extend(suites)
        out.append(line("[%s] " % task, totals(suites), len(suites)))
        for s in suites:
            out.append("  %4d  %s%s" % (s["tests"], s["name"], "" if s["failures"] + s["errors"] == 0 else "   <-- GAGAL"))
        for s in suites:
            for c in s["failed_cases"]:
                out.append("  GAGAL: " + c)
            for c in s["skipped_cases"]:
                out.append("  DILEWATI: " + c)
    for f in unreadable:
        out.append("LAPORAN TIDAK TERBACA: " + f)

    t = totals(everything)
    out.append(line("TOTAL SEMUA: ", t, len(everything)))
    if t["tests"] == 0:
        out.append("PERINGATAN: nol tes terbaca; periksa apakah tes sungguh berjalan")
    emit(out, t)


def emit(lines, t):
    print("\n".join(lines))
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        try:
            with open(summary, "a", encoding="utf-8") as fh:
                if t is None:
                    fh.write("### Tes unit: tidak ada laporan\n")
                else:
                    fh.write("### Tes unit: %d tes, %d gagal, %d galat, %d dilewati\n"
                             % (t["tests"], t["failures"], t["errors"], t["skipped"]))
        except OSError:
            pass


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "app/build/test-results")
    sys.exit(0)
