# Changelog

All notable changes to RTR Companion. Versions follow
[semantic versioning](https://semver.org/); dates are release dates.

## [0.9.0] - 2026-11-17

- **Pick your bike model** (Settings > Your bike model). Every model from the
  official app is listed, grouped as Tested (Apache RTR 160 4V, Raider 125),
  Should work (Apache RTR 160 2V and 180, Ntorq 125, Jupiter) or Not supported
  yet (310s, Ronin, electric scooters). Apache RTR 160 4V is the default, and
  connecting a bike for the first time asks which model it is.
- **Each model shows only what it sends.** Raider, Jupiter and Ntorq get their
  own data layouts, including range and fuel economy from the bike itself and
  idle stop-start counts; scooters hide the gear.
- **Fixed: Raider, Jupiter and Ntorq showed only some values.** Their frames
  carry no checksum, so the app threw away nearly all of them. The scramble is
  now learned from the data itself for these models.
- The Ride screen suggests checking the model if the data does not match it.
- New page: [docs/MODELS.md](docs/MODELS.md), what each model sends.

## [0.8.2] - 2026-11-17

- **Restore from anything you have.** Pick the backup zip, a zip you
  re-made after extracting it, or the CSV files from inside it (several at
  once). Each file is recognised by its contents. The fuel and service logs
  are merged with what is already on the phone.
- Restore now ends with a list of what was restored and, for anything
  skipped, the reason.
- The app version is shown at the bottom of Settings.

## [0.8.1] - 2026-11-17

- **Restore works with any file manager.** The file picker only offered files
  labelled exactly as zip, so many phones showed the backup greyed out. Any
  file can be picked now; the app checks it is a backup and says so if not.
  Backups from RTR Companion restore too.
- Backups now include your corrected navigation arrows and theme choice.

## [0.8.0] - 2026-11-17

**Now called Bike Companion.** RTR and Apache are TVS trademarks, so the app
has its own name and a new app id. Install it as a new app: back up in the old
version first (Settings > Your data > Back up), then restore in the new one.

- **A complete redesign.** New look built from user stories and a written
  design system: warm graphite and paper themes (dark, light or follow the
  phone), one signature "phosphor" accent that means live and good, Space
  Grotesk numerals and Manrope text, consistent spacing and cards everywhere.
- **Hand-built motion.** The ignition sweep when the bike connects, a liquid
  tab bar, odometer-style rolling numbers, a gear badge that pops on shifts,
  turn arrows that swing in, alerts that drop in (danger glows), and a radar
  while looking for the bike. Turned off when the phone's animations are off.
- **Turn arrows read from Google Maps' arrow icon.** Maps often names the turn
  only in its picture, so arrows were often wrong. The app now reads the
  icon, learns from the text when it names the turn, and lets you correct
  any arrow once (Settings > Navigation arrows).
- **Fixed:** most direction words in Maps text were not being recognised.
- **Connects by itself**: when the app opens, after the phone restarts, and in
  the background as soon as the ignition comes on.
- **Only calls and Google Maps reach the cluster** by default. Other apps are
  opted in one by one (Settings > Other apps), sender's name only.
- New ride dashboard: greeting, range as the fuel hero, directions card with
  the same arrow the cluster gets, lamps and switches as chips.

## [0.7.0] - 2026-11-13

- **Your own Info switch.** Pick what a short, double and long press of the
  handlebar Info switch does: show range, fuel, ride so far or time on the
  cluster, save the spot, next / previous song, play / pause, nearest petrol
  pump, or directions on/off. Short press declines a ringing call and long
  press finds a pump while on reserve (both can be turned off).
- **Ride modes** (Urban / Sport / Rain): live pill on the Ride screen with the
  mode's top speed from the owner's manual, time in each mode in ride reports,
  and a way to confirm which number the bike uses for each mode.
- **Clutch, brake and Info switch** indicators on the Ride screen.
- **Saved spots** appear as markers on the Map.
- **Alerts on the cluster**: FUEL LOW, SIDE STAND DOWN, HIGH ENGINE TEMP,
  CHARGING LOW, BREAK TIME.
- Service schedule, tyre pressures and fuel gauge curve now follow the owner's
  manual (service 3,000 km, oil 6,000 km, chain lube 500 km, and so on).
- Switch learner only asks for switches the app cannot already read.

## [0.6.0] - 2026-11-13

- **Release page**: release notes, install steps, versioned APK and SHA-256
  checksums on every GitHub release.
- **OpenStreetMap credit** shown on every map, as the map data licence requires.
- Licensed under **GPL-3.0**; new README with workflow, permissions, privacy,
  legal notes and references; this changelog.

## [0.5.1] - 2026-11-13

- **Map** opens on the rider straight away, using the last known position from
  every location source, instead of starting on an empty patch of ocean.
- Shows "Finding your location" and "No internet" hints instead of a blank map.

## [0.5.0] - 2026-11-13

- **New layout**: Ride, Map, Rides, Garage and More tabs.
- **Live map** with the ride being recorded, the parked bike and
  "Navigate to bike".
- **Garage**: fuel, service reminders and service history, battery health,
  weekly and monthly stats.
- **On the cluster**: caller name and missed-call count, WhatsApp and SMS
  sender, song title, overspeed warning.
- **Alerts**: overheating, not charging, overcharging, fault codes, reserve
  fuel, side stand down while moving, indicator left on.
- **Switch learning** wizard for indicators, high beam and side stand.
- **Riding style** score in ride reports.
- **Backup and restore**, **home-screen widget**.

## [0.4.0] - 2026-11-13

- **Fuel**: refuel log, automatic refuel detection, mileage from full tank to
  full tank, cost per km, monthly spend.
- Fuel left and range to empty; measured once the injection counter is
  calibrated by two full fills, estimated from the gauge until then.
- Fuel used and cost in each ride report.

## [0.3.0] - 2026-11-13

- **Ride recording** in the background while connected.
- **Key-off prompt**: save, keep riding or discard; saves automatically after
  a set time.
- **Ride reports**: distance, times, speeds, RPM, temperatures, battery,
  route on a map coloured by speed, graphs, gear usage, CSV and GPX export.
- **New design**: dark instrument theme, animated speedometer.

## [0.2.0] - 2026-11-13

- **Google Maps turn-by-turn** on the cluster: arrow, distance, instruction.
- Field fixes from the first RTR 160 4V ride: Trip F, Trip A and B, average
  mileage, engine temperature, intake temperature, battery voltage.
- One write queue for everything sent to the bike; the bike's scramble scheme
  is remembered.

## [0.1.0] - 2026-11-13

- First version: connects to the SmartXonnect cluster, detects the payload
  scramble from checksums, live values, diagnostics, shareable log.
