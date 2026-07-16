# Security

How Tokn protects your data: the cryptography, the key handling, the sync
protocol, and what Tokn does not protect against. Everything here reflects
the actual implementation in this repository. File paths are given so you
can check every claim against the code.

## Reporting a vulnerability

If you think you found a security issue, please report it privately first:

- Preferred: [GitHub private vulnerability reporting](https://github.com/tokn-authenticator/tokn/security/advisories/new)
- Or by email: security@usetokn.app

Please do not open a public issue for anything that could put users at risk
before a fix is out. You can expect a first response within a few days.
There is no bug bounty, this is a free-time open-source project, but reports
are taken seriously and credited if you want.

## The short version

All persistent data lives in a Room database encrypted with SQLCipher. The
database passphrase is a random 32-byte master key that is never written to
disk in plaintext. The master key is wrapped in one or more slots: password,
biometric, or a convenience slot backed by the Android Keystore. It is held
in memory only while the vault is unlocked. Everything that leaves the
device (backups, device-to-device sync) is end-to-end encrypted with keys
derived from a password or a pairing code. There is no server, no account,
and no cloud in any code path.

## Data at rest

Code: `core/security/src/main/java/me/diamondforge/tokn/security/`

- The vault is an SQLCipher database. `core/data` wires
  `SupportOpenHelperFactory` into Room, and the passphrase is the 32-byte
  master key from `VaultSession`, generated with `SecureRandom`.
- The master key is stored only in wrapped form, using a slot system
  (`vault/Slot.kt`, `vault/VaultManager.kt`):
  - **Password slot**: the key is wrapped with AES-256-GCM using a key
    derived from your password via Argon2id (version 1.3, 46 MiB memory,
    3 iterations, 2 lanes, see `crypto/Argon2KeyDeriver.kt`, built on
    Bouncy Castle).
  - **Biometric slot**: the key is wrapped by an AES-256-GCM key in the
    Android Keystore created with `setUserAuthenticationRequired(true)`.
    The wrapping key only works after a successful `BiometricPrompt`
    authentication.
  - **No-auth Keystore slot**: if you have not set a password, the master
    key is wrapped by a non-auth Android Keystore key so the vault opens
    without a prompt. In this mode, at-rest protection is whatever the
    Android Keystore provides on your device. Convenient, but weaker than
    a password.
- Setting a password removes the no-auth slot. Once a password exists,
  nothing on the device can unlock the vault without the password or an
  enrolled biometric. Removing the password brings the convenience slot
  back and deletes the biometric key.
- The unwrapped master key lives only in process memory while the vault is
  unlocked (`vault/VaultSession.kt`) and is zeroed with `Arrays.fill` on
  lock and after every wrap and unwrap. Zeroing managed memory is best
  effort, see the limitations section.
- Password changes and unlock attempts, successful and failed, land in the
  local audit log.

## Backups

Code: `core/backup/EncryptedBackupManager.kt`,
`core/security/EncryptionManager.kt`

- Encrypted backups are JSON envelopes carrying ciphertext produced with
  AES-256-GCM. The key comes from your backup password via Argon2id with
  the same parameters as the vault. The salt is 16 random bytes, and the
  KDF parameters are recorded in the file so future versions can change
  defaults without breaking old backups.
- Backups made by old versions that used PBKDF2-HMAC-SHA256 (310,000
  iterations) still restore. New backups always use Argon2id.
- A backup is exactly as strong as the password you pick for it. Tokn
  cannot recover a backup with a forgotten password. There is no escrow.
- Backup files are written through the Android Storage Access Framework to
  a location you pick. Tokn does not upload them anywhere.

## Device-to-device sync

Code: `feature/sync/src/main/java/me/diamondforge/tokn/sync/`

Sync moves accounts between two phones without any server. Wi-Fi and Wi-Fi
Direct share one protocol. The QR transport works differently.

**Local Wi-Fi and Wi-Fi Direct** (`crypto/Handshake.kt`,
`crypto/Pairing.kt`, `crypto/SecureChannel.kt`):

- The two devices run a J-PAKE password-authenticated key exchange (Bouncy
  Castle, NIST 3072-bit group, SHA-256) over a random six-digit pairing
  code shown on one screen and typed on the other.
- The code itself is never transmitted. An eavesdropper on the network
  learns nothing useful from the handshake, and an active man-in-the-middle
  without the code makes the key confirmation round fail. The transfer
  aborts before any account data is sent.
- The shared secret is expanded with HKDF-SHA256 into two independent
  directional AES-256 keys. All application frames are AES-256-GCM with a
  fresh random 96-bit nonce per frame.
- The pairing code is generated per session and used once.

**Animated QR** (`qr/`, `ui/SendViewModel.kt`): fully offline, nothing goes
over any network. The payload is gzip-compressed, encrypted with Argon2id
and AES-256-GCM under a passphrase you choose, then split into QR frames.
Anyone who films your screen still needs the passphrase.

Sync transfers issuers, account names, secrets, algorithm parameters,
groups and icons. It does not transfer usage statistics or device-local
settings.

## App hardening

- Screenshot protection (`FLAG_SECURE`) keeps codes out of the recents
  preview and blocks screen capture. On by default, can be turned off in
  settings.
- Permissions: `CAMERA` for QR scanning, `USE_BIOMETRIC` for vault unlock,
  and `INTERNET` only for optional service-icon fetching, which is off by
  default. Codes are always generated locally. Tokn does not request
  storage permissions; file access goes through the Storage Access
  Framework, so it only ever sees files you explicitly pick.
- No Google Play Services dependency, no telemetry, no crash reporting, no
  analytics. There is no code path that phones home.
- A local audit log records vault unlocks, account changes, backup and
  sync activity, with configurable retention.

## Threat model

Tokn is designed to protect against:

- **Theft of the locked device or its storage.** The vault is SQLCipher
  ciphertext. With a password set, offline brute force has to go through
  Argon2id at 46 MiB per guess.
- **Network attackers during sync.** An active man-in-the-middle without the pairing code
  cannot complete the handshake.
- **Cloud provider breaches, SIM swapping, phone number leaks.** Not
  applicable by construction.
- **Screen capture and the app switcher**, via `FLAG_SECURE`.

Tokn does not protect against:

- **A compromised device.** On a rooted, jailbroken, or malware-infected
  phone, an attacker with enough privileges can read process memory or
  tamper with the app. The Android Keystore guarantees no longer hold
  either.
- **An attacker who has your unlocked phone, your vault password, or can
  coerce a biometric unlock.** Whoever can open the vault sees the codes.
  That is what the vault is for.
- **Weak passwords.** Argon2id slows guessing down, it cannot make a
  guessable password strong. This applies to the vault, backups, and QR
  sync alike.
- **Compromise of the services you log into.** Tokn secures the second
  factor, nothing beyond it.

## Known limitations

- **No third-party audit yet.** The cryptographic primitives are standard
  and come from Bouncy Castle and SQLCipher rather than being hand-rolled,
  and this document exists to make review easy. An audit would still be
  welcome, and we will cooperate with anyone willing to do one.
- **Memory zeroing is best effort.** Kotlin runs on a garbage-collected
  runtime, so copies of secrets can survive in memory longer than we would
  like, despite the deliberate `Arrays.fill` wiping of key material.
- **The no-auth convenience mode trades security for usability.** Without a
  vault password, at-rest security reduces to the Android Keystore and your
  device lock. Set a password if your threat model includes someone getting
  hold of your powered-off phone. Recommended to set a password, which disables teh no-auth slot.
- **Six-digit pairing codes are short on purpose**, they get typed by hand,
  once. J-PAKE limits an active attacker to one online guess per session,
  after which the session aborts. The code is never reused.

## Cryptography

| Purpose | Primitive |
|---|---|
| Vault database | SQLCipher (AES-256) |
| Key derivation (vault, backups, QR sync) | Argon2id v1.3, 46 MiB, t=3, p=2 |
| Key wrapping and data encryption | AES-256-GCM, random 96-bit IV, 128-bit tag |
| Biometric binding | Android Keystore, `setUserAuthenticationRequired(true)` |
| Sync handshake | J-PAKE (NIST 3072, SHA-256) plus HKDF-SHA256 |
| Sync channel | AES-256-GCM, per-direction keys, per-frame nonce |
| Legacy backup restore | PBKDF2-HMAC-SHA256, 310k iterations (decrypt only) |
| OTP generation | TOTP (RFC 6238) / HOTP (RFC 4226), SHA-1/256/512 |
