package com.voxelpanel.commands;

import com.voxelpanel.VoxelPanel;
import com.voxelpanel.managers.MessageManager;
import com.voxelpanel.managers.ShareRequestManager;
import com.voxelpanel.managers.SoundManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Handles /shareaccept and /sharedeny for waypoint share requests. */
public class ShareResponseCommand implements CommandExecutor {
    private final VoxelPanel plugin;
    private final ShareRequestManager shareRequestManager;
    private final MessageManager messageManager;
    private final SoundManager soundManager;

    public ShareResponseCommand(VoxelPanel plugin, ShareRequestManager shareRequestManager, MessageManager messageManager, SoundManager soundManager) {
        this.plugin = plugin;
        this.shareRequestManager = shareRequestManager;
        this.messageManager = messageManager;
        this.soundManager = soundManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }

        if (!shareRequestManager.hasRequest(player.getUniqueId())) {
            messageManager.sendError(player, "share-no-request");
            soundManager.play(player, "error");
            return true;
        }

        ShareRequestManager.ShareRequest request = shareRequestManager.getRequest(player.getUniqueId());
        shareRequestManager.removeRequest(player.getUniqueId());

        if (command.getName().equalsIgnoreCase("shareaccept")) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                plugin.getWaypointRepository().shareWaypoint(request.waypointId(), player.getUniqueId());
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    messageManager.send(player, "share-accepted", "player", request.ownerName());
                    soundManager.play(player, "success");
                    var owner = plugin.getServer().getPlayer(request.ownerUuid());
                    if (owner != null && owner.isOnline()) {
                        messageManager.send(owner, "share-accepted", "player", player.getName());
                    }
                });
            });
        } else {
            messageManager.send(player, "share-denied", "player", request.ownerName());
            soundManager.play(player, "cancel");
            var owner = plugin.getServer().getPlayer(request.ownerUuid());
            if (owner != null && owner.isOnline()) {
                messageManager.send(owner, "share-denied", "player", player.getName());
            }
        }
        return true;
    }
}
