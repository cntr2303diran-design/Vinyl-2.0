#!/usr/bin/env bash
# Uji asap APK di emulator (dijalankan oleh reactivecircus/android-emulator-runner di CI). BUKAN pengganti uji di
# perangkat nyata: emulator dirender perangkat lunak, jadi angka kecepatannya hanya berguna sebagai perbandingan relatif.
#
# Yang diperiksa (APK release, hasil R8):
#   - terpasang, tidak bisa di-debug, dan terbuka lewat HomeActivity; onboarding pertama-buka bisa diselesaikan
#   - rotasi layar, geser halaman, TV dimatikan/dinyalakan, laci kaset, game Ular (usap di layar TV), kaset film
#   - proses dimatikan lalu dibuka lagi: pengaturan HARUS bertahan (onboarding tidak muncul lagi), ini menguji
#     pembacaan/penulisan JSON kotlinx.serialization + DataStore setelah R8
#   - paket dipasang/dihapus saat launcher berjalan (receiver perubahan paket)
#   - profil dasar (Baseline Profile) terpasang oleh ProfileInstaller dan bisa dikompilasi
#   - logcat bebas dari crash, ANR, dan tanda R8 rusak (NoSuchMethodError, SerializationException, dst.)
# Lalu perbandingan kelancaran geser release vs debug di emulator yang sama (relatif saja).
#
# Keluaran di <folder-keluaran>: result.txt (baris pertama PASS / FAIL: ... / INCONCLUSIVE: ...), log.txt, *.png,
# logcat_*.txt, ui_*.xml, gfx_*.txt. Skrip SELALU keluar dengan status 0; langkah CI berikutnya membaca result.txt.
# Langkah yang bergantung pada pencarian elemen layar (uiautomator) hanya memberi PERINGATAN bila elemen tak ditemukan;
# crash, ANR, tanda R8 rusak, dan pengaturan yang hilang setelah proses dimatikan adalah GAGAL.
#
# Pemakaian: smoke_test.sh <release.apk> <debug.apk> <folder-keluaran>
set -u
REL_APK="${1:?apk release}"
DBG_APK="${2:?apk debug}"
OUT="${3:?folder keluaran}"
PKG=com.vinyl.launcher
HOME_ACT="$PKG/.ui.home.HomeActivity"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
mkdir -p "$OUT"
: > "$OUT/log.txt"

FAILS=()
WARNS=()
OKS=()
COMPLETED=0
say()  { printf '%s\n' "$*" | tee -a "$OUT/log.txt"; }
fail() { FAILS+=("$*"); say "GAGAL: $*"; }
warn() { WARNS+=("$*"); say "PERINGATAN: $*"; }
ok()   { OKS+=("$*");   say "OK: $*"; }
step() { say ""; say "=== $* ==="; }

write_result() {
  {
    if [ "${#FAILS[@]}" -gt 0 ]; then echo "FAIL: ${FAILS[0]}"
    elif [ "$COMPLETED" -ne 1 ]; then echo "INCONCLUSIVE: skrip terhenti sebelum selesai"
    else echo "PASS"; fi
    local m
    for m in "${FAILS[@]}"; do echo "gagal: $m"; done
    for m in "${WARNS[@]}"; do echo "peringatan: $m"; done
    for m in "${OKS[@]}"; do echo "ok: $m"; done
  } > "$OUT/result.txt"
}
trap write_result EXIT

# ---------- alat bantu ----------
adbs()  { adb shell "$@" 2>&1 | tr -d '\r'; }
alive() { [ -n "$(adbs pidof "$PKG")" ]; }
top_is() { adbs dumpsys activity activities | grep -m1 -E 'topResumedActivity=|ResumedActivity' | grep -q "$1"; }

SHOT_N=0
shot() {
  SHOT_N=$((SHOT_N + 1))
  local f
  f=$(printf '%s/%02d_%s.png' "$OUT" "$SHOT_N" "$1")
  adb exec-out screencap -p > "$f" 2>/dev/null
  if [ ! -s "$f" ]; then warn "tangkapan layar gagal: $1"; rm -f "$f"; fi
}

