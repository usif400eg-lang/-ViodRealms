package com.viodrealms.tpu.gui;

import com.viodrealms.tpu.ViodRealmsTPU;
import com.viodrealms.tpu.database.WaypointRepository;
import com.viodrealms.tpu.utils.GUIUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import java.util.ArrayList;
import java.util.List;

public class AdminPlayerSearchMenu {
    public static void open(Player player, List<WaypointRepository.PlayerRecord> players, ViodRealmsTPU plugin) {
        Inventory inventory = Bukkit.createInventory(null, 54, ChatColor.DARK_GRAY + "Search Player");
        GUIUtils.fillBackground(inventory);
        int slot = 0;
        for (WaypointRepository.PlayerRecord record : players) {
            if (slot >= 45) break;
            inventory.setItem(slot, GUIUtils.createItem(Material.PLAYER_HEAD, "&a" + record.getPlayerName(), List.of(
                    "&7UUID: " + record.getUuid(),
                    "&7Select to view waypoints"), "admin_select_player:" + record.getUuid()));
            slot++;
        }
        for (int i = slot; i < 45; i++) {
            inventory.setItem(i, GUIUtils.createItem(Material.GRAY_STAINED_GLASS_PANE, "&7", new ArrayList<>(), "empty_slot"));
        }
        inventory.setItem(49, GUIUtils.createItem(Material.BARRIER, "&cBack", List.of("&7Return to admin menu"), "admin_back"));
        player.openInventory(inventory);
        GUIUtils.startAnimation(player, inventory);
    }
}
