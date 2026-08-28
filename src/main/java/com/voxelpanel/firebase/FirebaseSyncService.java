package com.voxelpanel.firebase;

import com.google.firebase.database.DatabaseReference;
import com.voxelpanel.VoxelPanel;
import com.voxelpanel.models.Waypoint;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Periodically pushes plugin data (waypoints, stats, online players) to Firebase
 * so the external dashboard can display live information.
 * All database reads happen asynchronously; only the Firebase writes touch the network.
 */
public class FirebaseSyncService {
    private final VoxelPanel plugin;
    private final FirebaseManager firebaseManager;
    private BukkitTask syncTask;

    public FirebaseSyncService(VoxelPanel plugin, FirebaseManager firebaseManager) {
        this.plugin = plugin;
        this.firebaseManager = firebaseManager;
    }

    private int cycle = 0;

    public void start() {
        if (!firebaseManager.isConnected()) {
            return;
        }
        // Fast live sync (default 3s) keeps the dashboard near real-time.
        long intervalTicks = plugin.getConfig().getLong("firebase.sync-interval-seconds", 3) * 20L;
        if (intervalTicks < 20L) intervalTicks = 20L; // floor at 1s to avoid overload
        syncTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::sync, 60L, intervalTicks);
        plugin.getLogger().info("[Firebase] Sync service started (every " + (intervalTicks / 20L) + "s).");
    }

    public void stop() {
        if (syncTask != null) {
            syncTask.cancel();
        }
    }

    /** Runs async: gathers data and writes it to Firebase. Online-player info is read on the main thread. */
    private void sync() {
        DatabaseReference serverRef = firebaseManager.getServerRef();
        if (serverRef == null) {
            return;
        }

        // Respect the live authorization state. An unauthorized (never-approved),
        // revoked, or rotated server must not publish any data to the shared tree.
        if (plugin.getServerIdentity() != null && !plugin.getServerIdentity().isAuthorized()) {
            return;
        }

        // Keep this server's public metadata (online/lastSeen) fresh for the dashboard list.
        if (plugin.getServerIdentity() != null) {
            plugin.getServerIdentity().publishMeta();
        }

        // Waypoints and stats can be read off-thread (repository uses its own connections).
        var knownRecords = plugin.getWaypointRepository().getKnownPlayers();
        List<Waypoint> allWaypoints = new ArrayList<>();
        Map<String, Integer> waypointCounts = new HashMap<>();
        for (var record : knownRecords) {
            var wps = plugin.getWaypointRepository().getWaypointsForPlayer(record.getUuid());
            allWaypoints.addAll(wps);
            waypointCounts.put(record.getPlayerName(), wps.size());
        }

        List<Map<String, Object>> waypointData = new ArrayList<>();
        for (Waypoint wp : allWaypoints) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("id", wp.getId());
            entry.put("owner", wp.getPlayerName());
            entry.put("ownerUuid", wp.getPlayerUuid().toString());
            entry.put("name", wp.getWaypointName());
            entry.put("world", wp.getWorldName());
            entry.put("x", wp.getX());
            entry.put("y", wp.getY());
            entry.put("z", wp.getZ());
            entry.put("category", wp.getCategory());
            entry.put("icon", wp.getIcon());
            entry.put("public", wp.isPublic());
            waypointData.add(entry);
        }

        // Known players list (everyone who has ever created a waypoint) with their rank.
        // Snapshot all ranks once (read config on this thread's single pass to avoid a
        // data race with setRank mutating the config on the main thread).
        Map<String, String> allRanks = plugin.getRankManager().getAllRanks();
        List<Map<String, Object>> knownPlayerData = new ArrayList<>();
        for (var record : knownRecords) {
            Map<String, Object> pd = new HashMap<>();
            pd.put("name", record.getPlayerName());
            pd.put("uuid", record.getUuid().toString());
            pd.put("rank", allRanks.getOrDefault(record.getPlayerName().toLowerCase(), "member"));
            pd.put("waypoints", waypointCounts.getOrDefault(record.getPlayerName(), 0));
            knownPlayerData.add(pd);
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalWaypoints", plugin.getWaypointRepository().getTotalWaypointCount());
        stats.put("publicWaypoints", plugin.getWaypointRepository().getPublicWaypointCount());
        stats.put("knownPlayers", knownRecords.size());
        stats.put("systemEnabled", plugin.isSystemEnabled());
        stats.put("lastSync", System.currentTimeMillis());

        // Online players + moderation snapshots must be read on the main thread.
        Bukkit.getScheduler().runTask(plugin, () -> {
            List<Map<String, Object>> players = new ArrayList<>();
            for (var p : Bukkit.getOnlinePlayers()) {
                Map<String, Object> pd = new HashMap<>();
                pd.put("name", p.getName());
                pd.put("uuid", p.getUniqueId().toString());
                pd.put("world", p.getWorld().getName());
                pd.put("health", Math.round(p.getHealth()));
                pd.put("food", p.getFoodLevel());
                pd.put("gamemode", p.getGameMode().name());
                pd.put("ping", p.getPing());
                pd.put("rank", allRanks.getOrDefault(p.getName().toLowerCase(), "member"));
                pd.put("waypoints", waypointCounts.getOrDefault(p.getName(), 0));
                players.add(pd);
            }
            int onlineCount = players.size();
            stats.put("onlinePlayers", onlineCount);
            stats.put("maxPlayers", Bukkit.getMaxPlayers());
            stats.put("startTime", plugin.getStartTime());
            stats.put("uptimeMs", System.currentTimeMillis() - plugin.getStartTime());
            stats.put("serverVersion", Bukkit.getVersion());
            stats.put("bukkitVersion", Bukkit.getBukkitVersion());
            stats.put("motd", Bukkit.getMotd());
            try {
                double[] tps = Bukkit.getTPS();
                stats.put("tps", Math.round(Math.min(tps[0], 20.0) * 100.0) / 100.0);
            } catch (Throwable ignored) {
                stats.put("tps", 20.0);
            }
            // Per-world entity/chunk counts.
            List<Map<String, Object>> worldData = new ArrayList<>();
            int totalEntities = 0, totalChunks = 0;
            for (var w : Bukkit.getWorlds()) {
                Map<String, Object> wd = new HashMap<>();
                int ents = w.getEntities().size();
                int chunks = w.getLoadedChunks().length;
                wd.put("name", w.getName());
                wd.put("players", w.getPlayers().size());
                wd.put("entities", ents);
                wd.put("chunks", chunks);
                wd.put("time", w.getTime());
                worldData.add(wd);
                totalEntities += ents;
                totalChunks += chunks;
            }
            stats.put("totalEntities", totalEntities);
            stats.put("loadedChunks", totalChunks);

            Map<String, Object> bans = plugin.getModerationManager().getBansSnapshot();
            Map<String, Object> whitelist = plugin.getModerationManager().getWhitelistSnapshot();

            // Time-series history point for charts (online count + total waypoints over time).
            Map<String, Object> historyPoint = new HashMap<>();
            historyPoint.put("t", System.currentTimeMillis());
            historyPoint.put("online", onlineCount);
            historyPoint.put("waypoints", (int) stats.get("totalWaypoints"));

            // Category distribution for the pie chart.
            Map<String, Integer> categoryCounts = new HashMap<>();
            for (Map<String, Object> wp : waypointData) {
                String cat = String.valueOf(wp.getOrDefault("category", "OTHER"));
                categoryCounts.merge(cat, 1, Integer::sum);
            }

            // Push everything back off-thread to avoid blocking the server on network I/O.
            final boolean pushHistory = (cycle++ % 10 == 0); // history point ~every 10 cycles
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                serverRef.child("waypoints").setValueAsync(waypointData);
                serverRef.child("knownPlayers").setValueAsync(knownPlayerData);
                serverRef.child("stats").setValueAsync(stats);
                serverRef.child("players").setValueAsync(players);
                serverRef.child("bans").setValueAsync(bans);
                serverRef.child("whitelist").setValueAsync(whitelist);
                serverRef.child("categoryStats").setValueAsync(categoryCounts);
                serverRef.child("worlds").setValueAsync(worldData);
                if (pushHistory) serverRef.child("history").push().setValueAsync(historyPoint);
            });
        });
    }
}
