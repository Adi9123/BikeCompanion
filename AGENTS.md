# AGENTS.md

Working rules for anyone, human or AI, changing Bike Companion. Read this
first, then [`docs/design/USER_STORIES.md`](docs/design/USER_STORIES.md) and
[`docs/design/DESIGN_SYSTEM.md`](docs/design/DESIGN_SYSTEM.md) before touching
the UI.

## What this is

An independent Android companion for motorcycles with a TVS SmartXonnect
Bluetooth cluster (developed on an Apache RTR 160 4V). It talks to the cluster
directly over Bluetooth LE: live data, Google Maps directions on the cluster,
ride recording, fuel, service and battery history. No account, no cloud.

**The vision:** it should feel like part of a premium motorcycle, an
instrument rather than an app. Calm until something matters, glanceable while
riding, deep when parked. If a change makes the app noisier, slower to glance
at or less honest about its data, it is the wrong change.

## Non-negotiables

1. **Never send calibration, settings or engine commands to the bike.** Only
   display frames: keepalive, directions, short texts, rider name. Reading is
   always safe; writing anything new needs the owner's explicit approval.
2. **Honest data.** A value the bike has not sent is `—`, never `0`. An
   estimate says "about". Stale values fade.
3. **Privacy.** Nothing is uploaded. Internet is used only for map tiles. Never
   commit real locations, routes, street names, bike MAC addresses or personal
   details. Use `Example Road` in tests and docs.
4. **No vendor code.** Do not copy code from the official app, its decompiled
   sources or other projects' files. Work from captures, the owner's manual
   and our own tests.
5. **Trademarks.** TVS, Apache, RTR and SmartXonnect belong to TVS Motor.
   Name bikes only to say what the app works with; never use them as the app's
   name or branding.

## Design rules (short version)

The full system is in [`docs/design/DESIGN_SYSTEM.md`](docs/design/DESIGN_SYSTEM.md).

- **Use the tokens, never raw values.** Colours come from `Tone` (both themes
  follow), type from `MaterialTheme.typography` and `TypeX`, spacing from
  `Space`, shapes from `Shapes`. A hex colour or a stray `16.dp` gutter in a
  screen is a bug.
- **Use the components.** `ScreenTitle`, `SectionTitle`, `Panel`, `StatTile`,
  `Pill`, `PrimaryButton`, `SecondaryButton`, `Segmented`, `RollingNumber`,
  `LiveDot`. Add to `Common.kt` rather than styling one-offs.
- **One primary action per screen**, filled with the Phosphor accent. Phosphor
  means live, on or good. It is never decoration.
- **Glance-first live screens.** Huge numerals, few elements, nothing small
  that matters. Detail goes one tap deeper.
- **Both themes, always.** Check every change in light and dark. Light mode is
  for sunlight, so contrast matters more there.
- **48 dp minimum touch targets**, 56 dp for primary actions; riders wear
  gloves.

## Motion rules

Animations are hand-built on Compose primitives (`Animatable`,
`animate*AsState`, `AnimatedContent`, springs and easing). **No animation
libraries** (no Lottie, no imported motion files). Use the vocabulary in
`ui/Motion.kt`: `Motion` durations, `Ease` curves, `Springs`, and the
`pressable` and `reveal` modifiers.

- Motion must explain: where something came from or went, that an action
  worked, or a real value changing. Otherwise cut it.
- Anything the rider triggers finishes within `Motion.Base` (260 ms).
- Never block input; everything must be interruptible.
- Only genuinely ongoing status may loop (live dot, recording, radar), slowly.
- Respect `Motion.reduced` (phone animation scale 0): no sweeps, no loops,
  instant transitions.
- The ignition sweep is the brand moment. Do not add a second one.

## Building: CI is the compiler

The owner's laptop is for other work. **Do not run Gradle locally**, not even
for one file. Push and let `.github/workflows/build.yml` build:

```bash
git push origin <branch>
# wait for the run; read the real result, not the task order
gh run list --limit 1
gh run view <id> --log-failed | grep -E "^e: |BUILD (SUCCESSFUL|FAILED)|FAILED"
```

If any other heavy tool must run locally, keep to two cores: `nice -n 15
taskset -c 0,1`, and pass thread caps.

Before pushing, with no local compiler to catch slips:

- Check comment balance per file (`grep -c '/\*'` against `grep -c '\*/'`).
  Kotlin nests block comments, so one stray `/*` swallows the rest of a file.
- Prefer raw strings (`"""\b"""`) for regexes; escaping through shell or
  Python edits has already produced literal backspace characters once.
- Read `BUILD SUCCESSFUL` or `BUILD FAILED`; never infer success from which
  tasks ran.

## Tests

`:app:testDebugUnitTest` runs in CI before the APK is built, so a failing test
blocks the build. The protocol layer is arithmetic on byte arrays, and the
arrow reader is arithmetic on pixels; anything that scales, truncates, orders
bytes or classifies shapes needs a test in `app/src/test/`. The worst bug in
the sister project (an odometer 10x low) compiled cleanly and was only found
by a test.

## Commits and releases

- Commits are authored by the repository owner only. **Do not add
  `Co-Authored-By` or other attribution trailers.**
- Work on a branch for anything that may not compile; keep `main` green.
- A release is a `v*` tag. CI attaches `BikeCompanion.apk`, a versioned copy
  and `SHA256SUMS.txt`, with notes taken from `CHANGELOG.md`. Add the
  changelog section before tagging, and bump `versionCode` and `versionName`
  in `app/build.gradle.kts`.

## Map of the code

```
protocol/  frame codec, field table, outbound frames, navigation parser
link/      BLE link, foreground service, boot receiver, scanner
nav/       notification listener, arrow reader and store
ride/      recording, summaries, CSV and GPX
fuel/      refuels, tank maths, injector-count calibration
garage/    switches, ride modes, maintenance, battery health
monitor/   alerts, Info switch actions
ui/        Theme, Motion, Common components, TabBar, screens
docs/      protocol notes, design system, user stories
```
