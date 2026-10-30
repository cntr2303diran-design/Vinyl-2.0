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
#   - layar Pengaturan terbuka dan teksnya terbaca (tangkapan layar); editor ambang piala terbuka
#   - buku harian (Keystore sungguhan di emulator): perekaman dinyalakan, ronde Ular/Bata dan kaset film dicatat, catatannya
#     masih terbaca sesudah proses dimatikan, tanpa alamat situs, dan di disk (retro_diary.xml, dibaca lewat root) hanya ada
#     nilai tersegel tanpa teks polos
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
# Dialog sistem "<aplikasi lain> isn't responding" (mis. Pixel Launcher yang kehabisan CPU saat booting) menutupi layar dan
# membuat onboarding tak bisa diselesaikan; dialog itu ditutup seperti oleh pengguna (dump_ui / ui_dialog.py). Pemeriksaan
# "pengaturan bertahan setelah proses dimatikan" hanya sah bila onboarding sungguh selesai; bila tidak, hasilnya GAGAL
# "tidak terverifikasi", bukan hijau.
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
# 1 bila ketukan "Mulai" di onboarding sungguh terjadi DAN beranda terbaca sesudahnya: hanya dengan begitu onboardingDone
# pernah ditulis, sehingga "pengaturan bertahan setelah proses dimatikan" bisa dinilai
FIRST_TAP=0
ONBOARDED=0
# 1 bila saklar perekaman buku harian benar-benar menyala (langkah "Benda ruangan"): hanya dengan begitu catatan game dan film
# bisa diperiksa di langkah 7b
DIARY_ON=0
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

# Dialog sistem "<aplikasi> isn't responding" / "keeps stopping" menutupi seluruh layar dan membuat pencarian simpul mana pun
# gagal. Di CI hal ini terjadi pada aplikasi LAIN (Pixel Launcher bawaan emulator) selagi emulator baru booting dan CPU-nya
# penuh; onboarding aplikasi ini lalu tak pernah bisa diselesaikan (terbukti di run CI 40707300168). Pengguna sungguhan akan
# menutup dialog itu, jadi skrip pun begitu ("Wait" untuk ANR, "Close app" untuk crash; lihat ui_dialog.py). Ini TIDAK
# menutupi masalah aplikasi ini: ANR/crash paket ini tetap dinilai dari logcat (report_anrs, scan_log), dan dialog yang
# judulnya memuat nama aplikasi ini dicatat sebagai PERINGATAN.
APP_LABEL="TV Launcher"
DIALOGS_CLOSED=0
# dismiss_error_dialog <berkas ui xml>: status 0 bila layar itu dialog galat sistem dan tombolnya sudah diketuk, 1 bila bukan
dismiss_error_dialog() {
  local res title btn cx cy
  res=$(python3 -I "$HERE/ui_dialog.py" "$1") || return 1
  IFS=$'\t' read -r title btn cx cy <<< "$res"
  DIALOGS_CLOSED=$((DIALOGS_CLOSED + 1))
  case "$title" in
    "$APP_LABEL"*) warn "dialog sistem milik aplikasi ini: \"$title\"; tombol \"$btn\" diketuk (ANR/crash-nya dinilai dari logcat)" ;;
    *) say "dialog sistem menutupi layar: \"$title\" (aplikasi lain, bukan yang diuji); tombol \"$btn\" diketuk" ;;
  esac
  adb shell input tap "$cx" "$cy"
  return 0
}

