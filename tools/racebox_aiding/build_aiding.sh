#!/usr/bin/env bash
# Build RaceBox UBX-MGA aiding blob from free IGS RINEX BRDC.
# Output: $OUT_DIR/aiding.ubx + $OUT_DIR/meta.json
#
# Tries several public mirrors with hard curl timeouts so a hung host
# cannot burn ~55 minutes of GitHub Actions. On failure the workflow
# skips publish, so the last good file on branch racebox-aiding stays.
set -euo pipefail

OUT_DIR="${1:-out/racebox-aiding}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

mkdir -p "$OUT_DIR"

UTC_YEAR="$(date -u +%Y)"
UTC_DOY="$(date -u +%j)"
DOY_PAD="$(printf '%03d' "$((10#$UTC_DOY))")"

echo "UTC date: ${UTC_YEAR}-doy${DOY_PAD}"

curl_get() {
  local url="$1"
  echo "Trying ${url}"
  if curl -fL --connect-timeout 10 --max-time 25 --retry 0 \
      --user-agent "REVIX-racebox-aiding" \
      -o "${WORK}/brdc.rnx.gz" "$url"; then
    if gzip -t "${WORK}/brdc.rnx.gz" 2>/dev/null; then
      echo "Downloaded ${url}"
      echo "$url" > "${WORK}/source_name.txt"
      return 0
    fi
    echo "Not a gzip file: ${url}"
    rm -f "${WORK}/brdc.rnx.gz"
  fi
  return 1
}

# Near-real-time WRD files appear first; DLR/IGS merged files later in the day.
brdc_urls() {
  local year="$1"
  local doy="$2"
  local yy
  yy="$(printf '%02d' "$((10#$year % 100))")"
  cat <<EOF
ftp://igs-ftp.bkg.bund.de/IGS/BRDC/${year}/${doy}/BRDC00WRD_R_${year}${doy}0000_01D_MN.rnx.gz
ftp://igs-ftp.bkg.bund.de/IGS/BRDC/${year}/${doy}/BRDC00WRD_S_${year}${doy}0000_01D_MN.rnx.gz
ftp://igs-ftp.bkg.bund.de/IGS/BRDC/${year}/${doy}/BRDM00DLR_S_${year}${doy}0000_01D_MN.rnx.gz
ftp://igs-ftp.bkg.bund.de/IGS/BRDC/${year}/${doy}/BRDC00IGS_R_${year}${doy}0000_01D_MN.rnx.gz
https://igs.bkg.bund.de/root_ftp/IGS/BRDC/${year}/${doy}/BRDC00WRD_R_${year}${doy}0000_01D_MN.rnx.gz
https://igs.bkg.bund.de/root_ftp/IGS/BRDC/${year}/${doy}/BRDM00DLR_S_${year}${doy}0000_01D_MN.rnx.gz
ftp://anonymous:anonymous@igs.ign.fr/pub/igs/data/${year}/${doy}/BRDM00DLR_S_${year}${doy}0000_01D_MN.rnx.gz
ftp://anonymous:anonymous@igs.ign.fr/pub/igs/data/${year}/${doy}/BRDC00IGS_R_${year}${doy}0000_01D_MN.rnx.gz
ftp://igs.gnsswhu.cn/pub/gps/data/daily/${year}/${doy}/${yy}p/BRDC00IGS_R_${year}${doy}0000_01D_MN.rnx.gz
ftp://gssc.esa.int/gnss/data/daily/${year}/${doy}/${yy}p/BRDC00IGS_R_${year}${doy}0000_01D_MN.rnx.gz
EOF
}

download_brdc() {
  local year="$1"
  local doy="$2"
  local url
  while IFS= read -r url; do
    [[ -z "$url" ]] && continue
    if curl_get "$url"; then
      return 0
    fi
  done < <(brdc_urls "$year" "$doy")
  return 1
}

if ! download_brdc "$UTC_YEAR" "$DOY_PAD"; then
  YDAY_EPOCH="$(date -u -d 'yesterday' +%s 2>/dev/null || date -u -v-1d +%s)"
  Y_YEAR="$(date -u -d "@${YDAY_EPOCH}" +%Y 2>/dev/null || date -u -r "${YDAY_EPOCH}" +%Y)"
  Y_DOY="$(date -u -d "@${YDAY_EPOCH}" +%j 2>/dev/null || date -u -r "${YDAY_EPOCH}" +%j)"
  Y_DOY_PAD="$(printf '%03d' "$((10#$Y_DOY))")"
  echo "Today missing — trying yesterday ${Y_YEAR}-doy${Y_DOY_PAD}"
  download_brdc "$Y_YEAR" "$Y_DOY_PAD"
fi

gunzip -c "${WORK}/brdc.rnx.gz" > "${WORK}/brdc.rnx"

# Clone converter (MIT)
timeout 45 git clone --depth 1 https://github.com/jkivilin/ubx-mga-gnss-rinex-ephemeris-converter.git "${WORK}/conv"
timeout 60 python3 -m pip install --quiet -r "${WORK}/conv/requirements.txt"

# GPS+GLO+GAL covers RaceBox Mini constellations well; skip BeiDou if unsupported by tool version
python3 "${WORK}/conv/convert_eph.py" "${WORK}/brdc.rnx" \
  -o "${OUT_DIR}/aiding.ubx" \
  --systems GPS,GLO,GAL \
  -v

SOURCE_NAME="$(cat "${WORK}/source_name.txt")"
BYTES="$(wc -c < "${OUT_DIR}/aiding.ubx" | tr -d ' ')"
if [[ -z "$BYTES" || "$BYTES" -lt 1000 ]]; then
  echo "aiding.ubx too small (${BYTES:-0} bytes)" >&2
  exit 1
fi
GENERATED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

python3 - <<PY
import json
meta = {
  "generated_at": "${GENERATED_AT}",
  "source": "${SOURCE_NAME}",
  "bytes": int("${BYTES}"),
  "systems": ["GPS", "GLO", "GAL"],
  "format": "ubx-mga"
}
open("${OUT_DIR}/meta.json", "w", encoding="utf-8").write(json.dumps(meta, indent=2) + "\n")
print(json.dumps(meta))
PY

echo "Wrote ${OUT_DIR}/aiding.ubx (${BYTES} bytes)"
