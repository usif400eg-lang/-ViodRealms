package com.voxelpanel.services;

import com.voxelpanel.VoxelPanel;
import com.voxelpanel.managers.MessageManager;
import com.voxelpanel.managers.SoundManager;
import com.voxelpanel.models.Waypoint;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TeleportService {
    private final VoxelPanel plugin;
    private final MessageManager messageManager;
    private final SoundManager soundManager;
    private final Map<UUID, Integer> activeTeleports = new HashMap<>();
    /** Cooldown tracking: player UUID -> timestamp (ms) when the last teleport completed. */
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    /** Last location before a teleport/death, used by the /back command. */
    private final Map<UUID, Location> lastLocations = new HashMap<>();

    public TeleportService(VoxelPanel plugin, MessageManager messageManager, SoundManager soundManager) {
        this.plugin = plugin;
        this.messageManager = messageManager;
        this.soundManager = soundManager;
    }

    /** Checks if the player is on teleport cooldown; sends a message if so. Returns true if blocked. */
    private boolean isOnCooldown(Player player) {
        if (player.hasPermission("voxelpanel.bypass.cooldown")) {
            return false;
        }
        int cooldownSeconds = plugin.getConfig().getInt("teleport.cooldown-seconds", 0);
        if (cooldownSeconds <= 0) {
            return false;
        }
        Long last = cooldowns.get(player.getUniqueId());
        if (last == null) {
            return false;
        }
        long elapsed = System.currentTimeMillis() - last;
        long remaining = (cooldownSeconds * 1000L) - elapsed;
        if (remaining > 0) {
            messageManager.send(player, "teleport-cooldown", "seconds", String.valueOf((remaining / 1000) + 1));
            soundManager.play(player, "error");
            return true;
        }
        return false;
    }

    private void applyCooldown(Player player) {
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
    }

    /** Stores the player's current location so /back can return there later. */
    public void saveLastLocation(Player player) {
        lastLocations.put(player.getUniqueId(), player.getLocation().clone());
    }

    public Location getLastLocation(UUID playerUuid) {
        return lastLocations.get(playerUuid);
    }

    public void teleport(Player player, Waypoint waypoint) {
        World world = Bukkit.getWorld(waypoint.getWorldName());
        if (world == null) {
            messageManager.send(player, "world-not-found");
            soundManager.play(player, "error");
            return;
        }
        Location targetLocation = new Location(world, waypoint.getX(), waypoint.getY(), waypoint.getZ(), waypoint.getYaw(), waypoint.getPitch());
        teleportToLocation(player, targetLocation);
    }

    /** Generic teleport with delay, cooldown, effects, and last-location tracking. */
    public void teleportToLocation(Player player, Location targetLocation) {
        if (targetLocation == null || targetLocation.getWorld() == null) {
            messageManager.send(player, "world-not-found");
            soundManager.play(player, "error");
            return;
        }

        if (isOnCooldown(player)) {
            return;
        }

        int delay = plugin.getConfig().getInt("teleport.delay", 0);

        if (delay <= 0 || player.hasPermission("voxelpanel.bypass.delay")) {
            performTeleport(player, targetLocation);
            return;
        }

        if (activeTeleports.containsKey(player.getUniqueId())) {
            messageManager.send(player, "teleport-failed");
            soundManager.play(player, "error");
            return;
        }

        Location startLocation = player.getLocation().clone();

        int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            int remaining = delay;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel(player);
                    return;
                }

                if (plugin.getConfig().getBoolean("teleport.cancel-on-move", false) &&
                    (player.getLocation().distanceSquared(startLocation) > 0.1)) {
                    messageManager.send(player, "teleport-cancelled-move");
                    soundManager.play(player, "cancel");
                    cancel(player);
                    return;
                }

                if (remaining <= 0) {
                    performTeleport(player, targetLocation);
                    cancel(player);
                    return;
                }

                // Effects
                if (plugin.getConfig().getBoolean("teleport.countdown-title", true)) {
                    player.sendTitle("§b" + remaining, "§7sec...", 0, 20, 0);
                }
                if (plugin.getConfig().getBoolean("teleport.particle-effects", true)) {
                    player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5, 0.1);
                    player.getWorld().spawnParticle(Particle.ENCHANT, player.getLocation().add(0, 0.5, 0), 5, 0.3, 0.3, 0.3, 0.05);
                }
                messageManager.send(player, "teleport-countdown", "seconds", String.valueOf(remaining));
                soundManager.play(player, "confirm");

                remaining--;
            }
        }, 0L, 20L);

        activeTeleports.put(player.getUniqueId(), taskId);
    }

    public void cancelTeleport(Player player) {
        Integer taskId = activeTeleports.remove(player.getUniqueId());
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    private void cancel(Player player) {
        Integer taskId = activeTeleports.remove(player.getUniqueId());
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    private void performTeleport(Player player, Location location) {
        // Remember where the player was before this teleport for /back.
        saveLastLocation(player);

        if (plugin.getConfig().getBoolean("teleport.safe-teleport", true)) {
            Location safeLoc = findSafeLocation(location);
            player.teleport(safeLoc);
        } else {
            player.teleport(location);
        }
        applyCooldown(player);
        messageManager.send(player, "teleport-success");
        soundManager.play(player, "teleport");
        if (plugin.getConfig().getBoolean("teleport.particle-effects", true)) {
            player.getWorld().spawnParticle(Particle.DRAGON_BREATH, player.getLocation().add(0, 1, 0), 50, 0.5, 1, 0.5, 0.1);
            player.getWorld().spawnParticle(Particle.FLAME, player.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.05);
        }
        player.sendTitle("§aTeleported!", "", 10, 40, 10);
    }

    private Location findSafeLocation(Location location) {
        World world = location.getWorld();
        if (world == null) return location;

        int x = location.getBlockX();
        int y = (int) location.getY();
        int z = location.getBlockZ();

        for (int checkY = y; checkY < world.getMaxHeight(); checkY++) {
            Location loc = new Location(world, x + 0.5, checkY, z + 0.5);
            if (isSafeBlock(world, loc.clone().subtract(0, 1, 0)) && isSafeBlock(world, loc)) {
                return loc;
            }
        }
        for (int checkY = y; checkY > 0; checkY--) {
            Location loc = new Location(world, x + 0.5, checkY, z + 0.5);
            if (isSafeBlock(world, loc.clone().subtract(0, 1, 0)) && isSafeBlock(world, loc)) {
                return loc;
            }
        }
        return location;
    }

    private boolean isSafeBlock(World world, Location loc) {
        return world.getBlockAt(loc).getType().isAir() ||
               world.getBlockAt(loc).getType().toString().contains("WATER") ||
               world.getBlockAt(loc).getType().toString().contains("GRASS") ||
               world.getBlockAt(loc).getType().toString().contains("PATH");
    }
}
