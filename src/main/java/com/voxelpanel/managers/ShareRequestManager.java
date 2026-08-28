package com.voxelpanel.managers;

import com.voxelpanel.VoxelPanel;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles pending waypoint share requests that require the target's acceptance.
 * A request maps the target player to the waypoint being shared and its owner.
 */
public class ShareRequestManager {
    private final VoxelPanel plugin;

    public record ShareRequest(int waypointId, UUID ownerUuid, String ownerName, String waypointName) {}

    /** Key: target player UUID, Value: the pending share request. */
    private final Map<UUID, ShareRequest> pendingShares = new HashMap<>();
    private final Map<UUID, BukkitTask> expiryTasks = new HashMap<>();

    public ShareRequestManager(VoxelPanel plugin) {
        this.plugin = plugin;
    }

    public void createRequest(UUID targetUuid, ShareRequest request) {
        cancelExisting(targetUuid);
        pendingShares.put(targetUuid, request);

        long expirySeconds = plugin.getConfig().getLong("share.expiry-seconds", 60);
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            pendingShares.remove(targetUuid);
            expiryTasks.remove(targetUuid);
            var target = plugin.getServer().getPlayer(targetUuid);
            if (target != null && target.isOnline()) {
                plugin.getMessageManager().send(target, "share-expired");
            }
        }, expirySeconds * 20L);
        expiryTasks.put(targetUuid, task);
    }

    public ShareRequest getRequest(UUID targetUuid) {
        return pendingShares.get(targetUuid);
    }

    public boolean hasRequest(UUID targetUuid) {
        return pendingShares.containsKey(targetUuid);
    }

    public void removeRequest(UUID targetUuid) {
        cancelExisting(targetUuid);
    }

    private void cancelExisting(UUID targetUuid) {
        pendingShares.remove(targetUuid);
        BukkitTask task = expiryTasks.remove(targetUuid);
        if (task != null) {
            task.cancel();
        }
    }
}
