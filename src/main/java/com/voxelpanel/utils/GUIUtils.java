package com.voxelpanel.utils;

import com.voxelpanel.VoxelPanel;
import net.kyori.adventure.text.Component;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GUIUtils {
    private static final Map<UUID, Integer> animationTasks = new HashMap<>();
    private static final Material[] borderMaterials = {
        Material.RED_STAINED_GLASS_PANE,
        Material.ORANGE_STAINED_GLASS_PANE,
        Material.YELLOW_STAINED_GLASS_PANE,
        Material.LIME_STAINED_GLASS_PANE,
        Material.CYAN_STAINED_GLASS_PANE,
        Material.BLUE_STAINED_GLASS_PANE,
        Material.PURPLE_STAINED_GLASS_PANE,
        Material.MAGENTA_STAINED_GLASS_PANE
    };

    public static void fillBackground(Inventory inventory) {
        ItemStack background = createItem(Material.BLACK_STAINED_GLASS_PANE, "&8", List.of(), "empty_slot");
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, background);
    }

    public static ItemStack label(String title, List<String> lore) {
        return createItem(Material.GRAY_STAINED_GLASS_PANE, title, lore, "empty_slot");
    }

    public static ItemStack button(Material material, String title, List<String> lore, String action) {
        return createItem(material, title, lore, action);
    }

    public static ItemStack createItem(Material material, String title, List<String> lore, String action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(Component.text(ChatColor.translateAlternateColorCodes('&', title)));
        List<Component> loreComponents = new ArrayList<>();
        for (String line : lore) {
            loreComponents.add(Component.text(ChatColor.translateAlternateColorCodes('&', line)));
        }
        meta.lore(loreComponents);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(VoxelPanel.getPlugin(VoxelPanel.class), "tpu_action");
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    public static void startAnimation(Player player, Inventory inventory) {
        UUID uuid = player.getUniqueId();
        cancelAnimation(uuid);
        if (!VoxelPanel.getPlugin(VoxelPanel.class).getConfig().getBoolean("gui.animated", true)) {
            return;
        }
        int interval = VoxelPanel.getPlugin(VoxelPanel.class).getConfig().getInt("gui.animation-interval", 2);
        int taskId = VoxelPanel.getPlugin(VoxelPanel.class).getServer().getScheduler().scheduleSyncRepeatingTask(
                VoxelPanel.getPlugin(VoxelPanel.class),
                () -> {
                    if (!player.isOnline() || player.getOpenInventory().getTopInventory() != inventory) {
                        cancelAnimation(uuid);
                        return;
                    }
                    animateBorder(inventory);
                },
                0L,
                interval * 20L
        );
        animationTasks.put(uuid, taskId);
    }

    public static void cancelAnimation(UUID uuid) {
        Integer taskId = animationTasks.remove(uuid);
        if (taskId != null) {
            VoxelPanel.getPlugin(VoxelPanel.class).getServer().getScheduler().cancelTask(taskId);
        }
    }

    private static void animateBorder(Inventory inventory) {
        int size = inventory.getSize();
        long time = System.currentTimeMillis() / 500L;
        NamespacedKey key = new NamespacedKey(VoxelPanel.getPlugin(VoxelPanel.class), "tpu_action");
        for (int i = 0; i < size; i++) {
            if (isBorderSlot(i, size)) {
                ItemStack current = inventory.getItem(i);
                // Only animate empty/background slots, never overwrite functional buttons.
                if (current != null) {
                    ItemMeta currentMeta = current.getItemMeta();
                    if (currentMeta != null) {
                        String action = currentMeta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
                        if (action != null && !action.equals("empty_slot")) {
                            continue;
                        }
                    }
                }
                Material mat = borderMaterials[(int) ((time + i) % borderMaterials.length)];
                ItemStack borderItem = createItem(mat, "&8", List.of(), "empty_slot");
                inventory.setItem(i, borderItem);
            }
        }
    }

    private static boolean isBorderSlot(int slot, int size) {
        int rows = size / 9;
        if (slot < 9) return true;
        if (slot >= size - 9) return true;
        if (slot % 9 == 0) return true;
        if (slot % 9 == 8) return true;
        return false;
    }
}
