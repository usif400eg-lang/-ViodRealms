package com.voxelpanel.commands;

import com.voxelpanel.VoxelPanel;
import com.voxelpanel.managers.MessageManager;
import com.voxelpanel.managers.SoundManager;
import com.voxelpanel.managers.WaypointManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.Arrays;

/** Text-command alternatives for the waypoint menus. */
public final class WaypointCommand implements CommandExecutor {
    private final VoxelPanel plugin;
    private final WaypointManager waypoints;
    private final MessageManager messages;
    private final SoundManager sounds;

    public WaypointCommand(VoxelPanel plugin, WaypointManager waypoints, MessageManager messages, SoundManager sounds) {
        this.plugin = plugin;
        this.waypoints = waypoints;
        this.messages = messages;
        this.sounds = sounds;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }

        // Admin toggle command: /waypoint on/off
        if (args.length == 1 && (args[0].equalsIgnoreCase("on") || args[0].equalsIgnoreCase("off"))) {
            if (!player.hasPermission("voxelpanel.admin")) {
                messages.sendError(player, "no-permission");
                sounds.play(player, "error");
                return true;
            }
            boolean enable = args[0].equalsIgnoreCase("on");
            plugin.setSystemEnabled(enable);
            player.sendMessage(messages.get("prefix") + "§7تم " + (enable ? "§aتفعيل" : "§cتعطيل") + " §7نظام الـ Waypoints بنجاح.");
            sounds.play(player, "success");
            return true;
        }

        if (!plugin.isSystemEnabled() && !player.hasPermission("voxelpanel.admin")) {
            player.sendMessage(messages.get("prefix") + "§cنظام الـ Waypoints معطل حالياً من قبل الإدارة.");
            sounds.play(player, "error");
            return true;
        }

