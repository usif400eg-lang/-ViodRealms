package com.voxelpanel.commands;

import com.voxelpanel.VoxelPanel;
import com.voxelpanel.gui.LanguageMenu;
import com.voxelpanel.managers.LanguageManager;
import com.voxelpanel.managers.MessageManager;
import com.voxelpanel.managers.SoundManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LanguageCommand implements CommandExecutor {
    private final VoxelPanel plugin;
    private final LanguageManager languageManager;
    private final MessageManager messageManager;
    private final SoundManager soundManager;

    public LanguageCommand(VoxelPanel plugin, LanguageManager languageManager, MessageManager messageManager, SoundManager soundManager) {
        this.plugin = plugin;
        this.languageManager = languageManager;
        this.messageManager = messageManager;
        this.soundManager = soundManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }

        if (args.length == 0) {
            LanguageMenu.open(player, plugin);
            return true;
        }

        String lang = args[0].toLowerCase();
        if (!languageManager.hasLanguage(lang)) {
            messageManager.sendError(player, "invalid-input");
            return true;
        }

        languageManager.setPlayerLanguage(player, lang);
        messageManager.send(player, "waypoint-created");
        soundManager.play(player, "success");
        return true;
    }
}
