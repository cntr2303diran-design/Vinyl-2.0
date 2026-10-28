#!/usr/bin/env bash
# Uji asap APK di emulator (dijalankan oleh reactivecircus/android-emulator-runner di CI). BUKAN pengganti uji di
# perangkat nyata: emulator dirender perangkat lunak, jadi angka kecepatannya hanya berguna sebagai perbandingan relatif.
#
# Yang diperiksa (APK release, hasil R8):
#   - terpasang, tidak bisa di-debug, dijadikan launcher default (seperti pengguna sungguhan; tanpa itu Android
#     menampilkan pemilih "Select a Home app" yang menutupi semua yang diuji), terbuka lewat HomeActivity, dan
#     onboarding pertama-buka bisa diselesaikan
#   - beranda benar-benar tampil (tangkapan layar + simpul aksesibilitas), rotasi layar, geser halaman (Audio Console,
#     Perpustakaan kaset, kembali ke Ruang TV), TV dimatikan/dinyalakan, laci kaset, game Ular (usap di layar TV), game Bata
#     (geser palet; tombol deck berubah Mulai/Jeda; rekor tampil di label kaset), kaset film
#   - layar Pengaturan terbuka dan teksnya terbaca (tangkapan layar)
#   - proses dimatikan sistem saat di latar lalu launcher dibuka lagi lewat tombol Home: pengaturan HARUS bertahan
#     (onboarding tidak muncul lagi); ini menguji pembacaan/penulisan JSON kotlinx.serialization + DataStore setelah R8
#   - paket dipasang/dihapus saat launcher berjalan (receiver perubahan paket)
#   - profil dasar (Baseline Profile) terpasang oleh ProfileInstaller dan bisa dikompilasi
#   - logcat bebas dari crash, ANR, dan tanda R8 rusak (NoSuchMethodError, SerializationException, dst.)
# Lalu perbandingan singkat kelancaran geser release vs debug di emulator yang sama (relatif saja, tidak menentukan lulus).
#
# Emulator baru booting sangat sibuk (aplikasi Google menyiapkan diri); ANR pada saat itu bukan kesalahan aplikasi.
# Karena itu skrip menunggu CPU emulator tenang dulu, dan sebuah ANR hanya dihitung GAGAL bila terjadi saat emulator
# tenang (beban rata-rata <= 2x jumlah inti); selain itu hanya PERINGATAN lengkap dengan angka bebannya.
#
# Keluaran di <folder-keluaran>: result.txt (baris pertama PASS / FAIL: ... / INCONCLUSIVE: ...), log.txt, *.png,
# logcat_*.txt, ui_*.xml, gfx_*.txt. Skrip SELALU keluar dengan status 0; langkah CI berikutnya membaca result.txt.
# Langkah yang bergantung pada pencarian elemen layar (uiautomator) hanya memberi PERINGATAN bila elemen tak ditemukan;
# crash, ANR saat tenang, tanda R8 rusak, dan pengaturan yang hilang setelah proses dimatikan adalah GAGAL.
#
# Pemakaian: smoke_test.sh <release.apk> <debug.apk> <folder-keluaran>
set -u
REL_APK="${1:?apk release}"
DBG_APK="${2:?apk debug}"
OUT="${3:?folder keluaran}"
PKG=com.vinyl.launcher
HOME_ACT="$PKG/.ui.home.HomeActivity"
# Aktivitas di balik ikon "TV Launcher": setelah onboarding selesai ia meneruskan ke Pengaturan
ICON_ACT="$PKG/.ui.onboarding.OnboardingActivity"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
mkdir -p "$OUT"
: > "$OUT/log.txt"

FAILS=()
WARNS=()
OKS=()
COMPLETED=0
SETTLED=0
NPROC=2
ANR_SEEN=" "
W=1080
H=2400
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
  rm -f "$OUT/_tmp_log.txt"
}
trap write_result EXIT

