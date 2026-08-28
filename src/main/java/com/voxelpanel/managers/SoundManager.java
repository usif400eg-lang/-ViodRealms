package com.voxelpanel.managers;

import com.voxelpanel.VoxelPanel;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class SoundManager {
    private final VoxelPanel plugin;

    public SoundManager(VoxelPanel plugin) {
        this.plugin = plugin;
    }

    public void play(Player player, String key) {
        if (player == null || !plugin.getConfig().getBoolean("sounds.enabled", true)) {
            return;
        }
        String soundName = plugin.getConfig().getString("sounds." + key, "");
        if (soundName == null || soundName.isBlank()) {
            return;
        }
        try {
            player.playSound(player.getLocation(), Sound.valueOf(soundName), 1.0F, 1.0F);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Unsupported sound value: " + soundName);
        }
    }
}
