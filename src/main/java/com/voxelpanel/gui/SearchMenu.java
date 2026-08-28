package com.voxelpanel.gui;

import com.voxelpanel.VoxelPanel;
import com.voxelpanel.models.Waypoint;
import com.voxelpanel.utils.GUIUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import java.util.ArrayList;
import java.util.List;

public class SearchMenu {
    public static void open(Player player, List<Waypoint> results, VoxelPanel plugin) {
        Inventory inventory = Bukkit.createInventory(null, 54, ChatColor.DARK_GRAY + "Search Results");
        GUIUtils.fillBackground(inventory);
        int slot = 0;
        for (Waypoint waypoint : results) {
            if (slot >= 45) {
                break;
            }
            inventory.setItem(slot, GUIUtils.createItem(Material.MAP, "&f" + waypoint.getWaypointName(), List.of(
                    "&7World: " + waypoint.getWorldName(),
                    "&7X: " + Math.round(waypoint.getX()),
                    "&7Y: " + Math.round(waypoint.getY()),
                    "&7Z: " + Math.round(waypoint.getZ())), "waypoint_select:" + waypoint.getId()));
            slot++;
        }
        inventory.setItem(49, GUIUtils.createItem(Material.BARRIER, "&cBack", List.of("&7Return to main menu"), "search_back"));
        player.openInventory(inventory);
        GUIUtils.startAnimation(player, inventory);
    }
}
