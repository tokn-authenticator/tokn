<img align="left" width="80" height="80" src="fastlane/metadata/android/en-US/images/icon.png"
alt="App icon">

# Tokn

<br>

[![Build](https://github.com/fthomys/tokn/actions/workflows/build.yml/badge.svg)](https://github.com/fthomys/tokn/actions/workflows/build.yml?query=branch%3Amain)
[![License: GPL-3.0-or-later](https://img.shields.io/badge/license-GPL--3.0--or--later-blue.svg)](LICENSE)

A small, opinionated 2FA / MFA authenticator for Android. Your one-time codes
stay on the device, encrypted. No sign-up, no sync server, no analytics.

## Why Tokn?

- **It minds its own business.** No account, no telemetry, no Google Play
  Services on your device.
- **Encrypted by default.** The local vault is stored in an SQLCipher database;
  unlock with biometrics (with a password fallback) or a password alone.
- **Hide on demand.** Optional screenshot protection keeps codes out of the
  recents preview and blocks screen capture.
- **Standards based.** TOTP and HOTP per [RFC 6238][totp-rfc] and
  [RFC 4226][hotp-rfc], with SHA-1, SHA-256 and SHA-512. Compatible with
  Google Authenticator URIs.
- **Adding a code is quick.** Scan a QR code with the camera, pick a QR image
  from the gallery, or type the secret in by hand.
- **Move between phones without a server.** Encrypted device-to-device sync
  over QR codes, the local Wi-Fi network, or Wi-Fi Direct. The handshake is
  end-to-end encrypted and nothing leaves the local network.
- **Bring it with you.** Encrypted backup and restore for migrating or just
  feeling safer.
- **Pleasant to look at.** Material 3, built with Jetpack Compose.

[totp-rfc]: https://www.rfc-editor.org/rfc/rfc6238
[hotp-rfc]: https://www.rfc-editor.org/rfc/rfc4226

## Install

Tokn is available on Google Play:

[<img height="80" alt="Get it on Google Play"
src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png"
/>](https://play.google.com/store/apps/details?id=me.diamondforge.tokn)

<!--
F-Droid submission is in progress; uncomment once the app is published.

[<img height="80" alt="Get it on F-Droid"
src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
/>](https://f-droid.org/app/me.diamondforge.tokn)
-->

Signed APKs are also attached to each
[GitHub Release](https://github.com/fthomys/tokn/releases) if you prefer
sideloading.

## A look around

<p>
<img width=200 alt="Vault with active TOTP codes"
src="fastlane/metadata/android/en-US/images/phoneScreenshots/vault-home.png?raw=true">
<img width=200 alt="Settings overview"
src="fastlane/metadata/android/en-US/images/phoneScreenshots/settings.png?raw=true">
<img width=200 alt="Appearance settings"
src="fastlane/metadata/android/en-US/images/phoneScreenshots/appearance-settings.png?raw=true">
</p>
<p>
<img width=200 alt="Security settings, vault unencrypted"
src="fastlane/metadata/android/en-US/images/phoneScreenshots/security-disabled.png?raw=true">
<img width=200 alt="Security settings with vault encryption and biometrics enabled"
src="fastlane/metadata/android/en-US/images/phoneScreenshots/security-enabled.png?raw=true">
<img width=200 alt="Backup and restore"
src="fastlane/metadata/android/en-US/images/phoneScreenshots/backup-restore.png?raw=true">
</p>
<p>
<img width=200 alt="Picking an import source"
src="fastlane/metadata/android/en-US/images/phoneScreenshots/import-source-picker.png?raw=true">
<img width=200 alt="Sync send/receive choice"
src="fastlane/metadata/android/en-US/images/phoneScreenshots/sync-send-receive.png?raw=true">
<img width=200 alt="Choose sync method"
src="fastlane/metadata/android/en-US/images/phoneScreenshots/sync-methods.png?raw=true">
</p>

## Permissions, in plain English

| Permission | What it's for |
|------------|---------------|
| `CAMERA` | Reading QR codes when you add an account or sync between devices. |
| `INTERNET` | Optional. Fetching service icons. Disabled by default; opt in from Settings. |
| `USE_BIOMETRIC` / `USE_FINGERPRINT` | Unlocking the vault with biometrics. |

Tokn does not request `READ_EXTERNAL_STORAGE`. Gallery imports go through the
Storage Access Framework, so it only sees the one file you pick.

## Build it yourself

```
./gradlew assembleRelease
```

The release variant is signed with a keystore that you point at via
`local.properties`:

```
KEYSTORE_FILE=path/to/keystore.jks
KEYSTORE_PASSWORD=...
KEY_ALIAS=...
KEY_PASSWORD=...
```

If you skip the keystore config the release build is produced unsigned, which
is what F-Droid does. For a quick debug build just run
`./gradlew assembleDebug`.

## Verifying a GitHub release

APKs from the GitHub Releases page are signed with the same upload key. To
check, run:

```
apksigner verify --print-certs --verbose tokn.apk
```

You should see something like:

```
Verifies
Verified using v1 scheme (JAR signing): true
Verified using v2 scheme (APK Signature Scheme v2): true
```

with these certificate fingerprints:

```
Owner: CN=Fabian Thomys, OU=DiamondForgeLabs, O=DiamondForgeLabs, L=Nuremberg, ST=Bayern, C=DE
Serial number: b1c270cf28aff4e3
Valid from: Mon Apr 06 00:30:58 CEST 2026 until: Fri Aug 22 00:30:58 CEST 2053
   SHA1:   07:89:32:7A:A5:6F:80:21:BA:C3:2E:BE:77:75:FC:EF:EC:AA:E3:42
   SHA256: 21:38:B7:30:0C:EC:84:29:76:A5:FC:6E:48:29:4B:7E:C1:B1:7B:3F:F0:67:23:74:F8:27:60:BB:05:84:DD:58
```

Note: F-Droid signs builds with its own key, and Google Play re-signs uploads
through Play App Signing. The fingerprints above apply only to the APKs
attached to GitHub Releases.

## Bugs, ideas, patches

Please open an issue or PR on
[GitHub](https://github.com/fthomys/tokn). Translations are welcome too.

## License

Tokn is free software, released under the GNU General Public License v3.0 or
later. Full text in [LICENSE](LICENSE).

```
SPDX-License-Identifier: GPL-3.0-or-later
```
