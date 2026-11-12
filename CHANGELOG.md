# Changelog

All notable changes to RTR Companion. Versions follow
[semantic versioning](https://semver.org/); dates are release dates.

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
