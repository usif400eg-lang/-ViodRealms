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

public class PublicWaypointMenu {
    private static final int ITEMS_PER_PAGE = 45;

    public static void open(Player player, VoxelPanel plugin) {
        open(player, plugin, 0);
    }

    public static void open(Player player, VoxelPanel plugin, int page) {
        GUIUtils.cancelAnimation(player.getUniqueId());
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Waypoint> waypoints = plugin.getWaypointRepository().getPublicWaypoints();
            plugin.getServer().getScheduler().runTask(plugin, () -> render(player, waypoints, plugin, page));
        });
    }

    private static void render(Player player, List<Waypoint> waypoints, VoxelPanel plugin, int page) {
        Inventory inventory = Bukkit.createInventory(null, 54, ChatColor.DARK_GRAY + "Public Waypoints");
        GUIUtils.fillBackground(inventory);

        int totalPages = Math.max(1, (int) Math.ceil(waypoints.size() / (double) ITEMS_PER_PAGE));
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;
        final int currentPage = page;

        int start = currentPage * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, waypoints.size());
        int slot = 0;
        for (int i = start; i < end; i++) {
            Waypoint waypoint = waypoints.get(i);
            Material iconMat;
            try {
                iconMat = Material.valueOf(waypoint.getIcon());
            } catch (IllegalArgumentException e) {
                iconMat = Material.ENDER_PEARL;
            }
            inventory.setItem(slot, GUIUtils.createItem(iconMat, "&f" + waypoint.getWaypointName(), List.of(
                    "&7Owner: " + waypoint.getPlayerName(),
                    "&7World: " + waypoint.getWorldName(),
                    "&7X: " + Math.round(waypoint.getX()) + " Y: " + Math.round(waypoint.getY()) + " Z: " + Math.round(waypoint.getZ())
            ), "public_select:" + waypoint.getId()));
            slot++;
        }
        for (int i = slot; i < 45; i++) {
            inventory.setItem(i, GUIUtils.createItem(Material.GRAY_STAINED_GLASS_PANE, "&7", new ArrayList<>(), "empty_slot"));
        }

        if (currentPage > 0) {
            inventory.setItem(48, GUIUtils.button(Material.ARROW, "&fPrevious Page",
                    List.of("&7Page " + currentPage + " / " + totalPages), "public_page:" + (currentPage - 1)));
        }
        inventory.setItem(49, GUIUtils.button(Material.BARRIER, "&cBack", List.of("&7Return to main menu"), "waypoint_back"));
        if (currentPage < totalPages - 1) {
            inventory.setItem(50, GUIUtils.button(Material.ARROW, "&fNext Page",
                    List.of("&7Page " + (currentPage + 2) + " / " + totalPages), "public_page:" + (currentPage + 1)));
        }

        player.openInventory(inventory);
        GUIUtils.startAnimation(player, inventory);
    }
}
