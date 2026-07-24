#!/usr/bin/env bash
# Build RaceBox UBX-MGA aiding blob from free IGS/BKG RINEX BRDC.
# Output: $OUT_DIR/aiding.ubx + $OUT_DIR/meta.json
set -euo pipefail

OUT_DIR="${1:-out/racebox-aiding}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

mkdir -p "$OUT_DIR"

UTC_YEAR="$(date -u +%Y)"
UTC_DOY="$(date -u +%j)"
# strip leading zeros for arithmetic, keep padded for paths
DOY_PAD="$(printf '%03d' "$((10#$UTC_DOY))")"

echo "UTC date: ${UTC_YEAR}-doy${DOY_PAD}"

download_brdc() {
  local year="$1"
  local doy="$2"
  local base="https://igs.bkg.bund.de/root_ftp/IGS/BRDC/${year}/${doy}"
  local names=(
    "BRDM00DLR_S_${year}${doy}0000_01D_MN.rnx.gz"
    "BRDC00WRD_R_${year}${doy}0000_01D_MN.rnx.gz"
    "BRDC00IGS_R_${year}${doy}0000_01D_MN.rnx.gz"
  )
  for name in "${names[@]}"; do
    local url="${base}/${name}"
    echo "Trying ${url}"
    if curl -fsSL --retry 3 --retry-delay 2 -o "${WORK}/brdc.rnx.gz" "$url"; then
      echo "Downloaded ${name}"
      echo "$name" > "${WORK}/source_name.txt"
      return 0
    fi
  done
  return 1
}

# Today, then yesterday (UTC)
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
git clone --depth 1 https://github.com/jkivilin/ubx-mga-gnss-rinex-ephemeris-converter.git "${WORK}/conv"
python3 -m pip install --quiet -r "${WORK}/conv/requirements.txt"

# GPS+GLO+GAL covers RaceBox Mini constellations well; skip BeiDou if unsupported by tool version
python3 "${WORK}/conv/convert_eph.py" "${WORK}/brdc.rnx" \
  -o "${OUT_DIR}/aiding.ubx" \
  --systems GPS,GLO,GAL \
  -v

SOURCE_NAME="$(cat "${WORK}/source_name.txt")"
BYTES="$(wc -c < "${OUT_DIR}/aiding.ubx" | tr -d ' ')"
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
