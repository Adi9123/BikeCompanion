# Design system

The look, feel and motion of Bike Companion. The code is the source of truth:
tokens live in `ui/Theme.kt`, motion in `ui/Motion.kt`, shared components in
`ui/Common.kt`. This document explains them and the reasons behind them. Read
[USER_STORIES.md](USER_STORIES.md) first; every rule here serves a story there.

## Concept: an instrument, not an app

A good motorcycle cluster is calm until it matters. It has one light that means
"all is well" and saves colour and movement for things the rider must notice.
At key-on it sweeps its needle once to show it is alive, and then it gets out of
the way.

Bike Companion borrows that personality:

- **Calm surfaces**, warm graphite at night and warm paper in daylight, never
  pure black or pure white.
- **One signature light**, *Phosphor*: an electric lime borrowed from LCD
  backlights. It means *live, on, good*. It is never used as decoration.
- **Numbers as heroes.** The type is chosen for its numerals first.
- **The ignition sweep** is the brand's signature moment, played once per
  connection, like the bike's own.

## Principles

From the [Figma UI design principles](https://www.figma.com/resource-library/ui-design-principles/),
applied to a phone on a motorcycle:

| Principle | How it applies here |
| :--- | :--- |
| **Hierarchy** | One hero per screen: speed on Ride, the route on Map, the latest ride on Rides. Size first, then weight, then colour. |
| **Progressive disclosure** | Live screens show the essentials; detail is one tap away (ride report, diagnostics). Settings group into pages, and rarely-used options sit under *Advanced*. |
| **Consistency** | Every card, row, chip and button comes from `Common.kt`. A toggle row looks and behaves the same on every page. |
| **Contrast** | The primary action on a screen is the only filled Phosphor button. Secondary actions are tonal or text. Danger is the only red. |
| **Accessibility** | Body text 15 sp and up; contrast at least 4.5:1 for text and 3:1 for large numerals and icons, in both themes; 48 dp minimum targets; colour is never the only signal (icons and words go with it); reduced motion is honoured. |
| **Proximity** | Related values share a card (fuel left and range); unrelated groups get 24 dp between them, related items 8 to 12 dp. Destructive actions sit apart from frequent ones. |
| **Alignment** | One 20 dp screen gutter, a 4 dp grid, left-aligned text, numerals aligned on their baseline. |

Our own additions:

- **Glance-first.** Anything the rider needs while moving must be readable in
  one second at arm's length.
- **Honest data.** Unknown is `—`, never `0`. Estimates say "about".
- **Glove-proof.** Primary actions are 56 dp tall and in the lower half.

## Colour

Two palettes with the same roles. Components ask for a role (`Tone.Accent`),
never a hex value, so both themes follow automatically.

| Role | Dark | Light | Use |
| :--- | :--- | :--- | :--- |
| `Bg` | `#0F1012` carbon | `#F4F2ED` paper | Screen background |
| `Panel` | `#18191C` | `#FFFFFF` | Cards |
| `Panel2` | `#222327` | `#ECE9E2` | Insets, tracks, chips |
| `Line` | `#2C2D32` | `#DEDAD0` | Hairlines and outlines |
| `Text` | `#F3F2EC` | `#151513` | Primary text |
| `Muted` | `#94938C` | `#68665F` | Secondary text |
| `Accent` | `#CDF545` phosphor | `#CDF545` | The signature light: live, on, primary action |
| `OnAccent` | `#141500` | `#141500` | Text and icons on Accent |
| `AccentText` | `#CDF545` | `#4A6400` | Accent used *as text* (lime is unreadable on paper) |
| `Amber` | `#FFB547` | `#A35F00` | Attention |
| `Red` | `#FF5D4D` | `#CC3524` | Danger and the rev limit only |
| `Blue` | `#7CC4FF` | `#1E67B0` | Information, secondary data |
| `Violet` | `#B8A4FF` | `#6447D1` | Tertiary data series |
| `Green` | `#5ED891` | `#1E8A4E` | Healthy (battery, charging) |

Rules:

- **Phosphor is earned.** Only the live link, the primary action of a screen,
  the selected tab and "good" data may use it.
- **Tinted containers, not loud fills.** Alerts use their colour at 14 to 18%
  opacity behind full-strength text.
- **No gradients for decoration.** A gradient is allowed only where it encodes
  data (the rev sweep getting hotter).

## Type

| Family | Use | Why |
| :--- | :--- | :--- |
| **Space Grotesk** (variable) | Numerals, screen titles, big values | Distinctive, technical numerals with real character; reads like an instrument, not a spreadsheet |
| **Manrope** (variable) | Everything you read: body, labels, buttons | Open, modern grotesk; excellent legibility at small sizes and on sunlit screens |

Both are OFL-licensed and bundled in `res/font/` (licences in
[`fonts/`](fonts/)). Numerals use tabular figures (`tnum`) wherever values
change, so digits do not shift as they update.

| Style | Family / weight | Size / line | Tracking | Use |
| :--- | :--- | :--- | :--- | :--- |
| `hero` | Space Grotesk 700 | 96 / 96 | -4% | Speed |
| `displayLarge` | Space Grotesk 700 | 56 / 60 | -3% | One big number per card |
| `displayMedium` | Space Grotesk 600 | 40 / 44 | -2% | Report headline numbers |
| `headlineLarge` | Space Grotesk 600 | 30 / 36 | -2% | Screen titles |
| `headlineSmall` | Space Grotesk 600 | 22 / 28 | -1% | Card titles with a number |
| `titleMedium` | Manrope 700 | 17 / 24 | 0 | Row and card titles |
| `bodyLarge` | Manrope 500 | 16 / 24 | 0 | Main text |
| `bodyMedium` | Manrope 500 | 15 / 22 | 0 | Descriptions |
| `bodySmall` | Manrope 500 | 13 / 18 | +0.5% | Hints, footnotes |
| `labelLarge` | Manrope 700 | 15 / 20 | +0.5% | Buttons |
| `labelMedium` | Manrope 700 | 13 / 16 | +2% | Chips, tile labels |
| `labelSmall` | Manrope 700 | 11 / 14 | +6% | Overlines (uppercase) |

## Space, shape, depth

- **Grid:** 4 dp. Spacing scale `4, 8, 12, 16, 20, 24, 32, 48`.
- **Gutter:** 20 dp left and right on every screen.
- **Card padding:** 20 dp. Between cards: 12 dp. Between sections: 28 dp.
- **Radii:** chips 12, controls 16, cards 24, hero surfaces 32.
- **Depth:** dark mode uses stepped surfaces (Bg, Panel, Panel2) and no
  shadows. Light mode adds a hairline and a soft shadow on cards so white
  cards lift off the paper.
- **Targets:** 48 dp minimum, 56 dp for primary actions.

## Components

All in `ui/Common.kt` and `ui/Motion.kt`. Use these before writing a new one.

| Component | Purpose |
| :--- | :--- |
| `ScreenTitle` | Space Grotesk title with an optional trailing action |
| `SectionTitle` | Small uppercase overline that starts a group |
| `Panel` | The card. Squashes slightly when pressed if it has `onClick` |
| `StatTile` | Label, rolling number, unit. Fades when stale |
| `Pill`, `StatusPill`, `RecordingPill` | Status capsules with a live dot |
| `PrimaryButton`, `SecondaryButton` | The only two button styles |
| `RollingNumber` | Digits that roll like an odometer when a value changes |
| `LiveDot` | A dot that breathes while something is live |
| `Modifier.pressable` | Squash and spring-back press feedback |
| `Modifier.reveal(index)` | Staggered entrance for a list of items |

## Motion

The [12 principles of animation](https://www.userinterface.wiki/12-principles-of-animation),
built by hand on Compose's own animation primitives: no animation libraries,
no Lottie, no imported motion files. Every curve and spring below was chosen
for this app.

### Vocabulary (`Motion.kt`)

Named after what a motorcycle does, so the intent is clear in code.

| Token | Value | Use |
| :--- | :--- | :--- |
| `Motion.Tap` | 90 ms | Press feedback |
| `Motion.Quick` | 160 ms | Small state changes: chips, toggles, colour |
| `Motion.Base` | 260 ms | Most transitions; the ceiling for anything interactive |
| `Motion.Scene` | 380 ms | Whole-screen changes |
| `Motion.Ignition` | 1400 ms | The ignition sweep only |
| `Ease.Glide` | `cubic(0.16, 1, 0.3, 1)` | Entrances: arrive fast, settle softly (slow out) |
| `Ease.Launch` | `cubic(0.7, 0, 0.84, 0)` | Exits: leave with intent (slow in) |
| `Ease.Shift` | `cubic(0.65, 0, 0.35, 1)` | Things moving from A to B on screen |
| `Spring.Fork` | damping 0.72, stiffness 420 | Position and scale: a slight overshoot that settles like front forks |
| `Spring.Needle` | damping 0.55, stiffness 140 | Gauges: follows the value with real-needle inertia |
| `Spring.Snap` | damping 0.85, stiffness 900 | Press release: quick, barely-there bounce |

### The 12 principles, applied

| Principle | Where you see it |
| :--- | :--- |
| Squash and stretch | The tab indicator stretches between tabs: its leading edge leads, the trailing edge follows. Pressed cards squash to 97%. |
| Anticipation | The ignition sweep starts with a 60 ms dip below zero before it sweeps up. Long-press actions show a filling ring. |
| Staging | When an alert arrives the rest of the dashboard dims slightly, so the eye goes to the alert. |
| Straight ahead and pose to pose | Pose to pose everywhere: we define key states and let springs interpolate. |
| Follow-through and overlap | Gauges overshoot and settle (`Spring.Needle`). Rolling digits start one after another, right to left. |
| Slow in, slow out | Entrances use `Ease.Glide`, exits `Ease.Launch`, travel `Ease.Shift`. Nothing moves linearly except time itself. |
| Arcs | The turn arrow swings in along an arc in the direction of the turn. |
| Secondary action | When the bike connects, the status pill's dot pulses once while the gauge sweeps. |
| Timing | Interactions finish within 260 ms. Only the ignition sweep is longer, and it never blocks input. |
| Exaggeration | Saved for danger: a danger alert drops in with a stronger overshoot and a slow glow. |
| Solid drawing | Consistent depth: cards scale from their centre, the gauge keeps its proportions, and nothing flattens. |
| Appeal | The sum: calm by default, alive when something happens. |

### Signature moments

1. **Ignition sweep.** When the bike connects, the rev arc sweeps to the
   limit and back, and the speed digits roll, like the cluster at key-on. Once
   per connection.
2. **Liquid tab bar.** The selected-tab capsule stretches toward the new tab
   and settles; the icon pops (scale 0.86, then 1.08, then 1).
3. **Rolling numbers.** Odometer-style digit rolls for changing values.
4. **Turn arrow swing.** A new arrow swings in along an arc from the side it
   points to.
5. **Alert drop.** Alerts fall in from above with a fork-spring; danger glows.
6. **Scan radar.** Concentric rings while scanning or waiting for the bike.

### Rules

- **Motion must explain.** Show where something came from or went, confirm
  an action, or follow a real value. If it does none of these, cut it.
- **Under 260 ms for anything the rider triggers.**
- **Never block input.** Every animation can be interrupted mid-flight.
- **No infinite loops** except status that is genuinely ongoing (live dot,
  recording, scanning), and those breathe slowly (1.6 s or longer).
- **Reduced motion is respected.** When the phone's animation scale is 0,
  `Motion.reduced` is true: transitions become instant cross-fades, the
  ignition sweep is skipped and loops stop.
- **Live values do not bounce.** Speed and revs follow `Spring.Needle`, which
  settles quickly and never wobbles while readable.
