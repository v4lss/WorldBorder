# WorldBorder

A Bukkit/Spigot plugin that provides configurable per-world borders with elliptic and rectangular shapes, chunk generation/trimming, and wrap-around support.

## Features

- Per-world borders with independent settings
- Elliptic (round) and rectangular (square) border shapes
- Per-world shape overrides
- Wrap-around mode (teleport to opposite side when crossing border)
- Asymmetric radii (different X and Z values)
- Safe teleport with knockback when players cross the border
- Chunk fill task (generate chunks to border in spiral pattern)
- Chunk trim task (remove chunks outside border, supports region file deletion)
- Memory monitoring with auto-pause/resume for fill tasks
- Periodic border enforcement for all online players
- Teleport and portal interception
- Creature spawn prevention outside borders
- Nether and The End support (DIM folder detection)

## Installation

1. Build with `./gradlew build`
2. Copy `build/libs/WorldBorder-1.0-SNAPSHOT.jar` to your server's `plugins/` folder
3. Restart the server

## Commands

| Command | Description |
|---------|-------------|
| `/wb set <radiusX> [radiusZ]` | Set border centered on you |
| `/wb set <radiusX> [radiusZ] <x> <z>` | Set border at coordinates |
| `/wb set <radiusX> [radiusZ] spawn` | Set border centered on world spawn |
| `/wb set <radiusX> [radiusZ] player <name>` | Set border centered on a player |
| `/wb <world> set <radiusX> [radiusZ] <x> <z>` | Set border for specific world |
| `/wb setcorners <x1> <z1> <x2> <z2>` | Set border by corners |
| `/wb radius <radiusX> [radiusZ]` | Change border radius |
| `/wb clear` | Remove border for current world |
| `/wb clear all` | Remove all borders |
| `/wb list` | Show border info for all worlds |
| `/wb shape <elliptic\|rectangular>` | Set default border shape |
| `/wb wshape <elliptic\|rectangular\|default>` | Set shape override for a world |
| `/wb wrap <on/off>` | Toggle wrap-around mode |
| `/wb fill [freq] [pad] [force]` | Fill world to border (generate chunks) |
| `/wb trim [freq] [pad]` | Trim world outside border |
| `/wb fill cancel` | Cancel fill task |
| `/wb fill pause` | Pause/unpause fill task |
| `/wb trim cancel` | Cancel trim task |
| `/wb trim pause` | Pause/unpause trim task |

### Task Parameters

- **freq**: Processing frequency (chunks per second). Default fill: 20, default trim: 5000
- **pad**: Padding blocks beyond border to process. Default: 208 (13 chunks)
- **force**: Force load already generated chunks (fill only)

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `worldborder.command.set` | Set borders | op |
| `worldborder.command.radius` | Change radius | op |
| `worldborder.command.clear` | Remove borders | op |
| `worldborder.command.list` | View borders | op |
| `worldborder.command.shape` | Change default shape | op |
| `worldborder.command.wshape` | Change per-world shape | op |
| `worldborder.command.wrap` | Toggle wrap-around | op |
| `worldborder.command.fill` | Fill world to border | op |
| `worldborder.command.trim` | Trim world outside border | op |
| `worldborder.command.help` | View help | op |

## Configuration

These values can be modified in `PluginSettings.kt`:

| Property | Description | Default |
|----------|-------------|---------|
| `timerTicks` | Border check interval (ticks) | 4 |
| `fillAutosaveFrequency` | Auto-save interval during fill (seconds) | 30 |
| `fillMemoryTolerance` | Minimum memory before pausing fill (MB, min: 1024, max: 10240) | 2048 |
| `roundByDefault` | Default shape (true = elliptic, false = rectangular) | true |
| `knockback` | Knockback distance when crossing border | 3.0 |

## Building

Requires:
- Java 25+
- Gradle 9.6.0

```bash
./gradlew build
```

The compiled JAR will be in `build/libs/WorldBorder-1.0-SNAPSHOT.jar`.
