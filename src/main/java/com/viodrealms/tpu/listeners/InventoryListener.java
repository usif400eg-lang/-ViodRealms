package com.viodrealms.tpu.listeners;

import com.viodrealms.tpu.ViodRealmsTPU;
import com.viodrealms.tpu.gui.*;
import com.viodrealms.tpu.managers.MessageManager;
import com.viodrealms.tpu.managers.SoundManager;
import com.viodrealms.tpu.managers.WaypointManager;
import com.viodrealms.tpu.models.Waypoint;
import com.viodrealms.tpu.utils.GUIUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class InventoryListener implements Listener {
    private final ViodRealmsTPU plugin;
    private final WaypointManager waypointManager;
    private final MessageManager messageManager;
    private final SoundManager soundManager;
    private final com.viodrealms.tpu.services.TeleportService teleportService;

    public InventoryListener(ViodRealmsTPU plugin, WaypointManager waypointManager, MessageManager messageManager, SoundManager soundManager, com.viodrealms.tpu.services.TeleportService teleportService) {
        this.plugin = plugin;
        this.waypointManager = waypointManager;
        this.messageManager = messageManager;
        this.soundManager = soundManager;
        this.teleportService = teleportService;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (CreateWaypointMenu.isOpen(player)) {
            if (event.getRawSlot() > 2) {
                return;
            }
            event.setCancelled(true);
            if (event.getRawSlot() == 2) {
                handleAction(player, "create_confirm");
            } else if (event.getRawSlot() == 1) {
                handleAction(player, "create_cancel");
            }
            return;
        }
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) {
            return;
        }

        ItemStack item = event.getCurrentItem();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        NamespacedKey key = new NamespacedKey(plugin, "tpu_action");
        String action = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (action == null) {
            return;
        }

        event.setCancelled(true);
        handleAction(player, action);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !CreateWaypointMenu.isOpen(player)) {
            return;
        }
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= 0 && rawSlot <= 2) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!(event.getView().getPlayer() instanceof Player player) || !CreateWaypointMenu.isOpen(player)) {
            return;
        }
        event.setResult(GUIUtils.createItem(Material.LIME_CONCRETE, "&fConfirm", java.util.List.of(
                "&7Save this waypoint at your current location"), "create_confirm"));
    }

    private void handleAction(Player player, String action) {
        String[] parts = action.split(":");
        if (parts.length == 0) {
            return;
        }

        switch (parts[0]) {
            case "main_my_waypoints" -> {
                GUIUtils.cancelAnimation(player.getUniqueId());
                WaypointMenu.open(player, plugin);
            }
            case "main_compass_track" -> {
                messageManager.send(player, "compass-no-waypoint");
                player.closeInventory();
            }
            case "main_search_waypoints" -> {
                waypointManager.setPendingAction(player.getUniqueId(), new WaypointManager.PendingAction(WaypointManager.PendingActionType.SEARCH, null, null));
                messageManager.send(player, "search-started");
                player.closeInventory();
            }
            case "main_public_waypoints" -> {
                GUIUtils.cancelAnimation(player.getUniqueId());
                PublicWaypointMenu.open(player, plugin);
            }
            case "main_shared_waypoints" -> {
                GUIUtils.cancelAnimation(player.getUniqueId());
                SharedWaypointMenu.open(player, plugin);
            }
            case "main_close" -> player.closeInventory();
            case "waypoint_filter" -> {
                String category = parts.length > 1 ? parts[1] : "ALL";
                WaypointMenu.open(player, plugin, category);
            }
            case "waypoint_page" -> {
                String category = parts.length > 1 ? parts[1] : "ALL";
                int page = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
                WaypointMenu.open(player, plugin, category, page);
            }
            case "public_page" -> {
                int page = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                PublicWaypointMenu.open(player, plugin, page);
            }
            case "shared_page" -> {
                int page = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                SharedWaypointMenu.open(player, plugin, page);
            }
            case "waypoint_select" -> {
                int id = Integer.parseInt(parts[1]);
                WaypointActionMenu.open(player, id, plugin);
            }
            case "waypoint_action_teleport" -> {
                int id = Integer.parseInt(parts[1]);
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    Waypoint waypoint = plugin.getWaypointRepository().getWaypointById(id).orElse(null);
                    if (waypoint == null || !waypointManager.isOwner(player.getUniqueId(), waypoint)) {
                        Bukkit.getScheduler().runTask(plugin, () -> messageManager.sendError(player, "invalid-input"));
                        return;
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> teleportService.teleport(player, waypoint));
                });
            }
            case "waypoint_action_rename" -> {
                int id = Integer.parseInt(parts[1]);
                waypointManager.setPendingAction(player.getUniqueId(), new WaypointManager.PendingAction(WaypointManager.PendingActionType.RENAME, id, null));
                player.sendMessage(messageManager.get("prefix") + "Enter a new waypoint name.");
                player.closeInventory();
            }
            case "waypoint_action_delete" -> ConfirmDeleteMenu.open(player, Integer.parseInt(parts[1]), plugin, false);
            case "waypoint_action_category" -> {
                int id = Integer.parseInt(parts[1]);
                CategorySelectMenu.open(player, id, plugin);
            }
            case "waypoint_action_icon" -> {
                int id = Integer.parseInt(parts[1]);
                IconSelectMenu.open(player, id, plugin);
            }
            case "waypoint_action_public" -> {
                int id = Integer.parseInt(parts[1]);
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    Waypoint waypoint = plugin.getWaypointRepository().getWaypointById(id).orElse(null);
                    if (waypoint == null) return;
                    boolean newPublic = !waypoint.isPublic();
                    plugin.getWaypointRepository().updateWaypointPublic(id, newPublic);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        messageManager.send(player, newPublic ? "public-enabled" : "public-disabled");
                        WaypointActionMenu.open(player, id, plugin);
                    });
                });
            }
            case "waypoint_action_share" -> {
                int id = Integer.parseInt(parts[1]);
                ShareMenu.open(player, id, plugin);
            }
            case "waypoint_action_back" -> {
                GUIUtils.cancelAnimation(player.getUniqueId());
                MainMenu.open(player, waypointManager.getWaypointCount(player.getUniqueId()), waypointManager.getMaxWaypoints(), plugin);
            }
            case "waypoint_back" -> {
                GUIUtils.cancelAnimation(player.getUniqueId());
                MainMenu.open(player, waypointManager.getWaypointCount(player.getUniqueId()), waypointManager.getMaxWaypoints(), plugin);
            }
            case "confirm_delete" -> {
                int id = Integer.parseInt(parts[1]);
                waypointManager.deleteWaypoint(player, id, false);
            }
            case "cancel_delete" -> WaypointMenu.open(player, plugin);
            case "search_item" -> {
                waypointManager.setPendingAction(player.getUniqueId(), new WaypointManager.PendingAction(WaypointManager.PendingActionType.SEARCH, null, null));
                messageManager.send(player, "search-started");
                player.closeInventory();
            }
            case "search_back" -> {
                GUIUtils.cancelAnimation(player.getUniqueId());
                MainMenu.open(player, waypointManager.getWaypointCount(player.getUniqueId()), waypointManager.getMaxWaypoints(), plugin);
            }
            case "admin_main_search" -> {
                waypointManager.setPendingAction(player.getUniqueId(), new WaypointManager.PendingAction(WaypointManager.PendingActionType.ADMIN_SEARCH, null, null));
                player.sendMessage(messageManager.get("prefix") + "Enter a player name to search.");
                player.closeInventory();
            }
            case "admin_main_player_waypoints" -> player.sendMessage("Player waypoint listing is available after a player selection.");
            case "admin_main_search_waypoints" -> player.sendMessage("Use the player search first to find a player.");
            case "admin_main_statistics" -> StatisticsMenu.open(player, plugin);
            case "admin_main_close" -> player.closeInventory();
            case "admin_select_player" -> {
                UUID targetUuid = UUID.fromString(parts[1]);
                AdminWaypointMenu.open(player, targetUuid, plugin, 1);
            }
            case "admin_waypoint_select" -> {
                int id = Integer.parseInt(parts[1]);
                UUID targetUuid = UUID.fromString(parts[2]);
                AdminWaypointActionMenu.open(player, targetUuid, id, plugin);
            }
            case "admin_waypoint_teleport" -> {
                int id = Integer.parseInt(parts[1]);
                UUID targetUuid = UUID.fromString(parts[2]);
                if (!player.hasPermission("viodrealms.tpu.admin.teleport")) {
                    messageManager.sendError(player, "no-permission");
                    return;
                }
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    Waypoint waypoint = plugin.getWaypointRepository().getWaypointById(id).orElse(null);
                    if (waypoint == null || !waypoint.getPlayerUuid().equals(targetUuid)) {
                        Bukkit.getScheduler().runTask(plugin, () -> messageManager.sendError(player, "invalid-input"));
                        return;
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> teleportService.teleport(player, waypoint));
                });
            }
            case "admin_waypoint_rename" -> {
                int id = Integer.parseInt(parts[1]);
                waypointManager.setPendingAction(player.getUniqueId(), new WaypointManager.PendingAction(WaypointManager.PendingActionType.ADMIN_RENAME, id, null));
                player.sendMessage(messageManager.get("prefix") + "Enter a new name for this waypoint.");
                player.closeInventory();
            }
            case "admin_waypoint_delete" -> {
                int id = Integer.parseInt(parts[1]);
                ConfirmDeleteMenu.open(player, id, plugin, true);
            }
            case "admin_delete_confirm" -> {
                int id = Integer.parseInt(parts[1]);
                waypointManager.deleteWaypoint(player, id, true);
            }
            case "admin_delete_cancel" -> WaypointMenu.open(player, plugin);
            case "admin_back" -> AdminMenu.open(player, plugin);
            case "admin_waypoint_back" -> {
                UUID targetUuid = UUID.fromString(parts[1]);
                AdminWaypointMenu.open(player, targetUuid, plugin, 1);
            }
            case "category_set" -> {
                int id = Integer.parseInt(parts[1]);
                String category = parts[2];
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    boolean success = plugin.getWaypointRepository().updateWaypointCategory(id, category);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (success) {
                            messageManager.send(player, "category-selected", "category", category);
                            soundManager.play(player, "success");
                        } else {
                            messageManager.sendError(player, "database-error");
                        }
                        WaypointActionMenu.open(player, id, plugin);
                    });
                });
            }
            case "icon_set" -> {
                int id = Integer.parseInt(parts[1]);
                String icon = parts[2];
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    boolean success = plugin.getWaypointRepository().updateWaypointIcon(id, icon);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (success) {
                            messageManager.send(player, "icon-selected", "icon", icon);
                            soundManager.play(player, "success");
                        } else {
                            messageManager.sendError(player, "database-error");
                        }
                        WaypointActionMenu.open(player, id, plugin);
                    });
                });
            }
            case "share_with" -> {
                int id = Integer.parseInt(parts[1]);
                waypointManager.setPendingAction(player.getUniqueId(), new WaypointManager.PendingAction(WaypointManager.PendingActionType.SHARE, id, null));
                player.sendMessage(messageManager.get("prefix") + "Enter a player name to share with.");
                player.closeInventory();
            }
            case "share_view" -> {
                int id = Integer.parseInt(parts[1]);
                player.sendMessage("Shared with: " + plugin.getWaypointRepository().getSharedPlayers(id));
                ShareMenu.open(player, id, plugin);
            }
            case "share_remove_all" -> {
                int id = Integer.parseInt(parts[1]);
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    List<UUID> shared = plugin.getWaypointRepository().getSharedPlayers(id);
                    for (UUID uuid : shared) {
                        plugin.getWaypointRepository().unshareWaypoint(id, uuid);
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        messageManager.send(player, "public-disabled");
                        ShareMenu.open(player, id, plugin);
                    });
                });
            }
            case "public_select" -> {
                int id = Integer.parseInt(parts[1]);
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    Waypoint waypoint = plugin.getWaypointRepository().getWaypointById(id).orElse(null);
                    if (waypoint == null) return;
                    plugin.getTeleportService().teleport(player, waypoint);
                });
            }
            case "shared_select" -> {
                int id = Integer.parseInt(parts[1]);
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    Waypoint waypoint = plugin.getWaypointRepository().getWaypointById(id).orElse(null);
                    if (waypoint == null) return;
                    plugin.getTeleportService().teleport(player, waypoint);
                });
            }
            case "language_set" -> {
                String lang = parts[1];
                plugin.getLanguageManager().setPlayerLanguage(player, lang);
                messageManager.send(player, "waypoint-created");
                soundManager.play(player, "success");
                player.closeInventory();
            }
            default -> {
            }
        }
    }
}
