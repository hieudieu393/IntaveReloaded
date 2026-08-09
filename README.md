
![IntaveReloaded](docs/assets/hero_banner.png "IntaveReloaded")

# IntaveReloaded

**IntaveReloaded** is maintained by **Onxe** and is based on the original Intave anticheat project.
Intave has been developed since 2016 and was used on some of the world's largest Minecraft servers before its source became publicly available.

This fork keeps the original Intave detection architecture and developer attribution while continuing development under the IntaveReloaded name.
The packet backend has been migrated from the external ProtocolLib dependency to **PacketEvents 2.13.0+**.

## Credits

Current maintainer / additional development:
- **Onxe**

Original Intave developers, retained with full credit:
- DarkAndBlue
- Jpx3
- vento
- vxcus
- lennoxlotl
- NotLucky
- Trattue

## Requirements

- A supported Bukkit/Spigot/Paper/Folia server version
- **PacketEvents 2.13.0 or newer**
- ViaVersion is optional and remains supported

## General

Unlike traditional module-based anticheats, IntaveReloaded accurately simulates player movement, client-side entity and block
data to detect even the smallest manipulations. Through this approach, Intave can prevent combat, movement and interaction
exploits such as speed/fly cheats or reaching beyond the normal interaction range.

Additionally, Intave provides heuristic checks for aimbot, auto-clicker, timer, placement, block breaking, inventory and other
cheats that cannot be detected solely by simulating client logic.

The original Intave check documentation remains useful for understanding the architecture and detection model.

## Development

### Setup

1. Clone this repository: `git clone https://github.com/hieudieu393/IntaveReloaded.git`.
2. Open the project as a Gradle project and allow IntelliJ/Gradle to finish indexing and resolving dependencies.
3. Install PacketEvents 2.13.0+ on any server where you run the built plugin.

### Testing

Choose one of the `intave/run_X.X.X` or test tasks corresponding to the Minecraft server version you want to test.
The development run/test configuration installs the required PacketEvents dependency for the server task.

Breakpoints and hotswapping are supported. The original project recommends the IntelliJ
[Single Hotswap](https://plugins.jetbrains.com/plugin/14832-single-hotswap) plugin for efficient method-body hotswapping.

## Contributing

Please read the existing [contributing guidelines](docs/CONTRIBUTING.md) before contributing.
For a high-level overview of the project organization, see [docs/STRUCTURE.md](docs/STRUCTURE.md).
A quick codebase reference is available in [docs/CHEATSHEET.md](docs/CHEATSHEET.md), and the block system is outlined in
[docs/BLOCK_SYSTEM.md](docs/BLOCK_SYSTEM.md).

## Upstream attribution

IntaveReloaded is derived from the original **Intave** project. The original authors and copyright notices are intentionally
retained throughout the source tree. This project does not claim authorship of the original Intave code; Onxe is credited for
IntaveReloaded maintenance and subsequent modifications.

## License

The original Intave source is distributed under the [PolyForm Perimeter License 1.0.0](LICENSE.md). Existing copyright and
license notices remain in place. Third-party libraries, including PacketEvents, are covered by their respective licenses.
