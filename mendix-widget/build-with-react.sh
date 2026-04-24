#!/bin/bash
set -e

WIDGET_DIR="$(cd "$(dirname "$0")" && pwd)"
FRONTEND_DIR="$WIDGET_DIR/../frontend"
MPK="$WIDGET_DIR/dist/1.0.0/3qcode.GisMap.mpk"

echo "[1/4] Building React frontend..."
cd "$FRONTEND_DIR"
npm run build

echo "[2/4] Copying assets to widget src..."
cp -f dist/assets/index.js "$WIDGET_DIR/src/assets/index.js"
cp -f dist/assets/index.css "$WIDGET_DIR/src/assets/index.css"

echo "[3/4] Building Mendix widget..."
cd "$WIDGET_DIR"
npm run build

echo "[4/4] Injecting React assets into mpk..."
TEMP_DIR=$(mktemp -d)
mkdir -p "$TEMP_DIR/3qcode/gismap/assets"
cp src/assets/index.js "$TEMP_DIR/3qcode/gismap/assets/"
cp src/assets/index.css "$TEMP_DIR/3qcode/gismap/assets/"
cd "$TEMP_DIR"
zip -ur "$MPK" 3qcode/gismap/assets/
cd "$WIDGET_DIR"
rm -rf "$TEMP_DIR"

echo ""
echo "Done: $MPK"