# uiautomator kadang gagal ("could not get idle state") pada layar yang terus beranimasi, maka dicoba beberapa kali
dump_ui() {
  local f="$OUT/ui_$1.xml" i
  for i in 1 2 3; do
    if timeout 45 adb shell uiautomator dump /sdcard/ui_dump.xml 2>&1 | grep -q 'dumped to'; then
      if adb pull /sdcard/ui_dump.xml "$f" > /dev/null 2>&1 && [ -s "$f" ]; then return 0; fi
    fi
    sleep 2
  done
  return 1
}

NX=0; NY=0; NX1=0; NY1=0; NX2=0; NY2=0
# find_node <nama> <regex>: isi NX NY (titik tengah) dan NX1..NY2 (batas); status 0 bila ada
find_node() {
  local res
  dump_ui "$1" || return 2
  res=$(python3 -I "$HERE/ui_find.py" "$OUT/ui_$1.xml" "$2") || return 1
  read -r NX NY NX1 NY1 NX2 NY2 <<< "$res"
  return 0
}
tap_node() { # <nama> <regex>
  if find_node "$1" "$2"; then adb shell input tap "$NX" "$NY"; return 0; fi
  return 1
}

SIZE=$(adbs wm size | grep -oE '[0-9]+x[0-9]+' | tail -1)
W=${SIZE%x*}
H=${SIZE#*x}
swipe_left()  { adb shell input swipe $((W * 85 / 100)) $((H * 50 / 100)) $((W * 15 / 100)) $((H * 50 / 100)) "${1:-250}"; }
swipe_right() { adb shell input swipe $((W * 15 / 100)) $((H * 50 / 100)) $((W * 85 / 100)) $((H * 50 / 100)) "${1:-250}"; }
swipe_up()    { adb shell input swipe $((W / 2)) $((H * 75 / 100)) $((W / 2)) $((H * 25 / 100)) 300; }

# scroll_tap <nama> <regex>: cari, bila belum tampak gulir ke bawah sampai 5 kali
scroll_tap() {
  local i
  for i in 1 2 3 4 5; do
    if tap_node "$1_$i" "$2"; then return 0; fi
    swipe_up
    sleep 1
  done
  return 1
}

check_alive() { # <label>
  sleep 1
  if alive; then ok "proses hidup: $1"; return 0; fi
  fail "proses mati: $1"
  return 1
}

scan_log() { # <label>: baca logcat sejauh ini
  local f="$OUT/logcat_$1.txt"
  adb logcat -d -v threadtime > "$f" 2>&1
  if grep -A4 'FATAL EXCEPTION' "$f" | grep -q "Process: $PKG"; then
    fail "crash (FATAL EXCEPTION) di $PKG — $1"
    grep -A14 'FATAL EXCEPTION' "$f" | head -50 | tee -a "$OUT/log.txt"
  fi
  if grep -qE "ANR in $PKG" "$f"; then fail "ANR di $PKG — $1"; fi
  local sig='NoSuchMethodError|NoSuchFieldError|AbstractMethodError|IncompatibleClassChangeError|VerifyError|SerializationException|MissingFieldException'
  if grep -E "$sig" "$f" | grep -q .; then
    fail "tanda R8 rusak di logcat ($1): $(grep -E "$sig" "$f" | head -3 | tr '\n' ' ')"
  fi
  # Kosongkan supaya pemeriksaan berikutnya hanya membaca bagian baru (berkas lama tetap tersimpan)
  adb logcat -c
}

install_apk() { # <label> <apk>: pasang bersih (data lama dibuang supaya onboarding muncul lagi)
  local o
  adb uninstall "$PKG" > /dev/null 2>&1
  o=$(adb install -r -t "$2" 2>&1)
  echo "$o" | tail -2 | tee -a "$OUT/log.txt"
  echo "$o" | grep -q 'Success'
}

launch_home() { # <label>
  local o
  o=$(adbs am start -W -n "$HOME_ACT")
  say "am start ($1): $(echo "$o" | grep -E 'Status|LaunchState|TotalTime|WaitTime|Error' | tr '\n' ' ')"
  if ! echo "$o" | grep -q 'Status: ok'; then fail "am start gagal ($1): $(echo "$o" | head -3 | tr '\n' ' ')"; return 1; fi
  return 0
}

# Onboarding pertama-buka: ketuk "Mulai" (menulis onboardingDone ke DataStore)
first_run() { # <label>
  local i
  sleep 5
  for i in 1 2 3; do
    if top_is Onboarding; then
      say "onboarding tampil ($1)"
      shot "${1}_onboarding"
      if scroll_tap "${1}_mulai" '^Mulai$'; then
        sleep 4
        return 0
      fi
      warn "tombol Mulai tidak ditemukan ($1); onboarding ditutup dengan BACK"
      adb shell input keyevent KEYCODE_BACK
      sleep 2
      return 1
    fi
    sleep 2
  done
  warn "onboarding tidak muncul pada pemasangan baru ($1)"
  return 1
}

rotate() { # 0 = tegak, 1 = mendatar
  adb shell settings put system accelerometer_rotation 0
  adb shell settings put system user_rotation "$1"
  sleep 3
}

# ---------- persiapan emulator ----------
step "Persiapan"
adb wait-for-device
adbs getprop ro.build.version.sdk | sed 's/^/API: /' | tee -a "$OUT/log.txt"
adbs getprop ro.product.model | sed 's/^/model: /' | tee -a "$OUT/log.txt"
say "layar: ${W}x${H}"
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard > /dev/null 2>&1
adb shell input keyevent 82
adb shell settings put global hide_error_dialogs 1 > /dev/null 2>&1
adb shell settings put global stay_on_while_plugged_in 3 > /dev/null 2>&1
adb logcat -G 16M > /dev/null 2>&1
adb logcat -c

# ---------- 1. pasang dan buka ----------
step "Pasang APK release dan buka"
if ! install_apk release "$REL_APK"; then
  fail "pemasangan APK release gagal"
  exit 0
fi
ok "APK release terpasang"
pkginfo=$(adbs dumpsys package "$PKG")
echo "$pkginfo" | grep -E 'versionCode=|versionName=|flags=|pkgFlags=' | head -4 | tee -a "$OUT/log.txt"
if echo "$pkginfo" | grep -E 'flags=|pkgFlags=' | grep -q 'DEBUGGABLE'; then
  fail "APK release terpasang sebagai DEBUGGABLE"
else
  ok "APK release tidak bisa di-debug"
fi
launch_home release || exit 0
shot "pertama_buka"
first_run release
shot "beranda"
if top_is HomeActivity; then ok "beranda (HomeActivity) tampil"; else warn "beranda tidak terdeteksi di depan: $(adbs dumpsys activity activities | grep -m1 -E 'topResumedActivity=')"; fi
check_alive "setelah dibuka"

# ---------- 2. profil dasar ----------
step "Profil dasar (Baseline Profile)"
sleep 8
bc=$(adbs am broadcast -a androidx.profileinstaller.action.INSTALL_PROFILE "$PKG/androidx.profileinstaller.ProfileInstallReceiver")
echo "$bc" | tee -a "$OUT/log.txt"
if echo "$bc" | grep -q 'result=1'; then ok "ProfileInstaller memasang profil (result=1)"; else warn "ProfileInstaller tidak memberi result=1"; fi
# Isi profil yang terpasang (perlu root; emulator google_apis mengizinkannya). Jumlah baris = jumlah kelas/metode di profil.
adb root > /dev/null 2>&1
adb wait-for-device
adbs cmd package dump-profiles "$PKG" > /dev/null 2>&1
profdump=$(adbs "ls /data/misc/profman/${PKG}*.txt 2>/dev/null | head -1")
if [ -n "$profdump" ]; then
  n=$(adbs "wc -l < $profdump" | tr -d ' ')
  say "profil terpasang: $profdump ($n baris)"
  adbs "head -3 $profdump" | tee -a "$OUT/log.txt"
  if [ "${n:-0}" -gt 100 ] 2>/dev/null; then ok "profil dasar terpasang berisi $n baris"; else warn "profil dasar terpasang sangat kecil ($n baris)"; fi
else
  warn "isi profil terpasang tidak bisa dibaca (dump-profiles tidak menghasilkan berkas)"
fi

# ---------- 3. rotasi ----------
step "Putar layar"
rotate 1
shot "mendatar"
check_alive "setelah diputar mendatar"
rotate 0
shot "tegak_lagi"
check_alive "setelah diputar tegak"

# ---------- 4. geser halaman ----------
step "Geser halaman"
swipe_left; sleep 1.5; shot "halaman_2"
swipe_left; sleep 1.5; shot "halaman_3"
swipe_right; sleep 1.2
swipe_right; sleep 1.5; shot "halaman_1_lagi"
check_alive "setelah geser halaman"

# ---------- 5. TV, laci, game ----------
step "Tombol daya TV"
if tap_node tv_daya '^Tombol daya TV$'; then
  sleep 1.5; shot "tv_mati"
  if find_node tv_mati_cek '^TV mati$'; then ok "TV mati (semantik 'TV mati' ada)"; else warn "semantik 'TV mati' tidak ditemukan setelah tombol daya"; fi
  tap_node tv_daya2 '^Tombol daya TV$' > /dev/null
  sleep 1.5; shot "tv_nyala_lagi"
  if find_node tv_nyala_cek '^TV'; then ok "TV menyala lagi"; fi
else
  warn "tombol daya TV tidak ditemukan"
fi
check_alive "setelah tombol daya"

step "Laci kaset dan game Ular"
if tap_node laci '^Laci kaset'; then
  sleep 1.5; shot "laci_terbuka"
  if tap_node kaset_ular '^Kaset Ular, '; then
    ok "kaset Ular ada di laci dan diketuk"
    sleep 3.5; shot "game_siap"
    if find_node tv_game '^TV menampilkan game'; then
      ok "TV menampilkan game"
      GX=$NX; GY=$NY; GW=$((NX2 - NX1)); GH=$((NY2 - NY1))
      adb shell input tap "$GX" "$GY"; sleep 0.8
      for d in "right" "down" "left" "up" "right" "down"; do
        case $d in
          right) adb shell input swipe $((GX - GW / 4)) "$GY" $((GX + GW / 4)) "$GY" 120 ;;
          left)  adb shell input swipe $((GX + GW / 4)) "$GY" $((GX - GW / 4)) "$GY" 120 ;;
          down)  adb shell input swipe "$GX" $((GY - GH / 4)) "$GX" $((GY + GH / 4)) 120 ;;
          up)    adb shell input swipe "$GX" $((GY + GH / 4)) "$GX" $((GY - GH / 4)) 120 ;;
        esac
        sleep 0.7
      done
      shot "game_jalan"
      adb shell input tap "$GX" "$GY"; sleep 0.8; shot "game_jeda"
    else
      warn "TV tidak menampilkan game setelah kaset Ular diketuk"
    fi
    check_alive "setelah main game"
    # keluarkan kaset game: buka laci lagi, ketuk kaset yang sama
    if tap_node laci2 '^Laci kaset'; then
      sleep 1.5
      if tap_node kaset_ular2 '^Kaset Ular, '; then
        sleep 3.5; shot "kaset_dikeluarkan"
        if find_node tv_musik '^TV menampilkan game'; then warn "TV masih menampilkan game setelah kaset dikeluarkan"; else ok "kaset game keluar dari deck"; fi
      else
        warn "kaset Ular tidak ditemukan saat mengeluarkan"
      fi
    fi
  else
    warn "kaset Ular tidak ditemukan di laci"
  fi
