package com.viodrealms.tpu.listeners;

import com.viodrealms.tpu.ViodRealmsTPU;
import com.viodrealms.tpu.managers.DeathWaypointManager;
import com.viodrealms.tpu.managers.MessageManager;
import com.viodrealms.tpu.managers.SoundManager;
import com.viodrealms.tpu.managers.WaypointManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerListener implements Listener {
    private final ViodRealmsTPU plugin;
    private final WaypointManager waypointManager;
    private final MessageManager messageManager;
    private final SoundManager soundManager;
    private final DeathWaypointManager deathWaypointManager;
    private final Map<UUID, Long> deathWaypointCheckCooldown = new HashMap<>();

    public PlayerListener(ViodRealmsTPU plugin, WaypointManager waypointManager, MessageManager messageManager, SoundManager soundManager, DeathWaypointManager deathWaypointManager) {
        this.plugin = plugin;
        this.waypointManager = waypointManager;
        this.messageManager = messageManager;
        this.soundManager = soundManager;
        this.deathWaypointManager = deathWaypointManager;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        // Remember the death location so /back can return the player there.
        plugin.getTeleportService().saveLastLocation(player);
        deathWaypointManager.createDeathWaypoint(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getCompassTrackerManager().resetPlayerCompass(player);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getConfig().getBoolean("death-waypoints.auto-delete-on-visit", true)) {
            return;
        }

        long now = System.currentTimeMillis();
        UUID uuid = player.getUniqueId();
        Long lastCheck = deathWaypointCheckCooldown.get(uuid);
        if (lastCheck != null && now - lastCheck < 1000) {
            return;
        }
        deathWaypointCheckCooldown.put(uuid, now);

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            var waypoints = plugin.getWaypointRepository().getWaypointsForPlayer(player.getUniqueId());
            for (var wp : waypoints) {
                if (wp.isDeathWaypoint() && wp.getWorldName().equals(player.getWorld().getName())) {
                    double dist = player.getLocation().distanceSquared(new org.bukkit.Location(player.getWorld(), wp.getX(), wp.getY(), wp.getZ()));
                    if (dist < 4.0) {
                        plugin.getDeathWaypointManager().checkDeathWaypointVisit(player, wp.getId());
                    }
                }
            }
        });
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        WaypointManager.PendingAction action = waypointManager.getPendingAction(player.getUniqueId());
        if (action == null) {
            return;
        }

        event.setCancelled(true);
        String message = event.getMessage();
        if (action.type() == WaypointManager.PendingActionType.CREATE) {
            waypointManager.createWaypoint(player, message);
        } else if (action.type() == WaypointManager.PendingActionType.RENAME) {
            waypointManager.renameWaypoint(player, action.waypointId(), message);
        } else if (action.type() == WaypointManager.PendingActionType.SEARCH) {
            waypointManager.searchWaypoints(player, message);
        } else if (action.type() == WaypointManager.PendingActionType.ADMIN_SEARCH) {
            waypointManager.adminSearch(player, message);
        } else if (action.type() == WaypointManager.PendingActionType.ADMIN_RENAME) {
            waypointManager.adminRename(player, action.waypointId(), message);
        } else if (action.type() == WaypointManager.PendingActionType.SHARE) {
            int waypointId = action.waypointId();
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                var target = Bukkit.getPlayer(message);
                var wp = plugin.getWaypointRepository().getWaypointById(waypointId).orElse(null);
                if (target == null || wp == null) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        messageManager.sendError(player, "player-not-found");
                        soundManager.play(player, "error");
                    });
                    return;
                }
                String wpName = wp.getWaypointName();
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    plugin.getShareRequestManager().createRequest(target.getUniqueId(),
                            new com.viodrealms.tpu.managers.ShareRequestManager.ShareRequest(waypointId, player.getUniqueId(), player.getName(), wpName));
                    messageManager.send(player, "share-sent", "player", target.getName());
                    messageManager.send(target, "share-received", "player", player.getName());
                    soundManager.play(player, "success");
                });
            });
        }
    }
}
