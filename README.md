<div align="center">
Bike Companion

An independent Android companion app for TVS Apache motorcycles with SmartXConnect. Live dashboard, ride recording and reports, fuel and service tracking, and Google Maps directions on your bike's cluster. No account, no cloud.

Build Latest release Show Image

⬇ Download the latest APK

All releases · Changelog · Protocol notes

</div>
Why I built this

My bike's dashboard knew more than its app would tell me. For me the official app often failed to connect, and even when it did it showed less than the bike was transmitting. So I decoded the Bluetooth link and built an independent, open-source app that talks to the cluster directly. No account, no cloud.

Unofficial. This app is not made, endorsed or supported by TVS Motor Company. "TVS", "Apache", "RTR" and "SmartXConnect" are trademarks of their owner and are used here only to say which bikes the app works with. Use it at your own risk, and never handle your phone while riding.

Quick start
Download the APK on an Android phone (8.0 or newer).
Switch the ignition on and force-stop the official TVS Connect app. The cluster takes one connection at a time.
Open Bike Companion, go to More → Find my bike and tap the device named TVSBT….

Full steps, permissions and fuel calibration are under Install.

Contents
Why I built this
Quick start
Features
Design
Supported bikes
Help wanted: testers
Install
Troubleshooting
Known limitations
How it works
Permissions and privacy
Project workflow
Code layout
Building from source
Contributing
Legal notes
References
Credits and third-party licences
License
Features
Tab	What you get
Ride	Animated speedometer with RPM arc and gear; trips A, B and F; mileage; engine and intake temperature; battery voltage; throttle; fuel left and range; live alerts; ride mode with its top speed; indicator, high-beam, side-stand, clutch, brake and Info switch status
Map	Live map that follows you, the ride being recorded, where you parked, spots saved with the Info switch, and "Navigate to bike"
Rides	Every ride recorded automatically. Reports with distance, times, average and top speed, fuel used and cost, RPM, temperatures, battery, route coloured by speed, graphs, gear and ride-mode usage and a riding-style score. CSV and GPX export
Garage	Fuel: refuel log, real mileage per tank, cost per km, fuel left and range. Service: reminders by km and days, service history with costs. Battery: health from resting, starting and charging voltage. Stats: week, month, all-time, records
Settings	Appearance (light, dark or follow the phone), connection, Info switch actions, ride-mode names, cluster display options, alerts, recording, backup and restore, switch learning, diagnostics

On the bike's cluster: Google Maps turn-by-turn (arrow, distance, instruction), caller name and missed-call count, WhatsApp and SMS sender, song title, overspeed warning, rider-name greeting.

Alerts: engine overheating, battery not charging or overcharging, engine and ABS fault codes, reserve fuel, side stand down while moving, indicator left on.

Info switch, your way: choose what a short, double and long press of the handlebar Info switch does: range, fuel, ride so far or time on the cluster; save the spot; next / previous song or play / pause; nearest petrol pump; directions on/off. During a call a short press declines it, and on reserve a long press finds a pump. Teach the app the switch once under More → Learn bike switches.

Also: key-off detection with a "Ride ended?" prompt, home-screen widget, backup and restore of all records.

Design

Bike Companion is designed to feel like part of the bike: calm until something matters, glanceable while riding, deep when parked. Dark and light themes, type set in Space Grotesk and Manrope, and every animation hand-built on Compose (no animation libraries), from the ignition sweep when the bike connects to the liquid tab bar.

User stories: who uses the app and what for.
Design system: colour, type, spacing, components and motion rules.
AGENTS.md: working rules for contributors and AI agents.

Fonts: Space Grotesk and Manrope, both under the SIL Open Font Licence (licences).

Supported bikes

Pick your model in Settings > Your bike model (Apache RTR 160 4V is the default). The app then shows only what that model sends.

Model	Status
Apache RTR 160 4V (SmartXConnect, LCD cluster)	Tested
Raider 125 (SmartXConnect)	Tested
Apache RTR 160 2V, Apache RTR 180	Should work, not tested yet
Ntorq 125, Jupiter (SmartXConnect)	Should work, not tested yet
Apache RR 310, RTR 310, Ronin, iQube, TVS X, Orbiter	Not supported yet (different connection)

What each model sends, and why the model choice matters: docs/MODELS.md.

Help wanted: testers

Tested so far: Apache RTR 160 4V and Raider 125. These should work but nobody has tested them yet:

