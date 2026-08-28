package com.viodrealms.tpu.gui;

import com.viodrealms.tpu.ViodRealmsTPU;
import com.viodrealms.tpu.utils.GUIUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import java.util.Arrays;

public class ConfirmDeleteMenu {
    public static void open(Player player, int waypointId, ViodRealmsTPU plugin, boolean admin) {
        Inventory inventory = Bukkit.createInventory(null, 27, ChatColor.DARK_GRAY + "Confirm Delete");
        GUIUtils.fillBackground(inventory);
        String confirmAction = admin ? "admin_delete_confirm:" + waypointId : "confirm_delete:" + waypointId;
        String cancelAction = admin ? "admin_delete_cancel" : "cancel_delete";
        inventory.setItem(11, GUIUtils.createItem(Material.LIME_WOOL, "&aConfirm Delete", Arrays.asList("&7Permanently delete this waypoint"), confirmAction));
        inventory.setItem(15, GUIUtils.createItem(Material.RED_WOOL, "&cCancel", Arrays.asList("&7Cancel this action"), cancelAction));
        player.openInventory(inventory);
        GUIUtils.startAnimation(player, inventory);
    }
}
