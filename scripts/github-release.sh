#!/usr/bin/env bash
#
# Create a GitHub release for tag v<version> and upload the APK asset.
# Usage: scripts/github-release.sh <version> <apk-path>
# Requires: the GitHub CLI `gh`, authenticated once via `gh auth login`.
#
set -euo pipefail

VERSION="${1:?usage: github-release.sh <version> <apk-path>}"
APK="${2:?usage: github-release.sh <version> <apk-path>}"
[ -f "$APK" ] || { echo "APK not found: $APK" >&2; exit 1; }
command -v gh >/dev/null 2>&1 || { echo "gh CLI not found — install it and run 'gh auth login'" >&2; exit 1; }

TAG="v$VERSION"
ASSET="pixelbridge-watch-$VERSION.apk"

# gh uploads assets under their on-disk basename, so stage the APK under the clean asset name.
STAGED="$(dirname "$APK")/$ASSET"
cp "$APK" "$STAGED"

NOTES=$(cat <<EOF
Standalone Wear OS notification app for the Google Pixel Watch (1st gen).

**Asset:** \`$ASSET\` — debug-signed; sideload with \`adb install -r $ASSET\`.

Requires unmodified Gadgetbridge on the phone (added as a *Bangle.js* device).
Setup: see the README. Changes: see CHANGELOG.md.
EOF
)

echo "Creating GitHub release $TAG ..."
gh release create "$TAG" "$STAGED" --title "PixelBridge $TAG" --notes "$NOTES"

rm -f "$STAGED"
echo "Done: $(gh repo view --json url -q .url)/releases/tag/$TAG"
