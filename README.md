<p align="center">
    <img src="minecraft_title.png" style="width: 69%" alt="MOAR logo">
</p>

<p align="center">
    <img src="https://img.shields.io/badge/minecraft-1.21.4--1.21.11-green" alt="Minecraft">
    <img src="https://img.shields.io/github/downloads/evilinc-labs/MOAR/total" alt="GitHub Downloads">
    <img src="https://img.shields.io/github/contributors/evilinc-labs/MOAR?color=blue" alt="GitHub Contributors">
    <img src="https://img.shields.io/github/stars/evilinc-labs/MOAR" alt="GitHub Repo Stars">
    <img src="https://img.shields.io/github/license/evilinc-labs/MOAR" alt="GitHub License">
</p>

**MOAR** — *Minecraft Orchestrated Automation & Response* — is a free, open-source client-side Fabric mod focused on advanced automation: schematic building, nether highway travel, spawn-proofing, and container management with minimal micromanagement. Load a `.litematic`, point it at some supply chests, and let it build, restock, recover from disconnects, and keep working across long multi-container runs.

<p align="center">
    <a href="https://github.com/evilinc-labs/MOAR/releases/latest"><img src="https://img.shields.io/badge/Download%20the%20latest%20MOAR%20release-2467d6?style=for-the-badge&logo=github&logoColor=white" alt="Download the latest MOAR release"></a>
</p>

> [!WARNING]
> **Use responsibly:** MOAR is a client-side automation mod. Some servers do not allow automation — check the rules of the server you play on before using it.

-----

## Features

### Automation Engine
* **Schematic Printer:** Full integration with **Litematica** and schematic files for seamless automated building — auto-detects active placements, with hologram block correlation as a fallback when the live placement list is unavailable.
* **AutoBuild:** Walks to each build zone, grabs materials from supply chests, places blocks, and moves on — including shulker handling, scaffold cleanup, and self-correction of wrong blocks.
* **Block State Handling:** Natively supports directional blocks including stairs, slabs, trapdoors, doors, logs, and pillars, plus a dedicated liquid pass that places water/lava after solids to keep pathfinding intact.
* **Multi-Schematic Queue:** Detect and queue all Litematica placements, then build them sequentially with auto-advance, manual reordering, and completion notifications.
* **Resume-First Building:** Printer checkpoints are saved to SQLite and survive disconnects — `/printer resume` restores the build state from disk.

### Highway Travel
* **End-to-End Nether Runs:** Plans multi-leg routes (approach → bounce → off-ramp → free flight) from your current position to any destination — fully hands-off.
* **Bounce Engine:** Elytra bounce flight with geometric ground/air detection, smooth re-launch, diagonal highway support, and lateral knockback recovery.
* **Grief Detour:** Detects griefed sections mid-bounce, plans a Baritone ground bypass, then resumes bouncing past the damage — up to 10 consecutive retries.
* **Elytra Resupply:** Monitors durability mid-travel and runs a full resupply cycle (shulker pull → equip → Mend via XP bottles → swap all inventory elytras) without interrupting the mission.

### Stash Management
* **Container Indexing:** Scans chests, barrels, shulker boxes, and hoppers across large areas — with waypoint walking for regions beyond render distance.
* **Item Retrieval:** `/stash get <item> [count]` walks the stash and pulls exact items back to you, skipping protected supply/dump/storage containers.
* **Kit System:** Save, snapshot, edit, and load named loadouts with `/stash kit ...`.
* **Storage Lanes:** Assign items to dedicated chest lanes and auto-sort your inventory into them — with a preview-first workflow so nothing moves until you confirm it.

### Stability & Safety
* **Anticheat Ready:** Built with **Grim** and other modern anticheats in mind — placement pacing waits for calmer movement windows instead of blindly stacking packets into setbacks.
* **Placement Verification:** Waits for server confirmation to catch anti-cheat rollbacks and repeated rejected placements.
* **Protected Pathfinding:** Baritone routes around storage and interactive blocks instead of mining through them during automation-sensitive walks.
* **Type-Safe Commands:** Uses Minecraft's statically typed command system to ensure input accuracy and reliability.

