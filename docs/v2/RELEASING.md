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

## Before a release that users of version 1 will install

Version 2 keeps v1's application id (`io.github.mehrdad_abdi.quranbookmarks`), so it installs as
an update only if it is signed with the key v1 was signed with, and carries v1's data over on its
first start (DESIGN.md §7.11). To repeat the device test of that path:

1. Build v1 from tag `v1-final` in a separate worktree (`git worktree add --detach ../qintar-v1 v1-final`,
   then `./gradlew :app:assembleDebug` there), install it on an emulator and enter some data
   through its screens: bookmarks, readings, the khatm page, a setting.
2. Build v2 with `./gradlew :app:assembleDebug -Pqintar.v1UpgradeTest=true` (debug build without
   the `.debug` suffix, signed with the same debug key) and `adb install -r` it over v1.
3. Check: no crash; the "carried over" card; bookmarks, groups (from tags), day totals, khatm
   position and settings as v1 showed them; a second start imports nothing; after "Got it", v1's
   `databases/quran.db` and caches are gone while `databases/quran_bookmarks_database` stays until
   Settings → Data → "Remove data of the previous version".
4. Uninstall that test package afterwards.

The same data, taken from such an emulator, is checked by `LegacyMigrationTest`
(`data/src/test/resources/v1-device/`).