# ---------- alat bantu ----------
adbs()  { adb shell "$@" 2>&1 | tr -d '\r'; }
alive() { [ -n "$(adbs pidof "$PKG")" ]; }
top_is() { adbs dumpsys activity activities | grep -m1 -E 'topResumedActivity=|ResumedActivity' | grep -q "$1"; }
top_is_home() { top_is "$PKG/.ui.home.HomeActivity"; }
top_desc() { adbs dumpsys activity activities | grep -m1 -E 'topResumedActivity=|ResumedActivity' | sed 's/^ *//'; }

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
# find_node <nama> <regex>: isi NX NY (titik tengah) dan NX1..NY2 (batas); status 0 bila ada, 1 bila tidak ada,
# 2 bila layar tidak bisa dibaca (uiautomator gagal)
find_node() {
  local res rc
  dump_ui "$1" || return 2
  res=$(python3 -I "$HERE/ui_find.py" "$OUT/ui_$1.xml" "$2")
  rc=$?
  [ "$rc" -ne 0 ] && return "$rc"
  read -r NX NY NX1 NY1 NX2 NY2 <<< "$res"
  return 0
}
tap_node() { # <nama> <regex>
  if find_node "$1" "$2"; then adb shell input tap "$NX" "$NY"; return 0; fi
  return 1
}
# wait_node <nama> <regex> <detik>: ulangi pencarian sampai simpul ada (status 0) atau waktu habis (status 1)
wait_node() {
  local t0=$SECONDS
  while :; do
    if find_node "$1" "$2"; then return 0; fi
    [ $((SECONDS - t0)) -ge "$3" ] && return 1
    sleep 3
  done
}
tap_wait() { # <nama> <regex> <detik>
  if wait_node "$1" "$2" "$3"; then adb shell input tap "$NX" "$NY"; return 0; fi
  return 1
}

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

# ---------- menunggu emulator tenang ----------
# Persentase CPU yang terpakai selama ~4 detik (selisih /proc/stat); 100 bila tak terbaca
cpu_busy_pct() {
  local a b r
  a=$(adbs "head -1 /proc/stat")
  sleep 4
  b=$(adbs "head -1 /proc/stat")
  r=$(awk -v a="$a" -v b="$b" 'BEGIN {
        n = split(a, x, " "); m = split(b, y, " ")
        ta = 0; tb = 0
        for (i = 2; i <= n; i++) ta += x[i]
        for (i = 2; i <= m; i++) tb += y[i]
        dt = tb - ta; di = (y[5] + y[6]) - (x[5] + x[6])
        if (dt <= 0) { print 100; exit }
        printf "%d\n", 100 * (dt - di) / dt
      }')
  case "$r" in ''|*[!0-9]*) r=100 ;; esac
  echo "$r"
}
# settle <batas detik>: tunggu sampai CPU emulator di bawah 30% tiga kali berturut-turut (status 0) atau waktu habis (1)
settle() {
  local limit=${1:-240} t0=$SECONDS calm=0 b
  while [ $((SECONDS - t0)) -lt "$limit" ]; do
    b=$(cpu_busy_pct)
    say "tunggu emulator tenang: CPU terpakai ${b}% (load $(adbs cat /proc/loadavg | cut -d' ' -f1))"
    if [ "$b" -lt 30 ]; then calm=$((calm + 1)); else calm=0; fi
    [ "$calm" -ge 3 ] && return 0
  done
  return 1
}
wait_boot() {
  local i
  adb wait-for-device
  for i in $(seq 1 90); do
    if [ "$(adbs getprop sys.boot_completed)" = "1" ] && [ "$(adbs getprop init.svc.bootanim)" != "running" ]; then return 0; fi
    sleep 2
  done
  return 1
}

# ---------- ANR, mati-nya proses, logcat ----------
# report_anrs <berkas logcat> <label>: tiap ANR paket ini dilaporkan sekali (ErrorId). GAGAL hanya bila emulator tenang.
ANR_PARSED=0   # jumlah ANR paket ini yang terbaca pada pemanggilan report_anrs terakhir (baru maupun yang sudah dilaporkan)
report_anrs() {
  local id load reason
  ANR_PARSED=0
  while IFS=$'\t' read -r id load reason; do
    [ -z "$id" ] && continue
    ANR_PARSED=$((ANR_PARSED + 1))
    case "$ANR_SEEN" in *" $id "*) continue ;; esac
    ANR_SEEN="$ANR_SEEN$id "
    load=${load:-?}
    if [ "$SETTLED" -ne 1 ]; then
      warn "ANR di $PKG ($2) saat emulator belum sempat tenang (load $load, $NPROC inti): $reason"
    elif awk -v l="$load" -v c="$NPROC" 'BEGIN { exit !(l + 0 > 2 * c) }'; then
      warn "ANR di $PKG ($2) saat emulator terbebani (load $load > 2x $NPROC inti): $reason"
    else
      fail "ANR di $PKG ($2) pada emulator yang tenang (load $load, $NPROC inti): $reason"
    fi
  done < <(python3 -I "$HERE/anr_scan.py" "$1" "$PKG")
}

