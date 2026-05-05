#!/usr/bin/env bash
# This sownloads the SNAP wiki-topcats dataset into ./data
#
set -euo pipefail

DATA_DIR="$(cd "$(dirname "$0")/.." && pwd)/data"
mkdir -p "$DATA_DIR"
cd "$DATA_DIR"

BASE="https://snap.stanford.edu/data"

download() {
  local file="$1"
  local plain="${file%.gz}"

  if [[ -f "$plain" ]]; then
    echo "[skip] $plain already exists"
    return
  fi

  # If a previous run left a corrupt partial .gz, try resuming first;
  # only blow it away if a fresh attempt is also broken.
  for attempt in 1 2; do
    if [[ ! -f "$file" ]]; then
      echo "[get ] $file (attempt $attempt)"
      curl -L -o "$file" "$BASE/$file"
    else
      echo "[res ] $file (resuming attempt $attempt)"
      curl -L -C - -o "$file" "$BASE/$file" || true
    fi

    if gzip -t "$file" 2>/dev/null; then
      break
    fi
    echo "[warn] $file appears corrupt, removing and re-downloading"
    rm -f "$file"
  done

  if ! gzip -t "$file" 2>/dev/null; then
    echo "[err ] gzip integrity check failed for $file" >&2
    exit 1
  fi

  echo "[gunz] $file"
  gunzip -k "$file"
  rm -f "$file"
}

download "wiki-topcats.txt.gz"
download "wiki-topcats-page-names.txt.gz"

echo
echo "Done. Files in $DATA_DIR:"
ls -lh "$DATA_DIR"
