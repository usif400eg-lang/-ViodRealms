package com.viodrealms.tpu.managers;

import com.viodrealms.tpu.ViodRealmsTPU;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles player-to-player teleport requests (TPA).
 * A request maps the target player to the requester, and expires automatically.
 */
public class TeleportRequestManager {
    private final ViodRealmsTPU plugin;

    /** Key: target player UUID, Value: requester UUID. */
    private final Map<UUID, UUID> pendingRequests = new HashMap<>();
    private final Map<UUID, BukkitTask> expiryTasks = new HashMap<>();

    public TeleportRequestManager(ViodRealmsTPU plugin) {
        this.plugin = plugin;
    }

    /** Records a request from requester -> target and schedules an expiry. */
    public void createRequest(Player requester, Player target) {
        UUID targetUuid = target.getUniqueId();
        cancelExisting(targetUuid);
        pendingRequests.put(targetUuid, requester.getUniqueId());

        long expirySeconds = plugin.getConfig().getLong("tpa.expiry-seconds", 60);
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            pendingRequests.remove(targetUuid);
            expiryTasks.remove(targetUuid);
            Player r = plugin.getServer().getPlayer(requester.getUniqueId());
            Player t = plugin.getServer().getPlayer(targetUuid);
            if (r != null && r.isOnline()) {
                plugin.getMessageManager().send(r, "tpa-expired");
            }
            if (t != null && t.isOnline()) {
                plugin.getMessageManager().send(t, "tpa-expired");
            }
        }, expirySeconds * 20L);
        expiryTasks.put(targetUuid, task);
    }

    /** Returns the requester UUID for a target, or null if none pending. */
    public UUID getRequester(UUID targetUuid) {
        return pendingRequests.get(targetUuid);
    }

    public boolean hasRequest(UUID targetUuid) {
        return pendingRequests.containsKey(targetUuid);
    }

    public void removeRequest(UUID targetUuid) {
        cancelExisting(targetUuid);
    }

    private void cancelExisting(UUID targetUuid) {
        pendingRequests.remove(targetUuid);
        BukkitTask task = expiryTasks.remove(targetUuid);
        if (task != null) {
            task.cancel();
        }
    }
}