# Proses launcher mati tiba-tiba: cari tahu sebabnya (crash = GAGAL, ANR = aturan ANR, selain itu GAGAL)
classify_death() { # <label>
  local f="$OUT/_tmp_log.txt"
  adb logcat -d -v threadtime > "$f" 2>&1
  if grep -A4 'FATAL EXCEPTION' "$f" | grep -q "Process: $PKG"; then
    fail "crash (FATAL EXCEPTION) mematikan proses: $1"
    grep -A14 'FATAL EXCEPTION' "$f" | head -40 | tee -a "$OUT/log.txt"
  elif grep -qE "ANR in $PKG" "$f"; then
    report_anrs "$f" "$1"
    if [ "$ANR_PARSED" -eq 0 ]; then
      fail "proses mati dengan jejak ANR yang laporannya tidak terbaca: $1"
    else
      say "proses dimatikan sistem setelah ANR ($1); penilaiannya ada pada baris ANR di atas"
    fi
  else
    fail "proses mati tanpa crash atau ANR yang terlihat: $1 — $(grep -E "lowmemorykiller|lmkd|am_kill|has died" "$f" | grep "$PKG" | head -2 | tr '\n' ' ')"
  fi
  rm -f "$f"
}

# Buka launcher lagi supaya langkah berikutnya tidak ikut gagal karena proses yang sudah mati
recover() { # <label>
  say "memulihkan: membuka launcher lagi ($1)"
  adb shell input keyevent KEYCODE_WAKEUP
  launch_home "pulih_$1" > /dev/null
  sleep 6
}

check_alive() { # <label>
  sleep 1
  if alive; then ok "proses hidup: $1"; return 0; fi
  say "proses tidak ada: $1"
  classify_death "$1"
  recover "$1"
  return 1
}

scan_log() { # <label>: baca logcat sejauh ini
  local f="$OUT/logcat_$1.txt"
  adb logcat -d -v threadtime > "$f" 2>&1
  if grep -A4 'FATAL EXCEPTION' "$f" | grep -q "Process: $PKG"; then
    fail "crash (FATAL EXCEPTION) di $PKG — $1"
    grep -A14 'FATAL EXCEPTION' "$f" | head -50 | tee -a "$OUT/log.txt"
  fi
  report_anrs "$f" "$1"
  local sig='NoSuchMethodError|NoSuchFieldError|AbstractMethodError|IncompatibleClassChangeError|VerifyError|SerializationException|MissingFieldException'
  if grep -E "$sig" "$f" | grep -q .; then
    fail "tanda R8 rusak di logcat ($1): $(grep -E "$sig" "$f" | head -3 | tr '\n' ' ')"
  fi
  # Kosongkan supaya pemeriksaan berikutnya hanya membaca bagian baru (berkas lama tetap tersimpan)
  adb logcat -c
}

# ---------- pemasangan dan peluncuran ----------
# Jadikan launcher ini default (seperti pengguna yang menekan "Atur" di onboarding). Tanpa ini, begitu ada dua launcher
# Android menampilkan pemilih "Select a Home app" yang menutupi layar dan membuat semua langkah berikutnya tak bermakna.
set_default_home() { # <label>
  local o r
  o=$(adbs cmd package set-home-activity "$HOME_ACT")
  say "set-home-activity ($1): ${o:-tanpa keluaran}"
  r=$(adbs cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1)
  if ! echo "$r" | grep -q "^$PKG/"; then
    adbs cmd role add-role-holder --user 0 android.app.role.HOME "$PKG" | tee -a "$OUT/log.txt"
    sleep 2
    r=$(adbs cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1)
  fi
  say "launcher default sekarang ($1): $r"
  if echo "$r" | grep -q "^$PKG/"; then return 0; fi
  warn "launcher default bukan $PKG ($1): $r"
  return 1
}

