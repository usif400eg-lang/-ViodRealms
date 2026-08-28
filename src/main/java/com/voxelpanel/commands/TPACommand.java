package com.voxelpanel.commands;

import com.voxelpanel.VoxelPanel;
import com.voxelpanel.managers.MessageManager;
import com.voxelpanel.managers.SoundManager;
import com.voxelpanel.managers.TeleportRequestManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles /tpa, /tpaccept, and /tpdeny for player-to-player teleport requests.
 * A single executor is reused for all three commands, dispatched by label.
 */
public class TPACommand implements CommandExecutor {
    private final VoxelPanel plugin;
    private final TeleportRequestManager requestManager;
    private final MessageManager messageManager;
    private final SoundManager soundManager;

    public TPACommand(VoxelPanel plugin, TeleportRequestManager requestManager, MessageManager messageManager, SoundManager soundManager) {
        this.plugin = plugin;
        this.requestManager = requestManager;
        this.messageManager = messageManager;
        this.soundManager = soundManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }

        String name = command.getName().toLowerCase();
        switch (name) {
            case "tpe" -> handleRequest(player, args);
            case "tpeaccept" -> handleAccept(player);
            case "tpedeny" -> handleDeny(player);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void handleRequest(Player player, String[] args) {
        if (args.length < 1) {
            messageManager.sendError(player, "invalid-input");
            return;
        }
        Player target = plugin.getServer().getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            messageManager.sendError(player, "player-not-found");
            soundManager.play(player, "error");
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            messageManager.sendError(player, "tpa-self");
            soundManager.play(player, "error");
            return;
        }
        requestManager.createRequest(player, target);
        messageManager.send(player, "tpa-sent", "player", target.getName());
        messageManager.send(target, "tpa-received", "player", player.getName());
        soundManager.play(player, "success");
        soundManager.play(target, "menu-open");
    }

    private void handleAccept(Player player) {
        if (!requestManager.hasRequest(player.getUniqueId())) {
            messageManager.sendError(player, "tpa-no-request");
            soundManager.play(player, "error");
            return;
        }
        var requesterUuid = requestManager.getRequester(player.getUniqueId());
        Player requester = plugin.getServer().getPlayer(requesterUuid);
        requestManager.removeRequest(player.getUniqueId());
        if (requester == null || !requester.isOnline()) {
            messageManager.sendError(player, "player-not-found");
            return;
        }
        messageManager.send(player, "tpa-accepted", "player", requester.getName());
        messageManager.send(requester, "tpa-accepted", "player", player.getName());
        plugin.getTeleportService().teleportToLocation(requester, player.getLocation());
    }

    private void handleDeny(Player player) {
        if (!requestManager.hasRequest(player.getUniqueId())) {
            messageManager.sendError(player, "tpa-no-request");
            soundManager.play(player, "error");
            return;
        }
        var requesterUuid = requestManager.getRequester(player.getUniqueId());
        Player requester = plugin.getServer().getPlayer(requesterUuid);
        requestManager.removeRequest(player.getUniqueId());
        messageManager.send(player, "tpa-denied");
        if (requester != null && requester.isOnline()) {
            messageManager.send(requester, "tpa-denied");
        }
        soundManager.play(player, "cancel");
    }
}
