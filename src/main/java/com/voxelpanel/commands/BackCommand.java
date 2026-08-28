package com.voxelpanel.commands;

import com.voxelpanel.VoxelPanel;
import com.voxelpanel.managers.MessageManager;
import com.voxelpanel.managers.SoundManager;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Returns the player to their previous location before the last teleport or death. */
public class BackCommand implements CommandExecutor {
    private final VoxelPanel plugin;
    private final MessageManager messageManager;
    private final SoundManager soundManager;

    public BackCommand(VoxelPanel plugin, MessageManager messageManager, SoundManager soundManager) {
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

        Location last = plugin.getTeleportService().getLastLocation(player.getUniqueId());
        if (last == null) {
            messageManager.sendError(player, "back-no-location");
            soundManager.play(player, "error");
            return true;
        }

        plugin.getTeleportService().teleportToLocation(player, last);
        return true;
    }
}