install_apk() { # <label> <apk>: pasang bersih (data lama dibuang supaya onboarding muncul lagi), lalu jadikan default
  local o
  adb uninstall "$PKG" > /dev/null 2>&1
  o=$(adb install -r -t "$2" 2>&1)
  echo "$o" | tail -2 | tee -a "$OUT/log.txt"
  echo "$o" | grep -q 'Success' || return 1
  set_default_home "$1"
  return 0
}

launch_home() { # <label>
  local o
  o=$(adbs am start -W -n "$HOME_ACT")
  say "am start ($1): $(echo "$o" | grep -E 'Status|LaunchState|TotalTime|WaitTime|Error' | tr '\n' ' ')"
  if ! echo "$o" | grep -q 'Status: ok'; then fail "am start gagal ($1): $(echo "$o" | head -3 | tr '\n' ' ')"; return 1; fi
  return 0
}

# Pastikan launcher kita yang di depan (bukan pemilih, dialog, atau aplikasi lain) sebelum langkah yang mengetuk layar
ensure_front() { # <label>
  if top_is_home; then return 0; fi
  say "layar depan bukan HomeActivity ($1): $(top_desc)"
  adb shell input keyevent KEYCODE_BACK
  sleep 1
  launch_home "depan_$1" > /dev/null
  sleep 4
  top_is_home
}

# expect_node <nama> <regex> <pesan bila ada> <pesan bila tidak ada>: OK bila simpul ada, PERINGATAN bila tidak ada atau layar tak terbaca
expect_node() {
  find_node "$1" "$2"
  case $? in
    0) ok "$3"; return 0 ;;
    1) warn "$4"; return 1 ;;
    *) warn "layar tidak terbaca saat memeriksa: $3"; return 1 ;;
  esac
}

# tap_tray <nama> <regex>: kaset di baki adalah daftar mendatar yang bisa melebihi lebar layar (kaset di luar layar tidak
# ada di pohon aksesibilitas). Cari; bila belum tampak, geser baki ke kiri (pada tinggi kartu yang sedang tampak) dan cari lagi.
tap_tray() {
  local i
  for i in 1 2 3 4 5 6 7; do
    if tap_node "$1_$i" "$2"; then return 0; fi
    if find_node "$1_baris" '^Kaset [^:]+, '; then
      adb shell input swipe $((W * 85 / 100)) "$NY" $((W * 25 / 100)) "$NY" 300
    fi
    sleep 1.5
  done
  return 1
}

# Urutan halaman beranda: Perpustakaan kaset <- Ruang TV -> Audio Console. Langkah yang mengetuk benda di meja (tombol daya,
# laci, kaset) harus mulai dari Ruang TV. Perpustakaan dan Audio Console sama-sama punya tombol "Kembali ke ruang TV";
# bila layar yang tampil bukan salah satu halaman beranda (dialog, aplikasi lain), launcher dibawa ke depan lebih dulu.
goto_room() { # <label>
  local i
  for i in 1 2 3 4; do
    # Laci yang masih terbuka menutupi meja dan menahan sentuhan ke tombol di belakangnya: tutup dulu
    if tap_node tutup_laci '^Tutup laci$'; then sleep 2; fi
    if find_node ruang '^Tombol daya TV$'; then return 0; fi
    if tap_node kembali '^Kembali ke ruang TV$'; then
      sleep 2
    else
      ensure_front "ruang_$1" > /dev/null
      sleep 2
    fi
  done
  if find_node ruang '^Tombol daya TV$'; then return 0; fi
  warn "Ruang TV tidak tampil ($1); layar depan: $(top_desc)"
  return 1
}

# Onboarding pertama-buka: ketuk "Mulai" (menulis onboardingDone ke DataStore)
first_run() { # <label>
  local i
  sleep 5
  for i in 1 2 3 4 5 6; do
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
    sleep 3
  done
  warn "onboarding tidak muncul pada pemasangan baru ($1); layar depan: $(top_desc)"
  return 1
}

rotate() { # 0 = tegak, 1 = mendatar
  adb shell settings put system accelerometer_rotation 0
  adb shell settings put system user_rotation "$1"
  sleep 3
}

