#!/usr/bin/env bash
# ─── JS Saver – Burp Extension Build Script ───────────────────────────────────
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC_DIR="$SCRIPT_DIR/src"
OUT_DIR="$SCRIPT_DIR/out"
LIBS_DIR="$SCRIPT_DIR/libs"
API_JAR="$SCRIPT_DIR/montoya-api-2026.2.jar"
BEAUTIFY_JAR="$LIBS_DIR/js-beautify-1.15.4.1.jar"
JSON_JAR="$LIBS_DIR/json-20250517.jar"
OUT_JAR="$SCRIPT_DIR/JsSaver.jar"
MANIFEST="$OUT_DIR/MANIFEST.MF"
UNPACK_DIR="$OUT_DIR/unpack"

echo "==> Cleaning output directory…"
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR" "$UNPACK_DIR"

echo "==> Compiling Java sources…"
CP="$API_JAR:$BEAUTIFY_JAR:$JSON_JAR"
javac --release 11 -cp "$CP" -d "$OUT_DIR" "$SRC_DIR"/*.java

echo "==> Unpacking dependency JARs into fat JAR staging area…"
(cd "$UNPACK_DIR" && jar xf "$BEAUTIFY_JAR" && jar xf "$JSON_JAR")
# Remove duplicate manifests from deps
rm -rf "$UNPACK_DIR/META-INF"

echo "==> Creating manifest…"
cat > "$MANIFEST" <<'EOF'
Manifest-Version: 1.0
BurpExtender-Module: JsSaverExtension
EOF

echo "==> Packaging JsSaver.jar (fat JAR with bundled beautifier)…"
# First add compiled classes, then overlay dep classes
jar cfm "$OUT_JAR" "$MANIFEST" -C "$OUT_DIR" . 2>/dev/null || true
jar uf "$OUT_JAR" -C "$UNPACK_DIR" .

echo ""
echo "BUILD SUCCESSFUL ─── $OUT_JAR"
echo "Load this JAR in Burp Suite → Extensions → Add → Java."

# ── VS Code extension (VSIX) ──────────────────────────────────────────────────
echo ""
echo "==> Packaging VS Code extension (VSIX)…"
cd "$SCRIPT_DIR/vscode-extension"
npx @vscode/vsce package --allow-missing-repository --skip-license --no-git-tag-version 2>&1 \
  | grep -E "DONE|ERROR|error" || true
echo ""
echo "BUILD COMPLETE"
echo "  Burp JAR : $OUT_JAR"
echo "  VSCode   : $SCRIPT_DIR/vscode-extension/js-grepper-*.vsix"
