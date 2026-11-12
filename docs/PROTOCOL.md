# SmartXonnect protocol: Apache clusters

This is what the app relies on. Each section says how sure we are of it. Facts
marked **expected** come from reading how the official app behaves and still
need to be confirmed on a real bike; the Diagnostics screen exists to confirm
them.

## GATT

| | UUID |
| :--- | :--- |
| Service | `5456534D-5647-5341-5342-454E544F5251` |
| Cluster → phone (notify) | `00005354-0000-1000-8000-00805f9b34fb` |
| Phone → cluster (write) | `00005352-0000-1000-8000-00805f9b34fb` |

The cluster advertises a name starting with `TVSBT`. It may not advertise the
service UUID, so the app scans without a filter. No pairing or bonding is used.

## Frame shape

Every frame is 20 bytes:

```
byte  1     2     3 ........ 18    19         20
      START ID    16 data bytes     CHECKSUM   FF
```

Positions in this document are **1-based** (the start byte is position 1).
Multi-byte values are **big-endian, unsigned**.

- Cluster → phone frames start with `5A`; phone → cluster frames start with `5B`.
- Checksum: `checksum = 255 − (sum of bytes 1..18 mod 256)`.

## Scrambling

On Apache clusters, bytes 3 to 19 (the data and the checksum) are XORed with a
one-byte key in both directions. The start byte, the id and the final `FF` are
sent in the clear.

| Key | Used by (expected) |
| :--- | :--- |
| `0xEA` | Most Apache variants, including the 4V family |
| `0xEB` | The Apache 2V family |

The app does not rely on that table: it tries every key on each incoming frame
and keeps the one whose checksum validates (see `FrameDecoder`). Outgoing
frames use the same key the bike was found to use.

## Cluster → phone

### `5A 10`: ride

| Pos | Len | Field | Scaling |
| ---: | ---: | :--- | :--- |
| 3 | 1 | Speed | km/h |
| 4 | 3 | Odometer | ÷ 10 → km |
| 7 | 1 | Fuel level | raw; scale unknown |
| 8 | 1 | Average speed | km/h |
| 9 | 1 | Mileage | km/l |
| 10 | 1 | Top speed | km/h |
| 11 | 1 | Throttle | % |
| 12 | 1 | Switch status | bit field |
| 13 | 1 | 0–60 time | raw |
| 14 | 1 | Average mileage | km/l |
| 15 | 2 | Trip F | raw |
| 17 | 2 | Engine speed | rpm |

### `5A 11`: status

| Pos | Field | Notes |
| ---: | :--- | :--- |
| 3 | Fuel sensor status | |
| 4 | Vehicle state 1 | bit field |
| 5 | Service reminder | |
| 6 | Gear | 1–6; 0 or 8 = neutral |
| 7 | Battery / vehicle state 2 | shared byte, scaling unknown |
| 8 | Cluster software version | |
| 9 | Engine fault (MIL) blink code | |
| 10 | Vehicle model code | |
| 11 | Diagnostic flags | `0x20` = fault present (expected) |
| 13 | Turn indicators | bit field |
| 14 | Engine temperature | raw − 25 → °C (below 25 reads 0) |
| 15 | Screen | |
| 16 | Vehicle state 3 | |
| 17 | ABS fault blink code | |
| 18 | Ride mode / backlight | |

### `5A 12`: trip

| Pos | Len | Field | Scaling |
| ---: | ---: | :--- | :--- |
| 3 | 1 | Lean angle | raw |
| 4 | 2 | Range to empty | km |
| 6 | 1 | Wheel offset | raw |
| 7 | 1 | Acceleration | raw |
| 8 | 1 | Torque | raw |
| 9 | 2 | Trip distance | ÷ 10 → km |
| 11 | 1 | Trip time, hours | |
| 12 | 1 | Trip time, minutes | |
| 13 | 1 | Trip mileage | km/l |
| 14 | 2 | Trip fuel | ÷ 10 → litres |
| 16 | 1 | Overspeed threshold | km/h |
| 17 | 1 | Overspeed setting | |

### `5A 16`: lap timer

Lap number, lap time and best lap. Expected only on track-oriented clusters.
Not decoded yet; shown raw on the Diagnostics screen.

### `5A 18`: engine

| Pos | Len | Field | Scaling |
| ---: | ---: | :--- | :--- |
| 3 | 1 | Engine load | % |
| 4 | 2 | Accumulated injection time | raw |
| 6 | 1 | Manifold pressure | kPa |
| 7 | 1 | Barometric pressure | kPa |
| 8 | 1 | Intake air temperature | raw |
| 9 | 1 | Engine temperature | raw − 25 → °C |
| 10 | 2 | Injection time | raw |
| 12 | 1 | Battery voltage | ÷ 10 → V |
| 13 | 2 | Run time since engine start | s |
| 15 | 2 | Distance travelled | raw |
| 17 | 2 | Injection volume | raw |

