package com.voxelpanel.commands;

import com.voxelpanel.VoxelPanel;
import com.voxelpanel.managers.CompassTrackerManager;
import com.voxelpanel.managers.MessageManager;
import com.voxelpanel.managers.SoundManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CompassCommand implements CommandExecutor {
    private final VoxelPanel plugin;
    private final CompassTrackerManager compassTrackerManager;
    private final MessageManager messageManager;
    private final SoundManager soundManager;

    public CompassCommand(VoxelPanel plugin, CompassTrackerManager compassTrackerManager, MessageManager messageManager, SoundManager soundManager) {
        this.plugin = plugin;
        this.compassTrackerManager = compassTrackerManager;
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
            if (compassTrackerManager.isTracking(player.getUniqueId())) {
                messageManager.send(player, "compass-reset");
                compassTrackerManager.resetPlayerCompass(player);
            } else {
                messageManager.send(player, "compass-no-waypoint");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("reset")) {
            messageManager.send(player, "compass-reset");
            compassTrackerManager.resetPlayerCompass(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("track")) {
            if (args.length < 2) {
                messageManager.sendError(player, "invalid-input");
                return true;
            }
            String waypointName = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                var waypoint = plugin.getWaypointRepository().getWaypointForPlayerByName(player.getUniqueId(), waypointName).orElse(null);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (waypoint == null) {
                        messageManager.sendError(player, "waypoint-not-found");
                        return;
                    }
                    compassTrackerManager.trackWaypoint(player, Bukkit.getWorld(waypoint.getWorldName()), waypoint.getX(), waypoint.getY(), waypoint.getZ(), waypoint.getWaypointName());
                    messageManager.send(player, "compass-set", "name", waypoint.getWaypointName());
                    soundManager.play(player, "success");
                });
            });
            return true;
        }

        return false;
    }
}
