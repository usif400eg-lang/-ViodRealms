package com.viodrealms.tpu.commands;

import com.viodrealms.tpu.ViodRealmsTPU;
import com.viodrealms.tpu.gui.AdminMenu;
import com.viodrealms.tpu.gui.AdminWaypointMenu;
import com.viodrealms.tpu.managers.MessageManager;
import com.viodrealms.tpu.managers.SoundManager;
import com.viodrealms.tpu.managers.WaypointManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TPUAdminCommand implements CommandExecutor {
    private final ViodRealmsTPU plugin;
    private final WaypointManager waypointManager;
    private final MessageManager messageManager;
    private final SoundManager soundManager;

    public TPUAdminCommand(ViodRealmsTPU plugin, WaypointManager waypointManager, MessageManager messageManager, SoundManager soundManager) {
        this.plugin = plugin;
        this.waypointManager = waypointManager;
        this.messageManager = messageManager;
        this.soundManager = soundManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }
        if (!player.hasPermission("viodrealms.tpu.admin")) {
            messageManager.sendError(player, "no-permission");
            soundManager.play(player, "error");
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("waypoint")) {
            String targetName = args[1];
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                var records = plugin.getWaypointRepository().getKnownPlayers();
                var match = records.stream().filter(r -> r.getPlayerName().equalsIgnoreCase(targetName)).findFirst().orElse(null);
                if (match == null) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        messageManager.sendError(player, "player-not-found");
                        soundManager.play(player, "error");
                    });
                    return;
                }
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    AdminWaypointMenu.open(player, match.getUuid(), plugin, 1);
                    soundManager.play(player, "menu-open");
                });
            });
            return true;
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("waypoint") && args[1].equalsIgnoreCase("del")) {
            String waypointName = args[2];
            String targetName = args[3];
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                var records = plugin.getWaypointRepository().getKnownPlayers();
                var match = records.stream().filter(r -> r.getPlayerName().equalsIgnoreCase(targetName)).findFirst().orElse(null);
                if (match == null) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        messageManager.sendError(player, "player-not-found");
                        soundManager.play(player, "error");
                    });
                    return;
                }
                var waypoint = plugin.getWaypointRepository().getWaypointForPlayerByName(match.getUuid(), waypointName).orElse(null);
                if (waypoint == null) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        messageManager.sendError(player, "waypoint-not-found");
                        soundManager.play(player, "error");
                    });
                    return;
                }
                plugin.getServer().getScheduler().runTask(plugin, () -> waypointManager.deleteWaypoint(player, waypoint.getId(), true));
            });
            return true;
        }

        AdminMenu.open(player, plugin);
        soundManager.play(player, "menu-open");
        return true;
    }
}
