package com.voxelpanel.managers;

import com.voxelpanel.VoxelPanel;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Stores and applies player ranks (member / dev / admin / op).
 *
 * Ranks are persisted in ranks.yml. The "op" rank grants Bukkit operator status.
 * If LuckPerms is installed, the matching permission group is also applied via its
 * console command, so ranks integrate with existing permission setups. Without
 * LuckPerms, ranks are still tracked and shown on the dashboard, and "op" still works.
 */
public class RankManager {
    public static final String[] RANKS = {"member", "dev", "admin", "op"};

    private final VoxelPanel plugin;
    private final File file;
    private FileConfiguration config;

    public RankManager(VoxelPanel plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "ranks.yml");
        load();
    }

    private void load() {
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Could not create ranks.yml");
            }
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    private void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save ranks.yml");
        }
    }

    public synchronized String getRank(String playerName) {
        return config.getString("ranks." + playerName.toLowerCase(), "member");
    }

    /** Sets a player's rank, persists it, and applies OP / LuckPerms side effects. */
    public boolean setRank(String playerName, String rank) {
        String normalized = rank.toLowerCase();
        boolean valid = false;
        for (String r : RANKS) {
            if (r.equals(normalized)) { valid = true; break; }
        }
        if (!valid) {
            return false;
        }

        synchronized (this) {
            config.set("ranks." + playerName.toLowerCase(), normalized);
            save();
        }

        // Apply the rank effects on the main thread.
        Bukkit.getScheduler().runTask(plugin, () -> {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(playerName);

            // "op" grants operator; any other rank removes it.
            offline.setOp(normalized.equals("op"));

            // If LuckPerms is present, sync the permission group too.
            if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
                // member/dev/admin/op map to LuckPerms groups of the same name.
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        "lp user " + playerName + " parent set " + normalized);
            }

            Player online = Bukkit.getPlayerExact(playerName);
            if (online != null) {
                online.sendMessage(plugin.getMessageManager().get("prefix") + "§7تم تغيير رتبتك إلى §f" + normalized);
            }
        });
        return true;
    }

    public synchronized Map<String, String> getAllRanks() {
        Map<String, String> result = new HashMap<>();
        if (config.isConfigurationSection("ranks")) {
            for (String key : config.getConfigurationSection("ranks").getKeys(false)) {
                result.put(key, config.getString("ranks." + key, "member"));
            }
        }
        return result;
    }
}
