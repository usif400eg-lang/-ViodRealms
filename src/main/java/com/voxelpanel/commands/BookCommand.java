package com.voxelpanel.commands;

import com.voxelpanel.VoxelPanel;
import com.voxelpanel.managers.MessageManager;
import com.voxelpanel.managers.SoundManager;
import com.voxelpanel.utils.BookUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Gives the player a written book listing all plugin commands. */
public class BookCommand implements CommandExecutor {
    private final VoxelPanel plugin;
    private final MessageManager messageManager;
    private final SoundManager soundManager;

    public BookCommand(VoxelPanel plugin, MessageManager messageManager, SoundManager soundManager) {
        this.plugin = plugin;
        this.messageManager = messageManager;
        this.soundManager = soundManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }

        if (!player.hasPermission("voxelpanel.use")) {
            messageManager.sendError(player, "no-permission");
            soundManager.play(player, "error");
            return true;
        }

        BookUtil.giveCommandBook(
                player,
                messageManager.get(player, "book-title"),
                messageManager.get(player, "book-author"));
        soundManager.play(player, "menu-open");
        return true;
    }
}
