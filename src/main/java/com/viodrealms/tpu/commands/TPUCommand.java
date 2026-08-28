package com.viodrealms.tpu.commands;

import com.viodrealms.tpu.ViodRealmsTPU;
import com.viodrealms.tpu.gui.MainMenu;
import com.viodrealms.tpu.managers.CompassTrackerManager;
import com.viodrealms.tpu.managers.MessageManager;
import com.viodrealms.tpu.managers.SoundManager;
import com.viodrealms.tpu.managers.WaypointManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.Arrays;

public class TPUCommand implements CommandExecutor {
    private final ViodRealmsTPU plugin;
    private final WaypointManager waypointManager;
    private final MessageManager messageManager;
    private final SoundManager soundManager;
    private final CompassTrackerManager compassTrackerManager;

    public TPUCommand(ViodRealmsTPU plugin, WaypointManager waypointManager, MessageManager messageManager, SoundManager soundManager, CompassTrackerManager compassTrackerManager) {
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

        if (!plugin.isSystemEnabled() && !player.hasPermission("viodrealms.tpu.admin")) {
            player.sendMessage(messageManager.get("prefix") + "§cنظام الـ Waypoints معطل حالياً من قبل الإدارة.");
            soundManager.play(player, "error");
            return true;
        }

        if (!player.hasPermission("viodrealms.tpu.use")) {
            messageManager.sendError(player, "no-permission");
            soundManager.play(player, "error");
            return true;
        }

        if (args.length > 0) {
            waypointManager.teleportWaypointByName(player, String.join(" ", Arrays.asList(args)));
        } else {
            com.viodrealms.tpu.gui.MainMenu.open(player, waypointManager.getWaypointCount(player.getUniqueId()), waypointManager.getMaxWaypoints(), plugin);
            soundManager.play(player, "menu-open");
        }
        return true;
    }
}