Apache RTR 160 2V
Apache RTR 180
Ntorq 125
Jupiter

If you ride one, connect, ride briefly, then share a log (More → Diagnostics & log → Log → Share) and open an issue with your bike model. Remove street names from the log first. A short "works" or "doesn't work" is useful too.

Install
Download the APK on an Android phone (Android 8.0 or newer).
Open it and allow Install unknown apps when asked.
Allow Bluetooth, location and notifications on first launch.
Switch the ignition on, force-stop the TVS Connect app (the cluster takes one connection at a time), then More → Find my bike and tap the device named TVSBT….
For directions and call alerts: More → Allow notification access.

Updating: builds are signed with a temporary key, so uninstall the old version before installing a new one. Back up first with More → Your data → Back up, then restore after installing.

Fuel calibration: fill the tank completely and log it in Garage → Fuel, ride with the app connected, then fill completely again. From then on fuel used, fuel left and range are measured, not estimated.

Troubleshooting

It won't connect. The cluster allows only one connection at a time. Force-stop the official TVS Connect app, switch the ignition on, then use More → Find my bike. Check that Bluetooth, location and notifications are allowed.

I see the wrong or missing values. Pick your bike under Settings → Your bike model. The app shows only what that model sends. If the frames don't match the model you chose, the Ride screen suggests checking it.

No directions or call alerts on the cluster. Turn on More → Allow notification access. Directions come from Google Maps notifications, so Maps needs to be navigating.

An update won't install over the old version. Builds are signed with a temporary key. Back up first under More → Your data → Back up, uninstall the old version, install the new one, then restore.

Something looks wrong on my bike. Ride briefly, then go to More → Diagnostics & log → Log → Share and open an issue with your bike model and the log. Remove street names first.

Known limitations
Apache RR 310, RTR 310, Ronin, iQube, TVS X and Orbiter are not supported yet. They use a different connection.
Builds use a temporary signing key, so updates need an uninstall first.
Map tiles come from OpenStreetMap and are meant for personal use.
How it works
                 Bluetooth LE (encrypted-looking, actually XOR-scrambled frames)
  ┌───────────┐ ─────────────────────────────────────────────▶ ┌──────────────┐
  │  Phone    │   phone status every 2 s, directions,          │  Bike        │
  │  Bike     │   caller / message text, rider name            │  cluster     │
  │ Companion │ ◀───────────────────────────────────────────── │ SmartXConnect│
  └───────────┘   speed, RPM, gear, odometer, trips,           └──────────────┘
        ▲         temperatures, battery, fuel, lamps
        │
        │ notifications (read-only)
  ┌─────┴──────┐
  │ Google Maps│  turn-by-turn text  ──▶  arrow code + distance + text
  │ Dialer, SMS│  caller / sender    ──▶  cluster message line
  └────────────┘
The cluster sends 20-byte frames about four times a second. On Apache clusters the payload is XOR-scrambled with a one-byte key; the app finds the key from each frame's checksum, so it does not need to know the exact model.
Frame layouts, the navigation frames and everything still unknown are in docs/PROTOCOL.md.
The app only sends display data. It never sends calibration, ride-mode or vehicle-setting commands.
Permissions and privacy
Permission	Why
Bluetooth (scan, connect)	Talk to the bike
Location	Record the ride route and show the live map; also needed for Bluetooth scanning on Android 11 and older
Notifications	Ride status and bike alerts
Foreground service (connected device, location)	Keep the connection and recording alive with the screen off
Notification access (granted in settings)	Read Google Maps directions, incoming calls and message senders
Internet	Only to download OpenStreetMap tiles
No account, no analytics, no ads. Nothing about you or your bike is uploaded.
Rides, fuel, service and battery records stay in the app's private storage until you share or back them up.
Logs and ride files contain your bike's Bluetooth address and, while navigating, street names. Remove them before posting a log publicly.
Project workflow
 edit code ──▶ git push ──▶ GitHub Actions ──▶ unit tests ──▶ build APK ──▶ artifact on the run
                                                    │
 git tag v1.2.3 ──▶ git push --tags ──▶ same build ─┴──▶ GitHub Release:
                                                        notes from CHANGELOG.md, APK,
                                                        checksums, source zip / tar.gz
