package com.viodrealms.tpu.managers;

import com.viodrealms.tpu.ViodRealmsTPU;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CompassTrackerManager {
    private final ViodRealmsTPU plugin;
    private final Map<UUID, Location> trackedLocations = new HashMap<>();
    private final Map<UUID, Integer> updateTasks = new HashMap<>();

    public CompassTrackerManager(ViodRealmsTPU plugin) {
        this.plugin = plugin;
    }

    public void trackWaypoint(Player player, org.bukkit.World world, double x, double y, double z, String name) {
        UUID uuid = player.getUniqueId();
        stopTracking(uuid);
        if (world == null) {
            player.sendMessage(plugin.getMessageManager().get(player, "world-not-found"));
            return;
        }
        Location loc = new Location(world, x, y, z);
        trackedLocations.put(uuid, loc);

        if (!plugin.getConfig().getBoolean("compass.enabled", true)) {
            return;
        }

        int interval = plugin.getConfig().getInt("compass.update-interval", 20);
        int taskId = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p == null || !p.isOnline()) {
                stopTracking(uuid);
                return;
            }
            Location target = trackedLocations.get(uuid);
            if (target == null || !target.getWorld().equals(p.getWorld())) {
                p.sendMessage(plugin.getMessageManager().get(p, "compass-no-waypoint"));
                stopTracking(uuid);
                return;
            }
            p.setCompassTarget(target);
            ItemStack compass = new ItemStack(org.bukkit.Material.COMPASS);
            ItemMeta meta = compass.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(
                        new org.bukkit.NamespacedKey(plugin, "tracking"),
                        org.bukkit.persistence.PersistentDataType.STRING,
                        name
                );
                compass.setItemMeta(meta);
            }
            p.getInventory().setItem(8, compass);
        }, 0L, interval);
        updateTasks.put(uuid, taskId);
    }

    public void stopTracking(UUID uuid) {
        trackedLocations.remove(uuid);
        Integer taskId = updateTasks.remove(uuid);
        if (taskId != null) {
            plugin.getServer().getScheduler().cancelTask(taskId);
        }
    }

    public boolean isTracking(UUID uuid) {
        return trackedLocations.containsKey(uuid);
    }

    public Location getTrackedLocation(UUID uuid) {
        return trackedLocations.get(uuid);
    }

    public void resetPlayerCompass(Player player) {
        UUID uuid = player.getUniqueId();
        stopTracking(uuid);
        player.setCompassTarget(player.getWorld().getSpawnLocation());
        if (player.getInventory().getItem(8) != null &&
            player.getInventory().getItem(8).getType() == org.bukkit.Material.COMPASS) {
            player.getInventory().setItem(8, new ItemStack(org.bukkit.Material.AIR));
        }
    }
}
