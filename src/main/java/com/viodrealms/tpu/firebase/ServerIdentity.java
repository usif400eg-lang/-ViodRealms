package com.viodrealms.tpu.firebase;

import com.viodrealms.tpu.ViodRealmsTPU;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Gives each server a stable, unique identity for the multi-server dashboard.
 *
 * Two ways to connect:
 *   1) Dashboard-first (recommended): the owner creates the server in the
 *      dashboard which generates server-id + auth-token, and pastes them into
 *      config.yml. This class reads them from config.
 *   2) Legacy pairing: if config has no server-id, one is generated locally
 *      along with a pairing code the owner enters in the dashboard.
 *
 * Values persist in server-identity.yml and the pairing code is printed to the
 * console. The auth-token is verified against Firebase so revoked servers stop.
 */
public class ServerIdentity {
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no ambiguous chars
    private final ViodRealmsTPU plugin;
    private final File file;
    private String serverId;
    private String pairingCode;
    private String authToken;

    /** Unique per-JVM-boot id, used to detect duplicate servers using the same credentials. */
    private final String instanceId = UUID.randomUUID().toString();

    /**
     * Whether this server is currently authorized to sync. In dashboard-token
     * mode this starts false and is confirmed by {@link #startAuthWatch()} once
     * the token is verified against Firebase; in legacy pairing mode it is true.
     * Every network-touching service checks this before writing.
     */
    private volatile boolean authorized;
    private volatile boolean bannerShown = false;
    private volatile boolean revokeLogged = false;
    private com.google.firebase.database.DatabaseReference watchRef;
    private com.google.firebase.database.ValueEventListener watchListener;

