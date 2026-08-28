package com.voxelpanel.firebase;

import com.voxelpanel.VoxelPanel;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

/**
 * Sends a periodic heartbeat to Firebase so the dashboard knows the server is
 * alive in real time, and periodically re-validates the dashboard auth token.
 *
 * If the token is revoked/changed from the dashboard, the heartbeat stops
 * writing "online" and the plugin's Firebase features quietly stand down —
 * without ever throwing into the main server thread.
 */
public class HeartbeatService {
    private final VoxelPanel plugin;
    private final FirebaseManager firebaseManager;
    private BukkitTask task;
    private volatile boolean revoked = false;

    public HeartbeatService(VoxelPanel plugin, FirebaseManager firebaseManager) {
        this.plugin = plugin;
        this.firebaseManager = firebaseManager;
    }

    public void start() {
        if (!firebaseManager.isConnected()) return;
        long seconds = Math.max(2, plugin.getConfig().getLong("firebase.heartbeat-seconds", 5));
        long ticks = seconds * 20L;
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::beat, 40L, ticks);
        plugin.getLogger().info("[Firebase] Heartbeat started (every " + seconds + "s).");
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    public boolean isRevoked() {
        return revoked;
    }

    private void beat() {
        var identity = plugin.getServerIdentity();
        var ref = firebaseManager.getServerRef();
        if (identity == null || ref == null) return;

        // The live auth watch (ServerIdentity.startAuthWatch) is the source of
        // truth for authorization and reacts in real time to revocation. Here we
        // simply respect it: skip the heartbeat while unauthorized.
        if (!identity.isAuthorized()) {
            if (!revoked) {
                revoked = true;
                try { ref.child("heartbeat").child("online").setValueAsync(false); } catch (Exception ignored) {}
            }
            return;
        }
        revoked = false;

        // Write the heartbeat (async, never blocks the server).
        try {
            java.util.Map<String, Object> hb = new java.util.HashMap<>();
            hb.put("online", true);
            hb.put("ts", System.currentTimeMillis());
            hb.put("players", Bukkit.getOnlinePlayers().size());
            hb.put("instanceId", identity.getInstanceId());
            ref.child("heartbeat").updateChildrenAsync(hb);
        } catch (Exception e) {
            // Network hiccups are expected; the SDK auto-reconnects. Never crash.
            plugin.getLogger().fine("[Firebase] Heartbeat write skipped: " + e.getMessage());
        }
    }
}