else
  warn "kontrol laci kaset tidak ditemukan"
fi
check_alive "setelah laci dan game"

step "Kaset film (WebView, jaringan emulator bisa saja tidak ada)"
if tap_node laci3 '^Laci kaset'; then
  sleep 1.5
  if tap_node kaset_film '^Kaset (?!Ular, )[^:]+, '; then
    ok "kaset film diketuk"
    sleep 12; shot "film_dimuat"
    check_alive "setelah kaset film dimuat"
    rotate 1; sleep 2; shot "film_mendatar"
    check_alive "film saat diputar mendatar"
    rotate 0; sleep 2
    if tap_node laci4 '^Laci kaset' && sleep 1.5 && tap_node kaset_film2 '^Kaset (?!Ular, )[^:]+, '; then
      sleep 3; shot "film_dikeluarkan"
    else
      warn "kaset film tidak ditemukan untuk dikeluarkan"
    fi
  else
    warn "kaset film tidak ditemukan di laci"
  fi
else
  warn "kontrol laci kaset tidak ditemukan (film)"
fi
check_alive "setelah kaset film"
scan_log fungsi

# ---------- 6. proses dimatikan lalu dibuka lagi ----------
step "Proses dimatikan lalu dibuka lagi (pengaturan harus bertahan)"
adb shell input keyevent KEYCODE_HOME
sleep 2
adbs am kill "$PKG" > /dev/null
sleep 2
if alive; then
  say "am kill tidak mematikan proses (layanan latar?); mencoba kill -9 lewat root"
  adb root > /dev/null 2>&1
  adb wait-for-device
  pid=$(adbs pidof "$PKG")
  [ -n "$pid" ] && adbs kill -9 "$pid" > /dev/null
  sleep 2
