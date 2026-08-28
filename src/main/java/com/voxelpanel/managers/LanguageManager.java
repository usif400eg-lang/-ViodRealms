package com.voxelpanel.managers;

import com.voxelpanel.VoxelPanel;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class LanguageManager {
    private final VoxelPanel plugin;
    private final Map<String, FileConfiguration> languages = new HashMap<>();
    private String defaultLanguage = "ar";

    public LanguageManager(VoxelPanel plugin) {
        this.plugin = plugin;
        loadLanguages();
    }

    public void loadLanguages() {
        languages.clear();
        playerDataCache = null;
        loadLanguageFile("ar");
        loadLanguageFile("en");
        String configuredDefault = plugin.getConfig().getString("language.default", "ar");
        if (languages.containsKey(configuredDefault)) {
            defaultLanguage = configuredDefault;
        }
    }

    private void loadLanguageFile(String lang) {
        File langFile = new File(plugin.getDataFolder(), lang + ".yml");
        if (!langFile.exists()) {
            plugin.saveResource(lang + ".yml", false);
        }
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(langFile), StandardCharsets.UTF_8)) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(reader);
            languages.put(lang, config);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to load language file: " + lang + ".yml");
        }
    }

    public String getMessage(Player player, String key) {
        String lang = player != null ? getPlayerLanguage(player) : defaultLanguage;
        FileConfiguration langConfig = languages.get(lang);
        if (langConfig == null) {
            langConfig = languages.get(defaultLanguage);
        }
        String message = langConfig.getString(key, null);
        if (message == null) {
            message = languages.get(defaultLanguage).getString(key, key);
        }
        return colorize(message);
    }

    public String getMessage(String lang, String key) {
        FileConfiguration langConfig = languages.get(lang);
        if (langConfig == null) {
            langConfig = languages.get(defaultLanguage);
        }
        String message = langConfig.getString(key, null);
        if (message == null) {
            message = languages.get(defaultLanguage).getString(key, key);
        }
        return colorize(message);
    }

    public String getDefaultMessage(String key) {
        String message = languages.get(defaultLanguage).getString(key, key);
        return colorize(message);
    }

    private FileConfiguration playerDataCache;

    public String getPlayerLanguage(Player player) {
        if (playerDataCache == null) {
            File playerDataFile = new File(plugin.getDataFolder(), "players.yml");
            if (!playerDataFile.exists()) {
                plugin.saveResource("players.yml", false);
            }
            playerDataCache = YamlConfiguration.loadConfiguration(playerDataFile);
        }
        return playerDataCache.getString(player.getUniqueId() + ".language", defaultLanguage);
    }

    public void setPlayerLanguage(Player player, String language) {
        File playerDataFile = new File(plugin.getDataFolder(), "players.yml");
        if (!playerDataFile.exists()) {
            plugin.saveResource("players.yml", false);
        }
        FileConfiguration playerData = YamlConfiguration.loadConfiguration(playerDataFile);
        playerData.set(player.getUniqueId() + ".language", language);
        try {
            playerData.save(playerDataFile);
            playerDataCache = playerData;
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save player language preference for " + player.getName());
        }
    }

    public String getDefaultLanguage() {
        return defaultLanguage;
    }

    public boolean hasLanguage(String lang) {
        return languages.containsKey(lang);
    }

    private String colorize(String input) {
        if (input == null) return "";
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', input);
    }
}
