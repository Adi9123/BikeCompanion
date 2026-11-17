# Supported models

Which bikes Bike Companion works with, and what each one sends. The layouts
are facts about the Bluetooth frames, worked out from the official app's frame
tables and from our own captures. No code from the official app is used.

## Support levels

| Level | Meaning |
| :--- | :--- |
| **Tested** | Connected to a real bike of this model. |
| **Not tested yet** | Same frame family as a tested bike; the layout comes from the official app. Expect it to work; send a log if a value looks wrong. |
| **Not supported yet** | The official app supports it, but over a different connection (TFT clusters, electric scooters) that this app does not speak. |

| Model | Family | Level |
| :--- | :--- | :--- |
| Apache RTR 160 4V | Apache | Tested (developed on this bike) |
| Raider 125 | Raider | Tested (connection) |
| Apache RTR 160 2V | Apache | Not tested yet |
| Apache RTR 180 | Apache | Not tested yet |
| Ntorq 125 | Scooter | Not tested yet |
| Jupiter (connected variants) | Scooter | Not tested yet |
| Apache RR 310 | | Not supported yet: different Bluetooth service |
| Apache RTR 310, Ronin | | Not supported yet: different connection |
| iQube, TVS X, Orbiter | | Not supported yet: electric, different connection |

The rider picks the model in **Settings > Your bike model**; Apache RTR 160
4V is the default. If the frames arriving do not match the chosen model
(Apache clusters send frames `0x12` and `0x16`, the others never do), the
Ride screen suggests checking the model.

## Frames without a checksum

Apache frames end with a checksum in byte 19. **Raider, Jupiter and Ntorq
frames do not**: byte 19 is data. The Apache decoder trusted only frames whose
checksum validated, so on those bikes it accepted about one frame in a hundred,
by chance, which is why earlier versions showed some values and not others.

For these families the scramble (none, XOR `0xEA` or `0xEB`) is learned from
the frames instead: under the right key, idle and unused fields decode to
zero, so zero bytes are counted under each key over the first frames and the
clear winner is used.

## What each family sends

Positions are 1-based (the start byte `5A` is position 1), multi-byte values
big-endian. ✅ same position as Apache · 🔀 different position · ❌ not sent.

| Value | Apache | Raider | Jupiter / Ntorq |
| :--- | :--- | :--- | :--- |
| Speed, odometer, fuel bars, average speed, top speed, throttle | ✅ 0x10 | ✅ | ✅ |
| Engine RPM | 0x10 @17, 2 bytes | ✅ | 🔀 @18 |
| Trip F (on reserve) | 0x10 @15, 2 bytes | ✅ | 🔀 3 bytes |
| 0–60 time | 0x10 @13 | ✅ | 🔀 @14 |
| Switch status (key-off bit) | 0x10 @12 | ✅ | 🔀 @13 |
| Average mileage | 0x10 @14 | ✅ | ❌ (see economy) |
| Instant mileage | 0x10 @9 | ❌ (trip status) | ❌ (trip status) |
| Gear | 0x11 @6 | ✅ | ❌ |
| Ride mode | 0x11 @18 low nibble | ❌ (backlight) | ❌ |
| ABS fault code | 0x11 @17 | ❌ | ❌ |
| Engine fault, service, indicators | 0x11 | ✅ | ✅ |
| Engine temp, intake air, battery, injection counter | 0x18 | ✅ | ✅ |
| Trip A / Trip B, mileage A / B | 0x19 (found on the RTR 160 4V) | ❌ | ❌ |
| **Range from the bike** | ❌ | 0x19 @12, 2 bytes | 0x19 @12 |
| **Average / instant fuel economy** | ❌ | 0x19 @9 / @10 | 0x19 @9 / @10 |
| **Idle stop-start count / time** | ❌ | 0x19 @16, 3 bytes / @3, 3 bytes | same |
| Lean angle, trip computer | 0x12 (zero on the 160 4V) | ❌ | ❌ |
| Lap timer | 0x16 | ❌ | ❌ |

Note that frame `0x19` means different things: Trip A/B on the Apache,
economy and range on the others. Reading one as the other gives nonsense, so
the model choice matters.

## Not yet verified per model

- **Turn arrows and texts sent to the cluster** use the Apache formats. The
  Raider, Jupiter and Ntorq may expect different arrow codes or frames. They
  are display-only frames, so a wrong one is ignored or shown oddly, never
  harmful.
- **Units** of the scooter economy and idle-time fields are as the official
  app names them; they need a capture to confirm.
