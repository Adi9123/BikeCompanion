# User stories

Who uses Bike Companion, what they are doing when they pick up the phone, and
what that means for the design. Every screen and animation should trace back to
a story here. If a design choice cannot, question the choice.

## The situation we design for

The phone is usually **not in the rider's hand**. It sits in a pocket, a tank
bag or a handlebar mount, in sunlight or at night, often touched with gloves
and always by someone whose attention belongs to the road. The bike's own
cluster is the primary screen while moving; the phone is the secondary one.

So the app has two modes of use, and they want opposite things:

| Moment | Rider's state | What the UI must do |
| :--- | :--- | :--- |
| **Glance** (riding, stopped at a signal) | One second of attention, maybe gloves | One huge number, colour that means something, nothing to read twice |
| **Sit-down** (before or after a ride, at home) | Minutes, curious, both hands | Depth on demand: reports, graphs, settings, history |

Everything below falls into one of these two.

## People

### Aditya — the daily commuter (primary)

Rides an Apache RTR 160 4V to work and back, 15 to 40 km a day, in city
traffic. Phone in a mount, Google Maps on for unfamiliar places. Has tried the
official app and stopped using it because it would not connect reliably.

- Wants to **get going without touching the phone**.
- Wants turn arrows on the cluster that are **right every time**.
- Wants to know fuel and range without doing arithmetic.
- Cares how the app looks. It should feel like part of a premium bike, not a
  settings panel.

### Meera — the weekend tourer

Rides 200 to 400 km on Sundays on highways and unknown roads. Plans fuel stops,
worries about range and about the battery on cold mornings.

- Wants **range she can trust** and the nearest petrol pump when on reserve.
- Wants a ride report worth sharing: route, distance, top speed, mileage.
- Wants to be warned early (charging fault, overheating), not surprised.

### Rahul — the careful owner

Keeps a notebook of fuel fills and service dates. Wants that notebook to fill
itself.

- Wants **real mileage per tank** and cost per km.
- Wants service reminders by km and by date, from the owner's manual.
- Wants his records backed up and never lost.

### Sam — the builder

A developer who wants to support another SmartXonnect bike, or improve this
one. Reads the protocol notes, runs the diagnostics screen, sends logs.

- Wants **every frame, live**, and a log he can export.
- Wants components and tokens he can reuse, so a new screen looks native
  without re-deciding spacing and colour.
- Wants docs that say why, not only what (`AGENTS.md`, this folder).

## Stories

Each story has acceptance criteria (how we know it works) and the design
consequence (what it forces on the UI).

### Connecting

**S1. First connection.** As Aditya, the first time I open the app I want to be
connected to my bike in under a minute, so I trust it from day one.
- Permissions are asked once, in one sequence, with a reason for each.
- The scan finds `TVSBT…` bikes without me knowing what a UUID is.
- *Design:* a single focused setup flow, one decision per screen (progressive
  disclosure); a scanning animation that shows the app is working.

**S2. Every ride after that.** As Aditya, I want the app to connect by itself
when I switch the ignition on, so I never press Connect again.
- With a remembered bike, the app connects on open, after a phone restart, and
  in the background when the bike powers up.
- *Design:* connection state is always visible but never demands action; the
  "key-on" moment is celebrated with the ignition sweep, once.

### Riding (glance)

**S3. Speed, gear and revs at a glance.** As a rider at a signal, I want the
numbers that matter in one look.
- Speed is the largest thing on screen; gear next to it; revs as a sweep.
- Values the bike has not sent show `—`, never `0`.
- *Design:* hierarchy by size, not by colour alone; tabular digits so numbers
  do not jitter; motion that follows the bike, never decorates.

**S4. Directions on the cluster.** As Aditya, I want Google Maps turns on the
cluster with the right arrow, so the phone can stay in my pocket.
- The arrow matches the turn, including when Maps names the turn only in its
  icon.
- If an arrow is ever wrong, I can correct it once and it stays fixed.
- *Design:* the phone shows the same arrow the cluster gets, so a mismatch is
  visible; a correction screen that shows the actual icons.

**S5. Only what I allowed.** As a rider, I want only calls and directions on
the cluster unless I add other apps myself, so it is never noisy.
- Defaults: calls and Google Maps on, everything else off.
- Any other app is opted in one at a time, and shows the sender only.

**S6. Warnings that I notice.** As Meera, I want overheating, charging faults,
side stand down and low fuel to grab my attention, and nothing else to.
- Danger looks and moves differently from information.
- *Design:* reserve red and motion for danger; information stays calm.

### After the ride (sit-down)

**S7. Ride report.** As Meera, after a ride I want the route, distance, time,
speeds, fuel used and how smoothly I rode, so I can see how it went.
- Rides are recorded automatically and saved at key-off (with a prompt).
- *Design:* a story-like report: headline numbers first, detail below.

**S8. Fuel and mileage.** As Rahul, I want to log a fill in three taps and see
real mileage per tank, so my notebook keeps itself.

**S9. Service.** As Rahul, I want reminders from the owner's manual schedule,
by km and date, so nothing is missed.

**S10. My data is mine.** As Rahul, I want to back up and restore everything,
so a new phone loses nothing.

### Personalising

**S11. The Info switch, my way.** As Aditya, I want to choose what short,
double and long presses of the handlebar Info switch do.

**S12. Light or dark.** As any rider, I want a light theme that reads in
sunlight and a dark one for night, following the phone by default.

### Building

**S13. See everything.** As Sam, I want live frames, decoded fields and an
exportable log, so I can work on a new bike without a debugger.

**S14. Extend it consistently.** As Sam, I want documented tokens, components
and motion rules, so my screen fits without a design review.

## Journeys

1. **Morning commute** (S2, S3, S4): ignition on → app connects in the
   background → notification shows the bike → the rider opens the app on the
   mount and sees the ignition sweep once → starts Maps → arrows reach the
   cluster → arrives → key-off → "Save this ride?" → saved.
2. **Sunday ride** (S6, S7, S8): long ride → reserve alert on phone and
   cluster → long press of the Info switch → nearest pump → fill up → log it in
   three taps → evening: ride report.
3. **Owner's Saturday** (S8, S9, S10): Garage tab → mileage trend → service
   due in 300 km → log the service → back up.
4. **Builder's evening** (S13, S14): Settings → Diagnostics → live frames →
   export the log → open an issue.

## What this means for the interface

- **Two speeds of UI.** Live screens are glance-first: few elements, very large
  numerals, no small text that matters. Everything else may be dense.
- **Thumb reach.** Primary actions live in the bottom half. Navigation is at
  the bottom. Touch targets are at least 48 dp; the main actions are 56 dp.
- **Colour has meaning.** One brand accent for "this is live and good".
  Amber means attention, red means danger. Decoration never borrows these.
- **Honest data.** Unknown is `—`. Estimates say "about". Stale values fade.
- **Motion explains.** Every animation either shows where something came from
  or went, confirms an action, or follows a real value. See
  [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md#motion).
