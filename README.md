# VoxelPanel

**VoxelPanel** is an advanced waypoint, teleportation, and remote-management
system for Paper Minecraft servers. It combines a full in-game waypoint suite
with an optional real-time web dashboard that lets you monitor and control your
server from anywhere — securely, with automatic connection and zero manual
polling.

> Two parts, one product:
> - **Plugin** — the Paper plugin that runs on your Minecraft server.
> - **Dashboard** — a static web control panel backed by Firebase Realtime Database.

---

## Table of Contents

- [Features](#features)
- [Architecture](#architecture)
- [Requirements](#requirements)
- [Installation](#installation)
- [Connecting to VoxelPanel](#connecting-to-voxelpanel)
- [Configuration](#configuration)
- [Commands](#commands)
- [Permissions](#permissions)
- [Player Usage](#player-usage)
- [Admin Usage](#admin-usage)
- [Security Model](#security-model)
- [Building from Source](#building-from-source)
- [Troubleshooting](#troubleshooting)
- [Version Compatibility](#version-compatibility)

---

## Features

### Waypoints & Teleportation
- Private, per-player waypoint storage with SQLite persistence
- GUI-driven waypoint menu with create, rename, search, teleport, and delete
- Categories (`MINE`, `BASE`, `FARM`, `OTHER`) and custom item icons
- Public waypoints (server warps) and player-to-player waypoint sharing
- Partial, case-insensitive search with pagination for large lists
- Safe teleportation with optional delay, cooldown, particle effects, and countdown titles
- Death waypoints with auto-expiry and optional compass tracking
- `/back` to return to your previous location
- Player-to-player teleport requests (`/tpe`)
- Multi-language support (Arabic and English), switchable per player

### Real-Time Web Dashboard (optional)
- Live server overview: TPS, uptime, online players, entities, chunks, worlds
- Player management: inspect inventory, message, teleport, change gamemode, set rank
- Moderation: bans and whitelist management
- Live console with command execution and streaming output
- File manager: browse and edit server files
- Plugin manager: view installed plugins and install from Modrinth
- Power controls (start / stop / restart / kill) via a Pterodactyl-style panel API
- Live chat bridge between the dashboard and in-game chat
- Analytics: online-players-over-time, waypoint growth, category distribution
- Activity log auditing every action taken from the panel
- Multi-server support with a server switcher and per-server connection status

### Connection System
- Dashboard-first onboarding: **Create Server → Stop → Add Plugin → Generate Config → Configure → Start & Register**
- Unique server id + secret auth token issued per server
- Automatic, near real-time connection with heartbeats
- Automatic reconnection after network failures (handled by the Firebase SDK)
- Real-time credential revocation and rotation from the dashboard
- Network errors never crash the Minecraft server

---

## Architecture

```
Dashboard (static site)  <->  Firebase Realtime Database  <->  Plugin (Paper server)
```

- The **plugin** pushes live data (stats, players, waypoints, worlds) to Firebase
  every few seconds and listens for a command queue in real time.
- The **dashboard** reads that data live and writes commands back; it never holds
  the Firebase service account. All access is enforced by Firebase Authentication
  and Realtime Database Security Rules.
- **Authorization** is driven by a panel-issued node token (`web.token`). The plugin
  live-watches the token and stands down instantly if it is revoked or rotated —
  without ever throwing on the main server thread.

---

## Requirements

- Paper server for Minecraft **1.21.1** (Paper API `1.21.1-R0.1-SNAPSHOT`)
- **Java 21** or newer
- **Maven 3.9+** (only needed to build from source)
- A Firebase project with Realtime Database (only needed for the dashboard)

---

## Installation

### 1. Get the plugin JAR
Download a release JAR, or build it yourself (see [Building from Source](#building-from-source)).
Pre-built output is placed in `creat plagin/VoxelPanel-1.0.0.jar`.

### 2. Install on your server
1. Copy the JAR into your Paper server's `plugins/` folder.
2. Start the server once. The plugin creates `plugins/VoxelPanel/` with a
   default `config.yml`, language files, and an SQLite database.
3. Stop the server to edit configuration, or continue to connect the dashboard.

Waypoints work fully offline — the dashboard connection is optional.

---

## Connecting to VoxelPanel

Onboarding follows six steps and the plugin registers itself automatically:

1. **Create Server** — create the server in VoxelPanel and copy the node token it shows once.
2. **Stop Server** — stop the Minecraft server.
3. **Add Plugin** — drop the VoxelPanel jar into the server's `plugins/` directory.
4. **Generate Config** — start the server once so `plugins/VoxelPanel/config.yml` is generated, then stop it again.
5. **Configure Plugin** — set `web.url` and `web.token` in `plugins/VoxelPanel/config.yml`.
6. **Start & Register** — start the server. It registers itself automatically and appears as online in the panel.

To disconnect or rotate credentials, open the server's edit dialog in the panel
and choose **Regenerate config**. The old token stops working immediately, and
you are given a fresh `config.yml`.

> The Firebase Admin service account key (`firebase-service-account.json`) is
> required for the plugin to reach the backend. Keep it private — it is
> git-ignored and must never be committed or shared.

---

## Configuration

All settings live in `plugins/VoxelPanel/config.yml`.

| Section | Key | Purpose |
|---|---|---|
| `waypoints` | `max-per-player` | Maximum waypoints per player (default `10`) |
| `waypoints` | `max-name-length` | Maximum waypoint name length |
| `waypoints` | `categories` | Available waypoint categories |
| `teleport` | `delay` / `cooldown-seconds` | Teleport warmup and cooldown |
| `teleport` | `safe-teleport` | Prevents teleporting into unsafe blocks |
| `death-waypoints` | `enabled` / `expiry-seconds` | Automatic death markers |
| `compass` | `enabled` / `update-interval` | Compass tracking behavior |
| `language` | `default` | Default language (`ar` or `en`) |
| `sounds` | `enabled` | UI and action sounds |
| `web` | `url` | VoxelPanel backend URL |
| `web` | `token` | Panel-issued node token that authorizes this server |
| `web` | `websocket` / `verify-tls` | Realtime channel + TLS verification |
| `firebase` | `enabled` | Enable the panel connection |
| `firebase` | `server-id` | Unique server id (auto-generated if blank) |
| `firebase` | `config-version` | Config format version (set by the panel) |
| `firebase` | `sync-interval-seconds` | How often live data is pushed |
| `firebase` | `heartbeat-seconds` | How often the plugin proves it is alive |
| `panel` | `base-url` / `api-key` / `server-identifier` | Pterodactyl-style power controls |

- `messages.yml`, `ar.yml`, and `en.yml` hold player-facing text and support
  Minecraft color codes.
- SQLite data is written to `plugins/VoxelPanel/data.db`.

---

## Commands

| Command | Description | Usage |
|---|---|---|
| `/tpu` | Open the waypoint menu or teleport to a named waypoint | `/tpu [waypoint name]` |
| `/tpubook` | Get a book listing all commands | `/tpubook` |
| `/waypoint` (`/wp`) | Manage waypoints via commands | `/waypoint <set\|del\|list\|category\|icon\|share\|public> [args]` |
| `/tpuadmin` | Open the administrator menu | `/tpuadmin` |
| `/compass` | Toggle the compass tracker | `/compass <track\|reset>` |
| `/language` (`/lang`) | Change your language | `/language [ar\|en]` |
| `/tpe` | Request to teleport to another player | `/tpe <player>` |
| `/tpeaccept` | Accept a pending teleport request | `/tpeaccept` |
| `/tpedeny` | Deny a pending teleport request | `/tpedeny` |
| `/back` | Return to your previous location | `/back` |
| `/shareaccept` | Accept a pending waypoint share | `/shareaccept` |
| `/sharedeny` | Deny a pending waypoint share | `/sharedeny` |

---

## Permissions

| Permission | Description | Default |
|---|---|---|
| `voxelpanel.use` | Access to waypoint commands | everyone |
| `voxelpanel.admin` | Access to admin commands | op |
| `voxelpanel.admin.view` | View other players' waypoints | op |
| `voxelpanel.admin.teleport` | Teleport to other players' waypoints | op |
| `voxelpanel.admin.rename` | Rename other players' waypoints | op |
| `voxelpanel.admin.delete` | Delete other players' waypoints | op |
| `voxelpanel.bypass.delay` | Bypass teleport delay | op |
| `voxelpanel.bypass.limit` | Bypass the waypoint limit | op |

---

## Player Usage

- Run `/tpu` to open the waypoint menu.
- Choose **Create Waypoint** to save your current location.
- Browse saved points under **My Waypoints**.
- Use **Search Waypoints** to find points by partial name.
- From a waypoint's action menu you can teleport, rename, set a category or icon,
  share it with another player, or delete it.
- Use `/back` to return to your last location, and `/compass track` to point your
  compass at a chosen waypoint.

---

## Admin Usage

- Run `/tpuadmin` to open the admin menu.
- Search for a player by username.
- View and manage that player's waypoints (view / teleport / rename / delete),
  subject to your permissions.
- For remote management, use the web dashboard: moderation, console, files,
  plugins, power controls, and live analytics.

---

## Security Model

- The dashboard uses **Firebase Authentication + Realtime Database Security Rules**;
  it never ships or holds the Admin service account key.
- Each server has a unique `server-id` and secret node token (`web.token`). The
  plugin proves it holds a matching token and never writes the token back.
- The plugin **live-watches** its authorization. Revoking or rotating the token in
  the dashboard stops all syncing and command execution within seconds.
- Duplicate or unauthorized connections are detectable via a per-boot `instanceId`.
- Publish the rules in `dashboard/firebase-rules.json` to your Firebase project.
  Editing the file alone does not deploy them.

---

## Building from Source

Using Maven directly:

```bash
mvn clean package
```

On Windows, you can also run the bundled helper, which downloads a local Maven if
needed and copies the finished JAR into `creat plagin/`:

```bat
build-plugin.cmd
```

The shaded JAR is produced in `target/` and relocates bundled Google/Firebase
libraries to avoid clashing with Paper's own dependencies.

---

## Troubleshooting

- **Panel shows "Not connected".** Confirm `web.url`, `firebase.server-id`,
  and `web.token` match the values the panel generated, and that
  the host allows outbound connections to the backend. The console logs a clear
  "LIVE CONNECTION OK" line when reachable.
- **"Auth token was revoked or rotated".** Copy a fresh `config.yml` from the
  panel's Regenerate config action.
- **SQLite fails to initialize.** Check write permissions on the server directory.
- **A world is missing.** Teleportation is blocked with a message rather than
  crashing.
- **Invalid config value.** The plugin logs the issue and falls back to defaults
  where possible.

---

## Version Compatibility

| Component | Version |
|---|---|
| Plugin | 2.0.0 |
| Server | Paper / Minecraft 1.21.1 |
| API | Paper API `1.21.1-R0.1-SNAPSHOT` |
| Java | 21+ |
| Dashboard | Firebase Realtime Database + static hosting |
