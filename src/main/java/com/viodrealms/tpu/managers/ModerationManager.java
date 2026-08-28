package com.viodrealms.tpu.managers;

import com.viodrealms.tpu.ViodRealmsTPU;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Wraps Bukkit's ban and whitelist systems so the dashboard can drive them.
 * All operations run on the main thread (Bukkit API is not thread-safe).
 */
public class ModerationManager {
    private final ViodRealmsTPU plugin;

    public ModerationManager(ViodRealmsTPU plugin) {
        this.plugin = plugin;
    }

    // ---- Bans by name ----
    public void banPlayer(String name, String reason) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            String finalReason = (reason == null || reason.isBlank()) ? "Banned by dashboard" : reason;
            Bukkit.getBanList(BanList.Type.NAME).addBan(name, finalReason, null, "Dashboard");
            Player online = Bukkit.getPlayerExact(name);
            if (online != null) {
                online.kickPlayer("§cتم حظرك: §f" + finalReason);
            }
            plugin.getLogger().info("[Dashboard] Banned player: " + name);
        });
    }

    // ---- Bans by UUID ----
    public void banPlayerId(String uuidStr, String reason) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
                String finalReason = (reason == null || reason.isBlank()) ? "Banned by dashboard" : reason;
                String name = offline.getName() != null ? offline.getName() : uuidStr;
                Bukkit.getBanList(BanList.Type.NAME).addBan(name, finalReason, null, "Dashboard");
                Player online = Bukkit.getPlayer(uuid);
                if (online != null) {
                    online.kickPlayer("§cتم حظرك: §f" + finalReason);
                }
                plugin.getLogger().info("[Dashboard] Banned UUID: " + uuidStr);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[Dashboard] Invalid UUID for ban: " + uuidStr);
            }
        });
    }

    public void unbanPlayer(String name) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.getBanList(BanList.Type.NAME).pardon(name);
            plugin.getLogger().info("[Dashboard] Unbanned player: " + name);
        });
    }

    // ---- Whitelist ----
    public void whitelistAdd(String name) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
            offline.setWhitelisted(true);
            plugin.getLogger().info("[Dashboard] Whitelisted: " + name);
        });
    }

    public void whitelistRemove(String name) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
            offline.setWhitelisted(false);
            plugin.getLogger().info("[Dashboard] Removed from whitelist: " + name);
        });
    }

    // ---- Snapshots for syncing to Firebase (must be called on main thread) ----
    public Map<String, Object> getBansSnapshot() {
        Map<String, Object> result = new HashMap<>();
        for (var entry : Bukkit.getBanList(BanList.Type.NAME).getBanEntries()) {
            String target = entry.getTarget();
            Map<String, Object> data = new HashMap<>();
            data.put("name", target);
            data.put("reason", entry.getReason() != null ? entry.getReason() : "");
            data.put("source", entry.getSource() != null ? entry.getSource() : "");
            // Firebase keys cannot contain '.', '#', '$', '/', '[', ']'
            result.put(sanitizeKey(target), data);
        }
        return result;
    }

    public Map<String, Object> getWhitelistSnapshot() {
        Map<String, Object> result = new HashMap<>();
        for (OfflinePlayer p : Bukkit.getWhitelistedPlayers()) {
            String name = p.getName() != null ? p.getName() : p.getUniqueId().toString();
            Map<String, Object> data = new HashMap<>();
            data.put("name", name);
            data.put("uuid", p.getUniqueId().toString());
            result.put(sanitizeKey(name), data);
        }
        return result;
    }

    private String sanitizeKey(String key) {
        return key.replaceAll("[.#$/\\[\\]]", "_");
    }
}