# ---------- persiapan emulator ----------
step "Persiapan"
wait_boot || warn "emulator belum menandai boot selesai; uji dilanjutkan"
adbs getprop ro.build.version.sdk | sed 's/^/API: /' | tee -a "$OUT/log.txt"
adbs getprop ro.product.model | sed 's/^/model: /' | tee -a "$OUT/log.txt"
SIZE=$(adbs wm size | grep -oE '[0-9]+x[0-9]+' | tail -1)
if [ -n "$SIZE" ]; then W=${SIZE%x*}; H=${SIZE#*x}; fi
NPROC=$(adbs nproc | tr -dc '0-9'); NPROC=${NPROC:-2}
say "layar: ${W}x${H}, inti CPU: $NPROC"
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard > /dev/null 2>&1
adb shell input keyevent 82
adb shell settings put global hide_error_dialogs 1 > /dev/null 2>&1
adb shell settings put global stay_on_while_plugged_in 3 > /dev/null 2>&1
adb logcat -G 16M > /dev/null 2>&1
adb logcat -c

# ---------- 1. pasang dan buka ----------
step "Pasang APK release dan jadikan launcher default"
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

step "Tunggu emulator tenang (sesudah booting, aplikasi Google menyiapkan diri dan memakai hampir semua CPU)"
if settle 270; then
  SETTLED=1
  ok "emulator tenang sebelum aplikasi dibuka"
else
  SETTLED=0
  warn "emulator tidak sempat tenang dalam 270 detik; ANR sesudahnya hanya dihitung sebagai peringatan"
fi

step "Buka aplikasi (onboarding pertama-buka)"
launch_home release || exit 0
shot "pertama_buka"
first_run release
if wait_node beranda_siap '^Tombol daya TV$' 45; then
  ok "beranda tampil dan terbaca (Tombol daya TV ada)"
else
  warn "beranda belum menampilkan 'Tombol daya TV' dalam 45 detik; layar depan: $(top_desc)"
fi
shot "beranda"
if top_is_home; then ok "beranda (HomeActivity) tampil"; else warn "beranda tidak terdeteksi di depan: $(top_desc)"; fi
check_alive "setelah dibuka"
scan_log buka

# ---------- 2. profil dasar ----------
step "Profil dasar (Baseline Profile)"
sleep 4
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
ensure_front rotasi > /dev/null
rotate 1
shot "mendatar"
check_alive "setelah diputar mendatar"
rotate 0
shot "tegak_lagi"
check_alive "setelah diputar tegak"

# ---------- 4. geser halaman ----------
step "Geser halaman (Perpustakaan kaset <- Ruang TV -> Audio Console)"
ensure_front geser > /dev/null
goto_room geser_awal
swipe_left; sleep 1.5; shot "halaman_konsol"
expect_node halaman_konsol '^AUDIO CONSOLE$' "geser ke kiri membuka Audio Console" "geser ke kiri dari Ruang TV tidak menampilkan Audio Console"
swipe_right; sleep 1.5
expect_node halaman_ruang1 '^Tombol daya TV$' "geser ke kanan dari Audio Console kembali ke Ruang TV" "geser ke kanan dari Audio Console tidak kembali ke Ruang TV"
swipe_right; sleep 1.5; shot "halaman_perpustakaan"
expect_node halaman_perpus '^Perpustakaan kaset$' "geser ke kanan membuka Perpustakaan kaset" "geser ke kanan dari Ruang TV tidak menampilkan Perpustakaan kaset"
swipe_left; sleep 1.5
expect_node halaman_ruang2 '^Tombol daya TV$' "geser ke kiri dari Perpustakaan kembali ke Ruang TV" "geser ke kiri dari Perpustakaan tidak kembali ke Ruang TV"
goto_room geser_akhir
check_alive "setelah geser halaman"

# ---------- 5. TV, laci, game ----------
step "Tombol daya TV"
ensure_front tv > /dev/null
goto_room tv
if tap_wait tv_daya '^Tombol daya TV$' 20; then
  sleep 1.5; shot "tv_mati"
  expect_node tv_mati_cek '^TV mati$' "TV mati (semantik 'TV mati' ada)" "semantik 'TV mati' tidak ditemukan setelah tombol daya"
  tap_node tv_daya2 '^Tombol daya TV$' > /dev/null
  sleep 1.5; shot "tv_nyala_lagi"
  find_node tv_nyala_cek '^TV mati$'
  case $? in
    1) ok "TV menyala lagi (semantik 'TV mati' hilang)" ;;
    0) warn "TV masih mati setelah tombol daya diketuk lagi" ;;
    *) warn "layar tidak terbaca saat memeriksa TV menyala lagi" ;;
  esac