# uiautomator kadang gagal ("could not get idle state") pada layar yang terus beranimasi, maka dicoba beberapa kali.
# Dialog galat sistem yang menutupi layar ditutup dulu (paling banyak 4 kali per pembacaan), lalu layar dibaca ulang.
dump_ui() {
  local f="$OUT/ui_$1.xml" tries=0 dialogs=0
  while [ "$tries" -lt 3 ]; do
    tries=$((tries + 1))
    if timeout 45 adb shell uiautomator dump /sdcard/ui_dump.xml 2>&1 | grep -q 'dumped to'; then
      if adb pull /sdcard/ui_dump.xml "$f" > /dev/null 2>&1 && [ -s "$f" ]; then
        if [ "$dialogs" -lt 4 ] && dismiss_error_dialog "$f"; then
          dialogs=$((dialogs + 1)); tries=$((tries - 1)); sleep 2
          continue
        fi
        return 0
      fi
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

# scroll_tap_n <nama> <regex> <kali>: seperti scroll_tap, dengan jumlah gulir sendiri (halaman yang panjang)
scroll_tap_n() {
  local i
  for ((i = 1; i <= $3; i++)); do
    if tap_node "$1_$i" "$2"; then return 0; fi
    swipe_up
    sleep 1
  done
  return 1
}

# node_checked <nama> <regex>: atribut "checked" sakelar yang labelnya cocok di ui_<nama>.xml, yang baru dibuat oleh find_node
# ("true" / "false"; kosong bila tak terbaca). Tidak membaca layar sendiri, jadi aman dipanggil di dalam $( ).
# --baris: di pohon aksesibilitas, baris sakelar di editor adalah simpul checkable tanpa teks dan labelnya simpul TextView
# lain di dalam batasnya (terbukti di dump emulator), jadi nilainya dibaca dari baris itu, bukan dari simpul berlabel.
node_checked() { python3 -I "$HERE/ui_attr.py" "$OUT/ui_$1.xml" "$2" checked --baris 2>/dev/null; }

# open_editor <nama>: tekan lama di titik kosong layar utama sampai editor "Edit beranda" terbuka (status 0; 1 bila tidak)
open_editor() {
  local pt px py
  for pt in "$((W / 2)) $((H * 22 / 100))" "$((W * 8 / 100)) $((H / 2))" "$((W / 2)) $((H * 62 / 100))"; do
    read -r px py <<< "$pt"
    adb shell input swipe "$px" "$py" "$px" "$py" 1100
    if wait_node "$1" '^Edit beranda$' 8; then return 0; fi
  done
  return 1
}
# close_editor <nama>: tutup editor lewat "Batal" (tombol BACK bila "Batal" tidak ditemukan). Pengaturan gaya TV di editor
# langsung tersimpan, jadi menutup lewat "Batal" tidak membatalkannya.
close_editor() {
  if ! tap_node "$1" '^Batal$'; then adb shell input keyevent KEYCODE_BACK; fi
  sleep 2
}
# sheet_scroll_tap <nama> <regex>: cari di lembar editor; bila belum tampak, gulir lembar sedikit demi sedikit lalu cari lagi.
# Usapannya pendek dan pelan (lembar hanya setinggi separuh layar dan lontaran dari usapan cepat bisa melompati baris yang dicari).
sheet_scroll_tap() {
  local i
  for i in 1 2 3 4 5 6 7 8; do
    if tap_node "$1_$i" "$2"; then return 0; fi
    adb shell input swipe $((W * 92 / 100)) $((H * 90 / 100)) $((W * 92 / 100)) $((H * 74 / 100)) 700
    sleep 1.2
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
# Jejak tumpukan semua thread saat ANR (ditulis sistem ke /data/anr; perlu root, emulator google_apis mengizinkannya):
# memperlihatkan apa yang dikerjakan thread utama selama macet. Disalin (sekali) ke folder keluaran supaya ikut ke branch log.
ANR_TRACES_SAVED=0
save_anr_traces() {
  [ "$ANR_TRACES_SAVED" -eq 1 ] && return 0
  ANR_TRACES_SAVED=1
  adb root > /dev/null 2>&1
  adb wait-for-device
  mkdir -p "$OUT/anr"
  local f
  for f in $(adbs "ls /data/anr 2>/dev/null"); do
    case "$f" in
      anr_*) adb pull "/data/anr/$f" "$OUT/anr/$f" > /dev/null 2>&1 && gzip -9 "$OUT/anr/$f" 2>/dev/null ;;
    esac
  done
  say "jejak ANR disalin: $(ls "$OUT/anr" 2>/dev/null | tr '\n' ' ')"
}

# report_anrs <berkas logcat> <label>: tiap ANR paket ini dilaporkan sekali (ErrorId). GAGAL hanya bila emulator tenang.
ANR_PARSED=0   # jumlah ANR paket ini yang terbaca pada pemanggilan report_anrs terakhir (baru maupun yang sudah dilaporkan)
report_anrs() {
  local id load reason new_anr=0
  ANR_PARSED=0
  while IFS=$'\t' read -r id load reason; do
    [ -z "$id" ] && continue
    ANR_PARSED=$((ANR_PARSED + 1))
    case "$ANR_SEEN" in *" $id "*) continue ;; esac
    ANR_SEEN="$ANR_SEEN$id "
    new_anr=1
    load=${load:-?}
    if [ "$SETTLED" -ne 1 ]; then
      warn "ANR di $PKG ($2) saat emulator belum sempat tenang (load $load, $NPROC inti): $reason"
    elif awk -v l="$load" -v c="$NPROC" 'BEGIN { exit !(l + 0 > 2 * c) }'; then
      warn "ANR di $PKG ($2) saat emulator terbebani (load $load > 2x $NPROC inti): $reason"
    else
      fail "ANR di $PKG ($2) pada emulator yang tenang (load $load, $NPROC inti): $reason"
    fi
  done < <(python3 -I "$HERE/anr_scan.py" "$1" "$PKG")
  # Sesudah loop (bukan di dalamnya): perintah adb membaca stdin dan akan menelan sisa baris ANR yang belum dibaca
  if [ "$new_anr" -eq 1 ]; then save_anr_traces < /dev/null; fi
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
if first_run release; then FIRST_TAP=1; fi
if wait_node beranda_siap '^Tombol daya TV$' 45; then
  ok "beranda tampil dan terbaca (Tombol daya TV ada)"
  if [ "$FIRST_TAP" -eq 1 ]; then ONBOARDED=1; fi
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

# Lampu dinding: sakelar "Lampu ruangan" (ketuk = ruang terang/gelap). Status dibaca dari atribut checked simpulnya;
# yang diperiksa hanya bahwa ketukan MENGGANTI status dan ketukan kedua mengembalikannya (rupa lampu dinilai dari tangkapan layar).
step "Lampu ruangan (ketuk berganti terang/gelap, ketuk lagi mengembalikan)"
ensure_front lampu > /dev/null
goto_room lampu
if find_node lampu_0 '^Lampu ruangan$'; then
  lamp0=$(node_checked lampu_0 '^Lampu ruangan$')
  say "lampu ruangan: checked=${lamp0:-tak terbaca}"
  shot "lampu_awal"
  adb shell input tap "$NX" "$NY"
  sleep 2.5; shot "lampu_diketuk"
  if find_node lampu_1 '^Lampu ruangan$'; then
    lamp1=$(node_checked lampu_1 '^Lampu ruangan$')
    say "lampu ruangan sesudah diketuk: checked=${lamp1:-tak terbaca}"
    if [ -z "$lamp0" ] || [ -z "$lamp1" ]; then
      warn "status sakelar 'Lampu ruangan' tidak terbaca dari pohon aksesibilitas"
    elif [ "$lamp0" != "$lamp1" ]; then
      ok "lampu ruangan berganti setelah diketuk (checked $lamp0 -> $lamp1)"
    else
      warn "lampu ruangan tidak berganti setelah diketuk (checked tetap $lamp1)"
    fi
    adb shell input tap "$NX" "$NY"
    sleep 2.5; shot "lampu_kembali"
    if find_node lampu_2 '^Lampu ruangan$'; then
      lamp2=$(node_checked lampu_2 '^Lampu ruangan$')
      if [ -n "$lamp0" ] && [ "$lamp2" = "$lamp0" ]; then
        ok "ketukan kedua mengembalikan lampu ruangan (checked $lamp1 -> $lamp2)"
      else
        warn "lampu ruangan tidak kembali ke keadaan awal (awal=${lamp0:-tak terbaca}, sekarang=${lamp2:-tak terbaca})"
      fi
    else
      warn "lampu ruangan tidak ditemukan sesudah ketukan kedua"
    fi
  else
    warn "lampu ruangan hilang dari layar setelah diketuk"
  fi
else
  warn "lampu ruangan tidak ditemukan di beranda (lampu hanya ada di dinding album/foto dan bila ruangnya cukup); layar depan: $(top_desc)"
fi
check_alive "setelah lampu ruangan"

# Benda ruangan (jam dinding, poster, rak piala, buku harian, radio, telepon): muncul hanya bila ada tempat bersih di layar,
# jadi tidak adanya benda hanya PERINGATAN. Yang wajib: aplikasi tetap hidup setelah mengetuk yang ada, dan bukunya
# (dialog) bisa dibuka lalu ditutup.
step "Benda ruangan (jam, poster, rak piala, buku harian, radio, telepon)"
ensure_front benda > /dev/null
goto_room benda
found_props=0
for spec in 'jam:^Jam dinding$' 'poster:^Poster di dinding$' 'piala:^Rak piala$' 'buku:^Buku harian kaset$' 'radio:^Radio' 'telepon:^Telepon putar$'; do
  nm=${spec%%:*}; rx=${spec#*:}
  if find_node "benda_$nm" "$rx"; then found_props=$((found_props + 1)); say "benda ruangan terlihat: $nm"; else say "benda ruangan tidak terlihat (mungkin tidak muat di layar ini): $nm"; fi
done
shot "benda_ruangan"
if [ "$found_props" -eq 0 ]; then
  warn "tidak satu pun benda ruangan terlihat di beranda (layar kecil/tegak menyembunyikannya; fungsinya ada di Pengaturan → Benda ruangan); layar depan: $(top_desc)"
else
  ok "$found_props benda ruangan terlihat di beranda"
fi
if find_node benda_piala_buka '^Rak piala$'; then
  adb shell input tap "$NX" "$NY"; sleep 2; shot "benda_piala_dialog"
  if find_node benda_piala_judul '^Rak piala'; then ok "daftar piala terbuka"; else warn "daftar piala tidak tampak setelah rak diketuk"; fi
  tap_node benda_piala_tutup '^Tutup$' > /dev/null || adb shell input keyevent KEYCODE_BACK
  sleep 1
fi
if find_node benda_buku_buka '^Buku harian kaset$'; then
  adb shell input tap "$NX" "$NY"; sleep 2; shot "benda_buku_dialog"
  if find_node benda_buku_saklar '^Catat buku harian'; then
    ok "buku harian terbuka dengan saklar perekaman"
    # Saklar utama mati secara bawaan. Dinyalakan di sini supaya ronde Ular/Bata dan kaset film di langkah berikutnya tercatat;
    # isinya diperiksa di langkah 7b, sesudah proses dimatikan, dengan kunci Keystore sungguhan.
    diary_state=$(node_checked benda_buku_saklar '^Catat buku harian')
    if [ "$diary_state" = "true" ]; then
      DIARY_ON=1; say "perekaman buku harian sudah menyala"
    elif [ "$diary_state" = "false" ]; then
      adb shell input tap "$NX" "$NY"; sleep 2
      if find_node benda_buku_nyala '^Catat buku harian' && [ "$(node_checked benda_buku_nyala '^Catat buku harian')" = "true" ]; then
        DIARY_ON=1; ok "perekaman buku harian dinyalakan lewat saklar utama"
        shot "benda_buku_nyala"
        expect_node benda_buku_jenis '^Musik$' "saklar per jenis (Musik, Film, Game) muncul setelah perekaman menyala" "saklar per jenis tidak muncul setelah perekaman dinyalakan"
      else
        warn "saklar perekaman buku harian tidak berubah menjadi menyala setelah diketuk"
      fi
    else
      warn "keadaan saklar perekaman buku harian tidak terbaca"
    fi
  else
    warn "saklar perekaman tidak tampak di buku harian"
  fi
  tap_node benda_buku_tutup '^Tutup$' > /dev/null || adb shell input keyevent KEYCODE_BACK
  sleep 1
fi
check_alive "setelah benda ruangan"
scan_log benda_ruangan

# Satu kaset game: laci -> ketuk kaset -> TV menampilkan game -> main sebentar -> keluarkan kaset lagi.
#   <kunci> penanda nama berkas, <judul> nama di label kaset (Ular / Bata), <gaya> usap = Ular, palet = Bata
play_game() {
  local key=$1 title=$2 style=$3 d paused tries
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
      # Jeda lewat tombol deck (menguji aksi deck sekaligus menyimpan rekor). Selama usapan bola bisa saja lolos (nyawa
      # berkurang): papan lalu menunggu luncuran dan tombol deck berbunyi 'Mulai game'. Itu keadaan sah (terlihat di
      # tangkapan layar uji emulator), bukan galat: bola diluncurkan lagi lewat tombol yang sama, lalu dijeda.
      paused=0
      for tries in 1 2; do
        if tap_node "deck_${key}_jeda_$tries" '^Jeda game$'; then paused=1; break; fi
        if find_node "deck_${key}_menunggu_$tries" '^Mulai game$'; then
          ok "$title menunggu luncuran (bola lolos selama usapan); diluncurkan lagi lewat tombol deck"
          tap_node "deck_${key}_luncur_$tries" '^Mulai game$' > /dev/null; sleep 1.2
        fi
      done
      if [ "$paused" -eq 1 ]; then
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
  # Halaman "Benda ruangan" harus selalu terjangkau walau bendanya tidak muat di layar
  if scroll_tap set_benda '^Benda ruangan' ; then
    sleep 2; shot "pengaturan_benda_ruangan"
    if wait_node set_benda_telepon '^Telepon putar' 10; then ok "halaman Benda ruangan memuat saklar telepon putar"; else warn "saklar 'Telepon putar' tidak tampak di Benda ruangan"; fi
    # Ambang piala diatur sendiri (bagian di bawah halaman): tombol Ubah membuka isian perunggu/perak/emas
    if scroll_tap_n set_benda_ambang '^Ubah ambang piala Game Ular' 10; then
      sleep 2; shot "pengaturan_ambang_dialog"
      expect_node set_ambang_kolom 'Perunggu \(poin\)' "editor ambang piala terbuka dengan kolom perunggu, perak, dan emas" "editor ambang piala tidak tampak setelah tombol Ubah diketuk"
      tap_node set_ambang_batal '^Batal$' > /dev/null || adb shell input keyevent KEYCODE_BACK
      sleep 1.5
    else
      warn "tombol 'Ubah ambang piala Game Ular' tidak ditemukan di Benda ruangan"
    fi
    adb shell input keyevent KEYCODE_BACK; sleep 1.5
  else
    warn "baris 'Benda ruangan' tidak ditemukan di Pengaturan"
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
if open_editor editor_judul; then
  ok "editor beranda terbuka lewat tekan lama"
  sleep 1; shot "editor_beranda"
  close_editor editor_batal
else
  warn "editor beranda tidak terbuka lewat tekan lama (titik yang dicoba tidak kosong?)"
fi
check_alive "setelah editor beranda"

# ---------- 6c. Ingat tombol daya TV (disiapkan di sini, diperiksa sesudah proses dimatikan di langkah 7) ----------
# Pilihan opsional (bawaan mati): TV yang dimatikan dengan tombol dayanya tetap mati setelah restart. Pilihannya ada di
# editor, tab "TV & kaset" (tab pertama), di ujung kartu TV, jadi lembar editor perlu digulir.
RP_READY=0     # 1 bila pilihan menyala dan TV sudah dimatikan: langkah 7 memeriksa bahwa TV tetap mati sesudah kill
RP_CHANGED=0   # 1 bila skrip menyalakan pilihannya (harus dimatikan lagi sesudahnya)
step "Ingat tombol daya TV: nyalakan pilihannya di editor, lalu matikan TV"
ensure_front ingat > /dev/null
goto_room ingat
if open_editor ingat_editor; then
  if sheet_scroll_tap ingat_baris '^Ingat tombol daya TV'; then
    RP_CHANGED=1
    sleep 1.5
    if find_node ingat_cek '^Ingat tombol daya TV'; then
      rp_state=$(node_checked ingat_cek '^Ingat tombol daya TV')
      case "$rp_state" in
        true)  ok "pilihan 'Ingat tombol daya TV' menyala setelah diketuk (checked=true)" ;;
        false) warn "pilihan 'Ingat tombol daya TV' masih mati setelah diketuk (checked=false)" ;;
        *)     warn "status pilihan 'Ingat tombol daya TV' tidak terbaca dari pohon aksesibilitas" ;;
      esac
    else
      warn "baris 'Ingat tombol daya TV' hilang dari layar setelah diketuk"
    fi
    shot "ingat_pilihan"
  else
    warn "baris 'Ingat tombol daya TV' tidak ditemukan di editor (tab TV & kaset)"
    shot "ingat_tak_ditemukan"
  fi
  close_editor ingat_batal
  sleep 1
  if tap_wait ingat_daya '^Tombol daya TV$' 15; then
    sleep 2.5
    find_node ingat_mati '^TV mati$'
    case $? in
      0) RP_READY=1; ok "TV dimatikan dengan tombol daya sementara 'Ingat tombol daya TV' dinyalakan" ;;
      1) warn "TV tidak mati setelah tombol daya diketuk (uji 'Ingat tombol daya TV' dilewati)" ;;
      *) warn "layar tidak terbaca saat memeriksa TV mati (uji 'Ingat tombol daya TV' dilewati)" ;;
    esac
    shot "ingat_tv_mati"
    sleep 1.5   # beri waktu penulisan keadaan ke berkas kecil di latar sebelum proses dimatikan
  else
    warn "tombol daya TV tidak ditemukan (uji 'Ingat tombol daya TV' dilewati)"
  fi
