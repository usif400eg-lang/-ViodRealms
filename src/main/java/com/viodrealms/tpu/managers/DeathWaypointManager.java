package com.viodrealms.tpu.managers;

import com.viodrealms.tpu.ViodRealmsTPU;
import com.viodrealms.tpu.database.WaypointRepository;
import com.viodrealms.tpu.models.Waypoint;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DeathWaypointManager {
    private final ViodRealmsTPU plugin;
    private final WaypointRepository waypointRepository;
    private final Map<UUID, BukkitTask> expiryTasks = new HashMap<>();
    private BukkitTask cleanupTask;

    public DeathWaypointManager(ViodRealmsTPU plugin, WaypointRepository waypointRepository) {
        this.plugin = plugin;
        this.waypointRepository = waypointRepository;
    }

    public void startCleanupTask() {
        cleanupTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            var expired = waypointRepository.getExpiredDeathWaypoints();
            for (Waypoint wp : expired) {
                waypointRepository.deleteWaypoint(wp.getId());
                Player owner = Bukkit.getPlayer(wp.getPlayerUuid());
                if (owner != null && owner.isOnline()) {
                    owner.sendMessage(plugin.getMessageManager().get(owner, "death-waypoint-expired"));
                }
            }
        }, 20 * 60L, 20 * 60L);
    }

    public void stopCleanupTask() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
    }

    public void createDeathWaypoint(Player player) {
        if (!plugin.getConfig().getBoolean("death-waypoints.enabled", true)) {
            return;
        }

        long expirySeconds = plugin.getConfig().getLong("death-waypoints.expiry-seconds", 300);
        long expiryMillis = System.currentTimeMillis() + (expirySeconds * 1000);

        Location loc = player.getLocation();
        Waypoint waypoint = new Waypoint(
                0,
                player.getUniqueId(),
                player.getName(),
                "Death (" + formatTime(expirySeconds) + ")",
                loc.getWorld().getName(),
                loc.getX(),
                loc.getY(),
                loc.getZ(),
                loc.getYaw(),
                loc.getPitch(),
                Instant.now(),
                "OTHER",
                "RED_BED",
                false,
                true,
                expiryMillis
        );

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean success = waypointRepository.insertWaypoint(waypoint);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (success) {
                    player.sendMessage(plugin.getMessageManager().get(player, "death-waypoint-created"));
                    startExpiryTimer(player.getUniqueId(), expirySeconds);
                    // Point the player's compass toward their death location if enabled.
                    if (plugin.getConfig().getBoolean("death-waypoints.track-compass", true)) {
                        plugin.getCompassTrackerManager().trackWaypoint(player, loc.getWorld(),
                                loc.getX(), loc.getY(), loc.getZ(), "Death");
                    }
                }
            });
        });
    }

    public void checkDeathWaypointVisit(Player player, int waypointId) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Waypoint waypoint = waypointRepository.getWaypointById(waypointId).orElse(null);
            if (waypoint == null || !waypoint.getPlayerUuid().equals(player.getUniqueId())) {
                return;
            }
            if (waypoint.isDeathWaypoint() && plugin.getConfig().getBoolean("death-waypoints.auto-delete-on-visit", true)) {
                waypointRepository.deleteWaypoint(waypointId);
                player.sendMessage(plugin.getMessageManager().get(player, "death-waypoint-visited"));
                stopExpiryTimer(player.getUniqueId());
            }
        });
    }

    private void startExpiryTimer(UUID playerUuid, long seconds) {
        stopExpiryTimer(playerUuid);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                var waypoints = waypointRepository.getWaypointsForPlayer(playerUuid);
                for (Waypoint wp : waypoints) {
                    if (wp.isDeathWaypoint() && wp.getDeathWaypointExpiry() != null && wp.getDeathWaypointExpiry() < System.currentTimeMillis()) {
                        waypointRepository.deleteWaypoint(wp.getId());
                        Player owner = Bukkit.getPlayer(playerUuid);
                        if (owner != null && owner.isOnline()) {
                            owner.sendMessage(plugin.getMessageManager().get(owner, "death-waypoint-expired"));
                        }
                    }
                }
            });
            expiryTasks.remove(playerUuid);
        }, seconds * 20L);
        expiryTasks.put(playerUuid, task);
    }

    private void stopExpiryTimer(UUID playerUuid) {
        BukkitTask task = expiryTasks.remove(playerUuid);
        if (task != null) {
            task.cancel();
        }
    }

    private String formatTime(long seconds) {
        long mins = seconds / 60;
        long secs = seconds % 60;
        return mins + ":" + (secs < 10 ? "0" : "") + secs;
    }
}
