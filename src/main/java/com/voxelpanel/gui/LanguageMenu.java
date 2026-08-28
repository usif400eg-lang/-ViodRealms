package com.voxelpanel.gui;

import com.voxelpanel.VoxelPanel;
import com.voxelpanel.utils.GUIUtils;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import java.util.List;

public class LanguageMenu {
    public static void open(Player player, VoxelPanel plugin) {
        Inventory inventory = org.bukkit.Bukkit.createInventory(null, 27, ChatColor.DARK_GRAY + "اختر اللغة / Select Language");
        GUIUtils.fillBackground(inventory);

        inventory.setItem(11, GUIUtils.createItem(Material.GREEN_BANNER, "&aالعربية", List.of("&7اضغط لاختيار العربية", "&7Click to select Arabic"), "language_set:ar"));
        inventory.setItem(15, GUIUtils.createItem(Material.RED_BANNER, "&cEnglish", List.of("&7Click to select English", "&7اضغط لاختيار الإنجليزية"), "language_set:en"));

        player.openInventory(inventory);
        GUIUtils.startAnimation(player, inventory);
    }
}
