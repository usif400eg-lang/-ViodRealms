package com.viodrealms.tpu.gui;

import com.viodrealms.tpu.ViodRealmsTPU;
import com.viodrealms.tpu.models.Waypoint;
import com.viodrealms.tpu.utils.GUIUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.List;

public class WaypointMenu {
    private static final String[] CATEGORIES = {"ALL", "MINE", "BASE", "FARM", "OTHER"};
    private static final Material[] CATEGORY_MATERIALS = {
        Material.EMERALD,
        Material.DIAMOND_PICKAXE,
        Material.BEACON,
        Material.WHEAT,
        Material.BARRIER
    };
    private static final int ITEMS_PER_PAGE = 36;

    public static void open(Player player, ViodRealmsTPU plugin) {
        open(player, plugin, "ALL", 0);
    }

    public static void open(Player player, ViodRealmsTPU plugin, String category) {
        open(player, plugin, category, 0);
    }

    public static void open(Player player, ViodRealmsTPU plugin, String category, int page) {
        GUIUtils.cancelAnimation(player.getUniqueId());
        player.closeInventory();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Waypoint> waypoints;
            if ("ALL".equalsIgnoreCase(category)) {
                waypoints = plugin.getWaypointRepository().getWaypointsForPlayer(player.getUniqueId());
            } else {
                waypoints = plugin.getWaypointRepository().getWaypointsForPlayerByCategory(player.getUniqueId(), category);
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> render(player, waypoints, plugin, category, page));
        });
    }

    private static void render(Player player, List<Waypoint> waypoints, ViodRealmsTPU plugin, String category, int page) {
        Inventory inventory = Bukkit.createInventory(null, 54, ChatColor.DARK_GRAY + "My Waypoints");
        GUIUtils.fillBackground(inventory);

        int totalPages = Math.max(1, (int) Math.ceil(waypoints.size() / (double) ITEMS_PER_PAGE));
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;
        final int currentPage = page;

        // Category filter buttons on the bottom row.
        for (int i = 0; i < CATEGORIES.length && i < 5; i++) {
            String cat = CATEGORIES[i];
            Material mat = CATEGORY_MATERIALS[i];
            boolean selected = cat.equalsIgnoreCase(category);
            List<String> lore = new ArrayList<>();
            lore.add("§7Click to filter by: " + cat);
            if (selected) {
                lore.add("§aSelected");
            }
            ItemStack item = GUIUtils.createItem(mat, selected ? "&a" + cat : "&f" + cat, lore, "waypoint_filter:" + cat);
            inventory.setItem(45 + i, item);
        }

        int start = currentPage * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, waypoints.size());
        int slot = 0;
        for (int i = start; i < end; i++) {
            Waypoint waypoint = waypoints.get(i);
            List<String> lore = new ArrayList<>();
            lore.add("§7World: " + waypoint.getWorldName());
            lore.add("§7X: " + Math.round(waypoint.getX()) + " Y: " + Math.round(waypoint.getY()) + " Z: " + Math.round(waypoint.getZ()));
            lore.add("§7Category: " + waypoint.getCategory());
            lore.add("§7Icon: " + waypoint.getIcon());
            if (waypoint.isPublic()) {
                lore.add("§aPublic");
            }
            lore.add("");
            lore.add("§eClick to manage");

            Material iconMat;
            try {
                iconMat = Material.valueOf(waypoint.getIcon());
            } catch (IllegalArgumentException e) {
                iconMat = Material.ENDER_PEARL;
            }
            inventory.setItem(slot, GUIUtils.createItem(iconMat, "&f" + waypoint.getWaypointName(), lore, "waypoint_select:" + waypoint.getId()));
            slot++;
        }

        // Fill remaining content slots (0..35) with empty glass.
        for (int i = slot; i < ITEMS_PER_PAGE; i++) {
            inventory.setItem(i, GUIUtils.createItem(Material.GRAY_STAINED_GLASS_PANE, "&7", new ArrayList<>(), "empty_slot"));
        }

        // Navigation buttons on the last row.
        if (currentPage > 0) {
            inventory.setItem(48, GUIUtils.button(Material.ARROW, "&fPrevious Page",
                    List.of("&7Page " + currentPage + " / " + totalPages), "waypoint_page:" + category + ":" + (currentPage - 1)));
        }
        inventory.setItem(49, GUIUtils.button(Material.BARRIER, "&cBack", List.of("&7Return to main menu"), "waypoint_back"));
        if (currentPage < totalPages - 1) {
            inventory.setItem(50, GUIUtils.button(Material.ARROW, "&fNext Page",
                    List.of("&7Page " + (currentPage + 2) + " / " + totalPages), "waypoint_page:" + category + ":" + (currentPage + 1)));
        }

        player.openInventory(inventory);
        GUIUtils.startAnimation(player, inventory);
    }
}
