# AndroidSSHClient

A native Android 8.0+ SSH client built with Kotlin, Jetpack Compose, Material 3, Hilt, Room, DataStore, Coroutines/Flow and mwiede/JSch.

> Screenshots: add phone, tablet, terminal and SFTP captures under `docs/screenshots/` after installing the app on a device.

## Features

- Connection create/edit/delete/search, swipe delete and long-press multi-select
- Password, private-key and private-key plus password authentication
- Strict SSH host-key verification with explicit SHA256 trust-on-first-use; changed keys are blocked
- Multi-tab ANSI/VT terminal with cell-accurate CJK/Nerd Font layout, ANSI 16/xterm-256/TrueColor foregrounds and backgrounds, scroll, pinch scaling, clipboard actions and Ctrl/Alt/Esc/Tab/arrow keys
- Optional reconnect and a `specialUse` foreground service for user-started persistent sessions
- SFTP browse, SAF upload/download, delete and rename
- Loopback-only local port forwarding (`127.0.0.1`)
- Ed25519/RSA key generation, encrypted import, public-key export and deletion
- Dynamic color, light/dark/system themes, seven terminal schemes, importable TTF/OTF terminal fonts, size/line-height/ligature settings
- Adaptive bottom navigation/navigation rail for phones, landscape, tablets and foldables

## Architecture

The installable `app` module keeps boundaries explicit without imposing cross-module build overhead on this initial release:

```text
app/src/main/java/io/github/chenjin/androidsshclient/
├── core/
│   ├── database/       Room entities and DAOs
│   ├── logging/        redacted event logging
│   ├── model/          domain models
│   ├── security/       Android Keystore AES-GCM
│   ├── ssh/            strict host keys, sessions, SFTP, forwarding, FGS
│   ├── terminal/       native Canvas ANSI terminal
│   └── ui/             font loader and Material theme
├── data/               repository implementations
├── di/                 Hilt providers
└── feature/
    ├── connections/
    ├── terminal/
    ├── tools/           SFTP, forwarding and keys
    └── settings/
```

UI state follows MVVM. Repositories are the data boundary; screens do not access Room, JSch, Keystore or DataStore directly. Session state is exposed as `StateFlow`.

## Toolchain and dependencies

- compileSdk/targetSdk 35, minSdk 26
- JDK 17, Gradle 8.9, AGP 8.7.3, Kotlin 2.0.21
- Compose BOM 2024.12.01, Material 3, Navigation Compose
- Hilt 2.52, Room 2.6.1, DataStore 1.1.1
- mwiede/JSch 0.2.21 and Bouncy Castle 1.79
- JUnit, MockK and Turbine

Versions are centralized in `gradle/libs.versions.toml`.

## Build

Install Android SDK 35 and JDK 17, set `ANDROID_HOME` as needed, then run:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

For local release signing, copy `keystore.properties.example` to `keystore.properties`, point `storeFile` to a local keystore and fill the four values. Both files and keystores are ignored. Without release credentials, `assembleRelease` deliberately falls back to the debug signing key so CI remains buildable.

## Fonts

Runtime font assets live in `app/src/main/assets/fonts/`:

- Fira Code Nerd Font Mono v3.3.0: Regular and Bold from [Nerd Fonts](https://github.com/ryanoasis/nerd-fonts/releases), including Private Use Area icon glyphs. Fira Code has no upstream italic masters; the requested italic asset names use the corresponding regular/bold face and Android applies synthetic oblique styling when requested.
- Source Han Sans SC: Regular and Bold from [Adobe Source Han Sans](https://github.com/adobe-fonts/source-han-sans/releases), subset to Latin, punctuation, CJK extensions, Unified Ideographs and full-width forms with `pyftsubset`.
- `LICENSE-FiraCode.txt` and `LICENSE-SourceHanSans.txt` preserve the SIL Open Font License notices.

`core/ui/font/AppFonts.kt` loads fonts asynchronously from `Application`. On API 29+ it constructs a real `Typeface.CustomFallbackBuilder` chain with Fira Code first and Source Han Sans SC second; API 26-28 uses Fira Code plus Android shaping fallback. The settings screen can import a TTF/OTF file up to 20 MB into private app storage; API 29+ also chains that custom face to Source Han Sans SC. The native terminal uses fixed cell coordinates, treats CJK/emoji as double-width and Nerd Font PUA glyphs as single-width, and enables/disables `liga` through `Paint.fontFeatureSettings`.

## Security model

- SSH is encrypted by the negotiated JSch transport; deprecated `ssh-rsa` is not force-enabled.
- First contact captures and rejects the presented key before authentication. The UI displays host, port, algorithm and OpenSSH-style SHA256 fingerprint. Acceptance is persisted before a strict reconnect.
- A changed key hard-fails. This version intentionally requires deleting the affected app data/known-host record through a future explicit management UI rather than offering a dangerous one-tap replacement.
- Passwords, private-key passphrases and imported private keys are stored only as AES-256-GCM ciphertext in Room, protected by a non-exportable Android Keystore key. DataStore contains preferences only.
- Logs contain event names and a hashed host identifier. Passwords, key material and terminal plaintext are never logged.
- SFTP uses the Storage Access Framework and requests no broad storage permission. Uploads use a temporary remote name before rename.
- Local forwarding binds only to `127.0.0.1`.
- Backups are disabled. Clipboard contents remain an Android/user responsibility.

The foreground service uses `specialUse` because interactive SSH shells and tunnels are user-started, indefinite encrypted sessions rather than bounded data synchronization. Publishing through Google Play requires declaring and justifying this subtype during review. Android background-start restrictions still apply.

## CI/CD

`.github/workflows/android.yml` runs for pushes to `main`/`master`, pull requests, manual dispatches and `v*` tags. It sets up JDK 17/Android SDK, restores Gradle caches, runs unit tests and lint, builds a release APK, and uploads it as an Actions artifact. A tag such as `v1.0.0` also creates a GitHub Release with generated notes and attaches the APK.

Configure these repository Actions secrets for production signing:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w 0 androidssh-release.jks` output |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Signing key alias |
| `KEY_PASSWORD` | Signing key password |

Trigger manually from **Actions > Android CI > Run workflow**, or publish a release:

```bash
git tag v1.0.0
git push origin v1.0.0
```

Download ordinary builds from the workflow run's **Artifacts** section and release builds from the repository **Releases** page.

## Publishing the repository

`scripts/publish.sh` creates the initial commit when needed. If authenticated GitHub CLI is available, it creates/pushes `AndroidSSHClient`; when an `origin` already exists it pushes `main`. Otherwise it prints the exact manual push command.

```bash
chmod +x scripts/publish.sh
./scripts/publish.sh
```

## Known limitations

The built-in terminal implements the shell-focused VT/ANSI subset, including cursor movement, erase commands, SGR attributes, xterm-256 colors, TrueColor, OSC filtering and live PTY sizing. It does not yet implement every DEC private mode or alternate-screen behavior; complex full-screen applications may require a future integration with a complete terminal engine. Host-key rotation currently requires explicit data maintenance rather than in-app replacement, by design. Integration tests against a disposable SSH server and physical API 26/35 devices are recommended before production rollout.

## License

Application source is provided under the Apache License 2.0 in `LICENSE`. Bundled fonts retain their own SIL OFL 1.1 licenses in the assets directory.
