package com.voxelpanel.firebase;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.voxelpanel.VoxelPanel;
import com.voxelpanel.models.Waypoint;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.time.Instant;

/**
 * Listens to the dashboard "commands" queue and executes each command once,
 * then removes it. Every action is recorded via ActivityLogger for auditing.
 *
 * Supported command types (value formats in comments):
 *   broadcast          "<message>"
 *   kick               "<player>"
 *   toggle_system      "true" | "false"
 *   delete_waypoint    "<id>"
 *   rename_waypoint     "<id>:<newName>"
 *   ban                "<name>" | "<name>|<reason>"
 *   ban_id             "<uuid>" | "<uuid>|<reason>"
 *   unban              "<name>"
 *   whitelist_add      "<name>"
 *   whitelist_remove   "<name>"
 *   set_rank           "<name>:<rank>"
 *   msg                "<name>:<message>"
 *   tp_player          "<name>:<targetName>"      (teleport name -> targetName)
 *   tp_coords          "<name>:<world>:<x>:<y>:<z>"
 *   gamemode           "<name>:<survival|creative|adventure|spectator>"
 *   time               "day|night|noon|midnight|<ticks>"   (all worlds)
 *   weather            "clear|rain|thunder"                (all worlds)
 *   save_all           ""
 *   console            "<raw console command>"
 *   create_public_waypoint  "<name>:<world>:<x>:<y>:<z>"
 *   edit_waypoint_coords    "<id>:<x>:<y>:<z>"
 */
public class FirebaseCommandListener {
    private final VoxelPanel plugin;
    private final FirebaseManager firebaseManager;
    private DatabaseReference commandsRef;
    private ChildEventListener listener;

    public FirebaseCommandListener(VoxelPanel plugin, FirebaseManager firebaseManager) {
        this.plugin = plugin;
        this.firebaseManager = firebaseManager;
    }

    public void start() {
        DatabaseReference serverRef = firebaseManager.getServerRef();
        if (serverRef == null) {
            return;
        }
        commandsRef = serverRef.child("commands");
        listener = new ChildEventListener() {
            @Override public void onChildAdded(DataSnapshot snapshot, String prev) { handleCommand(snapshot); }
            @Override public void onChildChanged(DataSnapshot snapshot, String prev) {}
            @Override public void onChildRemoved(DataSnapshot snapshot) {}
            @Override public void onChildMoved(DataSnapshot snapshot, String prev) {}
            @Override public void onCancelled(DatabaseError error) {
                plugin.getLogger().warning("[Firebase] Command listener cancelled: " + error.getMessage());
            }
        };
        commandsRef.addChildEventListener(listener);
        plugin.getLogger().info("[Firebase] Command listener started.");
    }

    public void stop() {
        if (commandsRef != null && listener != null) {
            commandsRef.removeEventListener(listener);
        }
    }

