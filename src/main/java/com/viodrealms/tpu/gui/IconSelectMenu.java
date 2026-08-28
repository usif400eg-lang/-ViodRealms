package com.viodrealms.tpu.gui;

import com.viodrealms.tpu.ViodRealmsTPU;
import com.viodrealms.tpu.utils.GUIUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import java.util.Arrays;
import java.util.List;

public class IconSelectMenu {
    public static void open(Player player, int waypointId, ViodRealmsTPU plugin) {
        Inventory inventory = Bukkit.createInventory(null, 54, ChatColor.DARK_GRAY + "Select Icon");
        GUIUtils.fillBackground(inventory);
        Material[] icons = {
            Material.ENDER_PEARL, Material.RED_BED, Material.DIAMOND_PICKAXE, Material.DIAMOND_SWORD,
            Material.COMPASS, Material.MAP, Material.BEACON, Material.WHEAT,
            Material.GOLD_INGOT, Material.EMERALD, Material.NETHER_STAR, Material.TOTEM_OF_UNDYING,
            Material.SHIELD, Material.CROSSBOW, Material.TRIDENT, Material.ELYTRA,
            Material.CHEST, Material.BARREL, Material.ENDER_CHEST, Material.HOPPER
        };
        int slot = 0;
        for (Material icon : icons) {
            inventory.setItem(slot, GUIUtils.createItem(icon, "&f" + icon.name(), List.of("&7Click to select icon"), "icon_set:" + waypointId + ":" + icon.name()));
            slot++;
        }
        for (int i = slot; i < 45; i++) {
            inventory.setItem(i, GUIUtils.createItem(Material.GRAY_STAINED_GLASS_PANE, "&7", List.of(), "empty_slot"));
        }
        inventory.setItem(49, GUIUtils.button(Material.BARRIER, "&cBack", List.of("&7Return"), "waypoint_action_back"));
        player.openInventory(inventory);
        GUIUtils.startAnimation(player, inventory);
    }
}