else
  warn "editor beranda tidak terbuka untuk pilihan 'Ingat tombol daya TV'"
fi
check_alive "setelah menyiapkan 'Ingat tombol daya TV'"

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
  # Perangkat nyata membuka launcher dengan kode yang sudah dikompilasi (profil dasar, dexopt latar belakang). Emulator CI
  # menjalankan semuanya di interpreter: komposisi pertama beranda memakan ±6 dtk di thread utama, jadi FocusEvent pertama
  # menunggu 4,4-5,5 dtk dan sering melewati batas ANR 5 dtk. Pada kode sebelum perubahan data pribadi pun nilainya 4,8 dtk
  # (garis dasar); sesudahnya 5,1-5,5 dtk pada 3 dari 5 run, sehingga uji ini bergantung pada jitter emulator, bukan pada
  # kode. Karena itu aplikasi dikompilasi (speed) selagi mati. ANR yang sungguhan (thread utama macet di kode terkompilasi)
  # tetap GAGAL; yang tidak lagi dihitung hanyalah macet gara-gara interpreter.
  say "kompilasi sebelum dibuka dari nol (speed)"
  adbs cmd package compile -f -m speed "$PKG" | tail -3 | tee -a "$OUT/log.txt"
  adbs dumpsys package dexopt | grep -A2 "\[$PKG\]" | head -4 | tee -a "$OUT/log.txt"
  settle 90 > /dev/null || true
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
  # Ketahanan pengaturan hanya bisa dinilai bila onboarding memang pernah selesai pada uji ini (ONBOARDED=1). Tanpa itu tidak ada
  # pengaturan yang bisa hilang, jadi "onboarding muncul lagi" tidak membuktikan apa pun tentang DataStore/JSON dan hasil hijau
  # akan menipu: dinilai GAGAL sebagai "tidak terverifikasi" (rilis tidak terbit; jalankan ulang).
  unverified="ketahanan pengaturan setelah proses dimatikan TIDAK TERVERIFIKASI: onboarding tidak pernah selesai pada uji ini (tombol Mulai tak terketuk atau beranda tak terbaca)"
  if top_is Onboarding; then
    if [ "$ONBOARDED" -eq 1 ]; then
      fail "onboarding muncul lagi setelah proses dimatikan: pengaturan tidak bertahan (DataStore/JSON)"
    else
      fail "$unverified"
    fi
  elif top_is_home; then
    if [ "$ONBOARDED" -eq 1 ]; then
      ok "beranda terbuka tanpa onboarding: pengaturan bertahan setelah proses dimatikan"
    else
      fail "$unverified"
    fi
  else
    warn "layar depan tidak dikenali setelah kill: $(top_desc)"
  fi
  check_alive "setelah dibuka kembali"
  # "Ingat tombol daya TV": TV yang dimatikan di langkah 6c harus tetap mati di proses yang baru (tanpa pilihan itu TV selalu
  # menyala lagi). Sesudahnya keadaan dikembalikan: TV dinyalakan dan pilihannya dimatikan.
  if [ "$RP_READY" -eq 1 ]; then
    if top_is_home; then
      sleep 2
      goto_room ingat_sesudah > /dev/null
      shot "ingat_sesudah_kill"
      find_node ingat_hasil '^TV mati$'
      case $? in
        0) ok "TV tetap mati setelah proses dimatikan (pilihan 'Ingat tombol daya TV')" ;;
        1) warn "TV menyala lagi setelah proses dimatikan padahal 'Ingat tombol daya TV' menyala" ;;
        *) warn "layar tidak terbaca saat memeriksa TV sesudah proses dimatikan" ;;
      esac
    else
      warn "beranda tidak di depan setelah proses dimatikan; 'Ingat tombol daya TV' tidak diperiksa: $(top_desc)"
    fi
  fi
  if top_is_home && { [ "$RP_READY" -eq 1 ] || [ "$RP_CHANGED" -eq 1 ]; }; then
    goto_room ingat_pulih > /dev/null
    if find_node ingat_pulih_mati '^TV mati$'; then
      tap_node ingat_pulih_daya '^Tombol daya TV$' > /dev/null
      sleep 2
    fi
    if [ "$RP_CHANGED" -eq 1 ]; then
      if open_editor ingat_pulih_editor && sheet_scroll_tap ingat_pulih_baris '^Ingat tombol daya TV'; then
        sleep 1.5
        find_node ingat_pulih_cek '^Ingat tombol daya TV'
        if [ "$(node_checked ingat_pulih_cek '^Ingat tombol daya TV')" = "true" ]; then
          # sheet_scroll_tap sudah mengetuk barisnya sekali; bila pilihannya masih menyala, ketuk sekali lagi supaya langkah
          # berikutnya mulai dari keadaan bawaan (mati)
          tap_node ingat_pulih_baris_lagi '^Ingat tombol daya TV' > /dev/null
          sleep 1.5
        fi
        close_editor ingat_pulih_batal
      else
        warn "pilihan 'Ingat tombol daya TV' tidak bisa dikembalikan ke mati (editor/barisnya tidak ditemukan)"
        if top_is Home; then adb shell input keyevent KEYCODE_BACK; sleep 1; fi
      fi
    fi
  fi
