# RTR Companion

An independent Android app for **TVS Apache motorcycles with SmartXonnect**
(Bluetooth instrument cluster), built first for the Apache RTR 160 4V.

## Download

**[Download the latest APK](https://github.com/Adi9123/RTRCompanion/releases/latest/download/RTRCompanion.apk)**
(Android 8.0 or newer). All versions are on the
[Releases page](https://github.com/Adi9123/RTRCompanion/releases).

Open the file on the phone and allow "Install unknown apps" when asked. Until
the project has a stable signing key, uninstall the previous version before
installing a new one.

## About

It talks to the cluster directly over Bluetooth Low Energy. There is no
account, no cloud and no internet permission; the build fails if one sneaks in.

## What it does

| Screen | |
| :--- | :--- |
| **Ride** | Speed, RPM, gear, odometer, engine temperature, battery voltage, fuel, trip, range, throttle, fault codes |
| **Diagnostics** | Connection health (state, signal, drops and their reasons), decoding status, and every frame the bike sends as a byte grid with changing bytes highlighted |
| **Log** | Every frame in and out with timestamps, shareable as a file |
| **Bike** | Scan, connect, auto-reconnect, settings, rider name on the cluster |

A `—` means the bike has not sent that value. The app never shows a made-up
`0`.

## How the app works

- The cluster is found by name (`TVSBT…`); it usually advertises no service UUID.
- Apache clusters scramble their data with a one-byte XOR. The app detects the
  scheme automatically from each frame's checksum, so it does not need to know
  the exact Apache variant.
- While connected, the app sends the same 2-second phone-status frame the
  official app sends. It never sends calibration or vehicle-setting commands.

Details: [docs/PROTOCOL.md](docs/PROTOCOL.md).

## Builds

Every push to `main` builds and tests the app on GitHub Actions; the APK of any
commit is attached to its run as the **RTRCompanion-apk** artifact. Pushing a
tag such as `v0.1.1` publishes that build as a GitHub Release, which is what the
download link above points to.

### Updates without uninstalling (optional)

Without a signing key, each CI build is signed with a different throwaway key,
so you must uninstall the old build before installing a new one. To avoid that,
create a key once and add it as repository secrets:

```bash
keytool -genkeypair -keystore rtr.jks -alias rtr -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 rtr.jks > rtr.jks.b64
```

Secrets (Settings → Secrets and variables → Actions): `KEYSTORE_B64` (contents
of `rtr.jks.b64`), `KEYSTORE_PASSWORD`, `KEY_ALIAS` (`rtr`), `KEY_PASSWORD`.
Keep `rtr.jks` safe and out of the repository.

## First test at the bike

1. Force-stop the TVS Connect app; the cluster accepts one connection at a time.
2. Ignition on.
3. **Bike** tab → Scan → tap the device named `TVSBT…`.
4. **Diagnostics**: frames should start arriving within a few seconds, and
   "Bike's scramble" should show a detected scheme.
5. Compare the **Ride** screen with the cluster: speed, odometer, gear, RPM.
6. **Log** → Share log file, so the session can be analysed.

## Privacy

Logs contain the bike's Bluetooth address. They stay on the phone unless you
share them. Do not commit logs or captures to this repository.
