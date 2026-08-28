package com.voxelpanel.gui;

import com.voxelpanel.VoxelPanel;
import com.voxelpanel.utils.GUIUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import java.util.List;

public class CategorySelectMenu {
    public static void open(Player player, int waypointId, VoxelPanel plugin) {
        Inventory inventory = Bukkit.createInventory(null, 27, ChatColor.DARK_GRAY + "Select Category");
        GUIUtils.fillBackground(inventory);
        String[] categories = {"MINE", "BASE", "FARM", "OTHER"};
        Material[] materials = {Material.DIAMOND_PICKAXE, Material.BEACON, Material.WHEAT, Material.BARRIER};
        for (int i = 0; i < categories.length; i++) {
            inventory.setItem(10 + i, GUIUtils.createItem(materials[i], "&f" + categories[i], List.of("&7Click to set category"), "category_set:" + waypointId + ":" + categories[i]));
        }
        inventory.setItem(18, GUIUtils.button(Material.BARRIER, "&cBack", List.of("&7Return"), "waypoint_action_back"));
        player.openInventory(inventory);
        GUIUtils.startAnimation(player, inventory);
    }
}