fi
if alive; then warn "proses tidak bisa dimatikan; uji kematian proses dilewati"; else
  ok "proses dimatikan"
  launch_home setelah_kill && {
    sleep 6
    shot "setelah_proses_dimatikan"
    if top_is Onboarding; then
      fail "onboarding muncul lagi setelah proses dimatikan: pengaturan tidak bertahan (DataStore/JSON)"
    elif top_is HomeActivity; then
      ok "beranda terbuka tanpa onboarding: pengaturan bertahan setelah proses dimatikan"
    else
      warn "layar depan tidak dikenali setelah kill: $(adbs dumpsys activity activities | grep -m1 -E 'topResumedActivity=')"
    fi
    check_alive "setelah dibuka kembali"
  }
fi

# ---------- 7. paket dipasang/dihapus saat launcher berjalan ----------
step "Aplikasi dihapus dan dipasang kembali saat launcher berjalan"
cand=""
for p in com.android.egg com.google.android.apps.docs com.google.android.apps.maps com.google.android.youtube \
         com.google.android.apps.photos com.google.android.apps.messaging com.google.android.calendar \
         com.android.calendar com.google.android.calculator com.android.calculator2 com.google.android.deskclock; do
  if adbs pm list packages "$p" | grep -q "package:$p\$"; then cand=$p; break; fi
