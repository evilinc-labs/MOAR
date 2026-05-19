# MOAR : Minecraft Orchestrated Automation & Response

[![Downloads](https://img.shields.io/github/downloads/evilinc-labs/MOAR/total.svg)

A client-side Fabric mod that automates schematic building, spawn-proofing, and container management. Load a `.litematic`, point it at some supply chests, and let it build, restock, recover from disconnects, and keep working across long multi-container runs.

## 2.2 Highlights

- **Smarter printer detection** : auto-detects active Litematica placements and can fall back to hologram block correlation when the live placement list is unavailable
- **Resume-first building** : printer checkpoints survive disconnects and `/printer resume` restores the build state from disk
- **Safer mode switching** : toggling between manual printer mode and AutoBuild now resets stale pathing and in-flight placement state cleanly
- **Better no-Baritone fallback** : the built-in vanilla walker now respects nearby-goal radius better and avoids trying to path to unreachable vertical targets
- **Anti-cheat-aware placement pacing** : placement and shulker interaction flows now wait for calmer movement windows instead of blindly stacking packets into setbacks
- **Safer stash retrieval** : `/stash get` and `/stash kit load` skip protected supply/dump/storage containers, track owned shulkers, split oversized stacks, and abort cleanly after repeated failures

## What It Does

### Schematic Printer
Load a Litematica schematic and build it automatically  or toggle manual mode to place blocks yourself.

- **Multi-schematic queue**: detect and queue all Litematica placements, builds sequentially with auto-advance and completion notifications
- **AutoBuild**: walks to each zone, grabs materials from supply chests, places blocks, and moves on
- **Manual + AutoBuild modes**: switch between assisted manual placement and full automation without stale walking/pathing state leaking across modes
- **Litematica auto-detection**: loads the closest active placement automatically and can re-anchor from hologram blocks when placement metadata is missing
- **Supply restocking**: indexes chests and shulker boxes; walks back to resupply when inventory runs low
- **Shulker handling**: places, opens, loots, and breaks shulkers from supply; cleans up temp platforms
- **Scaffold cleanup**: removes bridges and pillars placed during pathfinding after the build
- **Self-correction**: detects wrong blocks and replaces them
- **Placement verification**: waits for server confirmation to catch anti-cheat rollbacks and repeated rejected placements
- **Inventory-swap settling**: avoids firing placement packets immediately after hotbar swaps from the main inventory
- **Smart tool selection**: picks the fastest tool for breaking
- **Directional blocks**: handles stairs, slabs, trapdoors, doors, logs, pillars, etc.
- **Liquid pass**: places water/lava after solids to keep pathfinding intact
- **Checkpoints**: saves progress to SQLite; resume after disconnect with `/printer resume`
- **Area clearing**: mines out a region before building; dumps items into designated chests
- **Protected pathfinding**: Baritone routes around storage and interactive blocks instead of mining through them during automation-sensitive walks
- **Pathfinding** : uses [Baritone](https://github.com/cabaletta/baritone) if installed, otherwise falls back to a built-in vanilla walker tuned for nearby-zone approaches

### SpawnProofer
Define a region and let it place light sources on every dark spawnable surface.

- **Greedy solver** : picks positions that cover the most dark spots per placement
- **Embed mode** : swaps ground blocks for full-block lights (glowstone, sea lanterns, froglights)
- **Supply chests** : restocks light sources automatically
- **Pause/resume** : pause a run mid-route and continue later without redefining the region

### Stash Manager
Scan, index, and organize containers across large areas.

- **Container indexing** : scans chests, barrels, shulker boxes, and hoppers in a defined region
- **Experimental auto-organizing** : sorts items into columns, packs shulkers, handles overflow
- **Item retrieval** : `/stash get <item> [count]` walks the stash and pulls exact items back to the player
- **Kit system** : save, snapshot, edit, list, and load named loadouts with `/stash kit ...`
- **Region profiles** : save/load/delete named scan regions with `/stash region ...`
- **Protected-container blacklist** : stash retrieval skips printer supply chests, dump chests, and storage-designated containers
- **Smarter shulker retrieval** : tracks owned shulkers, re-deposits no-longer-needed ones, and splits oversized stacks instead of over-pulling
- **Waypoint walking** : navigates between zones for areas beyond render distance
- **CSV export** : exports the full inventory index to file
- **Dump chests** : designate chests for depositing mined items during area clearing
- **REST API** : embedded HTTP server with Prometheus metrics for Grafana dashboards
- **Webhook** : POST JSON to n8n or other services on scan completion

## Supported Versions

| Minecraft | Fabric API | Java |
|-----------|------------|------|
| 1.21–1.21.1 | 0.116.9+1.21.1 | 21 |
| 1.21.4 | 0.119.4+1.21.4 | 21 |
| 1.21.5 | 0.128.2+1.21.5 | 21 |
| 1.21.8 | 0.136.1+1.21.8 | 21 |
| 1.21.9–1.21.10 | 0.138.4+1.21.10 | 21 |
| 1.21.11 | 0.141.3+1.21.11 | 21 |

All versions require [Fabric Loader](https://fabricmc.net/use/) ≥ 0.18.4.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) ≥ 0.18.4
2. Drop [Fabric API](https://modrinth.com/mod/fabric-api) into `mods/`
3. Drop the MOAR `.jar` for your MC version into `mods/`
4. *(Optional)* Install [Baritone](https://github.com/cabaletta/baritone/releases) for pathfinding

## Quick Start

### Single Schematic
1. Place or load a schematic in Litematica, then run `/printer detect` or `/printer load mybase.litematic`
2. If you loaded manually, stand where the build origin should be and run `/printer here`
3. Mark your supply chests with `/printer supply add` while looking at each container
4. Use `/printer autobuild` for full automation, then `/printer toggle` to start
5. If you disconnect mid-build, return and use `/printer resume`

### Multiple Schematics (Queue)
1. Place multiple schematic placements in Litematica
2. Run `/printer queue detect` to auto-detect and queue all placements
3. Mark your supply chests with `/printer supply add`
4. Use `/printer autobuild`, then run `/printer toggle` to start building—each schematic completes automatically and advances to the next
5. Check progress with `/printer queue status`

## Commands

### Printer

| Command | What it does |
|---------|--------------|
| `/printer toggle` | Start/stop the printer |
| `/printer autobuild` | Enable fully automated building |
| `/printer autobuild on\|off\|toggle` | Explicitly control AutoBuild mode |
| `/printer load <file>` | Load a `.litematic` schematic |
| `/printer unload` | Unload the current schematic |
| `/printer detect` | Auto-detect active Litematica placements, with hologram-anchor fallback |
| `/printer list` | List available schematic files |
| `/printer here` | Anchor schematic to current position |
| `/printer pos <x> <y> <z>` | Anchor schematic to specific coordinates |
| `/printer status` | Show progress and completion percentage |
| `/printer materials` | Show required materials vs. supply inventory |
| `/printer resume` | Resume from last checkpoint |
| `/printer speed [1–20]` | Get/set placement speed (default: 13 blocks/sec) |
| `/printer sort [mode]` | Set build order: `bottom_up`, `top_down`, `nearest` |
| `/printer air` | Toggle air placement (floating blocks) |
| `/printer supply add [x y z]` | Mark a container as a supply source |
| `/printer supply remove` | Unmark the nearest supply chest |
| `/printer supply list` | List all supply chests |
| `/printer supply scan` | Show indexed supply inventory summary |
| `/printer supply clear` | Remove all supply chests |
| `/printer queue status` | Show current build + queued builds |
| `/printer queue list` | Alias for `status` |
| `/printer queue detect` | Auto-detect and queue all Litematica placements |
| `/printer queue next` | Manually advance to next build |
| `/printer queue skip [reason]` | Skip current build with optional reason |
| `/printer queue clear` | Clear all queued builds (not active) |
| `/printer queue auto <on\|off>` | Toggle auto-advance (default: on) |
| `/printer queue move <taskId> <position>` | Move task to specific position (1-indexed) |
| `/printer queue up <taskId>` | Move task up one position (towards front) |
| `/printer queue down <taskId>` | Move task down one position (towards back) |
| `/printer queue top <taskId>` | Move task to front of queue (build next) |
| `/printer queue bottom <taskId>` | Move task to back of queue (build last) |
| `/printer dump add [x y z]` | Mark a container as a dump chest |
| `/printer dump remove` | Unmark the nearest dump chest |
| `/printer dump list` | List all dump chests |
| `/printer dump clear` | Remove all dump chests |

### SpawnProofer

| Command | What it does |
|---------|--------------|
| `/spawnproof pos1 [x y z]` | Set corner 1 (default: player position) |
| `/spawnproof pos2 [x y z]` | Set corner 2 |
| `/spawnproof start` | Begin placing lights |
| `/spawnproof stop` | Stop and reset |
| `/spawnproof pause` | Pause mid-run |
| `/spawnproof resume` | Resume from pause |
| `/spawnproof scan` | Count dark spots without placing |
| `/spawnproof status` | Show state, region, and placement count |
| `/spawnproof lightsrc [block]` | Get/set light source block |
| `/spawnproof embed` | Toggle embed mode |
| `/spawnproof supply add` | Mark a chest as light supply |
| `/spawnproof supply remove` | Unmark a supply chest |
| `/spawnproof supply list` | List supply chests |

### Stash

| Command | What it does |
|---------|--------------|
| `/stash pos1 [x y z]` | Set corner 1 (default: player position) |
| `/stash pos2 [x y z]` | Set corner 2 |
| `/stash scan` | Scan all containers in the region |
| `/stash organize` | Experimental auto-organize: column sort, shulker pack, overflow |
| `/stash organize stop` | Stop organizing |
| `/stash search <item>` | Find scanned containers holding an item |
| `/stash get <item> [count]` | Retrieve an item directly from the indexed stash |
| `/stash stop` | Stop scanning |
| `/stash status` | Show scan state and index summary |
| `/stash export` | Export inventory index to CSV |
| `/stash clear` | Clear the index |
| `/stash kit create|snapshot|add|remove|show|list|delete|load` | Manage and retrieve named kits |
| `/stash region save|load|list|delete` | Manage named region profiles |
| `/stash dump add [x y z]` | Mark a dump chest |
| `/stash dump remove` | Unmark the nearest dump chest |
| `/stash dump list` | List all dump chests |
| `/stash dump clear` | Remove all dump chests |

### API & Monitoring

MOAR includes an embedded HTTP API for Grafana dashboards, Prometheus scraping, and n8n webhook integrations. All settings live in `config/moar/moar.properties` (auto-created on first launch).

#### Configuration (`config/moar/moar.properties`)

```properties
# Enable the embedded API server
api.enabled=false
# Bind address (use 0.0.0.0 to expose externally)
api.bind=127.0.0.1
# HTTP port
api.port=8585
# Bearer token for auth (leave blank to disable auth)
api.key=
# POST JSON to this URL on scan completion (n8n, etc.)
webhook.url=
```

#### REST Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/status` | GET | Scanner state, index size, region info |
| `/api/v1/containers?page=1&size=50` | GET | Paginated container list |
| `/api/v1/search?item=diamond` | GET | Find containers holding an item |
| `/api/v1/stats` | GET | Aggregate statistics (JSON) |
| `/api/v1/metrics` | GET | Prometheus-format metrics |
| `/api/v1/organizer` | GET | Organizer state and progress |
| `/api/v1/webhook/test` | POST | Webhook connectivity test |

All endpoints accept `Authorization: Bearer <api.key>` when `api.key` is set.

## Multi-Schematic Queue

Build multiple Litematica placements sequentially without manual intervention while reusing the same supply chest network and printer settings.

### Queue Workflow

```bash
# 1. Set up Litematica placements in-world
# 2. Auto-detect and queue all placements
/printer queue detect

# Or queue the currently loaded schematic repeatedly
/printer queue add

# 3. Configure supply chests
/printer supply add

# 4. Enable AutoBuild, then start building (auto-advances through queue)
/printer autobuild
/printer toggle

# 5. Check progress
/printer queue status
```

### Queue Best Practices

- **Detection**: Run `/printer queue detect` after all placements are enabled in Litematica. Detection only queues tasks; it does not start building.
- **Duplicates**: Use `/printer queue add` to queue the currently loaded schematic at its current anchor. Re-anchor with `/printer here` or load another placement, then run it again to stack duplicates intentionally.
- **Supplies first**: Add or verify supply chests before the first `/printer toggle` so the first build does not pause immediately for missing items.
- **AutoBuild first**: Run `/printer autobuild` before `/printer toggle` when you expect MOAR to walk between build zones. Use `/printer autobuild off` only for stationary/manual printer mode.
- **Start and pause**: Use `/printer toggle` to start the first queued task, pause the active task, or resume a paused one.
- **Folia / Grim**: Keep placement pacing conservative on strict servers. MOAR now stays on single-placement world ticks, but lower `bps` values and a clean line of sight still make builds more reliable.
- **Reordering**: Use `/printer queue status` before `move`, `up`, `down`, `top`, or `bottom`; the short task IDs shown there are the IDs those commands expect.
- **Skip vs next**: Use `/printer queue next` when you want to come back to the current build later. Use `/printer queue skip [reason]` only when you want to mark that build as intentionally abandoned.
- **Auto-advance**: Leave `/printer queue auto on` for hands-off batch runs. Turn it off when you want to inspect each schematic between builds.
- **Disconnects**: The queue is not persisted across disconnects, so re-run `/printer queue detect` after reconnecting if you still want a batch run.

### Queue Features

- **Auto-detection**: Scans all enabled Litematica placements and calculates proper anchor points (including schematic offsets)
- **Sequential building**: One build at a time—completes current, then auto-advances to next
- **Manual reordering**: Move tasks up/down, to specific positions, or to front/back of queue
- **Progress tracking**: Real-time status showing blocks placed, elapsed time, and remaining builds
- **Completion notifications**: Alerts when each build finishes with stats, including builds that stop after exhausting available materials
- **Auto-advance**: Enabled by default—builds continuously until queue is empty (toggle with `/printer queue auto`)
- **Manual control**: Pause with `/printer toggle`, skip problematic builds, advance manually, or clear the queue
- **Works with AutoBuild**: Fully compatible with existing automation features

### Queue Commands

| Command | Description |
|---------|-------------|
| `/printer queue status` | Show active build + queued builds |
| `/printer queue add` | Queue the currently loaded schematic at its current anchor |
| `/printer queue detect` | Auto-detect Litematica placements and add to queue |
| `/printer queue next` | Pause the current build and move it behind the rest of the queue |
| `/printer queue skip [reason]` | Skip current build (marks as failed) |
| `/printer queue clear` | Clear all queued builds (not active build) |
| `/printer queue auto on\|off` | Toggle automatic queue advancement |
| `/printer queue move <taskId> <position>` | Move task to specific position (1-indexed) |
| `/printer queue up <taskId>` | Move task up one position |
| `/printer queue down <taskId>` | Move task down one position |
| `/printer queue top <taskId>` | Move task to front (build next) |
| `/printer queue bottom <taskId>` | Move task to back (build last) |

**Note**: Task IDs are shown in the queue status output as short 8-character identifiers (e.g., `[a1b2c3d4]`).

### Implementation Notes

- **Sequential only**: Builds one schematic at a time (no parallelism)
- **No persistence**: Queue is lost on disconnect (individual checkpoint per schematic still works)
- **FIFO order**: First queued = first built (no priority system yet)
- **Shared resources**: All builds use the same supply chests

#### Grafana Setup

See [docs/grafana-setup.md](docs/grafana-setup.md) for a step-by-step guide to connect Prometheus + Grafana dashboards via Docker Compose.

### Keybinds

| Key | Action |
|-----|--------|
| `Numpad 0` | Toggle printer on/off |

## Building from Source

Requires **JDK 21+**.

```bash
./gradlew build                  # Build all versions
./gradlew :1.21.8:build          # Build one version
./gradlew buildAndCollect        # Collect all JARs -> build/libs/2.2.0/
```

Output: `versions/<mc>/build/libs/moar-2.2.0+<mc>.jar`

### Build Stack

| Tool | Version |
|------|---------|
| [Stonecutter](https://stonecutter.kikugie.dev/) | 0.8.3 |
| [Fabric Loom](https://github.com/FabricMC/fabric-loom) | 1.16.1 |
| [Gradle](https://gradle.org/) | 9.4.0 |

### Baritone Integration

Baritone is loaded via reflection at runtime : no compile dependency, nothing bundled. If Baritone isn't installed, the mod uses its built-in vanilla walker instead. On Baritone-enabled installs, MOAR also protects storage and other interactive blocks from path-mining during sensitive automation flows.

## License

[GNU Affero General Public License v3.0](LICENSE)

Forks must remain public with changes indicated. See the license for details.
