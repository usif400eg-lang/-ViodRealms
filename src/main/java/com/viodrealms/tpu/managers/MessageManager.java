package com.viodrealms.tpu.managers;

import com.viodrealms.tpu.ViodRealmsTPU;
import org.bukkit.entity.Player;

public class MessageManager {
    private final ViodRealmsTPU plugin;
    private final LanguageManager languageManager;

    public MessageManager(ViodRealmsTPU plugin) {
        this.plugin = plugin;
        this.languageManager = new LanguageManager(plugin);
    }

    public String get(String key) {
        return languageManager.getDefaultMessage(key);
    }

    public String get(String lang, String key) {
        return languageManager.getMessage(lang, key);
    }

    public String format(String key, String... replacements) {
        String value = get(key);
        for (int i = 0; i < replacements.length; i++) {
            value = value.replace("{" + i + "}", replacements[i]);
        }
        return value;
    }

    public void send(Player player, String key) {
        player.sendMessage(get("prefix") + get(player, key));
    }

    public void sendError(Player player, String key) {
        player.sendMessage(get("prefix") + get(player, key));
    }

    public void send(Player player, String key, String... replacements) {
        String message = get(player, key);
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                message = message.replace("{" + replacements[i] + "}", replacements[i + 1]);
            }
        }
        player.sendMessage(get("prefix") + message);
    }

    public String get(Player player, String key) {
        return languageManager.getMessage(player, key);
    }

    public void reload() {
        languageManager.loadLanguages();
    }
}
