package com.voxelpanel.gui;

import com.voxelpanel.VoxelPanel;
import com.voxelpanel.utils.GUIUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ShareMenu {
    public static void open(Player player, int waypointId, VoxelPanel plugin) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<UUID> sharedPlayers = plugin.getWaypointRepository().getSharedPlayers(waypointId);
            List<String> playerNames = new ArrayList<>();
            for (UUID uuid : sharedPlayers) {
                var p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    playerNames.add(p.getName());
                }
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> render(player, waypointId, playerNames, plugin));
        });
    }

    private static void render(Player player, int waypointId, List<String> sharedPlayers, VoxelPanel plugin) {
        Inventory inventory = Bukkit.createInventory(null, 27, ChatColor.DARK_GRAY + "Share Waypoint");
        GUIUtils.fillBackground(inventory);
        inventory.setItem(11, GUIUtils.createItem(Material.PLAYER_HEAD, "&fShare with Player", List.of("&7Click and type a player name"), "share_with:" + waypointId));
        inventory.setItem(13, GUIUtils.createItem(Material.BOOK, "&fShared With", List.of("&7Currently shared with: " + String.join(", ", sharedPlayers)), "share_view:" + waypointId));
        inventory.setItem(15, GUIUtils.createItem(Material.BARRIER, "&cRemove All Shares", List.of("&7Unshare with everyone"), "share_remove_all:" + waypointId));
        inventory.setItem(22, GUIUtils.button(Material.BARRIER, "&cBack", List.of("&7Return"), "waypoint_action_back"));
        player.openInventory(inventory);
        GUIUtils.startAnimation(player, inventory);
    }
}