    public ServerIdentity(ViodRealmsTPU plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "server-identity.yml");
        load();
        // Legacy pairing (no dashboard token) is allowed immediately; token mode
        // must be confirmed by the live auth watch before any data is published.
        this.authorized = !hasDashboardToken();
    }

    private void load() {
        // Prefer values provided by the dashboard via config.yml.
        String cfgServerId = plugin.getConfig().getString("firebase.server-id", "");
        String cfgToken = plugin.getConfig().getString("firebase.auth-token", "");

        FileConfiguration cfg = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        serverId = cfg.getString("server-id", "");
        pairingCode = cfg.getString("pairing-code", "");
        authToken = cfg.getString("auth-token", "");

        boolean changed = false;

        // Dashboard-provided id/token always win and are persisted locally.
        if (cfgServerId != null && !cfgServerId.isBlank() && !cfgServerId.equals(serverId)) {
            serverId = cfgServerId;
            changed = true;
        }
        if (cfgToken != null && !cfgToken.isBlank() && !cfgToken.equals(authToken)) {
            authToken = cfgToken;
            changed = true;
        }

        if (serverId == null || serverId.isBlank()) {
            serverId = UUID.randomUUID().toString();
            changed = true;
        }
        if (pairingCode == null || pairingCode.isBlank()) {
            pairingCode = generateCode(8);
            changed = true;
        }
        if (changed) {
            cfg.set("server-id", serverId);
            cfg.set("pairing-code", pairingCode);
            cfg.set("auth-token", authToken);
            try {
                file.getParentFile().mkdirs();
                cfg.save(file);
            } catch (IOException e) {
                plugin.getLogger().warning("[Firebase] Could not save server-identity.yml: " + e.getMessage());
            }
        }
    }

    private String generateCode(int len) {
        SecureRandom rnd = new SecureRandom();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) sb.append(CHARS.charAt(rnd.nextInt(CHARS.length())));
        return sb.toString();
    }

    public String getServerId() {
        return serverId;
    }

    public String getAuthToken() {
        return authToken;
    }

    public boolean hasDashboardToken() {
        return authToken != null && !authToken.isBlank();
    }

    public String getPairingCode() {
        return pairingCode;
    }

    /** True when this server is currently allowed to sync (token verified, or legacy mode). */
    public boolean isAuthorized() {
        return authorized;
    }

    public String getInstanceId() {
        return instanceId;
    }

    /**
     * Validates the dashboard connection settings and logs any problems.
     * Never throws — returns false so the caller can stand down gracefully.
     */
    public boolean validateConfig() {
        boolean ok = true;
        String dbUrl = plugin.getConfig().getString("firebase.database-url", "");
        if (dbUrl == null || dbUrl.isBlank() || dbUrl.contains("YOUR-PROJECT")) {
            plugin.getLogger().warning("[Firebase] config.yml: firebase.database-url is missing or a placeholder.");
            ok = false;
        }
        if (serverId == null || serverId.isBlank()) {
            plugin.getLogger().warning("[Firebase] config.yml: firebase.server-id is empty.");
            ok = false;
        }
        int cfgVersion = plugin.getConfig().getInt("firebase.config-version", 0);
        if (cfgVersion < 1) {
            plugin.getLogger().warning("[Firebase] config.yml: firebase.config-version looks outdated. Re-copy the config from the dashboard.");
        }
        if (!hasDashboardToken()) {
            plugin.getLogger().info("[Firebase] No auth-token set — running in legacy pairing mode (enter the pairing code in the dashboard).");
        }
        return ok;
    }

    /** Prints the pairing / connection details prominently in the console. */
    public void printBanner() {
        plugin.getLogger().info("========================================");
        plugin.getLogger().info("  ViodRealms Dashboard — Server Connection");
        plugin.getLogger().info("  Server ID   : " + serverId);
        if (hasDashboardToken()) {
            plugin.getLogger().info("  Auth Token  : (configured from dashboard)");
            plugin.getLogger().info("  Mode        : Dashboard-linked");
        } else {
            plugin.getLogger().info("  Pairing Code: " + pairingCode);
            plugin.getLogger().info("  Enter the pairing code in the dashboard");
            plugin.getLogger().info("  (Add Server) to claim this server.");
        }
        plugin.getLogger().info("========================================");
    }

    /**
     * Publishes this server's public metadata to Firebase so the dashboard can
     * resolve a pairing code -> serverId, verify the auth token, and show state.
     *
     * The auth token is NEVER written back by the plugin — the dashboard is the
     * single source of truth for it. The plugin only proves it holds a matching
     * token by writing to the token-gated {@code servers/{id}} tree, and reports
     * its live instanceId so the dashboard can detect duplicate/fake connections.
     */
    public void publishMeta() {
        var db = plugin.getFirebaseManager();
        if (db == null || !db.isConnected()) return;
        // In token mode, only publish once we're authorized (prevents an
        // unauthorized/revoked server from flipping itself back to "online").
        if (hasDashboardToken() && !authorized) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            String name = Bukkit.getServer().getName();
            String motd = Bukkit.getMotd();
            int port = Bukkit.getServer().getPort();
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                Map<String, Object> meta = new HashMap<>();
                meta.put("pairingCode", pairingCode);
                meta.put("name", motd != null && !motd.isBlank() ? motd : name);
                meta.put("port", port);
                meta.put("online", true);
                meta.put("lastSeen", System.currentTimeMillis());
                meta.put("instanceId", instanceId);
                meta.put("configVersion", plugin.getConfig().getInt("firebase.config-version", 1));
                var metaRef = db.getReference("serverMeta").child(serverId);
                metaRef.updateChildrenAsync(meta);
                db.getReference("pairingCodes").child(pairingCode).setValue(serverId, (error, ref) -> {
                    if (error != null) {
                        plugin.getLogger().severe("[Firebase] FAILED to publish server metadata: " + error.getMessage());
                    } else if (!bannerShown) {
                        bannerShown = true;
                        plugin.getLogger().info("[Firebase] Server registered. " + (hasDashboardToken() ? "Linked via dashboard token." : "Pairing Code: " + pairingCode));
                    }
                });
            });
        });
    }

    /**
     * Starts a live listener on the dashboard-issued token. This is the single
     * source of truth for authorization:
     *   - token present & matches ours -> authorized
     *   - token missing (server not yet created) -> unauthorized (wait)
     *   - token changed/removed (revoked or rotated) -> unauthorized (stand down)
     *
     * Reacts in real time (no polling) and never throws into the server thread.
     * Legacy pairing mode (no token in config) skips the watch and stays allowed.
     */
    public void startAuthWatch() {
        var db = plugin.getFirebaseManager();
        if (db == null || !db.isConnected()) return;
        if (!hasDashboardToken()) {
            authorized = true;
            return;
        }
        try {
            watchRef = db.getReference("serverMeta").child(serverId).child("authToken");
            watchListener = new com.google.firebase.database.ValueEventListener() {
                @Override public void onDataChange(com.google.firebase.database.DataSnapshot snap) {
                    String stored = snap.getValue(String.class);
                    boolean nowAuthorized = stored != null && stored.equals(authToken);
                    if (nowAuthorized) {
                        if (!authorized) {
                            plugin.getLogger().info("[Firebase] Authorization confirmed by dashboard — syncing enabled.");
                            revokeLogged = false;
                        }
                        authorized = true;
                        // Refresh presence as soon as we become authorized.
                        publishMeta();
                    } else {
                        authorized = false;
                        if (!revokeLogged) {
                            revokeLogged = true;
                            plugin.getLogger().warning("========================================");
                            if (stored == null) {
                                plugin.getLogger().warning("[Firebase] Waiting for authorization — this server-id is not registered in the dashboard yet.");
                            } else {
                                plugin.getLogger().warning("[Firebase] Auth token was revoked or rotated from the dashboard.");
                                plugin.getLogger().warning("[Firebase] This server is no longer authorized and has stopped syncing.");
                                plugin.getLogger().warning("[Firebase] Copy the fresh config.yml from the dashboard to reconnect.");
                            }
                            plugin.getLogger().warning("========================================");
                        }
                        // Best-effort mark offline; never block the server.
                        try { db.getReference("serverMeta").child(serverId).child("online").setValueAsync(false); } catch (Exception ignored) {}
                    }
                }
                @Override public void onCancelled(com.google.firebase.database.DatabaseError e) {
                    // A read error must not lock us out permanently, but must not
                    // silently grant access either. Keep the last known state.
                    plugin.getLogger().fine("[Firebase] Auth watch read error: " + e.getMessage());
                }
            };
            watchRef.addValueEventListener(watchListener);
        } catch (Throwable t) {
            plugin.getLogger().warning("[Firebase] Could not start auth watch: " + t.getMessage());
        }
    }

    /** Marks the server offline in metadata (best-effort on shutdown). */
    public void markOffline() {
        var db = plugin.getFirebaseManager();
        if (watchRef != null && watchListener != null) {
            try { watchRef.removeEventListener(watchListener); } catch (Exception ignored) {}
        }
        if (db == null || !db.isConnected()) return;
        try {
            db.getReference("serverMeta").child(serverId).child("online").setValueAsync(false);
        } catch (Exception ignored) {
        }
    }
}