done
if [ -n "$cand" ]; then
  say "paket uji: $cand"
  adbs pm uninstall -k --user 0 "$cand" | tee -a "$OUT/log.txt"
  sleep 4; check_alive "setelah paket $cand dihapus"; shot "paket_dihapus"
  adbs cmd package install-existing --user 0 "$cand" | tee -a "$OUT/log.txt"
  sleep 4; check_alive "setelah paket $cand dipasang kembali"; shot "paket_dipasang"
else
  warn "tidak ada paket sistem yang bisa dipakai untuk uji hapus/pasang"
fi
scan_log akhir_fungsi

# ---------- 8. kompilasi profil dasar ----------
step "Kompilasi dengan profil (speed-profile)"
adbs cmd package compile -f -m speed-profile "$PKG" | tee -a "$OUT/log.txt"
adbs dumpsys package dexopt | grep -A2 "\[$PKG\]" | head -6 | tee -a "$OUT/log.txt"

# ---------- 9. perbandingan kelancaran geser (relatif) ----------
perf_run() { # <label> <apk>
  local lbl=$1
  if ! install_apk "$lbl" "$2"; then warn "perbandingan: pemasangan $lbl gagal"; return 1; fi
  adb logcat -c
  launch_home "perf_$lbl" || return 1
  first_run "perf_$lbl" > /dev/null
  sleep 12
  adb shell dumpsys gfxinfo "$PKG" reset > /dev/null 2>&1
  local i
  for i in 1 2 3 4 5 6 7 8; do
    swipe_left 220; sleep 0.5
    swipe_right 220; sleep 0.5
  done
  sleep 1
  adbs dumpsys gfxinfo "$PKG" | grep -E 'Total frames rendered|Janky frames|50th percentile|90th percentile|95th percentile|99th percentile|Number Missed Vsync|Number High input latency|Number Slow UI thread|Number Slow issue draw commands' > "$OUT/gfx_$lbl.txt"
  say "--- gfxinfo $lbl ---"
  tee -a "$OUT/log.txt" < "$OUT/gfx_$lbl.txt"
  adbs dumpsys meminfo "$PKG" | grep -E 'TOTAL PSS|TOTAL:|Java Heap|Native Heap' | head -4 | sed "s/^/meminfo $lbl: /" | tee -a "$OUT/log.txt"
  alive || fail "proses mati saat uji kelancaran ($lbl)"
}
step "Perbandingan kelancaran geser: release, debug, release (emulator render perangkat lunak, hanya relatif)"
perf_run release1 "$REL_APK"
perf_run debug "$DBG_APK"
perf_run release2 "$REL_APK"
scan_log perf

COMPLETED=1
say ""
say "Selesai. gagal=${#FAILS[@]} peringatan=${#WARNS[@]} ok=${#OKS[@]}"
exit 0
