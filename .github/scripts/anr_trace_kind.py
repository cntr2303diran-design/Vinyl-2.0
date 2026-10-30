#!/usr/bin/env python3
"""Tentukan jenis sebuah ANR dari jejak tumpukannya (berkas anr_* dari /data/anr, boleh .gz).

Pemakaian:
  anr_trace_kind.py <folder-anr> <paket> <alasan-dari-logcat>   cari jejak yang cocok lalu nilai
  anr_trace_kind.py --selftest                                  uji diri dengan jejak buatan

Keluaran satu baris, kolom dipisah TAB: <JENIS> <keterangan>
  EMULATOR_GPU  thread utama paket hanya menunggu RenderThread (baris Java teratasnya metode native
                android.graphics.HardwareRenderer.n*) DAN RenderThread sedang di libEGL_emulation / qemu pipe, yaitu
                menunggu GPU emulator di sisi host (render perangkat lunak). Kode aplikasi tidak sedang dikerjakan.
  APP           selain itu: thread utama menjalankan atau menunggu sesuatu yang lain, atau bukti tidak lengkap.
                Tetap diperlakukan sebagai masalah aplikasi.
  TANPA-JEJAK   tidak ada jejak yang cocok dengan alasan itu (atau paketnya tidak ada di jejak).
Status 0 selalu, kecuali argumen salah (2) atau uji diri gagal (1).

Nama kelas aplikasi TIDAK dipakai (build release diacak oleh R8); yang dinilai hanya bentuk tumpukan thread utama dan
RenderThread.
"""
import gzip
import os
import re
import sys

# Tanda RenderThread yang menunggu GPU emulator (goldfish/ranchu): EGL emulasi dan pipa QEMU ke host
EMULATOR_MARKERS = ("libEGL_emulation.so", "qemu_pipe", "QemuPipeStream", "lib_renderControl_enc.so")
# Baris Java teratas thread utama bila ia sedang menunggu RenderThread
RENDER_WAIT = re.compile(r"^\s*at android\.graphics\.HardwareRenderer\.(n\w+)\(Native method\)")


def _read(path: str) -> str:
    opener = gzip.open if path.endswith(".gz") else open
    with opener(path, "rt", encoding="utf-8", errors="replace") as f:
        return f.read()


def _norm(s: str) -> str:
    return re.sub(r"\s+", " ", s).strip()


def _package_block(text: str, pkg: str):
    """Baris dump milik proses `pkg`: dari '----- pid N ...' (diikuti 'Cmd line: pkg') sampai '----- end N -----'."""
    lines = text.splitlines()
    for i, ln in enumerate(lines):
        m = re.match(r"^----- pid (\d+) at ", ln)
        if not m or i + 1 >= len(lines) or lines[i + 1].strip() != f"Cmd line: {pkg}":
            continue
        end = re.compile(rf"^----- end {m.group(1)} -----")
        for j in range(i + 1, len(lines)):
            if end.match(lines[j]):
                return lines[i:j]
        return lines[i:]
    return None


def _thread(block, name: str):
    """Baris bagian thread bernama `name` (pertama yang ditemukan), sampai baris kosong."""
    start = None
    for i, ln in enumerate(block):
        if ln.startswith(f'"{name}" '):
            start = i
            break
    if start is None:
        return None
    out = []
    for ln in block[start:]:
        if out and not ln.strip():
            break
        out.append(ln)
    return out


def _cpu_ms(section) -> str:
    for ln in section:
        m = re.search(r"schedstat=\(\s*(\d+)\s", ln)
        if m:
            return f"{int(m.group(1)) // 1_000_000} ms"
    return "?"


def classify_text(text: str, pkg: str):
    block = _package_block(text, pkg)
    if block is None:
        return "TANPA-JEJAK", f"paket {pkg} tidak ada di jejak"
    main = _thread(block, "main")
    if main is None:
        return "APP", "thread utama tidak ditemukan di jejak"
    top = next((ln for ln in main if ln.lstrip().startswith("at ")), "")
    m = RENDER_WAIT.match(top)
    if not m:
        return "APP", f"thread utama sedang di: {_norm(top) or '(tanpa bingkai Java)'}"
    render = _thread(block, "RenderThread")
    if render is None:
        return "APP", "thread utama menunggu RenderThread, tetapi RenderThread tidak ada di jejak"
    hit = next((mk for mk in EMULATOR_MARKERS if any(mk in ln for ln in render)), None)
    if hit is None:
        return "APP", "thread utama menunggu RenderThread, tetapi RenderThread tidak sedang menunggu GPU emulator"
    return "EMULATOR_GPU", (
        f"thread utama menunggu RenderThread (HardwareRenderer.{m.group(1)}); RenderThread di {hit} "
        f"(menunggu GPU emulator); CPU thread utama {_cpu_ms(main)}, RenderThread {_cpu_ms(render)}"
    )


def find_trace(folder: str, reason: str):
    """Isi jejak yang baris 'Subject:'-nya memuat awal `reason` (memuat kode jendela unik, mis. 'bd8cdeb com.…')."""
    key = _norm(reason)[:80]
    if not key or not os.path.isdir(folder):
        return None
    for name in sorted(os.listdir(folder)):
        if not name.startswith("anr_"):
            continue
        try:
            text = _read(os.path.join(folder, name))
        except OSError:
            continue
        first = next((ln for ln in text.splitlines()[:5] if ln.startswith("Subject:")), "")
        if key in _norm(first):
            return text
    return None


