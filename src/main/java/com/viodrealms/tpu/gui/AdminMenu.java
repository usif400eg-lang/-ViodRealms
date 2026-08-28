package com.viodrealms.tpu.gui;

import com.viodrealms.tpu.ViodRealmsTPU;
import com.viodrealms.tpu.utils.GUIUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import java.util.Arrays;

public class AdminMenu {
    public static void open(Player player, ViodRealmsTPU plugin) {
        Inventory inventory = Bukkit.createInventory(null, 27, ChatColor.DARK_GRAY + "Admin Menu");
        GUIUtils.fillBackground(inventory);
        inventory.setItem(10, GUIUtils.createItem(Material.PLAYER_HEAD, "&aSearch Player", Arrays.asList("&7Search for a known player"), "admin_main_search"));
        inventory.setItem(12, GUIUtils.createItem(Material.CHEST, "&aPlayer Waypoints", Arrays.asList("&7Open a selected player's waypoint list"), "admin_main_player_waypoints"));
        inventory.setItem(14, GUIUtils.createItem(Material.MAP, "&aSearch Waypoints", Arrays.asList("&7Search by waypoint name"), "admin_main_search_waypoints"));
        inventory.setItem(16, GUIUtils.createItem(Material.BOOK, "&aStatistics", Arrays.asList("&7View plugin statistics"), "admin_main_statistics"));
        inventory.setItem(22, GUIUtils.createItem(Material.BARRIER, "&cClose", Arrays.asList("&7Close this menu"), "admin_main_close"));
        player.openInventory(inventory);
        GUIUtils.startAnimation(player, inventory);
    }
}