One layout covers every Apache, including TFT models with lap timers and
lean angle. A smaller LCD cluster probably leaves many fields unused. A byte
that never changes on the Diagnostics grid during a ride is not real data on
that bike.

## Phone → cluster

All outgoing frames are built in the clear, then scrambled with the bike's
key. No checksum is added to these two.

### `5B 4A`: phone status (keepalive), every 2 s

| Pos | Field |
| ---: | :--- |
| 3 | Signal bars (high nibble), battery bars (low nibble) |
| 4 | Overspeed limit; 120 when no alert is set |
| 7, 8, 9 | Hour (12 h), minute, second |
| 10 | 1 = PM |
| 11 | Missed calls |
| 12 | Network type |
| 13, 14, 15 | Day, month, year (two digits) |
| 17 | Voice assist (high nibble) |
| 19 | Call state |
| 20 | `FF` |

### `5B 52`: rider name

Up to 17 characters from bytes 3 to 19, zero-padded, then `FF`. Characters
other than letters, digits and space are sent as `X`.

## Not implemented, on purpose

The official app can also write calibration values and vehicle settings
(follow-me headlamp timer, idle start-stop timer, ride mode). Those change how
the bike behaves. This app does not send them.

## Measured on an RTR 160 4V (first ride, v0.1.0)

- **Scramble is `XOR 0xEB`**, detected from the checksums. The official app uses
  this key for one Apache family; the table above expected `0xEA` for 4V bikes.
- The cluster first sends a few **all-zero frames** while it wakes up. They are
  not data and are skipped.
- Frames arrive about every 250 ms, cycling through `10`, `11`, `12`, `16`,
  `18` and `19`.
- **`5A 12` is all zeros** apart from position 7: trip distance, range and trip
  mileage from the official table are not sent by this cluster.
- **`5A 10` position 15-16 is Trip F** in tenths of a km; it rises exactly with
  the odometer.
- **`5A 11` position 14 is not engine temperature** on this bike; it is a bit
  field that changes with the lamps. Engine temperature comes from `5A 18`
  position 9 (raw − 25, rose from 40 to 73 °C as the engine warmed). `5A 18`
  position 8 is intake-air temperature in °C and position 12 battery voltage
  in tenths of a volt (12.4 V engine off, 14.6 V running).
- **`5A 19` is not in the official table.** Positions 5-7 and 8-10 count whole
  kilometres (each rose by one exactly when the odometer passed a whole km);
  read as Trip A and Trip B. Positions 14 and 15 read 33, likely average
  mileage in km/l. To confirm against the cluster display.
- `5A 10` position 12 becomes `0x80` just before the link drops when the
  ignition is switched off.
- Range to empty was not found in any frame.

## Navigation (phone → cluster)

The family this bike belongs to takes two frames per update, both with a
checksum and both scrambled.

### `5A 4E`: navigation control

| Pos | Len | Field |
| ---: | ---: | :--- |
| 3 | 2 | Distance to the turn, metres, rounded to 50 m |
| 5 | 2 | Time left, minutes |
| 7 | 3 | Distance left, metres |
| 10 | 1 | Arrow code (see `Maneuver` in `Nav.kt`) |
| 11 | 1 | Text lines (1, or 2 when the text is longer than 17) |
| 12 | 1 | Status, `0xFF` while navigating |
| 13 | 1 | Unit, 0 |
| 14 | 1 | Alert, 0 |
| 15 | 1 | Prompt: 1 turn coming up, 3 turn left now, 4 turn right now, 5 straight for a while, 8 arrived |
| 19 | 1 | Checksum |

An all-zero control frame (`5A 4E`, zeros, checksum, `FF`) ends navigation.

### `5B 4F`: instruction text

Up to 16 characters from position 3, zero-padded, checksum at 19, `FF` at 20.

### Arrow codes

From the official app's Apache table: left 0, sharp left 1, slight left 2,
keep left 7, merge left 8, U-turn left 9, right 16, sharp right 17, slight
right 18, keep right 23, merge right 24, U-turn 25, head 33, arrived 34,
ferry 35, straight 87, unknown 100, and roundabout codes 39-51, 71, 72 and 96.
The app has an arrow-code tester to check each one on the cluster.

### Where directions come from

Google Maps does not share its route with other apps, but it shows the next
step in its navigation notification. With notification access, the app reads
that text (distance, instruction, time and distance left) and turns it into
the frames above. The text is English; other languages are not parsed yet.

## Open questions for the bike

1. Do Trip A, Trip B and the two mileage values in `5A 19` match the cluster?
2. Fuel level scale (`5A 10` position 7 moved between 2 and 4 during a ride).
3. Which arrow codes draw which arrows on this cluster.
4. Is the hour field 12 h or 24 h? (Check the cluster clock after connecting.)