        if (!player.hasPermission("voxelpanel.use")) {
            messages.sendError(player, "no-permission");
            sounds.play(player, "error");
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("list")) {
            com.voxelpanel.gui.WaypointMenu.open(player, plugin);
            return true;
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("set")) {
            waypoints.createWaypoint(player, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
            return true;
        }

        if (args.length >= 2 && (args[0].equalsIgnoreCase("del") || args[0].equalsIgnoreCase("delete"))) {
            waypoints.deleteWaypointByName(player, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
            return true;
        }

        if (args.length >= 3 && (args[0].equalsIgnoreCase("rem") || args[0].equalsIgnoreCase("rename"))) {
            String oldName = args[1];
            String newName = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                var wp = plugin.getWaypointRepository().getWaypointForPlayerByName(player.getUniqueId(), oldName).orElse(null);
                if (wp == null) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        messages.sendError(player, "waypoint-not-found");
                        sounds.play(player, "error");
                    });
                    return;
                }
                plugin.getServer().getScheduler().runTask(plugin, () -> waypoints.renameWaypoint(player, wp.getId(), newName));
            });
            return true;
        }

        if (args.length >= 3 && args[0].equalsIgnoreCase("category")) {
            String name = args[1];
            String category = args[2].toUpperCase();
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                var wp = plugin.getWaypointRepository().getWaypointForPlayerByName(player.getUniqueId(), name).orElse(null);
                if (wp == null) {
                    Bukkit.getScheduler().runTask(plugin, () -> messages.sendError(player, "waypoint-not-found"));
                    return;
                }
                boolean success = plugin.getWaypointRepository().updateWaypointCategory(wp.getId(), category);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (success) {
                        messages.send(player, "category-selected", "category", category);
                        sounds.play(player, "success");
                    } else {
                        messages.sendError(player, "database-error");
                    }
                });
            });
            return true;
        }

        if (args.length >= 3 && args[0].equalsIgnoreCase("icon")) {
            String name = args[1];
            String icon = args[2].toUpperCase();
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                var wp = plugin.getWaypointRepository().getWaypointForPlayerByName(player.getUniqueId(), name).orElse(null);
                if (wp == null) {
                    Bukkit.getScheduler().runTask(plugin, () -> messages.sendError(player, "waypoint-not-found"));
                    return;
                }
                try {
                    ItemStack item = new ItemStack(org.bukkit.Material.valueOf(icon));
                    boolean success = plugin.getWaypointRepository().updateWaypointIcon(wp.getId(), icon);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (success) {
                            messages.send(player, "icon-selected", "icon", icon);
                            sounds.play(player, "success");
                        } else {
                            messages.sendError(player, "database-error");
                        }
                    });
                } catch (IllegalArgumentException e) {
                    messages.sendError(player, "invalid-input");
                }
            });
            return true;
        }

        if (args.length >= 3 && args[0].equalsIgnoreCase("public")) {
            String name = args[1];
            boolean isPublic = args[2].equalsIgnoreCase("on") || args[2].equalsIgnoreCase("true") || args[2].equalsIgnoreCase("1");
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                var wp = plugin.getWaypointRepository().getWaypointForPlayerByName(player.getUniqueId(), name).orElse(null);
                if (wp == null) {
                    Bukkit.getScheduler().runTask(plugin, () -> messages.sendError(player, "waypoint-not-found"));
                    return;
                }
                boolean success = plugin.getWaypointRepository().updateWaypointPublic(wp.getId(), isPublic);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (success) {
                        messages.send(player, isPublic ? "public-enabled" : "public-disabled");
                        sounds.play(player, "success");
                    } else {
                        messages.sendError(player, "database-error");
                    }
                });
            });
            return true;
        }

        if (args.length >= 3 && args[0].equalsIgnoreCase("share")) {
            String name = args[1];
            String targetName = args[2];
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                var wp = plugin.getWaypointRepository().getWaypointForPlayerByName(player.getUniqueId(), name).orElse(null);
                if (wp == null) {
                    Bukkit.getScheduler().runTask(plugin, () -> messages.sendError(player, "waypoint-not-found"));
                    return;
                }
                var target = Bukkit.getPlayer(targetName);
                if (target == null) {
                    Bukkit.getScheduler().runTask(plugin, () -> messages.sendError(player, "player-not-found"));
                    return;
                }
                if (target.getUniqueId().equals(player.getUniqueId())) {
                    Bukkit.getScheduler().runTask(plugin, () -> messages.sendError(player, "invalid-input"));
                    return;
                }
                int wpId = wp.getId();
                String wpName = wp.getWaypointName();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.getShareRequestManager().createRequest(target.getUniqueId(),
                            new com.voxelpanel.managers.ShareRequestManager.ShareRequest(wpId, player.getUniqueId(), player.getName(), wpName));
                    messages.send(player, "share-sent", "player", target.getName());
                    messages.send(target, "share-received", "player", player.getName());
                    sounds.play(player, "success");
                });
            });
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("export")) {
            String fileName = args[1].replaceAll("[^A-Za-z0-9_-]", "_");
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                var wps = plugin.getWaypointRepository().getWaypointsForPlayer(player.getUniqueId());
                if (wps.isEmpty()) {
                    Bukkit.getScheduler().runTask(plugin, () -> messages.sendError(player, "export-empty"));
                    return;
                }
                int count = com.voxelpanel.utils.ImportExportUtil.exportWaypoints(plugin, player.getUniqueId(), fileName, wps);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (count >= 0) {
                        messages.send(player, "export-success", "count", String.valueOf(count), "file", fileName + ".yml");
                        sounds.play(player, "success");
                    } else {
                        messages.sendError(player, "database-error");
                    }
                });
            });
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("import")) {
            String fileName = args[1].replaceAll("[^A-Za-z0-9_-]", "_");
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                var imported = com.voxelpanel.utils.ImportExportUtil.importWaypoints(plugin, player.getUniqueId(), player.getName(), fileName);
                if (imported == null) {
                    Bukkit.getScheduler().runTask(plugin, () -> messages.send(player, "import-no-file", "file", fileName + ".yml"));
                    return;
                }
                int maxWaypoints = waypoints.getMaxWaypoints();
                int existing = plugin.getWaypointRepository().countWaypoints(player.getUniqueId());
                int count = 0;
                for (var wp : imported) {
                    if (existing + count >= maxWaypoints && !player.hasPermission("voxelpanel.bypass.limit")) {
                        break;
                    }
                    if (plugin.getWaypointRepository().playerHasWaypoint(player.getUniqueId(), wp.getWaypointName())) {
                        continue;
                    }
                    if (plugin.getWaypointRepository().insertWaypoint(wp)) {
                        count++;
                    }
                }
                int finalCount = count;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    messages.send(player, "import-success", "count", String.valueOf(finalCount));
                    sounds.play(player, "success");
                });
            });
            return true;
        }

        player.sendMessage(messages.get("prefix") + "§7الاستخدام: ");
        player.sendMessage("§b/waypoint set <name> §7- إضافة نقطة جديدة");
        player.sendMessage("§b/waypoint del <name> §7- مسح نقطة");
        player.sendMessage("§b/waypoint list §7- عرض قائمة نقاطك");
        player.sendMessage("§b/waypoint rem <old> <new> §7- تغيير اسم نقطة");
        player.sendMessage("§b/waypoint category <name> <category> §7- تغيير فئة نقطة");
        player.sendMessage("§b/waypoint icon <name> <material> §7- تغيير أيقونة نقطة");
        player.sendMessage("§b/waypoint public <name> <on|off> §7- جعل النقطة عامة/خاصة");
        player.sendMessage("§b/waypoint share <name> <player> §7- مشاركة النقطة مع لاعب");
        player.sendMessage("§b/waypoint export <file> §7- تصدير نقاطك لملف");
        player.sendMessage("§b/waypoint import <file> §7- استيراد نقاط من ملف");
        return true;
    }
}
