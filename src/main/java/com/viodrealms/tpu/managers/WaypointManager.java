package com.viodrealms.tpu.managers;

import com.viodrealms.tpu.ViodRealmsTPU;
import com.viodrealms.tpu.database.WaypointRepository;
import com.viodrealms.tpu.models.Waypoint;
import com.viodrealms.tpu.utils.InputValidator;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class WaypointManager {
    public enum PendingActionType {
        CREATE,
        RENAME,
        SEARCH,
        ADMIN_SEARCH,
        ADMIN_RENAME,
        SHARE
    }

    public record PendingAction(PendingActionType type, Integer waypointId, String playerName) {}

    private final ViodRealmsTPU plugin;
    private final WaypointRepository waypointRepository;
    private final MessageManager messageManager;
    private final SoundManager soundManager;
    private final Map<UUID, PendingAction> pendingActions = new HashMap<>();

    public WaypointManager(ViodRealmsTPU plugin, WaypointRepository waypointRepository, MessageManager messageManager, SoundManager soundManager) {
        this.plugin = plugin;
        this.waypointRepository = waypointRepository;
        this.messageManager = messageManager;
        this.soundManager = soundManager;
    }

    public int getMaxWaypoints() {
        return plugin.getConfig().getInt("waypoints.max-per-player", 10);
    }

    public int getMaxWaypointNameLength() {
        return plugin.getConfig().getInt("waypoints.max-name-length", InputValidator.MAX_NAME_LENGTH);
    }

    public int getMaxPerCategory() {
        return plugin.getConfig().getInt("waypoints.max-per-category", 0);
    }

    public boolean isOwner(UUID playerUuid, Waypoint waypoint) {
        return waypoint != null && waypoint.getPlayerUuid().equals(playerUuid);
    }

    public void setPendingAction(UUID playerId, PendingAction action) {
        pendingActions.put(playerId, action);
    }

    public PendingAction getPendingAction(UUID playerId) {
        return pendingActions.get(playerId);
    }

    public void clearPendingAction(UUID playerId) {
        pendingActions.remove(playerId);
    }

    public void createWaypoint(Player player, String rawName) {
        String name = InputValidator.sanitizeName(rawName);
        if (!InputValidator.isValidWaypointName(name, getMaxWaypointNameLength())) {
            messageManager.sendError(player, "invalid-name");
            soundManager.play(player, "error");
            return;
        }

        if (waypointRepository.playerHasWaypoint(player.getUniqueId(), name)) {
            messageManager.sendError(player, "duplicate-name");
            soundManager.play(player, "error");
            return;
        }

        if (waypointRepository.countWaypoints(player.getUniqueId()) >= getMaxWaypoints()
                && !player.hasPermission("viodrealms.tpu.bypass.limit")) {
            messageManager.sendError(player, "max-waypoints-reached");
            soundManager.play(player, "error");
            return;
        }

        Waypoint waypoint = new Waypoint(
                0,
                player.getUniqueId(),
                player.getName(),
                name,
                player.getWorld().getName(),
                player.getLocation().getX(),
                player.getLocation().getY(),
                player.getLocation().getZ(),
                player.getLocation().getYaw(),
                player.getLocation().getPitch(),
                Instant.now(),
                "OTHER",
                plugin.getConfig().getString("waypoints.default-icon", "ENDER_PEARL"),
                false,
                false,
                null
        );

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean success = waypointRepository.insertWaypoint(waypoint);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (success) {
                    messageManager.send(player, "waypoint-created");
                    soundManager.play(player, "success");
                } else {
                    messageManager.sendError(player, "waypoint-creation-failed");
                    soundManager.play(player, "error");
                }
                clearPendingAction(player.getUniqueId());
            });
        });
    }

    /** Creates from the native GUI and returns to the waypoint list only after a successful save. */
    public void createWaypointFromGui(Player player, String rawName) {
        String name = InputValidator.sanitizeName(rawName);
        if (!InputValidator.isValidWaypointName(name, getMaxWaypointNameLength())) {
            messageManager.sendError(player, "invalid-name");
            soundManager.play(player, "error");
            com.viodrealms.tpu.gui.CreateWaypointMenu.open(player, plugin, rawName);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String failure = null;
            if (waypointRepository.countWaypoints(player.getUniqueId()) >= getMaxWaypoints()) {
                failure = "max-waypoints-reached";
            } else if (waypointRepository.playerHasWaypoint(player.getUniqueId(), name)) {
                failure = "duplicate-name";
            }
            if (failure != null) {
                String finalFailure = failure;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    messageManager.sendError(player, finalFailure);
                    soundManager.play(player, "error");
                    com.viodrealms.tpu.gui.CreateWaypointMenu.open(player, plugin, rawName);
                });
                return;
            }
            Waypoint waypoint = new Waypoint(0, player.getUniqueId(), player.getName(), name,
                    player.getWorld().getName(), player.getLocation().getX(), player.getLocation().getY(),
                    player.getLocation().getZ(), player.getLocation().getYaw(), player.getLocation().getPitch(), Instant.now(),
                    "OTHER", plugin.getConfig().getString("waypoints.default-icon", "ENDER_PEARL"), false, false, null);
            boolean success = waypointRepository.insertWaypoint(waypoint);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (success) {
                    messageManager.send(player, "waypoint-created");
                    soundManager.play(player, "success");
                    com.viodrealms.tpu.gui.CreateWaypointMenu.close(player);
                    player.closeInventory();
                    com.viodrealms.tpu.gui.WaypointMenu.open(player, plugin);
                } else {
                    messageManager.sendError(player, "waypoint-creation-failed");
                    soundManager.play(player, "error");
                    com.viodrealms.tpu.gui.CreateWaypointMenu.open(player, plugin, rawName);
                }
            });
        });
    }

    public void renameWaypoint(Player player, int waypointId, String rawName) {
        String name = InputValidator.sanitizeName(rawName);
        if (!InputValidator.isValidWaypointName(name)) {
            messageManager.sendError(player, "invalid-name");
            soundManager.play(player, "error");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Waypoint existing = waypointRepository.getWaypointById(waypointId).orElse(null);
            if (existing == null || !isOwner(player.getUniqueId(), existing)) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    messageManager.sendError(player, "invalid-input");
                    soundManager.play(player, "error");
                    clearPendingAction(player.getUniqueId());
                });
                return;
            }
            if (waypointRepository.playerHasWaypoint(player.getUniqueId(), name) && !existing.getWaypointName().equalsIgnoreCase(name)) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    messageManager.sendError(player, "duplicate-name");
                    soundManager.play(player, "error");
                });
                return;
            }
            boolean success = waypointRepository.renameWaypoint(waypointId, name);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (success) {
                    messageManager.send(player, "waypoint-renamed");
                    soundManager.play(player, "success");
                } else {
                    messageManager.sendError(player, "database-error");
                    soundManager.play(player, "error");
                }
                clearPendingAction(player.getUniqueId());
            });
        });
    }

    public void deleteWaypoint(Player player, int waypointId, boolean adminDelete) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Waypoint existing = waypointRepository.getWaypointById(waypointId).orElse(null);
            if (existing == null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    messageManager.sendError(player, "database-error");
                    soundManager.play(player, "error");
                });
                return;
            }
            if (!adminDelete && !isOwner(player.getUniqueId(), existing)) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    messageManager.sendError(player, "invalid-input");
                    soundManager.play(player, "error");
                });
                return;
            }
            boolean success = waypointRepository.deleteWaypoint(waypointId);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (success) {
                    messageManager.send(player, "waypoint-deleted");
                    soundManager.play(player, "delete");
                } else {
                    messageManager.sendError(player, "database-error");
                    soundManager.play(player, "error");
                }
                clearPendingAction(player.getUniqueId());
            });
        });
    }

    public void deleteWaypointByName(Player player, String rawName) {
        String name = InputValidator.sanitizeName(rawName);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Waypoint waypoint = waypointRepository.getWaypointForPlayerByName(player.getUniqueId(), name).orElse(null);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (waypoint == null) {
                    messageManager.sendError(player, "waypoint-not-found");
                    soundManager.play(player, "error");
                    return;
                }
                deleteWaypoint(player, waypoint.getId(), false);
            });
        });
    }

    public void teleportWaypointByName(Player player, String rawName) {
        String name = InputValidator.sanitizeName(rawName);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Waypoint waypoint = waypointRepository.getWaypointForPlayerByName(player.getUniqueId(), name).orElse(null);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (waypoint == null) {
                    messageManager.sendError(player, "waypoint-not-found");
                    soundManager.play(player, "error");
                    return;
                }
                plugin.getTeleportService().teleport(player, waypoint);
            });
        });
    }

    public void searchWaypoints(Player player, String query) {
        String term = InputValidator.sanitizeName(query);
        if (term.isBlank()) {
            messageManager.sendError(player, "invalid-input");
            soundManager.play(player, "error");
            clearPendingAction(player.getUniqueId());
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Waypoint> results = waypointRepository.searchWaypoints(player.getUniqueId(), term);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (results.isEmpty()) {
                    messageManager.send(player, "no-search-results");
                    soundManager.play(player, "error");
                    clearPendingAction(player.getUniqueId());
                    return;
                }
                clearPendingAction(player.getUniqueId());
                com.viodrealms.tpu.gui.SearchMenu.open(player, results, plugin);
            });
        });
    }

    public List<Waypoint> getWaypoints(UUID playerUuid) {
        return waypointRepository.getWaypointsForPlayer(playerUuid);
    }

    public List<Waypoint> getWaypointsByCategory(UUID playerUuid, String category) {
        return waypointRepository.getWaypointsForPlayerByCategory(playerUuid, category);
    }

    public List<Waypoint> getPublicWaypoints() {
        return waypointRepository.getPublicWaypoints();
    }

    public List<Waypoint> getSharedWaypoints(UUID playerUuid) {
        return waypointRepository.getSharedWaypointsForPlayer(playerUuid);
    }

    public int getWaypointCount(UUID playerUuid) {
        return waypointRepository.countWaypoints(playerUuid);
    }

    public void adminSearch(Player player, String playerName) {
        String term = InputValidator.sanitizeName(playerName);
        if (term.isBlank()) {
            messageManager.sendError(player, "invalid-input");
            soundManager.play(player, "error");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<WaypointRepository.PlayerRecord> records = waypointRepository.getKnownPlayers();
            List<WaypointRepository.PlayerRecord> matches = records.stream()
                    .filter(record -> record.getPlayerName().equalsIgnoreCase(term) || record.getPlayerName().toLowerCase().contains(term.toLowerCase()))
                    .toList();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (matches.isEmpty()) {
                    messageManager.sendError(player, "player-not-found");
                    soundManager.play(player, "error");
                    return;
                }
                com.viodrealms.tpu.gui.AdminPlayerSearchMenu.open(player, matches, plugin);
            });
        });
    }

    public void adminRename(Player player, int waypointId, String rawName) {
        String name = InputValidator.sanitizeName(rawName);
        if (!InputValidator.isValidWaypointName(name)) {
            messageManager.sendError(player, "invalid-name");
            soundManager.play(player, "error");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Waypoint waypoint = waypointRepository.getWaypointById(waypointId).orElse(null);
            if (waypoint == null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    messageManager.sendError(player, "database-error");
                    soundManager.play(player, "error");
                    clearPendingAction(player.getUniqueId());
                });
                return;
            }
            if (waypointRepository.playerHasWaypoint(waypoint.getPlayerUuid(), name) && !waypoint.getWaypointName().equalsIgnoreCase(name)) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    messageManager.sendError(player, "duplicate-name");
                    soundManager.play(player, "error");
                });
                return;
            }
            boolean success = waypointRepository.renameWaypoint(waypointId, name);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (success) {
                    messageManager.send(player, "waypoint-renamed");
                    soundManager.play(player, "success");
                } else {
                    messageManager.sendError(player, "database-error");
                    soundManager.play(player, "error");
                }
                clearPendingAction(player.getUniqueId());
            });
        });
    }
}
