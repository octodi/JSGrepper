#!/usr/bin/env bash
# Installs the JS Grepper VS Code / VSCodium extension.
# Auto-detects the correct extensions directory.
# Usage: bash install.sh

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
EXT_NAME="js-grepper-0.1.0"

# Detect which editor is installed and pick its extensions directory
if   [ -d "$HOME/.vscode-oss/extensions" ]; then
    EXT_DIR="$HOME/.vscode-oss/extensions/$EXT_NAME"
    EDITOR="VSCodium"
elif [ -d "$HOME/.vscode/extensions" ]; then
    EXT_DIR="$HOME/.vscode/extensions/$EXT_NAME"
    EDITOR="VS Code"
else
    EXT_DIR="$HOME/.vscode/extensions/$EXT_NAME"
    EDITOR="VS Code"
fi

echo "==> Detected: $EDITOR"
echo "==> Installing JS Grepper to: $EXT_DIR"
rm -rf "$EXT_DIR"
mkdir -p "$EXT_DIR"
cp -r "$SCRIPT_DIR/." "$EXT_DIR/"

echo ""
echo "✅ Installed to $EXT_DIR"
echo "   Restart $EDITOR (or Cmd+Shift+P → 'Developer: Reload Window') to activate."
echo "   The ⚡ JS Grepper panel will appear in the Activity Bar."