else
  warn "tombol daya TV tidak ditemukan; layar depan: $(top_desc)"
fi
check_alive "setelah tombol daya"

# Satu kaset game: laci -> ketuk kaset -> TV menampilkan game -> main sebentar -> keluarkan kaset lagi.
#   <kunci> penanda nama berkas, <judul> nama di label kaset (Ular / Bata), <gaya> usap = Ular, palet = Bata
play_game() {
  local key=$1 title=$2 style=$3 d
  ensure_front "game_$key" > /dev/null
  goto_room "game_$key" || return 1
  if ! tap_wait "laci_$key" '^Laci kaset' 20; then
    warn "kontrol laci kaset tidak ditemukan ($title); layar depan: $(top_desc)"
    return 1
  fi
  sleep 1.5; shot "laci_${key}_terbuka"
  if ! tap_tray "kaset_$key" "^Kaset $title, "; then
    warn "kaset $title tidak ditemukan di laci"
    return 1
  fi
  ok "kaset $title ada di laci dan diketuk"
  sleep 3.5; shot "game_${key}_siap"
  if find_node "tv_$key" '^TV menampilkan game'; then
    ok "TV menampilkan game $title"
    GX=$NX; GY=$NY; GW=$((NX2 - NX1)); GH=$((NY2 - NY1))
    if [ "$style" = "palet" ]; then
      expect_node "deck_${key}_siap" '^Mulai game$' "tombol deck $title menawarkan 'Mulai game' sebelum dimainkan" "tombol deck 'Mulai game' tidak ada pada papan $title yang baru"
      adb shell input tap "$GX" "$GY"; sleep 1.5
      expect_node "deck_${key}_jalan" '^Jeda game$' "$title berjalan: tombol deck berubah menjadi 'Jeda game'" "tombol deck tidak berubah menjadi 'Jeda game' setelah layar TV $title diketuk"
      # Geser jari bolak-balik di layar TV: palet mengikuti, bola memantul dan memecahkan bata
      for d in 1 2 3 4 5 6; do
        adb shell input swipe $((GX - GW / 4)) "$GY" $((GX + GW / 4)) "$GY" 200; sleep 0.5
        adb shell input swipe $((GX + GW / 4)) "$GY" $((GX - GW / 4)) "$GY" 200; sleep 0.5
      done
      shot "game_${key}_jalan"
      # Jeda lewat tombol deck (menguji aksi deck sekaligus menyimpan rekor)
      if tap_node "deck_${key}_jeda" '^Jeda game$'; then
        sleep 2; shot "game_${key}_jeda"
        expect_node "deck_${key}_jeda_cek" '^Mulai game$' "$title dijeda lewat tombol deck" "tombol deck tidak berubah menjadi 'Mulai game' setelah dijeda"
        # Bola yang sempat memecahkan bata menaikkan rekor; rekor tampil di label kaset di deck ("Kaset: BATA, Game · rekor N")
        if find_node "rekor_$key" "^Kaset: .*rekor [0-9]+"; then ok "rekor $title tersimpan dan tampil di label kaset"; else warn "rekor $title tidak tampil di label kaset (belum ada bata yang pecah, atau rekor tidak tersimpan)"; fi
      else
        warn "tombol 'Jeda game' tidak ditemukan; permainan mungkin sudah berakhir atau dijeda sistem"
        shot "game_${key}_tanpa_jeda"
      fi
    else
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
      shot "game_${key}_jalan"
      adb shell input tap "$GX" "$GY"; sleep 0.8; shot "game_${key}_jeda"
    fi
  else
    warn "TV tidak menampilkan game setelah kaset $title diketuk"
  fi
  check_alive "setelah main $title"
  # keluarkan kaset game: buka laci lagi, ketuk kaset yang sama
  ensure_front "keluar_$key" > /dev/null
  if tap_wait "laci_${key}_2" '^Laci kaset' 15; then
    sleep 1.5
    if tap_tray "kaset_${key}_2" "^Kaset $title, "; then
      sleep 3.5; shot "kaset_${key}_dikeluarkan"
      find_node "tv_${key}_keluar" '^TV menampilkan game'
      case $? in
        0) warn "TV masih menampilkan game setelah kaset $title dikeluarkan" ;;
        1) ok "kaset $title keluar dari deck" ;;
        *) warn "layar tidak terbaca saat memeriksa kaset $title dikeluarkan" ;;
      esac
    else
      warn "kaset $title tidak ditemukan saat mengeluarkan"
    fi
  else
    warn "kontrol laci kaset tidak ditemukan saat mengeluarkan kaset $title"
  fi
  check_alive "setelah kaset $title dikeluarkan"
  return 0
}

