#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -lt 1 ]; then
  echo "Usage: $0 input.png [output.ico]"
  exit 1
fi

INPUT="$1"
BASENAME="${INPUT%.*}"
OUTPUT="${2:-$BASENAME.ico}"

# Use ImageMagick to generate a multi-resolution .ico
# The list of sizes can be adjusted, but this set works well for modern Windows.
magick "$INPUT" -define icon:auto-resize=256,128,64,48,32,24,16 "$OUTPUT"

echo "Created $OUTPUT"
