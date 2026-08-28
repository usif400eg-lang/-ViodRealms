package com.voxelpanel.commands;

import com.voxelpanel.VoxelPanel;
import com.voxelpanel.models.Waypoint;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides tab completion for the plugin's player commands.
 * Waypoint names are read from the in-memory list to keep completion fast and off the DB thread.
 */
public class WaypointTabCompleter implements TabCompleter {
    private final VoxelPanel plugin;

    private static final List<String> WAYPOINT_SUB = List.of(
            "set", "del", "list", "rem", "category", "icon", "public", "share", "export", "import");
    private static final List<String> CATEGORIES = List.of("MINE", "BASE", "FARM", "OTHER");
    private static final List<String> LANGUAGES = List.of("ar", "en");
    private static final List<String> COMPASS_SUB = List.of("track", "reset");

    public WaypointTabCompleter(VoxelPanel plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }

        String cmd = command.getName().toLowerCase();
        List<String> result = new ArrayList<>();

        switch (cmd) {
            case "language" -> {
                if (args.length == 1) {
                    filter(LANGUAGES, args[0], result);
                }
            }
            case "compass" -> {
                if (args.length == 1) {
                    filter(COMPASS_SUB, args[0], result);
                } else if (args.length == 2 && args[0].equalsIgnoreCase("track")) {
                    filter(waypointNames(player), args[1], result);
                }
            }
            case "tpu" -> {
                if (args.length == 1) {
                    filter(waypointNames(player), args[0], result);
                }
            }
            case "waypoint" -> {
                if (args.length == 1) {
                    filter(WAYPOINT_SUB, args[0], result);
                } else if (args.length == 2) {
                    String sub = args[0].toLowerCase();
                    // Sub-commands whose second argument is an existing waypoint name.
                    if (List.of("del", "delete", "rem", "rename", "category", "icon", "public", "share").contains(sub)) {
                        filter(waypointNames(player), args[1], result);
                    }
                } else if (args.length == 3) {
                    String sub = args[0].toLowerCase();
                    if (sub.equals("category")) {
                        filter(CATEGORIES, args[2], result);
                    } else if (sub.equals("public")) {
                        filter(List.of("on", "off"), args[2], result);
                    } else if (sub.equals("share")) {
                        filter(onlinePlayerNames(), args[2], result);
                    }
                }
            }
            default -> {
            }
        }
        return result;
    }

    private List<String> waypointNames(Player player) {
        List<String> names = new ArrayList<>();
        for (Waypoint wp : plugin.getWaypointManager().getWaypoints(player.getUniqueId())) {
            names.add(wp.getWaypointName());
        }
        return names;
    }

    private List<String> onlinePlayerNames() {
        List<String> names = new ArrayList<>();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            names.add(p.getName());
        }
        return names;
    }

    private void filter(List<String> source, String input, List<String> result) {
        String lower = input.toLowerCase();
        for (String s : source) {
            if (s.toLowerCase().startsWith(lower)) {
                result.add(s);
            }
        }
    }
}
