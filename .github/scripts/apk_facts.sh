#!/usr/bin/env bash
# Mencetak fakta sebuah APK untuk log CI (bukti, bukan klaim): paket dan versi, apakah bisa di-debug, ukuran, izin,
# sidik jari sertifikat, keabsahan tanda tangan, serta berkas profil dasar di dalam APK.
# Hanya membaca; selalu keluar dengan status 0 supaya pencatatan tidak menggagalkan build. Penilaian (lulus/gagal)
# dilakukan langkah pemanggil dari berkas hasil di app/build/facts/.
#
# Pemakaian: .github/scripts/apk_facts.sh <label> <berkas.apk>
set -u
label="${1:?label (mis. release)}"
apk="${2:?berkas apk}"
out="app/build/facts"
mkdir -p "$out"
: > "$out/$label.perm"
: > "$out/$label.fp"

BT=$(ls -d "${ANDROID_HOME:-/nonexistent}"/build-tools/* 2>/dev/null | sort -V | tail -1 || true)
AAPT2="${BT:+$BT/aapt2}"
SIGNER="${BT:+$BT/apksigner}"

echo
echo "===== FAKTA APK ($label): $apk ====="
if [ ! -f "$apk" ]; then
  echo "berkas tidak ada"
  exit 0
fi
echo "ukuran: $(stat -c %s "$apk") bita"
stat -c %s "$apk" > "$out/$label.size"

if [ -x "${AAPT2:-}" ]; then
  badging=$("$AAPT2" dump badging "$apk" 2>&1 || true)
  echo "$badging" | grep -E "^(package:|minSdkVersion:|targetSdkVersion:|sdkVersion:)" || true
  if echo "$badging" | grep -q "application-debuggable"; then echo "bisa di-debug: ya"; else echo "bisa di-debug: tidak"; fi
  echo "--- izin di APK (aapt2 dump permissions) ---"
  "$AAPT2" dump permissions "$apk" 2>&1 | grep -E "^uses-permission" | while read -r line; do
    n=$(echo "$line" | sed -nE "s/.*name='([^']*)'.*/\1/p")
    m=$(echo "$line" | sed -nE "s/.*maxSdkVersion='([0-9]+)'.*/\1/p")
    echo "$n${m:+ (hanya s.d. API $m)}"
  done | sort -u | tee "$out/$label.perm"
else
  echo "aapt2 tidak ditemukan; izin tidak dicatat"
fi

if [ -x "${SIGNER:-}" ]; then
  # Format sama dengan keytool (huruf besar, dipisah titik dua) agar mudah dicocokkan
  FP=$("$SIGNER" verify --print-certs "$apk" 2>&1 | grep -m1 'certificate SHA-256 digest' | sed 's/.*digest: //' \
    | tr 'a-f' 'A-F' | sed 's/../&:/g; s/:$//' || true)
  echo "sertifikat SHA-256: ${FP:-tidak terbaca}"
  echo "${FP:-}" > "$out/$label.fp"
  "$SIGNER" verify --verbose "$apk" 2>&1 | grep -E "^(Verifies|DOES NOT VERIFY|Verified using)" || true
else
  echo "apksigner tidak ditemukan; sertifikat tidak dicatat"
fi

echo "--- isi APK ---"
echo "berkas classes*.dex: $(unzip -l "$apk" | grep -cE ' classes[0-9]*\.dex$')"
unzip -l "$apk" | grep -E "assets/dexopt/|META-INF/com/android/build/gradle/app-metadata" || echo "assets/dexopt/: TIDAK ADA"
echo "pustaka native (.so): $(unzip -l "$apk" | grep -cE '\.so$')"

# Berkas profil dasar teks hasil penggabungan (aturan pustaka + aturan wildcard kita yang sudah diperluas)
echo "--- profil dasar (berkas teks antara di app/build/intermediates) ---"
found=0
while IFS= read -r f; do
  found=1
  total=$(wc -l < "$f" 2>/dev/null || echo "?")
  ours=$(grep -c '^[HSP]*Lcom/vinyl/launcher' "$f" 2>/dev/null || true)
  echo "$f: $total baris, di antaranya aturan com/vinyl/launcher: ${ours:-0}"
done < <(find app/build/intermediates -path '*art_profile*' -type f -name '*.txt' 2>/dev/null | grep -i "$label" | sort)
[ "$found" -eq 1 ] || echo "(tidak ada berkas teks profil untuk varian $label)"
exit 0