# ---------------------------------------------------------------- uji diri (jejak buatan)

def _fake(main_top: str, render_native: str, pkg: str = "com.vinyl.launcher", reason: str = "Input dispatching timed out (abc1234 com.vinyl.launcher/x.HomeActivity (server) is not responding.") -> str:
    return "\n".join([
        f"Subject: {reason}",
        "",
        f"----- pid 7007 at 2026-10-30 14:23:06.731 +0000 -----",
        f"Cmd line: {pkg}",
        "DALVIK THREADS (3):",
        '"main" prio=5 tid=1 Native',
        "  | sysTid=7007 nice=-10",
        "  | state=S schedstat=( 140364694 67928004 231 ) utm=10 stm=3 core=1 HZ=100",
        "  native: #00 pc 0001  /apex/com.android.runtime/lib64/bionic/libc.so (syscall+22)",
        main_top,
        "  at android.view.ViewRootImpl.performDraw(ViewRootImpl.java:5351)",
        "  at android.os.Looper.loop(Looper.java:317)",
        "",
        '"RenderThread" daemon prio=7 tid=24 Native',
        "  | state=R schedstat=( 5770140462 185295210 359 ) utm=7 stm=569 core=0 HZ=100",
        render_native,
        "  native: #14 pc 0002  /system/lib64/libhwui.so (android::uirenderer::renderthread::RenderThread::threadLoop+475)",
        "",
        '"vinyl-secure" daemon prio=1 tid=15 Waiting',
        "  at java.lang.Thread.run(Thread.java:1012)",
        "",
        "----- end 7007 -----",
        "",
    ])


def selftest() -> int:
    pkg = "com.vinyl.launcher"
    gpu = "  native: #04 pc 0003  /vendor/lib64/egl/libEGL_emulation.so (createNativeSync+134)"
    idle = "  native: #04 pc 0004  /system/lib64/libhwui.so (android::uirenderer::renderthread::RenderThread::waitForWork+10)"
    cases = [
        ("render menunggu GPU emulator", _fake("  at android.graphics.HardwareRenderer.nSetStopped(Native method)", gpu), "EMULATOR_GPU"),
        ("sync-and-draw juga dikenali", _fake("  at android.graphics.HardwareRenderer.nSyncAndDrawFrame(Native method)", gpu), "EMULATOR_GPU"),
        ("kode aplikasi (diacak R8) di puncak tumpukan utama", _fake("  at p3.g0.b(Unknown Source:12)", gpu), "APP"),
        ("thread utama menunggu kunci", _fake("  at java.lang.Object.wait(Native method)", gpu), "APP"),
        ("RenderThread tidak menunggu GPU emulator", _fake("  at android.graphics.HardwareRenderer.nSetStopped(Native method)", idle), "APP"),
        ("paket lain", _fake("  at android.graphics.HardwareRenderer.nSetStopped(Native method)", gpu, pkg="com.lain.app"), "TANPA-JEJAK"),
    ]
    bad = 0
    for label, text, want in cases:
        got, detail = classify_text(text, pkg)
        flag = "ok" if got == want else "SALAH"
        if got != want:
            bad += 1
        print(f"  [{flag}] {label}: {got} ({detail})")
    # pencocokan jejak lewat baris Subject
    import tempfile
    with tempfile.TemporaryDirectory() as d:
        reason = "Input dispatching timed out (abc1234 com.vinyl.launcher/x.HomeActivity (server) is not responding."
        with gzip.open(os.path.join(d, "anr_2026-01-01-00-00-00-000.gz"), "wt", encoding="utf-8") as f:
            f.write(_fake("  at android.graphics.HardwareRenderer.nSetStopped(Native method)", gpu))
        with open(os.path.join(d, "anr_2026-01-01-00-00-01-000"), "w", encoding="utf-8") as f:
            f.write(_fake("  at p3.g0.b(Unknown Source:12)", gpu, reason="Input dispatching timed out (zzz9999 com.vinyl.launcher/x.HomeActivity (server) is not responding."))
        for label, rs, want in (
            ("jejak cocok (berkas .gz)", reason, "EMULATOR_GPU"),
            ("jejak cocok (berkas polos)", "Input dispatching timed out (zzz9999 com.vinyl.launcher/x.HomeActivity (server) is not responding.", "APP"),
            ("tidak ada jejak yang cocok", "Input dispatching timed out (nope000 com.vinyl.launcher/x.HomeActivity (server) is not responding.", "TANPA-JEJAK"),
        ):
            text = find_trace(d, rs)
            got = classify_text(text, pkg)[0] if text else "TANPA-JEJAK"
            flag = "ok" if got == want else "SALAH"
            if got != want:
                bad += 1
            print(f"  [{flag}] {label}: {got}")
    print("uji diri: " + ("lulus" if bad == 0 else f"{bad} kasus salah"))
    return 0 if bad == 0 else 1


def main() -> int:
    if len(sys.argv) == 2 and sys.argv[1] == "--selftest":
        return selftest()
    if len(sys.argv) != 4:
        print("pemakaian: anr_trace_kind.py <folder-anr> <paket> <alasan> | --selftest", file=sys.stderr)
        return 2
    folder, pkg, reason = sys.argv[1:4]
    text = find_trace(folder, reason)
    if text is None:
        print("TANPA-JEJAK\ttidak ada jejak yang cocok dengan alasan ANR itu")
        return 0
    kind, detail = classify_text(text, pkg)
    print(f"{kind}\t{detail}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
