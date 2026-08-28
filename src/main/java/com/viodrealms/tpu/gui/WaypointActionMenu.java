package com.viodrealms.tpu.gui;

import com.viodrealms.tpu.ViodRealmsTPU;
import com.viodrealms.tpu.models.Waypoint;
import com.viodrealms.tpu.utils.GUIUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import java.util.Arrays;
import java.util.List;

public class WaypointActionMenu {
    public static void open(Player player, int waypointId, ViodRealmsTPU plugin) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Waypoint waypoint = plugin.getWaypointRepository().getWaypointById(waypointId).orElse(null);
            plugin.getServer().getScheduler().runTask(plugin, () -> render(player, waypoint, plugin));
        });
    }

    private static void render(Player player, Waypoint waypoint, ViodRealmsTPU plugin) {
        if (waypoint == null) {
            player.closeInventory();
            return;
        }
        Inventory inventory = Bukkit.createInventory(null, 45, ChatColor.DARK_GRAY + waypoint.getWaypointName());
        GUIUtils.fillBackground(inventory);
        inventory.setItem(10, GUIUtils.button(Material.ENDER_PEARL, "&fTeleport", Arrays.asList("&7Teleport to this waypoint"), "waypoint_action_teleport:" + waypoint.getId()));
        inventory.setItem(12, GUIUtils.button(Material.ANVIL, "&fRename", Arrays.asList("&7Rename this waypoint"), "waypoint_action_rename:" + waypoint.getId()));
        inventory.setItem(14, GUIUtils.button(Material.PAINTING, "&fCategory", Arrays.asList("&7Category: " + waypoint.getCategory()), "waypoint_action_category:" + waypoint.getId()));
        inventory.setItem(16, GUIUtils.button(Material.ITEM_FRAME, "&fIcon", Arrays.asList("&7Icon: " + waypoint.getIcon()), "waypoint_action_icon:" + waypoint.getId()));
        inventory.setItem(20, GUIUtils.button(waypoint.isPublic() ? Material.LIME_DYE : Material.GRAY_DYE, waypoint.isPublic() ? "&aPublic" : "&7Private", Arrays.asList("&7Toggle public visibility"), "waypoint_action_public:" + waypoint.getId()));
        inventory.setItem(22, GUIUtils.button(Material.PLAYER_HEAD, "&fShare", Arrays.asList("&7Share with a player"), "waypoint_action_share:" + waypoint.getId()));
        inventory.setItem(24, GUIUtils.button(Material.LAVA_BUCKET, "&cDelete", Arrays.asList("&7Delete this waypoint"), "waypoint_action_delete:" + waypoint.getId()));
        inventory.setItem(36, GUIUtils.button(Material.BARRIER, "&cBack", Arrays.asList("&7Return to waypoint list"), "waypoint_action_back"));
        player.openInventory(inventory);
        GUIUtils.startAnimation(player, inventory);
    }
}
