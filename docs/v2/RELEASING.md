# Releasing

Every push to `main` runs `.github/workflows/release.yml`, which builds a signed release APK and
publishes it as a GitHub release tagged `v<versionName>`. The pull-request workflow refuses a PR
that does not increase `versionCode`, and the release fails if the tag already exists, so bump
both `versionCode` and `versionName` in `app/build.gradle.kts`.

## One-time setup: the signing key

An APK can only update an installed copy that was signed with the same key. If v1 APKs were
handed out, reuse that key. Otherwise create one and keep the file and passwords somewhere safe:
losing them means users must uninstall to update.

    keytool -genkeypair -v -keystore qintar.jks -alias qintar -keyalg RSA -keysize 4096 -validity 10000

Add these repository secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `QINTAR_KEYSTORE_BASE64` | output of `base64 -w0 qintar.jks` |
| `QINTAR_KEYSTORE_PASSWORD` | the keystore password |
| `QINTAR_KEY_ALIAS` | `qintar` (or the alias you chose) |
| `QINTAR_KEY_PASSWORD` | the key password |

## Building a signed APK locally

    export QINTAR_KEYSTORE_FILE=/path/to/qintar.jks
    export QINTAR_KEYSTORE_PASSWORD=... QINTAR_KEY_ALIAS=qintar QINTAR_KEY_PASSWORD=...
    ./gradlew :app:assembleRelease

Without those variables `assembleRelease` still succeeds but produces an unsigned APK, which is
what the pull-request workflow builds to prove that the release configuration compiles.
