#!/usr/bin/env bash
# Builds the signed release APK (and the app bundle for Google Play) on this computer, then checks
# the signature. Asks for the key password.
#   tools/release/build-signed.sh [keystore-path] [alias]
# Output: app/build/outputs/apk/release/app-release.apk and bundle/release/app-release.aab
set -euo pipefail
. "$(dirname "$0")/_env.sh"
KEYSTORE="${1:-$KEYSTORE}"; KEY_ALIAS="${2:-$KEY_ALIAS}"
[ -f "$KEYSTORE" ] || { echo "No keystore at $KEYSTORE" >&2; exit 1; }
ask_password
keytool -list -keystore "$KEYSTORE" -storepass:env KEY_PASSWORD -alias "$KEY_ALIAS" >/dev/null \
  || { echo "Wrong password, or no alias '$KEY_ALIAS' in $KEYSTORE." >&2; exit 1; }
cd "$REPO_ROOT"
LOG="$(mktemp -t qintar-release.XXXXXX.log)"
echo "Building (log: $LOG)…"
QINTAR_KEYSTORE_FILE="$KEYSTORE" QINTAR_KEY_ALIAS="$KEY_ALIAS" \
QINTAR_KEYSTORE_PASSWORD="$KEY_PASSWORD" QINTAR_KEY_PASSWORD="$KEY_PASSWORD" \
  ./gradlew :app:assembleRelease :app:bundleRelease >"$LOG" 2>&1 \
  || { tail -30 "$LOG"; echo "Build failed." >&2; exit 1; }
unset KEY_PASSWORD
APK=app/build/outputs/apk/release/app-release.apk
"$BUILD_TOOLS/apksigner" verify --print-certs "$APK" | grep -E "DN|SHA-256"
"$BUILD_TOOLS/aapt2" dump badging "$APK" | head -1
echo "APK:    $REPO_ROOT/$APK"
echo "Bundle: $REPO_ROOT/app/build/outputs/bundle/release/app-release.aab"
