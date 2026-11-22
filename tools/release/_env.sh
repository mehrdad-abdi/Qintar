# Shared settings for the release scripts. Sourced, not run.
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
GITHUB_REPO="${GITHUB_REPO:-mehrdad-abdi/Qintar}"
KEYSTORE="${QINTAR_KEYSTORE_FILE:-$HOME/keys/qintar-release.jks}"
KEY_ALIAS="${QINTAR_KEY_ALIAS:-qintar}"
export JAVA_HOME="${JAVA_HOME:-$HOME/tools/jdk-17.0.21+9}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export PATH="$JAVA_HOME/bin:$PATH"
BUILD_TOOLS="$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)"

ask_password() { # sets KEY_PASSWORD without echoing it
  read -rsp "Password for $KEYSTORE: " KEY_PASSWORD; echo; export KEY_PASSWORD
  [ -n "$KEY_PASSWORD" ] || { echo "No password given." >&2; exit 1; }
}
