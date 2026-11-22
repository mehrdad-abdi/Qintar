#!/usr/bin/env bash
# Creates the release signing key. Run once; keep the file and its password safe (password
# manager + a copy off this computer). Losing either means no update can reach installed apps.
#   tools/release/new-release-key.sh [keystore-path]
set -euo pipefail
. "$(dirname "$0")/_env.sh"
KEYSTORE="${1:-$KEYSTORE}"
[ -e "$KEYSTORE" ] && { echo "$KEYSTORE already exists; not overwriting it." >&2; exit 1; }
mkdir -p "$(dirname "$KEYSTORE")"; chmod 700 "$(dirname "$KEYSTORE")"
keytool -genkeypair -v -storetype PKCS12 -keystore "$KEYSTORE" -alias "$KEY_ALIAS" \
  -keyalg RSA -keysize 4096 -validity 10000
chmod 600 "$KEYSTORE"
echo "Created $KEYSTORE (alias $KEY_ALIAS). Back it up now."
