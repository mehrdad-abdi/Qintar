#!/usr/bin/env bash
# Uploads the signing key to the GitHub repository secrets used by .github/workflows/release.yml.
# Needs `gh auth login`. Asks for the key password; it is never printed or written to disk.
#   tools/release/set-github-secrets.sh [keystore-path]
set -euo pipefail
. "$(dirname "$0")/_env.sh"
KEYSTORE="${1:-$KEYSTORE}"
[ -f "$KEYSTORE" ] || { echo "No keystore at $KEYSTORE" >&2; exit 1; }
ask_password
keytool -list -keystore "$KEYSTORE" -storepass:env KEY_PASSWORD -alias "$KEY_ALIAS" >/dev/null \
  || { echo "Wrong password, or no alias '$KEY_ALIAS' in $KEYSTORE." >&2; exit 1; }
base64 -w0 "$KEYSTORE" | gh secret set QINTAR_KEYSTORE_BASE64 -R "$GITHUB_REPO"
printf %s "$KEY_ALIAS"    | gh secret set QINTAR_KEY_ALIAS -R "$GITHUB_REPO"
printf %s "$KEY_PASSWORD" | gh secret set QINTAR_KEYSTORE_PASSWORD -R "$GITHUB_REPO"
printf %s "$KEY_PASSWORD" | gh secret set QINTAR_KEY_PASSWORD -R "$GITHUB_REPO"
gh secret list -R "$GITHUB_REPO"
