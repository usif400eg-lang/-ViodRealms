package com.viodrealms.tpu.utils;

import com.viodrealms.tpu.ViodRealmsTPU;
import com.viodrealms.tpu.models.Waypoint;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Exports and imports a player's waypoints to and from a YAML file.
 * Files are stored under the plugin's data folder in an "exports" directory.
 */
public final class ImportExportUtil {

    private ImportExportUtil() {
    }

    private static File getExportsDir(ViodRealmsTPU plugin) {
        File dir = new File(plugin.getDataFolder(), "exports");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /** Writes the given waypoints to a YAML file. Returns the number written, or -1 on error. */
    public static int exportWaypoints(ViodRealmsTPU plugin, UUID playerUuid, String fileName, List<Waypoint> waypoints) {
        File file = new File(getExportsDir(plugin), fileName + ".yml");
        FileConfiguration config = new YamlConfiguration();
        int index = 0;
        for (Waypoint wp : waypoints) {
            String path = "waypoints." + index;
            config.set(path + ".name", wp.getWaypointName());
            config.set(path + ".world", wp.getWorldName());
            config.set(path + ".x", wp.getX());
            config.set(path + ".y", wp.getY());
            config.set(path + ".z", wp.getZ());
            config.set(path + ".yaw", wp.getYaw());
            config.set(path + ".pitch", wp.getPitch());
            config.set(path + ".category", wp.getCategory());
            config.set(path + ".icon", wp.getIcon());
            index++;
        }
        try {
            config.save(file);
            return index;
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to export waypoints: " + e.getMessage());
            return -1;
        }
    }

    /** Reads waypoints from a YAML file for the given player. Returns null if the file does not exist. */
    public static List<Waypoint> importWaypoints(ViodRealmsTPU plugin, UUID playerUuid, String playerName, String fileName) {
        File file = new File(getExportsDir(plugin), fileName + ".yml");
        if (!file.exists()) {
            return null;
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<Waypoint> imported = new ArrayList<>();
        if (!config.isConfigurationSection("waypoints")) {
            return imported;
        }
        for (String key : config.getConfigurationSection("waypoints").getKeys(false)) {
            String path = "waypoints." + key;
            String name = config.getString(path + ".name", "imported");
            String world = config.getString(path + ".world", "world");
            double x = config.getDouble(path + ".x");
            double y = config.getDouble(path + ".y");
            double z = config.getDouble(path + ".z");
            float yaw = (float) config.getDouble(path + ".yaw");
            float pitch = (float) config.getDouble(path + ".pitch");
            String category = config.getString(path + ".category", "OTHER");
            String icon = config.getString(path + ".icon", "ENDER_PEARL");
            imported.add(new Waypoint(0, playerUuid, playerName, name, world, x, y, z, yaw, pitch,
                    Instant.now(), category, icon, false, false, null));
        }
        return imported;
    }
}