    private void handleCommand(DataSnapshot snapshot) {
        String type, value, issuedBy;
        String key = snapshot.getKey();
        // Refuse to run remote commands while unauthorized (revoked/rotated token).
        if (plugin.getServerIdentity() != null && !plugin.getServerIdentity().isAuthorized()) {
            removeCommand(key);
            return;
        }
        try {
            type = snapshot.child("type").getValue(String.class);
            value = snapshot.child("value").getValue(String.class);
            issuedBy = snapshot.child("issuedBy").getValue(String.class);
        } catch (Exception e) {
            // Malformed command (wrong value types) — drop it so it is not retried.
            plugin.getLogger().warning("[Firebase] Dropping malformed command: " + e.getMessage());
            removeCommand(key);
            return;
        }
        if (type == null) {
            removeCommand(key);
            return;
        }

        final String fType = type, fValue = value, fBy = issuedBy;
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                execute(fType.toLowerCase(), fValue, fBy);
            } catch (Exception e) {
                plugin.getLogger().warning("[Firebase] Error executing command '" + fType + "': " + e.getMessage());
            } finally {
                removeCommand(key);
            }
        });
    }

    private void execute(String type, String value, String issuedBy) {
        switch (type) {
            case "broadcast" -> {
                if (value != null) {
                    Bukkit.broadcastMessage(plugin.getMessageManager().get("prefix") + value);
                    log("broadcast", value, issuedBy);
                }
            }
            case "kick" -> {
                Player t = Bukkit.getPlayerExact(value);
                if (t != null) t.kickPlayer("Kicked from dashboard");
                log("kick", value, issuedBy);
            }
            case "toggle_system" -> {
                boolean enable = Boolean.parseBoolean(value);
                plugin.setSystemEnabled(enable);
                log("toggle_system", enable ? "enabled" : "disabled", issuedBy);
            }
            case "delete_waypoint" -> {
                int id = parseInt(value, -1);
                if (id >= 0) {
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> plugin.getWaypointRepository().deleteWaypoint(id));
                    log("delete_waypoint", "#" + id, issuedBy);
                }
            }
            case "rename_waypoint" -> {
                String[] p = split(value, 2);
                if (p != null) {
                    int id = parseInt(p[0], -1);
                    if (id >= 0) {
                        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> plugin.getWaypointRepository().renameWaypoint(id, p[1]));
                        log("rename_waypoint", "#" + id + " -> " + p[1], issuedBy);
                    }
                }
            }
            case "ban" -> {
                String[] nr = splitReason(value);
                plugin.getModerationManager().banPlayer(nr[0], nr[1]);
                log("ban", nr[0] + (nr[1].isEmpty() ? "" : " (" + nr[1] + ")"), issuedBy);
            }
            case "ban_id" -> {
                String[] nr = splitReason(value);
                plugin.getModerationManager().banPlayerId(nr[0], nr[1]);
                log("ban_id", nr[0], issuedBy);
            }
            case "unban" -> {
                if (value != null) { plugin.getModerationManager().unbanPlayer(value); log("unban", value, issuedBy); }
            }
            case "whitelist_add" -> {
                if (value != null) { plugin.getModerationManager().whitelistAdd(value); log("whitelist_add", value, issuedBy); }
            }
            case "whitelist_remove" -> {
                if (value != null) { plugin.getModerationManager().whitelistRemove(value); log("whitelist_remove", value, issuedBy); }
            }
            case "set_rank" -> {
                String[] p = split(value, 2);
                if (p != null) { plugin.getRankManager().setRank(p[0], p[1]); log("set_rank", p[0] + " -> " + p[1], issuedBy); }
            }
            case "msg" -> {
                String[] p = split(value, 2);
                if (p != null) {
                    Player t = Bukkit.getPlayerExact(p[0]);
                    if (t != null) t.sendMessage(plugin.getMessageManager().get("prefix") + "§d[Admin] §f" + p[1]);
                    log("msg", p[0] + ": " + p[1], issuedBy);
                }
            }
            case "tp_player" -> {
                String[] p = split(value, 2);
                if (p != null) {
                    Player a = Bukkit.getPlayerExact(p[0]);
                    Player b = Bukkit.getPlayerExact(p[1]);
                    if (a != null && b != null) { a.teleport(b.getLocation()); log("tp_player", p[0] + " -> " + p[1], issuedBy); }
                }
            }
            case "tp_coords" -> {
                // name:world:x:y:z
                String[] p = value != null ? value.split(":", 5) : null;
                if (p != null && p.length == 5) {
                    Player a = Bukkit.getPlayerExact(p[0]);
                    World w = Bukkit.getWorld(p[1]);
                    if (a != null && w != null) {
                        a.teleport(new Location(w, parseD(p[2]), parseD(p[3]), parseD(p[4])));
                        log("tp_coords", p[0] + " -> " + p[1] + " " + p[2] + "," + p[3] + "," + p[4], issuedBy);
                    }
                }
            }
            case "gamemode" -> {
                String[] p = split(value, 2);
                if (p != null) {
                    Player t = Bukkit.getPlayerExact(p[0]);
                    GameMode gm = parseGameMode(p[1]);
                    if (t != null && gm != null) { t.setGameMode(gm); log("gamemode", p[0] + " -> " + gm.name(), issuedBy); }
                }
            }
            case "time" -> {
                Long ticks = parseTime(value);
                if (ticks != null) {
                    for (World w : Bukkit.getWorlds()) w.setTime(ticks);
                    log("time", value, issuedBy);
                }
            }
            case "weather" -> {
                if (value != null) {
                    for (World w : Bukkit.getWorlds()) applyWeather(w, value);
                    log("weather", value, issuedBy);
                }
            }
            case "save_all" -> {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "save-all");
                log("save_all", "", issuedBy);
            }
            case "inspect_player" -> {
                if (value != null) plugin.getPlayerInspector().publish(value);
            }
            case "refresh_plugins" -> {
                plugin.getPluginManagerBridge().publishPlugins();
            }
            case "refresh_auth" -> {
                if (plugin.getAuthMirrorService() != null) plugin.getAuthMirrorService().publish();
            }
            case "files_list" -> {
                if (plugin.getFileManagerBridge() != null) plugin.getFileManagerBridge().listDir(value != null ? value : "");
            }
            case "files_read" -> {
                if (value != null && plugin.getFileManagerBridge() != null) plugin.getFileManagerBridge().readFile(value);
            }
            case "files_write" -> {
                // value format: "<path>\u0000<content>"
                if (value != null) {
                    int i = value.indexOf('\u0000');
                    if (i >= 0) plugin.getFileManagerBridge().writeFile(value.substring(0, i), value.substring(i + 1), issuedBy);
                }
            }
            case "files_delete" -> {
                if (value != null && plugin.getFileManagerBridge() != null) plugin.getFileManagerBridge().deleteFile(value, issuedBy);
            }
            case "power" -> {
                // value = start|stop|restart|kill
                if (value != null && plugin.getPanelController() != null) plugin.getPanelController().sendPower(value, issuedBy);
            }
            case "download_plugin" -> {
                // value format: "<url>|<fileName>"
                if (value != null) {
                    int i = value.indexOf('|');
                    String url = i < 0 ? value : value.substring(0, i);
                    String fileName = i < 0 ? null : value.substring(i + 1);
                    plugin.getPluginManagerBridge().downloadPlugin(url, fileName, issuedBy);
                }
            }
            case "console" -> {
                if (value != null && !value.isBlank()) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), value);
                    log("console", value, issuedBy);
                }
            }
            case "backup_gdrive" -> {
                // value = the Google OAuth access token (drive.file scope)
                if (value != null && plugin.getBackupService() != null) {
                    plugin.getBackupService().startGoogleDriveBackup(value, issuedBy);
                }
            }
            case "backup_cancel" -> {
                if (plugin.getBackupService() != null) plugin.getBackupService().cancel(issuedBy);
            }
            case "create_public_waypoint" -> {
                // name:world:x:y:z
                String[] p = value != null ? value.split(":", 5) : null;
                if (p != null && p.length == 5) {
                    String wpName = p[0];
                    String world = p[1];
                    double x = parseD(p[2]), y = parseD(p[3]), z = parseD(p[4]);
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                        Waypoint wp = new Waypoint(0, java.util.UUID.nameUUIDFromBytes(("server:" + wpName).getBytes()),
                                "SERVER", wpName, world, x, y, z, 0f, 0f, Instant.now(), "OTHER", "BEACON", true, false, null);
                        plugin.getWaypointRepository().insertWaypoint(wp);
                    });
                    log("create_public_waypoint", wpName, issuedBy);
                }
            }
            case "edit_waypoint_coords" -> {
                // id:x:y:z  (handled by repository update)
                String[] p = value != null ? value.split(":", 4) : null;
                if (p != null && p.length == 4) {
                    int id = parseInt(p[0], -1);
                    if (id >= 0) {
                        double x = parseD(p[1]), y = parseD(p[2]), z = parseD(p[3]);
                        Bukkit.getScheduler().runTaskAsynchronously(plugin,
                                () -> plugin.getWaypointRepository().updateWaypointCoords(id, x, y, z));
                        log("edit_waypoint_coords", "#" + id, issuedBy);
                    }
                }
            }
            default -> plugin.getLogger().warning("[Firebase] Unknown command type: " + type);
        }
    }

    // ---- helpers ----
    private void log(String action, String target, String by) {
        plugin.getActivityLogger().log(action, target, by);
    }

    private void removeCommand(String key) {
        if (commandsRef != null && key != null) commandsRef.child(key).removeValueAsync();
    }

    private String[] splitReason(String value) {
        if (value == null) return new String[]{"", ""};
        int i = value.indexOf('|');
        if (i < 0) return new String[]{value, ""};
        return new String[]{value.substring(0, i), value.substring(i + 1)};
    }

    /** Splits "a:b" into [a, b] on the first colon; returns null if not present. */
    private String[] split(String value, int parts) {
        if (value == null) return null;
        int i = value.indexOf(':');
        if (i < 0) return null;
        return new String[]{value.substring(0, i), value.substring(i + 1)};
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }
    private double parseD(String s) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return 0; }
    }

    private GameMode parseGameMode(String s) {
        return switch (s.toLowerCase()) {
            case "survival", "0" -> GameMode.SURVIVAL;
            case "creative", "1" -> GameMode.CREATIVE;
            case "adventure", "2" -> GameMode.ADVENTURE;
            case "spectator", "3" -> GameMode.SPECTATOR;
            default -> null;
        };
    }

    private Long parseTime(String s) {
        if (s == null) return null;
        return switch (s.toLowerCase()) {
            case "day" -> 1000L;
            case "noon" -> 6000L;
            case "night" -> 13000L;
            case "midnight" -> 18000L;
            default -> { try { yield Long.parseLong(s.trim()); } catch (Exception e) { yield null; } }
        };
    }

    private void applyWeather(World w, String weather) {
        switch (weather.toLowerCase()) {
            case "clear" -> { w.setStorm(false); w.setThundering(false); }
            case "rain" -> { w.setStorm(true); w.setThundering(false); }
            case "thunder" -> { w.setStorm(true); w.setThundering(true); }
        }
    }
}