CI (.github/workflows/build.yml) runs the JVM unit tests, builds the debug APK, checks the merged manifest for unexpected permissions, and uploads the APK. Any push to main produces a downloadable build.
Releases are made by pushing a v* tag. The release text comes from the matching section of CHANGELOG.md plus .github/release-footer.md.
Versioning: semantic versioning; bump versionCode and versionName in app/build.gradle.kts and add a changelog section before tagging.
Code layout
app/src/main/java/dev/bikecompanion/
  protocol/   frame codec, scramble detection, Apache field table, navigation and text frames
  link/       Bluetooth scanner, GATT link, foreground service, phone status
  nav/        notification reader: Google Maps, calls, messages, music
  ride/       ride recorder, statistics, CSV and GPX
  fuel/       refuel log, mileage, injection-counter calibration, refuel detection
  garage/     service reminders, battery health, switch learning
  monitor/    live alerts, battery tracking, switch status, parked location
  backup/     backup and restore
  data/       app state and settings
  log/        session log
  ui/         Compose screens, charts, maps, widget
app/src/test/ JVM unit tests for everything that does arithmetic on bytes
docs/         protocol notes
Building from source

Requirements: JDK 17, Android SDK (API 35), Gradle 8.10.

bash
gradle :app:testDebugUnitTest :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk

Or push to a fork and let GitHub Actions build it.

Optional stable signing (so updates install without uninstalling): create a keystore and add KEYSTORE_B64, KEYSTORE_PASSWORD, KEY_ALIAS and KEY_PASSWORD as repository secrets.

bash
keytool -genkeypair -keystore rtr.jks -alias rtr -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 rtr.jks > rtr.jks.b64

Never commit the keystore; keep a safe copy.

Contributing
Testing on other bikes is the most useful help. Connect, ride briefly, then More → Diagnostics & log → Log → Share, and open an issue with the bike model and the log (remove street names first).
Anything that scales, truncates or orders bytes needs a unit test in app/src/test/.
Do not add code, images or files taken from the official TVS app.
Legal notes

This is a summary for contributors and users, not legal advice.

Independent code. Everything in this repository was written for this project. It contains no code, images or other material from the official app. The official app was studied only to learn the data format the bike uses, so that this independent app can talk to it.
Interoperability. Many countries allow studying software for that purpose: India's Copyright Act, 1957, section 52(1)(ab); the EU Software Directive 2009/24/EC, article 6; and in the US, 17 U.S.C. § 1201(f). The official app's own terms of use may still restrict reverse engineering for people who accepted them.
Trademarks. TVS, Apache, RTR and SmartXConnect belong to TVS Motor Company. Naming them to say which bikes the app works with is the intended use here; the app must not suggest it is official. A neutral app name would be safer before any wide distribution, such as an app-store listing.
Safety and warranty. The app sends only display information, never calibration or settings. Riders use it at their own risk.
Maps. Map data © OpenStreetMap contributors (ODbL), credited on every map. Tiles come from openstreetmap.org under its tile usage policy, which suits personal use; heavy or commercial use needs another tile provider.
References
GATT and Bluetooth LE on Android: https://developer.android.com/develop/connectivity/bluetooth/ble/ble-overview
Notification listener: https://developer.android.com/reference/android/service/notification/NotificationListenerService
Jetpack Compose: https://developer.android.com/compose
osmdroid: https://github.com/osmdroid/osmdroid
OpenStreetMap copyright and licence: https://www.openstreetmap.org/copyright
OpenStreetMap tile usage policy: https://operations.osmfoundation.org/policies/tiles/
GPX 1.1 schema: https://www.topografix.com/GPX/1/1/
GNU GPL v3: https://www.gnu.org/licenses/gpl-3.0.html
India, Copyright Act 1957, s. 52(1)(ab): https://www.indiacode.nic.in/handle/123456789/1367
EU Directive 2009/24/EC: https://eur-lex.europa.eu/eli/dir/2009/24/oj
17 U.S.C. § 1201(f): https://www.law.cornell.edu/uscode/text/17/1201
Credits and third-party licences
Component	Licence
AndroidX, Jetpack Compose, Material 3	Apache-2.0
Kotlin, kotlinx.coroutines	Apache-2.0
osmdroid	Apache-2.0
Map data © OpenStreetMap contributors	ODbL 1.0
License

Copyright © 2026 Aditya Shinde

Bike Companion is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. It is distributed in the hope that it will be useful, but without any warranty; without even the implied warranty of merchantability or fitness for a particular purpose. See LICENSE for the full text.
