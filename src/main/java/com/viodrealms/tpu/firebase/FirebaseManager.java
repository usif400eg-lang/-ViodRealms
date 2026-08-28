package com.viodrealms.tpu.firebase;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.viodrealms.tpu.ViodRealmsTPU;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * Handles the lifecycle of the Firebase Admin SDK connection.
 * Initialization is guarded so the plugin still works when Firebase is disabled
 * or the service account key is missing.
 */
public class FirebaseManager {
    private final ViodRealmsTPU plugin;
    private FirebaseApp firebaseApp;
    private FirebaseDatabase database;
    private boolean connected = false;

    public FirebaseManager(ViodRealmsTPU plugin) {
        this.plugin = plugin;
    }

    /** Attempts to initialize the Firebase connection. Returns true on success. */
    public boolean initialize() {
        String databaseUrl = plugin.getConfig().getString("firebase.database-url", "");
        String keyFileName = plugin.getConfig().getString("firebase.service-account-file", "firebase-service-account.json");

        // A valid setup means: a database URL is configured AND a key exists
        // (either in the plugin folder or bundled inside the JAR).
        boolean hasUrl = databaseUrl != null && !databaseUrl.isBlank() && !databaseUrl.contains("YOUR-PROJECT");
        File keyFile = new File(plugin.getDataFolder(), keyFileName);
        boolean hasKey = keyFile.exists() || plugin.getResource(keyFileName) != null;

        boolean enabledFlag = plugin.getConfig().getBoolean("firebase.enabled", false);

        // Auto-enable: if credentials are available we connect even if an old
        // config.yml still says enabled:false (self-heals stale configs), and
        // we persist the corrected flag so the file matches reality.
        if (!enabledFlag && hasUrl && hasKey) {
            plugin.getLogger().info("[Firebase] Auto-enabling (credentials found, config was disabled).");
            plugin.getConfig().set("firebase.enabled", true);
            try { plugin.saveConfig(); } catch (Exception ignored) {}
            enabledFlag = true;
        }

        if (!enabledFlag) {
            plugin.getLogger().info("[Firebase] Integration is disabled (no credentials found).");
            return false;
        }

        if (!hasUrl) {
            plugin.getLogger().warning("[Firebase] database-url is not configured. Disabling Firebase integration.");
            return false;
        }

        // Prefer a key file in the plugin folder (admin override); otherwise fall back to the
        // copy bundled inside the JAR so the plugin connects automatically on first run.
        InputStream credentialStream = null;
        try {
            if (keyFile.exists()) {
                credentialStream = new FileInputStream(keyFile);
                plugin.getLogger().info("[Firebase] Using service account from plugin folder.");
            } else {
                credentialStream = plugin.getResource(keyFileName);
                if (credentialStream != null) {
                    plugin.getLogger().info("[Firebase] Using bundled service account.");
                }
            }

            if (credentialStream == null) {
                plugin.getLogger().warning("[Firebase] No service account key found. Disabling Firebase integration.");
                return false;
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credentialStream))
                    .setDatabaseUrl(databaseUrl)
                    .build();

            // Use a uniquely named app so reloads don't clash with an existing default instance.
            String appName = "ViodRealmsTPU";
            for (FirebaseApp existing : FirebaseApp.getApps()) {
                if (existing.getName().equals(appName)) {
                    existing.delete();
                    break;
                }
            }
            firebaseApp = FirebaseApp.initializeApp(options, appName);
            database = FirebaseDatabase.getInstance(firebaseApp);
            connected = true;
            plugin.getLogger().info("[Firebase] Initialized. Testing live connection to " + databaseUrl + " ...");
            // The Admin SDK can "initialize" even with no network. Verify the real
            // connection state via the special ".info/connected" node and log it,
            // so blocked-outbound hosts are obvious in the console.
            try {
                database.getReference(".info/connected").addValueEventListener(new com.google.firebase.database.ValueEventListener() {
                    private boolean reported = false;
                    @Override public void onDataChange(com.google.firebase.database.DataSnapshot snap) {
                        Boolean isConn = snap.getValue(Boolean.class);
                        if (Boolean.TRUE.equals(isConn)) {
                            plugin.getLogger().info("[Firebase] LIVE CONNECTION OK — reachable and authenticated.");
                            reported = true;
                        } else if (!reported) {
                            plugin.getLogger().warning("[Firebase] Not connected yet — waiting for network... (if this never turns OK, the host blocks outbound connections to Firebase)");
                        }
                    }
                    @Override public void onCancelled(com.google.firebase.database.DatabaseError e) {
                        plugin.getLogger().severe("[Firebase] Connection check cancelled: " + e.getMessage());
                    }
                });
            } catch (Throwable ignored) {}
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "[Firebase] Failed to initialize.", e);
            return false;
        } finally {
            if (credentialStream != null) {
                try {
                    credentialStream.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public boolean isConnected() {
        return connected;
    }

    public FirebaseApp getApp() {
        return firebaseApp;
    }

    /** Returns a reference rooted at this server's data path (servers/{server-id}/...). */
    public DatabaseReference getServerRef() {
        if (!connected) {
            return null;
        }
        return database.getReference("servers").child(getServerId());
    }

    /** The active server id: prefer the generated identity, fall back to config. */
    public String getServerId() {
        if (plugin.getServerIdentity() != null) {
            return plugin.getServerIdentity().getServerId();
        }
        return plugin.getConfig().getString("firebase.server-id", "server1");
    }

    public DatabaseReference getReference(String path) {
        if (!connected) {
            return null;
        }
        return database.getReference(path);
    }

    public void shutdown() {
        if (firebaseApp != null) {
            try {
                firebaseApp.delete();
            } catch (Exception ignored) {
            }
        }
        connected = false;
    }
}
