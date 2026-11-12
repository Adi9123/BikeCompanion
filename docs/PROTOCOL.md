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

## Open questions for the bike

1. Is the 4V scramble `0xEA`? (Diagnostics → "Bike's scramble")
2. Which frames does the RTR 160 4V send, and which bytes change during a ride?
3. Fuel level scale, and what byte 7 of `5A 11` holds.
4. Does the cluster need the keepalive to stay connected, and does it accept it?
5. Is the hour field 12 h or 24 h? (Check the cluster clock after connecting.)