fi
scan_log kill

# ---------- 7b. buku harian terenkripsi ----------
# Catatan ronde Ular/Bata dan kaset film direkam saat langkah-langkah di atas (saklar dinyalakan di "Benda ruangan"), lalu proses
# dimatikan di langkah 7. Di sini bukunya dibuka di proses yang baru: baris yang tampil dibaca dari disk dan dibuka dengan kunci
# Android Keystore sungguhan (kunci dicari ulang lewat alias). Lalu berkas SharedPreferences dibaca lewat root: hanya boleh ada
# nilai tersegel, tanpa teks polos catatan. Elemen yang tidak ditemukan hanya PERINGATAN (ronde game bisa saja tak sempat dimulai);
# alamat situs di buku, kunci polos lama, teks polos di disk, dan pesan "tidak disimpan" adalah GAGAL.
step "Buku harian: catatan game dan film terbaca setelah proses dimatikan, dan di disk hanya ada nilai tersegel (Keystore sungguhan)"
DIARY_SEEN=0
if [ "$DIARY_ON" -ne 1 ]; then
  warn "perekaman buku harian tidak sempat dinyalakan (bukunya tidak tampak di beranda); catatan game dan film tidak diperiksa"
elif ! ensure_front buku_harian_isi > /dev/null; then
  warn "beranda tidak di depan; buku harian tidak diperiksa: $(top_desc)"
