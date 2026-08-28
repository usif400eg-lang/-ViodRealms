package com.voxelpanel.gui;

import com.voxelpanel.VoxelPanel;
import com.voxelpanel.utils.GUIUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import java.util.List;

/** Displays real plugin statistics loaded asynchronously from the database. */
public class StatisticsMenu {
    public static void open(Player player, VoxelPanel plugin) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            int totalWaypoints = plugin.getWaypointRepository().getTotalWaypointCount();
            int publicWaypoints = plugin.getWaypointRepository().getPublicWaypointCount();
            int knownPlayers = plugin.getWaypointRepository().getKnownPlayers().size();
            int onlinePlayers = Bukkit.getOnlinePlayers().size();
            plugin.getServer().getScheduler().runTask(plugin,
                    () -> render(player, plugin, totalWaypoints, publicWaypoints, knownPlayers, onlinePlayers));
        });
    }

    private static void render(Player player, VoxelPanel plugin, int total, int publicCount, int players, int online) {
        Inventory inventory = Bukkit.createInventory(null, 27, ChatColor.DARK_GRAY + "Statistics");
        GUIUtils.fillBackground(inventory);
        inventory.setItem(10, GUIUtils.createItem(Material.ENDER_PEARL, "&fTotal Waypoints", List.of("&7" + total), "empty_slot"));
        inventory.setItem(12, GUIUtils.createItem(Material.GLOBE_BANNER_PATTERN, "&fPublic Waypoints", List.of("&7" + publicCount), "empty_slot"));
        inventory.setItem(14, GUIUtils.createItem(Material.PLAYER_HEAD, "&fKnown Players", List.of("&7" + players), "empty_slot"));
        inventory.setItem(16, GUIUtils.createItem(Material.LIME_DYE, "&fOnline Players", List.of("&7" + online), "empty_slot"));
        inventory.setItem(22, GUIUtils.button(Material.BARRIER, "&cBack", List.of("&7Return to admin menu"), "admin_back"));
        player.openInventory(inventory);
        GUIUtils.startAnimation(player, inventory);
    }
}