step "Laci kaset dan game Ular"
play_game ular Ular usap

step "Game Bata (palet digeser dengan jari, bola memecahkan bata, rekor tampil di label kaset)"
play_game bata Bata palet

step "Kaset film (WebView, jaringan emulator bisa saja tidak ada)"
ensure_front film > /dev/null
goto_room film
FILM_RX='^Kaset (?!(Ular|Bata), )[^:]+, '
if tap_wait laci3 '^Laci kaset' 15; then
  sleep 1.5
  if tap_tray kaset_film "$FILM_RX"; then
    ok "kaset film diketuk"
    sleep 12; shot "film_dimuat"
    check_alive "setelah kaset film dimuat"
    rotate 1; sleep 2; shot "film_mendatar"
    check_alive "film saat diputar mendatar"
    rotate 0; sleep 2
    if tap_wait laci4 '^Laci kaset' 15 && sleep 1.5 && tap_tray kaset_film2 "$FILM_RX"; then
      sleep 3; shot "film_dikeluarkan"
    else
      warn "kaset film tidak ditemukan untuk dikeluarkan"
    fi
  else
    warn "kaset film tidak ditemukan di laci"
  fi
else
  warn "kontrol laci kaset tidak ditemukan (film); layar depan: $(top_desc)"
fi
check_alive "setelah kaset film"
scan_log fungsi

# ---------- 6. Pengaturan ----------
step "Pengaturan (teks harus terbaca; ikon aplikasi meneruskan ke Pengaturan sesudah onboarding)"
icon_out=$(adbs am start -W -n "$ICON_ACT")
say "am start (ikon aplikasi): $(echo "$icon_out" | grep -E 'Status|Error' | tr '\n' ' ')"
sleep 5
if top_is SettingsActivity; then
  ok "ikon aplikasi membuka Pengaturan"
  shot "pengaturan"
  if tap_wait set_izin '^Izin' 15; then
    sleep 2; shot "pengaturan_izin"
    adb shell input keyevent KEYCODE_BACK; sleep 1.5
  else
    warn "baris 'Izin & status' tidak ditemukan di Pengaturan"
  fi
  if tap_wait set_laci '^Laci kaset' 10; then
    sleep 2; shot "pengaturan_laci_kaset"
    adb shell input keyevent KEYCODE_BACK; sleep 1.5
  else
    warn "baris 'Laci kaset' tidak ditemukan di Pengaturan"
  fi
  adb shell input keyevent KEYCODE_BACK; sleep 1.5
else
  warn "Pengaturan tidak terbuka dari ikon aplikasi; layar depan: $(top_desc)"
  shot "pengaturan_gagal"
fi
check_alive "setelah Pengaturan"
scan_log pengaturan

# ---------- 6b. Edit beranda ----------
step "Edit beranda (tekan lama di layar utama; teksnya harus terbaca)"
ensure_front editor > /dev/null
goto_room editor
editor_open=0
for pt in "$((W / 2)) $((H * 22 / 100))" "$((W * 8 / 100)) $((H / 2))" "$((W / 2)) $((H * 62 / 100))"; do
  read -r px py <<< "$pt"
  adb shell input swipe "$px" "$py" "$px" "$py" 1100
  if wait_node editor_judul '^Edit beranda$' 8; then editor_open=1; break; fi
done
if [ "$editor_open" -eq 1 ]; then
  ok "editor beranda terbuka lewat tekan lama"
  sleep 1; shot "editor_beranda"
  if ! tap_node editor_batal '^Batal$'; then adb shell input keyevent KEYCODE_BACK; fi
  sleep 2
else
  warn "editor beranda tidak terbuka lewat tekan lama (titik yang dicoba tidak kosong?)"
