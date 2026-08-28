package com.viodrealms.tpu.gui;

import com.viodrealms.tpu.ViodRealmsTPU;
import com.viodrealms.tpu.utils.GUIUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Native Paper text-entry screen. The anvil rename box is the client-side text field. */
public final class CreateWaypointMenu {
    private static final Map<UUID, String> OPEN_SCREENS = new ConcurrentHashMap<>();

    private CreateWaypointMenu() {
    }

    public static void open(Player player, ViodRealmsTPU plugin) {
        open(player, plugin, "");
    }

    public static void open(Player player, ViodRealmsTPU plugin, String enteredName) {
        Inventory inventory = Bukkit.createInventory(null, org.bukkit.event.inventory.InventoryType.ANVIL,
                Component.text("Create Waypoint"));
        ItemStack nameField = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = nameField.getItemMeta();
        meta.displayName(Component.text(enteredName == null || enteredName.isBlank() ? "Waypoint Name" : enteredName));
        nameField.setItemMeta(meta);
        inventory.setItem(0, nameField);
        inventory.setItem(1, GUIUtils.createItem(Material.BARRIER, "&cCancel", java.util.List.of("&7Return without saving"), "create_cancel"));
        OPEN_SCREENS.put(player.getUniqueId(), enteredName == null ? "" : enteredName);
        player.openInventory(inventory);
    }

    public static boolean isOpen(Player player) {
        return OPEN_SCREENS.containsKey(player.getUniqueId()) && player.getOpenInventory().getTopInventory() instanceof AnvilInventory;
    }

    public static void close(Player player) {
        OPEN_SCREENS.remove(player.getUniqueId());
    }

    public static String enteredName(Player player) {
        if (player.getOpenInventory().getTopInventory() instanceof AnvilInventory anvil) {
            return anvil.getRenameText();
        }
        return OPEN_SCREENS.getOrDefault(player.getUniqueId(), "");
    }
}