else
  goto_room buku_harian_isi > /dev/null
  if find_node buku2_buka '^Buku harian kaset$'; then
    adb shell input tap "$NX" "$NY"; sleep 3; shot "buku_harian_isi"
    if find_node buku2_game 'Game.{1,5}Skor [0-9]+'; then
      DIARY_SEEN=1; ok "catatan game (nama dan skor ronde) ada di buku harian setelah proses dimatikan"
    else
      warn "tidak ada catatan game di buku harian (ronde Ular/Bata mungkin tidak sempat dimulai)"
    fi
    if find_node buku2_film '.{1,3}Film$'; then
      DIARY_SEEN=1; ok "catatan film (nama kaset) ada di buku harian setelah proses dimatikan"
    else
      warn "tidak ada catatan film di buku harian (kaset film mungkin tidak termuat)"
    fi
    find_node buku2_url 'https?://'
    case $? in
      0) fail "buku harian memuat alamat situs; film hanya boleh dicatat dengan nama kaset" ;;
      1) ok "buku harian tidak memuat alamat situs" ;;
      *) warn "layar tidak terbaca saat memeriksa buku harian dari alamat situs" ;;
    esac
    if find_node buku2_gagal 'Catatan tidak disimpan'; then
      fail "buku harian melaporkan penyimpanan terenkripsi tidak tersedia di emulator (Keystore)"
    fi
    tap_node buku2_tutup '^Tutup$' > /dev/null || adb shell input keyevent KEYCODE_BACK
    sleep 1
  else
    warn "buku harian tidak tampak di beranda setelah proses dimatikan; isinya tidak diperiksa"
  fi
  # Berkasnya di disk (emulator google_apis mengizinkan adb root)
  adb root > /dev/null 2>&1
  adb wait-for-device
  prefs_xml=$(adbs cat "/data/data/$PKG/shared_prefs/retro_diary.xml")
  case "$prefs_xml" in
    ""|*"No such file"*|*"Permission denied"*)
      warn "retro_diary.xml tidak terbaca lewat root; isi di disk tidak diperiksa (${prefs_xml:-kosong})" ;;
    *)
      printf '%s\n' "$prefs_xml" | sed -E 's/>[A-Za-z0-9+\/=]{40,}</>[nilai tersegel, disingkat]</' > "$OUT/retro_diary_disk.xml"
      say "retro_diary.xml di disk (nilai tersegel disingkat):"; sed 's/^/  /' "$OUT/retro_diary_disk.xml" | tee -a "$OUT/log.txt" > /dev/null
      if printf '%s' "$prefs_xml" | grep -q 'name="entries"'; then
        fail "kunci polos lama 'entries' ada di retro_diary.xml"
      fi
      if printf '%s' "$prefs_xml" | grep -q 'Skor \|Kaset '; then
        fail "retro_diary.xml di disk memuat teks polos catatan (Skor/Kaset)"
      elif printf '%s' "$prefs_xml" | grep -q 'name="entries_sealed"'; then
        ok "catatan buku harian di disk hanya berupa nilai tersegel (entries_sealed), tanpa teks polos"
      elif [ "$DIARY_SEEN" -eq 1 ]; then
        fail "catatan terbaca di buku harian tetapi retro_diary.xml tidak memuat entries_sealed"
      else
        warn "belum ada catatan yang tersimpan; isi tersegel di disk tidak dapat dinilai"
      fi
      if printf '%s' "$prefs_xml" | grep -q 'name="recording" value="true"'; then
        ok "saklar perekaman buku harian bertahan di disk"
      else
        warn "saklar perekaman buku harian tidak tampak menyala di retro_diary.xml"
      fi ;;
  esac
  # Favorit kontak tidak diisi oleh uji ini; bila berkasnya ada, tidak boleh memuat kunci polos lama
  phone_xml=$(adbs cat "/data/data/$PKG/shared_prefs/retro_phone.xml")
  case "$phone_xml" in
    ""|*"No such file"*|*"Permission denied"*) say "retro_phone.xml belum ada (tidak ada favorit kontak yang diisi)" ;;
    *) if printf '%s' "$phone_xml" | grep -q 'name="favorites"'; then fail "kunci polos lama 'favorites' ada di retro_phone.xml"; else ok "retro_phone.xml tidak memuat kunci polos"; fi ;;
  esac
  ls_poster=$(adbs ls "/data/data/$PKG/files/poster")
  case "$ls_poster" in
    *"No such file"*|"") say "folder poster belum ada (tidak ada foto poster yang dipilih)" ;;
    *) say "isi folder poster: $ls_poster" ;;
  esac
  check_alive "setelah memeriksa buku harian"
fi
scan_log buku_harian

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