fi
check_alive "setelah editor beranda"

# ---------- 7. proses dimatikan lalu dibuka lagi ----------
step "Proses dimatikan sistem saat di latar, lalu launcher dibuka lagi (pengaturan harus bertahan)"
# am kill hanya mematikan proses yang ada di latar: pindahkan launcher ke latar dengan membuka aplikasi lain
adb shell am start -n com.android.settings/.Settings > /dev/null 2>&1
sleep 5
if top_is_home; then warn "launcher masih di depan setelah membuka aplikasi lain; am kill mungkin tidak berlaku"; fi
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
  # Tombol Home: sistem membuka launcher default dari nol, seperti saat pengguna kembali ke beranda
  adb shell input keyevent KEYCODE_HOME
  for i in 1 2 3 4 5 6 7 8; do
    sleep 3
    if top_is Onboarding || top_is_home; then break; fi
  done
  if ! top_is Onboarding && ! top_is_home; then
    say "tombol Home tidak membuka launcher ($(top_desc)); membuka lewat am start"
    launch_home setelah_kill > /dev/null
    sleep 6
  fi
  shot "setelah_proses_dimatikan"
  if top_is Onboarding; then
    fail "onboarding muncul lagi setelah proses dimatikan: pengaturan tidak bertahan (DataStore/JSON)"
  elif top_is_home; then
    ok "beranda terbuka tanpa onboarding: pengaturan bertahan setelah proses dimatikan"
  else
    warn "layar depan tidak dikenali setelah kill: $(top_desc)"
  fi
  check_alive "setelah dibuka kembali"
fi
scan_log kill

# ---------- 8. paket dipasang/dihapus saat launcher berjalan ----------
step "Aplikasi dihapus dan dipasang kembali saat launcher berjalan"
ensure_front paket > /dev/null
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
adbs dumpsys meminfo "$PKG" | grep -E 'TOTAL PSS|TOTAL:|Java Heap|Native Heap' | head -4 | sed 's/^/meminfo release: /' | tee -a "$OUT/log.txt"
scan_log akhir_fungsi

# ---------- 9. kompilasi profil dasar ----------
step "Kompilasi dengan profil (speed-profile)"
adbs cmd package compile -f -m speed-profile "$PKG" | tee -a "$OUT/log.txt"
adbs dumpsys package dexopt | grep -A2 "\[$PKG\]" | head -6 | tee -a "$OUT/log.txt"

# ---------- 10. perbandingan kelancaran geser (relatif, tidak menentukan lulus) ----------
perf_run() { # <label> <apk>
  local lbl=$1
  if ! install_apk "$lbl" "$2"; then warn "perbandingan: pemasangan $lbl gagal"; return 1; fi
  settle 45 > /dev/null
  adb logcat -c
  launch_home "perf_$lbl" || return 1
  first_run "perf_$lbl" > /dev/null
  sleep 12
  adb shell dumpsys gfxinfo "$PKG" reset > /dev/null 2>&1
  local i
  for i in 1 2 3 4 5 6; do
    swipe_left 220; sleep 0.5
    swipe_right 220; sleep 0.5
  done
  sleep 1
  adbs dumpsys gfxinfo "$PKG" | grep -E 'Total frames rendered|Janky frames|50th percentile|90th percentile|95th percentile|99th percentile|Number Missed Vsync|Number High input latency|Number Slow UI thread|Number Slow issue draw commands' > "$OUT/gfx_$lbl.txt"
  say "--- gfxinfo $lbl ---"
  tee -a "$OUT/log.txt" < "$OUT/gfx_$lbl.txt"
  adbs dumpsys meminfo "$PKG" | grep -E 'TOTAL PSS|TOTAL:|Java Heap|Native Heap' | head -4 | sed "s/^/meminfo $lbl: /" | tee -a "$OUT/log.txt"
  check_alive "uji kelancaran ($lbl)"
}
step "Perbandingan kelancaran geser: release lalu debug (emulator render perangkat lunak, hanya relatif)"
perf_run release "$REL_APK"
perf_run debug "$DBG_APK"
scan_log perf

COMPLETED=1
say ""
say "Selesai. gagal=${#FAILS[@]} peringatan=${#WARNS[@]} ok=${#OKS[@]}"
exit 0
