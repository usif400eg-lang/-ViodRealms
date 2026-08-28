package com.viodrealms.tpu.gui;

import com.viodrealms.tpu.ViodRealmsTPU;
import com.viodrealms.tpu.utils.GUIUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import java.util.List;

public class MainMenu {
    public static void open(Player player, int count, int max, ViodRealmsTPU plugin) {
        Inventory inventory = Bukkit.createInventory(null, 27, ChatColor.DARK_GRAY + "ViodRealms TPU");
        GUIUtils.fillBackground(inventory);
        inventory.setItem(4, GUIUtils.label("&fViodRealms TPU", List.of("&7Waypoints: &f" + count + " &7/ &f" + max)));
        inventory.setItem(11, GUIUtils.button(Material.CHEST, "&fMy Waypoints", List.of("&7View and manage your saved locations"), "main_my_waypoints"));
        inventory.setItem(12, GUIUtils.button(Material.COMPASS, "&fCompass Tracker", List.of("&7Track a waypoint with your compass"), "main_compass_track"));
        inventory.setItem(13, GUIUtils.button(Material.MAP, "&fSearch", List.of("&7Filter your private waypoints"), "main_search_waypoints"));
        inventory.setItem(14, GUIUtils.button(Material.GLOBE_BANNER_PATTERN, "&fPublic Waypoints", List.of("&7View public waypoints from others"), "main_public_waypoints"));
        inventory.setItem(15, GUIUtils.button(Material.BOOK, "&fShared With Me", List.of("&7Waypoints shared with you"), "main_shared_waypoints"));
        inventory.setItem(22, GUIUtils.button(Material.BARRIER, "&cClose", List.of("&7Close this menu"), "main_close"));
        player.openInventory(inventory);
        GUIUtils.startAnimation(player, inventory);
    }
}
