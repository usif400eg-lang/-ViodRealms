package com.viodrealms.tpu.gui;

import com.viodrealms.tpu.ViodRealmsTPU;
import com.viodrealms.tpu.models.Waypoint;
import com.viodrealms.tpu.utils.GUIUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AdminWaypointMenu {
    public static void open(Player player, UUID targetUuid, ViodRealmsTPU plugin, int page) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Waypoint> waypoints = plugin.getWaypointRepository().getWaypointsForPlayer(targetUuid);
            plugin.getServer().getScheduler().runTask(plugin, () -> render(player, targetUuid, waypoints, plugin));
        });
    }

    private static void render(Player player, UUID targetUuid, List<Waypoint> waypoints, ViodRealmsTPU plugin) {
        Inventory inventory = Bukkit.createInventory(null, 54, ChatColor.DARK_GRAY + "Player Waypoints");
        GUIUtils.fillBackground(inventory);
        int slot = 0;
        for (Waypoint waypoint : waypoints) {
            if (slot >= 45) {
                break;
            }
            inventory.setItem(slot, GUIUtils.createItem(Material.ENDER_PEARL, "&a" + waypoint.getWaypointName(), List.of(
                    "&7World: " + waypoint.getWorldName(),
                    "&7X: " + Math.round(waypoint.getX()),
                    "&7Y: " + Math.round(waypoint.getY()),
                    "&7Z: " + Math.round(waypoint.getZ()),
                    "&7Owner: " + waypoint.getPlayerName()), "admin_waypoint_select:" + waypoint.getId() + ":" + targetUuid));
            slot++;
        }
        for (int i = slot; i < 45; i++) {
            inventory.setItem(i, GUIUtils.createItem(Material.GRAY_STAINED_GLASS_PANE, "&7", new ArrayList<>(), "empty_slot"));
        }
        inventory.setItem(49, GUIUtils.createItem(Material.BARRIER, "&cBack", List.of("&7Return to player search"), "admin_back"));
        player.openInventory(inventory);
        GUIUtils.startAnimation(player, inventory);
    }
}