### Specialized Tools
* **SpawnProofer:** Define a region and let a greedy solver place light sources on every dark spawnable surface — including embed mode for full-block lights like glowstone and froglights.
* **In-Game GUI:** `/moar gui` opens a control screen with tabs for Kits, Index, Regions, Retrieve, Printer, Spawnproof, and API settings.
* **REST API & Monitoring:** Embedded HTTP server with Prometheus metrics for Grafana dashboards and webhook integrations (n8n and friends).

-----

## Installation
<a href="https://fabricmc.net/wiki/install"><img src="https://cdn.jonasjones.dev/mod-badges/support-fabric.png" width="150px" alt="Fabric Supported"></a>
1. Install the Minecraft version corresponding to the mod release [(download)](https://www.minecraft.net/)
2. Install [Fabric Loader](https://fabricmc.net/use/) ≥ 0.18.4
3. Get the latest [Fabric API](https://modrinth.com/mod/fabric-api/) release for your MC version
4. Get the latest MOAR version here [(download)](https://github.com/evilinc-labs/MOAR/releases/latest)
5. *(Optional)* Get the corresponding [Baritone](https://github.com/cabaletta/baritone/releases) build for pathfinding
6. Put the files in your `.minecraft/mods` folder

### Supported Versions

| Minecraft | Fabric API | Java |
|-----------|------------|------|
| 1.21.4 | 0.119.4+1.21.4 | 21 |
| 1.21.5 | 0.128.2+1.21.5 | 21 |
| 1.21.8 | 0.136.1+1.21.8 | 21 |
| 1.21.9–1.21.10 | 0.138.4+1.21.10 | 21 |
| 1.21.11 | 0.141.3+1.21.11 | 21 |
| 26.1.1 (unobfuscated) | 0.145.4+26.1.1 | 25 |

## Getting Started

How do I...

<details>
<summary><strong>... open the GUI?</strong></summary>

> Run `/moar gui` — it has tabs for Kits, Index, Regions, Retrieve, Printer, Spawnproof, and API settings.

</details>

<details>
<summary><strong>... build a schematic?</strong></summary>

> 1. Place a schematic in Litematica
> 2. Mark your supply chests with `/printer supply add` while looking at each container
> 3. Run `/printer start` — it detects the placement and starts building
> 4. Want it to walk, restock, and build on its own? `/printer autobuild on`
> 5. If you disconnect mid-build, return and run `/printer resume` — it continues automatically

</details>

<details>
<summary><strong>... build multiple schematics in a row?</strong></summary>

> 1. Place multiple schematic placements in Litematica
> 2. Run `/printer queue detect` to auto-detect and queue all placements
> 3. Mark your supply chests with `/printer supply add`
> 4. Run `/printer autobuild on`, then `/printer start` — each schematic completes and auto-advances to the next
> 5. Check progress with `/printer queue status`

</details>

<details>
<summary><strong>... travel the nether highways?</strong></summary>

> Run `/moar travel goto <x> <z>` for nether coordinates, or `/moar travel goto <x> <z> overworld` to auto-divide overworld coordinates by 8. MOAR plans the route, bounces down the highway, detours around grief, and resupplies elytras on the way.

</details>

<details>
<summary><strong>... spawn-proof an area?</strong></summary>

> Set corners with `/spawnproof pos1` and `/spawnproof pos2`, mark a light supply chest with `/spawnproof supply add`, then run `/spawnproof start`.

</details>

## Commands

<details>
<summary><strong>MOAR (Core)</strong></summary>

| Command | What it does |
|---------|--------------|
| `/moar gui` | Open the MOAR control screen (Kits, Index, Regions, Retrieve, Printer, Spawnproof, API tabs) |

</details>

<details>
<summary><strong>Printer</strong></summary>

| Command | What it does |
|---------|--------------|
| `/printer start` | Start building — detects your Litematica placement automatically |
| `/printer stop` | Stop building |
| `/printer info` | Show the details: progress, region, AutoBuild, supplies, pathfinding |
| `/printer toggle` | Start/stop the printer in its current mode (manual or AutoBuild) |
| `/printer autobuild on\|off\|toggle` | Control AutoBuild mode explicitly |
| `/printer load <file>` | Load a `.litematic` schematic |
| `/printer unload` | Unload the current schematic |
| `/printer detect` | Auto-detect active Litematica placements, with hologram-anchor fallback |
| `/printer list` | List available schematic files |
| `/printer here` | Anchor schematic to current position |
| `/printer pos <x> <y> <z>` | Anchor schematic to specific coordinates |
| `/printer status` | Alias of `/printer info` |
| `/printer materials` | Show required materials vs. supply inventory |
| `/printer resume` | Resume from last checkpoint and continue building |
| `/printer holes` | List abandoned build targets, with a bounding box to help you find them |
| `/printer holes retry` | Clear the abandoned list so those positions are re-attempted on the next scan |
| `/printer speed [1–20]` | Get/set placement speed (default: 19 blocks/sec) |
| `/printer sort [mode]` | Set build order: `auto` (default — picks the best strategy for the build), `bottom_up`, `top_down`, `nearest` |
| `/printer air` | Toggle air placement (floating blocks) |
| `/printer camera [on\|off]` | `on` (default) turns your view to each block — the anticheat-safe path; `off` aims silently so your view stays put |
| `/printer supply add [x y z]` | Mark a container as a supply source |
| `/printer supply remove` | Unmark the nearest supply chest |
| `/printer supply list` | List all supply chests |
| `/printer supply scan` | Show indexed supply inventory summary |
| `/printer supply clear` | Remove all supply chests |
| `/printer dump add [x y z]` | Mark a container as a dump chest |
| `/printer dump remove` | Unmark the nearest dump chest |
| `/printer dump list` | List all dump chests |
| `/printer dump clear` | Remove all dump chests |

</details>

<details>
<summary><strong>Printer Queue</strong></summary>

| Command | What it does |
|---------|--------------|
| `/printer queue status` | Show active build + queued builds |
| `/printer queue add` | Queue the currently loaded schematic at its current anchor |
| `/printer queue detect` | Auto-detect Litematica placements and add to queue |
| `/printer queue next` | Pause the current build and move it behind the rest of the queue |
| `/printer queue skip [reason]` | Skip current build (marks as failed) |
| `/printer queue clear` | Clear all queued builds (not active build) |
| `/printer queue auto on\|off` | Toggle automatic queue advancement (default: on) |
| `/printer queue move <taskId> <position>` | Move task to specific position (1-indexed) |
| `/printer queue up <taskId>` | Move task up one position |
| `/printer queue down <taskId>` | Move task down one position |
| `/printer queue top <taskId>` | Move task to front (build next) |
| `/printer queue bottom <taskId>` | Move task to back (build last) |

**Note**: Task IDs are shown in the queue status output as short 8-character identifiers (e.g., `[a1b2c3d4]`). The queue is not persisted across disconnects — re-run `/printer queue detect` after reconnecting.

</details>

<details>
<summary><strong>Travel</strong></summary>

| Command | What it does |
|---------|--------------|
| `/moar travel goto <x> <z>` | Travel to nether coordinates |
| `/moar travel goto <x> <z> overworld` | Travel to overworld coordinates (auto-divides by 8) |
| `/moar travel goto <x> <z> nether` | Explicit nether coordinates |
| `/moar travel bounce` | Snap yaw to nearest 45° and bounce indefinitely in that direction |
| `/moar travel stop` | Stop the current travel mission |
| `/moar travel pause` | Pause the mission |
| `/moar travel resume` | Resume a paused mission or re-plan after stop |
| `/moar travel status` | Show current phase, owner, ticks, and last transition reason |
| `/moar travel log` | Print recent mission log entries |
| `/moar travel enderchest <x> <y> <z>` | Register an ender chest for elytra resupply |
| `/moar travel elytra resupply-count <n>` | Set how many elytras to pull per shulker trip (1–27) |
| `/moar travel elytra repair` | Run a standalone Mending cycle without a travel mission |

</details>

<details>
<summary><strong>SpawnProofer</strong></summary>

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

</details>

<details>
<summary><strong>Stash</strong></summary>

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
| `/stash kit create\|snapshot\|add\|remove\|show\|list\|delete\|load` | Manage and retrieve named kits |
| `/stash region save\|load\|list\|delete` | Manage named region profiles |
| `/stash dump add [x y z]` | Mark a dump chest |
| `/stash dump remove` | Unmark the nearest dump chest |
| `/stash dump list` | List all dump chests |
| `/stash dump clear` | Remove all dump chests |

</details>

<details>
<summary><strong>Storage Lanes</strong></summary>

| Command | What it does |
|---------|--------------|
| `/stash lanes` | Show lane subcommand help |
| `/stash lanes region pos1 [x y z]` | Set lane region corner 1 (default: player position) |
| `/stash lanes region pos2 [x y z]` | Set lane region corner 2 |
| `/stash lanes scan` | Scan the region for candidate lanes |
| `/stash lanes preview` | Preview pending (unsaved) lanes from the last scan |
| `/stash lanes accept` | Save pending lanes to the database |
| `/stash lanes list` | List accepted lanes |
| `/stash lanes clear` | Delete all accepted lanes |
| `/stash lanes create <name> [item]` | Create a manual lane, optionally pre-assigned to an item |
| `/stash lanes addchest <name>` | Add the chest/barrel/shulker you're looking at to a lane |
| `/stash lanes addinput <name>` | Add the hopper/container you're looking at as a lane input |
| `/stash lanes setmode <name> <mode>` | Set deposit mode: `direct_fill`, `input_only`, or `hybrid` |
| `/stash lanes setface <name> <direction>` | Set the lane's front-facing direction |
| `/stash lanes remove <name>` | Remove a lane |
| `/stash lanes assign <item>` | Assign an item to the chest/barrel you're looking at |
| `/stash lanes sort` | Sort your inventory into accepted lanes |
| `/stash lanes sort preview` | Preview inventory moves without executing them |
| `/stash lanes sort stop` | Stop an in-progress sort |

</details>

<details>
<summary><strong>API & Monitoring</strong></summary>

MOAR includes an embedded HTTP API for Grafana dashboards, Prometheus scraping, and n8n webhook integrations. All settings live in `config/moar/moar.properties` (auto-created on first launch).

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

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/status` | GET | Scanner state, index size, region info |
| `/api/v1/containers?page=1&size=50` | GET | Paginated container list |
| `/api/v1/search?item=diamond` | GET | Find containers holding an item |
| `/api/v1/stats` | GET | Aggregate statistics (JSON) |
| `/api/v1/metrics` | GET | Prometheus-format metrics |
| `/api/v1/organizer` | GET | Organizer state and progress |
| `/api/v1/webhook/test` | POST | Webhook connectivity test |

All endpoints accept `Authorization: Bearer <api.key>` when `api.key` is set. See [docs/grafana-setup.md](docs/grafana-setup.md) for a step-by-step Prometheus + Grafana guide via Docker Compose.

</details>

### Keybinds

| Key | Action |
|-----|--------|
| `Numpad 0` | Toggle printer on/off |

## Developing

### Building from Source

Requires **JDK 21+** for 1.21.x builds. The experimental 26.1.1 target requires **JDK 25+** and is only included when explicitly requested.

```bash
./gradlew build                  # Build targets supported by the current JDK
./gradlew :1.21.8:build          # Build one version
./gradlew -Pmoar.includeJava25Targets=true build  # Include 26.1.1 on JDK 25+
./gradlew buildAndCollect        # Collect all JARs -> build/libs/<mod.version>/
```

---

> ### Disclaimer
> MOAR is not affiliated with Mojang Studios. Minecraft is a registered trademark of Mojang Studios.
> Use of the MOAR software is subject to the terms outlined in the license agreement [GNU Affero General Public License v3.0](LICENSE).
