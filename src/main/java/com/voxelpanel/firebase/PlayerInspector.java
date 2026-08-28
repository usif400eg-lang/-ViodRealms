package com.voxelpanel.firebase;

import com.voxelpanel.VoxelPanel;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Publishes a detailed snapshot of a single player (inventory, armor, health,
 * hunger, xp, location) to Firebase under servers/{id}/inspect/{name} when the
 * dashboard requests it via the "inspect_player" command. The dashboard renders
 * the inventory grid with real item textures using each item's material id.
 */
public class PlayerInspector {
    private final VoxelPanel plugin;
    private final FirebaseManager firebaseManager;

    public PlayerInspector(VoxelPanel plugin, FirebaseManager firebaseManager) {
        this.plugin = plugin;
        this.firebaseManager = firebaseManager;
    }

    /** Reads the player on the main thread and writes the snapshot async. */
    public void publish(String playerName) {
        if (firebaseManager == null || !firebaseManager.isConnected()) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = Bukkit.getPlayerExact(playerName);
            var ref = firebaseManager.getServerRef();
            if (ref == null) return;
            if (p == null) {
                Map<String, Object> offline = new HashMap<>();
                offline.put("online", false);
                offline.put("name", playerName);
                offline.put("t", System.currentTimeMillis());
                ref.child("inspect").child(sanitize(playerName)).setValueAsync(offline);
                return;
            }

            Map<String, Object> data = new HashMap<>();
            data.put("online", true);
            data.put("name", p.getName());
            data.put("uuid", p.getUniqueId().toString());
            data.put("health", Math.round(p.getHealth()));
            data.put("maxHealth", Math.round(getMaxHealth(p)));
            data.put("food", p.getFoodLevel());
            data.put("saturation", Math.round(p.getSaturation()));
            data.put("level", p.getLevel());
            data.put("xp", Math.round(p.getExp() * 100));
            data.put("gamemode", p.getGameMode().name());
            data.put("world", p.getWorld().getName());
            data.put("x", Math.round(p.getLocation().getX()));
            data.put("y", Math.round(p.getLocation().getY()));
            data.put("z", Math.round(p.getLocation().getZ()));
            data.put("t", System.currentTimeMillis());

            PlayerInventory inv = p.getInventory();
            data.put("main", serializeContents(inv.getStorageContents()));
            data.put("armor", serializeContents(inv.getArmorContents()));
            data.put("offhand", serializeItem(inv.getItemInOffHand()));

            firebaseManager.getServerRef().child("inspect").child(sanitize(p.getName())).setValueAsync(data);
        });
    }

    private List<Map<String, Object>> serializeContents(ItemStack[] contents) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (contents == null) return list;
        for (ItemStack item : contents) {
            list.add(serializeItem(item));
        }
        return list;
    }

    /** Empty slots become an empty map so array indices stay aligned in Firebase. */
    private Map<String, Object> serializeItem(ItemStack item) {
        Map<String, Object> m = new HashMap<>();
        if (item == null || item.getType().isAir()) {
            m.put("type", "AIR");
            m.put("amount", 0);
            return m;
        }
        m.put("type", item.getType().name());
        m.put("amount", item.getAmount());
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            m.put("name", item.getItemMeta().getDisplayName());
        }
        // Mark enchanted items so the dashboard can render the glow animation.
        boolean enchanted = item.getEnchantments() != null && !item.getEnchantments().isEmpty();
        if (item.hasItemMeta() && item.getItemMeta() instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta esm) {
            enchanted = enchanted || esm.hasStoredEnchants();
        }
        if (enchanted) {
            m.put("enchanted", true);
        }
        return m;
    }

    private String sanitize(String key) {
        return key.replaceAll("[.#$/\\[\\]]", "_");
    }

    /** Reads max health via the attribute API, falling back safely across API versions. */
    private double getMaxHealth(Player p) {
        try {
            var attr = p.getAttribute(org.bukkit.attribute.Attribute.valueOf("GENERIC_MAX_HEALTH"));
            if (attr != null) return attr.getValue();
        } catch (Throwable ignored) {
            // Older/newer API naming differences — fall back below.
        }
        return 20.0;
    }
}
