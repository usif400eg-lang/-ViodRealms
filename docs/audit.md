# ViodRealms 2.0 — Project Audit & Migration Strategy

Status: **audit complete, implementation not started** (per §2: "Do not start major
coding until this audit and strategy are documented").

Audited commit: `4b25f81` (dashboard) / `e4c9bc8` (plugin).

---

## 1. What exists today

| Layer | Implementation | Size |
|---|---|---|
| Frontend | Static SPA: `index.html`, `app.js`, `style.css` | 1,098 / 2,502 / 1,027 lines |
| Data/realtime | Firebase Realtime Database (compat SDK, loaded from CDN) | — |
| Authorization | `firebase-rules.json` | 93 lines |
| Agent | Paper plugin, `com.voxelpanel` | 61 files, 6,101 lines |
| Backend | **none** | — |
| Build tooling | `package.json` with a single dependency | 5 lines |
| Tests | **none** | — |

### Feature state

**Working:** auth (email/Google/GitHub) with approval gate; multi-server switcher;
live stats/players/worlds/waypoints; fleet overview with metric cards and
performance history; console (read + execute); file manager (list/read/edit);
plugin list + Modrinth install; moderation (ban/whitelist); chat bridge; activity
log; Pterodactyl power controls; register-a-server flow with one-time `vp_` token
and live auth-watch revocation; AR/EN with RTL/LTR; dark/light themes.

**Partial:** metrics history (TPS/MSPT/CPU/heap published, no aggregation or
retention); alerts/reports/mutes/scheduled-task counters (UI + rules exist, no
producer); permissions (binary owner/admin only).

**Absent:** RBAC engine, backups, cloud storage, alert rules, notification
centre, AI agent, 2FA/session management, world management, warn/mute/timeout
punishments, node command policy enforcement, tests, module structure, docs.

---

## 2. Findings

Severity: **P0** = fix before further feature work; **P1** = fix during migration;
**P2** = improvement.

### P0-1 — Firebase Admin private key is shipped inside the distributed JAR

`src/main/resources/firebase-service-account.json` is bundled into
`VoxelPanel-1.0.0.jar`. I confirmed the packaged entry contains real
`BEGIN PRIVATE KEY` material and `FirebaseManager` intentionally falls back to it
(`plugin.getResource(keyFileName)`).

Impact: anyone who obtains the JAR — any customer, any host, anyone who downloads
a release — holds **full Admin SDK credentials** for the entire Firebase project.
Admin SDK bypasses all security rules. This means total read/write over every
server, every user record, and every token in the database.

This single issue invalidates §6 ("never store raw server secrets unnecessarily"),
§7 (server-side enforcement), and §38 (token leakage). It is the most severe
problem in the codebase and no amount of rule hardening compensates for it, because
Admin credentials ignore rules entirely.

Remediation requires an architectural change, not a patch — see §4 Phase 0.

### P0-2 — No backend means authorization cannot be enforced as §7 requires

There is no server-side component. All privileged logic runs in the browser and
all writes go directly to Firebase. Consequences:

- `OWNER_EMAIL` is a client constant (`app.js:6`); owner gating is cosmetic.
- Any authenticated user who is a member of a server can write to
  `servers/{id}/commands` — the rules grant blanket write. A crafted write from
  the browser console executes **arbitrary console commands** on that Minecraft
  server, including `op`, `stop`, and `reload`.
- §8 (Node Command Policy) is unenforceable: `FirebaseCommandListener` has zero
  allow/deny checks. I grepped for `blocked|allowed|nodePolicy|policy` — no matches.
  The `nodePolicy` written by the register wizard is stored but never read.
- §7's privilege-escalation guard, §37 rate limiting, and §26 AI safety all
  require a trusted enforcement point that does not currently exist.

### P0-3 — Firebase rules have no validation and no schema constraints

`firebase-rules.json` contains **0** `.validate` rules. Any authorized writer can
store arbitrary structures and unbounded payloads anywhere they have write access
(`commands`, `chatOut`, `panelConfig`, `serverMeta`). No type checks, no length
limits, no enum constraints on command `type`.

### P1-1 — XSS surface in the dashboard

84 `innerHTML =` assignments against 81 `escapeHtml` call sites. The ratio implies
unescaped interpolation paths. Player names, waypoint names, chat messages, file
names, and plugin metadata all originate from the Minecraft server and are
attacker-influenced (any player can set their own name/waypoint text).

### P1-2 — Monolithic frontend blocks §3 and §39

`app.js` is 2,502 lines in one global scope with implicit cross-function coupling
(`myServers`, `ACTIVE_SERVER`, `charts`, `fleetStats`). There is no module system,
no bundler, no TypeScript, and no test harness, so nothing is unit-testable.

### P1-3 — Realtime listener growth is unbounded

Fleet watching attaches one listener per server (`watchFleet`, `watchFleetCounters`
→ 5 refs per server). At 1,000 servers that is ~6,000 concurrent listeners in one
browser tab. §34 (10,000 servers) is not reachable with per-server client
listeners; this needs server-side aggregation.

### P1-4 — No `indexOn` declarations

0 `indexOn` rules. Every `limitToLast`/ordered query is unindexed, which Firebase
resolves by downloading the full node client-side. This degrades badly as
`history`, `activity`, and `consoleLog` grow, and there is no retention policy.

### P1-5 — Account security gaps (§29)

0 matches for `multiFactor|totp|2fa|recoveryCode`. No 2FA, no session/device
management, no revoke, no account-deletion workflow, no security activity log.

### P2-1 — File bridge path handling is sound but incomplete

`FileManagerBridge.resolve()` correctly normalizes and enforces
`p.startsWith(root)`, which defeats basic `../` traversal. Gaps versus §14:
symlink traversal is not checked (`normalize()` does not resolve symlinks —
`toRealPath()` would), and there is no streaming for large files or upload size cap.

### P2-2 — No observability or graceful-degradation signalling (§36)

The UI shows online/offline only. CONNECTING / DEGRADED / UNKNOWN states, command
acknowledgement, request IDs, and timeouts do not exist, so a failed command is
indistinguishable from a slow one.

---

## 3. Blocking conflicts requiring your decision

### Conflict A — Branding

§1 says "Keep current ViodRealms branding and identity." In the previous session
you explicitly instructed a **global rename to VoxelPanel**, which is now shipped
across both repositories (`com.voxelpanel`, `VoxelPanel.jar`,
`plugins/VoxelPanel/config.yml`, all UI text, both docs sites).

These are mutually exclusive. I have **not** reverted anything. Options:
1. Keep VoxelPanel (treat §1's wording as stale). No work.
2. Revert to ViodRealms. Full re-rename including package paths and config paths;
   breaks every deployed `config.yml` path again.

### Conflict B — Scope versus a single turn

The prompt specifies 43 sections including a modular TypeScript rewrite, a
backend, an RBAC engine, a backup engine with resumable chunked transfer and
SHA-256 verification, a five-provider cloud storage abstraction, an AI operations
agent, 2FA, a test suite, and ten documentation files. That is a multi-month
program for a team, not one turn.

§42 and the closing line forbid claiming a feature is done without code, rules,
Agent behavior, UI, error handling, **and tests**. I will not produce 43 shallow
stubs and mark them complete — that would violate the standard you set. §41 also
mandates incremental migration with the product working throughout.

So this document is the deliverable for this turn, and Phase 0 below is what I
recommend building next.

---

## 4. Migration strategy

Each phase ends at a working, shippable state. Rollback point = the git tag taken
before the phase begins.

### Phase 0 — Credential containment (P0-1, P0-2, P0-3) — **do this first**

Everything else is built on sand until the Admin key is out of the JAR and a
trusted enforcement point exists.

1. Stand up a minimal backend (Firebase Cloud Functions is the least-new-infra
   option since the project is already on Firebase; a small Node service behind
   the existing host is equivalent). It holds the Admin credential.
2. Move the Agent's write path from "Admin SDK in the plugin" to
   "plugin authenticates to the backend with its `vp_` node token; backend writes
   to the database." The plugin then needs **no** Firebase credential at all and
   ships clean.
3. Route `commands` through the backend so §8 policy, §7 permissions, §37 rate
   limits, and §22 audit entries are enforced where the client cannot reach.
4. Rotate the leaked service-account key in Google Cloud IAM. The current key must
   be assumed compromised — it has been distributed inside JARs.
5. Add `.validate` rules and `indexOn` to `firebase-rules.json`; make `commands`
   client-writable **never** (backend-only).

Rollback: the plugin keeps its legacy direct-write path behind a config flag for
one release so existing servers do not break mid-migration.

### Phase 1 — Frontend modularization (§3, P1-2)
Introduce Vite + TypeScript, extract `app.js` into the §3 module tree behind a
`services/` layer, no behavior change. This is the prerequisite for §39 tests.

### Phase 2 — Permission engine (§7)
Roles + namespaced permissions in the database, enforced in the backend, with the
privilege-escalation guard. UI hiding becomes cosmetic-only by design.

### Phase 3 — Agent hardening (§5, §6, §36)
Request IDs, command acknowledgement, timeouts, exponential-backoff reconnect,
replay protection, DEGRADED/CONNECTING states.

### Phase 4 — Console, Players, Files, Plugins, Worlds (§9–§15)
Each on top of the permission engine and policy layer from Phases 0/2.

### Phase 5 — Monitoring, Alerts, Notifications (§20, §21, §23)
Server-side aggregation replaces per-server client listeners (fixes P1-3), plus
retention/rollup for `history`.

### Phase 6 — Backup engine (§16–§18)
Chunked resumable transfer, SHA-256 verification, then the storage abstraction
(§17), then the restore flow with Pterodactyl fallback (§19).

### Phase 7 — AI Operations Agent (§25, §26)
Built last, deliberately: it can only be made safe once RBAC, node policy, rate
limits, and audit logging are enforced server-side.

### Phase 8 — Account security, tests, docs, hardening (§29, §39, §40, §38)

---

## 5. Recommended next step

Phase 0, item 4 (**rotate the service-account key**) is the only item here that is
urgent independent of any code, because the existing key should be treated as
already leaked.

I recommend starting implementation at Phase 0. It is self-contained, fixes all
three P0 findings, and unblocks every later phase.
