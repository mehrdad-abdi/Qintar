# Releasing

Every push to `main` runs `.github/workflows/release.yml`, which builds a signed release APK and
publishes it as a GitHub release tagged `v<versionName>`. The pull-request workflow refuses a PR
that does not increase `versionCode`, and the release fails if the tag already exists, so bump
both `versionCode` and `versionName` in `app/build.gradle.kts`.

## One-time setup: the signing key

An APK can only update an installed copy that was signed with the same key. The release key is
`~/keys/qintar-release.jks` (alias `qintar`). Keep the file and its password in a password manager
and a copy off this computer: losing either means users must uninstall to update. Google Play
refuses debug-signed uploads, so never release with a debug key.

The scripts in `tools/release/` need only the keystore and its password (they ask for it; it is
never printed or saved):

| Script | What it does |
|---|---|
| `tools/release/new-release-key.sh` | creates the key (once; refuses to overwrite) |
| `tools/release/set-github-secrets.sh` | checks the password, then uploads the four repository secrets the release workflow uses (`QINTAR_KEYSTORE_BASE64`, `QINTAR_KEYSTORE_PASSWORD`, `QINTAR_KEY_ALIAS`, `QINTAR_KEY_PASSWORD`) |
| `tools/release/build-signed.sh` | builds the signed APK and the Play bundle (`.aab`) locally and prints the signature |

Each takes an optional keystore path (and `build-signed.sh` an alias) to use another key.

Without the signing variables `assembleRelease` still succeeds but produces an unsigned APK, which
is what the pull-request workflow builds to prove that the release configuration compiles.

## Smoke-test every release build on a device

Release builds are shrunk by R8, which renames classes; debug builds and unit tests do not see
that. Two release-only crashes were found this way (navigation enum arguments, DataStore protobuf
fields; see `app/proguard-rules.pro`). Install the signed APK on an emulator or phone and at least:
open Today, start a khatm, open the reader and tick an ayah, play audio, open Read, Bookmarks,
Insights and Settings, change a setting.

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
