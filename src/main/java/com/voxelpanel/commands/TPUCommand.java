package com.voxelpanel.commands;

import com.voxelpanel.VoxelPanel;
import com.voxelpanel.gui.MainMenu;
import com.voxelpanel.managers.CompassTrackerManager;
import com.voxelpanel.managers.MessageManager;
import com.voxelpanel.managers.SoundManager;
import com.voxelpanel.managers.WaypointManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.Arrays;

public class TPUCommand implements CommandExecutor {
    private final VoxelPanel plugin;
    private final WaypointManager waypointManager;
    private final MessageManager messageManager;
    private final SoundManager soundManager;
    private final CompassTrackerManager compassTrackerManager;

    public TPUCommand(VoxelPanel plugin, WaypointManager waypointManager, MessageManager messageManager, SoundManager soundManager, CompassTrackerManager compassTrackerManager) {
        this.plugin = plugin;
        this.waypointManager = waypointManager;
        this.messageManager = messageManager;
        this.soundManager = soundManager;
        this.compassTrackerManager = compassTrackerManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }

        if (!plugin.isSystemEnabled() && !player.hasPermission("voxelpanel.admin")) {
            player.sendMessage(messageManager.get("prefix") + "§cنظام الـ Waypoints معطل حالياً من قبل الإدارة.");
            soundManager.play(player, "error");
            return true;
        }

        if (!player.hasPermission("voxelpanel.use")) {
            messageManager.sendError(player, "no-permission");
            soundManager.play(player, "error");
            return true;
        }

        if (args.length > 0) {
            waypointManager.teleportWaypointByName(player, String.join(" ", Arrays.asList(args)));
        } else {
            com.voxelpanel.gui.MainMenu.open(player, waypointManager.getWaypointCount(player.getUniqueId()), waypointManager.getMaxWaypoints(), plugin);
            soundManager.play(player, "menu-open");
        }
        return true;
    }
}
