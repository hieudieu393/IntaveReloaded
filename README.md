![IntaveReloaded](docs/assets/hero_banner.png "IntaveReloaded")

# IntaveReloaded

**IntaveReloaded** is a maintained fork of the original Intave anticheat, with the original authors and copyright notices retained. Reloaded keeps Intave's simulation-first architecture while extending coverage for modern combat, movement, inventory, packet-protocol and world interactions.

The project is currently maintained by **Onxe**.

## What Reloaded changes

Reloaded keeps the original internal configuration keys so existing thresholds, commands and API integrations remain familiar, while exposing friendlier check names in alerts and command lookup.

| Area | Reloaded coverage |
| --- | --- |
| **Combat — KillAura / Aim** | Original classic heuristics plus modern combat rotation, use-item rotation, freeze/blink attack evidence, obstruction/multi-action analysis, attack-angle and buffered zero-pitch signals. |
| **Reach / Hitbox** | AttackRaytrace remains the latency-compensated parent for reach, hitbox and timeout/backtrack-style evidence. |
| **Movement — Simulate / NoFall / NoSlow / Phase / Sprint** | Physics simulation is supplemented by movement-state guards for withheld air movement, impossible ground claims, NoSlow/Phase/Sprint contradictions and other packet-state signals. |
| **Elytra / Vehicle** | Dedicated modern state guards cover invalid Elytra transitions/toggle patterns and suspicious vehicle movement while preserving Physics exemptions. |
| **Timer** | Original synchronized Timer logic plus client tick-end invariants, duplicate movement handling, vehicle clock drift and a conservative negative/slow client clock. |
| **BadPackets** | Expanded protocol coverage for action order, duplicate input/movement, chat/book state, sequences, respawn state, exploit envelopes and invalid numeric/dig/window/client states. |
| **Inventory** | Existing inventory analysis plus inventory-state transition grace and dedicated AutoTotem/AutoSwap signals. |
| **Scaffold / FastBreak** | Placement and breaking pipelines add packet consistency, fabricated cursor/range envelopes, duplicate rotation placement, break protocol, no-swing and use-item/air-liquid guards. |

### Check names and config keys

Reloaded separates the **display name** from the stable **internal config key**. For example:

- `Simulate` → `check.physics`
- `KillAura` / `Aim` → `check.heuristics`
- `Reach` → `check.attackraytrace`
- `BadPackets` → `check.protocolscanner`
- `Scaffold` → `check.placementanalysis`
- `Inventory` / `AutoTotem` / `AutoSwap` → `check.inventoryclickanalysis`

Supplemental Reloaded guards usually feed the parent check's violation pipeline instead of creating a second threshold tree. This keeps the advanced configuration close to original Intave and avoids dozens of misleading per-subcheck switches.

`AirStuckGuard` is the main exception: its `enabled`, `max-gap-ms` and `flag-cooldown-ms` timing controls are exposed because the useful withheld-position window can vary by server environment.

## Configuration

`config.yml` remains the small day-to-day configuration. With `config: THIS`, its values are converted into the original-style advanced layout.

Use `config: ADVANCED` when you need direct access to check thresholds, guard timing and compatibility settings in `advanced.yml`.

The advanced file intentionally keeps original Intave internal keys. Reloaded-specific comments document which modern signals feed each parent check.

## Requirements & compatibility

- **Server:** Minecraft **1.21+** on a supported Bukkit/Spigot/Paper/Folia implementation
- **Client protocol:** Minecraft **1.17+**
- **PacketEvents 2.13.0+** is required
- ViaVersion/ViaBackwards are optional for native-version clients and needed when protocol translation is required

Server runtime support and client protocol support are separate: IntaveReloaded runs on modern servers while retaining compatibility logic for supported older client protocols.

## Development

### Setup

1. Clone the repository: `git clone https://github.com/hieudieu393/IntaveReloaded.git`.
2. Open it as a Gradle project and allow dependency/index resolution to finish.
3. Install PacketEvents 2.13.0+ on the test server.
4. Use a Minecraft 1.21+ server; add ViaVersion/ViaBackwards when testing translated client protocols.

### Testing

Server run/self-test tasks below Minecraft 1.21 are outside the supported server range. MCP-Reborn client tasks are supported from Minecraft 1.17 upward. The development run/test configuration installs the required PacketEvents dependency for server tasks.

## Credits

**Current maintainer / Reloaded development**
- Onxe

**Original Intave developers**
- DarkAndBlue
- Jpx3
- vento
- vxcus
- lennoxlotl
- NotLucky
- Trattue

IntaveReloaded does not claim authorship of the original Intave code. Original authorship, copyright and license notices are intentionally retained throughout the source tree.

## Project docs

See [CONTRIBUTING](docs/CONTRIBUTING.md), [STRUCTURE](docs/STRUCTURE.md), [CHEATSHEET](docs/CHEATSHEET.md) and [BLOCK_SYSTEM](docs/BLOCK_SYSTEM.md) for development notes and codebase orientation.

## License

The original Intave source is distributed under the [PolyForm Perimeter License 1.0.0](LICENSE.md). Existing copyright and license notices remain in place. Third-party libraries are covered by their respective licenses.
