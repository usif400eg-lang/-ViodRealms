package com.voxelpanel.gui;

import com.voxelpanel.VoxelPanel;
import com.voxelpanel.models.Waypoint;
import com.voxelpanel.utils.GUIUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import java.util.Arrays;
import java.util.UUID;

public class AdminWaypointActionMenu {
    public static void open(Player player, UUID targetUuid, int waypointId, VoxelPanel plugin) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Waypoint waypoint = plugin.getWaypointRepository().getWaypointById(waypointId).orElse(null);
            plugin.getServer().getScheduler().runTask(plugin, () -> render(player, targetUuid, waypoint, plugin));
        });
    }

    private static void render(Player player, UUID targetUuid, Waypoint waypoint, VoxelPanel plugin) {
        if (waypoint == null) {
            player.closeInventory();
            return;
        }
        Inventory inventory = Bukkit.createInventory(null, 27, ChatColor.DARK_GRAY + waypoint.getWaypointName());
        GUIUtils.fillBackground(inventory);
        inventory.setItem(11, GUIUtils.createItem(Material.ENDER_PEARL, "&aTeleport", Arrays.asList("&7Teleport to this waypoint"), "admin_waypoint_teleport:" + waypoint.getId() + ":" + targetUuid));
        inventory.setItem(13, GUIUtils.createItem(Material.ANVIL, "&aRename", Arrays.asList("&7Rename this waypoint"), "admin_waypoint_rename:" + waypoint.getId()));
        inventory.setItem(15, GUIUtils.createItem(Material.LAVA_BUCKET, "&cDelete", Arrays.asList("&7Delete this waypoint"), "admin_waypoint_delete:" + waypoint.getId()));
        inventory.setItem(22, GUIUtils.createItem(Material.BARRIER, "&cBack", Arrays.asList("&7Return to player waypoints"), "admin_waypoint_back:" + targetUuid));
        player.openInventory(inventory);
        GUIUtils.startAnimation(player, inventory);
    }
}
